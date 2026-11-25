import http from "@/api/http";

export interface LoginRequest {
  username: string;
  password: string;
  rememberMe: boolean;
}

export interface RoleInfo {
  roleId: number;
  roleCode: string;
  roleName: string;
}

export interface CurrentUserInfo {
  userId: number;
  username: string;
  displayName: string;
  status: string;
  roles: RoleInfo[];
  menuScopes: string[];
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string | null;
}

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await http.post<ApiResponse<LoginResponse>>("/auth/login", request);
  return response.data.data;
}

export async function fetchCurrentUser(): Promise<CurrentUserInfo> {
  const response = await http.get<ApiResponse<CurrentUserInfo>>("/auth/me");
  return response.data.data;
}
