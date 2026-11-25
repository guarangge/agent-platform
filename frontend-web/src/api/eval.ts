import coreHttp from "@/api/coreHttp";
import { triggerBlobDownload } from "@/utils/download";

export interface EvalCasePayload {
  caseCode: string;
  caseName: string;
  userInput: string;
  expectedReplyKeywords: string[];
  forbiddenReplyKeywords: string[];
  expectedToolCodes: string[];
  expectedStepTypes: string[];
  expectedFinalStatus: string;
  passThreshold: number;
  orderNo: number;
  status: string;
  metadata: Record<string, unknown>;
}

export interface BaselineComparison {
  baselineRunId: number;
  baselineRunCode: string;
  baselineAverageScore: number;
  scoreDelta: number;
  passDelta: number;
  improvedCaseCount: number;
  regressedCaseCount: number;
  unchangedCaseCount: number;
}

export interface EvalSuitePayload {
  suiteCode: string;
  suiteName: string;
  agentId: number;
  agentName: string;
  description: string;
  status: string;
  metadata: Record<string, unknown>;
  evalCases: EvalCasePayload[];
}

export interface ExecuteEvalSuitePayload {
  triggerType?: string;
  baselineRunId?: number;
  scenario?: string;
  caseTags?: string[];
  enforceCiGate?: boolean;
}

export interface EvalSuiteQueryParams {
  pageNum: number;
  pageSize: number;
  suiteCode?: string;
  suiteName?: string;
  agentId?: number;
  status?: string;
}

export interface EvalRunQueryParams {
  pageNum: number;
  pageSize: number;
  suiteId?: number;
  agentId?: number;
  runStatus?: string;
}

export interface EvalCaseRecord extends EvalCasePayload {
  caseId: number;
  createdAt: string;
  updatedAt: string;
}

