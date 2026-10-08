import { useState } from "react";
import type { PurchaseAction } from "@countercoach/engine";
import type { Coach } from "../useCoach";
import { EVIDENCE_LABEL, INFO_LABEL, THREAT_SHORT, abilityLine, fmtSouls } from "../format";
import { Badge, Icon, Section } from "./common";

function ActionCard({ c, label, a, tone }: { c: Coach; label: string; a: PurchaseAction; tone: "buy" | "save" | "alt" }) {
  const [open, setOpen] = useState(tone !== "alt");
  const [reasonFor, setReasonFor] = useState<"rejected" | "deferred" | null>(null);
  const [reason, setReason] = useState("");
  const item = c.deps.data.item(a.className);
  const pinned = c.decisions.pinned.some((p) => p.className === a.className);
  const submit = () => {
    if (!reasonFor) return;
    c.decide(reasonFor, a.className, reason || (reasonFor === "rejected" ? "not useful this game" : "later"), reasonFor === "deferred" ? (c.gameTime ?? 0) + 300 : null);
    setReasonFor(null);
    setReason("");
  };
  return (
    <article className={`card action action-${tone}`} aria-label={`${label}: ${a.name}`}>
      <div className="action-head">
        <span className="action-label">{label}</span>
        {a.pinned && <Badge tone="accent">pinned</Badge>}
      </div>
      <div className="action-main">
        <Icon src={a.image} name={a.name} size={48} tone={item?.slot ?? "neutral"} />
        <div className="action-text">
          <div className="action-name">{a.name}</div>
          <div className="action-cost">
            {a.remainingCost !== a.cost ? (
              <>
                <b>{fmtSouls(a.remainingCost)}</b> <s className="muted">{fmtSouls(a.cost)}</s>
              </>
            ) : (
              <b>{fmtSouls(a.cost)}</b>
            )}{" "}
            souls
            {tone !== "alt" && a.soulsShort > 0 && <span className="short"> · {fmtSouls(a.soulsShort)} more needed</span>}
          </div>
        </div>
      </div>
      {a.condition && <div className="condition">{a.condition}</div>}
      {a.requiresSell && (
        <div className="warn-line">
          Inventory full: sell {a.requiresSell.name} (≈{a.requiresSell.sellValue} souls; resale rule unverified)
        </div>
      )}
      <button type="button" className="linkish small" onClick={() => setOpen(!open)} aria-expanded={open}>
        {open ? "Hide why" : "Why?"}
      </button>
      {open && (
        <ul className="why">
          {a.reasons.map((r, i) => (
            <li key={i}>{r}</li>
          ))}
          {a.answers.slice(0, 2).map((x) => (
            <li key={x.ruleId + x.mechanic} className="muted">
              Counters {THREAT_SHORT[x.threat] ?? x.threat} via {x.mechanic.replace(/_/g, " ")} ({EVIDENCE_LABEL[x.evidence]})
            </li>
          ))}
          {a.flags.map((f, i) => (
            <li key={`f${i}`} className="flag">
              {f}
            </li>
          ))}
        </ul>
      )}
      <div className="action-buttons">
        <button type="button" className="btn ghost small" onClick={() => (pinned ? c.clearDecision(a.className) : c.decide("pinned", a.className, "pinned"))}>
          {pinned ? "Unpin" : "Pin"}
        </button>
        <button type="button" className="btn ghost small" onClick={() => setReasonFor("deferred")}>
          Defer 5 min
        </button>
        <button type="button" className="btn ghost small" onClick={() => setReasonFor("rejected")}>
          Reject
        </button>
      </div>
      {reasonFor && (
        <div className="reason-row">
          <input className="input" autoFocus placeholder={`Reason to ${reasonFor === "rejected" ? "reject" : "defer"} (optional)`} value={reason} onChange={(e) => setReason(e.target.value)} onKeyDown={(e) => e.key === "Enter" && submit()} />
          <button type="button" className="btn small" onClick={submit}>
            OK
          </button>
        </div>
      )}
    </article>
  );
}

