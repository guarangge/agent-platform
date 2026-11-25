import { expect, test, type Page } from "@playwright/test";

function api(data: unknown): { status: number; contentType: string; body: string } {
  return {
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ code: 200, message: "success", data })
  };
}

async function authenticate(page: Page): Promise<void> {
  await page.addInitScript(() => localStorage.setItem("registry_access_token", "e2e-token"));
  await page.route("**/api/registry/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/auth/me")) {
      return route.fulfill(api({
        userId: 1,
        username: "operator",
        displayName: "Policy Operator",
        status: "enabled",
        roles: [{ roleId: 2, roleCode: "USER", roleName: "User" }],
        menuScopes: ["chat", "tracing"]
      }));
    }
    if (path.endsWith("/agents")) {
      return route.fulfill(api({ records: [], total: 0, pageNum: 1, pageSize: 100 }));
    }
    return route.fulfill(api({}));
  });
}

const decision = {
  eventId: 12,
  sequenceNo: 8,
  runCode: "RUN_1",
  conversationId: 3,
  conversationCode: "CONV_1",
  taskId: 4,
  taskCode: "TASK_1",
  agentId: 9,
  agentName: "通用 Agent",
  traceId: "TRACE_1",
  toolCallId: "TOOL_CALL_1234567890ABCDEF1234",
  toolCode: "ARTIFACT_PUBLISH",
  toolName: "发布产物",
  decision: "approval",
  requestedAction: "approval",
  policyCode: "CAPABILITY_PUBLISH",
  policyVersion: 2,
  runtimeCapabilities: ["artifact.publish"],
  sideEffectLevel: "irreversible",
  parameterHash: "a".repeat(64),
  targetHash: "b".repeat(64),
  approvalNodeId: "capability:node",
  approvalGranted: false,
  occurredAt: "2026-09-08T10:00:00"
};

test("运营人员可查询 Capability 决策并查看同一 Tool Call 事件链", async ({ page }) => {
  await authenticate(page);
  await page.route("**/api/core/capability-policy/audits**", (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/metrics")) {
      return route.fulfill(api({
        totalCount: 1, allowCount: 0, sandboxCount: 0, approvalCount: 1, blockCount: 0,
        approvedReplayCount: 0, runCount: 1, toolCount: 1,
        decisionBuckets: [{ key: "approval", label: "approval", count: 1 }],
        capabilityBuckets: [{ key: "artifact.publish", label: "artifact.publish", count: 1 }],
        toolBuckets: [{ key: "ARTIFACT_PUBLISH", label: "发布产物", count: 1 }]
      }));
    }
    if (path.endsWith("/12")) {
      return route.fulfill(api({
        decision,
        correlatedEvents: [
          { eventId: "EVT_12", sequenceNo: 8, eventType: "policy.decided", payloadVersion: 1,
            payload: { toolCallId: decision.toolCallId, decision: "approval" }, metadata: { traceId: "TRACE_1" },
            occurredAt: decision.occurredAt },
          { eventId: "EVT_13", sequenceNo: 9, eventType: "approval.requested", payloadVersion: 1,
            payload: { toolCallId: decision.toolCallId, status: "pending" }, metadata: { traceId: "TRACE_1" },
            occurredAt: decision.occurredAt }
        ]
      }));
    }
    return route.fulfill(api({ records: [decision], total: 1, pageNum: 1, pageSize: 20 }));
  });

  await page.goto("/capability-policy-audits");

  await expect(page.getByText("Capability Policy 审计", { exact: true })).toBeVisible();
  await expect(page.getByText("ARTIFACT_PUBLISH", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("artifact.publish", { exact: true }).first()).toBeVisible();
  await page.getByRole("button", { name: "详情" }).click();
  await expect(page.getByText("Capability 决策审计详情")).toBeVisible();
  await expect(page.getByText("policy.decided", { exact: false })).toBeVisible();
  await expect(page.getByText("approval.requested", { exact: false })).toBeVisible();
  await expect(page.getByText(decision.parameterHash, { exact: true })).toBeVisible();
});
