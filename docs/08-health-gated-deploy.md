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

- [ ] `cat /var/lib/chipthrone-deploy/active-container`에서 활성 슬롯과 image ID 확인
- [ ] `cat /etc/nginx/conf.d/chipthrone-api-upstream.conf`의 포트가 활성 상태와 일치
- [ ] `systemctl status chipthrone-deploy.timer`와 `chipthrone-health-recovery.timer`가 active
- [ ] 배포 중 `free -m`, `docker stats`로 두 슬롯 동시 실행 메모리 확인
- [ ] SSE 연결을 유지한 상태에서 배포해 기존 연결이 즉시 종료되지 않는지 확인
- [ ] 기동 실패 이미지를 배포해 기존 upstream 유지와 Slack 실패 알림 확인
- [ ] 전환 후 후보를 일시 정지해 연속 실패 뒤 이전 슬롯 롤백 확인
- [ ] 실패 이미지가 다음 timer 실행에서 다시 배포되지 않는지 확인

운영 장애 주입은 실제 사용자가 적은 시간에 진행하고, 각 단계 전에 현재 활성 상태와 upstream 파일을 기록한다.
