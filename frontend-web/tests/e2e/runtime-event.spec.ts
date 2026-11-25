import { expect, test } from "@playwright/test";

interface RuntimeEventFixture {
  sequenceNo: number;
  eventType: string;
  payload?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}

function envelope(fixture: RuntimeEventFixture): Record<string, unknown> {
  return {
    eventId: `EVT_${fixture.sequenceNo}`,
    sequenceNo: fixture.sequenceNo,
    threadId: 8,
    turnId: null,
    runId: "RUN_TEST",
    taskId: 11,
    itemId: null,
    eventType: fixture.eventType,
    occurredAt: "2026-08-31T10:00:00",
    payloadVersion: 1,
    payload: fixture.payload || {},
    metadata: fixture.metadata || {}
  };
}

function sse(...events: RuntimeEventFixture[]): string {
  return events.map((event) => [
    `id: ${event.sequenceNo}`,
    "event: runtime.event",
    `data: ${JSON.stringify(envelope(event))}`,
    ""
  ].join("\n")).join("\n") + "\n";
}

function legacySse(event: string, data: Record<string, unknown>): string {
  return `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
}

test("灰度模式应支持全局切换、Agent 白名单和稳定百分比", async ({ page }) => {
  await page.goto("/");

  const modes = await page.evaluate(async () => {
    const { resolveRuntimeEventProtocolMode } = await import("/src/runtime/runtimeEventMode.ts");
    return {
      unified: resolveRuntimeEventProtocolMode(1, { mode: "unified" }),
      legacy: resolveRuntimeEventProtocolMode(1, { mode: "legacy" }),
      allowlisted: resolveRuntimeEventProtocolMode(7, { mode: "auto", agentIds: "7,9" }),
      excluded: resolveRuntimeEventProtocolMode(8, { mode: "auto", agentIds: "7,9" }),
      zeroPercent: resolveRuntimeEventProtocolMode(7, { mode: "auto", rolloutPercent: 0 }),
      fullPercent: resolveRuntimeEventProtocolMode(7, { mode: "auto", rolloutPercent: 100 })
    };
  });

  expect(modes).toEqual({
    unified: "unified",
    legacy: "legacy",
    allowlisted: "auto",
    excluded: "legacy",
    zeroPercent: "legacy",
    fullPercent: "auto"
  });
});

test("auto 模式应在统一协议不可用时回退旧 Run SSE", async ({ page }) => {
  let unifiedRequests = 0;
  let legacyRequests = 0;
  await page.route("**/api/core/runtime/runs/RUN_FALLBACK/events**", (route) => {
    unifiedRequests++;
    return route.fulfill({ status: 503, body: "unavailable" });
  });
  await page.route("**/api/core/conversations/8/runs/RUN_FALLBACK/stream", (route) => {
    legacyRequests++;
    return route.fulfill({
      status: 200,
      contentType: "text/event-stream; charset=utf-8",
      body: legacySse("status", { status: "running", taskId: 11 })
        + legacySse("delta", { content: "兼容回答" })
        + legacySse("completed", { status: "completed", taskId: 11 })
    });
  });
  await page.goto("/");

  const projection = await page.evaluate(async () => {
    const { streamRunEvents } = await import("/src/runtime/runStream.ts");
    const deltas: string[] = [];
    const statuses: string[] = [];
    const result = await streamRunEvents(8, 7, "RUN_FALLBACK", {
      onDelta: (content) => deltas.push(content),
      onStatus: (status) => statuses.push(status)
    }, { reconnectDelayMs: 0, timeoutMs: 5_000 });
    return { deltas, statuses, result };
  });

  expect(unifiedRequests).toBe(1);
  expect(legacyRequests).toBe(1);
  expect(projection.deltas).toEqual(["兼容回答"]);
  expect(projection.statuses).toEqual(["running"]);
  expect(projection.result).toMatchObject({ status: "completed", taskId: 11, lastSequenceNo: 0 });
});

test("统一消费器应按 sequenceNo 去重并从断点自动续传", async ({ page }) => {
  const requests: Array<{ cursor: string | null; lastEventId: string | undefined }> = [];
  let connectionCount = 0;
  await page.route("**/api/core/runtime/runs/RUN_TEST/events**", async (route) => {
    connectionCount++;
    const request = route.request();
    requests.push({
      cursor: new URL(request.url()).searchParams.get("afterSequence"),
      lastEventId: request.headers()["last-event-id"]
    });
    const body = connectionCount === 1
      ? sse(
          { sequenceNo: 1, eventType: "message.delta", payload: { content: "第一段" } },
          { sequenceNo: 2, eventType: "run.progress", payload: { message: "正在执行" }, metadata: { stage: "execution" } }
        )
      : sse(
          { sequenceNo: 2, eventType: "message.delta", payload: { content: "重复内容" } },
          { sequenceNo: 3, eventType: "message.delta", payload: { content: "第二段" } },
          { sequenceNo: 4, eventType: "run.completed", payload: { status: "completed", taskId: 11 } }
        );
    await route.fulfill({ status: 200, contentType: "text/event-stream; charset=utf-8", body });
  });
  await page.goto("/");

  const projection = await page.evaluate(async () => {
    const { streamRuntimeEvents } = await import("/src/runtime/runtimeEventStream.ts");
    const eventSequences: number[] = [];
    const deltas: string[] = [];
    const progress: string[] = [];
    const result = await streamRuntimeEvents("RUN_TEST", {
      onEvent: (event) => eventSequences.push(event.sequenceNo),
      onDelta: (content) => deltas.push(content),
      onProgress: (item) => progress.push(item.message)
    }, { reconnectDelayMs: 0, timeoutMs: 5_000 });
    return { eventSequences, deltas, progress, result };
  });

  expect(projection.eventSequences).toEqual([1, 2, 3, 4]);
  expect(projection.deltas).toEqual(["第一段", "第二段"]);
  expect(projection.progress).toEqual(["正在执行"]);
  expect(projection.result).toMatchObject({ status: "completed", taskId: 11, lastSequenceNo: 4 });
  expect(requests).toEqual([
    { cursor: "0", lastEventId: undefined },
    { cursor: "2", lastEventId: "2" }
  ]);
});

test("统一消费器应投影审批、产物与等待终态", async ({ page }) => {
  await page.route("**/api/core/runtime/runs/RUN_TEST/events**", (route) => route.fulfill({
    status: 200,
    contentType: "text/event-stream; charset=utf-8",
    body: sse(
      { sequenceNo: 1, eventType: "approval.requested", payload: { approvalId: 55, nodeName: "高风险 Tool" } },
      { sequenceNo: 2, eventType: "artifact.published", payload: { artifactId: "ARTIFACT_1", fileName: "学习计划.pdf" } },
      { sequenceNo: 3, eventType: "run.waiting", payload: { status: "waiting_approval", taskId: 11 } }
    )
  }));
  await page.goto("/");

  const projection = await page.evaluate(async () => {
    const { streamRuntimeEvents } = await import("/src/runtime/runtimeEventStream.ts");
    const projected: string[] = [];
    const result = await streamRuntimeEvents("RUN_TEST", {
      onApprovalRequested: () => projected.push("approval"),
      onArtifactPublished: () => projected.push("artifact"),
      onStatus: (status) => projected.push(status)
    }, { reconnectDelayMs: 0, timeoutMs: 5_000 });
    return { projected, result };
  });

  expect(projection.projected).toEqual(["approval", "artifact", "waiting_approval"]);
  expect(projection.result).toMatchObject({ status: "waiting_approval", taskId: 11, lastSequenceNo: 3 });
});

test("审批恢复后应从原游标投影新的 Run 终态", async ({ page }) => {
  await page.goto("/login");
  const result = await page.evaluate(async () => {
    const module = await import("/src/runtime/runtimeEventStream.ts");
    const projected: string[] = [];
    const approvalResolved = module.dispatchRuntimeEvent({
      eventId: "EVT_13", sequenceNo: 13, threadId: 1, turnId: null, runId: "RUN_1", taskId: 11,
      itemId: "APPROVAL_1", eventType: "approval.resolved", occurredAt: "2026-09-09T00:00:00Z",
      payloadVersion: 1, payload: { status: "approved" }, metadata: {}
    }, { onEvent: (event: { eventType: string }) => projected.push(event.eventType) });
    const completed = module.dispatchRuntimeEvent({
      eventId: "EVT_24", sequenceNo: 24, threadId: 1, turnId: null, runId: "RUN_1", taskId: 11,
      itemId: null, eventType: "run.completed", occurredAt: "2026-09-09T00:00:01Z",
      payloadVersion: 1, payload: { status: "completed", taskId: 11 }, metadata: {}
    }, {
      onEvent: (event: { eventType: string }) => projected.push(event.eventType),
      onStatus: (status: string) => projected.push(status)
    });
    return { projected, approvalResolved, completed };
  });

  expect(result.approvalResolved).toBeNull();
  expect(result.projected).toEqual(["approval.resolved", "run.completed", "completed"]);
  expect(result.completed).toMatchObject({ status: "completed", taskId: 11, lastSequenceNo: 24 });
});
