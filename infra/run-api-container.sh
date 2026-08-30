#!/usr/bin/env bash

set -euo pipefail

OWNER="ppupy1209"
IMAGE="${CHIPTHRONE_IMAGE:-ghcr.io/${OWNER}/chipthrone-api:latest}"
CONTAINER_NAME="${CHIPTHRONE_CONTAINER_NAME:-chipthrone-api}"
ENV_FILE="${CHIPTHRONE_ENV_FILE:-$HOME/chipthrone.env}"

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

if [[ -f "$ENV_FILE" ]]; then
  echo "==> env 파일 사용: $ENV_FILE"
fi

log_args=()
cloudwatch_enabled=false
if [[ -n "$AWS_REGION" || -n "$CLOUDWATCH_LOG_GROUP" ]]; then
  if [[ -z "$AWS_REGION" || -z "$CLOUDWATCH_LOG_GROUP" ]]; then
    echo "!! AWS_REGION과 CLOUDWATCH_LOG_GROUP을 함께 설정하세요." >&2
    exit 64
  fi

  log_args=(
    --log-driver awslogs
    --log-opt "awslogs-region=${AWS_REGION}"
    --log-opt "awslogs-group=${CLOUDWATCH_LOG_GROUP}"
    --log-opt mode=non-blocking
    --log-opt max-buffer-size=4m
  )
  cloudwatch_enabled=true
  echo "==> CloudWatch Logs 사용: ${CLOUDWATCH_LOG_GROUP} (${AWS_REGION})"
else
  log_args=(
    --log-driver local
    --log-opt max-size=10m
    --log-opt max-file=3
  )
  echo "==> CloudWatch 설정 없음: 회전되는 로컬 로그 사용"
fi

sudo docker pull "$IMAGE"

if [[ "$cloudwatch_enabled" == true ]]; then
  probe_name="${CONTAINER_NAME}-log-check-$$"
  sudo docker run --rm --name "$probe_name" \
    --entrypoint /bin/sh \
    "${log_args[@]}" \
    "$IMAGE" -c 'printf "%s\n" "chipthrone CloudWatch log delivery check"'
fi

sudo docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
run_args=(
  -d
  --name "$CONTAINER_NAME"
  --restart unless-stopped
  -p 8080:8080
  -e JAVA_OPTS="-Xms128m -Xmx512m"
)
if [[ "$cloudwatch_enabled" == true ]]; then
  run_args+=(-e CONSOLE_LOG_STRUCTURED_FORMAT=ecs)
fi
if [[ -f "$ENV_FILE" ]]; then
  run_args+=(--env-file "$ENV_FILE")
fi
run_args+=("${log_args[@]}" "$IMAGE")

sudo docker run "${run_args[@]}"

echo "==> 컨테이너 실행 완료"
sudo docker inspect --format 'logging={{.HostConfig.LogConfig.Type}} health={{.State.Health.Status}}' \
  "$CONTAINER_NAME"
