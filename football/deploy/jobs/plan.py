"""Which models the scheduled jobs publish, read from the reference files, so a change made
with `footy set-model-of-record` or to shadow_models.json reaches the jobs without editing
them. Prints shell assignments for deploy/jobs/lib.sh (load_plan):

  DOMESTIC_MARKET     the model + market model of record (publishes only fixtures with prices)
  DOMESTIC_FALLBACK   the first pure model of record (every fixture, as the fallback)
  UEFA_MODELS         the UEFA models of record
  SHADOWS_PURE        shadow models without prices (both groups), for the daily run
  SHADOWS_PRICED      domestic shadow models with prices
  SHADOWS_DOMESTIC    every domestic shadow model, for the Tuesday and Friday runs (the priced
                      ones publish once the prices are in; the others only what is new)

A model + market model is one with a `decision_time` (footy.live.predict_domestic does the
same test). Overrides: FOOTY_FALLBACK_MODEL, FOOTY_SHADOWS_PURE, FOOTY_SHADOWS_PRICED,
FOOTY_SHADOWS_DOMESTIC (comma-separated model names).
"""

import os
import re
from datetime import UTC, datetime

from footy import models_of_record, shadows
from footy.modeling.registry import candidates


def main() -> None:
    now = datetime.now(UTC)
    periods = models_of_record.load(shadows.PRIMARY_MODELS_PATH)
    domestic = models_of_record.models_at(periods.get("domestic", []), now)
    uefa = models_of_record.models_at(periods.get("uefa", []), now)
    reg = candidates()

    def priced(name: str) -> bool:
        factory = reg.get(name)
        return factory is not None and hasattr(factory(), "decision_time")

    market = [m for m in domestic if priced(m)]
    pure = [m for m in domestic if not priced(m)]
    listed = shadows.load()
    dom_shadows = [s.model for s in listed.get("domestic", [])]
    uefa_shadows = [s.model for s in listed.get("uefa", [])]
    plan = {
        "DOMESTIC_MARKET": market[0] if market else "",
        "DOMESTIC_FALLBACK": os.environ.get("FOOTY_FALLBACK_MODEL") or (pure[0] if pure else ""),
        "UEFA_MODELS": ",".join(uefa),
        "SHADOWS_PURE": os.environ.get("FOOTY_SHADOWS_PURE")
        or ",".join([m for m in dom_shadows if not priced(m)] + uefa_shadows),
        "SHADOWS_PRICED": os.environ.get("FOOTY_SHADOWS_PRICED")
        or ",".join(m for m in dom_shadows if priced(m)),
        "SHADOWS_DOMESTIC": os.environ.get("FOOTY_SHADOWS_DOMESTIC") or ",".join(dom_shadows),
    }
    for key, value in plan.items():
        if not re.fullmatch(r"[A-Za-z0-9_.,-]*", value):
            raise SystemExit(f"plan.py: unexpected model name in {key}: {value!r}")
        print(f"{key}={value}")


if __name__ == "__main__":
    main()
