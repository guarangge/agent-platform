import coreHttp from "@/api/coreHttp";
import { triggerBlobDownload } from "@/utils/download";

export interface TraceQueryParams {
  pageNum?: number;
  pageSize?: number;
  traceId?: string;
  eventType?: string;
  traceStatus?: string;
  agentId?: number;
  conversationId?: number;
  taskId?: number;
  keyword?: string;
  startedFrom?: string;
  startedTo?: string;
}

export interface TraceRecord {
  taskTraceId: number;
  conversationId: number;
  conversationCode: string;
  taskId: number;
  taskCode: string;
  agentId: number;
  agentName: string;
  traceId: string;
  taskStepId: number | null;
  stepNo: number | null;
  stepName: string | null;
  eventType: string;
  spanName: string;
  traceStatus: string;
  durationMs: number | null;
  errorMessage: string | null;
  inputSnapshot: string;
  outputSnapshot: string;
  metadata: string;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
}

export interface TraceMetricBucket {
  bucketKey: string;
  bucketLabel: string;
  countValue: number;
}

export interface TraceMetrics {
  totalCount: number;
  traceCount: number;
  failedCount: number;
  failureRate: number;
  avgDurationMs: number;
  approvalAvgWaitMs: number;
  approvalMaxWaitMs: number;
  compactionObservedCount: number;
  compactionCriticalCount: number;
  compactionAvgQualityScore: number;
  compactionAvgSavedTokens: number;
  agentBuckets: TraceMetricBucket[];
  statusBuckets: TraceMetricBucket[];
  eventTypeBuckets: TraceMetricBucket[];
  slowSpans: TraceRecord[];
}

export interface TraceTopology {
  conversationId: number;
  taskId: number;
  traceId: string;
  summary: {
    spanCount: number;
    successSpanCount: number;
    failedSpanCount: number;
    waitingApprovalSpanCount: number;
    approvalCount: number;
    pendingApprovalCount: number;
    approvedApprovalCount: number;
    rejectedApprovalCount: number;
    timedOutApprovalCount: number;
    revokedApprovalCount: number;
    delegationCount: number;
    successDelegationCount: number;
    failedDelegationCount: number;
    processingDelegationCount: number;
    totalDurationMs: number | null;
    wallClockDurationMs: number | null;
  };
  spans: Array<{
    taskTraceId: number;
    taskStepId: number | null;
    eventType: string;
    spanName: string;
    traceStatus: string;
    durationMs: number | null;
    errorMessage: string | null;
    inputSnapshot: string;
    outputSnapshot: string;
    metadata: string;
    startedAt: string | null;
    finishedAt: string | null;
  }>;
  approvals: Array<{
    approvalId: number;
    taskStepId: number | null;
    nodeId: string | null;
    nodeName: string | null;
    status: string;
    approvalComment: string | null;
    approvedBy: number | null;
    approvedAt: string | null;
    metadata: string;
    createdAt: string;
  }>;
  delegations: Array<{
    delegationId: number;
    parentTaskId: number;
    parentTaskStepId: number | null;
    childTaskId: number | null;
    delegateAgentId: number | null;
    delegateAgentCode: string | null;
    delegateAgentName: string | null;
    status: string;
    traceId: string | null;
    inputSnapshot: string;
    outputSnapshot: string;
    errorMessage: string | null;
    createdAt: string;
    updatedAt: string;
  }>;
}

export interface TraceInvestigation {
  trace: TraceRecord;
  promptSnapshot: Record<string, unknown>;
  toolInputObservation: Record<string, unknown>;
  promptReferences: Array<Record<string, unknown>>;
  toolReferences: Array<Record<string, unknown>>;
  replayable: boolean;
  replayBlockReason: string | null;
}

export interface TraceReplayRequest {
  modelCode?: string;
  systemPrompt?: string;
  userPrompt?: string;
}

export interface TraceReplayResult {
  sourceTaskTraceId: number;
  traceId: string;
  modelCode: string;
  modelIdentifier: string;
  originalOutput: string;
  replayOutput: string;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  durationMs: number | null;
  diff: Record<string, unknown>;
  replayCode: string;
  replayMode: "prompt_replay" | "simulation_replay" | "execution_replay";
  replayTraceId: string;
  status: "pending" | "running" | "succeeded" | "failed";
  manifest: Record<string, unknown>;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export async function fetchTracePage(params: TraceQueryParams): Promise<PageResult<TraceRecord>> {
  const response = await coreHttp.get<ApiResponse<PageResult<TraceRecord>>>("/traces", { params });
  return response.data.data;
}

export async function fetchTraceMetrics(params: TraceQueryParams): Promise<TraceMetrics> {
  const response = await coreHttp.get<ApiResponse<TraceMetrics>>("/traces/metrics", { params });
  return response.data.data;
}

export async function exportTraceCsv(params: TraceQueryParams): Promise<void> {
  const response = await coreHttp.get<Blob>("/traces/export", {
    params,
    responseType: "blob"
  });
  triggerBlobDownload(response, "trace-export.csv");
}

export async function fetchTraceTopology(conversationId: number, taskId: number, traceId: string): Promise<TraceTopology> {
  const response = await coreHttp.get<ApiResponse<TraceTopology>>("/traces/topology", {
    params: { conversationId, taskId, traceId }
  });
  return response.data.data;
}

export async function fetchTraceInvestigation(taskTraceId: number): Promise<TraceInvestigation> {
  const response = await coreHttp.get<ApiResponse<TraceInvestigation>>(`/traces/${taskTraceId}/investigation`);
  return response.data.data;
}

export async function replayTracePrompt(taskTraceId: number, request: TraceReplayRequest): Promise<TraceReplayResult> {
  const response = await coreHttp.post<ApiResponse<TraceReplayResult>>(`/traces/${taskTraceId}/replay`, request);
  return response.data.data;
}
