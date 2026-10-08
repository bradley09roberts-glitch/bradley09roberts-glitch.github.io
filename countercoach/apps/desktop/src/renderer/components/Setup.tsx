import { useMemo, useState } from "react";
import { MODE_SUPPORT, readField, type AbilityState, type EnemyItemsValue, type GameMode, type Item, type ThreatKind } from "@countercoach/engine";
import type { Coach } from "../useCoach";
import { THREAT_SHORT, fmtTime, parseTime } from "../format";
import { Badge, Chip, Icon, SearchPicker, Section, type PickOption } from "./common";

const SLOT_TONE = { weapon: "weapon", vitality: "vitality", spirit: "spirit" } as const;

function useRecent(key: string): [string[], (id: string) => void] {
  const [recent, setRecent] = useState<string[]>(() => {
    try {
      return JSON.parse(localStorage.getItem(key) ?? "[]") as string[];
    } catch {
      return [];
    }
  });
  const push = (id: string) => {
    const next = [id, ...recent.filter((r) => r !== id)].slice(0, 12);
    setRecent(next);
    try {
      localStorage.setItem(key, JSON.stringify(next));
    } catch {
      /* ignore */
    }
  };
  return [recent, push];
}

export function itemOptions(items: Item[]): PickOption[] {
  return items
    .filter((i) => i.tier <= 4)
    .sort((a, b) => a.tier - b.tier || a.name.localeCompare(b.name))
    .map((i) => ({ id: i.className, label: i.name, sub: `T${i.tier} ${i.slot} · ${i.cost}`, image: i.image, tone: SLOT_TONE[i.slot] }));
}

export function MatchBar({ c }: { c: Coach }) {
  const { state, now, input, send } = c;
  const phase = readField(state, "match.phase", now)?.value ?? "";
  const standing = readField(state, "match.standing", now)?.value ?? "unknown";
  const [timeText, setTimeText] = useState("");
  return (
    <div className="matchbar" role="toolbar" aria-label="Match controls">
      <button type="button" className="btn" onClick={c.newMatch} title="Start a new match (clears roster, items and threats)">
        New match
      </button>
      <label className="field inline">
        <span>Mode</span>
        <select
          className="input"
          value={state.mode}
          onChange={(e) => send({ type: "match.start", matchId: state.matchId ?? `m-${Date.now()}`, mode: e.target.value as GameMode, at: Date.now() })}
        >
          {(Object.keys(MODE_SUPPORT) as GameMode[]).map((m) => (
            <option key={m} value={m}>
              {MODE_SUPPORT[m].label}
              {MODE_SUPPORT[m].supported ? "" : " (unsupported)"}
            </option>
          ))}
        </select>
      </label>
      <label className="field inline">
        <span>Clock</span>
        <input
          className="input narrow"
          placeholder={fmtTime(c.gameTime)}
          value={timeText}
          aria-label="Match time mm:ss"
          onChange={(e) => setTimeText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              const t = parseTime(timeText);
              if (t != null) send(input.clock_(t));
              setTimeText("");
            }
          }}
          onBlur={() => {
            const t = parseTime(timeText);
            if (t != null) send(input.clock_(t));
            setTimeText("");
          }}
        />
      </label>
      <label className="field inline">
        <span>Phase</span>
        <select className="input" value={phase} onChange={(e) => e.target.value && send(input.phase(e.target.value as "lane" | "mid" | "late"))}>
          <option value="">from clock</option>
          <option value="lane">Laning</option>
          <option value="mid">Mid game</option>
          <option value="late">Late game</option>
        </select>
      </label>
      <div className="seg" role="group" aria-label="Ahead or behind">
        {(["behind", "even", "ahead", "unknown"] as const).map((s) => (
          <button key={s} type="button" className={`seg-btn ${standing === s ? "on" : ""}`} onClick={() => send(input.standing(s))}>
            {s === "unknown" ? "?" : s}
          </button>
        ))}
      </div>
      <button type="button" className="btn ghost" onClick={() => send({ type: state.paused ? "match.resume" : "match.pause", at: Date.now() })}>
        {state.paused ? "Resume clock" : "Pause clock"}
      </button>
    </div>
  );
}

