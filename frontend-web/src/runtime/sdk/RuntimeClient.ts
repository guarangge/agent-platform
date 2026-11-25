import { RuntimeSdkError } from "./RuntimeSdkError";
import type {
  CreateRuntimeRunOptions,
  CreateRuntimeRunRequest,
  CreateRuntimeThreadRequest,
  RuntimeApiResponse,
  RuntimeApprovalActionRequest,
  RuntimeArtifactDownload,
  RuntimeClientOptions,
  RuntimeEventCallbacks,
  RuntimeEventStreamOptions,
  RuntimeEventStreamResult,
  RuntimePageResult,
  RuntimeReplayDetail,
  RuntimeReplayPageItem,
  RuntimeReplayPageQuery,
  RuntimeRun,
  RuntimeSimulationReplayRequest,
  RuntimeSimulationReplayResult,
  RuntimeTask,
  RuntimeThread,
  RuntimeWaitSignalPayload
} from "./types";
import { streamRuntimeEvents } from "./RuntimeEventStream";

const DEFAULT_TIMEOUT_MS = 60_000;
const LONG_RUNNING_TIMEOUT_MS = 10 * 60_000;

/** 封装当前平台 Thread 与 Run Controller 细节的 TypeScript Runtime Client。 */
export class RuntimeClient {
  private readonly baseUrl: string;
  private readonly accessToken?: RuntimeClientOptions["accessToken"];
  private readonly fetcher: typeof fetch;
  private readonly requestTimeoutMs: number;
  private readonly onUnauthorized?: RuntimeClientOptions["onUnauthorized"];

  constructor(options: RuntimeClientOptions) {
    if (!options?.baseUrl?.trim()) throw new RuntimeSdkError("Runtime SDK baseUrl 不能为空", "INVALID_CONFIG");
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.accessToken = options.accessToken;
    this.fetcher = options.fetcher ?? globalThis.fetch.bind(globalThis);
    this.requestTimeoutMs = options.requestTimeoutMs ?? DEFAULT_TIMEOUT_MS;
    this.onUnauthorized = options.onUnauthorized;
  }

  /** 创建 Thread。 */
  createThread<T = RuntimeThread>(request: CreateRuntimeThreadRequest): Promise<T> {
    return this.request<T>("POST", "/conversations", request);
  }

  /** 查询 Thread。 */
  getThread<T = RuntimeThread>(threadId: number): Promise<T> {
    return this.request<T>("GET", `/conversations/${positiveId(threadId, "threadId")}`);
  }

  /** 创建异步 Run；未指定时自动生成 Idempotency-Key。 */
  createRun<T = RuntimeRun>(
    threadId: number,
    request: CreateRuntimeRunRequest,
    options: CreateRuntimeRunOptions = {}
  ): Promise<T> {
    const headers: Record<string, string> = {
      "Idempotency-Key": options.idempotencyKey?.trim() || createIdempotencyKey()
    };
    if (options.priority != null) headers["X-Agent-Run-Priority"] = String(options.priority);
    return this.request<T>(
      "POST",
      `/conversations/${positiveId(threadId, "threadId")}/messages/async`,
      request,
      headers
    );
  }

  /** 查询 Run。 */
  getRun<T = RuntimeRun>(threadId: number, runCode: string): Promise<T> {
    return this.request<T>(
      "GET",
      `/conversations/${positiveId(threadId, "threadId")}/runs/${encodeURIComponent(required(runCode, "runCode"))}`
    );
  }

  /** 请求取消 Run。 */
  cancelRun<T = RuntimeRun>(threadId: number, runCode: string): Promise<T> {
    return this.request<T>(
      "POST",
      `/conversations/${positiveId(threadId, "threadId")}/runs/${encodeURIComponent(required(runCode, "runCode"))}/cancel`
    );
  }

  /** 消费 Runtime Event；连接中断时从最后 sequenceNo 自动续传。 */
  streamEvents(
    runCode: string,
    callbacks: RuntimeEventCallbacks = {},
    options: RuntimeEventStreamOptions = {}
  ): Promise<RuntimeEventStreamResult> {
    return streamRuntimeEvents({
      baseUrl: this.baseUrl,
      accessToken: this.accessToken,
      fetcher: this.fetcher,
      onUnauthorized: this.onUnauthorized
    }, required(runCode, "runCode"), callbacks, options);
  }

