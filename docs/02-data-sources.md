# 데이터 소스 설계

chipthrone은 시간대에 따라 서로 다른 소스에서 시세를 가져온다. 핵심은 "실제가"와 "추정가"를 명확히 구분해 표시하는 것.

## 1. 정규장 / NXT — 실제가

### 한국투자증권 KIS Developers (무료, 실전 도메인)
- `https://openapi.koreainvestment.com:9443`, OAuth 토큰 발급(24h 캐시) 후 사용.
- 사용 엔드포인트:
  - **현재가** `inquire-price` (`tr_id=FHKST01010100`) — `FID_COND_MRKT_DIV_CODE=J`(KRX) / `NX`(넥스트레이드/NXT).
  - **정규장 종가** 일별시세(`inquire-daily-price` 등) — 최근 거래일 종가 + 영업일자(`stck_bsop_date`) → `regularClose/regularCloseDate`, `high`.
  - **투자의견** 종목투자의견 — `/api/opinions` 컨센서스 산출용(아래 4번).
- 호출 제한이 있으므로 **단일 폴링 + 캐시**로 보호. 종가는 1회 성공 후 캐시 고정, ESTIMATE 모드에선 현재가 미호출.
- 키(`KIS_APP_KEY/SECRET`) 없으면 KIS 비활성 → 전 구간 Hyperliquid 추정으로 폴백.

### 거래 시간 (KST)
- 프리마켓: 08:00 ~ 09:00
- 정규장: 09:00 ~ 15:30
- NXT 애프터마켓: 15:40 ~ 20:00
- 공휴일/주말은 휴장 → 추정(ESTIMATE) 모드.

## 2. 미국 정규장 — Alpaca IEX 실제가

### Alpaca Market Data Basic (무료)

