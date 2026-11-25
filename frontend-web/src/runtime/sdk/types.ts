/** Runtime SDK 可接受的 Token 提供器。 */
export type RuntimeAccessTokenProvider = () => string | null | undefined;

/** Runtime SDK 初始化配置。 */
export interface RuntimeClientOptions {
  baseUrl: string;
  accessToken?: RuntimeAccessTokenProvider;
  fetcher?: typeof fetch;
  requestTimeoutMs?: number;
  onUnauthorized?: () => void;
}

/** Runtime Protocol 对外事件信封。 */
export interface RuntimeEventEnvelope {
  eventId: string;
  sequenceNo: number;
  threadId: number;
  turnId: string | null;
  runId: string;
  taskId: number | null;
  itemId: string | null;
  eventType: string;
  occurredAt: string;
  payloadVersion: number;
  payload: Record<string, unknown>;
  metadata: Record<string, unknown>;
}

/** Runtime Event 的进度投影。 */
export interface RuntimeEventProgress {
  stage: string;
  message: string;
  sequenceNo: number;
  stepNo: number | null;
  stepCount: number | null;
  stepType: string | null;
}

/** Runtime Event 消费回调。 */
export interface RuntimeEventCallbacks {
  onEvent?: (event: RuntimeEventEnvelope) => void;
  onStatus?: (status: string, taskId: number | null) => void;
  onProgress?: (progress: RuntimeEventProgress) => void;
  onDelta?: (content: string) => void;
  onReset?: () => void;
  onReplace?: (content: string) => void;
  onApprovalRequested?: (event: RuntimeEventEnvelope) => void;
  onArtifactPublished?: (event: RuntimeEventEnvelope) => void;
}

/** Runtime Event 消费选项。 */
export interface RuntimeEventStreamOptions {
  afterSequence?: number;
  signal?: AbortSignal;
  timeoutMs?: number;
  reconnectDelayMs?: number;
}

/** Runtime Event 消费至等待态或终态后的结果。 */
export interface RuntimeEventStreamResult {
  status: string;
  taskId: number | null;
  errorMessage: string | null;
  lastSequenceNo: number;
}

/** 创建 Thread 的稳定请求。 */
export interface CreateRuntimeThreadRequest {
  agentId: number;
  title?: string | null;
  userMemoryEnabled?: boolean;
  userMemoryMode?: string;
  userMemoryWritebackEnabled?: boolean;
  preferSummaryOverRawHistory?: boolean;
  maxRecentMessages?: number;
  maxRecentUserMessages?: number;
  preferStateFirst?: boolean;
  historySummaryThresholdMessages?: number;
  minRetentionRatio?: number;
  qualityWarningThreshold?: number;
}

/** 创建 Run 的稳定请求。 */
export interface CreateRuntimeRunRequest {
  content: string;
}

/** 创建 Run 的幂等与优先级选项。 */
export interface CreateRuntimeRunOptions {
  idempotencyKey?: string;
  priority?: number;
}

/** 审批提交参数。 */
export interface RuntimeApprovalActionRequest {
  comment?: string;
  replanAfterReject?: boolean;
}

/** Runtime Task 中的审批摘要。 */
export interface RuntimeApproval {
  approvalId: number;
  taskId: number;
  status: string;
  nodeId: string;
  nodeName: string;
  approvalComment: string | null;
  [key: string]: unknown;
}

/** 审批或恢复操作返回的稳定 Task 字段。 */
export interface RuntimeTask {
  taskId: number;
  conversationId: number;
  status: string;
  errorMessage: string | null;
  approvals: RuntimeApproval[];
  [key: string]: unknown;
}

/** 唤醒持久化等待任务时携带的业务事件数据。 */
export type RuntimeWaitSignalPayload = Record<string, unknown>;

/** Artifact 下载结果，宿主自行决定保存、预览或转存。 */
export interface RuntimeArtifactDownload {
  data: Blob;
  fileName: string;
  contentType: string;
  size: number;
}

export type RuntimeReplayMode = "prompt_replay" | "simulation_replay" | "execution_replay";
export type RuntimeReplayStatus = "pending" | "running" | "succeeded" | "failed";

/** Replay 管理分页条件。 */
export interface RuntimeReplayPageQuery {
  pageNum: number;
  pageSize: number;
  replayMode?: RuntimeReplayMode;
  status?: RuntimeReplayStatus;
  sourceRunCode?: string;
}

/** SDK 通用分页响应。 */
export interface RuntimePageResult<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

/** Replay 审计列表项。 */
export interface RuntimeReplayPageItem {
  replayCode: string;
  replayTraceId: string;
  replayMode: RuntimeReplayMode;
  sourceRunCode: string | null;
  sourceTaskId: number;
  sourceTaskTraceId: number | null;
  userId: number;
  status: RuntimeReplayStatus;
  errorMessage: string | null;
  durationMs: number | null;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
}

/** Replay Manifest、输入和执行结果详情。 */
export interface RuntimeReplayDetail extends Omit<RuntimeReplayPageItem, "durationMs"> {
  manifest: Record<string, unknown>;
  inputSnapshot: Record<string, unknown>;
  resultSnapshot: Record<string, unknown>;
}

/** Simulation Replay 冻结事件范围。 */
export interface RuntimeSimulationReplayRequest {
  fromSequence?: number;
  toSequence?: number;
}

/** 无副作用 Simulation Replay 结果。 */
export interface RuntimeSimulationReplayResult {
  replayCode: string;
  replayTraceId: string;
  replayMode: RuntimeReplayMode;
  sourceRunCode: string;
  sourceTaskId: number;
  status: RuntimeReplayStatus;
  eventRange: Record<string, unknown>;
  output: Record<string, unknown>;
  injectedObservations: Array<Record<string, unknown>>;
  eventDiff: Record<string, unknown>;
  artifactVerification: Record<string, unknown>;
  manifest: Record<string, unknown>;
}

/** Thread 的最小稳定响应字段。 */
export interface RuntimeThread {
  conversationId: number;
  conversationCode: string;
  agentId: number;
  title: string | null;
  status: string;
  [key: string]: unknown;
}

/** Run 的稳定生命周期响应字段。 */
export interface RuntimeRun {
  runCode: string;
  conversationId: number;
  agentId: number;
  status: string;
  attemptCount: number;
  maxAttempts: number;
  priority: number;
  taskId: number | null;
  errorMessage: string | null;
  cancelRequested: boolean;
  [key: string]: unknown;
}

/** 平台统一响应包。 */
export interface RuntimeApiResponse<T> {
  code: number;
  message: string;
  data: T;
}
