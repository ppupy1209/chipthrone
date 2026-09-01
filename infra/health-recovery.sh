#!/usr/bin/env bash

set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-chipthrone-api}"
MAX_RESTARTS="${MAX_RESTARTS:-3}"
WINDOW_SECONDS="${WINDOW_SECONDS:-600}"
STATE_DIR="${STATE_DIR:-/var/lib/chipthrone-health-recovery}"
ACTIVE_STATE_FILE="${CHIPTHRONE_ACTIVE_STATE_FILE:-/var/lib/chipthrone-deploy/active-container}"
DEPLOY_LOCK_FILE="${CHIPTHRONE_DEPLOY_LOCK_FILE:-/var/lib/chipthrone-deploy/deploy.lock}"
DOCKER_BIN="${DOCKER_BIN:-/usr/bin/docker}"
LOGGER_BIN="${LOGGER_BIN:-/usr/bin/logger}"
FLOCK_BIN="${FLOCK_BIN:-/usr/bin/flock}"
NOW_EPOCH="${NOW_EPOCH:-$(date +%s)}"

if [[ ! "$MAX_RESTARTS" =~ ^[1-9][0-9]*$ ]]; then
  echo "MAX_RESTARTS must be a positive integer" >&2
  exit 2
fi

if [[ ! "$WINDOW_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "WINDOW_SECONDS must be a positive integer" >&2
  exit 2
fi

mkdir -p "$STATE_DIR" "$(dirname -- "$DEPLOY_LOCK_FILE")"

# 배포 중에는 후보 상태를 배포 스크립트가 판단해야 한다. 여기서 먼저 재시작하면
# 전환 후 실패를 가려 자동 롤백이 동작하지 않을 수 있다.
exec 8>"$DEPLOY_LOCK_FILE"
"$FLOCK_BIN" -n 8 || exit 0

LOCK_FILE="${STATE_DIR}/recovery.lock"
RESTART_HISTORY="${STATE_DIR}/restart-history"
LIMIT_REPORTED_AT="${STATE_DIR}/limit-reported-at"

exec 9>"$LOCK_FILE"
"$FLOCK_BIN" -n 9 || exit 0

if [[ -s "$ACTIVE_STATE_FILE" ]]; then
  IFS='|' read -r active_name _active_port _active_image active_slot < "$ACTIVE_STATE_FILE"
  if [[ "$active_name" =~ ^chipthrone-api-(blue|green)$ && "$active_slot" =~ ^(blue|green)$ ]]; then
    CONTAINER_NAME="$active_name"
  elif [[ "$active_name" == "chipthrone-api" && "$active_slot" == "legacy" ]]; then
    CONTAINER_NAME="$active_name"
  else
    "$LOGGER_BIN" -t chipthrone-health-recovery \
      "invalid active deployment state; fallback container=${CONTAINER_NAME}"
  fi
fi

health_status="$(
  "$DOCKER_BIN" inspect \
    --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
    "$CONTAINER_NAME" 2>/dev/null || printf 'absent'
)"

case "$health_status" in
  healthy|starting)
    exit 0
    ;;
  none)
    "$LOGGER_BIN" -t chipthrone-health-recovery \
      "container=${CONTAINER_NAME} has no Docker healthcheck; recovery skipped"
    exit 0
    ;;
  absent)
    "$LOGGER_BIN" -t chipthrone-health-recovery \
      "container=${CONTAINER_NAME} not found; recovery skipped"
    exit 0
    ;;
  unhealthy)
    ;;
  *)
    "$LOGGER_BIN" -t chipthrone-health-recovery \
      "container=${CONTAINER_NAME} returned unknown health status=${health_status}; recovery skipped"
    exit 0
    ;;
esac

touch "$RESTART_HISTORY"
recent_history="$(mktemp "${STATE_DIR}/restart-history.XXXXXX")"
trap 'rm -f "$recent_history"' EXIT

cutoff=$((NOW_EPOCH - WINDOW_SECONDS))
awk -v cutoff="$cutoff" '$1 >= cutoff' "$RESTART_HISTORY" > "$recent_history"
mv "$recent_history" "$RESTART_HISTORY"
trap - EXIT

restart_count="$(wc -l < "$RESTART_HISTORY" | tr -d ' ')"
if (( restart_count >= MAX_RESTARTS )); then
  last_reported=0
  if [[ -s "$LIMIT_REPORTED_AT" ]]; then
    read -r last_reported < "$LIMIT_REPORTED_AT"
  fi

  if (( NOW_EPOCH - last_reported >= WINDOW_SECONDS )); then
    "$LOGGER_BIN" -t chipthrone-health-recovery \
      "container=${CONTAINER_NAME} remains unhealthy; automatic recovery stopped after ${restart_count} restarts in ${WINDOW_SECONDS}s"
    printf '%s\n' "$NOW_EPOCH" > "$LIMIT_REPORTED_AT"
  fi
  exit 0
fi

printf '%s\n' "$NOW_EPOCH" >> "$RESTART_HISTORY"
"$LOGGER_BIN" -t chipthrone-health-recovery \
  "container=${CONTAINER_NAME} is unhealthy; restart attempt=$((restart_count + 1))/${MAX_RESTARTS}"
"$DOCKER_BIN" restart "$CONTAINER_NAME" > /dev/null
