import { getAccessToken } from "@/utils/token";
import { handleAuthenticationExpired } from "@/utils/authExpiration";
import { RuntimeClient } from "@/runtime/sdk";

/** 会话页面共享的 Runtime SDK Client。 */
export const runtimeClient = new RuntimeClient({
  baseUrl: import.meta.env.VITE_CORE_API_BASE_URL || "/api/core",
  accessToken: getAccessToken,
  onUnauthorized: handleAuthenticationExpired
});
