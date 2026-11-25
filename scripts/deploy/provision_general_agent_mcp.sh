#!/usr/bin/env bash
set -euo pipefail

REGISTRY_URL="${REGISTRY_URL:-http://localhost:8089/api/registry}"
REGISTRY_ADMIN_USERNAME="${REGISTRY_ADMIN_USERNAME:-zc}"
: "${REGISTRY_ADMIN_PASSWORD:?请设置 REGISTRY_ADMIN_PASSWORD}"
GENERAL_AGENT_CODE="${GENERAL_AGENT_CODE:-general_context_test_agent}"
GENERAL_AGENT_MAX_LLM_CALLS="${GENERAL_AGENT_MAX_LLM_CALLS:-20}"

for command in curl jq; do
  command -v "${command}" >/dev/null || { echo "缺少命令：${command}" >&2; exit 1; }
done

TOKEN="$(curl -fsS -X POST "${REGISTRY_URL}/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(jq -cn --arg username "${REGISTRY_ADMIN_USERNAME}" --arg password "${REGISTRY_ADMIN_PASSWORD}" \
    '{username:$username,password:$password}')" | jq -r '.data.accessToken // .data.token')"

request() {
  local method="$1" path="$2" payload="${3:-}" response
  if [[ -n "${payload}" ]]; then
    response="$(curl -fsS -X "${method}" "${REGISTRY_URL}${path}" \
      -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' -d "${payload}")"
  else
    response="$(curl -fsS -X "${method}" "${REGISTRY_URL}${path}" \
      -H "Authorization: Bearer ${TOKEN}")"
  fi
  if [[ "$(jq -r '.code' <<<"${response}")" != "200" ]]; then
    jq -r '"API失败：" + (.message // "unknown")' <<<"${response}" >&2
    return 1
  fi
  printf '%s' "${response}"
}

discover_stdio() {
  local catalog_id="$1" command="$2" argument="$3"
  request POST /tools/lifecycle/discover "$(jq -cn \
    --argjson catalogId "${catalog_id}" --arg command "${command}" --arg argument "${argument}" \
    '{catalogId:$catalogId,providerType:"mcp",transportType:"stdio",endpoint:null,authConfig:{},headers:{},
      invokeConfig:{command:$command,args:[$argument],inheritEnvironment:true}}')" >/dev/null
}

tool_id() {
  local code="$1"
  request GET "/tools?pageNum=1&pageSize=10&toolCode=${code}" | jq -r --arg code "${code}" \
    '.data.records[] | select(.toolCode == $code) | .toolId' | head -n 1
}

install_item() {
  local item_id="$1" remote_name="$2" display_name="$3" prefix="$4"
  local permission="$5" invoke_command="$6" invoke_argument="$7" category="$8" type="$9"
  local code existing payload
  code="$(printf '%s_%s' "${prefix}" "${remote_name}" | tr '[:lower:]-' '[:upper:]_')"
  existing="$(tool_id "${code}")"
  if [[ -n "${existing}" ]]; then
    printf '%s\n' "${existing}"
    return
  fi
  payload="$(jq -cn --argjson itemId "${item_id}" --arg code "${code}" --arg name "${display_name}" \
    --arg type "${type}" --arg category "${category}" --arg permission "${permission}" \
    --arg command "${invoke_command}" --arg argument "${invoke_argument}" \
    '{catalogItemId:$itemId,toolCode:$code,toolName:$name,toolType:$type,toolCategory:$category,
      permissionLevel:$permission,version:"1.0.0",providerType:"mcp",transportType:"stdio",endpoint:null,
      authType:"none",environmentCode:"local",authConfig:{},headers:{},
      invokeConfig:{command:$command,args:[$argument],inheritEnvironment:true}}')"
  request POST /tools/lifecycle/install "${payload}" | jq -r '.data.toolId'
}

