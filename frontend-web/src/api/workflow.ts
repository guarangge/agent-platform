import http from "@/api/http";

export interface WorkflowQueryParams {
  pageNum: number;
  pageSize: number;
  workflowCode?: string;
  workflowName?: string;
  workflowType?: string;
  workflowCategory?: string;
  version?: string;
  status?: string;
}

export interface WorkflowRecord {
  workflowId: number;
  workflowCode: string;
  workflowName: string;
  workflowType: string | null;
  workflowCategory: string | null;
  version: string;
  status: string;
  description: string | null;
  createdAt: string;
}

export interface WorkflowDetail {
  workflowId: number;
  workflowCode: string;
  workflowName: string;
  workflowType: string | null;
  workflowCategory: string | null;
  description: string | null;
  version: string;
  definitionJson: Record<string, unknown> | null;
  status: string;
  metadata: Record<string, unknown> | null;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowPageResult {
  records: WorkflowRecord[];
  total: number;
  pageNum: number;
  pageSize: number;
}

export interface CreateWorkflowPayload {
  workflowCode: string;
  workflowName: string;
  workflowType: string;
  workflowCategory: string;
  description: string;
  version: string;
  definitionJson: Record<string, unknown>;
  status: string;
  metadata: Record<string, unknown>;
  remark: string;
}

export interface UpdateWorkflowPayload {
  workflowCode: string;
  workflowName: string;
  workflowType: string;
  workflowCategory: string;
  description: string;
  version: string;
  definitionJson: Record<string, unknown>;
  status: string;
  metadata: Record<string, unknown>;
  remark: string;
}

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export async function fetchWorkflowPage(params: WorkflowQueryParams): Promise<WorkflowPageResult> {
  const response = await http.get<ApiResponse<WorkflowPageResult>>("/workflows", { params });
  return response.data.data;
}

export async function fetchWorkflowDetail(workflowId: number): Promise<WorkflowDetail> {
  const response = await http.get<ApiResponse<WorkflowDetail>>(`/workflows/${workflowId}`);
  return response.data.data;
}

export async function createWorkflow(payload: CreateWorkflowPayload): Promise<WorkflowDetail> {
  const response = await http.post<ApiResponse<WorkflowDetail>>("/workflows", payload);
  return response.data.data;
}

export async function updateWorkflow(workflowId: number, payload: UpdateWorkflowPayload): Promise<WorkflowDetail> {
  const response = await http.put<ApiResponse<WorkflowDetail>>(`/workflows/${workflowId}`, payload);
  return response.data.data;
}

export async function publishWorkflow(workflowId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/workflows/${workflowId}/publish`);
}

export interface WorkflowValidationReport {
  valid: boolean;
  issues: Array<{ code: string; nodeId: string | null; message: string }>;
}

export async function validateWorkflowDefinition(definitionJson: Record<string, unknown>): Promise<WorkflowValidationReport> {
  const response = await http.post<ApiResponse<WorkflowValidationReport>>("/workflows/validate", definitionJson);
  return response.data.data;
}

export async function disableWorkflow(workflowId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/workflows/${workflowId}/disable`);
}
