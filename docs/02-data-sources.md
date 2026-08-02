# 데이터 소스

## 금융위원회 일별 주식시세정보

- 공공데이터포털 `GetStockSecuritiesInfoService/getStockPriceInfo`를 사용한다.
- `srtnCd`로 삼성전자 `005930`, SK하이닉스 `000660`을 정확히 조회한다.
- 최근 30일 결과 중 `basDt`가 가장 최신인 행을 선택한다.
- `clpr`, `hipr`, `lstgStCnt`, `mrktTotAmt`, `basDt`를 확정 종가·고가·상장주식수·시가총액·기준일로 사용한다.
- 실제 일별 데이터는 다음 영업일에 갱신될 수 있으므로 화면에는 `전일`을 고정 문구로 쓰지 않고 실제 `basDt`를 `YYYY-MM-DD (요일)`로 표시한다.
- API 키가 없거나 실패하면 값을 꾸미지 않고 기존 캐시 또는 빈 공식값을 사용한다.

환경변수: `PUBLIC_DATA_SERVICE_KEY`. 공공데이터포털에서 받은 일반 인증키(Decoding)를 사용한다.

## Hyperliquid 24시간 추정 시세

- `POST https://api.hyperliquid.xyz/info`
- body: `{"type":"metaAndAssetCtxs","dex":"xyz"}`
- `markPx`를 추정 USD 가격, `prevDayPx`를 미국 카드의 24시간 등락률 기준가로 쓴다.
- 국내 두 종목은 `xyz:SMSN`, `xyz:SKHX`다.
- 지원 미국 종목은 반도체·M7 카테고리로 제공하며, 어느 카테고리에도 없는 지원 종목은 "기타"로 자동 분류된다. 같은 종목이 여러 카테고리에 있어도 구독은 한 번만 센다.

### 괴리율 산출용 과거 캔들

- `POST https://api.hyperliquid.xyz/info`
- body: `{"type":"candleSnapshot","req":{"coin":"xyz:SMSN","interval":"15m","startTime":…,"endTime":…}}`
- 응답 각 원소의 `T`가 캔들 마감 시각(ms)이다. 요청 시각 이전에 마감된 것 중 가장 늦은 캔들의 `c`를 쓴다.
- 공식 종가 기준일의 15:30 KST를 요청 시각으로 삼아 정규장 마감 시점의 추정가를 구한다.
- 종목별로 공식 종가 기준일이 바뀔 때만 1회 호출한다. 폴링 주기와 무관하다.
- 이는 공개 파생시장 가격이며 미국·한국 거래소의 주식 체결가가 아니다.

API는 universe 전체를 한 번에 반환한다. 따라서 사용자 200명이 같은 종목을 봐도, 서로 다른 종목을 봐도 polling cycle당 외부 호출은 batch 1회다. 구독자가 없으면 15초 grace 이후 호출하지 않는다.

## 한국수출입은행 USD/KRW

- `GET https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON`
- `data=AP01`, `cur_unit=USD`의 `deal_bas_r`를 사용한다.
- 휴일에는 최근 7일을 역순으로 확인해 가장 최근 공식 고시값을 사용한다.
- **30분 간격**으로 재조회한다. 일일 호출 한도 1,000회 대비 약 48회/일이다.
- 화면에는 고시 기준일과 마지막 조회 시각(`fxFetchedAt`)을 함께 표시한다. 실시간 환율이라고 표현하지 않는다.

환경변수: `KOREA_EXIM_AUTH_KEY`.

## 호출·캐시 정책

| 소스 | 호출 단위 | 정책 |
|---|---|---|
| Hyperliquid | 전체 universe batch | 활성 종목이 있을 때 3초당 1회 |
| 금융위원회 | 국내 종목별 | 활성 국내 종목만 일 단위, 발표 시각 전/후 최대 2개 성공 창 |
| 한국수출입은행 | 날짜별 | 일 단위 캐시, 휴일 최근값 탐색, 실패 시 10분 재시도 |

KIS, Alpaca, 증권사 투자의견 API는 현재 실행 경로에 없다. Toss Open API는 승인 후 약관상 시세 표시·제3자 제공 허용 범위를 먼저 확인해야 한다.
