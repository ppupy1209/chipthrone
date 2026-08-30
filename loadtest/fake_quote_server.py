#!/usr/bin/env python3
"""Hyperliquid/금융위원회/업비트 호환 Fake 서버와 정확한 호출 카운터."""

import json
import os
import threading
import time
from collections import Counter
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


COUNTS = Counter()
FAULTS = set()
SLACK_EVENTS = []
LOCK = threading.Lock()
UNIVERSE = (
    ["xyz:SMSN", "xyz:SKHX"]
    + [f"xyz:S{i:04d}" for i in range(1, 21)]
    + [
        "xyz:SNDK", "xyz:MU", "xyz:AVGO", "xyz:AMD", "xyz:ASML", "xyz:AAPL",
        "xyz:MSFT", "xyz:GOOGL", "xyz:AMZN", "xyz:NVDA", "xyz:META", "xyz:TSLA",
        "xyz:PLTR", "xyz:TSM", "xyz:SKHY",
    ]
)

USD_PRICES = {
    "xyz:SMSN": (170, 168),
    "xyz:SKHX": (1110, 1090),
    "xyz:SNDK": (81, 79),
    "xyz:MU": (155, 151),
    "xyz:AVGO": (310, 305),
    "xyz:AMD": (205, 200),
    "xyz:ASML": (1380, 1350),
    "xyz:AAPL": (230, 228),
    "xyz:MSFT": (520, 515),
    "xyz:GOOGL": (210, 207),
    "xyz:AMZN": (235, 232),
    "xyz:NVDA": (185, 180),
    "xyz:META": (780, 770),
    "xyz:TSLA": (410, 400),
    "xyz:PLTR": (165, 160),
    "xyz:TSM": (245, 240),
    "xyz:SKHY": (42, 40),
}


def increment(name):
    with LOCK:
        COUNTS[name] += 1


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        path = urlparse(self.path).path
        body = self.request_body()
        if path == "/info":
            increment("hyperliquid_batch")
            with LOCK:
                failing = "hyperliquid" in FAULTS
            if failing:
                return self.json_response({"error": "injected hyperliquid failure"}, status=503)
            contexts = []
            for index, symbol in enumerate(UNIVERSE, 1):
                mark, previous = USD_PRICES.get(symbol, (100 + index, 99 + index))
                contexts.append({"markPx": str(mark), "prevDayPx": str(previous)})
            return self.json_response([{"universe": [{"name": symbol} for symbol in UNIVERSE]}, contexts])
        if path == "/__reset":
            with LOCK:
                COUNTS.clear()
                FAULTS.clear()
                SLACK_EVENTS.clear()
            return self.json_response({"ok": True, "at_monotonic": time.monotonic()})
        if path == "/__fault":
            request = json.loads(body or b"{}")
            if request.get("source") != "hyperliquid" or not isinstance(request.get("enabled"), bool):
                return self.json_response({"error": "source=hyperliquid and boolean enabled are required"}, status=400)
            changed_at = time.monotonic()
            with LOCK:
                if request["enabled"]:
                    FAULTS.add("hyperliquid")
                else:
                    FAULTS.discard("hyperliquid")
            return self.json_response({"ok": True, "enabled": request["enabled"], "at_monotonic": changed_at})
        if path == "/slack":
            event = {"at_monotonic": time.monotonic(), "payload": json.loads(body or b"{}")}
            with LOCK:
                SLACK_EVENTS.append(event)
            return self.json_response({"ok": True})
        return self.send_error(404)

    def request_body(self):
        if "chunked" not in self.headers.get("Transfer-Encoding", "").lower():
            return self.rfile.read(int(self.headers.get("Content-Length", "0")))

        chunks = []
        while True:
            size = int(self.rfile.readline().split(b";", 1)[0].strip(), 16)
            if size == 0:
                self.rfile.readline()
                return b"".join(chunks)
            chunks.append(self.rfile.read(size))
            self.rfile.read(2)

    def do_GET(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        if parsed.path == "/__counts":
            with LOCK:
                return self.json_response(dict(COUNTS))
        if parsed.path == "/__state":
            with LOCK:
                return self.json_response({
                    "counts": dict(COUNTS),
                    "faults": sorted(FAULTS),
                    "slack_events": list(SLACK_EVENTS),
                })
        if parsed.path == "/v1/ticker":
            increment("upbit_fx")
            return self.json_response([{
                "market": "KRW-USDC",
                "trade_price": 1450.0,
                "trade_timestamp": int(time.time() * 1000),
            }])
        if parsed.path == "/v1/candles/minutes/1":
            increment("upbit_fx")
            requested_at = datetime.fromisoformat(query["to"][0].replace("Z", "+00:00"))
            traded_at = requested_at.astimezone(timezone.utc) - timedelta(minutes=1)
            return self.json_response([{
                "market": "KRW-USDC",
                "candle_date_time_utc": traded_at.replace(tzinfo=None).isoformat(timespec="seconds"),
                "trade_price": 1450.0,
                "timestamp": int(traded_at.timestamp() * 1000),
            }])
        if parsed.path == "/getStockPriceInfo":
            increment("fsc_daily_stock")
            # srtnCd는 실제 API가 무시한다. 클라이언트와 같이 likeSrtnCd를 본다.
            code = query.get("likeSrtnCd", ["100001"])[0]
            if code == "005930":
                close, shares = 262500, 5919637922
            elif code == "000660":
                close, shares = 1718000, 728002365
            else:
                close, shares = 100000 + int(code[-2:]) * 100, 1000000000
            return self.json_response({"response": {"body": {"items": {"item": [{
                "basDt": "20260731",
                "srtnCd": code,
                "clpr": str(close),
                "hipr": str(close + 1500),
                "lstgStCnt": str(shares),
                "mrktTotAmt": str(close * shares),
            }]}}}})
        return self.send_error(404)

    def json_response(self, value, status=200):
        body = json.dumps(value, ensure_ascii=False).encode()
        self.send_response(status)
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
