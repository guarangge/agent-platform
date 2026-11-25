import http from "@/api/http";
import coreHttp from "@/api/coreHttp";

export interface GuardrailPolicyQueryParams {
  pageNum: number;
  pageSize: number;
  policyCode?: string;
  policyName?: string;
  status?: string;
  actionType?: string;
}

export interface GuardrailPolicyRecord {
  policyId: number;
  policyCode: string;
  policyName: string;
  policyVersion: number;
  priority: number;
  actionType: string;
  rolloutMode: string;
  status: string;
  description: string | null;
  scopeUserCount: number;
  scopeRoleCount: number;
  scopeAgentCount: number;
  scopeToolCount: number;
  scopeWorkflowCount: number;
  scopeEnvCount: number;
  createdAt: string;
}

export interface GuardrailPolicyDetail {
  policyId: number;
  policyCode: string;
  policyName: string;
  policyVersion: number;
  priority: number;
  status: string;
  description: string | null;
  rolloutMode: string;
  rolloutPercentage: number | null;
  rolloutUserIds: number[];
  scopeUserIds: number[];
  scopeRoleCodes: string[];
  scopeAgentIds: number[];
  scopeToolIds: number[];
  scopeWorkflowIds: number[];
  scopeEnvs: string[];
  matchMode: string;
  requireNonEmptyInput: boolean;
  maxInputLength: number | null;
  blockedKeywords: string[];
  keywords: string[];
  actionType: string;
  approvalNodeName: string | null;
  approvalUserIds: number[];
  approvalRoleCodes: string[];
  reason: string | null;
  metadata: Record<string, unknown> | null;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface GuardrailPolicyPayload {
  policyCode: string;
  policyName: string;
  policyVersion: number;
  priority: number;
  status: string;
  description: string;
  rolloutMode: string;
  rolloutPercentage: number | null;
  rolloutUserIds: number[];
  scopeUserIds: number[];
  scopeRoleCodes: string[];
  scopeAgentIds: number[];
  scopeToolIds: number[];
  scopeWorkflowIds: number[];
  scopeEnvs: string[];
  matchMode: string;
  requireNonEmptyInput: boolean;
  maxInputLength: number | null;
  blockedKeywords: string[];
  keywords: string[];
  actionType: string;
  approvalNodeName: string;
  approvalUserIds: number[];
  approvalRoleCodes: string[];
  reason: string;
  metadata: Record<string, unknown>;
  remark: string;
}

export interface GuardrailPolicyDebugPayload {
  userId?: number | null;
  roleCodes?: string[];
  agentId?: number | null;
  toolId?: number | null;
  workflowId?: number | null;
  environment: string;
  inputText?: string;
}

export interface GuardrailPolicyDebugResult {
  finalDecision: string;
  primaryPolicy: Record<string, unknown> | null;
  matchedPolicies: Record<string, unknown>[];
  allEffectivePolicies: Record<string, unknown>[];
}

export interface GuardrailHitQueryParams {
  pageNum: number;
  pageSize: number;
  policyCode?: string;
  decisionType?: string;
  traceId?: string;
  taskId?: number;
  agentId?: number;
  startedFrom?: string;
  startedTo?: string;
}

export interface GuardrailHitRecord {
  hitId: number;
  conversationId: number;
  taskId: number;
  taskStepId: number | null;
  agentId: number;
  traceId: string | null;
  policyCode: string;
  policyName: string;
  policyVersion: number | null;
  decisionType: string;
  resourceKind: string;
  resourceId: number | null;
  resourceCode: string | null;
  resourceName: string | null;
  hitDetail: string | null;
  metadata: string;
  createdAt: string;
}

export interface ApprovalWorkbenchQueryParams {
  pageNum: number;
  pageSize: number;
  status?: string;
  agentId?: number;
  conversationCode?: string;
  taskCode?: string;
}

export interface ApprovalWorkbenchRecord {
  approvalId: number;
  conversationId: number;
  conversationCode: string;
  taskId: number;
  taskCode: string;
  agentId: number;
  agentName: string;
  taskStepId: number | null;
  nodeId: string | null;
  nodeName: string | null;
  status: string;
  approvalComment: string | null;
  approvedBy: number | null;
  approvedAt: string | null;
  approvalUserIds: number[];
  approvalRoleCodes: string[];
  timeoutMinutes: number | null;
  deadlineAt: string | null;
  waitingMinutes: number | null;
  approvalSourceType: string | null;
  nextPlanStepNo: number | null;
  metadata: string;
  createdAt: string;
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

export async function fetchGuardrailPolicyPage(params: GuardrailPolicyQueryParams): Promise<PageResult<GuardrailPolicyRecord>> {
  const response = await http.get<ApiResponse<PageResult<GuardrailPolicyRecord>>>("/guardrail-policies", { params });
  return response.data.data;
}

export async function fetchGuardrailPolicyDetail(policyId: number): Promise<GuardrailPolicyDetail> {
  const response = await http.get<ApiResponse<GuardrailPolicyDetail>>(`/guardrail-policies/${policyId}`);
  return response.data.data;
}

export async function createGuardrailPolicy(payload: GuardrailPolicyPayload): Promise<GuardrailPolicyDetail> {
  const response = await http.post<ApiResponse<GuardrailPolicyDetail>>("/guardrail-policies", payload);
  return response.data.data;
}

export async function updateGuardrailPolicy(policyId: number, payload: GuardrailPolicyPayload): Promise<GuardrailPolicyDetail> {
  const response = await http.put<ApiResponse<GuardrailPolicyDetail>>(`/guardrail-policies/${policyId}`, payload);
  return response.data.data;
}

export async function enableGuardrailPolicy(policyId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/guardrail-policies/${policyId}/enable`);
}

export async function disableGuardrailPolicy(policyId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/guardrail-policies/${policyId}/disable`);
}

export async function debugGuardrailPolicy(payload: GuardrailPolicyDebugPayload): Promise<GuardrailPolicyDebugResult> {
  const response = await http.post<ApiResponse<GuardrailPolicyDebugResult>>("/guardrail-policies/debug", payload);
  return response.data.data;
}

export async function fetchGuardrailHitPage(params: GuardrailHitQueryParams): Promise<PageResult<GuardrailHitRecord>> {
  const response = await coreHttp.get<ApiResponse<PageResult<GuardrailHitRecord>>>("/guardrails/hits", { params });
  return response.data.data;
}

export async function fetchApprovalWorkbenchPage(params: ApprovalWorkbenchQueryParams): Promise<PageResult<ApprovalWorkbenchRecord>> {
  const response = await coreHttp.get<ApiResponse<PageResult<ApprovalWorkbenchRecord>>>("/approvals", { params });
  return response.data.data;
}
