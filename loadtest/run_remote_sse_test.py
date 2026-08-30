#!/usr/bin/env python3
"""Measure the deployed SSE service using shared Hyperliquid batches.

All clients share the same symbols, so one Hyperliquid batch serves every
connection in a poll cycle.
"""

import argparse
import http.client
import json
import math
import socket
import ssl
import statistics
import threading
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from urllib.parse import urlparse


LATENCIES_MS = []
LATENCY_LOCK = threading.Lock()
MEASUREMENT_STARTED = 0.0


def percentile(values, value):
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, math.ceil(value * len(ordered)) - 1)
    return round(ordered[index], 2)


def prometheus_samples(base_url):
    request = urllib.request.Request(base_url + "/actuator/prometheus")
    with urllib.request.urlopen(request, timeout=10) as response:
        text = response.read().decode()
    samples = {}
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        key, raw_value = line.rsplit(" ", 1)
        try:
            samples[key] = float(raw_value)
        except ValueError:
            pass
    return samples


def metric_sum(samples, name, required_labels=()):
    return sum(
        value
        for key, value in samples.items()
        if (key == name or key.startswith(name + "{"))
        and all(label in key for label in required_labels)
    )


class SseClient:
    def __init__(self, parsed_url, symbols):
        self.parsed_url = parsed_url
        self.symbols = symbols
        self.connection = None
        self.response = None
        self.error = None
        self.events = 0

    def open(self):
        try:
            port = self.parsed_url.port or (443 if self.parsed_url.scheme == "https" else 80)
            if self.parsed_url.scheme == "https":
                self.connection = http.client.HTTPSConnection(
                    self.parsed_url.hostname,
                    port,
                    timeout=15,
                    context=ssl.create_default_context(),
                )
            else:
                self.connection = http.client.HTTPConnection(
                    self.parsed_url.hostname, port, timeout=15
                )
            base_path = self.parsed_url.path.rstrip("/")
            path = f"{base_path}/api/stream?symbols={','.join(self.symbols)}"
            self.connection.request("GET", path)
            self.response = self.connection.getresponse()
            if self.response.status != 200:
                raise RuntimeError(f"SSE HTTP {self.response.status}")
            self.connection.sock.settimeout(60)
            threading.Thread(target=self.read, daemon=True).start()
        except Exception as exception:
            self.error = str(exception)
        return self

    def read(self):
        try:
            while True:
                line = self.response.readline()
                if not line:
                    return
                if not line.startswith(b"data:"):
                    continue
                self.events += 1
                if time.monotonic() < MEASUREMENT_STARTED:
                    continue
                payload = json.loads(line[5:])
                at = datetime.fromisoformat(payload["at"].replace("Z", "+00:00"))
                latency = (datetime.now(timezone.utc) - at).total_seconds() * 1000
                if 0 <= latency <= 10_000:
                    with LATENCY_LOCK:
                        LATENCIES_MS.append(latency)
        except Exception:
            return

    def close(self):
        sock = self.connection.sock if self.connection else None
        if sock:
            try:
                sock.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
        if self.response:
            self.response.close()
        if self.connection:
            self.connection.close()


def external_calls(samples, source):
    return metric_sum(
        samples,
        "chipthrone_quote_external_api_calls_total",
        (f'source="{source}"',),
    )


