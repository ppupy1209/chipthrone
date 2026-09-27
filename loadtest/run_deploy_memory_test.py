#!/usr/bin/env python3
"""Hold SSE connections against the deployed API while a slot deployment runs.

Clients behave like browser EventSource: when a stream ends they reconnect after
a short delay, so the new slot receives the reconnect burst after the switch.
A 1-second /api/health probe records what an outside user sees during the
deployment. Metrics are exposed on :19100 for the local Prometheus, and the
per-second timeline is written as JSON when the run ends.
"""

import argparse
import asyncio
import json
import ssl
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread
from urllib.parse import urlparse

METRICS_PORT = 19100


class State:
    def __init__(self):
        self.target = 0
        self.open = 0
        self.connects = 0
        self.reconnects = 0
        self.events = 0
        self.connect_errors = {}
        self.health_ok = 0
        self.health_fail = 0
        self.health_last_ms = 0.0

    def prometheus(self):
        lines = [
            f"deploy_load_target_connections {self.target}",
            f"deploy_load_open_connections {self.open}",
            f"deploy_load_connects_total {self.connects}",
            f"deploy_load_reconnects_total {self.reconnects}",
            f"deploy_load_events_total {self.events}",
            f"deploy_load_health_ok_total {self.health_ok}",
            f"deploy_load_health_fail_total {self.health_fail}",
            f"deploy_load_health_latency_ms {self.health_last_ms}",
        ]
        for status, count in self.connect_errors.items():
            lines.append(f'deploy_load_connect_errors_total{{status="{status}"}} {count}')
        return "\n".join(lines) + "\n"


STATE = State()


class MetricsHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = STATE.prometheus().encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; version=0.0.4")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


async def open_stream(url, ssl_context):
    host = url.hostname
    port = url.port or (443 if url.scheme == "https" else 80)
    reader, writer = await asyncio.wait_for(
        asyncio.open_connection(host, port, ssl=ssl_context if url.scheme == "https" else None),
        timeout=10,
    )
    path = url.path + ("?" + url.query if url.query else "")
    writer.write(
        f"GET {path} HTTP/1.1\r\nHost: {host}\r\nAccept: text/event-stream\r\n"
        "Cache-Control: no-cache\r\n\r\n".encode()
    )
    await writer.drain()
    status_line = await asyncio.wait_for(reader.readline(), timeout=10)
    status = status_line.split(b" ")[1].decode() if status_line else "eof"
    return reader, writer, status


async def client(url, ssl_context, retry_seconds, stop):
    first = True
    while not stop.is_set():
        if not first:
            STATE.reconnects += 1
            await asyncio.sleep(retry_seconds)
        first = False
        writer = None
        try:
            reader, writer, status = await open_stream(url, ssl_context)
            if status != "200":
                STATE.connect_errors[status] = STATE.connect_errors.get(status, 0) + 1
                continue
            STATE.connects += 1
            STATE.open += 1
            try:
                while not stop.is_set():
                    line = await reader.readline()
                    if not line:
                        break
                    if line.startswith(b"event:quotes"):
                        STATE.events += 1
            finally:
                STATE.open -= 1
        except (OSError, asyncio.TimeoutError, ssl.SSLError, IndexError) as error:
            key = type(error).__name__
            STATE.connect_errors[key] = STATE.connect_errors.get(key, 0) + 1
        finally:
            if writer is not None:
                writer.close()


async def health_probe(base, ssl_context, stop):
    url = urlparse(base + "/api/health")
    while not stop.is_set():
        started = time.monotonic()
        writer = None
        try:
            reader, writer = await asyncio.wait_for(
                asyncio.open_connection(url.hostname, url.port or 443, ssl=ssl_context), timeout=2
            )
            writer.write(f"GET {url.path} HTTP/1.1\r\nHost: {url.hostname}\r\nConnection: close\r\n\r\n".encode())
            await writer.drain()
            status_line = await asyncio.wait_for(reader.readline(), timeout=2)
            if b" 200 " in status_line:
                STATE.health_ok += 1
            else:
                STATE.health_fail += 1
        except (OSError, asyncio.TimeoutError, ssl.SSLError):
            STATE.health_fail += 1
        finally:
            if writer is not None:
                writer.close()
        STATE.health_last_ms = round((time.monotonic() - started) * 1000, 2)
        await asyncio.sleep(max(0.0, 1 - (time.monotonic() - started)))


async def sampler(timeline, stop):
    while not stop.is_set():
        timeline.append({
            "at": datetime.now(timezone.utc).isoformat(),
            "open": STATE.open,
            "reconnects": STATE.reconnects,
            "connectErrors": dict(STATE.connect_errors),
            "healthOk": STATE.health_ok,
            "healthFail": STATE.health_fail,
            "healthLatencyMs": STATE.health_last_ms,
        })
        await asyncio.sleep(1)


async def main(args):
    ssl_context = ssl.create_default_context()
    url = urlparse(f"{args.base_url}/api/stream?symbols={args.symbols}")
    stop = asyncio.Event()
    timeline = []
    tasks = [
        asyncio.create_task(health_probe(args.base_url, ssl_context, stop)),
        asyncio.create_task(sampler(timeline, stop)),
    ]
    for index in range(args.connections):
        STATE.target += 1
        tasks.append(asyncio.create_task(client(url, ssl_context, args.retry_seconds, stop)))
        if (index + 1) % args.ramp_per_second == 0:
            await asyncio.sleep(1)

    await asyncio.sleep(args.duration)
    stop.set()
    for task in tasks:
        task.cancel()
    await asyncio.gather(*tasks, return_exceptions=True)

    result = {
        "target": args.base_url,
        "connections": args.connections,
        "retrySeconds": args.retry_seconds,
        "startedAt": timeline[0]["at"] if timeline else None,
        "endedAt": timeline[-1]["at"] if timeline else None,
        "totals": {
            "connects": STATE.connects,
            "reconnects": STATE.reconnects,
            "events": STATE.events,
            "connectErrors": STATE.connect_errors,
            "healthOk": STATE.health_ok,
            "healthFail": STATE.health_fail,
        },
        "timeline": timeline,
    }
    with open(args.output, "w") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(json.dumps(result["totals"], ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="https://api.chipthrone.com")
    parser.add_argument("--symbols", default="SNDK,MU")
    parser.add_argument("--connections", type=int, required=True)
    parser.add_argument("--ramp-per-second", type=int, default=25)
    parser.add_argument("--retry-seconds", type=float, default=3)
    parser.add_argument("--duration", type=int, required=True, help="seconds to hold after ramp-up")
    parser.add_argument("--output", required=True)
    arguments = parser.parse_args()

    server = ThreadingHTTPServer(("0.0.0.0", METRICS_PORT), MetricsHandler)
    Thread(target=server.serve_forever, daemon=True).start()
    asyncio.run(main(arguments))
