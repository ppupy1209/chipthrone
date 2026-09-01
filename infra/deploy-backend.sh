#!/usr/bin/env bash

set -euo pipefail

OWNER="ppupy1209"
IMAGE="${CHIPTHRONE_IMAGE:-ghcr.io/${OWNER}/chipthrone-api:latest}"
ENV_FILE="${CHIPTHRONE_ENV_FILE:-/home/ec2-user/chipthrone.env}"
HOST_LOG_DIR="${CHIPTHRONE_LOG_DIR:-/var/log/chipthrone}"
CONTAINER_LOG_DIR="/var/log/chipthrone"
STATE_DIR="${CHIPTHRONE_DEPLOY_STATE_DIR:-/var/lib/chipthrone-deploy}"
ACTIVE_STATE_FILE="${CHIPTHRONE_ACTIVE_STATE_FILE:-${STATE_DIR}/active-container}"
REJECTED_IMAGE_FILE="${CHIPTHRONE_REJECTED_IMAGE_FILE:-${STATE_DIR}/rejected-image}"
DEPLOY_LOCK_FILE="${CHIPTHRONE_DEPLOY_LOCK_FILE:-${STATE_DIR}/deploy.lock}"
UPSTREAM_FILE="${CHIPTHRONE_NGINX_UPSTREAM_FILE:-/etc/nginx/conf.d/chipthrone-api-upstream.conf}"

BLUE_PORT="${CHIPTHRONE_BLUE_PORT:-18080}"
GREEN_PORT="${CHIPTHRONE_GREEN_PORT:-18081}"
STARTUP_TIMEOUT_SECONDS="${CHIPTHRONE_STARTUP_TIMEOUT_SECONDS:-90}"
DRAIN_SECONDS="${CHIPTHRONE_DRAIN_SECONDS:-310}"
CHECK_INTERVAL_SECONDS="${CHIPTHRONE_CHECK_INTERVAL_SECONDS:-2}"
FAILURE_THRESHOLD="${CHIPTHRONE_FAILURE_THRESHOLD:-3}"
STOP_TIMEOUT_SECONDS="${CHIPTHRONE_STOP_TIMEOUT_SECONDS:-30}"
FORCE_DEPLOY="${CHIPTHRONE_FORCE_DEPLOY:-0}"

DOCKER_BIN="${DOCKER_BIN:-/usr/bin/docker}"
CURL_BIN="${CURL_BIN:-/usr/bin/curl}"
NGINX_BIN="${NGINX_BIN:-/usr/sbin/nginx}"
SYSTEMCTL_BIN="${SYSTEMCTL_BIN:-/usr/bin/systemctl}"
FLOCK_BIN="${FLOCK_BIN:-/usr/bin/flock}"
SLEEP_BIN="${SLEEP_BIN:-/usr/bin/sleep}"

log() {
  printf '%s\n' "==> $*"
}

fail() {
  printf '%s\n' "!! $*" >&2
  exit 1
}

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

SLACK_WEBHOOK_URL="${CHIPTHRONE_DEPLOY_SLACK_WEBHOOK_URL:-$(read_env_value SLACK_WEBHOOK_URL)}"

notify_slack() {
  local message="$1"
  local escaped
  [[ -n "$SLACK_WEBHOOK_URL" ]] || return 0
  escaped="${message//\\/\\\\}"
  escaped="${escaped//\"/\\\"}"
  if ! "$CURL_BIN" --fail --silent --show-error --max-time 5 \
      -H 'Content-Type: application/json' \
      --data "{\"text\":\"${escaped}\"}" \
      "$SLACK_WEBHOOK_URL" >/dev/null; then
    log "Slack deployment notification failed"
  fi
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    fail "${name} must be a positive integer"
  fi
}

require_non_negative_integer() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    fail "${name} must be a non-negative integer"
  fi
}

if [[ "${CHIPTHRONE_SKIP_ROOT_CHECK:-0}" != "1" && "${EUID}" -ne 0 ]]; then
  fail "run as root (sudo infra/deploy-backend.sh)"
fi

require_positive_integer CHIPTHRONE_BLUE_PORT "$BLUE_PORT"
require_positive_integer CHIPTHRONE_GREEN_PORT "$GREEN_PORT"
require_positive_integer CHIPTHRONE_STARTUP_TIMEOUT_SECONDS "$STARTUP_TIMEOUT_SECONDS"
require_non_negative_integer CHIPTHRONE_DRAIN_SECONDS "$DRAIN_SECONDS"
require_positive_integer CHIPTHRONE_CHECK_INTERVAL_SECONDS "$CHECK_INTERVAL_SECONDS"
require_positive_integer CHIPTHRONE_FAILURE_THRESHOLD "$FAILURE_THRESHOLD"
require_positive_integer CHIPTHRONE_STOP_TIMEOUT_SECONDS "$STOP_TIMEOUT_SECONDS"

mkdir -p "$STATE_DIR" "$HOST_LOG_DIR" "$(dirname -- "$UPSTREAM_FILE")"

