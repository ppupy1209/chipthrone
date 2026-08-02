# 관심 종목과 수요 기반 시세 수집

## 실제로 해결한 낭비

기존에도 단일 polling과 SSE fan-out이 있어 사용자별 API 호출은 없었다. 새 문제는 지원 종목이 늘 때 구독자가 없는 종목까지 고정 polling하는 것이었다.

현재 구조는 연결별 관심 종목을 공유 활성 집합으로 합치고 마지막 구독 종료 15초 뒤 제거한다. Hyperliquid는 전체 universe batch라 한 cycle의 외부 호출은 활성 고유 종목 수에도 늘지 않는다. 금융위원회 종목별 호출은 활성 국내 종목만 일 단위로 수행한다.

```text
사용자 N명 × 관심 종목 M개
  → 활성 고유 종목 Set
  → Hyperliquid batch 1회 / 3초
  → 연결별 종목으로 SSE fan-out

연결 0
  → 15초 grace
  → 활성 종목 0, 시세 API 호출 0
```

## 관심 종목 정책

- 삼성전자와 SK하이닉스는 핵심 종목으로 항상 포함한다.
- 미국 AI·반도체는 최대 6개, 전체 연결 상한은 8개다.
- 반도체·M7·AI 관련 각 카테고리에 최소 7개 후보를 노출한다.
- 선택은 Local Storage에 저장하며 인증·서버 저장은 범위에서 제외한다.
- 잘못된 저장값과 API 코드는 프론트와 백엔드 양쪽에서 검증한다.

## 정리 안전성

- completion/error/timeout/send 실패가 겹쳐도 연결 정리는 `AtomicBoolean`로 한 번만 실행한다.
- 종목별 subscriber count는 동시 자료구조로 관리하며 0 아래로 내려가지 않는다.
- grace 작업은 첫 구독 복귀 시 무효화하므로 순간 재연결 때문에 수집이 끊기지 않는다.
- 새 구독의 캐시가 5초보다 오래되면 즉시 갱신 요청을 병합한다. 연결 수만큼 scheduler 작업을 만들지 않는다.

## Prometheus 지표

| 지표 | 의미 |
|---|---|
| `chipthrone_sse_connections` | 현재 SSE 연결 수 |
| `chipthrone_quote_symbol_subscribers{symbol}` | 지원 종목별 구독자 수 |
| `chipthrone_quote_active_symbols` | 현재 수집 고유 종목 수 |
| `chipthrone_quote_subscribed_active_symbols` | grace 포함 활성 종목 수 |
| `chipthrone_quote_external_api_calls_total{source,operation}` | 데이터 소스별 실제 호출 수 |
| `chipthrone_quote_polls_total{mode,result}` | polling 성공·실패 |
| `chipthrone_quote_collection_duration_seconds` | 수집 시간 |
| `chipthrone_quote_delivery_latency_seconds` | 수집 시각부터 SSE 전달까지 지연 |
| `chipthrone_sse_orphan_emitters` | 종료됐으나 남은 Emitter |
| `chipthrone_container_cpu_usage_seconds` | 컨테이너 CPU 누적 사용량 |
| `chipthrone_container_memory_current_bytes` | 컨테이너 현재 메모리 |

사용자·연결 ID는 태그에 넣지 않는다. `symbol`도 서버가 정한 유한 지원 목록만 사용한다.

## Fake 부하 테스트

실제 금융위원회·한국수출입은행·Hyperliquid 쿼터와 운영 트래픽을 건드리지 않도록 `loadtest/fake_quote_server.py`가 같은 응답 형식과 호출 counter를 제공한다. 인위적 지연은 넣지 않는다. 표준 라이브러리 SSE 클라이언트가 연결을 실제로 유지한다.

측정 시나리오:

1. 전체 20개 종목 고정 수집
2. 활성 2개 종목만 수집
3. 200명이 동일 1개 종목 구독
4. 200명이 20개 종목에 분산
5. 전원 연결 종료와 grace 정리
6. 24시간 추정 수집
7. 미국 2개 종목 공유 batch

2026-08-02 로컬 Darwin arm64에서 각 실시간 시나리오를 12초 동안 측정했다. 200개 연결은 초기 서비스 규모에서 동일 종목 집중과 20종목 분산을 함께 검증하기 위한 값이다. 결과는 다음과 같다.

