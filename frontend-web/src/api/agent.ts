import http from "@/api/http";

export interface AgentQueryParams {
  pageNum: number;
  pageSize: number;
  agentCode?: string;
  agentName?: string;
  status?: string;
  version?: string;
}

export interface AgentRecord {
  agentId: number;
  agentCode: string;
  agentName: string;
  description: string | null;
  version: string;
  defaultModel: string | null;
  status: string;
  createdAt: string;
}

export interface AgentDetail {
  agentId: number;
  agentCode: string;
  agentName: string;
  description: string | null;
  version: string;
  defaultModel: string | null;
  defaultPromptId: number | null;
  defaultPromptName: string | null;
  decisionPromptId: number | null;
  decisionPromptName: string | null;
  plannerPromptId: number | null;
  plannerPromptName: string | null;
  toolScopeMode: string | null;
  status: string;
  metadata: Record<string, unknown> | null;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ToolOption {
  toolId: number;
  toolCode: string;
  toolName: string;
  toolType: string | null;
  toolCategory: string | null;
  inputSchema: Record<string, unknown> | null;
  outputSchema: Record<string, unknown> | null;
  providerType: string | null;
  transportType: string | null;
  providerCode: string | null;
  providerName: string | null;
  providerInstanceCode: string | null;
  providerInstanceName: string | null;
  environmentCode: string | null;
  endpoint: string | null;
  authType: string | null;
  authConfig: Record<string, unknown> | null;
  headers: Record<string, unknown> | null;
  providerTimeoutSeconds: number | null;
  retryTimes: number | null;
  operationName: string | null;
  operationType: string | null;
  invokeConfig: Record<string, unknown> | null;
  argumentMapping: Record<string, unknown> | null;
  outputMapping: Record<string, unknown> | null;
  permissionLevel: string | null;
  timeoutSeconds: number | null;
  status: string;
  description: string | null;
}

export interface AgentToolScope {
  agentId: number;
  toolScopeMode: string;
  selectedToolIds: number[];
  tools: ToolOption[];
  availableTools: ToolOption[];
}

export interface WorkflowOption {
  workflowId: number;
  workflowCode: string;
  workflowName: string;
  workflowType: string | null;
  workflowCategory: string | null;
  status: string;
  description: string | null;
}

export interface AgentWorkflowScope {
  agentId: number;
  selectedWorkflowIds: number[];
  workflows: WorkflowOption[];
  availableWorkflows: WorkflowOption[];
}

export interface UpdateAgentToolScopePayload {
  toolScopeMode: string;
  toolIds: number[];
}

export interface AgentToolCapabilityPackScope {
  agentId: number;
  selectedPackIds: number[];
  availablePacks: Array<{
    id: number;
    pack_code: string;
    pack_name: string;
    description?: string;
    version: string;
    status: string;
    tool_count?: number;
  }>;
}

export interface UpdateAgentWorkflowScopePayload {
  workflowIds: number[];
}

export interface CreateAgentPayload {
  agentCode: string;
  agentName: string;
  description: string;
  version: string;
  defaultModel: string;
  defaultPromptId: number | null;
  decisionPromptId: number | null;
  plannerPromptId: number | null;
  toolScopeMode: string;
  status: string;
  metadata: Record<string, unknown>;
  remark: string;
}

export interface UpdateAgentPayload {
  agentCode: string;
  agentName: string;
  description: string;
  version: string;
  defaultModel: string;
  defaultPromptId: number | null;
  decisionPromptId: number | null;
  plannerPromptId: number | null;
  toolScopeMode: string;
  status: string;
  metadata: Record<string, unknown>;
  remark: string;
}

export interface AgentPageResult {
  records: AgentRecord[];
  total: number;
  pageNum: number;
  pageSize: number;
}

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export async function fetchAgentPage(params: AgentQueryParams): Promise<AgentPageResult> {
  const response = await http.get<ApiResponse<AgentPageResult>>("/agents", {
    params
  });
  return response.data.data;
}

export async function fetchAgentDetail(agentId: number): Promise<AgentDetail> {
  const response = await http.get<ApiResponse<AgentDetail>>(`/agents/${agentId}`);
  return response.data.data;
}

export async function createAgent(payload: CreateAgentPayload): Promise<AgentDetail> {
  const response = await http.post<ApiResponse<AgentDetail>>("/agents", payload);
  return response.data.data;
}

export async function updateAgent(agentId: number, payload: UpdateAgentPayload): Promise<AgentDetail> {
  const response = await http.put<ApiResponse<AgentDetail>>(`/agents/${agentId}`, payload);
  return response.data.data;
}

export async function fetchAgentToolScope(agentId: number): Promise<AgentToolScope> {
  const response = await http.get<ApiResponse<AgentToolScope>>(`/agents/${agentId}/tools`);
  return response.data.data;
}

export async function updateAgentToolScope(agentId: number, payload: UpdateAgentToolScopePayload): Promise<AgentToolScope> {
  const response = await http.put<ApiResponse<AgentToolScope>>(`/agents/${agentId}/tools`, payload);
  return response.data.data;
}

export async function fetchAgentToolCapabilityPacks(agentId: number): Promise<AgentToolCapabilityPackScope> {
  const response = await http.get<ApiResponse<AgentToolCapabilityPackScope>>(`/agents/${agentId}/tool-capability-packs`);
  return response.data.data;
}

export async function updateAgentToolCapabilityPacks(agentId: number, packIds: number[]): Promise<AgentToolCapabilityPackScope> {
  const response = await http.put<ApiResponse<AgentToolCapabilityPackScope>>(
    `/agents/${agentId}/tool-capability-packs`, { packIds }
  );
  return response.data.data;
}

export async function fetchAgentWorkflowScope(agentId: number): Promise<AgentWorkflowScope> {
  const response = await http.get<ApiResponse<AgentWorkflowScope>>(`/agents/${agentId}/workflows`);
  return response.data.data;
}

export async function updateAgentWorkflowScope(agentId: number, payload: UpdateAgentWorkflowScopePayload): Promise<AgentWorkflowScope> {
  const response = await http.put<ApiResponse<AgentWorkflowScope>>(`/agents/${agentId}/workflows`, payload);
  return response.data.data;
}

export async function enableAgent(agentId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/agents/${agentId}/enable`);
}

export async function disableAgent(agentId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/agents/${agentId}/disable`);
}