  /** 从失败步骤恢复 Task。 */
  resumeTask<T = RuntimeTask>(threadId: number, taskId: number): Promise<T> {
    return this.request<T>("POST", taskPath(threadId, taskId, "/resume"), undefined, {}, LONG_RUNNING_TIMEOUT_MS);
  }

  /** 审批通过并从原 Checkpoint 继续 Task。 */
  approveTask<T = RuntimeTask>(
    threadId: number,
    taskId: number,
    approvalId: number,
    request: RuntimeApprovalActionRequest = {}
  ): Promise<T> {
    return this.submitApproval<T>(threadId, taskId, approvalId, "approve", request);
  }

  /** 审批拒绝并按请求决定是否重新规划。 */
  rejectTask<T = RuntimeTask>(
    threadId: number,
    taskId: number,
    approvalId: number,
    request: RuntimeApprovalActionRequest = {}
  ): Promise<T> {
    return this.submitApproval<T>(threadId, taskId, approvalId, "reject", request);
  }

  /** 撤回尚未处理的审批。 */
  revokeApproval<T = RuntimeTask>(
    threadId: number,
    taskId: number,
    approvalId: number,
    request: RuntimeApprovalActionRequest = {}
  ): Promise<T> {
    return this.submitApproval<T>(threadId, taskId, approvalId, "revoke", request);
  }

  /** 通过稳定事件键唤醒 waiting_event Task。 */
  signalWait(eventKey: string, payload: RuntimeWaitSignalPayload = {}): Promise<void> {
    return this.request<void>(
      "POST",
      `/conversations/waits/${encodeURIComponent(required(eventKey, "eventKey"))}/signal`,
      payload
    );
  }

  /** 下载受保护的 Task Artifact，并解析服务端 UTF-8 文件名。 */
  async downloadArtifact(threadId: number, taskId: number, path: string): Promise<RuntimeArtifactDownload> {
    const query = new URLSearchParams({ path: required(path, "path") });
    const response = await this.requestRaw(`${taskPath(threadId, taskId, "/artifacts")}?${query.toString()}`);
    const data = await response.blob();
    return {
      data,
      fileName: parseContentDispositionFileName(response.headers.get("Content-Disposition"))
        ?? path.split("/").pop()
        ?? "artifact.bin",
      contentType: response.headers.get("Content-Type") || data.type || "application/octet-stream",
      size: data.size
    };
  }

  /** 分页查询 Replay 独立审计实例。 */
  listReplays<T = RuntimeReplayPageItem>(query: RuntimeReplayPageQuery): Promise<RuntimePageResult<T>> {
    const pageNum = positiveId(query.pageNum, "pageNum");
    const pageSize = positiveId(query.pageSize, "pageSize");
    if (pageSize > 100) throw new RuntimeSdkError("pageSize 不能大于 100", "INVALID_ARGUMENT");
    const params = new URLSearchParams({ pageNum: String(pageNum), pageSize: String(pageSize) });
    appendQuery(params, "replayMode", query.replayMode);
    appendQuery(params, "status", query.status);
    appendQuery(params, "sourceRunCode", query.sourceRunCode);
    return this.request<RuntimePageResult<T>>("GET", `/runtime/replays?${params.toString()}`);
  }

  /** 查询 Replay 的 Manifest、Input 和独立结果。 */
  getReplay<T = RuntimeReplayDetail>(replayCode: string): Promise<T> {
    return this.request<T>("GET", `/runtime/replays/${encodeURIComponent(required(replayCode, "replayCode"))}`);
  }

  /** 使用冻结 Runtime Event 发起无副作用 Simulation Replay。 */
  createSimulationReplay<T = RuntimeSimulationReplayResult>(
    runCode: string,
    request: RuntimeSimulationReplayRequest = {}
  ): Promise<T> {
    validateReplayRange(request);
    return this.request<T>(
      "POST",
      `/runtime/replays/runs/${encodeURIComponent(required(runCode, "runCode"))}/simulation`,
      request,
      {},
      LONG_RUNNING_TIMEOUT_MS
    );
  }

  private submitApproval<T>(
    threadId: number,
    taskId: number,
    approvalId: number,
    action: "approve" | "reject" | "revoke",
    request: RuntimeApprovalActionRequest
  ): Promise<T> {
    const path = `${taskPath(threadId, taskId)}/approvals/${positiveId(approvalId, "approvalId")}/${action}`;
    return this.request<T>("POST", path, request, {}, LONG_RUNNING_TIMEOUT_MS);
  }

