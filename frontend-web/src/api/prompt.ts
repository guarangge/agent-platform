import http from "@/api/http";

export interface PromptQueryParams {
  pageNum: number;
  pageSize: number;
  promptCode?: string;
  promptName?: string;
  promptType?: string;
  promptCategory?: string;
  status?: string;
}

export interface PromptRecord {
  promptId: number;
  promptCode: string;
  promptName: string;
  promptType: string;
  promptCategory: string;
  version: string;
  status: string;
  description: string | null;
  createdAt: string;
}

export interface PromptDetail {
  promptId: number;
  promptCode: string;
  promptName: string;
  promptType: string;
  promptCategory: string;
  description: string | null;
  content: string;
  version: string;
  status: string;
  metadata: Record<string, unknown> | null;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PromptPageResult {
  records: PromptRecord[];
  total: number;
  pageNum: number;
  pageSize: number;
}

export interface CreatePromptPayload {
  promptCode: string;
  promptName: string;
  promptType: string;
  promptCategory: string;
  description: string;
  content: string;
  version: string;
  status: string;
  metadata: Record<string, unknown>;
  remark: string;
}

export interface UpdatePromptPayload {
  promptCode: string;
  promptName: string;
  promptType: string;
  promptCategory: string;
  description: string;
  content: string;
  version: string;
  status: string;
  metadata: Record<string, unknown>;
  remark: string;
}

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export async function fetchPromptPage(params: PromptQueryParams): Promise<PromptPageResult> {
  const response = await http.get<ApiResponse<PromptPageResult>>("/prompts", { params });
  return response.data.data;
}

export async function fetchPromptDetail(promptId: number): Promise<PromptDetail> {
  const response = await http.get<ApiResponse<PromptDetail>>(`/prompts/${promptId}`);
  return response.data.data;
}

export async function createPrompt(payload: CreatePromptPayload): Promise<PromptDetail> {
  const response = await http.post<ApiResponse<PromptDetail>>("/prompts", payload);
  return response.data.data;
}

export async function updatePrompt(promptId: number, payload: UpdatePromptPayload): Promise<PromptDetail> {
  const response = await http.put<ApiResponse<PromptDetail>>(`/prompts/${promptId}`, payload);
  return response.data.data;
}

export async function enablePrompt(promptId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/prompts/${promptId}/enable`);
}

export async function disablePrompt(promptId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/prompts/${promptId}/disable`);
}
