#!/usr/bin/env bash

set -euo pipefail

OWNER="ppupy1209"
RAW_INFRA_BASE="https://raw.githubusercontent.com/${OWNER}/chipthrone/main/infra"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${CHIPTHRONE_ENV_FILE:-$HOME/chipthrone.env}"
TARGET="/usr/local/bin/chipthrone-run-api"

read_env_value() {
  local key="$1"

  if [[ ! -f "$ENV_FILE" ]]; then
    return
  fi

  awk -F= -v key="$key" '
    $1 == key {
      sub(/^[^=]*=/, "")
      sub(/\r$/, "")
      print
      exit
    }
  ' "$ENV_FILE"
}

AWS_REGION="${AWS_REGION:-$(read_env_value AWS_REGION)}"
CLOUDWATCH_LOG_GROUP="${CLOUDWATCH_LOG_GROUP:-$(read_env_value CLOUDWATCH_LOG_GROUP)}"

if [[ -z "$AWS_REGION" || -z "$CLOUDWATCH_LOG_GROUP" ]]; then
  echo "!! ~/chipthrone.env에 AWS_REGION과 CLOUDWATCH_LOG_GROUP을 먼저 설정하세요." >&2
  exit 64
fi

if [[ -f "${SCRIPT_DIR}/run-api-container.sh" ]]; then
  sudo install -m 0755 "${SCRIPT_DIR}/run-api-container.sh" "$TARGET"
else
  download_path="$(mktemp)"
  trap 'rm -f "$download_path"' EXIT
  curl -fsSL "${RAW_INFRA_BASE}/run-api-container.sh" -o "$download_path"
  sudo install -m 0755 "$download_path" "$TARGET"
fi

CHIPTHRONE_ENV_FILE="$ENV_FILE" "$TARGET"

logging_driver="$(sudo docker inspect --format '{{.HostConfig.LogConfig.Type}}' chipthrone-api)"
if [[ "$logging_driver" != "awslogs" ]]; then
  echo "!! CloudWatch 로그 드라이버 적용 실패: ${logging_driver}" >&2
  exit 1
fi

echo "==> CloudWatch Logs 적용 완료: ${CLOUDWATCH_LOG_GROUP}"
