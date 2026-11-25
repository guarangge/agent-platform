#!/usr/bin/env python3
"""通过真实 HTTP 接口验证双服务 Agent 主链。"""

import argparse
import base64
import json
import os
import time
import urllib.error
import urllib.parse
from concurrent.futures import ThreadPoolExecutor
import urllib.request
import uuid
from typing import Optional


class ApiClient:
    def __init__(self, base_url: str, token: Optional[str] = None):
        self.base_url = base_url.rstrip("/")
        self.token = token

    def request(self, method: str, path: str, body=None):
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        request = urllib.request.Request(self.base_url + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise AssertionError(f"{method} {path} HTTP {error.code}: {detail}") from error
        if payload.get("code") != 200:
            raise AssertionError(f"{method} {path} 业务失败: {payload}")
        return payload.get("data")

    def get(self, path: str):
        return self.request("GET", path)

    def post(self, path: str, body):
        return self.request("POST", path, body)

    def put(self, path: str, body):
        return self.request("PUT", path, body)

    def upload(self, path: str, filename: str, content_type: str, content: bytes):
        boundary = "----AgentE2E" + uuid.uuid4().hex
        body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
                f"Content-Type: {content_type}\r\n\r\n").encode("utf-8") + content + f"\r\n--{boundary}--\r\n".encode("utf-8")
        headers = {"Content-Type": f"multipart/form-data; boundary={boundary}"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        request = urllib.request.Request(self.base_url + path, data=body, headers=headers, method="POST")
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = json.loads(response.read().decode("utf-8"))
        assert payload.get("code") == 200, payload
        return payload.get("data")

    def stream_events(self, run_code: str):
        headers = {"Accept": "text/event-stream"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        path = f"/api/core/runtime/runs/{urllib.parse.quote(run_code, safe='')}/events?afterSequence=0"
        request = urllib.request.Request(self.base_url + path, headers=headers, method="GET")
        with urllib.request.urlopen(request, timeout=60) as response:
            lines = response.read().decode("utf-8").splitlines()
        return [json.loads(line[5:].strip()) for line in lines if line.startswith("data:")]


def create_prompt(registry: ApiClient, code: str, name: str, prompt_type: str, content: str) -> int:
    result = registry.post("/api/registry/prompts", {
        "promptCode": code,
        "promptName": name,
        "promptType": prompt_type,
        "promptCategory": "general",
        "description": "平台 E2E 自动化资源",
        "content": content,
        "version": "1.0.0",
        "status": "enabled",
        "metadata": {"e2e": True},
        "remark": "由 platform E2E 创建",
    })
    return int(result["promptId"])


def assert_task(task: dict):
    assert task["status"] == "success", f"Task 未成功: {task}"
    assert task.get("traceId"), f"Task 缺少 traceId: {task}"
    assert task.get("plans"), f"Task 未保存 Plan: {task}"
    steps = task.get("steps") or []
    assert steps and all(item["status"] == "success" for item in steps), f"Step 未全部成功: {steps}"
    assert any(item["stepType"] == "reply" for item in steps), f"未执行 reply Step: {steps}"
    traces = task.get("traces") or []
    assert traces, f"Task 未保存 Trace: {task}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--registry-port", type=int, required=True)
    parser.add_argument("--core-port", type=int, required=True)
    parser.add_argument("--mock-port", type=int, required=True)
    parser.add_argument("--plugin-sha256", required=True)
    parser.add_argument("--image-plugin-sha256", required=True)
    parser.add_argument("--sdk-context-output")
    args = parser.parse_args()

    registry = ApiClient(f"http://127.0.0.1:{args.registry_port}")
    login = registry.post("/api/registry/auth/login", {"username": "zc", "password": os.environ["PLATFORM_E2E_ADMIN_PASSWORD"], "rememberMe": False})
    token = login["accessToken"]
    default_token = token
    registry.token = token
    me = registry.get("/api/registry/auth/me")
    assert me.get("currentScope", {}).get("tenantCode") == "DEFAULT", me
    tenant_id = registry.post("/api/registry/tenancy/tenants", {"code": "E2E_TENANT", "name": "E2E 租户"})
    organization_id = registry.post("/api/registry/tenancy/organizations", {"tenantId": tenant_id, "code": "E2E_ORG", "name": "E2E 组织"})
    workspace_id = registry.post("/api/registry/tenancy/workspaces", {"tenantId": tenant_id, "organizationId": organization_id, "code": "E2E_WS", "name": "E2E 工作区"})
    project_id = registry.post("/api/registry/tenancy/projects", {"tenantId": tenant_id, "organizationId": organization_id, "workspaceId": workspace_id, "code": "E2E_PROJECT", "name": "E2E 项目"})
    membership_id = registry.post("/api/registry/tenancy/memberships", {
        "userId": me["userId"], "tenantId": tenant_id, "organizationId": organization_id,
        "workspaceId": workspace_id, "projectId": project_id, "membershipRole": "owner", "defaultScope": False,
    })
    switched = registry.post("/api/registry/auth/switch-scope", {"membershipId": membership_id})
    token = switched["accessToken"]
    registry.token = token
    switched_me = registry.get("/api/registry/auth/me")
    assert switched_me["currentScope"]["tenantId"] == tenant_id, switched_me
    empty_models = registry.get("/api/registry/models?pageNum=1&pageSize=100")
    assert not (empty_models.get("records") or []), "RLS 未隔离默认租户 Model"
    core = ApiClient(f"http://127.0.0.1:{args.core_port}", token)
    default_core = ApiClient(f"http://127.0.0.1:{args.core_port}", default_token)

    runtime_plugin = registry.post("/api/registry/runtime-plugins", {
        "pluginCode": "gaokao-tool-plugin",
        "pluginName": "高考志愿 Tool 插件",
        "description": "平台 E2E 验证真实外部 Runtime Plugin Jar",
        "status": "enabled",
        "metadata": {"e2e": True},
    })
    runtime_plugin_id = int(runtime_plugin["pluginId"])
    runtime_version = registry.post(
        f"/api/registry/runtime-plugins/{runtime_plugin_id}/versions", {
            "version": "0.0.1-SNAPSHOT",
            "platformApiVersion": "1",
            "entrypoint": "com.zc.agent.plugin.gaokao.GaokaoAgentRuntimePlugin",
            "capabilities": [],
            "dependencies": [],
            "components": {"tools": [
                "SCORE_ANALYSIS", "DOC_SEARCH", "GAOKAO_POLICY_SEARCH",
                "GAOKAO_UNIVERSITY_SEARCH", "GAOKAO_MAJOR_SEARCH",
            ]},
            "artifactUri": "file:///app/plugins/agent-tool-plugin-gaokao.jar",
            "artifactSha256": args.plugin_sha256,
            "releaseNotes": "平台 E2E 制品",
            "status": "published",
        })
    runtime_version_id = int(runtime_version["versionId"])
    runtime_installation = registry.post(
        f"/api/registry/runtime-plugins/{runtime_plugin_id}/installations", {
            "versionId": runtime_version_id,
            "environmentCode": "e2e",
            "status": "enabled",
            "metadata": {"e2e": True},
        })
    runtime_installation_id = int(runtime_installation["installationId"])
    image_plugin = registry.post("/api/registry/runtime-plugins", {
        "pluginCode": "image-understanding-plugin",
        "pluginName": "图片理解 Tool 插件",
        "description": "平台 E2E 验证图片插件与多模态输入",
        "status": "enabled",
        "metadata": {"e2e": True},
    })
    image_plugin_id = int(image_plugin["pluginId"])
    image_version = registry.post(f"/api/registry/runtime-plugins/{image_plugin_id}/versions", {
        "version": "0.0.1-SNAPSHOT",
        "platformApiVersion": "1",
        "entrypoint": "com.zc.agent.plugin.image.ImageAgentRuntimePlugin",
        "capabilities": [],
        "dependencies": [],
        "components": {"tools": ["IMAGE_OCR", "IMAGE_LAYOUT_ANALYZE"]},
        "artifactUri": "file:///app/plugins/agent-tool-plugin-image.jar",
        "artifactSha256": args.image_plugin_sha256,
        "releaseNotes": "图片插件 E2E 制品",
        "status": "published",
    })
    registry.post(f"/api/registry/runtime-plugins/{image_plugin_id}/installations", {
        "versionId": int(image_version["versionId"]),
        "environmentCode": "e2e",
        "status": "enabled",
        "metadata": {"e2e": True},
    })
    plugin_snapshot = core.post("/api/core/runtime/plugins/reconcile", {})
    assert plugin_snapshot["status"] == "UP", plugin_snapshot
    assert plugin_snapshot["registrySync"]["status"] == "succeeded", plugin_snapshot
    observed_plugins = plugin_snapshot.get("plugins") or []
    assert len(observed_plugins) == 2, observed_plugins
    observed_by_code = {item["pluginCode"]: item for item in observed_plugins}
    assert observed_by_code["gaokao-tool-plugin"]["artifactSha256"] == args.plugin_sha256, observed_plugins
    assert observed_by_code["image-understanding-plugin"]["artifactSha256"] == args.image_plugin_sha256, observed_plugins
    plugin_detail = registry.get(f"/api/registry/runtime-plugins/{runtime_plugin_id}")
    installations = plugin_detail.get("installations") or []
    assert len(installations) == 1 and installations[0]["id"] == runtime_installation_id, installations
    assert installations[0]["load_status"] == "loaded", installations[0]
    audits = plugin_detail.get("audits") or []
    assert any(item["action"] == "report_load_result" for item in audits), audits

    registry.post("/api/registry/models", {
        "modelCode": "E2E_MOCK_MODEL",
        "modelName": "E2E Mock Model",
        "provider": "openai_compatible",
        "modelType": "chat",
        "baseUrl": f"http://127.0.0.1:{args.mock_port}",
        "apiKey": "e2e-api-key",
        "apiPath": "/v1/chat/completions",
        "modelIdentifier": "e2e-model",
        "temperature": 0.1,
        "maxTokens": 1024,
        "contextWindow": 8192,
        "reservedOutputTokens": 1024,
        "reservedSystemTokens": 512,
        "compactionTriggerRatio": 0.8,
        "tokenizerFamily": "cl100k_base",
        "timeoutSeconds": 20,
        "status": "enabled",
        "metadata": {"e2e": True, "inputModalities": ["text", "image"]},
        "remark": "平台主链 E2E Mock 模型",
    })
    default_prompt_id = create_prompt(registry, "E2E_DEFAULT_PROMPT", "E2E 默认 Prompt", "agent_system", "保持多轮上下文连续。")
    candidate_prompt_id = create_prompt(registry, "E2E_CANDIDATE_PROMPT", "E2E 候选 Prompt", "agent_system", "保持多轮上下文连续，并优先复用已确认事实。")
    decision_prompt_id = create_prompt(registry, "E2E_DECISION_PROMPT", "E2E 决策 Prompt", "decision_router", "对测试输入使用 direct_reply。")
    planner_prompt_id = create_prompt(registry, "E2E_PLANNER_PROMPT", "E2E 规划 Prompt", "planner_system", "生成一个可执行 reply 步骤。")

    agent = registry.post("/api/registry/agents", {
        "agentCode": "E2E_GENERAL_AGENT",
        "agentName": "E2E 通用 Agent",
        "description": "验证完整通用 Agent 执行链",
        "version": "1.0.0",
        "defaultModel": "E2E_MOCK_MODEL",
        "defaultPromptId": default_prompt_id,
        "decisionPromptId": decision_prompt_id,
        "plannerPromptId": planner_prompt_id,
        "toolScopeMode": "custom",
        "status": "enabled",
        "metadata": {"e2e": True},
        "remark": "由 platform E2E 创建",
    })
    agent_id = int(agent["agentId"])

    registry.post("/api/registry/prompt-experiments", {
        "experimentCode": "E2E_PROMPT_AB", "experimentName": "E2E Prompt 稳定分流",
        "basePromptId": default_prompt_id, "agentId": agent_id, "stage": "system",
        "allocationKey": "conversation", "rolloutPercent": 100, "status": "running",
        "variants": [
            {"variantCode": "control", "variantName": "Control", "promptId": default_prompt_id, "weight": 50, "control": True, "status": "enabled"},
            {"variantCode": "candidate", "variantName": "Candidate", "promptId": candidate_prompt_id, "weight": 50, "control": False, "status": "enabled"},
        ],
    })

    core.post("/api/core/cost-governance/prices", {
        "resourceType": "llm", "resourceCode": "E2E_MOCK_MODEL",
        "inputPricePer1000": 0.01, "outputPricePer1000": 0.02,
        "unitPrice": 0, "currency": "CNY", "status": "enabled",
    })
    core.post("/api/core/cost-governance/budgets", {
        "policyCode": "E2E_PROJECT_BUDGET", "policyName": "E2E 项目预算",
        "scopeType": "project", "scopeId": project_id, "periodType": "monthly",
        "budgetAmount": 100, "llmTokenLimit": 100000, "toolCallLimit": 1000,
        "sandboxSecondLimit": 10000, "delegationCallLimit": 1000,
        "warningPercent": 80, "hardLimit": True, "currency": "CNY",
        "priority": 100, "status": "enabled",
    })

    conversation = core.post("/api/core/conversations", {
        "agentId": agent_id,
        "title": "平台主链 E2E",
        "userMemoryEnabled": True,
        "userMemoryMode": "summary_and_facts",
        "userMemoryWritebackEnabled": True,
        "preferSummaryOverRawHistory": True,
        "maxRecentMessages": 12,
        "maxRecentUserMessages": 6,
        "preferStateFirst": True,
        "historySummaryThresholdMessages": 8,
        "minRetentionRatio": 0.5,
        "qualityWarningThreshold": 60,
    })
    conversation_id = int(conversation["conversationId"])

    png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
    attachment = core.upload(f"/api/core/conversations/{conversation_id}/attachments", "多模态测试.png", "image/png", png)
    image_run = core.post(f"/api/core/conversations/{conversation_id}/messages/async", {
        "content": "请分析这张图片。", "attachmentIds": [int(attachment["attachmentId"])]
    })
    image_run_code = image_run["runCode"]
    deadline = time.time() + 60
    while time.time() < deadline:
        image_run_result = core.get(f"/api/core/conversations/{conversation_id}/runs/{image_run_code}")
        if image_run_result["status"] in {"completed", "failed", "cancelled"}:
            break
        time.sleep(0.25)
    assert image_run_result["status"] == "completed", image_run_result
    image_events = core.stream_events(image_run_code)
    assert any(item.get("eventType") == "input.attachment.resolved"
               and item.get("payload", {}).get("route") == "native_vision" for item in image_events), image_events
    assert all("base64" not in json.dumps(item, ensure_ascii=False).lower() for item in image_events), image_events

    first = core.post(f"/api/core/conversations/{conversation_id}/messages", {"content": "我的项目代号是 ORBIT-42，请记住。"})
    assert "E2E_FIRST_OK" in first["assistantMessage"]["content"], first
    assert_task(first["task"])

    second = core.post(f"/api/core/conversations/{conversation_id}/messages", {"content": "请只返回我刚才给出的项目代号。"})
    assert "ORBIT-42" in second["assistantMessage"]["content"], second
    assert "E2E_HISTORY_OK" in second["assistantMessage"]["content"], "多轮上下文未注入 LLM"
    assert_task(second["task"])

    for index in range(10):
        filler = core.post(f"/api/core/conversations/{conversation_id}/messages", {"content": f"补充过程事实 {index + 1}：值为 F-{index + 1}。"})
        assert_task(filler["task"])
    long_context = core.post(f"/api/core/conversations/{conversation_id}/messages", {"content": "长期记忆校验：请只返回第一轮项目代号。"})
    assert "ORBIT-42" in long_context["assistantMessage"]["content"] and "E2E_LONG_CONTEXT_OK" in long_context["assistantMessage"]["content"], long_context
    assert_task(long_context["task"])

    with ThreadPoolExecutor(max_workers=4) as pool:
        runs = list(pool.map(lambda index: core.post(f"/api/core/conversations/{conversation_id}/messages/async", {"content": f"验证异步链路。批次 {index}"}), range(4)))
    run_codes = [item["runCode"] for item in runs]
    for run_code in run_codes:
        deadline = time.time() + 60
        while time.time() < deadline:
            run = core.get(f"/api/core/conversations/{conversation_id}/runs/{run_code}")
            if run["status"] in {"completed", "failed", "cancelled"}:
                break
            time.sleep(0.25)
        assert run["status"] == "completed", f"并发异步 Agent Run 未成功: {run}"
        assert run.get("taskId"), f"异步 Agent Run 未关联 Task: {run}"
        assert run.get("workerGroupCode") == "default", f"异步 Run 未固化 Worker Group: {run}"

    messages = core.get(f"/api/core/conversations/{conversation_id}/messages")
    assert any("E2E_IMAGE_OK" in item["content"] for item in messages if item["messageType"] == "assistant"), messages
    assert any("E2E_ASYNC_OK" in item["content"] for item in messages if item["messageType"] == "assistant"), messages
    executions = core.get(f"/api/core/conversations/{conversation_id}/executions")
    tasks = executions.get("tasks") or []
    assert len(tasks) == 18, f"期望 18 个 Task，实际 {len(tasks)}"
    for task in tasks:
        assert_task(task)

    query = urllib.parse.urlencode({"conversationId": conversation_id, "pageNum": 1, "pageSize": 100})
    traces = core.get(f"/api/core/traces?{query}")
    records = traces.get("records") or []
    assert records, "独立 Trace 查询未返回数据"
    decision_traces = [item for item in records if item.get("eventType") == "decision"]
    assert decision_traces, "缺少 Decision Trace"
    assert any("contextCompaction" in (item.get("metadata") or "") for item in decision_traces), "Decision Trace 缺少 Prompt/压缩观测"
    topology = core.get(f"/api/core/traces/topology?conversationId={conversation_id}")
    assert topology.get("spans"), "Trace 拓扑为空"
    capability_audits = core.get("/api/core/capability-policy/audits?pageNum=1&pageSize=20")
    assert isinstance(capability_audits.get("records"), list), capability_audits
    capability_metrics = core.get("/api/core/capability-policy/audits/metrics")
    assert all(key in capability_metrics for key in (
        "totalCount", "allowCount", "sandboxCount", "approvalCount", "blockCount",
        "approvedReplayCount", "runCount", "toolCount", "capabilityBuckets", "toolBuckets",
    )), capability_metrics
    metrics = core.get("/api/core/agent-runs/queue/metrics")
    assert metrics.get("queueCapacity", 0) > 0 and metrics.get("accepting"), f"队列指标异常: {metrics}"
    # Worker 节点属于部署级默认作用域，不应依赖当前业务租户的 JWT 作用域。
    control_plane = default_core.get("/api/core/agent-runs/control-plane")
    assert any(group["groupCode"] == "default" for group in control_plane["groups"]), control_plane
    assert any(node["groupCode"] == "default" and node["status"] == "online" for node in control_plane["nodes"]), control_plane
    usage = core.get("/api/core/cost-governance/usage?resourceType=llm&limit=500")
    assert usage and all(item["pricingStatus"] == "priced" for item in usage), f"LLM 用量未按价格落账: {usage}"
    assert all(item["agentId"] == agent_id for item in usage), f"用量缺少 Agent 归属: {usage}"
    cost_dashboard = core.get("/api/core/cost-governance/dashboard")
    assert cost_dashboard["projectUsage"]["llmTokens"] > 0, f"成本看板未聚合 Token: {cost_dashboard}"
    assert cost_dashboard["projectUsage"]["amount"] > 0, f"成本看板未聚合费用: {cost_dashboard}"
    experiment_metrics = core.get("/api/core/prompt-experiments/metrics")
    e2e_variants = [item for item in experiment_metrics if item["experiment_code"] == "E2E_PROMPT_AB"]
    assert len(e2e_variants) == 1 and e2e_variants[0]["exposures"] == 18, f"会话稳定分流或曝光归因异常: {e2e_variants}"

    circuit_payload = {
        "policyCode": "E2E_USER_CIRCUIT", "policyName": "E2E 用户熔断",
        "scopeType": "user", "scopeId": me["userId"], "periodType": "daily",
        "budgetAmount": None, "llmTokenLimit": 1, "toolCallLimit": None,
        "sandboxSecondLimit": None, "delegationCallLimit": None,
        "warningPercent": 80, "hardLimit": True, "currency": "CNY",
        "priority": 1, "status": "enabled",
    }
    circuit_policy = core.post("/api/core/cost-governance/budgets", circuit_payload)
    try:
        core.post(f"/api/core/conversations/{conversation_id}/messages", {"content": "这次调用应被预算熔断。"})
        raise AssertionError("预算硬限制未阻止 LLM 调用")
    except AssertionError as error:
        assert "预算策略" in str(error) and "熔断" in str(error), f"预算熔断错误不符合预期: {error}"
    alerts = core.get("/api/core/cost-governance/alerts?status=open")
    assert any(item["policyCode"] == "E2E_USER_CIRCUIT" and item["alertType"] == "exceeded" for item in alerts), alerts
    core.put(f"/api/core/cost-governance/budgets/{circuit_policy['id']}", {
        **circuit_payload,
        "status": "disabled",
    })

    # 切换到第二租户后，第一租户资源必须同时满足列表不可见和详情不可访问。
    other_tenant_id = registry.post("/api/registry/tenancy/tenants", {"code": "E2E_ISOLATED", "name": "E2E 隔离租户"})
    other_organization_id = registry.post("/api/registry/tenancy/organizations", {
        "tenantId": other_tenant_id, "code": "E2E_ISOLATED_ORG", "name": "E2E 隔离组织",
    })
    other_workspace_id = registry.post("/api/registry/tenancy/workspaces", {
        "tenantId": other_tenant_id, "organizationId": other_organization_id,
        "code": "E2E_ISOLATED_WS", "name": "E2E 隔离工作区",
    })
    other_project_id = registry.post("/api/registry/tenancy/projects", {
        "tenantId": other_tenant_id, "organizationId": other_organization_id,
        "workspaceId": other_workspace_id, "code": "E2E_ISOLATED_PROJECT", "name": "E2E 隔离项目",
    })
    other_membership_id = registry.post("/api/registry/tenancy/memberships", {
        "userId": me["userId"], "tenantId": other_tenant_id, "organizationId": other_organization_id,
        "workspaceId": other_workspace_id, "projectId": other_project_id,
        "membershipRole": "owner", "defaultScope": False,
    })
    isolated_scope = registry.post("/api/registry/auth/switch-scope", {"membershipId": other_membership_id})
    registry.token = isolated_scope["accessToken"]
    isolated_agents = registry.get("/api/registry/agents?pageNum=1&pageSize=100")
    assert not any(item["agentId"] == agent_id for item in isolated_agents.get("records", [])), \
        "跨租户列表泄露第一租户 Agent"
    try:
        registry.get(f"/api/registry/agents/{agent_id}")
        raise AssertionError("跨租户可以读取第一租户 Agent 详情")
    except AssertionError as error:
        assert "不存在" in str(error) or "无权" in str(error) or "403" in str(error), \
            f"跨租户详情访问没有返回明确拒绝: {error}"

    restored_scope = registry.post("/api/registry/auth/switch-scope", {"membershipId": membership_id})
    registry.token = restored_scope["accessToken"]
    assert registry.get(f"/api/registry/agents/{agent_id}")["agentId"] == agent_id

    if args.sdk_context_output:
        context = {
            "accessToken": restored_scope["accessToken"],
            "agentId": agent_id,
        }
        descriptor = os.open(args.sdk_context_output, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as output:
            json.dump(context, output, ensure_ascii=False)

    print(json.dumps({
        "status": "PASS",
        "agentId": agent_id,
        "conversationId": conversation_id,
        "taskCount": len(tasks),
        "traceCount": len(records),
        "usageCount": len(usage),
        "asyncRunCodes": run_codes,
        "crossTenantIsolation": "PASS",
        "runtimePlugin": "PASS",
        "capabilityPolicyAudit": "PASS",
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
