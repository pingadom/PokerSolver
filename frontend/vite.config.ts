import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { loadEnv } from "vite";
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: { proxy: {
    "/api/v1/trainer/research/flop-interval-collection-v4": loadEnv(mode, ".", "POKERLAB_").POKERLAB_QUALIFIED_FLOP_API_TARGET ?? "http://localhost:18080",
    "/api": "http://localhost:18080",
  } },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
    globals: true,
  },
}));
