#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/agent-platform-e2e.XXXXXX")"
CONTAINER_NAME="agent-platform-e2e-postgres-$$"
REGISTRY_PID=""
CORE_PID=""
MOCK_PID=""

free_port() {
  python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()'
}

cleanup() {
  for pid in "$CORE_PID" "$REGISTRY_PID" "$MOCK_PID"; do
    if [[ -n "$pid" ]]; then
      kill "$pid" >/dev/null 2>&1 || true
      wait "$pid" >/dev/null 2>&1 || true
    fi
  done
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

wait_http() {
  local url="$1"
  local name="$2"
  local pid="${3:-}"
  for _ in $(seq 1 120); do
    if curl --fail --silent "$url" >/dev/null; then return 0; fi
    if [[ -n "$pid" ]] && ! kill -0 "$pid" >/dev/null 2>&1; then
      echo "$name 启动失败" >&2
      tail -200 "$WORK_DIR/$name.log" >&2 || true
      return 1
    fi
    sleep 1
  done
  echo "等待 $name 超时: $url" >&2
  tail -200 "$WORK_DIR/$name.log" >&2 || true
  return 1
}

command -v docker >/dev/null || { echo "缺少 docker，无法运行平台 E2E" >&2; exit 1; }
command -v python3 >/dev/null || { echo "缺少 python3，无法运行平台 E2E" >&2; exit 1; }

POSTGRES_PORT="$(free_port)"
REGISTRY_PORT="$(free_port)"
CORE_PORT="$(free_port)"
MOCK_PORT="$(free_port)"
FRONTEND_PORT="$(free_port)"
POSTGRES_IMAGE="${PLATFORM_E2E_POSTGRES_IMAGE:-pgvector/pgvector:pg16}"
JWT_SECRET="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
SERVICE_TOKEN="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
export PLATFORM_E2E_ADMIN_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
export REGISTRY_TOOL_ENCRYPTION_KEY="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
export CORE_WAIT_WEBHOOK_SECRET="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"

cd "$ROOT_DIR"
mvn -q -DskipTests clean package
PLUGIN_JAR="$ROOT_DIR/agent-tool-plugin-gaokao/target/agent-tool-plugin-gaokao-0.0.1-SNAPSHOT.jar"
IMAGE_PLUGIN_JAR="$ROOT_DIR/agent-tool-plugin-image/target/agent-tool-plugin-image-0.0.1-SNAPSHOT.jar"
PLUGIN_DIR="$WORK_DIR/plugins"
mkdir -p "$PLUGIN_DIR"
cp "$PLUGIN_JAR" "$PLUGIN_DIR/agent-tool-plugin-gaokao.jar"
cp "$IMAGE_PLUGIN_JAR" "$PLUGIN_DIR/agent-tool-plugin-image.jar"
PLUGIN_SHA256="$(python3 -c 'import hashlib, pathlib, sys; print(hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest())' "$PLUGIN_JAR")"
IMAGE_PLUGIN_SHA256="$(python3 -c 'import hashlib, pathlib, sys; print(hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest())' "$IMAGE_PLUGIN_JAR")"

if ! docker image inspect "$POSTGRES_IMAGE" >/dev/null 2>&1; then
  for attempt in 1 2 3; do
    if docker pull "$POSTGRES_IMAGE"; then break; fi
    if [[ "$attempt" == "3" ]]; then
      echo "拉取 PostgreSQL 测试镜像失败: $POSTGRES_IMAGE" >&2
      exit 1
    fi
    sleep $((attempt * 3))
  done
fi
docker run -d --name "$CONTAINER_NAME" \
  -e POSTGRES_PASSWORD=123456 \
  -p "127.0.0.1:${POSTGRES_PORT}:5432" \
  "$POSTGRES_IMAGE" >/dev/null
for _ in $(seq 1 60); do
  if docker exec "$CONTAINER_NAME" pg_isready -U postgres >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$CONTAINER_NAME" createdb -U postgres agent_registry_center
docker exec "$CONTAINER_NAME" createdb -U postgres agent_core
docker exec "$CONTAINER_NAME" psql -U postgres -c "CREATE ROLE agent_e2e LOGIN PASSWORD 'agent_e2e_password'"
docker exec "$CONTAINER_NAME" psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE agent_registry_center TO agent_e2e"
docker exec "$CONTAINER_NAME" psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE agent_core TO agent_e2e"
docker exec "$CONTAINER_NAME" psql -U postgres -d agent_registry_center -c "GRANT ALL ON SCHEMA public TO agent_e2e"
docker exec "$CONTAINER_NAME" psql -U postgres -d agent_core -c "GRANT ALL ON SCHEMA public TO agent_e2e; CREATE EXTENSION IF NOT EXISTS vector"

python3 "$ROOT_DIR/scripts/test/mock_openai_server.py" --port "$MOCK_PORT" >"$WORK_DIR/mock.log" 2>&1 &
MOCK_PID=$!
wait_http "http://127.0.0.1:${MOCK_PORT}/health" "mock" "$MOCK_PID"

REGISTRY_DB_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/agent_registry_center" \
REGISTRY_DB_USERNAME=agent_e2e REGISTRY_DB_PASSWORD=agent_e2e_password \
REGISTRY_JWT_SECRET="$JWT_SECRET" CORE_JWT_SECRET="$JWT_SECRET" \
REGISTRY_BOOTSTRAP_ADMIN_PASSWORD="$PLATFORM_E2E_ADMIN_PASSWORD" \
REGISTRY_RUNTIME_SERVICE_TOKEN="$SERVICE_TOKEN" SERVER_PORT="$REGISTRY_PORT" \
java -jar "$ROOT_DIR/registry-center/target/registry-center-0.0.1-SNAPSHOT.jar" \
  "--registry.security.allowed-origins[0]=http://127.0.0.1:${FRONTEND_PORT}" \
  >"$WORK_DIR/registry.log" 2>&1 &
REGISTRY_PID=$!
wait_http "http://127.0.0.1:${REGISTRY_PORT}/api/health" "registry" "$REGISTRY_PID"

AGENT_CORE_DB_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/agent_core" \
AGENT_CORE_DB_USERNAME=agent_e2e AGENT_CORE_DB_PASSWORD=agent_e2e_password \
CORE_JWT_SECRET="$JWT_SECRET" REGISTRY_RUNTIME_SERVICE_TOKEN="$SERVICE_TOKEN" \
REGISTRY_BASE_URL="http://127.0.0.1:${REGISTRY_PORT}/api/registry" \
CORE_RUNTIME_PLUGIN_ENVIRONMENT_CODE=e2e \
CORE_RUN_WORKER_POLL_DELAY_MS=100 CORE_RUN_RECOVERY_INTERVAL_MS=1000 \
SERVER_PORT="$CORE_PORT" \
java -Dloader.path="$PLUGIN_DIR" -jar "$ROOT_DIR/agent-core/target/agent-core-0.0.1-SNAPSHOT-exec.jar" \
  "--core.security.allowed-origins[0]=http://127.0.0.1:${FRONTEND_PORT}" \
  >"$WORK_DIR/core.log" 2>&1 &
CORE_PID=$!
wait_http "http://127.0.0.1:${CORE_PORT}/api/health" "core" "$CORE_PID"

if ! python3 "$ROOT_DIR/scripts/test/platform_e2e_client.py" \
  --registry-port "$REGISTRY_PORT" --core-port "$CORE_PORT" --mock-port "$MOCK_PORT" \
  --plugin-sha256 "$PLUGIN_SHA256" --image-plugin-sha256 "$IMAGE_PLUGIN_SHA256" \
  --sdk-context-output "$WORK_DIR/sdk-context.json"; then
  echo "registry-center 日志：" >&2
  tail -200 "$WORK_DIR/registry.log" >&2 || true
  echo "agent-core 日志：" >&2
  tail -300 "$WORK_DIR/core.log" >&2 || true
  exit 1
fi

SDK_TOKEN="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["accessToken"])' "$WORK_DIR/sdk-context.json")"
SDK_AGENT_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["agentId"])' "$WORK_DIR/sdk-context.json")"
AGENT_RUNTIME_TOKEN="$SDK_TOKEN" java -jar \
  "$ROOT_DIR/agent-runtime-sdk-java-example/target/agent-runtime-sdk-java-example.jar" \
  --base-url "http://127.0.0.1:${CORE_PORT}/api/core" \
  --agent-id "$SDK_AGENT_ID" \
  --title "Java Runtime SDK E2E" \
  --message "验证异步链路。Java Runtime SDK E2E" \
  --idempotency-key "java-runtime-sdk-e2e" \
  --stream-timeout-seconds 60

if [[ "${RUN_FRONTEND_E2E:-false}" == "true" ]]; then
  cd "$ROOT_DIR/frontend-web"
  VITE_REGISTRY_API_BASE_URL="http://127.0.0.1:${REGISTRY_PORT}/api/registry" \
  VITE_CORE_API_BASE_URL="http://127.0.0.1:${CORE_PORT}/api/core" \
  FRONTEND_E2E_PORT="$FRONTEND_PORT" \
  npm run test:e2e
fi
