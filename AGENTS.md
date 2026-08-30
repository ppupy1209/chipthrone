# CHIP·THRONE — AI 에이전트 공유 문서

## 개요 / 목표

삼성전자·SK하이닉스의 **전일 확정 시세**(금융위원회)와 **24시간 해외 추정 시세**(Hyperliquid HIP-3 `xyz` dex perp)를
한 화면에서 비교하고, 관심 미국 AI·반도체 종목을 함께 보는 대시보드. 운영 주소는 https://chipthrone.com.

- 추정 가격은 실제 주식 체결가가 아니다. 모든 추정 카드에 `Hyperliquid 추정`과 괴리 기준을 표시한다.
- 투자 권유·자문이 아니다.

## 기술 스택

| 영역 | 스택 |
|---|---|
| 백엔드 | Java 21, Spring Boot, Gradle Wrapper, SSE fan-out, DB 없음(인메모리 캐시) |
| 프론트 | React 19, TypeScript, Vite 8, Tailwind 4, 관심 종목은 Local Storage |
| 인프라 | 프론트 Vercel / 백엔드 EC2 Docker(GitHub Actions → GHCR → Watchtower) / DNS·CDN Cloudflare |
| 관측 | Prometheus + Grafana (`docs/05-monitoring.md`), Slack 장애 알림 |
| 외부 API | 금융위원회 일별 주식시세정보, 업비트 공개 시세 조회, Hyperliquid 공개 `info` API |

## 실행·테스트 명령

로컬 전체 실행:

```bash
docker compose up --build
```

프론트 `http://localhost:5173`, 백엔드 `http://localhost:8080`, 종료는 `docker compose down`.

백엔드 — **`JAVA_HOME`을 IntelliJ JBR(21)로 지정해야 한다. 시스템 기본 `java`는 8이라 빌드가 깨진다.**

```bash
cd backend && ./gradlew test
```

```bash
cd backend && ./gradlew bootRun
```

프론트:

```bash
cd frontend && npm install && npm run build
```

```bash
cd frontend && npm test
```

```bash
cd frontend && npm run lint
```

환경변수(없으면 공식 확정값은 비우고 Hyperliquid 추정만 동작):
`PUBLIC_DATA_SERVICE_KEY`(공공데이터포털 Decoding 키), 프론트 `VITE_API_URL`.

## 아키텍처 결정 로그

- **[이전] DB 없이 인메모리 캐시 + Local Storage로 운영.** 종목 수가 적고 상태가 전부 파생값이라 영속 계층 비용이 이득보다 컸다.
- **[이전] KIS·Alpaca 제거, 시세 소스를 Hyperliquid 단일화.** 현재 모든 장중 추정 가격의 source/status는 `HYPERLIQUID`/`ESTIMATE`.
  Toss Open API는 시세 표시·제3자 제공 약관 확인 전까지 도입하지 않는다.
- **[이전] 수요 기반 단일 polling + SSE fan-out.** 사용자·종목 수와 무관하게 polling cycle당 Hyperliquid 호출 1회.
  활성 종목 0이면 수집만 멈추고 health check는 유지. 마지막 구독 해제 후 15초 grace.
- **[이전] 괴리율은 "같은 시점" 비교로만 계산.** 현재가 ↔ 전일 종가를 맞대면 등락이 섞여 추정 오차를 못 잰다.
  확정 종가 기준일 15:30 KST에 마감된 15분 캔들(`candleSnapshot`) × 그날 고시환율 ÷ 공식 종가.
  기준일이 바뀔 때만 재계산, 실패 시 30분 백오프.
- **[이전] 왕좌 교체 판정과 1·2위 막대는 금융위원회 공식값만 사용.** 추정가의 환율·perp 오차가 순위를 뒤집지 않게 하기 위함.
- **[이전, 2026-08-30 폐기] 환율 30분 주기 갱신.** 수출입은행 일 1000회 한도 대비 약 48회/일. 화면에 고시 기준일과 `fxFetchedAt`을 함께 표시하고 "실시간 환율"이라 쓰지 않는다.
- **[2026-08-03] 추정 정확도 조사 결과(문서화만, 코드 변경 없음).** 아래 "알려진 한계" 참고.
- **[2026-08-03] 미국 카드에 "종가 대비" 추가. 기준가는 Hyperliquid 자체 캔들로 만든다.**
  미국 종목엔 금융위 같은 확정 종가 소스가 없다. Yahoo 등은 재배포 조건이 걸려 공개 서비스에 쓰기 어렵다.
  대신 직전 정규장 마감(16:00 ET) 시각의 perp 캔들 종가를 기준가로 쓴다 — 그 시간대 추적 오차가
  0.03~0.09%(2026-08-03 실측, 5거래일 × NVDA/AAPL/TSLA/MU)라 등락률 기준으로 충분하고 새 키·소스가 없다.
  등락률은 **USD 기준**으로 계산한다(양쪽 다 달러라 환율 변동이 섞이지 않음). 국내 카드는 원화 기준 그대로다.
  한계: 미국 공휴일 캘린더가 없어 휴장일이면 그날 16:00 ET perp 가격이 기준가가 된다.
