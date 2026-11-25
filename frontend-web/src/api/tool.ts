import http from "@/api/http";

export interface ToolQueryParams {
  pageNum: number;
  pageSize: number;
  toolCode?: string;
  toolName?: string;
  toolType?: string;
  toolCategory?: string;
  status?: string;
}

export interface ToolRecord {
  toolId: number;
  toolCode: string;
  toolName: string;
  toolType: string | null;
  toolCategory: string | null;
  providerType: string | null;
  transportType: string | null;
  description: string | null;
  permissionLevel: string | null;
  timeoutSeconds: number | null;
  status: string;
  createdAt: string;
}

export interface ToolDetail {
  toolId: number;
  toolCode: string;
  toolName: string;
  toolType: string | null;
  toolCategory: string | null;
  runtimeCapabilities: string[];
  sideEffectLevel: string;
  description: string | null;
  inputSchema: Record<string, unknown> | null;
  outputSchema: Record<string, unknown> | null;
  providerCode: string;
  providerName: string;
  providerType: string | null;
  transportType: string | null;
  providerConfigSchema: Record<string, unknown> | null;
  providerStatus: string;
  providerInstanceCode: string;
  providerInstanceName: string;
  environmentCode: string;
  endpoint: string | null;
  authType: string;
  authConfig: Record<string, unknown> | null;
  headers: Record<string, unknown> | null;
  providerTimeoutSeconds: number;
  retryTimes: number;
  providerInstanceStatus: string;
  operationName: string;
  operationType: string;
  invokeConfig: Record<string, unknown> | null;
  argumentMapping: Record<string, unknown> | null;
  outputMapping: Record<string, unknown> | null;
  bindingStatus: string;
  permissionLevel: string | null;
  timeoutSeconds: number | null;
  status: string;
  metadata: Record<string, unknown> | null;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateToolPayload {
  toolCode: string;
  toolName: string;
  toolType: string;
  toolCategory: string;
  runtimeCapabilities: string[];
  sideEffectLevel: string;
  description: string;
  inputSchema: Record<string, unknown>;
  outputSchema: Record<string, unknown>;
  providerCode: string;
  providerName: string;
  providerType: string;
  transportType: string;
  providerConfigSchema: Record<string, unknown>;
  providerStatus: string;
  providerInstanceCode: string;
  providerInstanceName: string;
  environmentCode: string;
  endpoint: string;
  authType: string;
  authConfig: Record<string, unknown>;
  headers: Record<string, unknown>;
  providerTimeoutSeconds: number;
  retryTimes: number;
  providerInstanceStatus: string;
  operationName: string;
  operationType: string;
  invokeConfig: Record<string, unknown>;
  argumentMapping: Record<string, unknown>;
  outputMapping: Record<string, unknown>;
  bindingStatus: string;
  permissionLevel: string;
  timeoutSeconds: number;
  status: string;
  metadata: Record<string, unknown>;
  remark: string;
}

export interface UpdateToolPayload {
  toolCode: string;
  toolName: string;
  toolType: string;
  toolCategory: string;
  runtimeCapabilities: string[];
  sideEffectLevel: string;
  description: string;
  inputSchema: Record<string, unknown>;
  outputSchema: Record<string, unknown>;
  providerCode: string;
  providerName: string;
  providerType: string;
  transportType: string;
  providerConfigSchema: Record<string, unknown>;
  providerStatus: string;
  providerInstanceCode: string;
  providerInstanceName: string;
  environmentCode: string;
  endpoint: string;
  authType: string;
  authConfig: Record<string, unknown>;
  headers: Record<string, unknown>;
  providerTimeoutSeconds: number;
  retryTimes: number;
  providerInstanceStatus: string;
  operationName: string;
  operationType: string;
  invokeConfig: Record<string, unknown>;
  argumentMapping: Record<string, unknown>;
  outputMapping: Record<string, unknown>;
  bindingStatus: string;
  permissionLevel: string;
  timeoutSeconds: number;
  status: string;
  metadata: Record<string, unknown>;
  remark: string;
}

export interface ToolPageResult {
  records: ToolRecord[];
  total: number;
  pageNum: number;
  pageSize: number;
}

export interface ToolVersionRecord {
  id: number;
  version: string;
  compatibility: string;
  status: string;
  release_notes?: string;
  published_at?: string;
}

export interface ToolDeploymentRecord {
  id: number;
  version_id: number;
  version: string;
  deployment_code: string;
  deployment_name: string;
  environment_code: string;
  provider_type: string;
  transport_type: string;
  endpoint?: string;
  traffic_weight: number;
  rollout_percent: number;
  priority: number;
  failure_threshold: number;
  recovery_seconds: number;
  status: string;
}

export interface ToolLifecycleData {
  versions: ToolVersionRecord[];
  deployments: ToolDeploymentRecord[];
  migrations: Array<Record<string, unknown>>;
  audits: Array<Record<string, unknown>>;
  impact: Record<string, number>;
}

export interface ToolDeploymentPayload {
  versionId: number;
  deploymentCode: string;
  deploymentName: string;
  environmentCode: string;
  providerType: string;
  transportType: string;
  endpoint: string;
  authType: string;
  authConfig: Record<string, unknown>;
  headers: Record<string, unknown>;
  invokeConfig: Record<string, unknown>;
  trafficWeight: number;
  rolloutPercent: number;
  priority: number;
  timeoutSeconds: number;
  retryTimes: number;
  failureThreshold: number;
  recoverySeconds: number;
  status: string;
  metadata: Record<string, unknown>;
}

export interface ToolCapabilityPack {
  id: number;
  pack_code: string;
  pack_name: string;
  description?: string;
  version: string;
  status: string;
  metadata: Record<string, unknown>;
  tool_count?: number;
  agent_count?: number;
  tools?: Array<Record<string, unknown>>;
}

export interface ToolCapabilityPackPayload {
  packCode: string;
  packName: string;
  description: string;
  version: string;
  status: string;
  toolIds: number[];
  metadata: Record<string, unknown>;
}

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export async function fetchToolPage(params: ToolQueryParams): Promise<ToolPageResult> {
  const response = await http.get<ApiResponse<ToolPageResult>>("/tools", {
    params
  });
  return response.data.data;
}

export async function fetchToolDetail(toolId: number): Promise<ToolDetail> {
  const response = await http.get<ApiResponse<ToolDetail>>(`/tools/${toolId}`);
  return response.data.data;
}

export async function fetchToolRuntimeDetail(toolId: number): Promise<ToolDetail> {
  const response = await http.get<ApiResponse<ToolDetail>>(`/tools/${toolId}/runtime`);
  return response.data.data;
}

export async function createTool(payload: CreateToolPayload): Promise<ToolDetail> {
  const response = await http.post<ApiResponse<ToolDetail>>("/tools", payload);
  return response.data.data;
}

export async function updateTool(toolId: number, payload: UpdateToolPayload): Promise<ToolDetail> {
  const response = await http.put<ApiResponse<ToolDetail>>(`/tools/${toolId}`, payload);
  return response.data.data;
}

export async function enableTool(toolId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/tools/${toolId}/enable`);
}

export async function disableTool(toolId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/tools/${toolId}/disable`);
}

