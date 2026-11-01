/* Mosswood Chess: the "Sketchbook" ink renderer (window.Ink).
   Turns the clean SVG creature art into wobbly ballpoint-pen drawings (hatching instead of colour), and draws the
   hand-made board, square marks, arrows, badges and capture effects. Every drawing can carry three slightly different
   frames (.ink-f0/1/2) that the stylesheet cycles for a "line boil". Output is deterministic: same input, same ink.

   API
     Ink.init({ink:'blue'|'black'})       idempotent; adds hidden <svg id="ink-defs"> (hatch patterns ink-h1..4-0..2, ink-ph1..4)
     Ink.colors                           {ink, red, green, hi, pencil, paper}
     Ink.convert(markup, {frames, seed, side, weight, cls})   any SVG -> ink SVG (root classes kept + "ink-svg")
     Ink.piece(code, {palette, cls, frames, weight})          cached; source = (PieceArt.clean || PieceArt).svg
     Ink.coach(expr, {frames, weight, cls})                   cached; source = (PieceArt.clean || PieceArt).coach
     Ink.board({seed, frames=1})          <svg class="ink-board"> 800x800 hand-ruled grid, dark squares hatched
     Ink.mark(kind, seed)                 <svg class="ink-mark ink-mark-KIND"> for one square
     Ink.badge(sym, color)                <svg class="ink-badge"> pen circle + handwritten symbol
     Ink.arrow(x0, y0, x1, y1, {color, dashed, seed, width, trim:[start, end]})   <g class="ink-arrow"> in 800x800 space
     Ink.fx(kind, seed, {color})          <svg class="ink-fx-svg ink-fx-KIND">: cross | poof | stars
     Ink.pen(nameOrKind) / Ink.penFor(kind)   colour lookup ('ink' -> currentColor, 'red', 'green', 'best', 'blunder', ...)
   Ink colour is written as currentColor with a color="" attribute on each root, so CSS (e.g. body.ink-black .ink-svg
   { color: #2a2a30 }) can recolour existing drawings instantly. Classes on generated paths: ink-pp (paper fill),
   ink-hx (hatching), ink-o (outline), ink-o2 (second pen pass), ink-pl (paper line), ink-sh (shading ticks),
   ink-bl (blush), ink-sd (ground scribble, inside the original .pc-shadow group). */
