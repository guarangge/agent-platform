#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REPEAT_COUNT="${STABILITY_REPEAT:-3}"
TESTS="ConversationStateContinuityTest,AgentResourceAssemblyIntegrationTest,WorkflowCommonExecutionIntegrationTest,McpStdioTransportTest,McpLegacySseTransportTest,AgentRunWorkerStabilityTest,ToolCircuitBreakerServiceTest,TraceDataSanitizerTest"

if ! [[ "$REPEAT_COUNT" =~ ^[1-9][0-9]*$ ]]; then
  echo "STABILITY_REPEAT must be a positive integer: $REPEAT_COUNT" >&2
  exit 2
fi

for ((run = 1; run <= REPEAT_COUNT; run++)); do
  echo "[stability] run ${run}/${REPEAT_COUNT}"
  mvn -f "$ROOT_DIR/pom.xml" -pl agent-core -Dtest="$TESTS" test
done
