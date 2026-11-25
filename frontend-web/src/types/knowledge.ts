export interface KnowledgePageItem {
  knowledgeId: number;
  knowledgeCode: string;
  knowledgeName: string;
  description: string | null;
  embeddingModelCode: string | null;
  documentCount: number;
  indexedDocumentCount: number;
  status: string;
  updatedAt: string;
}

export interface KnowledgeSource {
  sourceId: number;
  sourceCode: string;
  sourceName: string;
  sourceType: string;
  sourceUri: string | null;
  syncMode: string;
  config: Record<string, unknown>;
  status: string;
  lastSyncedAt: string | null;
  metadata: Record<string, unknown>;
}

export interface KnowledgeDocument {
  documentId: number;
  sourceId: number | null;
  documentCode: string;
  documentName: string;
  contentType: string;
  sourceUri: string | null;
  content: string;
  contentHash: string;
  version: number;
  status: string;
  metadata: Record<string, unknown>;
  indexedAt: string | null;
  updatedAt: string;
}

export interface KnowledgeIndexTask {
  taskId: number;
  taskCode: string;
  knowledgeId: number;
  triggerType: string;
  documentIds: number[];
  status: string;
  totalDocuments: number;
  processedDocuments: number;
  totalChunks: number;
  errorMessage: string | null;
  createdAt: string;
}

export interface KnowledgeDetail {
  knowledgeId: number;
  knowledgeCode: string;
  knowledgeName: string;
  description: string | null;
  embeddingModelCode: string | null;
  chunkSize: number;
  chunkOverlap: number;
  retrievalTopK: number;
  similarityThreshold: number;
  status: string;
  metadata: Record<string, unknown>;
  remark: string | null;
  sources: KnowledgeSource[];
  documents: KnowledgeDocument[];
  agentIds: number[];
  roleIds: number[];
  indexTasks: KnowledgeIndexTask[];
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeCitation {
  knowledgeId: number;
  knowledgeCode: string;
  documentId: number;
  documentCode: string;
  documentName: string;
  chunkNo: number;
  sourceUri: string | null;
  excerpt: string;
  keywordScore: number;
  vectorScore: number;
  score: number;
}
