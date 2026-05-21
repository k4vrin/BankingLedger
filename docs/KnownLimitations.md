# Known Limitations

- The project is a portfolio backend, not a production banking system.
- Local JWT issuance exists only in the `dev` profile and is intended for demos.
- Seed data is deterministic and synthetic; it does not model real customers or private data.
- Reconciliation imports are API-driven and do not include bulk file upload handling.
- Report SQL is source-controlled for Oracle-oriented review, but no report export API is exposed yet.
- Kafka publishing uses local topics and simple JSON payloads rather than a schema registry.
- The outbox requeue endpoint is operational and role-protected, but it does not include a full approval workflow.
- The API uses cached account balances maintained inside ledger transactions; periodic balance recomputation is not implemented yet.
- CI uses Oracle Free through Docker Compose rather than Testcontainers; this keeps database behavior close to production but makes CI heavier than an in-memory database strategy.
- CI generates JaCoCo coverage artifacts but does not enforce a percentage threshold yet.
- GitHub dependency review is non-blocking until dependency graph is enabled for the repository; OWASP dependency-check remains the enforced dependency vulnerability gate.
- Formatter and Checkstyle gates are intentionally deferred until a concrete team style profile is chosen.
- Portfolio visual assets are editable Mermaid diagrams and docs; tracked screenshots are intentionally omitted.
- Multi-currency exchange, fees, interest accrual, card rails, ACH/wire integrations, and external payment networks are out of scope.

## Future Improvements

- Publish the CI badge after the repository path is public and stable.
- Add automated OpenAPI contract validation for documented examples.
- Add a report export API for selected operational SQL reports.
- Add periodic balance recomputation and variance alerting.
- Add schema registry support for Kafka event payloads.
- Add approval workflows for high-risk operational actions such as forced outbox requeue.
- Add an explicit formatter/checkstyle profile if the project starts accepting external contributions.
