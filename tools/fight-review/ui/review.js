// ---- 56-review.js
// Fight review: why each graded pick won or lost, from UFCStats round-by-round numbers, the judges, Sherdog's
// round-by-round scoring and a written story of each fight; and each fighter's weak spots.
// Reads its own data block (#review-data). Never changes the model's numbers or its record: a pick that lost is still
// a miss here, the review only explains it.
// Data: { b: {boutId: review}, f: {fighterId: profile}, agg, rm, th, al, tdt }. Inside a bout review "p" is the
// model's pick and "o" its opponent, as the ledger records them; the page shows them as Fighter A and B.

let RV = null;
function rvData() {
  if (RV === null) RV = safe(() => JSON.parse(document.getElementById("review-data").textContent), false) || false;
  return RV;
}
const reviewOf = b => { const D = rvData(); return D && b && b.raw ? D.b[b.raw.id] || null : null; };
const rvProfile = id => { const D = rvData(); return D && D.f ? D.f[id] || null : null; };

// row layout per round and fighter
const RX = { kd: 0, sigL: 1, sigA: 2, tdL: 3, tdA: 4, sub: 5, rev: 6, ctrl: 7, tot: 8, head: 9, body: 10, leg: 11, dist: 12, clinch: 13, ground: 14 };

const RV_VERDICT = {
  unlucky: { label: "Unlucky", tone: "warn", icon: "dice",
    def: "The pick lost a fight it was winning, or one that could have gone either way: ahead on the cards when it was finished, a split or majority decision, or Sherdog's scorers or the round-by-round stats had the pick winning." },
  outclassed: { label: "Outclassed", tone: "neg", icon: "cross",
    def: "The pick was clearly beaten: two rounds or more on the judges' cards, or behind when it was finished." },
  close: { label: "Close", tone: "", icon: "swap",
    def: "Competitive either way: one round in it on every card, or level when it was finished." },
  earned: { label: "Earned", tone: "pos", icon: "check",
    def: "The pick won clearly: two rounds or more on the cards, or ahead when the fight was finished." },
  fortunate: { label: "Fortunate", tone: "warn", icon: "dice",
    def: "The pick won a fight it was losing, or one that could have gone either way: behind when it found the finish, a split or majority decision, or Sherdog's scorers or the stats had the other fighter winning." },
  quick: { label: "Quick finish", tone: "", icon: "bolt",
    def: "Finished early with too little fighting before the end to say who was ahead." },
};
const RV_ORDER_MISS = ["unlucky", "close", "outclassed", "quick"];
const RV_ORDER_HIT = ["earned", "close", "fortunate", "quick"];

const RV_WEAK = { takedown_defence: "Takedown defence", bottom_game: "Stuck on the bottom", clinch_fence: "Pinned in the clinch", got_hurt_standing: "Hurt on the feet",
  body_shots: "Body shots", leg_kicks: "Leg kicks", faded_late: "Faded late", slow_start: "Slow start", outworked_at_range: "Outworked at range",
  walked_onto_counters: "Walked onto counters", submission_defence: "Submission defence", scrambles_back_taken: "Lost scrambles, gave up the back",
  low_output: "Too little output", size_strength: "Size and strength", cut_or_injury: "Cut or injury", fouls: "Fouls", one_shot: "Caught by one moment", none_clear: "Nothing clear" };
const RV_EDGE = { wrestling: "Wrestling", top_control: "Top control", clinch_work: "Clinch work", power: "Power", volume: "Volume", counters: "Counters",
  body_work: "Body work", leg_kicks: "Leg kicks", submissions: "Submissions", scrambles: "Scrambles", pace_cardio: "Pace and cardio",
  size_strength: "Size and strength", durability: "Durability", adjustment: "Adjustments", opportunism: "Took a chance" };
const RV_MOMENT = { takedown: "Takedown", knockdown: "Knockdown", hurt: "Hurt him", sub_attempt: "Submission attempt", reversal: "Reversal",
  back_take: "Took the back", ground_and_pound: "Ground and pound", clinch_control: "Clinch control", body_shot: "Body shot", leg_kicks: "Leg kicks",
  comeback: "Comeback", cut: "Cut", injury: "Injury", point_deduction: "Point deducted", foul: "Foul", fatigue: "Tired", finish: "Finish" };
const RV_STYLE = () => (rvData() && rvData().al) || {};
const RV_FLAG_TITLE = { td_def: "Takedown defence", controlled: "Gets controlled", absorbs: "Absorbs a lot", chin: "Knockdowns", ko_losses: "Loses by KO",
  sub_losses: "Loses by submission", fade: "Fades late", first_td: "First takedown", late: "Finishes strong",
  vs_wrestler: "Against wrestlers", vs_submission: "Against submission hunters", vs_power: "Against power strikers", vs_volume: "Against volume strikers" };

// p/o (pick/opponent) -> a/b (Fighter A/B on this page) and names
const poSide = (b, po) => po === "p" ? b.pickSide : po === "o" ? b.oppSide : null;
const poName = (b, po) => po ? sideName(b, poSide(b, po)) : null;
const poLast = (b, po) => po ? lastName(poName(b, po)) : "";
const winnerPO = b => b.result.ok ? "p" : "o";
const loserPO = b => b.result.ok ? "o" : "p";
const mmss = s => `${Math.floor(s / 60)}:${String(Math.round(s % 60)).padStart(2, "0")}`;
const rowsAB = (b, r) => b.aIsPick ? [r[0], r[1]] : [r[1], r[0]];
// UFCStats method text in plain words: "Decision - Split" -> "split decision"
function rvHow(s) {
  if (!s) return "";
  const m = /^Decision - (\w+)/.exec(s); if (m) return m[1].toLowerCase() + " decision";
  if (/doctor/i.test(s)) return "TKO (doctor's stoppage)";
  if (/^KO\/TKO/.test(s)) return "KO/TKO";
  if (/^Submission/.test(s)) return "submission";
  if (/^DQ/.test(s)) return "disqualification";
  if (/could not continue/i.test(s)) return "TKO (could not continue)";
  return s;
}

function verdictPill(b, opts = {}) {
  const rv = reviewOf(b); if (!rv || !b.result) return null;
  const V = RV_VERDICT[rv.v]; if (!V) return null;
  const btn = h("button.pill.rv-pill" + (V.tone ? "." + V.tone : ""), { type: "button", "aria-label": `Fight review: ${V.label}. What this means` },
    icon(V.icon), V.label);
  return popTrigger(btn, () => paras(h("b", V.label), V.def, verdictWhy(b, rv), opts.link === false ? null : h("p", h("a", { href: "#" + b.route, onclick: () => { muTab = "review"; } }, "Open the fight review"))));
}

