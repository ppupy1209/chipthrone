# 관심 종목과 수요 기반 시세 수집

## 1. 기존 구조에서 확인한 실제 낭비

분석 기준은 변경 전 `main` 코드와 운영 EC2의 읽기 전용 표본이다.

- 지원 종목은 `application.yml`의 `chipthrone.quote.assets`에서 관리했다. 변경 전에는 삼성전자 `005930`, SK하이닉스 `000660` 두 종목이었다. 현재는 CHIP THRONE의 범위에 맞춰 샌디스크 `SNDK`, 마이크론 `MU`, 브로드컴 `AVGO`를 더한 한·미 반도체 5종목만 지원한다.
- 단일 `QuotePollingWorker`와 SSE fan-out은 이미 구현돼 있었다. 사용자별 polling은 없었고, 같은 스냅샷을 모든 Emitter에 전달했다.
- KIS 현재가는 실제 거래 모드에서 지원 종목마다 polling 1회씩 호출했다. Hyperliquid `metaAndAssetCtxs`는 전체 심볼 일괄 조회 1회였다.
- 문제는 `@Scheduled` polling이 구독자 유무와 무관하게 3초마다 모든 지원 종목을 수집했다는 점이다. 지원 종목이 늘면 아무도 보지 않는 KIS 종목 호출도 선형 증가한다.
- 문서에는 환율 하루 1회라고 적혀 있었지만 변경 전 코드는 매 polling마다 실제 환율 API를 호출했다. 이번 변경에서 KST 날짜 단위 캐시와 실패 시 1분 재시도를 적용했다.
- 종가 캐시는 최근 확정 거래일이 바뀔 때만 갱신하고, 실패 시 30초 뒤 재시도한다. 투자의견은 기존대로 하루 1회 캐시한다.
- Emitter는 completion, timeout, error 콜백에서 제거됐지만, 연결별 종목과 공유 구독 수명은 관리하지 않았다.

기존 시장 시간과 실제 호출은 유지했다.

| 시장 상태 | KST | 실제 시세 | 수집 주기 |
|---|---:|---|---:|
| PREMARKET | 08:00–09:00 | KIS NXT | 3초 |
| REGULAR | 09:00–15:30 | KIS KRX | 3초 |
| NXT | 15:40–20:00 | KIS NXT | 3초 |
| ESTIMATE/휴장 | 그 외 | Alpaca IEX 종가 또는 Hyperliquid batch × 일일 FX | 60초 |

08:50–09:00, 15:20–15:40의 기존 no-trade break 동결과 마지막값 폴백, Slack 상태 전이 알림은 그대로 유지했다.

## 2. 관심 종목 UI

- `GET /api/assets`로 검색 가능한 지원 종목을 제공한다.
- 삼성전자와 SK하이닉스는 서비스의 핵심 비교 대상으로 고정한다. 샌디스크·마이크론·브로드컴 중 최대 2개를 관심 종목으로 추가한다.
- 선택값은 `chipthrone.watchlist.v1` Local Storage에 저장한다. 인증과 서버 저장은 범위에서 제외했다.
- 저장값은 지원 목록으로 다시 검증하고 중복·잘못된 코드를 제거한다.
- 국장 두 종목은 본문에서 시가총액 막대·역전 계산·투자의견까지 비교한다. 미국 반도체 주가는 데스크톱 우측 240px 컴팩트 레일, 모바일 본문 하단의 보조 영역에 표시하며 국장 왕좌 계산에는 포함하지 않는다.
- 모바일에서는 검색·칩·국장 카드·미장 보조 영역이 한 열로 줄어든다.
- UI는 `연결 중`, `SSE 연결됨`, `재연결 중`, 마지막 갱신 시각과 `실시간`, `장 마감`, `추정 시세`를 구분한다. 국장/미장 목록과 KIS/Alpaca IEX/Hyperliquid 출처도 함께 표시한다.

백엔드는 `/api/quotes`와 `/api/stream`의 종목 코드를 다시 검증한다. 빈 목록, 미지원 코드, 연결당 4개 초과는 HTTP 400이다.

## 3. 최종 구독·수집 흐름

```text
GET /api/stream?symbols=005930,000660
  → 지원 종목/최대 개수 검증
  → 연결별 Set<String> 보관
  → 종목별 공유 subscriber count 증가
  → 캐시가 5초보다 오래됐으면 1초 scheduler tick에 즉시 갱신 요청 병합
  → 활성 고유 종목만 QuoteService.refresh(Set)
      KIS: 고유 종목별 1회
      Alpaca: 활성 미장 종목 snapshot batch 1회
      Hyperliquid: 국장 폴백·미장 누락 때만 batch 1회
      FX: KST 날짜당 1회
      종가: 확정 거래일당 1회
  → 연결별 종목으로 snapshot 필터링 후 SSE fan-out
  → 종료/오류/timeout/send 실패에서 idempotent cleanup
  → 마지막 구독 종료 후 15초 grace
  → grace 만료 시 활성 집합에서 제거, 이후 외부 호출 0
```

