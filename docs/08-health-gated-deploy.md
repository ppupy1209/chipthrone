# 배포 실패 자동 롤백과 SSE drain

## 문제

기존 Watchtower 배포는 GHCR의 `latest`가 바뀌면 실행 중인 컨테이너를 바로 교체했다. 이미지 빌드가 성공해도 환경변수 누락이나 런타임 초기화 오류로 새 프로세스가 기동하지 못할 수 있다. 이때 Docker 재시작은 같은 실패 이미지를 다시 실행할 뿐 이전 정상 버전으로 돌아가지 못한다.

정상 이미지도 교체 순간에는 기존 SSE 연결을 모두 종료한다. 브라우저가 다시 연결하더라도 배포 시점의 사용자는 짧은 데이터 공백을 겪고, 여러 연결이 한꺼번에 재접속한다.

## 선택지

### Watchtower와 자동 재시작 유지

- 설정이 단순하고 현재 pull 배포 방식 유지 가능
- 새 이미지 자체가 잘못된 경우 같은 이미지의 재시작만 반복
- 정상 배포도 기존 SSE 연결을 즉시 종료

### 상시 blue-green 또는 Kubernetes 도입

- 전환과 롤백 기능을 표준 도구에 맡길 수 있음
- t3.micro 한 대에서 상시 두 JVM과 별도 오케스트레이터를 운영하는 비용이 현재 규모보다 큼

### 배포할 때만 두 슬롯을 겹쳐 실행

- 현재 EC2와 Nginx를 유지하면서 후보 검증과 이전 버전 보존 가능
- 배포 스크립트와 상태 파일을 직접 관리해야 함

현재 규모에서는 세 번째 방식을 선택했다. 운영 부하 테스트에서 컨테이너 메모리를 함께 수집한 400개 SSE 연결 시 최대값은 273.52MiB였고, 600개 연결 시 JVM 메모리 최대값은 182.98MiB였다. 이 실측값을 근거로 배포 슬롯의 JVM 최대 힙을 320MB로 제한해 두 컨테이너가 겹치는 동안 t3.micro의 메모리 여유를 확보한다. 컨테이너 전체 메모리에는 힙 밖의 영역도 포함되므로, 실제 EC2 적용 후에는 두 슬롯 동시 실행 구간의 호스트 메모리를 다시 측정한다.

## 배포 흐름

```text
GitHub Actions
  └─ latest + commit SHA 이미지를 GHCR에 저장
           ↓
EC2 chipthrone-deploy.timer (60초)
  └─ latest pull, 현재 컨테이너 image ID와 비교
           ↓
비활성 슬롯 기동 (blue:18080 또는 green:18081)
  └─ /actuator/health/readiness 확인
           ↓
Nginx upstream 파일 교체 + nginx -t + reload
  ├─ 신규 요청: 새 슬롯
  └─ 기존 SSE: 이전 Nginx worker와 이전 슬롯에서 계속 처리
           ↓
310초 동안 후보 Readiness 감시
  ├─ 정상: 이전 슬롯 종료
  └─ 연속 3회 실패: Nginx를 이전 슬롯으로 롤백
```

Nginx reload는 기존 worker의 연결을 끊지 않고 새 worker에 변경된 upstream을 적용한다. SSE Emitter의 최대 수명은 300초이므로 이전 슬롯을 310초 유지한 뒤 종료한다. 컨테이너 종료 시 Java가 `SIGTERM`을 직접 받도록 Docker entrypoint에 `exec`를 사용하고 Spring Boot Graceful Shutdown을 활성화했다.

## 실패 처리

- 후보가 90초 안에 Ready가 되지 않음: Nginx를 바꾸지 않고 후보 제거
- Nginx 설정 검사 또는 reload 실패: 이전 upstream 복원 후 후보 제거
- 전환 뒤 후보가 연속 3회 응답하지 않음: 이전 upstream으로 자동 롤백
- 실패한 image ID: `/var/lib/chipthrone-deploy/rejected-image`에 기록해 같은 `latest`의 반복 배포 차단
- 배포 중 자동 복구 timer: 공용 deploy lock을 얻지 못하면 재시작하지 않고 배포 스크립트의 판단에 맡김
- 배포 성공·실패·롤백: `SLACK_WEBHOOK_URL`이 있으면 Slack으로 전송