// On a fighter's page the verdict is told from that fighter's side: an unlucky loss, a fortunate win...
const RV_LOSER_CLASS = { unlucky: "unlucky", fortunate: "unlucky", outclassed: "outclassed", earned: "outclassed", close: "close", quick: "quick" };
function fighterVerdictPill(b, side) {
  const rv = reviewOf(b); if (!rv || !b.result) return null;
  const lc = RV_LOSER_CLASS[rv.v]; if (!lc) return null;
  const won = b.result.winnerSide === side;
  const [label, tone] = won
    ? { unlucky: ["Fortunate win", "warn"], outclassed: ["Clear win", "pos"], close: ["Close win", ""], quick: ["Quick finish", ""] }[lc]
    : { unlucky: ["Unlucky loss", "warn"], outclassed: ["Beaten clearly", "neg"], close: ["Close loss", ""], quick: ["Quick finish", ""] }[lc];
  const V = RV_VERDICT[rv.v];
  const btn = h("button.pill.rv-pill" + (tone ? "." + tone : ""), { type: "button", "aria-label": `${label}. What this means` }, label);
  return popTrigger(btn, () => paras(h("b", label), `For the model's pick this fight was “${V.label}”: ${V.def}`, verdictWhy(b, rv),
    h("p", h("a", { href: "#" + b.route, onclick: () => { muTab = "review"; } }, "Open the fight review"))));
}

// The reasons behind the verdict, in words.
function verdictWhy(b, rv) {
  const W = poLast(b, winnerPO(b)), Lr = poLast(b, loserPO(b));
  const bits = [];
  for (const w of rv.w || []) {
    let m;
    if (w === "split") bits.push("A split decision.");
    else if (w === "majority") bits.push("A majority decision.");
    else if (w === "sherdog_loser") bits.push(`Sherdog's scorers had ${Lr} winning.`);
    else if (w === "stats_loser") bits.push(`The round-by-round stats favoured ${Lr}.`);
    else if (w === "wide_cards") bits.push("Every judge had it by two rounds or more.");
    else if (w === "mostly_wide_cards") bits.push("The judges had it by two rounds or more on average.");
    else if (w === "one_round_cards") bits.push("One round was the difference on the cards.");
    else if (w === "stats_dominant") bits.push(`The round-by-round stats were one-sided for ${W}.`);
    else if ((m = /^rounds_(\d+)_(\d+)$/.exec(w))) {
      const l = +m[1], wn = +m[2];
      bits.push(l + wn === 0 ? "No completed rounds before the finish." : `Completed rounds before the finish: ${Lr} ${l}, ${W} ${wn}${(l + wn) < (rv.rd - 1) ? ", the rest even" : ""}.`);
    }
    else if (w === "final_round_loser") bits.push(`${Lr} was winning the round it ended in.`);
    else if (w === "final_round_winner") bits.push(`${W} was on top in the round it ended in.`);
    else if (w === "final_round_loser_stats") bits.push(`${Lr} was ahead on the numbers in the round it ended in, even counting the finishing flurry.`);
    else if (w === "final_round_even") bits.push("The round it ended in was even until the finish.");
    else if (w === "first_round_no_read") bits.push("Over in the first round, with no reliable read on who was ahead.");
    else if (w === "under_a_minute") bits.push("Over inside a minute.");
    else if (w === "disqualification") bits.push("Ended by disqualification.");
    else if (w === "doctor_or_injury") bits.push("Stopped by the doctor or an injury.");
  }
  return bits.length ? h("p.small", bits.join(" ")) : null;
}

function underratedText(b, rv) {
  const W = poName(b, winnerPO(b)), Lr = poName(b, loserPO(b));
  const mk = b.market && b.market.usable ? b.market : null;
  const mp = mk ? (mk.pPick) : null;
  switch (rv.ub) {
    case "both": return [`Both had ${lastName(Lr)} favoured: the model at ${pct(b.p)}, the market at ${pct(mp)}.`, `${W} was underrated by both.`];
    case "model": return [`The market had ${lastName(W)} as favourite (${pct(1 - mp)}); the model gave ${lastName(Lr)} ${pct(b.p)}.`, "Only the model missed."];
    case "model_unpriced": return [`No usable price. The model gave ${lastName(Lr)} ${pct(b.p)}.`, ""];
    case "market": return [`The market favoured ${lastName(poName(b, "o"))} (${pct(1 - mp)}); the model's pick ${lastName(W)} (${pct(b.p)}) won.`, "The model beat the market here."];
    case "neither": return [`Model (${pct(b.p)}) and market (${pct(mp)}) both had ${lastName(W)}.`, ""];
    default: return ["", ""];
  }
}

// ---------- pieces of the review tab
function rvVerdictPanel(b, rv) {
  const V = RV_VERDICT[rv.v] || RV_VERDICT.close;
  const r = b.result;
  const [ub1, ub2] = underratedText(b, rv);
  const judges = (rv.j || []).map(j => { const [a, bb] = b.aIsPick ? [j[1], j[2]] : [j[2], j[1]]; return `${j[0].split(" ").slice(-1)[0]} ${a}–${bb}`; });
  const st = rv.st;
  const resTxt = `${r.winnerName} won by ${rvHow(rv.how) || r.how}${rv.rd && r.how !== "Decision" ? ` in round ${rv.rd} at ${rv.tm}` : ""}.`;
  return h("section.panel.rv-verdict.tone-" + (V.tone || "plain"),
    h("div.panel-head", h("div",
      h("span.eyebrow", r.ok ? "Model right · fight review" : "Model wrong · fight review"),
      h("h2.h-sec.rv-vh", icon(V.icon), V.label),
      h("p.cap", V.def)),
      infoBtn("How the verdict is decided", rvMethodNote)),
    verdictWhy(b, rv),
    h("dl.kv",
      h("dt", "Result"), h("dd", resTxt),
      judges.length ? h("dt", `Judges (${lastName(b.A.name)}–${lastName(b.B.name)})`) : null, judges.length ? h("dd", judges.join(" · ")) : null,
      rv.sd && rv.sd.t && rv.sd.t.length ? h("dt", "Sherdog's scorers") : null,
      rv.sd && rv.sd.t && rv.sd.t.length ? h("dd", rv.sd.t.map(t => { const [a, bb] = b.aIsPick ? t : [t[1], t[0]]; return `${a}–${bb}`; }).join(" · ")) : null,
      h("dt", "Who had it wrong"), h("dd.rv-wrap", ub1, ub2 ? h("br") : null, ub2 ? h("b", ub2) : null),
      st && st.lk ? h("dt", "Luck") : null, st && st.lk ? h("dd.rv-wrap", h("b", { none: "Little", some: "Some", big: "A lot" }[st.lk] || st.lk), st.lkn ? ` · ${st.lkn}` : "") : null),
    rv.det ? h("p.foot", `Finish: ${rv.det}${rv.ref ? ` · referee ${rv.ref}` : ""}.`) : rv.ref ? h("p.foot", `Referee ${rv.ref}.`) : null);
}

