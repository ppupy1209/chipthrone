# API 레퍼런스

베이스 URL은 운영 `https://api.chipthrone.com`, 로컬 `http://localhost:8080`이다.

## GET /api/health

시세 구독자가 없어 수집이 멈춰도 Health API는 정상 응답한다.

```json
{"status":"UP","service":"chipthrone-api","version":"0.0.1","serverTime":"2026-08-02T06:00:00Z"}
```

## GET /api/assets

백엔드가 허용하는 종목 목록이다. UI 카테고리와 Local Storage 값은 이 목록으로 재검증한다.

```json
[
  {"code":"005930","name":"삼성전자","market":"KRX"},
  {"code":"NVDA","name":"엔비디아","market":"US"}
]
```

## GET /api/quotes?symbols=005930,000660

선택 종목의 마지막 스냅샷을 한 번 반환한다. 아직 수집값이 없으면 503이다.

```json
{
  "mode": "ESTIMATE",
  "at": "2026-08-02T06:00:00Z",
  "fxRate": 1450.0,
  "fxAsOfDate": "2026-08-01",
  "fxSource": "PYTH",
  "fxFetchedAt": "2026-08-02T05:41:12Z",
  "stocks": [
    {
      "code": "005930",
      "name": "삼성전자",
      "priceKrw": 246500.0,
      "priceUsd": 170.0,
      "changePct": -1.73,
      "sharesOutstanding": 5919637922,
      "marketCap": 1459180747773000.0,
      "officialMarketCap": 1553904954525000.0,
      "regularClose": 262500.0,
      "regularCloseDate": "2026-07-31",
      "high": 267000.0,
      "nxtClose": null,
      "nxtCloseDate": null,
      "market": "KRX",
      "source": "HYPERLIQUID",
      "status": "ESTIMATE",
      "officialCloseEstimate": 264523.0,
      "officialDivergencePct": 0.77
    }
  ]
}
```

`marketCap`은 추정 가격 기준, `officialMarketCap`은 금융위원회 확정값이다. `changePct`는 Hyperliquid `prevDayPx` 기준이다.

`officialCloseEstimate`는 `regularCloseDate`의 정규장 마감(15:30 KST) 시각 추정가(원)이고, `officialDivergencePct`는 그 값과 `regularClose`의 괴리율이다. 확정 종가가 없으면 둘 다 null이다. `fxFetchedAt`은 환율을 마지막으로 조회한 시각으로, 설정 기준값이면 null이다.

## GET /api/stream?symbols=005930,000660

- `symbols` 필수
- 연결당 최대 16개(지원 종목 전체): 국내 핵심 2개 + 미국 14개
- 빈 목록, 미지원 코드, 최대 개수 초과는 400
- 같은 종목의 여러 연결은 동일 수집 결과를 공유
- 캐시가 있으면 연결 직후 1건 전송

```text
event:quotes
data:{...QuoteSnapshot...}

:keepalive
```

투자의견 API는 KIS 제거와 함께 삭제했다.
