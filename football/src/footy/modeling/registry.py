"""Named model candidates. Lab modules in footy/modeling/lab/ add their own by defining
`CANDIDATES: dict[str, Callable[[], Model]]`; they are discovered automatically."""

import importlib
import pkgutil
from collections.abc import Callable

from footy.modeling.base import Model
from footy.modeling.baselines import EloBaseline, FrequencyBaseline
from footy.modeling.dixon_coles import DixonColes

CORE: dict[str, Callable[[], Model]] = {
    "baseline_frequency": FrequencyBaseline,
    "baseline_elo": EloBaseline,
    "baseline_poisson": lambda: DixonColes(xi=0.0, rho=False, name="baseline_poisson"),
    "dixon_coles": lambda: DixonColes(name="dixon_coles"),
}


def candidates() -> dict[str, Callable[[], Model]]:
    out = dict(CORE)
    try:
        import footy.modeling.lab as lab
    except ImportError:
        return out
    for mod in pkgutil.iter_modules(lab.__path__):
        m = importlib.import_module(f"footy.modeling.lab.{mod.name}")
        for name, factory in getattr(m, "CANDIDATES", {}).items():
            if name in out:
                raise ValueError(f"duplicate model name {name!r} in lab module {mod.name}")
            out[name] = factory
    return out
