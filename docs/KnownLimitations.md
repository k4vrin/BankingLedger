# Known Limitations

- The project is a portfolio backend, not a production banking system.
- Local JWT issuance exists only in the `dev` profile and is intended for demos.
- Seed data is deterministic and synthetic; it does not model real customers or private data.
- Reconciliation imports are API-driven and do not include bulk file upload handling.
- Report SQL is source-controlled for Oracle-oriented review, but no report export API is exposed yet.
- Kafka publishing uses local topics and simple JSON payloads rather than a schema registry.
- The outbox requeue endpoint is operational and role-protected, but it does not include a full approval workflow.
- The API uses cached account balances maintained inside ledger transactions; periodic balance recomputation is not implemented yet.
- Multi-currency exchange, fees, interest accrual, card rails, ACH/wire integrations, and external payment networks are out of scope.

## Future Improvements

- Add CI quality gates and publish status once the repository is public.
- Add automated OpenAPI contract validation for documented examples.
- Add a report export API for selected operational SQL reports.
- Add periodic balance recomputation and variance alerting.
- Add schema registry support for Kafka event payloads.
- Add approval workflows for high-risk operational actions such as forced outbox requeue.