exec 9>"$DEPLOY_LOCK_FILE"
if ! "$FLOCK_BIN" -n 9; then
  log "another deployment is already running; skipped"
  exit 0
fi

container_exists() {
  "$DOCKER_BIN" inspect "$1" >/dev/null 2>&1
}

container_image() {
  "$DOCKER_BIN" inspect --format '{{.Image}}' "$1"
}

active_name=""
active_port=""
active_image=""
active_slot=""
active_managed=false

read_active_state() {
  if [[ -s "$ACTIVE_STATE_FILE" ]]; then
    IFS='|' read -r active_name active_port active_image active_slot < "$ACTIVE_STATE_FILE"
    if [[ "$active_name" =~ ^chipthrone-api-(blue|green)$ \
          && "$active_port" =~ ^[0-9]+$ \
          && "$active_slot" =~ ^(blue|green)$ ]] \
          && container_exists "$active_name"; then
      active_image="$(container_image "$active_name")"
      active_managed=true
      return
    fi
    if [[ "$active_name" == "chipthrone-api" \
          && "$active_port" == "8080" \
          && "$active_slot" == "legacy" ]] \
          && container_exists "$active_name"; then
      active_image="$(container_image "$active_name")"
      return
    fi
    log "stale deployment state ignored: ${ACTIVE_STATE_FILE}"
    active_name=""
    active_port=""
    active_image=""
    active_slot=""
  fi

  if container_exists chipthrone-api; then
    active_name="chipthrone-api"
    active_port="8080"
    active_image="$(container_image chipthrone-api)"
    active_slot="legacy"
  fi
}

write_active_state() {
  local name="$1"
  local port="$2"
  local image_id="$3"
  local slot="$4"
  local pending
  pending="$(mktemp "${STATE_DIR}/active-container.XXXXXX")"
  printf '%s|%s|%s|%s\n' "$name" "$port" "$image_id" "$slot" > "$pending"
  mv "$pending" "$ACTIVE_STATE_FILE"
}

mark_rejected() {
  local image_id="$1"
  local pending
  pending="$(mktemp "${STATE_DIR}/rejected-image.XXXXXX")"
  printf '%s\n' "$image_id" > "$pending"
  mv "$pending" "$REJECTED_IMAGE_FILE"
}

clear_rejected() {
  rm -f "$REJECTED_IMAGE_FILE"
}

restore_upstream() {
  local backup="$1"
  local had_previous="$2"
  if [[ "$had_previous" == "true" ]]; then
    cp "$backup" "$UPSTREAM_FILE"
  else
    rm -f "$UPSTREAM_FILE"
  fi
  "$NGINX_BIN" -t >/dev/null 2>&1 || true
  "$SYSTEMCTL_BIN" reload nginx >/dev/null 2>&1 || true
}

switch_upstream() {
  local port="$1"
  local pending backup had_previous=false
  pending="$(mktemp "${STATE_DIR}/nginx-upstream.XXXXXX")"
  backup="$(mktemp "${STATE_DIR}/nginx-upstream-backup.XXXXXX")"

  if [[ -f "$UPSTREAM_FILE" ]]; then
    cp "$UPSTREAM_FILE" "$backup"
    had_previous=true
  fi

  cat > "$pending" <<UPSTREAM
upstream chipthrone_api {
    server 127.0.0.1:${port};
    keepalive 32;
}
UPSTREAM
  install -m 0644 "$pending" "$UPSTREAM_FILE"
  rm -f "$pending"

  if ! "$NGINX_BIN" -t; then
    restore_upstream "$backup" "$had_previous"
    rm -f "$backup"
    return 1
  fi
  if ! "$SYSTEMCTL_BIN" reload nginx; then
    restore_upstream "$backup" "$had_previous"
    rm -f "$backup"
    return 1
  fi

  rm -f "$backup"
}

candidate_ready() {
  local port="$1"
  "$CURL_BIN" --fail --silent --show-error --max-time 3 \
    "http://127.0.0.1:${port}/actuator/health/readiness" >/dev/null
}

wait_for_candidate() {
  local port="$1"
  local attempts=$(( (STARTUP_TIMEOUT_SECONDS + CHECK_INTERVAL_SECONDS - 1) / CHECK_INTERVAL_SECONDS ))
  local attempt
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if candidate_ready "$port"; then
      return 0
    fi
    if (( attempt < attempts )); then
      "$SLEEP_BIN" "$CHECK_INTERVAL_SECONDS"
    fi
  done
  return 1
}

monitor_candidate() {
  local port="$1"
  # 첫 확인은 전환 직후 수행하므로, 설정한 drain 시간이 모두 지난 뒤 마지막으로 한 번 더 확인한다.
  local checks=$(( (DRAIN_SECONDS + CHECK_INTERVAL_SECONDS - 1) / CHECK_INTERVAL_SECONDS + 1 ))
  local failures=0
  local check
  for ((check = 1; check <= checks; check++)); do
    if candidate_ready "$port"; then
      failures=0
    else
      failures=$((failures + 1))
      log "candidate readiness failure ${failures}/${FAILURE_THRESHOLD}"
      if (( failures >= FAILURE_THRESHOLD )); then
        return 1
      fi
    fi
    if (( check < checks )); then
      "$SLEEP_BIN" "$CHECK_INTERVAL_SECONDS"
    fi
  done
  return 0
}

