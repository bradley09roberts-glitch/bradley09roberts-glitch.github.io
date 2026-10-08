/** A captured or loaded image handed to the screen reader (raw pixels, never uploaded). */
export interface CapturePayload {
  width: number;
  height: number;
  /** Raw 4-byte pixels; see `order`. */
  data: Uint8Array;
  order: "rgba" | "bgra";
  /** Where the image came from (shown to the user). */
  source: string;
  at: number;
}

export type CaptureResult = CapturePayload | { error: string };

export interface SavedCaptures {
  count: number;
  bytes: number;
  dir: string;
}
