# Aakar geometry service (API by default; the worker runs the same image with `aakar-geometry worker`).
# Build context is the repo root:
#   docker build -f services/geometry/Dockerfile -t aakar-geometry .
FROM python:3.11-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_LINK_MODE=copy \
    UV_COMPILE_BYTECODE=1 \
    AAKAR_CONTRACTS_DIR=/contracts/schemas \
    AAKAR_MATERIALS_FILE=/design-tokens/materials.json \
    AAKAR_FAMILIES_FILE=/design-tokens/families.json \
    AAKAR_STORAGE=local \
    AAKAR_ASSET_DIR=/data/assets \
    AAKAR_PUBLIC_URL=http://localhost:8081

# OCCT runtime libraries needed by the cadquery-ocp wheel behind build123d, plus rtree's spatial index.
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      libgl1 libglu1-mesa libxrender1 libxext6 libsm6 libxi6 libxcursor1 libxft2 libxinerama1 \
      libfontconfig1 libgomp1 libspatialindex-dev curl \
 && rm -rf /var/lib/apt/lists/*

COPY --from=ghcr.io/astral-sh/uv:0.8.17 /uv /uvx /bin/

WORKDIR /app/services/geometry

# Contracts are the law; the materials and families files feed the template descriptors and registry.
COPY packages/contracts/schemas /contracts/schemas
COPY packages/design-tokens/materials.json /design-tokens/materials.json
COPY packages/design-tokens/families.json /design-tokens/families.json

# aakar-inspect is a path dependency (../inspect), so both services ship in the image.
COPY services/inspect/pyproject.toml services/inspect/README.md /app/services/inspect/
COPY services/inspect/aakar_inspect /app/services/inspect/aakar_inspect
COPY services/geometry/pyproject.toml services/geometry/README.md ./
COPY services/geometry/aakar_geometry ./aakar_geometry
RUN uv sync --no-dev

ENV PATH="/app/services/geometry/.venv/bin:${PATH}"
RUN mkdir -p /data/assets && python -c "import build123d, manifold3d, aakar_geometry.templates, aakar_geometry.features"  # warm import + sanity

EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD curl -fsS http://localhost:8081/healthz || exit 1
CMD ["aakar-geometry", "serve", "--host", "0.0.0.0", "--port", "8081"]
