# 배포 가이드 (CI/CD)

최저비용(AWS 프리티어) 기준 배포 구성. 워킹 스켈레톤 단계에서는 CI와 GHCR 이미지 빌드까지 자동 동작하며, EC2 배포는 인프라 준비 후 활성화한다.

## 1. 파이프라인 개요

| 워크플로 | 트리거 | 동작 | 필요 조건 |
|---|---|---|---|
| `ci.yml` | PR · main 푸시 | 백엔드 `gradle build` + 프론트 `npm build` | 없음(즉시 동작) |
| `deploy-backend.yml` | main 푸시 | Docker 이미지 → GHCR 푸시 | 없음(GITHUB_TOKEN) |
| EC2 watchtower | 60초 주기 | GHCR 새 이미지 감지 → 자동 pull/재시작 | 없음(SSH 불필요) |
| EC2 health recovery | 15초 주기 | API 응답 불능 컨테이너를 감지해 제한적으로 재시작 | systemd timer |

배포는 **이미지 푸시 + watchtower 자동 반영** 방식이다. GitHub Actions가 EC2에 SSH로 접속하지 않으므로 보안그룹 22를 열 필요가 없다. 프론트엔드 배포는 Vercel의 GitHub 연동이 담당한다(아래 4번).

## 2. AWS 인프라 프로비저닝 (프리티어)

### EC2
1. **t3.micro**(프리티어), Amazon Linux 2023, 키페어 생성.
2. 보안 그룹 인바운드: 22(SSH, 내 IP), 80(HTTP), 443(HTTPS).
3. 부트스트랩 스크립트 실행 (Docker·Nginx·Certbot 설치 + 리버스 프록시 설정 + 컨테이너 최초 실행):
   ```bash
   curl -fsSL https://raw.githubusercontent.com/ppupy1209/chipthrone/main/infra/ec2-bootstrap.sh | bash
   ```
   (또는 `infra/ec2-bootstrap.sh` 내용을 복사해 실행)
4. GHCR 이미지가 private면 pull 전에 로그인 필요. **public 권장**(아래 3번 참고).

### 기존 EC2에 Logback 파일 로그 적용

외부 로그 저장소를 사용하지 않고 Logback 파일 로그를 EC2의 `/var/log/chipthrone`에 보관한다. 활성 로그 파일은 10MB, 압축된 보관 파일은 최대 20MB로 제한한다. 최대 7일 이내에서 전체 디스크 사용량을 약 30MB로 유지한다.

호스트 디렉터리를 컨테이너에 연결하므로 Watchtower가 컨테이너를 교체해도 이전 로그가 유지된다. 중복 저장을 막기 위해 Docker 로그 드라이버는 비활성화한다.

기존 운영 컨테이너에는 다음 명령으로 적용한다.

```bash
curl -fsSL https://raw.githubusercontent.com/ppupy1209/chipthrone/main/infra/run-api-container.sh | bash
```

```bash
sudo docker inspect --format '{{.HostConfig.LogConfig.Type}} {{range .Mounts}}{{.Source}}:{{.Destination}}{{end}}' chipthrone-api
sudo ls -lh /var/log/chipthrone
curl https://api.chipthrone.com/api/health
```

`none /var/log/chipthrone:/var/log/chipthrone`이 출력되고 `api.log`와 Health API가 확인되면 적용 완료다.

### RDS (이후 단계)
- MySQL 8, **db.t3.micro**(프리티어), 20GB. EC2 보안 그룹에서만 3306 접근 허용.

## 3. GitHub 설정 (배포)

SSH 배포를 쓰지 않으므로 `EC2_HOST/USER/SSH_KEY`, `DEPLOY_ENABLED` 시크릿은 **불필요**하다.
이미지 푸시는 `GITHUB_TOKEN`으로 동작하고, EC2의 watchtower가 GHCR에서 새 이미지를 polling해 자동 반영한다.

**GHCR 패키지 공개** (EC2/watchtower가 로그인 없이 pull 가능하게)
- GitHub → 프로필 → Packages → `chipthrone-api` → Package settings → Change visibility → Public.
- (비공개 유지 시 EC2에서 `docker login ghcr.io` 필요)

설정 후 main 푸시 시 자동 배포된다.

> 참고: 현재 백엔드는 인메모리라 **RDS 없이 EC2 단독 배포로 충분**하다. 영속화(DB) 도입 시 RDS를 연결한다.

### 응답 불능 컨테이너 자동 복구

Docker 이미지가 15초마다 컨테이너 내부의 `/api/health`를 호출한다. 3초 안에 응답하지 않는 상태가 3회 연속되면 Docker가 컨테이너를 `unhealthy`로 표시한다.

`chipthrone-health-recovery.timer`는 15초마다 이 상태를 확인하고 `unhealthy` 컨테이너를 재시작한다. 프로세스 종료는 기존 `--restart unless-stopped`가 처리하고, 프로세스는 살아 있지만 API가 응답하지 않는 상태만 이 경로가 처리한다.

