import { expect, test } from "@playwright/test";

test("Runtime Client 应统一 Thread/Run 路径、JWT 和幂等键", async ({ page }) => {
  await page.goto("/login");
  const result = await page.evaluate(async () => {
    const sdk = await import("/src/runtime/sdk/index.ts");
    const calls: Array<{ url: string; method: string; headers: Record<string, string>; body: string | null }> = [];
    const fetcher = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
      const url = String(input);
      const headers = Object.fromEntries(new Headers(init?.headers).entries());
      calls.push({ url, method: init?.method || "GET", headers, body: String(init?.body || "") || null });
      const data = url.endsWith("/messages/async")
        ? { runCode: "RUN_1", conversationId: 9, agentId: 7, status: "queued", attemptCount: 0,
            maxAttempts: 2, priority: 50, taskId: null, errorMessage: null, cancelRequested: false }
        : { conversationId: 9, conversationCode: "CONV_9", agentId: 7, title: "SDK", status: "active" };
      return new Response(JSON.stringify({ code: 200, message: "success", data }), {
        status: 200, headers: { "Content-Type": "application/json" }
      });
    };
    const client = new sdk.RuntimeClient({ baseUrl: "/api/core/", accessToken: () => "TOKEN_1", fetcher });
    await client.createThread({ agentId: 7, title: "SDK" });
    await client.getThread(9);
    await client.createRun(9, { content: "hello" });
    await client.getRun(9, "RUN/1");
    await client.cancelRun(9, "RUN/1");
    return calls;
  });

  expect(result.map((item) => `${item.method} ${item.url}`)).toEqual([
    "POST /api/core/conversations",
    "GET /api/core/conversations/9",
    "POST /api/core/conversations/9/messages/async",
    "GET /api/core/conversations/9/runs/RUN%2F1",
    "POST /api/core/conversations/9/runs/RUN%2F1/cancel"
  ]);
  expect(result.every((item) => item.headers.authorization === "Bearer TOKEN_1")).toBeTruthy();
  expect(result[2].headers["idempotency-key"]).toBeTruthy();
  expect(JSON.parse(result[2].body || "{}")).toEqual({ content: "hello" });
});