function rvStoryPanel(b, rv) {
  const st = rv.st; if (!st) return null;
  const finish = b.result.how !== "Decision";
  const lean = x => x === "e" ? "Even" : x ? poLast(b, x) + " ahead" : "—";
  return h("section.panel",
    h("div.panel-head", h("div", h("span.eyebrow", "How it went"), h("h3.h-sec", "The fight"))),
    h("p.rv-story", st.s),
    st.tp ? h("p.small", h("b", "Turning point: "), st.tp) : null,
    finish ? h("div.rv-before",
      h("div", h("span.eyebrow", "In the final round, before the finish"), h("b", lean(st.bf)), st.bfn ? h("small", st.bfn) : null),
      h("div", h("span.eyebrow", "Over the whole fight at that moment"), h("b", lean(st.af)))) : null,
    h("p.foot", "Written from Sherdog's round-by-round play-by-play and the UFCStats numbers, checked by a second reader. ",
      rv.sd ? h("a", { href: `https://www.sherdog.com/news/news/playbyplay-${rv.sd.a}`, target: "_blank", rel: "noopener noreferrer" }, "Read Sherdog's coverage") : null, "."));
}

function rvBar(a, bb) {
  const t = a + bb, wa = t ? a / t * 100 : 50;
  return h("div.rv-bar", { "aria-hidden": "true" }, h("i.a", { style: { width: wa + "%" } }), h("i.b", { style: { width: (100 - wa) + "%" } }));
}
function rvRoundsPanel(b, rv) {
  const rounds = rv.r || [];
  const finish = b.result.how !== "Decision";
  const host = h("div.rv-rounds");
  const scorerChip = code => {
    const po = code && code[0] !== "e" && code[0] !== "?" ? code[0] : null;
    const side = po ? poSide(b, po) : null;
    return h("span.rv-sc" + (side ? "." + side : ""), { title: po ? `${poLast(b, po)} ${code.endsWith("8") ? "10-8" : "10-9"}` : code === "e" ? "10-10" : "Unclear" },
      po ? (code.endsWith("8") ? "8" : "") : "=");
  };
  rounds.forEach((rr, i) => {
    const [A, B] = rowsAB(b, rr);
    const n = i + 1;
    const last = n === rounds.length;
    const pA = b.aIsPick ? rv.pr[i] : 1 - rv.pr[i];
    const statLead = pA >= .5 ? b.A : b.B, statP = Math.max(pA, 1 - pA);
    const sc = rv.sd && rv.sd.sc ? (rv.sd.sc.find(x => x[0] === n) || [null, null])[1] : null;
    const scTally = sc && sc.length ? (() => {
      const c = { a: 0, b: 0, e: 0 }; sc.forEach(x => { const po = x && x[0]; const s = po === "p" || po === "o" ? poSide(b, po) : "e"; c[s]++; });
      return c.a > c.b ? `${lastName(b.A.name)} ${c.a}–${c.b}` : c.b > c.a ? `${lastName(b.B.name)} ${c.b}–${c.a}` : "Split";
    })() : null;
    const note = rv.st && rv.st.rn && rv.st.rn[i] ? rv.st.rn[i] : null;
    const secs = last ? (rv.sec - 300 * i) : 300;
    const line = (k, a, bb, fa, fb, unit) => h("div.rv-line",
      h("span.rv-va.num", fa != null ? fa : String(a)), rvBar(a, bb), h("span.rv-k", k, unit ? h("small", unit) : null), rvBar(bb, a), h("span.rv-vb.num", fb != null ? fb : String(bb)));
    const lines = [
      line("Sig. strikes", A[RX.sigL], B[RX.sigL], `${A[RX.sigL]}/${A[RX.sigA]}`, `${B[RX.sigL]}/${B[RX.sigA]}`, "landed/thrown"),
      A[RX.kd] + B[RX.kd] ? line("Knockdowns", A[RX.kd], B[RX.kd]) : null,
      A[RX.tdA] + B[RX.tdA] ? line("Takedowns", A[RX.tdL], B[RX.tdL], `${A[RX.tdL]}/${A[RX.tdA]}`, `${B[RX.tdL]}/${B[RX.tdA]}`, "landed/tried") : null,
      A[RX.ctrl] + B[RX.ctrl] ? line("Control", A[RX.ctrl], B[RX.ctrl], mmss(A[RX.ctrl]), mmss(B[RX.ctrl])) : null,
      A[RX.sub] + B[RX.sub] ? line("Sub. attempts", A[RX.sub], B[RX.sub]) : null,
      A[RX.ground] + B[RX.ground] ? line("Ground strikes", A[RX.ground], B[RX.ground], null, null, "significant") : null,
      A[RX.body] + B[RX.body] >= 4 ? line("Body", A[RX.body], B[RX.body]) : null,
      A[RX.leg] + B[RX.leg] >= 4 ? line("Legs", A[RX.leg], B[RX.leg]) : null,
    ];
    host.append(h("article.rv-round" + (last && finish ? ".ended" : ""),
      h("div.rv-rh",
        h("b", `Round ${n}`),
        h("span.faint.xsmall.num", last && finish ? `ended ${rv.tm}` : secs < 300 ? mmss(secs) : "5:00"),
        h("span.sp"),
        sc && sc.length ? h("span.rv-sd", { "aria-label": `Sherdog's three scorers: ${scTally}` }, h("span.faint.xsmall", "Sherdog"), ...sc.map(scorerChip)) : null,
        h("span.rv-stat.xsmall", { title: "Chance this round's numbers win the round, from a model fitted on Sherdog's scored rounds" },
          h("span.swatch." + statLead.side), ` Stats ${lastName(statLead.name)} ${pct(statP)}`)),
      h("div.rv-lines", lines),
      note ? h("p.rv-rnote", note[0] && note[0] !== "e" ? h("b", poLast(b, note[0]) + ": ") : note[0] === "e" ? h("b", "Even: ") : null, note[1]) : null));
  });
  const judges = rv.j && rv.j.length;
  return h("section.panel",
    h("div.panel-head", h("div", h("span.eyebrow", "Round by round"), h("h3.h-sec", "What happened in each round"),
      h("p.cap", "UFCStats numbers for each round. Chips show how Sherdog's three scorers called the round (a number marks a 10-8); “Stats” is the chance the round's numbers win it, from a model fitted on Sherdog's scored rounds.")),
      infoBtn("About the stats round model", rvRoundModelNote)),
    h("div.cmp-heads", h("span", h("span.swatch.a"), " ", fighterLink(b.A, b.A.name)), h("span", fighterLink(b.B, b.B.name), " ", h("span.swatch.b"))),
    host,
    !rv.sd ? h("p.foot", "No Sherdog play-by-play matched this bout, so rounds are judged on the stats alone.") : null,
    judges ? null : null);
}