export function HeroSection({ c }: { c: Coach }) {
  const { deps, state, now, input, send } = c;
  const heroId = readField(state, "me.hero", now)?.value as number | undefined;
  const hero = heroId != null ? deps.data.hero(heroId) : undefined;
  const [changing, setChanging] = useState(false);
  const [recent, pushRecent] = useRecent("cc.recentHeroes");
  const options = useMemo(() => deps.data.playableHeroes().map((h) => ({ id: String(h.id), label: h.name, sub: h.heroType ?? "", image: h.image, tone: "hero" as const })), [deps]);
  const profile = hero ? deps.profiles.get(hero.id) : null;
  if (hero && !changing) {
    return (
      <Section
        title="Your hero"
        right={
          <button type="button" className="btn ghost small" onClick={() => setChanging(true)}>
            Change
          </button>
        }
      >
        <div className="hero-row">
          <Icon src={hero.image} name={hero.name} size={52} tone="hero" round />
          <div>
            <div className="hero-name">{hero.name}</div>
            <div className="muted small">
              {profile?.status === "curated" ? <Badge tone="accent">Curated profile</Badge> : <Badge>Basic profile (data-derived)</Badge>}
              {hero.inDevelopmentFlag && <Badge tone="warn" title="Data still flags this hero as in development (recent release)">new hero</Badge>}
            </div>
          </div>
          {profile && profile.archetypes.length > 1 && (
            <label className="field">
              <span>Build</span>
              <select
                className="input"
                value={c.prefs.archetypeId ?? profile.archetypes[0]!.id}
                onChange={(e) => void c.updateSettings({ coach: { archetypeByHero: { ...c.settings.coach.archetypeByHero, [String(hero.id)]: e.target.value } } })}
              >
                {profile.archetypes.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.label}
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>
      </Section>
    );
  }
  return (
    <Section title="Pick your hero">
      <SearchPicker
        inputId="hero-search"
        grid
        options={options}
        recent={recent}
        placeholder="Search heroes (type, ↑/↓, Enter)"
        max={60}
        onPick={(id) => {
          send(input.hero(Number(id)));
          pushRecent(id);
          setChanging(false);
        }}
      />
    </Section>
  );
}

export function SoulsSection({ c }: { c: Coach }) {
  const { state, now, input, send } = c;
  const r = readField(state, "me.souls", now);
  const [text, setText] = useState("");
  const souls = r?.value ?? null;
  const commit = (v: number) => send(input.souls(v));
  return (
    <Section title="Souls (unspent)" right={r ? <span className={`fresh fresh-${r.freshness}`}>{r.freshness === "fresh" ? "just entered" : `${Math.round(r.ageS / 60)} min ago`}</span> : <Badge tone="warn">not entered</Badge>}>
      <div className="souls-row">
        <input
          className="input souls-input"
          inputMode="numeric"
          placeholder={souls != null ? String(souls) : "e.g. 2400"}
          value={text}
          aria-label="Unspent souls"
          onChange={(e) => setText(e.target.value.replace(/[^\d]/g, ""))}
          onKeyDown={(e) => {
            if (e.key === "Enter" && text) {
              commit(Number(text));
              setText("");
            }
          }}
        />
        <button type="button" className="btn" disabled={!text} onClick={() => (commit(Number(text)), setText(""))}>
          Set
        </button>
        {[-800, +500, +1000].map((d) => (
          <button key={d} type="button" className="btn ghost small" disabled={souls == null} onClick={() => souls != null && commit(Math.max(0, souls + d))}>
            {d > 0 ? `+${d}` : d}
          </button>
        ))}
      </div>
    </Section>
  );
}

export function ItemsSection({ c }: { c: Coach }) {
  const { deps, state, now, input, send } = c;
  const owned = (readField(state, "me.items", now)?.value as string[] | undefined) ?? [];
  const [recent, pushRecent] = useRecent("cc.recentItems");
  const [slot, setSlot] = useState<"all" | "weapon" | "vitality" | "spirit">("all");
  const options = useMemo(() => itemOptions(deps.data.items()).filter((o) => slot === "all" || o.tone === slot), [deps, slot]);
  const total = deps.rules.inventory.value.totalSlots;
  const add = (cn: string) => {
    const it = deps.data.item(cn);
    if (!it) return;
    // Buying an upgrade replaces its owned components (community rule; labelled in docs).
    const next = owned.filter((o) => !(deps.rules.componentUpgrade.value.consumesComponent && it.components.includes(o)));
    if (!next.includes(cn)) next.push(cn);
    send(input.items(next));
    pushRecent(cn);
  };
  return (
    <Section title={`Your items (${owned.length}/${total})`} right={<span className="muted small">/ to search</span>}>
      <div className="chips">
        {owned.map((cn) => {
          const it = deps.data.item(cn);
          return <Chip key={cn} label={it?.name ?? cn} image={it?.image} tone={it ? SLOT_TONE[it.slot] : "neutral"} onRemove={() => send(input.items(owned.filter((o) => o !== cn)))} />;
        })}
        {!owned.length && <span className="muted small">No items yet. Starting items count too.</span>}
      </div>
      <div className="seg small-seg" role="group" aria-label="Item category">
        {(["all", "weapon", "vitality", "spirit"] as const).map((s) => (
          <button key={s} type="button" className={`seg-btn ${slot === s ? "on" : ""} tone-${s}`} onClick={() => setSlot(s)}>
            {s}
          </button>
        ))}
      </div>
      <SearchPicker inputId="item-search" grid options={options.filter((o) => !owned.includes(o.id))} recent={recent} placeholder="Add an item you own" onPick={add} max={48} />
    </Section>
  );
}

export function AbilitySection({ c }: { c: Coach }) {
  const { deps, state, now, input, send } = c;
  const heroId = readField(state, "me.hero", now)?.value as number | undefined;
  const hero = heroId != null ? deps.data.hero(heroId) : undefined;
  const st: AbilityState = (readField(state, "me.abilities", now)?.value as AbilityState | undefined) ?? { unlocked: [], tiers: {} };
  const points = readField(state, "me.unspentPoints", now)?.value as number | undefined;
  const unlocks = readField(state, "me.unspentUnlocks", now)?.value as number | undefined;
  if (!hero) return null;
  const setTier = (cn: string, t: number) => {
    const unlocked = t >= 0 ? [...new Set([...st.unlocked, cn])] : st.unlocked.filter((u) => u !== cn);
    const tiers = { ...st.tiers };
    if (t >= 0) tiers[cn] = t;
    else delete tiers[cn];
    send(input.abilities({ unlocked, tiers }));
  };
  const costs = deps.data.snapshot.abilityCosts.tierCosts;
  return (
    <Section title="Abilities" right={<span className="muted small">tier costs {costs.join("/")} pts (from data)</span>}>
      <div className="abilities">
        {hero.abilities.map((a) => {
          const unlocked = st.unlocked.includes(a.className);
          const tier = unlocked ? (st.tiers[a.className] ?? 0) : -1;
          return (
            <div key={a.className} className={`ability-row ${unlocked ? "" : "locked"}`}>
              <Icon src={a.image} name={a.name} size={30} />
              <span className="ability-name">
                {a.name}
                {a.isUltimate && <span className="muted small"> (ult)</span>}
              </span>
              <div className="pips" role="group" aria-label={`${a.name} tier`}>
                <button type="button" className={`pip lock ${tier < 0 ? "on" : ""}`} onClick={() => setTier(a.className, -1)} title="Locked">
                  ✕
                </button>
                {[0, 1, 2, 3].map((t) => (
                  <button key={t} type="button" className={`pip ${tier >= t && tier >= 0 ? "on" : ""}`} onClick={() => setTier(a.className, t)} title={t === 0 ? "Unlocked (no upgrades)" : `Tier ${t}`}>
                    {t === 0 ? "U" : t}
                  </button>
                ))}
              </div>
            </div>
          );
        })}
      </div>
      <div className="row gap">
        <Stepper label="Unspent points" value={points} onChange={(v) => send(input.unspentPoints(v))} />
        <Stepper label="Unspent unlocks" value={unlocks} onChange={(v) => send(input.unspentUnlocks(v))} />
      </div>
    </Section>
  );
}

function Stepper({ label, value, onChange }: { label: string; value: number | undefined; onChange: (v: number) => void }) {
  return (
    <div className="stepper">
      <span className="small">{label}</span>
      <button type="button" className="btn ghost small" onClick={() => onChange(Math.max(0, (value ?? 0) - 1))} aria-label={`Decrease ${label}`}>
        −
      </button>
      <span className={`stepper-v ${value == null ? "muted" : ""}`}>{value ?? "?"}</span>
      <button type="button" className="btn ghost small" onClick={() => onChange((value ?? 0) + 1)} aria-label={`Increase ${label}`}>
        +
      </button>
    </div>
  );
}

export function RosterSection({ c }: { c: Coach }) {
  const { deps, state, now, input, send } = c;
  const enemies = (readField(state, "roster.enemies", now)?.value as number[] | undefined) ?? [];
  const allies = (readField(state, "roster.allies", now)?.value as number[] | undefined) ?? [];
  const lane = (readField(state, "roster.lane", now)?.value as number[] | undefined) ?? [];
  const me = readField(state, "me.hero", now)?.value as number | undefined;
  const options = (exclude: number[]) =>
    deps.data
      .playableHeroes()
      .filter((h) => !exclude.includes(h.id))
      .map((h) => ({ id: String(h.id), label: h.name, image: h.image, tone: "hero" as const }));
  const [openEnemy, setOpenEnemy] = useState<number | null>(null);
  return (
    <Section title="Teams">
      <div className="roster-block">
        <div className="roster-title">
          Enemies <span className="muted small">({enemies.length}) · click a hero to add items you can see · ◎ marks lane opponents</span>
        </div>
        <div className="chips">
          {enemies.map((id) => {
            const h = deps.data.hero(id);
            const items = (readField(state, `enemyItems:${id}`, now)?.value as EnemyItemsValue | undefined)?.items ?? [];
            return (
              <Chip
                key={id}
                label={h?.name ?? String(id)}
                image={h?.image}
                tone="hero"
                round
                extra={
                  <>
                    <button type="button" className={`chip-btn ${lane.includes(id) ? "on" : ""}`} title="Lane opponent" onClick={() => send(input.lane(lane.includes(id) ? lane.filter((x) => x !== id) : [...lane, id]))}>
                      ◎
                    </button>
                    <button type="button" className={`chip-btn ${openEnemy === id ? "on" : ""}`} title="Visible items" onClick={() => setOpenEnemy(openEnemy === id ? null : id)}>
                      {items.length ? `${items.length} items` : "items"}
                    </button>
                  </>
                }
                onRemove={() => send(input.enemies(enemies.filter((x) => x !== id)))}
              />
            );
          })}
        </div>
        {openEnemy != null && enemies.includes(openEnemy) && <EnemyItems c={c} heroId={openEnemy} />}
        {enemies.length < 6 && <SearchPicker options={options([...enemies, ...allies, ...(me != null ? [me] : [])])} placeholder="Add enemy hero" onPick={(id) => send(input.enemies([...enemies, Number(id)]))} max={8} />}
      </div>
      <div className="roster-block">
        <div className="roster-title">
          Allies <span className="muted small">({allies.length})</span>
        </div>
        <div className="chips">
          {allies.map((id) => {
            const h = deps.data.hero(id);
            return <Chip key={id} label={h?.name ?? String(id)} image={h?.image} tone="hero" round onRemove={() => send(input.allies(allies.filter((x) => x !== id)))} />;
          })}
        </div>
        {allies.length < 5 && <SearchPicker options={options([...enemies, ...allies, ...(me != null ? [me] : [])])} placeholder="Add ally hero (your 5 teammates)" onPick={(id) => send(input.allies([...allies, Number(id)]))} max={8} />}
      </div>
    </Section>
  );
}

function EnemyItems({ c, heroId }: { c: Coach; heroId: number }) {
  const { deps, state, now, input, send } = c;
  const v = (readField(state, `enemyItems:${heroId}`, now)?.value as EnemyItemsValue | undefined) ?? { items: [], complete: false };
  const options = useMemo(() => itemOptions(deps.data.items()), [deps]);
  const set = (items: string[], complete = v.complete) => send(input.enemyItems(heroId, items, complete));
  return (
    <div className="enemy-items">
      <div className="chips">
        {v.items.map((cn) => {
          const it = deps.data.item(cn);
          return <Chip key={cn} label={it?.name ?? cn} image={it?.image} tone={it ? SLOT_TONE[it.slot] : "neutral"} onRemove={() => set(v.items.filter((x) => x !== cn))} />;
        })}
      </div>
      <label className="check small">
        <input type="checkbox" checked={v.complete} onChange={(e) => set(v.items, e.target.checked)} /> I saw their full inventory (unseen items otherwise stay unknown, not "none")
      </label>
      <SearchPicker grid options={options.filter((o) => !v.items.includes(o.id))} placeholder={`Add an item seen on ${deps.data.hero(heroId)?.name}`} onPick={(id) => set([...v.items, id])} max={24} />
    </div>
  );
}

const REPORTABLE: ThreatKind[] = ["spirit_burst", "weapon_burst", "spirit_damage", "weapon_damage", "melee_damage", "enemy_healing", "hard_cc", "silence", "slow_kite", "debuffs", "mobility_escape", "channel_ultimate", "tanky_targets"];

export function ThreatSection({ c }: { c: Coach }) {
  const { deps, state, now, input, send } = c;
  const enemies = (readField(state, "roster.enemies", now)?.value as number[] | undefined) ?? [];
  const [severity, setSeverity] = useState<"minor" | "major">("major");
  const [source, setSource] = useState<number | null>(null);
  const recap = readField(state, "me.deathRecap", now);
  const [w, setW] = useState(""), [s, setS] = useState(""), [m, setM] = useState("");
  return (
    <Section title="What is hurting you?">
      <div className="row gap wrap">
        <div className="seg" role="group" aria-label="Severity">
          {(["minor", "major"] as const).map((x) => (
            <button key={x} type="button" className={`seg-btn ${severity === x ? "on" : ""}`} onClick={() => setSeverity(x)}>
              {x}
            </button>
          ))}
        </div>
        <select className="input" value={source ?? ""} onChange={(e) => setSource(e.target.value ? Number(e.target.value) : null)} aria-label="Source hero">
          <option value="">any enemy</option>
          {enemies.map((id) => (
            <option key={id} value={id}>
              {deps.data.hero(id)?.name}
            </option>
          ))}
        </select>
      </div>
      <div className="threat-buttons">
        {REPORTABLE.map((k) => (
          <button key={k} type="button" className="btn ghost small" onClick={() => send(input.threat(k, severity, source))}>
            {THREAT_SHORT[k]}
          </button>
        ))}
      </div>
      {state.threatReports.length > 0 && (
        <div className="chips">
          {state.threatReports.map((t) => (
            <Chip key={t.id} label={`${THREAT_SHORT[t.kind]} · ${t.severity}${t.sourceHeroId ? ` · ${deps.data.hero(t.sourceHeroId)?.name}` : ""}`} onRemove={() => send({ type: "threat.clear", id: t.id, at: Date.now() })} />
          ))}
        </div>
      )}
      <div className="recap">
        <span className="small">Last death recap %</span>
        <input className="input narrow" placeholder="gun" value={w} onChange={(e) => setW(e.target.value.replace(/\D/g, ""))} aria-label="Weapon damage percent" />
        <input className="input narrow" placeholder="spirit" value={s} onChange={(e) => setS(e.target.value.replace(/\D/g, ""))} aria-label="Spirit damage percent" />
        <input className="input narrow" placeholder="melee" value={m} onChange={(e) => setM(e.target.value.replace(/\D/g, ""))} aria-label="Melee damage percent" />
        <button
          type="button"
          className="btn small"
          disabled={!w && !s && !m}
          onClick={() => {
            send(input.deathRecap({ weaponPct: Number(w || 0), spiritPct: Number(s || 0), meleePct: Number(m || 0), killerHeroIds: source != null ? [source] : [] }));
            setW("");
            setS("");
            setM("");
          }}
        >
          Save
        </button>
        {recap && (
          <span className="muted small">
            saved {Math.round(recap.ageS / 60)} min ago: {recap.value.weaponPct}/{recap.value.spiritPct}/{recap.value.meleePct}
          </span>
        )}
      </div>
    </Section>
  );
}
