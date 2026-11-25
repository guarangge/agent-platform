import { runtimeClient } from "@/runtime/runtimeClient";
import type {
  RuntimePageResult,
  RuntimeReplayDetail,
  RuntimeReplayMode,
  RuntimeReplayPageItem,
  RuntimeReplayPageQuery,
  RuntimeReplayStatus,
  RuntimeSimulationReplayRequest,
  RuntimeSimulationReplayResult
} from "@/runtime/sdk";

export type ReplayMode = RuntimeReplayMode;
export type ReplayStatus = RuntimeReplayStatus;
export type ReplayPageQuery = RuntimeReplayPageQuery;
export type ReplayPageItem = RuntimeReplayPageItem;
export type ReplayDetail = RuntimeReplayDetail;
export type SimulationReplayRequest = RuntimeSimulationReplayRequest;
export type SimulationReplayResult = RuntimeSimulationReplayResult;

/** 查询 Replay 独立审计实例。 */
export function fetchReplayPage(query: ReplayPageQuery): Promise<RuntimePageResult<ReplayPageItem>> {
  return runtimeClient.listReplays(query);
}

/** 查询 Replay 的 Manifest、Input 和独立结果。 */
export function fetchReplayDetail(replayCode: string): Promise<ReplayDetail> {
  return runtimeClient.getReplay(replayCode);
}

/** 对指定 Run 发起无副作用 Simulation Replay。 */
export function createSimulationReplay(
  runCode: string,
  request: SimulationReplayRequest
): Promise<SimulationReplayResult> {
  return runtimeClient.createSimulationReplay(runCode, request);
}