function rvMomentsPanel(b, rv) {
  const st = rv.st; if (!st) return null;
  const items = [];
  const td = st.td || [], hu = st.hu || [];
  const ul = h("ul.rv-moments");
  (st.m || []).forEach(m => {
    const side = poSide(b, m[2]);
    ul.append(h("li", h("span.rv-rd.num", "R" + m[0]), h("span.swatch." + (side || "n")),
      h("div", h("b", `${RV_MOMENT[m[1]] || m[1]}`), m[2] ? h("span.faint", ` · ${poLast(b, m[2])}`) : null, m[3] ? h("small", m[3]) : null)));
  });
  return h("section.panel",
    h("div.panel-head", h("div", h("span.eyebrow", "Key moments"), h("h3.h-sec", "What decided it"))),
    h("div.rv-firsts",
      h("div", h("span.eyebrow", "First takedown"),
        td[0] ? h("b", `${poLast(b, td[0])}, round ${td[1]}`) : h("b", "None landed"), td[2] ? h("small", td[2]) : null),
      h("div", h("span.eyebrow", "First time hurt"),
        hu[0] ? h("b", `${poLast(b, hu[0])}, round ${hu[1]}`) : h("b", "Nobody clearly hurt"), hu[0] && hu[2] ? h("small", `By ${hu[2]}`) : null)),
    (st.m || []).length ? ul : null);
}

function rvProjPanel(b, rv) {
  if (!b.proj || !rv.r) return null;
  const tot = (side, k) => rv.r.reduce((s, rr) => s + rowsAB(b, rr)[side === "a" ? 0 : 1][k], 0);
  const secs = rv.sec || 0;
  if (secs < 240) return h("section.panel", h("h3.h-sec", "Projected against what happened"),
    h("p.small.muted", `Over in ${mmss(secs)}, too short to compare per-15-minute rates. The raw counts are in the rounds above.`));
  const m15 = secs / 900;
  const rows = [
    ["Sig. strikes", "landed a 15 min", "str", s => tot(s, RX.sigL) / m15, x => String(Math.round(x))],
    ["Takedowns", "landed a 15 min", "td", s => tot(s, RX.tdL) / m15, x => fixed(x, 1)],
    ["Control", "% of fight time", "ctl", s => tot(s, RX.ctrl) / secs, x => pct(x)],
    ["Knockdowns", "a 15 min", "ko", s => tot(s, RX.kd) / m15, x => fixed(x, 2)],
    ["Sub. attempts", "a 15 min", "sub", s => tot(s, RX.sub) / m15, x => fixed(x, 1)],
  ];
  const cell = (proj, act, fmt, k) => {
    const big = k === "ctl" ? Math.abs(act - proj) >= .2 : k === "str" ? Math.abs(act - proj) >= Math.max(15, proj * .5) : k === "td" ? Math.abs(act - proj) >= 2 : false;
    return h("td.n" + (big ? ".rv-off" : ""), h("span.faint", fmt(proj)), " → ", h("b", fmt(act)));
  };
  const flipTxt = [];
  if ((rv.fl || []).includes("strikes")) flipTxt.push("The striking went the other way from the projection.");
  if ((rv.fl || []).includes("control")) flipTxt.push("The fighter expected to control the fight on the mat was the one controlled.");
  if ((rv.fl || []).includes("control_surprise")) flipTxt.push("The model expected little grappling; one fighter spent long spells in control.");
  // read the style right but still picked the other man: the winner's grappling was projected and it arrived
  const Wpo = winnerPO(b), Wside = poSide(b, Wpo);
  const wProj = b.proj[Wside], wTd = tot(Wside, RX.tdL) / m15, wCtl = tot(Wside, RX.ctrl) / secs;
  const sawIt = !b.result.ok && wProj && ((wProj.td >= 2 && wTd >= Math.max(1.5, .6 * wProj.td)) || (wProj.ctl >= .3 && wCtl >= .6 * wProj.ctl));
  const sawNote = sawIt ? h("div.note", icon("info"), h("p", h("b", "Read the style, still missed: "),
    `the model projected ${lastName(sideName(b, Wside))}'s grappling (${fixed(wProj.td, 1)} takedowns and ${pct(wProj.ctl)} control a 15 minutes) and got it (${fixed(wTd, 1)} and ${pct(wCtl)}), yet still made ${lastName(sideName(b, b.pickSide))} the favourite at ${pct(b.p)}. The win chance didn't give that wrestling enough weight here.`)) : null;
  return h("section.panel",
    h("div.panel-head", h("div", h("span.eyebrow", "Model projection"), h("h3.h-sec", "Projected against what happened"),
      h("p.cap", "The model's per-15-minute projection for each fighter, then the actual rate in this fight. Highlighted cells missed by a lot."))),
    h("div.tablewrap.flat", h("table.table.rv-proj",
      h("thead", h("tr", h("th", ""), h("th.n", h("span.swatch.a"), " ", lastName(b.A.name)), h("th.n", h("span.swatch.b"), " ", lastName(b.B.name)))),
      h("tbody", rows.map(([k, unit, key, act, fmt]) => h("tr", h("td", k, h("small.faint", " " + unit)),
        cell(b.proj.a[key], act("a"), fmt, key), cell(b.proj.b[key], act("b"), fmt, key)))))),
    flipTxt.length ? h("div.note.warn", icon("alert"), h("p", h("b", "Read wrong: "), flipTxt.join(" "))) : h("p.foot", "The fight followed the projection's broad shape (who out-struck whom, who controlled)."),
    sawNote);
}

function rvFlagList(p, max = 6) {
  if (!p) return null;
  const items = [...(p.flags || []).map(f => ["neg", f]), ...(p.strengths || []).map(f => ["pos", f])].slice(0, max);
  if (!items.length) return h("p.small.muted", "Nothing stands out in their UFC record once sample size is allowed for.");
  return h("ul.rv-flags", items.map(([tone, [code, text]]) => h("li." + tone, h("span.mark." + (tone === "neg" ? "no" : "ok"), icon(tone === "neg" ? "alert" : "check"), RV_FLAG_TITLE[code] || code), h("span", text))));
}

