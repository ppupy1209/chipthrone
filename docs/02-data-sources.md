# 데이터 소스

## 금융위원회 일별 주식시세정보

- 공공데이터포털 `GetStockSecuritiesInfoService/getStockPriceInfo`를 사용한다.
- `likeSrtnCd`로 삼성전자 `005930`, SK하이닉스 `000660`을 조회한다. **`srtnCd`는 이 엔드포인트가 조용히 무시한다**
  (전 종목이 그대로 돌아온다). 접두 매치라 응답에서 `srtnCd` 정확 일치를 한 번 더 거른다.
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

## Pyth Network USD/KRW

- `GET https://hermes.pyth.network/v2/updates/price/latest?ids[]=<feed>&parsed=true`
- 피드는 `FX.USD/KRW`, id는 `e539120487c29b4defdf9a53d337316ea022a2688978a468f9efd847201be7e3`.
- 값은 `parsed[0].price`의 `price × 10^expo`다. `price`는 정수 문자열, `expo`는 음수 지수(-5).
- **Hyperliquid HIP-3 오라클이 원화 환산에 쓰는 것과 같은 피드다.** perp 가격을 만들 때 쓰인 환율과
  우리가 되돌릴 때 쓰는 환율이 같아야 환산에서 오차가 새로 생기지 않는다.
- **1분 간격**으로 재조회한다. 키도 호출 한도도 없다.
- 화면에는 `Pyth 실시간 · 갱신 HH:MM`으로 표시한다. 공적 고시환율이 아니라는 점을 면책조항에 밝힌다.

### 괴리율용 과거 시점 환율

- `GET https://hermes.pyth.network/v2/updates/price/{epochSeconds}?ids[]=<feed>&parsed=true`
- 확정 종가 기준일의 15:30 KST를 그대로 넘겨 **캔들과 같은 순간의** 환율을 받는다.
  날짜 단위 환율을 쓰면 그날의 환율 변동이 괴리율에 섞인다.
- 외환시장이 닫혀 그 시각 값이 없으면 404다. 0 → 1시간 → 24 → 48 → 72시간 순으로 되짚고, 다 실패하면 값을 꾸미지 않고 예외를 던진다.
- 브라켓은 `%5B%5D`로 인코딩되어 나가며 Hermes는 두 형태를 모두 받는다.

환경변수: 없음. 필요하면 `PYTH_HERMES_URL`, `PYTH_USD_KRW_FEED_ID`로 덮어쓴다.

## 호출·캐시 정책

| 소스 | 호출 단위 | 정책 |
|---|---|---|
| Hyperliquid | 전체 universe batch | 활성 종목이 있을 때 3초당 1회 |
| 금융위원회 | 국내 종목별 | 활성 국내 종목만 일 단위, 발표 시각 전/후 최대 2개 성공 창 |
| Pyth Network | 단일 피드 | 1분 캐시, 실패 시 10분 재시도 후 마지막 성공값 유지 |

KIS, Alpaca, 증권사 투자의견 API는 현재 실행 경로에 없다. Toss Open API는 승인 후 약관상 시세 표시·제3자 제공 허용 범위를 먼저 확인해야 한다.
