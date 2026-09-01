#!/usr/bin/env bash
# chipthrone EC2 초기 세팅 스크립트 (Amazon Linux 2023, t3.micro 기준)
# 사용법:
#   1) EC2에 SSH 접속
#   2) 이 스크립트를 복사해 실행:  bash ec2-bootstrap.sh
# Docker, Nginx, Certbot 설치 + Nginx 리버스 프록시 설정까지 수행한다.
set -euo pipefail

OWNER="ppupy1209"
API_DOMAIN="api.chipthrone.com"
RAW_INFRA_BASE="https://raw.githubusercontent.com/${OWNER}/chipthrone/main/infra"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

install_infra_file() {
  local source_name="$1"
  local target_path="$2"
  local mode="$3"

  if [ -f "${SCRIPT_DIR}/${source_name}" ]; then
    sudo install -m "$mode" "${SCRIPT_DIR}/${source_name}" "$target_path"
  else
    curl -fsSL "${RAW_INFRA_BASE}/${source_name}" -o "/tmp/${source_name}"
    sudo install -m "$mode" "/tmp/${source_name}" "$target_path"
    rm -f "/tmp/${source_name}"
  fi
}

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
sudo tee /etc/nginx/conf.d/chipthrone-api-upstream.conf >/dev/null <<'UPSTREAM'
upstream chipthrone_api {
    server 127.0.0.1:8080;
    keepalive 32;
}
UPSTREAM
sudo tee /etc/nginx/conf.d/chipthrone-api.conf >/dev/null <<NGINX
server {
    listen 80;
    server_name ${API_DOMAIN};

    location / {
        proxy_pass http://chipthrone_api;
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

echo "==> Readiness 검증, 자동 롤백, SSE drain 배포 설치"
install_infra_file "install-deploy-agent.sh" \
  "/usr/local/bin/chipthrone-install-deploy-agent" "0755"
if sudo /usr/local/bin/chipthrone-install-deploy-agent; then
  echo "==> 첫 슬롯 배포 완료"
else
  echo "!! 첫 슬롯 배포 실패 — 기존 컨테이너가 있으면 그대로 유지됩니다."
  echo "   GHCR 공개 여부와 journalctl -u chipthrone-deploy.service를 확인하세요."
fi

cat <<DONE

==================================================
다음 단계:
1) DNS: ${API_DOMAIN} A레코드를 이 EC2 퍼블릭 IP로 설정
2) DNS 전파 후 SSL 발급:
     sudo certbot --nginx -d ${API_DOMAIN}
3) 확인:
     cat /var/lib/chipthrone-deploy/active-container
     cat /etc/nginx/conf.d/chipthrone-api-upstream.conf
     curl https://${API_DOMAIN}/api/health
     systemctl status chipthrone-deploy.timer
     systemctl status chipthrone-health-recovery.timer
4) 이후 main 푸시 시 후보 슬롯 검증 후 Nginx 전환, 실패 시 이전 슬롯 롤백
==================================================
DONE