단일 scheduler만 사용한다. 첫 구독의 즉시 갱신 요청은 `AtomicBoolean`으로 병합해 동시 연결 수만큼 작업이 생기지 않는다. 연결 정리는 `AtomicBoolean`으로 한 번만 수행해 중복 콜백에도 subscriber count가 음수가 되지 않는다.

구독이 없을 때 quote collection만 멈춘다. Spring health endpoint, Docker healthcheck, EC2 systemd 복구, GitHub 외부 liveness는 polling 여부와 독립적으로 계속 동작한다.

## 4. Prometheus 지표

| 지표 | 의미 |
|---|---|
| `chipthrone_sse_connections` | 현재 SSE 연결 수 |
| `chipthrone_quote_symbol_subscribers{symbol}` | 지원 종목별 구독자 수 |
| `chipthrone_quote_active_symbols` | 현재 수집 고유 종목 수 |
| `chipthrone_quote_subscribed_active_symbols` | grace를 포함한 활성 구독 종목 수 |
| `chipthrone_quote_external_api_calls_total{source,operation}` | 실제 HTTP 외부 호출 수 |
| `chipthrone_quote_polls_total{mode,result}` | 시장 상태별 polling 성공·실패 |
| `chipthrone_quote_collection_duration_seconds` | 시세 수집 시간 |
| `chipthrone_quote_delivery_latency_seconds` | snapshot 시각부터 SSE send까지 지연 |
| `chipthrone_sse_orphan_emitters` | closed 상태인데 broadcaster에 남은 Emitter |
| `chipthrone_container_cpu_usage_seconds` | cgroup 컨테이너 CPU 누적 사용 시간 |
| `chipthrone_container_memory_current_bytes` | cgroup 컨테이너 현재 메모리 |

사용자 ID, 연결 ID, 임의 입력값은 태그에 넣지 않았다. `symbol` 태그도 서버 설정의 유한한 지원 목록에 대해서만 시작 시 등록한다. 지원 목록이 수천 개로 커지면 종목별 gauge를 제거하고 상위 집계/로그로 바꿔야 한다.

## 5. Fake 시세 서버 부하 테스트

KIS 호출 제한을 건드리지 않도록 `loadtest/fake_quote_server.py`가 KIS·Alpaca·Hyperliquid·FX 호환 응답과 정확한 호출 counter를 제공한다. 인위적 지연은 넣지 않았다. Python 표준 라이브러리 클라이언트가 200개 SSE 연결을 실제로 유지해 k6의 별도 SSE 확장에 의존하지 않는다.

초기 서비스에서 브라우저 1개가 SSE 1개를 열고 기본 관심 종목 2개를 선택한다고 가정해 200명 × 2종목으로 잡았다. 20개 지원 종목은 동일 종목 집중과 고르게 분산된 최악 조건을 모두 비교할 수 있는 최소 규모다. 측정 호스트는 로컬 macOS arm64 JVM이며 EC2 결과로 해석하면 안 된다.

| 시나리오 | 연결 | 활성 고유 종목 | KIS 현재가 호출 | batch 호출 | CPU 평균 | RSS 평균 | 전달 p95 | 오류율 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 전체 20종목 고정 polling | 200 | 20 | 100 | Hyper 5 | 34.18% | 260.54 MiB | 96.73 ms | 0% |
| 활성 2종목만 polling | 200 | 2 | 10 | Hyper 5 | 11.22% | 257.23 MiB | 80.07 ms | 0% |
| 200명이 동일 1종목 구독 | 200 | 1 | 5 | Hyper 5 | 9.33% | 260.74 MiB | 61.19 ms | 0% |
| 200명이 20종목에 분산 | 200 | 20 | 100 | Hyper 5 | 20.38% | 265.15 MiB | 76.39 ms | 0% |
| 전원 연결 종료 | 200→0 | 2→0 | grace 뒤 0 | grace 뒤 0 | 24.45% | 265.10 MiB | 65.77 ms | 0% |
| 장 마감 60초 polling | 200 | 2 | 0 | Hyper 2/65초 | 0.68% | 191.96 MiB | 72.80 ms | 0% |
| 미장 2종목 공유 batch | 200 | 2 | 0 | Alpaca 5, Hyper 0 | 8.52% | 255.70 MiB | 61.52 ms | 0% |