function rvLessonsPanel(b, rv) {
  const st = rv.st;
  const W = winnerPO(b), Lo = loserPO(b);
  const fW = sideRef(b, poSide(b, W)), fL = sideRef(b, poSide(b, Lo));
  const sty = rv.sty ? { p: rv.sty[0], o: rv.sty[1] } : null;
  const styleTxt = po => sty && sty[po] && sty[po] !== "unknown" ? (RV_STYLE()[sty[po]] || sty[po]) : null;
  const block = (f, po, tags, note, dict, lab) => h("div.rv-lesson",
    h("div.rv-lesson-h", portrait(f, f.side, "xs"), h("b", fighterLink(f, f.name)), h("span.faint.xsmall", lab)),
    tags && tags.length ? h("div.chips", tags.map(t => h("span.pill" + (lab === "Lost" ? ".neg" : ".pos"), dict[t] || t))) : null,
    note ? h("p.small", note) : null,
    styleTxt(po) ? h("p.xsmall.faint", `On fight night they profiled as: ${styleTxt(po).toLowerCase()}.`) : null,
    h("span.eyebrow", "Weak spots and strengths on their UFC record"), rvFlagList(rvProfile(f.id), 4),
    h("a.btn.btn-sm.btn-ghost", { href: "#fighter-" + f.id }, `${lastName(f.name)}'s profile`, icon("chevR")));
  return h("section.panel",
    h("div.panel-head", h("div", h("span.eyebrow", "Takeaways"), h("h3.h-sec", "What it says about them"),
      h("p.cap", "How this fight was won and lost, then each fighter's weak spots across every UFC fight on record. One fight is one fight: lean on the record, not the single result."))),
    h("div.grid-2", block(fL, Lo, st ? st.lw : null, st ? st.lwn : null, RV_WEAK, "Lost"), block(fW, W, st ? st.we : null, st ? st.wen : null, RV_EDGE, "Won")));
}

function rvMethodNote() {
  const D = rvData(); const rm = D && D.rm || {};
  return paras(h("b", "How the verdict is decided"),
    "Decisions: a split or majority decision, Sherdog's scorers having the loser ahead, or the round-by-round stats favouring the loser by half a round or more make it unlucky (or fortunate, for a pick that won). Two rounds or more on the cards is clear. Anything else is close.",
    "Finishes: completed rounds are scored with Sherdog's scorers where they exist, otherwise with the stats. The written review says who was ahead in the round it ended in, before the finish. Ahead when finished is unlucky; level is close; behind is outclassed (or earned).",
    `The stats round model was fitted on ${NF.format(rm.n_rounds || 0)} rounds that Sherdog's scorers scored, and agrees with them about ${pct(rm.cv_accuracy || 0)} of the time on rounds it wasn't fitted on.`,
    h("p.faint", "None of this changes the record: a miss is still a miss."));
}
function rvRoundModelNote() {
  const D = rvData(); const rm = D && D.rm || {}; const w = rm.weights || {};
  return paras(h("b", "Stats round model"),
    `A logistic model of who wins a round from the round's numbers, fitted on ${NF.format(rm.n_rounds || 0)} rounds scored by Sherdog's three scorers (majority call), 2024–26. Cross-validated, it picks Sherdog's winner ${pct(rm.cv_accuracy || 0)} of the time.`,
    `Per round, in log-odds: each significant strike landed ${fixed(w.sig_strikes, 2)}, each knockdown ${fixed(w.knockdowns, 2)}, each takedown ${fixed(w.takedowns, 2)}, each minute of control ${fixed(w.control_min, 2)}, each submission attempt ${fixed(w.sub_attempts, 2)}.`,
    h("p.faint", "It reads only the numbers, so a big moment that UFCStats doesn't count (a hurt that isn't a knockdown) can make it disagree with the scorers."));
}

function reviewTab(b) {
  if (!b.result) return weakSpotsTab(b);
  const rv = reviewOf(b);
  const out = h("div.mu-stack.rv");
  if (!rv) { out.append(emptyState("No fight review for this bout", "UFCStats has no round-by-round record for it.")); return out; }
  [rvVerdictPanel(b, rv), rvStoryPanel(b, rv), rvRoundsPanel(b, rv), rvMomentsPanel(b, rv), rvProjPanel(b, rv), rvLessonsPanel(b, rv)].forEach(p => p && out.append(p));
  return out;
}

// ---------- weak spots for a booked bout
function weakSpotsTab(b) {
  const out = h("div.mu-stack.rv");
  const D = rvData();
  if (!D) { out.append(emptyState("No weak-spot data", "This build of the page has no fight review data.")); return out; }
  const pa = rvProfile(b.A.id), pb = rvProfile(b.B.id);
  const watch = [];
  const check = (f, p, opp, po) => {
    if (!p || !po) return;
    const st = po.arch_now;
    if (st === "wrestler" && p.flags.some(x => ["td_def", "controlled", "first_td", "vs_wrestler"].includes(x[0])))
      watch.push(`${lastName(opp.name)} profiles as a wrestler, and ${lastName(f.name)} has a grappling weak spot: ${p.flags.filter(x => ["td_def", "controlled", "first_td", "vs_wrestler"].includes(x[0])).map(x => (RV_FLAG_TITLE[x[0]] || x[0]).toLowerCase()).join(", ")}.`);
    if ((st === "power" || st === "volume") && p.flags.some(x => ["chin", "absorbs", "ko_losses", "vs_power", "vs_volume"].includes(x[0])))
      watch.push(`${lastName(opp.name)} is a ${st === "power" ? "power" : "volume"} striker, and ${lastName(f.name)} has a striking weak spot: ${p.flags.filter(x => ["chin", "absorbs", "ko_losses", "vs_power", "vs_volume"].includes(x[0])).map(x => (RV_FLAG_TITLE[x[0]] || x[0]).toLowerCase()).join(", ")}.`);
    if (st === "submission" && p.flags.some(x => ["sub_losses", "vs_submission"].includes(x[0])))
      watch.push(`${lastName(opp.name)} hunts submissions, and ${lastName(f.name)} has lost that way before.`);
    if (p.flags.some(x => x[0] === "fade") && b.rounds === 5) watch.push(`${lastName(f.name)} tends to fade late, and this is a five-rounder.`);
  };
  check(b.A, pa, b.B, pb); check(b.B, pb, b.A, pa);
  const t = D.tdt && D.tdt.model;
  out.append(h("section.panel",
    h("div.panel-head", h("div", h("span.eyebrow", "Before the fight"), h("h3.h-sec", "Weak spots to watch"),
      h("p.cap", "Where one fighter's style meets the other's weak spot, from every UFC fight on record. Descriptive, not part of the model's forecast."))),
    watch.length ? h("ul.bullets", watch.map(x => h("li", x))) : h("p.small.muted", "No clear style-against-weakness clash between these two on the record."),
    t ? h("div.note", icon("info"), h("p", h("b", "How much to read into it: "),
      `across ${NF.format(t.n)} graded bouts, a takedown mismatch (one fighter gets taken down a lot, the other takes people down a lot) didn't win more often than the model's or the market's prices already said. Treat these as things to watch, not an edge.`)) : null));
  out.append(h("div.grid-2", rvFighterCard(b.A, pa), rvFighterCard(b.B, pb)));
  return out;
}

