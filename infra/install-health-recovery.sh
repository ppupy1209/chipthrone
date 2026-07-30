#!/usr/bin/env bash

set -euo pipefail

OWNER="ppupy1209"
RAW_INFRA_BASE="https://raw.githubusercontent.com/${OWNER}/chipthrone/main/infra"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

install_infra_file() {
  local source_name="$1"
  local target_path="$2"
  local mode="$3"

  if [ -f "${SCRIPT_DIR}/${source_name}" ]; then
    sudo install -m "$mode" "${SCRIPT_DIR}/${source_name}" "$target_path"
    return
  fi

  local download_path
  download_path="$(mktemp)"
  trap 'rm -f "$download_path"' RETURN
  curl -fsSL "${RAW_INFRA_BASE}/${source_name}" -o "$download_path"
  sudo install -m "$mode" "$download_path" "$target_path"
  rm -f "$download_path"
  trap - RETURN
}

install_infra_file "health-recovery.sh" \
  "/usr/local/bin/chipthrone-health-recovery" "0755"
install_infra_file "chipthrone-health-recovery.service" \
  "/etc/systemd/system/chipthrone-health-recovery.service" "0644"
install_infra_file "chipthrone-health-recovery.timer" \
  "/etc/systemd/system/chipthrone-health-recovery.timer" "0644"

sudo systemctl daemon-reload
sudo systemctl enable --now chipthrone-health-recovery.timer
systemctl status chipthrone-health-recovery.timer --no-pager
