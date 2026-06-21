# 배포 가이드 (CI/CD)

최저비용(AWS 프리티어) 기준 배포 구성. 워킹 스켈레톤 단계에서는 CI와 GHCR 이미지 빌드까지 자동 동작하며, EC2 배포는 인프라 준비 후 활성화한다.

## 1. 파이프라인 개요

| 워크플로 | 트리거 | 동작 | 필요 조건 |
|---|---|---|---|
| `ci.yml` | PR · main 푸시 | 백엔드 `gradle build` + 프론트 `npm build` | 없음(즉시 동작) |
| `deploy-backend.yml` | main 푸시 | Docker 이미지 → GHCR 푸시 | 없음(GITHUB_TOKEN) |
| EC2 watchtower | 60초 주기 | GHCR 새 이미지 감지 → 자동 pull/재시작 | 없음(SSH 불필요) |

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

## 7. KIS 키 주입 (정규장 연동)

KIS APP KEY/SECRET은 **시크릿**이므로 레포·이미지에 넣지 않고 EC2 환경변수로 주입한다.

1. EC2에서 env 파일 생성 (`~/chipthrone.env`):
   ```
   KIS_APP_KEY=실전_APP_KEY
   KIS_APP_SECRET=실전_APP_SECRET
   ```
2. 컨테이너를 env 파일로 재실행:
   ```bash
   sudo docker rm -f chipthrone-api
   sudo docker run -d --name chipthrone-api --restart unless-stopped \
     -p 8080:8080 -e JAVA_OPTS="-Xms128m -Xmx512m" \
     --env-file ~/chipthrone.env ghcr.io/ppupy1209/chipthrone-api:latest
   ```
   - watchtower가 이후 업데이트 시 이 환경변수를 그대로 유지한다.
   - `KIS_APP_KEY`가 비어 있으면 KIS 비활성(Hyperliquid 추정만 사용).
3. 확인: `/api/quotes`의 `regularClose`가 채워지면 KIS 연동 성공(주말·야간엔 종가, 정규장엔 실시세).
