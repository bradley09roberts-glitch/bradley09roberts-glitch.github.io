/* Mosswood Chess engine: rules, move generation, evaluation, search, analysis and bots.
   Works in the browser main thread, in a Web Worker (importScripts) and in Node (require). */
(function (global) {
  'use strict';

  const WHITE = 0, BLACK = 1;
  const PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6;
  const START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

  const KNIGHT_D = [33, 31, 18, 14, -14, -18, -31, -33];
  const BISHOP_D = [17, 15, -15, -17];
  const ROOK_D = [16, -16, 1, -1];
  const KING_D = [17, 16, 15, 1, -1, -15, -16, -17];

  const F_CAPTURE = 1, F_DOUBLE = 2, F_EP = 4, F_CASTLE = 8, F_PROMO = 16;
  const mkMove = (from, to, flags, promo) => from | (to << 7) | ((promo || 0) << 14) | (flags << 17);
  const mFrom = m => m & 127;
  const mTo = m => (m >> 7) & 127;
  const mPromo = m => (m >> 14) & 7;
  const mFlags = m => (m >> 17) & 31;

  const VAL = [0, 100, 320, 330, 500, 900, 20000];
  const SEE_VAL = [0, 100, 320, 330, 500, 900, 20000];
  const PIECE_CH = ' pnbrqk';

  const sqName = sq => 'abcdefgh'[sq & 7] + ((sq >> 4) + 1);
  const sqFromName = n => (n.charCodeAt(0) - 97) + (n.charCodeAt(1) - 49) * 16;
  const fileOf = sq => sq & 7;
  const rankOf = sq => sq >> 4;

  /* ---------- Zobrist hashing (two 32-bit halves) ---------- */
  let zseed = 0x2545F491;
  function rnd32() {
    zseed = (zseed + 0x6D2B79F5) | 0;
    let t = Math.imul(zseed ^ (zseed >>> 15), 1 | zseed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return (t ^ (t >>> 14)) | 0;
  }
  const ZP_LO = new Int32Array(16 * 128), ZP_HI = new Int32Array(16 * 128);
  for (let i = 0; i < 16 * 128; i++) { ZP_LO[i] = rnd32(); ZP_HI[i] = rnd32(); }
  const ZC_LO = new Int32Array(16), ZC_HI = new Int32Array(16);
  for (let i = 0; i < 16; i++) { ZC_LO[i] = rnd32(); ZC_HI[i] = rnd32(); }
  const ZE_LO = new Int32Array(8), ZE_HI = new Int32Array(8);
  for (let i = 0; i < 8; i++) { ZE_LO[i] = rnd32(); ZE_HI[i] = rnd32(); }
  const ZS_LO = rnd32(), ZS_HI = rnd32();

  // Castling rights: 1 = white king side, 2 = white queen side, 4 = black king side, 8 = black queen side
  const CASTLE_KEEP = new Int8Array(128).fill(15);
  CASTLE_KEEP[0] = 15 & ~2; CASTLE_KEEP[7] = 15 & ~1; CASTLE_KEEP[4] = 15 & ~3;
  CASTLE_KEEP[112] = 15 & ~8; CASTLE_KEEP[119] = 15 & ~4; CASTLE_KEEP[116] = 15 & ~12;

  /* ---------- Position ---------- */
  class Position {
    constructor() {
      this.board = new Int8Array(128);
      this.side = WHITE; this.castle = 0; this.ep = -1; this.half = 0; this.full = 1;
      this.kings = [4, 116];
      this.hlo = 0; this.hhi = 0;
      this.stack = [];
      this.histLo = []; this.histHi = [];
    }

    static fromFen(fen) {
      const p = new Position();
      const parts = fen.trim().split(/\s+/);
      const rows = parts[0].split('/');
      if (rows.length !== 8) throw new Error('Bad FEN');
      for (let i = 0; i < 8; i++) {
        let f = 0; const r = 7 - i;
        for (const ch of rows[i]) {
          if (/\d/.test(ch)) { f += +ch; continue; }
          const lower = ch.toLowerCase();
          const t = PIECE_CH.indexOf(lower);
          if (t <= 0) throw new Error('Bad FEN piece');
          const color = ch === lower ? BLACK : WHITE;
          const sq = r * 16 + f;
          p.board[sq] = (color << 3) | t;
          if (t === KING) p.kings[color] = sq;
          f++;
        }
      }
      p.side = parts[1] === 'b' ? BLACK : WHITE;
      const c = parts[2] || '-';
      p.castle = (c.includes('K') ? 1 : 0) | (c.includes('Q') ? 2 : 0) | (c.includes('k') ? 4 : 0) | (c.includes('q') ? 8 : 0);
      p.ep = parts[3] && parts[3] !== '-' ? sqFromName(parts[3]) : -1;
      p.half = parseInt(parts[4] || '0', 10) || 0;
      p.full = parseInt(parts[5] || '1', 10) || 1;
      p.computeHash();
      return p;
    }

    computeHash() {
      let lo = 0, hi = 0;
      for (let sq = 0; sq < 128; sq++) {
        if (sq & 0x88) { sq += 7; continue; }
        const pc = this.board[sq];
        if (pc) { lo ^= ZP_LO[pc * 128 + sq]; hi ^= ZP_HI[pc * 128 + sq]; }
      }
      lo ^= ZC_LO[this.castle]; hi ^= ZC_HI[this.castle];
      if (this.ep >= 0) { lo ^= ZE_LO[this.ep & 7]; hi ^= ZE_HI[this.ep & 7]; }
      if (this.side === BLACK) { lo ^= ZS_LO; hi ^= ZS_HI; }
      this.hlo = lo; this.hhi = hi;
    }

    clone() {
      const p = new Position();
      p.board.set(this.board);
      p.side = this.side; p.castle = this.castle; p.ep = this.ep; p.half = this.half; p.full = this.full;
      p.kings = this.kings.slice(); p.hlo = this.hlo; p.hhi = this.hhi;
      p.histLo = this.histLo.slice(); p.histHi = this.histHi.slice();
      return p;
    }

    toFen() {
      let s = '';
      for (let r = 7; r >= 0; r--) {
        let empty = 0;
        for (let f = 0; f < 8; f++) {
          const pc = this.board[r * 16 + f];
          if (!pc) { empty++; continue; }
          if (empty) { s += empty; empty = 0; }
          const ch = PIECE_CH[pc & 7];
          s += (pc >> 3) === WHITE ? ch.toUpperCase() : ch;
        }
        if (empty) s += empty;
        if (r) s += '/';
      }
      let c = (this.castle & 1 ? 'K' : '') + (this.castle & 2 ? 'Q' : '') + (this.castle & 4 ? 'k' : '') + (this.castle & 8 ? 'q' : '');
      return `${s} ${this.side === WHITE ? 'w' : 'b'} ${c || '-'} ${this.ep >= 0 ? sqName(this.ep) : '-'} ${this.half} ${this.full}`;
    }

    pieceAt(sq) { return this.board[sq]; }

    attacked(sq, by) {
      const b = this.board;
      let s;
      if (by === WHITE) {
        s = sq - 15; if (!(s & 0x88) && b[s] === 1) return true;
        s = sq - 17; if (!(s & 0x88) && b[s] === 1) return true;
      } else {
        s = sq + 15; if (!(s & 0x88) && b[s] === 9) return true;
        s = sq + 17; if (!(s & 0x88) && b[s] === 9) return true;
      }
      const off = by << 3;
      const N = off | KNIGHT, K = off | KING, B = off | BISHOP, R = off | ROOK, Q = off | QUEEN;
      for (let i = 0; i < 8; i++) {
        s = sq + KNIGHT_D[i]; if (!(s & 0x88) && b[s] === N) return true;
        s = sq + KING_D[i]; if (!(s & 0x88) && b[s] === K) return true;
      }
      for (let i = 0; i < 4; i++) {
        let d = BISHOP_D[i]; s = sq + d;
        while (!(s & 0x88)) { const p = b[s]; if (p) { if (p === B || p === Q) return true; break; } s += d; }
        d = ROOK_D[i]; s = sq + d;
        while (!(s & 0x88)) { const p = b[s]; if (p) { if (p === R || p === Q) return true; break; } s += d; }
      }
      return false;
    }

    inCheck(side) {
      if (side === undefined) side = this.side;
      return this.attacked(this.kings[side], side ^ 1);
    }

    // Pseudo-legal move generation. capturesOnly also includes promotions.
    genMoves(list, capturesOnly) {
      const b = this.board, us = this.side, them = us ^ 1;
      for (let sq = 0; sq < 128; sq++) {
        if (sq & 0x88) { sq += 7; continue; }
        const p = b[sq];
        if (!p || (p >> 3) !== us) continue;
        const t = p & 7;
        if (t === PAWN) {
          const fwd = us === WHITE ? 16 : -16;
          const r = sq >> 4;
          const startRank = us === WHITE ? 1 : 6, preRank = us === WHITE ? 6 : 1;
          let to = sq + fwd;
          if (!(to & 0x88) && !b[to]) {
            if (r === preRank) {
              list.push(mkMove(sq, to, F_PROMO, QUEEN), mkMove(sq, to, F_PROMO, KNIGHT), mkMove(sq, to, F_PROMO, ROOK), mkMove(sq, to, F_PROMO, BISHOP));
            } else if (!capturesOnly) {
              list.push(mkMove(sq, to, 0, 0));
              if (r === startRank && !b[to + fwd]) list.push(mkMove(sq, to + fwd, F_DOUBLE, 0));
            }
          }
          for (let k = 0; k < 2; k++) {
            to = sq + fwd + (k ? 1 : -1);
            if (to & 0x88) continue;
            const q = b[to];
            if (q && (q >> 3) === them) {
              if (r === preRank) {
                const fl = F_CAPTURE | F_PROMO;
                list.push(mkMove(sq, to, fl, QUEEN), mkMove(sq, to, fl, KNIGHT), mkMove(sq, to, fl, ROOK), mkMove(sq, to, fl, BISHOP));
              } else list.push(mkMove(sq, to, F_CAPTURE, 0));
            } else if (to === this.ep) list.push(mkMove(sq, to, F_CAPTURE | F_EP, 0));
          }
        } else if (t === KNIGHT || t === KING) {
          const D = t === KNIGHT ? KNIGHT_D : KING_D;
          for (let i = 0; i < 8; i++) {
            const to = sq + D[i];
            if (to & 0x88) continue;
            const q = b[to];
            if (!q) { if (!capturesOnly) list.push(mkMove(sq, to, 0, 0)); }
            else if ((q >> 3) === them) list.push(mkMove(sq, to, F_CAPTURE, 0));
          }
          if (t === KING && !capturesOnly) this.genCastles(list, sq);
        } else {
          const D = t === BISHOP ? BISHOP_D : t === ROOK ? ROOK_D : KING_D;
          for (let i = 0; i < D.length; i++) {
            const d = D[i]; let to = sq + d;
            while (!(to & 0x88)) {
              const q = b[to];
              if (!q) { if (!capturesOnly) list.push(mkMove(sq, to, 0, 0)); }
              else { if ((q >> 3) === them) list.push(mkMove(sq, to, F_CAPTURE, 0)); break; }
              to += d;
            }
          }
        }
      }
      return list;
    }

    genCastles(list, sq) {
      const b = this.board, us = this.side, them = us ^ 1;
      if (us === WHITE) {
        if (sq !== 4) return;
        if ((this.castle & 1) && b[7] === ROOK && !b[5] && !b[6] && !this.attacked(4, them) && !this.attacked(5, them) && !this.attacked(6, them)) list.push(mkMove(4, 6, F_CASTLE, 0));
        if ((this.castle & 2) && b[0] === ROOK && !b[3] && !b[2] && !b[1] && !this.attacked(4, them) && !this.attacked(3, them) && !this.attacked(2, them)) list.push(mkMove(4, 2, F_CASTLE, 0));
      } else {
        if (sq !== 116) return;
        if ((this.castle & 4) && b[119] === (8 | ROOK) && !b[117] && !b[118] && !this.attacked(116, them) && !this.attacked(117, them) && !this.attacked(118, them)) list.push(mkMove(116, 118, F_CASTLE, 0));
        if ((this.castle & 8) && b[112] === (8 | ROOK) && !b[115] && !b[114] && !b[113] && !this.attacked(116, them) && !this.attacked(115, them) && !this.attacked(114, them)) list.push(mkMove(116, 114, F_CASTLE, 0));
      }
    }

    make(m) {
      const b = this.board, from = mFrom(m), to = mTo(m), fl = mFlags(m);
      const piece = b[from], us = this.side, them = us ^ 1;
      let capSq = to, captured = b[to];
      if (fl & F_EP) { capSq = to + (us === WHITE ? -16 : 16); captured = b[capSq]; }
      this.stack.push({ m, captured, castle: this.castle, ep: this.ep, half: this.half, hlo: this.hlo, hhi: this.hhi });
      this.histLo.push(this.hlo); this.histHi.push(this.hhi);
      let lo = this.hlo, hi = this.hhi;
      if (this.ep >= 0) { lo ^= ZE_LO[this.ep & 7]; hi ^= ZE_HI[this.ep & 7]; }
      lo ^= ZC_LO[this.castle]; hi ^= ZC_HI[this.castle];
      lo ^= ZP_LO[piece * 128 + from]; hi ^= ZP_HI[piece * 128 + from];
      if (captured) { lo ^= ZP_LO[captured * 128 + capSq]; hi ^= ZP_HI[captured * 128 + capSq]; b[capSq] = 0; }
      b[from] = 0;
      const placed = (fl & F_PROMO) ? ((us << 3) | mPromo(m)) : piece;
      b[to] = placed;
      lo ^= ZP_LO[placed * 128 + to]; hi ^= ZP_HI[placed * 128 + to];
      if ((piece & 7) === KING) {
        this.kings[us] = to;
        if (fl & F_CASTLE) {
          let rf, rt;
          if (to > from) { rf = from + 3; rt = from + 1; } else { rf = from - 4; rt = from - 1; }
          const rook = b[rf]; b[rf] = 0; b[rt] = rook;
          lo ^= ZP_LO[rook * 128 + rf] ^ ZP_LO[rook * 128 + rt]; hi ^= ZP_HI[rook * 128 + rf] ^ ZP_HI[rook * 128 + rt];
        }
      }
      this.castle &= CASTLE_KEEP[from] & CASTLE_KEEP[to];
      this.ep = -1;
      if (fl & F_DOUBLE) {
        const enemyPawn = (them << 3) | PAWN;
        if ((!((to + 1) & 0x88) && b[to + 1] === enemyPawn) || (!((to - 1) & 0x88) && b[to - 1] === enemyPawn)) {
          this.ep = (from + to) >> 1;
          lo ^= ZE_LO[this.ep & 7]; hi ^= ZE_HI[this.ep & 7];
        }
      }
      lo ^= ZC_LO[this.castle]; hi ^= ZC_HI[this.castle];
      this.half = ((piece & 7) === PAWN || captured) ? 0 : this.half + 1;
      if (us === BLACK) this.full++;
      this.side = them;
      lo ^= ZS_LO; hi ^= ZS_HI;
      this.hlo = lo; this.hhi = hi;
    }

    unmake() {
      const u = this.stack.pop();
      this.histLo.pop(); this.histHi.pop();
      const m = u.m;
      this.side ^= 1;
      const us = this.side;
      if (us === BLACK) this.full--;
      this.castle = u.castle; this.ep = u.ep; this.half = u.half; this.hlo = u.hlo; this.hhi = u.hhi;
      if (m === 0) return; // null move
      const b = this.board, from = mFrom(m), to = mTo(m), fl = mFlags(m);
      let piece = b[to];
      if (fl & F_PROMO) piece = (us << 3) | PAWN;
      b[from] = piece; b[to] = 0;
      if (fl & F_EP) b[to + (us === WHITE ? -16 : 16)] = u.captured;
      else b[to] = u.captured;
      if ((piece & 7) === KING) {
        this.kings[us] = from;
        if (fl & F_CASTLE) {
          let rf, rt;
          if (to > from) { rf = from + 3; rt = from + 1; } else { rf = from - 4; rt = from - 1; }
          b[rf] = b[rt]; b[rt] = 0;
        }
      }
    }

    makeNull() {
      this.stack.push({ m: 0, captured: 0, castle: this.castle, ep: this.ep, half: this.half, hlo: this.hlo, hhi: this.hhi });
      this.histLo.push(this.hlo); this.histHi.push(this.hhi);
      let lo = this.hlo, hi = this.hhi;
      if (this.ep >= 0) { lo ^= ZE_LO[this.ep & 7]; hi ^= ZE_HI[this.ep & 7]; this.ep = -1; }
      lo ^= ZS_LO; hi ^= ZS_HI;
      this.half++;
      if (this.side === BLACK) this.full++;
      this.side ^= 1;
      this.hlo = lo; this.hhi = hi;
    }

    legalMoves() {
      const list = this.genMoves([], false), out = [];
      const us = this.side, them = us ^ 1;
      for (const m of list) {
        this.make(m);
        if (!this.attacked(this.kings[us], them)) out.push(m);
        this.unmake();
      }
      return out;
    }

    // number of earlier occurrences of the current position (same side to move)
    repetitions() {
      let n = 0;
      const len = this.histLo.length;
      for (let j = 2; j <= this.half && j <= len; j += 2) {
        if (this.histLo[len - j] === this.hlo && this.histHi[len - j] === this.hhi) n++;
      }
      return n;
    }

    insufficientMaterial() {
      let minors = 0, bishopsColor = [0, 0], knights = 0, other = 0;
      for (let sq = 0; sq < 128; sq++) {
        if (sq & 0x88) { sq += 7; continue; }
        const p = this.board[sq]; if (!p) continue;
        const t = p & 7;
        if (t === KING) continue;
        if (t === BISHOP) { minors++; bishopsColor[((sq >> 4) + (sq & 7)) & 1]++; }
        else if (t === KNIGHT) { minors++; knights++; }
        else other++;
      }
      if (other) return false;
      if (minors <= 1) return true;
      if (knights === 0 && (bishopsColor[0] === 0 || bishopsColor[1] === 0)) return true;
      return false;
    }

    status() {
      const legal = this.legalMoves();
      if (!legal.length) {
        if (this.inCheck()) return { over: true, reason: 'checkmate', winner: this.side ^ 1 };
        return { over: true, reason: 'stalemate', winner: -1 };
      }
      if (this.insufficientMaterial()) return { over: true, reason: 'insufficient', winner: -1 };
      if (this.half >= 100) return { over: true, reason: 'fifty', winner: -1 };
      if (this.repetitions() >= 2) return { over: true, reason: 'repetition', winner: -1 };
      return { over: false, legal };
    }

    moveFromUci(u) {
      if (!u || u.length < 4) return 0;
      const from = sqFromName(u.slice(0, 2)), to = sqFromName(u.slice(2, 4));
      const promo = u.length > 4 ? PIECE_CH.indexOf(u[4].toLowerCase()) : 0;
      for (const m of this.legalMoves()) {
        if (mFrom(m) === from && mTo(m) === to && (mPromo(m) || 0) === (promo > 0 ? promo : 0)) return m;
      }
      return 0;
    }

    san(m, legal) {
      legal = legal || this.legalMoves();
      const from = mFrom(m), to = mTo(m), fl = mFlags(m);
      const piece = this.board[from], t = piece & 7;
      let s;
      if (fl & F_CASTLE) s = to > from ? 'O-O' : 'O-O-O';
      else {
        s = '';
        if (t === PAWN) {
          if (fl & F_CAPTURE) s += 'abcdefgh'[from & 7] + 'x';
          s += sqName(to);
          if (fl & F_PROMO) s += '=' + PIECE_CH[mPromo(m)].toUpperCase();
        } else {
          s += PIECE_CH[t].toUpperCase();
          const others = legal.filter(o => o !== m && mTo(o) === to && this.board[mFrom(o)] === piece);
          if (others.length) {
            const sameFile = others.some(o => (mFrom(o) & 7) === (from & 7));
            const sameRank = others.some(o => (mFrom(o) >> 4) === (from >> 4));
            if (!sameFile) s += 'abcdefgh'[from & 7];
            else if (!sameRank) s += (from >> 4) + 1;
            else s += sqName(from);
          }
          if (fl & F_CAPTURE) s += 'x';
          s += sqName(to);
        }
      }
      this.make(m);
      if (this.inCheck()) s += this.legalMoves().length ? '+' : '#';
      this.unmake();
      return s;
    }
  }

  const toUci = m => sqName(mFrom(m)) + sqName(mTo(m)) + (mPromo(m) ? PIECE_CH[mPromo(m)] : '');

  /* ---------- Static exchange evaluation ---------- */
  // Least valuable attacker of sq for side on raw board b (ignores pins). Returns square or -1.
  function leastAttacker(b, sq, side) {
    let s;
    if (side === WHITE) {
      s = sq - 15; if (!(s & 0x88) && b[s] === 1) return s;
      s = sq - 17; if (!(s & 0x88) && b[s] === 1) return s;
    } else {
      s = sq + 15; if (!(s & 0x88) && b[s] === 9) return s;
      s = sq + 17; if (!(s & 0x88) && b[s] === 9) return s;
    }
    const off = side << 3;
    for (let i = 0; i < 8; i++) { s = sq + KNIGHT_D[i]; if (!(s & 0x88) && b[s] === (off | KNIGHT)) return s; }
    let bishop = -1, rook = -1, queen = -1;
    for (let i = 0; i < 4; i++) {
      let d = BISHOP_D[i]; s = sq + d;
      while (!(s & 0x88)) {
        const p = b[s];
        if (p) { if (p === (off | BISHOP) && bishop < 0) bishop = s; else if (p === (off | QUEEN) && queen < 0) queen = s; break; }
        s += d;
      }
      d = ROOK_D[i]; s = sq + d;
      while (!(s & 0x88)) {
        const p = b[s];
        if (p) { if (p === (off | ROOK) && rook < 0) rook = s; else if (p === (off | QUEEN) && queen < 0) queen = s; break; }
        s += d;
      }
    }
    if (bishop >= 0) return bishop;
    if (rook >= 0) return rook;
    if (queen >= 0) return queen;
    for (let i = 0; i < 8; i++) { s = sq + KING_D[i]; if (!(s & 0x88) && b[s] === (off | KING)) return s; }
    return -1;
  }

  // All attackers of sq for side (direct, ignoring pins)
  function attackersOf(b, sq, side) {
    const out = [];
    let s;
    if (side === WHITE) {
      s = sq - 15; if (!(s & 0x88) && b[s] === 1) out.push(s);
      s = sq - 17; if (!(s & 0x88) && b[s] === 1) out.push(s);
    } else {
      s = sq + 15; if (!(s & 0x88) && b[s] === 9) out.push(s);
      s = sq + 17; if (!(s & 0x88) && b[s] === 9) out.push(s);
    }
    const off = side << 3;
    for (let i = 0; i < 8; i++) {
      s = sq + KNIGHT_D[i]; if (!(s & 0x88) && b[s] === (off | KNIGHT)) out.push(s);
      s = sq + KING_D[i]; if (!(s & 0x88) && b[s] === (off | KING)) out.push(s);
    }
    for (let i = 0; i < 4; i++) {
      let d = BISHOP_D[i]; s = sq + d;
      while (!(s & 0x88)) { const p = b[s]; if (p) { if (p === (off | BISHOP) || p === (off | QUEEN)) out.push(s); break; } s += d; }
      d = ROOK_D[i]; s = sq + d;
      while (!(s & 0x88)) { const p = b[s]; if (p) { if (p === (off | ROOK) || p === (off | QUEEN)) out.push(s); break; } s += d; }
    }
    return out;
  }

  // Expected material gain for the side making capture from->to (positive = good for mover)
  function see(pos, from, to, epCapture) {
    const b = pos.board;
    const removed = [];
    const gain = [];
    let d = 0;
    const attacker = b[from];
    gain[0] = epCapture ? 100 : (b[to] ? SEE_VAL[b[to] & 7] : 0);
    let onSq = SEE_VAL[attacker & 7];
    let side = (attacker >> 3) ^ 1;
    removed.push(from, b[from]); b[from] = 0;
    let epSq = -1;
    if (epCapture) { epSq = to + ((attacker >> 3) === WHITE ? -16 : 16); removed.push(epSq, b[epSq]); b[epSq] = 0; }
    const victim = b[to];
    b[to] = attacker; // the attacker now stands on the square
    for (;;) {
      const s = leastAttacker(b, to, side);
      if (s < 0) break;
      d++;
      gain[d] = onSq - gain[d - 1];
      onSq = SEE_VAL[b[s] & 7];
      b[to] = b[s];
      removed.push(s, b[s]); b[s] = 0;
      side ^= 1;
    }
    b[to] = victim;
    for (let i = removed.length - 2; i >= 0; i -= 2) b[removed[i]] = removed[i + 1];
    for (; d > 0; d--) gain[d - 1] = -Math.max(-gain[d - 1], gain[d]);
    return gain[0];
  }

  // How much `side` would lose if the opponent captured on sq (best capture by SEE), 0 if safe
  function threatOn(pos, sq) {
    const b = pos.board, pc = b[sq];
    if (!pc) return 0;
    const owner = pc >> 3, enemy = owner ^ 1;
    let best = 0;
    for (const a of attackersOf(b, sq, enemy)) {
      if ((b[a] & 7) === KING && attackersOf(b, sq, owner).length) continue;
      const g = see(pos, a, sq, false);
      if (g > best) best = g;
    }
    return best;
  }

  /* ---------- Evaluation ---------- */
  // Piece-square tables (a8 first), simplified evaluation function values
  const T = {
    P: [0, 0, 0, 0, 0, 0, 0, 0, 50, 50, 50, 50, 50, 50, 50, 50, 10, 10, 20, 30, 30, 20, 10, 10, 5, 5, 10, 25, 25, 10, 5, 5, 0, 0, 0, 20, 20, 0, 0, 0, 5, -5, -10, 0, 0, -10, -5, 5, 5, 10, 10, -20, -20, 10, 10, 5, 0, 0, 0, 0, 0, 0, 0, 0],
    PE: [0, 0, 0, 0, 0, 0, 0, 0, 80, 80, 80, 80, 80, 80, 80, 80, 50, 50, 50, 50, 50, 50, 50, 50, 30, 30, 30, 30, 30, 30, 30, 30, 15, 15, 15, 15, 15, 15, 15, 15, 5, 5, 5, 5, 5, 5, 5, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
    N: [-50, -40, -30, -30, -30, -30, -40, -50, -40, -20, 0, 0, 0, 0, -20, -40, -30, 0, 10, 15, 15, 10, 0, -30, -30, 5, 15, 20, 20, 15, 5, -30, -30, 0, 15, 20, 20, 15, 0, -30, -30, 5, 10, 15, 15, 10, 5, -30, -40, -20, 0, 5, 5, 0, -20, -40, -50, -40, -30, -30, -30, -30, -40, -50],
    B: [-20, -10, -10, -10, -10, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 10, 10, 5, 0, -10, -10, 5, 5, 10, 10, 5, 5, -10, -10, 0, 10, 10, 10, 10, 0, -10, -10, 10, 10, 10, 10, 10, 10, -10, -10, 5, 0, 0, 0, 0, 5, -10, -20, -10, -10, -10, -10, -10, -10, -20],
    R: [0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 10, 10, 10, 10, 10, 5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, 0, 0, 0, 5, 5, 0, 0, 0],
    Q: [-20, -10, -10, -5, -5, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 5, 5, 5, 0, -10, -5, 0, 5, 5, 5, 5, 0, -5, 0, 0, 5, 5, 5, 5, 0, -5, -10, 5, 5, 5, 5, 5, 0, -10, -10, 0, 5, 0, 0, 0, 0, -10, -20, -10, -10, -5, -5, -10, -10, -20],
    K: [-30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -20, -30, -30, -40, -40, -30, -30, -20, -10, -20, -20, -20, -20, -20, -20, -10, 20, 20, 0, 0, 0, 0, 20, 20, 20, 30, 10, 0, 0, 10, 30, 20],
    KE: [-50, -40, -30, -20, -20, -30, -40, -50, -30, -20, -10, 0, 0, -10, -20, -30, -30, -10, 20, 30, 30, 20, -10, -30, -30, -10, 30, 40, 40, 30, -10, -30, -30, -10, 30, 40, 40, 30, -10, -30, -30, -10, 20, 30, 30, 20, -10, -30, -30, -30, 0, 0, 0, 0, -30, -30, -50, -30, -30, -30, -30, -30, -30, -50]
  };
  const MAT_MG = [0, 100, 320, 330, 500, 900, 0];
  const MAT_EG = [0, 120, 300, 320, 530, 950, 0];
  const PHASE_INC = [0, 0, 1, 1, 2, 4, 0];
  const PST_MG = [], PST_EG = [];
  (function buildPst() {
    const mgT = [null, T.P, T.N, T.B, T.R, T.Q, T.K];
    const egT = [null, T.PE, T.N, T.B, T.R, T.Q, T.KE];
    for (let pc = 0; pc < 16; pc++) { PST_MG[pc] = new Int16Array(128); PST_EG[pc] = new Int16Array(128); }
    for (let t = 1; t <= 6; t++) {
      for (let r = 0; r < 8; r++) for (let f = 0; f < 8; f++) {
        const sq = r * 16 + f;
        const wi = (7 - r) * 8 + f, bi = r * 8 + f;
        PST_MG[t][sq] = MAT_MG[t] + mgT[t][wi]; PST_EG[t][sq] = MAT_EG[t] + egT[t][wi];
        PST_MG[8 | t][sq] = MAT_MG[t] + mgT[t][bi]; PST_EG[8 | t][sq] = MAT_EG[t] + egT[t][bi];
      }
    }
  })();
  const PASSED_MG = [0, 5, 10, 15, 25, 40, 60, 0];
  const PASSED_EG = [0, 10, 20, 35, 60, 100, 150, 0];

  function evaluate(pos) {
    const b = pos.board;
    let mgW = 0, mgB = 0, egW = 0, egB = 0, phase = 0;
    const pawnsW = new Int8Array(8), pawnsB = new Int8Array(8);
    const maxRankB = [-1, -1, -1, -1, -1, -1, -1, -1], minRankW = [8, 8, 8, 8, 8, 8, 8, 8];
    const maxRankW = [-1, -1, -1, -1, -1, -1, -1, -1], minRankB = [8, 8, 8, 8, 8, 8, 8, 8];
    let bishW = 0, bishB = 0, npmW = 0, npmB = 0;
    const rooks = [];
    for (let sq = 0; sq < 128; sq++) {
      if (sq & 0x88) { sq += 7; continue; }
      const p = b[sq]; if (!p) continue;
      const t = p & 7, c = p >> 3;
      if (c === WHITE) { mgW += PST_MG[p][sq]; egW += PST_EG[p][sq]; }
      else { mgB += PST_MG[p][sq]; egB += PST_EG[p][sq]; }
      phase += PHASE_INC[t];
      const f = sq & 7, r = sq >> 4;
      if (t === PAWN) {
        if (c === WHITE) { pawnsW[f]++; if (r > maxRankW[f]) maxRankW[f] = r; if (r < minRankW[f]) minRankW[f] = r; }
        else { pawnsB[f]++; if (r > maxRankB[f]) maxRankB[f] = r; if (r < minRankB[f]) minRankB[f] = r; }
      } else if (t !== KING) {
        if (c === WHITE) npmW += MAT_MG[t]; else npmB += MAT_MG[t];
        if (t === BISHOP) { if (c === WHITE) bishW++; else bishB++; }
        else if (t === ROOK) rooks.push(sq, c);
      }
    }
    if (phase > 24) phase = 24;
    // pawn structure
    for (let f = 0; f < 8; f++) {
      if (pawnsW[f]) {
        if (pawnsW[f] > 1) { mgW -= 10 * (pawnsW[f] - 1); egW -= 20 * (pawnsW[f] - 1); }
        if ((f === 0 || !pawnsW[f - 1]) && (f === 7 || !pawnsW[f + 1])) { mgW -= 10 * pawnsW[f]; egW -= 15 * pawnsW[f]; }
        // passed: most advanced white pawn on this file
        const r = maxRankW[f];
        let passed = true;
        for (let ff = Math.max(0, f - 1); ff <= Math.min(7, f + 1); ff++) if (maxRankB[ff] > r) { passed = false; break; }
        if (passed) { mgW += PASSED_MG[r]; egW += PASSED_EG[r]; }
      }
      if (pawnsB[f]) {
        if (pawnsB[f] > 1) { mgB -= 10 * (pawnsB[f] - 1); egB -= 20 * (pawnsB[f] - 1); }
        if ((f === 0 || !pawnsB[f - 1]) && (f === 7 || !pawnsB[f + 1])) { mgB -= 10 * pawnsB[f]; egB -= 15 * pawnsB[f]; }
        const r = minRankB[f];
        let passed = true;
        for (let ff = Math.max(0, f - 1); ff <= Math.min(7, f + 1); ff++) if (minRankW[ff] < r) { passed = false; break; }
        if (passed) { mgB += PASSED_MG[7 - r]; egB += PASSED_EG[7 - r]; }
      }
    }
    if (bishW >= 2) { mgW += 30; egW += 50; }
    if (bishB >= 2) { mgB += 30; egB += 50; }
    for (let i = 0; i < rooks.length; i += 2) {
      const f = rooks[i] & 7, c = rooks[i + 1];
      const own = c === WHITE ? pawnsW[f] : pawnsB[f], opp = c === WHITE ? pawnsB[f] : pawnsW[f];
      if (!own) { const bonusMg = opp ? 10 : 20, bonusEg = opp ? 5 : 10; if (c === WHITE) { mgW += bonusMg; egW += bonusEg; } else { mgB += bonusMg; egB += bonusEg; } }
    }
    // king shelter (middlegame)
    if (phase > 10) {
      mgW += shelter(b, pos.kings[WHITE], WHITE);
      mgB += shelter(b, pos.kings[BLACK], BLACK);
    }
    let mg = mgW - mgB, eg = egW - egB;
    let score = ((mg * phase) + (eg * (24 - phase))) / 24;
    // mop-up: help the stronger side drive the lone king to the edge
    const matW = npmW, matB = npmB;
    if (phase < 12 && Math.abs(score) > 250) {
      const strong = score > 0 ? WHITE : BLACK;
      const wk = pos.kings[strong ^ 1], sk = pos.kings[strong];
      const wf = wk & 7, wr = wk >> 4;
      const centerDist = Math.max(3 - wf, wf - 4) + Math.max(3 - wr, wr - 4);
      const kd = Math.abs((wk & 7) - (sk & 7)) + Math.abs((wk >> 4) - (sk >> 4));
      const bonus = (centerDist * 10 + (14 - kd) * 4) * (24 - phase) / 24;
      score += strong === WHITE ? bonus : -bonus;
    }
    // drawish material
    const strong = score > 0 ? WHITE : BLACK;
    const strongPawns = strong === WHITE ? pawnsW.reduce((a, x) => a + x, 0) : pawnsB.reduce((a, x) => a + x, 0);
    if (strongPawns === 0) {
      const diff = strong === WHITE ? matW - matB : matB - matW;
      if (diff < 400) score /= 8;
    }
    score = Math.round(score);
    return (pos.side === WHITE ? score : -score) + 10;
  }

  function shelter(b, k, c) {
    const kr = k >> 4, kf = k & 7;
    if (c === WHITE ? kr > 1 : kr < 6) return -15;
    if (kf >= 3 && kf <= 4) return -10;
    let s = 0;
    const pawn = (c << 3) | PAWN, dir = c === WHITE ? 16 : -16;
    for (let f = Math.max(0, kf - 1); f <= Math.min(7, kf + 1); f++) {
      const s1 = k - kf + f + dir, s2 = s1 + dir;
      if (!(s1 & 0x88) && b[s1] === pawn) continue;
      if (!(s2 & 0x88) && b[s2] === pawn) { s -= 6; continue; }
      s -= 18;
    }
    return s;
  }

  /* ---------- Search ---------- */
  const INF = 1000000, MATE = 100000, MATE_BOUND = MATE - 1000, MAXPLY = 96;
  const TT_EXACT = 1, TT_LOWER = 2, TT_UPPER = 3;
  const now = () => (typeof performance !== 'undefined' ? performance.now() : Date.now());

  class Searcher {
    constructor(ttBits) {
      ttBits = ttBits || 19;
      this.ttSize = 1 << ttBits; this.mask = this.ttSize - 1;
      this.ttLo = new Int32Array(this.ttSize); this.ttHi = new Int32Array(this.ttSize);
      this.ttMove = new Int32Array(this.ttSize); this.ttScore = new Int32Array(this.ttSize);
      this.ttDepth = new Int8Array(this.ttSize); this.ttFlag = new Uint8Array(this.ttSize);
      this.killers = new Int32Array(MAXPLY * 2);
      this.history = new Int32Array(16 * 128);
      this.pv = []; for (let i = 0; i < MAXPLY + 1; i++) this.pv.push(new Int32Array(MAXPLY + 1));
      this.pvLen = new Int32Array(MAXPLY + 1);
      this.useQ = true;
      this.nodes = 0; this.stop = false; this.deadline = Infinity; this.ignoreTime = false; this.nodeLimit = Infinity;
    }

    clearTT() { this.ttFlag.fill(0); this.history.fill(0); this.killers.fill(0); }

    ttStore(pos, depth, flag, score, move, ply) {
      const i = pos.hlo & this.mask;
      if (this.ttFlag[i] && this.ttLo[i] === pos.hlo && this.ttHi[i] === pos.hhi && this.ttDepth[i] > depth && flag !== TT_EXACT) return;
      if (score > MATE_BOUND) score += ply; else if (score < -MATE_BOUND) score -= ply;
      this.ttLo[i] = pos.hlo; this.ttHi[i] = pos.hhi; this.ttMove[i] = move; this.ttScore[i] = score;
      this.ttDepth[i] = depth; this.ttFlag[i] = flag;
    }

    ttProbeMove(pos) {
      const i = pos.hlo & this.mask;
      return (this.ttFlag[i] && this.ttLo[i] === pos.hlo && this.ttHi[i] === pos.hhi) ? this.ttMove[i] : 0;
    }

    checkTime() {
      if (this.nodes >= this.nodeLimit) { this.stop = true; return; }
      if (!this.ignoreTime && now() > this.deadline) this.stop = true;
    }

    orderScores(pos, moves, ttMove, ply) {
      const b = pos.board, scores = new Array(moves.length);
      const k1 = this.killers[ply * 2], k2 = this.killers[ply * 2 + 1];
      for (let i = 0; i < moves.length; i++) {
        const m = moves[i];
        if (m === ttMove) { scores[i] = 10000000; continue; }
        const fl = mFlags(m);
        if (fl & (F_CAPTURE | F_PROMO)) {
          const victim = (fl & F_EP) ? PAWN : (b[mTo(m)] & 7);
          const attacker = b[mFrom(m)] & 7;
          scores[i] = 1000000 + victim * 100 - attacker + ((fl & F_PROMO) ? mPromo(m) * 1000 : 0);
        } else if (m === k1) scores[i] = 900000;
        else if (m === k2) scores[i] = 890000;
        else scores[i] = Math.min(800000, this.history[b[mFrom(m)] * 128 + mTo(m)]);
      }
      return scores;
    }

    qsearch(pos, alpha, beta, ply) {
      if ((++this.nodes & 1023) === 0) this.checkTime();
      if (this.stop) return 0;
      this.pvLen[ply] = ply;
      if (ply >= MAXPLY) return evaluate(pos);
      const inCheck = pos.inCheck();
      let best, stand = 0;
      if (!inCheck) {
        stand = evaluate(pos);
        if (stand >= beta) return stand;
        if (stand > alpha) alpha = stand;
        best = stand;
      } else best = -MATE + ply;
      const moves = pos.genMoves([], !inCheck);
      const scores = this.orderScores(pos, moves, 0, ply);
      const us = pos.side, them = us ^ 1, b = pos.board;
      let legal = 0;
      for (let i = 0; i < moves.length; i++) {
        let bi = i;
        for (let j = i + 1; j < moves.length; j++) if (scores[j] > scores[bi]) bi = j;
        const m = moves[bi]; moves[bi] = moves[i]; moves[i] = m;
        const sc = scores[bi]; scores[bi] = scores[i]; scores[i] = sc;
        const fl = mFlags(m);
        if (!inCheck && !(fl & F_PROMO)) {
          const victim = (fl & F_EP) ? PAWN : (b[mTo(m)] & 7);
          if (stand + VAL[victim] + 200 < alpha) continue;
          const attacker = b[mFrom(m)] & 7;
          if (VAL[attacker] > VAL[victim] && see(pos, mFrom(m), mTo(m), !!(fl & F_EP)) < 0) continue;
        }
        pos.make(m);
        if (pos.attacked(pos.kings[us], them)) { pos.unmake(); continue; }
        legal++;
        const score = -this.qsearch(pos, -beta, -alpha, ply + 1);
        pos.unmake();
        if (this.stop) return 0;
        if (score > best) {
          best = score;
          if (score > alpha) {
            alpha = score;
            this.updatePv(ply, m);
            if (score >= beta) break;
          }
        }
      }
      if (inCheck && !legal) return -MATE + ply;
      return best;
    }

    updatePv(ply, m) {
      const row = this.pv[ply], child = this.pv[ply + 1];
      row[ply] = m;
      const len = this.pvLen[ply + 1];
      for (let j = ply + 1; j < len; j++) row[j] = child[j];
      this.pvLen[ply] = Math.max(len, ply + 1);
    }

    negamax(pos, depth, alpha, beta, ply, allowNull) {
      if ((++this.nodes & 1023) === 0) this.checkTime();
      if (this.stop) return 0;
      this.pvLen[ply] = ply;
      const isPv = beta - alpha > 1;
      if (ply > 0) {
        if (pos.half >= 100 || pos.repetitions() >= 1) return 0;
        const ma = Math.max(alpha, -MATE + ply), mb = Math.min(beta, MATE - ply - 1);
        if (ma >= mb) return ma;
      }
      if (ply >= MAXPLY - 2) return evaluate(pos);
      const inCheck = pos.inCheck();
      if (inCheck) depth++;
      if (depth <= 0) return this.useQ ? this.qsearch(pos, alpha, beta, ply) : evaluate(pos);

      const ti = pos.hlo & this.mask;
      let ttMove = 0;
      if (this.ttFlag[ti] && this.ttLo[ti] === pos.hlo && this.ttHi[ti] === pos.hhi) {
        ttMove = this.ttMove[ti];
        if (ply > 0 && !isPv && this.ttDepth[ti] >= depth) {
          let s = this.ttScore[ti];
          if (s > MATE_BOUND) s -= ply; else if (s < -MATE_BOUND) s += ply;
          const fl = this.ttFlag[ti];
          if (fl === TT_EXACT || (fl === TT_LOWER && s >= beta) || (fl === TT_UPPER && s <= alpha)) return s;
        }
      }

      const us = pos.side, them = us ^ 1;
      if (!isPv && !inCheck && ply > 0) {
        const sEval = evaluate(pos);
        if (depth <= 2 && sEval - 120 * depth >= beta && Math.abs(beta) < MATE_BOUND) return sEval;
        if (allowNull && depth >= 3 && sEval >= beta && this.hasPieces(pos, us)) {
          pos.makeNull();
          const s = -this.negamax(pos, depth - 3, -beta, -beta + 1, ply + 1, false);
          pos.unmake();
          if (this.stop) return 0;
          if (s >= beta && s < MATE_BOUND) return beta;
        }
      }

      const moves = pos.genMoves([], false);
      const scores = this.orderScores(pos, moves, ttMove, ply);
      let legal = 0, best = -INF, bestMove = 0;
      const origAlpha = alpha;
      for (let i = 0; i < moves.length; i++) {
        let bi = i;
        for (let j = i + 1; j < moves.length; j++) if (scores[j] > scores[bi]) bi = j;
        const m = moves[bi]; moves[bi] = moves[i]; moves[i] = m;
        const sc = scores[bi]; scores[bi] = scores[i]; scores[i] = sc;
        pos.make(m);
        if (pos.attacked(pos.kings[us], them)) { pos.unmake(); continue; }
        legal++;
        const fl = mFlags(m);
        const quiet = !(fl & (F_CAPTURE | F_PROMO));
        let score;
        if (legal === 1) score = -this.negamax(pos, depth - 1, -beta, -alpha, ply + 1, true);
        else {
          let r = 0;
          if (depth >= 3 && quiet && legal > 3 && !inCheck && sc < 890000 && !pos.inCheck()) r = legal > 10 && depth >= 5 ? 2 : 1;
          score = -this.negamax(pos, depth - 1 - r, -alpha - 1, -alpha, ply + 1, true);
          if (score > alpha && r && !this.stop) score = -this.negamax(pos, depth - 1, -alpha - 1, -alpha, ply + 1, true);
          if (score > alpha && score < beta && !this.stop) score = -this.negamax(pos, depth - 1, -beta, -alpha, ply + 1, true);
        }
        pos.unmake();
        if (this.stop) return 0;
        if (score > best) {
          best = score; bestMove = m;
          if (score > alpha) {
            alpha = score;
            this.updatePv(ply, m);
            if (ply === 0) { this.rootBest = m; this.rootScore = score; this.rootPv = Array.from(this.pv[0].subarray(0, this.pvLen[0])); }
            if (score >= beta) {
              if (quiet) {
                if (this.killers[ply * 2] !== m) { this.killers[ply * 2 + 1] = this.killers[ply * 2]; this.killers[ply * 2] = m; }
                const hi = pos.board[mFrom(m)] * 128 + mTo(m);
                this.history[hi] += depth * depth;
              }
              break;
            }
          }
        }
      }
      if (!legal) return inCheck ? -MATE + ply : 0;
      this.ttStore(pos, depth, best <= origAlpha ? TT_UPPER : best >= beta ? TT_LOWER : TT_EXACT, best, bestMove, ply);
      return best;
    }

    hasPieces(pos, side) {
      const b = pos.board;
      for (let sq = 0; sq < 128; sq++) {
        if (sq & 0x88) { sq += 7; continue; }
        const p = b[sq];
        if (p && (p >> 3) === side) { const t = p & 7; if (t !== PAWN && t !== KING) return true; }
      }
      return false;
    }

    prepare(opts) {
      this.nodes = 0; this.stop = false;
      this.start = now();
      this.deadline = this.start + (opts.timeMs || 1000);
      this.nodeLimit = opts.nodeLimit || 5e7;
      this.killers.fill(0);
      for (let i = 0; i < this.history.length; i++) this.history[i] >>= 2;
    }

    // Best move by iterative deepening
    think(pos, opts) {
      opts = opts || {};
      this.prepare(opts);
      const maxDepth = opts.maxDepth || 64;
      const minDepth = opts.minDepth || 1;
      const legal = pos.legalMoves();
      if (!legal.length) return { move: 0, score: pos.inCheck() ? -MATE : 0, depth: 0, pv: [], nodes: 0 };
      let result = { move: legal[0], score: 0, depth: 0, pv: [legal[0]] };
      for (let d = 1; d <= maxDepth; d++) {
        this.ignoreTime = d <= minDepth;
        this.rootBest = 0; this.rootPv = null;
        const score = this.negamax(pos, d, -INF, INF, 0, false);
        if (this.stop) {
          if (this.rootBest && this.rootPv) result = { move: this.rootBest, score: this.rootScore, depth: d, pv: this.rootPv, partial: true };
          break;
        }
        result = { move: this.pv[0][0] || legal[0], score, depth: d, pv: Array.from(this.pv[0].subarray(0, this.pvLen[0])) };
        if (Math.abs(score) > MATE_BOUND && d >= 2 * (MATE - Math.abs(score)) + 2) break;
        if (legal.length === 1 && d >= 4) break;
        if (now() - this.start > (opts.timeMs || 1000) * 0.55 && d >= minDepth) break;
      }
      result.nodes = this.nodes;
      if (!result.pv.length || result.pv[0] !== result.move) result.pv = [result.move];
      return result;
    }

    // Score every root move (for the coach and for human-like bots)
    analyze(pos, opts) {
      opts = opts || {};
      this.prepare(opts);
      const maxDepth = opts.maxDepth || 64, minDepth = opts.minDepth || 1;
      const margin = opts.margin || 500;
      const legal = pos.legalMoves();
      if (!legal.length) return { depth: 0, moves: [] };
      let completed = legal.map(m => ({ move: m, score: 0, pv: [m], bound: 'exact' }));
      let completedDepth = 0;
      for (let d = 1; d <= maxDepth; d++) {
        this.ignoreTime = d <= minDepth;
        const iter = [];
        let bestSoFar = -INF;
        let aborted = false;
        for (const entry of completed) {
          const m = entry.move;
          let alpha = -INF;
          if (bestSoFar > -INF && Math.abs(bestSoFar) < MATE_BOUND) alpha = bestSoFar - margin;
          pos.make(m);
          this.pvLen[1] = 1;
          let s = -this.negamax(pos, d - 1, -INF, -alpha, 1, true);
          let pv;
          if (!this.stop) {
            pv = [m];
            for (let j = 1; j < this.pvLen[1]; j++) pv.push(this.pv[1][j]);
            if (pv.length < 2) pv = [m].concat(this.ttLine(pos, 6));
          }
          pos.unmake();
          if (this.stop) { aborted = true; break; }
          const bound = alpha > -INF && s <= alpha ? 'upper' : 'exact';
          iter.push({ move: m, score: s, pv, bound });
          if (s > bestSoFar) bestSoFar = s;
        }
        if (aborted) break;
        iter.sort((a, b) => b.score - a.score);
        completed = iter; completedDepth = d;
        if (Math.abs(iter[0].score) > MATE_BOUND && d >= 2 * (MATE - Math.abs(iter[0].score)) + 2) break;
        if (now() - this.start > (opts.timeMs || 1000) * 0.45 && d >= minDepth) break;
      }
      return { depth: completedDepth, moves: completed, nodes: this.nodes };
    }

    ttLine(pos, n) {
      const line = [];
      let made = 0;
      for (let i = 0; i < n; i++) {
        const m = this.ttProbeMove(pos);
        if (!m) break;
        const legal = pos.legalMoves();
        if (!legal.includes(m)) break;
        line.push(m); pos.make(m); made++;
      }
      while (made--) pos.unmake();
      return line;
    }
  }

  /* ---------- Opening knowledge ---------- */
  const BOOK = [
    ['Italian Game', 'e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d4 e5d4 c3d4 c5b4'],
    ['Italian Game: Quiet Line', 'e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 d2d3 g8f6 e1g1 d7d6 c2c3 e8g8'],
    ['Ruy López', 'e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7 f1e1 b7b5 a4b3 d7d6'],
    ['Scotch Game', 'e2e4 e7e5 g1f3 b8c6 d2d4 e5d4 f3d4 g8f6 d4c6 b7c6'],
    ['Two Knights Defense', 'e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 d2d3 f8e7 e1g1 e8g8'],
    ['Petrov\'s Defense', 'e2e4 e7e5 g1f3 g8f6 f3e5 d7d6 e5f3 f6e4 d2d4 d6d5'],
    ['Vienna Game', 'e2e4 e7e5 b1c3 g8f6 f1c4 b8c6 d2d3 f8c5'],
    ['Four Knights Game', 'e2e4 e7e5 g1f3 b8c6 b1c3 g8f6 f1b5 f8b4 e1g1 e8g8'],
    ['Sicilian Defense: Najdorf', 'e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6'],
    ['Sicilian Defense: Open', 'e2e4 c7c5 g1f3 b8c6 d2d4 c5d4 f3d4 g8f6 b1c3 e7e5'],
    ['Closed Sicilian', 'e2e4 c7c5 b1c3 b8c6 g2g3 g7g6 f1g2 f8g7'],
    ['French Defense', 'e2e4 e7e6 d2d4 d7d5 b1c3 g8f6 c1g5 f8e7 e4e5 f6d7'],
    ['Caro-Kann Defense', 'e2e4 c7c6 d2d4 d7d5 b1c3 d5e4 c3e4 c8f5 e4g3 f5g6'],
    ['Scandinavian Defense', 'e2e4 d7d5 e4d5 d8d5 b1c3 d5a5 d2d4 g8f6'],
    ['Queen\'s Gambit Declined', 'd2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5 f8e7 e2e3 e8g8'],
    ['Slav Defense', 'd2d4 d7d5 c2c4 c7c6 g1f3 g8f6 b1c3 d5c4'],
    ['King\'s Indian Defense', 'd2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6 g1f3 e8g8'],
    ['Nimzo-Indian Defense', 'd2d4 g8f6 c2c4 e7e6 b1c3 f8b4 e2e3 e8g8'],
    ['London System', 'd2d4 d7d5 c1f4 g8f6 e2e3 e7e6 g1f3 c7c5'],
    ['English Opening', 'c2c4 e7e5 b1c3 g8f6 g1f3 b8c6 g2g3 d7d5'],
    ['Réti Opening', 'g1f3 d7d5 g2g3 g8f6 f1g2 e7e6 e1g1 f8e7']
  ].map(([name, line]) => ({ name, moves: line.split(' ') }));

  const OPENING_NAMES = [
    ['e2e4', 'King\'s Pawn Opening'], ['d2d4', 'Queen\'s Pawn Opening'], ['c2c4', 'English Opening'], ['g1f3', 'Réti Opening'],
    ['f2f4', 'Bird\'s Opening'], ['b2b3', 'Nimzo-Larsen Attack'], ['g2g3', 'King\'s Fianchetto Opening'], ['b1c3', 'Van Geet Opening'],
    ['e2e3', 'Van \'t Kruijs Opening'], ['d2d3', 'Mieses Opening'], ['a2a3', 'Anderssen\'s Opening'], ['h2h4', 'Desprez Opening'],
    ['g2g4', 'Grob\'s Attack'], ['b2b4', 'Polish Opening'], ['f2f3', 'Barnes Opening'], ['h2h3', 'Clemenz Opening'], ['a2a4', 'Ware Opening'],
    ['e2e4 e7e5', 'King\'s Pawn Game'], ['e2e4 c7c5', 'Sicilian Defense'], ['e2e4 e7e6', 'French Defense'], ['e2e4 c7c6', 'Caro-Kann Defense'],
    ['e2e4 d7d5', 'Scandinavian Defense'], ['e2e4 d7d6', 'Pirc Defense'], ['e2e4 g8f6', 'Alekhine\'s Defense'], ['e2e4 b8c6', 'Nimzowitsch Defense'],
    ['e2e4 g7g6', 'Modern Defense'], ['e2e4 a7a6', 'St. George Defense'], ['e2e4 h7h6', 'Carr Defense'], ['e2e4 f7f6', 'Barnes Defense'],
    ['e2e4 e7e5 g1f3', 'King\'s Knight Opening'], ['e2e4 e7e5 g1f3 b8c6', 'King\'s Knight Opening: Normal'],
    ['e2e4 e7e5 g1f3 b8c6 f1c4', 'Italian Game'], ['e2e4 e7e5 g1f3 b8c6 f1c4 f8c5', 'Italian Game: Giuoco Piano'],
    ['e2e4 e7e5 g1f3 b8c6 f1c4 g8f6', 'Two Knights Defense'], ['e2e4 e7e5 g1f3 b8c6 f1b5', 'Ruy López'],
    ['e2e4 e7e5 g1f3 b8c6 d2d4', 'Scotch Game'], ['e2e4 e7e5 g1f3 b8c6 b1c3', 'Three Knights Game'], ['e2e4 e7e5 g1f3 b8c6 b1c3 g8f6', 'Four Knights Game'],
    ['e2e4 e7e5 g1f3 g8f6', 'Petrov\'s Defense'], ['e2e4 e7e5 g1f3 d7d6', 'Philidor Defense'], ['e2e4 e7e5 g1f3 f7f6', 'Damiano Defense'],
    ['e2e4 e7e5 f2f4', 'King\'s Gambit'], ['e2e4 e7e5 b1c3', 'Vienna Game'], ['e2e4 e7e5 f1c4', 'Bishop\'s Opening'],
    ['e2e4 e7e5 d1h5', 'Wayward Queen Attack'], ['e2e4 e7e5 d1h5 b8c6 f1c4 g8f6 h5f7', 'Scholar\'s Mate'],
    ['e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7', 'Scholar\'s Mate'], ['e2e4 e7e5 d1f3', 'Napoleon Attack'],
    ['e2e4 c7c5 g1f3', 'Sicilian Defense: Open'], ['e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6', 'Sicilian Defense: Najdorf'],
    ['e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 g7g6', 'Sicilian Defense: Dragon'], ['e2e4 c7c5 b1c3', 'Closed Sicilian'],
    ['e2e4 c7c5 c2c3', 'Sicilian Defense: Alapin'], ['e2e4 d7d5 e4d5 d8d5', 'Scandinavian Defense: Main Line'],
    ['e2e4 e7e6 d2d4 d7d5', 'French Defense: Main Line'], ['e2e4 c7c6 d2d4 d7d5', 'Caro-Kann Defense: Main Line'],
    ['d2d4 d7d5', 'Queen\'s Pawn Game'], ['d2d4 d7d5 c2c4', 'Queen\'s Gambit'], ['d2d4 d7d5 c2c4 d5c4', 'Queen\'s Gambit Accepted'],
    ['d2d4 d7d5 c2c4 e7e6', 'Queen\'s Gambit Declined'], ['d2d4 d7d5 c2c4 c7c6', 'Slav Defense'], ['d2d4 d7d5 c1f4', 'London System'],
    ['d2d4 d7d5 g1f3 g8f6 c1f4', 'London System'], ['d2d4 g8f6 c1f4', 'London System'], ['d2d4 g8f6', 'Indian Defense'],
    ['d2d4 g8f6 c2c4 g7g6', 'King\'s Indian Defense'], ['d2d4 g8f6 c2c4 g7g6 b1c3 d7d5', 'Grünfeld Defense'],
    ['d2d4 g8f6 c2c4 e7e6 b1c3 f8b4', 'Nimzo-Indian Defense'], ['d2d4 g8f6 c2c4 c7c5', 'Benoni Defense'], ['d2d4 f7f5', 'Dutch Defense'],
    ['d2d4 e7e6', 'Horwitz Defense'], ['c2c4 e7e5', 'English Opening: Reversed Sicilian'], ['f2f3 e7e5 g2g4 d8h4', 'Fool\'s Mate'],
    ['f2f3 e7e6 g2g4 d8h4', 'Fool\'s Mate']
  ].map(([line, name]) => ({ name, moves: line.split(' ') }));

  function openingName(ucis) {
    let best = null;
    for (const o of OPENING_NAMES.concat(BOOK)) {
      if (o.moves.length > ucis.length) continue;
      let ok = true;
      for (let i = 0; i < o.moves.length; i++) if (o.moves[i] !== ucis[i]) { ok = false; break; }
      if (ok && (!best || o.moves.length > best.moves.length)) best = o;
    }
    return best ? best.name : null;
  }

  function bookMoves(ucis) {
    const out = new Set();
    for (const o of BOOK) {
      if (o.moves.length <= ucis.length) continue;
      let ok = true;
      for (let i = 0; i < ucis.length; i++) if (o.moves[i] !== ucis[i]) { ok = false; break; }
      if (ok) out.add(o.moves[ucis.length]);
    }
    return Array.from(out);
  }

  /* ---------- Bots ---------- */
  function gaussian(rng) {
    let u = 0, v = 0;
    while (u === 0) u = rng();
    while (v === 0) v = rng();
    return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
  }

  // cfg: { random, captureBias, depth, q, noise, book, timeMs, full, mateMiss }
  function chooseBotMove(searcher, pos, cfg, history, startedFromStart) {
    const rng = Math.random;
    const legal = pos.legalMoves();
    if (!legal.length) return { move: 0 };
    if (legal.length === 1) return { move: legal[0], why: 'forced' };
    history = history || [];
    if (startedFromStart && cfg.book && history.length < cfg.book) {
      const cands = bookMoves(history).map(u => pos.moveFromUci(u)).filter(Boolean);
      if (cands.length) return { move: cands[Math.floor(rng() * cands.length)], why: 'book' };
    }
    if (cfg.random && rng() < cfg.random) {
      const caps = legal.filter(m => mFlags(m) & F_CAPTURE);
      const pool = caps.length && rng() < (cfg.captureBias || 0) ? caps : legal;
      return { move: pool[Math.floor(rng() * pool.length)], why: 'random' };
    }
    if (searcher.useQ !== !!cfg.q) { searcher.useQ = !!cfg.q; searcher.clearTT(); }
    if (cfg.full) {
      const r = searcher.think(pos, { timeMs: cfg.timeMs || 1500, maxDepth: cfg.maxDepth || 64, minDepth: 2 });
      return { move: r.move, score: r.score, depth: r.depth, why: 'search' };
    }
    const res = searcher.analyze(pos, { maxDepth: cfg.depth || 1, minDepth: Math.min(cfg.depth || 1, 2), timeMs: cfg.timeMs || 2500, margin: 300 });
    let bestM = res.moves[0].move, bestS = -Infinity;
    for (const e of res.moves) {
      let s = e.score;
      if (s > MATE_BOUND && cfg.mateMiss && rng() < cfg.mateMiss) s = 300;
      if (Math.abs(s) < MATE_BOUND) s += gaussian(rng) * (cfg.noise || 0);
      if (s > bestS) { bestS = s; bestM = e.move; }
    }
    return { move: bestM, score: res.moves[0].score, depth: res.depth, why: 'noisy' };
  }

  const API = {
    WHITE, BLACK, PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING, START_FEN,
    F_CAPTURE, F_DOUBLE, F_EP, F_CASTLE, F_PROMO,
    INF, MATE, MATE_BOUND, VAL,
    Position, Searcher, evaluate, see, threatOn, attackersOf, leastAttacker,
    mkMove, mFrom, mTo, mPromo, mFlags, toUci, sqName, sqFromName, fileOf, rankOf,
    KNIGHT_D, BISHOP_D, ROOK_D, KING_D,
    BOOK, openingName, bookMoves, chooseBotMove
  };
  global.MWEngine = API;
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
})(typeof self !== 'undefined' ? self : (typeof window !== 'undefined' ? window : globalThis));