12초 실시간 시나리오는 5개 poll cycle이었다. 따라서 측정 관계는 다음과 같다.

```text
고정: 20 활성 종목 × 5회 = KIS 현재가 100회
수요:  2 활성 종목 × 5회 = KIS 현재가 10회
공유:  1 활성 종목 × 5회 = KIS 현재가 5회 (사용자는 200명)
분산: 20 활성 종목 × 5회 = KIS 현재가 100회
       사용자 200명 × 관심 종목 2개 × 5회 = 2,000회가 아님
미장:  2 활성 종목, 사용자 200명이어도 Alpaca batch 5회
       KIS 0회, Hyperliquid 0회
```

고정 20종목 대비 활성 2종목은 KIS 현재가 호출 90%, 로컬 JVM CPU 평균 67.2%가 감소했다. RSS는 1.3%, 전달 p95는 17.2% 낮았다. 전원 종료 테스트는 400개 symbol subscription을 정리했고, 15초 grace 뒤 활성 종목 0, 불필요한 호출 0을 측정했다. 미장 시나리오는 200개 연결이 같은 2종목을 구독해도 polling 5회에 Alpaca batch 5회만 발생했다. 원본 수치는 [load-test-results.json](load-test-results.json)에 있다.

## 6. 운영 EC2 배포·SSE 검증과 비용 표현 범위

2026-08-02 10:34 KST에 배포 전 운영 EC2를 한 번 읽기 전용으로 확인했다.

- 2 vCPU, 메모리 916 MiB, swap 없음
- 호스트 메모리 used 400 MiB, available 378 MiB
- `chipthrone-api` 컨테이너 CPU 1.40%, 메모리 231.3 MiB, PID 36
- `watchtower` 메모리 15.66 MiB
- `vmstat` 표본 CPU idle 98–100%, 8080 established connection 1
- `/api/health`는 `UP`

사용자 승인 후 기존 `main` 배포 경로만 사용했다. GitHub Actions가 기존 GHCR 이미지에 push했고, 기존 EC2 Watchtower가 교체했다. 새 EC2, 로드 밸런서, 데이터베이스, 유료 모니터링은 만들지 않았다. 배포 뒤 `/api/health=UP`, `/api/assets` 5종목, Prometheus cgroup 지표 노출을 확인했다.

실제 KIS 대신 운영 서버가 장 마감 미국 종목 `SNDK,MU`를 Hyperliquid batch로 한 번 갱신하도록 모든 연결이 같은 두 종목을 구독했다. 연결은 로컬 표준 라이브러리 SSE 클라이언트에서 열었고, 연결 생성 뒤 12초 동안 측정했다.

| 운영 EC2 시나리오 | 최대 SSE | 활성 고유 종목 | 오류율 | 외부 호출 | CPU | 메모리 | 전달 p95 |
|---|---:|---:|---:|---|---:|---:|---:|
| 200개 연결 | 200 | 2 | 0% | KIS 0, Hyper 1 | JVM process 2.85% | JVM 평균 149.00 MiB | 초기 event 제외로 미산출 |
| 400개 연결·cgroup 계측 | 400 | 2 | 0% | KIS 0, Hyper 1 | container 7.49% | container 평균 268.23 MiB | 849.65 ms |
| 600개 연결 | 600 | 2 | 0% | KIS 0, Hyper 1 | JVM process 4.00% | JVM 평균 178.20 MiB | 222.80 ms |

600개까지 실패가 없었으므로 현재 수용량은 **최소 600 SSE 연결**이다. 포화·실패점까지 올리지 않아 절대 최대치는 아니다. 개선 전 최대 연결 수도 측정하지 않았으므로 “최대 연결 수 증가량”은 산출할 수 없다. 이를 만들려면 이전 이미지를 운영에 되돌리거나 같은 사양의 추가 인스턴스가 필요해 이번 무중단·무비용 범위에서 제외했다.

400개 종료 뒤 heartbeat cleanup과 15초 grace가 지난 시점에 SSE 연결 0, 활성 종목 0, 구독 활성 종목 0, orphan Emitter 0을 확인했다. 구독자가 없을 때 추가 KIS·Hyperliquid 호출도 없었다. 원본 결과는 [ec2-load-test-results.json](ec2-load-test-results.json)에 있다.

