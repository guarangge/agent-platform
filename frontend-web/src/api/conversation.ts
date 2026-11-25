import coreHttp from "@/api/coreHttp";
import { runtimeClient } from "@/runtime/runtimeClient";
import { triggerDownloadedBlob } from "@/utils/download";
import { getAccessToken } from "@/utils/token";

export interface ConversationQueryParams {
  pageNum: number;
  pageSize: number;
  agentId?: number;
}

export interface ConversationRecord {
  conversationId: number;
  conversationCode: string;
  agentId: number;
  agentCode: string;
  agentName: string;
  title: string | null;
  status: string;
  lastMessageAt: string | null;
  lastMessagePreview: string | null;
  createdAt: string;
}

export interface ConversationDetail {
  conversationId: number;
  conversationCode: string;
  agentId: number;
  agentCode: string;
  agentName: string;
  defaultModel: string | null;
  defaultPromptId: number | null;
  defaultPromptName: string | null;
  decisionPromptId: number | null;
  decisionPromptName: string | null;
  plannerPromptId: number | null;
  plannerPromptName: string | null;
  title: string | null;
  status: string;
  responseStreamingEnabled: boolean;
  lastMessageAt: string | null;
  lastMessagePreview: string | null;
  memorySummary: string | null;
  preferenceSummary: string | null;
  lastUserMessage: string | null;
  lastAssistantSummary: string | null;
  memoryUpdatedAt: string | null;
  userPreferenceSummary: string | null;
  userAttentionSummary: string | null;
  userFactSummary: string | null;
  userGoalSummary: string | null;
  userMemoryUpdatedAt: string | null;
  userMemoryEnabled: boolean;
  userMemoryMode: string;
  userMemoryWritebackEnabled: boolean;
  agentDefaultUserMemoryEnabled: boolean;
  agentDefaultUserMemoryMode: string;
  agentDefaultUserMemoryWritebackEnabled: boolean;
  preferSummaryOverRawHistory: boolean;
  maxRecentMessages: number;
  maxRecentUserMessages: number;
  preferStateFirst: boolean;
  historySummaryThresholdMessages: number;
  minRetentionRatio: number;
  qualityWarningThreshold: number;
  agentDefaultPreferSummaryOverRawHistory: boolean;
  agentDefaultMaxRecentMessages: number;
  agentDefaultMaxRecentUserMessages: number;
  agentDefaultPreferStateFirst: boolean;
  agentDefaultHistorySummaryThresholdMessages: number;
  agentDefaultMinRetentionRatio: number;
  agentDefaultQualityWarningThreshold: number;
  createdAt: string;
  updatedAt: string;
}

export interface MessageRecord {
  messageId: number;
  conversationId: number;
  messageType: string;
  sequenceNo: number;
  content: string;
  responseStatus: string;
  errorMessage: string | null;
  createdAt: string;
  attachments: AttachmentRecord[];
}

export interface AttachmentRecord {
  attachmentId: number;
  attachmentCode: string;
  conversationId: number;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  sha256: string;
  status: string;
  createdAt: string;
}

export interface SendMessageResult {
  conversation: ConversationDetail;
  userMessage: MessageRecord;
  assistantMessage: MessageRecord;
  task: TaskRecord;
}

