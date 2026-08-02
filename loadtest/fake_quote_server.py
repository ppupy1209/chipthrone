#!/usr/bin/env python3
"""Hyperliquid/금융위원회/수출입은행 호환 Fake 서버와 정확한 호출 카운터."""

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
        length = int(self.headers.get("Content-Length", "0"))
        self.rfile.read(length)
        if path == "/info":
            increment("hyperliquid_batch")
            contexts = []
            for index, symbol in enumerate(UNIVERSE, 1):
                mark, previous = USD_PRICES.get(symbol, (100 + index, 99 + index))
                contexts.append({"markPx": str(mark), "prevDayPx": str(previous)})
            return self.json_response([{"universe": [{"name": symbol} for symbol in UNIVERSE]}, contexts])
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
        if parsed.path == "/exchangeJSON":
            increment("korea_exim_fx")
            return self.json_response([{"cur_unit": "USD", "deal_bas_r": "1,450.00"}])
        if parsed.path == "/getStockPriceInfo":
            increment("fsc_daily_stock")
            code = query.get("srtnCd", ["100001"])[0]
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
