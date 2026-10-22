import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv, type UserConfig } from "vite";

export default defineConfig(({ mode }) => {
  const config: UserConfig = {
    plugins: [react()],
    server: {
      host: "127.0.0.1",
      // /media serves the teams' downloaded crests and venue photos (footy.api.media_routes).
      proxy: { "/api": "http://127.0.0.1:8000", "/media": "http://127.0.0.1:8000" },
    },
  };
  // Snapshot build (`npm run build:snapshot`, or VITE_SNAPSHOT=1): a static, read-only copy
  // that answers API requests from snapshot.json (src/snapshot.ts). Relative asset paths, so
  // it works from any folder; its own output folder, so the normal dist/ is never touched.
  // Every other build and the dev server use the config above unchanged.
  const snapshot = mode === "snapshot" || loadEnv(mode, ".", "VITE_").VITE_SNAPSHOT === "1";
  if (!snapshot) return config;
  return {
    ...config,
    base: "./",
    define: { "import.meta.env.VITE_SNAPSHOT": JSON.stringify("1") },
    build: { outDir: "dist-snapshot", emptyOutDir: true },
  };
});
