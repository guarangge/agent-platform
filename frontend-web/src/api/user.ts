import http from "@/api/http";

export interface UserQueryParams {
  pageNum: number;
  pageSize: number;
  username?: string;
  displayName?: string;
  status?: string;
}

export interface UserRecord {
  userId: number;
  username: string;
  displayName: string;
  email: string;
  mobile: string;
  status: string;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface UserDetail {
  userId: number;
  username: string;
  displayName: string;
  email: string;
  mobile: string;
  status: string;
  lastLoginAt: string | null;
  remark: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserRoleItem {
  roleId: number;
  roleCode: string;
  roleName: string;
}

export interface UserPageResult {
  records: UserRecord[];
  total: number;
  pageNum: number;
  pageSize: number;
}

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface CreateUserPayload {
  username: string;
  password: string;
  displayName: string;
  email: string;
  mobile: string;
  status: string;
  remark: string;
}

export interface UpdateUserPayload {
  displayName: string;
  email: string;
  mobile: string;
  status: string;
  remark: string;
}

export async function fetchUserPage(params: UserQueryParams): Promise<UserPageResult> {
  const response = await http.get<ApiResponse<UserPageResult>>("/users", {
    params
  });
  return response.data.data;
}

export async function fetchUserDetail(userId: number): Promise<UserDetail> {
  const response = await http.get<ApiResponse<UserDetail>>(`/users/${userId}`);
  return response.data.data;
}

export async function createUser(payload: CreateUserPayload): Promise<UserDetail> {
  const response = await http.post<ApiResponse<UserDetail>>("/users", payload);
  return response.data.data;
}

export async function updateUser(userId: number, payload: UpdateUserPayload): Promise<UserDetail> {
  const response = await http.put<ApiResponse<UserDetail>>(`/users/${userId}`, payload);
  return response.data.data;
}

export async function enableUser(userId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/users/${userId}/enable`);
}

export async function disableUser(userId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/users/${userId}/disable`);
}

export async function resetUserPassword(userId: number, newPassword: string): Promise<void> {
  await http.put<ApiResponse<null>>(`/users/${userId}/reset-password`, {
    newPassword
  });
}

export async function fetchUserRoles(userId: number): Promise<UserRoleItem[]> {
  const response = await http.get<ApiResponse<UserRoleItem[]>>(`/users/${userId}/roles`);
  return response.data.data;
}

export async function updateUserRoles(userId: number, roleIds: number[]): Promise<void> {
  await http.put<ApiResponse<null>>(`/users/${userId}/roles`, {
    roleIds
  });
}