310초 검증이 끝난 뒤 발생한 일반 런타임 장애는 이전 이미지로 자동 롤백하지 않는다. 원인이 배포인지 외부 API나 호스트 문제인지 구분하기 어렵기 때문이다. 이 구간은 기존 health recovery가 활성 슬롯을 제한적으로 재시작하고 외부 liveness가 감지한다.

## 자동 테스트

`infra/deploy-backend.test.sh`가 Docker와 Nginx를 대체한 테스트 실행 파일로 다음 경로를 검증한다.

1. 기존 단일 컨테이너 → blue 전환 → drain 종료 → 기존 컨테이너 제거
2. 다음 이미지 배포 → green 전환 → blue 제거
3. 후보 기동 실패 → 기존 upstream과 컨테이너 유지
4. 전환 후 후보 실패 → 기존 upstream 복구와 실패 후보 제거
5. 실패한 image ID 재확인 → 같은 후보를 다시 실행하지 않음

`infra/health-recovery.test.sh`는 활성 상태 파일이 blue 또는 green을 가리킬 때 해당 슬롯만 재시작하는지 확인한다.

## 기존 EC2 적용

이 단계부터는 EC2 SSH 접근이 필요하다. 코드 작성과 자동 테스트에는 PEM 키가 필요하지 않다.

```bash
curl -fsSL \
  https://raw.githubusercontent.com/ppupy1209/chipthrone/main/infra/install-deploy-agent.sh \
  -o /tmp/install-deploy-agent.sh
sudo bash /tmp/install-deploy-agent.sh
```

설치 스크립트는 기존 Nginx 인증서 설정을 덮어쓰지 않고 `proxy_pass` 대상만 이름 있는 upstream으로 바꾼다. 그다음 Watchtower를 중지하고 두 systemd timer를 활성화한다. 첫 슬롯 배포에 실패하면 기존 `chipthrone-api` 컨테이너와 8080 upstream을 유지한다.

### 운영 검증 체크리스트

- [x] `cat /var/lib/chipthrone-deploy/active-container`에서 활성 슬롯과 image ID 확인
- [x] `cat /etc/nginx/conf.d/chipthrone-api-upstream.conf`의 포트가 활성 상태와 일치
- [x] `systemctl status chipthrone-deploy.timer`와 `chipthrone-health-recovery.timer`가 active
- [x] 배포 중 `free -m`, `docker stats`로 두 슬롯 동시 실행 메모리 확인
- [x] SSE 연결을 유지한 상태에서 배포해 기존 연결이 즉시 종료되지 않는지 확인
- [x] 후보 기동 제한을 의도적으로 줄여 기존 upstream 유지와 Slack 실패 알림 확인
- [x] 전환 후 후보를 일시 정지해 연속 실패 뒤 이전 슬롯 롤백 확인
- [x] 실패 이미지가 다음 timer 실행에서 다시 배포되지 않는지 확인

운영 장애 주입은 실제 사용자가 적은 시간에 진행하고, 각 단계 전에 현재 활성 상태와 upstream 파일을 기록한다.

## 운영 적용 결과 (2026-09-02)

### 전환 전후 상태

운영 전환은 추가 AWS 리소스 없이 기존 EC2, Docker, Nginx에서 수행했다. `main`의 `35100bed20c04ca57a06e4171844fb82a81ac9e8` 반영과 GitHub Actions 성공 뒤 기존 Watchtower가 먼저 새 이미지를 적용했고, 그 상태를 검증한 다음 설치 스크립트로 systemd 슬롯 배포를 활성화했다.

