# Database Design

This document explains the database design for the Mini Core Banking Ledger. The schema is intentionally conservative: financial records should be traceable, append-oriented, and protected by database constraints where the invariant can be expressed safely in SQL.

The initial schema is implemented in `banking-ledger-api/src/main/resources/db/migration/V1__initial_schema.sql`.

## Design Goals

- Preserve financial correctness before optimizing for throughput.
- Store money as integer minor units, never floating-point values.
- Keep ledger records immutable after posting.
- Use reversals and adjustments instead of destructive edits.
- Use database constraints for uniqueness, referential integrity, valid statuses, valid directions, positive amounts, and currency consistency.
- Keep balance-changing workflows transactional and concurrency-safe.
- Keep audit and outbox records in the same database transaction as the business change that produced them.

## Schema Overview

The V1 schema contains the core tables needed for accounts, double-entry ledger posting, transfer requests, auditing, idempotency, and reliable event publishing.

### `customers`

`customers` stores the customer identity used by accounts and customer-initiated transfer requests.

Important choices:

- `id` is `RAW(16)` so Java UUIDs can be stored compactly.
- `external_customer_reference` is unique to support stable integration with external systems.
- `email` is unique when present.
- `status` is constrained to known lifecycle values.
- `version` supports optimistic locking for future customer profile updates.

### `accounts`

`accounts` represents both customer-facing accounts and internal ledger accounts such as suspense, clearing, and fee income accounts.

Important choices:

- `account_number` is unique and separate from the technical UUID.
- `account_type` describes the business/accounting role of the account.
- `account_category` separates `CUSTOMER` accounts from `INTERNAL` accounts.
- `currency_code` is constrained to ISO-style uppercase three-letter values.
- `available_balance_minor` and `ledger_balance_minor` are cached balance views, not the source of truth.
- `(id, currency_code)` is unique so child rows can enforce currency consistency through composite foreign keys.
- `version` supports optimistic locking, but transfer posting should still use explicit row locks for debit checks.

Balance source of truth:

The authoritative ledger is the posting history. Cached balances exist for fast account balance queries and debit validation. They must be updated only inside the same transaction that creates ledger postings.

### `ledger_transactions`

`ledger_transactions` is the business-level financial transaction record. A transfer, fee, reversal, or adjustment starts here.

Important choices:

- `external_reference` is unique for upstream or client-provided transaction references.
- `transaction_type` and `status` are constrained to known values.
- `amount_minor` must be positive.
- `posted_at` is required for `POSTED` and `REVERSED` transactions and must be absent for `PENDING`, `REJECTED`, and `FAILED` transactions.
- `failure_reason_code` is required for `REJECTED` and `FAILED` transactions.
- `(id, currency_code)` is unique so journal entries and transfer requests can enforce same-currency links.

### `journal_entries`

`journal_entries` groups the debit and credit postings for a ledger transaction.

Important choices:

- Each journal entry references one `ledger_transaction`.
- `total_debit_minor` and `total_credit_minor` must be positive.
- `total_debit_minor` must equal `total_credit_minor`.
- `posted_at` is required because journal entries represent financial posting records.
- A composite foreign key ensures the journal entry currency matches the parent ledger transaction currency.
- `(id, currency_code)` is unique so postings can enforce same-currency links.

The database can verify that the declared debit and credit totals match each other. It cannot fully verify, using simple check constraints, that all child postings sum exactly to those totals. The ledger posting service must enforce:

- at least two postings,
- at least one debit and one credit,
- all posting amounts are positive,
- all postings use the same currency,
- total debits equal total credits,
- posting totals match the journal entry totals.

### `postings`

`postings` are the immutable debit and credit lines against accounts.

Important choices:

- Each posting references one journal entry and one account.
- `direction` is constrained to `DEBIT` or `CREDIT`.
- `amount_minor` must be positive.
- Composite foreign keys ensure the posting currency matches both the journal entry currency and the account currency.
- Account transaction history is indexed by `(account_id, posted_at)`.

Posting rows should be append-only. Corrections must be represented by reversal or adjustment postings, not updates to existing posted rows.

### `transfer_requests`

`transfer_requests` stores account-to-account transfer workflow state.

Important choices:

- Source and destination accounts must be different.
- Source and destination account currencies must match the transfer currency.
- The linked ledger transaction currency must match the transfer currency.
- `ledger_transaction_id` is unique because one transfer should produce one ledger transaction.
- `completed_at` is required for `COMPLETED` and `REVERSED` transfers and must be absent for `PENDING`, `REJECTED`, and `FAILED` transfers.
- `failure_reason_code` is required for `REJECTED` and `FAILED` transfers.

This table is workflow state. The durable accounting record is still the combination of `ledger_transactions`, `journal_entries`, and `postings`.

### `audit_events`

`audit_events` stores immutable operational and financial audit records.

Important choices:

- Events capture entity type, entity id, actor details, channel, correlation id, payload, and timestamp.
- Actor roles and actor types are constrained to known values.
- Audit lookup is indexed by entity, event type, and correlation id.

Audit payloads must not contain secrets, raw credentials, full card data, or unnecessary personally identifiable information.

### `idempotency_records`

`idempotency_records` prevents duplicate money movement when clients retry requests.

Important choices:

