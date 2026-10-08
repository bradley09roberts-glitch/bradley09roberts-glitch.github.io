import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

export default defineConfig(({ mode }) => ({
  root: path.resolve(__dirname, "src/renderer"),
  publicDir: path.resolve(__dirname, "public"),
  base: "./",
  plugins: [react()],
  build: {
    outDir: path.resolve(__dirname, mode === "web" ? "dist-web" : "dist/renderer"),
    emptyOutDir: true,
    target: "chrome130",
    sourcemap: false,
  },
  server: { port: 5199, strictPort: true },
}));
