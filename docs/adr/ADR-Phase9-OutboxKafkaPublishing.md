# ADR: Phase 9 Outbox And Kafka Publishing

## Context

Financial workflows must commit business state and event intent atomically while still publishing events to Kafka after the database transaction succeeds. Direct Kafka publishing inside financial transactions would couple broker availability to ledger consistency, so the application uses an outbox table as the durable handoff.

## Decision

Financial use cases write `PENDING` `outbox_events` rows inside the same transaction as ledger, transfer, reversal, adjustment, audit, and reconciliation records. A scheduled publisher selects publishable rows with pessimistic locking, sends the stored payload to Kafka, and marks the row `PUBLISHED` only after Kafka acknowledgement.

The publisher selects rows with status `PENDING`, or `FAILED` rows whose `next_retry_at` is due. Rows are processed in deterministic `created_at, id` order. Failed publishes store a truncated error message, increment `retry_count`, and schedule the next attempt with bounded exponential backoff. Rows that reach the configured maximum attempts move to `DEAD_LETTER`.

Replay is an explicit operational action. `OPS_ADMIN` and `SERVICE` callers can requeue `FAILED` and `DEAD_LETTER` rows through the ops API. Published rows require `force=true`. Requeue preserves the original event id, payload, aggregate metadata, event type, destination, and retry count unless `resetRetryCount=true` is supplied. Every accepted requeue writes an audit row.

## Topics And Headers

Topic destinations are represented by `OutboxDestination`:

- `banking-ledger.ledger-events`
- `banking-ledger.account-events`
- `banking-ledger.reconciliation-events`

Published records use the outbox event id as the Kafka key and include these headers:

- `outbox-event-id`
- `correlation-id`
- `event-type`
- `schema-version`

## Event Schemas

Current v1 event names are:

- `LedgerTransactionPosted`
- `LedgerTransactionReversed`
- `AdjustmentPosted`
- `AccountBalanceChanged`
- `ReconciliationMismatchFound`
- `ReconciliationCompleted`

Payloads are stored as JSON in the outbox row and sent unchanged to Kafka. `AccountBalanceChanged` carries post-change account balances and is emitted only for customer accounts whose balances changed.

## Operations

Monitor:

- pending outbox count
- dead-letter count
- oldest pending event age
- publish success count
- publish failure count
- dead-letter transition count

Manual recovery flow:

1. Inspect dead-letter or failed rows through `/api/v1/ops/outbox/events?status=DEAD_LETTER` or `status=FAILED`.
2. Fix the broker, payload, or downstream condition that caused publication to fail.
3. Requeue the row with `POST /api/v1/ops/outbox/events/{eventId}/requeue`.
4. Use `resetRetryCount=true` when the previous attempts should not count against the retry budget.
5. Use `force=true` for a published event only when an operator intentionally accepts duplicate downstream delivery risk.

The normal scheduler never deletes outbox payloads and never republishes `PUBLISHED` or `DEAD_LETTER` rows unless an operator requeues them.
