#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNTIME_DIR="${PROJECT_ROOT}/.runtime"
MCP_FILESYSTEM_ROOT="${MCP_FILESYSTEM_ROOT:-${RUNTIME_DIR}/mcp-filesystem}"
AGENT_WORKSPACE_ROOT="${AGENT_WORKSPACE_ROOT:-${RUNTIME_DIR}/agent-workspace}"
SANDBOX_WORKSPACE_ROOT="${SANDBOX_WORKSPACE_ROOT:-${RUNTIME_DIR}/sandbox-workspace}"
KNOWLEDGE_STORAGE_ROOT="${REGISTRY_KNOWLEDGE_STORAGE_ROOT:-${RUNTIME_DIR}/knowledge-files}"
RUNTIME_IMAGE="${AGENT_RUNTIME_IMAGE:-agent-platform-runtime:2026.07.28}"
PLUGIN_ENVIRONMENT_CODE="${CORE_RUNTIME_PLUGIN_ENVIRONMENT_CODE:-default}"
GAOKAO_PLUGIN_JAR="${PROJECT_ROOT}/agent-tool-plugin-gaokao/target/agent-tool-plugin-gaokao-0.0.1-SNAPSHOT.jar"
IMAGE_PLUGIN_JAR="${PROJECT_ROOT}/agent-tool-plugin-image/target/agent-tool-plugin-image-0.0.1-SNAPSHOT.jar"
DOCKER_NETWORK="${AGENT_DOCKER_NETWORK:-agent-platform_default}"
POSTGRES_CONTAINER="${AGENT_POSTGRES_CONTAINER:-agent-postgres}"
# 在任何数据库或容器操作前验证配置，避免运行到一半才发现缺少密钥。
: "${POSTGRES_ADMIN_PASSWORD:?请设置 POSTGRES_ADMIN_PASSWORD}"
: "${MCP_POSTGRES_PASSWORD:?请设置 MCP_POSTGRES_PASSWORD}"
: "${REGISTRY_RUNTIME_SERVICE_TOKEN:?请设置 REGISTRY_RUNTIME_SERVICE_TOKEN}"
: "${REGISTRY_JWT_SECRET:?请设置 REGISTRY_JWT_SECRET}"
: "${CORE_JWT_SECRET:?请设置 CORE_JWT_SECRET}"
: "${REGISTRY_TOOL_ENCRYPTION_KEY:?请设置 REGISTRY_TOOL_ENCRYPTION_KEY，已有数据库必须沿用原值}"
: "${CORE_WAIT_WEBHOOK_SECRET:?请设置 CORE_WAIT_WEBHOOK_SECRET}"
if [[ "${REGISTRY_JWT_SECRET}" != "${CORE_JWT_SECRET}" ]]; then
  echo "REGISTRY_JWT_SECRET 与 CORE_JWT_SECRET 必须一致" >&2
  exit 1
fi
MCP_POSTGRES_DATABASE="${MCP_POSTGRES_DATABASE:-agent_mcp_demo}"
MCP_POSTGRES_USER="${MCP_POSTGRES_USER:-agent_mcp_reader}"
MCP_POSTGRES_READONLY_URL="postgresql://${MCP_POSTGRES_USER}:${MCP_POSTGRES_PASSWORD}@${POSTGRES_CONTAINER}:5432/${MCP_POSTGRES_DATABASE}"

mkdir -p "${MCP_FILESYSTEM_ROOT}" "${AGENT_WORKSPACE_ROOT}" "${SANDBOX_WORKSPACE_ROOT}" "${KNOWLEDGE_STORAGE_ROOT}"

echo "初始化只读 PostgreSQL MCP 示例库"
docker exec -i -e PGPASSWORD="${POSTGRES_ADMIN_PASSWORD}" "${POSTGRES_CONTAINER}" \
  psql -U postgres -d postgres -v ON_ERROR_STOP=1 \
  -v db_name="${MCP_POSTGRES_DATABASE}" -v reader_name="${MCP_POSTGRES_USER}" -v reader_password="${MCP_POSTGRES_PASSWORD}" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'reader_name', :'reader_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'reader_name') \gexec
