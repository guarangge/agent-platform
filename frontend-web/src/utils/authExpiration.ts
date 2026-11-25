import { clearAccessToken } from "@/utils/token";

const POST_LOGIN_REDIRECT_KEY = "post_login_redirect";
let redirecting = false;

/** 清理失效登录态并跳转登录页；并发失败请求只触发一次导航。 */
export function handleAuthenticationExpired(): void {
  clearAccessToken();
  if (typeof window === "undefined" || window.location.pathname === "/login" || redirecting) {
    return;
  }
  const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`;
  if (isSafeInternalPath(currentPath)) {
    window.sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, currentPath);
  }
  redirecting = true;
  window.location.replace("/login");
}

/** 登录成功后读取并删除原访问地址，非法外部地址统一回首页。 */
export function consumePostLoginRedirect(): string {
  if (typeof window === "undefined") return "/";
  const redirect = window.sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY) ?? "";
  window.sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY);
  return isSafeInternalPath(redirect) && !redirect.startsWith("/login") ? redirect : "/";
}

function isSafeInternalPath(path: string): boolean {
  return path.startsWith("/") && !path.startsWith("//") && !path.includes("\r") && !path.includes("\n");
}
