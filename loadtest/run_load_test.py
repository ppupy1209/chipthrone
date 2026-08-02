#!/usr/bin/env python3
"""Run repeatable SSE demand-collection scenarios against local fake quote APIs."""

import http.client
import json
import math
import os
import signal
import socket
import statistics
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

from fake_quote_server import Handler, ThreadingHTTPServer


ROOT = Path(__file__).resolve().parents[1]
JAR = next(iter((ROOT / "backend/build/libs").glob("*.jar")), ROOT / "backend/build/libs/chipthrone-api-0.0.1.jar")
BACKEND_PORT = 18080
FAKE_PORT = 19090
USERS = 200
EVENT_LATENCIES_MS = []
EVENT_LOCK = threading.Lock()
URL_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def request_json(url, method="GET"):
    request = urllib.request.Request(url, method=method)
    with URL_OPENER.open(request, timeout=5) as response:
        return json.load(response)


def wait_ready(url, process, timeout=30):
    deadline = time.time() + timeout
    last_error = None
    while time.time() < deadline:
        if process.poll() is not None:
            raise RuntimeError("backend exited before readiness")
        try:
            request_json(url)
            return
        except Exception as exception:
            last_error = exception
            time.sleep(0.2)
    raise RuntimeError(f"readiness timed out: {last_error!r}")


def parse_prometheus():
    text = URL_OPENER.open(
        f"http://127.0.0.1:{BACKEND_PORT}/actuator/prometheus", timeout=5
    ).read().decode()
    samples = {}
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        key, value = line.rsplit(" ", 1)
        try:
            samples[key] = float(value)
        except ValueError:
            pass
    return samples


def metric_sum(samples, name, required_labels=()):
    total = 0.0
    for key, value in samples.items():
        if key == name or key.startswith(name + "{"):
            if all(label in key for label in required_labels):
                total += value
    return total


class SseClient:
    def __init__(self, symbols):
        self.symbols = symbols
        self.connection = None
        self.response = None
        self.error = None

    def open(self):
        try:
            self.connection = http.client.HTTPConnection("127.0.0.1", BACKEND_PORT, timeout=10)
            symbols = ",".join(self.symbols)
            self.connection.request("GET", f"/api/stream?symbols={symbols}")
            self.response = self.connection.getresponse()
            if self.response.status != 200:
                raise RuntimeError(f"SSE HTTP {self.response.status}")
            self.connection.sock.settimeout(120)
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
                payload = json.loads(line[5:])
                at = datetime.fromisoformat(payload["at"].replace("Z", "+00:00"))
                latency = (datetime.now(timezone.utc) - at).total_seconds() * 1000
                if latency >= 0:
                    with EVENT_LOCK:
                        EVENT_LATENCIES_MS.append(latency)
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
        if sock:
            sock.close()


def percentile(values, percentile_value):
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, math.ceil(percentile_value * len(ordered)) - 1)
    return round(ordered[index], 2)


def process_sample(process):
    output = subprocess.check_output(
        ["ps", "-o", "%cpu=", "-o", "rss=", "-p", str(process.pid)], text=True
    ).strip().split()
    return float(output[0]), int(output[1]) / 1024


def wait_for_sse_cleanup(timeout=22):
    deadline = time.time() + timeout
    samples = parse_prometheus()
    while metric_sum(samples, "chipthrone_sse_connections") > 0 and time.time() < deadline:
        time.sleep(0.5)
        samples = parse_prometheus()
    return samples


def symbols_for(pattern, index):
    if pattern == "same_one":
        return ["100001"]
    if pattern == "distributed":
        first = index % 20 + 1
        second = (index + 7) % 20 + 1
        return [f"100{first:03d}", f"100{second:03d}"]
    return ["100001", "100002"]


def backend_process(demand_enabled, mode, log_file, us_market):
    config_locations = f"file:{ROOT / 'loadtest/application-loadtest.yml'}"
    if us_market:
        config_locations += f",file:{ROOT / 'loadtest/application-loadtest-us.yml'}"
    command = [
        "java",
        "-Xms128m",
        "-Xmx512m",
        "-jar",
        str(JAR),
        "--spring.profiles.active=loadtest",
        f"--spring.config.additional-location={config_locations}",
        f"--chipthrone.demand.enabled={str(demand_enabled).lower()}",
        f"--chipthrone.loadtest.market-mode={mode}",
        f"--chipthrone.loadtest.us-market-open={str(us_market).lower()}",
    ]
    if us_market:
        command += [
            f"--alpaca.snapshots-url=http://127.0.0.1:{FAKE_PORT}/v2/stocks/snapshots",
            "--alpaca.api-key=fake-loadtest-key",
            "--alpaca.api-secret=fake-loadtest-secret",
        ]
    return subprocess.Popen(command, cwd=ROOT, stdout=log_file, stderr=subprocess.STDOUT)


