import { defineConfig } from "@playwright/test";

const port = Number(process.env.FRONTEND_E2E_PORT || 5179);

export default defineConfig({
  testDir: "./tests/e2e",
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  use: { baseURL: `http://127.0.0.1:${port}`, trace: "retain-on-failure" },
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${port}`,
    url: `http://127.0.0.1:${port}`,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000
  },
  projects: [{ name: "chromium", use: { browserName: "chromium" } }]
});
