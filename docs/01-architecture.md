# 아키텍처

## 시스템 개요

CHIP·THRONE은 DB 없이 Spring Boot 인메모리 캐시와 React Local Storage로 동작한다.

```text
React SPA
  ├─ GET /api/assets
  └─ GET /api/stream?symbols=...
          │ SSE
Spring Boot
  ├─ 연결별 구독 Set
  ├─ 종목별 공유 subscriber count
  ├─ 단일 수요 기반 polling worker
  ├─ 종목별 최신값/일별 공식값 인메모리 캐시
  └─ 연결별 필터링 SSE fan-out
          │
          ├─ 금융위원회 일별 주식시세정보
          ├─ 업비트 KRW-USDC 공개 시세
          └─ Hyperliquid 공개 info API
```

KIS와 Alpaca 코드·설정·토큰 처리는 제거했다. 현재 모든 장중 추정 가격의 source/status는 `HYPERLIQUID`/`ESTIMATE`다.

## 구독과 수집

1. 백엔드가 연결당 최대 16개(지원 종목 전체) 코드를 지원 목록으로 검증한다.
2. 동일 종목을 여러 연결이 요청해도 공유 subscriber count와 활성 종목 집합에는 한 번만 들어간다.
3. 단일 scheduler가 활성 종목이 있을 때만 3초마다 `metaAndAssetCtxs`를 한 번 호출한다.
4. Hyperliquid 응답 한 건에서 활성 종목만 추려 스냅샷을 만들고, 연결별 관심 종목으로 다시 필터링해 fan-out 한다.
5. completion/error/timeout/send 실패 정리는 idempotent하다. 마지막 구독 종료 후 15초 grace가 지나면 종목을 제거한다.
6. 활성 종목이 0이면 시세 수집만 멈춘다. Health Check와 외부 liveness는 계속 동작한다.

Hyperliquid는 일괄 API이므로 한 polling cycle의 호출 수는 사용자 수와 선택 종목 수에 관계없이 1회다. 금융위원회는 종목별 API지만 활성 국내 종목만 일 단위로 조회하며 현재 상한은 삼성전자·SK하이닉스 2개다.

## 캐시와 장애 폴백

- Hyperliquid 실패: 마지막 성공 종목 스냅샷 유지, 연속 장애/복구 상태 전이만 Slack 알림
- 금융위원회: 성공값을 일 단위 캐시. 오전 조회 후 당일 발표 시각(13:05 KST)이 지나면 한 번 더 조회
- 업비트: 서버에서만 5분 간격으로 재조회. 원본 응답과 환율값은 외부 API와 화면에 노출하지 않음
- 환산 소스 실패: 10분 후 재시도하며 최근 30분 이내의 마지막 성공값만 사용
- 서버 재시작: 인메모리 캐시는 초기화되지만 브라우저 관심 종목은 Local Storage에 남음

## 시가총액과 괴리

```text
공식 시가총액 = 금융위원회 mrktTotAmt
추정 시가총액 = Hyperliquid markPx × KRW-USDC 근사 환산값 × 상장주식수
국내 괴리율 = (마감 시점 추정 원화가 / 같은 날 공식 종가 - 1) × 100
미국 등락률 = (markPx / prevDayPx - 1) × 100
```

국내 괴리율은 **같은 시점의 두 값**만 비교한다. 현재가와 전일 종가를 맞대면 그 사이 등락이
섞여 추정 오차를 잴 수 없기 때문에, 공식 종가 기준일의 정규장 마감(15:30 KST)에 마감된
Hyperliquid 15분 캔들(`candleSnapshot`)과 같은 시각 이전 30분 안의 업비트 KRW-USDC 마지막 체결가를 써서 계산한다.
공식 종가 기준일이 바뀔 때만 다시 계산하고, 실패하면 30분 뒤 재시도하며 직전 값을 유지한다.

미국 종목은 대조할 공식 종가가 없으므로 괴리율이 아니라 24시간 등락률을 표시한다.

왕좌 교체 문장과 1·2위 비교 막대는 금융위원회 공식 종가·시가총액만 사용한다. 막대는 두 종목
합계 대비 점유율로 그리며 가운데 50% 선이 역전선이다.

## 배포

- 프론트: Vercel
- 백엔드: 기존 EC2 Docker 컨테이너
- 이미지: GitHub Actions → GHCR, 기존 Watchtower 자동 반영
- DNS/CDN: Cloudflare

실제 AWS 변경이나 새 유료 리소스 생성은 별도 승인 없이는 수행하지 않는다.
