import { resolve } from 'node:path'
import { defineConfig } from 'electron-vite'
import react from '@vitejs/plugin-react'

const alias = { '@shared': resolve(__dirname, 'src/shared') }

export default defineConfig({
  main: {
    resolve: { alias },
    build: { externalizeDeps: true },
  },
  preload: {
    resolve: { alias },
    // Sandboxed preloads must be CommonJS and self-contained.
    build: { externalizeDeps: true },
  },
  renderer: {
    resolve: { alias },
    plugins: [react()],
  },
})
