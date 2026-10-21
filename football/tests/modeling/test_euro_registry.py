"""Named UEFA models: the pinned configurations and the CLI wiring (no data needed)."""

import pytest

from footy.cli import main
from footy.modeling.euro import EuroFrequency, EuroMixture, PanEuropeanDC, PanEuropeanElo
from footy.modeling.euro_registry import EURO_MODELS, euro_config, euro_factory


def test_pinned_model_of_record_config():
    """pan_euro_mix_mxg is the D-026 configuration, with its market-xG scope pinned to the
    targets it was selected and tested on."""
    assert EURO_MODELS["pan_euro_mix_mxg"] == {
        "family": "mix",
        "dc": {
            "reg_team": 10.0,
            "euro_weight": 1.0,
            "market_alpha": 0.25,
            "market_scope": "top5_exact",
        },
        "elo": {"k_euro": 45.0},
        "w_dc": 0.7,
    }
    assert EURO_MODELS["pan_euro_mix"] == {
        "family": "mix",
        "dc": {"reg_team": 6.0, "euro_weight": 1.0},
        "elo": {"k_euro": 45.0},
        "w_dc": 0.7,
    }


@pytest.mark.parametrize("name", list(EURO_MODELS))
def test_every_registered_model_builds_under_its_name(name):
    m = euro_factory(name)()
    assert m.name == name
    if EURO_MODELS[name] is None:
        assert isinstance(m, EuroFrequency)
        return
    assert isinstance(m, EuroMixture)
    dc, elo = m.members
    assert isinstance(dc, PanEuropeanDC) and isinstance(elo, PanEuropeanElo)
    cfg = EURO_MODELS[name]
    assert m.weights == [cfg["w_dc"], 1 - cfg["w_dc"]]
    assert dc.market_alpha == cfg["dc"].get("market_alpha")
    assert dc.market_scope == cfg["dc"].get("market_scope", "all")
    if dc.market_alpha is not None:
        assert dc.market_scope != "all", "a registered market-trained model names its scope"


def test_euro_config_is_a_copy():
    cfg = euro_config("pan_euro_mix_mxg")
    cfg["dc"]["market_scope"] = "all"
    assert EURO_MODELS["pan_euro_mix_mxg"]["dc"]["market_scope"] == "top5_exact"
    with pytest.raises(KeyError):
        euro_config("no_such_model")


@pytest.mark.parametrize(
    "argv",
    [
        ["predict-euro", "--model", "no_such_model", "--dry-run"],
        ["predict-euro", "--model", "pan_euro_mix_mxg", "--model-name", "other", "--dry-run"],
        ["predict-euro", "--model", "pan_euro_mix_mxg", "--config", "{}", "--dry-run"],
        # The frequency baseline has no team ratings for a record's features.
        ["predict-euro", "--model", "euro_frequency", "--dry-run"],
        # A raw config can't publish under a registered name (the unpinned D-026 JSON would
        # train on today's all-league market xG).
        [
            "predict-euro",
            "--config",
            '{"family": "mix", "dc": {"reg_team": 10.0, "euro_weight": 1.0, "market_alpha": 0.25},'
            ' "elo": {"k_euro": 45.0}, "w_dc": 0.7}',
            "--model-name",
            "pan_euro_mix_mxg",
            "--dry-run",
        ],
    ],
)
def test_predict_euro_refuses_bad_model_arguments(argv, settings):
    with pytest.raises(SystemExit) as exc:
        main(argv)
    assert exc.value.code == 2
