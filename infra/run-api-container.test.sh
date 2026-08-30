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
  printf '%s\n' 'logging=awslogs health=healthy'
fi
EOF
chmod +x "$MOCK_SUDO"

cloudwatch_env="${TEST_ROOT}/cloudwatch.env"
cat > "$cloudwatch_env" <<'EOF'
AWS_REGION=ap-northeast-2
CLOUDWATCH_LOG_GROUP=/chipthrone/api
EOF

PATH="${TEST_ROOT}:${PATH}" MOCK_CALLS="$MOCK_CALLS" \
  CHIPTHRONE_ENV_FILE="$cloudwatch_env" bash "$RUN_SCRIPT" >/dev/null
grep -q -- '--log-driver awslogs' "$MOCK_CALLS"
grep -q -- '--log-opt awslogs-region=ap-northeast-2' "$MOCK_CALLS"
grep -q -- '--log-opt awslogs-group=/chipthrone/api' "$MOCK_CALLS"
grep -q -- '-e CONSOLE_LOG_STRUCTURED_FORMAT=ecs' "$MOCK_CALLS"

: > "$MOCK_CALLS"
local_env="${TEST_ROOT}/local.env"
PATH="${TEST_ROOT}:${PATH}" MOCK_CALLS="$MOCK_CALLS" \
  CHIPTHRONE_ENV_FILE="$local_env" bash "$RUN_SCRIPT" >/dev/null
grep -q -- '--log-driver local' "$MOCK_CALLS"
grep -q -- '--log-opt max-size=10m' "$MOCK_CALLS"
if grep -q -- 'CONSOLE_LOG_STRUCTURED_FORMAT' "$MOCK_CALLS"; then
  echo "local logging unexpectedly enabled structured output" >&2
  exit 1
fi

partial_env="${TEST_ROOT}/partial.env"
printf '%s\n' 'AWS_REGION=ap-northeast-2' > "$partial_env"
set +e
PATH="${TEST_ROOT}:${PATH}" MOCK_CALLS="$MOCK_CALLS" \
  CHIPTHRONE_ENV_FILE="$partial_env" bash "$RUN_SCRIPT" >/dev/null 2>&1
status="$?"
set -e
if [[ "$status" != 64 ]]; then
  echo "expected partial CloudWatch config to exit 64, got ${status}" >&2
  exit 1
fi

echo "run-api-container tests passed"
