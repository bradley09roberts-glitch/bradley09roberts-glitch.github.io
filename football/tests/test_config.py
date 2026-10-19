import re

from footy.config import PROJECT_ROOT, Settings


def test_secrets_never_rendered(settings):
    s = Settings(
        _env_file=None,
        FOOTY_LIVE_PROVIDER="sportmonks",
        SPORTMONKS_API_TOKEN="sm-secret-123",
        API_FOOTBALL_KEY="af-secret-456",
    )
    rendered = repr(s) + str(s) + s.model_dump_json()
    assert "sm-secret-123" not in rendered
    assert "af-secret-456" not in rendered
    assert s.live_provider_configured()


def test_provider_needs_matching_key(settings):
    s = Settings(_env_file=None, FOOTY_LIVE_PROVIDER="api_football", SPORTMONKS_API_TOKEN="x")
    assert not s.live_provider_configured()


def test_env_example_lists_every_setting():
    example = (PROJECT_ROOT / ".env.example").read_text()
    declared = set(re.findall(r"^#?\s*([A-Z][A-Z0-9_]+)=", example, flags=re.MULTILINE))
    aliases = {f.validation_alias for f in Settings.model_fields.values()}
    assert aliases == declared
