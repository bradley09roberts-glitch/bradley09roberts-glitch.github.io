.PHONY: install check lint test frontend-check serve dev snapshot

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

# A static, read-only copy of the site: make snapshot OUT=<folder> (see README).
snapshot:
	$(if $(OUT),,$(error set OUT to the output folder, e.g. make snapshot OUT=../site))
	cd frontend && npm run build:snapshot
	uv run footy export-snapshot --out $(OUT) $(if $(PAGE),--single-page $(PAGE),)
