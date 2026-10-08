import { deps } from "../packages/engine/test/helpers.js";
import { CURATED_PROFILES } from "../packages/engine/src/index.js";
const d = deps();
for (const p of CURATED_PROFILES) {
  const hero = d.data.hero(p.heroId)!;
  for (const a of p.archetypes) for (const s of a.abilityPlan.upgrades.filter((x) => x.why.includes("'"))) {
    const q = s.why.match(/'([^']+)'/)![1]!;
    const t = hero.abilities.find((x) => x.className === s.ability)!.tiers[s.tier - 1]!.text.replace(/\s+/g, " ");
    if (!t.includes(q.split(",")[0]!.trim())) console.log(`${hero.name} ${s.ability} T${s.tier}\n  quote: ${q}\n  data:  ${t}`);
  }
}
