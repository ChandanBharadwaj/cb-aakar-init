# Aakar developer shortcuts. See docs/runbook-local.md.
SHELL := /bin/bash
COMPOSE := docker compose -f infra/docker-compose.yml

.PHONY: help infra infra-down api geometry inspect web admin slice test test-api test-python test-web contracts descriptors smoke-avatars smoke-katha smoke-catalogue shoot-catalogue

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

infra: ## Start Postgres, RabbitMQ, MinIO in Docker
	$(COMPOSE) up -d postgres rabbitmq minio minio-init

infra-down: ## Stop the Docker infra
	$(COMPOSE) down

api: ## Run the Spring Boot API (direct profile, port 8080)
	cd services/api && ./gradlew bootRun

geometry: ## Run the geometry service (port 8081, local asset storage; set AAKAR_SLICER_BIN=prusa-slicer for real slicing)
	cd services/geometry && uv run aakar-geometry serve

inspect: ## Run the inspect service (port 8082)
	cd services/inspect && uv run uvicorn aakar_inspect.api:app --port 8082

web: ## Run the storefront (port 3000)
	pnpm --filter @aakar/web dev

admin: ## Run the management portal (port 3100)
	pnpm --filter @aakar/admin dev

slice: ## Build the Jharokha phone stand from the example spec into out/slice
	cd services/geometry && uv run aakar-geometry build ../../packages/contracts/examples/jharokha-phone-stand.spec.json --out ../../out/slice

smoke-avatars: ## Live Avatar order smoke (needs `make api` and `make geometry` running)
	python3 scripts/smoke-avatar-order.py

smoke-katha: ## Live Katha guardrail and keepsakes smoke (needs `make api` and `make geometry` running)
	python3 scripts/smoke-katha-keepsakes.py

smoke-catalogue: ## Live walk of the second catalogue wave to G-code (needs `make api` and `make geometry`; set AAKAR_SLICER_BIN)
	python3 scripts/smoke-catalogue.py

shoot-catalogue: ## Build every Shop item and attach its rendered thumbnail and GLB as the item's media (needs `make api` and `make geometry`)
	python3 scripts/shoot-catalogue.py

descriptors: ## Export live template descriptors to packages/contracts/examples (mocks and API fixtures read them)
	cd services/geometry && uv run aakar-geometry templates > ../../packages/contracts/examples/template-descriptors.json

contracts: ## Validate schemas, examples and OpenAPI documents
	pnpm --filter @aakar/contracts validate

test: test-python test-api test-web ## Run every test suite

test-python: ## Python services
	cd services/inspect && uv sync -q && uv run pytest -q
	cd services/geometry && uv sync -q && uv run pytest -q

test-api: ## Spring Boot API (needs Postgres aakar_test)
	cd services/api && ./gradlew test

test-web: ## Storefront and portal typecheck, lint, build
	pnpm --filter @aakar/web typecheck && pnpm --filter @aakar/web lint && pnpm --filter @aakar/web build
	pnpm --filter @aakar/admin typecheck && pnpm --filter @aakar/admin lint && pnpm --filter @aakar/admin build