test("Runtime Client 应统一业务错误、401 和超时错误", async ({ page }) => {
  await page.goto("/login");
  const result = await page.evaluate(async () => {
    const sdk = await import("/src/runtime/sdk/index.ts");
    let unauthorized = 0;
    const businessClient = new sdk.RuntimeClient({
      baseUrl: "/api/core",
      onUnauthorized: () => unauthorized++,
      fetcher: async () => new Response(JSON.stringify({ code: 401, message: "Token 已失效", data: null }), {
        status: 401, headers: { "Content-Type": "application/json" }
      })
    });
    let businessError: Record<string, unknown> = {};
    try {
      await businessClient.getThread(9);
    } catch (error) {
      const sdkError = error as InstanceType<typeof sdk.RuntimeSdkError>;
      businessError = { name: sdkError.name, code: sdkError.code, status: sdkError.httpStatus };
    }

    const timeoutClient = new sdk.RuntimeClient({
      baseUrl: "/api/core",
      requestTimeoutMs: 5,
      fetcher: (_input, init) => new Promise((_resolve, reject) => {
        init?.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
      })
    });
    let timeoutCode = "";
    try {
      await timeoutClient.getThread(9);
    } catch (error) {
      timeoutCode = (error as InstanceType<typeof sdk.RuntimeSdkError>).code;
    }

    const errorCode = async (responseFactory: () => Promise<Response>): Promise<string> => {
      const client = new sdk.RuntimeClient({ baseUrl: "/api/core", fetcher: responseFactory });
      try {
        await client.getThread(9);
        return "";
      } catch (error) {
        return (error as InstanceType<typeof sdk.RuntimeSdkError>).code;
      }
    };
    const apiCode = await errorCode(async () => new Response(
      JSON.stringify({ code: 409, message: "重复请求", data: null }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    ));
    const invalidResponseCode = await errorCode(async () => new Response("not-json", { status: 200 }));
    const networkCode = await errorCode(async () => { throw new TypeError("network down"); });
    return { unauthorized, businessError, timeoutCode, apiCode, invalidResponseCode, networkCode };
  });

  expect(result.unauthorized).toBe(1);
  expect(result.businessError).toEqual({ name: "RuntimeSdkError", code: "HTTP_401", status: 401 });
  expect(result.timeoutCode).toBe("TIMEOUT");
  expect(result.apiCode).toBe("API_409");
  expect(result.invalidResponseCode).toBe("INVALID_RESPONSE");
  expect(result.networkCode).toBe("NETWORK_ERROR");
});

test("Runtime Client 应直接消费事件、断点续传并映射流式错误", async ({ page }) => {
  await page.goto("/login");
  const result = await page.evaluate(async () => {
    const sdk = await import("/src/runtime/sdk/index.ts");
    const event = (sequenceNo: number, eventType: string, payload: Record<string, unknown>) => ({
      eventId: `EVT_${sequenceNo}`, sequenceNo, threadId: 9, turnId: null, runId: "RUN/SDK", taskId: 12,
      itemId: null, eventType, occurredAt: "2026-09-10T00:00:00Z", payloadVersion: 1, payload, metadata: {}
    });
    const sse = (...events: Record<string, unknown>[]) => events.map((item) =>
      `id: ${item.sequenceNo}\nevent: runtime.event\ndata: ${JSON.stringify(item)}\n\n`).join("");
    const calls: Array<{ url: string; authorization: string | null; lastEventId: string | null }> = [];
    let connectionCount = 0;
    const client = new sdk.RuntimeClient({
      baseUrl: "/api/core/", accessToken: () => "TOKEN_STREAM",
      fetcher: async (input, init) => {
        connectionCount++;
        const headers = new Headers(init?.headers);
        calls.push({
          url: String(input), authorization: headers.get("Authorization"), lastEventId: headers.get("Last-Event-ID")
        });
        const body = connectionCount === 1
          ? sse(event(6, "message.delta", { content: "第一段" }))
          : sse(event(6, "message.delta", { content: "重复" }), event(7, "run.completed", { status: "completed" }));
        return new Response(body, { status: 200, headers: { "Content-Type": "text/event-stream" } });
      }
    });
    const deltas: string[] = [];
    const streamResult = await client.streamEvents("RUN/SDK", { onDelta: (content) => deltas.push(content) }, {
      afterSequence: 5, reconnectDelayMs: 0, timeoutMs: 5_000
    });

    const controller = new AbortController();
    const abortedClient = new sdk.RuntimeClient({
      baseUrl: "/api/core",
      fetcher: (_input, init) => new Promise((_resolve, reject) => {
        init?.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
      })
    });
    const abortedPromise = abortedClient.streamEvents("RUN_ABORT", {}, { signal: controller.signal });
    controller.abort();
    let abortedCode = "";
    try { await abortedPromise; } catch (error) {
      abortedCode = (error as InstanceType<typeof sdk.RuntimeSdkError>).code;
    }

    let unauthorized = 0;
    const unauthorizedClient = new sdk.RuntimeClient({
      baseUrl: "/api/core", onUnauthorized: () => unauthorized++,
      fetcher: async () => new Response("unauthorized", { status: 401 })
    });
    let unauthorizedCode = "";
    try { await unauthorizedClient.streamEvents("RUN_401"); } catch (error) {
      unauthorizedCode = (error as InstanceType<typeof sdk.RuntimeSdkError>).code;
    }
    return { calls, deltas, streamResult, abortedCode, unauthorized, unauthorizedCode };
  });

  expect(result.calls).toEqual([
    { url: "/api/core/runtime/runs/RUN%2FSDK/events?afterSequence=5", authorization: "Bearer TOKEN_STREAM", lastEventId: "5" },
    { url: "/api/core/runtime/runs/RUN%2FSDK/events?afterSequence=6", authorization: "Bearer TOKEN_STREAM", lastEventId: "6" }
  ]);
  expect(result.deltas).toEqual(["第一段"]);
  expect(result.streamResult).toMatchObject({ status: "completed", taskId: 12, lastSequenceNo: 7 });
  expect(result.abortedCode).toBe("ABORTED");
  expect(result.unauthorized).toBe(1);
  expect(result.unauthorizedCode).toBe("HTTP_401");
});

test("Runtime Client 应统一审批、失败恢复和等待唤醒路径", async ({ page }) => {
  await page.goto("/login");
  const result = await page.evaluate(async () => {
    const sdk = await import("/src/runtime/sdk/index.ts");
    const calls: Array<{ url: string; method: string; body: unknown }> = [];
    const client = new sdk.RuntimeClient({
      baseUrl: "/api/core",
      accessToken: () => "TOKEN_RECOVERY",
      fetcher: async (input, init) => {
        calls.push({
          url: String(input),
          method: init?.method || "GET",
          body: init?.body ? JSON.parse(String(init.body)) : null
        });
        const data = String(input).includes("/waits/")
          ? null
          : { taskId: 12, conversationId: 9, status: "completed", errorMessage: null, approvals: [] };
        return new Response(JSON.stringify({ code: 200, message: "success", data }), {
          status: 200, headers: { "Content-Type": "application/json" }
        });
      }
    });
    await client.resumeTask(9, 12);
    await client.approveTask(9, 12, 21, { comment: "确认执行" });
    await client.rejectTask(9, 12, 22, { comment: "参数不正确", replanAfterReject: true });
    await client.revokeApproval(9, 12, 23, { comment: "撤回审批" });
    await client.signalWait("order/paid", { orderId: 1001 });
    let invalidCode = "";
    try { await client.approveTask(9, 12, 0); } catch (error) {
      invalidCode = (error as InstanceType<typeof sdk.RuntimeSdkError>).code;
    }
    return { calls, invalidCode };
  });

  expect(result.calls).toEqual([
    { url: "/api/core/conversations/9/tasks/12/resume", method: "POST", body: null },
    { url: "/api/core/conversations/9/tasks/12/approvals/21/approve", method: "POST", body: { comment: "确认执行" } },
    { url: "/api/core/conversations/9/tasks/12/approvals/22/reject", method: "POST", body: { comment: "参数不正确", replanAfterReject: true } },
    { url: "/api/core/conversations/9/tasks/12/approvals/23/revoke", method: "POST", body: { comment: "撤回审批" } },
    { url: "/api/core/conversations/waits/order%2Fpaid/signal", method: "POST", body: { orderId: 1001 } }
  ]);
  expect(result.invalidCode).toBe("INVALID_ARGUMENT");
});

test("Runtime Client 应统一 Artifact 下载与 Replay 生命周期", async ({ page }) => {
  await page.goto("/login");
  const result = await page.evaluate(async () => {
    const sdk = await import("/src/runtime/sdk/index.ts");
    const calls: Array<{ url: string; method: string; accept: string | null; body: unknown }> = [];
    const client = new sdk.RuntimeClient({
      baseUrl: "/api/core",
      accessToken: () => "TOKEN_ARTIFACT",
      fetcher: async (input, init) => {
        const url = String(input);
        calls.push({
          url,
          method: init?.method || "GET",
          accept: new Headers(init?.headers).get("Accept"),
          body: init?.body ? JSON.parse(String(init.body)) : null
        });
        if (url.includes("/artifacts?")) {
          return new Response(new Blob(["pdf-content"], { type: "application/pdf" }), {
            status: 200,
            headers: {
              "Content-Type": "application/pdf",
              "Content-Disposition": "attachment; filename*=UTF-8''%E5%AD%A6%E4%B9%A0%E8%AE%A1%E5%88%92.pdf"
            }
          });
        }
        const data = url.includes("/simulation")
          ? { replayCode: "REPLAY_1", replayTraceId: "TRACE_1", replayMode: "simulation_replay",
              sourceRunCode: "RUN/1", sourceTaskId: 12, status: "succeeded", eventRange: {}, output: {},
              injectedObservations: [], eventDiff: {}, artifactVerification: {}, manifest: {} }
          : url.endsWith("/REPLAY%2F1")
            ? { replayCode: "REPLAY/1", manifest: {}, inputSnapshot: {}, resultSnapshot: {} }
            : { records: [], total: 0, pageNum: 2, pageSize: 20 };
        return new Response(JSON.stringify({ code: 200, message: "success", data }), {
          status: 200, headers: { "Content-Type": "application/json" }
        });
      }
    });
    const artifact = await client.downloadArtifact(9, 12, "reports/学习计划 (1).pdf");
    await client.listReplays({
      pageNum: 2, pageSize: 20, replayMode: "simulation_replay", status: "succeeded", sourceRunCode: "RUN/1"
    });
    await client.getReplay("REPLAY/1");
    await client.createSimulationReplay("RUN/1", { fromSequence: 2, toSequence: 8 });
    const invalidCodes: string[] = [];
    for (const action of [
      () => client.listReplays({ pageNum: 1, pageSize: 101 }),
      () => client.createSimulationReplay("RUN_1", { fromSequence: 9, toSequence: 8 })
    ]) {
      try { await action(); } catch (error) {
        invalidCodes.push((error as InstanceType<typeof sdk.RuntimeSdkError>).code);
      }
    }
    return {
      calls,
      artifact: {
        text: await artifact.data.text(), fileName: artifact.fileName,
        contentType: artifact.contentType, size: artifact.size
      },
      invalidCodes
    };
  });

  expect(result.calls).toEqual([
    {
      url: "/api/core/conversations/9/tasks/12/artifacts?path=reports%2F%E5%AD%A6%E4%B9%A0%E8%AE%A1%E5%88%92+%281%29.pdf",
      method: "GET", accept: "application/octet-stream", body: null
    },
    {
      url: "/api/core/runtime/replays?pageNum=2&pageSize=20&replayMode=simulation_replay&status=succeeded&sourceRunCode=RUN%2F1",
      method: "GET", accept: "application/json", body: null
    },
    { url: "/api/core/runtime/replays/REPLAY%2F1", method: "GET", accept: "application/json", body: null },
    {
      url: "/api/core/runtime/replays/runs/RUN%2F1/simulation", method: "POST", accept: "application/json",
      body: { fromSequence: 2, toSequence: 8 }
    }
  ]);
  expect(result.artifact).toEqual({
    text: "pdf-content", fileName: "学习计划.pdf", contentType: "application/pdf", size: 11
  });
  expect(result.invalidCodes).toEqual(["INVALID_ARGUMENT", "INVALID_ARGUMENT"]);
});