- **[이전, 2026-08-30 폐기] USD/KRW를 한국수출입은행 고시환율 → Pyth Network `FX.USD/KRW`로 교체.**
  `priceKrw = markPx × fxRate`가 선형이라 환율 오차가 곧 가격 오차인데, 실측에서 perp 자체 오차(0.05%)보다
  고시환율 지연(0.19%)이 3배 컸다. **Hyperliquid HIP-3 오라클이 쓰는 것과 같은 피드**라 perp을 만들 때 쓰인 환율과
  되돌릴 때 쓰는 환율이 일치한다. 키·호출 한도가 없어 갱신 주기도 30분 → 1분으로 좁혔다.
  괴리율용 과거 환율은 날짜가 아니라 **캔들과 같은 순간**(`fetchUsdKrw(Instant)`)으로 받는다 — 인터페이스를
  `LocalDate` → `Instant`로 바꾼 이유다. 대가: 공적 고시값이라는 근거를 잃으므로 면책조항에 민간 오라클임을 명시했다.
- **[2026-08-30] Pyth 인증 실패로 업비트 KRW-USDC 서버 내부 환산으로 교체.**
  인증키 없는 공개 호가 조회 API를 5분마다 호출해 최대 288회/일로 제한한다. 최우선 매수 호가와 매도 호가의 중간값을 쓰며,
  호가 갱신이 15분보다 오래됐으면 거부한다.
  실패 시 최근 30분 안의 마지막 성공값만 유지한다. 괴리율은 요청 시각 이전 30분 안의 마지막 분 캔들을 사용한다.
  업비트 원본 응답과 환율 필드는 외부 API와 화면에서 제거하고 환산된 추정값만 제공한다. KRW-USDC는 공적 환율이 아니며
  원화 프리미엄과 USDC 가격 변동이 반영될 수 있음을 면책조항에 밝힌다.
- **[2026-08-03] 금융위 API 필터 파라미터를 `srtnCd` → `likeSrtnCd`로 교체(버그 수정).**
  `getStockPriceInfo`는 `srtnCd`를 **조용히 무시한다**. 실측: `srtnCd=005930`이면 `totalCount=4,365,916`에 첫 행이 딥커머스(900110),
  `likeSrtnCd=005930`이면 `totalCount=1,615`에 첫 행이 005930. 그동안 첫 20행에 해당 종목이 없어 클라이언트 필터가 늘 빈 결과를 냈고,
  예외가 아니라 `Optional.empty()`라 로그도 안 남아 확정 시세가 조용히 비어 있었다.
  같은 이유로 "응답은 정상인데 종목이 없음" 경로에 warn을 추가했다. 목 서버는 파라미터 의미를 모르니 기존 테스트가 이걸 못 잡았다.
- **[2026-08-03] 확정 종가 섹션 제목에 실제 기준일을 넣는다.** "전일 확정 시세" → "2026-07-31 (금) 종가".
  공공데이터가 다음 영업일에 갱신될 수 있어 `전일`을 고정 문구로 쓰지 않는다는 기존 규칙과 같은 이유다.
  같은 이유로 추정 카드의 새 등락률 라벨도 "전날 종가 대비"가 아니라 **"종가 대비"**로 두고, 기준일은 hover로 밝힌다.
- **[2026-08-03] 국내 추정 카드에 추정 시가총액 + 종가 대비 등락률 추가.** 증권앱과 같은 기준(확정 종가 대비)이라
  Hyperliquid `prevDayPx` 기준 24시간 등락률과 섞이면 안 된다. 24시간 등락률은 계속 미국 카드 전용이다.
  괴리율 라벨은 두 카드 모두 "마감 시점 괴리율"로 통일했다(구 "확정 종가 시점 추정 괴리율").
