import http from "@/api/http";
import coreHttp from "@/api/coreHttp";
import type { KnowledgeCitation, KnowledgeDetail, KnowledgePageItem } from "@/types/knowledge";

interface ApiResponse<T> { code: number; message: string; data: T }
interface PageResponse<T> { records: T[]; total: number; pageNum: number; pageSize: number }

export interface SaveKnowledgePayload {
  knowledgeCode: string; knowledgeName: string; description: string; embeddingModelCode: string;
  chunkSize: number; chunkOverlap: number; retrievalTopK: number; similarityThreshold: number;
  status: string; metadata: Record<string, unknown>; remark: string;
}

export interface SaveSourcePayload {
  sourceCode: string; sourceName: string; sourceType: string; sourceUri: string; syncMode: string;
  config: Record<string, unknown>; status: string; metadata: Record<string, unknown>;
}


export async function fetchKnowledgePage(params: Record<string, unknown>): Promise<PageResponse<KnowledgePageItem>> {
  return (await http.get<ApiResponse<PageResponse<KnowledgePageItem>>>("/knowledge-bases", { params })).data.data;
}
export async function fetchKnowledgeDetail(id: number): Promise<KnowledgeDetail> {
  return (await http.get<ApiResponse<KnowledgeDetail>>(`/knowledge-bases/${id}`)).data.data;
}
export async function fetchKnowledgeBindingOptions(): Promise<{ agents: Array<{ id: number; code: string; name: string; status: string }>; roles: Array<{ id: number; code: string; name: string; status: string }> }> {
  return (await http.get("/knowledge-bases/binding-options")).data.data;
}
export async function createKnowledge(payload: SaveKnowledgePayload): Promise<KnowledgeDetail> {
  return (await http.post<ApiResponse<KnowledgeDetail>>("/knowledge-bases", payload)).data.data;
}
export async function updateKnowledge(id: number, payload: SaveKnowledgePayload): Promise<KnowledgeDetail> {
  return (await http.put<ApiResponse<KnowledgeDetail>>(`/knowledge-bases/${id}`, payload)).data.data;
}
export async function saveKnowledgeSource(knowledgeId: number, sourceId: number | null, payload: SaveSourcePayload): Promise<KnowledgeDetail> {
  const url = `/knowledge-bases/${knowledgeId}/sources${sourceId ? `/${sourceId}` : ""}`;
  return (await (sourceId ? http.put<ApiResponse<KnowledgeDetail>>(url, payload) : http.post<ApiResponse<KnowledgeDetail>>(url, payload))).data.data;
}
export async function uploadKnowledgeDocument(knowledgeId: number, sourceId: number | null, file: File): Promise<KnowledgeDetail> {
  const formData = new FormData();
  formData.append("file", file);
  if (sourceId !== null) formData.append("sourceId", String(sourceId));
  return (await http.post<ApiResponse<KnowledgeDetail>>(`/knowledge-bases/${knowledgeId}/documents/upload`, formData, { timeout: 900000 })).data.data;
}
export async function fetchKnowledgeDocumentPreview(knowledgeId: number, documentId: number): Promise<Blob> {
  const response = await http.get<Blob>(`/knowledge-bases/${knowledgeId}/documents/${documentId}/preview`, { responseType: "blob", timeout: 120000 });
  if (String(response.headers["content-type"] || "").includes("application/json")) {
    const payload = JSON.parse(await response.data.text()) as { message?: string };
    throw new Error(payload.message || "文档预览加载失败");
  }
  return response.data;
}
export async function reparseKnowledgeDocument(knowledgeId: number, documentId: number): Promise<KnowledgeDetail> {
  return (await http.post<ApiResponse<KnowledgeDetail>>(`/knowledge-bases/${knowledgeId}/documents/${documentId}/reparse`, undefined, { timeout: 900000 })).data.data;
}
export async function updateKnowledgeBindings(knowledgeId: number, agentIds: number[], roleIds: number[]): Promise<KnowledgeDetail> {
  return (await http.put<ApiResponse<KnowledgeDetail>>(`/knowledge-bases/${knowledgeId}/bindings`, { agentIds, roleIds })).data.data;
}
export async function createKnowledgeIndexTask(knowledgeId: number, documentIds: number[] = []): Promise<void> {
  await http.post(`/knowledge-bases/${knowledgeId}/index-tasks`, { documentIds, triggerType: "manual" });
}
export async function searchKnowledge(payload: { agentId: number; knowledgeId?: number; query: string; topK: number }): Promise<{ retrievalCode: string; hitCount: number; latencyMs: number; citations: KnowledgeCitation[]; promptContext: string }> {
  return (await coreHttp.post("/knowledge/search", payload)).data.data;
}
export async function evaluateKnowledge(payload: { agentId: number; knowledgeId?: number; cases: Array<{ caseCode: string; query: string; expectedDocumentIds: number[]; topK: number }> }): Promise<Record<string, unknown>> {
  return (await coreHttp.post("/knowledge/eval-runs", payload)).data.data;
}
export async function fetchKnowledgeChunks(agentId: number, knowledgeId: number): Promise<Array<Record<string, unknown>>> {
  return (await coreHttp.get("/knowledge/chunks", { params: { agentId, knowledgeId, pageNum: 1, pageSize: 100 } })).data.data;
}
export async function fetchKnowledgeEvalRuns(agentId?: number): Promise<Array<Record<string, unknown>>> {
  return (await coreHttp.get("/knowledge/eval-runs", { params: { agentId } })).data.data;
}
