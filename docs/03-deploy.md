# 배포 가이드 (CI/CD)

최저비용(AWS 프리티어) 기준 배포 구성. 워킹 스켈레톤 단계에서는 CI와 GHCR 이미지 빌드까지 자동 동작하며, EC2 배포는 인프라 준비 후 활성화한다.

## 1. 파이프라인 개요

| 워크플로 | 트리거 | 동작 | 필요 조건 |
|---|---|---|---|
| `ci.yml` | PR · main 푸시 | 백엔드 `gradle build` + 프론트 `npm build` | 없음(즉시 동작) |
| `deploy-backend.yml` (build-image) | main 푸시 | Docker 이미지 → GHCR 푸시 | 없음(GITHUB_TOKEN) |
| `deploy-backend.yml` (deploy) | main 푸시 | EC2 SSH 배포 | `DEPLOY_ENABLED=true` + 시크릿 |

프론트엔드 배포는 Vercel의 GitHub 연동이 담당한다(아래 4번).

## 2. AWS 인프라 프로비저닝 (프리티어)

### EC2
1. **t3.micro**(프리티어), Amazon Linux 2023, 키페어 생성.
2. 보안 그룹 인바운드: 22(SSH, 내 IP), 80(HTTP), 443(HTTPS).
3. Docker 설치:
   ```bash
   sudo dnf install -y docker
   sudo systemctl enable --now docker
   sudo usermod -aG docker ec2-user
   ```
4. (퍼블릭 GHCR 이미지는 로그인 없이 pull 가능)

### RDS (이후 단계)
- MySQL 8, **db.t3.micro**(프리티어), 20GB. EC2 보안 그룹에서만 3306 접근 허용.

## 3. GitHub 설정 (EC2 배포 활성화)

레포 → Settings → Secrets and variables → Actions

**Variables**
- `DEPLOY_ENABLED` = `true`

**Secrets**
- `EC2_HOST` = EC2 퍼블릭 IP/도메인
- `EC2_USER` = `ec2-user`
- `EC2_SSH_KEY` = 키페어 개인키(.pem 전체 내용)

설정 후 main 푸시 시 자동 배포된다.

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
