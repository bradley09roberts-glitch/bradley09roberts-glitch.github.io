/* Mosswood Chess: Sage Bramblewick, the coach.
   Turns engine numbers into plain-English explanations a beginner can act on. */
(function (global) {
  'use strict';
  const E = global.MWEngine, A = global.PieceArt;
  const { WHITE, PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING, MATE, MATE_BOUND, F_CAPTURE, F_EP, F_CASTLE, F_PROMO } = E;
  const { mFrom, mTo, mFlags, mPromo, sqName } = E;
  const PTS = [0, 1, 3, 3, 5, 9, 0];

  /* ---------- text helpers ---------- */
  const sqTag = sq => `<b class="sq" data-sq="${sqName(sq)}">${sqName(sq)}</b>`;
  const pn = pc => {
    const n = A.NAMES[pc & 7];
    return `<span class="pn">${A.mini(pc)}<span class="fn">${n.f}</span><span class="cn">${n.c}</span></span>`;
  };
  const endS = s => /[.!?]$/.test(s) ? s : s + '.';
  const cap1 = s => s.replace(/^(\s*)([a-z])/, (_, a, b) => a + b.toUpperCase());
  const plural = (n, w) => `${n} ${w}${n === 1 ? '' : 's'}`;
  let PERSP = WHITE; // the human player's colour, for "your" / "the enemy"
  const own = pc => ((pc >> 3) === PERSP ? 'your' : 'the enemy');
  const ownPiece = (pc, sq) => `${own(pc)} ${pn(pc)}${sq !== undefined ? ' on ' + sqTag(sq) : ''}`;
  const listAnd = arr => arr.length <= 1 ? (arr[0] || '') : arr.slice(0, -1).join(', ') + ' and ' + arr[arr.length - 1];

  function moveText(pos, m) {
    const from = mFrom(m), to = mTo(m), fl = mFlags(m), pc = pos.board[from];
    let s;
    if (fl & F_CASTLE) s = `${own(pc)} ${pn(pc)} castles ${to > from ? 'kingside' : 'queenside'}`;
    else if (fl & F_CAPTURE) {
      const victim = (fl & F_EP) ? ((((pc >> 3) ^ 1) << 3) | PAWN) : pos.board[to];
      s = `${own(pc)} ${pn(pc)} takes ${own(victim) === own(pc) ? '' : own(victim) + ' '}${pn(victim)} on ${sqTag(to)}`;
    } else s = `${own(pc)} ${pn(pc)} to ${sqTag(to)}`;
    if (fl & F_PROMO) s += ` and becomes a ${pn(((pc >> 3) << 3) | mPromo(m))}`;
    let san = '';
    try { san = pos.san(m); } catch (e) { san = E.toUci(m); }
    return `${s} <code class="san">${san}</code>`;
  }

  function lineText(pos, pv, n) {
    const q = pos.clone(), out = [];
    for (let i = 0; i < Math.min(n, pv.length); i++) {
      const m = pv[i];
      if (!q.legalMoves().includes(m)) break;
      out.push(moveText(q, m));
      q.make(m);
    }
    return out.join(' → ');
  }

  /* ---------- numbers ---------- */
  const wp = cp => {
    if (cp > MATE_BOUND) return 1;
    if (cp < -MATE_BOUND) return 0;
    cp = Math.max(-1500, Math.min(1500, cp));
    return 1 / (1 + Math.exp(-0.00368208 * cp));
  };
  const mateIn = s => s > MATE_BOUND ? Math.ceil((MATE - s) / 2) : s < -MATE_BOUND ? -Math.ceil((MATE + s) / 2) : 0;
  const accuracyOf = loss => Math.max(0, Math.min(100, 103.1668 * Math.exp(-0.04354 * Math.max(0, loss * 100)) - 3.1669));

  function evalWords(cp) { // cp from the player's point of view
    const m = mateIn(cp);
    if (m > 0) return `you have a forced checkmate in ${m}`;
    if (m < 0) return `the enemy has a forced checkmate in ${-m}`;
    const a = Math.abs(cp), you = cp > 0;
    if (a < 40) return 'the game is about equal';
    const who = you ? 'you are' : 'the enemy is';
    if (a < 120) return `${who} slightly better`;
    if (a < 300) return `${who} clearly better`;
    if (a < 700) return `${who} winning`;
    return `${who} completely winning`;
  }
  function evalLabel(cp) { // short, for bars
    const m = mateIn(cp);
    if (m) return (m > 0 ? 'M' : '-M') + Math.abs(m);
    const v = cp / 100;
    return (v > 0 ? '+' : '') + v.toFixed(1);
  }

  /* ---------- board facts ---------- */
  function attacksFrom(b, sq) {
    const pc = b[sq], t = pc & 7, c = pc >> 3, out = [];
    if (t === PAWN) {
      const f = c === WHITE ? 16 : -16;
      for (const d of [f - 1, f + 1]) { const s = sq + d; if (!(s & 0x88)) out.push(s); }
    } else if (t === KNIGHT || t === KING) {
      for (const d of (t === KNIGHT ? E.KNIGHT_D : E.KING_D)) { const s = sq + d; if (!(s & 0x88)) out.push(s); }
    } else {
      const D = t === BISHOP ? E.BISHOP_D : t === ROOK ? E.ROOK_D : E.KING_D;
      for (const d of D) { let s = sq + d; while (!(s & 0x88)) { out.push(s); if (b[s]) break; s += d; } }
    }
    return out;
  }
  const targetsOf = (pos, sq) => { const b = pos.board, c = b[sq] >> 3; return attacksFrom(b, sq).filter(s => b[s] && (b[s] >> 3) !== c); };
  const defenders = (pos, sq) => E.attackersOf(pos.board, sq, pos.board[sq] >> 3);

  // Pieces of `side` that the opponent can win by capturing (SEE > 0). If the opponent is to move, only legal captures count.
  function hanging(pos, side, minLoss) {
    minLoss = minLoss || 100;
    const b = pos.board, out = [];
    let legalCaps = null;
    if (pos.side !== side) {
      legalCaps = new Set();
      for (const m of pos.legalMoves()) if (mFlags(m) & F_CAPTURE) legalCaps.add(mFrom(m) * 128 + mTo(m));
    }
    for (let sq = 0; sq < 128; sq++) {
      if (sq & 0x88) { sq += 7; continue; }
      const pc = b[sq];
      if (!pc || (pc >> 3) !== side || (pc & 7) === KING) continue;
      let best = 0, by = -1;
      for (const a of E.attackersOf(b, sq, side ^ 1)) {
        if (legalCaps && !legalCaps.has(a * 128 + sq)) continue;
        if ((b[a] & 7) === KING && E.attackersOf(b, sq, side).length) continue;
        const g = E.see(pos, a, sq, false);
        if (g > best) { best = g; by = a; }
      }
      if (best >= minLoss) out.push({ sq, pc, loss: best, by });
    }
    return out.sort((x, y) => y.loss - x.loss);
  }

  function forkInfo(pos, sq) {
    const b = pos.board, pc = b[sq];
    if (!pc) return null;
    const v = E.VAL[pc & 7];
    const ts = targetsOf(pos, sq).filter(s => {
      const t = b[s] & 7;
      if (t === KING) return true;
      if (t === PAWN) return false;
      return E.VAL[t] > v || defenders(pos, s).length === 0;
    });
    if (ts.length < 2) return null;
    // a fork only works if the forking piece can't simply be taken for free (unless it gives check from safety)
    const safe = E.attackersOf(b, sq, (pc >> 3) ^ 1).length === 0 || E.threatOn(pos, sq) < 100;
    return safe ? ts : null;
  }

  function balance(pos, side) {
    let s = 0;
    const b = pos.board;
    for (let sq = 0; sq < 128; sq++) {
      if (sq & 0x88) { sq += 7; continue; }
      const pc = b[sq]; if (!pc) continue;
      s += ((pc >> 3) === side ? 1 : -1) * PTS[pc & 7];
    }
    return s;
  }

  function playLine(pos, pv, maxPlies) {
    const q = pos.clone(), caps = [], moves = [];
    let i = 0;
    for (; i < Math.min(maxPlies, pv.length); i++) {
      const m = pv[i];
      if (!q.legalMoves().includes(m)) break;
      moves.push(m);
      const fl = mFlags(m), mover = q.board[mFrom(m)];
      if (fl & F_CAPTURE) {
        const capSq = (fl & F_EP) ? mTo(m) + ((mover >> 3) === WHITE ? -16 : 16) : mTo(m);
        caps.push({ ply: i, by: mover, piece: q.board[capSq], sq: capSq, m });
      }
      q.make(m);
    }
    return { pos: q, caps, plies: i, moves };
  }

  function undevelopedMinors(pos, side) {
    const b = pos.board, r = side === WHITE ? 0 : 7;
    let n = 0;
    for (const f of [1, 2, 5, 6]) { const pc = b[r * 16 + f]; if (pc && (pc >> 3) === side && ((pc & 7) === KNIGHT || (pc & 7) === BISHOP)) n++; }
    return n;
  }

  function moveFacts(pos, m) {
    const b = pos.board, from = mFrom(m), to = mTo(m), fl = mFlags(m), pc = b[from], t = pc & 7, S = pc >> 3;
    const f = { m, from, to, pc, t, side: S, capture: !!(fl & F_CAPTURE), castle: !!(fl & F_CASTLE), promo: (fl & F_PROMO) ? ((S << 3) | mPromo(m)) : 0 };
    f.victim = f.capture ? ((fl & F_EP) ? (((S ^ 1) << 3) | PAWN) : b[to]) : 0;
    f.victimDefended = f.capture && !(fl & F_EP) ? E.attackersOf(b, to, S ^ 1).length > 0 : true;
    f.seeGain = f.capture ? E.see(pos, from, to, !!(fl & F_EP)) : 0;
    const after = pos.clone(); after.make(m);
    f.after = after;
    f.check = after.inCheck();
    f.mate = f.check && after.legalMoves().length === 0;
    f.fork = f.mate ? null : forkInfo(after, to);
    const back = S === WHITE ? 0 : 7;
    f.develop = (t === KNIGHT || t === BISHOP) && (from >> 4) === back && pos.full <= 15;
    const tn = sqName(to);
    f.center = t === PAWN && pos.full <= 10 && ['d4', 'e4', 'd5', 'e5', 'c4', 'c5'].includes(tn) && (from >> 4) === (S === WHITE ? 1 : 6);
    const hb = hanging(pos, S, 100), ha = hanging(after, S, 100);
    f.hangBefore = hb; f.hangAfter = ha;
    f.rescued = hb.filter(h => {
      if (h.sq === from) return !ha.some(x => x.sq === to);
      return after.board[h.sq] === h.pc && !ha.some(x => x.sq === h.sq);
    });
    f.newHang = ha.filter(h => {
      const prevSq = h.sq === to ? from : h.sq;
      return !hb.some(x => x.sq === prevSq && x.loss >= h.loss);
    });
    const eb = hanging(pos, S ^ 1, 100);
    const ea = hanging(after, S ^ 1, 100);
    f.newThreats = ea.filter(h => !eb.some(x => x.sq === h.sq) && h.sq !== to);
    f.attacks = targetsOf(after, to).filter(s => (after.board[s] & 7) !== PAWN);
    f.stopsMate = !f.check && !pos.inCheck() && mateThreats(pos, S).length > 0 && !hasMateInOne(after);
    // a pawn heading for promotion
    if (t === PAWN && !f.capture) {
      const r = to >> 4, rel = S === WHITE ? r : 7 - r;
      f.pushPromo = rel >= 5;
    }
    return f;
  }

  function hasMateInOne(pos) {
    for (const m of pos.legalMoves()) {
      pos.make(m);
      const mate = pos.inCheck() && pos.legalMoves().length === 0;
      pos.unmake();
      if (mate) return true;
    }
    return false;
  }

  function mateThreats(pos, side) {
    if (pos.inCheck()) return [];
    const q = pos.clone();
    q.makeNull();
    if (q.attacked(q.kings[q.side ^ 1], q.side)) return [];
    const res = [];
    for (const m of q.legalMoves()) {
      q.make(m);
      if (q.inCheck() && q.legalMoves().length === 0) res.push(m);
      q.unmake();
      if (res.length) break;
    }
    return res;
  }

  /* ---------- classification ---------- */
  function classify(ctx, facts) {
    const loss = ctx.loss;
    if (ctx.legalCount === 1) return 'forced';
    if (ctx.isBook) return 'book';
    const isBest = ctx.move === ctx.best.move || loss < 0.005;
    const wpBest = ctx.wpBefore, wpPlayed = ctx.wpAfter;
    if (loss < 0.02 && facts) {
      const sac = (facts.capture && facts.seeGain <= -200) || facts.newHang.some(h => h.loss >= 200);
      if (sac && wpPlayed >= 0.5 && wpBest < 0.97 && !(facts.capture && facts.seeGain > 0)) return 'brilliant';
    }
    const bm = mateIn(ctx.best.score), pmate = mateIn(ctx.played.score);
    if (!isBest && bm > 0 && pmate <= 0) return wpPlayed >= 0.5 ? 'miss' : 'blunder';
    if (isBest) {
      if (ctx.second !== null && ctx.second !== undefined && ctx.legalCount > 2 && wpPlayed >= 0.35 && wpBest < 0.98) {
        const gap = wpBest - wp(ctx.second);
        const recapture = ctx.prevMove && facts && facts.capture && mTo(ctx.prevMove) === facts.to;
        if (gap >= 0.12 && !recapture) return 'great';
      }
      return 'best';
    }
    if (loss >= 0.10 && wpBest >= 0.8 && wpPlayed >= 0.35) return 'miss';
    if (loss < 0.02) return 'excellent';
    if (loss < 0.05) return 'good';
    if (loss < 0.10) return 'inaccuracy';
    if (loss < 0.20) return 'mistake';
    return 'blunder';
  }

  const MOOD = { brilliant: 'proud', great: 'proud', best: 'happy', excellent: 'happy', good: 'neutral', book: 'neutral', forced: 'neutral', inaccuracy: 'thinking', mistake: 'worried', miss: 'worried', blunder: 'shocked' };
  const GOOD = new Set(['brilliant', 'great', 'best', 'excellent', 'good', 'book', 'forced']);

  /* ---------- reasons ---------- */
  function bestReason(pos, best, facts) {
    const mi = mateIn(best.score);
    if (facts.mate) return 'it\'s checkmate!';
    if (mi > 0) return `it starts a forced checkmate in ${mi}`;
    if (facts.capture && facts.seeGain >= 90) return !facts.victimDefended ? `it captures ${ownPiece(facts.victim)} for free — nothing was protecting it` : `it wins material (${ownPiece(facts.victim)} is worth more than what you give back)`;
    if (facts.fork) return `it attacks ${listAnd(facts.fork.map(s => ownPiece(facts.after.board[s], s)))} at the same time — a fork`;
    if (facts.stopsMate) return 'it stops the enemy\'s checkmate threat';
    if (facts.rescued.length) return `it keeps ${ownPiece(facts.rescued[0].pc, facts.rescued[0].sq === facts.from ? facts.to : facts.rescued[0].sq)} safe from capture`;
    if (facts.promo) return `your Sprout reaches the end and becomes a ${pn(facts.promo)}`;
    if (facts.castle) return 'it tucks your King into safety and brings a Tower into the game';
    if (facts.newThreats.length) return `it attacks ${ownPiece(facts.newThreats[0].pc, facts.newThreats[0].sq)}, which can't stay there for free`;
    if (facts.check) return 'it gives check and keeps the enemy busy';
    if (facts.develop) return 'it develops a piece toward the center';
    if (facts.center) return 'it grabs space in the center';
    if (facts.pushPromo) return 'it marches a Sprout closer to becoming a Dragon';
    if (facts.capture && facts.seeGain >= 0) return 'it keeps the material balance in your favour';
    return `it keeps your pieces well placed (${evalWords(best.score * (pos.side === PERSP ? 1 : -1))})`;
  }

  const LESSONS = {
    hung: { title: 'Pieces left unprotected', tip: 'Before you let go of a piece, ask: <i>"Can it be captured on its new square?"</i> Count who attacks that square and who defends it.' },
    ignoredThreat: { title: 'Missed enemy threats', tip: 'After every enemy move, ask: <i>"What is that piece attacking now?"</i> If one of your pieces is in danger, move it, protect it, or counter-attack something bigger.' },
    badCapture: { title: 'Captures that lost material', tip: 'Before capturing, check if the enemy piece is protected. Swapping your Dragon (9) for a Sprout (1) loses 8 points!' },
    removedDefender: { title: 'Moving away a defender', tip: 'When you move a piece, look at what it was guarding. Some pieces have important jobs where they stand.' },
    allowedMate: { title: 'Allowing checkmate', tip: 'Look at every check the enemy could give next move. If one of them is checkmate, stop it first — nothing else matters.' },
    missedMate: { title: 'Missed checkmates', tip: 'Every turn, look at all your checks first. Checks are the most forceful moves and sometimes they end the game.' },
    missedCapture: { title: 'Missed free pieces', tip: 'Scan the enemy army each turn: is anything unprotected? A free piece is the quickest way to get ahead.' },
    allowedFork: { title: 'Walking into forks', tip: 'Watch the enemy Hares (Knights): they jump in an L-shape and love attacking two pieces at once. Keep your King and Dragon off squares a Hare can reach together.' },
    missedFork: { title: 'Missed forks', tip: 'Look for moves that attack two things at once — the enemy can only save one.' },
    lostMaterial: { title: 'Tactics that cost material', tip: 'At sharp moments slow down and check <b>C</b>hecks, <b>C</b>aptures and <b>T</b>hreats for both sides before moving.' },
    kingSafety: { title: 'King safety', tip: 'Castle early and keep the Sprouts in front of your King. A safe King lets your other pieces attack.' },
    positional: { title: 'Drifting without a plan', tip: 'When there\'s no tactic, improve your worst piece, fight for the center, and ask what the enemy wants to do next.' }
  };

  /* ---------- the main judge ---------- */
  // ctx: { pos, move, best:{move,score,pv}, played:{score,pv,bound}, second, legalCount, prevMove, isBook, mover:'you'|'enemy' }
  function judge(ctx) {
    const pos = ctx.pos, m = ctx.move, S = pos.side;
    const you = S === PERSP;
    ctx.wpBefore = wp(ctx.best.score);
    ctx.wpAfter = m === ctx.best.move ? ctx.wpBefore : wp(ctx.played.score);
    ctx.loss = Math.max(0, ctx.wpBefore - ctx.wpAfter);
    const facts = moveFacts(pos, m);
    const cls = classify(ctx, facts);
    const info = A.CLASS_INFO[cls];
    const r = { cls, loss: ctx.loss, wpBefore: ctx.wpBefore, wpAfter: ctx.wpAfter, tags: [], arrows: [], marks: [], mood: MOOD[cls], title: '', body: [], better: '', facts };
    r.accuracy = accuracyOf(GOOD.has(cls) && cls !== 'good' && cls !== 'excellent' ? 0 : ctx.loss);
    const bestFacts = ctx.best.move && ctx.best.move !== m ? moveFacts(pos, ctx.best.move) : null;
    const reply = ctx.played.pv && ctx.played.pv.length > 1 ? ctx.played.pv[1] : 0;

    if (!you) return judgeEnemy(ctx, r, facts, bestFacts, reply);

    if (GOOD.has(cls)) {
      r.title = { brilliant: 'Brilliant!', great: 'Great move!', best: 'Best move!', excellent: 'Excellent!', good: 'Good move', book: 'Book move', forced: 'Only move' }[cls];
      r.body.push(praise(pos, facts, cls, ctx));
      if ((cls === 'good' || cls === 'excellent') && bestFacts) {
        r.better = `Even stronger was ${moveText(pos, ctx.best.move)} — ${endS(bestReason(pos, ctx.best, bestFacts))}`;
        r.arrows.push({ m: ctx.best.move, kind: 'best' });
      }
      return r;
    }

    // ---- something went wrong ----
    r.title = { inaccuracy: 'Inaccuracy', mistake: 'Mistake', miss: 'Missed chance!', blunder: 'Blunder!' }[cls];
    r.arrows.push({ m, kind: cls });
    const playedMate = mateIn(ctx.played.score), bestMate = mateIn(ctx.best.score);
    let explained = false;

    if (playedMate < 0) {
      r.tags.push('allowedMate');
      const n = -playedMate;
      const line = ctx.played.pv.slice(1);
      r.body.push(n === 1
        ? `This lets the enemy checkmate you right away: ${lineText(facts.after, line, 1)}.`
        : `This allows a forced checkmate in ${n}: ${lineText(facts.after, line, Math.min(line.length, 5))}.`);
      if (reply) r.arrows.push({ m: reply, kind: 'threat', pos: facts.after });
      explained = true;
    } else if (bestMate > 0 && playedMate <= 0) {
      r.tags.push('missedMate');
      r.body.push(bestMate === 1
        ? `You had checkmate in one! ${cap1(moveText(pos, ctx.best.move))} would have ended the game.`
        : `You had a forced checkmate in ${bestMate}, starting with ${moveText(pos, ctx.best.move)}.`);
      explained = true;
    }

    if (!explained) {
      const pl = playLine(pos, ctx.played.pv || [m], 10);
      const swing = balance(pl.pos, S) - balance(pos, S);
      let bestSwing = 0;
      if (bestFacts) { const bl = playLine(pos, ctx.best.pv || [ctx.best.move], 10); bestSwing = balance(bl.pos, S) - balance(pos, S); }
      if (swing <= -1) {
        explained = explainLoss(pos, m, facts, pl, swing, reply, r);
      }
      if (!explained && bestFacts && bestSwing - swing >= 2) {
        explained = explainMissedGain(pos, ctx, bestFacts, bestSwing - swing, r);
      }
      if (!explained) {
        const tip = kingWalk(pos, facts);
        if (tip) { r.tags.push('kingSafety'); r.body.push(tip); }
        else {
          r.tags.push('positional');
          r.body.push(cls === 'inaccuracy'
            ? 'Not a disaster, but it gives the enemy an easier game.'
            : `This lets the enemy take over — after it, ${evalWords(ctx.played.score)}.`);
          if (reply) r.body.push(`The enemy's best answer is ${lineText(facts.after, [reply], 1)}.`);
        }
      }
    }
    if (bestFacts) {
      r.better = `Better was ${moveText(pos, ctx.best.move)} — ${endS(bestReason(pos, ctx.best, bestFacts))}`;
      r.arrows.push({ m: ctx.best.move, kind: 'best' });
    }
    const lesson = r.tags.length && LESSONS[r.tags[0]];
    if (lesson) r.lesson = lesson.tip;
    return r;
  }

  function explainLoss(pos, m, facts, pl, swing, reply, r) {
    const S = pos.side;
    const lost = pl.caps.filter(c => (c.piece >> 3) === S && c.ply > 0).sort((a, b) => PTS[b.piece & 7] - PTS[a.piece & 7])[0];
    const pts = plural(-swing, 'point');
    const afterRep = facts.after;
    if (facts.capture && facts.seeGain < 0 && facts.victimDefended && PTS[facts.pc & 7] > PTS[facts.victim & 7]) {
      r.tags.push('badCapture');
      r.body.push(`You captured ${ownPiece(facts.victim)}, but it was protected — the enemy takes back your ${pn(facts.pc)}. That swap costs you ${pts}.`);
      if (reply) r.arrows.push({ m: reply, kind: 'threat', pos: afterRep });
      return true;
    }
    if (lost && lost.sq === facts.to) {
      const defs = defenders(afterRep, facts.to).length;
      const attacker = lost.by;
      r.tags.push('hung');
      r.marks.push({ sq: facts.to, kind: 'danger' });
      if (!defs) r.body.push(`Your ${pn(facts.promo || facts.pc)} on ${sqTag(facts.to)} isn't protected, so ${ownPiece(attacker)} can simply take it for free.`);
      else r.body.push(`Your ${pn(facts.promo || facts.pc)} on ${sqTag(facts.to)} can be taken by ${ownPiece(attacker)}. Even though it's protected, the trade is bad for you — it costs ${pts}.`);
      if (reply) r.arrows.push({ m: reply, kind: 'threat', pos: afterRep });
      return true;
    }
    if (lost) {
      const prevSq = lost.sq;
      const wasHanging = facts.hangBefore.find(h => h.sq === prevSq);
      if (wasHanging) {
        r.tags.push('ignoredThreat');
        r.marks.push({ sq: prevSq, kind: 'danger' });
        r.body.push(`Your ${pn(lost.piece)} on ${sqTag(prevSq)} was already under attack, and this move didn't save it. Now: ${lineText(afterRep, [reply], 1)}.`);
        if (reply) r.arrows.push({ m: reply, kind: 'threat', pos: afterRep });
        return true;
      }
      if (attacksFrom(pos.board, facts.from).includes(prevSq) && defenders(afterRep, prevSq).length === 0 && pos.board[prevSq] === lost.piece) {
        r.tags.push('removedDefender');
        r.marks.push({ sq: prevSq, kind: 'danger' });
        r.body.push(`Your ${pn(facts.pc)} was guarding your ${pn(lost.piece)} on ${sqTag(prevSq)}. Moving it away left the ${pn(lost.piece)} unprotected.`);
        if (reply) r.arrows.push({ m: reply, kind: 'threat', pos: afterRep });
        return true;
      }
    }
    if (reply) {
      const q = afterRep.clone(); q.make(reply);
      const fk = forkInfo(q, mTo(reply));
      if (fk) {
        r.tags.push('allowedFork');
        r.body.push(`This allows a fork: ${lineText(afterRep, [reply], 1)} attacks ${listAnd(fk.map(s => ownPiece(q.board[s], s)))} at the same time. You can only save one.`);
        r.arrows.push({ m: reply, kind: 'threat', pos: afterRep });
        return true;
      }
      if (q.inCheck() && lost) {
        r.tags.push('lostMaterial');
        r.body.push(`The enemy has ${lineText(afterRep, [reply], 1)} with check, and after you deal with the check they win your ${pn(lost.piece)}.`);
        r.arrows.push({ m: reply, kind: 'threat', pos: afterRep });
        return true;
      }
    }
    r.tags.push('lostMaterial');
    const shown = Math.min(pl.plies - 1, 4);
    r.body.push(`This lets the enemy win material${lost ? ` (your ${pn(lost.piece)})` : ''}: ${lineText(afterRep, pl.moves.slice(1), Math.max(1, shown))}. You'd end up ${pts} down.`);
    if (reply) r.arrows.push({ m: reply, kind: 'threat', pos: afterRep });
    return true;
  }

  function explainMissedGain(pos, ctx, bf, diff, r) {
    const S = pos.side;
    if (bf.capture && bf.seeGain >= 90) {
      r.tags.push('missedCapture');
      r.marks.push({ sq: bf.to, kind: 'free' });
      r.body.push(!bf.victimDefended
        ? `${cap1(ownPiece(bf.victim, bf.to))} was free — nothing protected it! You could have played ${moveText(pos, bf.m)}.`
        : `You could have won material with ${moveText(pos, bf.m)}: the ${pn(bf.victim)} is worth more than what you'd lose.`);
      return true;
    }
    if (bf.fork) {
      r.tags.push('missedFork');
      r.body.push(`You missed a fork! ${cap1(moveText(pos, bf.m))} attacks ${listAnd(bf.fork.map(s => ownPiece(bf.after.board[s], s)))} at once.`);
      return true;
    }
    if (bf.rescued.length) {
      const h = bf.rescued[0];
      r.tags.push('ignoredThreat');
      r.marks.push({ sq: h.sq, kind: 'danger' });
      r.body.push(`Your ${pn(h.pc)} on ${sqTag(h.sq)} was under attack and needed help. This move doesn't save it.`);
      return true;
    }
    r.tags.push('missedCapture');
    r.body.push(`There was a way to win about ${plural(Math.round(diff), 'point')} of material: ${lineText(pos, ctx.best.pv || [bf.m], 4)}.`);
    return true;
  }

  function kingWalk(pos, f) {
    if (f.t !== KING || f.castle || pos.full > 20) return '';
    const rights = f.side === WHITE ? (pos.castle & 3) : (pos.castle & 12);
    if (!rights) return '';
    return 'Moving your Frog King this early takes away your chance to castle and leaves it exposed in the middle.';
  }

  function praise(pos, f, cls, ctx) {
    if (f.mate) return 'Checkmate! The enemy Frog King has nowhere left to hop. 🎉';
    if (cls === 'brilliant') {
      const h = f.newHang[0];
      return h ? `You left ${ownPiece(h.pc, h.sq)} where it can be captured — but taking it would backfire. A bold, brilliant idea!` : `You gave up material on purpose, and it works. That's a sacrifice worthy of a grandmaster!`;
    }
    if (cls === 'forced') return 'It was the only legal move.';
    if (cls === 'book') {
      const name = ctx.opening ? ` (${ctx.opening})` : '';
      return `A well-known opening move${name}. Masters have played this thousands of times.`;
    }
    if (f.capture && f.seeGain >= 90) {
      if (!f.victimDefended) return `Free ${pn(f.victim)}! Nothing was protecting it, so you won it for nothing.`;
      return `You won material: your ${pn(f.pc)} took a ${pn(f.victim)} that was worth more than it could cost you.`;
    }
    if (f.fork) return `Fork! Your ${pn(f.promo || f.pc)} on ${sqTag(f.to)} attacks ${listAnd(f.fork.map(s => ownPiece(f.after.board[s], s)))} at the same time.`;
    if (f.promo) return `Your Sprout reached the far side and grew into a ${pn(f.promo)}!`;
    if (f.capture && ctx.prevMove && mTo(ctx.prevMove) === f.to) return 'You took back — the material stays even.';
    if (f.rescued.length) {
      const h = f.rescued[0];
      return h.sq === f.from ? `Smart — your ${pn(h.pc)} escaped from danger.` : `Good — you protected your ${pn(h.pc)} on ${sqTag(h.sq)}.`;
    }
    if (f.capture && f.seeGain >= 0) return `A fair trade: your ${pn(f.pc)} takes the enemy ${pn(f.victim)}.`;
    if (f.check) return 'Check! The enemy has to deal with the threat to their King.';
    if (f.castle) return 'You castled — your Frog King is tucked behind its Sprouts and a Tower joins the fight.';
    if (f.newThreats.length) return `This puts pressure on ${ownPiece(f.newThreats[0].pc, f.newThreats[0].sq)} — the enemy has to react.`;
    if (f.develop) return `Nice development — your ${pn(f.pc)} leaves the back row and aims at the center.`;
    if (f.center) return 'Good start: Sprouts in the center give your pieces room and control key squares.';
    if (f.pushPromo) return 'Your Sprout marches on toward the far side — the enemy must watch it closely.';
    return { great: 'The only strong move here — well found!', best: 'That\'s exactly the move I would choose.', excellent: 'Very nearly the best move.', good: 'A solid, sensible move.' }[cls] || 'Good.';
  }

  function judgeEnemy(ctx, r, f, bestFacts, reply) {
    const pos = ctx.pos, cls = r.cls;
    r.title = GOOD.has(cls) ? (cls === 'great' || cls === 'brilliant' ? 'Strong enemy move' : cls === 'book' ? 'Enemy book move' : 'Enemy move') : `Enemy ${A.CLASS_INFO[cls].label.toLowerCase()}`;
    const bits = [];
    if (f.mate) bits.push('Checkmate.');
    else {
      if (f.capture) bits.push(`They captured ${ownPiece(f.victim, f.to)}.`);
      if (f.check) bits.push('They gave check.');
      if (f.fork) bits.push(`It's a fork: their ${pn(f.pc)} attacks ${listAnd(f.fork.map(s => ownPiece(f.after.board[s], s)))}.`);
      else if (f.newThreats.length) bits.push(`Watch out: it attacks ${listAnd(f.newThreats.slice(0, 2).map(h => ownPiece(h.pc, h.sq)))}.`);
      if (f.newHang.length && !GOOD.has(cls)) bits.push(`It leaves their ${pn(f.newHang[0].pc)} on ${sqTag(f.newHang[0].sq)} unprotected!`);
      if (f.castle) bits.push('They castled their King to safety.');
      else if (!bits.length && f.develop) bits.push(`They developed a ${pn(f.pc)}.`);
      if (!bits.length) bits.push('A quiet move.');
    }
    r.body.push(bits.join(' '));
    if (!GOOD.has(cls) && reply) {
      const after = f.after;
      const rf = moveFacts(after, reply);
      let goal = 'grab the advantage';
      if (rf.mate) goal = 'checkmate them on the spot';
      else if (mateIn(-ctx.played.score) > 0) goal = 'start a forced checkmate';
      else if (rf.capture && rf.seeGain >= 90) goal = !rf.victimDefended ? `win their unprotected ${pn(rf.victim)}` : 'win material';
      else if (rf.fork) goal = 'fork two pieces';
      r.body.push(`That gave you a chance to ${goal}: ${lineText(after, [reply], 1)}.`);
      r.arrows.push({ m: reply, kind: 'best', pos: after });
    }
    return r;
  }

  /* ---------- live help ---------- */
  function alerts(pos) {
    const side = pos.side, b = pos.board, out = [];
    if (pos.inCheck()) {
      const checkers = E.attackersOf(b, pos.kings[side], side ^ 1);
      out.push({
        kind: 'check', mood: 'worried',
        html: `<b>Check!</b> Your ${pn(b[pos.kings[side]])} is attacked by ${listAnd(checkers.map(s => ownPiece(b[s], s)))}. You must escape: move the King, block the attack, or capture the attacker.`,
        marks: checkers.map(s => ({ sq: s, kind: 'danger' }))
      });
      return out;
    }
    const mt = mateThreats(pos, side);
    if (mt.length) {
      out.push({ kind: 'mate', mood: 'shocked', html: `<b>Danger!</b> The enemy is threatening <b>checkmate</b> on ${sqTag(mTo(mt[0]))}. Find a way to stop it before anything else!`, marks: [{ sq: mTo(mt[0]), kind: 'danger' }] });
    }
    const hs = hanging(pos, side, 100);
    if (hs.length) {
      const h = hs[0];
      const atk = E.attackersOf(b, h.sq, side ^ 1).sort((x, y) => E.VAL[b[x] & 7] - E.VAL[b[y] & 7]);
      const more = hs.length > 1 ? ` (Also in danger: ${listAnd(hs.slice(1, 3).map(x => `your ${pn(x.pc)} on ${sqTag(x.sq)}`))}.)` : '';
      const defs = E.attackersOf(b, h.sq, side).length;
      out.push({
        kind: 'hanging', mood: 'worried',
        html: `<b>Watch out:</b> your ${pn(h.pc)} on ${sqTag(h.sq)} is under attack by ${ownPiece(b[atk[0]])}${defs ? ' — and its protection isn\'t enough' : ' and nothing protects it'}.${more}`,
        marks: hs.slice(0, 3).map(x => ({ sq: x.sq, kind: 'danger' }))
      });
    }
    return out;
  }

  function hint(pos, ana, level) {
    const best = ana.moves[0];
    if (!best) return null;
    const f = moveFacts(pos, best.move);
    if (level >= 3) {
      return { html: `I'd play ${moveText(pos, best.move)} — ${endS(bestReason(pos, best, f))}`, arrows: [{ m: best.move, kind: 'hint' }], marks: [] };
    }
    if (level === 2) {
      return { html: `Look at your ${pn(f.pc)} on ${sqTag(f.from)}. Where could it go?`, marks: [{ sq: f.from, kind: 'hint' }], arrows: [] };
    }
    const mi = mateIn(best.score);
    let html;
    if (mi === 1) html = 'There\'s a <b>checkmate in one</b>! Look at every check you can give.';
    else if (mi > 1) html = `You have a forced checkmate in ${mi}. Look for forcing moves — checks first!`;
    else if (pos.inCheck()) html = 'You\'re in check. Compare the three escapes: move the King, block, or capture the attacker. Which one is safest?';
    else if (f.capture && f.seeGain >= 90) html = !f.victimDefended ? 'One of the enemy\'s pieces is <b>unprotected</b>. Can you find it?' : 'You can <b>win material</b> with a capture. Count attackers and defenders!';
    else if (f.fork) html = 'Look for a move that <b>attacks two things at once</b>.';
    else if (f.rescued.length) html = 'One of your pieces is <b>in danger</b>. How can you keep it safe?';
    else if (mateThreats(pos, pos.side).length) html = 'The enemy is threatening checkmate! Ask: what would they play if it were their turn?';
    else if (f.promo || f.pushPromo) html = 'Your Sprouts dream of reaching the far side. Can one march forward?';
    else if (f.castle) html = 'Your Frog King would love to be safer. Is <b>castling</b> possible?';
    else if (f.develop) html = 'Bring another piece into the game — Hares and Owls want to leave the back row.';
    else if (f.center) html = 'Fight for the <b>center</b> squares (d4, e4, d5, e5).';
    else if (f.newThreats.length) html = 'Can you <b>attack</b> one of the enemy pieces and make them react?';
    else if (f.check) html = 'A <b>check</b> might be strong here.';
    else html = 'No big tactic here. Find your least active piece and give it a better square.';
    return { html, marks: [], arrows: [] };
  }

  function openingTip(pos, m, seen) {
    if (pos.full > 12) return null;
    const f = { from: mFrom(m), to: mTo(m), fl: mFlags(m) };
    const pc = pos.board[f.from], t = pc & 7, S = pc >> 3;
    const und = undevelopedMinors(pos, S);
    const tip = (id, html) => (seen.has(id) ? null : { id, html });
    if (t === QUEEN && pos.full <= 6 && und >= 3) return tip('earlyQueen', 'Tip: bringing your Dragon out very early lets enemy pieces chase it around while they develop. Usually Hares and Owls come out first.');
    if (t === KING && !(f.fl & F_CASTLE) && (S === WHITE ? pos.castle & 3 : pos.castle & 12)) return tip('kingWalk', 'Tip: moving your Frog King before castling gives up the right to castle. Keep it home until it can castle to safety.');
    if (t === PAWN && (f.from & 7) === 5 && pos.full <= 10 && !(f.fl & F_CAPTURE)) return tip('fPawn', 'Tip: the Sprout in front of your King\'s side (the f-pawn) shields your King. Moving it early opens a path for attackers.');
    if (t === KNIGHT && ((f.to & 7) === 0 || (f.to & 7) === 7)) return tip('rim', 'Tip: "A Hare on the rim is dim!" Hares on the edge attack only a few squares. They\'re happier near the center.');
    if (t === PAWN && ((f.from & 7) === 0 || (f.from & 7) === 7) && pos.full <= 6) return tip('edgePawn', 'Tip: edge Sprouts don\'t help control the center. In the opening, prefer center Sprouts and developing pieces.');
    if ((t === KNIGHT || t === BISHOP) && (f.from >> 4) !== (S === WHITE ? 0 : 7) && und >= 2 && pos.full <= 8 && !(f.fl & F_CAPTURE)) {
      if (!E.threatOn(pos, f.from)) return tip('twice', 'Tip: in the opening, try to move each piece once before moving one piece twice. Get everyone into the game!');
    }
    if (pos.full >= 9 && (S === WHITE ? (pos.castle & 3) : (pos.castle & 12)) && t !== KING) return tip('castleSoon', 'Tip: your Frog King is still in the middle. Castling soon will keep it safe.');
    return null;
  }

  /* ---------- review summary ---------- */
  function summarize(rec) {
    // rec: { plies: [{ color, judge }], playerColor, result, opening }
    const sides = [0, 1].map(c => {
      const ms = rec.plies.filter(p => p.color === c && p.judge);
      const counts = {};
      for (const k of Object.keys(A.CLASS_INFO)) counts[k] = 0;
      let accSum = 0, inv = 0;
      for (const p of ms) { counts[p.judge.cls]++; accSum += p.judge.accuracy; inv += 1 / Math.max(10, p.judge.accuracy); }
      // blend of the plain average and the harmonic mean, so a few big blunders weigh heavily (like lichess)
      const acc = ms.length ? (accSum / ms.length + ms.length / inv) / 2 : 0;
      return { accuracy: acc, counts, n: ms.length };
    });
    const me = rec.playerColor;
    const mine = rec.plies.map((p, i) => ({ ...p, i })).filter(p => p.color === me && p.judge);
    const bad = mine.filter(p => !GOOD.has(p.judge.cls)).sort((a, b) => b.judge.loss - a.judge.loss);
    const goodMoments = mine.filter(p => ['brilliant', 'great'].includes(p.judge.cls) || (p.judge.facts && (p.judge.facts.mate || p.judge.facts.fork || (p.judge.facts.capture && p.judge.facts.seeGain >= 300)) && GOOD.has(p.judge.cls)));
    const tagCount = {};
    for (const p of bad) for (const t of p.judge.tags) tagCount[t] = (tagCount[t] || 0) + 1;
    const lessons = Object.entries(tagCount).sort((a, b) => (a[0] === 'positional') - (b[0] === 'positional') || b[1] - a[1]).slice(0, 3).map(([t, n]) => ({ tag: t, n, ...LESSONS[t] }));
    const keyMoments = bad.slice(0, 3).sort((a, b) => a.i - b.i);
    // narrative
    const s = [];
    if (rec.opening) s.push(`The game opened with the <b>${rec.opening}</b>.`);
    const firstBig = mine.find(p => ['mistake', 'blunder', 'miss'].includes(p.judge.cls));
    if (!firstBig) s.push('You made no serious mistakes — wonderful, steady play!');
    else {
      const moveNo = Math.floor(firstBig.i / 2) + 1;
      if (moveNo >= 8) s.push(`You played well through the opening. The first big slip came on <b>move ${moveNo}</b>.`);
      else s.push(`Things went wrong early, on <b>move ${moveNo}</b>.`);
    }
    if (goodMoments.length) {
      const g = goodMoments[0];
      s.push(`Your best moment: <b>move ${Math.floor(g.i / 2) + 1}</b> (${A.CLASS_INFO[g.judge.cls].label.toLowerCase()}) — keep finding moves like that!`);
    }
    if (lessons.length) s.push(`The thing to work on next: <b>${lessons[0].title.toLowerCase()}</b>.`);
    return { sides, keyMoments, goodMoments, lessons, narrative: s.join(' ') };
  }

  function setPerspective(c) { PERSP = c; }

  global.Coach = {
    judge, hint, alerts, openingTip, summarize, moveText, lineText, evalWords, evalLabel, wp, mateIn, accuracyOf,
    hanging, moveFacts, setPerspective, sqTag, pn, LESSONS, GOOD, MOOD, mateThreats, balance
  };
})(typeof window !== 'undefined' ? window : globalThis);