cleanup_candidate() {
  local name="$1"
  "$DOCKER_BIN" rm -f "$name" >/dev/null 2>&1 || true
}

read_active_state

log "pulling deployment candidate: ${IMAGE}"
"$DOCKER_BIN" pull "$IMAGE" >/dev/null
desired_image="$($DOCKER_BIN image inspect --format '{{.Id}}' "$IMAGE")"

if [[ "$FORCE_DEPLOY" != "1" && -s "$REJECTED_IMAGE_FILE" ]]; then
  read -r rejected_image < "$REJECTED_IMAGE_FILE"
  if [[ "$rejected_image" == "$desired_image" ]]; then
    log "the latest image already failed deployment; waiting for a new image"
    exit 0
  fi
fi

if [[ "$FORCE_DEPLOY" != "1" && "$active_managed" == "true" && "$active_image" == "$desired_image" ]]; then
  log "active slot already runs the latest image"
  exit 0
fi

if [[ "$active_slot" == "blue" ]]; then
  candidate_slot="green"
  candidate_port="$GREEN_PORT"
else
  candidate_slot="blue"
  candidate_port="$BLUE_PORT"
fi
candidate_name="chipthrone-api-${candidate_slot}"

cleanup_candidate "$candidate_name"

log "starting ${candidate_slot} candidate on 127.0.0.1:${candidate_port}"
run_args=(
  run -d
  --name "$candidate_name"
  --restart unless-stopped
  --stop-timeout "$STOP_TIMEOUT_SECONDS"
  -p "127.0.0.1:${candidate_port}:8080"
  # 운영 실측에서 400 SSE 연결 시 최대 컨테이너 메모리는 273.52MiB였다.
  # 두 슬롯이 겹치는 동안 t3.micro 메모리에 여유를 남기도록 최대 heap을 낮춘다.
  -e "JAVA_OPTS=-Xms128m -Xmx320m"
  -e "LOGGING_FILE_NAME=${CONTAINER_LOG_DIR}/api-${candidate_slot}.log"
  -v "${HOST_LOG_DIR}:${CONTAINER_LOG_DIR}"
  --log-driver none
  --label "chipthrone.deploy.slot=${candidate_slot}"
  --label "chipthrone.deploy.image=${desired_image}"
)
if [[ -f "$ENV_FILE" ]]; then
  run_args+=(--env-file "$ENV_FILE")
fi
run_args+=("$IMAGE")
"$DOCKER_BIN" "${run_args[@]}" >/dev/null

if ! wait_for_candidate "$candidate_port"; then
  log "candidate did not become ready; active slot remains unchanged"
  mark_rejected "$desired_image"
  notify_slack ":x: CHIP THRONE 배포 실패: 후보 슬롯이 Readiness 검증을 통과하지 못했습니다. image=${desired_image}"
  cleanup_candidate "$candidate_name"
  exit 1
fi

log "candidate is ready; switching Nginx to ${candidate_slot}"
if ! switch_upstream "$candidate_port"; then
  log "Nginx switch failed; active slot remains unchanged"
  mark_rejected "$desired_image"
  notify_slack ":x: CHIP THRONE 배포 실패: Nginx upstream 전환에 실패했습니다. image=${desired_image}"
  cleanup_candidate "$candidate_name"
  exit 1
fi
write_active_state "$candidate_name" "$candidate_port" "$desired_image" "$candidate_slot"

if ! monitor_candidate "$candidate_port"; then
  mark_rejected "$desired_image"
  if [[ -n "$active_name" ]] && container_exists "$active_name"; then
    log "candidate became unhealthy; rolling back to ${active_name}"
    if switch_upstream "$active_port"; then
      write_active_state "$active_name" "$active_port" "$active_image" "$active_slot"
      notify_slack ":warning: CHIP THRONE 자동 롤백: 새 슬롯이 전환 후 불안정해 이전 슬롯으로 복구했습니다. image=${desired_image}"
      cleanup_candidate "$candidate_name"
      exit 1
    fi
    fail "candidate and Nginx rollback both failed; manual recovery required"
  fi
  fail "candidate became unhealthy and no previous slot is available"
fi

if [[ -n "$active_name" && "$active_name" != "$candidate_name" ]] && container_exists "$active_name"; then
  log "SSE drain window completed; stopping previous container ${active_name}"
  "$DOCKER_BIN" stop --time "$STOP_TIMEOUT_SECONDS" "$active_name" >/dev/null || true
  "$DOCKER_BIN" rm "$active_name" >/dev/null || true
fi

clear_rejected
notify_slack ":white_check_mark: CHIP THRONE 배포 완료: slot=${candidate_slot}, image=${desired_image}"
log "deployment completed: slot=${candidate_slot}, image=${desired_image}"