export interface EvalSuiteRecord {
  suiteId: number;
  currentVersion: number;
  suiteCode: string;
  suiteName: string;
  agentId: number;
  agentName: string;
  description: string | null;
  status: string;
  caseCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface EvalSuiteDetail {
  suiteId: number;
  currentVersion: number;
  suiteCode: string;
  suiteName: string;
  agentId: number;
  agentName: string;
  description: string | null;
  status: string;
  metadata: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
  evalCases: EvalCaseRecord[];
}

export interface EvalRunRecord {
  runId: number;
  suiteId: number;
  suiteVersion: number;
  suiteCode: string;
  suiteName: string;
  agentId: number;
  agentName: string;
  runCode: string;
  runStatus: string;
  totalCases: number;
  passedCases: number;
  failedCases: number;
  averageScore: number;
  triggerType: string;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
}

export interface EvalRunCaseRecord {
  runCaseId: number;
  caseId: number;
  caseCode: string;
  caseName: string;
  conversationId: number | null;
  taskId: number | null;
  traceId: string | null;
  runStatus: string;
  passed: boolean | null;
  score: number;
  ruleScore: number | null;
  judgeScore: number | null;
  judgePassed: boolean | null;
  judgeRationale: string | null;
  actualReply: string | null;
  actualToolCodes: string[];
  actualStepTypes: string[];
  actualFinalStatus: string | null;
  baselineScore: number | null;
  baselinePassed: boolean | null;
  scoreDelta: number | null;
  failureReasons: string[];
  metadata: Record<string, unknown>;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
}

export interface EvalRunDetail extends EvalRunRecord {
  metadata: Record<string, unknown>;
  baselineComparison: BaselineComparison | null;
  runCases: EvalRunCaseRecord[];
}

export interface EvalRunCompareItem {
  caseCode: string;
  caseName: string;
  changeType: string;
  fromScore: number | null;
  toScore: number | null;
  scoreDelta: number | null;
  fromPassed: boolean | null;
  toPassed: boolean | null;
  fromStatus: string | null;
  toStatus: string | null;
  fromReply: string | null;
  toReply: string | null;
}

export interface EvalRunCompareResult {
  suiteId: number;
  fromRunId: number;
  fromRunCode: string;
  fromSuiteVersion: number;
  toRunId: number;
  toRunCode: string;
  toSuiteVersion: number;
  summary: {
    improvedCount: number;
    regressedCount: number;
    unchangedCount: number;
    addedCount: number;
    removedCount: number;
    averageScoreDelta: number;
    passDelta: number;
  };
  comparedCases: EvalRunCompareItem[];
}

export interface EvalSuiteVersionRecord {
  versionId: number;
  suiteId: number;
  versionNo: number;
  suiteCode: string;
  suiteName: string;
  agentName: string;
  status: string;
  caseCount: number;
  metadataJson: string;
  createdAt: string;
  createdBy: number | null;
}

export interface EvalSuiteDiffItem {
  changeType: string;
  itemKey: string;
  beforeText: string | null;
  afterText: string | null;
}

export interface EvalSuiteDiffResult {
  suiteId: number;
  fromVersion: number;
  toVersion: number;
  summary: {
    addedCount: number;
    removedCount: number;
    changedCount: number;
  };
  changedItems: EvalSuiteDiffItem[];
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

export async function fetchEvalSuitePage(params: EvalSuiteQueryParams): Promise<PageResult<EvalSuiteRecord>> {
  const response = await coreHttp.get<ApiResponse<PageResult<EvalSuiteRecord>>>("/evals/suites", { params });
  return response.data.data;
}

export async function fetchEvalSuiteDetail(suiteId: number): Promise<EvalSuiteDetail> {
  const response = await coreHttp.get<ApiResponse<EvalSuiteDetail>>(`/evals/suites/${suiteId}`);
  return response.data.data;
}

export async function createEvalSuite(payload: EvalSuitePayload): Promise<EvalSuiteDetail> {
  const response = await coreHttp.post<ApiResponse<EvalSuiteDetail>>("/evals/suites", payload);
  return response.data.data;
}

export async function updateEvalSuite(suiteId: number, payload: EvalSuitePayload): Promise<EvalSuiteDetail> {
  const response = await coreHttp.put<ApiResponse<EvalSuiteDetail>>(`/evals/suites/${suiteId}`, payload);
  return response.data.data;
}

export async function markEvalBaselineRun(suiteId: number, runId: number): Promise<EvalSuiteDetail> {
  const response = await coreHttp.put<ApiResponse<EvalSuiteDetail>>(`/evals/suites/${suiteId}/baseline/${runId}`);
  return response.data.data;
}

export async function executeEvalSuite(suiteId: number, payload: ExecuteEvalSuitePayload = { triggerType: "manual" }): Promise<EvalRunDetail> {
  const response = await coreHttp.post<ApiResponse<EvalRunDetail>>(`/evals/suites/${suiteId}/runs`, payload);
  return response.data.data;
}

export async function fetchEvalRunPage(params: EvalRunQueryParams): Promise<PageResult<EvalRunRecord>> {
  const response = await coreHttp.get<ApiResponse<PageResult<EvalRunRecord>>>("/evals/runs", { params });
  return response.data.data;
}

export async function fetchEvalRunDetail(runId: number): Promise<EvalRunDetail> {
  const response = await coreHttp.get<ApiResponse<EvalRunDetail>>(`/evals/runs/${runId}`);
  return response.data.data;
}

export async function exportEvalRunDetail(runId: number): Promise<void> {
  const response = await coreHttp.get<Blob>(`/evals/runs/${runId}/export`, {
    responseType: "blob"
  });
  triggerBlobDownload(response, `eval-run-${runId}.csv`);
}

export async function exportEvalRunJsonReport(runId: number): Promise<void> {
  const response = await coreHttp.get<Blob>(`/evals/runs/${runId}/report.json`, {
    responseType: "blob"
  });
  triggerBlobDownload(response, `eval-run-report-${runId}.json`);
}

export async function exportEvalRunMarkdownReport(runId: number): Promise<void> {
  const response = await coreHttp.get<Blob>(`/evals/runs/${runId}/report.md`, {
    responseType: "blob"
  });
  triggerBlobDownload(response, `eval-run-report-${runId}.md`);
}

export async function fetchEvalRunCompare(fromRunId: number, toRunId: number): Promise<EvalRunCompareResult> {
  const response = await coreHttp.get<ApiResponse<EvalRunCompareResult>>("/evals/runs/compare", {
    params: { fromRunId, toRunId }
  });
  return response.data.data;
}

export async function exportEvalRunCompare(fromRunId: number, toRunId: number): Promise<void> {
  const response = await coreHttp.get<Blob>("/evals/runs/compare/export", {
    params: { fromRunId, toRunId },
    responseType: "blob"
  });
  triggerBlobDownload(response, `eval-run-compare-${fromRunId}-${toRunId}.csv`);
}

export async function fetchEvalSuiteVersions(suiteId: number): Promise<EvalSuiteVersionRecord[]> {
  const response = await coreHttp.get<ApiResponse<EvalSuiteVersionRecord[]>>(`/evals/suites/${suiteId}/versions`);
  return response.data.data;
}

export async function fetchEvalSuiteDiff(suiteId: number, fromVersion: number, toVersion: number): Promise<EvalSuiteDiffResult> {
  const response = await coreHttp.get<ApiResponse<EvalSuiteDiffResult>>(`/evals/suites/${suiteId}/versions/diff`, {
    params: { fromVersion, toVersion }
  });
  return response.data.data;
}

export async function exportEvalSuiteDiff(suiteId: number, fromVersion: number, toVersion: number): Promise<void> {
  const response = await coreHttp.get<Blob>(`/evals/suites/${suiteId}/versions/diff/export`, {
    params: { fromVersion, toVersion },
    responseType: "blob"
  });
  triggerBlobDownload(response, `eval-suite-diff-${suiteId}-${fromVersion}-${toVersion}.csv`);
}
