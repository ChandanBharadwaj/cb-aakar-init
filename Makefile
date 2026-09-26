# Aakar developer shortcuts. See docs/runbook-local.md.
SHELL := /bin/bash
COMPOSE := docker compose -f infra/docker-compose.yml

.PHONY: help infra infra-down api geometry inspect web slice test test-api test-python test-web contracts

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

infra: ## Start Postgres, RabbitMQ, MinIO in Docker
	$(COMPOSE) up -d postgres rabbitmq minio minio-init

infra-down: ## Stop the Docker infra
	$(COMPOSE) down

api: ## Run the Spring Boot API (direct profile, port 8080)
	cd services/api && ./gradlew bootRun

geometry: ## Run the geometry service (port 8081, local asset storage)
	cd services/geometry && uv run aakar-geometry serve

inspect: ## Run the inspect service (port 8082)
	cd services/inspect && uv run uvicorn aakar_inspect.api:app --port 8082

web: ## Run the storefront (port 3000)
	pnpm --filter @aakar/web dev

slice: ## Build the Jharokha phone stand from the example spec into out/slice
	cd services/geometry && uv run aakar-geometry build ../../packages/contracts/examples/jharokha-phone-stand.spec.json --out ../../out/slice

contracts: ## Validate schemas, examples and OpenAPI documents
	pnpm --filter @aakar/contracts validate

test: test-python test-api test-web ## Run every test suite

test-python: ## Python services
	cd services/inspect && uv sync -q && uv run pytest -q
	cd services/geometry && uv sync -q && uv run pytest -q

test-api: ## Spring Boot API (needs Postgres aakar_test)
	cd services/api && ./gradlew test

test-web: ## Storefront typecheck, lint, build
	pnpm --filter @aakar/web typecheck && pnpm --filter @aakar/web lint && pnpm --filter @aakar/web build
