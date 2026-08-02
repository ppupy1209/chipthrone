#!/usr/bin/env python3
"""KIS/Hyperliquid/FX compatible local server with exact request counters."""

import json
import os
import threading
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


COUNTS = Counter()
LOCK = threading.Lock()
UNIVERSE = (
    ["xyz:SMSN", "xyz:SKHX"]
    + [f"xyz:S{i:04d}" for i in range(1, 21)]
    + ["xyz:SNDK", "xyz:MU", "xyz:AVGO"]
)


def increment(name):
    with LOCK:
        COUNTS[name] += 1


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        path = urlparse(self.path).path
        length = int(self.headers.get("Content-Length", "0"))
        self.rfile.read(length)
        if path == "/info":
            increment("hyperliquid_batch")
            contexts = [
                {"markPx": str(100 + index), "prevDayPx": str(99 + index)}
                for index in range(1, len(UNIVERSE) + 1)
            ]
            return self.json_response([{"universe": [{"name": symbol} for symbol in UNIVERSE]}, contexts])
        if path == "/oauth2/tokenP":
            increment("kis_token")
            return self.json_response({"access_token": "fake-token", "expires_in": 86400})
        if path == "/__reset":
            with LOCK:
                COUNTS.clear()
            return self.json_response({"ok": True})
        return self.send_error(404)

    def do_GET(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        if parsed.path == "/__counts":
            with LOCK:
                return self.json_response(dict(COUNTS))
        if parsed.path == "/fx":
            increment("fx")
            return self.json_response({"rates": {"KRW": 1450}})
        if parsed.path == "/v2/stocks/snapshots":
            increment("alpaca_batch")
            symbols = query.get("symbols", [""])[0].split(",")
            return self.json_response({
                "snapshots": {
                    symbol: {
                        "latestTrade": {"p": 80 + index * 20},
                        "dailyBar": {"c": 80 + index * 20},
                        "prevDailyBar": {"c": 78 + index * 20},
                    }
                    for index, symbol in enumerate(symbols)
                    if symbol
                }
            })
        if parsed.path.endswith("/inquire-price"):
            increment("kis_current")
            code = query.get("FID_INPUT_ISCD", ["100001"])[0]
            price = 100000 + int(code[-2:]) * 100
            return self.json_response({
                "output": {
                    "stck_prpr": str(price),
                    "prdy_ctrt": "1.25",
                    "stck_prdy_clpr": str(price - 1000),
                }
            })
        if parsed.path.endswith("/inquire-daily-price"):
            division = query.get("FID_COND_MRKT_DIV_CODE", ["J"])[0]
            increment("kis_nxt_close" if division == "NX" else "kis_regular_close")
            code = query.get("FID_INPUT_ISCD", ["100001"])[0]
            price = 99000 + int(code[-2:]) * 100
            return self.json_response({
                "output": [{
                    "stck_bsop_date": "20260731",
                    "stck_clpr": str(price),
                    "stck_hgpr": str(price + 1500),
                }]
            })
        if parsed.path.endswith("/invest-opinion"):
            increment("kis_opinion")
            return self.json_response({"output": []})
        return self.send_error(404)

    def json_response(self, value):
        body = json.dumps(value, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True

    def log_message(self, _format, *_args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer((os.getenv("FAKE_QUOTE_HOST", "127.0.0.1"), 19090), Handler).serve_forever()
