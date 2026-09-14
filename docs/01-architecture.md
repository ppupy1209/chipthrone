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
  ├─ 종목별 최신값 인메모리 캐시
  └─ 연결별 필터링 SSE fan-out
          │
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

Hyperliquid는 일괄 API이므로 한 polling cycle의 호출 수는 사용자 수와 선택 종목 수에 관계없이 1회다.

## 캐시와 장애 폴백

- Hyperliquid 실패: 마지막 성공 종목 스냅샷 유지, 연속 장애/복구 상태 전이만 Slack 알림
- 업비트: 서버에서만 5분 간격으로 재조회. 원본 응답과 환율값은 외부 API와 화면에 노출하지 않음
- 환산 소스 실패: 10분 후 재시도하며 최근 30분 이내의 마지막 성공값만 사용
- 서버 재시작: 인메모리 캐시는 초기화되지만 브라우저 관심 종목은 Local Storage에 남음

## 시가총액과 왕좌 판정

```text
추정 시가총액 = Hyperliquid markPx × KRW-USDC 근사 환산값 × 상장주식수
미국 등락률 = (markPx / prevDayPx - 1) × 100
```

왕좌 배지, 왕좌 교체 문장, 1·2위 비교 막대는 모두 추정 시가총액을 사용한다. 막대는 두 종목
합계 대비 점유율로 그리며 가운데 50% 선이 역전선이다. 국내 종목 상장주식수는 `application.yml` 고정값이다.

금융위원회 공식 종가·시가총액과 이를 기준으로 한 마감 시점 괴리율은 2026-09-14에 제외했다.
사유는 [데이터 소스](02-data-sources.md)에 정리했다.

## 배포

- 프론트: Vercel
- 백엔드: 기존 EC2의 blue/green Docker 슬롯
- 이미지: GitHub Actions → GHCR, systemd timer가 60초마다 pull 확인
- 전환: 비활성 슬롯 Readiness 확인 → Nginx reload → 기존 SSE 연결 최대 310초 drain
- 롤백: 전환 후 후보 Readiness가 연속 3회 실패하면 이전 슬롯으로 upstream 복구
- DNS/CDN: Cloudflare

실제 AWS 변경이나 새 유료 리소스 생성은 별도 승인 없이는 수행하지 않는다.
