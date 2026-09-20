#!/usr/bin/env python3
"""Run a production-shaped local SSE load while exporting probe metrics to Prometheus."""

import http.server
import json
import os
import signal
import subprocess
import sys
import tempfile
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

from fake_quote_server import Handler as FakeQuoteHandler
from fake_quote_server import ThreadingHTTPServer
from run_sse_thread_starvation_test import (
    BACKEND_PORT,
    FAKE_PORT,
    JAR,
    ROOT,
    StreamClient,
    close_all,
    percentile,
    request_bytes,
    stack_count,
    wait_ready,
)


TOMCAT_WORKERS = 200
BLOCKING_CLIENTS = 200
ASYNC_CLIENTS = 400
METRICS_PORT = 19100
PROBE_TIMEOUT_SECONDS = 0.5
PROBE_INTERVAL_SECONDS = 1.0
RAMP_INTERVAL_SECONDS = 1.0
PHASE_HOLD_SECONDS = 20
WARMUP_SECONDS = 10
RECOVERY_SECONDS = 15


class ProbeState:
    def __init__(self):
        self.lock = threading.Lock()
        self.phase = "warmup"
        self.target_connections = 0
        self.health_success = 1
        self.health_latency_seconds = 0.0
        self.history = []

    def set_phase(self, phase, target_connections=0):
        with self.lock:
            self.phase = phase
            self.target_connections = target_connections

    def set_target_connections(self, value):
        with self.lock:
            self.target_connections = value

    def record_probe(self, success, latency_seconds, error=None):
        now = time.time()
        with self.lock:
            self.health_success = int(success)
            self.health_latency_seconds = latency_seconds
            self.history.append({
                "at_epoch_seconds": now,
                "phase": self.phase,
                "success": success,
                "latency_ms": round(latency_seconds * 1000, 2),
                "error": error,
            })

    def prometheus(self):
        with self.lock:
            phase = self.phase
            target_connections = self.target_connections
            success = self.health_success
            latency = self.health_latency_seconds
        phases = ("warmup", "blocking", "recovery", "async", "complete")
        lines = [
            "# HELP chipthrone_lab_phase Current load-test phase.",
            "# TYPE chipthrone_lab_phase gauge",
        ]
        lines.extend(
            f'chipthrone_lab_phase{{phase="{candidate}"}} {1 if candidate == phase else 0}'
            for candidate in phases
        )
        lines.extend([
            "# HELP chipthrone_lab_target_sse_connections Connections opened by the load generator.",
            "# TYPE chipthrone_lab_target_sse_connections gauge",
            f"chipthrone_lab_target_sse_connections {target_connections}",
            "# HELP chipthrone_lab_health_probe_success Whether the last external health probe succeeded.",
            "# TYPE chipthrone_lab_health_probe_success gauge",
            f"chipthrone_lab_health_probe_success {success}",
            "# HELP chipthrone_lab_health_probe_latency_seconds Last external health probe latency.",
            "# TYPE chipthrone_lab_health_probe_latency_seconds gauge",
            f"chipthrone_lab_health_probe_latency_seconds {latency}",
        ])
        return "\n".join(lines) + "\n"


PROBE_STATE = ProbeState()


class MetricsHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/metrics":
            self.send_error(404)
            return
        body = PROBE_STATE.prometheus().encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; version=0.0.4")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, _format, *_args):
        pass


def start_backend(log_file):
    command = [
        "java",
        "-Xms128m",
        "-Xmx512m",
        "-jar",
        str(JAR),
        "--spring.profiles.active=sse-thread-lab",
        f"--spring.config.additional-location=file:{ROOT / 'loadtest/application-loadtest.yml'}",
        f"--server.tomcat.threads.max={TOMCAT_WORKERS}",
        f"--server.tomcat.threads.min-spare={TOMCAT_WORKERS}",
        "--server.tomcat.accept-count=500",
        "--server.tomcat.mbeanregistry.enabled=true",
    ]
    return subprocess.Popen(command, cwd=ROOT, stdout=log_file, stderr=subprocess.STDOUT)


def run_probe(stop_event):
    while not stop_event.is_set():
        started = time.perf_counter()
        try:
            request_bytes(
                f"http://127.0.0.1:{BACKEND_PORT}/api/health",
                PROBE_TIMEOUT_SECONDS,
            )
            PROBE_STATE.record_probe(True, time.perf_counter() - started)
        except Exception as exception:
            PROBE_STATE.record_probe(
                False,
                time.perf_counter() - started,
                type(exception).__name__,
            )
        stop_event.wait(PROBE_INTERVAL_SECONDS)


def open_batch(path, count):
    with ThreadPoolExecutor(max_workers=count) as pool:
        return list(pool.map(lambda _index: StreamClient(path).open(), range(count)))


