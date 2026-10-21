"""Live early-edition predictions for upcoming fixtures (M7).

Domestic: the selected model is fitted per league (league + second tier) on every result
final before today (UK), then predicts this week's fixtures from the openfootball schedules
(team mapping verified against played results). European: the pan-European model is fitted
on all domestic and UEFA results before today and predicts scheduled UEFA matches.
Early edition = no lineup or injury information. Lineup editions are not produced (no
lineup source on the current provider plan), so they are recorded as unavailable.
"""

from datetime import UTC, date, datetime, timedelta
from zoneinfo import ZoneInfo

import pandas as pd
from sqlalchemy import Engine, text

from footy.fixtures import upcoming
from footy.modeling.data import SECOND_TIER, load_matches
from footy.modeling.grid import MarketProbs
from footy.modeling.ledger import data_revision, git_commit
from footy.publish import FixtureIn, publish

UK = ZoneInfo("Europe/London")
LEAGUE_OF = {**{k: k for k in SECOND_TIER}, **{v: k for k, v in SECOND_TIER.items()}}


def model_version(engine: Engine, extra: str = "") -> str:
    return f"code:{(git_commit() or 'unknown')[:10]}|data:{data_revision(engine)}{extra}"


def predict_domestic(
    engine: Engine,
    factory,
    model_name: str,
    divisions: list[str],
    *,
    now: datetime | None = None,
    days: int = 7,
    dry_run: bool = False,
    notes: str | None = None,
    only_with_market: bool = False,
) -> list[dict]:
    """`only_with_market`: publish only the fixtures for which a model + market candidate
    found usable prices, so a later run with fresher prices can still publish the rest.
    A model + market candidate (one with a `decision_time`) always publishes that way: a
    record saved under its name without prices would be the base model's forecast, and since
    a fixture's record is never replaced it would block the real one when the prices arrive
    (the base model publishes its own records under its own name). A dry run still lists
    every fixture, with `market_used`."""
    now = now or datetime.now(UTC)
    today = now.astimezone(UK).date()
    out = []
    for div in divisions:
        top = LEAGUE_OF[div]
        df = load_matches(engine, [top, SECOND_TIER[top]])
        hist = df[(df["date"] < pd.Timestamp(today)) & df.model_exclusion.isna()]
        fx_list, check = upcoming(engine, div, "2627", today, days=days)
        fx_list = [x for x in fx_list if x[0].kickoff_utc is None or x[0].kickoff_utc > now]
        if not fx_list:
            continue
        model = factory()
        market_model = hasattr(model, "decision_time")
        if market_model:  # model + market: prices quoted up to now
            model.decision_time = now
        cutoff = pd.Timestamp(datetime(today.year, today.month, today.day, tzinfo=UK)).tz_convert(
            "UTC"
        )
        model.fit(hist, cutoff)
        frame = pd.DataFrame(
            {
                "division": div,
                "season": "2627",
                "date": [pd.Timestamp(x.match_date) for x, _, _ in fx_list],
                "home_team": [h for _, h, _ in fx_list],
                "away_team": [a for _, _, a in fx_list],
                "kickoff_utc": [x.kickoff_utc for x, _, _ in fx_list],
                "model_exclusion": None,
            }
        )
        mp = model.predict(frame)
        counts = pd.concat([hist.home_team, hist.away_team]).value_counts()
        fresh = {d: str(g["date"].max().date()) for d, g in hist.groupby("division")}
        used = getattr(model, "last_used", None)
        prov = model.provenance() if hasattr(model, "provenance") else None
        fixtures = []
        for j, (x, h, a) in enumerate(fx_list):
            market, missing = None, ["lineups", "injuries", "odds"]
            if used is not None and (used.used_1x2[j] or used.used_ou[j]):
                r = used.iloc[j]
                market = {
                    "p_H": None if pd.isna(r.p_H) else float(r.p_H),
                    "p_D": None if pd.isna(r.p_D) else float(r.p_D),
                    "p_A": None if pd.isna(r.p_A) else float(r.p_A),
                    "p_over_2.5": None if pd.isna(r.p_O) else float(r.p_O),
                    "books": list(model.params()["books"]),
                    "quote_time_utc": str(r.quote_time_utc),
                    "provenance": prov,
                }
                missing = ["lineups", "injuries"]
            fixtures.append(
                FixtureIn(
                    competition=div,
                    season="2627",
                    match_date=x.match_date,
                    kickoff_utc=x.kickoff_utc,
                    home_team=h,
                    away_team=a,
                    features={
                        "home_matches_in_window": int(counts.get(h, 0)),
                        "away_matches_in_window": int(counts.get(a, 0)),
                        "fixture_source": f"openfootball schedule line {x.line_no}",
                        "team_mapping_verified_on": check["checked_results"],
                        "model_params": model.params(),
                    }
                    | ({"market_prices_used": market} if market else {}),
                    missing_fields=missing,
                )
            )
        if only_with_market or (market_model and not dry_run):
            keep = [j for j, f in enumerate(fixtures) if "market_prices_used" in f.features]
            fixtures = [fixtures[j] for j in keep]
            mp = _subset(mp, keep)
            if not fixtures:
                continue
        if dry_run:
            out.extend(
                {
                    "fixture": f.ref,
                    "p1x2": mp.p1x2[j].round(3).tolist(),
                    "market_used": "market_prices_used" in f.features,
                }
                for j, f in enumerate(fixtures)
            )
            continue
        ids = publish(
            engine,
            fixtures,
            mp,
            model_name=model_name,
            model_version=model_version(engine),
            training_cutoff_utc=cutoff.to_pydatetime(),
            data_freshness={"latest_result_by_division": fresh},
            notes=notes,
            now=now,
        )
        out.extend({"id": i, "fixture": f.ref} for i, f in zip(ids, fixtures, strict=True))
    return out


