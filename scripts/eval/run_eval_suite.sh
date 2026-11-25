#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "用法: $0 <suite_id>"
  exit 1
fi

if [[ -z "${AGENT_CORE_BASE_URL:-}" ]]; then
  echo "缺少环境变量 AGENT_CORE_BASE_URL"
  exit 1
fi

if [[ -z "${AGENT_CORE_JWT:-}" ]]; then
  echo "缺少环境变量 AGENT_CORE_JWT"
  exit 1
fi

SUITE_ID="$1"
RESPONSE_FILE="$(mktemp)"

curl --silent --show-error --fail \
  -X POST "${AGENT_CORE_BASE_URL%/}/api/core/evals/suites/${SUITE_ID}/runs" \
  -H "Authorization: Bearer ${AGENT_CORE_JWT}" \
  -H "Content-Type: application/json" \
  -d '{"triggerType":"ci"}' > "${RESPONSE_FILE}"

python3 - "$RESPONSE_FILE" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, "r", encoding="utf-8") as fh:
    payload = json.load(fh)

data = payload.get("data") or {}
run_status = data.get("runStatus")
run_code = data.get("runCode")
avg = data.get("averageScore")
passed = data.get("passedCases")
total = data.get("totalCases")

print(f"runCode={run_code}")
print(f"runStatus={run_status}")
print(f"averageScore={avg}")
print(f"passedCases={passed}/{total}")

if run_status != "success":
    sys.exit(2)
PY

rm -f "${RESPONSE_FILE}"
