#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
RECOVERY_SCRIPT="${SCRIPT_DIR}/health-recovery.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

MOCK_DOCKER="${TEST_ROOT}/docker"
MOCK_LOGGER="${TEST_ROOT}/logger"
MOCK_FLOCK="${TEST_ROOT}/flock"
DOCKER_CALLS="${TEST_ROOT}/docker-calls"
LOGGER_CALLS="${TEST_ROOT}/logger-calls"

cat > "$MOCK_DOCKER" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

case "$1" in
  inspect)
    printf '%s\n' "${MOCK_HEALTH_STATUS:?}"
    ;;
  restart)
    printf 'restart %s\n' "$2" >> "${MOCK_DOCKER_CALLS:?}"
    ;;
  *)
    exit 64
    ;;
esac
EOF

cat > "$MOCK_LOGGER" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${MOCK_LOGGER_CALLS:?}"
EOF

cat > "$MOCK_FLOCK" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

chmod +x "$MOCK_DOCKER" "$MOCK_LOGGER" "$MOCK_FLOCK" "$RECOVERY_SCRIPT"

run_recovery() {
  local state_dir="$1"
  local health_status="$2"
  local now_epoch="$3"

  MOCK_HEALTH_STATUS="$health_status" \
  MOCK_DOCKER_CALLS="$DOCKER_CALLS" \
  MOCK_LOGGER_CALLS="$LOGGER_CALLS" \
  STATE_DIR="$state_dir" \
  DOCKER_BIN="$MOCK_DOCKER" \
  LOGGER_BIN="$MOCK_LOGGER" \
  FLOCK_BIN="$MOCK_FLOCK" \
  NOW_EPOCH="$now_epoch" \
  "$RECOVERY_SCRIPT"
}

assert_restart_count() {
  local expected="$1"
  local actual=0
  if [[ -f "$DOCKER_CALLS" ]]; then
    actual="$(wc -l < "$DOCKER_CALLS" | tr -d ' ')"
  fi
  if [[ "$actual" != "$expected" ]]; then
    echo "expected restart count=${expected}, actual=${actual}" >&2
    exit 1
  fi
}

healthy_state="${TEST_ROOT}/healthy"
run_recovery "$healthy_state" healthy 1000
assert_restart_count 0

unhealthy_state="${TEST_ROOT}/unhealthy"
run_recovery "$unhealthy_state" unhealthy 1000
assert_restart_count 1

run_recovery "$unhealthy_state" unhealthy 1010
run_recovery "$unhealthy_state" unhealthy 1020
assert_restart_count 3

run_recovery "$unhealthy_state" unhealthy 1030
assert_restart_count 3
grep -q "automatic recovery stopped" "$LOGGER_CALLS"

run_recovery "$unhealthy_state" unhealthy 1701
assert_restart_count 4

echo "health-recovery tests passed"
