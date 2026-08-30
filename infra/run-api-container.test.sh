#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
RUN_SCRIPT="${SCRIPT_DIR}/run-api-container.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

MOCK_CALLS="${TEST_ROOT}/sudo-calls"
MOCK_SUDO="${TEST_ROOT}/sudo"

cat > "$MOCK_SUDO" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${MOCK_CALLS:?}"
if [[ "$*" == *" docker inspect "* ]]; then
  printf '%s\n' 'docker-logging=none health=healthy'
fi
EOF
chmod +x "$MOCK_SUDO"

local_env="${TEST_ROOT}/local.env"
printf '%s\n' 'PUBLIC_DATA_SERVICE_KEY=test' > "$local_env"
PATH="${TEST_ROOT}:${PATH}" MOCK_CALLS="$MOCK_CALLS" \
  CHIPTHRONE_ENV_FILE="$local_env" bash "$RUN_SCRIPT" >/dev/null
grep -q -- 'install -d -m 0755 /var/log/chipthrone' "$MOCK_CALLS"
grep -q -- '-e LOGGING_FILE_NAME=/var/log/chipthrone/api.log' "$MOCK_CALLS"
grep -q -- '-v /var/log/chipthrone:/var/log/chipthrone' "$MOCK_CALLS"
grep -q -- '--log-driver none' "$MOCK_CALLS"
grep -q -- "--env-file ${local_env}" "$MOCK_CALLS"

echo "run-api-container tests passed"