SELECT format('ALTER ROLE %I PASSWORD %L', :'reader_name', :'reader_password') \gexec
SELECT format('CREATE DATABASE %I', :'db_name')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db_name') \gexec
SQL
docker exec -i -e PGPASSWORD="${POSTGRES_ADMIN_PASSWORD}" "${POSTGRES_CONTAINER}" \
  psql -U postgres -d "${MCP_POSTGRES_DATABASE}" -v ON_ERROR_STOP=1 \
  -v reader_name="${MCP_POSTGRES_USER}" <<'SQL'
CREATE TABLE IF NOT EXISTS public.platform_capabilities (
    capability_code VARCHAR(64) PRIMARY KEY,
    capability_name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO public.platform_capabilities(capability_code, capability_name)
VALUES ('MCP_STDIO', 'MCP stdio 协议调用'), ('MCP_HTTP', 'MCP Streamable HTTP 协议调用')
ON CONFLICT (capability_code) DO UPDATE SET capability_name = EXCLUDED.capability_name;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'reader_name') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'reader_name') \gexec
SELECT format('GRANT SELECT ON ALL TABLES IN SCHEMA public TO %I', :'reader_name') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO %I', :'reader_name') \gexec
SQL

echo "构建后端 JAR、Runtime Plugin 与 MCP Runtime 镜像"
(cd "${PROJECT_ROOT}/registry-center" && mvn -q -DskipTests package)
(cd "${PROJECT_ROOT}/agent-core" && mvn -q -DskipTests package)
(cd "${PROJECT_ROOT}" && mvn -q -pl agent-tool-plugin-gaokao,agent-tool-plugin-image -am -DskipTests package)
test -r "${GAOKAO_PLUGIN_JAR}" && test -r "${IMAGE_PLUGIN_JAR}"
docker build \
  --build-arg BASE_IMAGE="${AGENT_RUNTIME_BASE_IMAGE:-eclipse-temurin:17-jre-noble}" \
  -t "${RUNTIME_IMAGE}" \
  -f "${PROJECT_ROOT}/docker/runtime/Dockerfile" "${PROJECT_ROOT}"

docker rm -f agent-core agent-registry-center >/dev/null 2>&1 || true

