#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_SCRIPT="${SCRIPT_DIR}/deploy-backend.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

MOCK_BIN="${TEST_ROOT}/bin"
mkdir -p "$MOCK_BIN"

cat > "${MOCK_BIN}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${MOCK_DOCKER_CALLS:?}"

container_file() {
  printf '%s/%s.image' "${MOCK_CONTAINERS:?}" "$1"
}

case "$1" in
  pull)
    exit 0
    ;;
  image)
    printf '%s\n' "${MOCK_DESIRED_IMAGE:-sha256:new}"
    ;;
  inspect)
    if [[ "$2" == "--format" ]]; then
      name="$4"
      file="$(container_file "$name")"
      [[ -f "$file" ]] || exit 1
      cat "$file"
    else
      [[ -f "$(container_file "$2")" ]]
    fi
    ;;
  run)
    name=""
    previous=""
    for argument in "$@"; do
      if [[ "$previous" == "--name" ]]; then
        name="$argument"
        break
      fi
      previous="$argument"
    done
    [[ -n "$name" ]]
    printf '%s\n' "${MOCK_DESIRED_IMAGE:-sha256:new}" > "$(container_file "$name")"
    ;;
  stop)
    exit 0
    ;;
  rm)
    name="${@: -1}"
    rm -f "$(container_file "$name")"
    ;;
  *)
    exit 64
    ;;
esac
EOF

cat > "${MOCK_BIN}/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
count=0
if [[ -f "${MOCK_CURL_COUNT:?}" ]]; then
  read -r count < "$MOCK_CURL_COUNT"
fi
count=$((count + 1))
printf '%s\n' "$count" > "$MOCK_CURL_COUNT"

case "${MOCK_CURL_MODE:-success}" in
  success)
    exit 0
    ;;
  startup-fail)
    exit 22
    ;;
  post-switch-fail)
    if (( count == 1 )); then exit 0; else exit 22; fi
    ;;
  *)
    exit 64
    ;;
esac
EOF

cat > "${MOCK_BIN}/nginx" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

cat > "${MOCK_BIN}/systemctl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${MOCK_SYSTEMCTL_CALLS:?}"
EOF

cat > "${MOCK_BIN}/flock" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

cat > "${MOCK_BIN}/sleep" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

chmod +x "${MOCK_BIN}"/* "$DEPLOY_SCRIPT"

invoke_deploy() {
  local scenario_root="$1"
  local curl_mode="$2"
  local containers="${scenario_root}/containers"
  local state_dir="${scenario_root}/state"
  local log_dir="${scenario_root}/logs"
  local upstream="${scenario_root}/nginx/upstream.conf"

  MOCK_DESIRED_IMAGE="${MOCK_DESIRED_IMAGE:-sha256:new}" \
  MOCK_CONTAINERS="$containers" \
  MOCK_DOCKER_CALLS="${scenario_root}/docker-calls" \
  MOCK_SYSTEMCTL_CALLS="${scenario_root}/systemctl-calls" \
  MOCK_CURL_COUNT="${scenario_root}/curl-count" \
  MOCK_CURL_MODE="$curl_mode" \
  CHIPTHRONE_SKIP_ROOT_CHECK=1 \
  CHIPTHRONE_DEPLOY_STATE_DIR="$state_dir" \
  CHIPTHRONE_LOG_DIR="$log_dir" \
  CHIPTHRONE_NGINX_UPSTREAM_FILE="$upstream" \
  CHIPTHRONE_ENV_FILE="${scenario_root}/missing.env" \
  CHIPTHRONE_STARTUP_TIMEOUT_SECONDS=2 \
  CHIPTHRONE_DRAIN_SECONDS=4 \
  CHIPTHRONE_CHECK_INTERVAL_SECONDS=1 \
  CHIPTHRONE_FAILURE_THRESHOLD=2 \
  DOCKER_BIN="${MOCK_BIN}/docker" \
  CURL_BIN="${MOCK_BIN}/curl" \
  NGINX_BIN="${MOCK_BIN}/nginx" \
  SYSTEMCTL_BIN="${MOCK_BIN}/systemctl" \
  FLOCK_BIN="${MOCK_BIN}/flock" \
  SLEEP_BIN="${MOCK_BIN}/sleep" \
  "$DEPLOY_SCRIPT"
}

run_scenario() {
  local scenario="$1"
  local curl_mode="$2"
  local scenario_root="${TEST_ROOT}/${scenario}"
  local containers="${scenario_root}/containers"
  local state_dir="${scenario_root}/state"
  local log_dir="${scenario_root}/logs"
  local upstream="${scenario_root}/nginx/upstream.conf"
  mkdir -p "$containers" "$state_dir" "$log_dir" "$(dirname -- "$upstream")"
  printf '%s\n' 'sha256:old' > "${containers}/chipthrone-api.image"
  cat > "$upstream" <<'EOF'
upstream chipthrone_api {
    server 127.0.0.1:8080;
}
EOF
  invoke_deploy "$scenario_root" "$curl_mode"
}

run_scenario success success
success_root="${TEST_ROOT}/success"
grep -q '^chipthrone-api-blue|18080|sha256:new|blue$' "${success_root}/state/active-container"
grep -q '127.0.0.1:18080' "${success_root}/nginx/upstream.conf"
[[ -f "${success_root}/containers/chipthrone-api-blue.image" ]]
[[ ! -f "${success_root}/containers/chipthrone-api.image" ]]
grep -q -- '--stop-timeout 30' "${success_root}/docker-calls"
grep -q -- '127.0.0.1:18080:8080' "${success_root}/docker-calls"

rm -f "${success_root}/curl-count"
MOCK_DESIRED_IMAGE=sha256:newer invoke_deploy "$success_root" success
grep -q '^chipthrone-api-green|18081|sha256:newer|green$' "${success_root}/state/active-container"
grep -q '127.0.0.1:18081' "${success_root}/nginx/upstream.conf"
[[ -f "${success_root}/containers/chipthrone-api-green.image" ]]
[[ ! -f "${success_root}/containers/chipthrone-api-blue.image" ]]

set +e
run_scenario startup-failure startup-fail
startup_status="$?"
set -e
[[ "$startup_status" != "0" ]]
startup_root="${TEST_ROOT}/startup-failure"
[[ -f "${startup_root}/containers/chipthrone-api.image" ]]
[[ ! -f "${startup_root}/containers/chipthrone-api-blue.image" ]]
grep -q '127.0.0.1:8080' "${startup_root}/nginx/upstream.conf"
grep -q '^sha256:new$' "${startup_root}/state/rejected-image"
startup_run_count="$(grep -c '^run ' "${startup_root}/docker-calls")"
invoke_deploy "$startup_root" startup-fail
[[ "$(grep -c '^run ' "${startup_root}/docker-calls")" == "$startup_run_count" ]]

set +e
run_scenario rollback post-switch-fail
rollback_status="$?"
set -e
[[ "$rollback_status" != "0" ]]
rollback_root="${TEST_ROOT}/rollback"
grep -q '^chipthrone-api|8080|sha256:old|legacy$' "${rollback_root}/state/active-container"
grep -q '127.0.0.1:8080' "${rollback_root}/nginx/upstream.conf"
[[ -f "${rollback_root}/containers/chipthrone-api.image" ]]
[[ ! -f "${rollback_root}/containers/chipthrone-api-blue.image" ]]
grep -q '^sha256:new$' "${rollback_root}/state/rejected-image"

echo "deploy-backend tests passed"
