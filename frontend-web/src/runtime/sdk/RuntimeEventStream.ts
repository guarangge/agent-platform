import { RuntimeSdkError } from "./RuntimeSdkError";
import type {
  RuntimeAccessTokenProvider,
  RuntimeEventCallbacks,
  RuntimeEventEnvelope,
  RuntimeEventProgress,
  RuntimeEventStreamOptions,
  RuntimeEventStreamResult
} from "./types";

export interface ParsedSseFrame {
  id: number | null;
  event: string;
  data: string;
}

interface RuntimeEventTransport {
  baseUrl: string;
  accessToken?: RuntimeAccessTokenProvider;
  fetcher: typeof fetch;
  onUnauthorized?: () => void;
}

interface ConnectionResult {
  eventCount: number;
  lastSequenceNo: number;
  terminal: RuntimeEventStreamResult | null;
}

const DEFAULT_TIMEOUT_MS = 15 * 60 * 1000;
const DEFAULT_RECONNECT_DELAY_MS = 300;
const MAX_EMPTY_CONNECTIONS = 3;

/** 消费统一 Runtime Event SSE，并在非终态断连后从最后 sequenceNo 自动续传。 */
export async function streamRuntimeEvents(
  transport: RuntimeEventTransport,
  runCode: string,
  callbacks: RuntimeEventCallbacks = {},
  options: RuntimeEventStreamOptions = {}
): Promise<RuntimeEventStreamResult> {
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const reconnectDelayMs = options.reconnectDelayMs ?? DEFAULT_RECONNECT_DELAY_MS;
  const controller = new AbortController();
  let timedOut = false;
  const abortFromCaller = () => controller.abort();
  options.signal?.addEventListener("abort", abortFromCaller, { once: true });
  if (options.signal?.aborted) controller.abort();
  const timeoutId = globalThis.setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  let cursor = Math.max(0, options.afterSequence ?? 0);
  let emptyConnections = 0;

  try {
    while (true) {
      throwIfAborted(controller.signal);
      const response = await openRuntimeEventStream(transport, runCode, cursor, controller.signal);
      const connection = await consumeConnection(response, cursor, callbacks, controller.signal);
      cursor = Math.max(cursor, connection.lastSequenceNo);
      if (connection.terminal) return connection.terminal;
      emptyConnections = connection.eventCount === 0 ? emptyConnections + 1 : 0;
      if (emptyConnections >= MAX_EMPTY_CONNECTIONS) {
        throw new RuntimeSdkError("Runtime Event 连接已结束但未收到 Run 终态", "STREAM_CLOSED");
      }
      await abortableDelay(reconnectDelayMs, controller.signal);
    }
  } catch (error) {
    if (error instanceof RuntimeSdkError) throw error;
    if (timedOut) throw new RuntimeSdkError("Runtime Event 等待超时，请稍后查询 Run 状态", "STREAM_TIMEOUT", null, error);
    if (options.signal?.aborted || controller.signal.aborted) {
      throw new RuntimeSdkError("Runtime Event 消费已取消", "ABORTED", null, error);
    }
    throw new RuntimeSdkError(error instanceof Error ? error.message : "Runtime Event 网络请求失败",
      "STREAM_NETWORK_ERROR", null, error);
  } finally {
    globalThis.clearTimeout(timeoutId);
    options.signal?.removeEventListener("abort", abortFromCaller);
  }
}

async function openRuntimeEventStream(
  transport: RuntimeEventTransport,
  runCode: string,
  cursor: number,
  signal: AbortSignal
): Promise<Response> {
  const token = transport.accessToken?.();
  const headers: Record<string, string> = { Accept: "text/event-stream" };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (cursor > 0) headers["Last-Event-ID"] = String(cursor);
  const response = await transport.fetcher(
    `${transport.baseUrl}/runtime/runs/${encodeURIComponent(runCode)}/events?afterSequence=${cursor}`,
    { headers, signal }
  );
  if (response.status === 401) transport.onUnauthorized?.();
  if (!response.ok) {
    throw new RuntimeSdkError(`Runtime Event 连接失败，HTTP ${response.status}`, `HTTP_${response.status}`, response.status);
  }
  if (!response.body) throw new RuntimeSdkError("Runtime Event 响应体不可用", "INVALID_RESPONSE", response.status);
  return response;
}

async function consumeConnection(
  response: Response,
  initialSequenceNo: number,
  callbacks: RuntimeEventCallbacks,
  signal: AbortSignal
): Promise<ConnectionResult> {
  const reader = response.body!.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  let eventCount = 0;
  let lastSequenceNo = initialSequenceNo;
  let terminal: RuntimeEventStreamResult | null = null;
  try {
    while (!terminal) {
      throwIfAborted(signal);
      const { value, done } = await reader.read();
      buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, "\n");
      let boundary = buffer.indexOf("\n\n");
      while (boundary >= 0) {
        const frame = parseSseFrame(buffer.slice(0, boundary));
        buffer = buffer.slice(boundary + 2);
        if (frame?.event === "runtime.event") {
          const event = parseRuntimeEnvelope(frame.data);
          if (event && event.sequenceNo > lastSequenceNo) {
            eventCount++;
            lastSequenceNo = event.sequenceNo;
            terminal = dispatchRuntimeEvent(event, callbacks);
          }
        }
        boundary = buffer.indexOf("\n\n");
      }
      if (done) break;
    }
  } finally {
    await reader.cancel().catch(() => undefined);
  }
  return { eventCount, lastSequenceNo, terminal };
}