def run(base_url, users, duration, symbols):
    global LATENCIES_MS, MEASUREMENT_STARTED
    LATENCIES_MS = []
    MEASUREMENT_STARTED = time.monotonic()
    parsed_url = urlparse(base_url)
    before = prometheus_samples(base_url)
    with ThreadPoolExecutor(max_workers=min(users, 100)) as pool:
        clients = list(pool.map(lambda _: SseClient(parsed_url, symbols).open(), range(users)))
    errors = sum(client.error is not None for client in clients)
    samples = []
    deadline = time.monotonic() + duration
    while time.monotonic() < deadline:
        metrics = prometheus_samples(base_url)
        samples.append((time.monotonic(), metrics))
        time.sleep(1)
    after = prometheus_samples(base_url)

    for client in clients:
        client.close()
    cleanup_started = time.monotonic()
    cleanup_deadline = cleanup_started + 45
    cleaned = prometheus_samples(base_url)
    while time.monotonic() < cleanup_deadline:
        connections = metric_sum(cleaned, "chipthrone_sse_connections")
        active = metric_sum(cleaned, "chipthrone_quote_active_symbols")
        if connections == 0 and active == 0:
            break
        time.sleep(1)
        cleaned = prometheus_samples(base_url)

    cpu_percent = None
    container_cpu_percent = None
    if len(samples) >= 2:
        first_time, first = samples[0]
        last_time, last = samples[-1]
        elapsed = last_time - first_time
        cpu_delta = metric_sum(last, "process_cpu_time_ns_total") - metric_sum(
            first, "process_cpu_time_ns_total"
        )
        cpu_percent = round(cpu_delta / 1_000_000_000 / elapsed * 100, 2)
        container_cpu_delta = metric_sum(
            last, "chipthrone_container_cpu_usage_seconds"
        ) - metric_sum(first, "chipthrone_container_cpu_usage_seconds")
        if container_cpu_delta >= 0:
            container_cpu_percent = round(container_cpu_delta / elapsed * 100, 2)
    jvm_memory = [metric_sum(sample, "jvm_memory_used_bytes") / 1024 / 1024 for _, sample in samples]
    container_memory = [
        metric_sum(sample, "chipthrone_container_memory_current_bytes") / 1024 / 1024
        for _, sample in samples
    ]
    with LATENCY_LOCK:
        latencies = list(LATENCIES_MS)

    return {
        "measured_at": datetime.now(timezone.utc).isoformat(),
        "target": base_url,
        "users_requested": users,
        "symbols": symbols,
        "duration_seconds": duration,
        "connections_max": int(max(
            metric_sum(sample, "chipthrone_sse_connections") for _, sample in samples
        )),
        "active_unique_symbols_max": int(max(
            metric_sum(sample, "chipthrone_quote_active_symbols") for _, sample in samples
        )),
        "connection_error_rate": round(errors / users, 4),
        "events_received": sum(client.events for client in clients),
        "delivery_latency_p95_ms": percentile(latencies, 0.95),
        "process_cpu_percent": cpu_percent,
        "container_cpu_percent": container_cpu_percent,
        "jvm_memory_mib_mean": round(statistics.mean(jvm_memory), 2),
        "jvm_memory_mib_max": round(max(jvm_memory), 2),
        "container_memory_mib_mean": round(statistics.mean(container_memory), 2),
        "container_memory_mib_max": round(max(container_memory), 2),
        "open_files_max": int(max(
            metric_sum(sample, "process_files_open_files") for _, sample in samples
        )),
        "fsc_daily_stock_calls": int(
            external_calls(after, "financial_services_commission")
            - external_calls(before, "financial_services_commission")
        ),
        "upbit_fx_calls": int(
            external_calls(after, "upbit") - external_calls(before, "upbit")
        ),
        "hyperliquid_calls": int(
            external_calls(after, "hyperliquid") - external_calls(before, "hyperliquid")
        ),
        "connections_after_cleanup": int(metric_sum(cleaned, "chipthrone_sse_connections")),
        "active_symbols_after_grace": int(metric_sum(cleaned, "chipthrone_quote_active_symbols")),
        "orphan_emitters_after_cleanup": int(metric_sum(cleaned, "chipthrone_sse_orphan_emitters")),
        "cleanup_seconds": round(time.monotonic() - cleanup_started, 2),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="https://api.chipthrone.com")
    parser.add_argument("--users", type=int, required=True)
    parser.add_argument("--duration", type=int, default=12)
    parser.add_argument("--symbols", default="SNDK,MU")
    args = parser.parse_args()
    result = run(
        args.base_url.rstrip("/"),
        args.users,
        args.duration,
        [symbol.strip() for symbol in args.symbols.split(",") if symbol.strip()],
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
