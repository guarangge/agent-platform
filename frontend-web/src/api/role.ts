import http from "@/api/http";

export interface RoleQueryParams {
  pageNum: number;
  pageSize: number;
  roleCode?: string;
  roleName?: string;
  status?: string;
}

export interface RoleRecord {
  roleId: number;
  roleCode: string;
  roleName: string;
  status: string;
  description: string | null;
  createdAt: string;
}

export interface RoleDetail {
  roleId: number;
  roleCode: string;
  roleName: string;
  status: string;
  description: string | null;
  menuScope: string[];
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface RoleAgentItem {
  agentId: number;
  agentCode: string;
  agentName: string;
  status: string;
  version: string;
  description: string | null;
  boundAt: string;
}

export interface RolePageResult {
  records: RoleRecord[];
  total: number;
  pageNum: number;
  pageSize: number;
}

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface CreateRolePayload {
  roleCode: string;
  roleName: string;
  status: string;
  description: string;
  menuScope: string[];
  remark: string;
}

export interface UpdateRolePayload {
  roleCode: string;
  roleName: string;
  status: string;
  description: string;
  menuScope: string[];
  remark: string;
}

export async function fetchRolePage(params: RoleQueryParams): Promise<RolePageResult> {
  const response = await http.get<ApiResponse<RolePageResult>>("/roles", {
    params
  });
  return response.data.data;
}

export async function fetchRoleDetail(roleId: number): Promise<RoleDetail> {
  const response = await http.get<ApiResponse<RoleDetail>>(`/roles/${roleId}`);
  return response.data.data;
}

export async function createRole(payload: CreateRolePayload): Promise<RoleDetail> {
  const response = await http.post<ApiResponse<RoleDetail>>("/roles", payload);
  return response.data.data;
}

export async function updateRole(roleId: number, payload: UpdateRolePayload): Promise<RoleDetail> {
  const response = await http.put<ApiResponse<RoleDetail>>(`/roles/${roleId}`, payload);
  return response.data.data;
}

export async function enableRole(roleId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/roles/${roleId}/enable`);
}

export async function disableRole(roleId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/roles/${roleId}/disable`);
}

export async function fetchRoleAgents(roleId: number): Promise<RoleAgentItem[]> {
  const response = await http.get<ApiResponse<RoleAgentItem[]>>(`/roles/${roleId}/agents`);
  return response.data.data;
}

export async function updateRoleAgents(roleId: number, agentIds: number[]): Promise<void> {
  await http.put<ApiResponse<null>>(`/roles/${roleId}/agents`, {
    agentIds
  });
}