| 구분 | 전환 전 | 전환 후 |
|---|---|---|
| 배포 방식 | Watchtower가 `latest` 감지 후 `chipthrone-api` 즉시 교체 | `chipthrone-deploy.timer`가 비활성 슬롯 검증 후 Nginx 전환 |
| image ID | `sha256:9217dac14ffb0296c437d4ce731855bce575e37442a95f9920392f212873df4c` | `sha256:0225676812d25349b8f5ca52d5f0b049651a178657163371fc13430e3b7b4dc6` |
| immutable 태그 | `89afdb52e4697aae6a6bff4ece47faf6ab86ff17` | `35100bed20c04ca57a06e4171844fb82a81ac9e8` |
| 활성 대상 | legacy `chipthrone-api`, `8080` | `chipthrone-api-green`, `18081` |
| 자동화 | Watchtower + 기존 복구 방식 | deploy timer + health recovery timer |

주요 전환 시각은 다음과 같다.

- 20:34:04 KST: Watchtower가 새 image ID의 legacy 컨테이너 기동
- 20:40:22 KST: 설치 후 최초 blue 슬롯으로 Nginx 전환
- 20:45:36 KST: 310초 drain 뒤 legacy 컨테이너 제거와 최초 슬롯 배포 완료
- 20:50:48.823 KST: 같은 이미지를 강제 배포해 green 슬롯으로 정상 전환
- 20:56:04 KST: 계측에서 이전 blue 슬롯 제거 확인

최종 상태는 Watchtower 0개, green 컨테이너 1개, 두 systemd timer 모두 `active`/`enabled`다. 활성 상태 파일과 Nginx upstream은 `green`/`18081`로 일치했고, 외부 `/api/health`와 내부 Readiness는 200, 실제 시세 snapshot과 SSE `quotes` 이벤트도 정상 응답했다. 애플리케이션 ERROR와 커널 OOM 기록은 없었다.

### 두 슬롯 동시 실행 메모리

| 구간 | 호스트 최대 사용 | 호스트 최소 가용 | 기존 슬롯 최대 | 후보 슬롯 최대 |
|---|---:|---:|---:|---:|
| 최초 legacy → blue 전환 | 660MiB | 111MiB | 약 217MiB | 203.7MiB |
| 정상 blue → green 강제 재배포 | 671MiB | 100MiB | blue 219.6MiB | green 218.5MiB |
| blue pause 롤백 실험 | 658MiB | 114MiB | green 218.4MiB | blue 206.5MiB |

모든 구간에서 OOM은 없었다. 최종 단일 green 상태는 호스트 434MiB 사용, 338MiB 가용, 컨테이너 222.3MiB였고 루트 디스크 사용률은 18%였다. 호스트에 swap이 없어 두 슬롯 구간의 최소 가용 메모리 100MiB는 충분히 넓은 여유로 보기는 어렵다.

### SSE drain 검증

- 연결 시작: 20:50:05 KST
- Nginx green 전환: 20:50:48.823 KST
- 연결 종료: 20:55:06 KST
- 실제 연결 유지: 301초
- 수신량: `quotes` 99건, heartbeat 30건
- 재접속: 0회
- 종료 원인: Nginx 전환이 아니라 SSE Emitter의 300초 수명 만료

전환 직전 20:50:45와 직후 20:50:49에도 같은 연결에서 `quotes`를 받았다. 20:52:16에는 기존 blue 연결과 신규 green 연결이 동시에 존재해 신규 요청만 새 슬롯으로 전달되는 것도 확인했다. 이전 blue는 20:55:59까지 존재했고 20:56:04에는 제거돼, 설정한 310초에 마지막 상태 확인과 컨테이너 종료 시간이 더해진 범위에서 정리됐다.

### 후보 기동 실패와 재시도 차단

실제 불량 이미지를 레지스트리에 올리는 대신 `CHIPTHRONE_STARTUP_TIMEOUT_SECONDS=1`로 줄여 후보가 제한 시간 안에 Ready가 되지 못하는 경로를 주입했다.