def ramp_connections(path, total, batch_size):
    clients = []
    while len(clients) < total:
        count = min(batch_size, total - len(clients))
        clients.extend(open_batch(path, count))
        PROBE_STATE.set_target_connections(len(clients))
        print(f"RAMP phase={PROBE_STATE.phase} connections={len(clients)}", flush=True)
        if len(clients) < total:
            time.sleep(RAMP_INTERVAL_SECONDS)
    return clients


def phase_summary(history, phase):
    samples = [sample for sample in history if sample["phase"] == phase]
    successes = [sample["latency_ms"] for sample in samples if sample["success"]]
    return {
        "probe_attempts": len(samples),
        "probe_successes": len(successes),
        "probe_failures": len(samples) - len(successes),
        "success_rate": round(len(successes) / len(samples), 4) if samples else None,
        "latency_p50_ms": percentile(successes, 0.50),
        "latency_p95_ms": percentile(successes, 0.95),
        "latency_max_ms": round(max(successes), 2) if successes else None,
        "failure_types": sorted({sample["error"] for sample in samples if sample["error"]}),
    }


def main():
    if not JAR.exists():
        raise SystemExit(f"missing {JAR}; run: cd backend && ./gradlew bootJar")

    fake = ThreadingHTTPServer(("127.0.0.1", FAKE_PORT), FakeQuoteHandler)
    metrics = ThreadingHTTPServer(("0.0.0.0", METRICS_PORT), MetricsHandler)
    threading.Thread(target=fake.serve_forever, daemon=True).start()
    threading.Thread(target=metrics.serve_forever, daemon=True).start()

    with tempfile.NamedTemporaryFile(mode="w+") as log_file:
        backend = start_backend(log_file)
        blocking_clients = []
        async_clients = []
        probe_stop = threading.Event()
        probe_thread = threading.Thread(target=run_probe, args=(probe_stop,), daemon=True)
        try:
            wait_ready(backend)
            probe_thread.start()
            print(f"WARMUP seconds={WARMUP_SECONDS}", flush=True)
            time.sleep(WARMUP_SECONDS)

            PROBE_STATE.set_phase("blocking")
            blocking_clients = ramp_connections(
                "/__lab/blocking-stream?holdMs=60000",
                BLOCKING_CLIENTS,
                batch_size=25,
            )
            time.sleep(PHASE_HOLD_SECONDS)
            blocking_stack_count = stack_count(
                backend.pid,
                "BlockingSseLabController.blockingStream",
            )

            close_all(blocking_clients)
            blocking_clients = []
            PROBE_STATE.set_phase("recovery")
            PROBE_STATE.set_target_connections(0)
            wait_ready(backend, timeout=20)
            time.sleep(RECOVERY_SECONDS)

            PROBE_STATE.set_phase("async")
            async_clients = ramp_connections(
                "/api/stream?symbols=100001",
                ASYNC_CLIENTS,
                batch_size=50,
            )
            time.sleep(PHASE_HOLD_SECONDS)
            async_stack_count = stack_count(
                backend.pid,
                "BlockingSseLabController.blockingStream",
            )

            result = {
                "measured_at": datetime.now(timezone.utc).isoformat(),
                "host": os.uname().sysname + " " + os.uname().machine,
                "tomcat_max_threads": TOMCAT_WORKERS,
                "blocking_sse": {
                    "connected_clients": BLOCKING_CLIENTS,
                    "worker_stacks_in_blocking_handler": blocking_stack_count,
                    "health": phase_summary(PROBE_STATE.history, "blocking"),
                },
                "async_sse": {
                    "connected_clients": ASYNC_CLIENTS,
                    "worker_stacks_in_blocking_handler": async_stack_count,
                    "health": phase_summary(PROBE_STATE.history, "async"),
                },
                "probe_samples": list(PROBE_STATE.history),
            }
            output = ROOT / "docs/sse-capacity-load-results.json"
            output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            print("RESULT " + json.dumps(result, ensure_ascii=False), flush=True)

            PROBE_STATE.set_phase("complete")
            PROBE_STATE.set_target_connections(0)
            time.sleep(5)
        except Exception:
            log_file.seek(0)
            sys.stderr.write(log_file.read())
            raise
        finally:
            probe_stop.set()
            close_all(blocking_clients)
            close_all(async_clients)
            backend.send_signal(signal.SIGTERM)
            try:
                backend.wait(timeout=15)
            except subprocess.TimeoutExpired:
                backend.kill()
            fake.shutdown()
            fake.server_close()
            metrics.shutdown()
            metrics.server_close()


if __name__ == "__main__":
    main()
