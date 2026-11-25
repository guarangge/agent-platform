import axios from "axios";
import { getAccessToken } from "@/utils/token";
import { handleAuthenticationExpired } from "@/utils/authExpiration";

const http = axios.create({
  baseURL: import.meta.env.VITE_REGISTRY_API_BASE_URL || "/api/registry",
  timeout: 10000
});

http.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    if (response.data?.code === 401) {
      handleAuthenticationExpired();
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

export default http;
