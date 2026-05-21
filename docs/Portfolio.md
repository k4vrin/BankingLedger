# Portfolio Narrative

## 2-Minute Explanation

Mini Core Banking Ledger is a Java 21 and Spring Boot backend that models the financial correctness concerns behind a small banking ledger. It supports account creation, double-entry transfers, immutable reversals, operational adjustments, audit search, ledger investigation, reconciliation, and transactional outbox publishing to Kafka.

The system keeps Oracle as the source of truth. Every balance-changing operation posts balanced journal entries, updates cached account balances inside the same transaction, writes audit events, and records outbox events atomically. Transfers use idempotency keys so client retries cannot move money twice, and pessimistic account locks serialize concurrent balance updates.

The project is designed as an interview artifact: it has local Docker Compose infrastructure, deterministic seed data, OpenAPI docs, a runnable HTTP demo, Oracle-oriented SQL reports, ADRs, CI quality gates, and concise incident write-ups that explain how operational failures are diagnosed.

## Deep-Dive Talking Points

### Transactions And Ledger Correctness

- Balance-changing workflows run inside explicit Spring transaction boundaries.
- Ledger transactions, journal entries, postings, cached balances, audit rows, idempotency records, and outbox rows commit together.
- The double-entry policy requires total debits and credits to match before persistence.
- Oracle constraints enforce positive amounts, valid statuses, valid currencies, and required relationships as a final safety net.

### Idempotency

- `POST /api/v1/transfers` requires `Idempotency-Key`.
- The service hashes the request body and stores the response for the operation scope.
- Same key and same body returns the original response.
- Same key and different body returns a structured `409 IDEMPOTENCY_KEY_CONFLICT`.
- This protects clients from duplicate money movement during retries, timeouts, and network uncertainty.

### Locking And Concurrency

- Transfer creation loads source and destination accounts with pessimistic locks.
- Accounts are locked in stable order to reduce deadlock risk.
- Validation and cached balance updates happen only after locks are acquired.
- Concurrency exceptions are mapped to retryable structured `409` responses.

### Immutable Reversals

- Posted ledger rows are not edited to correct mistakes.
- A reversal creates a new ledger transaction with opposite postings.
- The original transfer is linked to the reversal request for investigation.
- This preserves auditability and mirrors real financial correction patterns.

### Outbox And Event Publishing

- Business transactions write outbox rows in the same database commit as financial data.
- A scheduled publisher reads pending events and publishes to Kafka.
- Failed publishes retain retry metadata and can move to dead-letter status.
- Ops users can requeue eligible events through a protected endpoint.

### Audit, Investigation, And Reconciliation

- Audit events capture actor, role, entity, channel, correlation id, and redacted payload.
- The investigation endpoint links one ledger transaction to journal entries, postings, operational requests, audit events, and outbox events.
- Reconciliation imports external settlement rows and records matched or mismatched results for auditor review.

### Security

- JWT bearer tokens authenticate protected APIs.
- Roles and account ownership checks authorize business operations.
- Dev token issuance exists only in the `dev` profile.
- Security errors use the same structured error shape without leaking token content or cryptographic details.

## Resume Bullets

- Built a Java 21/Spring Boot core banking ledger API with account, transfer, reversal, adjustment, audit, reconciliation, and outbox workflows.
- Implemented double-entry ledger posting with balanced journal entries, append-only financial records, and Oracle constraints for amount, currency, status, and relationship invariants.
- Designed ACID transaction boundaries that atomically update ledger records, cached balances, audit events, idempotency records, and transactional outbox events.
- Added idempotent transfer creation and pessimistic account locking to prevent duplicate transfers and overdraft races under concurrent requests.
- Secured APIs with Spring Security JWT resource server support, role checks, ownership authorization, and structured authentication/authorization errors.
- Integrated Kafka publishing through a transactional outbox with retry, dead-letter handling, metrics, and protected requeue operations.
- Added Oracle-oriented SQL/PL-SQL reports, reconciliation mismatch workflows, and ledger investigation APIs for operational debugging.
- Added Docker Compose local infrastructure, OpenAPI documentation, deterministic demo data, GitHub Actions CI, JaCoCo coverage, dependency review, secret scanning, and container scanning.

## GitHub Repository Metadata

Recommended repository description:

```text
Spring Boot core banking ledger portfolio API with double-entry accounting, Oracle, JWT security, reconciliation, Kafka outbox, and CI quality gates.
```

Recommended topics:

```text
java spring-boot oracle flyway jpa banking ledger double-entry-accounting kafka jwt docker github-actions
```

License: add one only if the repository owner wants explicit reuse rights. Until then, treat the project as a public portfolio artifact rather than a reusable library.

Contribution note: external contributions are not expected; issues and pull requests can be disabled or treated as discussion-only for portfolio review.

## Demo Script

Target duration: under 10 minutes.

1. Start dependencies and the API from `banking-ledger-api/`:

```bash
make deps-up
make run
```

2. Open Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

3. Run [demo-flow.http](../banking-ledger-api/http/demo-flow.http) from top to bottom.

4. Highlight these checkpoints:

- Account lookup returns seeded customer account data.
- Transfer creation returns a completed transfer and linked ledger transaction.
- Replaying the same idempotency key returns the original response.
- Reusing the idempotency key with a changed body returns a structured conflict.
- Reversal and adjustment post through the ledger engine.
- Reconciliation records an expected mismatch.
- Audit and ledger investigation endpoints explain what happened.

5. Reset for another demo:

```bash
make deps-reset
make deps-up
```