def _subset(mp: MarketProbs, keep: list[int]) -> MarketProbs:
    def take(v):
        return None if v is None else v[keep]

    return MarketProbs(
        p1x2=take(mp.p1x2),
        p_over={k: v[keep] for k, v in mp.p_over.items()},
        p_btts=take(mp.p_btts),
        grid=take(mp.grid),
        lam_h=take(mp.lam_h),
        lam_a=take(mp.lam_a),
    )


def predict_euro(
    engine: Engine,
    factory,
    model_name: str,
    comps: tuple[str, ...] = ("UCL", "UEL", "UECL"),
    *,
    now: datetime | None = None,
    days: int = 3,
    dry_run: bool = False,
    notes: str | None = None,
) -> list[dict]:
    from footy.modeling.euro import load_combined
    from footy.modeling.euro_eval import team_countries

    now = now or datetime.now(UTC)
    today = now.astimezone(UK).date()
    df = load_combined(engine, include_scheduled=True)
    hist = df[(df.status == "played") & (df.date < pd.Timestamp(today))]
    fx = df[
        (df.kind == "euro")
        & (df.status == "scheduled")
        & df.comp.isin(comps)
        & (df.date >= pd.Timestamp(today))
        & (df.date <= pd.Timestamp(today + timedelta(days=days)))
    ]
    fx = fx[fx.kickoff_utc.isna() | (fx.kickoff_utc > now)]
    if fx.empty:
        return []
    model = factory()
    model.team_country = team_countries(engine)
    model.fit(hist, pd.Timestamp(today))
    mp = model.predict(fx)
    counts = pd.concat([hist.home_team, hist.away_team]).value_counts()
    with engine.connect() as conn:
        latest_euro = conn.execute(
            text("SELECT max(match_date) FROM euro_matches WHERE status='played'")
        ).scalar()
        latest_dom = conn.execute(text("SELECT max(match_date) FROM matches")).scalar()
    fixtures = []
    for r in fx.itertuples():
        hs, as_ = model.strength(r.home_team), model.strength(r.away_team)
        fixtures.append(
            FixtureIn(
                competition=r.comp,
                season=r.season,
                match_date=r.date.date(),
                kickoff_utc=None if pd.isna(r.kickoff_utc) else r.kickoff_utc.to_pydatetime(),
                home_team=r.home_team,
                away_team=r.away_team,
                features={
                    "home_matches_in_window": int(counts.get(r.home_team, 0)),
                    "away_matches_in_window": int(counts.get(r.away_team, 0)),
                    "home_league_for_rating": model.league_of.get(r.home_team),
                    "away_league_for_rating": model.league_of.get(r.away_team),
                    "home_attack_defence": hs,
                    "away_attack_defence": as_,
                    "stage": getattr(r, "stage", None),
                    "neutral": int(r.neutral),
                    "model_params": model.params(),
                },
                missing_fields=["lineups", "injuries", "odds"],
            )
        )
    if dry_run:
        return [
            {
                "fixture": f.ref,
                "p1x2": mp.p1x2[j].round(3).tolist(),
                "xg": [round(float(mp.lam_h[j]), 2), round(float(mp.lam_a[j]), 2)],
            }
            for j, f in enumerate(fixtures)
        ]
    ids = publish(
        engine,
        fixtures,
        mp,
        model_name=model_name,
        model_version=model_version(engine),
        training_cutoff_utc=datetime(today.year, today.month, today.day, tzinfo=UK).astimezone(UTC),
        data_freshness={
            "latest_uefa_result": str(latest_euro),
            "latest_domestic_result": str(latest_dom),
        },
        notes=notes,
        now=now,
    )
    return [{"id": i, "fixture": f.ref} for i, f in zip(ids, fixtures, strict=True)]


def today_uk() -> date:
    return datetime.now(UK).date()
