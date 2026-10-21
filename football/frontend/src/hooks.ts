import { useEffect } from "react";

export const SITE_NAME = "Football model";

/** Sets the browser tab title while the calling page is shown. */
export function useDocumentTitle(title: string | null | undefined): void {
  useEffect(() => {
    if (!title) return;
    document.title = `${title} · ${SITE_NAME}`;
  }, [title]);
}
