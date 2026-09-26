# Aakar inspect service. Build context is the repo root:
#   docker build -f services/inspect/Dockerfile -t aakar-inspect .
FROM python:3.11-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_LINK_MODE=copy \
    UV_COMPILE_BYTECODE=1 \
    AAKAR_CONTRACTS_DIR=/contracts/schemas

# libgomp for scipy/shapely wheels, libspatialindex for rtree (ray-casting candidates).
RUN apt-get update \
 && apt-get install -y --no-install-recommends libgomp1 libspatialindex-dev curl \
 && rm -rf /var/lib/apt/lists/*

COPY --from=ghcr.io/astral-sh/uv:0.8.17 /uv /uvx /bin/

WORKDIR /app/services/inspect

# Contracts are the law: the service validates every report against them at runtime.
COPY packages/contracts/schemas /contracts/schemas

COPY services/inspect/pyproject.toml services/inspect/README.md ./
COPY services/inspect/aakar_inspect ./aakar_inspect
RUN uv sync --no-dev --no-editable

ENV PATH="/app/services/inspect/.venv/bin:${PATH}"

EXPOSE 8082
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD curl -fsS http://localhost:8082/healthz || exit 1
CMD ["aakar-inspect", "serve", "--host", "0.0.0.0", "--port", "8082"]
