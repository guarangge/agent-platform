import { expect, test, type Page, type Route } from "@playwright/test";

interface ReplayFixture {
  replayCode: string;
  replayTraceId: string;
  replayMode: "prompt_replay" | "simulation_replay" | "execution_replay";
  sourceRunCode: string | null;
  sourceTaskId: number;
  sourceTaskTraceId: number | null;
  userId: number;
  status: "pending" | "running" | "succeeded" | "failed";
  errorMessage: string | null;
  durationMs: number | null;
  startedAt: string;
  finishedAt: string;
  createdAt: string;
}

const replay: ReplayFixture = {
  replayCode: "REPLAY_1",
  replayTraceId: "REPLAY_TRACE_1",
  replayMode: "simulation_replay",
  sourceRunCode: "RUN_SOURCE",
  sourceTaskId: 20,
  sourceTaskTraceId: null,
  userId: 1,
  status: "succeeded",
  errorMessage: null,
  durationMs: 1200,
  startedAt: "2026-08-31T10:00:00",
  finishedAt: "2026-08-31T10:00:01.2",
  createdAt: "2026-08-31T10:00:00"
};

function api(data: unknown): { status: number; contentType: string; body: string } {
  return {
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ code: 200, message: "success", data })
  };
}

async function authenticate(page: Page, roleCode = "ADMIN"): Promise<void> {
  await page.addInitScript(() => localStorage.setItem("registry_access_token", "e2e-token"));
  await page.route("**/api/registry/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/auth/me")) {
      return route.fulfill(api({
        userId: 1,
        username: "admin",
        displayName: "Replay Admin",
        status: "enabled",
        roles: [{ roleId: 1, roleCode, roleName: roleCode }],
        menuScopes: ["chat", "tracing"]
      }));
    }
    return route.fulfill(api({ records: [], total: 0, pageNum: 1, pageSize: 20 }));
  });
}

function detail(fixture: ReplayFixture): Record<string, unknown> {
  return {
    ...fixture,
    manifest: {
      manifestVersion: 1,
      replayMode: fixture.replayMode,
      policy: { providerInvocationAllowed: false, sandboxAllowed: false }
    },
    inputSnapshot: {
      sourceRunCode: fixture.sourceRunCode,
      eventRange: { fromSequence: 1, toSequence: 8 }
    },
    resultSnapshot: {
      eventDiff: {
        consistent: true,
        summary: { matchedCount: 4, changedCount: 0, missingCount: 0, unexpectedCount: 0 }
      },
      artifactVerification: {
        consistent: true,
        summary: { artifactCount: 1, verifiedCount: 1, mismatchCount: 0 }
      }
    }
  };
}

test("管理员应查看 Replay 列表、Event Diff 和 Artifact 校验", async ({ page }) => {
  await authenticate(page);
  await page.route("**/api/core/runtime/replays**", (route) => {
    const path = new URL(route.request().url()).pathname;
    if (route.request().method() === "GET" && path.endsWith("/runtime/replays/REPLAY_1")) {
      return route.fulfill(api(detail(replay)));
    }
    return route.fulfill(api({ records: [replay], total: 1, pageNum: 1, pageSize: 20 }));
  });

  await page.goto("/replays");

  await expect(page.getByRole("heading", { name: "Replay 管理" })).toBeVisible();
  await expect(page.getByText("REPLAY_1", { exact: true })).toBeVisible();
  await expect(page.getByText("RUN_SOURCE", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "详情" }).click();
  await expect(page.getByText("Replay 审计详情")).toBeVisible();
  await expect(page.locator(".result-grid pre").filter({ hasText: '"consistent": true' })).toHaveCount(2);
  await expect(page.locator(".result-grid pre").filter({ hasText: '"verifiedCount": 1' })).toBeVisible();
});

test("管理员应从页面按冻结事件范围发起 Simulation Replay", async ({ page }) => {
  await authenticate(page, "SUPER_ADMIN");
  let records: ReplayFixture[] = [];
  let submittedBody: unknown;
  await page.route("**/api/core/runtime/replays**", async (route: Route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (request.method() === "POST" && path.endsWith("/runtime/replays/runs/RUN_SOURCE/simulation")) {
      submittedBody = request.postDataJSON();
      records = [replay];
      return route.fulfill(api({
        ...replay,
        eventRange: { fromSequence: 2, toSequence: 8 },
        output: { assembledMessage: "录制结果" },
        injectedObservations: [],
        eventDiff: { consistent: true },
        artifactVerification: { consistent: true },
        manifest: { manifestVersion: 1 }
      }));
    }
    if (request.method() === "GET" && path.endsWith("/runtime/replays/REPLAY_1")) {
      return route.fulfill(api(detail(replay)));
    }
    return route.fulfill(api({ records, total: records.length, pageNum: 1, pageSize: 20 }));
  });

  await page.goto("/replays");
  await page.getByRole("button", { name: "发起 Simulation Replay" }).click();
  const dialog = page.getByRole("dialog", { name: "发起 Simulation Replay" });
  await dialog.getByLabel("来源 Run Code").fill("RUN_SOURCE");
  await dialog.locator(".el-input-number input").nth(0).fill("2");
  await dialog.locator(".el-input-number input").nth(1).fill("8");
  await dialog.getByRole("button", { name: "确认执行" }).click();

  await expect(page.getByText("Simulation Replay 已完成：REPLAY_1")).toBeVisible();
  await expect(page.getByText("Replay 审计详情")).toBeVisible();
  expect(submittedBody).toEqual({ fromSequence: 2, toSequence: 8 });
});

test("普通用户访问 Replay 管理页应被前端权限阻断", async ({ page }) => {
  await authenticate(page, "USER");
  let replayRequestCount = 0;
  await page.route("**/api/core/**", (route) => {
    if (new URL(route.request().url()).pathname.startsWith("/api/core/runtime/replays")) {
      replayRequestCount++;
    }
    return route.fulfill(api({ records: [], total: 0, pageNum: 1, pageSize: 20 }));
  });

  await page.goto("/replays");

  await expect(page).toHaveURL(/\/chat$/);
  await expect(page.getByText("当前账号无权访问该页面")).toBeVisible();
  expect(replayRequestCount).toBe(0);
});
