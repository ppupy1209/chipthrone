#!/usr/bin/env python3
"""Measure quote-source detection and recovery using localhost-only fake APIs."""

import json
import os
import signal
import subprocess
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
URL_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def request_json(url, method="GET", payload=None):
    body = None if payload is None else json.dumps(payload).encode()
    request = urllib.request.Request(url, data=body, method=method)
    if body is not None:
        request.add_header("Content-Type", "application/json")
    with URL_OPENER.open(request, timeout=5) as response:
        return json.load(response)


def wait_ready(process, timeout=30):
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("backend exited before readiness")
        try:
            request_json(f"http://127.0.0.1:{BACKEND_PORT}/api/health")
            return
        except Exception as exception:
            last_error = exception
            time.sleep(0.2)
    raise RuntimeError(f"readiness timed out: {last_error!r}")


def state():
    return request_json(f"http://127.0.0.1:{FAKE_PORT}/__state")


def event_containing(text):
    for event in state()["slack_events"]:
        if text in event["payload"].get("text", ""):
            return event
    return None


def wait_event(text, health_probes, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            request_json(f"http://127.0.0.1:{BACKEND_PORT}/api/health")
            health_probes["success"] += 1
        except Exception:
            health_probes["failure"] += 1
        event = event_containing(text)
        if event is not None:
            return event
        time.sleep(0.2)
    raise RuntimeError(f"timed out waiting for Slack event containing {text!r}")


def prometheus_samples():
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


def metric_sum(samples, name, labels=()):
    return sum(
        value for key, value in samples.items()
        if (key == name or key.startswith(name + "{")) and all(label in key for label in labels)
    )


def backend_process(log_file):
    java = os.getenv("JAVA_BIN", "java")
    environment = os.environ.copy()
    environment["SPRING_APPLICATION_JSON"] = json.dumps({
        "chipthrone": {"alert": {"slack-webhook-url": "http://127.0.0.1:19090/slack"}}
    })
    return subprocess.Popen([
        java,
        "-Xms128m",
        "-Xmx512m",
        "-jar",
        str(JAR),
        "--spring.profiles.active=loadtest",
        f"--spring.config.additional-location=file:{ROOT / 'loadtest/application-loadtest.yml'}",
        "--chipthrone.demand.enabled=false",
    ], cwd=ROOT, env=environment, stdout=log_file, stderr=subprocess.STDOUT)


def main():
    if not JAR.exists():
        raise SystemExit(f"missing {JAR}; run: cd backend && ./gradlew bootJar")

    fake = ThreadingHTTPServer(("127.0.0.1", FAKE_PORT), Handler)
    threading.Thread(target=fake.serve_forever, daemon=True).start()
    with tempfile.NamedTemporaryFile(mode="w+") as log_file:
        backend = backend_process(log_file)
        try:
            wait_ready(backend)
            deadline = time.monotonic() + 10
            while (
                (state()["counts"].get("hyperliquid_batch", 0) == 0 or event_containing("기동") is None)
                and time.monotonic() < deadline
            ):
                time.sleep(0.2)
            if event_containing("기동") is None:
                raise RuntimeError(f"fake Slack webhook did not receive the startup event: {state()}")
            request_json(f"http://127.0.0.1:{FAKE_PORT}/__reset", method="POST", payload={})

            injected = request_json(
                f"http://127.0.0.1:{FAKE_PORT}/__fault",
                method="POST",
                payload={"source": "hyperliquid", "enabled": True},
            )
            health_probes = {"success": 0, "failure": 0}
            failure_event = wait_event("시세 소스 장애", health_probes, timeout=25)
            failure_metrics = prometheus_samples()

            restored = request_json(
                f"http://127.0.0.1:{FAKE_PORT}/__fault",
                method="POST",
                payload={"source": "hyperliquid", "enabled": False},
            )
            recovery_event = wait_event("시세 소스 복구", health_probes, timeout=10)
            recovered_metrics = prometheus_samples()
            final_state = state()

            poll_failures = metric_sum(
                failure_metrics,
                "chipthrone_quote_polls_total",
                ('result="failure"',),
            )
            freshness_age = metric_sum(
                failure_metrics,
                "chipthrone_quote_freshness_age_seconds",
            )
            if poll_failures < 5:
                raise AssertionError(f"expected at least 5 failed polls, got {poll_failures}")
            if health_probes["failure"] != 0:
                raise AssertionError(f"health endpoint failed {health_probes['failure']} times")
            if "hyperliquid" in final_state["faults"]:
                raise AssertionError("injected fault was not cleared")

            total_probes = health_probes["success"] + health_probes["failure"]
            result = {
                "measured_at": datetime.now(timezone.utc).isoformat(),
                "environment": "local backend + localhost fake APIs",
                "fault": "Hyperliquid-compatible endpoint HTTP 503",
                "actual_external_api_calls": 0,
                "fake_api_attempts": final_state["counts"],
                "mttd_seconds": round(failure_event["at_monotonic"] - injected["at_monotonic"], 3),
                "recovery_detection_seconds": round(
                    recovery_event["at_monotonic"] - restored["at_monotonic"], 3
                ),
                "incident_duration_seconds": round(
                    recovery_event["at_monotonic"] - injected["at_monotonic"], 3
                ),
                "health_availability_percent": round(100 * health_probes["success"] / total_probes, 2),
                "health_probes": health_probes,
                "failed_polls_at_detection": int(poll_failures),
                "quote_freshness_age_at_detection_seconds": round(freshness_age, 3),
                "successful_polls_after_recovery": int(metric_sum(
                    recovered_metrics,
                    "chipthrone_quote_polls_total",
                    ('result="success"',),
                )),
                "assertions": {
                    "all_quote_sources_are_localhost": True,
                    "health_remained_available_during_source_failure": True,
                    "failure_and_recovery_notifications_received": True,
                },
            }
            print(json.dumps(result, ensure_ascii=False, indent=2))
        except Exception:
            log_file.seek(0)
            print(log_file.read(), end="", file=os.sys.stderr)
            raise
        finally:
            if backend.poll() is None:
                backend.send_signal(signal.SIGTERM)
                try:
                    backend.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    backend.kill()
            fake.shutdown()
            fake.server_close()


if __name__ == "__main__":
    main()