function rvFighterCard(f, p) {
  const host = h("section.panel.rv-fcard",
    h("div.rv-lesson-h", portrait(f, f.side, "sm"), h("div", h("b", fighterLink(f, f.name)),
      h("small.faint", p ? `${plural(p.n, "UFC fight")} · ${Math.round(p.mins)} min${p.arch_now && p.arch_now !== "unknown" ? " · " + (RV_STYLE()[p.arch_now] || p.arch_now) : ""}` : "No UFC fight statistics"))));
  if (!p) return host;
  host.append(rvFlagList(p, 6));
  const fin = p.fin || [0, 0, 0, 0, 0];
  const losses = fin[1] + fin[2] + fin[3] + fin[4];
  host.append(h("dl.kv",
    h("dt", "UFC losses"), h("dd", losses ? `${losses}: ${fin[1]} KO/TKO, ${fin[2]} submission, ${fin[3]} decision` : "None"),
    h("dt", "Takedowns stopped"), h("dd", p.tdd[1] ? `${p.tdd[1] - p.tdd[0]} of ${p.tdd[1]}` : "None tried", p.tdd[1] ? h("small", ` · ${pct(p.tdd[2])} adjusted, division ${pct(p.tdd[3])}`) : null),
    h("dt", "Time controlled"), h("dd", pct(p.cc[0]), h("small", ` · division ${pct(p.cc[2])}`)),
    h("dt", "After conceding the first takedown"), h("dd", (p.ftd.conceded[0] + p.ftd.conceded[1]) ? `${p.ftd.conceded[0]}–${p.ftd.conceded[1]}` : "Never happened")));
  return host;
}

// ---------- fighter page panel
function weakSpotsPanel(f) {
  const p = rvProfile(f.id); if (!p) return null;
  const fin = p.fin || [0, 0, 0, 0, 0];
  const losses = fin[1] + fin[2] + fin[3] + fin[4];
  const AL = RV_STYLE();
  const archRows = Object.entries(p.arch || {}).filter(([k]) => k !== "unknown").sort((x, y) => y[1][0] - x[1][0]);
  const fadeRows = (p.fade || []).filter(x => x[2] >= 1);
  const tagRows = Object.entries(p.tags || {}).sort((a, b) => b[1] - a[1]);
  const D = rvData();
  return h("section.panel.rv-fighter",
    h("div.panel-head", h("div", h("h2.h-sec", "Weak spots"),
      h("p.cap", `From all ${plural(p.n, "UFC fight")} on UFCStats (${Math.round(p.mins)} minutes), round by round. Rates are pulled toward the division average until the sample is big enough, so a short career can't look extreme.`)),
      infoBtn("How weak spots are found", () => paras(h("b", "Weak spots"),
        "A weak spot is flagged when, after allowing for sample size, the fighter is among the worst fifth of their division (worst seventh for knockdowns), or when the same thing keeps happening: most losses by KO, losing most fights where the opponent lands the first takedown, a clear drop from round 1 to round 3, or a losing record against one style.",
        `Styles are each opponent's UFC numbers going into that fight: wrestlers land ${D.th.wrestler_td15}+ takedowns a 15 minutes or control ${pct(D.th.wrestler_ctl)}+ of the time; submission hunters try ${D.th.sub15}+ submissions a 15; power strikers score ${D.th.kd15}+ knockdowns a 15; volume strikers land ${D.th.slpm}+ significant strikes a minute.`,
        h("p.faint", "These describe the record. In testing, takedown mismatches didn't beat the model's or the market's prices.")))),
    rvFlagList(p, 8),
    h("div.grid-2",
      h("div",
        h("h3.h-sub.stats-sub", "How they lose"),
        h("dl.kv",
          h("dt", "UFC record (UFCStats)"), h("dd", `${fin[0]}–${losses}`),
          h("dt", "Losses by KO/TKO"), h("dd", String(fin[1])), h("dt", "By submission"), h("dd", String(fin[2])), h("dt", "By decision"), h("dd", String(fin[3])),
          h("dt", "Knocked down"), h("dd", `${p.kd[0]} times`, h("small", ` · ${fixed(p.kd[2], 2)} a 15 adjusted, division ${fixed(p.kd[3], 2)}`)))),
      h("div",
        h("h3.h-sub.stats-sub", "On the mat"),
        h("dl.kv",
          h("dt", "Takedowns stopped"), h("dd", p.tdd[1] ? `${p.tdd[1] - p.tdd[0]} of ${p.tdd[1]}` : "None tried", p.tdd[1] ? h("small", ` · ${pct(p.tdd[2])} adjusted`) : null),
          h("dt", "Division average"), h("dd", pct(p.tdd[3])),
          h("dt", "Taken down"), h("dd", `${fixed(p.tk[0], 2)} a 15`, h("small", ` · division ${fixed(p.tk[2], 2)}`)),
          h("dt", "Time controlled"), h("dd", pct(p.cc[0]), h("small", ` · division ${pct(p.cc[2])}`)),
          h("dt", "After conceding the first takedown"), h("dd", (p.ftd.conceded[0] + p.ftd.conceded[1]) ? `won ${p.ftd.conceded[0]}, lost ${p.ftd.conceded[1]}` : "Never happened"),
          h("dt", "After landing the first takedown"), h("dd", (p.ftd.landed[0] + p.ftd.landed[1]) ? `won ${p.ftd.landed[0]}, lost ${p.ftd.landed[1]}` : "Never happened")))),
    fadeRows.length ? h("div", h("h3.h-sub.stats-sub", "Round by round"),
      h("div.tablewrap.flat", h("table.table",
        h("thead", h("tr", h("th", "Round"), h("th.n", "Strike difference a minute"), h("th.n", "Rounds"))),
        h("tbody", fadeRows.map(([r, v, n]) => h("tr", h("td", "Round " + r), h("td.n" + (v < 0 ? ".rv-neg" : ".rv-pos"), v == null ? "—" : (v > 0 ? "+" : "") + fixed(v, 1)), h("td.n", String(n)))))))) : null,
    archRows.length ? h("div", h("h3.h-sub.stats-sub", "Against each style"),
      h("div.tablewrap.flat", h("table.table",
        h("thead", h("tr", h("th", "Opponent's style on the night"), h("th.n", "Record"), h("th.n", "Rounds won on the stats"))),
        h("tbody", archRows.map(([k, v]) => h("tr", h("td", AL[k] || k), h("td.n", `${v[1]}–${v[2]}${v[0] - v[1] - v[2] > 0 ? "–" + (v[0] - v[1] - v[2]) : ""}`), h("td.n", pct(v[3])))))))) : null,
    tagRows.length ? h("div", h("h3.h-sub.stats-sub", "In their reviewed losses"),
      h("div.chips", tagRows.map(([t, n]) => h("span.pill.neg", `${RV_WEAK[t] || t}${n > 1 ? " ×" + n : ""}`)))) : null);
}

