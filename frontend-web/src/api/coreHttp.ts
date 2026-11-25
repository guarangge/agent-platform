import axios from "axios";
import { getAccessToken } from "@/utils/token";
import { handleAuthenticationExpired } from "@/utils/authExpiration";

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

const coreHttp = axios.create({
  baseURL: import.meta.env.VITE_CORE_API_BASE_URL || "/api/core",
  timeout: 60000
});

coreHttp.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

coreHttp.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResponse<unknown> | undefined;
    if (payload?.code === 401) {
      handleAuthenticationExpired();
    }
    if (payload && typeof payload.code === "number" && payload.code !== 200) {
      return Promise.reject(new Error(payload.message || "请求失败"));
    }
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      handleAuthenticationExpired();
    }
    return Promise.reject(error);
  }
);

export default coreHttp;
