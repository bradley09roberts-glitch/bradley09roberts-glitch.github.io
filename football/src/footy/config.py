"""Application settings, read from environment variables (and an optional .env file).

Provider credentials are held as SecretStr so they never appear in reprs, logs or API
responses. Nothing here is ever sent to the frontend.
"""

from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

PROJECT_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=PROJECT_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    database_url: str = Field(
        default=f"sqlite:///{PROJECT_ROOT / 'data' / 'footy.sqlite3'}",
        validation_alias="FOOTY_DATABASE_URL",
    )
    data_dir: Path = Field(default=PROJECT_ROOT / "data", validation_alias="FOOTY_DATA_DIR")
    display_timezone: str = Field(default="Europe/London", validation_alias="FOOTY_DISPLAY_TZ")
    log_level: str = Field(default="INFO", validation_alias="FOOTY_LOG_LEVEL")
    fd_download: bool = Field(default=True, validation_alias="FOOTY_FD_DOWNLOAD")

    live_provider: Literal["none", "sportmonks", "api_football"] = Field(
        default="none", validation_alias="FOOTY_LIVE_PROVIDER"
    )
    sportmonks_api_token: SecretStr | None = Field(
        default=None, validation_alias="SPORTMONKS_API_TOKEN"
    )
    api_football_key: SecretStr | None = Field(default=None, validation_alias="API_FOOTBALL_KEY")
    # Leagues the provider key covers, as division:provider_league_id pairs. The default is the
    # five top divisions; the Sportmonks free plan covers only SC0:501 and DK1:271.
    sportmonks_leagues: str = Field(
        default="E0:8,SP1:564,I1:384,D1:82,F1:301", validation_alias="FOOTY_SPORTMONKS_LEAGUES"
    )

    def sportmonks_league_ids(self) -> dict[str, int]:
        pairs = (p.split(":") for p in self.sportmonks_leagues.split(",") if p.strip())
        return {code.strip(): int(i) for code, i in pairs}

    @property
    def raw_dir(self) -> Path:
        """Append-only store for raw downloaded files and API responses."""
        return self.data_dir / "raw"

    @property
    def fd_inbox_dir(self) -> Path:
        """Hand-downloaded football-data CSVs, named <season>_<division>.csv (e.g. 2526_E0.csv)."""
        return self.data_dir / "inbox" / "football-data"

    def live_provider_configured(self) -> bool:
        if self.live_provider == "sportmonks":
            return self.sportmonks_api_token is not None
        if self.live_provider == "api_football":
            return self.api_football_key is not None
        return False


@lru_cache
def get_settings() -> Settings:
    return Settings()
