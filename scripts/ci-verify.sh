#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git diff --check
fi
python3 "$ROOT_DIR/scripts/security/check_secrets.py"
python3 -m unittest discover -s "$ROOT_DIR/scripts/test" -p 'test_publication_security.py'
mvn test
bash "$ROOT_DIR/scripts/test/run_agent_stability_suite.sh"
bash "$ROOT_DIR/scripts/test/run_fault_injection_suite.sh"

cd "$ROOT_DIR/frontend-web"
npm ci
npm run build
if [[ "${CI:-false}" == "true" ]]; then
  npx playwright install --with-deps chromium
else
  npx playwright install chromium
fi

cd "$ROOT_DIR"
RUN_FRONTEND_E2E=true bash "$ROOT_DIR/scripts/test/run_platform_e2e.sh"