export function PurchasePanel({ c }: { c: Coach }) {
  const r = c.output.rec;
  return (
    <Section
      title="Purchase"
      className="purchase"
      right={
        <span className="row gap">
          <Badge tone={r.informationState === "manual" ? "neutral" : r.informationState === "last-observed" ? "warn" : "accent"} title="Information state">
            {INFO_LABEL[r.informationState]}
          </Badge>
          <Badge tone={r.confidence === "high" ? "good" : r.confidence === "medium" ? "warn" : "bad"} title={r.confidenceReasons.join("\n")}>
            {r.confidence} confidence
          </Badge>
        </span>
      }
    >
      {r.decision === "unsupported" || r.decision === "need-info" ? (
        <div className="empty-state">
          <div className="big">{r.summary}</div>
          {r.why.map((w, i) => (
            <p key={i} className="muted">
              {w}
            </p>
          ))}
        </div>
      ) : (
        <>
          <div className="summary-line">
            <span className="big">{r.summary}</span>
            <span className="muted small">
              {r.archetypeLabel} · {r.route} route · {r.phase}
              {r.phaseSource === "clock" ? " (from clock)" : r.phaseSource === "default" ? " (assumed)" : ""}
            </span>
          </div>
          {r.decision === "no-legal-purchase" && <p className="muted">{r.why[0]}</p>}
          <div className="actions">
            {r.buyNow && <ActionCard c={c} label="BUY NOW" a={r.buyNow} tone="buy" />}
            {!r.buyNow && r.decision === "save" && (
              <article className="card action action-save-only">
                <div className="action-label">BUY NOW</div>
                <div className="big">Save</div>
                <p className="muted small">{r.delays ?? "Nothing affordable is worth delaying the target."}</p>
              </article>
            )}
            {r.saveFor && <ActionCard c={c} label="SAVE FOR" a={r.saveFor} tone="save" />}
            {r.alternative && <ActionCard c={c} label="ALTERNATIVE" a={r.alternative} tone="alt" />}
          </div>
          {r.delays && r.buyNow && <p className="muted small">{r.delays}</p>}
          {r.stability.reason && <p className="muted small">↺ {r.stability.reason}</p>}
          <div className="route-note muted small">{r.routeReason}</div>
        </>
      )}
      {r.missingInfo.length > 0 && (
        <div className="missing">
          <div className="small strong">Improve this advice</div>
          <ul>
            {r.missingInfo.slice(0, 4).map((m) => (
              <li key={m.field + m.why}>{m.why}</li>
            ))}
          </ul>
        </div>
      )}
      {r.excluded.length > 0 && (
        <div className="excluded small">
          {r.excluded.map((e) => (
            <span key={e.className} className="excl">
              {e.name}: {e.reason}{" "}
              <button type="button" className="linkish small" onClick={() => c.clearDecision(e.className)}>
                undo
              </button>
            </span>
          ))}
        </div>
      )}
    </Section>
  );
}

export function AbilityPanel({ c }: { c: Coach }) {
  const a = c.output.ability;
  if (!a) return null;
  return (
    <Section title="Next ability action" right={<Badge tone={a.planSource === "curated" ? "accent" : "neutral"} title={a.provenance.interpretation}>{a.planSource} plan</Badge>}>
      <div className={`ability-action kind-${a.kind}`}>
        {a.ability && <Icon src={a.ability.image} name={a.ability.name} size={44} />}
        <div>
          <div className="big">{abilityLine(a)}</div>
          {a.whatChanges && <div className="tier-text">{a.whatChanges}</div>}
          <div className="muted small">{a.reason}</div>
          {a.alternative && <div className="small">Alternative: {a.alternative.note}</div>}
          {a.issues.map((i) => (
            <div key={i} className="flag small">
              {i}
            </div>
          ))}
        </div>
      </div>
      {a.upcoming.length > 0 && (
        <ol className="upcoming small">
          {a.upcoming.map((u) => (
            <li key={`${u.className}${u.tier}`}>
              {u.name} T{u.tier} <span className="muted">({u.cost} pt)</span> {u.breakpoint && <Badge tone="accent">breakpoint</Badge>}
            </li>
          ))}
        </ol>
      )}
    </Section>
  );
}