(function (global) {
  'use strict';

  const doc = global.document;
  const TAU = Math.PI * 2;
  const PAPER = '#fffdf6';
  const PENS = { blue: '#22408f', black: '#2a2a30' };
  const colors = { ink: PENS.blue, red: '#d0373b', green: '#2c8a47', hi: 'rgba(255,224,40,.5)', pencil: '#77736b', paper: PAPER };
  const HI_FILL = '#ffe028';
  const FONT = "Caveat, 'Patrick Hand', 'Comic Sans MS', 'Segoe Print', cursive";

  /* ---------------------------------------------------------------- small helpers */
  function rng(seed) {
    let a = (seed >>> 0) || 0x9e3779b9;
    return function () {
      a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function hash() {
    let h = 2166136261;
    for (let a = 0; a < arguments.length; a++) {
      const s = String(arguments[a]) + '\u0001';
      for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
    }
    return h >>> 0;
  }
  const r1 = v => Math.round(v * 10) / 10;
  const r2 = v => Math.round(v * 100) / 100;
  const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
  const num = (v, d) => { const n = parseFloat(v); return isFinite(n) ? n : d; };
  const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const dist = (a, b) => Math.hypot(b[0] - a[0], b[1] - a[1]);

  /* ---------------------------------------------------------------- colour */
  const NAMED = { white: [255, 255, 255], black: [0, 0, 0], red: [255, 0, 0], green: [0, 128, 0], blue: [0, 0, 255], yellow: [255, 255, 0], gold: [255, 215, 0], orange: [255, 165, 0], pink: [255, 192, 203], purple: [128, 0, 128], gray: [128, 128, 128], grey: [128, 128, 128], brown: [165, 42, 42] };
  function parseColor(c) {
    if (c == null) return null;
    c = String(c).trim().toLowerCase();
    if (!c || c === 'none' || c === 'transparent') return { none: true };
    if (c.startsWith('url(')) return { r: 128, g: 128, b: 128, a: 1 };
    if (c === 'currentcolor') return { r: 40, g: 40, b: 60, a: 1 };
    if (c[0] === '#') {
      let h = c.slice(1);
      if (h.length === 3 || h.length === 4) h = h.split('').map(x => x + x).join('');
      const n = parseInt(h.slice(0, 6), 16);
      if (!isFinite(n)) return null;
      return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255, a: h.length === 8 ? parseInt(h.slice(6), 16) / 255 : 1 };
    }
    const m = c.match(/^rgba?\(([^)]*)\)/);
    if (m) {
      const p = m[1].split(/[\s,/]+/).filter(Boolean).map(v => (v.endsWith('%') ? parseFloat(v) * 2.55 : parseFloat(v)));
      return { r: p[0] || 0, g: p[1] || 0, b: p[2] || 0, a: p.length > 3 ? (m[1].trim().endsWith('%') ? p[3] / 255 : p[3]) : 1 };
    }
    if (NAMED[c]) return { r: NAMED[c][0], g: NAMED[c][1], b: NAMED[c][2], a: 1 };
    return { r: 128, g: 128, b: 128, a: 1 };
  }
  const lum = c => (0.299 * c.r + 0.587 * c.g + 0.114 * c.b) / 255;

  /** Pen colour lookup: 'ink'/'blue'/'pen' -> currentColor (the svg's ink), 'red'/'green'/'pencil'/'hi'/'paper' -> pens, anything else as given. */
  function penColor(c) {
    if (!c) return 'currentColor';
    const k = String(c).toLowerCase();
    if (k === 'ink' || k === 'blue' || k === 'pen' || k === 'currentcolor') return 'currentColor';
    if (k === 'black') return PENS.black;
    if (colors[k]) return colors[k];
    if (KIND_PEN.indexOf(k) >= 0) return penFor(k);
    return c;
  }
  const KIND_PEN = ['blunder', 'mistake', 'miss', 'threat', 'danger', 'check', 'brilliant', 'great', 'best', 'excellent', 'good', 'hint', 'free', 'played', 'book', 'forced', 'inaccuracy'];
  /** Which pen a review classification / arrow kind should be written in. */
  function penFor(kind) {
    switch (kind) {
      case 'blunder': case 'mistake': case 'miss': case 'threat': case 'danger': case 'check': return colors.red;
      case 'brilliant': case 'great': case 'best': case 'excellent': case 'good': case 'hint': case 'free': return colors.green;
      case 'played': case 'book': case 'forced': case 'inaccuracy': return 'currentColor';
      default: return 'currentColor';
    }
  }

  /* ---------------------------------------------------------------- path flattening */
  const CMD = /^[MmLlHhVvCcSsQqTtAaZz]$/;
  function tokenize(d) { return String(d).match(/[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?/g) || []; }

  function cubicTo(out, x0, y0, x1, y1, x2, y2, x3, y3, tol) {
    const L = Math.hypot(x1 - x0, y1 - y0) + Math.hypot(x2 - x1, y2 - y1) + Math.hypot(x3 - x2, y3 - y2);
    const n = clamp(Math.ceil(L / tol), 2, 64);
    for (let i = 1; i <= n; i++) {
      const t = i / n, u = 1 - t;
      const a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
      out.push([a * x0 + b * x1 + c * x2 + d * x3, a * y0 + b * y1 + c * y2 + d * y3]);
    }
  }
  function quadTo(out, x0, y0, x1, y1, x2, y2, tol) {
    const L = Math.hypot(x1 - x0, y1 - y0) + Math.hypot(x2 - x1, y2 - y1);
    const n = clamp(Math.ceil(L / tol), 2, 64);
    for (let i = 1; i <= n; i++) {
      const t = i / n, u = 1 - t;
      out.push([u * u * x0 + 2 * u * t * x1 + t * t * x2, u * u * y0 + 2 * u * t * y1 + t * t * y2]);
    }
  }
  function arcTo(out, x1, y1, rx, ry, phi, fa, fs, x2, y2, tol) {
    if (!rx || !ry || (x1 === x2 && y1 === y2)) { out.push([x2, y2]); return; }
    rx = Math.abs(rx); ry = Math.abs(ry);
    const cp = Math.cos(phi * Math.PI / 180), sp = Math.sin(phi * Math.PI / 180);
    const dx = (x1 - x2) / 2, dy = (y1 - y2) / 2;
    const xp = cp * dx + sp * dy, yp = -sp * dx + cp * dy;
    const lam = (xp * xp) / (rx * rx) + (yp * yp) / (ry * ry);
    if (lam > 1) { const s = Math.sqrt(lam); rx *= s; ry *= s; }
    const nu = rx * rx * ry * ry - rx * rx * yp * yp - ry * ry * xp * xp, de = rx * rx * yp * yp + ry * ry * xp * xp;
    let co = Math.sqrt(Math.max(0, nu / (de || 1)));
    if (!!fa === !!fs) co = -co;
    const cxp = (co * rx * yp) / ry, cyp = (-co * ry * xp) / rx;
    const cx = cp * cxp - sp * cyp + (x1 + x2) / 2, cy = sp * cxp + cp * cyp + (y1 + y2) / 2;
    const ang = (ux, uy, vx, vy) => Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
    const t1 = ang(1, 0, (xp - cxp) / rx, (yp - cyp) / ry);
    let dt = ang((xp - cxp) / rx, (yp - cyp) / ry, (-xp - cxp) / rx, (-yp - cyp) / ry);
    if (!fs && dt > 0) dt -= TAU; else if (fs && dt < 0) dt += TAU;
    const n = clamp(Math.ceil((Math.abs(dt) * Math.max(rx, ry)) / tol), 3, 90);
    for (let i = 1; i <= n; i++) {
      const a = t1 + (dt * i) / n, ex = rx * Math.cos(a), ey = ry * Math.sin(a);
      out.push([cp * ex - sp * ey + cx, sp * ex + cp * ey + cy]);
    }
  }

  /** Flatten an SVG path "d" into subpaths [{pts:[[x,y]...], closed}] (absolute coordinates). */
  function flattenPath(d, tol) {
    const t = tokenize(d), subs = [];
    let i = 0, cmd = '', last = '', x = 0, y = 0, sx = 0, sy = 0, cx = 0, cy = 0, qx = 0, qy = 0, cur = null;
    const N = () => parseFloat(t[i++]);
    const ensure = () => { if (!cur) { cur = { pts: [[x, y]], closed: false }; subs.push(cur); } return cur.pts; };
    while (i < t.length) {
      let c = t[i];
      if (CMD.test(c)) i++;
      else {
        if (!cmd || cmd === 'z' || cmd === 'Z') { i++; continue; }
        c = cmd === 'M' ? 'L' : cmd === 'm' ? 'l' : cmd;
      }
      cmd = c;
      const U = c.toUpperCase(), rel = c !== U, ox = rel ? x : 0, oy = rel ? y : 0;
      switch (U) {
        case 'M': x = ox + N(); y = oy + N(); sx = x; sy = y; cur = { pts: [[x, y]], closed: false }; subs.push(cur); break;
        case 'L': x = ox + N(); y = oy + N(); ensure().push([x, y]); break;
        case 'H': x = ox + N(); ensure().push([x, y]); break;
        case 'V': y = oy + N(); ensure().push([x, y]); break;
        case 'C': {
          const x1 = ox + N(), y1 = oy + N(), x2 = ox + N(), y2 = oy + N(), x3 = ox + N(), y3 = oy + N();
          cubicTo(ensure(), x, y, x1, y1, x2, y2, x3, y3, tol); cx = x2; cy = y2; x = x3; y = y3; break;
        }
        case 'S': {
          const x1 = last === 'C' || last === 'S' ? 2 * x - cx : x, y1 = last === 'C' || last === 'S' ? 2 * y - cy : y;
          const x2 = ox + N(), y2 = oy + N(), x3 = ox + N(), y3 = oy + N();
          cubicTo(ensure(), x, y, x1, y1, x2, y2, x3, y3, tol); cx = x2; cy = y2; x = x3; y = y3; break;
        }
        case 'Q': {
          const x1 = ox + N(), y1 = oy + N(), x2 = ox + N(), y2 = oy + N();
          quadTo(ensure(), x, y, x1, y1, x2, y2, tol); qx = x1; qy = y1; x = x2; y = y2; break;
        }
        case 'T': {
          const x1 = last === 'Q' || last === 'T' ? 2 * x - qx : x, y1 = last === 'Q' || last === 'T' ? 2 * y - qy : y;
          const x2 = ox + N(), y2 = oy + N();
          quadTo(ensure(), x, y, x1, y1, x2, y2, tol); qx = x1; qy = y1; x = x2; y = y2; break;
        }
        case 'A': {
          const rx = N(), ry = N(), rot = N(), fa = N(), fs = N(), x2 = ox + N(), y2 = oy + N();
          arcTo(ensure(), x, y, rx, ry, rot, fa, fs, x2, y2, tol); x = x2; y = y2; break;
        }
        case 'Z': if (cur) cur.closed = true; x = sx; y = sy; cur = null; break;
        default: break;
      }
      last = U;
      if (!isFinite(x) || !isFinite(y)) break;
    }
    return subs.filter(s => s.pts.length > 1 && s.pts.every(p => isFinite(p[0]) && isFinite(p[1])));
  }

  function ellipsePts(cx, cy, rx, ry, tol) {
    const per = Math.PI * (3 * (rx + ry) - Math.sqrt((3 * rx + ry) * (rx + 3 * ry)));
    const n = clamp(Math.ceil(per / (tol * 0.8)), 12, 160), pts = [];
    for (let i = 0; i < n; i++) { const a = (i / n) * TAU; pts.push([cx + rx * Math.cos(a), cy + ry * Math.sin(a)]); }
    return pts;
  }

  /** Geometry of one SVG shape element as flattened subpaths. */
  function shapeSubs(el, tol) {
    const A = k => num(el.getAttribute(k), 0);
    switch (el.localName) {
      case 'path': return flattenPath(el.getAttribute('d') || '', tol);
      case 'circle': return A('r') > 0 ? [{ pts: ellipsePts(A('cx'), A('cy'), A('r'), A('r'), tol), closed: true }] : [];
      case 'ellipse': return A('rx') > 0 && A('ry') > 0 ? [{ pts: ellipsePts(A('cx'), A('cy'), A('rx'), A('ry'), tol), closed: true }] : [];
      case 'line': return [{ pts: [[A('x1'), A('y1')], [A('x2'), A('y2')]], closed: false }];
      case 'rect': {
        const x = A('x'), y = A('y'), w = A('width'), h = A('height');
        if (w <= 0 || h <= 0) return [];
        let rx = el.hasAttribute('rx') ? A('rx') : A('ry'), ry = el.hasAttribute('ry') ? A('ry') : rx;
        rx = Math.min(rx, w / 2); ry = Math.min(ry, h / 2);
        if (!rx || !ry) return [{ pts: [[x, y], [x + w, y], [x + w, y + h], [x, y + h]], closed: true }];
        return flattenPath(`M${x + rx} ${y}H${x + w - rx}A${rx} ${ry} 0 0 1 ${x + w} ${y + ry}V${y + h - ry}A${rx} ${ry} 0 0 1 ${x + w - rx} ${y + h}H${x + rx}A${rx} ${ry} 0 0 1 ${x} ${y + h - ry}V${y + ry}A${rx} ${ry} 0 0 1 ${x + rx} ${y}Z`, tol);
      }
      case 'polygon': case 'polyline': {
        const v = (el.getAttribute('points') || '').match(/[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?/g) || [];
        const pts = [];
        for (let i = 0; i + 1 < v.length; i += 2) pts.push([+v[i], +v[i + 1]]);
        return pts.length > 1 ? [{ pts, closed: el.localName === 'polygon' }] : [];
      }
      default: return [];
    }
  }

  /* ---------------------------------------------------------------- resampling + the wobbling pen */
  /** Resample a polyline evenly by arc length, keeping sharp corners exactly. Returns arrays + normals. */
  function prep(pts, closed, spacing, minPts) {
    const P = [];
    for (const p of pts) { const q = P[P.length - 1]; if (!q || Math.abs(p[0] - q[0]) + Math.abs(p[1] - q[1]) > 1e-6) P.push(p); }
    if (closed && P.length > 2 && dist(P[0], P[P.length - 1]) < 1e-3) P.pop();
    if (closed && P.length < 3) closed = false;
    const n = P.length;
    if (n < 2) return null;
    let total = 0;
    for (let i = 1; i < n; i++) total += dist(P[i - 1], P[i]);
    if (closed) total += dist(P[n - 1], P[0]);
    if (total < 1e-4) return null;
    const sp = Math.min(spacing, total / (minPts || (closed ? 10 : 2)));
    const corner = new Uint8Array(n);
    for (let i = 0; i < n; i++) {
      if (!closed && (i === 0 || i === n - 1)) { corner[i] = 1; continue; }
      const a = P[(i - 1 + n) % n], b = P[i], c = P[(i + 1) % n];
      let d = Math.abs(Math.atan2(c[1] - b[1], c[0] - b[0]) - Math.atan2(b[1] - a[1], b[0] - a[0]));
      if (d > Math.PI) d = TAU - d;
      if (d > 0.6) corner[i] = 1;
    }
    let st = 0;
    if (closed) for (let i = 0; i < n; i++) if (corner[i]) { st = i; break; }
    const order = [];
    for (let j = 0; j < (closed ? n + 1 : n); j++) order.push((st + j) % n);
    const X = [P[order[0]][0]], Y = [P[order[0]][1]], C = [closed ? corner[order[0]] : 1];
    let a = 0;
    for (let j = 1; j < order.length; j++) {
      const end = j === order.length - 1;
      if (!corner[order[j]] && !end) continue;
      const seg = [];
      let L = 0;
      for (let q = a + 1; q <= j; q++) { const d = dist(P[order[q - 1]], P[order[q]]); seg.push(d); L += d; }
      const m = Math.max(1, Math.round(L / sp));
      let q = 0, acc = 0;
      for (let s = 1; s <= m; s++) {
        const target = (L * s) / m;
        while (q < seg.length - 1 && acc + seg[q] < target) { acc += seg[q]; q++; }
        const p0 = P[order[a + q]], p1 = P[order[a + q + 1]], tt = seg[q] ? clamp((target - acc) / seg[q], 0, 1) : 1;
        X.push(p0[0] + (p1[0] - p0[0]) * tt); Y.push(p0[1] + (p1[1] - p0[1]) * tt);
        C.push(s === m ? (corner[order[j]] || (!closed && end) ? 1 : 0) : 0);
      }
      a = j;
    }
    if (closed) { X.pop(); Y.pop(); C.pop(); }
    const N = X.length, S = new Float64Array(N), NX = new Float64Array(N), NY = new Float64Array(N);
    for (let i = 1; i < N; i++) S[i] = S[i - 1] + Math.hypot(X[i] - X[i - 1], Y[i] - Y[i - 1]);
    for (let i = 0; i < N; i++) {
      const pi = closed ? (i - 1 + N) % N : Math.max(0, i - 1), ni = closed ? (i + 1) % N : Math.min(N - 1, i + 1);
      const tx = X[ni] - X[pi], ty = Y[ni] - Y[pi], l = Math.hypot(tx, ty) || 1;
      NX[i] = -ty / l; NY[i] = tx / l;
    }
    return { X, Y, C, S, NX, NY, n: N, closed, len: total };
  }

  /** Walk a prepped line with a pen: smooth noise along the normal (sum of two sines over arc length + tiny jitter).
      o: {st, count, amp, k, bias, splayFrom, splay, jit} */
  function walk(R, o, rand) {
    const n = R.n, k = o.k || 1;
    const ph1 = rand() * TAU, ph2 = rand() * TAU, l1 = (26 + rand() * 20) * k, l2 = (7 + rand() * 5) * k;
    const jit = 0.14 * k * (o.jit == null ? 1 : o.jit), amp = o.amp || 0, bias = o.bias || 0;
    const X = [], Y = [], C = [], I = [];
    let s = 0, pi = -1;
    for (let j = 0; j < o.count; j++) {
      const i = R.closed ? (((o.st || 0) + j) % n + n) % n : Math.min(n - 1, (o.st || 0) + j);
      if (pi >= 0) s += Math.hypot(R.X[i] - R.X[pi], R.Y[i] - R.Y[pi]);
      pi = i;
      let off = amp * (0.62 * Math.sin((TAU * s) / l1 + ph1) + 0.38 * Math.sin((TAU * s) / l2 + ph2)) + (rand() - 0.5) * jit + bias;
      if (o.splayFrom != null && j > o.splayFrom) off += (j - o.splayFrom) * o.splay;
      X.push(R.X[i] + R.NX[i] * off); Y.push(R.Y[i] + R.NY[i] * off); C.push(R.C[i]); I.push(i);
    }
    if (C.length) { C[0] = 1; C[C.length - 1] = 1; }
    return { X, Y, C, I };
  }

  /* Compact path data: coordinates kept as integer tenths, written relative ("l", "q", "m") with minimal separators,
     so a drawing stays exact (no rounding drift) but is ~40% smaller than absolute "Q12.3 45.6 ..." text. */
  function f10(n) {
    if (n === 0) return '0';
    const neg = n < 0;
    if (neg) n = -n;
    const w = Math.floor(n / 10), f = n % 10;
    return (neg ? '-' : '') + (f ? (w ? w : '') + '.' + f : String(w));
  }
  class PathBuf {
    constructor() { this.s = ''; this.cmdc = ''; this.dot = false; this.sep = false; this.x = 0; this.y = 0; }
    c(ch) { if (ch !== this.cmdc || ch === 'M' || ch === 'm') { this.s += ch; this.cmdc = ch; this.sep = false; } }
    n(v) {
      const t = f10(v);
      if (this.sep && !(t[0] === '-' || (t[0] === '.' && this.dot))) this.s += ' ';
      this.s += t; this.dot = t.indexOf('.') >= 0; this.sep = true;
    }
    M(x, y) { x = Math.round(x * 10); y = Math.round(y * 10); if (this.s) { this.c('m'); this.n(x - this.x); this.n(y - this.y); } else { this.c('M'); this.n(x); this.n(y); } this.x = x; this.y = y; this.m0x = x; this.m0y = y; }
    L(x, y) { x = Math.round(x * 10); y = Math.round(y * 10); this.c('l'); this.n(x - this.x); this.n(y - this.y); this.x = x; this.y = y; }
    Q(x1, y1, x, y) {
      x1 = Math.round(x1 * 10); y1 = Math.round(y1 * 10); x = Math.round(x * 10); y = Math.round(y * 10);
      this.c('q'); this.n(x1 - this.x); this.n(y1 - this.y); this.n(x - this.x); this.n(y - this.y); this.x = x; this.y = y;
    }
    Z() { this.s += 'z'; this.cmdc = 'z'; this.sep = false; this.x = this.m0x; this.y = this.m0y; }
  }

  /** Points -> smooth path data (quadratic midpoint smoothing inside runs, exact sharp corners). */
  function dSmooth(X, Y, C, z, n, b) {
    n = n == null ? X.length : n;
    const own = !b;
    b = b || new PathBuf();
    if (!n) return own ? '' : b;
    b.M(X[0], Y[0]);
    if (n === 1) { b.L(X[0] + 0.05, Y[0]); return own ? b.s : b; }
    let a = 0;
    for (let j = 1; j < n; j++) {
      if (!C[j] && j < n - 1) continue;
      if (j - a === 1) b.L(X[j], Y[j]);
      else {
        for (let q = a + 1; q < j; q++) {
          const ex = q < j - 1 ? (X[q] + X[q + 1]) / 2 : X[j], ey = q < j - 1 ? (Y[q] + Y[q + 1]) / 2 : Y[j];
          b.Q(X[q], Y[q], ex, ey);
        }
      }
      a = j;
    }
    if (z) b.Z();
    return own ? b.s : b;
  }

  /** Like dSmooth, but lifts the pen at chosen corners (by prepped index): overshoot past the corner, restart just before it. */
  function dBroken(W, brk, b) {
    const n = W.X.length;
    let a = 0;
    const emit = (from, to, pre, post) => {
      const X = W.X.slice(from, to + 1), Y = W.Y.slice(from, to + 1), C = W.C.slice(from, to + 1);
      C[0] = 1; C[C.length - 1] = 1;
      if (pre) { X.unshift(X[0] - pre[0]); Y.unshift(Y[0] - pre[1]); C.unshift(1); }
      if (post) { X.push(X[X.length - 1] + post[0]); Y.push(Y[Y.length - 1] + post[1]); C.push(1); }
      dSmooth(X, Y, C, false, null, b);
    };
    let pre = null;
    for (let j = 1; j < n - 1; j++) {
      const e = W.C[j] && brk[W.I[j]];
      if (!e) continue;
      const ix = W.X[j] - W.X[j - 1], iy = W.Y[j] - W.Y[j - 1], il = Math.hypot(ix, iy) || 1;
      const ox = W.X[j + 1] - W.X[j], oy = W.Y[j + 1] - W.Y[j], ol = Math.hypot(ox, oy) || 1;
      emit(a, j, pre, [(ix / il) * e, (iy / il) * e]);
      pre = [(ox / ol) * e * 0.45, (oy / ol) * e * 0.45];
      a = j;
    }
    emit(a, n - 1, pre, null);
    return b;
  }

  /** Points -> coarse closed polygon (every `step`-th point plus corners): used for paper fills hidden under the line. */
  function dPoly(X, Y, C, n, step, b) {
    if (!n) return b;
    b.M(X[0], Y[0]);
    for (let i = 1; i < n; i++) if (C[i] || i % step === 0 || i === n - 1) b.L(X[i], Y[i]);
    b.Z();
    return b;
  }

  /** One-shot pen stroke through raw points (used by board, marks, arrows, fx). */
  function penStroke(pts, rand, o) {
    o = o || {};
    const k = o.k || 1;
    const R = prep(pts, !!o.closed, o.sp || 2.4 * k, o.minPts);
    if (!R) return '';
    const count = R.closed ? R.n + 1 + (o.ov || 0) : R.n;
    const W = walk(R, { st: o.st || 0, count, amp: o.amp == null ? 0.6 * k : o.amp, k, bias: o.bias, jit: o.jit, splayFrom: R.closed ? R.n : null, splay: o.splay || 0 }, rand);
    return dSmooth(W.X, W.Y, W.C, false);
  }

  /** Points of a hand-drawn loop around (cx,cy): `turns` turns, slight spiral, random radius drift. */
  function loopPts(cx, cy, rx, ry, turns, a0, spiral, rand, rot) {
    const n = Math.ceil(turns * 72), pts = [];
    const cr = Math.cos(rot || 0), sr = Math.sin(rot || 0);
    const w1 = rand() * TAU, w2 = rand() * TAU, wa = 0.035 + rand() * 0.03;
    for (let i = 0; i <= n; i++) {
      const t = i / n, a = a0 + TAU * turns * t;
      const f = 1 + (spiral || 0) * (t - 0.5) + wa * Math.sin(a * 2 + w1) + wa * 0.6 * Math.sin(a * 3 + w2);
      const x = rx * f * Math.cos(a), y = ry * f * Math.sin(a);
      pts.push([cx + x * cr - y * sr, cy + x * sr + y * cr]);
    }
    return pts;
  }

  /* ---------------------------------------------------------------- hatching */
  /** Hand hatching inside closed polygons (even-odd): parallel strokes at `angle`, irregular spacing and ends.
      clip (optional) {ax, ay, c, soft}: only keep the part of each stroke where ax*x + ay*y > c (the shadow side).
      Appends to PathBuf b and returns it. */
  function hatchLines(polys, angle, sp, rand, k, T, b, clip) {
    b = b || new PathBuf();
    const ux = Math.cos(angle), uy = Math.sin(angle), nx = -uy, ny = ux;
    let tmin = Infinity, tmax = -Infinity;
    for (const P of polys) for (const p of P) { const t = nx * p[0] + ny * p[1]; if (t < tmin) tmin = t; if (t > tmax) tmax = t; }
    if (!isFinite(tmin)) return b;
    let t = tmin + sp * (0.25 + rand() * 0.6);
    const vs = [];
    while (t < tmax - sp * 0.15) {
      const tt = t + (rand() - 0.5) * sp * 0.3;
      vs.length = 0;
      for (const P of polys) {
        const m = P.length;
        for (let j = 0; j < m; j++) {
          const a = P[j], c = P[(j + 1) % m];
          const ta = nx * a[0] + ny * a[1], tb = nx * c[0] + ny * c[1];
          if ((ta <= tt) !== (tb <= tt)) {
            const q = (tt - ta) / (tb - ta);
            vs.push(ux * (a[0] + (c[0] - a[0]) * q) + uy * (a[1] + (c[1] - a[1]) * q));
          }
        }
      }
      vs.sort((p, q) => p - q);
      for (let j = 0; j + 1 < vs.length; j += 2) {
        let v0 = vs[j], v1 = vs[j + 1];
        if (v1 - v0 < 0.9 * k) continue;
        v0 += (rand() < 0.08 ? -0.35 : 0.3 + rand() * 0.75) * k;
        v1 -= (rand() < 0.08 ? -0.35 : 0.3 + rand() * 0.75) * k;
        if (clip) {
          const g = clip.ax * ux + clip.ay * uy, h = (clip.ax * nx + clip.ay * ny) * tt, c = clip.c + (rand() - 0.5) * (clip.soft || 0);
          if (Math.abs(g) < 1e-6) { if (h <= c) continue; }
          else if (g > 0) v0 = Math.max(v0, (c - h) / g);
          else v1 = Math.min(v1, (c - h) / g);
        }
        if (v1 - v0 < 0.45 * k) continue;
        const tilt = (rand() - 0.5) * 0.4 * k;
        let x0 = ux * v0 + nx * tt, y0 = uy * v0 + ny * tt, x1 = ux * v1 + nx * (tt + tilt), y1 = uy * v1 + ny * (tt + tilt);
        if (T) { const p = T(x0, y0), q = T(x1, y1); x0 = p[0]; y0 = p[1]; x1 = q[0]; y1 = q[1]; }
        b.M(x0, y0); b.L(x1, y1);
      }
      t += sp * (0.8 + rand() * 0.4);
    }
    return b;
  }

  // Hatch density levels (units are a 100-unit viewBox): spacing, directions, line width, opacity
  const HATCH = {
    1: { sp: 4.3, dirs: 1, w: 0.5, op: 0.8 },
    2: { sp: 3.0, dirs: 1, w: 0.55, op: 0.85 },
    3: { sp: 3.1, dirs: 2, w: 0.55, op: 0.85 },
    4: { sp: 2.35, dirs: 2, w: 0.6, op: 0.9 },
    5: { sp: 1.85, dirs: 3, w: 0.66, op: 0.95 }
  };
  function toneLevel(L, side) {
    if (side === 'b') return L >= 0.74 ? 0 : L >= 0.55 ? 2 : L >= 0.4 ? 3 : L >= 0.17 ? 4 : 5;
    return L >= 0.86 ? 0 : L >= 0.62 ? 1 : L >= 0.38 ? 2 : L >= 0.2 ? 3 : 4;
  }

  /* ---------------------------------------------------------------- SVG -> ink conversion */
  const SHAPES = { path: 1, circle: 1, ellipse: 1, rect: 1, line: 1, polygon: 1, polyline: 1 };
  const PROPS = ['fill', 'stroke', 'stroke-width', 'fill-opacity', 'stroke-opacity', 'opacity'];

  function readStyle(el, inh) {
    const st = Object.assign({}, inh);
    delete st.opacity;
    for (const p of PROPS) { const v = el.getAttribute(p); if (v != null) st[p] = v.trim(); }
    const css = el.getAttribute('style');
    if (css) {
      for (const decl of css.split(';')) {
        const i = decl.indexOf(':');
        if (i > 0) { const key = decl.slice(0, i).trim().toLowerCase(); if (PROPS.indexOf(key) >= 0) st[key] = decl.slice(i + 1).trim(); }
      }
    }
    return st;
  }

  function geomKey(el) {
    let s = el.localName;
    for (const a of ['d', 'cx', 'cy', 'r', 'rx', 'ry', 'x', 'y', 'width', 'height', 'points', 'x1', 'y1', 'x2', 'y2']) {
      const v = el.getAttribute(a);
      if (v != null) s += a + v;
    }
    return s;
  }

  function classifyFill(st, el, ctx, S) {
    const cls = el.getAttribute('class') || '';
    let f = st.fill;
    if (f == null) f = S.closedish ? '#000' : 'none';
    const c = parseColor(f);
    const k2 = ctx.k * ctx.k;
    if (/\bpc-shadow\b/.test(cls)) return { kind: 'shadow' };
    if (!c || c.none) return null;
    const op = c.a * num(st['fill-opacity'], 1) * num(st.opacity, 1) * (st.gop == null ? 1 : st.gop);
    const L = lum(c);
    if (L < 0.2 && op < 0.45 && S.area > 40 * k2) return { kind: 'shadow' };
    if (op < 0.3) return null;
    if (L > 0.8 && op < 0.65) return null; // soft white highlights
    if (op < 0.78 && c.r > c.g + 40 && c.r > c.b + 15) return { kind: 'blush' };
    if (op < 0.5) return null;
    let lvl = toneLevel(L, ctx.side);
    if ((lvl >= 5 || L < 0.22) && S.area < 55 * k2) return { kind: 'solid' };
    if (lvl > 0 && S.area < 9 * k2) return lvl >= 3 ? { kind: 'solid' } : { kind: 'paper' };
    return lvl === 0 ? { kind: 'paper' } : { kind: 'hatch', lvl };
  }

  function classifyStroke(st, el, prev, ctx, hasFill) {
    const c = parseColor(st.stroke);
    if (!c || c.none) return null;
    const k = ctx.k, w = num(st['stroke-width'], 1);
    const op = c.a * num(st['stroke-opacity'], 1) * num(st.opacity, 1) * (st.gop == null ? 1 : st.gop);
    const L = lum(c);
    if (op < 0.2 || w <= 0) return null;
    if (L > 0.85 && op < 0.6) return null; // white sheen strokes
    const d = el.getAttribute('d');
    if (L > 0.6 && prev && d && prev.getAttribute('d') === d && num(prev.getAttribute('stroke-width'), 1) > w) return { kind: 'paper', w: w * 0.62 };
    if (!hasFill && w >= 2.8 * k && (op < 0.95 || L > 0.25)) return { kind: 'shade', w };
    if (L < 0.2 && op > 0.8) return { kind: 'line', w: w * 0.6, double: w >= 1.75 * k };
    return { kind: 'detail', w: clamp(w * 0.48, 0.5 * k, 1.15 * k), op: clamp(0.45 + op * 0.45, 0.5, 0.92) };
  }

  function analyzeShape(el, st, prev, ctx) {
    const raw = shapeSubs(el, ctx.tol);
    if (!raw.length) return null;
    const k = ctx.k, tag = el.localName;
    const closedish = tag === 'path' || tag === 'line' || tag === 'polyline' ? raw.some(s => s.closed) : true;
    const subs = [], polys = [];
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity, area = 0, per = 0;
    for (const s of raw) {
      const R = prep(s.pts, s.closed, ctx.sp, s.closed ? 10 : 3);
      if (!R) continue;
      subs.push(R);
      per += R.len;
      const poly = [];
      let a = 0;
      for (let i = 0; i < R.n; i++) {
        const x = R.X[i], y = R.Y[i];
        poly.push([x, y]);
        if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
        const j = (i + 1) % R.n;
        a += x * R.Y[j] - R.X[j] * y;
      }
      polys.push(poly);
      area += Math.abs(a) / 2;
    }
    if (!subs.length) return null;
    const key = geomKey(el);
    ctx.dup[key] = (ctx.dup[key] || 0) + 1;
    const S = {
      t: 's', subs, polys, closedish, area, per, cls: el.getAttribute('class'), tr: el.getAttribute('transform'),
      bbox: [x0, y0, x1, y1], diag: Math.hypot(x1 - x0, y1 - y0), seed: hash(ctx.seed, key, ctx.dup[key]), inEyes: ctx.eyes > 0
    };
    S.fill = classifyFill(st, el, ctx, S);
    S.stroke = classifyStroke(st, el, prev, ctx, !!(S.fill && S.fill.kind !== 'shadow'));
    if (S.fill && S.fill.kind === 'shadow') S.stroke = null;
    if (!S.fill && !S.stroke) return null;
    // fill-only paper shapes (spots, bellies, glowing eyes) get a fine contour so they read on paper
    if (!S.stroke && S.fill && S.fill.kind === 'paper' && per > 7 * k) S.stroke = { kind: 'detail', w: 0.72 * k, op: 0.85 };
    if (!S.stroke && S.fill && S.fill.kind === 'hatch' && area > 60 * k * k && ctx.side === 'b') S.stroke = { kind: 'detail', w: 0.55 * k, op: 0.7 };
    // shape-level hand decisions (stable across frames)
    const Rs = rng(S.seed);
    S.amp = 0.7 * k * clamp(S.diag / (16 * k), 0.22, 1);
    S.angle = -Math.PI / 4 + (Rs() - 0.5) * 0.4;
    S.angle2 = S.angle + 1.42 + (Rs() - 0.5) * 0.2;
    S.params = subs.map(R => {
      const p = { st: 0, ov: 0, splay: 0, st2: 0, n2: 0, bias2: 0 };
      if (R.closed) {
        const corners = [];
        for (let i = 0; i < R.n; i++) if (R.C[i]) corners.push(i);
        p.st = corners.length ? corners[Math.floor(Rs() * corners.length)] : Math.floor(Rs() * R.n);
        if (R.len > 14 * k && Rs() < 0.8) {
          const ovLen = clamp(R.len * (0.04 + Rs() * 0.05), 1.4 * k, 5 * k);
          p.ov = Math.max(1, Math.round(ovLen / (R.len / R.n)));
          p.splay = (Rs() < 0.65 ? 1 : -1) * (0.08 + Rs() * 0.14) * k;
        }
        p.st2 = Math.floor(Rs() * R.n);
        p.n2 = Math.max(3, Math.round(R.n * (0.4 + Rs() * 0.45)));
      } else {
        p.n2 = R.n;
      }
      p.bias2 = (Rs() < 0.5 ? -1 : 1) * (0.3 + Rs() * 0.3) * k;
      // sharp corners where the pen lifts: the line runs on a little past the corner and the next one starts fresh
      p.brk = null;
      if (S.diag > 10 * k) {
        for (let i = 0; i < R.n; i++) {
          if (!R.C[i] || (!R.closed && (i === 0 || i === R.n - 1))) continue;
          const a = (i - 1 + R.n) % R.n, c = (i + 1) % R.n;
          let t = Math.abs(Math.atan2(R.Y[c] - R.Y[i], R.X[c] - R.X[i]) - Math.atan2(R.Y[i] - R.Y[a], R.X[i] - R.X[a]));
          if (t > Math.PI) t = TAU - t;
          if (t > 1.05 && Rs() < 0.45) { p.brk = p.brk || {}; p.brk[i] = (0.7 + Rs() * 1.2) * k; }
        }
      }
      return p;
    });
    return S;
  }

  function textHtml(el) {
    const c = el.cloneNode(true);
    if (c.getAttribute('fill') !== 'none') c.setAttribute('fill', 'currentColor');
    c.setAttribute('stroke', c.getAttribute('stroke') && c.getAttribute('stroke') !== 'none' ? 'currentColor' : 'none');
    if (!c.getAttribute('font-family')) c.setAttribute('font-family', FONT);
    return c.outerHTML;
  }

  function analyzeChildren(parent, inh, ctx) {
    const out = [];
    let prev = null;
    for (const el of Array.from(parent.children)) {
      const tag = el.localName;
      if (tag === 'g' || tag === 'a' || tag === 'svg') {
        const st = readStyle(el, inh);
        const gop = (inh.gop == null ? 1 : inh.gop) * num(st.opacity, 1);
        const inh2 = Object.assign({}, st, { gop });
        delete inh2.opacity;
        const eyes = /eyes/.test(el.getAttribute('class') || '');
        if (eyes) ctx.eyes++;
        const kids = analyzeChildren(el, inh2, ctx);
        if (eyes) ctx.eyes--;
        out.push({ t: 'g', cls: el.getAttribute('class'), tr: el.getAttribute('transform'), kids });
        prev = null;
      } else if (SHAPES[tag]) {
        const S = analyzeShape(el, readStyle(el, inh), prev, ctx);
        if (S) out.push(S);
        prev = el;
      } else if (tag === 'text') {
        out.push({ t: 'raw', html: textHtml(el) });
        prev = null;
      }
    }
    return out;
  }

  /* per-frame emission */
  function rigid(S, R, k) {
    const b = S.bbox, cx = (b[0] + b[2]) / 2, cy = (b[1] + b[3]) / 2;
    const dx = (R() - 0.5) * 0.6 * k, dy = (R() - 0.5) * 0.6 * k, a = (R() - 0.5) * 0.014;
    const ca = Math.cos(a), sa = Math.sin(a);
    return (x, y) => { const u = x - cx, v = y - cy; return [cx + u * ca - v * sa + dx, cy + u * sa + v * ca + dy]; };
  }
  function applyT(W, T) { for (let i = 0; i < W.X.length; i++) { const p = T(W.X[i], W.Y[i]); W.X[i] = p[0]; W.Y[i] = p[1]; } }

  function shadowInk(S, R, ctx) {
    const b = S.bbox, cx = (b[0] + b[2]) / 2, cy = (b[1] + b[3]) / 2, rx = (b[2] - b[0]) / 2, ry = (b[3] - b[1]) / 2, k = ctx.k;
    const pts = [], rows = 4;
    for (let i = 0; i < rows; i++) {
      const v = -0.62 + (1.24 * i) / (rows - 1);
      const y = cy + v * ry + (R() - 0.5) * 0.5 * k;
      const half = rx * Math.sqrt(Math.max(0.05, 1 - v * v)) * (0.86 + R() * 0.16);
      const xa = cx - half + (R() - 0.5) * k, xb = cx + half + (R() - 0.5) * k;
      if (i % 2 === 0) pts.push([xa, y], [xb, y + (R() - 0.5) * 0.5 * k]);
      else pts.push([xb, y], [xa, y + (R() - 0.5) * 0.5 * k]);
    }
    const d = penStroke(pts, R, { k, amp: 0.25 * k, sp: 4 * k });
    return `<path class="ink-sd" d="${d}" stroke="${colors.pencil}" stroke-width="${r2(0.85 * k * ctx.weight)}" stroke-opacity=".75"/>`;
  }

  function blushInk(S, R, ctx, T) {
    const b = S.bbox, cx = (b[0] + b[2]) / 2, cy = (b[1] + b[3]) / 2, rx = (b[2] - b[0]) / 2, ry = (b[3] - b[1]) / 2, k = ctx.k;
    const pb = new PathBuf();
    for (let i = -1; i <= 1; i++) {
      const x = cx + i * rx * 0.55 + (R() - 0.5) * 0.3 * k, h = Math.max(ry * 0.85, 1.2 * k);
      const p = T(x - h * 0.45, cy + h * 0.8), q = T(x + h * 0.45 + (R() - 0.5) * 0.3 * k, cy - h * 0.8);
      pb.M(p[0], p[1]); pb.L(q[0], q[1]);
    }
    return `<path class="ink-bl" d="${pb.s}" stroke-width="${r2(0.55 * k * ctx.weight)}" stroke-opacity=".6"/>`;
  }

  function shadeInk(S, R, ctx, T) {
    const k = ctx.k, w = S.stroke.w, pb = new PathBuf();
    const L = clamp(w * 1.05, 2 * k, 3.8 * k), a = -Math.PI / 3 + (R() - 0.5) * 0.15;
    const ux = (Math.cos(a) * L) / 2, uy = (Math.sin(a) * L) / 2;
    for (const R0 of S.subs) {
      const step = Math.max(1, Math.round((1.45 * k) / (R0.len / Math.max(1, R0.n - 1))));
      for (let i = 0; i < R0.n; i += step) {
        const j = (R() - 0.5) * 0.4 * k;
        const p = T(R0.X[i] - ux + j, R0.Y[i] - uy), q = T(R0.X[i] + ux * (0.75 + R() * 0.4) + j, R0.Y[i] + uy * (0.75 + R() * 0.4));
        pb.M(p[0], p[1]); pb.L(q[0], q[1]);
      }
    }
    return `<path class="ink-sh" d="${pb.s}" stroke-width="${r2(0.5 * k * ctx.weight)}" stroke-opacity=".7"/>`;
  }

  /** Half-plane on the shadow side (light comes from the upper left), in bbox-normalised terms. */
  function shadowClip(S, thr) {
    const b = S.bbox, cx = (b[0] + b[2]) / 2, cy = (b[1] + b[3]) / 2, rx = Math.max(1e-3, (b[2] - b[0]) / 2), ry = Math.max(1e-3, (b[3] - b[1]) / 2);
    const ax = 0.45 / rx, ay = 0.89 / ry;
    return { ax, ay, c: thr + ax * cx + ay * cy, soft: 0.14 };
  }

  function emitShape(S, f, ctx) {
    const k = ctx.k, Rf = rng(hash(S.seed, 'frame', f)), T = rigid(S, Rf, k);
    const fill = S.fill, stroke = S.stroke;
    let out = '';
    if (fill && fill.kind === 'shadow') {
      out = shadowInk(S, Rf, ctx);
    } else {
      const outlined = stroke && (stroke.kind === 'line' || stroke.kind === 'detail' || stroke.kind === 'paper');
      const mains = S.subs.map((R, i) => {
        const p = S.params[i];
        const count = R.closed ? (outlined ? R.n + 1 + p.ov : R.n) : R.n;
        const amp = stroke && stroke.kind === 'detail' ? S.amp * 0.8 : S.amp;
        const W = walk(R, { st: R.closed ? p.st : 0, count, amp, k, splayFrom: R.closed ? R.n : null, splay: p.splay }, Rf);
        applyT(W, T);
        return W;
      });
      if (fill) {
        if (fill.kind === 'paper' || fill.kind === 'hatch' || fill.kind === 'solid') {
          const fb = new PathBuf();
          mains.forEach((W, i) => {
            const n = S.subs[i].closed ? S.subs[i].n : W.X.length;
            if (fill.kind === 'solid' || n < 24) dSmooth(W.X, W.Y, W.C, true, n, fb);
            else dPoly(W.X, W.Y, W.C, n, 2, fb);
          });
          if (fill.kind === 'solid') out += `<path d="${fb.s}" fill="currentColor" stroke="none"/>`;
          else out += `<path class="ink-pp" d="${fb.s}" fill="${PAPER}" stroke="none"/>`;
          const wh = Math.sqrt(ctx.weight);
          if (fill.kind === 'hatch') {
            const H = HATCH[fill.lvl], sp = H.sp * k, hb = new PathBuf();
            hatchLines(S.polys, S.angle, sp * (0.92 + Rf() * 0.16), Rf, k, T, hb);
            if (H.dirs > 1) hatchLines(S.polys, S.angle2, sp * (1.0 + Rf() * 0.16), Rf, k, T, hb);
            if (H.dirs > 2) hatchLines(S.polys, S.angle + 0.72, sp * 1.1, Rf, k, T, hb);
            // form shading: one more layer of strokes on the shadow side of bigger shapes
            if (S.area > 90 * k * k && fill.lvl < 5) hatchLines(S.polys, H.dirs > 1 ? S.angle + 0.72 : S.angle2, sp * 1.05, Rf, k, T, hb, shadowClip(S, 0.32));
            if (hb.s) out += `<path class="ink-hx" d="${hb.s}" stroke-width="${r2(H.w * k * wh)}" stroke-opacity="${H.op}"/>`;
          } else if (fill.kind === 'paper' && ctx.side !== 'b' && S.area > 230 * k * k && !S.inEyes) {
            const hb = hatchLines(S.polys, S.angle, 2.7 * k, Rf, k, T, null, shadowClip(S, 0.5));
            if (hb.s) out += `<path class="ink-hx ink-form" d="${hb.s}" stroke-width="${r2(0.45 * k * wh)}" stroke-opacity=".7"/>`;
          }
        } else if (fill.kind === 'blush') out += blushInk(S, Rf, ctx, T);
      }
      if (stroke) {
        if (stroke.kind === 'shade') out += shadeInk(S, Rf, ctx, T);
        else {
          const ob = new PathBuf();
          mains.forEach((W, i) => {
            const brk = stroke.kind === 'line' && stroke.w >= 0.9 * k ? S.params[i].brk : null;
            if (brk) dBroken(W, brk, ob); else dSmooth(W.X, W.Y, W.C, false, null, ob);
          });
          const w = stroke.w * (stroke.kind === 'paper' ? 1 : ctx.weight);
          if (stroke.kind === 'paper') out += `<path class="ink-pl" d="${ob.s}" stroke="${PAPER}" stroke-width="${r2(w)}"/>`;
          else out += `<path class="ink-o" d="${ob.s}" stroke-width="${r2(w)}"${stroke.op ? ` stroke-opacity="${stroke.op}"` : ''}/>`;
          if (stroke.kind === 'line' && stroke.double && S.per > 10 * k) {
            const b2 = new PathBuf();
            S.subs.forEach((R, i) => {
              if (R.len < 8 * k) return;
              const p = S.params[i];
              const W = walk(R, { st: R.closed ? p.st2 : 0, count: R.closed ? p.n2 : R.n, amp: S.amp * 1.25, k, bias: p.bias2 }, Rf);
              applyT(W, T);
              dSmooth(W.X, W.Y, W.C, false, null, b2);
            });
            if (b2.s) out += `<path class="ink-o2" d="${b2.s}" stroke-width="${r2(w * 0.5)}" stroke-opacity=".5"/>`;
          }
        }
      }
    }
    if (S.cls || S.tr) out = `<g${S.cls ? ` class="${esc(S.cls)}"` : ''}${S.tr ? ` transform="${esc(S.tr)}"` : ''}>${out}</g>`;
    return out;
  }

  function emitList(list, f, ctx) {
    let h = '';
    for (const node of list) {
      if (node.t === 's') h += emitShape(node, f, ctx);
      else if (node.t === 'g') h += `<g${node.cls ? ` class="${esc(node.cls)}"` : ''}${node.tr ? ` transform="${esc(node.tr)}"` : ''}>${emitList(node.kids, f, ctx)}</g>`;
      else if (node.t === 'raw') h += node.html;
    }
    return h;
  }

  const FRAME_ATTRS = 'fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"';
  const frameOpen = f => `<g class="ink-f ink-f${f}" ${FRAME_ATTRS}>`;

  /** Convert clean SVG markup into an ink drawing (see contract): same viewBox, root classes + "ink-svg", 1-3 boil frames. */
  function convert(markup, opts) {
    opts = opts || {};
    init();
    const ckey = ['cv', opts.frames, opts.seed, opts.side, opts.weight, opts.cls, colors.ink, markup].join('|');
    const hit = cache.get(ckey);
    if (hit !== undefined) return hit;
    const out = flat(convertNow(markup, opts));
    cache.set(ckey, out);
    return out;
  }
  function convertNow(markup, opts) {
    const frames = clamp(opts.frames == null ? (Ink.defaultFrames || 3) : opts.frames | 0, 1, 3);
    const tpl = doc.createElement('template');
    tpl.innerHTML = String(markup).trim();
    const root = tpl.content.querySelector('svg');
    if (!root) return String(markup);
    const vbAttr = root.getAttribute('viewBox') || '0 0 100 100';
    const vb = vbAttr.trim().split(/[\s,]+/).map(Number);
    const k = Math.max(vb[2] || 100, vb[3] || 100) / 100;
    const rootCls = root.getAttribute('class') || '';
    let side = opts.side || 'auto';
    if (side === 'auto') side = /\bpc-b\b/.test(rootCls) ? 'b' : 'w';
    const ctx = { k, side, seed: opts.seed == null ? 1 : opts.seed, weight: opts.weight || 1, tol: 0.9 * k, sp: 2.7 * k, dup: {}, eyes: 0 };
    const tree = analyzeChildren(root, readStyle(root, { gop: 1 }), ctx);
    let body = '';
    for (let f = 0; f < frames; f++) body += frameOpen(f) + emitList(tree, f, ctx) + '</g>';
    let attrs = '';
    for (const a of Array.from(root.attributes)) {
      const n = a.name;
      if (n === 'class' || n === 'viewbox' || n === 'viewBox' || n === 'color' || n === 'xmlns' || n === 'id' || PROPS.indexOf(n) >= 0 || n === 'style') continue;
      attrs += ` ${n}="${esc(a.value)}"`;
    }
    const cls = [rootCls, 'ink-svg', opts.cls].filter(Boolean).join(' ').trim();
    return `<svg class="${esc(cls)}" viewBox="${esc(vbAttr)}"${attrs} color="${colors.ink}">${body}</svg>`;
  }

  /* ---------------------------------------------------------------- caches + public piece / coach */
  const cache = new Map();
  // markup is built with +=, which leaves V8 rope (cons) strings that cost several times their length on the heap;
  // a multi-part join always yields one flat string, so copy before caching
  const flat = s => (typeof s === 'string' && s.length > 1) ? [s.charAt(0), s.slice(1)].join('') : s;
  function cached(key, make) {
    let v = cache.get(key);
    if (v === undefined) { v = flat(make()); cache.set(key, v); }
    return v;
  }
  function artSource() { return global.PieceArt ? (global.PieceArt.clean || global.PieceArt) : null; }

  function piece(code, o) {
    o = o || {};
    const frames = o.frames == null ? (Ink.defaultFrames || 3) : o.frames, weight = o.weight || 1;
    const src = artSource();
    if (!src) return '';
    // keyed by the source markup itself, so a different art set (classic pieces, PieceArt.clean) never hits a stale drawing
    const markup = src.svg(code, { palette: o.palette, set: o.set });
    return convert(markup, { frames, seed: hash('piece', code & 7, code >> 3, o.palette || ''), side: 'auto', weight, cls: o.cls });
  }

  function coach(expr, o) {
    o = o || {};
    expr = expr || 'happy';
    const frames = o.frames == null ? (Ink.defaultFrames || 3) : o.frames, weight = o.weight || 1;
    const src = artSource();
    if (!src || !src.coach) return '';
    // one seed for all expressions: the hedgehog's body is drawn identically, only the face changes
    return convert(src.coach(expr), { frames, seed: hash('sage-bramblewick'), side: 'w', weight, cls: o.cls });
  }

  /* ---------------------------------------------------------------- the board */
  function board(o) {
    o = o || {};
    init();
    const seed = o.seed == null ? 7 : o.seed, frames = clamp(o.frames == null ? 1 : o.frames | 0, 1, 3);
    const key = ['board', seed, frames, colors.ink].join('|');
    return cached(key, () => {
      const k = 8;
      let body = '';
      for (let f = 0; f < frames; f++) {
        const Rs = rng(hash(seed, 'board-hand')), R = rng(hash(seed, 'board', f));
        const hb = new PathBuf();
        let grid = '', grid2 = '', border = '', border2 = '';
        for (let row = 0; row < 8; row++) {
          for (let col = 0; col < 8; col++) {
            if ((row + col) % 2 !== 1) continue;
            const x = col * 100, y = row * 100, ang = -Math.PI / 4 + (Rs() - 0.5) * 0.22 + (R() - 0.5) * 0.04;
            const m = 3.5;
            hatchLines([[[x + m, y + m], [x + 100 - m, y + m], [x + 100 - m, y + 100 - m], [x + m, y + 100 - m]]], ang, 10.5 + Rs() * 2.2, R, k * 0.9, null, hb);
          }
        }
        // interior ruling: each line drawn in one or two strokes with slight bow, some ends overshooting
        for (let dir = 0; dir < 2; dir++) {
          for (let i = 1; i < 8; i++) {
            const p = i * 100, bow = (Rs() - 0.5) * 3.2, e0 = -4 + Rs() * 14, e1 = -4 + Rs() * 14;
            const split = Rs() < 0.45 ? 260 + Rs() * 280 : 0;
            const seg = (a, b) => {
              const pts = [];
              for (let s = 0; s <= 8; s++) {
                const t = a + ((b - a) * s) / 8, u = (t + e0) / (800 + e0 + e1);
                const off = bow * Math.sin(Math.PI * clamp(u, 0, 1)) + (R() - 0.5) * 0.8;
                pts.push(dir ? [t, p + off] : [p + off, t]);
              }
              return penStroke(pts, R, { k, amp: 1.1, sp: 24 });
            };
            if (split) { grid += seg(-e0, split + 5 + Rs() * 6); grid += seg(split - 3, 800 + e1); }
            else grid += seg(-e0, 800 + e1);
            if (Rs() < 0.35) { const a = Rs() * 500; grid2 += seg(a, a + 120 + Rs() * 200); }
          }
        }
        // border: four strokes overshooting the corners, then a quicker second pass
        const B = 3.5, corners = [[B, B], [800 - B, B], [800 - B, 800 - B], [B, 800 - B]];
        for (let pass = 0; pass < 2; pass++) {
          for (let s = 0; s < 4; s++) {
            const a = corners[s], b = corners[(s + 1) % 4], dx = b[0] - a[0], dy = b[1] - a[1], L = Math.hypot(dx, dy);
            const ux = dx / L, uy = dy / L, o0 = (pass ? 2 : 6) + Rs() * 12, o1 = (pass ? 2 : 6) + Rs() * 12;
            const off = pass ? (Rs() - 0.5) * 6 : 0;
            const pts = [];
            for (let q = 0; q <= 10; q++) {
              const t = -o0 + ((L + o0 + o1) * q) / 10;
              pts.push([a[0] + ux * t - uy * off, a[1] + uy * t + ux * off]);
            }
            const d = penStroke(pts, R, { k, amp: pass ? 2 : 1.3, sp: 22 });
            if (pass) border2 += d; else border += d;
          }
        }
        body += frameOpen(f) +
          `<path class="ink-bh" d="${hb.s}" stroke-width="1.55" stroke-opacity=".4"/>` +
          `<path class="ink-bg" d="${grid}" stroke-width="1.9" stroke-opacity=".78"/>` +
          (grid2 ? `<path class="ink-bg2" d="${grid2}" stroke-width="1.1" stroke-opacity=".4"/>` : '') +
          `<path class="ink-bb" d="${border}" stroke-width="3.6" stroke-opacity=".9"/>` +
          `<path class="ink-bb2" d="${border2}" stroke-width="1.6" stroke-opacity=".55"/>` +
          '</g>';
      }
      return `<svg class="ink-board${frames > 1 ? ' ink-svg' : ''}" viewBox="0 0 800 800" preserveAspectRatio="none" aria-hidden="true" color="${colors.ink}">${body}</svg>`;
    });
  }

  /* ---------------------------------------------------------------- square marks */
  function dashedLoop(Rs, R, color, r, w) {
    let d = '';
    const n = 11 + Math.floor(Rs() * 3), a0 = Rs() * TAU;
    for (let i = 0; i < n; i++) {
      const a = a0 + (i / n) * TAU + (R() - 0.5) * 0.06, len = (TAU / n) * (0.5 + R() * 0.16);
      const pts = [];
      for (let q = 0; q <= 5; q++) { const t = a + (len * q) / 5, rr = r + (R() - 0.5) * 1.2; pts.push([50 + Math.cos(t) * rr, 51 + Math.sin(t) * rr * 0.97]); }
      d += penStroke(pts, R, { amp: 0.4, sp: 2.5 });
    }
    return `<path d="${d}" stroke="${color}" stroke-width="${w}" pathLength="1"/>`;
  }
  /** Three little emphasis ticks radiating out of a loop's upper-right shoulder. */
  function sparkle(cx, cy, r, R, color, w) {
    let d = '';
    for (let i = 0; i < 3; i++) {
      const a = -1.32 + i * 0.42 + (R() - 0.5) * 0.12, l0 = r + 1 + R(), l1 = r + 6.5 + R() * 2.5;
      d += 'M' + r1(cx + Math.cos(a) * l0) + ' ' + r1(cy + Math.sin(a) * l0) + 'L' + r1(cx + Math.cos(a) * l1) + ' ' + r1(cy + Math.sin(a) * l1);
    }
    return `<path d="${d}" stroke="${color}" stroke-width="${w}" pathLength="1"/>`;
  }
  function asterisk(x, y, s, R, color, w, arms) {
    let d = '';
    arms = arms || 3;
    const a0 = R() * Math.PI;
    for (let i = 0; i < arms; i++) {
      const a = a0 + (i / arms) * Math.PI + (R() - 0.5) * 0.25, l = s * (0.8 + R() * 0.35);
      d += 'M' + r1(x - Math.cos(a) * l) + ' ' + r1(y - Math.sin(a) * l) + 'L' + r1(x + Math.cos(a) * l * (0.85 + R() * 0.3)) + ' ' + r1(y + Math.sin(a) * l * (0.85 + R() * 0.3));
    }
    return `<path d="${d}" stroke="${color}" stroke-width="${w}" pathLength="1"/>`;
  }
  function twinkle(x, y, s, R, color, w) {
    // a four-point star drawn as one continuous pen line with pinched waist
    const pts = [];
    for (let i = 0; i <= 8; i++) {
      const a = -Math.PI / 2 + (i / 8) * TAU, r = i % 2 ? s * 0.28 : s * (0.95 + R() * 0.15);
      pts.push([x + Math.cos(a) * r, y + Math.sin(a) * r]);
    }
    return `<path d="${penStroke(pts, R, { amp: 0.3, sp: 1.6, minPts: 16 })}" stroke="${color}" stroke-width="${w}" pathLength="1"/>`;
  }

  const MARKS = {
    last(Rs, R) {
      // two overlapping chisel-marker swipes: flat translucent yellow, slanted ends, darker overlap and edges, faint streaks
      let h = '';
      const tilt = (Rs() - 0.5) * 0.05;
      for (let i = 0; i < 2; i++) {
        const yc = 26 + i * 48 + (R() - 0.5) * 2, hh = 25.5 + R() * 2, x0 = 5 + R() * 3, x1 = 95 - R() * 3, sl = 3 + R() * 2.5;
        const yAt = (x, v) => yc + v * hh + (x - 50) * tilt + (R() - 0.5) * 0.9;
        const pts = [];
        for (let q = 0; q <= 6; q++) { const x = x0 + sl + ((x1 - x0) * q) / 6; pts.push([x, yAt(x, -1)]); }
        pts.push([x1 + sl * 0.2 + R(), yc + (R() - 0.5) * 2]);
        for (let q = 6; q >= 0; q--) { const x = x0 - sl + ((x1 - x0) * q) / 6; pts.push([x, yAt(x, 1)]); }
        pts.push([x0 - sl * 0.2 - R(), yc + (R() - 0.5) * 2]);
        const d = 'M' + pts.map(p => r1(p[0]) + ' ' + r1(p[1])).join('L') + 'Z';
        h += `<path d="${d}" fill="${HI_FILL}" fill-opacity="${r2(0.4 + R() * 0.06)}" stroke="${HI_FILL}" stroke-opacity=".35" stroke-width="1.1"/>`;
        let st = '';
        for (let q = 0; q < 3; q++) {
          const y = yc + (R() - 0.5) * hh * 1.5, a = x0 + R() * 20, b = x1 - R() * 20;
          st += 'M' + r1(a) + ' ' + r1(y + (a - 50) * tilt) + 'L' + r1(b) + ' ' + r1(y + (b - 50) * tilt + (R() - 0.5));
        }
        h += `<path d="${st}" stroke="#e8b800" stroke-opacity=".16" stroke-width="${r1(1 + R() * 1.5)}"/>`;
      }
      return h;
    },
    sel(Rs, R) {
      const pts = loopPts(50, 51, 42.5, 41, 1.14, Rs() * TAU, -0.07, R, (Rs() - 0.5) * 0.4);
      return `<path class="ink-loop" d="${penStroke(pts, R, { amp: 0.7, sp: 2.6 })}" stroke-width="3.1" pathLength="1"/>`;
    },
    dot(Rs, R) {
      // a pen pressed down and circled a few times: a small, slightly scruffy filled dot
      const pts = [];
      const a0 = Rs() * TAU, turns = 2.8;
      for (let i = 0; i <= 80; i++) { const t = i / 80, a = a0 + t * TAU * turns, r = 1.2 + 7.6 * Math.sqrt(t); pts.push([50 + Math.cos(a) * r, 50 + Math.sin(a) * r * 0.92]); }
      return `<path d="${penStroke(pts, R, { amp: 0.3, sp: 1.4 })}" stroke-width="2.7" stroke-opacity=".7" pathLength="1"/>`;
    },
    ring(Rs, R) {
      const pts = loopPts(50, 51, 44, 43, 1.08, Rs() * TAU, -0.05, R, 0);
      return `<path d="${penStroke(pts, R, { amp: 0.6, sp: 2.6 })}" stroke-width="2.5" stroke-opacity=".8" pathLength="1"/>`;
    },
    check(Rs, R) {
      const pts = loopPts(50, 52, 41, 39, 2.35, Rs() * TAU, -0.16, R, (Rs() - 0.5) * 0.6);
      return `<path d="${penStroke(pts, R, { amp: 1.1, sp: 2.6 })}" stroke="${colors.red}" stroke-width="2.7" pathLength="1"/>`;
    },
    danger(Rs, R) { return dashedLoop(Rs, R, colors.red, 43, 2.9); },
    free(Rs, R) { return dashedLoop(Rs, R, colors.green, 43, 2.9); },
    hint(Rs, R) {
      const pts = loopPts(48, 53, 40, 39, 1.12, Rs() * TAU, -0.06, R, 0);
      return `<path d="${penStroke(pts, R, { amp: 0.7, sp: 2.6 })}" stroke="${colors.green}" stroke-width="2.8" pathLength="1"/>` +
        sparkle(48, 53, 44, R, colors.green, 2.2) + twinkle(14, 86, 6, R, colors.green, 1.6);
    },
    ping(Rs, R) {
      const pts = loopPts(50, 51, 41, 40, 1.3, Rs() * TAU, -0.1, R, 0.2);
      return `<path d="${penStroke(pts, R, { amp: 0.9, sp: 2.6 })}" stroke="${colors.pencil}" stroke-width="2.2" pathLength="1"/>`;
    }
  };
  const MARK_ALIAS = { target: 'ring', targets: 'ring', capture: 'ring', select: 'sel', selected: 'sel', move: 'dot', legal: 'dot', alert: 'danger', hanging: 'danger', threat: 'danger', mate: 'check', tip: 'hint', best: 'hint', coach: 'ping', highlight: 'last' };

  function mark(kind, seed) {
    init();
    kind = MARK_ALIAS[kind] || kind;
    if (!MARKS[kind]) kind = 'ping';
    seed = seed == null ? 1 : seed;
    const key = ['mk', kind, seed, colors.ink].join('|');
    return cached(key, () => {
      const frames = kind === 'last' ? 1 : 3;
      let body = '';
      for (let f = 0; f < frames; f++) {
        const Rs = rng(hash('mark', kind, seed)), R = rng(hash('mark', kind, seed, f));
        body += frameOpen(f) + MARKS[kind](Rs, R) + '</g>';
      }
      const style = kind === 'last' ? ' style="mix-blend-mode:multiply"' : '';
      return `<svg class="ink-mark ink-mark-${kind}${frames > 1 ? ' ink-svg' : ''}" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true" color="${colors.ink}"${style}>${body}</svg>`;
    });
  }

  /* ---------------------------------------------------------------- badge */
  function badge(sym, color) {
    init();
    sym = sym == null ? '' : String(sym);
    const c = penColor(color || 'red');
    const key = ['bd', sym, c, colors.ink].join('|');
    return cached(key, () => {
      const R = rng(hash('badge', sym, c));
      const pts = loopPts(20, 20.5, 15.2, 14.6, 1.12, -1.9 + (R() - 0.5) * 0.6, -0.05, R, 0);
      const d = penStroke(pts, R, { amp: 0.35, sp: 1.4, k: 0.4 });
      const rot = r1(-8 + R() * 10);
      // colour emoji ignore the pen colour (and CSS filters on SVG text are unreliable), so draw them in ink instead
      let glyph;
      if (sym === '📖') {
        glyph = `<path d="M9 14c4-2 8-2 11 0 3-2 7-2 11 0v13c-4-2-8-2-11 0-3-2-7-2-11 0z M20 14v13" fill="none" stroke="${c}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" transform="rotate(${rot} 20 20)"/>`;
      } else {
        const txt = sym === '👍' ? '✓✓' : sym;
        const chars = Array.from(txt).length;
        const fs = chars >= 3 ? 14 : chars === 2 ? 18 : 23;
        glyph = `<text x="20" y="21.5" text-anchor="middle" dominant-baseline="central" font-family="${FONT}" font-weight="700" font-size="${fs}" fill="${c}" transform="rotate(${rot} 20 20)">${esc(txt)}</text>`;
      }
      return `<svg class="ink-badge" viewBox="0 0 40 40" aria-hidden="true" color="${colors.ink}">` +
        `<ellipse cx="20" cy="20.5" rx="15.6" ry="15" fill="${PAPER}"/>` +
        `<path d="${d}" fill="none" stroke="${c}" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"/>` +
        glyph + '</svg>';
    });
  }

  /* ---------------------------------------------------------------- arrows (board 800x800 space) */
  function arrow(x0, y0, x1, y1, o) {
    o = o || {};
    const c = penColor(o.color || 'green'), w = o.width || 6.5;
    const R = rng(hash('arrow', o.seed == null ? 1 : o.seed, r1(x0), r1(y0), r1(x1), r1(y1)));
    const trim = o.trim || [0, 0];
    let dx = x1 - x0, dy = y1 - y0, L = Math.hypot(dx, dy) || 1;
    let ux = dx / L, uy = dy / L;
    x0 += ux * (trim[0] || 0); y0 += uy * (trim[0] || 0); x1 -= ux * (trim[1] || 0); y1 -= uy * (trim[1] || 0);
    dx = x1 - x0; dy = y1 - y0; L = Math.hypot(dx, dy) || 1; ux = dx / L; uy = dy / L;
    const nx = -uy, ny = ux, bow = (R() < 0.5 ? -1 : 1) * L * (0.025 + R() * 0.035);
    const mx = (x0 + x1) / 2 + nx * bow, my = (y0 + y1) / 2 + ny * bow;
    const B = t => { const u = 1 - t; return [u * u * x0 + 2 * u * t * mx + t * t * x1, u * u * y0 + 2 * u * t * my + t * t * y1]; };
    const n = Math.max(6, Math.ceil(L / 30)), pts = [];
    for (let i = 0; i <= n; i++) pts.push(B(i / n));
    let shaft = '';
    if (o.dashed) {
      let s = 0;
      const total = L;
      while (s < total - 8) {
        const len = Math.min(18 + R() * 12, total - s), a = s / total, b = (s + len) / total, seg = [];
        for (let q = 0; q <= 4; q++) seg.push(B(a + ((b - a) * q) / 4));
        shaft += penStroke(seg, R, { k: 8, amp: 1, sp: 8 });
        s += len + 12 + R() * 7;
      }
    } else shaft = penStroke(pts, R, { k: 8, amp: 2.2, sp: 18 });
    const shaft2 = o.dashed ? '' : penStroke(pts.slice(Math.floor(n * 0.15)), R, { k: 8, amp: 2.8, sp: 18, bias: (R() < 0.5 ? -1 : 1) * (2 + R() * 1.5) });
    // head: tangent at the tip, a quick "V"
    const tx = x1 - mx, ty = y1 - my, tl = Math.hypot(tx, ty) || 1, ax = tx / tl, ay = ty / tl;
    const hl = clamp(L * 0.3, 26, 42) * Math.sqrt(w / 6.5), spread = 0.5 + (R() - 0.5) * 0.1;
    const side = s => { const a = Math.atan2(ay, ax) + Math.PI + s * spread; return [x1 + Math.cos(a) * hl * (0.92 + R() * 0.16), y1 + Math.sin(a) * hl * (0.92 + R() * 0.16)]; };
    const lp = side(1), rp = side(-1);
    const head = penStroke([lp, [(lp[0] + x1) / 2, (lp[1] + y1) / 2], [x1 + ax * 2, y1 + ay * 2], [(rp[0] + x1) / 2, (rp[1] + y1) / 2], rp], R, { k: 8, amp: 1, sp: 7 });
    return `<g class="ink-arrow${o.dashed ? ' ink-arrow-dashed' : ''}" color="${colors.ink}" fill="none" stroke="${c}" stroke-linecap="round" stroke-linejoin="round" opacity=".92">` +
      `<path class="ink-arrow-shaft" d="${shaft}" stroke-width="${r2(w)}" pathLength="1"/>` +
      (shaft2 ? `<path class="ink-arrow-shaft2" d="${shaft2}" stroke-width="${r2(w * 0.42)}" stroke-opacity=".55" pathLength="1"/>` : '') +
      `<path class="ink-arrow-head" d="${head}" stroke-width="${r2(w * 1.05)}" pathLength="1"/></g>`;
  }

  /* ---------------------------------------------------------------- board effects */
  const FX = {
    cross(R, c) {
      // scribble-out: tight zig-zags over the piece, then one slash back across
      const pts = [], n = 9, rot = -0.35 + (R() - 0.5) * 0.2, cr = Math.cos(rot), sr = Math.sin(rot);
      for (let i = 0; i <= n; i++) {
        const u = -34 + (68 * i) / n + (R() - 0.5) * 3, v = (i % 2 ? 1 : -1) * (24 + R() * 7);
        pts.push([50 + u * cr - v * sr, 52 + u * sr + v * cr]);
      }
      const slash = [[16 + R() * 4, 30 + R() * 6], [50, 52 + (R() - 0.5) * 4], [84 - R() * 4, 74 - R() * 6]];
      return `<path class="ink-draw" style="--i:0" d="${penStroke(pts, R, { amp: 0.6, sp: 3 })}" stroke="${c}" stroke-width="3.2" pathLength="1"/>` +
        `<path class="ink-draw" style="--i:1" d="${penStroke(slash, R, { amp: 0.8, sp: 3 })}" stroke="${c}" stroke-width="2.6" pathLength="1"/>`;
    },
    poof(R, c) {
      const pts = [], bumps = 7 + Math.floor(R() * 3), a0 = R() * TAU, ph = [];
      for (let i = 0; i < bumps; i++) ph.push(0.85 + R() * 0.3);
      for (let i = 0; i <= 140; i++) {
        const t = i / 140, a = a0 + t * TAU, bi = Math.floor(t * bumps) % bumps, bt = (t * bumps) % 1;
        const r = (22 + 8 * Math.sin(Math.PI * bt) * ph[bi]) * (1 + 0.04 * Math.sin(a * 3));
        pts.push([50 + Math.cos(a) * r, 52 + Math.sin(a) * r * 0.82]);
      }
      let h = `<path class="ink-draw" style="--i:0" d="${penStroke(pts, R, { amp: 0.6, sp: 2.4, closed: true, ov: 4 })}" fill="${PAPER}" stroke="${c}" stroke-width="2.6" pathLength="1"/>`;
      // a curl of smoke inside
      const curl = [];
      for (let i = 0; i <= 30; i++) { const t = i / 30, a = 0.5 + t * TAU * 1.1, r = 3 + 9 * t; curl.push([46 + Math.cos(a) * r, 54 + Math.sin(a) * r * 0.75]); }
      h += `<path class="ink-draw" style="--i:1" d="${penStroke(curl, R, { amp: 0.3, sp: 2 })}" stroke="${c}" stroke-width="1.8" stroke-opacity=".8" pathLength="1"/>`;
      const spots = [[12, 18], [86, 14], [90, 78], [10, 82], [50, 7]];
      spots.forEach((p, i) => { h += i % 2 ? asterisk(p[0] + (R() - 0.5) * 4, p[1] + (R() - 0.5) * 4, 4.5 + R() * 2, R, c, 1.8) : twinkle(p[0], p[1], 5 + R() * 2, R, c, 1.6); });
      return h;
    },
    stars(R, c) {
      let h = twinkle(50, 46, 15 + R() * 3, R, c, 2.4);
      h += asterisk(22 + R() * 6, 24 + R() * 6, 6.5, R, c, 2) + asterisk(80 - R() * 6, 74 - R() * 6, 5.5, R, c, 2);
      h += twinkle(80 - R() * 5, 20 + R() * 5, 7, R, c, 1.8) + twinkle(20 + R() * 5, 78 - R() * 5, 6, R, c, 1.8);
      h += `<circle cx="${r1(64 + R() * 6)}" cy="${r1(86 + R() * 4)}" r="1.4" fill="${c}" stroke="none"/><circle cx="${r1(8 + R() * 5)}" cy="${r1(50 + R() * 6)}" r="1.2" fill="${c}" stroke="none"/>`;
      return h;
    }
  };

  function fx(kind, seed, o) {
    init();
    o = o || {};
    if (!FX[kind]) kind = 'stars';
    const c = penColor(o.color || 'ink');
    const key = ['fx', kind, seed == null ? 1 : seed, c, colors.ink].join('|');
    return cached(key, () => {
      const R = rng(hash('fx', kind, seed == null ? 1 : seed));
      return `<svg class="ink-fx-svg ink-fx-${kind}" viewBox="0 0 100 100" aria-hidden="true" color="${colors.ink}">${frameOpen(0)}${FX[kind](R, c)}</g></svg>`;
    });
  }

  /* ---------------------------------------------------------------- defs: hatch patterns */
  // Periodic 40x40 tiles of slightly irregular 45-degree lines (seamless), three variants per level so fills can boil.
  const PAT = { 1: { n: 7, dirs: 1, w: 0.5, op: 0.8 }, 2: { n: 9, dirs: 1, w: 0.55, op: 0.85 }, 3: { n: 9, dirs: 2, w: 0.55, op: 0.85 }, 4: { n: 12, dirs: 2, w: 0.6, op: 0.9 } };
  function patternMarkup() {
    let h = '';
    for (const lvl of [1, 2, 3, 4]) {
      const P = PAT[lvl];
      for (let f = 0; f < 3; f++) {
        const R = rng(hash('pattern', lvl, f));
        let lines = '';
        for (let dir = 0; dir < P.dirs; dir++) {
          for (let i = 0; i < P.n; i++) {
            const c = ((i + 0.5 + (R() - 0.5) * 0.55) * 40) / P.n, w = r2(P.w * (0.8 + R() * 0.45)), op = r2(P.op * (0.75 + R() * 0.25));
            let d = '';
            for (const cc of [c - 40, c, c + 40]) {
              // "/" lines satisfy x + y = cc + 20 ; "\" lines satisfy x - y = cc - 20
              d += dir === 0 ? `M${r1(-6)} ${r1(cc + 26)}L${r1(46)} ${r1(cc - 26)}` : `M${r1(-6)} ${r1(-6 - cc + 20)}L${r1(46)} ${r1(46 - cc + 20)}`;
            }
            lines += `<path d="${d}" stroke-width="${w}" stroke-opacity="${op}"/>`;
          }
        }
        h += `<pattern id="ink-h${lvl}-${f}" patternUnits="userSpaceOnUse" width="40" height="40"><g fill="none" stroke="currentColor" stroke-linecap="round">${lines}</g></pattern>`;
      }
    }
    // paper + hatch combined tiles (opaque), handy for CSS backgrounds or large fills
    for (const lvl of [1, 2, 3, 4]) h += `<pattern id="ink-ph${lvl}" patternUnits="userSpaceOnUse" width="40" height="40"><rect width="40" height="40" fill="${PAPER}"/><rect width="40" height="40" fill="url(#ink-h${lvl}-0)"/></pattern>`;
    return h;
  }

  let defsEl = null, inited = false;
  function ensureDefs() {
    if (!doc || !doc.body) return;
    defsEl = doc.getElementById('ink-defs');
    if (!defsEl) {
      const wrap = doc.createElement('div');
      wrap.innerHTML = `<svg id="ink-defs" aria-hidden="true" focusable="false" style="position:absolute;width:0;height:0;overflow:hidden;pointer-events:none" color="${colors.ink}"><defs>${patternMarkup()}</defs></svg>`;
      defsEl = wrap.firstElementChild;
      doc.body.appendChild(defsEl);
    }
    defsEl.setAttribute('color', colors.ink);
  }

  /** Idempotent. opts.ink: 'blue' (default ballpoint) | 'black'. Changing ink clears the caches. */
  function init(opts) {
    const want = opts && opts.ink ? (PENS[opts.ink] || opts.ink) : null;
    if (want && want !== colors.ink) { colors.ink = want; cache.clear(); }
    if (!inited || want) {
      inited = true;
      if (doc && doc.body) ensureDefs();
      else if (doc) doc.addEventListener('DOMContentLoaded', ensureDefs, { once: true });
    }
    return Ink;
  }

  const Ink = {
    enabled: false,
    defaultFrames: 3, // boil frames when a caller does not ask for a count (the app sets 1 for reduced motion)
    colors,
    init,
    convert,
    piece,
    coach,
    board,
    mark,
    badge,
    arrow,
    fx,
    pen: penColor,
    penFor,
    hatchFill: (lvl, frame) => `url(#ink-h${clamp(lvl | 0, 1, 4)}-${clamp(frame | 0, 0, 2)})`,
    clearCache: () => cache.clear(),
    _cache: cache
  };
  global.Ink = Ink;
})(typeof window !== 'undefined' ? window : globalThis);
