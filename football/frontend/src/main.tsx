import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { DEFAULT_PATH } from "./pages";
import { installRouter } from "./router";
import { SNAPSHOT } from "./snapshot";
import { SnapshotBanner } from "./components/SnapshotBanner";
import "./styles.css";

installRouter(DEFAULT_PATH);

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    {SNAPSHOT ? (
      <>
        <SnapshotBanner />
        <App />
      </>
    ) : (
      <App />
    )}
  </StrictMode>,
);
