#!/usr/bin/env bash

set -euo pipefail

OWNER="ppupy1209"
RAW_INFRA_BASE="https://raw.githubusercontent.com/${OWNER}/chipthrone/main/infra"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SERVER_CONFIG="${CHIPTHRONE_NGINX_SERVER_CONFIG:-/etc/nginx/conf.d/chipthrone-api.conf}"
UPSTREAM_CONFIG="${CHIPTHRONE_NGINX_UPSTREAM_FILE:-/etc/nginx/conf.d/chipthrone-api-upstream.conf}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "run as root: sudo infra/install-deploy-agent.sh" >&2
  exit 1
fi

install_infra_file() {
  local source_name="$1"
  local target_path="$2"
  local mode="$3"
  local temporary

  if [[ -f "${SCRIPT_DIR}/${source_name}" ]]; then
    install -m "$mode" "${SCRIPT_DIR}/${source_name}" "$target_path"
    return
  fi

  temporary="$(mktemp)"
  curl -fsSL "${RAW_INFRA_BASE}/${source_name}" -o "$temporary"
  install -m "$mode" "$temporary" "$target_path"
  rm -f "$temporary"
}

[[ -f "$SERVER_CONFIG" ]] || {
  echo "Nginx server config not found: ${SERVER_CONFIG}" >&2
  exit 1
}

echo "==> 슬롯 배포 파일 설치"
install_infra_file "deploy-backend.sh" "/usr/local/bin/chipthrone-deploy" "0755"
install_infra_file "chipthrone-deploy.service" "/etc/systemd/system/chipthrone-deploy.service" "0644"
install_infra_file "chipthrone-deploy.timer" "/etc/systemd/system/chipthrone-deploy.timer" "0644"
install_infra_file "health-recovery.sh" "/usr/local/bin/chipthrone-health-recovery" "0755"
install_infra_file "chipthrone-health-recovery.service" "/etc/systemd/system/chipthrone-health-recovery.service" "0644"
install_infra_file "chipthrone-health-recovery.timer" "/etc/systemd/system/chipthrone-health-recovery.timer" "0644"

if [[ ! -f "$UPSTREAM_CONFIG" ]]; then
  install_infra_file "nginx/chipthrone-api-upstream.conf" "$UPSTREAM_CONFIG" "0644"
fi

echo "==> Nginx를 전환 가능한 upstream으로 변경"
server_backup="${SERVER_CONFIG}.before-slot-deploy"
cp "$SERVER_CONFIG" "$server_backup"
if grep -qF 'proxy_pass http://127.0.0.1:8080;' "$SERVER_CONFIG"; then
  sed -i 's|proxy_pass http://127.0.0.1:8080;|proxy_pass http://chipthrone_api;|' "$SERVER_CONFIG"
elif ! grep -qF 'proxy_pass http://chipthrone_api;' "$SERVER_CONFIG"; then
  echo "expected proxy_pass was not found in ${SERVER_CONFIG}" >&2
  exit 1
fi

if ! nginx -t; then
  cp "$server_backup" "$SERVER_CONFIG"
  nginx -t || true
  echo "Nginx validation failed; original config restored" >&2
  exit 1
fi
systemctl reload nginx

echo "==> Watchtower 중지 후 health-gated 배포 timer 활성화"
docker rm -f watchtower >/dev/null 2>&1 || true
rm -f /usr/local/bin/chipthrone-run-api
systemctl daemon-reload
systemctl enable --now chipthrone-health-recovery.timer
systemctl enable --now chipthrone-deploy.timer

if ! systemctl start chipthrone-deploy.service; then
  echo "initial slot deployment failed; the previous container remains active" >&2
  echo "check: journalctl -u chipthrone-deploy.service -n 100" >&2
  exit 1
fi

cat <<'DONE'

슬롯 배포 설치 완료
- 배포 상태: cat /var/lib/chipthrone-deploy/active-container
- 최근 배포: journalctl -u chipthrone-deploy.service -n 100
- 배포 timer: systemctl status chipthrone-deploy.timer
- 활성 upstream: cat /etc/nginx/conf.d/chipthrone-api-upstream.conf
DONE
