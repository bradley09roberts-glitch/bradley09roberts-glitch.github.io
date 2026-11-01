/* Mosswood Chess: margin doodles for the Sketchbook look.
   A set of little Mosswood drawings (moon, toadstools, snail, a Frog King peeking over a ruled line, fireflies ...)
   and handwritten margin notes, drawn as clean SVG (one dark line colour + simple light fills, the same conventions as
   pieces.js) so the Ink renderer can turn them into wobbly ballpoint doodles.

   Doodles.mount(container) scatters them around the page margins (never over the central column on desktop),
   Doodles.svgs[id] is the clean markup of one doodle and Doodles.render(id) gives it ready to use (inked when the
   Sketchbook ink is on) so other screens can reuse one, e.g. for an empty state. */
(function (global) {
  'use strict';

  const doc = global.document;
  const L = '#3b2414';
  const C = {
    paper: '#fff4de', cream: '#fffaf0', white: '#ffffff', dot: '#2b1a10', cheek: '#ff9a86',
    cap: '#e0473b', cap2: '#b02f27', spot: '#fff8ea', leaf: '#86c95b', leaf2: '#a2dd73',
    gold: '#f6c847', moon: '#f6d36b', glow: '#ffe79a', fire: '#fff2a8', beak: '#f0a830',
    fur: '#e2cfb0', fur2: '#c9ae86', bark: '#c49a6c', brown: '#7d5536', door: '#8a5532', iron: '#6b5a48',
    frog: '#a9d977', blue: '#3a78cf', cap3: '#6f8fd8', shell: '#e8b86a', snail: '#efe6cc', berry: '#d4405a',
    drag: '#f3d382', drag2: '#d9a845', wing: '#f09f5b', wing2: '#c8743a', scale: '#fff3c9', sq: '#9a8b78',
    red: '#d0373b', green: '#2c8a47', pencil: '#77736b', hi: 'rgba(255,224,40,.5)'
  };
  const FONT = "Caveat, 'Patrick Hand', 'Segoe Print', 'Bradley Hand', cursive";

  /* ---------------------------------------------------------------- drawing helpers */
  const f1 = v => Math.round(v * 10) / 10;
  const S = w => `stroke="${L}" stroke-width="${w || 2.4}" stroke-linejoin="round" stroke-linecap="round"`;
  const line = (d, w) => `<path d="${d}" fill="none" ${S(w)}/>`;
  const shape = (d, fill, w) => `<path d="${d}" fill="${fill}" ${S(w)}/>`;
  const circ = (cx, cy, r, fill, w) => `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${fill}"${w ? ' ' + S(w) : ''}/>`;
  const ell = (cx, cy, rx, ry, fill, w, extra) => `<ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="${fill}"${w ? ' ' + S(w) : ''}${extra || ''}/>`;
  const blush = (cx, cy, rx, ry) => ell(cx, cy, rx || 3, ry || 2, C.cheek, 0, ' opacity=".55"');
  const pts = a => a.map(p => f1(p[0]) + ' ' + f1(p[1]));
  const poly = a => 'M' + pts(a).join(' L');

  /** five-pointed star outline */
  function star(cx, cy, R, r, rot) {
    const a = [];
    for (let i = 0; i < 10; i++) {
      const ang = ((rot == null ? -90 : rot) + i * 36) * Math.PI / 180, rr = i % 2 ? r : R;
      a.push([cx + Math.cos(ang) * rr, cy + Math.sin(ang) * rr]);
    }
    return poly(a) + ' Z';
  }
  /** the doodler's star: one continuous pen stroke */
  function penStar(cx, cy, R, rot) {
    const a = [];
    for (let i = 0; i < 6; i++) {
      const ang = ((rot == null ? -90 : rot) + ((i * 2) % 5) * 72) * Math.PI / 180;
      a.push([cx + Math.cos(ang) * R, cy + Math.sin(ang) * R]);
    }
    return poly(a);
  }
  /** spiral from radius r0 (centre) to r1 over `turns` turns */
  function spiral(cx, cy, r0, r1, turns, a0, flipY) {
    const n = Math.ceil(turns * 28), a = [];
    for (let i = 0; i <= n; i++) {
      const t = i / n, ang = (a0 || 0) + t * turns * Math.PI * 2, r = r0 + (r1 - r0) * t;
      a.push([cx + Math.cos(ang) * r, cy + Math.sin(ang) * r * (flipY ? -1 : 1)]);
    }
    return poly(a);
  }
  /** pointed leaf from a base point, along angle `deg` */
  function leaf(bx, by, deg, len, wid) {
    const a = deg * Math.PI / 180, dx = Math.cos(a), dy = Math.sin(a), nx = -dy, ny = dx;
    const P = (t, o) => f1(bx + dx * len * t + nx * o) + ' ' + f1(by + dy * len * t + ny * o);
    return `M${P(0, 0)} C${P(0.28, wid)} ${P(0.72, wid * 0.85)} ${P(1, 0)} C${P(0.72, -wid * 0.85)} ${P(0.28, -wid)} ${P(0, 0)} Z`;
  }
  function leafVein(bx, by, deg, len) {
    const a = deg * Math.PI / 180;
    return `M${f1(bx + Math.cos(a) * len * 0.12)} ${f1(by + Math.sin(a) * len * 0.12)} L${f1(bx + Math.cos(a) * len * 0.78)} ${f1(by + Math.sin(a) * len * 0.78)}`;
  }
  /** short dashes along a parametric curve fn(t)->[x,y]: a dotted flight trail / footpath */
  function dashes(fn, dash, gap, steps) {
    steps = steps || 400;
    const a = [];
    let prev = fn(0), acc = 0, on = true, cur = [prev];
    for (let i = 1; i <= steps; i++) {
      const p = fn(i / steps), d = Math.hypot(p[0] - prev[0], p[1] - prev[1]);
      acc += d;
      if (on) cur.push(p);
      if (acc >= (on ? dash : gap)) {
        if (on && cur.length > 1) a.push(cur);
        on = !on; acc = 0; cur = [p];
      }
      prev = p;
    }
    if (on && cur.length > 1) a.push(cur);
    return a.map(s => 'M' + f1(s[0][0]) + ' ' + f1(s[0][1]) + ' L' + f1(s[s.length - 1][0]) + ' ' + f1(s[s.length - 1][1])).join(' ');
  }
  const text = (x, y, size, str, o) => {
    o = o || {};
    return `<text x="${x}" y="${y}" font-size="${size}" font-family="${FONT}" font-weight="${o.weight || 600}" fill="${o.fill || L}"` +
      `${o.len ? ` textLength="${o.len}" lengthAdjust="spacingAndGlyphs"` : ''}>${str}</text>`;
  };

  /* ---------------------------------------------------------------- the doodles
     Each: id, label, vb [w, h], zone (top | bottom | any: where on the page it likes to sit), size (relative scale),
     anim (wrapper idle animation), note (handwritten text), under (highlighter drawn beneath the ink), art(). */
  function firefly(x, y, rot, s, n) {
    return `<g transform="translate(${x} ${y}) rotate(${rot}) scale(${s})">` +
      line('M-1 -8.5 C-2 -12 -4 -13.5 -6.5 -13.5 M1 -8.5 C2 -12 4 -13.5 6.5 -13.5', 1.2) +
      `<g class="dd-glow dd-g${n}">` + line('M0 12.5 L0 15.8 M-5.6 10 L-8 12.2 M5.6 10 L8 12.2 M-7.4 4.6 L-10.6 4 M7.4 4.6 L10.6 4', 1.3) + '</g>' +
      circ(0, 5.6, 4.6, C.fire, 1.7) +
      ell(0, -1, 3.1, 4.1, C.dot) +
      circ(0, -6.6, 2.6, C.dot) +
      ell(-4.2, -2.4, 2.6, 5.4, C.white, 1.3, ' transform="rotate(28 -4.2 -2.4)"') +
      ell(4.2, -2.4, 2.6, 5.4, C.white, 1.3, ' transform="rotate(-28 4.2 -2.4)"') +
      '</g>';
  }

  const DEFS = [
    { id: 'moon', label: 'Sleepy moon', vb: [100, 100], zone: 'top', size: 1.12, anim: 'float',
      art: () =>
        shape('M44 14 C20 18 8 42 14 64 C20 84 44 96 66 88 C74 85 80 80 84 74 C66 80 46 72 38 56 C30 40 34 24 44 14 Z', C.moon, 2.6) +
        line('M19 45 Q23.5 49.5 28 45', 2) + line('M21 61 Q25.5 64.5 30 60.5', 1.8) + blush(19.5, 54, 3.4, 2.2) +
        `<g class="dd-tw">${shape(star(70, 28, 10, 4.4), C.gold, 2)}</g>` +
        shape(star(88, 52, 5.8, 2.6, -80), C.gold, 1.7) +
        line('M57 4.5 L57 14.5 M52 9.5 L62 9.5', 1.7) + circ(91, 21, 1.4, C.dot) + circ(80, 66, 1.2, C.dot) + circ(94, 36, 1, C.dot)
    },
    { id: 'toadstools', label: 'Toadstool cluster', vb: [100, 100], zone: 'bottom', size: 1, anim: '',
      art: () =>
        line('M3 88.5 C24 86 48 90.5 70 87.5 C80 86.4 90 88.6 97 87.4', 2.2) +
        line('M7 88 L5 81 M9.5 88 L10.5 79.5 M12 88.4 L15.5 82 M87 87.4 L85 81 M89.5 87.4 L90.5 79.5 M92 87.6 L95.5 82.5', 1.7) +
        // medium toadstool (behind)
        shape('M68 88 C67 83 67.5 78 69.5 73 L78 73 C80 78 80.5 83 79.5 88 Z', C.paper, 2.2) +
        shape('M59 75 C58 63 65 56 73.5 56 C82 56 89 63 88 75 C79 77.5 67 77.5 59 75 Z', C.cap, 2.4) +
        circ(67, 64, 2.6, C.spot) + circ(79, 62.5, 3, C.spot) + circ(74.5, 70.5, 1.8, C.spot) +
        // tiny one
        shape('M21 89 C20.5 86 21 83.5 22 82 L26.5 82 C27.5 83.5 28 86 27.5 89 Z', C.paper, 1.9) +
        shape('M16.5 83 C16.5 77 20 74.5 24.2 74.5 C28.4 74.5 32 77 32 83 C27.5 84.6 21 84.6 16.5 83 Z', C.cap, 2) +
        circ(22.4, 78.4, 1.7, C.spot) + circ(27.6, 79.6, 1.2, C.spot) +
        // big one with a face
        shape('M34 88 C32 78 33 68 36 60 L50 60 C53 68 54 78 52 88 Z', C.paper, 2.4) +
        ell(39.6, 71, 1.5, 1.9, C.dot) + ell(46.4, 71, 1.5, 1.9, C.dot) + line('M41 76 Q43 78.4 45 76', 1.5) +
        blush(37.4, 75.4, 2, 1.4) + blush(48.6, 75.4, 2, 1.4) +
        shape('M16 61 C14 41 28 28 43 28 C58 28 72 41 70 61 C56 65 30 65 16 61 Z', C.cap, 2.6) +
        `<path d="M22 60 C34 62.5 52 62.5 64 60" fill="none" stroke="${C.cap2}" stroke-width="2.2" stroke-linecap="round" opacity=".8"/>` +
        circ(29.5, 46, 4.4, C.spot) + circ(44, 36.5, 3.4, C.spot) + circ(57.5, 47, 4.6, C.spot) + circ(42, 53, 2.6, C.spot) + circ(64, 38.5, 1.8, C.spot)
    },
    { id: 'snail', label: 'Snail', vb: [120, 84], zone: 'bottom', size: 1, anim: 'creep',
      art: () =>
        line('M104.5 76.6 L108.5 76.8 M112 77 L115 76.8 M117.6 76.6 L119 76.6', 1.6) +
        line('M10 26 C12 31 15 38 18.5 47 M29 22.5 C27.5 30 26.5 36 26.5 44', 2.2) +
        shape('M8 70 C6 62 10 54 16 50 C18 42 30 40 33 47 C35 53 36 59 42 62 L90 62 C97 62 102 67 104.5 73 C82 76.5 30 76.5 8 70 Z', C.snail, 2.4) +
        line('M11 58.5 Q15 61.5 19.5 58', 1.6) + blush(23.5, 56.5, 2.6, 1.7) +
        circ(10, 23.5, 3.8, C.white, 1.8) + circ(29, 20.5, 3.8, C.white, 1.8) + circ(10.6, 24.4, 1.7, C.dot) + circ(29.6, 21.4, 1.7, C.dot) +
        circ(67, 38, 25, C.shell, 2.6) +
        line(spiral(68.5, 39.5, 1.5, 21.5, 2.55, -Math.PI * 0.35), 2.2) +
        line('M84 18 C88 21 90.5 25 91.5 29', 1.5)
    },
    { id: 'frog', label: 'Frog King peeking over the line', vb: [110, 64], zone: 'any', size: 1.05, anim: '', tilt: 2.5,
      art: () =>
        circ(36, 27, 11, C.frog, 2.4) + circ(74, 27, 11, C.frog, 2.4) +
        shape('M18 51 C18 35 34 27 55 27 C76 27 92 35 92 51 Z', C.frog, 2.4) +
        circ(36, 26, 7, C.white, 1.8) + circ(74, 26, 7, C.white, 1.8) +
        circ(38.6, 26.6, 3.4, C.dot) + circ(76.6, 26.6, 3.4, C.dot) + circ(39.6, 25.2, 1, C.white) + circ(77.6, 25.2, 1, C.white) +
        circ(51.5, 38.5, 1, C.dot) + circ(58.5, 38.5, 1, C.dot) +
        line('M40 43 Q55 49 70 43', 2) + blush(30, 42, 3.4, 2.2) + blush(80, 42, 3.4, 2.2) +
        shape('M47 29.5 L45.5 18 L51 23 L55 15 L59 23 L64.5 18 L63 29.5 C58 28.4 52 28.4 47 29.5 Z', C.gold, 2) + circ(55, 24.6, 1.7, C.cap) +
        line('M1 51 C30 50.2 70 51.6 109 50.6', 2.2) +
        ell(25, 52, 3, 3.6, C.frog, 1.9) + ell(30.5, 52.6, 3, 3.6, C.frog, 1.9) + ell(36, 52, 3, 3.6, C.frog, 1.9) +
        ell(74, 52, 3, 3.6, C.frog, 1.9) + ell(79.5, 52.6, 3, 3.6, C.frog, 1.9) + ell(85, 52, 3, 3.6, C.frog, 1.9)
    },
    { id: 'castle', label: 'Castle on a hill', vb: [100, 100], zone: 'bottom', size: 1.05, anim: '', tilt: 4,
      art: () =>
        line('M12 28 Q15 25 18 28 Q21 25 24 28 M77 17 Q79.4 14.6 81.8 17 Q84.2 14.6 86.6 17', 1.6) +
        shape('M2 95 C14 77 32 67 50 67 C68 67 86 77 98 95 Z', C.leaf2, 2.4) +
        line('M14 86 L12.5 81 M16.5 86 L17.5 80 M19 86.5 L22 82 M82 84 L80.5 79 M84.5 84 L85.5 78.5 M87 84.6 L90 80.5', 1.6) +
        line(dashes(t => [50 - 9 * t + 3 * Math.sin(t * 5), 77 + 17 * t], 2.6, 2.4), 1.6) +
        shape('M27 73 L28 50 L38 50 L38 73 Z', C.paper, 2.2) + shape('M25 51 L33 36 L41 51 Z', C.blue, 2.2) + line('M33 57 L33 63', 2) +
        shape('M62 73 L62 50 L72 50 L73 73 Z', C.paper, 2.2) + shape('M59 51 L67 36 L75 51 Z', C.blue, 2.2) + line('M67 57 L67 63', 2) +
        shape('M37 75 L38 40 L62 40 L63 75 Z', C.paper, 2.4) +
        `<path d="M41 52 H47 M53 58 H59 M42 66 H46" fill="none" stroke="${C.fur}" stroke-width="2" stroke-linecap="round"/>` +
        shape('M45 75 L45 67 C45 61 55 61 55 67 L55 75 Z', C.door, 2.2) +
        circ(50, 51, 4.2, C.glow, 2) + line('M50 46.8 L50 55.2 M45.8 51 L54.2 51', 1.3) +
        line('M50 21 L50 6.5', 2) +
        `<g class="dd-flag">${shape('M50.6 6.5 C54 5 57 8 62 6.5 L62 13 C57 14.5 54 11.5 50.6 13 Z', C.blue, 1.8)}</g>` +
        shape('M33 43 C33 29 41 21.5 50 21.5 C59 21.5 67 29 67 43 C58 45.5 42 45.5 33 43 Z', C.cap, 2.4) +
        circ(41, 33, 2.8, C.spot) + circ(52.5, 27.5, 2.2, C.spot) + circ(59.5, 36, 3, C.spot) + circ(47, 40, 1.6, C.spot)
    },
    { id: 'fireflies', label: 'Fireflies', vb: [100, 100], zone: 'top', size: 1, anim: 'float',
      art: () =>
        line(dashes(t => [62 - 54 * t + 11 * Math.sin(t * Math.PI * 2), 31 + 20 * t - 11 * Math.cos(t * Math.PI * 2) + 11], 2.4, 2.8), 1.4) +
        line(dashes(t => [37 + 40 * t - 9 * Math.sin(t * Math.PI * 2), 58 + 30 * t + 9 * Math.cos(t * Math.PI * 2) - 9], 2.4, 2.8), 1.4) +
        line(dashes(t => [70 + 26 * t, 82 - 30 * t + 6 * Math.sin(t * Math.PI * 3)], 2.4, 2.8), 1.4) +
        firefly(72, 22, 32, 1.45, 1) + firefly(31, 51, -24, 1.2, 2) + firefly(64, 80, 58, 1.05, 3)
    },
    { id: 'fern', label: 'Fern frond', vb: [80, 130], zone: 'bottom', size: 1.05, anim: 'sway',
      art: () => {
        const P = t => [33 + 11 * t + 7 * Math.sin(Math.PI * t), 127 - 95 * t];
        const stem = [];
        for (let i = 0; i <= 30; i++) stem.push(P(i / 30));
        let leaves = '', veins = '';
        for (let i = 0; i < 8; i++) {
          const t = 0.08 + i * 0.105, p = P(t), q = P(t + 0.01), dir = Math.atan2(q[1] - p[1], q[0] - p[0]) * 180 / Math.PI;
          const len = 25 * (1 - t * 0.72), wid = 6.4 * (1 - t * 0.55);
          const pl = P(t), pr = P(t + 0.035);
          leaves += shape(leaf(pl[0], pl[1], dir - 58, len, wid), C.leaf, 1.9) + shape(leaf(pr[0], pr[1], dir + 58, len * 0.96, wid), C.leaf2, 1.9);
          if (len > 13) veins += leafVein(pl[0], pl[1], dir - 58, len) + ' ' + leafVein(pr[0], pr[1], dir + 58, len * 0.96) + ' ';
        }
        const top = P(1);
        return leaves + line(veins.trim(), 1.1) + line(poly(stem), 2.4) +
          line(spiral(top[0] - 6.5, top[1] - 6, 1.2, 7.6, 1.35, Math.PI * 0.2, false), 2.3) +
          line('M3 128 C12 126 24 127.5 34 127 C46 126.5 60 128 77 126.5', 1.8);
      }
    },
    { id: 'acorn', label: 'Acorn', vb: [80, 100], zone: 'any', size: 0.82, anim: 'sway',
      art: () =>
        shape('M46.5 12.5 C52 6.5 56 8 60 4 C62.5 8 66 8 70 6 C70 10 72.5 13 76.5 14 C72.5 17 70.5 19.5 70.5 23 C66.5 21.5 62 22.5 58 24.5 C56 20 52 16 46.5 12.5 Z', C.leaf, 1.9) +
        line('M47.5 13 L70 15.5', 1.2) +
        line('M40 23 C40 17 42 13 46.5 10', 3) +
        shape('M18 46 C16 66 26 86 40 93 C54 86 64 66 62 46 Z', C.shell, 2.6) +
        line('M37.5 92 L40 97.5 L42.5 92', 2) +
        ell(32, 64, 2, 2.6, C.dot) + ell(48, 64, 2, 2.6, C.dot) + circ(32.7, 63, 0.8, C.white) + circ(48.7, 63, 0.8, C.white) +
        line('M36 71.5 Q40 74.5 44 71.5', 1.6) + blush(27.5, 70, 2.8, 1.8) + blush(52.5, 70, 2.8, 1.8) +
        shape('M11 49 C9 32 23 22.5 40 22.5 C57 22.5 71 32 69 49 C56 53 24 53 11 49 Z', C.brown, 2.6)
    },
    { id: 'owl', label: 'Sleepy owl on a branch', vb: [100, 100], zone: 'any', size: 1, anim: '',
      art: () =>
        shape('M44 80 L42 93 L50 89 L58 93 L56 80 Z', C.fur2, 2) +
        shape('M2 79.5 C30 76 60 82 98 77.5 L98 84.5 C60 89 30 84 2 86.5 Z', C.bark, 2.2) +
        line('M86 79 C89 73 93 70 98 68.5', 2) + shape(leaf(93, 70, -40, 12, 3.6), C.leaf, 1.6) + shape(leaf(14, 85, 105, 13, 4), C.leaf2, 1.6) +
        shape('M30 80 C25 60 30 37 50 36 C70 37 75 60 70 80 C60 84 40 84 30 80 Z', C.fur, 2.6) +
        shape('M39 79 C36 70 42 64 50 64 C58 64 64 70 61 79 C56 81 44 81 39 79 Z', C.cream) +
        line('M44.5 69.5 L47 71.5 L49.5 69.5 M50.5 69.5 L53 71.5 L55.5 69.5 M47.5 75 L50 77 L52.5 75', 1.4) +
        shape('M30 57 C24 63 24 73 30.5 80 C33 72 34 64 30 57 Z', C.fur2, 2.2) + shape('M70 57 C76 63 76 73 69.5 80 C67 72 66 64 70 57 Z', C.fur2, 2.2) +
        circ(41.5, 54, 8.2, C.cream, 2) + circ(58.5, 54, 8.2, C.cream, 2) +
        line('M37 54 Q41.5 58 46 54 M54 54 Q58.5 58 63 54', 2) +
        shape('M47.6 59 L52.4 59 L50 64 Z', C.beak, 1.6) +
        ell(44, 81, 3.6, 2, C.beak, 1.4) + ell(56, 81, 3.6, 2, C.beak, 1.4) +
        shape('M32.5 46 C32 34 40 26.5 50 26.5 C60 26.5 66.5 31 70.5 38 C74 44 77.5 50 80 56.5 C74.5 52.5 70.5 48.5 67.5 46 C56 42 44 42 32.5 46 Z', C.cap3, 2.4) +
        shape('M32.5 46 C44 42 56 42 67.5 46 L68 50 C56 46 44 46 32 50 Z', C.cream, 2) +
        circ(81, 58.5, 4, C.white, 1.8) +
        `<g class="dd-zz">${text(73, 22, 13, 'z', { weight: 700 })}${text(81, 13, 17, 'Z', { weight: 700 })}</g>`
    },
    { id: 'dragon', label: 'Baby dragon flying', vb: [124, 86], zone: 'top', size: 1.12, anim: 'float',
      art: () => {
        const wing = 'M70 47 C68 38 66 30 64 24 C60 19 54 15 46 13 C49 18 50 23 50 27 C46 25 41 25 37 27 C41 30 44 34 45 38 C41 38 37 40 34 43 C42 44 50 46 56 49 Z';
        return `<g class="dd-wing dd-wing2"><g transform="translate(10 -4)">${shape(wing, C.wing2, 2)}</g></g>` +
          shape('M46.5 61 C36 61 30 69 20 67 C12 65.5 8 59.5 9.5 53 C12.5 57 16 59.5 22 59.5 C30 59.5 36 53 46.5 53 Z', C.drag, 2.3) +
          shape('M10 54 C6 51.5 3 47.5 4 42 C8 44 12 46 14 50.5 Z', C.wing2, 1.8) +
          shape('M52 46.5 L54.5 40.5 L58 45.5 Z', C.wing2, 1.6) + shape('M74 45.5 L77.5 40 L80 47 Z', C.wing2, 1.6) +
          shape('M40 60 C40 50 52 45 66 45 C78 45 86 49 88 55 C90 63 82 70 66 71 C52 72 40 69 40 60 Z', C.drag, 2.6) +
          shape('M50 66 C58 69.5 70 69.5 79 64.5 C81 62.5 81 60.5 80 59 C72 63.5 60 65 50 64 Z', C.scale, 1.5) +
          ell(55.5, 72, 4.2, 2.6, C.drag2, 1.8) + ell(73, 71.2, 4.2, 2.6, C.drag2, 1.8) +
          line('M86 58.5 C89.5 60 91.5 62 92 65', 2) +
          shape('M84 29 C80 23 79 18 80 13.5 C84 17.5 87 22.5 88 26.5 Z', C.cream, 1.8) +
          shape('M92 26 C92 20 94 16 97 13.5 C98 18.5 97.5 22.5 96 26.5 Z', C.cream, 1.8) +
          shape('M78 40 C76 28 85 22 94 25 C100 27 104 32 109 36 C113 39 112 46 107 47.5 C100 49.5 91 51 85 49.5 C80.5 48 78.5 44 78 40 Z', C.drag, 2.6) +
          ell(92.5, 34.5, 4, 4.6, C.white, 1.6) + ell(93.5, 35.3, 1.8, 3, C.dot) + circ(94.3, 33.3, 0.9, C.white) +
          ell(106, 38.5, 1.1, 0.85, C.dot) + line('M97.5 44 Q102.5 46.5 107.5 43.2', 1.6) + blush(88, 43, 3, 2) +
          `<g class="dd-wing dd-wing1">${shape(wing, C.wing, 2.4) + line('M64 24.5 L47.5 14.5 M64 25 L38.5 27.5 M64.5 25.5 L36 42.5', 1.3)}</g>` +
          `<g class="dd-puff">${circ(115, 33, 3, C.white, 1.4)}${circ(119.5, 26.5, 2.2, C.white, 1.3)}${circ(122, 21, 1.3, C.white, 1.2)}</g>`;
      }
    },
    { id: 'lantern', label: 'Lantern', vb: [72, 112], zone: 'top', size: 0.95, anim: '',
      art: () =>
        line('M2 7.5 C20 4.5 46 10 70 5.5', 2.4) + shape(leaf(14, 6.4, 120, 13, 4), C.leaf, 1.6) + shape(leaf(58, 7.2, 70, 11, 3.4), C.leaf2, 1.6) +
        `<g class="dd-swing">` + line('M36 8 L36 15', 2) + circ(36, 18, 3, 'none', 2) +
        line('M24 32 C24 22.5 48 22.5 48 32', 2) +
        `<g class="dd-glow dd-g2">${line('M9 48 L3.5 46 M8 61 L1.5 61 M9 74 L3.5 77 M63 48 L68.5 46 M64 61 L70.5 61 M63 74 L68.5 77', 1.6)}</g>` +
        shape('M22 34 C16 46 16 70 22 82 L50 82 C56 70 56 46 50 34 Z', C.glow, 2.4) +
        line('M30 35 C27 50 27 66 30 81 M42 35 C45 50 45 66 42 81', 1.6) +
        `<rect x="32" y="63" width="8" height="18" rx="1.5" fill="${C.white}" ${S(1.8)}/>` + line('M36 60.5 L36 63', 1.4) +
        `<g class="dd-flame">${shape('M36 46 C32 52 32 58 36 60.5 C40 58 40 52 36 46 Z', C.gold, 1.6)}</g>` +
        shape('M20 34.5 L52 34.5 L46 26 L26 26 Z', C.iron, 2.4) +
        shape('M18 82 L54 82 L50 90 L22 90 Z', C.iron, 2.4) + line('M32 90 L33.5 95 L38.5 95 L40 90', 2) + '</g>'
    },
    { id: 'beetle', label: 'Beetle', vb: [90, 104], zone: 'bottom', size: 0.82, anim: 'wiggle',
      art: () =>
        line('M26 46 L14 40 L8 30 M24 60 L10 62 L4 70 M28 74 L17 84 L15 94 M64 46 L76 40 L82 30 M66 60 L80 62 L86 70 M62 74 L73 84 L75 94', 2.2) +
        line('M41 16 C37 10 31 8 27 10.5 M49 16 C53 10 59 8 63 10.5', 1.8) + circ(26.5, 11, 1.7, C.dot) + circ(63.5, 11, 1.7, C.dot) +
        ell(45, 22, 10, 8, C.iron, 2.2) + circ(40.5, 19.5, 2.4, C.white, 1.2) + circ(49.5, 19.5, 2.4, C.white, 1.2) + circ(41, 20, 1, C.dot) + circ(50, 20, 1, C.dot) +
        shape('M26 36 C26 26 64 26 64 36 C64 42 26 42 26 36 Z', C.iron, 2.4) +
        shape('M45 38 L45 92 C30 92 20 78 20 60 C20 46 30 38 45 38 Z', C.cap, 2.6) +
        shape('M45 38 C60 38 70 46 70 60 C70 78 60 92 45 92 Z', C.cap, 2.6) +
        circ(33, 52, 4, C.dot) + circ(31.5, 71, 3.6, C.dot) + circ(39.5, 84, 2.4, C.dot) +
        circ(57, 52, 4, C.dot) + circ(58.5, 71, 3.6, C.dot) + circ(50.5, 84, 2.4, C.dot) +
        `<path d="M27 49 C29 44 33 41 38 40" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" opacity=".45"/>`
    },
    { id: 'berries', label: 'Berries on a sprig', vb: [100, 92], zone: 'any', size: 0.92, anim: 'sway',
      art: () =>
        line('M6 88 C22 74 40 58 58 47 C70 40 82 32 95 19', 2.6) + line('M59 46.5 C62 53 63.5 58 63.5 63', 2) + line('M84 30 C85.5 34 86 37 86 40.5', 1.8) +
        shape(leaf(30, 68, -150, 25, 8), C.leaf, 2) + line(leafVein(30, 68, -150, 25), 1.2) +
        shape(leaf(48, 54, -78, 23, 7.5), C.leaf2, 2) + line(leafVein(48, 54, -78, 23), 1.2) +
        shape(leaf(78, 35, 18, 20, 6.5), C.leaf, 2) + line(leafVein(78, 35, 18, 20), 1.2) +
        shape(leaf(16, 80, 150, 16, 5.4), C.leaf2, 1.8) +
        circ(71, 69, 6.6, C.berry, 2.2) + circ(57.5, 70, 6.6, C.berry, 2.2) + circ(64.5, 80, 6.8, C.berry, 2.2) + circ(77, 80.5, 5.4, C.berry, 2.2) +
        circ(86, 45, 5.2, C.berry, 2) + circ(93.5, 50.5, 4.2, C.berry, 2) +
        circ(55.4, 67.6, 1.7, C.white) + circ(69, 66.6, 1.7, C.white) + circ(62.2, 77.4, 1.8, C.white) + circ(75.4, 78.4, 1.4, C.white) + circ(84.4, 43, 1.4, C.white)
    },
    { id: 'swirls', label: 'Swirls and stars', vb: [100, 100], zone: 'any', size: 0.9, anim: '',
      art: () =>
        line(spiral(33, 62, 1.5, 23, 2.7, 0.3), 2.4) +
        `<g class="dd-tw">${line(penStar(71, 30, 19, -90), 2.4)}</g>` +
        line('M86 66 L86 78 M80.8 69 L91.2 75 M91.2 69 L80.8 75', 2) +
        shape('M15 11 C16 17 17 18.5 23 19.5 C17 20.5 16 22 15 28 C14 22 13 20.5 7 19.5 C13 18.5 14 17 15 11 Z', C.gold, 1.7) +
        line('M58 90 C62 83 66 95 70 89 C74 83 78 95 82 89 C84 86 86 86 88 88', 2) +
        circ(49, 13, 1.5, C.dot) + circ(93, 49, 1.2, C.dot) + circ(8, 90, 1.2, C.dot)
    },
    { id: 'board', label: 'Chessboard corner', vb: [100, 100], zone: 'any', size: 0.95, anim: '', tilt: 4,
      art: () => {
        let sq = '';
        for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) {
          if ((r + c) % 2) continue; // a1 (bottom-left) is dark
          sq += `<rect x="${22 + c * 22}" y="${54 - r * 22}" width="22" height="22" fill="${C.sq}"/>`;
        }
        return sq +
          line('M22 4 L22 76 L96 76', 2.8) +
          line('M44 76 L44 6 M66 76 L66 8 M22 54 L94 54 M22 32 L92 32 M22 10 L88 10', 1.6) +
          text(29, 92, 13, 'a') + text(51, 92, 13, 'b') + text(73, 92, 13, 'c') +
          text(9, 70, 13, '1') + text(9, 48, 13, '2') + text(9, 26, 13, '3') +
          // a little Hare on b1 and its jump to c3
          `<path d="M52 67.5 C49.5 63 49 58.5 50.5 57 C52.5 57 53.5 62 53.6 66.5 Z M57.5 66.5 C58 62 60 57.5 62 58 C63.4 59.6 61.6 63.6 59.6 67.6 Z" fill="${C.cream}" ${S(1.5)}/>` +
          circ(55.5, 70, 5, C.cream, 1.7) + circ(53.7, 69.4, 0.8, C.dot) + circ(57.3, 69.4, 0.8, C.dot) +
          `<g class="dd-red">${line('M58 61 C60 45 66 34 74.5 25.5 M67 25.6 L75.2 24.6 L74.8 33', 2.1).replace(L, C.red)}</g>`;
      }
    },
    { id: 'note-hares', label: 'Note: watch the Hares!', vb: [184, 62], zone: 'any', size: 1.18, anim: '', note: true,
      art: () =>
        text(6, 33, 28, 'watch the Hares!', { weight: 700, len: 146 }) +
        line('M8 45 C28 41 48 49 70 44.5 C92 40 112 47 134 43.5 C142 42.5 148 43 154 44.5', 1.8) +
        `<g transform="translate(6 0)"><path d="M162 41 C158.5 33 158 25 160 23.5 C162.5 23.5 163.6 31 163.8 39 Z M168 39 C169 31 171.5 24 173.8 24.6 C175.6 26.6 173 34 170 41 Z" fill="${C.cream}" ${S(1.5)}/>` +
        circ(166, 45.5, 6.2, C.cream, 1.7) + circ(163.8, 44.8, 0.9, C.dot) + circ(168.2, 44.8, 0.9, C.dot) + line('M165.2 48.2 L166 49 L166.8 48.2', 1.1) + '</g>'
    },
    { id: 'note-castle', label: 'Note: castle early', vb: [156, 56], zone: 'any', size: 1.1, anim: '', note: true,
      under: `<path class="dd-hi" d="M4 18.5 C40 16.5 84 19.5 120 17.5 L121.5 39 C84 41.5 40 39.5 3 40.5 Z" fill="${C.hi}"/>`,
      art: () =>
        text(8, 35, 27, 'castle early', { weight: 700, len: 108 }) +
        `<g class="dd-green">${line('M124.5 29 C128 31 130.5 34.5 131.5 40 C135.5 29.5 141.5 20.5 151 12.5', 3).replace(L, C.green)}</g>`
    },
    { id: 'note-opening', label: 'Note: 1. e4 e5 2. Nf3', vb: [176, 50], zone: 'any', size: 1.12, anim: '', note: true,
      art: () =>
        `<g class="dd-pencil">${text(6, 31, 25, '1. e4 e5 2. Nf3 …', { weight: 500, len: 160, fill: C.pencil })}` +
        `<path d="M6 41 C40 39.5 90 42 140 40" fill="none" stroke="${C.pencil}" stroke-width="1.4" stroke-linecap="round"/></g>`
    },
    { id: 'note-check', label: 'Note: check, think!', vb: [168, 58], zone: 'any', size: 1.12, anim: '', note: true,
      art: () =>
        `<g class="dd-red">` +
        text(6, 34, 27, 'check', { weight: 700, len: 58, fill: C.red }) +
        `<path d="M70 26 C80 24.5 88 26 98 25 M91 18.5 L99 25 L91.5 31.5" fill="none" stroke="${C.red}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>` +
        text(106, 34, 27, 'think!', { weight: 700, len: 56, fill: C.red }) +
        `<path d="M104 42 C120 40 144 41.5 164 40 M108 48 C124 46.5 142 47.5 160 46.5" fill="none" stroke="${C.red}" stroke-width="1.8" stroke-linecap="round"/>` +
        `</g>`
    }
  ];

  /* ---------------------------------------------------------------- markup */
  const svgs = {};
  const BY = {};
  DEFS.forEach((d, i) => {
    d.index = i;
    BY[d.id] = d;
    svgs[d.id] = `<svg class="dd-art dd-art-${d.id}" viewBox="0 0 ${d.vb[0]} ${d.vb[1]}" aria-hidden="true">${d.under || ''}${d.art()}</svg>`;
  });
  const list = DEFS.map(d => ({ id: d.id, label: d.label, w: d.vb[0], h: d.vb[1], zone: d.zone, note: !!d.note }));

  const inkOn = () => !!(global.Ink && global.Ink.enabled);
  const inkCache = new Map();

  /** One doodle as markup: inked when the Sketchbook ink is on (or opts.ink), else the clean drawing. */
  function render(id, opts) {
    opts = opts || {};
    const d = BY[id];
    if (!d) return '';
    injectCSS();
    const cls = ['dd-svg', opts.cls].filter(Boolean).join(' ');
    const useInk = opts.ink == null ? inkOn() : !!opts.ink && !!global.Ink;
    if (!useInk) return svgs[id].replace('class="dd-art ', `class="${cls} dd-art `);
    const Ink = global.Ink;
    const frames = opts.frames == null ? 3 : opts.frames, seed = opts.seed == null ? d.index + 1 : opts.seed;
    const key = [id, frames, seed, cls, Ink.colors && Ink.colors.ink].join('|');
    let out = inkCache.get(key);
    if (out == null) {
      try {
        out = Ink.convert(svgs[id], { frames, seed, side: 'w', cls });
        // the highlighter swipe sits under the ink (it is not a pen line, so the converter would drop it)
        if (d.under) out = out.replace(/^(<svg[^>]*>)/, '$1' + d.under);
      } catch (e) {
        out = svgs[id].replace('class="dd-art ', `class="${cls} dd-art `);
      }
      inkCache.set(key, out);
    }
    return out;
  }

  /* ---------------------------------------------------------------- styles */
  const CSS = `
.dd-root { pointer-events: none; }
.dd-root .dd { position: absolute; display: block; line-height: 0; pointer-events: none; transform-origin: 50% 50%; }
.dd-root .dd > svg { display: block; width: 100%; height: 100%; overflow: visible; }
.dd-root .dd > svg.ink-svg { color: var(--sk-ink, #22408f); }
.dd-svg .dd-red { color: var(--sk-red, #d0373b); }
.dd-svg .dd-green { color: var(--sk-green, #2c8a47); }
.dd-svg .dd-pencil { color: var(--sk-pencil, #77736b); }
.dd-svg .dd-tw, .dd-svg .dd-swing, .dd-svg .dd-flag, .dd-svg .dd-wing, .dd-svg .dd-flame, .dd-svg .dd-zz, .dd-svg .dd-puff, .dd-svg .dd-glow { transform-box: fill-box; }
.dd-a-float > svg { animation: ddFloat 6.5s ease-in-out infinite; animation-delay: var(--dd-d, 0s); }
.dd-a-sway > svg { transform-origin: 50% 96%; animation: ddSway 5.5s ease-in-out infinite; animation-delay: var(--dd-d, 0s); }
.dd-a-wiggle > svg { animation: ddWiggle 7s ease-in-out infinite; animation-delay: var(--dd-d, 0s); }
.dd-a-creep > svg { animation: ddCreep 9s ease-in-out infinite; animation-delay: var(--dd-d, 0s); }
.dd-svg .dd-tw { transform-origin: 50% 50%; animation: ddTwinkle 3.2s ease-in-out infinite; animation-delay: var(--dd-d, 0s); }
.dd-svg .dd-glow { animation: ddBlink 2.6s ease-in-out infinite; }
.dd-svg .dd-g2 { animation-delay: -.9s; animation-duration: 3.1s; }
.dd-svg .dd-g3 { animation-delay: -1.7s; animation-duration: 2.2s; }
.dd-svg .dd-swing { transform-origin: 50% 0%; animation: ddSwing 4.8s ease-in-out infinite; animation-delay: var(--dd-d, 0s); }
.dd-svg .dd-flag { transform-origin: 0% 50%; animation: ddFlag 2.4s ease-in-out infinite; }
.dd-svg .dd-wing1 { transform-origin: 90% 96%; animation: ddFlap 1.5s ease-in-out infinite; }
.dd-svg .dd-wing2 { transform-origin: 90% 96%; animation: ddFlap 1.5s ease-in-out infinite; animation-delay: -.12s; }
.dd-svg .dd-flame { transform-origin: 50% 100%; animation: ddFlicker 1.3s ease-in-out infinite; }
.dd-svg .dd-zz { animation: ddZz 3.6s ease-in-out infinite; }
.dd-svg .dd-puff { transform-origin: 0% 100%; animation: ddPuff 3s ease-out infinite; }
@keyframes ddFloat { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-6px); } }
@keyframes ddSway { 0%, 100% { transform: rotate(-2.5deg); } 50% { transform: rotate(2.5deg); } }
@keyframes ddSwing { 0%, 100% { transform: rotate(-3deg); } 50% { transform: rotate(3deg); } }
@keyframes ddWiggle { 0%, 70%, 100% { transform: rotate(0); } 76% { transform: rotate(-4deg); } 84% { transform: rotate(4deg); } 92% { transform: rotate(-2deg); } }
@keyframes ddCreep { 0%, 100% { transform: translateX(0); } 50% { transform: translateX(-5px); } }
@keyframes ddTwinkle { 0%, 100% { transform: scale(1) rotate(0deg); opacity: 1; } 50% { transform: scale(.74) rotate(16deg); opacity: .6; } }
@keyframes ddBlink { 0%, 40%, 100% { opacity: 1; } 55%, 75% { opacity: .08; } }
@keyframes ddFlag { 0%, 100% { transform: skewY(0deg) scaleX(1); } 50% { transform: skewY(-9deg) scaleX(.9); } }
@keyframes ddFlap { 0%, 100% { transform: rotate(0deg); } 50% { transform: rotate(-11deg) scaleY(.92); } }
@keyframes ddFlicker { 0%, 100% { transform: scale(1, 1); } 30% { transform: scale(.88, 1.1); } 65% { transform: scale(1.08, .92); } }
@keyframes ddZz { 0% { transform: translate(-3px, 5px); opacity: 0; } 25%, 70% { opacity: 1; } 100% { transform: translate(4px, -5px); opacity: 0; } }
@keyframes ddPuff { 0% { transform: translate(-3px, 3px) scale(.6); opacity: 0; } 25% { opacity: 1; } 100% { transform: translate(4px, -4px) scale(1.1); opacity: 0; } }
@media (prefers-reduced-motion: reduce) {
  .dd-root .dd > svg, .dd-svg .dd-tw, .dd-svg .dd-swing, .dd-svg .dd-glow, .dd-svg .dd-flag, .dd-svg .dd-wing, .dd-svg .dd-flame, .dd-svg .dd-zz, .dd-svg .dd-puff { animation: none !important; }
}`;
  function injectCSS() {
    if (!doc || doc.getElementById('doodles-css')) return;
    const st = doc.createElement('style');
    st.id = 'doodles-css';
    st.textContent = CSS;
    (doc.head || doc.documentElement).appendChild(st);
  }

  /* ---------------------------------------------------------------- layout */
  function rng(seed) {
    let a = (seed >>> 0) || 0x9e3779b9;
    return function () {
      a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function shuffle(arr, R) {
    const a = arr.slice();
    for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(R() * (i + 1)); const t = a[i]; a[i] = a[j]; a[j] = t; }
    return a;
  }
  const ZONE = { top: 0, any: 1, bottom: 2 };

  /** Where the central content column sits: opts.content (px width) | opts.column (element/selector) | <main> | 760px. */
  function column(vw, opts) {
    if (opts.content) { const c = Math.min(vw, opts.content); return [(vw - c) / 2, (vw + c) / 2]; }
    let el = opts.column;
    if (typeof el === 'string') el = doc.querySelector(el);
    if (el === undefined) el = doc.querySelector('main');
    if (el && el.getBoundingClientRect) {
      const r = el.getBoundingClientRect();
      if (r.width > 40) {
        const cs = global.getComputedStyle(el);
        return [Math.max(0, r.left + (parseFloat(cs.paddingLeft) || 0)), Math.min(vw, r.right - (parseFloat(cs.paddingRight) || 0))];
      }
    }
    const c = Math.min(vw, 760);
    return [(vw - c) / 2, (vw + c) / 2];
  }

  const MODES = {
    // n: doodles per side, s: size range (px, geometric mean of width and height), note: width range of a margin note,
    // op: opacity, edge/inner: px kept clear at the page edge (binder holes) and next to the content column
    desk: { n: 6, s: [64, 150], note: [150, 200], top: 74, bot: 16, gap: 20, op: 0.92, notes: 1, edge: 18, inner: 16 },
    tab: { n: 3, s: [54, 92], note: [128, 160], top: 80, bot: 18, gap: 44, op: 0.8, notes: 1, edge: 10, inner: 10 }
  };

  const HOLES = 36;

  function sizeOf(d, px) {
    const g = Math.sqrt(d.vb[0] * d.vb[1]);
    return [d.vb[0] / g * px, d.vb[1] / g * px];
  }

  /** Stack a side's doodles down one margin (top to bottom). Returns placed items. */
  function placeSide(side, pool, notes, vw, vh, gutter, M, R) {
    // the left margin has binder holes and the red margin line: start right of the holes when there is room
    const edge = side === 'l' && gutter - HOLES - M.inner >= M.s[0] ? HOLES : Math.min(M.edge, Math.max(4, gutter * 0.2));
    const laneW = gutter - edge - M.inner;
    const tight = laneW < M.s[0];
    const lane0 = side === 'l' ? edge : vw - gutter + M.inner;
    const avail = vh - M.top - M.bot;
    const want = tight ? Math.min(M.n, 4) : M.n;
    const queue = pool.slice();
    // a note slots in among the first few drawings (never when it would end up half under the content)
    if (!tight) notes.forEach(n => queue.splice(Math.floor(R() * Math.min(queue.length, 3)), 0, n));
    const picked = [];
    let used = 0;
    for (const d of queue) {
      if (picked.length >= want) break;
      let w, h, vertical = false;
      if (d.note) {
        const nw = M.note[0] + (M.note[1] - M.note[0]) * R();
        [w, h] = sizeOf(d, 1);
        const k0 = nw / w;
        w *= k0; h *= k0;
        if (w > laneW) {
          if (laneW >= M.note[0] * 0.85) { const k = laneW / w; w *= k; h *= k; }
          else if (laneW >= h * 0.9) vertical = true;
          else continue;
        }
      } else {
        const px = (tight ? Math.min(M.s[0], gutter * (0.95 + 0.15 * R())) : M.s[0] + (M.s[1] - M.s[0]) * (0.2 + 0.8 * R())) * d.size;
        [w, h] = sizeOf(d, px);
        if (!tight && w > laneW) { const k = laneW / w; w *= k; h *= k; }
        if (Math.sqrt(w * h) < Math.min(M.s[0] * 0.72, 44)) continue;
      }
      const bw = vertical ? h : w, bh = vertical ? w : h;
      const need = bh + (picked.length ? M.gap : 0);
      if (used + need > avail) continue;
      used += need;
      picked.push({ d, w, h, bw, bh, vertical, r: R() });
    }
    // sky things at the top, ground things at the bottom
    picked.sort((a, b) => ZONE[a.d.zone] - ZONE[b.d.zone] || a.r - b.r);
    const n = picked.length;
    if (!n) return [];
    const spare = Math.max(0, avail - used);
    const wts = [];
    for (let i = 0; i <= n; i++) wts.push((i === 0 || i === n ? 0.3 : 1) * (0.55 + R()));
    const tw = wts.reduce((a, b) => a + b, 0);
    let y = M.top + spare * wts[0] / tw;
    let lastX = -1;
    return picked.map((p, i) => {
      let x;
      if (tight) {
        // no real margin: hug the page edge, partly off the page, faint
        const bleed = p.bw * (0.12 + 0.14 * R());
        const room = Math.max(0, gutter - (p.bw - bleed) - 2);
        x = side === 'l' ? -bleed + room * 0.5 * R() : vw - p.bw + bleed - room * 0.5 * R();
      } else {
        // zig-zag across the margin so the column does not look like a list
        const room = Math.max(0, laneW - p.bw);
        let t = 0.1 + 0.8 * R();
        if (lastX >= 0 && Math.abs(t - lastX) < 0.35) t = t < 0.5 ? Math.min(0.95, t + 0.4) : Math.max(0.05, t - 0.4);
        lastX = t;
        x = lane0 + room * t;
      }
      const d = p.d;
      let rot = (R() * 2 - 1) * (d.note ? 4 : d.tilt || 8);
      if (p.vertical) rot += side === 'l' ? -90 : 90;
      const it = { d, side, x, y, w: p.w, h: p.h, bw: p.bw, bh: p.bh, rot, op: tight ? M.op * 0.85 : M.op };
      y += p.bh + M.gap + (i < n - 1 ? spare * wts[i + 1] / tw : 0);
      return it;
    });
  }

  /** Phones (and pages with no margin to speak of): a few small, faint doodles tucked into the corners. */
  function corners(drawings, vw, vh, opts, R, big) {
    const tops = drawings.filter(d => d.zone === 'top'), bots = drawings.filter(d => d.zone === 'bottom');
    const pick = [tops[0], tops[1], bots[0], bots[1]].filter(Boolean);
    const top = opts.top == null ? (big ? 72 : 62) : opts.top, k = big ? 1.2 : 1;
    const slots = [
      { side: 'l', y: top, px: 50 * k, dx: -6 }, { side: 'r', y: top + 8, px: 46 * k, dx: -4 },
      { side: 'l', y: vh - 66 * k, px: 50 * k, dx: 6 }, { side: 'r', y: vh - 72 * k, px: 54 * k, dx: 8 }
    ];
    const n = vw < 360 ? 3 : 4;
    return pick.slice(0, n).map((d, i) => {
      const s = slots[i], [w, h] = sizeOf(d, s.px);
      const x = s.side === 'l' ? s.dx : vw - w - s.dx;
      const y = i < 2 ? s.y : Math.min(s.y, vh - h - 8);
      return { d, side: s.side, x, y, w, h, bw: w, bh: h, rot: (R() * 2 - 1) * 8, op: big ? 0.55 : 0.42 };
    });
  }

  /** Where every doodle goes for a viewport of vw x vh: [{d, side, x, y, w, h, bw, bh, rot, op}] in px. */
  function layout(vw, vh, opts) {
    opts = opts || {};
    const R = rng((opts.seed == null ? 40 : opts.seed) * 7919 + 13);
    const drawings = shuffle(DEFS.filter(d => !d.note), R);
    const notes = shuffle(DEFS.filter(d => d.note), R);
    if (vw < 700) return corners(drawings, vw, vh, opts, R, false);
    const M = Object.assign({}, vw >= 1100 ? MODES.desk : MODES.tab);
    if (opts.top != null) M.top = opts.top;
    const col = column(vw, opts);
    const gl = col[0], gr = vw - col[1];
    // the content fills the page: anything in the margins would sit under it, so keep to the corners
    if (Math.min(gl, gr) < 40) return corners(drawings, vw, vh, opts, R, true);
    const left = drawings.filter((d, i) => i % 2 === 0), right = drawings.filter((d, i) => i % 2 === 1);
    const nn = M.notes;
    return placeSide('l', left, notes.slice(0, nn), vw, vh, gl, M, R).concat(placeSide('r', right, notes.slice(nn, nn * 2), vw, vh, gr, M, R));
  }

  /* ---------------------------------------------------------------- mount */
  const state = { el: null, opts: {}, w: 0, h: 0, timer: 0, bound: false };
  const vwU = (v, vw) => (v / vw * 100).toFixed(3) + 'vw';
  const vhU = (v, vh) => (v / vh * 100).toFixed(3) + 'vh';

  function itemHtml(it, vw, vh) {
    const d = it.d;
    // box of the unrotated drawing, centred on the (rotated) footprint
    const cx = it.x + it.bw / 2, cy = it.y + it.bh / 2;
    const left = cx - it.w / 2, top = cy - it.h / 2;
    const horiz = it.side === 'l' ? `left:${vwU(left, vw)}` : `right:${vwU(vw - (left + it.w), vw)}`;
    const R = rng(d.index * 31 + 7);
    const style = `${horiz};top:${vhU(top, vh)};width:${it.w.toFixed(1)}px;height:${it.h.toFixed(1)}px;` +
      `transform:rotate(${it.rot.toFixed(2)}deg);opacity:${it.op.toFixed(2)};--bp:${(-R() * 0.45).toFixed(3)}s;--dd-d:${(-R() * 6).toFixed(2)}s`;
    return `<div class="dd dd-${d.id}${d.anim ? ' dd-a-' + d.anim : ''}" data-doodle="${d.id}" style="${style}">${render(d.id)}</div>`;
  }

  function draw() {
    const el = state.el;
    if (!el) return;
    const vw = global.innerWidth || doc.documentElement.clientWidth, vh = global.innerHeight || doc.documentElement.clientHeight;
    state.w = vw; state.h = vh;
    const items = layout(vw, vh, state.opts);
    el.innerHTML = items.map(it => itemHtml(it, vw, vh)).join('');
  }

  function onResize() {
    clearTimeout(state.timer);
    state.timer = setTimeout(() => {
      if (!state.el || !state.el.isConnected) return;
      const vw = global.innerWidth, vh = global.innerHeight;
      // phones change height while scrolling (URL bar): only re-layout on real size changes
      if (vw === state.w && Math.abs(vh - state.h) < Math.max(90, state.h * 0.15)) return;
      draw();
    }, 180);
  }

  /** Clear `container` and scatter doodles around the page margins.
      opts: { seed, content: px width of the central column, column: element/selector to measure, top: px to keep clear at the top } */
  function mount(container, opts) {
    if (typeof container === 'string') container = doc.querySelector(container);
    if (!container) return;
    injectCSS();
    state.el = container;
    state.opts = opts || {};
    container.classList.add('dd-root');
    container.setAttribute('aria-hidden', 'true');
    draw();
    if (!state.bound) { global.addEventListener('resize', onResize, { passive: true }); state.bound = true; }
  }
  /** Re-draw the mounted doodles (e.g. after switching the ink on/off or changing ink colour). */
  function refresh() { if (state.el) draw(); }
  function unmount() { if (state.el) state.el.innerHTML = ''; state.el = null; }

  global.Doodles = { list, svgs, render, mount, refresh, unmount, layout, injectCSS };
})(typeof window !== 'undefined' ? window : globalThis);
