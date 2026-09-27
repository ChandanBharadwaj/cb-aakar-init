# ADR-0006: Hosting: local Docker only for now; production deferred

- Status: Deferred (decided by the product owner on 2026-09-27: "productionizing is not now")
- Date: 2026-09-27
- Deciders: Chandan Bharadwaj (product owner)

## Context
Customers, payments, shipping and the studio are in India, which would favour an Indian cloud region. The owner wants to play with the product locally first and decide on production hosting later.

## Decision
For now the **only deployment target is the local Docker Compose stack** (`infra/docker-compose.yml`): Postgres, RabbitMQ, MinIO, the three services and the storefront on one machine. No cloud account, Terraform or CI deployment is created until the owner asks. The earlier recommendation (AWS ap-south-1) stays on record as the default candidate when that day comes.

## Consequences
- Phase 1's "50 paid orders shipped" exit criterion is reinterpreted as end-to-end runs on the local stack, with real payment and shipping providers in sandbox mode.
- Everything must keep working with `docker compose up` on a laptop: no service may depend on a cloud-only product.
- When production is scheduled, revisit: region, managed Postgres and MQ, object storage, secrets, observability.