배포 전 idle Docker 표본은 CPU 1.40%, 메모리 231.3 MiB였다. 배포 후 400개 연결을 정리한 10초 cgroup 표본은 CPU 1.02%, 메모리 평균 275.07 MiB였다. CPU는 27.0% 낮았지만 서로 다른 짧은 관측창이므로 추세 참고값이다. 메모리는 18.9% 높아 **메모리 감소를 주장할 수 없다**. 새 컨테이너 기동 직후 214.70 MiB도 관측됐지만 cold/warm 상태가 달라 감소율 근거에서 제외했다.

고정 EC2 사양과 요금은 바뀌지 않았으므로 확인 가능한 월 AWS 비용 절감액은 **0원**이다. 새 유료 리소스는 생성하지 않았고, 짧은 테스트는 기존 인스턴스에서 수행했다. AWS Billing/크레딧에는 접근하지 못했으므로 최종 청구 여부는 콘솔에서 확인해야 한다. 주장 가능한 효과는 다음뿐이다.

- 사용자 수와 무관하게 Hyperliquid batch가 polling당 1회인 구조 확인
- 활성 고유 종목에 비례하는 KIS 호출 구조와 구독자 0일 때 불필요한 호출 0
- 현 EC2에서 최소 600개 SSE 연결 수용 확인
- 트래픽 증가에 따른 조기 scale-out 비용 회피 가능성

## 7. 검증과 남은 한계

완료한 검증:

- 백엔드 `./gradlew clean test bootJar`: 성공. 55개 테스트에 구독 concurrency/중복 close/grace cleanup, polling, KIS·Alpaca·Hyperliquid fallback, Slack 상태 전이, cgroup v1/v2 계측이 포함된다.
- 프론트 `npm test`, `npm run lint`, `npm run build`: 성공.
- Docker capture stack 5개 서비스: backend healthy, frontend, fake quotes, Prometheus, Grafana 실행 확인.
- 실제 브라우저: Local Storage 선택 유지, live/estimate 상태, 마지막 갱신, backend 중단 시 `재연결 중`, 재기동 뒤 `SSE 연결됨` 복구 확인.
- 미지원 종목 `/api/stream?symbols=999999`: HTTP 400.
- Prometheus: SSE 연결 1, orphan Emitter 0 확인.
- Fake 부하 일곱 시나리오: 최대 SSE 200개, 오류율 0%, polling 실패 0.
- GitHub CI 백엔드·프론트·복구 스크립트: 성공.
- 운영 EC2: 최대 600 SSE 연결, 연결 오류 0%, KIS 호출 0, 종료 후 SSE/활성 종목/orphan Emitter 0.

남은 한계:

- 서버 재시작 시 quote cache와 구독 집계는 초기화된다. Local Storage만 브라우저에 남는다.
- 인스턴스가 여러 대면 각 인스턴스가 별도 polling한다. 수평 확장 시 Redis pub/sub 또는 단일 collector가 필요하다.
- Hyperliquid는 원천 특성상 batch 1회가 필요해 KIS처럼 활성 종목 수와 정확히 비례하지 않는다.
- 공휴일은 정적 목록이고 종가의 최근 거래일 계산은 주말 중심이라 연도별 거래일 캘린더가 필요하다.
- 미국 정규장은 뉴욕시간·DST만 반영하며 휴장일·조기폐장 캘린더가 없다. Alpaca Basic은 IEX 범위라 전체 SIP 시세와 차이가 날 수 있다.
- 개선 전 EC2 최대 연결 수, 장시간 soak, 브라우저별 모바일 접근성, 프록시 idle timeout은 아직 측정하지 않았다.

## 8. 부분 캡처

전체 화면은 저장하지 않고 확인 대상별로 잘랐다.

- [관심 종목 검색·추가](images/watchlist-search-sidebar.png)
- [국장 2개 고정·미장 2개 선택](images/watchlist-selected-sidebar.png)
- [삼성전자·SK하이닉스 실시간 메인](images/live-krx-main.png)
- [미국 반도체 실시간 사이드바](images/live-us-sidebar.png)
- [국장 시가총액 그래프](images/market-cap-krx-main.png)
- [국장 왕좌 역전 계산](images/reversal-krx-main.png)
- [실시간 연결·마지막 갱신](images/live-connection-main.png)
- [SSE 재연결 중](images/reconnecting-main.png)
- [국장 장 마감·추정 시세](images/closed-estimate-krx-main.png)
- [미장 장 마감 시세](images/closed-us-sidebar.png)
- [장 마감 상태·마지막 갱신](images/closed-connection-main.png)
- [Grafana 외부 API 호출률 패널](images/grafana-external-api-calls-us.png)
- [Grafana SSE 전달 지연 p95 패널](images/grafana-sse-delivery-p95-us.png)