- **[2026-08-03] 미국 카드에 종목별 시그니처 색 도입.** 14종목이 전부 같은 초록이라 구분이 안 됐다.
  색은 상단 3px 스트립에만 얹고 숫자(달러가·원화 환산·추정 시가총액)는 본문 색을 그대로 상속시킨다.
  테마 전환이 `.dark` 클래스 토글이라 인라인 style만으로는 라이트/다크 두 색을 못 고른다. 그래서
  카드에 `--brand`/`--brand-dark` CSS 변수를 심고 `index.css`의 레이어 밖 `.brand-strip` 규칙이 고르게 했다.
  레이어 밖 규칙이라 Tailwind border 유틸리티보다 항상 우선한다. 색 테이블은 `frontend/src/lib/brandColor.ts` 한 곳.

## 컨벤션

- 문서·주석·커밋 메시지는 한국어. 커밋은 `feat:`/`fix:`/`perf:`/`docs:`/`tweak:` 접두어.
- 값을 꾸미지 않는다. 외부 API 실패 시 유효시간 안의 마지막 성공값 또는 빈 값을 쓴다. 원화 환산 소스는 면책조항에 명시하되 원본 환율은 노출하지 않는다.
- 추정치와 확정치를 문구로 구분한다(`Hyperliquid 추정`, 실제 `basDt` 표시 등).
- AWS 변경·유료 리소스 생성은 별도 승인 없이 하지 않는다.

## 알려진 한계 (추정 시세 정확도)

2026-08-03 12:51 KST 실측 기준.

- **장중 추적 오차는 bp 단위다.** `xyz:SMSN` mark $169.22 / oracle $169.17(0.03%), KRW 환산값이 한국 실시간 가격 대비 +0.02%.
- **최대 오차원은 perp이 아니라 환산값이다.** `priceKrw = markPx × fxRate` 선형이라 KRW-USDC의 원화 프리미엄과
  USDC 가격 변동이 그대로 추정가 오차에 반영된다.
- **미국 종목이 국내 종목보다 훨씬 얇다.** 24h 거래대금 SKHX $436M / SMSN $68.6M vs NVDA $18.3M / AAPL $13.3M / TSLA $7.6M.
  게다가 한국 사용자가 보는 시간대는 미국 장 마감 시간이라 현물 앵커가 없다.
- **`prevDayPx` 기준 등락률은 전일 종가 기준 등락률과 다르다.** 2026-08-03 12:51 KST에 SMSN 24h 등락률은 **+0.53%**,
  같은 시각 한국 시장 기준 등락률은 **-7.8%**. 부호까지 반대다.
  현재 `changePct`는 미국 카드에서만 표시하므로(`UsCompanyCard`) 문제가 없지만, 국내 카드에 그대로 옮기면 안 된다.
- **표시되는 괴리율은 최선의 순간만 잰다.** 15:30 KST는 오라클이 KRX에 앵커된 시점이라 오차가 가장 작다. 야간·주말 정확도를 대표하지 않는다.
- **정확도 이력이 없다.** `EstimateAccuracyService`는 `ConcurrentHashMap` 인메모리 + 최신 종가 기준일 1건만 유지한다.
  실제 MAE/p95를 말하려면 `divergencePct`를 날짜별로 적재해야 한다.

## 현재 진행 상태

- 완료:
  - 수요 기반 단일 polling + SSE fan-out, 컨테이너 자원 계측, EC2 부하 테스트 실측(`docs/ec2-load-test-results.json`)
  - localhost Fake API 장애 주입, quote freshness SLI, MTTD·복구 감지 실측(`docs/07-slo-resilience.md`)
  - 공공데이터 기반 확정 시세와 같은 시점 괴리율 산출(`EstimateAccuracyService`)
  - 업비트 KRW-USDC 5분 주기 내부 환산, 원본 환율 비노출
  - 미국 카드 종목별 시그니처 색 + 숫자 본문색 통일 (`brandColor.ts`, `.brand-strip`)
  - 금융위 필터 파라미터 버그 수정(`likeSrtnCd`) — 확정 종가·확정 시총·괴리율이 실제로 채워지는 것 확인
- 진행 중:
  - 없음
- 다음 작업 (후보, 미착수):
  - 괴리율 시계열 적재 → 실제 MAE/p95 산출
  - 국내 카드에 전일 종가 기준 등락률 병기(24h 등락률과 혼동 방지)
  - 고시환율 지연이 추정가에 주는 오차를 화면에 노출할지 결정

## 문서

- [아키텍처](docs/01-architecture.md) · [데이터 소스](docs/02-data-sources.md) · [배포](docs/03-deploy.md)
- [API](docs/04-api.md) · [모니터링](docs/05-monitoring.md) · [수요 기반 수집과 측정](docs/06-demand-driven-quotes.md)
- [SLO와 외부 시세 장애 복구 실험](docs/07-slo-resilience.md)
