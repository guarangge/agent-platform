import http from "@/api/http";

export interface ModelQueryParams {
  pageNum: number;
  pageSize: number;
  modelCode?: string;
  modelName?: string;
  provider?: string;
  modelType?: string;
  status?: string;
}

export interface ModelRecord {
  modelId: number;
  modelCode: string;
  modelName: string;
  provider: string;
  modelType: string;
  status: string;
  createdAt: string;
}

export interface ModelDetail {
  modelId: number;
  modelCode: string;
  modelName: string;
  provider: string;
  modelType: string;
  baseUrl: string | null;
  apiKeyConfigured: boolean;
  apiKeyMasked: string | null;
  apiPath: string | null;
  modelIdentifier: string | null;
  temperature: number | null;
  maxTokens: number | null;
  contextWindow: number | null;
  reservedOutputTokens: number | null;
  reservedSystemTokens: number | null;
  compactionTriggerRatio: number | null;
  tokenizerFamily: string | null;
  timeoutSeconds: number | null;
  status: string;
  metadata: Record<string, unknown> | null;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ModelPageResult {
  records: ModelRecord[];
  total: number;
  pageNum: number;
  pageSize: number;
}

export interface CreateModelPayload {
  modelCode: string;
  modelName: string;
  provider: string;
  modelType: string;
  baseUrl: string;
  apiKey: string;
  apiPath: string;
  modelIdentifier: string;
  temperature: number | null;
  maxTokens: number | null;
  contextWindow: number | null;
  reservedOutputTokens: number | null;
  reservedSystemTokens: number | null;
  compactionTriggerRatio: number | null;
  tokenizerFamily: string;
  timeoutSeconds: number | null;
  status: string;
  metadata: Record<string, unknown>;
  remark: string;
}

export interface UpdateModelPayload extends CreateModelPayload {}

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export async function fetchModelPage(params: ModelQueryParams): Promise<ModelPageResult> {
  const response = await http.get<ApiResponse<ModelPageResult>>("/models", { params });
  return response.data.data;
}

export async function fetchModelDetail(modelId: number): Promise<ModelDetail> {
  const response = await http.get<ApiResponse<ModelDetail>>(`/models/${modelId}`);
  return response.data.data;
}

export async function createModel(payload: CreateModelPayload): Promise<ModelDetail> {
  const response = await http.post<ApiResponse<ModelDetail>>("/models", payload);
  return response.data.data;
}

export async function updateModel(modelId: number, payload: UpdateModelPayload): Promise<ModelDetail> {
  const response = await http.put<ApiResponse<ModelDetail>>(`/models/${modelId}`, payload);
  return response.data.data;
}

export async function enableModel(modelId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/models/${modelId}/enable`);
}

export async function disableModel(modelId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/models/${modelId}/disable`);
}
