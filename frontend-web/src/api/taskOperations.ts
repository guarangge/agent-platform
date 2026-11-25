import coreHttp from "@/api/coreHttp";

export interface TaskOperationsQueryParams {
  pageNum: number;
  pageSize: number;
  status?: string;
  agentId?: number;
  conversationId?: number;
  taskId?: number;
  keyword?: string;
}

export interface TaskOperationsRecord {
  taskId: number;
  taskCode: string;
  runCode: string | null;
  conversationId: number;
  conversationCode: string;
  conversationTitle: string | null;
  agentId: number;
  agentName: string;
  parentTaskId: number | null;
  rootTaskId: number | null;
  taskKind: string | null;
  executionType: string | null;
  traceId: string | null;
  currentPlanVersion: number | null;
  status: string;
  errorMessage: string | null;
  stepCount: number;
  successStepCount: number;
  failedStepCount: number;
  approvalCount: number;
  pendingApprovalCount: number;
  delegationCount: number;
  durationMs: number | null;
  startedAt: string | null;
  finishedAt: string | null;
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

/** 查询当前用户可见的跨会话任务运行记录。 */
export async function fetchTaskOperationsPage(
  params: TaskOperationsQueryParams
): Promise<PageResult<TaskOperationsRecord>> {
  const response = await coreHttp.get<ApiResponse<PageResult<TaskOperationsRecord>>>("/tasks", { params });
  return response.data.data;
}
