.PHONY: install check lint test frontend-check serve dev

install:
	uv sync
	cd frontend && npm ci

lint:
	uv run ruff check .
	uv run ruff format --check .

test:
	uv run pytest

frontend-check:
	cd frontend && npm run typecheck && npm run build

check: lint test frontend-check

serve:
	uv run footy serve

dev:
	cd frontend && npm run dev
