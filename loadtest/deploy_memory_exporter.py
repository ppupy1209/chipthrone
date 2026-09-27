#!/usr/bin/env python3
"""Expose EC2 host and container memory during slot deployment as Prometheus text.

Runs on the EC2 host as root (reads /var/lib/docker). Standard library only, so it
does not add a dependency or a Docker CLI call per scrape to the host being measured.
"""

import glob
import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer

CGROUP_GLOB = "/sys/fs/cgroup/system.slice/docker-*.scope"
DOCKER_CONTAINERS = "/var/lib/docker/containers"


def meminfo():
    values = {}
    with open("/proc/meminfo") as f:
        for line in f:
            key, rest = line.split(":", 1)
            values[key] = int(rest.split()[0]) * 1024
    return values


def vmstat(key):
    with open("/proc/vmstat") as f:
        for line in f:
            name, value = line.split()
            if name == key:
                return int(value)
    return 0


def container_name(container_id):
    try:
        with open(os.path.join(DOCKER_CONTAINERS, container_id, "config.v2.json")) as f:
            return json.load(f)["Name"].lstrip("/")
    except (OSError, ValueError, KeyError):
        return container_id[:12]


def read_int(path):
    with open(path) as f:
        return int(f.read().strip())


def memory_stat(path):
    stat = {}
    with open(path) as f:
        for line in f:
            key, value = line.split()
            stat[key] = int(value)
    return stat


def process_rss(comm):
    total = 0
    for status in glob.glob("/proc/[0-9]*/status"):
        try:
            with open(status) as f:
                text = f.read()
        except OSError:
            continue
        if f"Name:\t{comm}\n" not in text:
            continue
        for line in text.splitlines():
            if line.startswith("VmRSS:"):
                total += int(line.split()[1]) * 1024
    return total


def render():
    info = meminfo()
    lines = [
        f"deploy_host_memory_total_bytes {info['MemTotal']}",
        f"deploy_host_memory_available_bytes {info['MemAvailable']}",
        f"deploy_host_memory_free_bytes {info['MemFree']}",
        f"deploy_host_swap_total_bytes {info['SwapTotal']}",
        f"deploy_host_oom_kill_total {vmstat('oom_kill')}",
        f"deploy_nginx_rss_bytes {process_rss('nginx')}",
    ]
    for scope in glob.glob(CGROUP_GLOB):
        container_id = os.path.basename(scope)[len("docker-"):-len(".scope")]
        name = container_name(container_id)
        if not name.startswith("chipthrone-api"):
            continue
        try:
            current = read_int(os.path.join(scope, "memory.current"))
            stat = memory_stat(os.path.join(scope, "memory.stat"))
        except OSError:
            continue
        label = f'container="{name}"'
        lines.append(f"deploy_container_memory_current_bytes{{{label}}} {current}")
        lines.append(f"deploy_container_memory_anon_bytes{{{label}}} {stat.get('anon', 0)}")
        lines.append(f"deploy_container_memory_file_bytes{{{label}}} {stat.get('file', 0)}")
    return "\n".join(lines) + "\n"


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = render().encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; version=0.0.4")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", int(os.environ.get("PORT", "9101"))), Handler).serve_forever()
