/// <reference types="vite/client" />
import type { KinetikApi } from '@shared/ipc'

declare global {
  interface Window {
    kinetik: KinetikApi
  }
}
