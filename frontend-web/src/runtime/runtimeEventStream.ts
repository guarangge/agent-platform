import { runtimeClient } from "@/runtime/runtimeClient";
import type {
  RuntimeEventCallbacks,
  RuntimeEventStreamOptions,
  RuntimeEventStreamResult
} from "@/runtime/types";

export { dispatchRuntimeEvent, parseSseFrame } from "@/runtime/sdk";

/**
 * 消费统一 Runtime Event SSE。连接正常结束但 Run 未终态时，从最后 sequenceNo 自动续传。
 */
export async function streamRuntimeEvents(
  runCode: string,
  callbacks: RuntimeEventCallbacks = {},
  options: RuntimeEventStreamOptions = {}
): Promise<RuntimeEventStreamResult> {
  return runtimeClient.streamEvents(runCode, callbacks, options);
}
