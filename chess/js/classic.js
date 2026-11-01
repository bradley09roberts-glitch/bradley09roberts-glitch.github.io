/* Mosswood Chess: classic Staunton-style pieces (window.ClassicArt), for players who want to see the real chess pieces.
   Same conventions as the creature ART in pieces.js: each entry is p => markup for a 0 0 100 100 viewBox, using the
   palette object p (p.L outline, p.body fill, p.body2 shading, p.dot knight's eye), a .pc-shadow ellipse at cy 91 and
   everything else inside <g class="pc-body">. Every piece stands on the same plinth (a slab and a ring); light comes
   from the upper left, so each part carries a p.body2 crescent along its right edge (the Ink renderer turns these into
   hatching) and a faint white sheen on its left (colour mode only; Ink drops it).
     1 pawn  2 knight  3 bishop  4 rook  5 queen  6 king */
(function (global) {
  'use strict';

  const S = (p, w) => `stroke="${p.L}" stroke-width="${w || 2.6}" stroke-linejoin="round" stroke-linecap="round"`;
  const shadow = rx => `<ellipse class="pc-shadow" cx="50" cy="91" rx="${rx}" ry="4.2"/>`;
  const SHEEN = 'fill="none" stroke="#fff" stroke-width="2.2" opacity=".34" stroke-linecap="round"';

  const f = v => String(Math.round(v * 100) / 100);
  const pt = (x, y) => f(x) + ' ' + f(y);
  const mx = x => 100 - x;

  /* ---------------------------------------------------------------- geometry helpers */
  /** Closed outline of a left/right symmetric part, from its right half: `start` is the top point (on or right of the
      x = 50 axis) and `segs` run down to the bottom: ['L', x, y] or ['C', x1, y1, x2, y2, x, y]. */
  function sym(start, segs) {
    const ends = [start];
    let d = start[0] !== 50 ? `M${pt(mx(start[0]), start[1])} L${pt(start[0], start[1])}` : `M${pt(start[0], start[1])}`;
    for (const s of segs) {
      if (s[0] === 'C') { d += ` C${pt(s[1], s[2])} ${pt(s[3], s[4])} ${pt(s[5], s[6])}`; ends.push([s[5], s[6]]); }
      else { d += ` L${pt(s[1], s[2])}`; ends.push([s[1], s[2]]); }
    }
    const end = ends[ends.length - 1];
    if (end[0] !== 50) d += ` L${pt(mx(end[0]), end[1])}`;
    for (let i = segs.length - 1; i >= 0; i--) {
      const s = segs[i], to = ends[i];
      d += s[0] === 'C' ? ` C${pt(mx(s[3]), s[4])} ${pt(mx(s[1]), s[2])} ${pt(mx(to[0]), to[1])}` : ` L${pt(mx(to[0]), to[1])}`;
    }
    return d + ' Z';
  }

  /** Path data continuing through segments (same format as sym), without a leading M. */
  const segsD = segs => segs.map(s => (s[0] === 'C' ? `C${pt(s[1], s[2])} ${pt(s[3], s[4])} ${pt(s[5], s[6])}` : `L${pt(s[1], s[2])}`)).join(' ');

  function bez(c, t) {
    const u = 1 - t, a = u * u * u, b = 3 * u * u * t, e = 3 * u * t * t, g = t * t * t;
    return [a * c[0] + b * c[2] + e * c[4] + g * c[6], a * c[1] + b * c[3] + e * c[5] + g * c[7]];
  }

  /** n + 1 points evenly spaced (by length) along a chain of segments, optionally only between fractions t0..t1. */
  function along(start, segs, n, t0, t1) {
    const dense = [];
    let p = start;
    for (const s of segs) {
      const q = s[0] === 'C' ? [s[5], s[6]] : [s[1], s[2]];
      const c = s[0] === 'C' ? [p[0], p[1], s[1], s[2], s[3], s[4], q[0], q[1]]
        : [p[0], p[1], p[0] + (q[0] - p[0]) / 3, p[1] + (q[1] - p[1]) / 3, p[0] + (2 * (q[0] - p[0])) / 3, p[1] + (2 * (q[1] - p[1])) / 3, q[0], q[1]];
      for (let i = dense.length ? 1 : 0; i <= 40; i++) dense.push(bez(c, i / 40));
      p = q;
    }
    return resample(dense, n, t0 == null ? 0 : t0, t1 == null ? 1 : t1);
  }

  /** n + 1 points along a circle/ellipse arc (degrees, clockwise on screen). */
  function arc(cx, cy, rx, ry, a0, a1, n) {
    const out = [];
    for (let i = 0; i <= n; i++) {
      const a = ((a0 + ((a1 - a0) * i) / n) * Math.PI) / 180;
      out.push([cx + rx * Math.cos(a), cy + ry * Math.sin(a)]);
    }
    return out;
  }

  function resample(P, n, t0, t1) {
    const L = [0];
    for (let i = 1; i < P.length; i++) L.push(L[i - 1] + Math.hypot(P[i][0] - P[i - 1][0], P[i][1] - P[i - 1][1]));
    const total = L[L.length - 1], out = [];
    let j = 0;
    for (let i = 0; i <= n; i++) {
      const target = total * (t0 + ((t1 - t0) * i) / n);
      while (j < P.length - 2 && L[j + 1] < target) j++;
      const seg = L[j + 1] - L[j] || 1, t = Math.min(1, Math.max(0, (target - L[j]) / seg));
      out.push([P[j][0] + (P[j + 1][0] - P[j][0]) * t, P[j][1] + (P[j + 1][1] - P[j][1]) * t]);
    }
    return out;
  }

  /** A shading crescent hugging an edge: the edge points must run so that the inside of the part is on their left
      when walking down the right-hand side (i.e. clockwise around the part). It sits `inset` inside the outline and
      swells to `thick` in the middle, tapering to points at both ends. */
  function crescent(pts, inset, thick, skew) {
    const n = pts.length - 1, outer = [], inner = [];
    for (let i = 0; i <= n; i++) {
      const a = pts[Math.max(0, i - 1)], b = pts[Math.min(n, i + 1)];
      const tx = b[0] - a[0], ty = b[1] - a[1], l = Math.hypot(tx, ty) || 1;
      const nx = -ty / l, ny = tx / l;
      let s = i / n;
      if (skew) s = Math.pow(s, skew);
      const w = thick * Math.pow(Math.sin(Math.PI * s), 0.62);
      outer.push([pts[i][0] + nx * inset, pts[i][1] + ny * inset]);
      inner.push([pts[i][0] + nx * (inset + w), pts[i][1] + ny * (inset + w)]);
    }
    return outer.concat(inner.reverse());
  }

  const polyD = P => 'M' + P.map(q => pt(q[0], q[1])).join(' L') + ' Z';
  function polyArea(P) {
    let a = 0;
    for (let i = 0; i < P.length; i++) { const q = P[(i + 1) % P.length]; a += P[i][0] * q[1] - q[0] * P[i][1]; }
    return Math.abs(a) / 2;
  }

  /** Dark army palette? (classic sets draw the black pieces' details in white, see light()) */
  function isDark(p) {
    const h = String(p.body || '').replace('#', ''), n = parseInt(h.length === 3 ? h.replace(/./g, c => c + c) : h, 16);
    return isFinite(n) && (0.299 * (n >> 16 & 255) + 0.587 * (n >> 8 & 255) + 0.114 * (n & 255)) / 255 < 0.45;
  }

  /** Pale detail colour for the dark army: the bishop's slit and the king's cross ink as paper-white highlights. */
  const light = p => p.ermine || '#e6e0f0';

  /** A p.body2 shading patch: path data, or a polygon (from crescent). On the dark army small patches are left out,
      because the Ink renderer would draw them as solid black blobs inside the cross-hatching. */
  function shade(p, d) {
    if (Array.isArray(d)) {
      if (isDark(p) && polyArea(d) < 60) return '';
      d = polyD(d);
    }
    return `<path d="${d}" fill="${p.body2}"/>`;
  }
  const part = (p, d, w) => `<path d="${d}" fill="${p.body}" ${S(p, w)}/>`;
  const line = (p, d, w) => `<path d="${d}" fill="none" ${S(p, w || 1.5)}/>`;

  /* ---------------------------------------------------------------- shared pieces of a piece */
  /** The plinth every piece stands on: a wide slab (half width hw, y 82.4-89.8) with a ring on top (y 76.8-82.8). */
  function base(p, hw) {
    const slab = [['L', 50 + hw - 3.6, 82.4], ['C', 50 + hw - 2, 82.4, 50 + hw - 1.4, 83.1, 50 + hw - 0.9, 84.6],
      ['L', 50 + hw - 0.1, 87.3], ['C', 50 + hw + 0.3, 88.9, 50 + hw - 0.7, 89.8, 50 + hw - 2.3, 89.8], ['L', 50, 89.8]];
    const rw = hw - 4, rr = 3;
    const ring = [['L', 50 + rw - rr, 76.8], ['C', 50 + rw + 1, 76.8, 50 + rw + 1, 82.8, 50 + rw - rr, 82.8], ['L', 50, 82.8]];
    // shading at the slab's right end (left out on the dark army, where it would ink as a solid black block)
    const xs = 50 + hw, xa = 50 + hw * 0.4;
    const slabShade = isDark(p) ? '' : shade(p, `M${pt(xa, 84.2)} L${pt(xs - 3.2, 84.2)} Q${pt(xs - 2, 84.4)} ${pt(xs - 1.9, 85.8)} L${pt(xs - 1.6, 86.9)} Q${pt(xs - 1.7, 88.3)} ${pt(xs - 3, 88.3)} L${pt(xa, 88.3)} Q${pt(xa - 2.2, 86.25)} ${pt(xa, 84.2)} Z`);
    return part(p, sym([50, 82.4], slab))
      + slabShade
      + `<path d="M${pt(50 - hw + 2.8, 85.2)} L${pt(50 - hw + 8, 85.2)}" ${SHEEN}/>`
      + part(p, sym([50, 76.8], ring))
      + pillShade(p, rw, 79.8, 6)
      + `<path d="M${pt(50 - rw + 1.8, 78.9)} L${pt(50 - rw + 7, 78.9)}" ${SHEEN}/>`;
  }

  /** A trumpet-shaped stem flaring from half width wt at yt down to wb at yb (its foot hides behind the ring). */
  function stem(p, yt, wt, yb, wb) {
    const h = yb - yt;
    const segs = [['L', 50 + wt, yt], ['C', 50 + wt + 0.2, yt + h * 0.5, 50 + wt + (wb - wt) * 0.42, yb - h * 0.2, 50 + wb, yb], ['L', 50, yb]];
    const edge = along([50 + wt, yt], segs.slice(1, 2), 18, 0.02, 0.86);
    return part(p, sym([50, yt], segs))
      + shade(p, crescent(edge, 1.7, Math.min(5.2, wt * 0.55 + 1.4), 0.85))
      + `<path d="M${pt(50 - wt + 2, yt + h * 0.3)} C${pt(50 - wt + 1.7, yt + h * 0.55)} ${pt(50 - wt - (wb - wt) * 0.18, yb - h * 0.28)} ${pt(50 - wt - (wb - wt) * 0.45, yb - h * 0.12)}" ${SHEEN}/>`;
  }

  /** Shading at the right end of a pill-shaped band (half width hw, centred at yc, height h). Skipped on the dark
      army, where such a small patch would ink as a solid black block. */
  function pillShade(p, hw, yc, h) {
    if (isDark(p)) return '';
    const xa = 50 + hw * 0.42, xr = 50 + hw, yt = yc - h / 2 + 1.5, yb = yc + h / 2 - 1.5;
    return shade(p, `M${pt(xa, yt)} L${pt(xr - 3.2, yt)} Q${pt(xr - 1.4, yt + 0.1)} ${pt(xr - 1.4, yc)} Q${pt(xr - 1.4, yb - 0.1)} ${pt(xr - 3.2, yb)} L${pt(xa, yb)} Q${pt(xa - 1.6, yc)} ${pt(xa, yt)} Z`);
  }

  /** A pill-shaped band (rounded rectangle) with its shading. */
  const band = (p, hw, yc, h) => `<rect x="${f(50 - hw)}" y="${f(yc - h / 2)}" width="${f(hw * 2)}" height="${f(h)}" rx="${f(h / 2)}" fill="${p.body}" ${S(p, 2.4)}/>` + pillShade(p, hw, yc, h);

  /** A collar disc (flattened ring) centred at cy. */
  const disc = (p, cy, rx, ry, w) => `<ellipse cx="50" cy="${f(cy)}" rx="${f(rx)}" ry="${f(ry)}" fill="${p.body}" ${S(p, w || 2.3)}/>`;

  /** Map every coordinate pair of an absolute-command path (M/L/C/Q only) through fn. */
  const mapD = (d, fn) => d.replace(/(-?[\d.]+)[ ,](-?[\d.]+)/g, (m, x, y) => { const q = fn(+x, +y); return pt(q[0], q[1]); });

  /* ---------------------------------------------------------------- the six pieces */
  const ClassicArt = {
    /* Pawn: round head on a collar, flared stem, plinth */
    1: p => `${shadow(21)}<g class="pc-body">
      ${stem(p, 55, 7.4, 78, 17.6)}
      ${base(p, 23)}
      ${disc(p, 55.4, 14.2, 3.8, 2.4)}
      <circle cx="50" cy="38.6" r="14.6" fill="${p.body}" ${S(p)}/>
      ${shade(p, crescent(arc(50, 38.6, 14.6, 14.6, -72, 112, 22), 1.7, 5.4))}
      <path d="${'M' + arc(50, 38.6, 10.6, 10.6, 196, 252, 8).map(q => pt(q[0], q[1])).join(' L')}" ${SHEEN}/>
    </g>`,

    /* Knight: a horse's head in profile, facing left (drawn narrow and tall, then fitted by K) */
    2: p => {
      const K = (x, y) => [46 + (x - 46) * 1.1, 79 - (79 - y) * 0.952];
      const back = [['C', 60.6, 28.4, 64.4, 33.6, 66.6, 40.6], ['C', 69.6, 50.6, 70, 64.6, 66.8, 79]];
      const outline = 'M33.4 79 C33.2 71.6 35 66.2 38.8 61.8 C41.8 58.4 44 55.6 44.6 51.4 C44.9 49.6 44.4 48.4 43.2 48.2'
        + ' C40.4 48.8 37.4 50 34.4 51.6 C31.4 53.2 28.4 54.4 25.4 54.1 C21.6 53.7 19.8 50.8 20.6 47.4'
        + ' C22.6 41.6 27 35 31.8 30.2 C36.2 25.6 41.6 21.4 47.4 18.6 L50.6 10.2 C51.2 9.1 52.3 9.1 52.8 10'
        + ' L56.4 20.8 C57.6 22.2 58.8 25 59.4 26.8 ' + segsD(back) + ' Z';
      const maneEdge = along([59.4, 26.8], back, 20, 0.04, 0.97).map(q => K(q[0], q[1]));
      const k = d => mapD(d, K), e = K(40.6, 31.4);
      return `${shadow(22)}<g class="pc-body">
      ${base(p, 25)}
      <path d="${k(outline)}" fill="${p.body}" ${S(p)}/>
      ${shade(p, crescent(maneEdge, 1.7, 7.8, 0.9))}
      <path d="${k('M50.9 14.6 L52.3 11.8 L54.4 18.6')}" fill="${p.body2}" ${S(p, 1.2)}/>
      <path d="${k('M62.2 31.2 C61 33.6 59.4 34.6 57.6 35.2 M66.4 40.6 C64.6 43.4 62.8 44.4 60.6 45 M68.5 51.2 C66.6 53.8 64.6 54.8 62.4 55.2 M68.6 62 C66.8 64.4 64.8 65.4 62.6 65.8')}" fill="none" ${S(p, 1.5)}/>
      <path d="${k('M57.4 24.6 C60.6 30.4 61.8 37.2 61.2 44.6')}" fill="none" ${S(p, 1.3)}/>
      <ellipse cx="${f(e[0])}" cy="${f(e[1])}" rx="2.7" ry="2" transform="rotate(-30 ${f(e[0])} ${f(e[1])})" fill="${p.dot}" ${S(p, 1.1)}/>
      <path d="${k('M36.2 29.4 C38.2 27.2 41.2 26.4 44 27.2')}" fill="none" ${S(p, 1.3)}/>
      <path d="${k('M24.2 45.8 C25.8 45.6 26.8 46.6 26.6 48')}" fill="none" ${S(p, 1.5)}/>
      <path d="${k('M21.2 50.8 C23.4 51 25.6 50.6 27.2 49.8')}" fill="none" ${S(p, 1.3)}/>
      <path d="${k('M43 48.4 C43.6 44 41.6 40.6 38.4 39')}" fill="none" ${S(p, 1.3)}/>
      <path d="${k('M24.4 42.6 C27.4 37.4 32 31.8 37.2 27.4')}" ${SHEEN}/>
    </g>`;
    },

    /* Bishop: mitre with a diagonal slit and a ball on top, double collar */
    3: p => {
      const mitre = [['C', 54.4, 23.2, 59.6, 26.8, 63.2, 31.6], ['C', 69, 39, 68.6, 47.4, 61.2, 52.9], ['C', 58, 54.2, 54.2, 54.6, 50, 54.6]];
      const edge = along([50, 20.2], mitre.slice(0, 2), 40);
      const dk = isDark(p), a = edge[dk ? 15 : 16], b = edge[dk ? 21 : 20], cut = dk ? light(p) : p.L;
      return `${shadow(22)}<g class="pc-body">
      ${stem(p, 55.6, 6.8, 78, 17.6)}
      ${base(p, 25)}
      ${disc(p, 56.4, 14.4, 3.6, 2.4)}
      ${disc(p, 52.2, 10.8, 2.8, 2.1)}
      ${part(p, sym([50, 20.2], mitre))}
      ${shade(p, crescent(along([50, 20.2], mitre.slice(0, 2), 22, 0.12, 0.98), 1.7, 5.8, 0.9))}
      <path d="M${pt(a[0] + 0.5, a[1] - 0.3)} L46 42.2 L${pt(b[0] + 0.5, b[1] + 0.1)} Z" fill="${cut}" ${S(p, 1.2)}/>
      <path d="M39.6 44.6 C38.4 38.8 40.8 32.4 45.6 26.8" ${SHEEN}/>
      <circle cx="50" cy="16.4" r="4.3" fill="${p.body}" ${S(p, 2.3)}/>
      <path d="M47.5 15.5 C47.7 14.4 48.6 13.5 49.7 13.3" ${SHEEN}/>
    </g>`;
    },

    /* Rook: castle turret with three merlons on a straight tower */
    4: p => {
      const tower = [['L', 63.6, 35], ['C', 63.9, 50, 64.3, 62, 64.8, 69.2], ['C', 65.4, 74, 67.6, 76.6, 70.6, 78.4], ['L', 50, 78.4]];
      const turret = [['L', 55.2, 11.6], ['L', 55.2, 19.6], ['L', 60.6, 19.6], ['L', 60.6, 11.6], ['L', 71.4, 11.6], ['L', 68.2, 33.2], ['L', 50, 33.2]];
      return `${shadow(24)}<g class="pc-body">
      ${part(p, sym([50, 35], tower))}
      ${shade(p, crescent(along([63.6, 35], tower.slice(1, 3), 20, 0.03, 0.82), 1.7, 5.2, 0.9))}
      <path d="M38.6 40.6 C38.2 51 37.8 61.6 36.8 70" ${SHEEN}/>
      ${line(p, 'M36.2 66 C45.4 67.4 54.6 67.4 63.8 66')}
      ${base(p, 26)}
      ${band(p, 17.8, 34, 6)}
      ${part(p, sym([50, 11.6], turret))}
      ${shade(p, crescent(along([71.4, 11.6], turret.slice(5, 6), 14), 1.6, 4.8))}
      ${line(p, 'M31.2 24.4 C43.8 25.8 56.2 25.8 68.8 24.4')}
      <path d="M32.2 15 L32.7 20.8" ${SHEEN}/>
    </g>`;
    },

    /* Queen: five-pointed coronet topped with little balls */
    5: p => {
      const crown = [['L', 55.4, 31.6], ['L', 60.6, 18], ['L', 66.4, 33.4], ['L', 71.2, 25],
        ['C', 67.6, 32, 62.4, 40.4, 58.6, 46], ['C', 56, 47.2, 53, 47.4, 50, 47.4]];
      const balls = [[28.4, 22.6, 2.9], [39.2, 15.4, 3.1], [50, 11.5, 3.4], [60.8, 15.4, 3.1], [71.6, 22.6, 2.9]];
      return `${shadow(24)}<g class="pc-body">
      ${stem(p, 50.6, 7, 78, 19)}
      ${base(p, 26)}
      ${disc(p, 51.4, 14.6, 3.6, 2.4)}
      ${disc(p, 47.2, 11.4, 2.8, 2.1)}
      ${part(p, sym([50, 14.2], crown))}
      ${shade(p, crescent(along([71.2, 25], crown.slice(4, 5), 18, 0.06, 1), 1.6, 4.8, 0.9))}
      ${line(p, 'M35.6 39 C44.9 41.4 55.1 41.4 64.4 39')}
      <path d="M34.6 32.2 C36.8 36 38.9 39.4 40.8 42.6" ${SHEEN}/>
      ${balls.map(b => `<circle cx="${b[0]}" cy="${b[1]}" r="${b[2]}" fill="${p.body}" ${S(p, 2.2)}/>`).join('')}
    </g>`;
    },

    /* King: tall crown with a band, a dome and a cross on top */
    6: p => {
      const cup = [['L', 64.6, 29.4], ['C', 64.4, 35.8, 61.6, 42.8, 58, 47.8], ['C', 55.6, 48.8, 53, 49, 50, 49]];
      const dome = [['C', 58.2, 16, 64.4, 19.8, 64.8, 26.2], ['L', 50, 26.2]];
      const cross = [['L', 52.6, 4.8], ['L', 52.6, 8.8], ['L', 57, 8.8], ['L', 57, 13.1], ['L', 52.6, 13.1], ['L', 52.6, 18], ['L', 50, 18]];
      return `${shadow(25)}<g class="pc-body">
      ${stem(p, 51.6, 7.2, 78, 19.4)}
      ${base(p, 26.4)}
      ${disc(p, 52.4, 15, 3.6, 2.4)}
      ${disc(p, 48.2, 11.8, 2.9, 2.1)}
      ${part(p, sym([50, 29.4], cup))}
      ${shade(p, crescent(along([64.6, 29.4], cup.slice(1, 2), 16, 0.04, 0.95), 1.6, 4.6, 0.9))}
      <path d="M38.6 33.2 C39.2 37.8 40.7 42 42.8 45.6" ${SHEEN}/>
      <path d="${sym([50, 4.8], cross)}" fill="${isDark(p) ? light(p) : p.body}" ${S(p, 2.3)}/>
      ${part(p, sym([50, 16], dome))}
      ${shade(p, crescent(along([50, 16], dome.slice(0, 1), 14, 0.3, 1), 1.5, 4))}
      <path d="M38.6 22.8 C40 20.3 42.2 18.8 45.2 18" ${SHEEN}/>
      ${band(p, 17, 27.8, 6.2)}
    </g>`;
    }
  };

  global.ClassicArt = ClassicArt;
})(typeof window !== 'undefined' ? window : globalThis);
