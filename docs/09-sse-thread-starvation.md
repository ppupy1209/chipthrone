# SSE 장기 연결의 Tomcat worker 고갈 재현

## 검증 질문

동기식 Servlet handler가 SSE 연결을 끝날 때까지 붙잡고 있으면, 연결 수가 Tomcat worker 수에 도달했을 때 일반 API도 응답하지 못하는가? 현재 CHIP·THRONE의 `SseEmitter` 경로는 더 많은 연결에서도 worker를 반환하는가?

운영 트래픽과 외부 API를 건드리지 않고 localhost에서 실제 Spring Boot 애플리케이션 JAR을 실행했다. Tomcat worker는 일반적인 기본값과 같은 200개로 두고, 동기 SSE 200개와 운영 코드의 비동기 SSE 400개를 차례로 연결했다. 시세 원본만 localhost fake server로 고정했다.

## 재현 장치

- `sse-thread-lab` 프로필에서만 `GET /__lab/blocking-stream`을 등록한다.
- 재현 handler는 10초 heartbeat를 직접 쓰면서 요청 thread에서 최대 60초 대기한다.
- 비교 대상은 운영 코드와 같은 `GET /api/stream`과 `SseEmitter`다.
- 외부 probe가 1초마다 `/api/health`를 호출하며 500ms 안에 응답하는지 측정한다.
- Prometheus가 1초마다 Actuator와 부하 생성기 metric을 수집하고 Grafana가 연결 수, Tomcat worker, Health, JVM 메모리, CPU를 표시한다.
- `jcmd Thread.print`에서 재현 handler stack 수를 별도로 센다.
- 기본 프로필에서는 재현 endpoint가 404인지 자동 테스트한다.

```bash
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootJar
cd ..

docker compose -f docker-compose.sse-thread-lab.yml up -d
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  python3 loadtest/run_sse_capacity_test.py

# http://localhost:13001/d/chipthrone-sse-thread-lab/chip-throne-sse-worker
docker compose -f docker-compose.sse-thread-lab.yml down
```

## 동기식 SSE 200개: worker 고갈

![동기식 SSE 연결이 200개로 증가하면서 Tomcat worker와 Health가 함께 고갈되는 Grafana 실측](/docs/images/grafana-sse-worker-blocking-200.png)

연결을 25개씩 늘리는 동안 busy worker가 같은 방향으로 증가했다. 마지막 scrape에서는 200개 current worker 중 150개가 busy로 관측됐고, 다음 구간부터 애플리케이션이 Prometheus scrape에도 응답하지 못했다. 그래프의 scrape와 외부 Health 성공률이 함께 0으로 떨어진 이유다.

부하 생성기에서는 동기 연결 200개를 모두 열었고, 같은 시점의 thread dump에서도 `BlockingSseLabController.blockingStream` stack 200개를 확인했다. 포화에 도달한 뒤 Health probe 13회가 연속으로 500ms timeout에 걸렸다. Nginx가 앞에 있다면 이 상태가 upstream timeout까지 지속되는 순간 외부에서는 504로 보일 수 있다.

## 운영 `SseEmitter` 경로 400개: 연결과 worker 수명 분리

![운영 SseEmitter 경로에 SSE 400개를 연결해도 Tomcat worker와 Health가 안정적인 Grafana 실측](/docs/images/grafana-sse-emitter-400.png)

동기 연결을 닫고 애플리케이션이 회복된 뒤 실제 `/api/stream`에 SSE 400개를 연결했다. Actuator의 `chipthrone_sse_connections`도 400을 기록했지만 busy worker는 0으로 돌아왔고, blocking handler stack도 0개였다.

400개 연결을 올리고 유지하는 동안 Health 28회가 모두 성공했다. p50은 3.44ms, p95는 5.25ms, 최대는 5.35ms였다. 로컬 프로세스의 JVM 메모리는 그래프상 약 160~190MiB 범위였고 CPU도 낮게 유지됐다.

| 비교 | 동기 handler | 운영 `SseEmitter` |
|---|---:|---:|
| 연결된 SSE 클라이언트 | 200 | 400 |
| 동기 handler stack | 200 | 0 |
| 포화/유지 구간 Health | 0/13 | 28/28 |
| Health p50 | timeout | 3.44ms |
| Health p95 | timeout | 5.25ms |
| Health 최대 | timeout | 5.35ms |

원본 probe 표본과 집계값은 `docs/sse-capacity-load-results.json`에 저장한다. Grafana 이미지는 이 실행에서 Prometheus가 수집한 실제 시계열을 캡처한 것이다.

## 현재 설계에서 각 장치가 해결하는 문제

- `SseEmitter`: 장기 연결의 수명과 Servlet worker 점유 시간을 분리한다. 이번 worker 고갈의 직접 해결책이다.
- 단일 polling + fan-out: 연결마다 외부 시세 API를 호출하지 않고 한 번 수집한 snapshot을 구독자에게 나눈다.
- heartbeat: 주기적으로 write해 끊어진 연결을 발견하고 emitter와 구독 상태를 정리한다. 네트워크 단절을 항상 즉시 감지하는 장치는 아니다.
- `proxy_buffering off`: Nginx가 event를 모아서 늦게 보내지 않도록 한다. 애플리케이션 worker 고갈을 해결하지는 않는다.
- HTTP/2: 브라우저와 프록시 사이의 연결 multiplexing에 유리하지만, 동기 handler가 점유한 Servlet worker를 반환시키지는 않는다.

## 한계

- 실제 애플리케이션 JAR과 worker 200개를 사용했지만 단일 Apple Silicon localhost 결과다. EC2 인스턴스의 절대 용량이나 최대 동시 접속 수를 뜻하지 않는다.
- 시세 upstream은 fake server이며 Nginx와 HTTP/2를 경로에 넣지 않았다. 따라서 외부 504 발생 시각, 프록시 버퍼링, 브라우저 도메인당 연결 제한은 별도 검증 대상이다.
- `SseEmitter`도 연결 객체와 전송 버퍼 등 메모리를 사용한다. worker가 반환된다는 사실이 연결 수 제한이 없다는 뜻은 아니다.
- 이번 목적은 worker 고갈 가설의 원인 분리다. 운영 용량 산정에는 실제 EC2에서 더 긴 soak test와 네트워크·메모리 한계 측정이 추가로 필요하다.
