import coreHttp from "@/api/coreHttp";

export interface CapabilityPolicyAuditQuery {
  pageNum?: number;
  pageSize?: number;
  decision?: string;
  toolCode?: string;
  capability?: string;
  sideEffectLevel?: string;
  policyCode?: string;
  agentId?: number;
  runCode?: string;
  traceId?: string;
  occurredFrom?: string;
  occurredTo?: string;
}

export interface CapabilityPolicyAuditItem {
  eventId: number;
  sequenceNo: number;
  runCode: string;
  conversationId: number;
  conversationCode: string;
  taskId: number | null;
  taskCode: string | null;
  agentId: number;
  agentName: string;
  traceId: string | null;
  toolCallId: string;
  toolCode: string;
  toolName: string;
  decision: "allow" | "sandbox" | "approval" | "block";
  requestedAction: string | null;
  policyCode: string | null;
  policyVersion: number | null;
  runtimeCapabilities: string[];
  sideEffectLevel: string;
  parameterHash: string;
  targetHash: string | null;
  approvalNodeId: string | null;
  approvalGranted: boolean;
  occurredAt: string;
}

export interface CapabilityPolicyAuditBucket {
  key: string;
  label: string;
  count: number;
}

export interface CapabilityPolicyAuditMetrics {
  totalCount: number;
  allowCount: number;
  sandboxCount: number;
  approvalCount: number;
  blockCount: number;
  approvedReplayCount: number;
  runCount: number;
  toolCount: number;
  decisionBuckets: CapabilityPolicyAuditBucket[];
  capabilityBuckets: CapabilityPolicyAuditBucket[];
  toolBuckets: CapabilityPolicyAuditBucket[];
}

export interface CapabilityPolicyCorrelatedEvent {
  eventId: string;
  sequenceNo: number;
  eventType: string;
  payloadVersion: number;
  payload: Record<string, unknown>;
  metadata: Record<string, unknown>;
  occurredAt: string;
}

export interface CapabilityPolicyAuditDetail {
  decision: CapabilityPolicyAuditItem;
  correlatedEvents: CapabilityPolicyCorrelatedEvent[];
}

interface PageResult<T> {
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

export async function fetchCapabilityPolicyAudits(
  params: CapabilityPolicyAuditQuery
): Promise<PageResult<CapabilityPolicyAuditItem>> {
  const response = await coreHttp.get<ApiResponse<PageResult<CapabilityPolicyAuditItem>>>(
    "/capability-policy/audits",
    { params }
  );
  return response.data.data;
}

export async function fetchCapabilityPolicyAuditMetrics(
  params: CapabilityPolicyAuditQuery
): Promise<CapabilityPolicyAuditMetrics> {
  const response = await coreHttp.get<ApiResponse<CapabilityPolicyAuditMetrics>>(
    "/capability-policy/audits/metrics",
    { params }
  );
  return response.data.data;
}

export async function fetchCapabilityPolicyAuditDetail(eventId: number): Promise<CapabilityPolicyAuditDetail> {
  const response = await coreHttp.get<ApiResponse<CapabilityPolicyAuditDetail>>(
    `/capability-policy/audits/${eventId}`
  );
  return response.data.data;
}