- 지원 종목: 샌디스크 `SNDK`, 마이크론 `MU`, 브로드컴 `AVGO`.
- `GET /v2/stocks/snapshots?symbols=...&feed=iex`로 현재 활성 미장 종목을 한 번에 batch 조회한다. 사용자별·종목별 요청을 만들지 않는다.
- Basic 무료 플랜은 분당 200회, WebSocket 30개 심볼이며 실시간 범위는 IEX다. 전체 미국 거래소 통합 SIP 시세가 아니라는 점을 UI와 면책문구에 표시한다. [공식 플랜 설명](https://docs.alpaca.markets/us/docs/about-market-data-api), [snapshot API](https://docs.alpaca.markets/us/reference/stocksnapshots-1)
- 뉴욕시간 평일 09:30~16:00은 `LIVE`, 그 외에는 snapshot의 직전 값을 `CLOSED`로 표시한다. DST는 `America/New_York` 시간대로 처리한다.
- 키가 없거나 batch 응답에 종목이 빠지면 Hyperliquid 해당 종목 perp로 폴백해 `ESTIMATE`로 표시한다.
- 미국 휴장일 캘린더는 아직 없어 평일 공휴일을 장중으로 오판할 수 있다. 운영 전 거래소 캘린더 연동이 필요하다.

## 3. 야간 / 주말 — 추정가

### Hyperliquid HIP-3 perp (공개 info API, 무료) — ✅ 검증 완료
- builder dex `xyz`에 주식/지수 perp 다수 상장. 24시간 거래.
- 검증된 심볼: 삼성전자 `xyz:SMSN`, SK하이닉스 `xyz:SKHX`, 샌디스크 `xyz:SNDK`, 마이크론 `xyz:MU`, 브로드컴 `xyz:AVGO`.
- 조회: `POST https://api.hyperliquid.xyz/info`, body `{"type":"metaAndAssetCtxs","dex":"xyz"}`
  - 응답 `[meta, assetCtxs]`. `meta.universe[i].name` ↔ `assetCtxs[i]` 인덱스 매칭.
  - `markPx`(현재가 USD), `prevDayPx`(전일가, 등락률용) 사용.
- 원화 추정 = `markPx(USD) × USD/KRW`. (GDR 비율 1:1로 계산 시 실제 1·2위 초접전 시총과 일치 확인)

### 환율 소스 — ✅ 검증 완료
- `GET https://open.er-api.com/v6/latest/USD` (무료·키 불필요), `rates.KRW` 사용. 갱신은 하루 1회.
- 주의: Hyperliquid `xyz:KRW`는 거래량 0이라 환율로 부적합.
- 참고: **순위·격차·역전% 계산은 두 종목에 같은 FX가 곱해져 약분 → 환율과 무관**. FX는 절대 원화가/시총 표시값에만 영향.
- 인트라데이가 필요하면 Yahoo `KRW=X`(비공식)로 교체 가능(클라이언트 인터페이스 분리됨).

## 4. 시장 상태 판별 로직

```
now(KST) 기준:
  주말/공휴일                → ESTIMATE (Hyperliquid)
  평일 08:00~09:00          → PREMARKET
  평일 09:00~15:30          → REGULAR (KIS)
  평일 15:40~20:00          → NXT (KIS)
  그 외 평일 시간           → ESTIMATE (Hyperliquid)
```

공휴일은 `MarketModeService`의 정적 집합(2026년치: 신정·설·삼일절·어린이날·부처님오신날·현충일·광복절·추석·개천절·한글날·성탄절·연말폐장 등)으로 판별한다. 음력 공휴일은 매년 날짜가 바뀌므로 연도별 갱신이 필요하다.

미장은 `UsMarketHours`가 `America/New_York` 기준 평일 09:30~16:00을 별도로 판정한다. KRX `MarketMode`와 독립적이므로 한국장이 닫혀도 미국장이 열려 있으면 미장 종목은 3초 주기로 갱신한다.

## 5. 증권사 투자의견 (`/api/opinions`)

- KIS **종목투자의견** API로 종목별 최근 리포트를 수집(`KisMarketDataClient.fetchInvestOpinions`).
- 같은 증권사는 최신 1건만 남기고, 매수/중립/매도로 분류해 **컨센서스**(평균 목표가, 기관 수, buy/hold/sell, 1~5 점수) 산출.
- 국장 종목만 대상으로 하루 1회 캐시(`InvestOpinionService`), 실패 시 캐시/빈 값으로 폴백.

## 6. 수요 기반 폴링 주기

- 시세 폴링: 정규장/PREMARKET/NXT 기본 3초, ESTIMATE/no-trade break 60초. 활성 SSE 구독 종목만 수집하며 모든 클라이언트가 결과를 공유한다.
- 마지막 구독 종료 후 15초 grace 동안 순간 재연결을 기다린 뒤 활성 집합에서 제거한다. 이후 구독자가 없으면 quote collection 호출은 0이다.
- KIS 현재가: 활성 국장 종목만 REGULAR/NXT/PREMARKET에서 종목별 1회. ESTIMATE에선 미호출한다.
- Alpaca: 활성 미장 종목 전체를 polling cycle당 snapshot batch 1회. 미장 종목만 있고 모두 응답하면 Hyperliquid는 호출하지 않는다.
- Hyperliquid: KRX 폴백 또는 누락된 US 종목이 필요할 때만 polling cycle당 `metaAndAssetCtxs` batch 1회.
- KIS 종가: 1회 성공 후 캐시 고정, 거래일이 바뀔 때만 재조회.
- 환율: 하루 1회 갱신값을 캐시.

## 7. 검증 현황

- [x] Hyperliquid 삼성전자/하이닉스 perp 심볼명 → `xyz:SMSN`, `xyz:SKHX` (라이브 응답 확인)
- [x] Hyperliquid SNDK/MU/AVGO perp 심볼과 Alpaca snapshot batch 파싱(Fake 서버) 확인
- [x] 환율 API → open.er-api.com (무료·키 불필요, 하루 1회)
- [x] KIS 정규장/NXT 현재가·종가·투자의견 연동 (라이브 검증)
- [x] 미장 상장주식수는 2026년 SEC 10-Q 공시 기준으로 설정
- [ ] 국장 상장주식수 데이터 소스(KRX/공시)와 미장 공시 변경 자동 갱신 — 현재 설정값
- [ ] 공휴일 정적 집합 → 연도별 자동 갱신(또는 특일정보 API) 검토
- [ ] 미국 휴장일·조기폐장 캘린더 연동

> ⚠️ 본 서비스의 추정 시세는 투자 참고용이며, 공식 거래소 가격이 아니다.
