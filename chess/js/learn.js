/* Mosswood Chess: Learn screen (piece guide + berry hunts, rules, golden rules, glossary) and Trials (puzzles). */
(function () {
  'use strict';
  const MW = window.MW;
  const E = window.MWEngine, A = window.PieceArt, C = window.Coach;
  const { BoardView, coachEng, sleep, esc, coachSay, convertAna, Snd, store, playMoveSound, judgeArrows } = MW;
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));

  const BERRY = '<svg viewBox="0 0 40 40"><path d="M20 10 C22 3 30 3 32 6 C28 10 24 11 20 10Z" fill="#7cc35a" stroke="#3b2414" stroke-width="1.6"/><circle cx="20" cy="24" r="12" fill="#e5484d" stroke="#3b2414" stroke-width="2"/><circle cx="15" cy="20" r="3.2" fill="#fff" opacity=".65"/><circle cx="24" cy="28" r="1.4" fill="#7a1d22" opacity=".6"/><circle cx="19" cy="30" r="1.4" fill="#7a1d22" opacity=".6"/><circle cx="26" cy="21" r="1.4" fill="#7a1d22" opacity=".6"/></svg>';

  /* ---------- Meet the army ---------- */
  const ARMY = [
    { t: 1, value: '1 point', move: 'Marches straight forward one square — or two squares on its very first move. It captures one square <b>diagonally</b> forward.', special: 'Reach the far side and it grows into any piece you like, usually a Dragon. That\'s called <b>promotion</b>.', game: { start: 'e2', berries: ['e4', 'e5', 'e8'], par: 5 } },
    { t: 2, value: '3 points', move: 'Leaps in an <b>L-shape</b>: two squares one way, then one square to the side. The only piece that can jump over others.', special: 'Brilliant at <b>forks</b> — attacking two enemy pieces at the same time.', game: { start: 'b1', berries: ['c3', 'e4', 'g5'], par: 3 } },
    { t: 3, value: '3 points', move: 'Glides <b>diagonally</b> as far as it likes. It stays on squares of one colour for the whole game.', special: 'Two Owls working together (the "bishop pair") are very strong in open positions.', game: { start: 'c1', berries: ['f4', 'b8', 'h2'], par: 3 } },
    { t: 4, value: '5 points', move: 'Rolls in <b>straight lines</b> — up, down, left and right — as far as it likes.', special: 'Joins the King when castling. Towers love open files with no Sprouts in the way.', game: { start: 'a1', berries: ['a6', 'f6', 'f2'], par: 3 } },
    { t: 5, value: '9 points', move: 'Flies in <b>any straight line</b>, straight or diagonal — a Tower and an Owl combined!', special: 'Your most powerful piece. Don\'t bring her out too early, or enemy pieces will chase her around.', game: { start: 'd1', berries: ['h5', 'e8', 'a4'], par: 3 } },
    { t: 6, value: 'Priceless', move: 'Hops <b>one square</b> in any direction. He can never step onto a square that is attacked.', special: 'If he is attacked and can\'t escape, that\'s <b>checkmate</b> and the game is lost. Keep him safe — castle early!', game: { start: 'e1', berries: ['e3', 'g4', 'f6'], par: 6 } }
  ];

  function loneMoves(t, sq) {
    const out = [];
    const add = s => out.push({ to: s, m: E.mkMove(sq, s, 0, 0) });
    if (t === 1) { const s = sq + 16; if (!(s & 0x88)) { add(s); if ((sq >> 4) === 1) add(s + 16); } }
    else if (t === 2 || t === 6) { for (const d of (t === 2 ? E.KNIGHT_D : E.KING_D)) { const s = sq + d; if (!(s & 0x88)) add(s); } }
    else {
      const D = t === 3 ? E.BISHOP_D : t === 4 ? E.ROOK_D : E.KING_D;
      for (const d of D) { let s = sq + d; while (!(s & 0x88)) { add(s); s += d; } }
    }
    return out;
  }

  function renderArmy(body) {
    body.innerHTML = `<p class="muted" style="margin-top:0">Every creature in Mosswood moves like a classic chess piece. Learn them here, then try each one's <b>berry hunt</b> — collect every berry in as few moves as you can!</p>
      <div class="army">${ARMY.map((p, i) => {
        const n = A.NAMES[p.t];
        const best = (store.get('mw.berries', {}))[p.t];
        return `<div class="panel army-card"><div class="army-top"><div class="army-art"><div>${A.svg(p.t)}</div><div>${A.svg(8 | p.t)}</div></div>
          <div><h3>${n.f}</h3><div class="cn">plays as the ${n.c} · ${n.letter}</div><span class="val">${p.value}</span></div></div>
          <p>${p.move}</p><p class="sp">✦ ${p.special}</p>
          <button class="btn small" data-hunt="${i}"><span class="emo">🍓</span> Berry hunt${best ? ' · ' + '★'.repeat(best) : ''}</button></div>`;
      }).join('')}</div><div id="hunt"></div>`;
    $$('[data-hunt]', body).forEach(b => b.addEventListener('click', () => startHunt(ARMY[+b.dataset.hunt])));
  }

  let huntBoard = null;
  function startHunt(p) {
    const box = $('#hunt');
    const n = A.NAMES[p.t];
    box.innerHTML = `<div class="panel minigame" style="margin-top:14px"><div class="board-frame"><div id="hunt-board"></div></div>
      <div class="mg-info"><h3>${n.f} berry hunt</h3><p>Move your ${n.f} (${n.c}) onto every berry. ${p.t === 1 ? 'Sprouts only move forward — and something magical happens on the last row!' : 'Tap the piece, then tap where it should go.'}</p>
      <p><b>Moves:</b> <span id="hunt-moves">0</span> · <b>Target:</b> ${p.game.par}</p><div class="stars" id="hunt-stars"></div>
      <button class="btn small ghost" id="hunt-reset">Start again</button></div></div>`;
    huntBoard = new BoardView($('#hunt-board'));
    // scroll after BoardView has given the board its height, or the target is clamped to a too-short page
    box.scrollIntoView({ behavior: 'smooth', block: 'start' });
    const fake = { board: new Int8Array(128) };
    let sq = E.sqFromName(p.game.start), moves = 0;
    let t = p.t;
    let berries = new Set(p.game.berries.map(E.sqFromName));
    fake.board[sq] = t;
    const berryArt = s => (window.Ink && window.Ink.enabled) ? window.Ink.convert(BERRY, { frames: 3, seed: (s % 5) + 1, side: 'w' }) : BERRY;
    const drawBerries = () => huntBoard.setMarks('coach', Array.from(berries).map(s => ({ sq: s, kind: 'berry', html: `<span>${berryArt(s)}</span>` })));
    huntBoard.setPosition(fake, { fade: true });
    drawBerries();
    const arm = () => huntBoard.setInteractive(true, { side: 0, getMoves: from => from === sq ? loneMoves(t, sq) : [] });
    huntBoard.onMove = async (m) => {
      huntBoard.setInteractive(false);
      const to = E.mTo(m);
      Snd.play(t === 2 || t === 6 ? 'hop' : 'move');
      await huntBoard.animateMove(fake, m);
      fake.board[sq] = 0; fake.board[to] = t; sq = to; moves++;
      huntBoard.setPosition(fake);
      $('#hunt-moves').textContent = moves;
      if (berries.has(to)) {
        berries.delete(to);
        drawBerries();
        Snd.play('sparkle');
        huntBoard.burst(to, ['#e5484d', '#7cc35a', '#f6c847', '#ffffff'], 14, 'leaf');
      }
      if (t === 1 && (to >> 4) === 7) {
        t = 5; fake.board[to] = 5;
        Snd.play('promote');
        await huntBoard.swapPiece(to, 5, true);
        MW.toast('Your Sprout grew into a Dragon! That\'s promotion.');
      }
      if (!berries.size) {
        const stars = moves <= p.game.par ? 3 : moves <= p.game.par + 2 ? 2 : 1;
        $('#hunt-stars').textContent = '★'.repeat(stars) + '☆'.repeat(3 - stars);
        const rec = store.get('mw.berries', {});
        rec[p.t] = Math.max(rec[p.t] || 0, stars);
        store.set('mw.berries', rec);
        Snd.play('win');
        MW.confetti(40);
        return;
      }
      arm();
    };
    arm();
    $('#hunt-reset').onclick = () => startHunt(p);
  }

  /* ---------- Rules ---------- */
  const RULES = [
    { title: 'Check', fen: '4k3/8/8/8/8/8/8/4R1K1 b - - 0 1', arrows: [['e1', 'e8', 'threat']], marks: [['e8', 'check']], text: 'When a King is attacked, he is <b>in check</b>. The player must get out of check right away: move the King, block the attack, or capture the attacker.' },
    { title: 'Checkmate', fen: '3R2k1/5ppp/8/8/8/8/5PPP/6K1 b - - 0 1', arrows: [['d8', 'g8', 'threat']], marks: [['g8', 'check']], text: 'Check with <b>no escape</b> is checkmate — the game is over! Here the Frog King is trapped behind his own Sprouts (a "back-rank mate").' },
    { title: 'Stalemate', fen: '7k/5Q2/6K1/8/8/8/8/8 b - - 0 1', arrows: [], marks: [['h8', 'hint']], text: 'If the player to move has <b>no legal moves</b> but is <b>not</b> in check, it\'s stalemate — a draw. Careful when you\'re winning: leave the enemy King a square to move to!' },
    { title: 'Castling', fen: 'r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1', arrows: [['e1', 'g1', 'best'], ['h1', 'f1', 'best']], marks: [], text: 'Once per game the King can hop two squares toward a Tower, and the Tower jumps over him. Only if neither has moved, nothing is between them, and the King isn\'t in or passing through check.' },
    { title: 'En passant', fen: '4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1', arrows: [['e5', 'd6', 'best']], marks: [['d5', 'danger']], text: 'If an enemy Sprout jumps two squares and lands right beside yours, you may capture it as if it had only moved one — but only on the very next move.' },
    { title: 'Promotion', fen: '4k3/1P6/8/8/8/8/8/4K3 w - - 0 1', arrows: [['b7', 'b8', 'best']], marks: [], text: 'A Sprout that reaches the far side grows into a Dragon, Tower, Owl or Hare — almost always a Dragon!' },
    { title: 'Draws', fen: '8/8/4k3/8/8/3K4/8/8 w - - 0 1', arrows: [], marks: [], text: 'A game is drawn by stalemate, when the same position happens three times, after 50 moves with no capture or Sprout move, or when nobody has enough pieces left to checkmate (like King vs King).' }
  ];
  function renderRules(body) {
    body.innerHTML = `<div class="rules-grid">${RULES.map((r, i) => `<div class="panel rule-card"><h3>${r.title}</h3><div class="board-frame"><div id="rule-b${i}"></div></div><p>${r.text}</p></div>`).join('')}</div>`;
    RULES.forEach((r, i) => {
      const b = new BoardView($('#rule-b' + i));
      const pos = E.Position.fromFen(r.fen);
      b.setPosition(pos);
      b.setArrows(r.arrows.map(([f, t, k]) => ({ from: E.sqFromName(f), to: E.sqFromName(t), kind: k, dashed: k === 'threat' })));
      b.setMarks('check', r.marks.map(([s, k]) => ({ sq: E.sqFromName(s), kind: k })));
      if (pos.inCheck()) { const el = b.pieces.get(pos.kings[pos.side]); if (el) el.classList.add('in-check'); }
    });
  }

  /* ---------- Golden rules ---------- */
  const GOLDEN = [
    ['🔍', 'Look before you leap', 'Before every move, check three things for both sides: <b>Checks, Captures and Threats</b>. Most beginner games are decided by a piece left hanging.'],
    ['🛡️', 'Guard your army', 'After you choose a move, ask: <i>"Can the piece I moved be captured? Did I stop protecting something?"</i> If yes, think again.'],
    ['🎯', 'Claim the center', 'Sprouts and pieces in the middle (d4, e4, d5, e5) control the most squares and reach both sides of the board quickly.'],
    ['🐇', 'Wake up the Hares and Owls', 'In the opening, bring out your Hares (Knights) and Owls (Bishops) before moving the same piece twice.'],
    ['🏰', 'Castle early', 'Tuck your Frog King safely behind his Sprouts and connect your Towers. Uncastled Kings get ambushed.'],
    ['🐉', 'Keep the Dragon home (at first)', 'Bring your Dragon out early and enemy pieces will chase her while they develop. Let the smaller pieces go first.'],
    ['⚖️', 'Count the points', 'Sprout 1, Hare 3, Owl 3, Tower 5, Dragon 9. Don\'t trade a big piece for a small one. When you\'re ahead, trading pieces makes winning easier.'],
    ['👑', 'Wake the King in the endgame', 'When most pieces are gone, the King becomes a fighter. March him toward the center and help your Sprouts promote.']
  ];
  function renderGolden(body) {
    body.innerHTML = `<div class="gold-grid">${GOLDEN.map(([i, t, x]) => `<div class="panel gold-card"><div class="gi">${i}</div><h3>${t}</h3><p>${x}</p></div>`).join('')}</div>`;
  }

  /* ---------- Glossary ---------- */
  const WORDS = [
    ['Fork', 'One piece attacks two (or more) enemy pieces at once. The enemy can usually only save one. Hares are the champions of forking.'],
    ['Pin', 'A piece can\'t move because a more valuable piece (often the King) is hiding behind it on the same line.'],
    ['Skewer', 'Like a pin in reverse: a big piece is attacked, and when it moves away, the piece behind it gets captured.'],
    ['Hanging piece', 'A piece that can be captured for free because nothing protects it. Hanging pieces are the #1 way games are lost at first!'],
    ['Discovered attack', 'Moving one piece out of the way reveals an attack from a piece behind it — two threats in one move.'],
    ['Development', 'Bringing your pieces off the back row into active squares. Good development = more pieces ready to fight.'],
    ['Tempo', 'A "move\'s worth of time". Making a threat the enemy must answer gains a tempo.'],
    ['Trade (exchange)', 'Capturing a piece and letting yours be captured back. Fair trades swap equal points.'],
    ['Sacrifice', 'Giving away material on purpose to get something bigger — like a checkmate attack.'],
    ['Blunder', 'A big mistake that loses a lot — usually material or the game.'],
    ['Back-rank mate', 'Checkmate along the back row, when the King is trapped behind his own Sprouts.'],
    ['Passed pawn', 'A Sprout with no enemy Sprouts in front of it or on the neighbouring files. It wants to run and promote!'],
    ['Zugzwang', 'A German word: any move you make worsens your position, but you have to move.'],
    ['Material', 'The total value of your pieces. "Being up material" means you have more points on the board.']
  ];
  function renderWords(body) {
    body.innerHTML = `<div class="words-grid">${WORDS.map(([w, d]) => `<div class="panel word-card"><h3>${w}</h3><p>${d}</p></div>`).join('')}</div>`;
  }

  let learnTab = 'army';
  function openLearn() {
    $$('#learn-tabs button').forEach(b => b.classList.toggle('on', b.dataset.t === learnTab));
    const body = $('#learn-body');
    if (learnTab === 'army') renderArmy(body);
    else if (learnTab === 'rules') renderRules(body);
    else if (learnTab === 'golden') renderGolden(body);
    else renderWords(body);
  }

  /* ---------- Trials ---------- */
  const TRIALS = [
    { id: 't1', name: 'Back-Rank Bolt', theme: 'Checkmate in 1', fen: '6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1', goal: 'mate', n: 1, intro: 'The enemy Frog King is hiding behind his own Sprouts. Can you trap him?' },
    { id: 't2', name: 'The Unguarded Owl', theme: 'Win a free piece', fen: '1k6/pp6/8/3b4/8/8/5PPP/3R2K1 w - - 0 1', goal: 'win', n: 1, intro: 'One enemy piece has nobody looking after it…' },
    { id: 't3', name: 'The Royal Fork', theme: 'Fork', fen: '4q1k1/pp3p1p/8/3N4/8/8/5PPP/6K1 w - - 0 1', goal: 'win', n: 1, intro: 'Your Hare is itching to jump. Find a square where it attacks two big targets at once.' },
    { id: 't4', name: 'Smothered Snare', theme: 'Checkmate in 1', fen: '6rk/6pp/8/6N1/8/8/8/6K1 w - - 0 1', goal: 'mate', n: 1, intro: 'The enemy King is boxed in by his own army. Only one creature can reach him…' },
    { id: 't5', name: 'The Owl\'s Skewer', theme: 'Skewer', fen: 'q7/8/2k5/8/8/8/5P1P/5BK1 w - - 0 1', goal: 'win', n: 1, intro: 'The enemy King and Dragon stand on the same diagonal. Line them up!' },
    { id: 't6', name: 'Sprout Ascends', theme: 'Promotion', fen: '8/1P4k1/8/8/8/8/6K1/4r3 w - - 0 1', goal: 'win', n: 1, intro: 'Your brave Sprout is one step from glory — but that enemy Tower is lurking. Don\'t wait!' },
    { id: 't7', name: 'Two-Tower Ladder', theme: 'Checkmate in 1', fen: '7k/R7/8/8/8/8/8/1R4K1 w - - 0 1', goal: 'mate', n: 1, intro: 'Two Towers working together can climb like a ladder.' },
    { id: 't8', name: 'The Dragon\'s Kiss', theme: 'Checkmate in 1', fen: '7k/Q7/6K1/8/8/8/8/8 w - - 0 1', goal: 'mate', n: 1, intro: 'Your King and Dragon are close to the enemy King. Deliver the final blow!' },
    { id: 't9', name: 'Hare Hunt', theme: 'Fork', fen: 'r3k3/8/8/1N6/8/8/5PPP/4K3 w - - 0 1', goal: 'win', n: 1, intro: 'Can your Hare attack the enemy King and Tower at the same time?' },
    { id: 't10', name: 'Ladder in Two', theme: 'Checkmate in 2', fen: '6k1/8/8/8/8/8/R7/1R4K1 w - - 0 1', goal: 'mate', n: 2, intro: 'Checkmate in two moves. Use your Towers like a ladder, one rung at a time.' },
    { id: 't11', name: 'Dragon in Danger', theme: 'Save your piece', fen: 'r1b1kb1r/pppp1ppp/2n2n2/4p1q1/4P3/3P1N2/PPP2PPP/RNBQKB1R b KQkq - 1 4', goal: 'win', n: 1, intro: 'The enemy Hare is attacking your Dragon! Find a safe square — without walking into a new trap.' },
    { id: 't12', name: 'Duskmire Strikes', theme: 'Checkmate in 1', fen: 'rnb1k1nr/pppp1ppp/8/2b1p3/2B1P2q/2N5/PPPP1PPP/R1BQK1NR b KQkq - 0 1', goal: 'mate', n: 1, intro: 'Playing as the Duskmire army: the enemy left a weak spot near his King. Strike!' }
  ];

  let trialBoard = null, T = null;
  const trialSpot = { feed: () => $('#tr-feed'), av: () => $('#tr-av'), board: () => trialBoard };

  function renderTrialList() {
    const solved = store.get('mw.trials', {});
    const done = TRIALS.filter(t => solved[t.id]).length;
    $('#trials').innerHTML = `<div class="panel"><h2 class="ph">The Trials of Mosswood</h2><p class="muted" style="margin:4px 0 0">Short puzzles that train the skills that win games: spotting checkmates, free pieces and forks. Solved ${done} of ${TRIALS.length}.</p>
      <div class="trials-grid">${TRIALS.map((t, i) => `<button class="trial-btn ${solved[t.id] ? 'done' : ''}" data-tr="${i}"><span class="tn">${solved[t.id] ? '✓' : i + 1}</span><span><b>${t.name}</b><small>${t.theme}</small></span></button>`).join('')}</div></div>`;
    $$('[data-tr]').forEach(b => b.addEventListener('click', () => playTrial(+b.dataset.tr)));
  }

  async function playTrial(idx) {
    const t = TRIALS[idx];
    const myTok = (T && T.tok || 0) + 1;
    T = { idx, t, tok: myTok, pos: E.Position.fromFen(t.fen), moved: 0, tries: 0, hint: 0, ana: null };
    $('#trials').innerHTML = `<div class="trial-play"><div><div class="board-frame"><div id="tr-board"></div></div></div>
      <div style="display:flex;flex-direction:column;gap:10px"><div class="panel"><span class="kicker">${t.theme}</span><h2 class="ph" style="margin-top:2px">${idx + 1}. ${t.name}</h2>
      <p style="margin:4px 0 0">${t.intro}</p><p class="small muted" style="margin:6px 0 0">You play the <b>${T.pos.side === 0 ? 'Sunmeadow' : 'Duskmire'}</b> army.</p></div>
      <div class="coach-card"><div class="co-av" id="tr-av"></div><div class="co-feed" id="tr-feed"></div></div>
      <div class="g-tools" style="grid-template-columns:repeat(4,1fr)"><button class="tool" id="tr-hint"><span>🏮</span>Hint</button><button class="tool" id="tr-reset"><span>⟲</span>Reset</button><button class="tool" id="tr-next"><span>➜</span>Next</button><button class="tool" id="tr-list"><span>☰</span>All trials</button></div></div></div>`;
    trialBoard = new BoardView($('#tr-board'));
    trialBoard.setFlipped(T.pos.side === 1);
    trialBoard.setPosition(T.pos, { fade: true });
    $('#tr-list').onclick = renderTrialList;
    $('#tr-next').onclick = () => playTrial((idx + 1) % TRIALS.length);
    $('#tr-reset').onclick = () => playTrial(idx);
    $('#tr-hint').onclick = trialHint;
    coachSay({ mood: 'thinking', html: 'Let me study this position for a moment…' }, trialSpot);
    MW.coachBusy && (function () { const av = $('#tr-av'); if (av) av.classList.add('busy'); })();
    T.ana = await analyse(T.pos);
    // the player may have left the trial (or opened another) while the coach was thinking
    if (!T || T.tok !== myTok || !$('#tr-av')) return;
    $('#tr-av').classList.remove('busy');
    coachSay({ mood: 'happy', title: 'Your move!', html: t.goal === 'mate' ? `Find the checkmate${t.n > 1 ? ` in ${t.n} moves` : ''}. Remember: look at every check first!` : 'Find the strongest move. Tap a piece to see where it can go.' }, trialSpot);
    arm();
  }

  async function analyse(pos) {
    const res = await coachEng.request({ type: 'analyze', multi: true, fen: pos.toFen(), moves: [], timeMs: 900, minDepth: 4, maxDepth: 12 });
    return convertAna(pos, res);
  }

  function arm() {
    const pos = T.pos, legal = pos.legalMoves(), myTok = T.tok;
    trialBoard.setInteractive(true, { side: pos.side, getMoves: from => legal.filter(m => E.mFrom(m) === from).map(m => ({ to: E.mTo(m), m, ep: !!(E.mFlags(m) & E.F_EP) })) });
    trialBoard.onMove = m => onTrialMove(m, myTok);
  }

  async function onTrialMove(m, myTok) {
    if (!T || T.tok !== myTok) return;
    const pos = T.pos, ana = T.ana;
    trialBoard.setInteractive(false);
    trialBoard.setArrows([]); trialBoard.clearMarks('hint', 'coach');
    playMoveSound(pos, m);
    await trialBoard.animateMove(pos.clone(), m);
    const best = ana.moves[0], e = ana.moves.find(x => x.move === m) || { move: m, score: best.score - 500, pv: [m] };
    const remaining = T.t.n - T.moved;
    let ok;
    if (T.t.goal === 'mate') ok = C.mateIn(e.score) > 0 && C.mateIn(e.score) <= remaining;
    else ok = m === best.move || (C.wp(best.score) - C.wp(e.score) < 0.03 && e.score > best.score - 120);
    C.setPerspective(pos.side);
    const before = pos.clone();
    if (!ok) {
      T.tries++;
      Snd.play('illegal');
      const j = C.judge({ pos: before, move: m, best, played: e, second: null, legalCount: ana.moves.length, prevMove: 0 });
      coachSay({ mood: 'worried', title: 'Not quite…', html: `<p>${j.body.join(' ')}</p><p class="small">Have another go${T.tries >= 2 ? ' — or tap Hint' : ''}!</p>`, kind: 'alert' }, trialSpot);
      await sleep(1500);
      if (!T || T.tok !== myTok) return;
      trialBoard.setPosition(pos);
      arm();
      return;
    }
    pos.make(m);
    trialBoard.setPosition(pos);
    T.moved++;
    const st = pos.status();
    if (pos.inCheck()) Snd.play('check');
    const solved = st.over && st.reason === 'checkmate' || (T.t.goal !== 'mate' && T.moved >= T.t.n);
    if (solved) {
      if (st.reason === 'checkmate') {
        const kel = trialBoard.pieces.get(pos.kings[pos.side]);
        if (kel) kel.classList.add('mated');
      }
      trialBoard.burst(E.mTo(m), ['#2ec4b6', '#f6c847', '#ffffff', '#7cc35a'], 20, 'star');
      Snd.play('win');
      MW.confetti(60);
      const f = C.moveFacts(before, m);
      const why = f.mate ? 'checkmate, the King has nowhere to go!' : f.fork ? 'a perfect fork, the enemy can only save one piece.' : f.promo ? 'promotion! Your Sprout has become mighty.' : f.capture ? 'you won material cleanly.' : 'that\'s the strongest move.';
      const s = store.get('mw.trials', {}); s[T.t.id] = true; store.set('mw.trials', s);
      coachSay({ mood: 'proud', title: 'Trial complete!', html: `${C.moveText(before, m).replace(/^./, c => c.toUpperCase())} — ${why}${T.tries ? '' : ' First try, too!'}`,
        actions: [{ label: 'Next trial ➜', fn: () => playTrial((T.idx + 1) % TRIALS.length) }, { label: 'All trials', ghost: true, fn: renderTrialList }] }, trialSpot);
      return;
    }
    // opponent replies (multi-move puzzles)
    coachSay({ mood: 'happy', title: 'Good! Keep going…', html: 'The enemy replies…' }, trialSpot);
    await sleep(500);
    const res = await coachEng.request({ type: 'analyze', multi: false, fen: pos.toFen(), moves: [], timeMs: 400, minDepth: 3 });
    if (!T || T.tok !== myTok) return;
    const reply = pos.moveFromUci(res.move);
    if (reply) {
      playMoveSound(pos, reply);
      await trialBoard.animateMove(pos.clone(), reply);
      pos.make(reply);
      trialBoard.setPosition(pos);
      trialBoard.setMarks('check', [{ sq: E.mFrom(reply), kind: 'last' }, { sq: E.mTo(reply), kind: 'last' }]);
    }
    T.ana = await analyse(pos);
    if (!T || T.tok !== myTok) return;
    T.hint = 0;
    coachSay({ mood: 'thinking', title: 'Now finish it!', html: T.t.goal === 'mate' ? 'Find the final blow — checkmate!' : 'Find the best follow-up.' }, trialSpot);
    arm();
  }

  function trialHint() {
    if (!T || !T.ana) return;
    T.hint = Math.min(3, T.hint + 1);
    const h = C.hint(T.pos, T.ana, T.hint);
    if (!h) return;
    C.setPerspective(T.pos.side);
    coachSay({ mood: 'thinking', title: `Hint ${T.hint} of 3`, html: h.html, kind: 'tip' }, trialSpot);
    trialBoard.setMarks('hint', (h.marks || []).map(x => ({ sq: x.sq, kind: 'hint' })));
    trialBoard.setArrows(judgeArrows(h.arrows));
  }

  function open(name) {
    if (name === 'learn') openLearn();
    else renderTrialList();
  }

  document.addEventListener('DOMContentLoaded', () => {
    $$('#learn-tabs button').forEach(b => b.addEventListener('click', () => { learnTab = b.dataset.t; Snd.play('click'); openLearn(); }));
  });

  MW.learn = { open, TRIALS };
})();
