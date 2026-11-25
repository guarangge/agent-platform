import coreHttp from "@/api/coreHttp";
import { triggerBlobDownload } from "@/utils/download";

export interface CompactionQueryParams {
  pageNum?: number;
  pageSize?: number;
  traceId?: string;
  eventType?: string;
  traceStatus?: string;
  agentId?: number;
  conversationId?: number;
  taskId?: number;
  keyword?: string;
  startedFrom?: string;
  startedTo?: string;
}

export interface CompactionTraceRecord {
  taskTraceId: number;
  conversationId: number;
  conversationCode: string;
  taskId: number;
  taskCode: string;
  agentId: number;
  agentName: string;
  traceId: string;
  taskStepId: number | null;
  stepNo: number | null;
  stepName: string | null;
  eventType: string;
  spanName: string;
  traceStatus: string;
  durationMs: number | null;
  errorMessage: string | null;
  stage: string | null;
  tokenizerFamily: string | null;
  qualityScore: number | null;
  qualityStatus: string | null;
  originalChars: number | null;
  compactedChars: number | null;
  savedChars: number | null;
  estimatedOriginalTokens: number | null;
  estimatedCompactedTokens: number | null;
  savedTokens: number | null;
  summarized: boolean | null;
  truncated: boolean | null;
  policyJson: string;
  metadata: string;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
}

export interface CompactionBucketItem {
  bucketKey: string;
  bucketLabel: string;
  countValue: number;
}

export interface CompactionMetrics {
  totalCount: number;
  criticalCount: number;
  warningCount: number;
  avgQualityScore: number;
  avgSavedChars: number;
  avgSavedTokens: number;
  stageBuckets: CompactionBucketItem[];
  qualityBuckets: CompactionBucketItem[];
  topSavedTokenRecords: CompactionTraceRecord[];
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

export async function fetchCompactionPage(params: CompactionQueryParams): Promise<PageResult<CompactionTraceRecord>> {
  const response = await coreHttp.get<ApiResponse<PageResult<CompactionTraceRecord>>>("/traces/compaction", { params });
  return response.data.data;
}

export async function fetchCompactionMetrics(params: CompactionQueryParams): Promise<CompactionMetrics> {
  const response = await coreHttp.get<ApiResponse<CompactionMetrics>>("/traces/compaction/metrics", { params });
  return response.data.data;
}

export async function exportCompactionCsv(params: CompactionQueryParams): Promise<void> {
  const response = await coreHttp.get<Blob>("/traces/compaction/export", {
    params,
    responseType: "blob"
  });
  triggerBlobDownload(response, "trace-compaction-export.csv");
}
