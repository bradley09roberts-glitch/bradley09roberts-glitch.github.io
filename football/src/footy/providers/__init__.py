"""Live data providers behind one interface, so nothing else depends on a particular API."""

from footy.config import Settings
from footy.providers.base import LiveProvider


def get_provider(settings: Settings) -> LiveProvider | None:
    """The configured provider, or None when none is chosen or its key is missing."""
    if not settings.live_provider_configured():
        return None
    if settings.live_provider == "sportmonks":
        from footy.providers.sportmonks import SportmonksClient, SportmonksProvider

        token = settings.sportmonks_api_token.get_secret_value()
        return SportmonksProvider(SportmonksClient(token), settings.sportmonks_league_ids())
    raise NotImplementedError(f"provider {settings.live_provider!r} has no adapter yet")