- `(operation_scope, idempotency_key)` is unique.
- `request_hash` allows the API to reject reuse of the same idempotency key with a different request body.
- `response_status` and `response_body` allow duplicate requests to return the original outcome.
- `expires_at` supports cleanup of old idempotency records.

For transfer creation, idempotency record creation and transfer posting must be part of the same transaction or coordinated so a retry cannot double-post.

### `outbox_events`

`outbox_events` supports reliable event publishing to Kafka or another broker.

Important choices:

- Outbox records are written in the same database transaction as the ledger or transfer change.
- `status` is constrained to `PENDING`, `PUBLISHED`, `FAILED`, or `DEAD_LETTER`.
- `published_at` is required only when status is `PUBLISHED`.
- `next_retry_at` is required for `FAILED` rows and optional for fresh `PENDING` rows.
- Worker lookup is indexed by `(status, next_retry_at)`.

The application should publish from the outbox after the database commit, then mark records as published in a separate transaction.

## Isolation Level And Locking Strategy

The project should use Oracle `READ COMMITTED` as the default isolation level.

Oracle already provides statement-level read consistency through MVCC. Enabling `SERIALIZABLE` globally is not recommended for this application because normal reads and simple writes would pay the cost of stronger isolation, and concurrent writers can still fail with serialization errors that require retry handling.

Recommended strategy:

- Use `READ COMMITTED` globally.
- Use explicit transaction boundaries around transfer posting, reversal, adjustment, idempotency replay, and outbox writes.
- Use pessimistic row locks for balance-changing operations.
- Use optimistic locking for normal entity updates where retry or conflict reporting is acceptable.
- Add concurrent integration tests to prove overdrafts and duplicate postings cannot occur.

### Transfer Posting Transaction

A transfer posting transaction should:

1. Resolve the idempotency key.
2. Lock the source and destination account rows with `SELECT ... FOR UPDATE`.
3. Lock accounts in deterministic order, usually by account UUID, to reduce deadlock risk.
4. Validate account status, currency, amount, and available balance after locks are acquired.
5. Insert or update the transfer request.
6. Insert the ledger transaction, journal entry, and postings.
7. Update cached account balances.
8. Insert audit events.
9. Insert outbox events.
10. Commit everything atomically.

If any step fails, the whole transaction must roll back.

### Why Not Global `SERIALIZABLE`

Global `SERIALIZABLE` is not the best default here.

- It can produce serialization failures under concurrent transfers.
- It still requires retry logic.
- It does not replace row-level locking for debit checks.
- It can reduce throughput for read-heavy account history and audit queries.
- It makes unrelated workflows pay for the isolation needs of transfer posting.

Use stronger isolation only for a narrow workflow if there is a measured need and a documented retry strategy.

## Database Best Practices

### Migrations

- Treat Flyway migrations as append-only after they have been applied to a shared environment.
- Because V1 is still early project setup, editing it before a shared release is acceptable.
- Keep migrations Oracle-compatible.
- Prefer explicit constraint names so production errors are easier to diagnose.
- Test migrations against a clean Oracle database.

### Constraints

- Keep database constraints for invariants that should never be violated.
- Use check constraints for enum-like statuses, directions, positive money amounts, and lifecycle timestamp rules.
- Use foreign keys for ownership and parent-child integrity.
- Use composite foreign keys where cross-table currency consistency matters.
- Keep domain/service validation even when the database also validates the same rule, so API errors can be clear and intentional.

### Money

- Store money as integer minor units in `number(19, 0)`.
- Do not use floating-point types.
- Validate positive amounts before persistence.
- Keep currency explicit on every financial row.
- Never mix currencies inside a journal entry.

### Immutability

- Posted ledger records should not be updated by normal workflows.
- Reversals should create equal and opposite postings.
- Adjustments should create new entries with explicit reasons.
- Any operational correction must leave an audit trail.

### Indexes

Indexes should support real access patterns:

- account history by account and posted time,
- transaction lookup by status, type, external reference, and posted time,
- transfer lookup by account, status, and creation time,
- audit lookup by entity and correlation id,
- idempotency cleanup by expiry,
- outbox worker polling by status and retry time.

Avoid adding indexes for hypothetical queries until the access pattern exists.

### Testing

The database layer should have integration tests for:

- clean Flyway migration on Oracle,
- invalid enum/status values,
- negative or zero money amounts,
- currency mismatch rejection,
- duplicate account numbers and external references,
- duplicate idempotency keys,
- duplicate transfer-to-ledger links,
- failed lifecycle states without failure reasons,
- completed lifecycle states without completion timestamps,
- concurrent transfers from the same source account,
- duplicate idempotency requests arriving concurrently,
- transaction rollback leaving no partial postings.

## Current Limitations

Some financial invariants require service-level enforcement or database triggers/procedures because they span multiple child rows.

Current V1 limitations:

- The database does not directly prove that postings sum to the journal entry totals.
- The database does not directly prove that every journal entry has at least two postings.
- The database does not directly prove that every journal entry has both debit and credit postings.
- The database does not yet enforce append-only behavior with triggers.
- Reversal, settlement, and reconciliation tables are planned for later phases.

These limitations are acceptable for V1 if the domain layer and integration tests enforce the missing invariants before any posting is committed.
