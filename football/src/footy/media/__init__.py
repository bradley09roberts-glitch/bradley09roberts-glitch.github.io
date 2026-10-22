"""Team pictures from freely licensed sources (docs/research/team-media.md).

- `wdqs`: polite, cached Wikidata Query Service client (the only Wikidata endpoint used).
- `mapping`: team_key to Wikidata item, written to reference/team_wikidata.csv.
- `facts`: crest file, home venue and its photo, official colours, to reference/team_media.json.
- `commons`: the resumable Wikimedia Commons downloader (free licences only).
- `badge`: our own generated SVG badge, shown when no free crest is stored.
- `service`: what GET /api/teams/{team_key}/media returns.
"""
