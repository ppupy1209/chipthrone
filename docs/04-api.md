# API 레퍼런스

베이스: `https://api.chipthrone.com` (로컬 `http://localhost:8080`). CORS 허용: `localhost:5173`, `*.chipthrone.com`, `*.vercel.app`.

## GET /api/health

```json
{ "status": "UP", "service": "chipthrone-api", "version": "0.0.1", "serverTime": "2026-06-21T07:00:00Z" }
```

## GET /api/quotes

최신 스냅샷 1회. 스냅샷이 아직 없으면 503.

```json
{
  "mode": "ESTIMATE",                 // PREMARKET | REGULAR | NXT | ESTIMATE
  "at": "2026-06-21T07:00:00Z",       // ISO-8601(UTC)
  "fxRate": 1531.0,                   // USD/KRW
  "stocks": [
    {
      "code": "005930",
      "name": "삼성전자",
      "priceKrw": 369354.8,           // 모드별 실제가 또는 추정가(원)
      "priceUsd": 241.25,             // perp USD(추정) 또는 원화/환율
      "changePct": 5.38,              // 백엔드 기준 등락률
      "sharesOutstanding": 5919637922,
      "marketCap": 2.1864e15,         // priceKrw × 상장주식수
      "regularClose": 354000.0,       // 정규장 종가(KIS), 미연동 시 null
      "regularCloseDate": "2026-06-19",
      "high": 357000.0,               // 정규장 고가(KIS), null 가능
      "nxtClose": 350500.0,           // 애프터마켓(NXT) 종가, null 가능
      "nxtCloseDate": "2026-06-19"
    }
    // ... SK하이닉스(000660)
  ]
}
```

프론트는 등락률/등락금액을 종가(정규장 종가 우선) 대비로 직접 계산해 표시한다.

## GET /api/stream

`text/event-stream`(SSE). 폴링 갱신마다 `quotes` 이벤트로 위 스냅샷을 push. 구독 즉시 현재 스냅샷 1건 전송.

```
event:quotes
data:{ ...QuoteSnapshot... }
```

## GET /api/opinions

증권사 투자의견 컨센서스(KIS 종목투자의견). 하루 1회 캐시.

```json
{
  "asOf": "2026-06-22",
  "stocks": [
    {
      "code": "005930",
      "name": "삼성전자",
      "consensus": {
        "avgTargetPrice": 95000.0,
        "institutionCount": 12,
        "buy": 10, "hold": 2, "sell": 0,
        "score": 4.2            // 강력매도1 ~ 강력매수5 평균
      },
      "reports": [
        { "date": "2026-06-20", "broker": "○○증권", "opinion": "매수", "prevOpinion": "매수", "targetPrice": 96000.0 }
      ]
    }
    // ... SK하이닉스(000660)
  ]
}
```

> KIS 키 미연동 시 KIS 의존 필드(regularClose/high/nxtClose/opinions)는 null/빈 값이며, 시세는 Hyperliquid 추정으로 폴백한다.
