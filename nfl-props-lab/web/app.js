// NFL Props Lab web app. Works against the FastAPI server (/api/...) or, when a
// snapshot is embedded (window.NPL_SNAPSHOT), entirely offline from that snapshot.
// Every number shown is read from a saved edition, lake table or backtest summary.

const SNAP = window.NPL_SNAPSHOT || null;
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const app = document.getElementById("app");
const cache = new Map();

async function api(path) {
  if (cache.has(path)) return cache.get(path);
  let data;
  if (SNAP) {
    if (!(path in SNAP.data)) throw new Error(`Not included in this snapshot: ${path}`);
    data = SNAP.data[path];
  } else {
    const r = await fetch(path);
    if (!r.ok) throw new Error(`${r.status} ${r.statusText} for ${path}`);
    data = await r.json();
  }
  cache.set(path, data);
  return data;
}

// ---------------------------------------------------------------- helpers
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const pct = (p) => (p == null || isNaN(p) ? "–" : p < 0.005 ? "<1%" : p > 0.995 ? ">99%" : `${Math.round(p * 100)}%`);
const pct1 = (p) => (p == null || isNaN(p) ? "–" : `${(p * 100).toFixed(1)}%`);
const dec = (x, n = 1) => (x == null || isNaN(x) ? "–" : Number(x).toFixed(n));
const sgn = (x, n = 3) => (x == null || isNaN(x) ? "–" : (x > 0 ? "+" : "") + Number(x).toFixed(n));
const MODEL_NAMES = { X1: "Stacked J1 + ML (live headline)", X: "Stacked J0 + ML (primary)", J1: "Joint sim + injury report", J0: "Joint sim (history only)", M: "Gradient boosting", A: "Baseline A: game log", C: "Baseline C: role climatology" };
const MARKET_LABELS = { pass_yds: "Passing yards", pass_att: "Pass attempts", pass_cmp: "Completions", rush_yds: "Rushing yards", rush_att: "Rushing attempts", receptions: "Receptions", rec_yds: "Receiving yards" };
const MARKET_STAT = { pass_yds: "passing_yards", pass_att: "attempts", pass_cmp: "completions", rush_yds: "rushing_yards", rush_att: "carries", receptions: "receptions", rec_yds: "receiving_yards" };
const MODEL_ORDER = ["X1", "X", "J1", "J0", "M", "A", "C"];
const store = {
  get(k, d) { try { const v = localStorage.getItem("npl:" + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem("npl:" + k, JSON.stringify(v)); } catch { /* storage unavailable */ } },
};
function londonTime(iso) {
  if (!iso) return "–";
  return new Intl.DateTimeFormat("en-GB", { timeZone: "Europe/London", weekday: "short", day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit", timeZoneName: "short" }).format(new Date(iso));
}

// Discrete distribution: mirrors npl/dist.py so manual lines give identical answers.
class Dist {
  constructor(d) { this.lo = d.lo; this.c = d.counts; this.n = this.c.reduce((a, b) => a + b, 0);
    this.cum = []; let s = 0; for (const v of this.c) { s += v; this.cum.push(s); } }
  pge(k) { const i = Math.ceil(k - 1e-9) - this.lo; if (i <= 0) return 1; if (i >= this.c.length) return 0; return (this.n - this.cum[i - 1]) / this.n; }
  peq(k) { const i = k - this.lo; return i < 0 || i >= this.c.length ? 0 : this.c[i] / this.n; }
  line(L) { if (Number.isInteger(L)) { const push = this.peq(L), over = this.pge(L + 1); return { over, under: Math.max(1 - over - push, 0), push }; }
    const over = this.pge(L); return { over, under: 1 - over, push: 0 }; }
  q(p) { const t = p * this.n - 1e-9; const i = this.cum.findIndex((v) => v >= t); return this.lo + (i < 0 ? this.c.length - 1 : i); }
}
const fair = (pw, pp = 0) => (pw > 0 ? (pw + (1 - pw - pp)) / pw : null);

// ---------------------------------------------------------------- tooltip (hover + focus)
const tip = document.createElement("div"); tip.id = "tip"; tip.hidden = true; document.body.appendChild(tip);
function showTip(el, x, y) {
  const t = el.getAttribute("data-tip"); if (!t) return;
  tip.textContent = ""; t.split("|").forEach((line, i) => { const d = document.createElement(i ? "div" : "b"); d.textContent = line; tip.appendChild(d); if (!i) tip.appendChild(document.createElement("br")); });
  tip.hidden = false; const w = tip.offsetWidth, h = tip.offsetHeight;
  tip.style.left = Math.min(window.innerWidth - w - 8, Math.max(8, x + 12)) + "px";
  tip.style.top = Math.max(8, y - h - 12) + "px";
}
document.addEventListener("pointermove", (e) => { const el = e.target.closest?.("[data-tip]"); if (el) showTip(el, e.clientX, e.clientY); else tip.hidden = true; });
document.addEventListener("focusin", (e) => { const el = e.target.closest?.("[data-tip]"); if (el) { const r = el.getBoundingClientRect(); showTip(el, r.left + r.width / 2, r.top); } });
document.addEventListener("focusout", () => { tip.hidden = true; });

// ---------------------------------------------------------------- charts (SVG strings)
function niceTicks(lo, hi, n = 5) {
  const span = hi - lo || 1; const step0 = span / n; const mag = 10 ** Math.floor(Math.log10(step0));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((s) => s >= step0) || step0;
  const out = []; for (let v = Math.ceil(lo / step) * step; v <= hi + 1e-9; v += step) out.push(+v.toFixed(6)); return out;
}

function distChart(dist, { line = null, unit = "", width = 640, height = 190 } = {}) {
  const D = dist instanceof Dist ? dist : new Dist(dist);
  const lo = D.q(0.002), hi = D.q(0.998);
  const span = hi - lo + 1; const bw = Math.max(1, Math.ceil(span / 36));
  const bins = []; for (let a = Math.floor(lo / bw) * bw; a <= hi; a += bw) { let p = 0; for (let v = a; v < a + bw; v++) p += D.peq(v); bins.push({ a, b: a + bw - 1, p }); }
  const m = { l: 34, r: 8, t: 14, b: 26 }, W = width - m.l - m.r, H = height - m.t - m.b;
  const pmax = Math.max(...bins.map((b) => b.p)) * 1.12 || 1;
  const x0 = bins[0].a, x1 = bins[bins.length - 1].b + 1;
  const X = (v) => m.l + ((v - x0) / (x1 - x0)) * W, Y = (p) => m.t + H - (p / pmax) * H;
  const slot = W / bins.length, thick = Math.min(24, Math.max(2, slot - 2));
  let s = `<svg class="chart" style="max-width:${width + 120}px" viewBox="0 0 ${width} ${height}" role="img" aria-label="Predictive distribution${line != null ? `, line ${line}` : ""}">`;
  s += `<g class="grid">${niceTicks(0, pmax, 4).map((t) => `<line x1="${m.l}" x2="${width - m.r}" y1="${Y(t)}" y2="${Y(t)}"/><text x="${m.l - 6}" y="${Y(t) + 4}" text-anchor="end">${Math.round(t * 100)}%</text>`).join("")}</g>`;
  bins.forEach((b, i) => {
    const cx = m.l + slot * (i + 0.5), h = Math.max(0, m.t + H - Y(b.p));
    const over = line != null && b.a > line; const lab = bw === 1 ? `${b.a}${unit}` : `${b.a} to ${b.b}${unit}`;
    const r = Math.min(4, thick / 2, h);
    s += `<path class="bar${line != null && !over ? " soft" : ""}" d="M${cx - thick / 2},${m.t + H} v${-(h - r)} q0,${-r} ${r},${-r} h${thick - 2 * r} q${r},0 ${r},${r} v${h - r} z" data-tip="${esc(pct1(b.p))}|${esc(lab)}" ${i % 3 === 0 ? 'tabindex="0"' : ""}/>`;
  });
  s += `<line class="axis" x1="${m.l}" x2="${width - m.r}" y1="${m.t + H}" y2="${m.t + H}"/>`;
  niceTicks(x0, x1 - 1, 6).forEach((t) => { s += `<text x="${X(t + 0.5)}" y="${height - 8}" text-anchor="middle">${t}</text>`; });
  if (line != null && line >= x0 - 1 && line <= x1 + 1) {
    const lx = X(line + 0.5);
    s += `<line class="ltg" x1="${lx}" x2="${lx}" y1="${m.t - 4}" y2="${m.t + H}"/><text class="ltgl" x="${lx + 5}" y="${m.t + 6}">${esc(line)}</text>`;
  }
  return s + "</svg>";
}

function rangeBar(c, width = 300) {
  const lo = Math.min(c.q05, 0), hi = Math.max(c.q95, 1) * 1.04;
  const X = (v) => 6 + ((v - lo) / (hi - lo)) * (width - 12);
  return `<svg class="chart" viewBox="0 0 ${width} 26" role="img" aria-label="Range ${c.q05} to ${c.q95}, median ${c.median}">
    <line class="axis" x1="${X(c.q05)}" x2="${X(c.q95)}" y1="13" y2="13"/>
    <rect class="bar soft" x="${X(c.q10)}" y="8" width="${Math.max(2, X(c.q90) - X(c.q10))}" height="10" rx="3"/>
    <rect class="bar" x="${X(c.q25)}" y="6" width="${Math.max(2, X(c.q75) - X(c.q25))}" height="14" rx="3"/>
    <line x1="${X(c.median)}" x2="${X(c.median)}" y1="3" y2="23" stroke="var(--ink)" stroke-width="2"/>
  </svg>`;
}

function columnChart(points, { width = 640, height = 170, label = "" } = {}) {
  // points: [{x: label, v: number|null, note: string, tip: string}]
  const m = { l: 30, r: 6, t: 12, b: 30 }, W = width - m.l - m.r, H = height - m.t - m.b;
  const vmax = Math.max(1, ...points.map((p) => p.v ?? 0)) * 1.12;
  const Y = (v) => m.t + H - (v / vmax) * H; const slot = W / Math.max(points.length, 1); const thick = Math.min(24, slot - 3);
  let s = `<svg class="chart" style="max-width:${width + 160}px" viewBox="0 0 ${width} ${height}" role="img" aria-label="${esc(label)}"><g class="grid">${niceTicks(0, vmax, 4).map((t) => `<line x1="${m.l}" x2="${width - m.r}" y1="${Y(t)}" y2="${Y(t)}"/><text x="${m.l - 5}" y="${Y(t) + 4}" text-anchor="end">${t}</text>`).join("")}</g>`;
  points.forEach((p, i) => {
    const cx = m.l + slot * (i + 0.5);
    if (p.v == null) { s += `<text x="${cx}" y="${m.t + H - 4}" text-anchor="middle" class="lab" data-tip="${esc(p.tip)}">${esc(p.note || "–")}</text>`; }
    else { const h = Math.max(1, m.t + H - Y(p.v)); const r = Math.min(4, thick / 2, h);
      s += `<path class="bar" d="M${cx - thick / 2},${m.t + H} v${-(h - r)} q0,${-r} ${r},${-r} h${thick - 2 * r} q${r},0 ${r},${r} v${h - r} z" data-tip="${esc(p.tip)}" tabindex="0"/>`; }
    if (points.length <= 24 || i % 2 === 0) s += `<text x="${cx}" y="${height - 12}" text-anchor="middle">${esc(p.x)}</text>`;
  });
  return s + `<line class="axis" x1="${m.l}" x2="${width - m.r}" y1="${m.t + H}" y2="${m.t + H}"/></svg>`;
}

function pitChart(deciles, color = "var(--bar)") {
  const width = 360, height = 150, m = { l: 34, r: 6, t: 10, b: 24 }, W = width - m.l - m.r, H = height - m.t - m.b;
  const vmax = Math.max(0.16, ...deciles) * 1.1; const Y = (v) => m.t + H - (v / vmax) * H; const slot = W / deciles.length; const th = Math.min(24, slot - 3);
  let s = `<svg class="chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="PIT histogram"><g class="grid">${niceTicks(0, vmax, 3).map((t) => `<line x1="${m.l}" x2="${width - m.r}" y1="${Y(t)}" y2="${Y(t)}"/><text x="${m.l - 5}" y="${Y(t) + 4}" text-anchor="end">${Math.round(t * 100)}%</text>`).join("")}</g>`;
  deciles.forEach((v, i) => { const cx = m.l + slot * (i + 0.5), h = m.t + H - Y(v), r = Math.min(4, th / 2, h);
    s += `<path d="M${cx - th / 2},${m.t + H} v${-(h - r)} q0,${-r} ${r},${-r} h${th - 2 * r} q${r},0 ${r},${r} v${h - r} z" fill="${color}" data-tip="${pct1(v)}|PIT ${i / 10}–${(i + 1) / 10}" tabindex="0"/>`; });
  s += `<line x1="${m.l}" x2="${width - m.r}" y1="${Y(0.1)}" y2="${Y(0.1)}" stroke="var(--ink)" stroke-width="1"/><text class="lab" x="${width - m.r}" y="${Y(0.1) - 4}" text-anchor="end">10% = calibrated</text>`;
  s += `<line class="axis" x1="${m.l}" x2="${width - m.r}" y1="${m.t + H}" y2="${m.t + H}"/><text x="${m.l}" y="${height - 6}">outcome low in forecast</text><text x="${width - m.r}" y="${height - 6}" text-anchor="end">outcome high</text></svg>`;
  return s;
}

function reliabilityChart(rows, color = "var(--bar)") {
  const width = 300, height = 260, m = { l: 36, r: 10, t: 10, b: 32 }, W = width - m.l - m.r, H = height - m.t - m.b;
  const X = (v) => m.l + v * W, Y = (v) => m.t + H - v * H;
  let s = `<svg class="chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="Reliability diagram"><g class="grid">${[0, 0.25, 0.5, 0.75, 1].map((t) => `<line x1="${m.l}" x2="${width - m.r}" y1="${Y(t)}" y2="${Y(t)}"/><text x="${m.l - 5}" y="${Y(t) + 4}" text-anchor="end">${t * 100}%</text><text x="${X(t)}" y="${height - 16}" text-anchor="middle">${t * 100}%</text>`).join("")}</g>`;
  s += `<line class="diag" x1="${X(0)}" y1="${Y(0)}" x2="${X(1)}" y2="${Y(1)}"/>`;
  rows.filter((r) => r.bin !== "ALL" && r.n >= 20).forEach((r) => { const rad = Math.max(4, Math.min(9, Math.sqrt(r.n) / 4));
    s += `<circle cx="${X(r.mean_p)}" cy="${Y(r.freq)}" r="${rad}" fill="${color}" stroke="var(--card)" stroke-width="2" data-tip="${pct1(r.freq)} observed|forecast ${pct1(r.mean_p)} · n=${r.n}" tabindex="0"/>`; });
  return s + `<text x="${m.l + W / 2}" y="${height - 2}" text-anchor="middle">forecast probability</text></svg>`;
}

function skillChart(models) {
  // models: [{m, d, lo, hi}] CRPS difference vs baseline (negative = better)
  const width = 520, rowH = 30, m = { l: 150, r: 16, t: 18, b: 22 }, height = m.t + m.b + rowH * models.length;
  const vals = models.flatMap((x) => [x.lo, x.hi, x.d]).filter((v) => v != null);
  let lo = Math.min(0, ...vals), hi = Math.max(0, ...vals); const pad = (hi - lo) * 0.12 || 0.01; lo -= pad; hi += pad;
  const X = (v) => m.l + ((v - lo) / (hi - lo)) * (width - m.l - m.r);
  let s = `<svg class="chart" style="max-width:${width}px" viewBox="0 0 ${width} ${height}" role="img" aria-label="CRPS difference versus baseline with 95% intervals"><g class="grid">${niceTicks(lo, hi, 5).map((t) => `<line x1="${X(t)}" x2="${X(t)}" y1="${m.t - 6}" y2="${height - m.b}"/><text x="${X(t)}" y="${height - 6}" text-anchor="middle">${+t.toFixed(3)}</text>`).join("")}</g>`;
  s += `<line x1="${X(0)}" x2="${X(0)}" y1="${m.t - 8}" y2="${height - m.b}" stroke="var(--ink)" stroke-width="1"/><text class="lab" x="${X(0) + 4}" y="${m.t - 8}">baseline</text>`;
  models.forEach((x, i) => { const y = m.t + rowH * (i + 0.5);
    s += `<text class="lab" x="${m.l - 10}" y="${y + 4}" text-anchor="end">${esc(x.m)} ${esc(MODEL_NAMES[x.m]?.split(":")[0] ?? "")}</text>`;
    if (x.lo != null) s += `<line x1="${X(x.lo)}" x2="${X(x.hi)}" y1="${y}" y2="${y}" stroke="var(--m-${x.m})" stroke-width="2" stroke-linecap="round"/>`;
    s += `<circle cx="${X(x.d)}" cy="${y}" r="5" fill="var(--m-${x.m})" stroke="var(--card)" stroke-width="2" data-tip="${sgn(x.d)} CRPS vs baseline|95% CI ${sgn(x.lo)} to ${sgn(x.hi)}" tabindex="0"/>`; });
  return s + `<text x="${m.l}" y="${height - 6 - 12}" class="tiny"></text></svg>`;
}

// ---------------------------------------------------------------- shell
const ICONS = {
  today: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/></svg>',
  props: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/></svg>',
  shortlist: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/></svg>',
  lab: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 3h6M10 3v6l-6 10a1.5 1.5 0 0 0 1.3 2h13.4a1.5 1.5 0 0 0 1.3-2L14 9V3"/></svg>',
  record: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 8v4l3 2"/><circle cx="12" cy="12" r="9"/></svg>',
  data: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/></svg>',
  more: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/></svg>',
  warn: '<svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3 2 20h20L12 3zM12 10v4M12 17h.01"/></svg>',
  info: '<svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/></svg>',
  ok: '<svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/></svg>',
};
const NAV = [["today", "Today"], ["props", "Props"], ["shortlist", "Shortlist"], ["lab", "Backtest lab"], ["record", "Saved record"], ["data", "Data & method"]];

function shell() {
  const theme = store.get("theme", "auto");
  if (theme !== "auto") document.documentElement.setAttribute("data-theme", theme);
  document.body.insertAdjacentHTML("afterbegin", `
    <header class="topbar"><div class="topbar-in">
      <a class="brand" href="#today"><span class="yard" aria-hidden="true"></span><b>NFL Props Lab</b></a>
      <span class="spacer"></span>
      <span class="tiny muted" id="modeTag">${SNAP ? "Snapshot " + esc(SNAP.built_london) : "Live server"}</span>
      <button class="chipbtn" id="themeBtn" aria-label="Colour theme">Theme: ${esc(theme)}</button>
    </div></header>`);
  app.innerHTML = `<div class="layout"><nav class="tabs" aria-label="Sections">${NAV.map(([k, l], i) =>
    `<a href="#${k}" data-k="${k}" class="${i > 3 ? "more-only" : ""}">${ICONS[k]}<span>${l}</span></a>`).join("")}
    <a href="#more" data-k="more" class="phone-only">${ICONS.more}<span>More</span></a></nav><main id="view"></main></div>`;
  $("#themeBtn").addEventListener("click", () => {
    const order = ["auto", "light", "dark"]; const next = order[(order.indexOf(store.get("theme", "auto")) + 1) % 3];
    store.set("theme", next); if (next === "auto") document.documentElement.removeAttribute("data-theme"); else document.documentElement.setAttribute("data-theme", next);
    $("#themeBtn").textContent = "Theme: " + next;
  });
}

function route() {
  const h = decodeURIComponent(location.hash.replace(/^#/, "")) || "today";
  const [k, ...rest] = h.split(".");
  const arg = rest.join(".");
  $$("nav.tabs a").forEach((a) => a.classList.toggle("on", a.dataset.k === k || (k === "more" && a.dataset.k === "more")));
  const view = $("#view");
  view.innerHTML = `<div class="skeleton">Loading…</div>`;
  const fn = { today: viewToday, props: viewProps, shortlist: viewShortlist, lab: viewLab, record: viewRecord, data: viewData, player: viewPlayer, game: viewGame, more: viewMore }[k] || viewToday;
  Promise.resolve(fn(view, arg)).catch((e) => { view.innerHTML = `<div class="banner crit">${ICONS.warn}<div><p><b>Could not load this view.</b></p><p class="small">${esc(e.message)}</p><p class="small">If the server is running, check <code>npl serve</code> output; otherwise this snapshot may not include the page.</p></div></div>`; });
  window.scrollTo(0, 0);
}
window.addEventListener("hashchange", route);

function banner(kind, html) { return `<div class="banner ${kind}">${ICONS[kind === "crit" || kind === "warn" ? "warn" : kind === "ok" ? "ok" : "info"]}<div>${html}</div></div>`; }
function injuryPill(s) { if (!s) return ""; const c = s.toLowerCase(); return `<span class="pill ${c}"><span class="dot"></span>${esc(s)}</span>`; }
function partPill(p) {
  const map = { played: ["good", "Played"], inactive: ["out", "Inactive"], dnp: ["doubtful", "Did not play"], reserve: ["", "Reserve list"], active_unverified: ["questionable", "Active, no stat (participation unverified)"], not_final: ["", "Not final"] };
  const [c, l] = map[p] || ["", p]; return `<span class="pill ${c}"><span class="dot"></span>${esc(l)}</span>`;
}
function copyBtn(id, text) { return `<button class="btn ghost" data-copy="${id}">Copy ${esc(text)}</button>`; }
document.addEventListener("click", async (e) => {
  const b = e.target.closest("[data-copy]"); if (!b) return;
  const payload = window.__copy?.[b.dataset.copy]; if (!payload) return;
  try { await navigator.clipboard.writeText(payload); b.textContent = "Copied"; }
  catch { const ta = document.createElement("textarea"); ta.value = payload; document.body.appendChild(ta); ta.select(); b.textContent = "Select and copy the highlighted text"; }
});
function setCopy(id, text) { window.__copy = window.__copy || {}; window.__copy[id] = text; }
function toCSV(rows) { if (!rows.length) return ""; const keys = [...new Set(rows.flatMap((r) => Object.keys(r)))]; const q = (v) => { const s = v == null ? "" : typeof v === "object" ? JSON.stringify(v) : String(v); return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s; }; return [keys.join(","), ...rows.map((r) => keys.map((k) => q(r[k])).join(","))].join("\n"); }
function exportLinks(path, label) { return SNAP ? copyBtn(path, label + " (CSV)") : `<a class="btn ghost" href="/api/export/${path}.csv">${esc(label)} CSV</a> <a class="btn ghost" href="/api/export/${path}.json">JSON</a>`; }

// ---------------------------------------------------------------- Today
async function viewToday(v) {
  const [s, st] = await Promise.all([api("/api/slate"), api("/api/status")]);
  if (!s.edition) { v.innerHTML = banner("warn", "<p>No edition has been published yet. Run <code>npl publish</code>.</p>"); return; }
  const e = s.edition; const before = s.games.filter((g) => g.published_before_kickoff).length;
  const odds = st.odds || {};
  const stale = (st.stale || []).length, failed = (st.failed_jobs || []).length;
  v.innerHTML = `<section class="hero fade"><span class="eyebrow">${e.season} ${e.season_type === "REG" ? "regular season" : "postseason"} · Week ${e.week}</span>
      <h1>This week's slate</h1>
      <p>Edition <span class="mono">${esc(e.edition_id)}</span>, published ${esc(e.published_at_london)}. ${before} of ${s.games.length} games were still to kick off when it was saved, so only those count in the pre-match record.</p></section>
    ${banner("info", `<p><b>Model v${esc(e.code.model_version)}.</b> ${esc(e.status_note)}</p><p class="small muted">Headline model ${esc(e.headline_model)}: ${esc(MODEL_NAMES[e.headline_model])}. Code ${esc(e.code.git_commit.slice(0, 10))}.</p>`)}
    ${odds.key_configured ? "" : banner("warn", `<p><b>No verified bookmaker prices.</b> No odds key is configured, so there is no value, ROI or "bet" anywhere in the app. Lines you type are labelled <span class="pill manual">manual line — no verified price</span>.</p>`)}
    ${stale || failed ? banner("crit", `<p><b>Data attention needed:</b> ${failed} failed or partial job(s), ${stale} feed(s) not checked for over 30 hours. See <a href="#data">Data &amp; method</a>.</p>`) : banner("ok", `<p>All feeds were checked within the last 30 hours and the recent jobs completed. Freshness details are under <a href="#data">Data &amp; method</a>.</p>`)}
    <div class="grid">${s.games.map(gameCard).join("")}</div>`;
}
function volLine(sim, label) {
  if (!sim) return `<div><span class="muted">${label}</span> –</div>`;
  const a = sim.attempts, p = sim.plays;
  return `<div><span class="eyebrow">${esc(label)}</span><div class="num">${dec(p.mean, 0)} plays · ${dec(a.mean, 1)} pass att <span class="muted">(${a.q10}–${a.q90})</span></div></div>`;
}
function gameCard(g) {
  const st = g.published_before_kickoff ? `<span class="pill good"><span class="dot"></span>Saved before kickoff</span>` : `<span class="pill"><span class="dot"></span>Kicked off before edition: not in record</span>`;
  const al = (g.alerts || []).filter((a) => a.status !== "Questionable" || true).slice(0, 8);
  return `<a class="panel game" href="#game.${esc(g.game_id)}" style="color:inherit">
    <div class="teams">${esc(g.away)}<span class="at">at</span>${esc(g.home)}</div>
    <div class="ko">${esc(g.kickoff_london)}<br><span class="tiny muted">${esc(g.state)}</span></div>
    <div class="vol">${volLine(g.away_sim, g.away)}${volLine(g.home_sim, g.home)}</div>
    <div class="row" style="grid-column:1/-1">${st}${al.map((a) => `<span class="pill ${a.status.toLowerCase()}"><span class="dot"></span>${esc(a.name)} (${esc(a.team)}) ${esc(a.status)}</span>`).join("")}${(g.alerts || []).length > 8 ? `<span class="tiny muted">+${g.alerts.length - 8} more</span>` : ""}</div>
  </a>`;
}

// ---------------------------------------------------------------- Props explorer
function propCard(c, idx) {
  if (c.status !== "ok") return "";
  const ha = c.is_home ? "v" : "at";
  const ms = (c.milestones || []).map((m) => `<span title="P(${MARKET_LABELS[c.market]} ≥ ${m.k})">${m.k}+ <b>${pct(m.p)}</b></span>`).join("");
  const u = c.uncertainty || {};
  return `<article class="panel prop">
    <div class="head"><div class="who"><a href="#player.${esc(c.player_id)}">${esc(c.name)}</a>
      <div class="tiny muted">${esc(c.position)} · ${esc(c.team)} ${ha} ${esc(c.opponent)} · ${esc(c.kickoff_london)}</div></div>
      <span class="pill model" title="${esc(MODEL_NAMES[c.model])}">${esc(c.model)}</span></div>
    <div class="row between"><span class="mkt">${esc(c.market_label)}</span>${c.published_before_kickoff ? "" : '<span class="pill"><span class="dot"></span>Saved after kickoff: not in record</span>'}</div>
    <div class="big"><div><div class="v num">${dec(c.mean, 1)}</div><div class="l">mean</div></div>
      <div><div class="v num">${c.median}</div><div class="l">median</div></div>
      <div><div class="v num" style="font-size:1.3rem">${c.q10}–${c.q90}</div><div class="l">80% range</div></div></div>
    ${rangeBar(c)}
    <div class="ms">${ms}</div>
    <div class="foot"><span>P(plays) ${pct(c.p_play)}</span><span>Uncertainty ${esc(u.level || "–")}</span><span>History ${c.sample_size ?? 0} games</span>${injuryPill(c.injury_report)}</div>
    <div class="row"><button class="btn ghost" data-open="${idx}">Distribution and manual line</button></div>
  </article>`;
}

async function viewProps(v) {
  const p = await api("/api/props");
  const cards = p.cards.filter((c) => c.status === "ok");
  const teams = [...new Set(cards.map((c) => c.team))].sort();
  const f = Object.assign({ q: "", team: "", pos: "", market: "", minp: "0.5", sort: "kickoff" }, store.get("propsFilter", {}));
  v.innerHTML = `<section class="hero"><span class="eyebrow">${p.season} Week ${p.week} · edition ${esc(p.edition_id)}</span><h1>Props explorer</h1>
    <p>Forecasts for every eligible offensive player on the week's rosters, starters and reserves. Probabilities are conditional on the player taking part. There are no bookmaker prices: each card shows the model's distribution, and you can test any line yourself.</p></section>
    <div class="filters" role="search">
      <label>Player<input type="search" id="fq" value="${esc(f.q)}" placeholder="Name"></label>
      <label>Team<select id="fteam"><option value="">All 28</option>${teams.map((t) => `<option ${t === f.team ? "selected" : ""}>${t}</option>`).join("")}</select></label>
      <label>Position<select id="fpos"><option value="">All</option>${["QB", "RB", "WR", "TE", "FB"].map((t) => `<option ${t === f.pos ? "selected" : ""}>${t}</option>`).join("")}</select></label>
      <label>Market<select id="fmkt"><option value="">All</option>${Object.entries(MARKET_LABELS).map(([k, l]) => `<option value="${k}" ${k === f.market ? "selected" : ""}>${l}</option>`).join("")}</select></label>
      <label>P(plays) at least<select id="fminp">${[["0", "Any"], ["0.5", "50%"], ["0.8", "80%"]].map(([k, l]) => `<option value="${k}" ${k === f.minp ? "selected" : ""}>${l}</option>`).join("")}</select></label>
      <label>Sort<select id="fsort">${[["kickoff", "Kickoff, team"], ["mean", "Mean (high first)"], ["uncert", "Most history first"]].map(([k, l]) => `<option value="${k}" ${k === f.sort ? "selected" : ""}>${l}</option>`).join("")}</select></label>
      <button class="btn ghost" id="freset">Reset</button>
    </div>
    <div class="row between"><span class="small muted" id="fcount"></span><span id="fexport"></span></div>
    <div class="grid" id="cards"></div><div class="row" style="justify-content:center"><button class="btn ghost" id="more" hidden>Show more</button></div>`;
  let shown = 60;
  const apply = () => {
    const nf = { q: $("#fq").value.trim(), team: $("#fteam").value, pos: $("#fpos").value, market: $("#fmkt").value, minp: $("#fminp").value, sort: $("#fsort").value };
    store.set("propsFilter", nf);
    let r = cards.filter((c) => (!nf.q || c.name.toLowerCase().includes(nf.q.toLowerCase())) && (!nf.team || c.team === nf.team) && (!nf.pos || c.position === nf.pos) && (!nf.market || c.market === nf.market) && (c.p_play ?? 0) >= +nf.minp);
    if (nf.sort === "mean") r.sort((a, b) => b.mean - a.mean); else if (nf.sort === "uncert") r.sort((a, b) => (b.sample_size ?? 0) - (a.sample_size ?? 0)); else r.sort((a, b) => (b.published_before_kickoff - a.published_before_kickoff) || (a.kickoff_utc || "").localeCompare(b.kickoff_utc || "") || a.team.localeCompare(b.team) || a.name.localeCompare(b.name));
    $("#fcount").textContent = `${r.length.toLocaleString("en-GB")} forecasts match (of ${cards.length.toLocaleString("en-GB")})`;
    $("#cards").innerHTML = r.slice(0, shown).map((c) => propCard(c, cards.indexOf(c))).join("") || `<p class="muted">No forecasts match these filters. Reset them to see the whole slate.</p>`;
    $("#more").hidden = r.length <= shown;
    const rows = r.map((c) => ({ edition_id: p.edition_id, model: c.model, player_id: c.player_id, name: c.name, position: c.position, team: c.team, opponent: c.opponent, market: c.market, p_play: c.p_play, mean: c.mean, median: c.median, q10: c.q10, q90: c.q90, ...Object.fromEntries((c.milestones || []).map((m) => [`p_ge_${m.k}`, m.p])) }));
    setCopy("props", toCSV(rows));
    $("#fexport").innerHTML = SNAP ? copyBtn("props", "filtered CSV") : `<a class="btn ghost" href="/api/export/props.csv">All CSV</a> <a class="btn ghost" href="/api/export/props.json">JSON</a> ${copyBtn("props", "filtered CSV")}`;
  };
  ["#fq", "#fteam", "#fpos", "#fmkt", "#fminp", "#fsort"].forEach((s) => $(s).addEventListener(s === "#fq" ? "input" : "change", () => { shown = 60; apply(); }));
  $("#freset").addEventListener("click", () => { store.set("propsFilter", {}); route(); });
  $("#more").addEventListener("click", () => { shown += 60; apply(); });
  $("#cards").addEventListener("click", (e) => { const b = e.target.closest("[data-open]"); if (b) openSheet(cards[+b.dataset.open], p); });
  apply();
}

function openSheet(c, p) {
  const D = new Dist(c.dist || {});
  const back = document.createElement("div"); back.className = "sheet-back";
  const others = Object.entries(c.other_models || {}).filter(([, x]) => x.status === "ok").sort((a, b) => MODEL_ORDER.indexOf(a[0]) - MODEL_ORDER.indexOf(b[0]));
  const ri = c.role_inputs || {};
  back.innerHTML = `<div class="sheet" role="dialog" aria-modal="true" aria-label="${esc(c.name)} ${esc(c.market_label)}">
    <div class="row between"><div><span class="eyebrow">${esc(c.team)} ${c.is_home ? "v" : "at"} ${esc(c.opponent)} · ${esc(c.kickoff_london)}</span><h2>${esc(c.name)}: ${esc(c.market_label)}</h2></div><button class="btn ghost" id="close">Close</button></div>
    <div id="dchart">${c.dist ? distChart(D) : '<p class="muted">Distribution not included in this snapshot.</p>'}</div>
    <p class="tiny muted">Distribution of ${esc(c.market_label.toLowerCase())} if he takes part${c.mix_weight_J0 != null ? `: ${Math.round(c.mix_weight_J0 * 100)}% joint simulation (${(c.n_draws_played ?? 0).toLocaleString("en-GB")} of ${(c.n_draws ?? 0).toLocaleString("en-GB")} game draws), ${Math.round((1 - c.mix_weight_J0) * 100)}% gradient-boosting model` : c.n_draws ? ` (${(c.n_draws_played ?? 0).toLocaleString("en-GB")} of ${c.n_draws.toLocaleString("en-GB")} game simulations)` : ""}. P(plays) ${pct(c.p_play)}.</p>
    <form class="lineform" id="lf"><label for="lineIn" class="small"><b>Test a line</b></label><input id="lineIn" type="number" step="0.5" inputmode="decimal" placeholder="e.g. ${c.median + 0.5}" aria-describedby="lineHelp"><button class="btn" type="submit">Calculate</button><span id="lineHelp" class="tiny muted">Whole or half numbers. Integer lines can push.</span></form>
    <div id="lineOut"></div>
    ${others.length ? `<h3>Other models in the same edition</h3><div class="tablewrap"><table><thead><tr><th>Model</th><th>Mean</th><th>Median</th><th>80% range</th></tr></thead><tbody>
      <tr><td><span class="sw" style="background:var(--m-${c.model})"></span>${esc(c.model)} (shown)</td><td>${dec(c.mean, 1)}</td><td>${c.median}</td><td>${c.q10}–${c.q90}</td></tr>
      ${others.map(([m, x]) => `<tr><td><span class="sw" style="background:var(--m-${m})"></span>${esc(m)} ${esc(MODEL_NAMES[m] || "")}</td><td>${dec(x.mean, 1)}</td><td>${x.median}</td><td>${x.q10}–${x.q90}</td></tr>`).join("")}</tbody></table></div>` : ""}
    <h3>Role inputs (before kickoff)</h3>
    <dl class="kv"><dt>Target share</dt><dd>${pct1(ri.target_share)}</dd><dt>Designed-run share</dt><dd>${pct1(ri.designed_run_share)}</dd><dt>Catch rate (shrunk)</dt><dd>${pct1(ri.catch_rate)}</dd>${c.position === "QB" ? `<dt>P(starts)</dt><dd>${pct(ri.p_start)}</dd>` : ""}<dt>Games in history</dt><dd>${ri.games_in_history ?? 0}</dd><dt>Own catches / carries pooled</dt><dd>${ri.own_catch_pool ?? 0} / ${ri.own_carry_pool ?? 0}</dd><dt>Official report</dt><dd>${c.injury_report ? injuryPill(c.injury_report) : "not listed"}</dd><dt>Flags</dt><dd>${esc((c.flags || []).join(", ") || "none")}</dd></dl>
    <p class="tiny muted">Uncertainty ${esc(c.uncertainty?.level)}: ${esc((c.uncertainty?.reasons || []).join("; ") || "role and history are stable")}. Edition ${esc(p.edition_id)}, model ${esc(c.model)}.</p>
  </div>`;
  document.body.appendChild(back);
  const close = () => { back.remove(); document.removeEventListener("keydown", onKey); };
  const onKey = (e) => { if (e.key === "Escape") close(); };
  document.addEventListener("keydown", onKey);
  back.addEventListener("click", (e) => { if (e.target === back) close(); });
  $("#close", back).addEventListener("click", close);
  $("#lineIn", back).focus();
  $("#lf", back).addEventListener("submit", (e) => {
    e.preventDefault(); const L = Number($("#lineIn", back).value);
    if (!$("#lineIn", back).value || !isFinite(L) || (L * 2) % 1 !== 0 || Math.abs(L) > 2000) { $("#lineOut", back).innerHTML = `<p class="err small">Enter a whole or half number, for example 54.5 or 5.</p>`; return; }
    if (!c.dist) return;
    const r = D.line(L);
    const w = c.mix_weight_J0 ?? (c.model.startsWith("J") ? 1 : null);
    const se = w != null && c.n_draws_played ? w * Math.sqrt(Math.max(r.over * (1 - r.over), 0) / c.n_draws_played) : null;
    $("#dchart", back).innerHTML = distChart(D, { line: L });
    $("#lineOut", back).innerHTML = `<div class="row"><span class="pill manual">manual line — no verified price</span><span class="tiny muted">Conditional on taking part. Not a value judgement: no bookmaker price was recorded.</span></div>
      <div class="probs"><div><div class="l eyebrow">Over ${L}</div><div class="v num">${pct1(r.over)}</div><div class="tiny muted">fair ${dec(fair(r.over, r.push), 2)}</div></div>
      <div><div class="l eyebrow">Under ${L}</div><div class="v num">${pct1(r.under)}</div><div class="tiny muted">fair ${dec(fair(r.under, r.push), 2)}</div></div>
      <div><div class="l eyebrow">Push</div><div class="v num">${Number.isInteger(L) ? pct1(r.push) : "0%"}</div><div class="tiny muted">${Number.isInteger(L) ? "stake returned" : "half-point line"}</div></div></div>
      <p class="tiny muted">${se == null ? "No simulation noise (analytic distribution)." : `Monte Carlo standard error ±${pct1(se)} on Over.`} Fair price = break-even decimal odds for this model probability (push returns the stake).</p>`;
  });
}

// ---------------------------------------------------------------- Shortlist
async function viewShortlist(v) {
  const p = await api("/api/props");
  const cards = p.cards.filter((c) => c.status === "ok");
  const sel = store.get("short", { market: "rec_yds", k: 50 });
  const opts = Object.keys(MARKET_LABELS).flatMap((m) => [...new Set(cards.filter((c) => c.market === m).flatMap((c) => c.milestones.map((x) => x.k)))].map((k) => [m, k]));
  v.innerHTML = `<section class="hero"><span class="eyebrow">${p.season} Week ${p.week}</span><h1>Shortlist</h1><p>Two separate lists. The first ranks players by model probability for a milestone; it says nothing about value because no prices exist. The second would hold odds-based qualifying selections under frozen rules.</p></section>
    <section class="panel"><div class="row between"><h2>Probability ranking</h2><label class="small">Milestone <select id="sm">${opts.map(([m, k]) => `<option value="${m}|${k}" ${m === sel.market && k === sel.k ? "selected" : ""}>${MARKET_LABELS[m]} ${k}+</option>`).join("")}</select></label></div>
      <p class="small muted">Players with P(plays) of at least 80% and 6 or more games of history. Probability is P(stat ≥ milestone) if he takes part.</p><div class="tablewrap"><table id="st"></table></div></section>
    <section class="panel"><h2>Qualifying selections (odds-based)</h2>${banner("warn", `<p><b>No qualifying bet.</b> Selections need a real, contemporaneous price from a verified UK-accessible bookmaker, an edge that clears thresholds frozen on validation data, and a fresh quote. No odds key is configured and no prices are stored, so nothing qualifies. A manual line never qualifies.</p>`)}</section>`;
  const draw = () => {
    const [m, k] = $("#sm").value.split("|"); store.set("short", { market: m, k: +k });
    const rows = cards.filter((c) => c.market === m && c.p_play >= 0.8 && (c.sample_size ?? 0) >= 6).map((c) => ({ c, p: c.milestones.find((x) => x.k === +k)?.p })).filter((r) => r.p != null).sort((a, b) => b.p - a.p).slice(0, 40);
    $("#st").innerHTML = `<thead><tr><th>#</th><th class="l">Player</th><th class="l">Game</th><th>P(${k}+)</th><th>Mean</th><th>80% range</th><th>History</th></tr></thead><tbody>${rows.map((r, i) => `<tr><td>${i + 1}</td><td class="l"><a href="#player.${esc(r.c.player_id)}">${esc(r.c.name)}</a> <span class="muted">${esc(r.c.position)}</span></td><td class="l">${esc(r.c.team)} ${r.c.is_home ? "v" : "at"} ${esc(r.c.opponent)}</td><td><b>${pct(r.p)}</b></td><td>${dec(r.c.mean, 1)}</td><td>${r.c.q10}–${r.c.q90}</td><td>${r.c.sample_size} g</td></tr>`).join("")}</tbody>`;
  };
  $("#sm").addEventListener("change", draw); draw();
}

// ---------------------------------------------------------------- Player
async function viewPlayer(v, id) {
  const p = await api(`/api/player/${id}`);
  const pos = p.position;
  const key = pos === "QB" ? ["attempts", "Pass attempts"] : pos === "RB" || pos === "FB" ? ["carries", "Carries"] : ["targets", "Targets"];
  const games = p.game_log.slice(-20);
  const pts = games.map((g) => ({ x: `${String(g.season).slice(2)}w${g.week}`, v: g.participation === "played" ? g[key[0]] : null,
    note: { inactive: "INA", dnp: "DNP", reserve: "RES", active_unverified: "?", not_final: "…" }[g.participation] || "–",
    tip: `${g.participation === "played" ? g[key[0]] + " " + key[1].toLowerCase() : g.participation}|${g.season} week ${g.week} v ${g.opponent}` }));
  const W = p.windows || {};
  const cols = pos === "QB" ? ["attempts", "completions", "passing_yards", "carries", "rushing_yards"] : pos === "RB" || pos === "FB" ? ["carries", "rushing_yards", "targets", "receptions", "receiving_yards"] : ["targets", "receptions", "receiving_yards", "carries", "rushing_yards"];
  const lab = { attempts: "Att", completions: "Cmp", passing_yards: "Pass yds", carries: "Car", rushing_yards: "Rush yds", targets: "Tgt", receptions: "Rec", receiving_yards: "Rec yds" };
  setCopy("gl", toCSV(p.game_log));
  v.innerHTML = `<section class="hero"><span class="eyebrow">${esc(pos)} · ${esc(p.team)}</span><h1>${esc(p.name)}</h1><p class="small muted">GSIS id <span class="mono">${esc(p.player_id)}</span>${p.rookie_season ? ` · rookie season ${esc(p.rookie_season)}` : ""}</p></section>
    <section class="panel"><h2>Recent windows</h2><div class="tablewrap"><table><thead><tr><th class="l">Window</th><th>Games</th>${cols.map((c) => `<th>${lab[c]}</th>`).join("")}</tr></thead><tbody>${Object.entries(W).map(([k, w]) => `<tr><td class="l">${esc(k)}</td><td>${w.games}</td>${cols.map((c) => `<td>${dec(w[c], 1)}</td>`).join("")}</tr>`).join("")}</tbody></table></div><p class="tiny muted">Averages over games he played. Inactive, DNP and reserve weeks are excluded and shown in the log.</p></section>
    <section class="panel"><h2>${esc(key[1])} by game</h2>${columnChart(pts, { label: key[1] + " by game" })}<p class="tiny muted">Last ${games.length} team games across ${esc([...new Set(games.map((g) => g.season))].join(" and "))}. INA = inactive, DNP = active but did not play, RES = reserve list, ? = active with no recorded stat and no on-field data yet.</p></section>
    ${Object.keys(p.historical_frequency || {}).length ? `<section class="panel"><h2>Historical frequency this season</h2><p class="small">How often he reached each milestone in games he played. <b>This is a count of past games, not a probability.</b> The forecast cards give model probabilities.</p><div class="tablewrap"><table><thead><tr><th class="l">Market</th><th class="l">Milestones reached</th></tr></thead><tbody>${Object.entries(p.historical_frequency).filter(([m]) => cols.includes(MARKET_STAT[m])).map(([m, rows]) => `<tr><td class="l">${MARKET_LABELS[m]}</td><td class="l">${rows.map((r) => `${r.k}+ in ${r.hits} of ${r.games}`).join(" · ")}</td></tr>`).join("")}</tbody></table></div></section>` : ""}
    ${p.forecasts?.length ? `<section class="panel"><h2>Saved forecasts</h2><div class="tablewrap"><table><thead><tr><th class="l">Edition</th><th class="l">Model</th><th class="l">Market</th><th>Mean</th><th>Median</th><th>80% range</th><th>P(plays)</th></tr></thead><tbody>${p.forecasts.filter((f) => f.status === "ok").sort((a, b) => a.market.localeCompare(b.market) || MODEL_ORDER.indexOf(a.model) - MODEL_ORDER.indexOf(b.model)).map((f) => `<tr><td class="l mono">${esc(f.edition_id)}</td><td class="l"><span class="sw" style="background:var(--m-${f.model})"></span>${esc(f.model)}</td><td class="l">${esc(f.market_label)}</td><td>${dec(f.mean, 1)}</td><td>${f.median}</td><td>${f.q10}–${f.q90}</td><td>${pct(f.p_play)}</td></tr>`).join("")}</tbody></table></div></section>` : ""}
    <section class="panel"><div class="row between"><h2>Game log</h2><span>${SNAP ? copyBtn("gl", "game log CSV") : exportLinks(`player/${p.player_id}`, "Game log")}</span></div>
      <div class="tablewrap"><table><thead><tr><th class="l">Week</th><th class="l">Opp</th><th class="l">Status</th>${cols.map((c) => `<th>${lab[c]}</th>`).join("")}</tr></thead><tbody>${[...p.game_log].reverse().map((g) => `<tr class="${g.participation === "played" ? "" : "dim"}"><td class="l">${g.season} W${g.week}${g.season_type === "POST" ? " (post)" : ""}</td><td class="l">${esc(g.team)} v ${esc(g.opponent)}</td><td class="l">${partPill(g.participation)}</td>${cols.map((c) => `<td>${g[c] == null ? "–" : dec(g[c], 0)}</td>`).join("")}</tr>`).join("")}</tbody></table></div>
      <p class="tiny muted">A dash means no value: the player did not take part or participation is unverified. A zero means he played and recorded zero.</p></section>`;
}

// ---------------------------------------------------------------- Game
async function viewGame(v, id) {
  const g = await api(`/api/game/${id}`);
  const G = g.game;
  const stats = [["plays", "Offensive plays"], ["dropbacks", "Dropbacks"], ["attempts", "Pass attempts"], ["completions", "Completions"], ["passing_yards", "Passing yards"], ["sacks", "Sacks"], ["scrambles", "Scrambles"], ["designed_runs", "Designed runs"], ["kneels", "Kneels"]];
  const T = Object.fromEntries(g.teams.map((t) => [t.team, t]));
  const byTeam = (team) => g.cards.filter((c) => c.team === team && c.status === "ok");
  const keyMk = { QB: ["pass_yds", "pass_att", "rush_yds"], RB: ["rush_yds", "rush_att", "receptions"], FB: ["rush_att", "receptions"], WR: ["rec_yds", "receptions"], TE: ["rec_yds", "receptions"] };
  const players = (team) => { const by = {}; byTeam(team).forEach((c) => { (by[c.player_id] ||= { c, m: {} }).m[c.market] = c; });
    return Object.values(by).sort((a, b) => ["QB", "RB", "WR", "TE", "FB"].indexOf(a.c.position) - ["QB", "RB", "WR", "TE", "FB"].indexOf(b.c.position) || b.c.p_play - a.c.p_play);
  };
  const ptable = (team) => `<div class="tablewrap"><table><thead><tr><th class="l">Player</th><th>P(plays)</th><th class="l">Key forecasts (median, 80% range)</th></tr></thead><tbody>${players(team).filter((x) => x.c.p_play >= 0.05).map((x) => `<tr class="${x.c.p_play < 0.5 ? "dim" : ""}"><td class="l"><a href="#player.${esc(x.c.player_id)}">${esc(x.c.name)}</a> <span class="muted">${esc(x.c.position)}</span> ${injuryPill(x.c.injury_report)}</td><td>${pct(x.c.p_play)}</td><td class="l">${(keyMk[x.c.position] || []).filter((m) => x.m[m]).map((m) => `${MARKET_LABELS[m]} ${x.m[m].median} (${x.m[m].q10}–${x.m[m].q90})`).join(" · ")}</td></tr>`).join("")}</tbody></table></div>`;
  v.innerHTML = `<section class="hero"><span class="eyebrow">${esc(G.kickoff_london)} · edition ${esc(g.edition_id)}</span><h1>${esc(G.away)} at ${esc(G.home)}</h1>
      <p>${G.published_before_kickoff ? "Saved before kickoff: part of the pre-match record." : "This edition was saved after kickoff, so the game is excluded from the pre-match record."}</p></section>
    <section class="panel"><h2>Simulated team volume</h2><p class="small muted">From ${esc(g.model)} joint simulations of the whole game. Both teams share game-level noise: in history, when one offence ran more plays the other ran fewer (residual correlation about −0.48), and pass rates move in opposite directions with game script.</p>
      <div class="tablewrap"><table><thead><tr><th class="l">Per team</th>${[G.away, G.home].map((t) => `<th>${esc(t)} mean</th><th>${esc(t)} 80% range</th>`).join("")}</tr></thead><tbody>${stats.map(([k, l]) => `<tr><td class="l">${l}</td>${[G.away, G.home].map((t) => { const s = T[t]?.sim?.[k]; return s ? `<td>${dec(s.mean, 1)}</td><td>${s.q10}–${s.q90}</td>` : "<td>–</td><td>–</td>"; }).join("")}</tr>`).join("")}</tbody></table></div>
      <p class="tiny muted">Inputs: ${[G.away, G.home].map((t) => { const i = T[t]?.inputs || {}; return `${esc(t)} expected plays ${dec(i.mu_plays, 1)}, dropback rate ${pct1(i.dropback_rate)}, opponent pass-yards factor ${dec(i.opp_rec_yards_factor, 2)}`; }).join("; ")}.</p></section>
    <div class="grid"><section class="panel"><h2>${esc(G.away)}</h2>${ptable(G.away)}</section><section class="panel"><h2>${esc(G.home)}</h2>${ptable(G.home)}</section></div>`;
}

// ---------------------------------------------------------------- Backtest lab
async function viewLab(v) {
  const runs = await api("/api/backtests");
  if (!runs.length) { v.innerHTML = banner("warn", "<p>No backtest reports yet. Run <code>npl backtest</code>.</p>"); return; }
  const sel = store.get("labRun", runs[runs.length - 1].id);
  const run = runs.find((r) => r.id === sel) || runs[runs.length - 1];
  const markets = Object.keys(run.markets);
  const mk = store.get("labMkt", markets[0]); const market = markets.includes(mk) ? mk : markets[0];
  const R = run.markets[market]; const models = Object.keys(R.models).sort((a, b) => MODEL_ORDER.indexOf(a) - MODEL_ORDER.indexOf(b));
  const tab = store.get("labTab", "stats");
  const ms = (run.milestones || {});
  v.innerHTML = `<section class="hero"><span class="eyebrow">Retrospective research · not a published record</span><h1>Backtest lab</h1>
      <p>Walk-forward results: each week was forecast using only earlier weeks, then scored. Models are compared on exactly the same player-games. Lower CRPS is better. Intervals resample whole weeks because props from one game are correlated.</p></section>
    <div class="filters"><label>Run<select id="lr">${runs.map((r) => `<option value="${esc(r.id)}" ${r.id === run.id ? "selected" : ""}>${esc(r.id)}</option>`).join("")}</select></label>
      <label>Market<select id="lm">${markets.map((m) => `<option value="${m}" ${m === market ? "selected" : ""}>${MARKET_LABELS[m]}</option>`).join("")}</select></label>
      <label>View<select id="lt">${[["stats", "Statistical"], ["calib", "Calibration"], ["money", "Profit and loss"]].map(([k, l]) => `<option value="${k}" ${k === tab ? "selected" : ""}>${l}</option>`).join("")}</select></label></div>
    <p class="small muted">${esc(run.run.label)}: ${run.slates} weekly slates, ${run.run.rows.toLocaleString("en-GB")} ledger rows, ${run.run.n_sims ? run.run.n_sims.toLocaleString("en-GB") + " simulations per game, " : ""}model v${esc(run.run.model_version)}, code ${esc((run.run.code?.git_commit || "").slice(0, 10))}${run.run.code?.dirty ? " (uncommitted changes)" : ""}. Splits: training ${run.run.splits.train.join("–")}, validation ${run.run.splits.validation.join("–")}, test ${run.run.splits.test.join("–")}. Ledger SHA-256 <span class="mono">${esc(run.run.ledger_sha256.slice(0, 16))}…</span> ${SNAP ? "" : `<a href="/api/export/ledger/${esc(run.id)}.csv.gz">download ledger</a>`}</p>
    <div id="lab"></div>`;
  const body = $("#lab");
  if (tab === "money") {
    body.innerHTML = `<section class="panel"><h2>Profit and loss</h2>${banner("warn", `<p><b>ROI unavailable.</b> No historical bookmaker prices are stored, so there is no yield, bankroll path or closing-line value. Prices are never invented from fair odds.</p><p class="small">To enable this tab: an odds source with historical player-prop snapshots (The Odds API archive starts 3 May 2023, 5-minute snapshots, paid plans from USD 30 per month), then quotes stored with their observation times, linked to players through the audited name mapping and settled under each bookmaker's rules. US-only books would be research references, not UK-accessible results.</p>`)}</section>`;
  } else if (tab === "calib") {
    const msm = Object.fromEntries(Object.entries(ms).map(([m, v]) => [m, Array.isArray(v) ? [] : (v[market] || [])]));
    const show = models.filter((m) => msm[m]?.length).slice(0, 4);
    const pitCell = (v) => { const d = v - 0.1; const cls = d > 0.03 ? "hi2" : d > 0.012 ? "hi" : d < -0.03 ? "lo2" : d < -0.012 ? "lo" : ""; return `<td class="c ${cls}" data-tip="${pct1(v)} of outcomes|calibrated = 10%">${Math.round(v * 100)}</td>`; };
    const focus = models[0];
    body.innerHTML = `<section class="panel"><h2>PIT: ${MARKET_LABELS[market]}</h2><p class="small muted">Share of outcomes (%) falling in each tenth of the forecast distribution. A calibrated model puts 10% in every column. High values on the left mean outcomes landed low (over-prediction); a dip at both ends means the forecast is too wide. Shading marks distance from 10%.</p>
      <div class="tablewrap"><table class="pit"><thead><tr><th class="l">Model</th>${[...Array(10).keys()].map((i) => `<th>${i * 10}–${i * 10 + 10}</th>`).join("")}</tr></thead><tbody>${models.map((m) => `<tr><td class="l"><span class="sw" style="background:var(--m-${m})"></span>${esc(m)}</td>${R.models[m].pit_deciles.map(pitCell).join("")}</tr>`).join("")}</tbody></table></div>
      <div><div class="legend"><span><i class="box" style="background:var(--m-${focus})"></i>${esc(focus)} ${esc(MODEL_NAMES[focus] || "")}</span></div>${pitChart(R.models[focus].pit_deciles, `var(--m-${focus})`)}</div></section>
      <section class="panel"><h2>Interval coverage</h2><div class="tablewrap"><table><thead><tr><th class="l">Model</th><th>50% interval</th><th>80% interval</th><th>90% interval</th></tr></thead><tbody>${models.map((m) => `<tr><td class="l"><span class="sw" style="background:var(--m-${m})"></span>${esc(m)}</td><td>${pct1(R.models[m].cov50)}</td><td>${pct1(R.models[m].cov80)}</td><td>${pct1(R.models[m].cov90)}</td></tr>`).join("")}</tbody></table></div><p class="tiny muted">Intervals are inclusive integer ranges, so discrete counts (receptions, attempts) cover more than the nominal level even when calibrated. PIT is the fairer check.</p></section>
      <section class="panel"><h2>Milestone reliability: ${MARKET_LABELS[market]}</h2><p class="small muted">Forecast P(stat ≥ threshold) against how often it happened, at fixed thresholds (not offered lines). Dots on the diagonal are calibrated; dot size grows with the number of forecasts.</p>
      ${show.length ? `<div class="grid tight">${show.map((m) => `<div><div class="legend"><span><i class="box" style="background:var(--m-${m})"></i>${esc(m)}</span></div>${reliabilityChart(msm[m].filter((r) => r.bin !== "ALL"), `var(--m-${m})`)}</div>`).join("")}</div>
      <div class="tablewrap"><table><thead><tr><th class="l">Model</th><th>Threshold</th><th>n</th><th>Brier</th><th>Log loss</th><th>Mean forecast</th><th>Observed</th></tr></thead><tbody>${models.filter((m) => msm[m]?.length).flatMap((m) => msm[m].filter((r) => r.bin === "ALL").map((r) => `<tr><td class="l"><span class="sw" style="background:var(--m-${m})"></span>${esc(m)}</td><td>${r.threshold}+</td><td>${r.n.toLocaleString("en-GB")}</td><td>${dec(r.brier, 4)}</td><td>${dec(r.log_loss, 4)}</td><td>${pct1(r.mean_p)}</td><td>${pct1(r.freq)}</td></tr>`)).join("")}</tbody></table></div>` : `<p class="small muted">This run's summary predates per-market reliability. Regenerate it with <code>evaluate.write_report</code>.</p>`}</section>`;
  } else {
    const base = run.baseline;
    const sk = models.filter((m) => m !== base).map((m) => ({ m, d: R.models[m].crps_minus_baseline, lo: R.models[m].crps_minus_baseline_95ci?.[0], hi: R.models[m].crps_minus_baseline_95ci?.[1] }));
    body.innerHTML = `<section class="panel"><h2>${MARKET_LABELS[market]}: ${R.n_common.toLocaleString("en-GB")} player-games over ${R.n_weeks} weeks</h2>
      <p class="small muted">CRPS difference against baseline ${esc(base)} (${esc(MODEL_NAMES[base])}). Left of the line is better than the baseline; the bar is the 95% week-block interval.</p>
      <div class="legend">${sk.map((x) => `<span><i style="background:var(--m-${x.m})"></i>${esc(x.m)}</span>`).join("")}</div>${skillChart(sk)}
      <div class="tablewrap"><table><thead><tr><th class="l">Model</th><th>CRPS</th><th>vs ${esc(base)} (95% CI)</th><th>Skill</th><th>Log score</th><th>MAE</th><th>Bias</th><th>RMSE</th></tr></thead><tbody>${models.map((m) => { const x = R.models[m]; return `<tr><td class="l"><span class="sw" style="background:var(--m-${m})"></span>${esc(m)} ${esc(MODEL_NAMES[m])}</td><td>${dec(x.crps, 3)}</td><td>${x.crps_minus_baseline_95ci ? `${sgn(x.crps_minus_baseline)} [${sgn(x.crps_minus_baseline_95ci[0])}, ${sgn(x.crps_minus_baseline_95ci[1])}]` : "baseline"}</td><td>${x.crps_skill_vs_baseline != null ? sgn(x.crps_skill_vs_baseline * 100, 1) + "%" : "–"}</td><td>${dec(x.log_score, 3)}</td><td>${dec(x.mae_median, 2)}</td><td>${sgn(x.bias_mean, 2)}</td><td>${dec(x.rmse_mean, 2)}</td></tr>`; }).join("")}</tbody></table></div>
      <p class="tiny muted">MAE is of the median; bias and RMSE are of the mean. Skill = 1 − CRPS/CRPS(baseline). Negative CRPS differences favour the model.</p></section>
      <section class="panel"><h2>What was not scored</h2><p class="small muted">Rows excluded from every metric, by actual participation and forecast status. Inactive and DNP players are void for props under the usual rules, so they are not counted as Unders.</p>
      <div class="tablewrap"><table><thead><tr><th class="l">Model</th><th class="l">Participation</th><th class="l">Forecast status</th><th>Rows</th></tr></thead><tbody>${(run.exclusions || []).sort((a, b) => b.n - a.n).slice(0, 30).map((e) => `<tr><td class="l">${esc(e.model)}</td><td class="l">${esc(e.participation)}</td><td class="l">${esc(e.status)}</td><td>${e.n.toLocaleString("en-GB")}</td></tr>`).join("")}</tbody></table></div></section>`;
  }
  $("#lr").addEventListener("change", (e) => { store.set("labRun", e.target.value); route(); });
  $("#lm").addEventListener("change", (e) => { store.set("labMkt", e.target.value); route(); });
  $("#lt").addEventListener("change", (e) => { store.set("labTab", e.target.value); route(); });
}

// ---------------------------------------------------------------- Saved record
async function viewRecord(v) {
  const r = await api("/api/record");
  setCopy("rec", toCSV(r.editions.map(({ games, ...e }) => ({ ...e, code: e.code?.git_commit, games_before_kickoff: games.filter((g) => g.published_before_kickoff).length }))));
  v.innerHTML = `<section class="hero"><span class="eyebrow">Genuine pre-match record</span><h1>Saved record</h1><p>${esc(r.note)} Editions are immutable files with a SHA-256 hash, committed to git so the push time is independently timestamped. A correction is a new edition; nothing is overwritten.</p></section>
    <section class="panel"><h2>Scoring status by model</h2><div class="tablewrap"><table><thead><tr><th class="l">Model</th><th>Forecasts</th><th>Saved before kickoff</th><th>Scored</th><th>Awaiting result</th><th>Mean CRPS</th></tr></thead><tbody>${Object.entries(r.summary).sort((a, b) => MODEL_ORDER.indexOf(a[0]) - MODEL_ORDER.indexOf(b[0])).map(([m, s]) => `<tr><td class="l"><span class="sw" style="background:var(--m-${m})"></span>${esc(m)} ${esc(MODEL_NAMES[m])}</td><td>${s.forecasts.toLocaleString("en-GB")}</td><td>${s.pre_kickoff.toLocaleString("en-GB")}</td><td>${s.settled_scored.toLocaleString("en-GB")}</td><td>${s.pending.toLocaleString("en-GB")}</td><td>${s.mean_crps == null ? "–" : dec(s.mean_crps, 3)}</td></tr>`).join("")}</tbody></table></div><p class="tiny muted">Mean CRPS pools all markets, so compare models within this table only. Per-market prospective scores appear once enough games are final.</p></section>
    <div class="row between"><h2>Editions</h2><span>${SNAP ? copyBtn("rec", "editions CSV") : exportLinks("record", "Editions")}</span></div>
    ${r.editions.map((e) => `<section class="panel"><div class="row between"><h3 class="mono" style="font-size:0.95rem">${esc(e.edition_id)}</h3><span class="pill ${e.retrospective ? "" : "good"}"><span class="dot"></span>${e.retrospective ? "Retrospective" : "Published"}</span></div>
      <dl class="kv"><dt>Published</dt><dd>${esc(e.published_london)}</dd><dt>Forecasts</dt><dd>${e.n_forecasts.toLocaleString("en-GB")} across ${Object.keys(MODEL_NAMES).length - 1} models</dd><dt>Code</dt><dd class="mono">${esc(e.code?.git_commit)}${e.code?.dirty ? " (dirty)" : ""}</dd><dt>File</dt><dd class="mono">${esc(e.file)}</dd><dt>SHA-256</dt><dd class="mono">${esc(e.sha256)}</dd><dt>Inputs hash</dt><dd class="mono">${esc(e.inputs_hash)}</dd></dl>
      <div class="tablewrap"><table><thead><tr><th class="l">Game</th><th class="l">Kickoff (UK)</th><th class="l">In record</th></tr></thead><tbody>${e.games.map((g) => `<tr><td class="l"><a href="#game.${esc(g.game_id)}">${esc(g.away)} at ${esc(g.home)}</a></td><td class="l">${esc(g.kickoff_london)}</td><td class="l">${g.published_before_kickoff ? '<span class="pill good"><span class="dot"></span>Yes</span>' : '<span class="pill"><span class="dot"></span>No, kicked off earlier</span>'}</td></tr>`).join("")}</tbody></table></div></section>`).join("")}`;
}

// ---------------------------------------------------------------- Data & methodology
const BLOCKED = [
  ["Bookmaker prices", "Blocked", "No odds key configured. Historical prop snapshots need a paid plan (The Odds API from USD 30/month; archive from 3 May 2023). Not purchased: needs your approval."],
  ["UK-accessible NFL prop prices", "Not documented", "The Odds API documents NFL props for US bookmakers only; BetMGM UK is not in its UK list. A 1-credit check with a key would settle it."],
  ["Inactive lists (late edition)", "Unavailable", "Published about 90 minutes before kickoff; NFL.com terms prohibit systematic retrieval. The late edition is marked unavailable."],
  ["Injury report timestamps", "Unavailable", "nflverse injury rows carry no publication time, so historical use assumes the Friday 4pm ET final report (variant J1 only)."],
  ["Snap counts and PFR advanced stats", "Not used", "Sports Reference terms prohibit using its content to support machine-learning prediction."],
  ["Participation and routes before kickoff", "Unavailable", "On-field data arrives after each season; used only to label outcomes of past games."],
  ["Pre-kickoff weather forecasts", "Not integrated", "Observed weather would leak; archived lead-time forecasts exist only from 2024 (Open-Meteo Previous Runs)."],
  ["Preseason", "Absent", "nflverse carries no preseason games; not modelled."],
];
async function viewData(v) {
  const d = await api("/api/data");
  const cov = d.coverage || [];
  const ds = [...new Set(cov.map((c) => c.dataset))].sort();
  const seasons = [...new Set(cov.map((c) => c.season).filter((s) => typeof s === "number" && s >= 2012))].sort();
  const cell = (dsn, s) => { const r = cov.find((c) => c.dataset === dsn && c.season === s); return r ? `<td class="c has" data-tip="${r.rows.toLocaleString("en-GB")} rows|${esc(dsn)} ${s}">${r.rows >= 1000 ? Math.round(r.rows / 1000) + "k" : r.rows}</td>` : `<td class="c none">·</td>`; };
  v.innerHTML = `<section class="hero"><span class="eyebrow">Sources, licences, coverage, versions</span><h1>Data and methodology</h1><p>What the forecasts are built from, what is missing, and why. Full audit notes with source links are in <span class="mono">docs/DATA_AUDIT.md</span> and <span class="mono">docs/research/</span>.</p></section>
    ${d.odds?.key_configured ? "" : banner("warn", `<p><b>Odds:</b> ${esc(d.odds?.blocker || "")}</p>`)}
    ${(d.failed_jobs || []).length ? banner("crit", `<p><b>Failed or partial jobs</b></p><p class="small">${d.failed_jobs.map((j) => `${esc(j.job)} at ${esc(j.started_at)} (${esc(j.status)})`).join("<br>")}</p>`) : ""}
    ${(d.stale || []).length ? banner("crit", `<p><b>Stale feeds:</b> ${d.stale.map((s) => `${esc(s.dataset)} ${esc(s.part)} (${s.hours_since_check} h)`).join(", ")}</p>`) : ""}
    <section class="panel"><h2>Freshness</h2><div class="tablewrap"><table><thead><tr><th class="l">Dataset</th><th class="l">Part</th><th class="l">New content retrieved</th><th class="l">Last checked</th><th class="l">Upstream last modified</th><th>Rows</th></tr></thead><tbody>${(d.freshness || []).sort((a, b) => a.dataset.localeCompare(b.dataset)).map((f) => `<tr><td class="l">${esc(f.dataset)}</td><td class="l">${esc(f.part)}</td><td class="l">${londonTime(f.retrieved_at)}</td><td class="l">${londonTime(f.checked_at)}</td><td class="l">${esc(f.upstream_last_modified || "–")}</td><td>${f.n_rows == null ? "–" : f.n_rows.toLocaleString("en-GB")}</td></tr>`).join("")}</tbody></table></div></section>
    <section class="panel"><h2>Coverage: rows held per dataset and season</h2><p class="small muted">Counted from the stored files, not from documentation.</p><div class="tablewrap"><table class="heat"><thead><tr><th class="l">Dataset</th>${seasons.map((s) => `<th>${String(s).slice(2)}</th>`).join("")}</tr></thead><tbody>${ds.map((x) => `<tr><td class="l">${esc(x)}</td>${seasons.map((s) => cell(x, s)).join("")}</tr>`).join("")}</tbody></table></div></section>
    <section class="panel"><h2>Gaps and blockers</h2><div class="tablewrap"><table><thead><tr><th class="l">Need</th><th class="l">Status</th><th class="l">Why</th></tr></thead><tbody>${BLOCKED.map(([a, b, c]) => `<tr><td class="l">${esc(a)}</td><td class="l"><span class="pill ${b === "Blocked" ? "out" : "questionable"}"><span class="dot"></span>${esc(b)}</span></td><td class="l" style="white-space:normal;min-width:260px">${esc(c)}</td></tr>`).join("")}</tbody></table></div></section>
    <section class="panel"><h2>How the forecasts work</h2><div class="stack small" style="max-width:72ch">
      <p><b>Opportunity before efficiency.</b> Each game is simulated 10,000 times. Team plays and pass rate are drawn jointly for both teams; dropbacks become sacks, scrambles and pass attempts; attempts are shared among the receivers available in that draw; catches and per-catch yards come from each player's own recent plays mixed with his position's pool. Runs are shared the same way. QB totals are the sum of what his receivers caught, so completions never exceed attempts and receptions never exceed targets.</p>
      <p><b>Small samples stay humble.</b> Shares are recency-weighted with an extra discount at each season boundary and shrunk towards the usage of players with the same experience, so rookies and reserves carry wide ranges instead of borrowed starter roles.</p>
      <p><b>No odds in, no invented odds out.</b> No model uses spreads, totals, prices, third-party projections or tips. Without real recorded prices the app shows probabilities and fair prices only.</p>
      <p><b>Honest evaluation.</b> Seasons 2022–2023 select and calibrate; 2024–2025 are kept untouched for a single final test; 2026 week 7 onwards is the genuine pre-match record. Every reported number can be recomputed from a published ledger.</p></div></section>`;
}

async function viewMore(v) {
  v.innerHTML = `<section class="hero"><h1>More</h1></section><div class="grid tight">${NAV.slice(4).map(([k, l]) => `<a class="panel" href="#${k}"><h3>${l}</h3></a>`).join("")}</div>`;
}

shell();
route();