docker run -d --name agent-registry-center --restart unless-stopped \
  --network "${DOCKER_NETWORK}" -p 8089:8089 \
  -e REGISTRY_DB_URL="jdbc:postgresql://${POSTGRES_CONTAINER}:5432/agent_registry_center" \
  -e REGISTRY_DB_USERNAME=postgres -e REGISTRY_DB_PASSWORD="${POSTGRES_ADMIN_PASSWORD}" \
  -e AGENT_CORE_BASE_URL=http://agent-core:8081/api/core \
  -e REGISTRY_RUNTIME_SERVICE_TOKEN="${REGISTRY_RUNTIME_SERVICE_TOKEN}" \
  -e REGISTRY_JWT_SECRET="${REGISTRY_JWT_SECRET}" \
  -e REGISTRY_TOOL_ENCRYPTION_KEY="${REGISTRY_TOOL_ENCRYPTION_KEY}" \
  -e REGISTRY_BOOTSTRAP_ADMIN_PASSWORD="${REGISTRY_BOOTSTRAP_ADMIN_PASSWORD:-}" \
  -e MCP_FILESYSTEM_ROOT=/workspace/mcp-filesystem \
  -e MCP_POSTGRES_READONLY_URL="${MCP_POSTGRES_READONLY_URL}" \
  -e GITHUB_MCP_TOKEN="${GITHUB_MCP_TOKEN:-}" \
  -e REGISTRY_KNOWLEDGE_STORAGE_ROOT=/workspace/knowledge-files \
  -e REGISTRY_KNOWLEDGE_PDF_OCR_ENABLED="${REGISTRY_KNOWLEDGE_PDF_OCR_ENABLED:-true}" \
  -e REGISTRY_KNOWLEDGE_PDF_OCR_RENDER_COMMAND="${REGISTRY_KNOWLEDGE_PDF_OCR_RENDER_COMMAND:-pdftoppm}" \
  -e REGISTRY_KNOWLEDGE_PDF_OCR_COMMAND="${REGISTRY_KNOWLEDGE_PDF_OCR_COMMAND:-tesseract}" \
  -e REGISTRY_KNOWLEDGE_PDF_OCR_LANGUAGES="${REGISTRY_KNOWLEDGE_PDF_OCR_LANGUAGES:-chi_sim+eng}" \
  -e REGISTRY_KNOWLEDGE_PDF_OCR_DPI="${REGISTRY_KNOWLEDGE_PDF_OCR_DPI:-240}" \
  -e REGISTRY_KNOWLEDGE_PDF_OCR_PAGE_TIMEOUT_SECONDS="${REGISTRY_KNOWLEDGE_PDF_OCR_PAGE_TIMEOUT_SECONDS:-60}" \
  -e REGISTRY_KNOWLEDGE_DOCX_OCR_ENABLED="${REGISTRY_KNOWLEDGE_DOCX_OCR_ENABLED:-true}" \
  -e REGISTRY_KNOWLEDGE_DOCX_OCR_COMMAND="${REGISTRY_KNOWLEDGE_DOCX_OCR_COMMAND:-tesseract}" \
  -e REGISTRY_KNOWLEDGE_DOCX_OCR_LANGUAGES="${REGISTRY_KNOWLEDGE_DOCX_OCR_LANGUAGES:-chi_sim+eng}" \
  -e REGISTRY_KNOWLEDGE_DOCX_OCR_IMAGE_TIMEOUT_SECONDS="${REGISTRY_KNOWLEDGE_DOCX_OCR_IMAGE_TIMEOUT_SECONDS:-60}" \
  -e REGISTRY_KNOWLEDGE_DOCX_OCR_MAX_IMAGES="${REGISTRY_KNOWLEDGE_DOCX_OCR_MAX_IMAGES:-100}" \
  -v "${PROJECT_ROOT}/registry-center/target/registry-center-0.0.1-SNAPSHOT.jar:/app/app.jar:ro" \
  -v "${MCP_FILESYSTEM_ROOT}:/workspace/mcp-filesystem" \
  -v "${KNOWLEDGE_STORAGE_ROOT}:/workspace/knowledge-files" \
  "${RUNTIME_IMAGE}"