upsert_pack() {
  local code="$1" name="$2" description="$3" tool_ids_json="$4" metadata="$5"
  local pack_id payload response
  pack_id="$(request GET /tool-capability-packs | jq -r --arg code "${code}" \
    '.data[] | select(.pack_code == $code) | .id' | head -n 1)"
  payload="$(jq -cn --arg code "${code}" --arg name "${name}" --arg description "${description}" \
    --argjson toolIds "${tool_ids_json}" --argjson metadata "${metadata}" \
    '{packCode:$code,packName:$name,description:$description,version:"1.0.0",status:"enabled",
      toolIds:$toolIds,metadata:$metadata}')"
  if [[ -n "${pack_id}" ]]; then
    response="$(request PUT "/tool-capability-packs/${pack_id}" "${payload}")"
  else
    response="$(request POST /tool-capability-packs "${payload}")"
  fi
  jq -r '.data.id' <<<"${response}"
}

discover_stdio 1 mcp-server-filesystem '${MCP_FILESYSTEM_ROOT}'
discover_stdio 2 mcp-server-postgres '${MCP_POSTGRES_READONLY_URL}'

filesystem_read_ids=()
filesystem_write_ids=()
while IFS=$'\t' read -r item_id remote_name display_name; do
  case "${remote_name}" in
    create_directory|edit_file|move_file|write_file)
      filesystem_write_ids+=("$(install_item "${item_id}" "${remote_name}" "${display_name}" MCP_FS admin \
        mcp-server-filesystem '${MCP_FILESYSTEM_ROOT}' document file)")
      ;;
    read_file)
      install_item "${item_id}" "${remote_name}" "${display_name}" MCP_FS restricted \
        mcp-server-filesystem '${MCP_FILESYSTEM_ROOT}' document file >/dev/null
      ;;
    *)
      filesystem_read_ids+=("$(install_item "${item_id}" "${remote_name}" "${display_name}" MCP_FS restricted \
        mcp-server-filesystem '${MCP_FILESYSTEM_ROOT}' document file)")
      ;;
  esac
done < <(request GET /tools/lifecycle/catalogs/1/items | jq -r '.data[] | [.id,.remote_name,.display_name] | @tsv')

postgres_item="$(request GET /tools/lifecycle/catalogs/2/items | jq -r '.data[] | select(.remote_name == "query") | [.id,.remote_name,.display_name] | @tsv')"
IFS=$'\t' read -r postgres_item_id postgres_remote postgres_display <<<"${postgres_item}"
postgres_tool_id="$(install_item "${postgres_item_id}" "${postgres_remote}" "PostgreSQL Readonly Query" MCP_PG \
  restricted mcp-server-postgres '${MCP_POSTGRES_READONLY_URL}' general knowledge)"

filesystem_read_json="$(printf '%s\n' "${filesystem_read_ids[@]}" | jq -s 'map(tonumber)')"
filesystem_write_json="$(printf '%s\n' "${filesystem_write_ids[@]}" | jq -s 'map(tonumber)')"
read_pack_id="$(upsert_pack MCP_FILESYSTEM_READ 'MCP 文件系统只读能力包' \
  '受专用目录边界约束的目录浏览、文件读取和搜索能力' "${filesystem_read_json}" \
  '{"managedBy":"platform","riskLevel":"restricted","accessMode":"read_only"}')"
write_pack_id="$(upsert_pack MCP_FILESYSTEM_WRITE 'MCP 文件系统写入能力包' \
  '受专用目录边界和人工审批约束的创建、编辑、移动与写入能力' "${filesystem_write_json}" \
  '{"managedBy":"platform","riskLevel":"admin","accessMode":"read_write","approvalRequired":true}')"
database_pack_id="$(upsert_pack MCP_DATABASE_READ 'MCP 数据库只读能力包' \
  '使用独立只读账号执行 PostgreSQL SELECT 查询' "[$postgres_tool_id]" \
  '{"managedBy":"platform","riskLevel":"restricted","accessMode":"read_only"}')"

agent_id="$(request GET "/agents?pageNum=1&pageSize=20&agentCode=${GENERAL_AGENT_CODE}" | jq -r \
  --arg code "${GENERAL_AGENT_CODE}" '.data.records[] | select(.agentCode == $code) | .agentId' | head -n 1)"