| 시나리오 | SSE | 활성 종목 | 금융위 호출 | Hyper batch | CPU 평균 | RSS 평균 | 전달 p95 | 오류율 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 전체 20종목 고정 | 200 | 20 | 20 | 5 | 26.53% | 258.93 MiB | 115.42 ms | 0% |
| 활성 2종목 | 200 | 2 | 2 | 5 | 15.26% | 266.01 MiB | 62.46 ms | 0% |
| 200명이 동일 1종목 | 200 | 1 | 1 | 5 | 14.35% | 254.30 MiB | 66.96 ms | 0% |
| 200명이 20종목 분산 | 200 | 20 | 20 | 5 | 19.48% | 261.67 MiB | 74.81 ms | 0% |
| 24시간 추정 2종목 | 200 | 2 | 2 | 5 | 16.42% | 265.25 MiB | 62.47 ms | 0% |
| 미국 2종목 공유 | 200 | 2 | 0 | 5 | 13.66% | 264.95 MiB | 61.61 ms | 0% |

고정 20종목 대비 활성 2종목은 금융위원회형 종목별 호출이 20회에서 2회로 90% 감소했다. 전체 Fake 외부 호출은 26회에서 8회로 69.2% 감소했다. 같은 측정에서 JVM CPU 평균은 42.5%, 전달 p95는 45.9% 낮았지만 RSS는 2.7% 증가했으므로 메모리 감소는 주장하지 않는다.

200명이 같은 종목을 구독해도 Hyperliquid는 5회, 금융위원회형 호출은 1회였다. `200명 × 1종목 × 5 cycle = 1,000회`가 아니다. 미국 종목은 금융위원회 호출 없이 Hyperliquid batch 5회만 발생했다.

전원 종료 시 400개 symbol subscription을 정리했고, 15초 grace 뒤 활성 종목 0, 불필요한 외부 호출 0을 측정했다. 별도 재측정에서 미국 시나리오도 400개 정리, 남은 subscription 0, orphan Emitter 0을 확인했다. 원본 수치는 [load-test-results.json](load-test-results.json)에 있다. 로컬 측정은 EC2 성능으로 해석하지 않는다.

## 비용 표현 범위

- 고정 EC2 사양과 요금이 바뀌지 않으면 AWS 월 비용 절감액은 주장하지 않는다.
- 주장 가능한 것은 불필요한 외부 API 호출 감소, 서버 작업 감소, 동일 인스턴스의 수용 여력 증가 가능성, 조기 scale-out 비용 회피다.
- 운영 EC2 개선 전후를 동일 조건으로 측정하지 않았다면 CPU·메모리 감소율과 최대 연결 증가량을 만들지 않는다.
- Hyperliquid batch 특성상 현재 관계는 `외부 호출 ≈ 활성 종목 수`보다 강한 `외부 호출 = 활성 종목이 있으면 polling당 1회`다.

## 남은 한계

- Hyperliquid 값은 실제 주식 체결가가 아니다.
- 토스증권 API 승인 후에도 시세 표시·재배포 약관을 확인하기 전에는 연결하지 않는다.
- 인스턴스가 여러 대면 각 인스턴스가 별도 polling한다. 수평 확장 시 단일 collector 또는 pub/sub가 필요하다.
- 서버 재시작 시 인메모리 시세·구독 집계는 초기화된다.

## 부분 캡처

전체 화면은 저장하지 않고 확인 대상별로 나눴다.

- [전일 확정 시세와 왕좌 교체 조건](images/official-confirmed-prices.jpg)
- [24시간 추정 시세와 공식 고시환율](images/hyperliquid-estimates-fx.jpg)
- [미국 반도체 종목 선택](images/us-watchlist-picker.jpg)
- [M7·AI 관련 종목 목록](images/us-watchlist-categories.jpg)
- [미국 Hyperliquid 추정 카드](images/us-hyperliquid-cards.jpg)
- [Grafana 외부 API 호출률](images/grafana-external-api-calls-current.jpg)
- [Grafana SSE 전달 지연 p95](images/grafana-sse-delivery-p95-current.jpg)
