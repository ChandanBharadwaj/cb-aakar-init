# ADR-0011: Contract-first schemas and OpenAPI

- Status: Accepted
- Date: 2026-09-26
- Deciders: Chandan Bharadwaj (product owner)

## Context
Three languages (Java, Python, TypeScript) exchange the same shapes: the Design Spec, template descriptors, printability reports, price breakdowns and job events. Drift between them is the most likely source of integration bugs.

## Decision
`packages/contracts` holds JSON Schemas (2020-12) and OpenAPI 3.1 documents and is changed **before** any service. Services validate what they emit against the schemas in tests; the web client generates types from the OpenAPI document; CI validates the package and the generated types.

## Consequences
Small overhead per change. In exchange integration is a matter of validating against a shared example, not reading three codebases.
