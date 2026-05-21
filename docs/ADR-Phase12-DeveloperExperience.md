# ADR: Phase 12 Developer Experience

## Status

Accepted.

## Context

The project should be easy for reviewers to run, inspect, and understand without private infrastructure or secret material. Phase 12 adds documentation and local demo affordances around the already implemented backend capabilities.

## Decision

OpenAPI documentation is generated with Springdoc and grouped into customer, operations, audit, and development slices. The published spec documents JWT bearer authentication, correlation ids, transfer idempotency, common structured errors, and core examples.

Local development uses deterministic repeatable Flyway seed data in the `dev` profile. Seed identifiers are stable so README snippets, HTTP files, and OpenAPI examples can point at known resources.

Developer commands live in `banking-ledger-api/Makefile`. The commands wrap dependency startup, tests, application startup, token issuance, and report discovery without replacing the underlying Maven or Docker Compose workflows.

Demo API requests live in a source-controlled `.http` file instead of a binary Postman collection. This keeps the collection reviewable and easy to run from common IDE REST clients.

Diagrams are stored as Mermaid source files under `docs/diagrams` so they stay editable in source control.

## Consequences

- Reviewers can inspect Swagger UI locally and run the demo flow with generated dev JWTs.
- Documentation avoids committing real secrets or bearer token values.
- Seed data is development-only and not loaded by the production profile.
- Future CI and portfolio polish phases can reuse the same commands and docs.
