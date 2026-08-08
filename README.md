# CHIP·THRONE

삼성전자와 SK하이닉스의 전일 확정 시세와 24시간 해외 추정 시세를 비교하고, 관심 있는 미국 AI·반도체 종목을 함께 보는 대시보드다.

🔗 https://chipthrone.com

## 화면 구성

- **전일 확정 시세**: 금융위원회 주식시세정보의 종가·시가총액·기준일과 왕좌 교체 조건
- **365일·24시간 추정 시세**: Hyperliquid 파생시장 가격을 Pyth Network의 USD/KRW 실시간 시세로 환산
- **미국 반도체·AI**: 반도체·M7 목록에서 개수 제한 없이 선택, 브라우저 Local Storage에 유지
- **괴리율**: 국내 두 종목은 공식 종가와 **같은 시점(15:30 KST)의 추정가**를 맞대어 계산. 미국 종목은 대조할 공식 종가가 없어 24시간 등락률 표시
- **수요 기반 수집**: 사용자별 polling 없이 활성 고유 종목만 단일 polling하고 SSE로 공유

추정 가격은 실제 주식 체결가가 아니다. 모든 추정 카드에 `Hyperliquid 추정`과 괴리 기준을 표시한다.

## 데이터 소스

| 데이터 | 출처 | 갱신 정책 |
|---|---|---|
| 삼성전자·SK하이닉스 확정 종가/시총 | 금융위원회 일별 주식시세정보 | 활성 국내 종목만 일 단위 캐시 |
| 국내·미국 24시간 추정 가격 | Hyperliquid `metaAndAssetCtxs` | 활성 종목이 있을 때 3초마다 batch 1회 |
| USD/KRW | Pyth Network `FX.USD/KRW` | 1분 캐시 · 키 불필요 |

KIS와 Alpaca는 사용하지 않는다. 토스증권 Open API는 이용 조건과 시세 재배포 권한을 확인한 뒤 별도 작업으로 검토한다.

## 로컬 실행

```bash
docker compose up --build
```

- 프론트: `http://localhost:5173`
- 백엔드: `http://localhost:8080`
- 종료: `docker compose down`

직접 실행하려면 Java 21과 Node 20 이상이 필요하다.

```bash
cd backend
./gradlew bootRun

cd frontend
npm install
npm run dev
```

환율은 키 없이 바로 붙는다. 국내 확정 종가만 공공데이터포털 키가 필요하며, 키가 없으면 확정값 칸을 비워 둔다.

```bash
PUBLIC_DATA_SERVICE_KEY=... docker compose up --build
```

프론트가 다른 백엔드를 바라보게 하려면 `frontend/.env`에 `VITE_API_URL`을 지정한다.

## 문서

- [아키텍처](docs/01-architecture.md)
- [데이터 소스](docs/02-data-sources.md)
- [수요 기반 수집과 측정](docs/06-demand-driven-quotes.md)
- [SLO와 외부 시세 장애 복구 실험](docs/07-slo-resilience.md)

## 부분 캡처

- [전일 확정 시세와 왕좌 교체 조건](docs/images/official-confirmed-prices.jpg)
- [24시간 추정 시세와 USD/KRW](docs/images/hyperliquid-estimates-fx.jpg)
- [미국 반도체 종목 선택](docs/images/us-watchlist-picker.jpg)
- [M7·AI 관련 종목 목록](docs/images/us-watchlist-categories.jpg)
- [미국 Hyperliquid 추정 카드](docs/images/us-hyperliquid-cards.jpg)
- [Grafana 외부 API 호출률 패널](docs/images/grafana-external-api-calls-current.jpg)
- [Grafana SSE 전달 지연 p95 패널](docs/images/grafana-sse-delivery-p95-current.jpg)
- [Grafana 시세 freshness 장애·복구 패널](docs/images/grafana-quote-freshness-failure-recovery.png)
- [Grafana polling 실패·복구 패널](docs/images/grafana-polling-failure-recovery.png)

본 서비스는 투자 권유나 자문이 아니다.