export interface AgentRunResult {
  runCode: string;
  conversationId: number;
  status: "queued" | "running" | "completed" | "waiting_approval" | "waiting_event" | "paused" | "failed" | "cancelled";
  attemptCount: number;
  maxAttempts: number;
  taskId: number | null;
  errorMessage: string | null;
  cancelRequested: boolean;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface TaskStepRecord {
  taskStepId: number;
  stepNo: number;
  stepType: string;
  stepName: string;
  status: string;
  retryCount: number;
  inputSnapshot: string;
  outputSnapshot: string;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
}

export interface TaskPlanStepRecord {
  taskPlanStepId: number;
  stepId: string;
  stepNo: number;
  stepType: string;
  stepName: string;
  objective: string | null;
  dependsOnJson: string;
  targetToolCode: string | null;
  targetWorkflowId: number | null;
  inputsJson: string;
  expectedOutput: string | null;
  failurePolicy: string | null;
  replannable: boolean;
  status: string;
  description: string | null;
  createdAt: string;
}

export interface TaskPlanRecord {
  taskPlanId: number;
  taskId: number;
  planVersion: number;
  planId: string;
  planType: string;
  goal: string;
  status: string;
  assumptionsJson: string;
  constraintsJson: string;
  risksJson: string;
  plannerPromptId: number | null;
  plannerPromptName: string | null;
  source: string;
  metadataJson: string;
  createdAt: string;
  steps: TaskPlanStepRecord[];
}

export interface TaskPlanDiffSummary {
  addedCount: number;
  removedCount: number;
  changedCount: number;
}

export interface TaskPlanDiffItem {
  changeType: string;
  stepKey: string;
  beforeText: string | null;
  afterText: string | null;
}

export interface TaskPlanDiffResult {
  taskId: number;
  fromPlanVersion: number;
  toPlanVersion: number;
  summary: TaskPlanDiffSummary;
  changedSteps: TaskPlanDiffItem[];
}

export interface ApprovalRecord {
  approvalId: number;
  conversationId: number;
  taskId: number;
  taskStepId: number;
  nodeId: string;
  nodeName: string;
  status: string;
  approvalComment: string | null;
  approvedBy: number | null;
  approvedAt: string | null;
  createdAt: string;
}

export interface TaskTraceRecord {
  taskTraceId: number;
  taskId: number;
  traceId: string;
  taskStepId: number | null;
  eventType: string;
  spanName: string;
  traceStatus: string;
  inputSnapshot: string;
  outputSnapshot: string;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  createdAt: string;
}

export interface TaskRecord {
  taskId: number;
  taskCode: string;
  conversationId: number;
  messageId: number;
  agentId: number;
  parentTaskId: number | null;
  rootTaskId: number | null;
  triggerTaskStepId: number | null;
  taskKind: string | null;
  executionType: string | null;
  currentPlanVersion: number | null;
  executionChainJson: string;
  traceId: string | null;
  promptId: number | null;
  promptName: string | null;
  status: string;
  startedAt: string | null;
  finishedAt: string | null;
  errorMessage: string | null;
  memorySummary: string | null;
  keyFindings: string | null;
  finalResponseSummary: string | null;
  createdAt: string;
  approvals: ApprovalRecord[];
  plans: TaskPlanRecord[];
  traces: TaskTraceRecord[];
  steps: TaskStepRecord[];
}

export interface ConversationExecution {
  conversationId: number;
  tasks: TaskRecord[];
}

export interface DelegationTreeNode {
  taskId: number;
  taskCode: string;
  agentId: number;
  agentName: string | null;
  taskKind: string | null;
  executionType: string | null;
  traceId: string | null;
  status: string;
  parentTaskId: number | null;
  rootTaskId: number | null;
  triggerTaskStepId: number | null;
  currentPlanVersion: number | null;
  resultSummary: string | null;
  errorMessage: string | null;
  stepCount: number;
  traceCount: number;
  approvalCount: number;
  childCount: number;
  descendantCount: number;
  successDescendantCount: number;
  failedDescendantCount: number;
  waitingApprovalDescendantCount: number;
  depth: number;
  startedAt: string | null;
  finishedAt: string | null;
  children: DelegationTreeNode[];
}

export interface DelegationAuditRecord {
  delegationId: number;
  parentTaskId: number;
  parentTaskCode: string | null;
  parentTaskStepId: number;
  childTaskId: number;
  childTaskCode: string | null;
  childTaskStatus: string | null;
  delegateAgentId: number;
  delegateAgentCode: string;
  delegateAgentName: string;
  delegateInstruction: string;
  traceId: string | null;
  status: string;
  childResultSummary: string | null;
  outputSnapshot: string;
  errorMessage: string | null;
  auditChain: string;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface DelegationTreeSummary {
  totalTaskCount: number;
  delegatedTaskCount: number;
  successTaskCount: number;
  failedTaskCount: number;
  waitingApprovalTaskCount: number;
  maxDepth: number;
}

export interface DelegationTreeResult {
  conversationId: number;
  summary: DelegationTreeSummary;
  roots: DelegationTreeNode[];
  audits: DelegationAuditRecord[];
}

export interface CreateConversationPayload {
  agentId: number;
  title?: string;
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

export interface UpdateConversationTitlePayload {
  title: string;
}

export interface UpdateConversationInteractionPreferencePayload {
  responseStreamingEnabled: boolean;
}

export interface AgentRunStreamCallbacks {
  onStatus?: (status: string, taskId: number | null) => void;
  onProgress?: (progress: AgentRunProgress) => void;
  onDelta?: (content: string) => void;
  onReset?: () => void;
  onReplace?: (content: string) => void;
}

export interface AgentRunProgress {
  stage: string;
  message: string;
  sequenceNo: number;
  stepNo: number | null;
  stepCount: number | null;
  stepType: string | null;
}

export interface AgentRunStreamResult {
  status: string;
  taskId: number | null;
  errorMessage: string | null;
}

export interface UpdateConversationMemoryPolicyPayload {
  userMemoryEnabled: boolean;
  userMemoryMode: string;
  userMemoryWritebackEnabled: boolean;
  preferSummaryOverRawHistory?: boolean;
  maxRecentMessages?: number;
  maxRecentUserMessages?: number;
  preferStateFirst?: boolean;
  historySummaryThresholdMessages?: number;
  minRetentionRatio?: number;
  qualityWarningThreshold?: number;
}

export interface SendMessagePayload {
  content: string;
  attachmentIds?: number[];
}

export interface ApprovalActionPayload {
  comment?: string;
  replanAfterReject?: boolean;
}

export interface ApprovalMetrics {
  conversationId: number;
  totalCount: number;
  pendingCount: number;
  approvedCount: number;
  rejectedCount: number;
  timedOutCount: number;
  revokedCount: number;
  overduePendingCount: number;
  averageWaitMs: number;
  maxWaitMs: number;
}

export interface ManualTaskPlanStepPayload {
  stepNo: number;
  stepId: string;
  stepType: string;
  stepName: string;
  objective?: string | null;
  dependsOnStepIds?: string[];
  targetToolCode?: string | null;
  targetWorkflowId?: number | null;
  inputs?: Record<string, unknown>;
  expectedOutput?: string | null;
  failurePolicy?: string | null;
  replannable?: boolean;
  status?: string | null;
  description?: string | null;
}

export interface ManualTaskPlanContinuePayload {
  goal: string;
  planType: string;
  assumptions?: string[];
  constraints?: string[];
  risks?: string[];
  comment?: string;
  steps: ManualTaskPlanStepPayload[];
}

export interface TaskPlanControlPayload {
  action: "pause" | "resume" | "skip_step" | "single_step";
  targetStepNo?: number;
  comment?: string;
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

const LONG_RUNNING_TIMEOUT_MS = 10 * 60 * 1000;

export async function fetchConversationPage(params: ConversationQueryParams): Promise<PageResult<ConversationRecord>> {
  const response = await coreHttp.get<ApiResponse<PageResult<ConversationRecord>>>("/conversations", { params });
  return response.data.data;
}

export async function createConversation(payload: CreateConversationPayload): Promise<ConversationDetail> {
  return runtimeClient.createThread<ConversationDetail>(payload);
}

export async function fetchConversationDetail(conversationId: number): Promise<ConversationDetail> {
  return runtimeClient.getThread<ConversationDetail>(conversationId);
}

export async function updateConversationTitle(
  conversationId: number,
  payload: UpdateConversationTitlePayload
): Promise<ConversationDetail> {
  const response = await coreHttp.put<ApiResponse<ConversationDetail>>(`/conversations/${conversationId}/title`, payload);
  return response.data.data;
}

export async function updateConversationInteractionPreference(
  conversationId: number,
  payload: UpdateConversationInteractionPreferencePayload
): Promise<ConversationDetail> {
  const response = await coreHttp.put<ApiResponse<ConversationDetail>>(
    `/conversations/${conversationId}/interaction-preferences`, payload
  );
  return response.data.data;
}

export async function updateConversationMemoryPolicy(
  conversationId: number,
  payload: UpdateConversationMemoryPolicyPayload
): Promise<ConversationDetail> {
  const response = await coreHttp.post<ApiResponse<ConversationDetail>>(`/conversations/${conversationId}/memory-policy`, payload);
  return response.data.data;
}

export async function fetchConversationMessages(conversationId: number): Promise<MessageRecord[]> {
  const response = await coreHttp.get<ApiResponse<MessageRecord[]>>(`/conversations/${conversationId}/messages`);
  return response.data.data;
}

export async function uploadConversationAttachment(conversationId: number, file: File): Promise<AttachmentRecord> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await coreHttp.post<ApiResponse<AttachmentRecord>>(
    `/conversations/${conversationId}/attachments`, formData
  );
  return response.data.data;
}

export async function fetchConversationAttachmentBlob(
  conversationId: number,
  attachmentId: number
): Promise<Blob> {
  const response = await coreHttp.get<Blob>(
    `/conversations/${conversationId}/attachments/${attachmentId}/content`,
    { responseType: "blob" }
  );
  return response.data;
}

export async function deleteConversationAttachment(conversationId: number, attachmentId: number): Promise<void> {
  await coreHttp.delete(`/conversations/${conversationId}/attachments/${attachmentId}`);
}

export async function sendConversationMessage(conversationId: number, payload: SendMessagePayload): Promise<SendMessageResult> {
  const response = await coreHttp.post<ApiResponse<SendMessageResult>>(`/conversations/${conversationId}/messages`, payload, {
    timeout: LONG_RUNNING_TIMEOUT_MS
  });
  return response.data.data;
}

export async function enqueueConversationMessage(
  conversationId: number,
  payload: SendMessagePayload,
  idempotencyKey: string
): Promise<AgentRunResult> {
  return runtimeClient.createRun<AgentRunResult>(conversationId, payload, { idempotencyKey });
}

export async function fetchAgentRun(conversationId: number, runCode: string): Promise<AgentRunResult> {
  return runtimeClient.getRun<AgentRunResult>(conversationId, runCode);
}

export async function downloadTaskArtifact(downloadUrl: string): Promise<void> {
  const url = new URL(downloadUrl, window.location.origin);
  const match = url.pathname.match(/\/api\/core\/conversations\/(\d+)\/tasks\/(\d+)\/artifacts$/);
  const path = url.searchParams.get("path");
  if (!match || !path) throw new Error("Artifact 下载地址无效");
  const artifact = await runtimeClient.downloadArtifact(Number(match[1]), Number(match[2]), path);
  triggerDownloadedBlob(artifact.data, artifact.fileName);
}

export async function streamAgentRun(
  conversationId: number,
  runCode: string,
  callbacks: AgentRunStreamCallbacks = {},
  options: { signal?: AbortSignal } = {}
): Promise<AgentRunStreamResult> {
  const baseUrl = String(coreHttp.defaults.baseURL || "").replace(/\/$/, "");
  const token = getAccessToken();
  const response = await fetch(
    `${baseUrl}/conversations/${conversationId}/runs/${encodeURIComponent(runCode)}/stream`,
    { headers: token ? { Authorization: `Bearer ${token}` } : undefined, signal: options.signal }
  );
  if (!response.ok) throw new Error(`流式连接失败，HTTP ${response.status}`);
  if (!response.body) throw new Error("浏览器未提供流式响应体");

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  let result: AgentRunStreamResult | null = null;
  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, "\n");
    let boundary = buffer.indexOf("\n\n");
    while (boundary >= 0) {
      const frame = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      const event = parseAgentRunStreamFrame(frame);
      if (event) {
        if (event.event === "status") callbacks.onStatus?.(String(event.data.status || "running"), nullableNumber(event.data.taskId));
        if (event.event === "progress") callbacks.onProgress?.({
          stage: String(event.data.stage || "execution"),
          message: String(event.data.message || "正在执行任务"),
          sequenceNo: nullableNumber(event.data.sequenceNo) || 0,
          stepNo: nullableNumber(event.data.stepNo),
          stepCount: nullableNumber(event.data.stepCount),
          stepType: nullableText(event.data.stepType)
        });
        if (event.event === "delta") callbacks.onDelta?.(String(event.data.content || ""));
        if (event.event === "reset") callbacks.onReset?.();
        if (event.event === "replace") callbacks.onReplace?.(String(event.data.content || ""));
        if (event.event === "completed" || event.event === "failed") {
          result = {
            status: String(event.data.status || (event.event === "completed" ? "completed" : "failed")),
            taskId: nullableNumber(event.data.taskId),
            errorMessage: nullableText(event.data.message || event.data.errorMessage)
          };
        }
      }
      boundary = buffer.indexOf("\n\n");
    }
    if (result || done) break;
  }
  await reader.cancel();
  if (!result) throw new Error("流式连接结束但未收到完成事件");
  return result;
}

function parseAgentRunStreamFrame(frame: string): { event: string; data: Record<string, unknown> } | null {
  let event = "message";
  const dataLines: string[] = [];
  for (const line of frame.split("\n")) {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
  }
  if (!dataLines.length) return null;
  try {
    const data = JSON.parse(dataLines.join("\n"));
    return data && typeof data === "object" ? { event, data } : null;
  } catch {
    return null;
  }
}

function nullableNumber(value: unknown): number | null {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function nullableText(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value : null;
}

export async function cancelAgentRun(conversationId: number, runCode: string): Promise<AgentRunResult> {
  return runtimeClient.cancelRun<AgentRunResult>(conversationId, runCode);
}

export async function fetchConversationExecutions(conversationId: number): Promise<ConversationExecution> {
  const response = await coreHttp.get<ApiResponse<ConversationExecution>>(`/conversations/${conversationId}/executions`);
  return response.data.data;
}

export async function fetchConversationApprovalMetrics(conversationId: number): Promise<ApprovalMetrics> {
  const response = await coreHttp.get<ApiResponse<ApprovalMetrics>>(`/conversations/${conversationId}/approval-metrics`);
  return response.data.data;
}

export async function fetchDelegationTree(conversationId: number): Promise<DelegationTreeResult> {
  const response = await coreHttp.get<ApiResponse<DelegationTreeResult>>(`/conversations/${conversationId}/delegation-tree`);
  return response.data.data;
}

export async function resumeConversationTask(conversationId: number, taskId: number): Promise<TaskRecord> {
  return runtimeClient.resumeTask<TaskRecord>(conversationId, taskId);
}

export async function fetchTaskPlanDiff(
  conversationId: number,
  taskId: number,
  fromVersion: number,
  toVersion: number
): Promise<TaskPlanDiffResult> {
  const response = await coreHttp.get<ApiResponse<TaskPlanDiffResult>>(`/conversations/${conversationId}/tasks/${taskId}/plans/diff`, {
    params: {
      fromVersion,
      toVersion
    }
  });
  return response.data.data;
}

export async function continueConversationTaskWithManualPlan(
  conversationId: number,
  taskId: number,
  payload: ManualTaskPlanContinuePayload
): Promise<TaskRecord> {
  const response = await coreHttp.post<ApiResponse<TaskRecord>>(`/conversations/${conversationId}/tasks/${taskId}/plans/manual-continue`, payload, {
    timeout: LONG_RUNNING_TIMEOUT_MS
  });
  return response.data.data;
}

export async function controlConversationTaskPlan(
  conversationId: number,
  taskId: number,
  payload: TaskPlanControlPayload
): Promise<Record<string, unknown>> {
  const response = await coreHttp.post<ApiResponse<Record<string, unknown>>>(
    `/conversations/${conversationId}/tasks/${taskId}/plan-control`, payload
  );
  return response.data.data;
}

export async function previewConversationTaskPlanControl(
  conversationId: number,
  taskId: number,
  action: TaskPlanControlPayload["action"],
  targetStepNo?: number
): Promise<Record<string, unknown>> {
  const response = await coreHttp.get<ApiResponse<Record<string, unknown>>>(
    `/conversations/${conversationId}/tasks/${taskId}/plan-control/preview`,
    { params: { action, targetStepNo } }
  );
  return response.data.data;
}

export async function approveConversationTask(
  conversationId: number,
  taskId: number,
  approvalId: number,
  payload?: ApprovalActionPayload
): Promise<TaskRecord> {
  return runtimeClient.approveTask<TaskRecord>(conversationId, taskId, approvalId, payload);
}

export async function rejectConversationTask(
  conversationId: number,
  taskId: number,
  approvalId: number,
  payload?: ApprovalActionPayload
): Promise<TaskRecord> {
  return runtimeClient.rejectTask<TaskRecord>(conversationId, taskId, approvalId, payload);
}

export async function revokeConversationApproval(
  conversationId: number,
  taskId: number,
  approvalId: number,
  payload?: ApprovalActionPayload
): Promise<TaskRecord> {
  return runtimeClient.revokeApproval<TaskRecord>(conversationId, taskId, approvalId, payload);
}
