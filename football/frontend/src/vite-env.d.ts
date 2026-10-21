/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** "1" in a snapshot build (npm run build:snapshot); see src/snapshot.ts. */
  readonly VITE_SNAPSHOT?: string;
}
