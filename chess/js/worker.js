/* Mosswood Chess engine worker: runs searches off the main thread. */
importScripts('engine.js');
const E = self.MWEngine;
const searcher = new E.Searcher(19);
const toU = m => (m ? E.toUci(m) : null);
self.onmessage = (ev) => {
  const msg = ev.data;
  try {
    const pos = E.Position.fromFen(msg.fen || E.START_FEN);
    for (const u of msg.moves || []) {
      const m = pos.moveFromUci(u);
      if (!m) throw new Error('Illegal move in history: ' + u);
      pos.make(m);
    }
    let res;
    if (msg.type === 'bot') {
      const r = E.chooseBotMove(searcher, pos, msg.cfg, msg.moves || [], (msg.fen || E.START_FEN) === E.START_FEN);
      res = { move: toU(r.move), why: r.why, score: r.score, depth: r.depth };
    } else if (msg.type === 'analyze') {
      searcher.useQ = true;
      if (msg.multi) {
        const a = searcher.analyze(pos, msg);
        res = { depth: a.depth, moves: a.moves.map(e => ({ move: toU(e.move), score: e.score, pv: (e.pv || []).map(toU), bound: e.bound })) };
      } else {
        const r = searcher.think(pos, msg);
        res = { depth: r.depth, move: toU(r.move), score: r.score, pv: (r.pv || []).map(toU) };
      }
    }
    self.postMessage({ id: msg.id, ok: true, res });
  } catch (err) {
    self.postMessage({ id: msg.id, ok: false, error: String(err && err.message || err) });
  }
};
