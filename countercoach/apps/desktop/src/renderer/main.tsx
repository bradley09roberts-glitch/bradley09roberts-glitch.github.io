import { StrictMode, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { OverlayApp } from "./components/Overlay";
import { getHost, type Bootstrap } from "./host";
import "./styles.css";

function Root() {
  const [boot, setBoot] = useState<Bootstrap | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    getHost()
      .getBootstrap()
      .then(setBoot)
      .catch((e: unknown) => setError(String(e)));
  }, []);
  if (error) return <div className="fatal">Could not load game data: {error}</div>;
  if (!boot) return <div className="loading">Loading game data…</div>;
  return <App boot={boot} />;
}

const view = new URLSearchParams(location.search).get("view");
createRoot(document.getElementById("root")!).render(<StrictMode>{view === "overlay" ? <OverlayApp /> : <Root />}</StrictMode>);
