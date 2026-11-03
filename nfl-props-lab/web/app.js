// Props Lab web app (NFL and NBA). Works against the FastAPI server (/api/...) or, when a
// snapshot is embedded (window.NPL_SNAPSHOT), entirely offline from that snapshot.
// Every number shown is read from a saved edition, lake table or backtest summary.

const SNAP = window.NPL_SNAPSHOT || null;
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const app = document.getElementById("app");
const cache = new Map();

// a page the offline copy does not hold (shown as a calm note, not as an error)
class NotInCopy extends Error {}
async function api(path) {
  if (cache.has(path)) return cache.get(path);
  let data;
  if (SNAP) {
    if (!(path in SNAP.data)) throw new NotInCopy(`Not included in this snapshot: ${path}`);
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
// Rounding: half up (halves away from zero) on the figure as written, its shortest decimal form,
// as the server rounds (npl.nba.extras.half_up): 2.675 -> 2.68, 0.575 -> 58%, -2.25 -> -2.3.
// toFixed and Math.round work on the binary value instead (2.675.toFixed(2) is "2.67"), so every
// shown figure goes through rnd. shift moves the decimal point in the number's string form.
const shift = (x, k) => { const [m, e = "0"] = String(x).split("e"); return Number(`${m}e${Number(e) + k}`); };
const rnd = (x, d = 0) => { const a = shift(Math.round(shift(Math.abs(x), d)), -d); return (x < 0 ? -a : a) + 0; }; // + 0: never -0
const pctInt = (p) => rnd(shift(p, 2), 0); // a chance as a whole percentage
const pctFrom = (r, p) => (p === 0 ? "0%" : p === 1 ? "100%" : r < 1 ? "<1%" : r > 99 ? ">99%" : `${r}%`);
const pct = (p) => (p == null || isNaN(p) ? "–" : pctFrom(pctInt(p), p));
const pct1 = (p) => (p == null || isNaN(p) ? "–" : `${rnd(shift(p, 2), 1).toFixed(1)}%`);
const dec = (x, n = 1) => (x == null || isNaN(x) ? "–" : rnd(Number(x), n).toFixed(n));
const sgn = (x, n = 3) => { if (x == null || isNaN(x)) return "–"; const r = rnd(Number(x), n); return (r > 0 ? "+" : "") + r.toFixed(n); };
const signed = (r, s) => (r > 0 ? "+" : r < 0 ? "−" : "") + s;
const pm1 = (x) => { if (x == null || isNaN(x)) return "–"; const r = rnd(Number(x), 1); return signed(r, Math.abs(r).toFixed(1)); };
// up to 2 decimals with trailing zeros dropped (spreads and totals: 227.75, 227.5, 228)
const num2 = (x) => (x == null || isNaN(x) ? "–" : String(rnd(Number(x), 2)));
const pm2 = (x) => { if (x == null || isNaN(x)) return "–"; const r = rnd(Number(x), 2); return signed(r, String(Math.abs(r))); };
// a - b from the two figures as shown to n decimals, so a shown difference matches them
const minusShown = (a, b, n = 1) => (a == null || b == null || isNaN(a) || isNaN(b) ? null : shift(Math.round(shift(rnd(Number(a), n), n)) - Math.round(shift(rnd(Number(b), n), n)), -n) + 0);
// over and under as shown: on a line with no push the under is 100% less the over as shown, so
// the pair adds up to 100% (57.5% over shows as 58% over, 42% under)
function ouPair(over, under, push) {
  if (over == null || isNaN(over)) return ["–", "–"];
  if (push) return [pct(over), pct(under)];
  const o = pctInt(over); return [pctFrom(o, over), pctFrom(100 - o, under)];
}
// The offline copy holds player pages for some players only (NBA: the snapshot's
// snapshot_player_pages list; otherwise the pages it embeds), so a name links to a page only
// where the copy has one. On the live server every name links.
const NBA_PAGES = (() => {
  if (!SNAP) return null;
  const l = SNAP.data?.snapshot_player_pages ?? SNAP.snapshot_player_pages ?? SNAP.data?.["/api/nba/props"]?.snapshot_player_pages;
  return new Set((Array.isArray(l) ? l : Object.keys(SNAP.data).filter((k) => k.startsWith("/api/nba/player/")).map((k) => k.slice(16))).map(String));
})();
const hasNbaPage = (pid) => !NBA_PAGES || NBA_PAGES.has(String(pid));
const hasNflPage = (pid) => !SNAP || `/api/player/${pid}` in SNAP.data;
const nameLink = (ok, href, name, cls = "") => (ok ? `<a href="${href}"${cls ? ` class="${cls}"` : ""}>${esc(name)}</a>` : `<span class="nolink${cls ? ` ${cls}` : ""}">${esc(name)}</span>`);
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

// ---------------------------------------------------------------- tooltip (hover, focus and tap)
// A mouse shows the tip while it hovers. Keyboard focus and a tap (touch or pen) open it beside
// the element ("pinned") until a tap elsewhere, Escape or a change of view closes it; a pinned tip
// follows its element when the page scrolls. A tap never cancels the tapped element's own action.
const tip = document.createElement("div"); tip.id = "tip"; tip.hidden = true; tip.setAttribute("role", "tooltip"); document.body.appendChild(tip);
let tipFor = null, tipPinned = false, lastPtr = -1e9;
function placeTip(x, y, box) {
  const w = tip.offsetWidth, h = tip.offsetHeight;
  // beside the pointer, or centred above the element (below it when there is no room above)
  let top = (box ? box.top : y) - h - 12; if (box && top < 8) top = box.bottom + 10;
  tip.style.left = Math.min(window.innerWidth - w - 8, Math.max(8, box ? x - w / 2 : x + 12)) + "px";
  tip.style.top = Math.max(8, Math.min(top, window.innerHeight - h - 8)) + "px";
}
function showTip(el, x, y, box = null) {
  const t = el.getAttribute("data-tip"); if (!t) return;
  if (tipFor !== el || tip.hidden) {
    tip.textContent = ""; t.split("|").forEach((line, i) => { const d = document.createElement(i ? "div" : "b"); d.textContent = line; tip.appendChild(d); if (!i) tip.appendChild(document.createElement("br")); });
  }
  tip.hidden = false; tipFor = el; placeTip(x, y, box);
}
function pinTip(el) { const r = el.getBoundingClientRect(); showTip(el, r.left + r.width / 2, r.top, r); tipPinned = true; }
function hideTip() { tip.hidden = true; tipFor = null; tipPinned = false; }
document.addEventListener("pointerdown", (e) => { lastPtr = e.timeStamp; }, true);
document.addEventListener("pointermove", (e) => {
  if (e.pointerType !== "mouse") return; // a finger moving is a scroll, not a hover
  const el = e.target.closest?.("[data-tip]");
  if (el) { tipPinned = false; showTip(el, e.clientX, e.clientY); } else if (!tipPinned && !tip.hidden) hideTip();
});
// a tap toggles the tapped element's tip; a tap anywhere else closes it
document.addEventListener("pointerup", (e) => {
  if (e.pointerType === "mouse") return;
  const el = e.target.closest?.("[data-tip]");
  if (!el) { if (!tip.hidden) hideTip(); return; }
  if (tipFor === el && !tip.hidden) hideTip(); else pinTip(el);
});
// keyboard focus pins the tip; focus that comes with a click or tap is left to the handlers above
document.addEventListener("focusin", (e) => { const el = e.target.closest?.("[data-tip]"); if (el && e.timeStamp - lastPtr > 800) pinTip(el); });
document.addEventListener("focusout", (e) => { if (tipPinned && tipFor && (tipFor === e.target || tipFor.contains(e.target))) hideTip(); });
// Escape closes an open tip first (a sheet behind it stays open until the next Escape)
window.addEventListener("keydown", (e) => { if (e.key !== "Escape" || tip.hidden) return; const pinned = tipPinned; hideTip(); if (pinned) e.stopImmediatePropagation(); }, true);
document.addEventListener("scroll", () => {
  if (!tipPinned || !tipFor) return;
  const r = tipFor.getBoundingClientRect();
  if (!tipFor.isConnected || r.bottom < 0 || r.top > window.innerHeight) hideTip(); else placeTip(r.left + r.width / 2, r.top, r);
}, { capture: true, passive: true });
// a control that re-draws the view takes its tip with it
const tipGone = () => { if (tipFor && !tipFor.isConnected) hideTip(); };
["click", "change", "input"].forEach((t) => document.addEventListener(t, tipGone));

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
  s += `<g class="grid">${niceTicks(0, pmax, 4).map((t) => `<line x1="${m.l}" x2="${width - m.r}" y1="${Y(t)}" y2="${Y(t)}"/><text x="${m.l - 6}" y="${Y(t) + 4}" text-anchor="end">${pctInt(t)}%</text>`).join("")}</g>`;
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
  let s = `<svg class="chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="PIT histogram"><g class="grid">${niceTicks(0, vmax, 3).map((t) => `<line x1="${m.l}" x2="${width - m.r}" y1="${Y(t)}" y2="${Y(t)}"/><text x="${m.l - 5}" y="${Y(t) + 4}" text-anchor="end">${pctInt(t)}%</text>`).join("")}</g>`;
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
  const width = 400, rowH = 28, m = { l: 44, r: 14, t: 18, b: 22 }, height = m.t + m.b + rowH * models.length;
  const vals = models.flatMap((x) => [x.lo, x.hi, x.d]).filter((v) => v != null);
  let lo = Math.min(0, ...vals), hi = Math.max(0, ...vals); const pad = (hi - lo) * 0.12 || 0.01; lo -= pad; hi += pad;
  const X = (v) => m.l + ((v - lo) / (hi - lo)) * (width - m.l - m.r);
  let s = `<svg class="chart" style="max-width:${width}px" viewBox="0 0 ${width} ${height}" role="img" aria-label="CRPS difference versus baseline with 95% intervals"><g class="grid">${niceTicks(lo, hi, 5).map((t) => `<line x1="${X(t)}" x2="${X(t)}" y1="${m.t - 6}" y2="${height - m.b}"/><text x="${X(t)}" y="${height - 6}" text-anchor="middle">${+t.toFixed(3)}</text>`).join("")}</g>`;
  s += `<line x1="${X(0)}" x2="${X(0)}" y1="${m.t - 8}" y2="${height - m.b}" stroke="var(--ink)" stroke-width="1"/><text class="lab" x="${X(0) + 4}" y="${m.t - 8}">baseline</text>`;
  models.forEach((x, i) => { const y = m.t + rowH * (i + 0.5);
    s += `<text class="lab" x="${m.l - 10}" y="${y + 4}" text-anchor="end">${esc(x.m)}</text>`;
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
  defense: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6l-8-3z"/></svg>',
  pen: '<svg class="pen" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m13.5 6.5 4 4"/></svg>',
  dvp: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><path d="M14 17.5h7M17.5 14v7"/></svg>',
  more: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/></svg>',
  warn: '<svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3 2 20h20L12 3zM12 10v4M12 17h.01"/></svg>',
  info: '<svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/></svg>',
  ok: '<svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/></svg>',
};
// Fallback marks for the sport switch until the league logos load
const GLYPH = {
  nfl: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" aria-hidden="true"><g transform="rotate(-35 12 12)"><ellipse cx="12" cy="12" rx="10" ry="6"/><path d="M8.5 12h7M10 10.5v3M12 10.5v3M14 10.5v3"/></g></svg>',
  nba: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9.5"/><path d="M12 2.5v19M2.5 12h19M5.6 5.2c2.3 1.9 3.6 4.2 3.6 6.8s-1.3 4.9-3.6 6.8M18.4 5.2c-2.3 1.9-3.6 4.2-3.6 6.8s1.3 4.9 3.6 6.8"/></svg>',
};
const THEME_ICON = {
  auto: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="8"/><path d="M12 4a8 8 0 0 1 0 16z" fill="currentColor"/></svg>',
  light: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>',
  dark: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"/></svg>',
};
// team colours and player photos (data URIs), loaded once from /api/visuals/<sport>
const LOOK = { nfl: { teams: {}, photos: {}, logos: {}, logos_dark: {} }, nba: { teams: {}, photos: {}, logos: {}, logos_dark: {} } };
// transparent logos in two versions (for light and for dark pages); CSS shows the matching one
function logo(sport, abbr, cls = "tl") {
  const u = LOOK[sport]?.logos?.[abbr]; if (!u) return ""; const d = LOOK[sport]?.logos_dark?.[abbr];
  return `<span class="${cls}" aria-hidden="true">${d ? `<img class="lt" src="${u}" alt=""><img class="dk" src="${d}" alt="">` : `<img src="${u}" alt="">`}</span>`;
}
function teamTag(sport, abbr) { return `<span class="tteam">${logo(sport, abbr)}<span>${esc(abbr)}</span></span>`; }
function leagueMark(sport) { return logo(sport, "_league", "lgm") || GLYPH[sport]; }
function paintMarks() { $$(".sport a").forEach((a) => { const m = a.querySelector("svg, .lgm"); if (m) m.outerHTML = leagueMark(a.dataset.sport); }); }
const HEX = /^#[0-9a-fA-F]{3,8}$/;
function teamColors(sport, team) {
  const c = LOOK[sport]?.teams?.[team] || [];
  const a = HEX.test(c[0] || "") ? c[0] : null; let b = HEX.test(c[1] || "") ? c[1] : null;
  if (b && /^#0{3,6}$/.test(b)) b = null; // a black second colour reads as a hole in the ring
  return [a, b];
}
function teamVars(sport, team, n1 = "--t1", n2 = "--t2") { const [a, b] = teamColors(sport, team); return a ? `${n1}:${a};${n2}:${b || a}` : ""; }
const HOST_THEME = document.documentElement.getAttribute("data-theme");
function applyTheme(t) {
  const v = t === "auto" ? HOST_THEME : t;
  if (v) document.documentElement.setAttribute("data-theme", v); else document.documentElement.removeAttribute("data-theme");
}
const NAV_NFL = [["today", "Today"], ["props", "Props"], ["shortlist", "Shortlist"], ["lab", "Backtest lab"], ["defense", "Defences"], ["record", "Saved record"], ["data", "Data & method"]];
const NAV_NBA = [["nba-today", "Tonight"], ["nba-props", "Props"], ["nba-dvp", "Vs position"], ["nba-defense", "Defence"], ["nba-lab", "Model lab"], ["nba-about", "Data & method"]];
let NAV = NAV_NFL;
const NAV_ICON = { "nba-today": "today", "nba-props": "props", "nba-dvp": "dvp", "nba-defense": "defense", "nba-lab": "lab", "nba-about": "data" };
const sportOf = (k) => (k.startsWith("nba-") ? "nba" : "nfl");

function shell() {
  const THEMES = ["auto", "light", "dark"];
  let theme = store.get("theme", "auto"); if (!THEMES.includes(theme)) theme = "auto";
  applyTheme(theme);
  const label = (t) => `${THEME_ICON[t]}<span class="hide-phone">${t === "auto" ? "Auto" : t === "light" ? "Light" : "Dark"}</span>`;
  document.body.insertAdjacentHTML("afterbegin", `<div class="arena" aria-hidden="true"><i></i><i></i><i></i></div>
    <header class="topbar"><div class="topbar-in">
      <a class="brand" href="#" id="brandLink"><span class="mark" aria-hidden="true"></span><b>Props Lab</b></a>
      <div class="sport" role="tablist" aria-label="Sport"><span class="lens" aria-hidden="true"></span><a href="#today" data-sport="nfl" role="tab">${GLYPH.nfl}NFL</a><a href="#nba-today" data-sport="nba" role="tab">${GLYPH.nba}NBA</a></div>
      <span class="spacer"></span>
      <span class="stamp hide-phone" id="modeTag">${SNAP ? "Updated " + esc(SNAP.built_london) : "Live server"}</span>
      <button class="chipbtn" id="themeBtn" aria-label="Colour theme: ${theme}">${label(theme)}</button>
    </div></header>`);
  app.innerHTML = `<div class="layout"><nav class="tabs" aria-label="Sections"></nav><main id="view"></main></div>`;
  $("#themeBtn").addEventListener("click", () => {
    theme = THEMES[(THEMES.indexOf(theme) + 1) % THEMES.length];
    store.set("theme", theme); applyTheme(theme);
    $("#themeBtn").innerHTML = label(theme); $("#themeBtn").setAttribute("aria-label", "Colour theme: " + theme);
  });
}
// the dock's highlight slides to the open section
function moveLens() {
  const nav = $("nav.tabs"); const lens = nav && $(".lens", nav); const a = nav && $("a.on", nav);
  if (!lens) return;
  if (!a || a.offsetParent === null) { lens.style.opacity = "0"; return; }
  lens.style.opacity = "1";
  if (getComputedStyle(nav).display === "flex") { lens.style.width = ""; lens.style.height = a.offsetHeight + "px"; lens.style.transform = `translateY(${a.offsetTop}px)`; }
  else { lens.style.height = ""; lens.style.width = a.offsetWidth + "px"; lens.style.transform = `translateX(${a.offsetLeft}px)`; }
}
window.addEventListener("resize", moveLens);
// entrance order for cards and panels, and a sheen that follows the pointer
function stagger(root) {
  [...root.children].forEach((el, i) => el.style.setProperty("--i", Math.min(i, 8)));
  $$(".cards, .games, .grid", root).forEach((g) => [...g.children].forEach((el, i) => el.style.setProperty("--i", Math.min(i, 14))));
}
document.addEventListener("pointermove", (e) => {
  const el = e.target.closest && e.target.closest(".pcard, .panel"); if (!el) return;
  const r = el.getBoundingClientRect(); el.style.setProperty("--mx", `${e.clientX - r.left}px`); el.style.setProperty("--my", `${e.clientY - r.top}px`);
}, { passive: true });

function renderNav(sport) {
  NAV = sport === "nba" ? NAV_NBA : NAV_NFL;
  const nav = $("nav.tabs"); if (nav.dataset.sport === sport) return;
  nav.dataset.sport = sport;
  nav.style.gridTemplateColumns = `repeat(${Math.min(NAV.length, 4) + (NAV.length > 4 ? 1 : 0)}, 1fr)`;
  document.documentElement.setAttribute("data-sport", sport);
  nav.innerHTML = `<span class="lens" aria-hidden="true"></span>` + NAV.map(([k, l], i) => `<a href="#${k}" data-k="${k}" class="${i > 3 ? "more-only" : ""}">${ICONS[NAV_ICON[k] || k]}<span>${l}</span></a>`).join("") +
    (NAV.length > 4 ? `<a href="#more" data-k="more" class="phone-only">${ICONS.more}<span>More</span></a>` : "");
  $$(".sport a").forEach((a) => a.classList.toggle("on", a.dataset.sport === sport));
  $("#brandLink").setAttribute("href", sport === "nba" ? "#nba-today" : "#today");
}

const VIEWS = { today: viewToday, props: viewProps, shortlist: viewShortlist, lab: viewLab, record: viewRecord, data: viewData, player: viewPlayer, game: viewGame, defense: viewDefense, more: viewMore,
  "nba-today": viewNbaToday, "nba-props": viewNbaProps, "nba-player": viewNbaPlayer, "nba-game": viewNbaGame, "nba-lab": viewNbaLab, "nba-about": viewNbaAbout, "nba-defense": viewNbaDefense, "nba-dvp": viewNbaDvp };
// other spellings of a page; an unknown NBA page opens Tonight, any other unknown page the NFL slate
const ROUTE_ALIAS = { "nba-defence": "nba-defense", defence: "defense", nba: "nba-today", nfl: "today" };
function routeKey(k) { const a = ROUTE_ALIAS[k] || k; return VIEWS[a] ? a : a.startsWith("nba-") ? "nba-today" : "today"; }
function route() {
  hideTip();
  const h = decodeURIComponent(location.hash.replace(/^#/, "")) || (store.get("sport", "nfl") === "nba" ? "nba-today" : "today");
  const [k0, ...rest] = h.split(".");
  const k = routeKey(k0), arg = rest.join(".");
  if (k !== "more") { store.set("sport", sportOf(k)); renderNav(sportOf(k)); } else renderNav(store.get("sport", "nfl"));
  $$("nav.tabs a").forEach((a) => a.classList.toggle("on", a.dataset.k === k || (k === "more" && a.dataset.k === "more")));
  requestAnimationFrame(moveLens);
  const view = $("#view");
  view.innerHTML = `<div class="skeleton">Loading…</div>`;
  const home = sportOf(k) === "nba" ? ["nba-today", "tonight's slate"] : ["today", "this week's slate"];
  Promise.resolve(VIEWS[k](view, arg)).then(() => stagger(view)).catch((e) => {
    // a page the offline copy does not hold is a note, not an error
    view.innerHTML = e instanceof NotInCopy ? banner("info", `<p><b>Not in this offline copy.</b></p><p class="small">The offline copy keeps only some pages; the live app has them all. <a href="#${home[0]}">Back to ${home[1]}</a></p>`)
      : `<div class="banner crit">${ICONS.warn}<div><p><b>Could not load this view.</b></p><p class="small">${esc(e.message)}</p><p class="small">If the server is running, check <code>npl serve</code> output; otherwise this snapshot may not include the page.</p></div></div>`; });
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
  v.innerHTML = `<section class="hero"><div class="medal">${leagueMark("nfl")}</div><span class="eyebrow">${e.season} ${e.season_type === "REG" ? "regular season" : "postseason"} · Week ${e.week}</span>
      <h1>This week's slate</h1>
      <p>Edition <span class="mono">${esc(e.edition_id)}</span>, published ${esc(e.published_at_london)}. ${before} of ${s.games.length} games were still to kick off when it was saved, so only those count in the pre-match record.</p></section>
    ${banner("info", `<p><b>Model v${esc(e.code.model_version)}.</b> ${esc(e.status_note)}</p><p class="small muted">Headline model ${esc(e.headline_model)}: ${esc(MODEL_NAMES[e.headline_model])}. Code ${esc(e.code.git_commit.slice(0, 10))}.</p>`)}
    ${odds.key_configured ? "" : banner("warn", `<p><b>No verified bookmaker prices.</b> No odds key is configured, so there is no value, ROI or "bet" anywhere in the app. Lines you type are labelled <span class="pill manual">manual line — no verified price</span>.</p>`)}
    ${stale || failed ? banner(failed ? "crit" : "warn", `<p><b>Data attention needed:</b> ${[failed ? `${failed} failed or partial job(s)` : "", stale ? `${stale} feed(s) not checked for over 30 hours` : ""].filter(Boolean).join(", ")}. See <a href="#data">Data &amp; method</a>.</p>`) : banner("ok", `<p>All feeds were checked within the last 30 hours and the recent jobs completed. Freshness details are under <a href="#data">Data &amp; method</a>.</p>`)}
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
  return `<a class="panel game lit" href="#game.${esc(g.game_id)}" style="color:inherit;${teamVars("nfl", g.away, "--a1", "--a2")};${teamVars("nfl", g.home, "--h1", "--h2")}">
    <div class="teams">${logo("nfl", g.away, "tl big")}${esc(g.away)}<span class="at">at</span>${logo("nfl", g.home, "tl big")}${esc(g.home)}</div>
    <div class="ko">${esc(g.kickoff_london)}<br><span class="tiny muted">${esc(g.state)}</span></div>
    <div class="vol">${volLine(g.away_sim, g.away)}${volLine(g.home_sim, g.home)}</div>
    <div class="row" style="grid-column:1/-1">${st}${al.map((a) => `<span class="pill ${a.status.toLowerCase()}"><span class="dot"></span>${esc(a.name)} (${esc(a.team)}) ${esc(a.status)}</span>`).join("")}${(g.alerts || []).length > 8 ? `<span class="tiny muted">+${g.alerts.length - 8} more</span>` : ""}</div>
  </a>`;
}

// ---------------------------------------------------------------- Props explorer
function propCard(c, idx, D) {
  if (c.status !== "ok") return "";
  const ha = "v";
  const ms = (c.milestones || []).map((m) => `<span title="P(${MARKET_LABELS[c.market]} ≥ ${m.k})">${m.k}+ <b>${pct(m.p)}</b></span>`).join("");
  const u = c.uncertainty || {};
  return `<article class="panel prop">
    <div class="head">${avatar(c.team, c.name, c.player_id, "nfl")}<div class="who">${nameLink(hasNflPage(c.player_id), `#player.${esc(c.player_id)}`, c.name)}
      <div class="tiny muted">${esc(c.position)} · ${esc(c.team)} ${ha} ${esc(c.opponent)} · ${esc(c.kickoff_london)}</div></div>
      <span class="pill model" title="${esc(MODEL_NAMES[c.model])}">${esc(c.model)}</span></div>
    <div class="row between"><span class="mkt">${esc(c.market_label)}</span>${c.published_before_kickoff ? "" : '<span class="pill"><span class="dot"></span>Saved after kickoff: not in record</span>'}</div>
    <div class="big"><div><div class="v num">${dec(c.mean, 1)}</div><div class="l">mean</div></div>
      <div><div class="v num">${c.median}</div><div class="l">median</div></div>
      <div><div class="v num" style="font-size:1.3rem">${c.q10}–${c.q90}</div><div class="l">80% range</div></div></div>
    ${rangeBar(c)}
    ${c.log && c.log.values.length ? (() => { const { line, src, fair: isFair } = lineFor("nfl", c); const L = new Dist(c.dist).line(line);
      return `<div class="pline"><div><span class="mk">Line</span><button class="linebtn" data-line="${idx}">${line}<small>${src} ${ICONS.pen}</small></button></div></div>${ouBar(L, isFair)}${hitChips(c.log, line, c.opponent)}${miniBars(c.log, line, 10)}`; })() : ""}
    <div class="ms">${ms}</div>
    <div class="foot"><span>P(plays) ${pct(c.p_play)}</span><span>Uncertainty ${esc(u.level || "–")}</span><span>History ${c.sample_size ?? 0} games</span>${injuryPill(c.injury_report)}</div>
    ${oppContext(c, D)}
    <div class="row"><button class="btn ghost" data-open="${idx}">Distribution and manual line</button></div>
  </article>`;
}

async function viewProps(v) {
  const [p, D] = await Promise.all([api("/api/props"), api("/api/defense").catch(() => null)]);
  const cards = p.cards.filter((c) => c.status === "ok");
  const teams = [...new Set(cards.map((c) => c.team))].sort();
  const f = Object.assign({ q: "", team: "", pos: "", market: "", minp: "0.5", sort: "kickoff" }, store.get("propsFilter", {}));
  v.innerHTML = `<section class="hero"><div class="medal">${leagueMark("nfl")}</div><span class="eyebrow">${p.season} Week ${p.week} · edition ${esc(p.edition_id)}</span><h1>Props explorer</h1>
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
    $("#cards").innerHTML = r.slice(0, shown).map((c) => propCard(c, cards.indexOf(c), D)).join("") || `<p class="muted">No forecasts match these filters. Reset them to see the whole slate.</p>`;
    $("#more").hidden = r.length <= shown;
    bindLineButtons($("#cards"), cards, "nfl", apply);
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
    <div class="row between"><div><span class="eyebrow">${esc(c.team)} v ${esc(c.opponent)} · ${esc(c.kickoff_london)}</span><h2>${esc(c.name)}: ${esc(c.market_label)}</h2></div><button class="btn ghost" id="close">Close</button></div>
    <div id="dchart">${c.dist ? distChart(D) : '<p class="muted">Distribution not included in this snapshot.</p>'}</div>
    <p class="tiny muted">Distribution of ${esc(c.market_label.toLowerCase())} if he takes part${c.mix_weight_J0 != null ? `: ${pctInt(c.mix_weight_J0)}% joint simulation (${(c.n_draws_played ?? 0).toLocaleString("en-GB")} of ${(c.n_draws ?? 0).toLocaleString("en-GB")} game draws), ${100 - pctInt(c.mix_weight_J0)}% gradient-boosting model` : c.n_draws ? ` (${(c.n_draws_played ?? 0).toLocaleString("en-GB")} of ${c.n_draws.toLocaleString("en-GB")} game simulations)` : ""}. P(plays) ${pct(c.p_play)}.</p>
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
    $("#st").innerHTML = `<thead><tr><th>#</th><th class="l">Player</th><th class="l">Game</th><th>P(${k}+)</th><th>Mean</th><th>80% range</th><th>History</th></tr></thead><tbody>${rows.map((r, i) => `<tr><td>${i + 1}</td><td class="l">${nameLink(hasNflPage(r.c.player_id), `#player.${esc(r.c.player_id)}`, r.c.name, "plink")} <span class="muted">${esc(r.c.position)}</span></td><td class="l">${esc(r.c.team)} v ${esc(r.c.opponent)}</td><td><b>${pct(r.p)}</b></td><td>${dec(r.c.mean, 1)}</td><td>${r.c.q10}–${r.c.q90}</td><td>${r.c.sample_size} g</td></tr>`).join("")}</tbody>`;
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
  v.innerHTML = `<section class="hero">${avatar(p.team, p.name, p.player_id, "nfl")}<span class="eyebrow">${esc(pos)} · ${esc(p.team)}</span><h1>${esc(p.name)}</h1><p class="small muted">GSIS id <span class="mono">${esc(p.player_id)}</span>${p.rookie_season ? ` · rookie season ${esc(p.rookie_season)}` : ""}</p></section>
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
  const [g, D] = await Promise.all([api(`/api/game/${id}`), api("/api/defense").catch(() => null)]);
  const G = g.game;
  const stats = [["plays", "Offensive plays"], ["dropbacks", "Dropbacks"], ["attempts", "Pass attempts"], ["completions", "Completions"], ["passing_yards", "Passing yards"], ["sacks", "Sacks"], ["scrambles", "Scrambles"], ["designed_runs", "Designed runs"], ["kneels", "Kneels"]];
  const T = Object.fromEntries(g.teams.map((t) => [t.team, t]));
  const byTeam = (team) => g.cards.filter((c) => c.team === team && c.status === "ok");
  const keyMk = { QB: ["pass_yds", "pass_att", "rush_yds"], RB: ["rush_yds", "rush_att", "receptions"], FB: ["rush_att", "receptions"], WR: ["rec_yds", "receptions"], TE: ["rec_yds", "receptions"] };
  const players = (team) => { const by = {}; byTeam(team).forEach((c) => { (by[c.player_id] ||= { c, m: {} }).m[c.market] = c; });
    return Object.values(by).sort((a, b) => ["QB", "RB", "WR", "TE", "FB"].indexOf(a.c.position) - ["QB", "RB", "WR", "TE", "FB"].indexOf(b.c.position) || b.c.p_play - a.c.p_play);
  };
  const ptable = (team) => `<div class="tablewrap"><table><thead><tr><th class="l">Player</th><th>P(plays)</th><th class="l">Key forecasts (median, 80% range)</th></tr></thead><tbody>${players(team).filter((x) => x.c.p_play >= 0.05).map((x) => `<tr class="${x.c.p_play < 0.5 ? "dim" : ""}"><td class="l">${nameLink(hasNflPage(x.c.player_id), `#player.${esc(x.c.player_id)}`, x.c.name, "plink")} <span class="muted">${esc(x.c.position)}</span> ${injuryPill(x.c.injury_report)}</td><td>${pct(x.c.p_play)}</td><td class="l">${(keyMk[x.c.position] || []).filter((m) => x.m[m]).map((m) => `${MARKET_LABELS[m]} ${x.m[m].median} (${x.m[m].q10}–${x.m[m].q90})`).join(" · ")}</td></tr>`).join("")}</tbody></table></div>`;
  v.innerHTML = `<section class="hero"><span class="eyebrow">${esc(G.kickoff_london)} · edition ${esc(g.edition_id)}</span><h1>${esc(G.away)} at ${esc(G.home)}</h1>
      <p>${G.published_before_kickoff ? "Saved before kickoff: part of the pre-match record." : "This edition was saved after kickoff, so the game is excluded from the pre-match record."}</p></section>
    <section class="panel"><h2>Simulated team volume</h2><p class="small muted">From ${esc(g.sim_model || g.model)} joint simulations of the whole game${g.sim_model && g.sim_model !== g.model ? ` (the game simulation inside ${esc(g.model)})` : ""}. Both teams share game-level noise: in history, when one offence ran more plays the other ran fewer (residual correlation about −0.48), and pass rates move in opposite directions with game script.</p>
      <div class="tablewrap"><table><thead><tr><th class="l">Per team</th>${[G.away, G.home].map((t) => `<th>${esc(t)} mean</th><th>${esc(t)} 80% range</th>`).join("")}</tr></thead><tbody>${stats.map(([k, l]) => `<tr><td class="l">${l}</td>${[G.away, G.home].map((t) => { const s = T[t]?.sim?.[k]; return s ? `<td>${dec(s.mean, 1)}</td><td>${s.q10}–${s.q90}</td>` : "<td>–</td><td>–</td>"; }).join("")}</tr>`).join("")}</tbody></table></div>
      <p class="tiny muted">Inputs: ${[G.away, G.home].map((t) => { const i = T[t]?.inputs || {}; return `${esc(t)} expected plays ${dec(i.mu_plays, 1)}, dropback rate ${pct1(i.dropback_rate)}, opponent pass-yards factor ${dec(i.opp_rec_yards_factor, 2)}`; }).join("; ")}.</p></section>
    ${D && D.teams && D.teams[G.home] && D.teams[G.away] ? `<section class="panel"><h2>Defensive matchup</h2><p class="small muted">Each defence's rank (1 = best of 32) in ${D.season} before week ${D.before_week}, adjusted for opponents. Bold = clear evidence; plain = could be noise.</p>
      <div class="tablewrap"><table class="pit"><thead><tr><th class="l">Defence</th><th>Games</th>${D.heat_columns.map((c) => `<th>${HEAT_SHORT[c]}</th>`).join("")}</tr></thead><tbody>${[G.home, G.away].map((tm) => `<tr><td class="l"><a href="#defense.${esc(tm)}">${esc(tm)}</a> <span class="muted">faces ${esc(tm === G.home ? G.away : G.home)}</span></td><td>${D.teams[tm].games}</td>${D.heat_columns.map((c) => rankCell(D.teams[tm].metrics[c], tm)).join("")}</tr>`).join("")}</tbody></table></div></section>` : ""}
    <div class="grid"><section class="panel"><h2>${esc(G.away)}</h2>${ptable(G.away)}</section><section class="panel"><h2>${esc(G.home)}</h2>${ptable(G.home)}</section></div>
    <section class="panel" id="scen"><h2>What if? (exploratory)</h2>
      <p class="small muted">Remove players and re-run this game's joint simulation with the same inputs and seed. Their volume is shared among the teammates left, in proportion to their usual shares. The result is a labelled exploration: it never changes a published edition, and stacking is not applied.</p>
      ${SNAP ? banner("info", "<p>Scenarios run on the local server (<code>uv run npl serve</code>); this snapshot cannot simulate.</p>") : `<form id="sf" class="stack"><div class="row">${[G.away, G.home].map((t) => `<fieldset style="border:1px solid var(--rule);border-radius:8px;padding:8px 10px;min-width:0"><legend class="eyebrow">${esc(t)}</legend>${players(t).filter((x) => x.c.p_play >= 0.05).map((x) => `<label class="small" style="display:flex;gap:6px;align-items:center"><input type="checkbox" name="u" value="${esc(x.c.player_id)}">${esc(x.c.name)} <span class="muted">${esc(x.c.position)}</span></label>`).join("")}</fieldset>`).join("")}</div><div class="row"><button class="btn" type="submit">Run scenario</button><span class="tiny muted">At most 12 players; each team keeps at least one quarterback.</span></div></form><div id="sout"></div>`}
    </section>`;
  if (!SNAP) $("#sf").addEventListener("submit", async (e) => {
    e.preventDefault(); const ids = $$("#sf input[name=u]:checked").map((x) => x.value);
    const out = $("#sout"); if (!ids.length) { out.innerHTML = `<p class="small err">Tick at least one player to remove.</p>`; return; }
    out.innerHTML = `<p class="small muted">Simulating…</p>`;
    const r = await fetch(`/api/scenario/${encodeURIComponent(id)}?unavailable=${encodeURIComponent(ids.join(","))}`);
    const d = await r.json(); if (!r.ok) { out.innerHTML = `<p class="small err">${esc(d.detail || "Scenario failed")}</p>`; return; }
    const ch = (o) => o.base == null || o.scenario == null ? "–" : `${dec(o.base, 1)} → <b>${dec(o.scenario, 1)}</b>`;
    out.innerHTML = `<div class="row"><span class="pill manual">${esc(d.label)}</span><span class="tiny muted">${esc(d.model)}; ${d.n_draws.toLocaleString("en-GB")} draws, seed ${d.seed}.</span></div>
      <div class="tablewrap"><table><thead><tr><th class="l">Player</th><th>P(plays)</th><th>Targets</th><th>Receiving yds</th><th>Carries</th><th>Rushing yds</th><th>Pass att</th><th>Passing yds</th></tr></thead><tbody>${d.players.filter((p) => p.p_play_base >= 0.05).map((p) => `<tr class="${p.removed ? "dim" : ""}"><td class="l">${esc(p.name)} <span class="muted">${esc(p.position)} ${esc(p.team)}</span>${p.removed ? " (removed)" : ""}</td><td>${pct(p.p_play_base)} → ${pct(p.p_play_scenario)}</td><td>${ch(p.targets)}</td><td>${ch(p.receiving_yards)}</td><td>${ch(p.carries)}</td><td>${ch(p.rushing_yards)}</td><td>${ch(p.attempts)}</td><td>${ch(p.passing_yards)}</td></tr>`).join("")}</tbody></table></div>
      <p class="tiny muted">Means if each player takes part, before → after. Team volume is unchanged by personnel in this model (a known limitation).</p>
      ${d.joint.length ? `<h3>How outcomes move together</h3><p class="small muted">Correlation between the starting quarterback's passing yards and each receiver's yards across simulated games. Positive values mean their Overs tend to land together, so multiplying their separate probabilities would misstate a combined bet.</p><div class="tablewrap"><table><thead><tr><th class="l">Quarterback</th><th class="l">Receiver</th><th>Correlation</th></tr></thead><tbody>${d.joint.map((j) => `<tr><td class="l">${esc(j.qb)} (${esc(j.team)})</td><td class="l">${esc(j.receiver)}</td><td>${dec(j.corr, 2)}</td></tr>`).join("")}</tbody></table></div>` : ""}`;
  });
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
    const pitCell = (v) => { const d = v - 0.1; const cls = d > 0.03 ? "hi2" : d > 0.012 ? "hi" : d < -0.03 ? "lo2" : d < -0.012 ? "lo" : ""; return `<td class="c ${cls}" data-tip="${pct1(v)} of outcomes|calibrated = 10%">${pctInt(v)}</td>`; };
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
      ${run.participation ? `<section class="panel"><h2>Participation probability</h2><p class="small muted">How well P(plays) predicted who took part, over game-day candidates (played, inactive or did not play). Lower Brier and log loss are better. All other metrics on this page are conditional on taking part.</p>
      <div class="tablewrap"><table><thead><tr><th class="l">Model</th><th>Player-games</th><th>Brier</th><th>Log loss</th><th>Mean P(plays)</th><th>Played</th><th>On injury report</th><th>Brier (listed)</th><th>Log loss (listed)</th></tr></thead><tbody>${Object.entries(run.participation).sort((a, b) => MODEL_ORDER.indexOf(a[0]) - MODEL_ORDER.indexOf(b[0])).map(([m, x]) => `<tr><td class="l"><span class="sw" style="background:var(--m-${m})"></span>${esc(m)}</td><td>${x.n.toLocaleString("en-GB")}</td><td>${dec(x.brier, 4)}</td><td>${dec(x.log_loss, 4)}</td><td>${pct1(x.mean_p)}</td><td>${pct1(x.played_rate)}</td><td>${x.listed_n.toLocaleString("en-GB")}</td><td>${dec(x.listed_brier, 4)}</td><td>${dec(x.listed_log_loss, 4)}</td></tr>`).join("")}</tbody></table></div></section>` : ""}
      <section class="panel"><h2>What was not scored</h2><p class="small muted">Rows excluded from every metric, by actual participation and forecast status. Inactive and DNP players are void for props under the usual rules, so they are not counted as Unders.</p>
      <div class="tablewrap"><table><thead><tr><th class="l">Model</th><th class="l">Participation</th><th class="l">Forecast status</th><th>Rows</th></tr></thead><tbody>${(run.exclusions || []).sort((a, b) => b.n - a.n).slice(0, 30).map((e) => `<tr><td class="l">${esc(e.model)}</td><td class="l">${esc(e.participation)}</td><td class="l">${esc(e.status)}</td><td>${e.n.toLocaleString("en-GB")}</td></tr>`).join("")}</tbody></table></div></section>`;
  }
  $("#lr").addEventListener("change", (e) => { store.set("labRun", e.target.value); route(); });
  $("#lm").addEventListener("change", (e) => { store.set("labMkt", e.target.value); route(); });
  $("#lt").addEventListener("change", (e) => { store.set("labTab", e.target.value); route(); });
}

// ---------------------------------------------------------------- prop research components (NFL + NBA)
// Hit rates count past games where the stat went OVER the line; the line is the user's own
// (saved per player and market), else the model's fair line: the half point at which its
// over and under chances are closest to 50/50. No bookmaker line is invented.
const lineKey = (sport, c) => `line:${sport}:${c.player_id}:${c.market}`;
function modelLine(c) {
  if (!c.dist) return Math.floor(c.mean) + 0.5;
  const D = new Dist(c.dist); let best = c.median + 0.5, gap = 2;
  for (let L = c.median - 1.5; L <= c.median + 1.5; L += 1) { if (L < 0) continue; const g = Math.abs(D.line(L).over - 0.5); if (g < gap) { gap = g; best = L; } }
  return best;
}
function lineFor(sport, c) {
  const v = store.get(lineKey(sport, c), null);
  if (v != null) return { line: v, src: "your line", fair: false };
  if (c.market_line) return { line: c.market_line.line, src: "US market line", fair: false, market: true };
  return { line: modelLine(c), src: "fair line", fair: true };
}
function leanLabel(L, fair) { if (fair) return `<span class="lean">Model's 50/50 line</span>`; return L.over >= 0.55 ? `<span class="lean over">Model leans over</span>` : L.under >= 0.55 ? `<span class="lean under">Model leans under</span>` : `<span class="lean">Close to 50/50</span>`; }
function ouBar(L, fairLine) { const [o, u] = ouPair(L.over, L.under, L.push); return `<div class="ou"><div class="bar2"><span class="o" style="width:${(L.over * 100).toFixed(1)}%"></span></div><div class="row between tiny"><span>Over <b>${o}</b></span>${leanLabel(L, fairLine)}<span>Under <b>${u}</b></span></div></div>`; }
function hitRate(vals, line) { if (!vals.length) return null; const over = vals.filter((v) => v > line).length; return { over, n: vals.length, r: over / vals.length }; }
function hitChips(log, line, opp) {
  const v = log.values || [];
  const cur = v.filter((_, i) => (log.season || [])[i] === log.current_season);
  const h2h = v.filter((_, i) => (log.opp || [])[i] === opp);
  const sets = [["L5", v.slice(-5)], ["L10", v.slice(-10)], ["L20", v.slice(-20)], ["Season", cur], ["H2H", h2h]];
  return `<div class="hits">${sets.map(([k, s]) => { const h = hitRate(s, line);
    const cls = !h ? "na" : h.r >= 0.6 ? "hot" : h.r <= 0.4 ? "cold" : "mid";
    return `<span class="hit ${cls}" style="--k:${sets.findIndex((x) => x[0] === k)}" data-tip="${esc(k)}: ${h ? `${h.over} of ${h.n} games over ${line}` : "no games"}"><i>${k}</i><b>${h ? pctInt(h.r) + "%" : "–"}</b><em>${h ? `${h.over}/${h.n}` : ""}</em></span>`; }).join("")}</div>`;
}
function miniBars(log, line, n = 10, { width = 300, height = 92, labels = true } = {}) {
  const v = (log.values || []).slice(-n), o = (log.opp || []).slice(-n), d = (log.date || []).slice(-n);
  if (!v.length) return `<p class="tiny muted">No previous games.</p>`;
  const top = Math.max(...v, line) * 1.18 || 1;
  const m = { t: 12, b: labels ? 16 : 4 }, H = height - m.t - m.b, slot = width / v.length, bw = Math.min(26, slot - 4);
  const Y = (x) => m.t + H - (x / top) * H;
  let s = `<svg class="chart minibars" viewBox="0 0 ${width} ${height}" role="img" aria-label="Last ${v.length} games against the line ${line}">`;
  v.forEach((x, i) => { const cx = slot * (i + 0.5), h = Math.max(1.5, m.t + H - Y(x)), cls = x > line ? "over" : x < line ? "under" : "push";
    s += `<rect class="${cls}" style="--j:${i}" x="${cx - bw / 2}" y="${m.t + H - h}" width="${bw}" height="${h}" rx="3" data-tip="${esc(x)}|${esc(d[i] || "")} v ${esc(o[i] || "")}"/>`;
    if (bw >= 14) s += `<text class="bv" x="${cx}" y="${m.t + H - h - 3}" text-anchor="middle">${x}</text>`;
    if (labels) s += `<text class="bl" x="${cx}" y="${height - 4}" text-anchor="middle">${esc((o[i] || "").slice(0, 4))}</text>`; });
  const ly = Y(line);
  s += `<line class="lline" x1="0" x2="${width}" y1="${ly}" y2="${ly}"/><text class="lval" x="${width - 2}" y="${ly - 3}" text-anchor="end">${line}</text>`;
  return s + "</svg>";
}
function avatar(team, name, pid, sport) {
  const ini = String(name || "").split(/\s+/).map((w) => w[0]).filter(Boolean).slice(0, 2).join("");
  const ph = pid && LOOK[sport]?.photos?.[pid];
  return `<span class="ava" style="${teamVars(sport, team)}" aria-hidden="true">${ph ? `<img src="${ph}" alt="" decoding="async">` : `<b>${esc(ini)}</b>`}${logo(sport, team) ? `<i class="lg">${logo(sport, team, "")}</i>` : `<i>${esc(team)}</i>`}</span>`;
}
function rankTone(rank, n = 30) { return rank >= n - 7 ? "soft" : rank <= 8 ? "tough" : "avg"; }

// ---------------------------------------------------------------- NBA
const NBA_MKT = { pts: "Points", reb: "Rebounds", ast: "Assists", fg3m: "3-pointers made", pra: "Pts + Reb + Ast" };
const NBA_SHORT = { pts: "PTS", reb: "REB", ast: "AST", fg3m: "3PM", pra: "PRA" };

// an injury field the source leaves unspecified reads as empty (as npl.nba.extras._spec)
const UNSPEC = new Set(["", "nan", "none", "not specified", "unknown"]);
const spec = (v) => (v == null || UNSPEC.has(String(v).trim().toLowerCase()) ? "" : String(v));
function nbaInjury(inj) {
  if (!inj) return "";
  const out = ["OUT", "OFS"].includes(inj.fantasy); const r = inj.report;
  const snap = r ? `|Snapshot ${esc(r.as_of_date || "")}: ${esc(r.status || "listed")}${r.label ? " · " + esc(r.label) : ""}${r.return_date ? " · expected back " + esc(dayFmt(r.return_date, true)) : ""}` : "";
  return `<span class="pill ${out ? "out" : "questionable"}" data-tip="Injury report (${esc(String(inj.as_of || "").includes("T") ? londonTime(inj.as_of) : (inj.as_of || "date unknown"))})|${esc(inj.status)}${spec(inj.detail) ? " · " + esc(spec(inj.detail)) : ""}${snap}"><span class="dot"></span>${out ? "Out" : "Game-time decision"}</span>`;
}

function nbaCard(c, idx, cal) {
  const { line, src, fair: isFair } = lineFor("nba", c);
  const ch = nbaChance(c, line, cal);
  const o = c.opp_factor;
  // minutes shift of 1 or more, shown as the minutes as shown less the minutes without it as shown
  const ms = c.minutes_lineup_shift, minShift = ms && Math.abs(ms) >= 1 ? minusShown(c.minutes_expected, c.minutes_expected - ms, 0) : 0;
  return `<article class="pcard">
    <header>${avatar(c.team, c.name, c.player_id, "nba")}<div class="who">${nameLink(hasNbaPage(c.player_id), `#nba-player.${esc(c.player_id)}.${esc(c.market)}`, c.name)}
      <div class="sub">${esc(c.position)} · ${esc(c.team)} ${c.is_home ? "v" : "@"} ${esc(c.opponent)} · <span class="nw">${esc(c.tip_london)}</span></div></div>${nbaInjury(c.injury)}</header>
    <div class="pline"><div><span class="mk">${esc(NBA_MKT[c.market])}</span><button class="linebtn" data-line="${idx}" aria-label="Change the line">${line}<small>${src} ${ICONS.pen}</small></button></div>
      <div class="proj"><b>${dec(c.mean, 1)}</b><span>projection</span></div></div>
    ${nbaOuBar(ch, isFair)}
    ${usPrices(c)}
    ${hitChips(c.log, line, c.opponent)}
    ${miniBars(c.log, line, 10)}
    <div class="facts"><span data-tip="Chance he plays|${c.injury ? "Includes the injury report (assumed effect, not validated)" : "From recent appearances"}">Plays ${pct(c.p_play)}</span><span data-tip="Expected minutes|${c.minutes_lineup_shift ? `Includes ${pm1(c.minutes_lineup_shift)} for teammates out tonight or back from absences` : "No change for teammates in or out"}">Min ${dec(c.minutes_expected, 0)}${minShift ? ` <small>(${pm2(minShift)})</small>` : ""}</span><span>80% range ${c.q10}–${c.q90}</span>${c.simple != null ? `<span data-tip="Simple baseline|Season average x opponent allowed x rest days x minutes trend. A rule-of-thumb check, not the model: its line gaps have not beaten the market so far.">Simple ${dec(c.simple, 1)}</span>` : ""}
      ${o ? `<span class="mu ${rankTone(o.rank)}" data-tip="${esc(c.opponent)} defence v ${esc(NBA_SHORT[c.market === "pra" ? "pts" : c.market])}|Rank ${o.rank} of 30 by what it allows per possession (1 = allows least); model factor ${pm1(shift(o.factor, 2) - 100)}%">Matchup ${o.rank}/30</span>` : ""}
      ${dvpChip(c)}
      ${c.opp_pace ? `<span data-tip="${esc(c.opponent)} pace|${c.opp_pace.value} possessions per 48 min (rank ${c.opp_pace.rank})">Pace ${c.opp_pace.rank}/30</span>` : ""}</div>
    ${c.teammates_out && c.teammates_out.length ? `<div class="warnline">${ICONS.warn}<span>${c.teammates_out.map((t) => esc(t.name)).join(", ")} ruled out after playing last game: extra minutes and shots are not in this projection.</span></div>` : ""}
    ${wowyBlock(c, line)}
    <button class="btn ghost ctxbtn" data-ctx="${idx}" aria-haspopup="dialog">Context: minutes, game script, v ${esc(c.opponent)}</button>
  </article>`;
}

// With/without splits against teammates ruled out tonight (this season and last, same team)
function wowyBlock(c, line) {
  if (!c.wowy || !c.wowy.length) return "";
  const cell = (v, mins) => { if (!v.length) return `<span class="muted">no games yet</span>`;
    const avg = v.reduce((a, b) => a + b, 0) / v.length, h = hitRate(v, line);
    return `<b>${dec(avg, 1)}</b> avg <span class="muted">· over ${line} in ${h.over} of ${h.n} · ${dec(mins, 0)} min</span>`; };
  return `<div class="wowy">${c.wowy.map((w) => `<div class="wrow"><span class="wk">Without ${esc(w.name)}</span><span>${cell(w.without, w.min_without)}</span></div>
    <div class="wrow"><span class="wk">With ${esc(w.name.split(" ").slice(-1)[0])}</span><span>${cell(w.with, w.min_with)}</span></div>`).join("")}
    <p class="tiny muted">${esc(String(c.wowy[0].seasons.map((y) => `${y - 1}-${String(y).slice(2)}`).join(" and ")))} games for ${esc(c.team)}, latest 20 each way. A handful of games without him is a weak guide.</p></div>`;
}

// ---------------------------------------------------------------- NBA research context
// US prices on the card face, a lean label from the calibrated chance, and a context block
// (injuries, volume, game script, the opponent, his games against tonight's opponent). Every
// figure is read from the card or the props payload's `extras` (npl.nba.extras); a missing
// figure shows as missing. US prices are a research reference that no UK customer can take.
const NBA_STATS = ["pts", "reb", "ast", "fg3m", "pra"];
const BOOKS = { betmgm: "BetMGM", draftkings: "DraftKings", fanduel: "FanDuel", williamhill_us: "Caesars", betrivers: "BetRivers", fanatics: "Fanatics", ballybet: "Bally Bet", hardrockbet: "Hard Rock Bet", betparx: "betPARX", betonlineag: "BetOnline", bovada: "Bovada", lowvig: "LowVig", mybookieag: "MyBookie", betus: "BetUS", fliff: "Fliff" };
const bookName = (k) => (k ? BOOKS[k] || String(k).replace(/_/g, " ") : "");
const odds2 = (x) => dec(x, 2);
const TINY = 5; // fewer games or player-games than this: "tiny sample"
const tinyTag = (n) => (n != null && n > 0 && n < TINY ? `<span class="tinys">tiny sample</span>` : "");
const LEAN_PP = 3; // model and market chances (whole per cent, as shown) under 3 percentage points apart: "No lean"
const THIN_BOOKS = 3; // fewer books than this at the line, or no regulated best price: a thin market, no lean
const NBA_LONG = { pts: "points", reb: "rebounds", ast: "assists", fg3m: "3-pointers made", pra: "points + rebounds + assists" };
// The props payload's `calibration` (npl.nba.calib through appdata): the V4 slope per market for
// the edition's model version and the slope below which a market has no model view at the
// bookmakers' line (D-043). null, missing or for another version: no calibrated chance at all.
const nbaCal = (P) => { const k = P && P.calibration; return k && k.slope && (!P.model_version || k.version === P.model_version) ? k : null; };
const flatMarkets = (cal) => (cal ? NBA_STATS.filter((k) => cal.slope[k] != null && cal.slope[k] < cal.flat_below) : []);
function dayFmt(iso, year = false) {
  if (!iso) return "–"; const d = new Date(`${String(iso).slice(0, 10)}T12:00:00Z`); if (isNaN(d)) return String(iso);
  return new Intl.DateTimeFormat("en-GB", { timeZone: "UTC", day: "numeric", month: "short", ...(year ? { year: year === true ? "numeric" : year } : {}) }).format(d);
}
function shortTime(iso) { return iso ? new Intl.DateTimeFormat("en-GB", { timeZone: "Europe/London", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }).format(new Date(iso)) : "–"; }

// What a card shows at its line (D-043). Away from the bookmakers' line (a line you typed, or the
// model's 50/50 line) only the raw model exists. At the bookmakers' line only the calibrated
// chance is shown, because the raw model is far too confident there. kind:
//   raw     the raw model at a line with no market
//   nocal   bookmakers' line, but this model version has no calibration
//   flat    bookmakers' line, calibration slope below flat_below: no model view
//   thin    fewer than 3 books or no regulated best price: no lean (calibrated chance still shown)
//   nopcal  bookmakers' line with no calibrated chance stored
//   lean    calibrated model v market; gap = their difference in whole percentage points, as shown
function nbaChance(c, line, cal) {
  const L = new Dist(c.dist).line(line); const m = c.market_line;
  const base = { ...L, raw: L.over, mk: c.market, show: true, cal: false };
  if (!m || m.line !== line) return { ...base, kind: "raw", hasCal: !!cal };
  const at = { ...base, show: false, market: m.fair_over, books: m.books };
  const b = cal ? cal.slope[c.market] : null;
  if (b == null) return { ...at, kind: "nocal" };
  if (b < cal.flat_below) return { ...at, kind: "flat", slope: b, below: cal.flat_below };
  const pc = c.p_cal_market;
  const calib = pc == null ? at : { ...at, over: pc, under: Math.max(0, 1 - pc - L.push), show: true, cal: true };
  if (m.books < THIN_BOOKS || m.best_over == null || m.best_under == null || m.fair_over == null) return { ...calib, kind: "thin" };
  if (pc == null) return { ...at, kind: "nopcal" };
  return { ...calib, kind: "lean", gap: pctInt(pc) - pctInt(m.fair_over), exact: pc - m.fair_over };
}
// a description of model v market, never a pick: no gap has made money on real past lines (D-043)
function nbaLean(ch, isFair) {
  const tag = (txt, tipTxt, cls = "") => `<span class="lean${cls ? " " + cls : ""}"${tipTxt ? ` data-tip="${esc(tipTxt)}"` : ""}>${txt}</span>`;
  const what = NBA_LONG[ch.mk] || ch.mk;
  if (ch.kind === "raw") return tag(isFair ? "Model's 50/50 line" : "No market at this line");
  if (ch.kind === "nocal") return tag("No calibrated chance for this model", "No calibrated chance|This model version has no calibration on real bookmakers' lines, so the card shows no chance at the US line. A line you type shows the raw model.");
  if (ch.kind === "flat") return tag("No model view at this line", `No model view|Tested on real lines, the calibrated chance for ${what} barely follows the model (slope ${dec(ch.slope, 2)}; below ${dec(ch.below, 1)} counts as no view), so a gap to the market would mostly show the price.`);
  if (ch.kind === "thin") return tag(`No lean: thin market (${plural(ch.books, "book")})`, `Thin market|${plural(ch.books, "US book")} at this line${ch.books >= THIN_BOOKS ? ", but no regulated best price" : ""}. Setting the model against the market needs ${THIN_BOOKS} or more books and a regulated best price on both sides.`);
  if (ch.kind === "nopcal") return tag("No calibrated chance at this line", "No calibrated chance|None was stored for this line, so the model is not set against the market here.");
  const vs = `model ${pct(ch.over)} v market ${pct(ch.market)}`, gap = Math.abs(ch.gap);
  const tipTxt = `Model v market|Calibrated model ${pct(ch.over)} v market ${pct(ch.market)} over (margin removed): ${plural(gap, "percentage point")} apart. Under ${LEAN_PP} apart: no lean. Not a pick: gaps like this have not made money on past lines.`;
  if (gap < LEAN_PP) return tag(`No lean: ${vs}`, tipTxt);
  return tag(`Leans ${ch.gap > 0 ? "over" : "under"}: ${vs}`, tipTxt, ch.gap > 0 ? "over" : "under");
}
function calNote(ch) {
  const what = NBA_LONG[ch.mk] || ch.mk;
  if (ch.kind === "raw") return ch.hasCal ? "Raw model chance (calibrated only at a US line)." : "Raw model chance (this model version has no calibration).";
  if (ch.kind === "nocal") return "The raw model is far too confident at bookmakers' lines, so none is shown. Type a line for the raw model.";
  if (ch.kind === "flat") return `On real bookmakers' lines the model added almost nothing for ${esc(what)}. Type a line for the raw model.`;
  if (ch.kind === "nopcal") return "No calibrated chance at this line. Type a line for the raw model.";
  return ch.show ? `Calibrated chance at the US line (raw model ${pct(ch.raw)} over).` : "No calibrated chance at this line.";
}
function nbaOuBar(ch, isFair) {
  const nil = !ch.show; // no model chance to show at this line
  const [o, u] = nil ? ["–", "–"] : ouPair(ch.over, ch.under, ch.push);
  return `<div class="ou${nil ? " nil" : ""}"><div class="bar2">${nil ? "" : `<span class="o" style="width:${(ch.over * 100).toFixed(1)}%"></span>`}</div>
    <div class="row between tiny"><span>Over <b>${o}</b></span><span>Under <b>${u}</b></span></div>
    <div class="leanrow">${nbaLean(ch, isFair)}</div>
    <div class="tiny muted cal">${calNote(ch)}</div></div>`;
}
// best regulated prices with their books, and BetMGM's pair when its main line is the consensus line
function usPrices(c) {
  const m = c.market_line; if (!m) return "";
  const uk = m.availability === "verified_uk";
  const best = (s) => (m[`best_${s}`] != null ? `<b>${odds2(m[`best_${s}`])}</b> <span class="bk">${esc(bookName(m[`best_${s}_book`]))}</span>` : `<span class="bk">none</span>`);
  const mgm = m.mgm_over != null && m.mgm_under != null ? `<span><b>${odds2(m.mgm_over)}</b></span><span><b>${odds2(m.mgm_under)}</b></span>`
    : `<span class="bk wide">${m.mgm_line != null ? `main line ${esc(m.mgm_line)}: no price at ${esc(m.line)}` : "no line"}</span>`;
  return `<div class="usp" role="group" aria-label="${uk ? "UK" : "US"} bookmaker prices at ${esc(m.line)}">
    <div class="usp-h"><b>${uk ? "UK" : "US"} prices at ${esc(m.line)}</b><span>${uk ? "UK books" : "research only · not for UK customers"}</span></div>
    <div class="usp-g"><span></span><span class="hd">Over</span><span class="hd">Under</span>
      <span class="k" data-tip="Best price|Highest price at a state-regulated US book on its main line (offshore books are left out)">Best</span><span>${best("over")}</span><span>${best("under")}</span>
      <span class="k">BetMGM</span>${mgm}</div>
    <div class="usp-f">Market ${pct(m.fair_over)} over (margin removed) · ${m.books} book${m.books === 1 ? "" : "s"} · <span class="nw">${esc(shortTime(m.observed_at))}</span>${m.arb ? " · the best prices imply an arbitrage: one is probably stale" : ""}</div>
    <div class="usp-n">Lean = model v market, not a pick</div></div>`;
}

// one slate player's context: his block in the props `extras` plus what his card points to
function nbaCtx(c, X) {
  const me = c && X && X.players ? X.players[c.player_id] : null;
  if (!me) return null;
  return { ...me, defence: X.defence?.[c.opponent] || null, pos_v_opp: X.pos_v_opp?.[`${c.opponent}|${c.position}`] || null,
    game_lines: X.game_lines?.[c.game_id] || null, league: X.league || {}, seasons: X.seasons || {}, stats: X.stats || NBA_STATS, notes: X.notes || {} };
}
const seasonLab = (S, s) => (String(s) === String(S.current) ? S.current_label : String(s) === String(S.last) ? S.last_label : String(s));
const kvl = (rows) => `<dl class="cxkv">${rows.map(([k, val]) => `<dt>${k}</dt><dd>${val}</dd>`).join("")}</dl>`;
const plural = (n, w) => `${n} ${w}${n === 1 ? "" : "s"}`;

// the SportsDataverse snapshot's structured fields (never its comment text)
function injDesc(r) {
  if (!r) return `Snapshot: <span class="muted">not listed</span>`;
  return `Snapshot${r.as_of_date ? ` ${esc(dayFmt(r.as_of_date))}` : ""}: ` + ([r.status ? `<b>${esc(r.status)}</b>` : "", r.label ? esc(r.label) : "",
    r.return_date ? `expected back ${esc(dayFmt(r.return_date, true))}` : ""].filter(Boolean).join(" · ") || `<span class="muted">listed, no detail</span>`);
}
// the edition's status and detail ("Day-To-Day · Hip"). A teammate's come from his own entry in
// the same edition: his card, else the game's player list (an offline copy keeps every player
// there but trims the cards); failing both, the snapshot's body part for him.
const statusLine = (status, detail) => `Edition: <b>${esc(status || "listed")}</b>${spec(detail) ? ` · ${esc(spec(detail))}` : ""}`;
function editionInjury(pid, gid) {
  const card = cache.get("/api/nba/props")?.cards?.find((x) => x.player_id === pid && x.injury);
  if (card) return card.injury;
  const g = cache.get(`/api/nba/game/${gid}`) || (SNAP ? SNAP.data[`/api/nba/game/${gid}`] : null);
  return g?.players?.find((x) => x.player_id === pid)?.injury || null;
}
function cxInjury(c) {
  if (!c) return ""; const tm = c.teammates_out || []; const rows = [];
  if (c.injury) rows.push([esc(c.name), `${statusLine(c.injury.status, c.injury.detail)}<br>${injDesc(c.injury.report)}`]);
  tm.forEach((t) => { const own = editionInjury(t.player_id, c.game_id);
    rows.push([esc(t.name), `${statusLine(t.status || own?.status || "Out", spec(t.detail) || spec(own?.detail) || spec(t.report?.type))}<br>${injDesc(t.report)}`]); });
  return rows.length ? `<section class="cx"><h4>Injuries</h4>${kvl(rows)}${tm.length ? `<p class="tiny muted">Teammates ruled out after playing his team's last game: their minutes and shots are not shared out in the projection.</p>` : ""}</section>` : "";
}
function cxVolume(x) {
  const V = x.volume || {}, S = x.seasons || {};
  if (!V.season && !V.last5 && !V.last_season) return "";
  const cols = [["season", S.current_label || "This season"], ["last5", "Last 5"], ["last_season", S.last_label || "Last season"]];
  const row = (label, f) => `<tr><td>${label}</td>${cols.map(([k]) => `<td>${V[k] ? f(V[k]) : "–"}</td>`).join("")}</tr>`;
  return `<section class="cx"><h4>Volume</h4><div class="tablewrap"><table class="cxt"><thead><tr><th>Per game</th>${cols.map(([k, l]) => `<th>${esc(l)}<small>${V[k] ? plural(V[k].games, "game") : "no games"}</small>${V[k] ? tinyTag(V[k].games) : ""}</th>`).join("")}</tr></thead><tbody>
    ${row("Minutes", (b) => dec(b.minutes, 1))}${row("Shots (FGA)", (b) => dec(b.fga, 1))}${row("Shot volume", (b) => dec(b.shot_volume, 1))}${row("Usage rate", (b) => pct1(b.usage))}</tbody></table></div>
    <p class="tiny muted">Per game, except usage rate: the share of his team's plays he ends with a shot, free throws or a turnover while on court. Seasons: regular season only; last 5: his last 5 games this season. Shot volume = FGA + 0.44 × FTA.</p></section>`;
}
// "PRA (points + rebounds + assists)" the first time the context block names it, "PRA" after
const praName = (said) => (said.pra ? "PRA" : ((said.pra = true), "PRA (points + rebounds + assists)"));
// game lines from his team's side: spread, total, prices to win, implied points and the move
function glRows(g, team, opp, home) {
  if (!g) return [["Game lines", `<span class="muted">none stored for this game</span>`]];
  const f = home ? 1 : -1, me = home ? "home" : "away", them = home ? "away" : "home";
  // spreads and totals are medians of the books' lines, so they can end in .25 or .75: shown to
  // up to 2 decimals, and the move is the latest figure as shown less the first as shown
  const two = (x) => (x == null || isNaN(x) ? null : rnd(Number(x), 2));
  const sp = two(g.home_spread == null ? null : f * g.home_spread), tot = two(g.total);
  const fs = g.first || null, ml = g.moneyline || {}, b = g.books || {}, mv = g.move || {};
  const mvSp = fs ? minusShown(sp, two(fs.home_spread == null ? null : f * fs.home_spread), 2) : mv.home_spread == null ? null : f * mv.home_spread;
  const mvTot = fs ? minusShown(tot, two(fs.total), 2) : mv.total;
  const pts = (side) => (side === "home" ? g.home_points : g.away_points);
  const win = (side, t) => `${esc(t)} ${ml[side] == null ? "–" : `<b>${odds2(ml[side])}</b> <span class="muted">${esc(bookName(ml[`${side}_book`]))}</span>`}`;
  const moved = (g.observations || 0) < 2 ? `<span class="muted">one pull so far (${esc(shortTime(g.seen))})</span>`
    : `spread ${pm2(mvSp)} · total ${pm2(mvTot)} <span class="muted">since ${esc(shortTime(g.first_seen))} (${g.observations} pulls)</span>`;
  return [["Spread", `${esc(team)} <b>${sp == null ? "–" : sp === 0 ? "level" : pm2(sp)}</b> <span class="muted">(${plural(b.spread ?? 0, "book")})</span>`],
    ["Total", `<b>${num2(tot)}</b> <span class="muted">(${plural(b.total ?? 0, "book")})</span>`],
    ["To win", `${win(me, team)} · ${win(them, opp)}`],
    ["Implied points", `${esc(team)} <b>${dec(pts(me), 1)}</b> · ${esc(opp)} ${dec(pts(them), 1)}`], ["Moved", moved]];
}
function cxScript(x, c, said) {
  const S = x.seasons || {}, cur = String(S.current), last = String(S.last), rows = [];
  if (c) rows.push(...glRows(x.game_lines, c.team, c.opponent, c.is_home));
  const q = x.script?.q4 || {}, lg = x.league || {};
  // his shares first, then the league's in brackets: "18.0% of his 568 pts in 4th quarters, 1.2% in
  // overtime (league 23.9%, overtime 0.5%)"
  const q4 = [cur, last].filter((s) => q[s]).map((s) => { const a = q[s], l = lg[s] || {};
    const league = l.q4 == null ? "" : ` <span class="muted">(league ${pct1(l.q4)}${l.ot == null ? "" : `, overtime ${pct1(l.ot)}`})</span>`;
    return `${esc(seasonLab(S, s))}: ${a.points && a.q4 != null ? `<b>${pct1(a.q4)}</b> of his ${a.points} pts in 4th quarters${a.ot == null ? "" : `, ${pct1(a.ot)} in overtime`}` : "no points in the play-by-play yet"}${league}${s === cur ? tinyTag(x.volume?.season?.games) : ""}`; });
  rows.push(["4th-quarter share", q4.length ? q4.join("<br>") : `<span class="muted">no play-by-play yet</span>`]);
  // per game in close games and in the rest: "no close games yet; other games: 16.0 PRA, 26.8 min (4)"
  const cl = x.script?.close || {};
  const part = (o, w, yet) => (o && o.games ? `${w} games: <b>${dec(o.pra, 1)}</b> ${praName(said)}, ${dec(o.minutes, 1)} min (${o.games})` : `no ${w} games${yet}`);
  const close = [cur, last].filter((s) => cl[s]).map((s) => { const yet = s === cur ? " yet" : "";
    return `${esc(seasonLab(S, s))}: ${part(cl[s].close, "close", yet)}; ${part(cl[s].other, "other", yet)}${[cl[s].close?.games, cl[s].other?.games].some((n) => n > 0 && n < TINY) ? `<span class="tinys">tiny sample</span>` : ""}`; });
  rows.push([`Close games <span class="nw">(≤5 pts)</span>, per game`, close.length ? close.join("<br>") : `<span class="muted">no finished games</span>`]);
  const foot = [c && x.game_lines ? "Lines: median of regulated US books, best price to win per side. US prices: research only, not open to UK customers." : "", close.length ? "Close-game figures: number of games in brackets." : ""].filter(Boolean).join(" ");
  return `<section class="cx"><h4>Game script</h4>${kvl(rows)}${foot ? `<p class="tiny muted">${foot}</p>` : ""}</section>`;
}
function cxOpp(x, c, market, said) {
  if (!c) return "";
  const d = x.defence, pv = x.pos_v_opp || {}, S = x.seasons || {}, rows = [];
  if (d) rows.push(["Assists allowed", `<b>${dec(d.ast_allowed, 1)}</b> per game · ${ord(d.ast_rank)} of ${d.of} <span class="muted">(1st = fewest)</span>`],
    ["Defensive rebounds", `<b>${pct1(d.dreb_pct)}</b> DREB% · ${ord(d.dreb_rank)} of ${d.of} <span class="muted">(1st = highest)</span>`]);
  else rows.push(["Defence", `<span class="muted">no team box scores this season yet</span>`]);
  const mk = market === "pra" ? praName(said) : NBA_LONG[market] || market;
  // average result and average line per player-game; the difference is taken from the two
  // figures as shown
  const lines = [String(S.current), String(S.last)].filter((s) => pv[s]).map((s) => { const m = pv[s].markets?.[market]; const df = m ? minusShown(m.result, m.line, 1) : null;
    return `${esc(seasonLab(S, s))}: ${m ? `average <b>${dec(m.result, 1)}</b> v line ${dec(m.line, 1)} <span class="${df > 0 ? "upc" : df < 0 ? "dnc" : ""}">(${pm1(df)})</span> · ${m.over} over, ${m.under} under · ${plural(m.n, "player-game")}${tinyTag(m.n)}` : `<span class="muted">no ${esc(market === "pra" ? "PRA" : mk)} lines stored</span>`}`; });
  rows.push([`${esc(POS_NAME[c.position] || c.position)} v ${esc(c.opponent)}: ${esc(mk)} v their lines`, lines.length ? lines.join("<br>") : `<span class="muted">no stored lines in ${esc(c.opponent)} games</span>`]);
  return `<section class="cx"><h4>Opponent: ${esc(c.opponent)}${d ? ` · ${esc(seasonLab(S, d.season))}, ${plural(d.games, "game")}` : ""}${d ? tinyTag(d.games) : ""}</h4>${kvl(rows)}</section>`;
}
// his games against tonight's opponent: result per stat, minus tonight's line (green over, red
// under), and that night's stored line where we have one
function cxGrid(x, c, market, said) {
  const g = x.vs_opp; if (!g || !c) return "";
  const st = x.stats || NBA_STATS, n = (g.games || []).length;
  if (!n) return `<section class="cx"><h4>v ${esc(g.opp)}</h4><p class="small muted">No games against ${esc(g.opp)} in our box scores.</p></section>`;
  const type = { R: "Reg.", I: "Play-in", P: "Playoffs" };
  const rows = g.games.map((r) => `<tr><td><b>${esc(dayFmt(r.d, "2-digit"))}</b><small>${type[r.t] || ""} ${r.h ? "home" : "away"}</small>${r.tm !== c.team ? `<small>for ${esc(r.tm)}</small>` : ""}</td>${st.map((s, i) => {
    // result minus tonight's line, from the two figures as shown in this column
    const v = r.v ? r.v[i] : null, t = g.tonight ? g.tonight[i] : null, l = r.line ? r.line[i] : null;
    const d = v == null || t == null ? null : minusShown(v, t, 1);
    const cls = [d == null ? "" : d > 0 ? "ov" : d < 0 ? "un" : "ev", s === market ? "on" : ""].filter(Boolean).join(" ");
    return `<td${cls ? ` class="${cls}"` : ""}><b>${v == null ? "–" : v}</b>${d == null ? "" : `<small>${pm1(d)}</small>`}${l == null ? "" : `<em>${esc(l)}</em>`}</td>`; }).join("")}</tr>`).join("");
  return `<section class="cx"><h4>v ${esc(g.opp)} · ${plural(g.n, "game")}${tinyTag(g.n)}</h4><div class="tablewrap"><table class="vog"><thead><tr><th>Game</th>${st.map((s) => `<th${s === market ? ' class="on"' : ""}>${NBA_SHORT[s]}</th>`).join("")}</tr></thead>
    <tbody><tr class="tn"><td>Tonight's line</td>${st.map((s, i) => `<td${s === market ? ' class="on"' : ""}>${g.tonight?.[i] == null ? "–" : esc(g.tonight[i])}</td>`).join("")}</tr>${rows}</tbody></table></div>
    <p class="tiny muted">Newest first${g.n > n ? `, latest ${n} of ${g.n}` : ""}. Coloured: result minus tonight's line (green over, red under). Grey: that night's stored line, where we have one.${said && !said.pra && st.includes("pra") ? " PRA = points + rebounds + assists." : ""}</p></section>`;
}
function cxNotes(x) {
  const n = x.notes || {}; const keys = ["volume", "script", "defence", "pos_v_opp", "vs_opp", "injury", "game_lines"].filter((k) => n[k]);
  return keys.length ? `<details class="cxnotes"><summary class="tiny">How these are measured</summary>${keys.map((k) => `<p class="tiny muted">${esc(n[k])}</p>`).join("")}</details>` : "";
}
function ctxBlock(x, c, market) {
  const m = market || c?.market || "pts"; const said = { pra: false }; // sections are built in reading order
  return `<div class="ctx">${[cxInjury(c), cxVolume(x), cxScript(x, c, said), cxOpp(x, c, m, said), cxGrid(x, c, m, said)].filter(Boolean).join("")}${cxNotes(x)}<p class="tiny muted">Context only: none of these figures feeds the projection.</p></div>`;
}
// the card's detail: a sheet with the context block (phones: slides up from the bottom)
function openNbaSheet(c, P, opener) {
  const x = nbaCtx(c, P.extras);
  const back = document.createElement("div"); back.className = "sheet-back";
  back.innerHTML = `<div class="sheet" role="dialog" aria-modal="true" aria-label="${esc(c.name)}: research context">
    <div class="row between" style="flex-wrap:nowrap;align-items:flex-start"><div><span class="eyebrow">${esc(c.team)} ${c.is_home ? "v" : "@"} ${esc(c.opponent)} · ${esc(c.tip_london)}</span><h2>${esc(c.name)}</h2><p class="tiny muted">Context for his ${esc(NBA_LONG[c.market] || c.market)} card</p></div><button class="btn ghost" data-close>Close</button></div>
    ${x ? ctxBlock(x, c) : banner("info", `<p class="small">No context figures for this card${P.extras?.error ? ` (${esc(P.extras.error)})` : ""}.</p>`)}
    ${hasNbaPage(c.player_id) ? `<p class="small sheetlink"><a href="#nba-player.${esc(c.player_id)}.${esc(c.market)}">${esc(c.name)}: full player page (${esc(NBA_SHORT[c.market] || c.market)})</a></p>` : `<p class="tiny sheetlink nopage">His player page is not in this offline copy.</p>`}</div>`;
  document.body.appendChild(back);
  const close = () => { back.remove(); document.removeEventListener("keydown", onKey); window.removeEventListener("hashchange", close); if (opener && opener.isConnected) opener.focus(); };
  const onKey = (e) => { if (e.key === "Escape") close(); };
  document.addEventListener("keydown", onKey); window.addEventListener("hashchange", close);
  back.addEventListener("click", (e) => { if (e.target === back) close(); });
  $("[data-close]", back).addEventListener("click", close);
  $("[data-close]", back).focus();
}

let DVPC = null; // current-season defence v position for the props cards
async function dvpCtx() {
  if (DVPC) return DVPC;
  const M = await api("/api/nba/matchups"); const season = Object.keys(M.rows).sort().slice(-1)[0];
  const f = { season, games: "all", venue: "all", stype: "R", rate: "pg" };
  return (DVPC = { M, f, B: dvpBoard(M, f) });
}
function dvpChip(c) {
  if (!DVPC || !DVP_POS.includes(c.position)) return "";
  const s = DVP.find((x) => x[0] === c.market); if (!s) return "";
  const L = DVPC.B.league(c.position, s[0]); const r = L.rank(c.opponent); if (!r) return "";
  const g = DVPC.B.agg[c.opponent]?.games || 0; const rel = relWord(dvpRel(DVPC.M, c.position, s[0], g));
  const tone = r > L.n - 8 ? "soft" : r <= 8 ? "tough" : "";
  return `<span class="mu ${tone}" data-tip="${esc(c.opponent)} v opposing ${POS_ONE[c.position]} · ${esc(s[2])}|${dvpFmt(s, L.vals[c.opponent])} per game (league ${dvpFmt(s, L.avg)}) · ${r} of ${L.n}, ${L.n} = gives up the most|${g} games this season · signal: ${esc(rel)}">v ${esc(c.position)} ${r}/${L.n}</span>`;
}
function bindLineButtons(root, cards, sport, rerender) {
  root.querySelectorAll("[data-line]").forEach((b) => b.addEventListener("click", () => {
    const c = cards[+b.dataset.line]; const cur = lineFor(sport, c).line;
    const host = b.closest(".pcard, .panel") || b.parentElement;
    $$(".lineedit").forEach((x) => x.remove());
    const id = `le-${sport}-${c.player_id}-${c.market}`;
    const box = document.createElement("form"); box.className = "lineedit";
    box.innerHTML = `<label class="tiny" for="${esc(id)}">Your bookmaker's line for ${esc(c.name)} · ${esc(c.market_label || NBA_MKT[c.market])}</label>
      <div class="row"><input id="${esc(id)}" type="number" inputmode="decimal" step="0.5" min="0" value="${cur}"><button class="btn" type="submit">Use line</button></div>
      <div class="row between"><button class="btn ghost" type="button" data-act="reset">Back to the ${c.market_line ? "market" : "model's"} line</button><button class="btn ghost" type="button" data-act="close">Cancel</button></div>
      <span class="tiny">Half points avoid pushes.</span>`;
    host.appendChild(box);
    const inp = box.querySelector("input"); inp.focus(); inp.select();
    box.addEventListener("submit", (ev) => { ev.preventDefault(); const x = parseFloat(inp.value); if (!isNaN(x) && x >= 0) store.set(lineKey(sport, c), x); rerender(); });
    box.querySelector('[data-act="reset"]').addEventListener("click", () => { store.set(lineKey(sport, c), null); rerender(); });
    box.querySelector('[data-act="close"]').addEventListener("click", () => box.remove());
    box.addEventListener("keydown", (ev) => { if (ev.key === "Escape") box.remove(); });
  }));
}

// saved before tip-off: the edition's own flag (published_before_tip); a slate without it is
// compared with the tip-off time
const beforeTip = (s, g) => (g.published_before_tip != null ? !!g.published_before_tip : Date.parse(s.published_at) < Date.parse(g.tip_utc));
async function viewNbaToday(v) {
  const [s, p] = await Promise.all([api("/api/nba/slate"), api("/api/nba/props")]);
  // injured players per team: the slate counts them over every player in the edition
  // (games[].injured); a slate without that field falls back to the cards held here, which an
  // offline copy trims to players likely to play
  const outs = {}; p.cards.filter((c) => c.market === "pts" && c.injury).forEach((c) => { (outs[c.team] ||= []).push(c); });
  const injured = (g, t) => { if (g.injured) { const x = g.injured[t] || {}; return [x.out || 0, x.questionable || 0]; }
    const l = outs[t] || []; const o = l.filter((c) => ["OUT", "OFS"].includes(c.injury.fantasy)).length; return [o, l.length - o]; };
  const n = s.games.length, nb = s.games.filter((g) => beforeTip(s, g)).length;
  const when = nb === n ? (n === 1 ? "before tip-off" : `before all ${n} tip-offs`) : nb === 0 ? (n === 1 ? "after tip-off" : `after all ${n} tip-offs`) : `after ${n - nb} of ${n} games had started`;
  v.innerHTML = `<section class="hero"><div class="medal">${leagueMark("nba")}</div><span class="eyebrow">NBA · ${esc(s.date_et)} (US date) · ${esc(s.kind)} edition</span><h1>Tonight's slate</h1>
      <p>${plural(n, "game")}, saved ${esc(s.published_london)}, ${when}. Times are UK time. Every projection comes from the saved edition; tap a game for both teams or open the props board.</p>
      <div class="row"><a class="btn" href="#nba-props">Open the props board</a></div></section>
    <div class="games">${s.games.map((g) => { const started = !beforeTip(s, g);
      return `<a class="panel game-tile" href="#nba-game.${esc(g.game_id)}" style="${teamVars("nba", g.away, "--a1", "--a2")};${teamVars("nba", g.home, "--h1", "--h2")}">
      <div class="teams">${logo("nba", g.away, "tl big")}<b>${esc(g.away)}</b><span class="at">@</span>${logo("nba", g.home, "tl big")}<b>${esc(g.home)}</b></div>
      <div class="meta tiny"><div>${esc(g.tip_london)}${g.time_valid ? "" : " (time to be confirmed)"}</div>
        <div>${[g.away, g.home].map((t) => { const [o, q] = injured(g, t); return `${esc(t)}: ${o} out${q ? `, ${q} questionable` : ""}`; }).join(" · ")}</div>
        ${started ? `<span class="pill started"><span class="dot"></span>Started before this edition</span>` : ""}</div></a>`; }).join("")}</div>
    ${banner("info", `<p class="small">${(s.notes || []).map(esc).join(" ")}</p><p class="tiny muted">Box scores through ${esc(londonTime(s.inputs.box_last_tip))}; injury snapshot ${esc(s.inputs.injury_snapshot || "none")}${s.inputs.injury_snapshot_at ? ` (published ${esc(londonTime(s.inputs.injury_snapshot_at))})` : ""}. Data: SportsDataverse (CC BY 4.0)${s.inputs.injuries_live ? "; this edition's injury reports came from ESPN game feeds at publish time, before that access was dropped on 3 Nov 2026" : ""}.</p>`)}`;
}

async function viewNbaProps(v) {
  const p = await api("/api/nba/props"); await dvpCtx().catch(() => null);
  const cal = nbaCal(p), flat = flatMarkets(cal);
  const all = p.cards;
  const games = [...new Map(all.map((c) => [c.game_id, `${c.is_home ? c.opponent : c.team} @ ${c.is_home ? c.team : c.opponent}`])).entries()];
  const f = Object.assign({ q: "", game: "", market: "pts", minp: "0.6", sort: "proj", form: "all" }, store.get("nbaFilter", {}));
  // hot: over the line in >= 4 of the last 5 and >= 7 of the last 10; cold: the reverse
  const formOf = (c) => { const { line } = lineFor("nba", c); const v = c.log.values; const h5 = hitRate(v.slice(-5), line), h10 = hitRate(v.slice(-10), line);
    if (!h5 || !h10 || h10.n < 10) return "none"; if (h5.over >= 4 && h10.over >= 7) return "hot"; if (h5.over <= 1 && h10.over <= 3) return "cold"; return "none"; };
  // an offline copy holds only players at least this likely to play (props.snapshot_min_p_play):
  // lower "Plays at least" options would show the same cards, so they are left out
  const snapMin = SNAP && p.snapshot_min_p_play != null && !isNaN(p.snapshot_min_p_play) ? Number(p.snapshot_min_p_play) : null;
  const minOpts = ["0", "0.3", "0.6", "0.85"].filter((x) => snapMin == null || Number(x) >= snapMin);
  if (snapMin != null && !minOpts.some((x) => Number(x) === snapMin)) minOpts.unshift(String(snapMin));
  if (!minOpts.includes(f.minp)) f.minp = minOpts.find((x) => Number(x) >= Number(f.minp)) || minOpts[0];
  // the longer explanation folds away on phones, so the first card starts on the first screen
  const wide = window.matchMedia("(min-width: 700px)").matches;
  v.innerHTML = `<section class="hero"><div class="medal">${leagueMark("nba")}</div><span class="eyebrow">NBA props · ${esc(p.date_et)} · saved ${esc(p.published_london)}</span><h1>Props board</h1>
      ${banner("warn", `<p><b>Research only: no demonstrated edge.</b> Tested on a full season of real bookmaker lines (about 72,000 props, 2024-25), the bookmakers' prices were more accurate than this model and its picks lost about 2% at the best US prices (more at a single bookmaker). Use these cards to research, not as bets.</p>`)}
      <details class="howto"${wide ? " open" : ""}><summary>How the cards work</summary><p>Each card shows the model's projection, its over/under chance at a line, and how often the player went over that line recently. The line is, in order: yours (tap it to type your bookmaker's), the US market line where stored (a research reference; US books are not open to UK bettors), or the model's fair 50/50 line. ${cal ? `At the US line the chance is calibrated on real lines; at any other line it is the raw model.${flat.length ? ` For ${esc(flat.map((k) => NBA_LONG[k]).join(" and "))} the calibration found almost no signal, so those cards show no model view at the US line.` : ""}` : "This model version has no calibration on real lines, so cards show no chance at the US line; at any other line they show the raw model."} "Context" opens minutes, game script, the opponent and his games against them.</p></details></section>
    <div class="mktabs" role="tablist">${Object.entries(NBA_SHORT).map(([k, l]) => `<button role="tab" class="${k === f.market ? "on" : ""}" data-mk="${k}">${l}</button>`).join("")}</div>
    <div class="filters nbaf" role="search">
      <label>Player<input type="search" id="nq" value="${esc(f.q)}" placeholder="Name"></label>
      <label>Game<select id="ngame"><option value="">All games</option>${games.map(([id, l]) => `<option value="${esc(id)}" ${id === f.game ? "selected" : ""}>${esc(l)}</option>`).join("")}</select></label>
      <label>Plays at least<select id="nminp">${minOpts.map((x) => `<option value="${x}" ${x === f.minp ? "selected" : ""}>${pctInt(x)}%</option>`).join("")}</select></label>
      <label>Form<select id="nform">${[["all", "Everyone"], ["hot", "Hot: 4+ of last 5 over"], ["cold", "Cold: 1 or fewer of last 5 over"]].map(([k, l]) => `<option value="${k}" ${k === f.form ? "selected" : ""}>${l}</option>`).join("")}</select></label>
      <label>Sort<select id="nsort">${[["proj", "Projection"], ["edge", "Biggest gap to the market"], ["l10", "L10 hit rate"], ["tip", "Tip-off"]].map(([k, l]) => `<option value="${k}" ${k === f.sort ? "selected" : ""}>${l}</option>`).join("")}</select></label>
    </div>
    <div class="countrow"><p class="tiny" id="ncount"></p>${snapMin != null ? `<p class="tiny" id="nsnap">This copy holds players at least ${pctInt(snapMin)}% likely to play.</p>` : ""}</div><div class="cards" id="ncards"></div><div class="row"><button class="btn ghost" id="nmore" hidden>Show more</button></div>`;
  let shown = 24;
  const render = () => {
    let r = all.filter((c) => c.market === f.market && c.p_play >= +f.minp && (!f.game || c.game_id === f.game) && (!f.q || c.name.toLowerCase().includes(f.q.toLowerCase())) && (f.form === "all" || formOf(c) === f.form));
    // "gap": calibrated model minus market in whole percentage points, as on the cards (largest
    // first, the exact gap breaking ties); every card that cannot be set against the market at its
    // line (no model view, thin market, no calibrated chance, no market there) follows, in the
    // order it came
    let comparable = 0;
    const key = (c) => { const { line } = lineFor("nba", c);
      if (f.sort === "edge") { const ch = nbaChance(c, line, cal); if (ch.kind !== "lean") return [1, 0, 0]; comparable++; return [0, -Math.abs(ch.gap), -Math.abs(ch.exact)]; }
      if (f.sort === "l10") { const h = hitRate(c.log.values.slice(-10), line); return [-(h ? h.r : 0)]; } if (f.sort === "proj") return [-c.mean]; return [c.tip_utc]; };
    const cmp = (a, b) => { for (let i = 0; i < a.length; i++) { if (a[i] < b[i]) return -1; if (a[i] > b[i]) return 1; } return 0; };
    r = r.map((c) => [key(c), c]).sort((a, b) => cmp(a[0], b[0])).map((x) => x[1]);
    $("#ncount").textContent = `${r.length} players · ${NBA_MKT[f.market]}${f.form !== "all" ? ` · ${f.form} at each card's line (form describes the past; the last-10 average was the weakest forecast tested)` : ""}${f.sort === "edge" ? (comparable ? ` · ${comparable} can be set against the market; the rest follow` : " · none of these can be set against the market at its line, so the gap sort leaves them in order") : ""}`;
    $("#ncards").innerHTML = r.slice(0, shown).map((c) => nbaCard(c, all.indexOf(c), cal)).join("") || banner("info", "<p>No players match these filters.</p>");
    $("#nmore").hidden = r.length <= shown;
    bindLineButtons($("#ncards"), all, "nba", render);
  };
  const save = () => { store.set("nbaFilter", f); shown = 24; render(); };
  $$(".mktabs button").forEach((b) => b.addEventListener("click", () => { f.market = b.dataset.mk; $$(".mktabs button").forEach((x) => x.classList.toggle("on", x === b)); save(); }));
  $("#nq").addEventListener("input", (e) => { f.q = e.target.value; save(); });
  $("#ngame").addEventListener("change", (e) => { f.game = e.target.value; save(); });
  $("#nminp").addEventListener("change", (e) => { f.minp = e.target.value; save(); });
  $("#nsort").addEventListener("change", (e) => { f.sort = e.target.value; save(); });
  $("#nform").addEventListener("change", (e) => { f.form = e.target.value; save(); });
  $("#nmore").addEventListener("click", () => { shown += 24; render(); });
  $("#ncards").addEventListener("click", (e) => { const b = e.target.closest("[data-ctx]"); if (b) openNbaSheet(all[+b.dataset.ctx], p, b); });
  render();
}

// #nba-player.<id> or #nba-player.<id>.<market> (a card's links open his page on its market)
async function viewNbaPlayer(v, arg) {
  const [pid, want] = String(arg || "").split(".");
  const [P, props] = await Promise.all([api(`/api/nba/player/${pid}`), api("/api/nba/props")]); await dvpCtx().catch(() => null);
  const cards = props.cards.filter((c) => c.player_id === pid); const cal = nbaCal(props);
  let mk = NBA_MKT[want] ? want : store.get("nbaPlayerMk", "pts"); if (!cards.find((c) => c.market === mk)) mk = cards[0]?.market || "pts";
  const draw = () => {
    const c = cards.find((x) => x.market === mk);
    const head = `<section class="hero">${avatar(P.team, P.name, pid, "nba")}<span class="eyebrow">${esc(P.position)} · ${esc(P.team)}</span><h1>${esc(P.name)}</h1></section>`;
    // context: his card's figures in the props `extras`; the live server's page extras otherwise
    // (a player with no game tonight gets volume and game script only)
    const own = P.extras && (P.extras.volume || P.extras.script) ? P.extras : null;
    const ctxPanel = (x, card) => (x ? `<section class="panel"><h2>Context</h2>${ctxBlock(x, card, mk)}</section>` : "");
    if (!c) { v.innerHTML = head + banner("info", "<p>No forecast for this player in the latest edition (no game tonight, or no previous NBA minutes).</p>") + ctxPanel(own, null) + logTable(P); return; }
    const { line, src, fair: isFair } = lineFor("nba", c); const D = new Dist(c.dist); const ch = nbaChance(c, line, cal);
    v.innerHTML = head + `<div class="mktabs">${cards.map((x) => `<button class="${x.market === mk ? "on" : ""}" data-mk="${x.market}">${NBA_SHORT[x.market]}</button>`).join("")}</div>
      <section class="panel"><div class="row between"><div><span class="eyebrow">${esc(c.team)} ${c.is_home ? "v" : "@"} ${esc(c.opponent)} · ${esc(c.tip_london)}</span><h2>${esc(NBA_MKT[mk])}</h2></div>${nbaInjury(c.injury)}</div>
        <div class="pline"><div><span class="mk">Line</span><button class="linebtn" data-line="0">${line}<small>${src} ${ICONS.pen}</small></button></div><div class="proj"><b>${dec(c.mean, 1)}</b><span>projection (median ${c.median}, 80% ${c.q10}–${c.q90})</span></div></div>
        ${nbaOuBar(ch, isFair)}${usPrices(c)}<p class="tiny solid bep">${ch.show ? `Break-even decimal prices at this line (${ch.cal ? "calibrated chance" : "raw model"}): over ${dec(fair(ch.over, ch.push), 2)}, under ${dec(fair(ch.under, ch.push), 2)}.` : "No break-even prices at this line: the card shows no model chance here."}</p>
        ${hitChips(c.log, line, c.opponent)}
        <h3 style="margin-top:12px">Last 20 games</h3>${miniBars(c.log, line, 20, { width: 640, height: 170 })}
        <h3 style="margin-top:12px">Model distribution</h3>${distChart(D, { line })}
        <p class="tiny solid">Conditional on playing (chance he plays ${pct(c.p_play)}). Model N1; simpler forecasts for comparison: recent average ${dec(c.baselines.A, 1)}, last-10 average ${dec(c.baselines.C, 1)}. "Break-even" is the decimal price at which a bet would neither win nor lose money if that chance were exactly right.</p></section>
      ${ctxPanel(nbaCtx(c, props.extras) || own, c)}
      <div id="mupanel"></div>
      ${logTable(P)}`;
    const mu = (season) => nbaMatchupPanel(c, season).then((h) => { const el = $("#mupanel", v); if (!el) return; el.innerHTML = h;
      $$("[data-museason]", el).forEach((b) => b.addEventListener("click", () => { store.set("muSeason", b.dataset.museason); mu(b.dataset.museason); })); });
    mu();
    $$(".mktabs button[data-mk]", v).forEach((b) => b.addEventListener("click", () => { mk = b.dataset.mk; store.set("nbaPlayerMk", mk);
      try { history.replaceState(null, "", `#nba-player.${pid}.${mk}`); } catch { /* the address keeps the earlier market */ }
      draw(); }));
    bindLineButtons(v, [c], "nba", draw);
  };
  draw();
}
function logTable(P) {
  const rows = P.game_log.slice(-15).reverse();
  return `<section class="panel"><h2>Game log</h2><div class="tablewrap"><table><thead><tr><th class="l">Date</th><th class="l">Opp</th><th>Min</th><th>PTS</th><th>REB</th><th>AST</th><th>3PM</th><th>PRA</th></tr></thead><tbody>
    ${rows.map((r) => r.participation === "played" ? `<tr><td class="l">${esc(r.date)}${r.starter ? "" : ' <span class="tiny muted">bench</span>'}</td><td class="l">${r.home ? "v" : "@"} ${esc(r.opp)}</td><td>${dec(r.minutes, 0)}</td><td>${dec(r.pts, 0)}</td><td>${dec(r.reb, 0)}</td><td>${dec(r.ast, 0)}</td><td>${dec(r.fg3m, 0)}</td><td>${dec(r.pts + r.reb + r.ast, 0)}</td></tr>`
      : `<tr class="dim"><td class="l">${esc(r.date)}</td><td class="l">${r.home ? "v" : "@"} ${esc(r.opp)}</td><td colspan="6" class="l">Did not play</td></tr>`).join("")}</tbody></table></div></section>`;
}

async function viewNbaGame(v, gid) {
  const g = await api(`/api/nba/game/${gid}`); const G = g.game;
  const tbl = (team) => `<div class="tablewrap"><table><thead><tr><th class="l">Player</th><th>Plays</th><th>Min</th><th>PTS</th><th>REB</th><th>AST</th><th>3PM</th><th>PRA</th></tr></thead><tbody>
    ${g.players.filter((p) => p.team === team && (p.p_play >= 0.05 || p.injury)).map((p) => `<tr class="${p.p_play < 0.5 ? "dim" : ""}"><td class="l">${nameLink(hasNbaPage(p.player_id), `#nba-player.${esc(p.player_id)}`, p.name, "plink")} ${nbaInjury(p.injury)}</td><td>${pct(p.p_play)}</td><td>${dec(p.minutes_expected, 0)}</td>${["pts", "reb", "ast", "fg3m", "pra"].map((m) => `<td data-tip="${esc(NBA_MKT[m])}|median ${p[m]?.median}, 80% ${p[m]?.q10}–${p[m]?.q90}">${dec(p[m]?.mean, 1)}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`;
  const mu = (t) => { const o = g.opp[t]; if (!o) return ""; return ["pts", "reb", "ast", "fg3m"].map((s) => `<span class="mu ${rankTone(o[s].rank)}">${NBA_SHORT[s]} ${o[s].rank}/30</span>`).join(" ") + ` <span class="tiny muted">pace ${o.pace.value} (${o.pace.rank}/30)</span>`; };
  const box = (t) => { const d = (g.defence || {})[t]; return d ? `<div class="tiny defline">${dec(d.ast_allowed, 1)} assists allowed per game (${ord(d.ast_rank)} of ${d.of}, 1st = fewest) · DREB% ${pct1(d.dreb_pct)} (${ord(d.dreb_rank)}, 1st = highest) · ${plural(d.games, "game")}${tinyTag(d.games)}</div>` : ""; };
  v.innerHTML = `<section class="hero"><span class="eyebrow">${esc(G.tip_london)} · edition ${esc(g.edition_id)}</span><h1>${esc(G.away)} @ ${esc(G.home)}</h1><p class="small">${G.published_before_tip === false ? "This game had started before this edition was saved. " : ""}Projections are means conditional on playing; rows below 50% to play are dimmed.</p></section>
    <section class="panel"><h2>Game lines</h2>${g.game_lines ? `<div class="solid">${kvl(glRows(g.game_lines, G.home, G.away, true))}<p class="tiny gl-foot">From ${esc(G.home)}'s side. Median of regulated US books, best price to win per side; implied points = (total ∓ spread) / 2. US prices: research only, not open to UK customers.</p></div>` : `<p class="small solid gl-none">No game lines stored for this game.</p>`}</section>
    <section class="panel"><h2>Defences</h2><p class="small">Rank of each defence by what it allows per possession (1 = allows least, 30 = most), shrunk towards average as the model uses it. Full breakdowns by court area, shot type and position: <a href="#nba-defense.${esc(G.away)}">${esc(G.away)}</a> · <a href="#nba-defense.${esc(G.home)}">${esc(G.home)}</a>.</p>
      ${[G.away, G.home].map((t) => `<div class="solid defrow"><b>${esc(t)} defence</b> ${mu(t)}${box(t)}</div>`).join("")}</section>
    <div class="grid"><section class="panel"><h2>${esc(G.away)}</h2>${tbl(G.away)}</section><section class="panel"><h2>${esc(G.home)}</h2>${tbl(G.home)}</section></div>`;
}

async function viewNbaLab(v) {
  const runs = await api("/api/nba/backtests");
  const tbl = (r) => `<div class="tablewrap"><table><thead><tr><th class="l">Market</th><th>Player-games</th><th>Model N1</th><th>Recent average</th><th>Last-10 average</th><th>N1 v recent average</th></tr></thead><tbody>
    ${Object.entries(r.markets).map(([m, x]) => `<tr><td class="l">${esc(NBA_MKT[m])}</td><td>${x.n.toLocaleString("en-GB")}</td><td>${dec(x.N1, 3)}</td><td>${dec(x.A, 3)}</td><td>${dec(x.C, 3)}</td><td><b>${dec((x.N1_minus_A / x.A) * 100, 1)}%</b> <span class="tiny muted">[${dec((x.N1_minus_A_ci[0] / x.A) * 100, 1)}, ${dec((x.N1_minus_A_ci[1] / x.A) * 100, 1)}]</span></td></tr>`).join("")}</tbody></table></div>`;
  const show = runs.filter((r) => /n02|n03/.test(r.label));
  v.innerHTML = `<section class="hero"><span class="eyebrow">NBA · retrospective research, not a published record</span><h1>Model lab</h1>
      <p>Every game is forecast using only games played before it. Scores are CRPS: the average distance between the forecast distribution and what happened (lower is better). In brackets, a 95% interval from resampling whole weeks.</p></section>
    ${show.map((r) => `<section class="panel"><h2>${/TEST/.test(r.label) ? "Test season 2025-26 (run once, frozen model)" : "Validation 2023-24 and 2024-25"}</h2>${tbl(r)}
      <p class="tiny muted">${esc(r.label)} · ${r.rows_scored.toLocaleString("en-GB")} player-games scored. Chance of playing: Brier ${dec(r.participation.p_play.brier, 3)} (share of last 10 games played: ${dec(r.participation.p_play_naive.brier, 3)}).</p></section>`).join("")}
    ${banner("info", `<p><b>Reading this honestly.</b> The model beats a player's recent average by 1–4% depending on the market, consistently. That is a real but modest edge in forecast accuracy, not evidence of beating bookmakers, whose lines already sit close to a good estimate. The last-10 average many prop sites show is the weakest of the three.</p>`)}`;
}

async function viewNbaAbout(v) {
  v.innerHTML = `<section class="hero"><span class="eyebrow">NBA</span><h1>Data and method</h1></section>
    <section class="panel"><h2>What the model does</h2><p class="small">For each player: expected minutes (recent games weighted towards the latest three) with the spread of minutes players actually showed in past seasons, so short nights from blowouts and foul trouble are included; per-minute scoring, rebounding, assist and three-point rates weighted over about 15 games and shrunk towards the position average when minutes are few; an opponent adjustment (what that defence allows per possession, relative to the league at the time) and pace. Points + rebounds + assists is built from the same minutes, so it is consistent with its parts.</p>
      <p class="small">Chance of playing comes from recent appearances, then the daily injury report: Out = 1%, game-time decision = 65% of the usual chance. Those two numbers are assumptions; injury reports have no history here before September 2026, so their effect is judged from now on.</p></section>
    <section class="panel"><h2>Known gaps</h2><ul class="small"><li>When a teammate who played last game is ruled out, the extra minutes and shots are not added yet (flagged on the card).</li><li>Box scores arrive the morning after games (about 12:00 to 12:40 UK time). An edition published before then is made without the previous night's games, so a team on the second night of a back-to-back is forecast without the first one.</li><li>Injury news comes from the daily snapshot (about 18:40 UK time). Later news is not included, so late scratches are missed.</li><li>Bookmaker prices are US books' (The Odds API), shown as a research reference only: UK bookmakers' NBA props are not available through the odds feed, and any line you enter is your own.</li></ul></section>
    <section class="panel"><h2>Sources</h2><p class="small">Box scores, schedules and the daily injury snapshot: SportsDataverse (sportsdataverse-data releases, CC BY 4.0), built from ESPN's game feeds. Nothing is fetched from ESPN itself (its terms forbid automated access; D-046). Player photos and logos are copies saved before 3 Nov 2026; without one, initials are shown. Prices: The Odds API (player props and game lines from US books), a research reference only. Not used: NBA.com statistics and injury PDFs (their terms forbid use in connection with gambling).</p></section>
    <section class="panel" id="ctxdefs"><h2>Card prices and context</h2><p class="small">US prices on a card are the best price at a state-regulated US book on its main line, and BetMGM's pair when its main line is the consensus line. The market chance is the average over books with the margin removed. The lean sets the calibrated model's chance against that chance, both as whole percentages: under 3 percentage points apart is "No lean". With fewer than 3 books at the line, or no regulated best price, the card says "thin market" and shows no lean. Where the calibration on real lines found almost no signal for a market, the card says "No model view at this line". A lean is a description, not a pick: no gap has made money on real past lines (D-043). "Tiny sample" marks figures from fewer than 5 games or player-games.</p></section>`;
  // the context definitions travel with the props payload; filled in when it has loaded
  api("/api/nba/props").then((p) => { const n = p.extras?.notes; const el = $("#ctxdefs", v); if (!el) return;
    // the edition's calibration, as the cards use it (slopes from reports/nba/model/calibration.json)
    const cal = nbaCal(p), flat = flatMarkets(cal);
    el.insertAdjacentHTML("beforeend", `<p class="small">${cal ? `Model ${esc(cal.version)}: calibration slope ${NBA_STATS.filter((k) => cal.slope[k] != null).map((k) => `${esc(NBA_SHORT[k])} ${dec(cal.slope[k], 2)}`).join(", ")} (1 would mean the calibrated chance follows the model fully). Below ${dec(cal.flat_below, 1)} the card shows no model view at the US line${flat.length ? `: ${esc(flat.map((k) => NBA_LONG[k]).join(" and "))}` : ""}.` : "This edition's model version has no calibration on real lines, so cards show no chance at the US line."}</p>`);
    if (!n) return;
    const lab = { volume: "Volume", script: "Game script", defence: "Opponent defence", pos_v_opp: "Position v the opponent's lines", vs_opp: "Games v tonight's opponent", injury: "Injuries", game_lines: "Game lines" };
    el.insertAdjacentHTML("beforeend", `<dl class="cxkv">${Object.entries(lab).filter(([k]) => n[k]).map(([k, l]) => `<dt>${l}</dt><dd class="small">${esc(n[k])}</dd>`).join("")}</dl>`); }).catch(() => null);
}

// ---------------------------------------------------------------- NBA defensive matchups
// Every number is aggregated in the browser from per-game rows, so all 30 teams are compared
// under the same season, game-window, venue and game-type filters.
const DZ = ["RA", "PAINT", "MID", "LC3", "RC3", "LW3", "RW3", "TOP3"];
const DZ3 = new Set(["LC3", "RC3", "LW3", "RW3", "TOP3"]);
const DZ_SHORT = { RA: "Rim", PAINT: "Paint", MID: "Mid", LC3: "L corner", RC3: "R corner", LW3: "L wing", RW3: "R wing", TOP3: "Top" };
const DSUM = { "2PT": ["RA", "PAINT", "MID"], "3PT": ["LC3", "RC3", "LW3", "RW3", "TOP3"], "Paint (rim + lane)": ["RA", "PAINT"], "Corner 3s": ["LC3", "RC3"], "Above-break 3s": ["LW3", "RW3", "TOP3"], "All field goals": DZ };
const POS_NAME = { ALL: "All opponents", G: "Guards", F: "Forwards", C: "Centres" };
function dmF() { return Object.assign({ season: "2027", games: "all", venue: "all", stype: "R", rate: "pg", pos: "ALL", metric: "pts" }, store.get("dmFilter", {})); }
function dmRows(M, team, f) {
  let r = ((M.rows[f.season] || {})[team] || []);
  if (f.stype !== "all") r = r.filter((x) => (f.stype === "R" ? x.t === "R" : x.t !== "R"));
  if (f.venue !== "all") r = r.filter((x) => (f.venue === "home") === (x.h === 1));
  if (f.games !== "all") r = r.slice(-(+f.games));
  return r;
}
function dmAgg(rows, pos) {
  const pl = pos === "ALL" ? ["G", "F", "C", "U"] : [pos];
  const a = { games: rows.length, poss: 0, pa: 0, fouls: 0, sf: 0, fn: 0, z: DZ.map(() => [0, 0]), fta: 0, ftm: 0, bx: Array(15).fill(0) }; // one slot per box column (matchups.BOX_COLS)
  for (const r of rows) {
    a.poss += r.p; a.pa += r.pa; if (r.f != null) { a.fouls += r.f; a.sf += r.sf; a.fn++; }
    for (const p of pl) {
      const z = r.z[p]; if (z) DZ.forEach((_, i) => { a.z[i][0] += z[2 * i]; a.z[i][1] += z[2 * i + 1]; });
      const t = r.ft[p]; if (t) { a.fta += t[0]; a.ftm += t[1]; }
      const b = r.bx[p]; if (b) b.forEach((v, i) => { a.bx[i] += v; });
    }
  }
  return a;
}
const dmScale = (a, f) => (f.rate === "p100" ? (a.poss ? 100 / a.poss : 0) : (a.games ? 1 / a.games : 0));
function dmZone(a, zs, f) { // stats for a set of zones
  let fga = 0, fgm = 0, pts = 0; for (const z of zs) { const i = DZ.indexOf(z); fga += a.z[i][0]; fgm += a.z[i][1]; pts += a.z[i][1] * (DZ3.has(z) ? 3 : 2); }
  const tot = a.z.reduce((s, x) => s + x[0], 0); const k = dmScale(a, f);
  return { fga_n: fga, att: fga * k, made: fgm * k, pct: fga ? fgm / fga : null, pts: pts * k, share: tot ? fga / tot : null };
}
function dmLeague(M, f, fn) { // fn(agg) -> value; returns {vals: {team: v}, avg, rank(team) 1 = lowest}
  const vals = {}; for (const t of Object.keys(M.rows[f.season] || {})) { const v = fn(dmAgg(dmRows(M, t, f), f.pos)); if (v != null && !isNaN(v)) vals[t] = v; }
  const arr = Object.values(vals); const avg = arr.reduce((s, x) => s + x, 0) / (arr.length || 1);
  const sorted = Object.entries(vals).sort((a, b) => a[1] - b[1]).map((x) => x[0]);
  return { vals, avg, n: arr.length, rank: (t) => sorted.indexOf(t) + 1 };
}
const DM_METRIC = { pts: ["Points allowed", (s) => s.pts, "1st = fewest points allowed"], att: ["Attempts allowed", (s) => s.att, "1st = fewest attempts allowed"], pct: ["FG% allowed", (s) => s.pct, "1st = lowest FG% allowed"] };
function dmFmt(m, v) { return v == null || isNaN(v) ? "–" : m === "pct" || m === "share" ? pct1(v) : dec(v, 1); }
function dmTone(m, v, avg) { // favourable for the scorer when the defence allows more than average
  if (v == null || avg == null || !avg) return "zn";
  const d = m === "pct" ? (v - avg) * 100 : (v / avg - 1) * 100; const s = m === "pct" ? [1.5, 4] : [5, 15];
  return d >= s[1] ? "zf2" : d >= s[0] ? "zf1" : d <= -s[1] ? "zd2" : d <= -s[0] ? "zd1" : "zn";
}
function dmRel(M, pos, z, m) { const r = M.reliability[`${pos === "U" ? "ALL" : pos}|${z}|${m === "pct" ? "pct" : "att"}`]; return r; }
function relAt(r, n) { if (!r) return null; const rho = r.rho_game; return (n * rho) / (1 + (n - 1) * rho); }
function relWord(x) { return x == null ? "not measured" : x >= 0.6 ? "solid" : x >= 0.35 ? "some signal" : x >= 0.15 ? "mostly noise" : "noise"; }

function zoneAt(x, y) { // mirrors npl/nba/shots.py zone_of, for drawing the court
  const dx = x - 25, d = Math.hypot(dx, y);
  const three = y <= 8.75 ? Math.abs(dx) >= 22 : d >= 23.75;
  if (!three) return d <= 4 ? "RA" : Math.abs(dx) <= 8 && y <= 13.75 ? "PAINT" : "MID";
  if (y <= 8.75) return dx < 0 ? "LC3" : "RC3";
  return Math.abs(dx) <= 9 ? "TOP3" : dx < 0 ? "LW3" : "RW3";
}
const DZ_LABEL_AT = { RA: [25, 1.6], PAINT: [25, 10.5], MID: [25, 18.5], LC3: [1.5, 1.5], RC3: [48.5, 1.5], LW3: [6.5, 23], RW3: [43.5, 23], TOP3: [25, 30] };
function courtMap(cells, { width = 500 } = {}) { // cells[z] = {tone, label, tip}
  const S = 10, H = 47, Y = (y) => (41.75 - y) * S;
  const groups = {}; for (let x = 0; x < 50; x++) for (let y = -5; y < 42; y++) { const z = zoneAt(x + 0.5, y + 0.5); (groups[z] ||= []).push(`M${x * S},${Y(y + 1)}h${S}v${S}h-${S}z`); }
  let s = `<svg class="court" viewBox="0 0 500 ${H * S}" style="max-width:${width}px" role="img" aria-label="Half court: defence by shooting area">`;
  for (const z of DZ) { const c = cells[z] || {}; s += `<g class="zone ${c.tone || "zn"}" data-zone="${z}" tabindex="0" data-tip="${esc(c.tip || DZ_SHORT[z])}"><path d="${groups[z].join("")}"/></g>`; }
  // court lines
  s += `<g class="lines"><rect x="${17 * S}" y="${Y(13.75)}" width="${16 * S}" height="${19 * S}"/><path d="M${21 * S},${Y(0)} a${4 * S},${4 * S} 0 0 0 ${8 * S},0"/>`;
  s += `<path d="M${3 * S},${Y(-5.25)} V${Y(8.75)} A${23.75 * S},${23.75 * S} 0 0 1 ${47 * S},${Y(8.75)} V${Y(-5.25)}"/>`;
  s += `<circle cx="${25 * S}" cy="${Y(13.75)}" r="${6 * S}"/><circle class="rim" cx="${25 * S}" cy="${Y(0)}" r="7.5"/><line x1="${22 * S}" x2="${28 * S}" y1="${Y(-1.25)}" y2="${Y(-1.25)}"/><rect x="0" y="0" width="500" height="${H * S}"/></g>`;
  for (const z of DZ) { const c = cells[z]; if (!c || !c.label) continue; const [lx, ly] = DZ_LABEL_AT[z]; const small = z === "LC3" || z === "RC3";
    s += `<text class="zl${small ? " sm" : ""}" x="${lx * S}" y="${Y(ly)}" text-anchor="middle">${esc(c.label)}</text>`; }
  return s + "</svg>";
}
function dmFilters(M, f, extra = "", rates = [["pg", "Per game"], ["p100", "Per 100 possessions"]]) {
  const opt = (id, pairs, cur) => `<select id="${id}">${pairs.map(([k, l]) => `<option value="${k}" ${String(k) === String(cur) ? "selected" : ""}>${l}</option>`).join("")}</select>`;
  return `<div class="filters dmf" role="search">
    <label>Season${opt("dms", Object.entries(M.seasons).reverse(), f.season)}</label>
    <label>Games${opt("dmg", [["all", "Whole season"], ["5", "Last 5"], ["10", "Last 10"], ["20", "Last 20"]], f.games)}</label>
    <label>Venue${opt("dmv", [["all", "Home and away"], ["home", "Home"], ["away", "Away"]], f.venue)}</label>
    <label>Games type${opt("dmt", [["R", "Regular season"], ["P", "Playoffs and play-in"], ["all", "All"]], f.stype)}</label>
    <label>Scale${opt("dmr", rates, f.rate)}</label>${extra}</div>`;
}
function dmBind(v, f, key = "dmFilter") {
  [["#dms", "season"], ["#dmg", "games"], ["#dmv", "venue"], ["#dmt", "stype"], ["#dmr", "rate"], ["#dmm", "metric"], ["#dmp", "pos"]].forEach(([id, k]) => { const el = $(id, v); if (el) el.addEventListener("change", () => { f[k] = el.value; store.set(key, f); route(); }); });
  $$("[data-dmpos]", v).forEach((b) => b.addEventListener("click", () => { f.pos = b.dataset.dmpos; store.set("dmFilter", f); route(); }));
  $$("[data-dmmetric]", v).forEach((b) => b.addEventListener("click", () => { f.metric = b.dataset.dmmetric; store.set("dmFilter", f); route(); }));
}
const rateLbl = (f) => (f.rate === "p100" ? "per 100 poss." : f.rate === "p36" ? "per 36 min at the position" : "per game");
const ord = (n) => { const s = ["th", "st", "nd", "rd"], v = n % 100; return n + (s[(v - 20) % 10] || s[v] || s[0]); };

async function viewNbaDefense(v, team) {
  const M = await api("/api/nba/matchups"); const f = dmF();
  if (!M.rows[f.season]) f.season = Object.keys(M.rows).slice(-1)[0];
  if (team) return viewNbaDefenseTeam(v, M, f, team);
  const teams = Object.keys(M.rows[f.season]).sort();
  const cols = [["Pts", "Points allowed", (a) => a.pa * dmScale(a, f), "pts"], ["Paint", "Paint points (rim + lane)", (a) => dmZone(a, DSUM["Paint (rim + lane)"], f).pts, "pts"], ["Mid", "Mid-range points", (a) => dmZone(a, ["MID"], f).pts, "pts"], ["3PT", "Three-point points", (a) => dmZone(a, DSUM["3PT"], f).pts, "pts"],
    ["Rim att", "Rim attempts allowed", (a) => dmZone(a, ["RA"], f).att, "att"], ["Corner 3 att", "Corner 3 attempts allowed", (a) => dmZone(a, DSUM["Corner 3s"], f).att, "att"], ["Opp 3P%", "Opponent 3P%", (a) => dmZone(a, DSUM["3PT"], f).pct, "pct"], ["FT rate", "Opponent FTA / FGA", (a) => { const t = a.z.reduce((s, x) => s + x[0], 0); return t ? a.fta / t : null; }, "pct"], ["Fouls", "Fouls committed per game", (a) => (a.fn ? a.fouls / a.fn : null), "n"]];
  const sortK = store.get("dmSort", 0);
  const L = cols.map((c) => dmLeague(M, { ...f, pos: "ALL" }, c[2]));
  const order = [...teams].sort((a, b) => (L[sortK].vals[a] ?? 99) - (L[sortK].vals[b] ?? 99));
  const gsum = teams.map((t) => dmRows(M, t, f).length); const gmin = Math.min(...gsum), gmax = Math.max(...gsum);
  v.innerHTML = `<section class="hero"><span class="eyebrow">NBA · defensive matchups · ${esc(M.seasons[f.season])}</span><h1>Team defence</h1>
      <p>What each defence allows and where it comes from: court area, shot type and opposing position. Tap a team for its half-court map, position splits and fouls. Ranks: <b>1st = allows the least</b> (fewest points, attempts, lowest percentage).</p></section>
    ${dmFilters(M, f)}
    ${gmax < 10 ? banner("warn", `<p class="small">Only ${gmin}–${gmax} games per team in this selection. Defence splits this early are mostly noise (see the reliability notes on each team page); switch the season to 2025-26 for a full season of evidence.</p>`) : ""}
    <section class="panel"><div class="tablewrap"><table class="pit dmtab"><thead><tr><th class="l">Team</th><th>Games</th>${cols.map(([s, l], i) => `<th><button class="thbtn ${i === sortK ? "on" : ""}" data-sort="${i}" title="${esc(l)}">${esc(s)}</button></th>`).join("")}</tr></thead><tbody>
      ${order.map((t) => `<tr><td class="l"><a href="#nba-defense.${esc(t)}">${teamTag("nba", t)}</a></td><td>${dmRows(M, t, f).length}</td>${cols.map((c, i) => { const val = L[i].vals[t]; const r = L[i].rank(t); return `<td class="c ${r && L[i].n ? (r <= 5 ? "lo2" : r <= 10 ? "lo" : r > L[i].n - 5 ? "hi2" : r > L[i].n - 10 ? "hi" : "") : ""}" data-tip="${esc(c[1])} (${c[3] === "pct" ? "share" : rateLbl(f)})|${dmFmt(c[3] === "pct" ? "pct" : "n", val)} · ${ord(r)} of ${L[i].n} · league ${dmFmt(c[3] === "pct" ? "pct" : "n", L[i].avg)}">${dmFmt(c[3] === "pct" ? "pct" : "n", val)}</td>`; }).join("")}</tr>`).join("")}</tbody></table></div>
      <p class="tiny muted">Per game unless "Per 100 possessions" is chosen (fouls are always per game). Blue = among the stingiest, red = among the most generous. Tap a column to sort.</p></section>
    ${dmSources(M)}`;
  dmBind(v, f);
  $$("[data-sort]", v).forEach((b) => b.addEventListener("click", () => { store.set("dmSort", +b.dataset.sort); route(); }));
}
function dmSources(M) {
  return `<section class="panel"><h2>Sources and definitions</h2><p class="small">${esc(M.source)}. Play-by-play updated ${esc(londonTime(M.updated.play_by_play))}; box scores ${esc(londonTime(M.updated.box_scores))}. Games played overnight arrive about 11:45 UK time; until their play-by-play arrives they are left out of every figure here, so all figures use the same games.</p>
    <details class="small"><summary>Shooting areas</summary><pre class="defs">${esc(M.definitions.zones)}</pre></details>
    <details class="small"><summary>Positions</summary><p>${esc(M.definitions.positions)}</p></details>
    <details class="small"><summary>Fouls, free throws, per 100 possessions and points</summary><p>${esc(M.definitions.fouls)}</p><p>${esc(M.definitions.ft_rate)}</p><p>${esc(M.definitions.per100)}</p><p>${esc(M.definitions.points)}</p></details></section>`;
}

// ---------------------------------------------------------------- NBA defence v position
// box columns in each team-game row: pts reb ast min fga fgm fg3a fg3m fta ftm oreb dreb stl blk tov
const DVP = [ // key, short, long, value from the summed box (before scaling), "pct" for rates
  ["pts", "PTS", "Points", (b) => b[0]],
  ["reb", "REB", "Rebounds", (b) => b[1]],
  ["oreb", "OREB", "Offensive rebounds", (b) => b[10]],
  ["dreb", "DREB", "Defensive rebounds", (b) => b[11]],
  ["ast", "AST", "Assists", (b) => b[2]],
  ["pra", "PRA", "Points + rebounds + assists", (b) => b[0] + b[1] + b[2]],
  ["fg3m", "3PM", "3-pointers made", (b) => b[7]],
  ["fg3a", "3PA", "3-point attempts", (b) => b[6]],
  ["fg3p", "3P%", "3-point percentage", (b) => (b[6] ? b[7] / b[6] : null), "pct"],
  ["fg2m", "2PM", "2-pointers made", (b) => b[5] - b[7]],
  ["fg2a", "2PA", "2-point attempts", (b) => b[4] - b[6]],
  ["fg2p", "2P%", "2-point percentage", (b) => (b[4] - b[6] ? (b[5] - b[7]) / (b[4] - b[6]) : null), "pct"],
  ["fta", "FTA", "Free-throw attempts", (b) => b[8]],
  ["stl", "STL", "Steals", (b) => b[12]],
  ["blk", "BLK", "Blocks", (b) => b[13]],
  ["tov", "TOV", "Turnovers", (b) => b[14]],
];
const DVP_POS = ["G", "F", "C"];
function dvpVal(a, s, f) {
  const v = s[3](a.bx); if (v == null) return null; if (s[4] === "pct") return v;
  const k = f.rate === "p36" ? (a.bx[3] ? 36 / a.bx[3] : 0) : dmScale(a, f); return v * k;
}
function dvpBoard(M, f) { // one aggregate per team and position; league ranks computed on demand
  const teams = Object.keys(M.rows[f.season] || {}).sort();
  const agg = {}; for (const t of teams) { const rows = dmRows(M, t, f); agg[t] = { games: rows.length }; for (const p of DVP_POS) agg[t][p] = dmAgg(rows, p); }
  const memo = {};
  const league = (p, key) => { if (memo[p + key]) return memo[p + key]; const s = DVP.find((x) => x[0] === key); const vals = {};
    for (const t of teams) { const val = dvpVal(agg[t][p], s, f); if (val != null && !isNaN(val) && agg[t].games) vals[t] = val; }
    const arr = Object.values(vals); const avg = arr.reduce((x, y) => x + y, 0) / (arr.length || 1);
    const sorted = Object.entries(vals).sort((a, b) => a[1] - b[1]).map((x) => x[0]);
    return (memo[p + key] = { vals, avg, n: arr.length, rank: (team) => sorted.indexOf(team) + 1 }); };
  return { teams, agg, league };
}
// red = gives up the most (good for that position's overs), blue = gives up the least
function dvpClass(r, n) { if (!r || !n) return ""; return r > n - 3 ? "hi2" : r > n - 8 ? "hi" : r <= 3 ? "lo2" : r <= 8 ? "lo" : ""; }
function dvpRel(M, p, key, games) { const k = key === "fg3p" ? "fg3m" : key === "fg2p" ? "fg2m" : key; return relAt(M.reliability[`${p}|box|${k}`], Math.max(games, 1)); }
const dvpFmt = (s, v) => (v == null || isNaN(v) ? "–" : s[4] === "pct" ? pct1(v) : dec(v, 1));
const POS_ONE = { G: "guards", F: "forwards", C: "centres" };
function dvF() { return Object.assign({ season: "2027", games: "all", venue: "all", stype: "R", rate: "pg", pos: "G", stat: "ast" }, store.get("dvpFilter", {})); }

async function viewNbaDvp(v) {
  const M = await api("/api/nba/matchups"); const f = dvF();
  if (!M.rows[f.season]) f.season = Object.keys(M.rows).slice(-1)[0];
  if (!DVP_POS.includes(f.pos)) f.pos = "G";
  const B = dvpBoard(M, f); const s = DVP.find((x) => x[0] === f.stat) || DVP[0];
  const L = Object.fromEntries(DVP_POS.map((p) => [p, B.league(p, s[0])]));
  const order = B.teams.filter((t) => B.agg[t].games).sort((a, b) => (L[f.pos].vals[b] ?? -1) - (L[f.pos].vals[a] ?? -1));
  const gs = order.map((t) => B.agg[t].games); const gmin = Math.min(...gs), gmax = Math.max(...gs);
  const relNow = Object.fromEntries(DVP_POS.map((p) => [p, relWord(dvpRel(M, p, s[0], Math.round((gmin + gmax) / 2)))]));
  const cell = (t, p) => { const val = L[p].vals[t]; const r = L[p].rank(t); const n = L[p].n;
    return `<td class="c ${dvpClass(r, n)}${p === f.pos ? " sel" : ""}" data-tip="${esc(t)} v opposing ${POS_ONE[p]} · ${esc(s[2])}|${dvpFmt(s, val)} ${s[4] === "pct" ? "" : rateLbl(f)} · league ${dvpFmt(s, L[p].avg)}|${r} of ${n} (${n} = gives up the most) · ${B.agg[t].games} games · signal: ${esc(relNow[p])}"><b>${dvpFmt(s, val)}</b><div class="tiny">${r}/${n}</div></td>`; };
  const most = (t) => { const best = DVP_POS.map((p) => [p, L[p].rank(t) / (L[p].n || 1)]).sort((a, b) => b[1] - a[1])[0]; return best && best[1] > 0.7 ? `${POS_NAME[best[0]]} (${L[best[0]].rank(t)}/${L[best[0]].n})` : "No standout"; };
  v.innerHTML = `<section class="hero"><span class="eyebrow">NBA · defence v position · ${esc(M.seasons[f.season])}</span><h1>Who gives up what, to whom</h1>
      <p>Every stat each defence allows to opposing guards, forwards and centres. Pick a stat, then a position to sort by. In each cell, <b>30/30 means that defence gives up the most</b> of that stat to that position and 1/30 the least. Red = generous, blue = stingy.</p></section>
    ${dmFilters(M, f, "", [["pg", "Per game"], ["p100", "Per 100 possessions"], ["p36", "Per 36 minutes at that position"]])}
    <div class="mktabs dvptabs" role="tablist" aria-label="Stat">${DVP.map(([k, sh, lg]) => `<button role="tab" class="${k === s[0] ? "on" : ""}" data-dvstat="${k}" title="${esc(lg)}">${sh}</button>`).join("")}</div>
    ${gmax < 15 ? banner("warn", `<p class="small"><b>Only ${gmin}–${gmax} games per team in this selection.</b> Position splits this early are mostly noise: for ${esc(s[2].toLowerCase())} the signal is ${esc(relNow.G)} for guards, ${esc(relNow.F)} for forwards and ${esc(relNow.C)} for centres. Switch the season to 2025-26 to see full-season patterns.</p>`) : ""}
    <section class="panel"><div class="row between"><h2>${esc(s[2])} allowed ${s[4] === "pct" ? "" : rateLbl(f)}</h2>
      <div class="mktabs small" role="tablist" aria-label="Sort by position">${DVP_POS.map((p) => `<button class="${p === f.pos ? "on" : ""}" data-dvpos="${p}">${POS_NAME[p]}</button>`).join("")}</div></div>
      <div class="tablewrap"><table class="pit dmtab dvp"><thead><tr><th class="l">Team</th><th>Games</th>${DVP_POS.map((p) => `<th class="${p === f.pos ? "on" : ""}"><button class="thbtn ${p === f.pos ? "on" : ""}" data-dvpos="${p}">v ${POS_NAME[p]}</button><div class="tiny muted">lg ${dvpFmt(s, L[p].avg)}</div></th>`).join("")}<th class="l">Gives up most to</th></tr></thead><tbody>
      ${order.map((tm) => `<tr><td class="l"><a href="#nba-defense.${esc(tm)}">${teamTag("nba", tm)}</a></td><td>${B.agg[tm].games}</td>${DVP_POS.map((p) => cell(tm, p)).join("")}<td class="l small">${esc(most(tm))}</td></tr>`).join("")}</tbody></table></div>
      <p class="tiny muted">Sorted by ${esc(POS_NAME[f.pos].toLowerCase())}, most allowed first. "Gives up most to" is the position where this defence ranks most generous for this stat (shown when it is in the top 30%). Signal after a full season: guards ${esc(relWord(dvpRel(M, "G", s[0], 82)))}, forwards ${esc(relWord(dvpRel(M, "F", s[0], 82)))}, centres ${esc(relWord(dvpRel(M, "C", s[0], 82)))}. Per game totals depend on how many minutes each position plays; per 36 minutes removes that.</p></section>
    ${dmSources(M)}`;
  dmBind(v, f, "dvpFilter");
  $$("[data-dvstat]", v).forEach((b) => b.addEventListener("click", () => { f.stat = b.dataset.dvstat; store.set("dvpFilter", f); route(); }));
  $$("[data-dvpos]", v).forEach((b) => b.addEventListener("click", () => { f.pos = b.dataset.dvpos; store.set("dvpFilter", f); route(); }));
}

// one team: every stat it allows to each position, with league ranks
function dvpMatrix(M, f, team) {
  const B = dvpBoard(M, { ...f, rate: f.rate === "p100" ? "p100" : "pg" });
  if (!B.agg[team] || !B.agg[team].games) return "";
  const g = B.agg[team].games; const give = [], hold = [];
  const rows = DVP.map((s) => { const cells = DVP_POS.map((p) => { const L = B.league(p, s[0]); const val = L.vals[team]; const r = L.rank(team);
      const rel = dvpRel(M, p, s[0], g);
      if (r && r > L.n - 3) give.push([r / L.n, `${s[2]} to ${POS_ONE[p]} (${r}/${L.n})`, rel]);
      if (r && r <= 3) hold.push([1 - r / L.n, `${s[2]} to ${POS_ONE[p]} (${r}/${L.n})`, rel]);
      return `<td class="c ${dvpClass(r, L.n)}" data-tip="${esc(team)} v opposing ${POS_ONE[p]} · ${esc(s[2])}|${dvpFmt(s, val)} (league ${dvpFmt(s, L.avg)}) · ${r} of ${L.n}, ${L.n} = gives up the most|signal: ${esc(relWord(rel))}"><b>${dvpFmt(s, val)}</b><div class="tiny">${r}/${L.n}</div></td>`; });
    return `<tr><td class="l"><b>${s[1]}</b><div class="tiny muted">${esc(s[2])}</div></td>${cells.join("")}</tr>`; }).join("");
  const li = (arr) => arr.sort((a, b) => b[0] - a[0]).slice(0, 6).map(([, txt, rel]) => `<li>${esc(txt)} <span class="tiny muted">(signal: ${esc(relWord(rel))})</span></li>`).join("");
  return `<section class="panel"><h2>Every stat by opposing position</h2>
    <p class="small">What ${esc(team)} allow to opposing guards, forwards and centres (${f.rate === "p100" ? "per 100 possessions" : "per game"}, ${g} games). <b>30/30 = gives up the most</b> of that stat to that position; red = generous, blue = stingy.</p>
    <div class="dvpsum">${give.length ? `<div><h3>Gives up the most</h3><ul class="small mlist">${li(give)}</ul></div>` : ""}${hold.length ? `<div><h3>Gives up the least</h3><ul class="small mlist">${li(hold)}</ul></div>` : ""}</div>
    <div class="tablewrap"><table class="pit deft dvp"><thead><tr><th class="l">Stat</th>${DVP_POS.map((p) => `<th>v ${POS_NAME[p]}</th>`).join("")}</tr></thead><tbody>${rows}</tbody></table></div>
    <p class="tiny muted">${g < 15 ? `With ${g} games these splits are mostly noise; compare the 2025-26 season. ` : ""}Steals, blocks and turnovers are those recorded by the opposing players (a guard's steals against this team). <a href="#nba-dvp">Compare all teams</a>.</p></section>`;
}

function viewNbaDefenseTeam(v, M, f, team) {
  const rows = dmRows(M, team, f); const a = dmAgg(rows, f.pos);
  const tot = a.z.reduce((s, x) => s + x[0], 0);
  const met = DM_METRIC[f.metric] ? f.metric : "pts";
  // league for each zone and summary, same filters and position
  const zl = {}; for (const z of DZ) zl[z] = dmLeague(M, f, (q) => DM_METRIC[met][1](dmZone(q, [z], f)));
  const cells = {}; for (const z of DZ) { const s = dmZone(a, [z], f); const L = zl[z]; const val = DM_METRIC[met][1](s); const rel = relAt(dmRel(M, f.pos, z, met), Math.max(a.games, 1));
    cells[z] = { tone: s.fga_n < 8 ? "zn" : dmTone(met, val, L.avg), label: met === "pct" ? (s.pct == null ? "–" : `${pctInt(s.pct)}%`) : dmFmt(met, val),
      tip: `${M.zone_labels[z]} · ${POS_NAME[f.pos]}|${DM_METRIC[met][0]} ${dmFmt(met, val)} (${met === "pct" ? "" : rateLbl(f) + ", "}league ${dmFmt(met, L.avg)}) · ${ord(L.rank(team))} of ${L.n}|${s.fga_n} attempts, ${a.games} games · signal: ${relWord(rel)}` }; }
  const sumRows = Object.entries(DSUM);
  const zoneRow = (label, zs, isSum) => { const s = dmZone(a, zs, f); const L = dmLeague(M, f, (q) => DM_METRIC[met][1](dmZone(q, zs, f))); const val = DM_METRIC[met][1](s);
    return `<tr class="${isSum ? "sum" : ""}"><td class="l">${esc(label)}<div class="tiny muted">${s.fga_n} att.</div></td><td>${dmFmt("n", s.made)}-${dmFmt("n", s.att)}</td><td>${dmFmt("pct", s.pct)}</td><td>${dmFmt("n", s.pts)}</td><td>${dmFmt("share", s.share)}</td><td>${dmFmt(met, L.avg)}</td><td class="${dmTone(met, val, L.avg)}t">${s.fga_n ? ord(L.rank(team)) : "–"}</td></tr>`; };
  // position matrix: points per game by area for each position
  const pm = ["G", "F", "C"].map((p) => { const q = dmAgg(rows, p); return { p, q, areas: [["RA"], ["PAINT"], ["MID"], ["LC3", "RC3"], ["LW3", "RW3"], ["TOP3"]].map((zs) => dmZone(q, zs, f)) }; });
  const posTable = ["G", "F", "C"].map((p) => { const q = dmAgg(rows, p); const k = dmScale(q, f); const b = q.bx; const L = dmLeague(M, { ...f, pos: p }, (x) => x.bx[0] * dmScale(x, f));
    return `<tr class="${f.pos === p ? "on" : ""}"><td class="l"><button class="linkbtn" data-dmpos="${p}">${POS_NAME[p]}</button></td><td><b>${dmFmt("n", b[0] * k)}</b><div class="tiny muted">${ord(L.rank(team))} · lg ${dmFmt("n", L.avg)}</div></td>
      <td>${dmFmt("n", (b[5] - b[7]) * k)}-${dmFmt("n", (b[4] - b[6]) * k)}<div class="tiny muted">${dmFmt("pct", b[4] - b[6] ? (b[5] - b[7]) / (b[4] - b[6]) : null)}</div></td>
      <td>${dmFmt("n", b[7] * k)}-${dmFmt("n", b[6] * k)}<div class="tiny muted">${dmFmt("pct", b[6] ? b[7] / b[6] : null)}</div></td>
      <td>${dmFmt("n", b[9] * k)}-${dmFmt("n", b[8] * k)}<div class="tiny muted">${dmFmt("n", b[9] * k)} pts</div></td><td>${dmFmt("n", b[1] * k)}</td><td>${dmFmt("n", b[2] * k)}</td></tr>`; }).join("");
  const LF = { fouls: dmLeague(M, { ...f, pos: "ALL" }, (x) => (x.fn ? x.fouls / x.fn : null)), sf: dmLeague(M, { ...f, pos: "ALL" }, (x) => (x.fn ? x.sf / x.fn : null)),
    fta: dmLeague(M, { ...f, pos: "ALL" }, (x) => x.fta * dmScale(x, f)), ftr: dmLeague(M, { ...f, pos: "ALL" }, (x) => { const t = x.z.reduce((s, y) => s + y[0], 0); return t ? x.fta / t : null; }) };
  const all = dmAgg(rows, "ALL"); const kAll = dmScale(all, f); const totAll = all.z.reduce((s, x) => s + x[0], 0);
  const relPts = relAt(M.reliability["ALL|pts_per100"], Math.max(a.games, 1));
  v.innerHTML = `<section class="hero"><span class="eyebrow">${esc(M.seasons[f.season])} · ${a.games} games · ${tot.toLocaleString("en-GB")} opponent shots</span><h1 class="withlogo">${logo("nba", team, "tl hero")}${esc(team)} defence</h1>
      <p>Allows <b>${dmFmt("n", all.pa * kAll)}</b> points ${rateLbl(f)} (${ord(dmLeague(M, { ...f, pos: "ALL" }, (x) => x.pa * dmScale(x, f)).rank(team))} of 30, 1st = fewest). How much this sample says about the defence: <b>${relWord(relPts)}</b>.</p>
      <p class="small"><a href="#nba-defense">All teams</a></p></section>
    ${dmFilters(M, f, `<label>Opposing position<select id="dmp">${Object.entries(POS_NAME).map(([k, l]) => `<option value="${k}" ${k === f.pos ? "selected" : ""}>${l}</option>`).join("")}</select></label>`)}
    ${a.games < 10 ? banner("warn", `<p class="small">${a.games} games in this selection: area and position splits are mostly noise this early. Treat colours as a hint, and compare with the full 2025-26 season.</p>`) : ""}
    <section class="panel"><div class="row between"><h2>Where opponents score${f.pos !== "ALL" ? ` · ${POS_NAME[f.pos]}` : ""}</h2>
      <div class="mktabs small">${Object.entries(DM_METRIC).map(([k, [l]]) => `<button class="${k === met ? "on" : ""}" data-dmmetric="${k}">${l}</button>`).join("")}</div></div>
      <div class="courtwrap">${courtMap(cells)}<div class="courtside">
        <div class="legend"><span><i class="box zf2b"></i>Favourable for a scorer</span><span><i class="box zf1b"></i>Slightly</span><span><i class="box znb"></i>About average</span><span><i class="box zd1b"></i>Slightly difficult</span><span><i class="box zd2b"></i>Difficult</span></div>
        <p class="tiny muted">${esc(DM_METRIC[met][0])} ${met === "pct" ? "" : rateLbl(f)} compared with the league under the same filters; ${esc(DM_METRIC[met][2])}. Areas with fewer than 8 attempts stay grey. Tap an area for its numbers.</p>
        <div class="ftbox"><b>Free throws (separate from court areas)</b><div>${dmFmt("n", a.ftm * dmScale(a, f))}-${dmFmt("n", a.fta * dmScale(a, f))} ${rateLbl(f)} · ${dmFmt("pct", a.fta ? a.ftm / a.fta : null)} · FT rate ${dmFmt("pct", tot ? a.fta / tot : null)}</div></div>
        <div id="zoneInfo" class="tiny"></div></div></div>
      <div class="tablewrap"><table class="deft"><thead><tr><th class="l">Area</th><th>Made-Att</th><th>FG%</th><th>Pts</th><th>Share</th><th>League (${esc(DM_METRIC[met][0].toLowerCase())})</th><th>Rank</th></tr></thead><tbody>
        ${DZ.map((z) => zoneRow(M.zone_labels[z], [z], false)).join("")}${sumRows.map(([l, zs]) => zoneRow(l, zs, true)).join("")}</tbody></table></div>
      <p class="tiny muted">${rateLbl(f)} for made-attempted and points. Summary rows (shaded) add the areas above them and are not extra shots. Left and right are as drawn, basket at the bottom.</p></section>
    <section class="panel"><h2>Defence v position</h2><p class="small">Box-score production by opposing players listed at each position. Tap a position to filter the map and areas above.</p>
      <div class="tablewrap"><table class="deft"><thead><tr><th class="l">Position</th><th>Pts</th><th>2PM-2PA</th><th>3PM-3PA</th><th>FTM-FTA</th><th>Reb</th><th>Ast</th></tr></thead><tbody>${posTable}</tbody></table></div>
      <h3 style="margin-top:12px">Points by area and position (${rateLbl(f)})</h3>
      <div class="tablewrap"><table class="deft"><thead><tr><th class="l">Position</th><th>Rim</th><th>Paint</th><th>Mid</th><th>Corner 3</th><th>Wing 3</th><th>Top 3</th><th>3PA</th></tr></thead><tbody>
        ${pm.map(({ p, q, areas }) => `<tr><td class="l">${POS_NAME[p]}</td>${areas.map((s, i) => { const zs = [["RA"], ["PAINT"], ["MID"], ["LC3", "RC3"], ["LW3", "RW3"], ["TOP3"]][i]; const L = dmLeague(M, { ...f, pos: p }, (x) => dmZone(x, zs, f).pts); return `<td class="${dmTone("pts", s.pts, L.avg)}t" data-tip="${esc(POS_NAME[p])} · ${esc(zs.map((z) => M.zone_labels[z]).join(" + "))}|${dmFmt("n", s.pts)} pts (league ${dmFmt("n", L.avg)}) · ${s.fga_n} attempts">${dmFmt("n", s.pts)}</td>`; }).join("")}<td>${dmFmt("n", dmZone(q, DSUM["3PT"], f).att)}</td></tr>`).join("")}</tbody></table></div>
      <p class="tiny muted">Individual positions (PG, SG, SF, PF) are not available: this source lists each player as G, F or C in each game, and every shot and free throw belongs to exactly one of those. Position splits are the noisiest numbers here (about 0.15 reliability after 10 games, 0.55–0.65 after a full season).</p></section>
    ${dvpMatrix(M, f, team)}
    <section class="panel"><h2>Fouls and free throws</h2><div class="tablewrap"><table class="deft"><thead><tr><th class="l">Measure</th><th>${esc(team)}</th><th>League</th><th>Rank</th></tr></thead><tbody>
      <tr><td class="l">Fouls committed per game</td><td>${dmFmt("n", all.fn ? all.fouls / all.fn : null)}</td><td>${dmFmt("n", LF.fouls.avg)}</td><td>${ord(LF.fouls.rank(team))}</td></tr>
      <tr><td class="l">Shooting fouls per game</td><td>${dmFmt("n", all.fn ? all.sf / all.fn : null)}</td><td>${dmFmt("n", LF.sf.avg)}</td><td>${ord(LF.sf.rank(team))}</td></tr>
      <tr><td class="l">Opponent FTM-FTA ${rateLbl(f)}</td><td>${dmFmt("n", all.ftm * kAll)}-${dmFmt("n", all.fta * kAll)}</td><td>${dmFmt("n", LF.fta.avg)} FTA</td><td>${ord(LF.fta.rank(team))}</td></tr>
      <tr><td class="l">Free-throw points allowed ${rateLbl(f)}</td><td>${dmFmt("n", all.ftm * kAll)}</td><td colspan="2" class="l tiny muted">kept separate from court areas</td></tr>
      <tr><td class="l">Opponent FT rate (FTA / FGA)</td><td>${dmFmt("pct", totAll ? all.fta / totAll : null)}</td><td>${dmFmt("pct", LF.ftr.avg)}</td><td>${ord(LF.ftr.rank(team))}</td></tr>
      ${["G", "F", "C"].map((p) => { const q = dmAgg(rows, p); const t = q.z.reduce((s, x) => s + x[0], 0); return `<tr><td class="l">FTM-FTA by opposing ${POS_NAME[p].toLowerCase()} ${rateLbl(f)}</td><td>${dmFmt("n", q.ftm * dmScale(q, f))}-${dmFmt("n", q.fta * dmScale(q, f))}</td><td colspan="2" class="l tiny muted">FT rate ${dmFmt("pct", t ? q.fta / t : null)}</td></tr>`; }).join("")}
      </tbody></table></div><p class="tiny muted">1st = fewest. Fouls drawn by position are not available (the play-by-play does not record who was fouled). ${esc(M.definitions.ft_rate)}</p></section>
    ${dmSources(M)}`;
  dmBind(v, f);
  $$(".court .zone", v).forEach((g) => g.addEventListener("click", () => { $("#zoneInfo", v).innerHTML = esc(g.dataset.tip).split("|").map((x, i) => (i ? `<div>${x}</div>` : `<b>${x}</b>`)).join(""); }));
}

// player matchup panel: his shot profile against the opponent's defence
async function nbaMatchupPanel(card, oppSeason) {
  let M, P; try { [M, P] = await Promise.all([api("/api/nba/matchups"), api(`/api/nba/shotprofile/${card.player_id}`)]); } catch { return ""; }
  const seasons = Object.keys(M.rows).sort(); const cur = seasons[seasons.length - 1], prev = seasons[seasons.length - 2];
  // his profile: this season once he has 10 games, otherwise last season
  const pSeason = P.seasons[cur] && P.seasons[cur].games >= 10 ? cur : P.seasons[prev] ? prev : cur;
  const pf = P.seasons[pSeason];
  if (!pf) return `<section class="panel"><h2>Matchup</h2><p class="small muted">No shot data for this player yet.</p></section>`;
  oppSeason = oppSeason || store.get("muSeason", cur);
  const f = { season: oppSeason, games: "all", venue: "all", stype: "R", rate: "p100", pos: P.position };
  const toggle = `<div class="mktabs small">${[cur, prev].filter(Boolean).map((s) => `<button class="${s === oppSeason ? "on" : ""}" data-museason="${s}">${esc(card.opponent)} ${esc(M.seasons[s])} (${dmRows(M, card.opponent, { ...f, season: s }).length} g)</button>`).join("")}</div>`;
  const opp = card.opponent; const rows = dmRows(M, opp, f); const allRows = dmRows(M, opp, { ...f, pos: "ALL" });
  const tot = DZ.reduce((s, z) => s + pf.zones[z][0], 0);
  const lines = []; const tbl = DZ.map((z) => {
    const [a, m] = pf.zones[z]; const share = tot ? a / tot : 0; const q = dmAgg(rows, f.pos); const s = dmZone(q, [z], f);
    const La = dmLeague(M, f, (x) => dmZone(x, [z], f).att), Lp = dmLeague(M, f, (x) => dmZone(x, [z], f).pct);
    const rel = relAt(dmRel(M, f.pos, z, "att"), Math.max(q.games, 1));
    if (share >= 0.12 && s.fga_n >= 8 && La.avg) {
      const dAtt = s.att / La.avg - 1, dPct = s.pct != null && Lp.avg != null ? (s.pct - Lp.avg) * 100 : 0;
      const what = `${pctInt(share)}% of his shots come from ${M.zone_labels[z].toLowerCase()}`;
      if (dAtt >= 0.1 || dPct >= 3) lines.push(["adv", `${what}; ${opp} allow ${POS_NAME[f.pos].toLowerCase()} ${dAtt >= 0 ? "+" : ""}${pctInt(dAtt)}% attempts there and ${dmFmt("pct", s.pct)} (league ${dmFmt("pct", Lp.avg)}).`, rel]);
      else if (dAtt <= -0.1 || dPct <= -3) lines.push(["dif", `${what}; ${opp} allow ${POS_NAME[f.pos].toLowerCase()} ${pctInt(dAtt)}% attempts there and ${dmFmt("pct", s.pct)} (league ${dmFmt("pct", Lp.avg)}).`, rel]);
    }
    return `<tr><td class="l">${esc(M.zone_labels[z])}</td><td>${pctInt(share)}%</td><td>${dmFmt("pct", a ? m / a : null)}</td><td>${dmFmt("n", (m * (DZ3.has(z) ? 3 : 2)) / pf.games)}</td><td class="${dmTone("att", s.att, La.avg)}t">${dmFmt("n", s.att)}<div class="tiny muted">lg ${dmFmt("n", La.avg)}</div></td><td class="${dmTone("pct", s.pct, Lp.avg)}t">${dmFmt("pct", s.pct)}<div class="tiny muted">lg ${dmFmt("pct", Lp.avg)}</div></td></tr>`; }).join("");
  const gN = dmAgg(rows, f.pos).games;
  return `<section class="panel"><div class="row between"><h2>Matchup v ${esc(opp)} defence</h2>${toggle}</div>
    <p class="small">His shot profile (${esc(M.seasons[pSeason])}, ${pf.games} games) beside what ${esc(opp)} allow to opposing ${esc(POS_NAME[f.pos].toLowerCase())} per 100 possessions (${esc(M.seasons[f.season])}, ${gN} games). Green = the defence allows more than average there, red = less.</p>
    ${lines.length ? `<ul class="small mlist">${lines.map(([k, t, rel]) => `<li class="${k}"><b>${k === "adv" ? "Advantage" : "Difficulty"}:</b> ${esc(t)} <span class="tiny muted">(signal: ${relWord(rel)})</span></li>`).join("")}</ul>` : `<p class="small muted">No clear advantage or difficulty in his main shooting areas at this sample size.</p>`}
    <div class="tablewrap"><table class="deft"><thead><tr><th class="l">Area</th><th>His share</th><th>His FG%</th><th>His pts per game</th><th>${esc(opp)} att. allowed per 100 poss.</th><th>${esc(opp)} FG% allowed</th></tr></thead><tbody>${tbl}</tbody></table></div>
    ${(() => { const B = dvpBoard(M, { ...f, rate: "pg" }); const g = B.agg[opp]?.games || 0; if (!g || !DVP_POS.includes(f.pos)) return "";
      const keys = ["pts", "reb", "ast", "pra", "fg3m", "fg3a", "fg2m", "fta", "stl", "blk", "tov"];
      return `<h3 style="margin-top:12px">${esc(opp)} v opposing ${POS_ONE[f.pos]}, every stat</h3><div class="tablewrap"><table class="pit deft dvp"><thead><tr><th class="l">Stat</th><th>Allowed per game</th><th>League</th><th>Rank</th></tr></thead><tbody>${keys.map((k) => { const s = DVP.find((x) => x[0] === k); const L = B.league(f.pos, k); const r = L.rank(opp);
        return `<tr><td class="l">${esc(s[2])}</td><td>${dvpFmt(s, L.vals[opp])}</td><td>${dvpFmt(s, L.avg)}</td><td class="c ${dvpClass(r, L.n)}" data-tip="signal: ${esc(relWord(dvpRel(M, f.pos, k, g)))}">${r}/${L.n}</td></tr>`; }).join("")}</tbody></table></div>
        <p class="tiny muted">${g} games; 30/30 = gives up the most to this position. Red = generous, blue = stingy.</p>`; })()}
    <p class="tiny muted">Free throws: he takes ${dmFmt("n", pf.ft[0] / pf.games)} a game (${dmFmt("pct", pf.ft[0] ? pf.ft[1] / pf.ft[0] : null)}). <a href="#nba-defense.${esc(opp)}">Full ${esc(opp)} defence breakdown</a>. Context only: these splits are not inputs to the projection.</p></section>`;
}

// ---------------------------------------------------------------- Defences
function fmtVal(v, f) { if (v == null || isNaN(v)) return "–"; return f === "pct" ? pct1(v) : f === "dec2" ? dec(v, 2) : f === "int" ? dec(v, 0) : dec(v, 1); }
function fmtDiff(v, f) { if (v == null || isNaN(v)) return "–"; const r = f === "pct" ? rnd(shift(v, 2), 1) : rnd(Number(v), f === "dec2" ? 2 : 1); return signed(r, Math.abs(r).toFixed(f === "dec2" ? 2 : 1) + (f === "pct" ? " pts" : "")); }
function rankClass(r) { return r <= 4 ? "lo2" : r <= 10 ? "lo" : r >= 29 ? "hi2" : r >= 23 ? "hi" : ""; }
function ordinal(n) { const s = ["th", "st", "nd", "rd"], v = n % 100; return n + (s[(v - 20) % 10] || s[v] || s[0]); }
function verdictPill(v, p) { const pp = p == null ? "" : ` · ${pctInt(p)}%`; return v === "likely strong" ? `<span class="pill good"><span class="dot"></span>Likely strong${pp}</span>` : v === "likely weak" ? `<span class="pill out"><span class="dot"></span>Likely weak${pp}</span>` : `<span class="pill"><span class="dot"></span>Can't tell yet${pp}</span>`; }
const HEAT_SHORT = { ypp: "Overall", net_ypd: "Pass", sack_rate: "Pass rush", ypc: "Run", qb_rush_ypg: "QB runs", ypg_WR: "v WR", ypg_TE: "v TE", ypg_RB: "v RB" };
const rel = (D, k) => (D.reliability && D.reliability[k]) || null;
function reliabilityWord(c, skill) {
  if (c == null) return "not measured";
  const w = ["partly carries over", "carries over a little", "mostly noise", "almost pure noise"];
  let i = c >= 0.25 ? 0 : c >= 0.15 ? 1 : c >= 0.08 ? 2 : 3;
  if (skill != null && skill < 0.02) i = Math.max(i, 2); // barely beat "assume average" in 2022–25
  return w[i];
}

function rankCell(m, team) {
  if (!m) return `<td class="c">–</td>`;
  const clear = m.verdict !== "can't tell yet";
  return `<td class="c ${rankClass(m.rank)}" data-tip="${esc(`${ordinal(m.rank)} of 32 · ${m.verdict}`)}|${esc(m.label)}: ${esc(fmtVal(m.actual, m.fmt))} allowed v ${esc(fmtVal(m.expected, m.fmt))} usual · ${pctInt(m.p_better)}% chance better than average">${clear ? `<b>${m.rank}</b>` : m.rank}</td>`;
}

async function viewDefense(v, team) {
  const D = await api("/api/defense");
  if (team) return viewDefenseTeam(v, D, team);
  const cols = D.heat_columns; const saved = store.get("defSort", cols[0]); const sortKey = cols.includes(saved) ? saved : cols[0];
  const teams = Object.values(D.teams).sort((a, b) => (a.metrics[sortKey]?.rank ?? 99) - (b.metrics[sortKey]?.rank ?? 99) || a.team.localeCompare(b.team));
  v.innerHTML = `<section class="hero"><span class="eyebrow">${D.season} season · games before week ${D.before_week}</span><h1>Team defences</h1>
      <p>How each defence has done against the pass, the run, each receiving position and quarterback runs. Every number is compared with what the same opponents managed in their other games, so a soft schedule does not flatter a defence. Rank 1 is the best defence; tap a team for the detail.</p></section>
    <section class="panel"><div class="row between"><h2>Ranks by area</h2><label class="small">Sort by <select id="dsort">${cols.map((c) => `<option value="${c}" ${c === sortKey ? "selected" : ""}>${HEAT_SHORT[c]}</option>`).join("")}</select></label></div>
      <div class="legend"><span><i class="box" style="background:var(--pit-lo2)"></i>Top 4</span><span><i class="box" style="background:var(--pit-lo)"></i>5–10</span><span><i class="box" style="background:var(--pit-hi)"></i>23–28</span><span><i class="box" style="background:var(--pit-hi2)"></i>Bottom 4</span><span><b>Bold</b> = clear evidence</span></div>
      <div class="tablewrap"><table class="pit heatdef"><thead><tr><th class="l">Team</th><th>Games</th>${cols.map((c) => `<th>${HEAT_SHORT[c]}</th>`).join("")}<th class="l">Pattern</th></tr></thead><tbody>
      ${teams.map((t) => `<tr><td class="l"><a href="#defense.${esc(t.team)}">${teamTag("nfl", t.team)}</a></td><td>${t.games}</td>${cols.map((c) => rankCell(t.metrics[c], t.team)).join("")}<td class="l">${esc(t.summary.tag)}</td></tr>`).join("")}</tbody></table></div>
      <p class="tiny muted">Measures: Overall = yards per play; Pass = net yards per dropback (sacks and scrambles included); Pass rush = sack rate; Run = yards per designed run by non-quarterbacks; QB runs = quarterback rushing yards per game; v WR / TE / RB = receiving yards to that position per game. ${esc(D.note)}</p></section>
    ${D.reliability && Object.keys(D.reliability).length ? `<section class="panel"><h2>Which of these are real?</h2>
      <p class="small">Most early-season defensive splits are noise. We checked every measure on 2012–2025: how much of a difference seen in a defence's first games carried over into the rest of that season (first fitted on 2012–2021, then checked on 2022–2025). The ranks above already allow for this.</p>
      <div class="tablewrap"><table class="deft"><thead><tr><th class="l">Area</th><th>Carries over after 6 games</th><th>'Likely' labels that held, 2022–25</th></tr></thead><tbody>
      ${cols.map((c) => { const r = D.reliability[c]; if (!r) return ""; return `<tr><td class="l"><b>${HEAT_SHORT[c]}</b><div class="tiny muted">${esc(r.label)}</div></td><td>${pctInt(r.carry_over_6g)}%<div class="tiny muted">${reliabilityWord(r.carry_over_6g, r.skill_vs_average)}</div></td><td>${r.labels_held != null && r.labels_check >= 10 ? `${pctInt(r.labels_held)}%<div class="tiny muted">of ${r.labels_check}</div>` : `<span class="tiny muted">too few labels</span>`}</td></tr>`; }).join("")}</tbody></table></div>
      <p class="tiny muted">"Held" = the defence stayed on the same side of average over the rest of the season; 50% would be a coin flip, and even a correct label can miss because the remaining games are noisy too. Full table for every measure: reports/defense/STABILITY.md.</p></section>` : ""}
    <section class="panel"><h2>How the forecasts use this</h2><p class="small">The forecasts already adjust for each opponent's plays allowed, pass rate, sack rate, yards per completion, catch rate and yards per carry. Splits against each receiving position are shown here for context only: they carry over too little from week to week (table above) to justify adding them to the model.</p></section>`;
  $("#dsort").addEventListener("change", (e) => { store.set("defSort", e.target.value); route(); });
}

const lc1 = (x) => /^[A-Z][a-z]/.test(x) ? x[0].toLowerCase() + x.slice(1) : x;
function viewDefenseTeam(v, D, team) {
  const t = D.teams[team];
  if (!t) { v.innerHTML = banner("warn", `<p>No defence profile for ${esc(team)}.</p>`); return; }
  const byFacet = {};
  Object.entries(t.metrics).forEach(([k, m]) => { (byFacet[m.facet] ||= []).push([k, m]); });
  const order = ["overall", "pass", "rush", "run", "qb", "vs_WR", "vs_TE", "vs_RB"];
  v.innerHTML = `<section class="hero"><span class="eyebrow">${D.season} defence · ${t.games} games before week ${D.before_week}</span><h1 class="withlogo">${logo("nfl", team, "tl hero")}${esc(team)} defence</h1>
      <p><b>${esc(t.summary.tag[0].toUpperCase() + t.summary.tag.slice(1))}.</b> ${t.summary.strong.length ? `Clear strengths: ${esc(t.summary.strong.map(lc1).join("; "))}.` : ""} ${t.summary.weak.length ? `Clear weaknesses: ${esc(t.summary.weak.map(lc1).join("; "))}.` : ""} ${!t.summary.strong.length && !t.summary.weak.length ? "No area is clearly better or worse than average yet; differences so far are within normal game-to-game noise." : ""}</p>
      <p class="small"><a href="#defense">All defences</a></p></section>
    ${order.filter((f) => byFacet[f]).map((f) => `<section class="panel"><h2>${esc(D.facets[f])}</h2><div class="tablewrap"><table class="deft"><thead><tr><th class="l">Measure</th><th>Allowed / usual</th><th>Rank</th><th class="l">Evidence</th></tr></thead><tbody>
      ${byFacet[f].map(([k, m]) => `<tr><td class="l">${esc(m.label)}${rel(D, k) ? `<div class="tiny muted">${reliabilityWord(rel(D, k).carry_over_6g, rel(D, k).skill_vs_average)}</div>` : ""}</td><td>${fmtVal(m.actual, m.fmt)} / ${fmtVal(m.expected, m.fmt)}<div class="tiny muted">lasting ${fmtDiff(m.shrunk, m.fmt)}</div></td><td class="${rankClass(m.rank)}">${ordinal(m.rank)}${m.prev_rank ? `<div class="tiny muted">${D.season - 1}: ${ordinal(m.prev_rank)}</div>` : ""}</td><td class="l">${verdictPill(m.verdict)}<div class="tiny muted">${pctInt(m.p_better)}% chance better than average</div></td></tr>`).join("")}</tbody></table></div></section>`).join("")}
    <p class="tiny muted">Allowed = this season before week ${D.before_week}. Usual = what the same offences managed in their other games. Lasting = the part of the difference expected to last for the rest of the season, based on how much such differences carried over in 2012–2021; noisy measures are shrunk almost to zero. The percentage is the chance the defence is truly better than average on that measure. Interception and sack rates: higher is better; everything else: lower is better. ${D.season - 1} = rank over the full ${D.season - 1} regular season, same method.</p>`;
}

const OPP_SHORT = { net_ypd: "pass D", sack_rate: "pass rush", ypc: "run D", rush_ypg: "rush yds/g", qb_rush_ypg: "QB runs", ypg_WR: "v WR", ypg_TE: "v TE", ypg_RB: "v RB" };
function oppContext(c, D) {
  if (!D || !D.teams) return "";
  const t = D.teams[c.opponent]; if (!t) return "";
  const pos = { WR: "WR", TE: "TE", RB: "RB", FB: "RB" }[c.position];
  let keys = [];
  if (["receptions", "rec_yds"].includes(c.market) && pos) keys = ["net_ypd", `ypg_${pos}`];
  else if (["rush_yds", "rush_att"].includes(c.market)) keys = c.position === "QB" ? ["qb_rush_ypg"] : ["ypc", "rush_ypg"];
  else if (["pass_yds", "pass_att", "pass_cmp"].includes(c.market)) keys = ["net_ypd", "sack_rate"];
  const bits = keys.filter((k) => t.metrics[k]).map((k) => ({ ...t.metrics[k], key: k })).map((m) => `<span class="${rankClass(m.rank)}" style="padding:0 4px;border-radius:4px" data-tip="${esc(m.label)}|${esc(fmtVal(m.actual, m.fmt))} allowed v ${esc(fmtVal(m.expected, m.fmt))} usual · ${esc(m.verdict)}">${ordinal(m.rank)}${m.verdict !== "can't tell yet" ? "*" : ""} ${esc(OPP_SHORT[m.key] || m.label)}</span>`);
  return bits.length ? `<div class="tiny muted">Opp. defence (${esc(c.opponent)}, 1 = toughest; * = clear evidence): ${bits.join(" · ")} <a href="#defense.${esc(c.opponent)}">more</a></div>` : "";
}

// ---------------------------------------------------------------- Saved record
async function viewRecord(v) {
  const r = await api("/api/record");
  setCopy("rec", toCSV(r.editions.map(({ games, ...e }) => ({ ...e, code: e.code?.git_commit, games_before_kickoff: games.filter((g) => g.published_before_kickoff).length }))));
  v.innerHTML = `<section class="hero"><span class="eyebrow">Genuine pre-match record</span><h1>Saved record</h1><p>${esc(r.note)} Editions are immutable files with a SHA-256 hash, committed to git so the push time is independently timestamped. A correction is a new edition; nothing is overwritten.</p></section>
    <section class="panel"><h2>Scoring status by model</h2><div class="tablewrap"><table><thead><tr><th class="l">Model</th><th>In record</th><th>Scored</th><th>Void (did not play)</th><th>Awaiting result</th></tr></thead><tbody>${Object.entries(r.summary).sort((a, b) => MODEL_ORDER.indexOf(a[0]) - MODEL_ORDER.indexOf(b[0])).map(([m, s]) => `<tr><td class="l"><span class="sw" style="background:var(--m-${m})"></span>${esc(m)} ${esc(MODEL_NAMES[m])}</td><td>${s.forecasts.toLocaleString("en-GB")}</td><td>${s.settled_scored.toLocaleString("en-GB")}</td><td>${(s.void ?? 0).toLocaleString("en-GB")}</td><td>${s.pending.toLocaleString("en-GB")}</td></tr>`).join("")}</tbody></table></div>
      ${Object.values(r.summary).some((s) => s.settled_scored) ? `<h3>Mean CRPS by market (lower is better)</h3><div class="tablewrap"><table><thead><tr><th class="l">Model</th>${Object.keys(MARKET_LABELS).map((k) => `<th>${MARKET_LABELS[k]}</th>`).join("")}</tr></thead><tbody>${Object.entries(r.summary).sort((a, b) => MODEL_ORDER.indexOf(a[0]) - MODEL_ORDER.indexOf(b[0])).map(([m, s]) => `<tr><td class="l">${esc(m)}</td>${Object.keys(MARKET_LABELS).map((k) => `<td>${s.by_market?.[k] ? `${dec(s.by_market[k].mean_crps, 3)} <span class="muted">(${s.by_market[k].n})</span>` : "–"}</td>`).join("")}</tr>`).join("")}</tbody></table></div><p class="tiny muted">Early prospective numbers rest on a handful of games and carry wide uncertainty; compare models within a market only.</p>` : `<p class="small muted">No game in the record is final yet. Scores appear after <code>npl update && npl settle</code> once nflverse publishes final statistics (usually the morning after).</p>`}</section>
    <div class="row between"><h2>Editions</h2><span>${SNAP ? copyBtn("rec", "editions CSV") : exportLinks("record", "Editions")}</span></div>
    ${r.editions.map((e) => `<section class="panel"><div class="row between"><h3 class="mono" style="font-size:0.95rem">${esc(e.edition_id)}</h3><span class="pill ${e.retrospective ? "" : "good"}"><span class="dot"></span>${e.retrospective ? "Retrospective" : "Published"}</span></div>
      <dl class="kv"><dt>Published</dt><dd>${esc(e.published_london)}</dd><dt>Forecasts</dt><dd>${e.n_forecasts.toLocaleString("en-GB")} across ${Object.keys(MODEL_NAMES).length - 1} models</dd><dt>Code</dt><dd class="mono">${esc(e.code?.git_commit)}${e.code?.dirty ? " (dirty)" : ""}</dd><dt>File</dt><dd class="mono">${esc(e.file)}</dd><dt>SHA-256</dt><dd class="mono">${esc(e.sha256)}</dd><dt>Inputs hash</dt><dd class="mono">${esc(e.inputs_hash)}</dd></dl>
      <div class="tablewrap"><table><thead><tr><th class="l">Game</th><th class="l">Kickoff (UK)</th><th class="l">In record</th></tr></thead><tbody>${e.games.map((g) => `<tr><td class="l"><a href="#game.${esc(g.game_id)}">${esc(g.away)} at ${esc(g.home)}</a></td><td class="l">${esc(g.kickoff_london)}</td><td class="l">${g.published_before_kickoff ? '<span class="pill good"><span class="dot"></span>Yes</span>' : '<span class="pill"><span class="dot"></span>No, kicked off earlier</span>'}</td></tr>`).join("")}</tbody></table></div></section>`).join("")}`;
}

// ---------------------------------------------------------------- Data & methodology
const BLOCKED = [
  ["Bookmaker prices", "US books only", "The Odds API, US books only: a research reference, not prices a UK customer can take. Every pull keeps a credit floor (NPL_ODDS_FLOOR, default 400)."],
  ["UK-accessible NFL prop prices", "Unavailable", "On 26 Oct the odds feed's UK region listed match odds, spreads and totals only: no player props (D-018)."],
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
  const cell = (dsn, s) => { const r = cov.find((c) => c.dataset === dsn && c.season === s); return r ? `<td class="c has" data-tip="${r.rows.toLocaleString("en-GB")} rows|${esc(dsn)} ${s}">${r.rows >= 1000 ? rnd(r.rows / 1000) + "k" : r.rows}</td>` : `<td class="c none">·</td>`; };
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

async function loadLook() {
  for (const s of ["nfl", "nba"]) { try { LOOK[s] = await api(`/api/visuals/${s}`); } catch { /* no photos: initials are shown */ } }
}
shell();
loadLook().finally(() => { paintMarks(); route(); });
