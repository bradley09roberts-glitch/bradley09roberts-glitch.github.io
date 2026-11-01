/* Mosswood Chess: the app (board, game flow, coach panel, review, settings). */
(function () {
  'use strict';
  const E = window.MWEngine, A = window.PieceArt, C = window.Coach, Snd = window.Sound;
  const { WHITE, BLACK, PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING, mFrom, mTo, mFlags, mPromo } = E;
  const START = E.START_FEN;

  /* ---------- helpers ---------- */
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
  const pick = arr => arr[Math.floor(Math.random() * arr.length)];
  const store = {
    get(k, d) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* storage may be unavailable */ } },
    del(k) { try { localStorage.removeItem(k); } catch (e) { /* ignore */ } }
  };
  const reducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ---------- settings & profile ---------- */
  const DEFAULTS = { art: 'sketch', boil: true, ink: 'blue', pieceSet: 'creatures', sound: true, theme: 'glade', coords: true, anim: 'normal', idle: true, names: 'both', evalBar: false, danger: true, freeHL: false, guard: true, coach: 'guide', badges: false, takebacks: true, color: 'w', bot: 'pip' };
  const settings = Object.assign({}, DEFAULTS, store.get('mw.settings', {}));
  const profile = Object.assign({ rating: 400, games: 0, wins: 0, losses: 0, draws: 0, bots: {}, best: 400 }, store.get('mw.profile', {}));
  const saveSettings = () => { store.set('mw.settings', settings); applySettings(); };
  const saveProfile = () => store.set('mw.profile', profile);
  const animSpeed = () => (reducedMotion || settings.anim === 'off') ? 0 : settings.anim === 'fast' ? 1.8 : 1;
  const inkOn = () => !!(window.Ink && window.Ink.enabled);
  function setupArt() {
    if (A.setPieceSet) A.setPieceSet(settings.pieceSet);
    // reduced motion never boils, so one frame per drawing is enough (saves DOM and memory)
    if (settings.art === 'sketch' && window.Ink) { window.Ink.init({ ink: settings.ink }); window.Ink.defaultFrames = reducedMotion ? 1 : 3; window.Ink.enabled = true; }
    else if (window.Ink) window.Ink.enabled = false;
  }

  function applySettings() {
    const b = document.body;
    b.className = b.className.replace(/\b(theme-\w+|names-\w+)\b/g, '').trim();
    b.classList.add('theme-' + settings.theme, 'names-' + settings.names);
    b.classList.toggle('no-coords', !settings.coords);
    b.classList.toggle('badges', !!settings.badges);
    b.classList.toggle('no-idle', !settings.idle);
    b.classList.toggle('style-sketch', inkOn());
    b.classList.toggle('boil', inkOn() && !!settings.boil && !reducedMotion);
    b.classList.toggle('ink-black', settings.ink === 'black');
    b.classList.toggle('pieces-classic', settings.pieceSet === 'classic');
    const tc = document.querySelector('meta[name="theme-color"]');
    if (tc) tc.setAttribute('content', inkOn() ? '#fbf8ef' : '#0f1d1f');
    Snd.setEnabled(settings.sound);
    $('#btn-sound').textContent = settings.sound ? '🔊' : '🔈';
    if (G && !G.over) { $('#evalbar').hidden = !settings.evalBar; refreshDanger(); }
  }

  /* ---------- bots ---------- */
  const BOTS = [
    { id: 'pip', name: 'Pip the Sprout', short: 'Pip', elo: 250, art: 1, pal: 'w', color: '#6fb64f', style: 'Wobbly', blurb: 'Just learned how the pieces move. Very excited. Very confused.',
      cfg: { random: 0.6, captureBias: 0.6, depth: 1, q: false, noise: 160 },
      say: { hello: ['Hi hi hi! We\'re playing chess? Yay!', 'I know how ALL the pieces move. Probably.'], capture: ['Ooh, shiny! Mine!', 'Yoink!'], check: ['Boop! Is that a check?'], hurt: ['Hey! That was my friend!', 'Eep!'], win: ['I won?! I WON! Wheee!'], lose: ['Aww… good game! Again?'] } },
    { id: 'puddle', name: 'Prince Puddle', short: 'Puddle', elo: 450, art: 6, pal: 'prince', color: '#2fa37c', style: 'Impulsive', blurb: 'A young frog prince who hops before he looks.',
      cfg: { random: 0.3, captureBias: 0.5, depth: 1, q: false, noise: 90, book: 2 },
      say: { hello: ['Ribbit! A prince never backs down!'], capture: ['Hop-hop, gotcha!', 'Ribbit! Into my pond!'], check: ['Check! Did I do that right?'], hurt: ['Ribbit… that wasn\'t the plan.'], win: ['Long live Prince Puddle!'], lose: ['I\'ll practise in my pond. Well played!'] } },
    { id: 'hops', name: 'Sir Hopsworth', short: 'Hopsworth', elo: 700, art: 2, pal: 'w', color: '#3f78c9', style: 'Reckless', blurb: 'A brave hare knight who loves to attack — sometimes a little too much.',
      cfg: { random: 0.12, depth: 2, q: false, noise: 70, book: 4, mateMiss: 0.3 },
      say: { hello: ['En garde! Sir Hopsworth, at your service.'], capture: ['For the Sunmeadow!', 'A worthy prize!'], check: ['Have at thee, King!'], hurt: ['A mere flesh wound!'], win: ['Victory! Huzzah!'], lose: ['You fought with honour. I yield!'] } },
    { id: 'hoot', name: 'Madame Hoot', short: 'Hoot', elo: 950, art: 3, pal: 'rose', color: '#c0457a', style: 'Careful', blurb: 'A careful owl scholar. She rarely leaves pieces hanging.',
      cfg: { random: 0.05, depth: 2, q: true, noise: 45, book: 6, mateMiss: 0.1 },
      say: { hello: ['Hoo-hoo. Shall we think carefully today?'], capture: ['Hoo! Unprotected, I\'m afraid.'], check: ['Mind your King, dear.'], hurt: ['Hoo… I didn\'t see that one.'], win: ['A lesson well learned, I hope.'], lose: ['Splendid play! Hoo-hoo!'] } },
    { id: 'toad', name: 'Captain Toadstool', short: 'Toadstool', elo: 1200, art: 4, pal: 'w', color: '#d2483c', style: 'Solid', blurb: 'Guardian of the Mushroom Keep. Stubborn, steady and hard to break.',
      cfg: { random: 0.02, depth: 3, q: true, noise: 30, book: 8 },
      say: { hello: ['Halt! None shall pass the Mushroom Keep.'], capture: ['The Keep claims another!'], check: ['Surrender your King!'], hurt: ['Hold the walls!'], win: ['The Keep stands strong.'], lose: ['The walls have fallen… well done.'] } },
    { id: 'morwen', name: 'Morwen the Moth-Witch', short: 'Morwen', elo: 1500, art: 3, pal: 'b', color: '#6a3db0', style: 'Tricky', blurb: 'A night-owl sorceress who sets traps in the dark.',
      cfg: { depth: 4, q: true, noise: 15, book: 10, timeMs: 1500 },
      say: { hello: ['Welcome to my glade, little one. Watch your step…'], capture: ['Caught in my web.'], check: ['Can you feel the trap closing?'], hurt: ['Clever… very clever.'], win: ['The night always wins.'], lose: ['You saw through my spells. Impressive.'] } },
    { id: 'grim', name: 'Grimbleton the Toad King', short: 'Grimbleton', elo: 1800, art: 6, pal: 'b', color: '#3f6f7f', style: 'Cunning', blurb: 'Ruler of the Duskmire bog. Patient, cunning and always hungry.',
      cfg: { full: true, maxDepth: 6, timeMs: 1200, book: 12 },
      say: { hello: ['Croooak. Another snack wanders into my bog.'], capture: ['Delicious.'], check: ['Your King trembles. Croak.'], hurt: ['Grrrk! You\'ll pay for that.'], win: ['Into the bog with you!'], lose: ['Croak… you\'re no ordinary snack.'] } },
    { id: 'ember', name: 'Ember the Ancient Wyrm', short: 'Ember', elo: 2200, art: 5, pal: 'ember', color: '#d9452f', style: 'Full strength', blurb: 'The oldest dragon of the Ashpeaks. Plays at full engine strength.',
      cfg: { full: true, timeMs: 2500, book: 14 },
      say: { hello: ['So. Another challenger climbs my mountain.'], capture: ['Ash and embers.'], check: ['Feel the heat.'], hurt: ['…Interesting.'], win: ['The Wyrm remains undefeated.'], lose: ['Impossible! You are a true champion of Mosswood!'] } }
  ];
  const botById = id => BOTS.find(b => b.id === id) || BOTS[0];
  // portraits stay creatures even with classic pieces on the board (otherwise Hoot and Morwen are both bishops)
  const botArt = b => A.svg(b.art, { palette: b.pal, set: 'creatures' });

  /* ---------- engine client ---------- */
  class EngineClient {
    constructor() {
      this.seq = 0; this.pending = new Map(); this.local = null;
      try {
        this.w = new Worker('js/worker.js');
        this.w.onmessage = ev => {
          const p = this.pending.get(ev.data.id);
          if (!p) return;
          this.pending.delete(ev.data.id);
          if (ev.data.ok) p.res(ev.data.res); else p.rej(new Error(ev.data.error));
        };
        this.w.onerror = () => {
          this.w = null;
          const list = Array.from(this.pending.values()); this.pending.clear();
          for (const p of list) this.runLocal(p.msg).then(p.res, p.rej);
        };
      } catch (e) { this.w = null; }
    }
    request(msg) {
      if (this.w) {
        const id = ++this.seq;
        return new Promise((res, rej) => { this.pending.set(id, { res, rej, msg }); this.w.postMessage(Object.assign({ id }, msg)); });
      }
      return this.runLocal(msg);
    }
    async runLocal(msg) {
      await sleep(20);
      if (!this.local) this.local = new E.Searcher(18);
      const pos = E.Position.fromFen(msg.fen || START);
      for (const u of msg.moves || []) pos.make(pos.moveFromUci(u));
      const U = m => (m ? E.toUci(m) : null);
      if (msg.type === 'bot') {
        const r = E.chooseBotMove(this.local, pos, msg.cfg, msg.moves || [], (msg.fen || START) === START);
        return { move: U(r.move) };
      }
      this.local.useQ = true;
      if (msg.multi) {
        const a = this.local.analyze(pos, msg);
        return { depth: a.depth, moves: a.moves.map(e => ({ move: U(e.move), score: e.score, pv: (e.pv || []).map(U), bound: e.bound })) };
      }
      const r = this.local.think(pos, msg);
      return { depth: r.depth, move: U(r.move), score: r.score, pv: (r.pv || []).map(U) };
    }
  }
  const botEng = new EngineClient();
  const coachEng = new EngineClient();

  function pvFromUci(pos, list) {
    const q = pos.clone(), out = [];
    for (const u of list || []) { const m = q.moveFromUci(u); if (!m) break; out.push(m); q.make(m); }
    return out;
  }
  function convertAna(pos, res) {
    if (res.moves) return { multi: true, depth: res.depth, moves: res.moves.map(e => ({ move: pos.moveFromUci(e.move), score: e.score, pv: pvFromUci(pos, e.pv), bound: e.bound })).filter(e => e.move) };
    return { single: true, depth: res.depth, move: pos.moveFromUci(res.move), score: res.score, pv: pvFromUci(pos, res.pv) };
  }
  function posAt(fen, ucis, k) {
    const p = E.Position.fromFen(fen);
    for (let i = 0; i < k; i++) p.make(p.moveFromUci(ucis[i]));
    return p;
  }

  /* ---------- board view ---------- */
  const SPORES = { 0: ['#e0473b', '#fff8ea', '#f6c847', '#86c95b'], 1: ['#8a5ad0', '#86f5da', '#cbbcff', '#ffd84a'] };
  const INK_MARKS = new Set(['last', 'sel', 'dot', 'ring', 'check', 'danger', 'free', 'hint', 'ping']);
  const ARROW_COLORS = { best: '#6fbf3a', hint: '#3fc9bd', threat: '#e5484d', played: '#f6c847' };

  class BoardView {
    constructor(el) {
      this.el = el; this.flipped = false; this.pieces = new Map(); this.marks = {}; this.arrows = [];
      this.interactive = false; this.selected = -1; this.targets = []; this.onMove = null; this.side = WHITE; this.getMoves = () => [];
      el.classList.add('board');
      el.innerHTML = '<div class="b-squares"></div><div class="b-marks"></div><div class="b-pieces"></div><svg class="b-arrows" viewBox="0 0 800 800" preserveAspectRatio="none"></svg><div class="b-fx"></div><div class="b-coords"></div>';
      this.sqLayer = $('.b-squares', el); this.mLayer = $('.b-marks', el); this.pLayer = $('.b-pieces', el);
      this.aLayer = $('.b-arrows', el); this.fx = $('.b-fx', el); this.cLayer = $('.b-coords', el);
      let h = '';
      for (let row = 0; row < 8; row++) for (let col = 0; col < 8; col++) h += `<div class="s ${(row + col) % 2 ? 'd' : 'l'}"></div>`;
      this.sqLayer.innerHTML = h;
      // cycle through 8 hand-drawn variants (#board keeps 8, #rv-board 9) so new boards hit Ink's cache instead of growing it forever
      if (inkOn()) this.sqLayer.insertAdjacentHTML('beforeend', window.Ink.board({ seed: 8 + ((BoardView.count = (BoardView.count || 0) + 1) - 1) % 8 }));
      this.renderCoords();
      el.addEventListener('pointerdown', e => this.down(e));
      el.addEventListener('pointermove', e => this.move(e));
      el.addEventListener('pointerup', e => this.up(e));
      el.addEventListener('pointercancel', e => this.up(e, true));
      el.addEventListener('contextmenu', e => e.preventDefault());
    }
    colRow(sq) { const f = sq & 7, r = sq >> 4; return this.flipped ? [7 - f, r] : [f, 7 - r]; }
    tf(sq, extra) { const [c, r] = this.colRow(sq); return `translate(${c * 100}%, ${r * 100}%)${extra || ''}`; }
    sqFromPoint(x, y) {
      const rect = this.el.getBoundingClientRect();
      const col = Math.floor((x - rect.left) / rect.width * 8), row = Math.floor((y - rect.top) / rect.height * 8);
      if (col < 0 || col > 7 || row < 0 || row > 7) return -1;
      const f = this.flipped ? 7 - col : col, r = this.flipped ? row : 7 - row;
      return r * 16 + f;
    }
    renderCoords() {
      let h = '';
      for (let row = 0; row < 8; row++) {
        const rank = this.flipped ? row + 1 : 8 - row;
        h += `<span class="rk ${row % 2 === 0 ? 'on-l' : 'on-d'}" style="top:calc(${row * 12.5}% + 1px)">${rank}</span>`;
      }
      for (let col = 0; col < 8; col++) {
        const file = 'abcdefgh'[this.flipped ? 7 - col : col];
        h += `<span class="fl ${(7 + col) % 2 === 0 ? 'on-l' : 'on-d'}" style="left:calc(${(col + 1) * 12.5}% - 0.85em)">${file}</span>`;
      }
      this.cLayer.innerHTML = h;
    }
    setFlipped(v) {
      this.flipped = !!v;
      for (const [sq, el] of this.pieces) el.style.transform = this.tf(sq);
      this.renderCoords(); this.renderMarks(); this.renderArrows();
    }
    makePiece(code) {
      const d = document.createElement('div');
      d.className = 'piece'; d.dataset.code = code;
      d.style.setProperty('--bp', (-Math.random() * 0.45).toFixed(2) + 's');
      d.innerHTML = `<div class="pc-inner" style="--d:${(-Math.random() * 5).toFixed(2)}s;--bob:${(3 + Math.random() * 1.4).toFixed(2)}s">${A.svg(code)}<span class="badge-letter">${A.NAMES[code & 7].letter}</span></div>`;
      return d;
    }
    setPosition(pos, opts) {
      opts = opts || {};
      this.pos = pos;
      const want = new Map();
      for (let sq = 0; sq < 128; sq++) { if (sq & 0x88) { sq += 7; continue; } if (pos.board[sq]) want.set(sq, pos.board[sq]); }
      for (const [sq, el] of Array.from(this.pieces)) {
        if (want.get(sq) !== +el.dataset.code) { el.remove(); this.pieces.delete(sq); }
        else { el.classList.remove('in-check', 'mated', 'cheer', 'moving', 'dragging'); el.getAnimations().forEach(a => a.cancel()); el.style.transform = this.tf(sq); }
      }
      for (const [sq, code] of want) {
        if (this.pieces.has(sq)) continue;
        const el = this.makePiece(code);
        el.style.transform = this.tf(sq);
        this.pLayer.appendChild(el);
        this.pieces.set(sq, el);
        if (opts.fade && animSpeed()) el.animate([{ opacity: 0, transform: this.tf(sq, ' scale(.5)') }, { opacity: 1, transform: this.tf(sq) }], { duration: 320, easing: 'cubic-bezier(.3,1.4,.5,1)', delay: Math.random() * 160, fill: 'backwards' });
      }
    }
    animateMove(pos, m, opts) {
      opts = opts || {};
      const from = mFrom(m), to = mTo(m), fl = mFlags(m);
      const el = this.pieces.get(from);
      const speed = opts.speed === undefined ? animSpeed() : opts.speed;
      const code = pos.board[from], t = code & 7;
      let capSq = -1;
      if (fl & E.F_CAPTURE) capSq = (fl & E.F_EP) ? to + ((code >> 3) === WHITE ? -16 : 16) : to;
      const capEl = capSq >= 0 ? this.pieces.get(capSq) : null;
      const capCode = capSq >= 0 ? pos.board[capSq] : 0;
      let rf = -1, rt = -1;
      if (fl & E.F_CASTLE) { if (to > from) { rf = from + 3; rt = from + 1; } else { rf = from - 4; rt = from - 1; } }
      const rookEl = rf >= 0 ? this.pieces.get(rf) : null;
      this.pieces.delete(from);
      if (capEl) this.pieces.delete(capSq);
      if (el) this.pieces.set(to, el);
      if (rookEl) { this.pieces.delete(rf); this.pieces.set(rt, rookEl); }
      const promo = (fl & E.F_PROMO) ? ((code & 8) | mPromo(m)) : 0;
      if (!el || !speed) {
        if (el) el.style.transform = this.tf(to);
        if (capEl) capEl.remove();
        if (rookEl) rookEl.style.transform = this.tf(rt);
        if (promo) this.swapPiece(to, promo, false);
        return Promise.resolve();
      }
      el.classList.add('moving');
      const a = this.motion(el, from, to, t, speed, opts.dragged, 0);
      const waits = [a.finished.catch(() => {})];
      if (rookEl) waits.push(this.motion(rookEl, rf, rt, ROOK, speed, false, 120 / speed).finished.catch(() => {}));
      if (capEl) setTimeout(() => this.poof(capEl, capSq, capCode), a.total * 0.72);
      return Promise.all(waits).then(() => {
        el.classList.remove('moving');
        if (promo) return this.swapPiece(to, promo, true);
      });
    }
    motion(el, from, to, t, speed, dragged, delay) {
      const [c0, r0] = this.colRow(from), [c1, r1] = this.colRow(to);
      const dist = Math.hypot(c1 - c0, r1 - r0);
      const T = (c, r, x) => `translate(${c * 100}%, ${r * 100}%)${x || ''}`;
      const lerp = k => [c0 + (c1 - c0) * k, r0 + (r1 - r0) * k];
      const mc = (c0 + c1) / 2, mr = (r0 + r1) / 2;
      const dir = c1 >= c0 ? 1 : -1;
      let frames, dur;
      if (dragged) {
        const cur = el.style.transform;
        frames = [{ transform: cur }, { offset: .7, transform: T(c1, r1, ' scale(1.12,.88)') }, { transform: T(c1, r1) }];
        dur = 200;
      } else if (t === KNIGHT) {
        dur = 560;
        frames = [{ transform: T(c0, r0) }, { offset: .14, transform: T(c0, r0, ' scale(1.12,.84)') },
          { offset: .52, transform: T(mc, mr - .95 - dist * .12, ` scale(.94,1.1) rotate(${dir * 14}deg)`) },
          { offset: .86, transform: T(c1, r1, ' scale(1.16,.84)') }, { transform: T(c1, r1) }];
      } else if (t === QUEEN) {
        dur = 440 + dist * 45;
        const [a1, b1] = lerp(.18), [a2, b2] = lerp(.82);
        frames = [{ transform: T(c0, r0) }, { offset: .22, transform: T(a1, b1 - .55, ` scale(1.2) rotate(${dir * -6}deg)`) },
          { offset: .78, transform: T(a2, b2 - .55, ` scale(1.2) rotate(${dir * 6}deg)`) }, { offset: .92, transform: T(c1, r1, ' scale(1.06,.92)') }, { transform: T(c1, r1) }];
      } else if (t === ROOK) {
        dur = 300 + dist * 55;
        const [a1, b1] = lerp(.25), [a3, b3] = lerp(.75);
        frames = [{ transform: T(c0, r0) }, { offset: .25, transform: T(a1, b1, ' rotate(-4deg)') }, { offset: .5, transform: T(mc, mr, ' rotate(3deg)') },
          { offset: .75, transform: T(a3, b3, ' rotate(-2deg)') }, { offset: .92, transform: T(c1, r1, ' scale(1.06,.94)') }, { transform: T(c1, r1) }];
      } else if (t === BISHOP) {
        dur = 360 + dist * 50;
        frames = [{ transform: T(c0, r0) }, { offset: .5, transform: T(mc, mr - .32, ` scale(1.1) rotate(${dir * 9}deg)`) }, { offset: .9, transform: T(c1, r1, ' scale(1.05,.95)') }, { transform: T(c1, r1) }];
      } else if (t === KING) {
        dur = dist > 1.5 ? 560 : 460;
        frames = [{ transform: T(c0, r0) }, { offset: .18, transform: T(c0, r0, ' scale(1.14,.86)') }, { offset: .55, transform: T(mc, mr - .5, ' scale(.96,1.08)') },
          { offset: .86, transform: T(c1, r1, ' scale(1.14,.86)') }, { transform: T(c1, r1) }];
      } else {
        dur = 300 + dist * 55;
        frames = [{ transform: T(c0, r0) }, { offset: .5, transform: T(mc, mr - .3, ' scale(1.05)') }, { offset: .86, transform: T(c1, r1, ' scale(1.1,.9)') }, { transform: T(c1, r1) }];
      }
      dur = dur / speed;
      el.style.transform = T(c1, r1);
      const a = el.animate(frames, { duration: dur, easing: dragged ? 'ease-out' : 'cubic-bezier(.45,.05,.35,1)', delay: delay || 0, fill: 'backwards' });
      a.total = dur + (delay || 0);
      return a;
    }
    poof(el, sq, code) {
      const T0 = el.style.transform;
      el.style.zIndex = 15;
      el.animate([{ transform: T0, opacity: 1 }, { offset: .3, transform: T0 + ' scale(1.2) rotate(-8deg)', opacity: 1, filter: 'brightness(1.8)' }, { transform: T0 + ' scale(0) rotate(40deg)', opacity: 0 }], { duration: 380, easing: 'ease-in' })
        .finished.then(() => el.remove(), () => el.remove());
      if (inkOn()) {
        this.inkFx(sq, 'cross', 0, 650);
        this.inkFx(sq, 'poof', 230, 900);
        this.burst(sq, [window.Ink.colors.ink, window.Ink.colors.red, window.Ink.colors.ink], 9, 'spore');
      } else this.burst(sq, SPORES[code >> 3], 16, 'spore');
      const t = code & 7;
      if (t === QUEEN || t === ROOK) this.el.animate([{ transform: 'translate(0,0)' }, { transform: 'translate(-4px,2px)' }, { transform: 'translate(4px,-2px)' }, { transform: 'translate(-2px,1px)' }, { transform: 'translate(0,0)' }], { duration: 320 });
    }
    swapPiece(sq, code, animate) {
      const old = this.pieces.get(sq);
      const nw = this.makePiece(code);
      nw.style.transform = this.tf(sq);
      this.pLayer.appendChild(nw);
      this.pieces.set(sq, nw);
      if (old) old.remove();
      if (animate && animSpeed()) {
        if (inkOn()) this.inkFx(sq, 'stars', 0, 1100);
        else this.burst(sq, ['#f6c847', '#fff4b0', '#86f5da', '#ffffff'], 18, 'star');
        return nw.animate([{ transform: this.tf(sq, ' scale(0) rotate(-120deg)') }, { offset: .7, transform: this.tf(sq, ' scale(1.35) rotate(12deg)') }, { transform: this.tf(sq) }], { duration: 650, easing: 'ease-out' }).finished.catch(() => {});
      }
      return Promise.resolve();
    }
    inkFx(sq, kind, delay, life, color) {
      if (!inkOn() || !animSpeed()) return;
      const [c, r] = this.colRow(sq);
      const d = document.createElement('div');
      d.className = 'ink-fx ink-fx-' + kind;
      d.style.left = c * 12.5 + '%'; d.style.top = r * 12.5 + '%';
      d.innerHTML = window.Ink.fx(kind, sq * 7 + Math.floor(Math.random() * 5), color ? { color } : undefined);
      setTimeout(() => {
        this.fx.appendChild(d);
        d.animate([{ opacity: 1 }, { opacity: 1, offset: .65 }, { opacity: 0 }], { duration: life || 800 }).finished.then(() => d.remove(), () => d.remove());
      }, delay || 0);
    }
    burst(sq, colors, n, kind) {
      if (!animSpeed()) return;
      if (inkOn() && kind === 'star') { this.inkFx(sq, 'stars', 0, 1100, colors && colors[0] === '#2ec4b6' ? 'green' : undefined); return; }
      const [c, r] = this.colRow(sq);
      const W = this.el.clientWidth / 8;
      for (let i = 0; i < n; i++) {
        const s = document.createElement('i');
        s.className = 'spark' + (kind === 'star' ? ' star' : kind === 'leaf' ? ' leaf' : '');
        s.style.left = (c + .5) * 12.5 + '%'; s.style.top = (r + .5) * 12.5 + '%';
        s.style.setProperty('--c', colors[i % colors.length]);
        this.fx.appendChild(s);
        const ang = Math.random() * Math.PI * 2, d = W * (.45 + Math.random() * .75);
        const dx = Math.cos(ang) * d, dy = Math.sin(ang) * d - W * .25;
        s.animate([{ transform: 'translate(0,0) scale(1)', opacity: 1 }, { transform: `translate(${dx}px,${dy}px) scale(.25) rotate(${Math.random() * 360}deg)`, opacity: 0 }], { duration: 550 + Math.random() * 450, easing: 'cubic-bezier(.2,.8,.3,1)' })
          .finished.then(() => s.remove(), () => s.remove());
      }
      const ring = document.createElement('i');
      ring.className = 'ring-fx';
      ring.style.left = c * 12.5 + '%'; ring.style.top = r * 12.5 + '%';
      ring.style.setProperty('--c', colors[0]);
      this.fx.appendChild(ring);
      ring.animate([{ transform: 'scale(.3)', opacity: 1 }, { transform: 'scale(1.7)', opacity: 0 }], { duration: 520, easing: 'ease-out' }).finished.then(() => ring.remove(), () => ring.remove());
    }
    setMarks(layer, list) { this.marks[layer] = list || []; this.renderMarks(); }
    clearMarks(...layers) { for (const l of layers) this.marks[l] = []; this.renderMarks(); }
    renderMarks() {
      let h = '';
      for (const k of ['last', 'check', 'danger', 'free', 'coach', 'sel', 'targets', 'hint', 'ping', 'badge']) {
        for (const m of (this.marks[k] || [])) {
          const [c, r] = this.colRow(m.sq);
          let inner = m.html || '';
          if (inkOn()) {
            if (m.kind === 'badge' && m.sym) inner = window.Ink.badge(m.sym, m.cls || 'ink');
            else if (!m.html && INK_MARKS.has(m.kind)) inner = window.Ink.mark(m.kind, m.sq + 17 * c);
          }
          h += `<div class="mk ${m.kind}" style="left:${c * 12.5}%;top:${r * 12.5}%;${m.color ? '--c:' + m.color : ''}">${inner}</div>`;
        }
      }
      this.mLayer.innerHTML = h;
    }
    ping(sq) {
      this.setMarks('ping', [{ sq, kind: 'ping' }]);
      clearTimeout(this.pingT);
      this.pingT = setTimeout(() => this.setMarks('ping', []), 1400);
    }
    setArrows(list) { this.arrows = list || []; this.renderArrows(); }
    renderArrows() {
      let body = '';
      if (inkOn()) {
        for (const a of this.arrows) {
          const [c0, r0] = this.colRow(a.from), [c1, r1] = this.colRow(a.to);
          body += window.Ink.arrow(c0 * 100 + 50, r0 * 100 + 50, c1 * 100 + 50, r1 * 100 + 50, { color: a.kind === 'hint' ? 'green' : (a.kind || 'ink'), dashed: !!a.dashed, seed: a.from * 128 + a.to, trim: [22, 12] });
        }
        this.aLayer.innerHTML = body;
        return;
      }
      for (const a of this.arrows) {
        const color = a.color || ARROW_COLORS[a.kind] || (A.CLASS_INFO[a.kind] && A.CLASS_INFO[a.kind].color) || '#f6c847';
        const [c0, r0] = this.colRow(a.from), [c1, r1] = this.colRow(a.to);
        const x0 = c0 * 100 + 50, y0 = r0 * 100 + 50, x1 = c1 * 100 + 50, y1 = r1 * 100 + 50;
        const ang = Math.atan2(y1 - y0, x1 - x0);
        const sx = x0 + Math.cos(ang) * 20, sy = y0 + Math.sin(ang) * 20;
        const ex = x1 - Math.cos(ang) * 30, ey = y1 - Math.sin(ang) * 30;
        const tipX = x1 - Math.cos(ang) * 4, tipY = y1 - Math.sin(ang) * 4;
        const px = Math.cos(ang + Math.PI / 2) * 24, py = Math.sin(ang + Math.PI / 2) * 24;
        body += `<g class="arr" opacity="${a.dashed ? .78 : .86}"><line x1="${sx}" y1="${sy}" x2="${ex}" y2="${ey}" stroke="${color}" stroke-width="15" stroke-linecap="round"${a.dashed ? ' stroke-dasharray="20 14"' : ''}/><polygon points="${tipX},${tipY} ${ex + px},${ey + py} ${ex - px},${ey - py}" fill="${color}"/></g>`;
      }
      this.aLayer.innerHTML = body;
    }
    setInteractive(on, opts) {
      this.interactive = !!on;
      this.el.classList.toggle('interactive', this.interactive);
      if (opts) { this.side = opts.side; this.getMoves = opts.getMoves; }
      this.select(-1);
    }
    select(sq) {
      this.selected = sq;
      this.targets = sq >= 0 ? this.getMoves(sq) : [];
      this.setMarks('sel', sq >= 0 ? [{ sq, kind: 'sel' }] : []);
      const seen = new Set(), tg = [];
      for (const t of this.targets) { if (seen.has(t.to)) continue; seen.add(t.to); tg.push({ sq: t.to, kind: (this.pos.board[t.to] || t.ep) ? 'ring' : 'dot' }); }
      this.setMarks('targets', tg);
    }
    down(e) {
      if (!this.interactive || e.button > 0) return;
      Snd.unlock();
      const sq = this.sqFromPoint(e.clientX, e.clientY);
      if (sq < 0) return;
      if (this.selected >= 0 && this.targets.some(t => t.to === sq)) { e.preventDefault(); this.tryMove(this.selected, sq, false); return; }
      const p = this.pos.board[sq];
      if (p && (p >> 3) === this.side) {
        e.preventDefault();
        if (this.selected === sq) { this.select(-1); return; }
        this.select(sq);
        const el = this.pieces.get(sq);
        if (el && this.targets.length) {
          this.drag = { sq, x: e.clientX, y: e.clientY, el, moved: false, id: e.pointerId };
          try { this.el.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ }
        }
        Snd.play('click');
        return;
      }
      this.select(-1);
    }
    move(e) {
      const d = this.drag;
      if (!d || e.pointerId !== d.id) return;
      if (!d.moved && Math.hypot(e.clientX - d.x, e.clientY - d.y) < 6) return;
      if (!d.moved) { d.moved = true; d.el.classList.add('dragging'); }
      const rect = this.el.getBoundingClientRect(), s = rect.width / 8;
      const cx = (e.clientX - rect.left) / s - .5, cy = (e.clientY - rect.top) / s - .5;
      d.el.style.transform = `translate(${cx * 100}%, ${cy * 100}%)`;
    }
    up(e, cancel) {
      const d = this.drag;
      if (!d || e.pointerId !== d.id) return;
      this.drag = null;
      if (!d.moved) return;
      d.el.classList.remove('dragging');
      const sq = cancel ? -1 : this.sqFromPoint(e.clientX, e.clientY);
      if (sq >= 0 && sq !== d.sq && this.targets.some(t => t.to === sq)) { this.tryMove(d.sq, sq, true); return; }
      const cur = d.el.style.transform;
      d.el.style.transform = this.tf(d.sq);
      d.el.animate([{ transform: cur }, { transform: this.tf(d.sq) }], { duration: 180, easing: 'ease-out' });
    }
    async tryMove(from, to, dragged) {
      const cands = this.targets.filter(t => t.to === to);
      if (!cands.length) return;
      let m = cands[0].m;
      if (cands.length > 1) {
        m = await this.pickPromotion(to, cands);
        if (!m) {
          const el = this.pieces.get(from);
          if (el) el.style.transform = this.tf(from);
          this.select(-1);
          return;
        }
      }
      this.select(-1);
      if (this.onMove) this.onMove(m, { dragged });
    }
    pickPromotion(to, cands) {
      return new Promise(resolve => {
        const [c, r] = this.colRow(to);
        const side = this.side << 3;
        const box = document.createElement('div');
        box.className = 'promo-pick';
        box.style.left = c * 12.5 + '%';
        if (r === 0) box.style.top = '0'; else box.style.bottom = '0';
        const order = [QUEEN, KNIGHT, ROOK, BISHOP];
        box.innerHTML = order.map(t => `<button data-t="${t}" title="${A.NAMES[t].f} (${A.NAMES[t].c})">${A.svg(side | t)}</button>`).join('');
        const done = v => { box.remove(); this.el.removeEventListener('pointerdown', outside, true); resolve(v); };
        const outside = ev => { if (!box.contains(ev.target)) { ev.stopPropagation(); done(0); } };
        box.addEventListener('pointerdown', ev => ev.stopPropagation());
        box.querySelectorAll('button').forEach(b => b.addEventListener('click', ev => {
          ev.stopPropagation();
          const t = +b.dataset.t;
          done((cands.find(x => mPromo(x.m) === t) || cands[0]).m);
        }));
        this.el.appendChild(box);
        setTimeout(() => this.el.addEventListener('pointerdown', outside, true), 0);
      });
    }
  }

  /* ---------- fx outside the board ---------- */
  function confetti(n) {
    if (!animSpeed()) return;
    const colors = ['#f6c847', '#7cc35a', '#e0473b', '#86f5da', '#c0b3ff', '#fff4de'];
    for (let i = 0; i < (n || 70); i++) {
      const d = document.createElement('div');
      d.className = 'confetti';
      d.style.left = Math.random() * 100 + 'vw';
      d.style.background = pick(colors);
      d.style.borderRadius = Math.random() < .4 ? '50%' : '2px';
      document.body.appendChild(d);
      const x = (Math.random() - .5) * 200, rot = Math.random() * 900;
      d.animate([{ transform: 'translate(0,0) rotate(0)', opacity: 1 }, { transform: `translate(${x}px, ${window.innerHeight + 60}px) rotate(${rot}deg)`, opacity: .9 }],
        { duration: 2200 + Math.random() * 1800, delay: Math.random() * 600, easing: 'cubic-bezier(.25,.6,.4,1)' }).finished.then(() => d.remove(), () => d.remove());
    }
  }
  let toastT = 0;
  function toast(msg, ms) {
    const t = $('#toast');
    t.innerHTML = msg; t.hidden = false;
    clearTimeout(toastT);
    toastT = setTimeout(() => { t.hidden = true; }, ms || 2400);
  }
  function modal(html, opts) {
    opts = opts || {};
    const m = $('#modal'), card = $('#modal-card');
    card.innerHTML = (opts.close === false ? '' : '<button class="x-btn" data-close aria-label="Close">×</button>') + html;
    m.hidden = false;
    const close = () => { m.hidden = true; card.innerHTML = ''; if (opts.onClose) opts.onClose(); };
    card.querySelectorAll('[data-close]').forEach(b => b.addEventListener('click', close));
    m.onclick = e => { if (e.target === m && opts.close !== false) close(); };
    return { card, close };
  }

  /* ---------- coach panel ---------- */
  const COACH_SPOTS = {
    game: { feed: () => $('#coach-feed'), av: () => $('#coach-av'), board: () => board },
    review: { feed: () => $('#rv-coach-feed'), av: () => $('#rv-coach-av'), board: () => rvBoard }
  };
  // colour emoji get class "emo" so the Sketchbook skin can ink them (★ ✓ → ?! are not pictographic and stay as text)
  const emo = s => /\p{Extended_Pictographic}/u.test(s) ? `<span class="emo">${s}</span>` : s;
  function clsPill(cls) {
    const ci = A.CLASS_INFO[cls];
    return ci ? `<span class="cls-pill" style="--c:${ci.color}"><i>${emo(ci.sym)}</i>${ci.label}</span>` : '';
  }
  function coachSay(msg, spot) {
    const S = typeof spot === 'object' ? spot : COACH_SPOTS[spot || 'game'];
    const feed = S.feed(), av = S.av();
    if (!feed) return null;
    if (!msg.append) feed.innerHTML = '';
    else {
      $$('.bubble', feed).forEach(b => b.classList.add('old'));
      while (feed.children.length >= 2) feed.lastElementChild.remove();
    }
    const b = document.createElement('div');
    b.className = 'bubble ' + (msg.kind || '');
    let h = '';
    if (msg.title || msg.cls) h += `<div class="bh">${msg.cls ? clsPill(msg.cls) : ''}${msg.title ? `<b class="t">${msg.title}</b>` : ''}</div>`;
    const acts = msg.actions && msg.actions.length ? `<div class="acts${msg.actionsTop ? ' top' : ''}">${msg.actions.map((a, i) => `<button class="btn small ${a.ghost ? 'ghost' : ''}" data-a="${i}">${a.label}</button>`).join('')}</div>` : '';
    if (msg.actionsTop) h += acts;
    if (msg.html) h += `<div class="bt">${msg.html}</div>`;
    if (msg.better) h += `<div class="better">${msg.better}</div>`;
    if (msg.lesson) h += `<div class="lesson">${msg.lesson}</div>`;
    if (!msg.actionsTop) h += acts;
    b.innerHTML = h;
    if (msg.actions) b.querySelectorAll('[data-a]').forEach(btn => btn.addEventListener('click', () => { Snd.play('click'); msg.actions[+btn.dataset.a].fn(btn); }));
    b.querySelectorAll('b.sq').forEach(x => {
      const go = () => { const bd = S.board && S.board(); if (bd) bd.ping(E.sqFromName(x.dataset.sq)); };
      x.addEventListener('mouseenter', go); x.addEventListener('click', go);
    });
    feed.prepend(b);
    if (av) {
      av.innerHTML = A.coach(msg.mood || 'happy');
      av.classList.remove('talk', 'busy'); void av.offsetWidth; av.classList.add('talk');
    }
    if (msg.sound) Snd.play(msg.sound);
    return b;
  }
  function coachBusy(on, spot) {
    const av = (COACH_SPOTS[spot || 'game']).av();
    if (!av) return;
    if (on) { av.innerHTML = A.coach('thinking'); av.classList.add('busy'); }
    else av.classList.remove('busy');
  }
  function judgeArrows(list) {
    return (list || []).filter(a => a.kind === 'best' || a.kind === 'threat' || a.kind === 'hint').map(a => ({ from: mFrom(a.m), to: mTo(a.m), kind: a.kind, dashed: a.kind === 'threat' }));
  }

  /* ---------- world decor ---------- */
  function initWorld() {
    const flies = $('.w-flies');
    let h = '';
    const n = window.innerWidth < 600 ? 10 : 18;
    for (let i = 0; i < n; i++) {
      h += `<i class="fly" style="left:${(Math.random() * 100).toFixed(1)}%;top:${(35 + Math.random() * 60).toFixed(1)}%;--dx:${(Math.random() * 120 - 60).toFixed(0)}px;--dy:${(-30 - Math.random() * 80).toFixed(0)}px;--t:${(9 + Math.random() * 10).toFixed(1)}s;--dl:${(-Math.random() * 15).toFixed(1)}s"></i>`;
    }
    flies.innerHTML = h;
    $('.brand-ico').innerHTML = A.svg(1);
    $('#hero-art').innerHTML = `<div class="ha ha3">${A.svg(13)}</div><div class="ha ha1">${A.svg(6)}</div><div class="ha ha2">${A.svg(10)}</div>`;
    $$('.seg-ico[data-ico]').forEach(e => { e.innerHTML = A.svg(+e.dataset.ico); });
  }

  /* ---------- navigation ---------- */
  let current = 'home';
  function show(name) {
    if (name === 'game' && !G) name = 'home';
    current = name;
    $$('.screen').forEach(s => s.classList.toggle('on', s.id === 'scr-' + name));
    const navName = name === 'game' || name === 'review' ? 'home' : name;
    $$('.topnav button').forEach(b => b.classList.toggle('on', b.dataset.go === navName));
    if (name === 'home') renderHome();
    if ((name === 'learn' || name === 'trials') && window.MW.learn) window.MW.learn.open(name);
    window.scrollTo(0, 0);
  }

  /* ---------- home ---------- */
  let selectedBot = settings.bot;
  function renderHome() {
    const totalG = profile.wins + profile.losses + profile.draws;
    $('#profile-card').innerHTML = `<div class="prof-av">${A.svg(6)}</div>
      <div class="prof-main"><h3>Your rating</h3><div class="prof-rating">${Math.round(profile.rating)}<small>${profile.games < 10 ? 'provisional' : 'Mosswood rating'}</small></div>
      <div class="prof-rec">${totalG ? `${profile.wins} wins · ${profile.losses} losses · ${profile.draws} draws` : 'No battles yet — Pip is waiting!'}</div></div>`;
    // bots
    $('#bots').innerHTML = BOTS.map(b => {
      const r = profile.bots[b.id] || { w: 0, l: 0, d: 0 };
      return `<button class="bot-card ${b.id === selectedBot ? 'on' : ''}" data-bot="${b.id}" style="--bc:${b.color}">
        ${r.w ? '<span class="bc-crown" title="Defeated!">👑</span>' : ''}${r.w + r.l + r.d ? `<span class="bc-rec">${r.w}-${r.l}${r.d ? '-' + r.d : ''}</span>` : ''}
        <div class="bc-av">${botArt(b)}</div><div class="bc-name">${esc(b.name)}</div><div class="bc-elo">≈ ${b.elo}</div><div class="bc-style">${esc(b.style)}</div></button>`;
    }).join('') + `<div class="bot-detail" id="bot-detail" style="grid-column:1/-1"></div>`;
    $$('.bot-card').forEach(c => c.addEventListener('click', () => { selectedBot = c.dataset.bot; settings.bot = selectedBot; saveSettings(); Snd.play('click'); renderHome(); }));
    const sb = botById(selectedBot);
    $('#bot-detail').innerHTML = `<div class="bd-av">${botArt(sb)}</div><div><b>${esc(sb.name)}</b> <span class="muted small">≈ ${sb.elo}</span><p>${esc(sb.blurb)}</p></div>`;
    // setup segs
    $$('#seg-color button').forEach(b => b.classList.toggle('on', b.dataset.v === settings.color));
    $$('#seg-coach button').forEach(b => b.classList.toggle('on', b.dataset.v === settings.coach));
    // resume
    const cur = store.get('mw.current', null);
    const rc = $('#resume-card');
    if (cur && cur.ucis && (!G || G.over)) {
      const b = botById(cur.botId);
      rc.hidden = false;
      rc.innerHTML = `<div class="resume"><div class="bd-av">${botArt(b)}</div><div><b>Unfinished battle</b><br><span class="muted small">vs ${esc(b.name)} · move ${Math.floor(cur.ucis.length / 2) + 1}</span></div><button class="btn small" id="btn-resume">Continue</button></div>`;
      $('#btn-resume').onclick = () => resumeGame(cur);
    } else if (G && !G.over) {
      rc.hidden = false;
      rc.innerHTML = `<div class="resume"><div class="bd-av">${botArt(G.bot)}</div><div><b>Battle in progress</b><br><span class="muted small">vs ${esc(G.bot.name)}</span></div><button class="btn small" id="btn-back">Return</button></div>`;
      $('#btn-back').onclick = () => show('game');
    } else rc.hidden = true;
    // history
    const hist = store.get('mw.history', []);
    $('#history-card').innerHTML = `<h2 class="ph">Battle chronicle</h2>` + (hist.length ? `<div class="hist-list">${hist.slice(0, 8).map((g, i) => {
      const b = botById(g.botId);
      return `<div class="hist-item"><div class="hi-av">${botArt(b)}</div><div class="hi-main"><span class="res ${g.res}">${g.res === 'win' ? 'Won' : g.res === 'loss' ? 'Lost' : 'Draw'}</span> vs ${esc(b.short)}<br><span class="muted small">${esc(g.reasonText || '')} · ${Math.ceil(g.ucis.length / 2)} moves</span></div><button class="btn small ghost" data-rv="${i}">Review</button></div>`;
    }).join('')}</div>` : '<p class="muted small">Your finished games will be listed here so you can review them with the coach.</p>');
    $$('[data-rv]').forEach(b => b.addEventListener('click', () => {
      const g = hist[+b.dataset.rv];
      openReview({ startFen: g.startFen || START, ucis: g.ucis, player: g.player, botId: g.botId, result: g.result, reasonText: g.reasonText, res: g.res });
    }));
  }

  /* ---------- game ---------- */
  let board = null, rvBoard = null;
  let G = null;
  let TOKEN = 0;

  function newGameState(bot, player, ucis) {
    const pos = E.Position.fromFen(START);
    const g = {
      token: ++TOKEN, bot, player, startFen: START, pos, plies: [], over: null, analyses: {}, hintLevel: 0, hinted: {},
      hintsUsed: 0, takebacks: 0, tips: new Set(), coach: settings.coach, lastEval: null
    };
    for (const u of ucis || []) {
      const m = pos.moveFromUci(u);
      if (!m) break;
      const san = pos.san(m);
      g.plies.push({ uci: u, san, color: pos.side, moveNo: pos.full });
      pos.make(m);
    }
    return g;
  }

  function startGame(botId, colorChoice) {
    const bot = botById(botId);
    let player = colorChoice === 'b' ? BLACK : colorChoice === 'w' ? WHITE : (Math.random() < .5 ? WHITE : BLACK);
    G = newGameState(bot, player, []);
    setupGameScreen();
    board.setPosition(G.pos, { fade: true });
    show('game');
    const intro = settings.coach === 'off'
      ? 'I\'ll stay quiet this game. When it\'s over, I\'ll walk you through every important moment.'
      : settings.coach === 'light'
        ? 'I\'ll speak up for the big moments. Tap <b>Hint</b> if you\'re stuck!'
        : `Welcome to the battle! I'll comment on your moves, warn you about danger, and let you take back mistakes. Tap <b>Hint</b> anytime.${player === BLACK ? ' You command the Duskmire army, so the enemy moves first.' : ''}`;
    coachSay({ mood: 'happy', title: `You vs ${esc(bot.name)}`, html: intro });
    setTimeout(() => botSay('hello'), 600);
    saveCurrent();
    beginTurn();
  }

  function resumeGame(cur) {
    const bot = botById(cur.botId);
    G = newGameState(bot, cur.player, cur.ucis);
    // only the move-list badges need the verdict class; the review recomputes full judgements
    (cur.cls || []).forEach((c, i) => { if (c && G.plies[i]) G.plies[i].judge = { cls: c }; });
    G.coach = cur.coach || settings.coach;
    G.hintsUsed = cur.hintsUsed || 0; G.takebacks = cur.takebacks || 0;
    setupGameScreen();
    board.setPosition(G.pos, { fade: true });
    show('game');
    coachSay({ mood: 'happy', title: 'Welcome back!', html: `Let's continue your battle against ${esc(bot.name)}.` });
    refreshAfterMove();
    beginTurn();
  }

  function setupGameScreen() {
    C.setPerspective(G.player);
    board.setFlipped(G.player === BLACK);
    board.clearMarks('last', 'check', 'danger', 'free', 'coach', 'sel', 'targets', 'hint', 'badge');
    board.setArrows([]);
    $('#evalbar').hidden = !settings.evalBar;
    $('#evalbar').classList.toggle('flipped', G.player === BLACK);
    $('#t-danger').classList.toggle('on', !!settings.danger);
    renderBars(); renderMoveList();
  }

  function saveCurrent() {
    if (!G || G.over) { store.del('mw.current'); return; }
    store.set('mw.current', { botId: G.bot.id, player: G.player, ucis: G.plies.map(p => p.uci), cls: G.plies.map(p => p.judge ? p.judge.cls : 0), coach: G.coach, hintsUsed: G.hintsUsed, takebacks: G.takebacks });
  }

  function capturedBy(pos) {
    const full = { 1: 8, 2: 2, 3: 2, 4: 2, 5: 1 };
    const cnt = [{}, {}];
    for (let sq = 0; sq < 128; sq++) { if (sq & 0x88) { sq += 7; continue; } const p = pos.board[sq]; if (p && (p & 7) !== KING) { const c = p >> 3; cnt[c][p & 7] = (cnt[c][p & 7] || 0) + 1; } }
    const res = [[], []];
    for (const c of [0, 1]) for (const t of [5, 4, 3, 2, 1]) { const miss = Math.max(0, full[t] - (cnt[c][t] || 0)); for (let i = 0; i < miss; i++) res[c ^ 1].push((c << 3) | t); }
    return res; // res[side] = pieces captured BY side
  }

  function renderBars() {
    if (!G) return;
    const topColor = board.flipped ? WHITE : BLACK;
    const caps = capturedBy(G.pos);
    const mat = C.balance(G.pos, WHITE);
    for (const [sel, color] of [['#bar-top', topColor], ['#bar-bot', topColor ^ 1]]) {
      const el = $(sel);
      const isYou = color === G.player;
      const adv = color === WHITE ? mat : -mat;
      const name = isYou ? 'You' : G.bot.name;
      const sub = isYou ? `${color === WHITE ? 'Sunmeadow' : 'Duskmire'} army · ≈ ${Math.round(profile.rating)}` : `${color === WHITE ? 'Sunmeadow' : 'Duskmire'} army · ≈ ${G.bot.elo}`;
      const av = isYou ? A.svg((color << 3) | KING) : botArt(G.bot);
      const capsHtml = caps[color].map(c => `<span class="cap">${A.mini(c)}</span>`).join('') + (adv > 0 ? `<span class="adv">+${adv}</span>` : '');
      el.style.setProperty('--bc', isYou ? '#2f5a4a' : G.bot.color);
      el.innerHTML = `<div class="pb-av">${av}</div><div class="pb-info"><div class="pb-name">${esc(name)}</div><div class="pb-sub">${sub}</div></div><div class="pb-think"><i></i><i></i><i></i></div><div class="pb-caps">${capsHtml}</div><div class="pb-say"></div>`;
      el.dataset.color = color;
      el.classList.toggle('turn', !G.over && G.pos.side === color);
    }
  }

  function figSan(san, color) {
    const m = /^([NBRQK])(.*)$/.exec(san);
    if (!m) return esc(san);
    return A.mini((color << 3) | (' PNBRQK'.indexOf(m[1]))) + esc(m[2]);
  }

  function renderMoveList() {
    const el = $('#moves');
    let h = '';
    G.plies.forEach((p, i) => {
      if (p.color === WHITE || i === 0) h += `<span class="no">${p.moveNo}.</span>`;
      if (i === 0 && p.color === BLACK) h += '<span class="mv">…</span>';
      const j = p.judge, ci = j && A.CLASS_INFO[j.cls];
      const showCi = ci && G.coach !== 'off' && p.color === G.player && !['good', 'excellent', 'best', 'book', 'forced'].includes(j.cls);
      h += `<span class="mv ${i === G.plies.length - 1 ? 'cur' : ''}">${figSan(p.san, p.color)}${showCi ? `<span class="ci" style="--c:${ci.color}">${emo(ci.sym)}</span>` : ''}</span>`;
    });
    el.innerHTML = h || '<span class="muted small" style="grid-column:1/-1">No moves yet.</span>';
    el.scrollTop = el.scrollHeight;
    const name = E.openingName(G.plies.map(p => p.uci));
    $('#opening-name').textContent = name || '';
  }

  function botSay(kind, chance) {
    if (!G || (chance !== undefined && Math.random() > chance)) return;
    const lines = G.bot.say[kind];
    if (!lines) return;
    const bar = $$('.pbar', $('#scr-game')).find(b => +b.dataset.color !== G.player);
    const say = bar && $('.pb-say', bar);
    if (!say) return;
    say.textContent = pick(lines);
    say.classList.add('show');
    clearTimeout(say._t);
    say._t = setTimeout(() => say.classList.remove('show'), 2800);
  }

  function setThinking(on) {
    const bar = $$('.pbar', $('#scr-game')).find(b => +b.dataset.color !== (G && G.player));
    if (bar) bar.classList.toggle('thinking', on);
  }

  function updateEvalBar(el, cpWhite, flipped) {
    const fill = $('.eb-fill', el), lab = $('.eb-label', el);
    const w = C.wp(cpWhite === null || cpWhite === undefined ? 0 : cpWhite);
    fill.style.height = (w * 100).toFixed(1) + '%';
    el.classList.toggle('flipped', !!flipped);
    lab.textContent = cpWhite === null || cpWhite === undefined ? '' : C.evalLabel(Math.abs(cpWhite)).replace('+', '');
    lab.style.color = (cpWhite || 0) >= 0 ? '#2b1a0c' : '#f4ead2';
    lab.style.bottom = ((cpWhite || 0) >= 0) !== !!flipped ? '4px' : 'auto';
    lab.style.top = ((cpWhite || 0) >= 0) !== !!flipped ? 'auto' : '4px';
  }

  function refreshAfterMove() {
    const pos = G.pos;
    const last = G.plies[G.plies.length - 1];
    if (last) {
      const f = E.sqFromName(last.uci.slice(0, 2)), t = E.sqFromName(last.uci.slice(2, 4));
      board.setMarks('last', [{ sq: f, kind: 'last' }, { sq: t, kind: 'last' }]);
    } else board.setMarks('last', []);
    for (const el of board.pieces.values()) el.classList.remove('in-check');
    if (pos.inCheck()) {
      const k = pos.kings[pos.side];
      board.setMarks('check', [{ sq: k, kind: 'check' }]);
      const kel = board.pieces.get(k);
      if (kel) kel.classList.add('in-check');
    } else board.setMarks('check', []);
    renderBars(); renderMoveList(); saveCurrent();
  }

  function refreshDanger() {
    if (!G || G.over || G.pos.side !== G.player) { board.setMarks('danger', []); board.setMarks('free', []); return; }
    $('#t-danger').classList.toggle('on', !!settings.danger);
    board.setMarks('danger', settings.danger ? C.hanging(G.pos, G.player, 100).map(h => ({ sq: h.sq, kind: 'danger' })) : []);
    board.setMarks('free', settings.freeHL ? C.hanging(G.pos, G.player ^ 1, 100).map(h => ({ sq: h.sq, kind: 'free' })) : []);
  }

  function ensureAnalysis(ply) {
    if (G.analyses[ply]) return G.analyses[ply];
    const pos = G.pos.clone();
    const tok = G.token;
    const p = coachEng.request({ type: 'analyze', multi: true, fen: G.startFen, moves: G.plies.slice(0, ply).map(x => x.uci), timeMs: 900, minDepth: 3, maxDepth: 14 })
      .then(res => convertAna(pos, res))
      .then(a => {
        if (G && G.token === tok && a.moves[0] && G.plies.length === ply) {
          G.lastEval = (pos.side === WHITE ? 1 : -1) * a.moves[0].score;
          if (settings.evalBar) updateEvalBar($('#evalbar'), G.lastEval, G.player === BLACK);
        }
        return a;
      });
    p.catch(() => {});
    G.analyses[ply] = p;
    return p;
  }

  async function beginTurn() {
    if (!G || G.over) return;
    // a new look was chosen while the blunder guard was open: reload now that the player has answered it
    if (G.reloadAfterGuard) { const reload = G.reloadAfterGuard; G.reloadAfterGuard = null; reload(); return; }
    const tok = G.token;
    const st = G.pos.status();
    if (st.over) return endGame(st);
    renderBars();
    updateTools();
    if (G.pos.side === G.player) {
      G.hintLevel = 0;
      board.setMarks('hint', []);
      ensureAnalysis(G.plies.length);
      const legal = st.legal;
      board.setInteractive(true, {
        side: G.player,
        getMoves: from => legal.filter(m => mFrom(m) === from).map(m => ({ to: mTo(m), m, ep: !!(mFlags(m) & E.F_EP) }))
      });
      board.onMove = (m, info) => onUserMove(m, info, tok);
      refreshDanger();
    } else {
      board.setInteractive(false);
      board.setMarks('danger', []); board.setMarks('free', []);
      await botTurn(tok);
    }
  }

  function updateTools() {
    const mine = G && !G.over && G.pos.side === G.player;
    $('#t-hint').disabled = !mine;
    $('#t-undo').disabled = !G || G.over || !settings.takebacks || !G.plies.some(p => p.color === G.player);
    $('#t-resign').disabled = !G || G.over;
  }

  function playMoveSound(pos, m) {
    const fl = mFlags(m), t = pos.board[mFrom(m)] & 7;
    const ink = inkOn();
    if (fl & E.F_CASTLE) Snd.play('castle');
    else if (fl & E.F_PROMO) Snd.play('promote');
    else if (fl & E.F_CAPTURE) Snd.play(ink ? 'scribble' : 'capture');
    else if (t === KNIGHT || t === KING) Snd.play('hop');
    else Snd.play(ink ? 'pen' : 'move');
  }

  async function applyMove(m, opts) {
    const pos = G.pos;
    const san = pos.san(m);
    const p = { uci: E.toUci(m), san, color: pos.side, moveNo: pos.full };
    board.setArrows([]);
    board.clearMarks('coach', 'hint', 'danger', 'free', 'sel', 'targets');
    for (const el of board.pieces.values()) el.classList.remove('in-check');
    board.setMarks('check', []);
    setTimeout(() => playMoveSound(pos, m), opts && opts.dragged ? 0 : 140);
    const before = pos.clone();
    const anim = board.animateMove(before, m, { dragged: opts && opts.dragged });
    pos.make(m);
    G.plies.push(p);
    await anim;
    board.setPosition(pos);
    if (pos.inCheck()) Snd.play('check');
    refreshAfterMove();
    return { before, ply: G.plies.length - 1 };
  }

  async function onUserMove(m, info, tok) {
    if (!G || G.token !== tok || G.over || G.pos.side !== G.player) return;
    board.setInteractive(false);
    const ply = G.plies.length;
    const anaP = G.analyses[ply];
    const { before } = await applyMove(m, info);
    if (G.token !== tok) return;
    if (G.hinted[ply]) G.plies[ply].hinted = G.hinted[ply];
    const st = G.pos.status();
    let verdict = null;
    if (G.coach !== 'off' && anaP) {
      coachBusy(true);
      try {
        const ana = await anaP;
        if (G.token !== tok) return;
        verdict = judgeLive(before, m, ana, ply);
        G.plies[ply].judge = verdict;
      } catch (e) { verdict = null; }
      coachBusy(false);
    }
    renderMoveList();
    if (verdict) presentVerdict(verdict, before, m, ply, st);
    if (st.over) return endGame(st);
    if (verdict && G.coach === 'guide' && settings.guard && settings.takebacks && ['mistake', 'blunder', 'miss'].includes(verdict.cls)) {
      const choice = await guardPrompt(verdict, before, m);
      if (!G || G.token !== tok) return;
      if (choice === 'undo') { undoPlies(1, true); return; }
    } else if (G.coach === 'guide' && (!verdict || C.GOOD.has(verdict.cls))) {
      const tip = C.openingTip(before, m, G.tips);
      if (tip) { G.tips.add(tip.id); coachSay({ mood: 'thinking', title: 'Coach tip', html: tip.html, kind: 'tip', append: true }); }
    }
    const cap = mFlags(m) & E.F_CAPTURE ? before.board[mTo(m)] & 7 : 0;
    if (cap >= KNIGHT) botSay('hurt', .6);
    beginTurn();
  }

  function judgeLive(pos, m, ana, ply) {
    const best = ana.moves[0];
    let played = ana.moves.find(e => e.move === m);
    if (!played) played = { move: m, score: best.score - 300, pv: [m] };
    const ucis = G.plies.slice(0, ply).map(p => p.uci);
    const isBook = G.startFen === START && E.bookMoves(ucis).includes(E.toUci(m));
    const prev = ply > 0 ? posAt(G.startFen, G.plies.map(p => p.uci), ply - 1).moveFromUci(G.plies[ply - 1].uci) : 0;
    return C.judge({ pos, move: m, best, played, second: ana.moves[1] ? ana.moves[1].score : null, legalCount: ana.moves.length, prevMove: prev, isBook, opening: E.openingName(ucis.concat([E.toUci(m)])) });
  }

  function presentVerdict(v, before, m, ply, st) {
    const notable = !['good', 'excellent', 'best', 'book', 'forced'].includes(v.cls) || (v.facts && (v.facts.mate || v.facts.fork || (v.facts.capture && v.facts.seeGain >= 150)));
    if (G.coach === 'light' && !notable) return;
    const sound = v.cls === 'blunder' || v.cls === 'mistake' || v.cls === 'miss' ? 'warn' : ['brilliant', 'great'].includes(v.cls) ? 'good' : null;
    const bad = !C.GOOD.has(v.cls);
    coachSay({
      mood: v.mood, cls: v.cls, title: `${v.title}`, html: `<p>${C.moveText(before, m).replace(/^./, c => c.toUpperCase())}</p><p>${v.body.join(' ')}</p>`,
      better: bad || G.coach === 'guide' ? v.better : '', lesson: bad ? v.lesson : '', sound
    });
    if (v.cls === 'brilliant' || v.cls === 'great') board.burst(mTo(m), ['#2ec4b6', '#5b8def', '#f6c847', '#ffffff'], 18, 'star');
  }

  function guardPrompt(v, before, m) {
    return new Promise(resolve => {
      const show = () => {
        board.setArrows(judgeArrows(v.arrows));
        board.setMarks('coach', (v.marks || []).map(x => ({ sq: x.sq, kind: x.kind === 'free' ? 'free' : 'danger' })));
      };
      show();
      // remembered so a style change in Settings can wait until the player answers (see openSettings)
      G.guardEl = coachSay({
        mood: v.mood, cls: v.cls, title: v.cls === 'miss' ? 'Wait — you missed something!' : 'Hold on! Want to take that back?',
        html: `<p>${v.body.join(' ')}</p><p class="small legend"><span style="color:${inkOn() ? window.Ink.colors.green : '#6fbf3a'}">■</span> green arrow = a better idea${v.arrows.some(a => a.kind === 'threat') ? ` · <span style="color:${inkOn() ? window.Ink.colors.red : '#e5484d'}">■</span> red arrow = the danger` : ''}</p>`,
        better: v.better, lesson: v.lesson, sound: 'warn', actionsTop: true,
        actions: [
          { label: '↶ Take it back', fn: () => { scrollToBoardOnMobile(); resolve('undo'); } },
          { label: 'Play on', ghost: true, fn: () => { board.setArrows([]); board.clearMarks('coach'); scrollToBoardOnMobile(); coachSay({ mood: 'neutral', html: 'Brave choice! Let\'s see what happens…' }); resolve('go'); } }
        ]
      });
      revealCoach();
    });
  }

  async function botTurn(tok) {
    setThinking(true);
    const t0 = performance.now();
    const ucis = G.plies.map(p => p.uci);
    let uci = null;
    try {
      const res = await botEng.request({ type: 'bot', fen: G.startFen, moves: ucis, cfg: G.bot.cfg });
      uci = res.move;
    } catch (e) { uci = null; }
    if (!G || G.token !== tok) return;
    let m = uci ? G.pos.moveFromUci(uci) : 0;
    if (!m) { const l = G.pos.legalMoves(); m = l[Math.floor(Math.random() * l.length)]; }
    const minThink = (500 + Math.random() * 600) / Math.max(1, animSpeed() || 3);
    const waited = performance.now() - t0;
    if (waited < minThink) await sleep(minThink - waited);
    if (!G || G.token !== tok) return;
    setThinking(false);
    const { before } = await applyMove(m);
    if (!G || G.token !== tok) return;
    const fl = mFlags(m);
    if (G.pos.inCheck()) botSay('check', .7);
    else if ((fl & E.F_CAPTURE) && (before.board[mTo(m)] & 7) >= KNIGHT) botSay('capture', .7);
    const st = G.pos.status();
    if (st.over) return endGame(st);
    if (G.coach === 'guide') {
      const al = C.alerts(G.pos);
      if (al.length) {
        const a = al[0];
        coachSay({ mood: a.mood, html: a.html + (al[1] ? '<br>' + al[1].html : ''), kind: 'alert', append: true, sound: a.kind === 'mate' || a.kind === 'check' ? 'warn' : null });
        board.setMarks('coach', a.marks || []);
      }
    }
    beginTurn();
  }

  function undoPlies(n, fromGuard) {
    if (!G) return;
    G.token = ++TOKEN;
    setThinking(false);
    let k = 0;
    if (fromGuard) k = 1;
    else {
      // undo back to the player's previous turn
      while (G.plies.length - k > 0 && G.plies[G.plies.length - 1 - k].color !== G.player) k++;
      if (G.plies.length - k > 0) k++;
    }
    if (!k) return;
    const keep = G.plies.slice(0, G.plies.length - k);
    G.plies = keep;
    G.pos = posAt(G.startFen, keep.map(p => p.uci), keep.length);
    for (const key of Object.keys(G.analyses)) if (+key > keep.length) delete G.analyses[key];
    G.takebacks++;
    board.setArrows([]);
    board.clearMarks('coach', 'hint', 'sel', 'targets');
    board.setPosition(G.pos, { fade: true });
    refreshAfterMove();
    coachSay({ mood: 'happy', title: 'Take two!', html: 'No problem — that\'s how we learn. Look at what each enemy piece is attacking, then choose again.' });
    beginTurn();
  }

  async function doHint() {
    if (!G || G.over || G.pos.side !== G.player) return;
    const tok = G.token, ply = G.plies.length;
    G.hintLevel = Math.min(3, G.hintLevel + 1);
    G.hintsUsed++;
    G.hinted[ply] = G.hintLevel;
    coachBusy(true);
    let ana;
    try { ana = await ensureAnalysis(ply); } catch (e) { coachBusy(false); return; }
    coachBusy(false);
    if (!G || G.token !== tok || G.plies.length !== ply) return;
    const h = C.hint(G.pos, ana, G.hintLevel);
    if (!h) return;
    coachSay({ mood: 'thinking', title: `Hint ${G.hintLevel} of 3`, html: h.html, kind: 'tip', sound: 'coach',
      actions: G.hintLevel < 3 ? [{ label: 'Another hint', ghost: true, fn: doHint }] : [] });
    board.setMarks('hint', (h.marks || []).map(x => ({ sq: x.sq, kind: 'hint' })));
    board.setArrows(judgeArrows(h.arrows));
    revealCoach();
  }

  function resign() {
    if (!G || G.over) return;
    const md = modal(`<h2>Surrender?</h2><p>Lay down your arms against ${esc(G.bot.name)}? You can still review the game with the coach afterwards.</p>
      <div class="modal-actions"><button class="btn danger" id="m-yes">🏳 Resign</button><button class="btn ghost" data-close>Keep fighting</button></div>`);
    $('#m-yes', md.card).onclick = () => { md.close(); endGame({ over: true, reason: 'resign', winner: G.player ^ 1 }); };
  }

  const REASON = {
    checkmate: 'by checkmate', resign: 'by resignation', stalemate: 'by stalemate', repetition: 'by threefold repetition',
    fifty: 'by the 50-move rule', insufficient: 'by insufficient material'
  };

  async function endGame(st) {
    if (!G || G.over) return;
    G.over = st;
    G.token = ++TOKEN;
    board.setInteractive(false);
    setThinking(false);
    updateTools();
    renderBars();
    store.del('mw.current');
    const res = st.winner === -1 ? 'draw' : st.winner === G.player ? 'win' : 'loss';
    const reasonText = (res === 'draw' ? 'Draw ' : res === 'win' ? 'Won ' : 'Lost ') + REASON[st.reason];
    // animations
    if (st.reason === 'checkmate') {
      const loser = st.winner ^ 1;
      const kel = board.pieces.get(G.pos.kings[loser]);
      if (kel) { kel.classList.remove('in-check'); kel.classList.add('mated'); }
      let i = 0;
      for (const [sq, el] of board.pieces) if ((G.pos.board[sq] >> 3) === st.winner) { el.style.setProperty('--cd', (i++ * 0.05) + 's'); el.classList.add('cheer'); }
    }
    if (res === 'win') { Snd.play('win'); confetti(90); botSay('lose'); }
    else if (res === 'loss') { Snd.play('lose'); botSay('win'); }
    else Snd.play('draw');
    // rating
    const score = res === 'win' ? 1 : res === 'draw' ? .5 : 0;
    const exp = 1 / (1 + Math.pow(10, (G.bot.elo - profile.rating) / 400));
    const K = profile.games < 10 ? 40 : 24;
    const delta = Math.round(K * (score - exp));
    const before = Math.round(profile.rating);
    profile.rating = Math.max(100, profile.rating + delta);
    profile.best = Math.max(profile.best || 0, profile.rating);
    profile.games++;
    profile[res === 'win' ? 'wins' : res === 'loss' ? 'losses' : 'draws']++;
    const br = profile.bots[G.bot.id] || { w: 0, l: 0, d: 0 };
    br[res === 'win' ? 'w' : res === 'loss' ? 'l' : 'd']++;
    profile.bots[G.bot.id] = br;
    saveProfile();
    const hist = store.get('mw.history', []);
    hist.unshift({ date: Date.now(), botId: G.bot.id, player: G.player, ucis: G.plies.map(p => p.uci), startFen: G.startFen, result: st, res, reasonText });
    store.set('mw.history', hist.slice(0, 15));
    coachSay({ mood: res === 'win' ? 'proud' : res === 'loss' ? 'worried' : 'neutral', title: res === 'win' ? 'Victory!' : res === 'loss' ? 'Good fight!' : 'A draw!',
      html: res === 'win' ? 'Wonderful! Let\'s look back at the game — even winners learn a lot from a review.' : 'Every defeat is a lesson in disguise. Let\'s review the game together and find the turning points.',
      actions: [{ label: '★ Review the game', fn: () => reviewCurrent() }] });
    await sleep(st.reason === 'checkmate' ? 1300 : 600);
    if (!G || G.over !== st) return;
    showResult(res, st, before, delta);
  }

  function showResult(res, st, before, delta) {
    const title = res === 'win' ? 'Victory!' : res === 'loss' ? 'Defeat' : 'Draw';
    const art = res === 'loss' ? botArt(G.bot) : A.svg((G.player << 3) | KING);
    const extra = st.reason === 'stalemate' ? '<br><span class="small muted">Stalemate: the side to move had no legal moves but was not in check — that\'s a draw!</span>' : '';
    const flavour = res === 'win' ? `${esc(G.bot.name)} has been defeated!` : res === 'loss' ? `${esc(G.bot.name)} wins this time.` : 'Neither army could claim the day.';
    const md = modal(`<div class="result-banner ${res}"><div class="rb-art">${art}</div><h2>${title}</h2><p>${flavour} <b>${REASON[st.reason]}</b>${extra}</p>
      <div class="rating-change">${before} → ${Math.round(profile.rating)} <span class="d ${delta >= 0 ? 'up' : 'down'}">${delta >= 0 ? '+' : ''}${delta}</span></div></div>
      <div class="modal-actions"><button class="btn" id="m-review">★ Game review with Sage Bramblewick</button><button class="btn ghost" id="m-rematch">Rematch</button><button class="btn ghost" id="m-home">Choose another opponent</button></div>`);
    $('#m-review', md.card).onclick = () => { md.close(); reviewCurrent(); };
    $('#m-rematch', md.card).onclick = () => { md.close(); startGame(G.bot.id, G.player === WHITE ? 'w' : 'b'); };
    $('#m-home', md.card).onclick = () => { md.close(); show('home'); };
  }

  function reviewCurrent() {
    if (!G) return;
    openReview({ startFen: G.startFen, ucis: G.plies.map(p => p.uci), player: G.player, botId: G.bot.id, result: G.over, analyses: G.analyses, res: G.over ? (G.over.winner === -1 ? 'draw' : G.over.winner === G.player ? 'win' : 'loss') : null });
  }

  /* ---------- review ---------- */
  let R = null, RTOKEN = 0;

  async function openReview(rec) {
    const tok = ++RTOKEN;
    const bot = botById(rec.botId);
    R = { rec, bot, tok, i: 0, positions: [], moves: [], anas: [], judges: [], evals: [], ready: false };
    const n = rec.ucis.length;
    const p = E.Position.fromFen(rec.startFen);
    R.positions.push(p.clone());
    for (let i = 0; i < n; i++) { const m = p.moveFromUci(rec.ucis[i]); R.moves.push(m); R.sans = R.sans || []; R.sans.push(p.san(m)); p.make(m); R.positions.push(p.clone()); }
    C.setPerspective(rec.player);
    show('review');
    rvBoard.setFlipped(rec.player === BLACK);
    rvBoard.setInteractive(false);
    rvBoard.setArrows([]); rvBoard.clearMarks('last', 'check', 'badge', 'coach', 'hint', 'sel', 'targets');
    rvBoard.setPosition(R.positions[0], { fade: true });
    renderReviewBars();
    for (const id of ['#rv-coach', '#rv-graph-wrap', '#rv-summary', '#rv-keys', '#rv-lessons', '#rv-moves-wrap', '#rv-actions']) $(id).hidden = true;
    $('#rv-loading').hidden = false;
    $('#rv-load-av').innerHTML = A.coach('thinking');
    $('#rv-load-av').classList.add('busy');
    const prog = (k, txt) => { $('#rv-prog').style.width = (k * 100).toFixed(0) + '%'; $('#rv-prog-txt').textContent = txt || ''; };
    prog(0, 'Gathering the scrolls…');
    // analyse every position
    for (let i = 0; i <= n; i++) {
      if (tok !== RTOKEN) return;
      prog(i / (n + 1), `Studying move ${Math.max(1, Math.ceil(i / 2))} of ${Math.ceil(n / 2)}…`);
      const pos = R.positions[i];
      const st = pos.status();
      if (st.over && st.reason !== 'repetition' && st.reason !== 'fifty') { R.anas[i] = { terminal: true, score: st.reason === 'checkmate' ? -(E.MATE - 1) : 0 }; continue; }
      if (rec.analyses && rec.analyses[i]) {
        try { R.anas[i] = await rec.analyses[i]; continue; } catch (e) { /* fall through */ }
      }
      try {
        const res = await coachEng.request({ type: 'analyze', multi: false, fen: rec.startFen, moves: rec.ucis.slice(0, i), timeMs: n > 80 ? 220 : 340, minDepth: 4, maxDepth: 14 });
        R.anas[i] = convertAna(pos, res);
      } catch (e) { R.anas[i] = { single: true, move: 0, score: 0, pv: [] }; }
    }
    if (tok !== RTOKEN) return;
    prog(1, 'Writing up the lessons…');
    computeReview();
    $('#rv-load-av').classList.remove('busy');
    $('#rv-loading').hidden = true;
    for (const id of ['#rv-coach', '#rv-graph-wrap', '#rv-summary', '#rv-keys', '#rv-lessons', '#rv-moves-wrap', '#rv-actions']) $(id).hidden = false;
    renderReviewPanels();
    R.ready = true;
    rvGo(0);
  }

  const scoreOf = a => a.terminal ? a.score : a.multi ? (a.moves[0] ? a.moves[0].score : 0) : a.score;
  const pvOf = a => a.terminal ? [] : a.multi ? (a.moves[0] ? a.moves[0].pv : []) : a.pv;

  function computeReview() {
    const { rec } = R;
    const n = rec.ucis.length;
    R.opening = rec.startFen === START ? E.openingName(rec.ucis) : null;
    for (let i = 0; i < n; i++) {
      const pos = R.positions[i], m = R.moves[i], a = R.anas[i], an = R.anas[i + 1];
      const nextScore = scoreOf(an), nextPv = pvOf(an);
      let best, played, second = null;
      if (a.multi && a.moves.length) {
        best = a.moves[0];
        played = a.moves.find(e => e.move === m) || { move: m, score: -nextScore, pv: [m].concat(nextPv) };
        second = a.moves[1] ? a.moves[1].score : null;
      } else {
        best = { move: a.move || m, score: a.score, pv: a.pv && a.pv.length ? a.pv : [a.move || m] };
        played = m === best.move ? { move: m, score: best.score, pv: best.pv } : { move: m, score: -nextScore, pv: [m].concat(nextPv) };
      }
      if (played.score > best.score) best = { move: m, score: played.score, pv: played.pv };
      const isBook = rec.startFen === START && E.bookMoves(rec.ucis.slice(0, i)).includes(rec.ucis[i]);
      R.judges[i] = C.judge({ pos, move: m, best, played, second, legalCount: pos.legalMoves().length, prevMove: i ? R.moves[i - 1] : 0, isBook, opening: R.opening });
      R.evals[i] = (pos.side === WHITE ? 1 : -1) * best.score;
    }
    const last = R.positions[n];
    R.evals[n] = (last.side === WHITE ? 1 : -1) * scoreOf(R.anas[n]);
    R.summary = C.summarize({ plies: R.judges.map((j, i) => ({ color: R.positions[i].side, judge: j })), playerColor: rec.player, opening: R.opening });
  }

  function renderReviewBars() {
    const { rec, bot } = R;
    const topColor = rec.player === BLACK ? WHITE : BLACK;
    for (const [sel, color] of [['#rv-top', topColor], ['#rv-bot', topColor ^ 1]]) {
      const you = color === rec.player;
      const el = $(sel);
      el.style.setProperty('--bc', you ? '#2f5a4a' : bot.color);
      el.innerHTML = `<div class="pb-av">${you ? A.svg((color << 3) | KING) : botArt(bot)}</div><div class="pb-info"><div class="pb-name">${you ? 'You' : esc(bot.name)}</div><div class="pb-sub">${color === WHITE ? 'Sunmeadow' : 'Duskmire'}</div></div><div class="pb-caps"></div>`;
      el.dataset.color = color;
    }
  }

  function renderReviewPanels() {
    const { rec, bot, summary: S } = R;
    const me = rec.player;
    const accMe = S.sides[me].accuracy, accBot = S.sides[me ^ 1].accuracy;
    const order = ['brilliant', 'great', 'best', 'excellent', 'good', 'book', 'inaccuracy', 'mistake', 'miss', 'blunder'];
    $('#rv-summary').innerHTML = `<h2 class="ph">Battle report</h2>
      ${rec.res ? `<p class="small muted" style="margin:0">${rec.res === 'win' ? 'You won' : rec.res === 'loss' ? 'You lost' : 'Draw'}${rec.result && REASON[rec.result.reason] ? ' ' + REASON[rec.result.reason] : ''}${R.opening ? ' · ' + esc(R.opening) : ''}</p>` : ''}
      <div class="acc-row"><div class="acc"><div class="av">${A.svg((me << 3) | KING)}</div><div class="pct">${accMe.toFixed(0)}</div><small>Your accuracy</small></div>
      <div class="acc"><div class="av">${botArt(bot)}</div><div class="pct">${accBot.toFixed(0)}</div><small>${esc(bot.short)}'s accuracy</small></div></div>
      <div class="ctable"><span></span><span class="hd">You</span><span class="hd">${esc(bot.short)}</span>
      ${order.map(k => { const ci = A.CLASS_INFO[k]; return `<span class="cl"><i style="--c:${ci.color}">${emo(ci.sym)}</i>${ci.label}</span><span class="n">${S.sides[me].counts[k] || 0}</span><span class="n">${S.sides[me ^ 1].counts[k] || 0}</span>`; }).join('')}</div>
      `;
    // key moments
    const kms = S.keyMoments, gms = S.goodMoments.slice(0, 2);
    const keyBtn = (p, label) => {
      const j = p.judge, ci = A.CLASS_INFO[j.cls];
      const first = (j.body[0] || '').replace(/<svg[\s\S]*?<\/svg>/g, '');
      return `<button class="key" data-go="${p.i + 1}"><span class="ci" style="--c:${ci.color}">${emo(ci.sym)}</span><span class="kt"><b>Move ${Math.floor(p.i / 2) + 1} · ${ci.label}</b>${label ? ' — ' + label : ''}<br>${first}</span></button>`;
    };
    $('#rv-keys').innerHTML = `<h2 class="ph">Key moments</h2>` + (kms.length ? kms.map(p => keyBtn(p)).join('') : '<p class="muted small">No real mistakes to show — clean game!</p>')
      + (gms.length ? `<h2 class="ph" style="margin-top:12px">Your finest moves</h2>` + gms.map(p => keyBtn(p)).join('') : '');
    $$('#rv-keys [data-go]').forEach(b => b.addEventListener('click', () => { rvGo(+b.dataset.go); scrollToBoardOnMobile(); }));
    $('#rv-lessons').innerHTML = `<h2 class="ph">What to practise</h2>` + (S.lessons.length ? S.lessons.map(l => `<div class="lesson-card"><h4>${l.title}${l.n > 1 ? ` <span class="muted small">×${l.n}</span>` : ''}</h4><p>${l.tip}</p></div>`).join('')
      : '<p class="muted small">No repeated weaknesses spotted. Try a stronger opponent!</p>');
    renderGraph();
    // move list
    let h = '';
    R.moves.forEach((m, i) => {
      const color = R.positions[i].side;
      if (color === WHITE || i === 0) h += `<span class="no">${R.positions[i].full}.</span>`;
      if (i === 0 && color === BLACK) h += '<span class="mv">…</span>';
      const ci = A.CLASS_INFO[R.judges[i].cls];
      h += `<span class="mv" data-i="${i + 1}">${figSan(R.sans[i], color)}<span class="ci" style="--c:${ci.color}">${emo(ci.sym)}</span></span>`;
    });
    $('#rv-moves').innerHTML = h;
    $$('#rv-moves .mv[data-i]').forEach(e => e.addEventListener('click', () => { rvGo(+e.dataset.i); scrollToBoardOnMobile(); }));
  }

  function renderGraph() {
    const n = R.evals.length - 1, W = 600, H = 100;
    const x = k => n ? (k / n) * W : 0;
    const y = cp => (H * (1 - C.wp(cp))).toFixed(1);
    let pts = '';
    for (let k = 0; k <= n; k++) pts += `${x(k).toFixed(1)},${y(R.evals[k])} `;
    let dots = '';
    R.judges.forEach((j, i) => {
      if (['blunder', 'mistake', 'miss', 'brilliant', 'great'].includes(j.cls)) dots += `<circle cx="${x(i + 1)}" cy="${y(R.evals[i + 1])}" r="5" fill="${A.CLASS_INFO[j.cls].color}" stroke="#111" stroke-width="1.5"/>`;
    });
    $('#rv-graph').innerHTML = `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none"><rect width="${W}" height="${H}" fill="#2c2a3a"/>
      <polygon points="0,${H} ${pts} ${W},${H}" fill="#efe5cc"/><polyline points="${pts}" fill="none" stroke="#c8901c" stroke-width="1.5"/>
      <line x1="0" y1="${H / 2}" x2="${W}" y2="${H / 2}" stroke="#888" stroke-dasharray="4 4" stroke-width="1"/>${dots}
      <line id="rv-cursor" x1="0" y1="0" x2="0" y2="${H}" stroke="#4fd1c5" stroke-width="2.5"/></svg>`;
    const svg = $('#rv-graph svg');
    svg.addEventListener('click', e => { const r = svg.getBoundingClientRect(); rvGo(Math.round((e.clientX - r.left) / r.width * n)); scrollToBoardOnMobile(); });
  }

  function rvGo(i, opts) {
    if (!R || !R.ready && i !== 0) return;
    opts = opts || {};
    const n = R.moves.length;
    i = clamp(i, 0, n);
    const prev = R.i;
    R.i = i;
    R.lineTok = (R.lineTok || 0) + 1;
    rvBoard.setInteractive(false);
    rvBoard.clearMarks('coach', 'hint', 'sel', 'targets', 'ping');
    if (i === prev + 1 && animSpeed()) {
      const m = R.moves[prev];
      playMoveSound(R.positions[prev], m);
      rvBoard.animateMove(R.positions[prev], m).then(() => { if (R.i === i) rvBoard.setPosition(R.positions[i]); });
    } else rvBoard.setPosition(R.positions[i]);
    const pos = R.positions[i];
    // marks
    if (i > 0) {
      const m = R.moves[i - 1], j = R.judges[i - 1], ci = A.CLASS_INFO[j.cls];
      rvBoard.setMarks('last', [{ sq: mFrom(m), kind: 'last' }, { sq: mTo(m), kind: 'last' }]);
      rvBoard.setMarks('badge', inkOn() ? [{ sq: mTo(m), kind: 'badge', sym: ci.sym, cls: j.cls }] : [{ sq: mTo(m), kind: 'badge', color: ci.color, html: `<i>${ci.sym}</i>` }]);
      rvBoard.setArrows(judgeArrows(j.arrows));
    } else { rvBoard.setMarks('last', []); rvBoard.setMarks('badge', []); rvBoard.setArrows([]); }
    rvBoard.setMarks('check', pos.inCheck() ? [{ sq: pos.kings[pos.side], kind: 'check' }] : []);
    updateEvalBar($('#rv-evalbar'), R.evals[i], R.rec.player === BLACK);
    $('#rv-pos').textContent = i === 0 ? 'Start' : `Move ${R.positions[i - 1].full}${R.positions[i - 1].side === BLACK ? '…' : '.'} ${R.sans[i - 1]}`;
    $$('#rv-moves .mv').forEach(e => e.classList.toggle('cur', +e.dataset.i === i));
    const cur = $(`#rv-moves .mv[data-i="${i}"]`), box = $('#rv-moves');
    if (cur && box) box.scrollTop = Math.max(0, cur.offsetTop - box.offsetTop - box.clientHeight / 2);
    const cx = $('#rv-cursor');
    if (cx) { const xx = n ? (i / n) * 600 : 0; cx.setAttribute('x1', xx); cx.setAttribute('x2', xx); }
    // captured bars
    const caps = capturedBy(pos), mat = C.balance(pos, WHITE);
    for (const sel of ['#rv-top', '#rv-bot']) {
      const el = $(sel), color = +el.dataset.color, adv = color === WHITE ? mat : -mat;
      $('.pb-caps', el).innerHTML = caps[color].map(c => `<span class="cap">${A.mini(c)}</span>`).join('') + (adv > 0 ? `<span class="adv">+${adv}</span>` : '');
    }
    // coach card
    if (i === 0) {
      coachSay({ mood: 'happy', title: 'Here\'s how it went', html: `<p>${R.summary.narrative}</p><p class="small">Press ▶ to step through the game, or tap a <b>key moment</b>. I'll explain every move in plain words.</p>`,
        actions: R.summary.keyMoments.length ? [{ label: 'Jump to the first key moment', fn: () => rvGo(R.summary.keyMoments[0].i + 1) }] : [] }, 'review');
      return;
    }
    const j = R.judges[i - 1], before = R.positions[i - 1], m = R.moves[i - 1];
    const mine = before.side === R.rec.player;
    const acts = [];
    if (mine && !C.GOOD.has(j.cls)) acts.push({ label: '<span class="emo">🎯</span> Try to find a better move', fn: () => rvPractice(i - 1) });
    const anaB = R.anas[i - 1];
    const bestPv = anaB.multi ? (anaB.moves[0] && anaB.moves[0].pv) : anaB.pv;
    if (bestPv && bestPv.length && bestPv[0] !== m) acts.push({ label: '▶ Show the best line', ghost: true, fn: () => rvLine(i - 1, bestPv) });
    if (!mine && !C.GOOD.has(j.cls)) {
      const nx = R.anas[i];
      const pv = pvOf(nx);
      if (pv && pv.length) acts.push({ label: '▶ Show how to punish it', ghost: true, fn: () => rvLine(i, pv) });
    }
    coachSay({
      mood: j.mood, cls: j.cls, title: `${before.full}${before.side === BLACK ? '…' : '.'} ${R.sans[i - 1]}`,
      html: `<p>${C.moveText(before, m).replace(/^./, c => c.toUpperCase())}${mine ? '' : ' <span class="small">(enemy move)</span>'}</p><p>${j.body.join(' ')}</p>`,
      better: mine ? j.better : '', lesson: mine ? j.lesson : '', actions: acts
    }, 'review');
  }

  async function rvLine(fromIdx, pv) {
    R.lineTok = (R.lineTok || 0) + 1;
    const myTok = R.lineTok;
    const start = R.positions[fromIdx].clone();
    rvBoard.setPosition(start);
    rvBoard.setArrows([]); rvBoard.clearMarks('badge', 'last', 'check');
    const steps = pv.slice(0, 6);
    coachSay({ mood: 'thinking', title: 'The best line', html: C.lineText(start, steps, steps.length) + '.', actions: [{ label: '⟲ Back to the game', fn: () => rvGo(R.i) }] }, 'review');
    const p = start.clone();
    for (const m of steps) {
      await sleep(650);
      if (!R || R.lineTok !== myTok) return;
      if (!p.legalMoves().includes(m)) break;
      playMoveSound(p, m);
      await rvBoard.animateMove(p.clone(), m);
      p.make(m);
      rvBoard.setPosition(p);
      rvBoard.setMarks('last', [{ sq: mFrom(m), kind: 'last' }, { sq: mTo(m), kind: 'last' }]);
      rvBoard.setMarks('check', p.inCheck() ? [{ sq: p.kings[p.side], kind: 'check' }] : []);
    }
  }

  async function rvPractice(idx) {
    const pos = R.positions[idx].clone();
    const myTok = R.lineTok = (R.lineTok || 0) + 1;
    let tries = 0;
    const setup = () => {
      rvBoard.setPosition(pos);
      rvBoard.setArrows([]); rvBoard.clearMarks('badge', 'last', 'check', 'coach');
      if (pos.inCheck()) rvBoard.setMarks('check', [{ sq: pos.kings[pos.side], kind: 'check' }]);
      const legal = pos.legalMoves();
      rvBoard.setInteractive(true, { side: pos.side, getMoves: from => legal.filter(m => mFrom(m) === from).map(m => ({ to: mTo(m), m, ep: !!(mFlags(m) & E.F_EP) })) });
    };
    setup();
    scrollToBoardOnMobile();
    coachSay({ mood: 'thinking', title: 'Your turn to try!', html: 'Here\'s the position before your move. Can you find something better? Think about what\'s attacked and what\'s unprotected.', kind: 'tip',
      actions: [{ label: 'Show me the answer', ghost: true, fn: () => reveal() }, { label: 'Back', ghost: true, fn: () => rvGo(R.i) }] }, 'review');
    let ana = R.anas[idx];
    if (!ana.multi) {
      try { ana = convertAna(pos, await coachEng.request({ type: 'analyze', multi: true, fen: R.rec.startFen, moves: R.rec.ucis.slice(0, idx), timeMs: 900, minDepth: 3 })); R.anas[idx] = ana; } catch (e) { /* keep single */ }
    }
    const reveal = () => {
      if (!ana.moves || !ana.moves[0]) return;
      rvBoard.setInteractive(false);
      const b = ana.moves[0];
      rvBoard.setArrows([{ from: mFrom(b.move), to: mTo(b.move), kind: 'best' }]);
      const h = C.hint(pos, ana, 3);
      coachSay({ mood: 'happy', title: 'The answer', html: h.html, actions: [{ label: '⟲ Back to the game', fn: () => rvGo(R.i) }] }, 'review');
    };
    rvBoard.onMove = async (m) => {
      if (R.lineTok !== myTok) return;
      rvBoard.setInteractive(false);
      playMoveSound(pos, m);
      await rvBoard.animateMove(pos.clone(), m);
      if (!ana.multi) { reveal(); return; }
      const best = ana.moves[0], e = ana.moves.find(x => x.move === m) || { move: m, score: best.score - 400, pv: [m] };
      const loss = C.wp(best.score) - C.wp(e.score);
      const ok = m === best.move || loss < 0.03 || (C.mateIn(best.score) > 0 && C.mateIn(e.score) > 0);
      const j = C.judge({ pos, move: m, best, played: e, second: null, legalCount: ana.moves.length, prevMove: 0 });
      if (ok) {
        Snd.play('good');
        rvBoard.burst(mTo(m), ['#2ec4b6', '#f6c847', '#ffffff', '#7cc35a'], 20, 'star');
        coachSay({ mood: 'proud', title: 'Yes! That\'s it!', html: `${C.moveText(pos, m).replace(/^./, c => c.toUpperCase())} — ${j.body.join(' ')}`, actions: [{ label: '⟲ Back to the game', fn: () => rvGo(R.i) }] }, 'review');
      } else {
        tries++;
        Snd.play('illegal');
        coachSay({ mood: 'worried', title: 'Not quite…', html: `<p>${j.body.join(' ')}</p><p class="small">Try again${tries >= 2 ? ' — or ask for the answer' : ''}.</p>`, kind: 'alert',
          actions: [{ label: 'Show me the answer', ghost: true, fn: () => reveal() }, { label: 'Back', ghost: true, fn: () => rvGo(R.i) }] }, 'review');
        await sleep(1300);
        if (R.lineTok === myTok) setup();
      }
    };
  }

  function revealCoach() {
    if (window.innerWidth > 920) return;
    // scroll only as far as needed to show the newest bubble's buttons, so the board (arrows, marks) stays in view
    const b = $('#coach-feed .bubble');
    if (!b) return;
    const t = $('.acts', b) || b;
    // never push the bubble's top under the sticky header
    const dy = Math.min(t.getBoundingClientRect().bottom - (window.innerHeight - 12), b.getBoundingClientRect().top - 70);
    if (dy > 0) window.scrollBy({ top: dy, behavior: 'smooth' });
  }
  function scrollToBoardOnMobile() {
    if (window.innerWidth <= 920) { const el = current === 'review' ? $('#rv-board') : $('#board'); if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' }); }
  }

  /* ---------- settings ---------- */
  function openSettings() {
    const sw = (k, label, sub) => `<div class="set-row"><label for="s-${k}">${label}<small>${sub || ''}</small></label><span class="switch"><input type="checkbox" id="s-${k}" data-k="${k}" ${settings[k] ? 'checked' : ''}><span></span></span></div>`;
    const sel = (k, label, opts, sub) => `<div class="set-row"><label for="s-${k}">${label}<small>${sub || ''}</small></label><select id="s-${k}" data-k="${k}">${opts.map(([v, t]) => `<option value="${v}" ${settings[k] === v ? 'selected' : ''}>${t}</option>`).join('')}</select></div>`;
    const md = modal(`<h2>Settings</h2>
      ${sel('art', 'Art style', [['sketch', 'Sketchbook'], ['storybook', 'Storybook']], 'Changing this reloads the page — your game is kept')}
      ${sel('pieceSet', 'Pieces', [['creatures', 'Mosswood creatures'], ['classic', 'Classic chess pieces']], 'Classic pieces look like the ones on other chess sites. Reloads the page — your game is kept')}
      ${settings.art === 'sketch' ? sw('boil', 'Wobbly ink lines', 'The hand-drawn lines gently shimmer, like a flip-book') : ''}
      ${settings.art === 'sketch' ? sel('ink', 'Pen colour', [['blue', 'Blue ballpoint'], ['black', 'Black ink']], 'Reloads the page — your game is kept') : ''}
      ${sw('sound', 'Sound effects')}
      ${sel('coach', 'Coach', [['guide', 'Guide me'], ['light', 'Light touch'], ['off', 'Quiet (review only)']], 'How chatty Sage Bramblewick is during games')}
      ${sw('guard', 'Blunder guard', 'Pause after a mistake and offer a take-back')}
      ${sw('takebacks', 'Allow take-backs')}
      ${sw('danger', 'Show my pieces in danger', 'Red rings around your pieces that can be captured')}
      ${sw('freeHL', 'Show free enemy pieces', `${settings.art === 'sketch' ? 'Green' : 'Gold'} rings on enemy pieces you can win (training wheels!)`)}
      ${sw('evalBar', 'Show "who\'s winning" bar', 'An evaluation bar beside the board during games')}
      ${sel('names', 'Piece names', [['both', 'Fantasy + chess names'], ['fantasy', 'Fantasy names only'], ['classic', 'Chess names only']])}
      ${sw('badges', 'Letter badges on pieces', 'Small K, Q, R, B, N, P labels')}
      ${settings.art !== 'sketch' ? sel('theme', 'Board', [['glade', 'Mossy glade'], ['stone', 'Castle stone'], ['autumn', 'Autumn wood'], ['twilight', 'Twilight']]) : ''}
      ${sw('coords', 'Board coordinates')}
      ${sel('anim', 'Move animations', [['normal', 'Normal'], ['fast', 'Fast'], ['off', 'Off']])}
      ${sw('idle', 'Idle creature animations', 'Breathing, blinking, flapping')}
      <div class="modal-actions"><button class="btn ghost small" id="s-reset">Reset rating &amp; history</button></div>`);
    md.card.querySelectorAll('[data-k]').forEach(inp => inp.addEventListener('change', () => {
      const k = inp.dataset.k;
      settings[k] = inp.type === 'checkbox' ? inp.checked : inp.value;
      if (k === 'coach' && G && !G.over) G.coach = settings.coach;
      saveSettings();
      if (k === 'art' || k === 'pieceSet' || k === 'ink') {
        const reload = () => {
          saveCurrent(); // the newest ply's verdict arrives after its move was saved
          try { if (G && !G.over) sessionStorage.setItem('mw.autoresume', '1'); } catch (e) { /* ignore */ }
          setTimeout(() => location.reload(), 120);
        };
        // reloading during a blunder-guard prompt would lose the take-back offer and let the bot play on
        if (G && !G.over && G.guardEl && G.guardEl.isConnected) {
          G.reloadAfterGuard = reload;
          toast('Your new look will appear once you choose Take it back or Play on.');
        } else reload();
      }
    }));
    $('#s-reset', md.card).onclick = () => {
      if (!confirm('Reset your rating, records and game history?')) return;
      Object.assign(profile, { rating: 400, games: 0, wins: 0, losses: 0, draws: 0, bots: {}, best: 400 });
      saveProfile(); store.del('mw.history');
      toast('Progress reset. A fresh adventure begins!');
      md.close(); renderHome();
    };
  }

  /* ---------- wiring ---------- */
  function init() {
    setupArt();
    initWorld();
    board = new BoardView($('#board'));
    rvBoard = new BoardView($('#rv-board'));
    board.setPosition(E.Position.fromFen(START));
    applySettings();
    $$('header [data-go]').forEach(b => b.addEventListener('click', () => { Snd.unlock(); show(b.dataset.go); }));
    $('#btn-sound').onclick = () => { settings.sound = !settings.sound; saveSettings(); Snd.unlock(); Snd.play('click'); };
    $('#btn-settings').onclick = openSettings;
    $$('#seg-color button').forEach(b => b.addEventListener('click', () => { settings.color = b.dataset.v; saveSettings(); renderHome(); Snd.play('click'); }));
    $$('#seg-coach button').forEach(b => b.addEventListener('click', () => { settings.coach = b.dataset.v; saveSettings(); renderHome(); Snd.play('click'); }));
    $('#btn-start').onclick = () => {
      Snd.unlock();
      if (G && !G.over && G.plies.length) {
        const md = modal(`<h2>Abandon your battle?</h2><p>You have a game in progress against ${esc(G.bot.name)}. Starting a new one will count it as a loss.</p>
          <div class="modal-actions"><button class="btn" id="m-new">Start new battle</button><button class="btn ghost" id="m-ret">Return to the battle</button></div>`);
        $('#m-new', md.card).onclick = () => { md.close(); G.over = { reason: 'resign' }; store.del('mw.current'); startGame(selectedBot, settings.color); };
        $('#m-ret', md.card).onclick = () => { md.close(); show('game'); };
        return;
      }
      startGame(selectedBot, settings.color);
    };
    $('#t-hint').onclick = doHint;
    $('#t-undo').onclick = () => { if (G && !G.over && settings.takebacks) undoPlies(0, false); };
    $('#t-flip').onclick = () => { board.setFlipped(!board.flipped); renderBars(); };
    $('#t-danger').onclick = () => { settings.danger = !settings.danger; saveSettings(); refreshDanger(); toast(settings.danger ? 'Danger sense on: your threatened pieces glow red.' : 'Danger sense off.'); };
    $('#t-resign').onclick = resign;
    $('#rv-first').onclick = () => rvGo(0);
    $('#rv-prev').onclick = () => rvGo(R.i - 1);
    $('#rv-next').onclick = () => rvGo(R.i + 1, { animate: true });
    $('#rv-last').onclick = () => rvGo(R.moves.length);
    $('#rv-rematch').onclick = () => { if (R) startGame(R.rec.botId, R.rec.player === WHITE ? 'w' : 'b'); };
    $('#rv-home').onclick = () => show('home');
    document.addEventListener('keydown', e => {
      if (!$('#modal').hidden) { if (e.key === 'Escape') { $('#modal').hidden = true; } return; }
      if (current === 'review' && R && R.ready) {
        if (e.key === 'ArrowLeft') { rvGo(R.i - 1); e.preventDefault(); }
        if (e.key === 'ArrowRight') { rvGo(R.i + 1); e.preventDefault(); }
      }
    });
    renderHome();
    show('home');
    if (inkOn() && window.Doodles) {
      // decoration only: draw the margin doodles after first paint so they stay off the start-up critical path
      const mountDoodles = () => { try { window.Doodles.mount($('#doodles')); } catch (e) { /* decoration only */ } };
      if (window.requestIdleCallback) requestIdleCallback(mountDoodles, { timeout: 1000 }); else setTimeout(mountDoodles, 50);
    }
    // after a style switch reloads the page, jump straight back into the battle
    let auto = null;
    try { auto = sessionStorage.getItem('mw.autoresume'); sessionStorage.removeItem('mw.autoresume'); } catch (e) { /* ignore */ }
    const cur = store.get('mw.current', null);
    if (auto && cur && cur.ucis) resumeGame(cur);
  }

  window.MW = { debug: () => ({ G, board, R, rvBoard }), BoardView, coachEng, sleep, esc, coachSay, coachBusy, show, settings, store, toast, modal, convertAna, animSpeed, confetti, playMoveSound, judgeArrows, Snd };
  document.addEventListener('DOMContentLoaded', init);
})();
