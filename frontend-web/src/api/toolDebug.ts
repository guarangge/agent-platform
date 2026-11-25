import coreHttp from "@/api/coreHttp";

export interface ToolDebugPayload {
  connectivityOnly?: boolean;
  inputText?: string;
  arguments?: Record<string, unknown>;
}

export interface ToolDebugResult {
  toolCode: string;
  toolName: string;
  providerType: string;
  transportType: string;
  schemaVersion: string;
  healthStatus: string;
  success: boolean;
  message: string;
  output: Record<string, unknown>;
}

export interface ToolHealthCheckHistoryRecord {
  toolHealthCheckId: number;
  toolId: number;
  toolCode: string;
  toolName: string;
  providerType: string;
  transportType: string;
  providerCode: string;
  providerName: string;
  providerInstanceCode: string;
  providerInstanceName: string;
  environmentCode: string;
  endpoint: string | null;
  operationName: string | null;
  operationType: string | null;
  healthStatus: string;
  success: boolean;
  message: string;
  failureReason: string | null;
  statusCode: number | null;
  latencyMs: number | null;
  outputSnapshot: string;
  checkedAt: string;
}

export interface ToolHealthCheckTrendBucket {
  bucketKey: string;
  bucketLabel: string;
  totalCount: number;
  successCount: number;
  failedCount: number;
}

export interface ToolRelatedTraceRecord {
  conversationId: number;
  conversationCode: string;
  taskId: number;
  taskCode: string;
  traceId: string;
  eventType: string;
  spanName: string;
  traceStatus: string;
  errorMessage: string | null;
  startedAt: string | null;
}

export interface ToolHealthCheckMetrics {
  totalCount: number;
  successCount: number;
  failedCount: number;
  successRate: number;
  avgLatencyMs: number;
  lastCheckedAt: string | null;
  lastSuccessAt: string | null;
  latestFailureReason: string;
  consecutiveFailureCount: number;
  consecutiveFailureAlertThreshold: number;
  consecutiveFailureAlertTriggered: boolean;
  slaTargetSuccessRate: number;
  slaBreached: boolean;
  latestRecord: ToolHealthCheckHistoryRecord | null;
  trendBuckets: ToolHealthCheckTrendBucket[];
  relatedTraces: ToolRelatedTraceRecord[];
}

export interface PageResult<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

export interface ToolRuntimeGovernance {
  states: Array<Record<string, unknown>>;
  audits: Array<Record<string, unknown>>;
}

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export async function debugTool(toolId: number, payload: ToolDebugPayload): Promise<ToolDebugResult> {
  const response = await coreHttp.post<ApiResponse<ToolDebugResult>>(`/tools/${toolId}/debug`, payload);
  return response.data.data;
}

export async function healthCheckTool(toolId: number): Promise<ToolDebugResult> {
  const response = await coreHttp.get<ApiResponse<ToolDebugResult>>(`/tools/${toolId}/health`);
  return response.data.data;
}

export async function fetchToolHealthHistoryPage(toolId: number, pageNum = 1, pageSize = 10): Promise<PageResult<ToolHealthCheckHistoryRecord>> {
  const response = await coreHttp.get<ApiResponse<PageResult<ToolHealthCheckHistoryRecord>>>(`/tools/${toolId}/health-history`, {
    params: { pageNum, pageSize }
  });
  return response.data.data;
}

export async function fetchToolHealthHistoryMetrics(toolId: number): Promise<ToolHealthCheckMetrics> {
  const response = await coreHttp.get<ApiResponse<ToolHealthCheckMetrics>>(`/tools/${toolId}/health-history/metrics`);
  return response.data.data;
}

export async function fetchToolRuntimeGovernance(toolId: number): Promise<ToolRuntimeGovernance> {
  const response = await coreHttp.get<ApiResponse<ToolRuntimeGovernance>>(`/tools/${toolId}/runtime-governance`);
  return response.data.data;
}

export async function runToolCompatibility(toolId: number, payload: ToolDebugPayload): Promise<Record<string, unknown>> {
  const response = await coreHttp.post<ApiResponse<Record<string, unknown>>>(`/tools/${toolId}/compatibility-runs`, payload);
  return response.data.data;
}

export async function fetchToolCompatibilityHistory(toolId: number): Promise<Array<Record<string, unknown>>> {
  const response = await coreHttp.get<ApiResponse<Array<Record<string, unknown>>>>(`/tools/${toolId}/compatibility-runs`);
  return response.data.data;
}

export async function fetchToolCompatibilityMatrix(): Promise<Array<Record<string, unknown>>> {
  const response = await coreHttp.get<ApiResponse<Array<Record<string, unknown>>>>("/tools/compatibility-matrix");
  return response.data.data;
}