  private async request<T>(
    method: "GET" | "POST",
    path: string,
    body?: unknown,
    headers: Record<string, string> = {},
    timeoutMs: number = this.requestTimeoutMs
  ): Promise<T> {
    const controller = new AbortController();
    const timeout = globalThis.setTimeout(() => controller.abort(), timeoutMs);
    const token = this.accessToken?.();
    try {
      const response = await this.fetcher(`${this.baseUrl}${path}`, {
        method,
        headers: {
          Accept: "application/json",
          ...(body === undefined ? {} : { "Content-Type": "application/json" }),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...headers
        },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal
      });
      const payload = await parsePayload<T>(response);
      if (response.status === 401 || payload?.code === 401) this.onUnauthorized?.();
      if (!response.ok) {
        throw new RuntimeSdkError(payload?.message || `Runtime 请求失败，HTTP ${response.status}`,
          `HTTP_${response.status}`, response.status);
      }
      if (!payload || typeof payload.code !== "number") {
        throw new RuntimeSdkError("Runtime 响应格式无效", "INVALID_RESPONSE", response.status);
      }
      if (payload.code !== 200) {
        throw new RuntimeSdkError(payload.message || "Runtime 请求失败", `API_${payload.code}`, response.status);
      }
      return payload.data;
    } catch (error) {
      if (error instanceof RuntimeSdkError) throw error;
      if (controller.signal.aborted) {
        throw new RuntimeSdkError("Runtime 请求超时", "TIMEOUT", null, error);
      }
      throw new RuntimeSdkError(error instanceof Error ? error.message : "Runtime 网络请求失败",
        "NETWORK_ERROR", null, error);
    } finally {
      globalThis.clearTimeout(timeout);
    }
  }

  private async requestRaw(path: string): Promise<Response> {
    const controller = new AbortController();
    const timeout = globalThis.setTimeout(() => controller.abort(), this.requestTimeoutMs);
    const token = this.accessToken?.();
    try {
      const response = await this.fetcher(`${this.baseUrl}${path}`, {
        method: "GET",
        headers: {
          Accept: "application/octet-stream",
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        signal: controller.signal
      });
      if (response.status === 401) this.onUnauthorized?.();
      if (!response.ok) {
        const payload = await parsePayload<unknown>(response);
        throw new RuntimeSdkError(payload?.message || `Runtime 请求失败，HTTP ${response.status}`,
          `HTTP_${response.status}`, response.status);
      }
      return response;
    } catch (error) {
      if (error instanceof RuntimeSdkError) throw error;
      if (controller.signal.aborted) throw new RuntimeSdkError("Runtime 请求超时", "TIMEOUT", null, error);
      throw new RuntimeSdkError(error instanceof Error ? error.message : "Runtime 网络请求失败",
        "NETWORK_ERROR", null, error);
    } finally {
      globalThis.clearTimeout(timeout);
    }
  }
}

async function parsePayload<T>(response: Response): Promise<RuntimeApiResponse<T> | null> {
  try {
    return await response.json() as RuntimeApiResponse<T>;
  } catch {
    return null;
  }
}

function positiveId(value: number, field: string): number {
  if (!Number.isInteger(value) || value <= 0) throw new RuntimeSdkError(`${field} 必须为正整数`, "INVALID_ARGUMENT");
  return value;
}

function required(value: string, field: string): string {
  if (!value?.trim()) throw new RuntimeSdkError(`${field} 不能为空`, "INVALID_ARGUMENT");
  return value.trim();
}

function taskPath(threadId: number, taskId: number, suffix = ""): string {
  return `/conversations/${positiveId(threadId, "threadId")}/tasks/${positiveId(taskId, "taskId")}${suffix}`;
}

function appendQuery(params: URLSearchParams, key: string, value?: string): void {
  if (value?.trim()) params.set(key, value.trim());
}

function validateReplayRange(request: RuntimeSimulationReplayRequest): void {
  const from = optionalPositive(request.fromSequence, "fromSequence");
  const to = optionalPositive(request.toSequence, "toSequence");
  if (from != null && to != null && from > to) {
    throw new RuntimeSdkError("fromSequence 不能大于 toSequence", "INVALID_ARGUMENT");
  }
}

function optionalPositive(value: number | undefined, field: string): number | null {
  if (value == null) return null;
  return positiveId(value, field);
}

function parseContentDispositionFileName(contentDisposition: string | null): string | null {
  if (!contentDisposition) return null;
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1]);
    } catch {
      return utf8Match[1];
    }
  }
  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i);
  return plainMatch?.[1] ?? null;
}

function createIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
  return `runtime-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
