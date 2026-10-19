"""FastAPI application. Run with: uv run footy serve"""

from datetime import UTC, datetime

from fastapi import FastAPI
from sqlalchemy import Engine

from footy import __version__
from footy.config import Settings, get_settings
from footy.db import check_connection, current_revision, get_engine, head_revision


def create_app(settings: Settings | None = None, engine: Engine | None = None) -> FastAPI:
    settings = settings or get_settings()
    app = FastAPI(title="Football model API", version=__version__)

    def _engine() -> Engine:
        return engine or get_engine()

    @app.get("/api/health")
    def health() -> dict:
        """Liveness plus the facts a reviewer needs before trusting anything else.

        No metrics or predictions are reported here; `data_imported` stays false until
        the M1 importers have actually loaded data.
        """
        db_ok = False
        revision = None
        try:
            db_ok = check_connection(_engine())
            revision = current_revision(_engine())
        except Exception:  # surfaced as database: "error", never hidden
            db_ok = False
        head = head_revision()
        return {
            "status": "ok" if db_ok and revision == head else "degraded",
            "app_version": __version__,
            "server_time_utc": datetime.now(UTC).isoformat(timespec="seconds"),
            "display_timezone": settings.display_timezone,
            "database": "ok" if db_ok else "error",
            "schema_revision": revision,
            "schema_head": head,
            "live_provider": settings.live_provider,
            "live_provider_configured": settings.live_provider_configured(),
            "milestone": "M0",
            "data_imported": False,
        }

    return app


app = create_app()
