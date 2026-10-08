import { useEffect, useState } from "react";
import type { Bootstrap } from "./host";
import { useCoach } from "./useCoach";
import { AbilityPanel, ExtrasPanel, PurchasePanel, ThreatPanel } from "./components/Coach";
import { OverlayCard } from "./components/Overlay";
import { ReviewPanel, ScenarioPanel, SettingsPanel, WhatIfPanel } from "./components/Panels";
import { AbilitySection, HeroSection, ItemsSection, MatchBar, RosterSection, SoulsSection, ThreatSection } from "./components/Setup";
import { fmtTime } from "./format";

const TABS = ["Match", "What-if", "Review", "Scenarios", "Settings"] as const;
type Tab = (typeof TABS)[number];

export function App({ boot }: { boot: Bootstrap }) {
  const c = useCoach(boot);
  const [tab, setTab] = useState<Tab>(() => (new URLSearchParams(location.search).get("tab") as Tab) ?? "Match");
  // Keyboard: "/" focuses item search, Ctrl+K hero search, Alt+1..5 switches tabs.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      const typing = target.tagName === "INPUT" || target.tagName === "SELECT" || target.tagName === "TEXTAREA";
      if (e.key === "/" && !typing) {
        e.preventDefault();
        setTab("Match");
        setTimeout(() => document.getElementById("item-search")?.focus(), 0);
      } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setTab("Match");
        setTimeout(() => document.getElementById("hero-search")?.focus(), 0);
      } else if (e.altKey && /^[1-5]$/.test(e.key)) {
        setTab(TABS[Number(e.key) - 1]!);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);
  const heroSet = c.output.rec.heroId != null;
  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="logo" aria-hidden>
            ◆
          </span>
          CounterCoach
          <span className="muted small"> · external companion · data build {c.dataBoot.snapshot.meta.clientVersion}</span>
        </div>
        <nav className="tabs" role="tablist">
          {TABS.map((t, i) => (
            <button key={t} role="tab" aria-selected={tab === t} className={`tab ${tab === t ? "on" : ""}`} onClick={() => setTab(t)} title={`Alt+${i + 1}`}>
              {t}
            </button>
          ))}
        </nav>
        <div className="clock" title="Match clock (extrapolated from your last entry)">
          {c.state.paused ? "⏸ " : ""}
          {fmtTime(c.gameTime)}
        </div>
      </header>
      {c.deps.compat.status === "outdated" && <div className="banner bad">{c.deps.compat.message}</div>}
      {tab === "Match" && (
        <main className="match">
          <div className="col setup">
            <MatchBar c={c} />
            <HeroSection c={c} />
            {heroSet && (
              <>
                <SoulsSection c={c} />
                <ItemsSection c={c} />
                <AbilitySection c={c} />
              </>
            )}
            <RosterSection c={c} />
            <ThreatSection c={c} />
          </div>
          <div className="col coach">
            <PurchasePanel c={c} />
            <AbilityPanel c={c} />
            <ThreatPanel c={c} />
          </div>
          <div className="col side">
            {c.host.kind === "web" && (
              <div className="overlay-preview" aria-label="Overlay preview">
                <div className="tiny muted">Overlay preview</div>
                <OverlayCard m={c.overlayModel} expanded={c.settings.overlay.expanded} scale={c.settings.overlay.scale} />
              </div>
            )}
            <ExtrasPanel c={c} />
          </div>
        </main>
      )}
      {tab === "What-if" && (
        <main className="page">
          <WhatIfPanel c={c} />
        </main>
      )}
      {tab === "Review" && (
        <main className="page">
          <ReviewPanel c={c} />
        </main>
      )}
      {tab === "Scenarios" && (
        <main className="page">
          <ScenarioPanel c={c} fixtures={boot.fixtures} />
        </main>
      )}
      {tab === "Settings" && (
        <main className="page">
          <SettingsPanel c={c} />
        </main>
      )}
      <footer className="footer tiny muted">
        Not affiliated with or endorsed by Valve. Uses only your own inputs; it does not read game memory, inject, or automate purchases. Game data: deadlock-api.com (community).
      </footer>
    </div>
  );
}
