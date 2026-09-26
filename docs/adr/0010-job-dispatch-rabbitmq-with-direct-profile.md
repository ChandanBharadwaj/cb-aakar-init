# ADR-0010: Job dispatch over RabbitMQ with a direct HTTP profile

- Status: Accepted
- Date: 2026-09-26
- Deciders: Chandan Bharadwaj (product owner)

## Context
Generation runs 2 to 60 seconds in Python workers while the API is Java. Production needs durable queues, retries and horizontal scaling; local development and the vertical slice need a two-process setup that works without a broker.

## Decision
Messages use one envelope (`packages/contracts/schemas/events/envelope.v1.json`) on topic exchange `aakar.design`. The API has two `JobDispatcher` implementations selected by Spring profile: `rabbit` publishes and consumes results; `direct` (default) calls the geometry service's `POST /v1/build` over HTTP and receives progress on an internal callback. The request and result bodies are identical in both paths.

## Consequences
The geometry service exposes the same build function over HTTP and as a worker. Developers can run the whole slice with Postgres, the API and the geometry service only. Rabbit-specific behaviour (dead-lettering, retries) is exercised in the Compose stack and staging.
