"""Command-line entry point: `uv run footy <command>`."""

import argparse

from footy.config import get_settings


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="footy")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("migrate", help="apply database migrations up to head")
    serve = sub.add_parser("serve", help="run the API server")
    serve.add_argument("--host", default="127.0.0.1")
    serve.add_argument("--port", type=int, default=8000)
    serve.add_argument("--reload", action="store_true")
    args = parser.parse_args(argv)

    if args.command == "migrate":
        from footy.db import upgrade_to_head

        upgrade_to_head()
        print(f"Migrated {get_settings().database_url}")
    elif args.command == "serve":
        import uvicorn

        from footy.db import upgrade_to_head

        upgrade_to_head()
        uvicorn.run("footy.api.main:app", host=args.host, port=args.port, reload=args.reload)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