/** 解析单个 SSE Frame，供自定义宿主和兼容测试复用。 */
export function parseSseFrame(frame: string): ParsedSseFrame | null {
  let event = "message";
  let id: number | null = null;
  const dataLines: string[] = [];
  for (const line of frame.split("\n")) {
    if (!line || line.startsWith(":")) continue;
    if (line.startsWith("event:")) event = line.slice(6).trim();
    if (line.startsWith("id:")) {
      const parsed = Number(line.slice(3).trim());
      id = Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
    }
    if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
  }
  return dataLines.length ? { id, event, data: dataLines.join("\n") } : null;
}

/** 将 Runtime Event 投影为 UI 可直接消费的稳定回调。 */
export function dispatchRuntimeEvent(
  event: RuntimeEventEnvelope,
  callbacks: RuntimeEventCallbacks
): RuntimeEventStreamResult | null {
  callbacks.onEvent?.(event);
  if (event.eventType === "message.reset") callbacks.onReset?.();
  if (event.eventType === "message.delta") callbacks.onDelta?.(text(event.payload.content));
  if (event.eventType === "run.progress") callbacks.onProgress?.(toProgress(event));
  if (event.eventType === "approval.requested") callbacks.onApprovalRequested?.(event);
  if (event.eventType === "artifact.published") callbacks.onArtifactPublished?.(event);

  const status = resolveRunStatus(event);
  if (status) callbacks.onStatus?.(status, event.taskId ?? positiveNumber(event.payload.taskId));
  if (!status || !isTerminalStatus(status)) return null;
  return {
    status,
    taskId: event.taskId ?? positiveNumber(event.payload.taskId),
    errorMessage: nullableText(event.payload.errorMessage),
    lastSequenceNo: event.sequenceNo
  };
}

function parseRuntimeEnvelope(data: string): RuntimeEventEnvelope | null {
  try {
    const value = JSON.parse(data) as Partial<RuntimeEventEnvelope>;
    if (!value || typeof value !== "object" || !positiveNumber(value.sequenceNo) || !nullableText(value.eventType)) return null;
    return {
      eventId: text(value.eventId), sequenceNo: Number(value.sequenceNo), threadId: positiveNumber(value.threadId) ?? 0,
      turnId: nullableText(value.turnId), runId: text(value.runId), taskId: positiveNumber(value.taskId),
      itemId: nullableText(value.itemId), eventType: String(value.eventType), occurredAt: text(value.occurredAt),
      payloadVersion: positiveNumber(value.payloadVersion) ?? 1, payload: objectValue(value.payload),
      metadata: objectValue(value.metadata)
    };
  } catch {
    return null;
  }
}

function toProgress(event: RuntimeEventEnvelope): RuntimeEventProgress {
  return {
    stage: text(event.metadata.stage) || "execution", message: text(event.payload.message) || "正在执行任务",
    sequenceNo: event.sequenceNo, stepNo: positiveNumber(event.metadata.stepNo),
    stepCount: positiveNumber(event.metadata.stepCount), stepType: nullableText(event.metadata.stepType)
  };
}

function resolveRunStatus(event: RuntimeEventEnvelope): string | null {
  if (event.eventType === "run.queued") return "queued";
  if (event.eventType === "run.started") return "running";
  if (event.eventType === "run.completed") return "completed";
  if (event.eventType === "run.failed") return "failed";
  if (event.eventType === "run.cancelled") return "cancelled";
  if (event.eventType === "run.waiting") return text(event.payload.status) || "waiting_event";
  return null;
}

function isTerminalStatus(status: string): boolean {
  return ["completed", "waiting_approval", "waiting_event", "paused", "failed", "cancelled"].includes(status);
}

function objectValue(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function positiveNumber(value: unknown): number | null {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function nullableText(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value : null;
}

function text(value: unknown): string {
  return typeof value === "string" ? value : value == null ? "" : String(value);
}

function throwIfAborted(signal: AbortSignal): void {
  if (signal.aborted) throw new DOMException("Runtime Event 消费已取消", "AbortError");
}

function abortableDelay(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const onAbort = () => {
      globalThis.clearTimeout(timeoutId);
      reject(new DOMException("Runtime Event 消费已取消", "AbortError"));
    };
    const timeoutId = globalThis.setTimeout(() => {
      signal.removeEventListener("abort", onAbort);
      resolve();
    }, milliseconds);
    signal.addEventListener("abort", onAbort, { once: true });
  });
}
