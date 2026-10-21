"""Named UEFA (pan-European) model configurations, so a model is published and evaluated by
name rather than by raw JSON (`footy predict-euro --model <name>`).

A name means one fixed configuration. Once a name has published records, its entry must not
change: a model trained differently gets a new name. That is why `pan_euro_mix_mxg` pins
its market-xG scope to "top5_exact", the targets it was selected and tested on (D-026).

Configurations are the dicts `footy.modeling.euro_eval.make_factory` takes (None is the
frequency baseline); each carries its registry name, so backtest ledgers are saved under it.
"""

from copy import deepcopy

_ELO = {"k_euro": 45.0}

EURO_MODELS: dict[str, dict | None] = {
    # Baseline: home/draw/away, goal-line and BTTS rates over UEFA matches in the window.
    "euro_frequency": None,
    # Goals only: the UEFA model of record before 21 Oct 2026 (records of 21-22 Oct 2026).
    "pan_euro_mix": {
        "family": "mix",
        "dc": {"reg_team": 6.0, "euro_weight": 1.0},
        "elo": _ELO,
        "w_dc": 0.7,
    },
    # UEFA model of record from 21 Oct 2026 (D-026): Dixon-Coles targets 0.25 x goals +
    # 0.75 x market xG from the five top divisions' closing prices (exact 1X2 + O/U 2.5
    # solve, Pinnacle, Bet365 and the average on every date), as selected and tested.
    "pan_euro_mix_mxg": {
        "family": "mix",
        "dc": {
            "reg_team": 10.0,
            "euro_weight": 1.0,
            "market_alpha": 0.25,
            "market_scope": "top5_exact",
        },
        "elo": _ELO,
        "w_dc": 0.7,
    },
    # Candidates (round 3): market xG from every division with closing prices, Pinnacle left
    # out from 23 Jul 2025 (D-014).
    "pan_euro_mix_mxg_all_ma0": {
        "family": "mix",
        "dc": {
            "reg_team": 10.0,
            "euro_weight": 1.0,
            "market_alpha": 0.0,
            "market_scope": "all_d014",
        },
        "elo": _ELO,
        "w_dc": 0.7,
    },
    "pan_euro_mix_mxg_all_ma0.25": {
        "family": "mix",
        "dc": {
            "reg_team": 10.0,
            "euro_weight": 1.0,
            "market_alpha": 0.25,
            "market_scope": "all_d014",
        },
        "elo": _ELO,
        "w_dc": 0.7,
    },
    # Neighbour: more weight on the market-trained Dixon-Coles part.
    "pan_euro_mix_mxg_all_ma0_w0.8": {
        "family": "mix",
        "dc": {
            "reg_team": 10.0,
            "euro_weight": 1.0,
            "market_alpha": 0.0,
            "market_scope": "all_d014",
        },
        "elo": _ELO,
        "w_dc": 0.8,
    },
}


def euro_config(name: str) -> dict | None:
    """A copy of the registered configuration, with `name` set (None: frequency baseline)."""
    if name not in EURO_MODELS:
        raise KeyError(f"unknown UEFA model {name!r}; registered: {', '.join(EURO_MODELS)}")
    cfg = deepcopy(EURO_MODELS[name])
    if cfg is not None:
        cfg["name"] = name
    return cfg


def euro_factory(name: str):
    """A factory for the registered model; the model it makes is called `name`."""
    from footy.modeling.euro_eval import make_factory

    factory = make_factory(euro_config(name))

    def make():
        m = factory()
        m.name = name
        return m

    return make
