#!/usr/bin/env python3
"""Reproduce Tomcat worker starvation and compare it with the real async SSE path."""

import argparse
import http.client
import json
import math
import os
import shutil
import signal
import socket
import statistics
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from fake_quote_server import Handler, ThreadingHTTPServer


ROOT = Path(__file__).resolve().parents[1]
JAR = next(
    (path for path in (ROOT / "backend/build/libs").glob("*.jar") if not path.name.endswith("-plain.jar")),
    ROOT / "backend/build/libs/chipthrone-api-0.0.1.jar",
)
BACKEND_PORT = 18080
FAKE_PORT = 19090
WORKERS = 8
URL_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def percentile(values, percentile_value):
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, math.ceil(percentile_value * len(ordered)) - 1)
    return round(ordered[index], 2)


def request_bytes(url, timeout):
    request = urllib.request.Request(url, headers={"Connection": "close"})
    with URL_OPENER.open(request, timeout=timeout) as response:
        return response.read()


def wait_ready(process, timeout=30):
    deadline = time.time() + timeout
    last_error = None
    while time.time() < deadline:
        if process.poll() is not None:
            raise RuntimeError("backend exited before readiness")
        try:
            request_bytes(f"http://127.0.0.1:{BACKEND_PORT}/api/health", 1)
            return
        except Exception as exception:
            last_error = exception
            time.sleep(0.2)
    raise RuntimeError(f"readiness timed out: {last_error!r}")


def probe_health(attempts, timeout):
    latencies = []
    failures = []
    for _ in range(attempts):
        started = time.perf_counter()
        try:
            request_bytes(f"http://127.0.0.1:{BACKEND_PORT}/api/health", timeout)
            latencies.append((time.perf_counter() - started) * 1000)
        except Exception as exception:
            failures.append(type(exception).__name__)
    return {
        "attempts": attempts,
        "successes": len(latencies),
        "timeouts_or_errors": len(failures),
        "latency_p50_ms": percentile(latencies, 0.50),
        "latency_p95_ms": percentile(latencies, 0.95),
        "latency_max_ms": round(max(latencies), 2) if latencies else None,
        "failure_types": sorted(set(failures)),
    }


class StreamClient:
    def __init__(self, path):
        self.path = path
        self.connection = None
        self.response = None

    def open(self):
        self.connection = http.client.HTTPConnection("127.0.0.1", BACKEND_PORT, timeout=5)
        self.connection.request("GET", self.path)
        self.response = self.connection.getresponse()
        if self.response.status != 200:
            raise RuntimeError(f"stream HTTP {self.response.status}: {self.path}")
        first_line = self.response.readline()
        if not first_line:
            raise RuntimeError(f"stream closed before first event: {self.path}")
        if self.connection.sock:
            self.connection.sock.settimeout(30)
        return self

    def close(self):
        sock = self.connection.sock if self.connection else None
        if sock:
            try:
                sock.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
        if self.response:
            try:
                self.response.close()
            except OSError:
                pass
        if sock:
            sock.close()


def stack_count(process_id, method_name):
    java_home = os.environ.get("JAVA_HOME")
    jcmd = Path(java_home) / "bin/jcmd" if java_home else None
    executable = str(jcmd) if jcmd and jcmd.exists() else shutil.which("jcmd")
    if not executable:
        raise RuntimeError("jcmd not found; set JAVA_HOME to a JDK 21 installation")
    dump = subprocess.check_output(
        [executable, str(process_id), "Thread.print"],
        text=True,
        stderr=subprocess.STDOUT,
    )
    return dump.count(method_name)


def prometheus_value(metric_name):
    text = request_bytes(f"http://127.0.0.1:{BACKEND_PORT}/actuator/prometheus", 2).decode()
    values = []
    for line in text.splitlines():
        if line.startswith(metric_name + "{") or line.startswith(metric_name + " "):
            values.append(float(line.rsplit(" ", 1)[1]))
    return round(sum(values), 2)


def start_backend(log_file):
    command = [
        "java",
        "-Xms128m",
        "-Xmx256m",
        "-jar",
        str(JAR),
        "--spring.profiles.active=sse-thread-lab",
        f"--spring.config.additional-location=file:{ROOT / 'loadtest/application-loadtest.yml'}",
        f"--server.tomcat.threads.max={WORKERS}",
        f"--server.tomcat.threads.min-spare={WORKERS}",
        "--server.tomcat.accept-count=100",
    ]
    return subprocess.Popen(command, cwd=ROOT, stdout=log_file, stderr=subprocess.STDOUT)


def close_all(clients):
    for client in clients:
        client.close()


def run_experiment():
    if not JAR.exists():
        raise SystemExit(f"missing {JAR}; run: cd backend && ./gradlew bootJar")

    fake = ThreadingHTTPServer(("127.0.0.1", FAKE_PORT), Handler)
    threading.Thread(target=fake.serve_forever, daemon=True).start()
    with tempfile.NamedTemporaryFile(mode="w+") as log_file:
        backend = start_backend(log_file)
        blocking_clients = []
        async_clients = []
        try:
            wait_ready(backend)

            blocking_clients = [
                StreamClient("/__lab/blocking-stream?holdMs=20000").open()
                for _ in range(WORKERS)
            ]
            time.sleep(0.3)
            blocking_stack_count = stack_count(
                backend.pid,
                "BlockingSseLabController.blockingStream",
            )
            blocking_health = probe_health(attempts=5, timeout=0.35)

            close_all(blocking_clients)
            blocking_clients = []
            wait_ready(backend, timeout=10)

            async_clients = [
                StreamClient("/api/stream?symbols=100001").open()
                for _ in range(WORKERS)
            ]
            time.sleep(0.3)
            async_stack_count = stack_count(
                backend.pid,
                "BlockingSseLabController.blockingStream",
            )
            async_connections = int(prometheus_value("chipthrone_sse_connections"))
            async_health = probe_health(attempts=20, timeout=1)

            return {
                "measured_at": datetime.now(timezone.utc).isoformat(),
                "host": os.uname().sysname + " " + os.uname().machine,
                "tomcat_max_threads": WORKERS,
                "blocking_sse": {
                    "connected_clients": WORKERS,
                    "worker_stacks_in_blocking_handler": blocking_stack_count,
                    "health": blocking_health,
                },
                "async_sse": {
                    "connected_clients": async_connections,
                    "worker_stacks_in_blocking_handler": async_stack_count,
                    "health": async_health,
                },
            }
        except Exception:
            log_file.seek(0)
            sys.stderr.write(log_file.read())
            raise
        finally:
            close_all(blocking_clients)
            close_all(async_clients)
            backend.send_signal(signal.SIGTERM)
            try:
                backend.wait(timeout=10)
            except subprocess.TimeoutExpired:
                backend.kill()
            fake.shutdown()
            fake.server_close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        type=Path,
        default=ROOT / "docs/sse-thread-starvation-results.json",
    )
    args = parser.parse_args()
    result = run_experiment()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
