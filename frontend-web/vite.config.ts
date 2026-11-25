import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import path from "node:path";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src")
    }
  },
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api/registry": {
        target: "http://127.0.0.1:8089",
        changeOrigin: true
      },
      "/api/core": {
        target: "http://127.0.0.1:8081",
        changeOrigin: true
      }
    }
  }
});
