#!/usr/bin/env bash
# chipthrone EC2 초기 세팅 스크립트 (Amazon Linux 2023, t3.micro 기준)
# 사용법:
#   1) EC2에 SSH 접속
#   2) 이 스크립트를 복사해 실행:  bash ec2-bootstrap.sh
# Docker, Nginx, Certbot 설치 + Nginx 리버스 프록시 설정까지 수행한다.
set -euo pipefail

OWNER="ppupy1209"
IMAGE="ghcr.io/${OWNER}/chipthrone-api:latest"
API_DOMAIN="api.chipthrone.com"

echo "==> 시스템 업데이트 & Docker 설치"
sudo dnf update -y
sudo dnf install -y docker nginx
sudo systemctl enable --now docker
sudo systemctl enable --now nginx
sudo usermod -aG docker ec2-user || true

echo "==> Certbot 설치"
sudo dnf install -y certbot python3-certbot-nginx || \
  echo "(certbot dnf 설치 실패 시: sudo python3 -m pip install certbot certbot-nginx)"

echo "==> Nginx 리버스 프록시 설정 (${API_DOMAIN})"
sudo tee /etc/nginx/conf.d/chipthrone-api.conf >/dev/null <<NGINX
server {
    listen 80;
    server_name ${API_DOMAIN};

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        # SSE(실시간 시세 스트리밍) 대비
        proxy_set_header Connection '';
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
    }
}
NGINX
sudo nginx -t && sudo systemctl reload nginx

echo "==> 컨테이너 최초 실행 시도 ($IMAGE)"
# GHCR 패키지가 private면 먼저 로그인 필요:
#   echo <GitHub PAT(read:packages)> | docker login ghcr.io -u ${OWNER} --password-stdin
# 시크릿(KIS 키 등)은 env 파일로 주입한다. 있으면 --env-file 로 전달.
ENV_FILE="$HOME/chipthrone.env"
ENV_OPT=""
if [ -f "$ENV_FILE" ]; then
  ENV_OPT="--env-file $ENV_FILE"
  echo "==> env 파일 사용: $ENV_FILE"
fi

if sudo docker pull "$IMAGE"; then
  sudo docker rm -f chipthrone-api 2>/dev/null || true
  sudo docker run -d --name chipthrone-api --restart unless-stopped \
    -p 8080:8080 -e JAVA_OPTS="-Xms128m -Xmx512m" $ENV_OPT "$IMAGE"
  echo "==> 컨테이너 실행 완료"
else
  echo "!! 이미지 pull 실패 — GHCR 패키지를 public으로 바꾸거나 docker login 후 재시도하세요."
fi

echo "==> watchtower 실행 (새 이미지 자동 반영, 60초 주기)"
sudo docker rm -f watchtower 2>/dev/null || true
sudo docker run -d --name watchtower --restart unless-stopped \
  -v /var/run/docker.sock:/var/run/docker.sock \
  containrrr/watchtower --cleanup --interval 60 chipthrone-api

cat <<DONE

==================================================
다음 단계:
1) DNS: ${API_DOMAIN} A레코드를 이 EC2 퍼블릭 IP로 설정
2) DNS 전파 후 SSL 발급:
     sudo certbot --nginx -d ${API_DOMAIN}
3) 확인:
     curl http://localhost:8080/api/health
     curl https://${API_DOMAIN}/api/health
4) 이후 main 푸시 시 GitHub Actions가 자동 재배포
==================================================
DONE