def run_scenario(name, demand_enabled, mode, pattern, duration, disconnect_check=False, us_market=False):
    global EVENT_LATENCIES_MS
    EVENT_LATENCIES_MS = []
    request_json(f"http://127.0.0.1:{FAKE_PORT}/__reset", method="POST")
    with tempfile.NamedTemporaryFile(mode="w+") as log_file:
        backend = backend_process(demand_enabled, mode, log_file, us_market)
        clients = []
        try:
            wait_ready(f"http://127.0.0.1:{BACKEND_PORT}/api/health", backend)
            with ThreadPoolExecutor(max_workers=50) as pool:
                clients = list(pool.map(lambda index: SseClient(symbols_for(pattern, index)).open(), range(USERS)))
            errors = sum(client.error is not None for client in clients)
            cpu_samples = []
            rss_samples = []
            max_connections = 0
            max_active_symbols = 0
            deadline = time.time() + duration
            while time.time() < deadline:
                cpu, rss = process_sample(backend)
                cpu_samples.append(cpu)
                rss_samples.append(rss)
                metrics = parse_prometheus()
                max_connections = max(max_connections, int(metric_sum(metrics, "chipthrone_sse_connections")))
                max_active_symbols = max(max_active_symbols, int(metric_sum(metrics, "chipthrone_quote_active_symbols")))
                time.sleep(1)

            before_close = parse_prometheus()
            connected_counts = request_json(f"http://127.0.0.1:{FAKE_PORT}/__counts")
            subscriber_before = metric_sum(before_close, "chipthrone_quote_symbol_subscribers")
            for client in clients:
                client.close()
            after_close = wait_for_sse_cleanup()
            subscriber_after = metric_sum(after_close, "chipthrone_quote_symbol_subscribers")

            unnecessary_after_grace = None
            calls_during_grace = None
            remaining_active = int(metric_sum(after_close, "chipthrone_quote_active_symbols"))
            if disconnect_check:
                counts_after_disconnect = request_json(f"http://127.0.0.1:{FAKE_PORT}/__counts")
                time.sleep(16)
                counts_at_grace = request_json(f"http://127.0.0.1:{FAKE_PORT}/__counts")
                time.sleep(7)
                counts_after_grace = request_json(f"http://127.0.0.1:{FAKE_PORT}/__counts")
                calls_during_grace = sum(counts_at_grace.values()) - sum(counts_after_disconnect.values())
                unnecessary_after_grace = sum(counts_after_grace.values()) - sum(counts_at_grace.values())
                after_grace_metrics = parse_prometheus()
                remaining_active = int(metric_sum(after_grace_metrics, "chipthrone_quote_active_symbols"))

            poll_failures = int(metric_sum(
                before_close,
                "chipthrone_quote_polls_total",
                ('result="failure"',),
            ))
            with EVENT_LOCK:
                latencies = list(EVENT_LATENCIES_MS)
            return {
                "scenario": name,
                "users": USERS,
                "selection_pattern": pattern,
                "market_mode": mode,
                "duration_seconds": duration,
                "max_sse_connections": max_connections,
                "active_unique_symbols": max_active_symbols,
                "fake_external_calls": connected_counts,
                "kis_current_calls": connected_counts.get("kis_current", 0),
                "hyperliquid_batch_calls": connected_counts.get("hyperliquid_batch", 0),
                "alpaca_batch_calls": connected_counts.get("alpaca_batch", 0),
                "cpu_percent_mean": round(statistics.mean(cpu_samples), 2),
                "cpu_percent_max": round(max(cpu_samples), 2),
                "rss_mib_mean": round(statistics.mean(rss_samples), 2),
                "rss_mib_max": round(max(rss_samples), 2),
                "delivery_latency_p95_ms": percentile(latencies, 0.95),
                "events_received": len(latencies),
                "connection_error_rate": round(errors / USERS, 4),
                "poll_failures": poll_failures,
                "cleaned_subscriptions": int(subscriber_before - subscriber_after),
                "remaining_active_symbols": remaining_active,
                "calls_during_disconnect_grace": calls_during_grace,
                "unnecessary_calls_after_grace": unnecessary_after_grace,
            }
        except Exception:
            log_file.seek(0)
            sys.stderr.write(log_file.read())
            raise
        finally:
            for client in clients:
                client.close()
            backend.send_signal(signal.SIGTERM)
            try:
                backend.wait(timeout=10)
            except subprocess.TimeoutExpired:
                backend.kill()


def main():
    if not JAR.exists():
        raise SystemExit(f"missing {JAR}; run: cd backend && ./gradlew bootJar")
    fake = ThreadingHTTPServer(("127.0.0.1", FAKE_PORT), Handler)
    threading.Thread(target=fake.serve_forever, daemon=True).start()
    try:
        scenarios = [
            ("fixed_all_symbols", False, "REGULAR", "popular_pair", 12, False, False),
            ("active_symbols_only", True, "REGULAR", "popular_pair", 12, False, False),
            ("shared_single_symbol", True, "REGULAR", "same_one", 12, False, False),
            ("distributed_symbols", True, "REGULAR", "distributed", 12, False, False),
            ("all_disconnected", True, "REGULAR", "popular_pair", 6, True, False),
            ("closed_market_low_frequency", True, "ESTIMATE", "popular_pair", 65, False, False),
            ("us_shared_symbols_alpaca_batch", True, "ESTIMATE", "popular_pair", 12, False, True),
        ]
        selected_scenarios = set(filter(None, os.getenv("LOADTEST_SCENARIOS", "").split(",")))
        if selected_scenarios:
            scenarios = [scenario for scenario in scenarios if scenario[0] in selected_scenarios]
        results = []
        for scenario in scenarios:
            print(f"START {scenario[0]}", flush=True)
            result = run_scenario(*scenario)
            results.append(result)
            print("RESULT " + json.dumps(result, ensure_ascii=False), flush=True)
        print(json.dumps({
            "measured_at": datetime.now(timezone.utc).isoformat(),
            "host": os.uname().sysname + " " + os.uname().machine,
            "supported_symbols": 20,
            "results": results,
        }, ensure_ascii=False, indent=2))
    finally:
        fake.shutdown()
        fake.server_close()


if __name__ == "__main__":
    main()