// ---------- the Fight review page
const RV_KEY = "osl.ui.review.v1";
const rvUI = Object.assign({ win: "season", res: "miss", v: "all", ub: "all", q: "", n: 40 }, LS.get(RV_KEY, {}), { n: 40 });
const saveRv = () => LS.set(RV_KEY, { win: rvUI.win, res: rvUI.res, v: rvUI.v, ub: rvUI.ub });

function rvResidRows(obj, labels, base) {
  const rows = Object.entries(obj || {}).filter(([, v]) => v && v.model && v.model.n >= 20)
    .sort((a, b) => (labels.order || []).indexOf(a[0]) - (labels.order || []).indexOf(b[0]));
  if (base && base.model) rows.unshift(["__all", base]);
  const f = x => x == null ? "—" : `${x.resid > 0 ? "+" : ""}${pctNum(x.resid)} pts`;
  const ci = x => x == null ? "" : `${pctNum(x.lo)} to ${pctNum(x.hi)}`;
  return h("div.tablewrap.flat", h("table.table",
    h("thead", h("tr", h("th", labels.head), h("th.n", "Bouts"), h("th.n", "Pick won"), h("th.n", "Model said"), h("th.n", "Model gap"), h("th.n", "Market gap"))),
    h("tbody", rows.map(([k, v]) => h("tr" + (k === "__all" ? ".rv-base" : ""),
      h("td", k === "__all" ? h("b", "All bouts (baseline)") : (labels.map[k] || k)),
      h("td.n", NF.format(v.model.n)), h("td.n", pct(v.model.won)), h("td.n", pct(v.model.said)),
      h("td.n", { title: "95% range " + ci(v.model) }, f(v.model), h("small.faint", " " + ci(v.model))),
      h("td.n", v.market ? h("span", { title: "95% range " + ci(v.market) }, f(v.market), h("small.faint", " " + ci(v.market))) : "—"))))));
}