export async function fetchToolLifecycle(toolId: number): Promise<ToolLifecycleData> {
  const response = await http.get<ApiResponse<ToolLifecycleData>>(`/tools/${toolId}/lifecycle`);
  return response.data.data;
}

export async function createToolVersion(toolId: number, payload: Record<string, unknown>): Promise<void> {
  await http.post(`/tools/${toolId}/lifecycle/versions`, payload);
}

export async function createToolDeployment(toolId: number, payload: ToolDeploymentPayload): Promise<void> {
  await http.post(`/tools/${toolId}/lifecycle/deployments`, payload);
}

export async function updateToolDeploymentRoute(
  toolId: number,
  deploymentId: number,
  payload: Record<string, unknown>
): Promise<void> {
  await http.put(`/tools/${toolId}/lifecycle/deployments/${deploymentId}/route`, payload);
}

export async function migrateToolVersion(toolId: number, payload: Record<string, unknown>): Promise<void> {
  await http.post(`/tools/${toolId}/lifecycle/migrations`, payload);
}

export async function discoverRemoteTools(payload: Record<string, unknown>): Promise<Record<string, unknown>> {
  const response = await http.post<ApiResponse<Record<string, unknown>>>("/tools/lifecycle/discover", payload);
  return response.data.data;
}

export async function fetchToolCatalogs(): Promise<Array<Record<string, unknown>>> {
  const response = await http.get<ApiResponse<Array<Record<string, unknown>>>>("/tools/lifecycle/catalogs");
  return response.data.data;
}

export async function createToolCatalog(payload: Record<string, unknown>): Promise<void> {
  await http.post("/tools/lifecycle/catalogs", payload);
}

export async function installCatalogTool(payload: Record<string, unknown>): Promise<void> {
  await http.post("/tools/lifecycle/install", payload);
}

export async function retireToolLifecycle(toolId: number): Promise<void> {
  await http.post(`/tools/${toolId}/lifecycle/retire`);
}

export async function fetchToolCapabilityPacks(): Promise<ToolCapabilityPack[]> {
  const response = await http.get<ApiResponse<ToolCapabilityPack[]>>("/tool-capability-packs");
  return response.data.data;
}

export async function fetchToolCapabilityPack(packId: number): Promise<ToolCapabilityPack> {
  const response = await http.get<ApiResponse<ToolCapabilityPack>>(`/tool-capability-packs/${packId}`);
  return response.data.data;
}

export async function createToolCapabilityPack(payload: ToolCapabilityPackPayload): Promise<ToolCapabilityPack> {
  const response = await http.post<ApiResponse<ToolCapabilityPack>>("/tool-capability-packs", payload);
  return response.data.data;
}

export async function updateToolCapabilityPack(packId: number, payload: ToolCapabilityPackPayload): Promise<ToolCapabilityPack> {
  const response = await http.put<ApiResponse<ToolCapabilityPack>>(`/tool-capability-packs/${packId}`, payload);
  return response.data.data;
}

export async function deleteToolCapabilityPack(packId: number): Promise<void> {
  await http.delete(`/tool-capability-packs/${packId}`);
}
