# SSE 장기 연결의 Tomcat worker 고갈 재현

## 검증 질문

동기식 Servlet handler가 SSE 연결을 끝날 때까지 붙잡고 있으면, 연결 수가 Tomcat worker 수에 도달했을 때 일반 API도 응답하지 못하는가? 현재 CHIP·THRONE의 `SseEmitter` 경로는 같은 연결 수에서 worker를 반환하는가?

운영 트래픽과 외부 API를 건드리지 않기 위해 localhost fake 시세 서버를 사용했다. 기본 worker 수 전체를 재현할 필요는 없으므로 Tomcat 최대 worker를 8개로 축소하고 SSE 연결도 8개로 맞췄다.

## 재현 장치

- `sse-thread-lab` 프로필에서만 `GET /__lab/blocking-stream`을 등록한다.
- 재현 handler는 250ms마다 comment를 flush하면서 요청 thread에서 최대 20초 대기한다.
- 실제 해결 경로는 운영 코드와 같은 `GET /api/stream`과 `SseEmitter`를 사용한다.
- `jcmd Thread.print`에서 재현 handler stack 수를 세고, 별도 클라이언트로 `/api/health`를 호출한다.
- 기본 프로필에서는 재현 endpoint가 404인지 자동 테스트한다.

```bash
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootJar
cd ..
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  python3 loadtest/run_sse_thread_starvation_test.py
```

## 동기식 SSE: worker 고갈

![동기식 SSE 8개가 Tomcat worker 8개를 모두 점유해 Health 요청 5건이 전부 실패한 로컬 실험](/docs/images/sse-thread-starvation-before.png)

동기 handler의 첫 event를 받은 연결 8개가 유지되는 동안 thread dump에도 `BlockingSseLabController.blockingStream` stack이 8개 남았다. 같은 시점의 Health 요청은 350ms 제한 안에 하나도 응답하지 못했다. Nginx가 앞에 있다면 upstream 응답 제한을 넘는 순간 504로 보일 수 있지만, 이번 실험은 원인을 분리하기 위해 애플리케이션에 직접 요청하고 timeout으로 판정했다.

## 비동기 SSE: 연결과 worker 수명 분리

![같은 SSE 8개를 SseEmitter로 유지하면서 Health 요청 20건이 모두 성공한 로컬 실험](/docs/images/sse-thread-starvation-after.png)

`SseEmitter`는 MVC async 처리를 시작하고 controller 반환 뒤 Servlet worker를 풀에 돌려준다. 연결 8개는 계속 유지됐지만 동기 재현 handler stack은 0개였고, Health 요청 20건이 모두 성공했다.

| 비교 | 동기 handler | `SseEmitter` |
|---|---:|---:|
| 연결된 SSE 클라이언트 | 8 | 8 |
| 동기 handler stack | 8 | 0 |
| Health 성공 | 0/5 | 20/20 |
| Health 지연 p50 | 측정 불가 | 0.75ms |
| Health 지연 p95 | 측정 불가 | 0.99ms |
| Health 최대 지연 | 측정 불가 | 1.38ms |

원본 측정값은 `docs/sse-thread-starvation-results.json`에 저장한다.

## 현재 설계에서 각 장치가 해결하는 문제

- `SseEmitter`: 장기 연결의 수명과 Servlet worker 점유 시간을 분리한다. 이번 worker 고갈의 직접 해결책이다.
- 단일 polling + fan-out: 연결마다 외부 시세 API를 호출하지 않고 한 번 수집한 snapshot을 구독자에게 나눈다.
- heartbeat: 주기적으로 write해 끊어진 연결을 발견하고 emitter와 구독 상태를 정리한다. 네트워크 단절을 항상 즉시 감지하는 장치는 아니다.
- `proxy_buffering off`: Nginx가 event를 모아서 늦게 보내지 않도록 한다. 애플리케이션 worker 고갈을 해결하지는 않는다.
- HTTP/2: 브라우저와 프록시 사이의 연결 multiplexing에 유리하지만, 동기 handler가 점유한 Servlet worker를 반환시키지는 않는다.

## 한계

- localhost와 8 worker로 임계점을 축소한 결정론적 재현이며 운영 동시 접속량을 예측하는 용량 시험은 아니다.
- Nginx와 HTTP/2를 경로에 넣지 않아 504와 브라우저 도메인당 연결 제한은 측정하지 않았다.
- `SseEmitter`도 연결 객체와 전송 버퍼 등 메모리는 사용한다. worker가 반환된다는 사실이 연결 수 제한이 없다는 뜻은 아니다.