- 주입 시작: 21:09:27.098 KST
- 실패 처리 종료: 21:09:29.550 KST
- 감지와 정리 시간: 2.452초
- 결과: green 상태와 `18081` upstream 유지, blue 후보 제거
- 외부 영향: 계측한 모든 `/api/health` 요청 200
- Slack 실패 알림 도착: 21:09:28 KST

실패 기록의 image ID가 현재 image ID와 같음을 확인한 뒤 일반 배포를 실행했다. 21:10:32.625부터 21:10:33.416까지 0.791초 안에 `the latest image already failed deployment; waiting for a new image`로 종료됐고 blue 관련 Docker 이벤트는 없었다. systemd timer도 21:09:57과 21:10:58 실행에서 같은 이미지를 차단했다.

### 전환 후 Readiness 실패와 자동 롤백

같은 이미지를 blue 후보로 강제 배포하고 active state가 blue로 바뀐 직후 컨테이너를 pause했다.

- blue 전환 관측: 21:12:35.042 KST
- blue pause 완료: 21:12:35.253 KST
- 연속 세 번째 Readiness 실패와 rejected 기록: 21:12:49.983 KST
- green upstream 복구: 21:12:50.013 KST
- green active state 복구: 21:12:50.153 KST
- 배포 스크립트 종료: 21:12:51.210 KST
- 외부 `/api/health` 200 재확인: 21:12:51.705 KST
- Slack 자동 롤백 알림 도착: 21:12:50 KST

pause부터 실패 확정까지 14.730초, active state 롤백까지 14.901초, 외부 Health 200 복구까지 16.452초가 걸렸다. 이 구간에서 외부 점검은 timeout과 502를 반환했지만 이전 green 컨테이너는 계속 healthy였다. 롤백 뒤 blue는 제거됐고 active state와 upstream은 green/18081로 일치했다.

롤백이 기록한 실패 image ID도 다음 일반 배포를 0.794초 안에 차단했으며 blue 기동 이벤트는 없었다. 운영 이미지 자체는 정상이고 실패가 의도적인 pause로 발생했으므로 현재 image ID와 rejected ID의 동일성을 확인한 뒤 해당 기록만 제거했다. 이후 21:15부터 21:20까지 여섯 번의 systemd timer 실행이 모두 `active slot already runs the latest image`로 종료돼 자동 배포의 정상 동작 상태가 복원됐음을 확인했다.

### 예상과 달랐던 점

- 310초 drain 뒤 이전 슬롯 제거는 정확히 310초 지점이 아니라 마지막 Readiness 확인, graceful stop, 계측 간격이 더해져 전환 후 약 311~316초 사이에 관측됨
- Nginx reload와 active state 기록이 순차 실행되므로 전환 중 한 표본에서 upstream은 blue지만 active state는 아직 green인 짧은 구간 관측
- 자동 롤백 중 upstream과 active state가 green으로 돌아가는 경계에서 외부 요청 1건이 502를 반환함. 신규 요청의 200 복구는 pause 후 16.452초에 확인됨
- 후보 기동 실패 실험은 실제 손상 이미지가 아니라 1초 startup gate를 이용한 제한 시간 초과 검증임

### 남은 한계와 후속 과제

- t3.micro에 swap이 없고 정상 두 슬롯 구간의 최소 가용 메모리가 100MiB이므로 JVM heap이나 동시 부하를 늘리기 전에 재계측 필요
- 자동 롤백은 이전 슬롯을 유지하는 310초 안에서만 가능. 이후 장애는 health recovery의 제한적 재시작 대상
- Nginx upstream과 active state가 별도 파일이므로 배포 도중에는 일시적으로 값이 다를 수 있음. 운영 판정은 배포 종료 뒤 두 값을 함께 확인해야 함
- 실제 불량 애플리케이션 이미지를 GHCR에 올리는 검증은 수행하지 않음. 레지스트리 오염 없이 Readiness 실패, 롤백, 재시도 차단 경로만 운영에서 확인함
- 운영 검증은 합성 Health, snapshot, SSE 요청 기준이며 실제 사용자 트래픽의 지연 분포는 별도 수집하지 않음
