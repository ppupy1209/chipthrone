# 아키텍처 설계

## 1. 시스템 개요

chipthrone은 삼성전자(005930)와 SK하이닉스(000660)의 주가·시가총액을 실시간 비교하는 단일 페이지 대시보드다.
핵심 제약은 **AWS 프리티어 1대(EC2 t3.micro)** 에서 동작해야 한다는 것. 현재 DB는 사용하지 않고 **인메모리**로만 동작한다.

```
┌──────────────┐   SSE    ┌─────────────────────────────────┐
│  React SPA   │◀─────────│  Spring Boot (EC2 t3.micro)     │
│  (Vercel)    │  HTTP    │  ├ @Scheduled 단일 폴링 워커     │
└──────────────┘─────────▶│  ├ 인메모리 캐시(AtomicReference)│
                          │  ├ SSE Emitter (fan-out)        │
                          │  └ 마지막값 폴백(try/catch)      │
                          └──────────────┬──────────────────┘
                                         │
                                ┌────────▼───────────────┐
                                │ 외부 데이터 소스        │
                                │ KIS / Hyperliquid / FX │
                                └────────────────────────┘
```

> 현재 RDS/Caffeine/Resilience4j는 사용하지 않는다. 영속화(과거 시총·역전 기록) 도입 시 RDS(MySQL) 추가 예정.

## 2. 시장 모드 & 거래 시간 (KST)

`MarketModeService`가 현재 시각(Asia/Seoul) + 주말/공휴일로 모드를 판별한다.

| 시간대 | 모드 | 소스 | 표시 |
|---|---|---|---|
| 08:00 ~ 09:00 | PREMARKET | KIS(시간외) / 추정 | 프리마켓 |
| 09:00 ~ 15:30 | REGULAR | KIS 실시세 | 정규장 |
| 15:40 ~ 20:00 | NXT | KIS NXT(애프터마켓) | 애프터마켓 |
| 그 외 · 주말 · 공휴일 | ESTIMATE | Hyperliquid perp × 환율 | 추정 |

공휴일은 `MarketModeService` 내 정적 집합(2026년치)으로 관리한다(음력 공휴일은 매년 갱신 필요). 자세한 데이터 소스는 [02-data-sources.md](02-data-sources.md).

## 3. 핵심 과제: 외부 API 호출 최소화 (fan-out)

문제: 유저 요청마다 외부 API를 호출하면 (1) 무료 쿼터 소진 (2) 응답 지연 (3) 장애 전파.

해결:
1. `@Scheduled` 워커가 **주기(기본 3초)마다 한 번만** 외부 시세를 폴링한다.
2. 최신 스냅샷을 **인메모리 캐시(AtomicReference)** 에 저장한다. (프리티어라 Redis 미사용)
3. 클라이언트는 **SSE**로 구독하고, 폴링 결과를 N명에게 fan-out 한다.
4. 외부 API 실패 시 예외 전파 없이 **마지막 성공 스냅샷/값을 유지**(폴백)한다.

추가로 KIS **종가**는 확정 불변값이므로 1회 성공 후 캐시 고정하고, 거래일이 바뀔 때만 재조회한다(호출 최소화).

→ 동시 접속자가 늘어도 외부 호출 횟수는 일정하게 유지된다.

## 4. 데이터 모델 (현재 인메모리)

DB는 사용하지 않는다. 종목 마스터(코드·이름·상장주식수)는 설정(`QuoteProperties`)으로, 시세·종가·투자의견 캐시는 인메모리(AtomicReference / ConcurrentHashMap)로 보관한다.

영속화 도입 시(과거 시총 추이·역전 기록 등) 운영 관행대로 **외래키·JPA 연관관계 미사용**(ID 참조, 애플리케이션 레벨 조인) 원칙을 따른다.

`StockQuote` 응답 필드: `code, name, priceKrw, priceUsd, changePct, sharesOutstanding, marketCap, regularClose, regularCloseDate, high, nxtClose, nxtCloseDate`.

## 5. 시가총액 & 역전 계산

```
시가총액 = 현재가 × 상장주식수
격차(%)  = (1위 시총 − 2위 시총) / 2위 시총 × 100
역전 임계 등락률 = (1위 시총 / 2위 상장주식수 / 2위 현재가 − 1) × 100
```

상장주식수는 자주 바뀌지 않으므로 설정값으로 보관한다(재검증 필요).

## 6. API 엔드포인트

| 엔드포인트 | 설명 |
|---|---|
| `GET /api/health` | 헬스체크 |
| `GET /api/quotes` | 최신 스냅샷(JSON) 1회 |
| `GET /api/stream` | SSE(`text/event-stream`) — 갱신마다 push |
| `GET /api/opinions` | 증권사 투자의견 컨센서스(KIS 종목투자의견) |

응답 형식 상세는 [04-api.md](04-api.md).

## 7. 배포 (CI/CD)

1. `main` 머지 시 GitHub Actions가 백엔드 Docker 이미지를 빌드해 **GHCR**에 푸시.
2. EC2의 **watchtower**가 60초 주기로 새 이미지를 감지해 자동 pull·재시작(SSH 인바운드 불필요).
3. 프론트엔드는 Vercel GitHub 연동으로 자동 배포.
4. Nginx 리버스 프록시 + Let's Encrypt(certbot) SSL. 자세한 절차는 [03-deploy.md](03-deploy.md).
