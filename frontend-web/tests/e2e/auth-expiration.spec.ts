import { expect, test } from "@playwright/test";

test("Token 失效后清理登录态并跳转登录页", async ({ page }) => {
  await page.goto("/login");
  await page.evaluate(async () => {
    localStorage.setItem("registry_access_token", "EXPIRED_TOKEN");
    history.replaceState({}, "", "/chat?from=expired#message");
    const module = await import("/src/utils/authExpiration.ts");
    module.handleAuthenticationExpired();
  });

  await expect(page).toHaveURL(/\/login$/);
  const state = await page.evaluate(() => ({
    token: localStorage.getItem("registry_access_token"),
    redirect: sessionStorage.getItem("post_login_redirect")
  }));
  expect(state.token).toBeNull();
  expect(state.redirect).toBe("/chat?from=expired#message");
});