VIEWS.review = host => {
  document.title = "Fight review · Octagon Season Ledger";
  const D = rvData();
  host.append(h("div.page-head", h("div", h("span.eyebrow", "Why picks won or lost"), h("h1.h-page", "Fight review"),
    h("p.lede", "Every graded fight checked round by round: UFCStats numbers, the judges, Sherdog's round scoring and a written story of the fight. Was a miss bad luck, a coin flip or a beating? Did the model and the market both underrate the winner? Does a fighter keep losing the same way?"))));
  if (!D) { host.append(emptyState("No review data in this build", "The page was built without the fight review block.")); return; }
  const body = h("div.view");
  host.append(h("div.toolbar",
    segmented([["season", "2025–26 season"], ["all", "Since 2019"]], rvUI.win, v => { rvUI.win = v; rvUI.n = 40; saveRv(); draw(); }, "Window")), body);
  const inWin = b => rvUI.win === "all" || b.date >= "2025-01-01";

  function draw() {
    clear(body);
    const A = D.agg[rvUI.win];
    const xs = L.graded.filter(inWin).filter(b => reviewOf(b));
    const misses = xs.filter(b => !b.result.ok), hits = xs.filter(b => b.result.ok);
    const cnt = (ys, v) => ys.filter(b => reviewOf(b).v === v).length;
    const tile = (v, ys, sub) => { const k = cnt(ys, v), V = RV_VERDICT[v];
      return h("button.stat.rv-tile.tone-" + (V.tone || "plain"), { type: "button", onclick: () => { rvUI.res = sub; rvUI.v = v; rvUI.n = 40; saveRv(); draw(); listHost.scrollIntoView({ behavior: reduceMotion() ? "auto" : "smooth", block: "start" }); } },
        h("span.l", icon(V.icon), V.label), h("span.v.num", ys.length ? pct(k / ys.length) : "—"), h("span.s", `${frac(k, ys.length)} ${sub === "miss" ? "misses" : "right picks"}`)); };
    body.append(h("section.pf-sec",
      h("div.sec-head", h("h2.h-sec", `The misses: ${NF.format(misses.length)} of ${NF.format(xs.length)}`),
        h("p.cap", "How each losing pick lost. Unlucky and close misses are the ones a model can't be blamed much for; outclassed misses are where it misread the fight.")),
      h("div.stats", RV_ORDER_MISS.map(v => tile(v, misses, "miss")))));
    body.append(h("section.pf-sec",
      h("div.sec-head", h("h2.h-sec", `The right picks: ${NF.format(hits.length)}`),
        h("p.cap", "The same check on the wins. A fortunate win counts as right in the record, but it flattered the model.")),
      h("div.stats", RV_ORDER_HIT.map(v => tile(v, hits, "hit")))));

    // who underrated the winner
    const ubc = k => misses.filter(b => reviewOf(b).ub === k).length;
    const both = misses.filter(b => reviewOf(b).ub === "both"), bothOut = both.filter(b => reviewOf(b).v === "outclassed");
    body.append(h("div.grid-2",
      h("section.panel",
        h("div.panel-head", h("div", h("h3.h-sec", "Who had the winner wrong"), h("p.cap", "For every miss: did the market also have the loser favoured?"))),
        barList([
          { label: "Model and market both had the loser", n: misses.length, k: ubc("both") },
          { label: "Only the model: the market had the winner", n: misses.length, k: ubc("model") },
          { label: "No usable price", n: misses.length, k: ubc("model_unpriced"), thin: true },
        ]),
        h("p.small", h("b", `${NF.format(bothOut.length)} misses`), " were outclassed fights where both the model and the market had the loser favoured: the winners both of them genuinely underrated. ",
          h("button.linkbtn", { type: "button", onclick: () => { rvUI.res = "miss"; rvUI.v = "outclassed"; rvUI.ub = "both"; saveRv(); draw(); listHost.scrollIntoView({ block: "start" }); } }, "Show them"))),
      h("section.panel",
        h("div.panel-head", h("div", h("h3.h-sec", "How the losers were beaten"), h("p.cap", A.stories ? `From the written reviews of ${NF.format(A.stories)} fights with a Sherdog play-by-play.` : "No written reviews in this window."))),
        A.stories ? barList(Object.entries(A.loser_weak || {}).filter(([t]) => t !== "none_clear").sort((a, b) => b[1] - a[1]).slice(0, 8)
          .map(([t, n]) => ({ label: RV_WEAK[t] || t, n: A.stories, k: n }))) : null,
        A.stories ? h("p.foot", "Share of reviewed fights where the loser was beaten this way (a fight can have up to three).") : null)));

    // style check
    const SL = Object.assign({}, D.al);
    body.append(h("section.panel",
      h("div.panel-head", h("div", h("h3.h-sec", "Style check: does the model have a blind spot?"),
        h("p.cap", "How often the model's pick won against each kind of opponent, against what the model and the market said. Compare each row with the baseline: the model runs a little cautious overall, so most gaps sit above zero. A style whose gap sits well below the baseline, with a 95% range that doesn't reach it, would be a blind spot. Opponent styles use only their UFC fights before that night."))),
      rvResidRows(A.by_opp_style, { head: "Pick's opponent", map: SL, order: ["wrestler", "submission", "power", "volume", "balanced", "unknown"] }, A.overall),
      h("h3.h-sub.stats-sub", "By the model's own projected grappling gap"),
      rvResidRows(A.by_proj_control_gap, { head: "Projected control", map: { opp_much_more: "Opponent controls much more", opp_more: "Opponent controls more", even: "About even", pick_more: "Pick controls more", pick_much_more: "Pick controls much more" },
        order: ["opp_much_more", "opp_more", "even", "pick_more", "pick_much_more"] }, A.overall),
      (() => { const t = D.tdt || {}; if (!t.model) return null;
        return h("div.note", icon("info"), h("p", h("b", "The takedown test: "),
          `for ${NF.format(t.model.n)} graded bouts, each fighter's takedowns conceded and landed going in (only earlier UFC fights) were combined into a mismatch score. It added nothing to the model's price (z = ${t.model.z}) or the market's (z = ${t.market.z}): a fighter who gets taken down a lot, facing a wrestler, won about as often as the prices said. Weak spots describe a fighter; they weren't a betting edge on their own.`)); })(),
      h("p.foot", "“Model gap” is how much more often the pick won than the model's average forecast said, in percentage points (market gap: the same against the market's price for the pick), with its 95% range.")));

    // the list
    listHost = h("section.panel.rv-listp");
    body.append(listHost);
    drawList();
  }
  let listHost = null;
  function drawList() {
    clear(listHost);
    const xs0 = L.graded.filter(inWin).filter(b => reviewOf(b));
    const verdicts = rvUI.res === "hit" ? RV_ORDER_HIT : rvUI.res === "miss" ? RV_ORDER_MISS : ["unlucky", "fortunate", "close", "outclassed", "earned", "quick"];
    if (rvUI.v !== "all" && !verdicts.includes(rvUI.v)) rvUI.v = "all";
    const set = k => v => { rvUI[k] = v; rvUI.n = 40; saveRv(); drawList(); };
    const xs = xs0.filter(b => (rvUI.res === "all" || (rvUI.res === "hit") === b.result.ok)
      && (rvUI.v === "all" || reviewOf(b).v === rvUI.v)
      && (rvUI.ub === "all" || reviewOf(b).ub === rvUI.ub)
      && (!rvUI.q || fold(b.A.name + " " + b.B.name + " " + b.ev.name).includes(fold(rvUI.q)))).slice().reverse();
    listHost.append(h("div.panel-head", h("div", h("h3.h-sec", "Every reviewed fight"), h("p.cap", "Open one for its round-by-round review."))),
      h("div.toolbar",
        h("div.field", h("label", { htmlFor: "rv-res" }, "Picks"), selectEl("rv-res", [["miss", "Misses"], ["hit", "Right picks"], ["all", "All"]], rvUI.res, set("res"), "Picks")),
        h("div.field", h("label", { htmlFor: "rv-v" }, "Verdict"), selectEl("rv-v", [["all", "Any verdict"], ...verdicts.map(v => [v, RV_VERDICT[v].label])], rvUI.v, set("v"), "Verdict")),
        h("div.field", h("label", { htmlFor: "rv-ub" }, "Market"), selectEl("rv-ub", [["all", "Any"], ["both", "Both had the loser"], ["model", "Only the model wrong"], ["market", "Model beat the market"], ["neither", "Both right"], ["model_unpriced", "No price"]], rvUI.ub, set("ub"), "Market")),
        h("div.field.toolbar-grow", h("label", { htmlFor: "rv-q" }, "Fighter or event"), searchField("rv-q", "e.g. Osbourne", rvUI.q, set("q"), "Fighter or event"))),
      h("p.ev-count", { "aria-live": "polite" }, `${plural(xs.length, "fight")}`));
    if (!xs.length) { listHost.append(emptyState("No fights match", "Loosen a filter.")); return; }
    const ul = h("ul.rv-list");
    xs.slice(0, rvUI.n).forEach(b => {
      const rv = reviewOf(b), V = RV_VERDICT[rv.v];
      const [ub1] = underratedText(b, rv);
      ul.append(h("li.rv-item",
        h("a.rv-item-a", { href: "#" + b.route, onclick: () => { muTab = "review"; } },
          h("div.rv-item-h",
            h("span.mark." + (b.result.ok ? "ok" : "no"), icon(b.result.ok ? "check" : "cross")),
            h("b", `${lastName(sideName(b, b.pickSide))} `, h("span.faint", "vs "), lastName(sideName(b, b.oppSide))),
            h("span.pill.rv-pill" + (V.tone ? "." + V.tone : ""), icon(V.icon), V.label),
            h("span.sp"), h("small.faint.num", fmtDay(b.date))),
          h("small.faint", `${b.result.winnerName} by ${rvHow(rv.how) || b.result.how}${b.result.how !== "Decision" ? `, R${rv.rd} ${rv.tm}` : ""} · model ${pct(b.p)} ${lastName(sideName(b, b.pickSide))}`),
          rv.st && rv.st.s ? h("p.small", rv.st.s) : (ub1 ? h("p.small.muted", ub1) : null))));
    });
    listHost.append(ul);
    if (xs.length > rvUI.n) listHost.append(h("div.center-row", h("button.btn", { type: "button", onclick: () => { rvUI.n += 40; drawList(); } }, `Show more (${NF.format(xs.length - rvUI.n)} left)`)));
  }
  draw();
};
