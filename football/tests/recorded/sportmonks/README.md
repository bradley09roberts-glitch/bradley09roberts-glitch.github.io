# Recorded Sportmonks responses

Real API responses go here, copied unedited from the raw store
(`data/raw/sportmonks/...`) once the owner's key exists (DECISIONS.md D-010).
`tests/test_sportmonks_contract.py` parses every `*.json` file in this folder and
checks the adapter's assumptions against it. While the folder holds no responses,
that test is skipped, so the adapter remains unverified against live data.

Name files after the endpoint, e.g. `fixtures_between.json`, `odds_market_1.json`,
`odds_market_80.json`, `odds_market_14.json`. Remove nothing from a response, and
never commit one that contains the API token.