[[ -n "${agent_id}" ]] || { echo "通用 Agent 不存在：${GENERAL_AGENT_CODE}" >&2; exit 1; }
agent_payload="$(request GET "/agents/${agent_id}" | jq -c --argjson maxCalls "${GENERAL_AGENT_MAX_LLM_CALLS}" \
  '.data | {
    agentCode,agentName,description,version,defaultModel,defaultPromptId,decisionPromptId,plannerPromptId,
    toolScopeMode,status,metadata:(.metadata | .executionPolicy.maxLlmCallsPerRun = $maxCalls),remark
  }')"
request PUT "/agents/${agent_id}" "${agent_payload}" >/dev/null
base_pack_ids="$(request GET /tool-capability-packs | jq -c '[.data[] | select(.pack_code == "BASIC_REASONING" or
  .pack_code == "RESEARCH_RETRIEVAL" or .pack_code == "DOCUMENT_WORKSPACE" or
  .pack_code == "CONTROLLED_EXECUTION") | .id]')"
all_pack_ids="$(jq -cn --argjson base "${base_pack_ids}" --argjson read "${read_pack_id}" \
  --argjson write "${write_pack_id}" --argjson database "${database_pack_id}" '$base + [$read,$write,$database] | unique')"
request PUT "/agents/${agent_id}/tool-capability-packs" "$(jq -cn --argjson ids "${all_pack_ids}" '{packIds:$ids}')" >/dev/null

sandbox_tool_id="$(tool_id SANDBOX_EXECUTE)"
policy_payload="$(jq -cn --argjson agentId "${agent_id}" --argjson sandboxId "${sandbox_tool_id}" \
  --argjson writeIds "${filesystem_write_json}" \
  '{policyCode:"GENERAL_AGENT_HIGH_RISK_TOOL_APPROVAL",policyName:"通用Agent高风险Tool审批",
    policyVersion:1,priority:900,status:"enabled",description:"通用Agent执行Sandbox或MCP文件写入前必须由平台管理员审批",
    rolloutMode:"all",rolloutPercentage:100,rolloutUserIds:[],scopeUserIds:[],scopeRoleCodes:[],
    scopeAgentIds:[$agentId],scopeToolIds:([$sandboxId] + $writeIds),scopeWorkflowIds:[],scopeEnvs:[],
    matchMode:"always",requireNonEmptyInput:false,maxInputLength:null,blockedKeywords:[],keywords:[],
    actionType:"approval",approvalNodeName:"高风险Tool人工审批",approvalUserIds:[1],approvalRoleCodes:["ADMIN"],
    reason:"该操作可能执行代码或修改持久化文件，必须人工确认",
    metadata:{managedBy:"platform",riskLevel:"high",stage:"input"},remark:"通用Agent默认高风险审批策略"}')"
policy_id="$(request GET '/guardrail-policies?pageNum=1&pageSize=10&policyCode=GENERAL_AGENT_HIGH_RISK_TOOL_APPROVAL' |
  jq -r '.data.records[] | select(.policyCode == "GENERAL_AGENT_HIGH_RISK_TOOL_APPROVAL") | .policyId' | head -n 1)"
if [[ -n "${policy_id}" ]]; then
  request PUT "/guardrail-policies/${policy_id}" "${policy_payload}" >/dev/null
else
  request POST /guardrail-policies "${policy_payload}" >/dev/null
fi

if [[ -n "${GITHUB_MCP_TOKEN:-}" ]]; then
  request POST /tools/lifecycle/discover "$(jq -cn --arg token "${GITHUB_MCP_TOKEN}" \
    '{catalogId:3,providerType:"mcp",transportType:"streamable_http",endpoint:"https://api.githubcopilot.com/mcp/",
      authConfig:{bearerToken:$token},headers:{},invokeConfig:{}}')" >/dev/null
  echo "GitHub MCP 已完成发现；请按最小权限原则人工选择只读 Tool 安装。"
else
  echo "GitHub MCP 跳过：未提供 GITHUB_MCP_TOKEN。"
fi

echo "通用 Agent MCP provisioning 完成：agentId=${agent_id}, packIds=${all_pack_ids}"