무한 재시작으로 장애 원인이 가려지는 것을 막기 위해 10분 안에는 최대 3회만 자동 복구한다. 이후에도 응답하지 않으면 재시작을 중단하고, EC2 외부의 Uptime Monitor가 Slack으로 장애를 알린다.

기존 EC2에는 아래 명령으로 자동 복구 계층만 추가한다. Docker Healthcheck는 새 백엔드 이미지가 Watchtower를 통해 반영된 뒤 활성화된다.

```bash
curl -fsSL https://raw.githubusercontent.com/ppupy1209/chipthrone/main/infra/install-health-recovery.sh | bash
```

```bash
# 현재 상태
sudo docker inspect --format '{{.State.Health.Status}}' chipthrone-api
systemctl status chipthrone-health-recovery.timer

# 자동 복구 이력
journalctl -u chipthrone-health-recovery.service
```

#### TODO: EC2 응답 불능 자동 복구 검증

운영 트래픽이 적은 시간에 수행한다. `docker pause` 이후 자동 복구가 동작하기 전까지 API가 실제로 응답하지 않으므로, 테스트 중 외부 장애가 발생한다.

사전 조건:

- [ ] 자동 복구 변경사항을 `main`에 푸시하고 GitHub Actions 빌드 성공 확인
- [ ] Watchtower가 새 백엔드 이미지를 반영한 뒤 컨테이너 상태가 `healthy`인지 확인
- [ ] `install-health-recovery.sh`를 실행하고 systemd timer가 `active`인지 확인
- [ ] `https://api.chipthrone.com/api/health`가 `200 OK`인지 확인

```bash
sudo docker inspect --format '{{.State.Status}} {{.State.Health.Status}}' chipthrone-api
systemctl is-active chipthrone-health-recovery.timer
curl -i https://api.chipthrone.com/api/health
```

장애 주입과 관측:

```bash
# 터미널 1: 자동 복구 로그 관측
sudo journalctl -fu chipthrone-health-recovery.service

# 터미널 2: 시작 시각 기록 후 API 프로세스 일시 정지
date -Is
sudo docker pause chipthrone-api

# 상태 변화 관측: healthy → unhealthy → starting → healthy
watch -n 2 "sudo docker inspect --format '{{.State.Status}} {{.State.Health.Status}}' chipthrone-api"
```

확인 및 기록:

- [ ] Docker가 연속 Healthcheck 실패 후 컨테이너를 `unhealthy`로 판정
- [ ] systemd 복구 스크립트가 `unhealthy` 상태를 감지해 컨테이너 재시작
- [ ] 컨테이너가 다시 `healthy`가 되고 외부 Health API가 `200 OK`로 복귀
- [ ] 장애 주입 시각, `unhealthy` 판정 시각, 재시작 시각, 외부 API 복구 시각 기록
- [ ] 전체 복구 시간과 Slack Uptime Monitor 알림 발생 여부 기록

자동 복구가 동작하지 않으면 다른 터미널에서 즉시 일시 정지를 해제하고 로그를 확인한다.

```bash
sudo docker unpause chipthrone-api
sudo journalctl -u chipthrone-health-recovery.service --since "10 minutes ago"
sudo docker logs --tail 100 chipthrone-api
```

## 4. Vercel (프론트엔드)

1. [vercel.com](https://vercel.com)에서 GitHub 레포 import.
2. Root Directory: `frontend`, Framework: Vite (자동 감지).
3. 환경변수: `VITE_API_URL` = `https://api.chipthrone.com`
4. 도메인: `chipthrone.com`, `www` 연결.

## 5. DNS (Cloudflare)

| 호스트 | 타입 | 값 |
|---|---|---|
| `chipthrone.com`, `www` | CNAME | Vercel 제공 타깃 |
| `api` | A | EC2 퍼블릭 IP |

## 6. Nginx + SSL (EC2)

`infra/nginx/api.chipthrone.com.conf` 를 `/etc/nginx/conf.d/` 에 배치 후:
```bash
sudo certbot --nginx -d api.chipthrone.com
```

## 7. 공식 데이터 키 주입

공공데이터포털 인증키는 **시크릿**이므로 레포·이미지에 넣지 않고 EC2 환경변수로 주입한다. 환율(Pyth)은 키가 없다. KIS·Alpaca 키는 더 이상 사용하지 않는다.

1. EC2에서 env 파일 생성 (`~/chipthrone.env`):
   ```
   PUBLIC_DATA_SERVICE_KEY=공공데이터포털_DECODING_인증키
   ```
2. 컨테이너를 env 파일로 재실행:
   ```bash
   /usr/local/bin/chipthrone-run-api
   ```
   - watchtower가 이후 업데이트 시 이 환경변수를 그대로 유지한다.
   - 키가 비면 Hyperliquid 추정 시세는 계속 동작한다.
   - 공공데이터 키가 비면 국내 확정 종가·시총이 비어 있고, 환율 키가 비면 설정 기준 환율을 표시한다.
3. 확인: `/api/quotes?symbols=005930,SNDK`에서 `source=HYPERLIQUID`, 국내 종목의 `regularClose/officialMarketCap`, `fxSource=PYTH`를 확인한다.
