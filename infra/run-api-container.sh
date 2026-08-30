#!/usr/bin/env bash

set -euo pipefail

OWNER="ppupy1209"
IMAGE="${CHIPTHRONE_IMAGE:-ghcr.io/${OWNER}/chipthrone-api:latest}"
CONTAINER_NAME="${CHIPTHRONE_CONTAINER_NAME:-chipthrone-api}"
ENV_FILE="${CHIPTHRONE_ENV_FILE:-$HOME/chipthrone.env}"
HOST_LOG_DIR="${CHIPTHRONE_LOG_DIR:-/var/log/chipthrone}"
CONTAINER_LOG_DIR="/var/log/chipthrone"

if [[ -f "$ENV_FILE" ]]; then
  echo "==> env 파일 사용: $ENV_FILE"
fi

sudo install -d -m 0755 "$HOST_LOG_DIR"
echo "==> Logback 파일 로그 사용: ${HOST_LOG_DIR}/api.log"

sudo docker pull "$IMAGE"

sudo docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
run_args=(
  -d
  --name "$CONTAINER_NAME"
  --restart unless-stopped
  -p 8080:8080
  -e JAVA_OPTS="-Xms128m -Xmx512m"
  -e LOGGING_FILE_NAME="${CONTAINER_LOG_DIR}/api.log"
  -v "${HOST_LOG_DIR}:${CONTAINER_LOG_DIR}"
  --log-driver none
)
if [[ -f "$ENV_FILE" ]]; then
  run_args+=(--env-file "$ENV_FILE")
fi
run_args+=("$IMAGE")

sudo docker run "${run_args[@]}"

echo "==> 컨테이너 실행 완료"
sudo docker inspect --format 'docker-logging={{.HostConfig.LogConfig.Type}} health={{.State.Health.Status}}' \
  "$CONTAINER_NAME"
