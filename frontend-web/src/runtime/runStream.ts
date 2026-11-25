import { streamAgentRun } from "@/api/conversation";
import { resolveRuntimeEventProtocolMode } from "@/runtime/runtimeEventMode";
import { streamRuntimeEvents } from "@/runtime/runtimeEventStream";
import type {
  RuntimeEventCallbacks,
  RuntimeEventStreamOptions,
  RuntimeEventStreamResult
} from "@/runtime/types";

/** 根据灰度模式消费统一或旧 Run SSE，auto 模式在统一协议不可用时自动回退旧接口。 */
export async function streamRunEvents(
  conversationId: number,
  agentId: number | null | undefined,
  runCode: string,
  callbacks: RuntimeEventCallbacks = {},
  options: RuntimeEventStreamOptions = {}
): Promise<RuntimeEventStreamResult> {
  const mode = resolveRuntimeEventProtocolMode(agentId);
  if (mode === "legacy") return streamLegacyRun(conversationId, runCode, callbacks, options.signal);
  try {
    return await streamRuntimeEvents(runCode, callbacks, options);
  } catch (error) {
    if (mode === "unified" || isAbortError(error)) throw error;
    console.warn("统一 Runtime Event 连接失败，自动回退旧 Run SSE", error);
    callbacks.onReset?.();
    return streamLegacyRun(conversationId, runCode, callbacks, options.signal);
  }
}

async function streamLegacyRun(
  conversationId: number,
  runCode: string,
  callbacks: RuntimeEventCallbacks,
  signal?: AbortSignal
): Promise<RuntimeEventStreamResult> {
  const result = await streamAgentRun(conversationId, runCode, callbacks, { signal });
  return { ...result, lastSequenceNo: 0 };
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}