export function ThreatPanel({ c }: { c: Coach }) {
  const t = c.output.threatPanel;
  return (
    <Section title="Threats" right={<span className="muted small">strength index, not probability</span>}>
      {!t.length && <p className="muted small">No threats yet. Add enemy heroes, visible items or a report.</p>}
      <ul className="threats">
        {t.map((x) => (
          <li key={x.kind} className={x.urgent ? "urgent" : ""}>
            <div className="threat-top">
              <span className="strong">{x.label}</span>
              <Badge tone={x.evidence === "roster-only" ? "neutral" : "warn"}>{EVIDENCE_LABEL[x.evidence]}</Badge>
              <Badge>{x.context}</Badge>
              {x.urgent && <Badge tone="bad">urgent</Badge>}
            </div>
            <div className="meter" aria-label={`strength ${x.strength}`}>
              <span style={{ width: `${Math.round(x.strength * 100)}%` }} />
            </div>
            <div className="muted small">
              {x.topSource}
              {x.answeredBy ? ` · you have ${x.answeredBy}` : ""}
            </div>
          </li>
        ))}
      </ul>
    </Section>
  );
}

export function ExtrasPanel({ c }: { c: Coach }) {
  const o = c.output;
  const spike = o.powerSpike;
  return (
    <>
      <Section title="Power spike">
        <div className="small">
          {spike.item ? (
            <div>
              Item: <b>{spike.item.name}</b> — {spike.item.soulsRemaining > 0 ? `${fmtSouls(spike.item.soulsRemaining)} souls to go` : "affordable now"}
            </div>
          ) : (
            <div className="muted">No item target yet.</div>
          )}
          {spike.ability && (
            <div>
              Ability: <b>{spike.ability.name} T{spike.ability.tier}</b> — {spike.ability.pointsRemaining > 0 ? `${spike.ability.pointsRemaining} more point(s)` : "ready"}
            </div>
          )}
          <div className="muted">{spike.etaNote}</div>
        </div>
      </Section>
      {o.lanePlan.opponents.length > 0 && (
        <Section title="Lane plan">
          {o.lanePlan.opponents.map((op) => (
            <div key={op.heroId} className="small">
              <b>{op.name}</b>
              <ul>
                {op.respect.map((r, i) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            </div>
          ))}
          {o.lanePlan.yourTools.length > 0 && <div className="small">Your tools: {o.lanePlan.yourTools.join(" · ")}</div>}
          <div className="muted small">{o.lanePlan.note}</div>
        </Section>
      )}
      {o.teamUtility.length > 0 && (
        <Section title="Team utility">
          <ul className="small">
            {o.teamUtility.map((t) => (
              <li key={t.tool}>
                <b>{t.label}</b>: {t.explanation}
              </li>
            ))}
          </ul>
        </Section>
      )}
      {o.activeHints.length > 0 && (
        <Section title="Your actives">
          <ul className="small">
            {o.activeHints.map((h) => (
              <li key={h.className}>
                <b>{h.name}</b>: {h.hint}
              </li>
            ))}
          </ul>
        </Section>
      )}
      {o.buildRepair && (o.buildRepair.offRoute.length > 0 || o.buildRepair.dropped.length > 0) && (
        <Section title="Build repair">
          <div className="small">{o.buildRepair.note}</div>
          {o.buildRepair.dropped.length > 0 && (
            <ul className="small">
              {o.buildRepair.dropped.map((d) => (
                <li key={d.className}>
                  Skip {d.name}: {d.reason}
                </li>
              ))}
            </ul>
          )}
          <div className="small muted">Next on route: {o.buildRepair.remaining.slice(0, 4).map((r) => r.name).join(" → ") || "—"}</div>
        </Section>
      )}
      {o.replacement?.weakest && (
        <Section title="Replacement">
          <div className="small">
            Weakest slot: <b>{o.replacement.weakest.name}</b> (≈{o.replacement.weakest.sellValue} souls back). {o.replacement.timing}
          </div>
          <div className="muted small">{o.replacement.ruleNote}</div>
        </Section>
      )}
      <div className="muted tiny">Computed locally in {o.computeMs} ms.</div>
    </>
  );
}
