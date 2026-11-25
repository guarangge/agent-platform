#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TESTS="AgentRunWorkerStabilityTest,ToolCircuitBreakerServiceTest,WorkflowFailureSemanticsTest,RuntimeLlmGatewayServiceTest,DockerSandboxExecutorIntegrationTest"

echo "[fault-injection] worker/tool/workflow/llm/sandbox"
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  docker image inspect agent-sandbox-runtime:1.0.0 >/dev/null 2>&1 || \
    docker build -t agent-sandbox-runtime:1.0.0 "$ROOT_DIR/docker/sandbox"
  SANDBOX_INTEGRATION_TEST=true mvn -f "$ROOT_DIR/pom.xml" -pl agent-core -Dtest="$TESTS" test
else
  echo "[fault-injection] Docker 不可用，仅跳过真实 Sandbox 容器故障注入" >&2
  mvn -f "$ROOT_DIR/pom.xml" -pl agent-core -Dtest="$TESTS" test
fi
