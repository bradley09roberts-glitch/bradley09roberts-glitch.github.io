// The reader's theme goes on <html> first of all (theme-init.ts), before React and the pages
// are evaluated, so the page is in it from the earliest moment the bundle can reach.
import "./theme-init";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { DEFAULT_PATH } from "./pages";
import { installRouter } from "./router";
import "./styles.css";

installRouter(DEFAULT_PATH);

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
