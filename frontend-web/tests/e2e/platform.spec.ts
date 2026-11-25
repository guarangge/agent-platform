import { expect, test } from "@playwright/test";

async function fillCredentials(page: import("@playwright/test").Page): Promise<void> {
  const password = process.env.PLATFORM_E2E_ADMIN_PASSWORD;
  if (!password) throw new Error("请设置 PLATFORM_E2E_ADMIN_PASSWORD");
  await page.getByPlaceholder("请输入用户名").fill(process.env.PLATFORM_E2E_ADMIN_USERNAME || "zc");
  await page.getByPlaceholder("请输入密码").fill(password);
}

test("真实双服务登录并访问 Agent 管理页", async ({ page }) => {
  await page.goto("/login");
  await expect(page.getByText("登录系统")).toBeVisible();
  await fillCredentials(page);
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page).toHaveURL(/\/chat$/);
  await page.getByRole("button", { name: "打开账户菜单" }).click();
  await page.getByText("管理设置", { exact: true }).click();
  await expect(page).toHaveURL(/\/settings$/);
  await page.getByText("Agent 管理", { exact: true }).first().click();
  await expect(page).toHaveURL(/\/agents$/);
  await expect(page.getByText("Agent 管理", { exact: true }).last()).toBeVisible();
  await expect(page.getByRole("button", { name: "新建 Agent" })).toBeVisible();
});

test("退出后受保护页面回到登录页", async ({ page }) => {
  await page.goto("/login");
  await fillCredentials(page);
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page).toHaveURL(/\/chat$/);
  await page.getByRole("button", { name: "打开账户菜单" }).click();
  await page.getByText("退出登录", { exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto("/agents");
  await expect(page).toHaveURL(/\/login$/);
});