docker run -d --name agent-core --restart unless-stopped \
  --network "${DOCKER_NETWORK}" -p 8081:8081 \
  -e AGENT_CORE_DB_URL="jdbc:postgresql://${POSTGRES_CONTAINER}:5432/agent_core" \
  -e AGENT_CORE_DB_USERNAME=postgres -e AGENT_CORE_DB_PASSWORD="${POSTGRES_ADMIN_PASSWORD}" \
  -e REGISTRY_BASE_URL=http://agent-registry-center:8089/api/registry \
  -e REGISTRY_RUNTIME_SERVICE_TOKEN="${REGISTRY_RUNTIME_SERVICE_TOKEN}" \
  -e CORE_JWT_SECRET="${CORE_JWT_SECRET}" \
  -e CORE_WAIT_WEBHOOK_SECRET="${CORE_WAIT_WEBHOOK_SECRET}" \
  -e JAVA_TOOL_OPTIONS=-Dloader.path=/app/plugins \
  -e CORE_RUNTIME_PLUGIN_ENVIRONMENT_CODE="${PLUGIN_ENVIRONMENT_CODE}" \
  -e CORE_INTERACTIVE_LOOP_ENABLED="${CORE_INTERACTIVE_LOOP_ENABLED:-false}" \
  -e CORE_INTERACTIVE_LOOP_KILL_SWITCH="${CORE_INTERACTIVE_LOOP_KILL_SWITCH:-false}" \
  -e CORE_INTERACTIVE_LOOP_ROLLOUT_PERCENT="${CORE_INTERACTIVE_LOOP_ROLLOUT_PERCENT:-0}" \
  -e CORE_INTERACTIVE_LOOP_AGENT_IDS="${CORE_INTERACTIVE_LOOP_AGENT_IDS:-}" \
  -e CORE_INTERACTIVE_LOOP_PROJECT_IDS="${CORE_INTERACTIVE_LOOP_PROJECT_IDS:-}" \
  -e DOC_SEARCH_ROOT=/workspace/docs \
  -e CORE_AGENT_WORKSPACE_ROOT=/workspace/agent-workspace \
  -e CORE_SANDBOX_WORKSPACE_ROOT=/workspace/sandbox-workspace \
  -e CORE_SANDBOX_HOST_WORKSPACE_ROOT="${SANDBOX_WORKSPACE_ROOT}" \
  -e MCP_FILESYSTEM_ROOT=/workspace/mcp-filesystem \
  -e MCP_POSTGRES_READONLY_URL="${MCP_POSTGRES_READONLY_URL}" \
  -e GITHUB_MCP_TOKEN="${GITHUB_MCP_TOKEN:-}" \
  -e AGENT_PLUGIN_IMAGE_OCR_ENDPOINT="${AGENT_PLUGIN_IMAGE_OCR_ENDPOINT:-}" \
  -e AGENT_PLUGIN_IMAGE_OCR_MODEL="${AGENT_PLUGIN_IMAGE_OCR_MODEL:-}" \
  -e AGENT_PLUGIN_IMAGE_OCR_API_KEY="${AGENT_PLUGIN_IMAGE_OCR_API_KEY:-}" \
  -v "${PROJECT_ROOT}/agent-core/target/agent-core-0.0.1-SNAPSHOT-exec.jar:/app/app.jar:ro" \
  -v "${GAOKAO_PLUGIN_JAR}:/app/plugins/agent-tool-plugin-gaokao.jar:ro" \
  -v "${IMAGE_PLUGIN_JAR}:/app/plugins/agent-tool-plugin-image.jar:ro" \
  -v "${PROJECT_ROOT}/docs:/workspace/docs:ro" \
  -v "${AGENT_WORKSPACE_ROOT}:/workspace/agent-workspace" \
  -v "${SANDBOX_WORKSPACE_ROOT}:/workspace/sandbox-workspace" \
  -v "${MCP_FILESYSTEM_ROOT}:/workspace/mcp-filesystem" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  "${RUNTIME_IMAGE}"

echo "等待服务健康检查"
for url in http://localhost:8089/actuator/health http://localhost:8081/actuator/health; do
  for _ in $(seq 1 60); do
    if curl -fsS "${url}" >/dev/null; then break; fi
    sleep 2
  done
  curl -fsS "${url}"
  echo
done

if ! docker logs agent-core 2>&1 | grep -F "gaokao-tool-plugin:0.0.1-SNAPSHOT" >/dev/null; then
  echo "agent-core 未发现 gaokao-tool-plugin，请检查插件挂载和 loader.path" >&2
  docker logs --tail 200 agent-core >&2
  exit 1
fi
if ! docker logs agent-core 2>&1 | grep -F "image-understanding-plugin:0.0.1-SNAPSHOT" >/dev/null; then
  echo "agent-core 未发现 image-understanding-plugin，请检查插件挂载和 loader.path" >&2
  docker logs --tail 200 agent-core >&2
  exit 1
fi

echo "Runtime Plugin 已加载：gaokao-tool-plugin:0.0.1-SNAPSHOT, image-understanding-plugin:0.0.1-SNAPSHOT"
