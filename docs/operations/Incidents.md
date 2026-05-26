# Incident Write-Ups

These scenarios are portfolio write-ups. They describe how the backend is designed to detect, explain, and prevent common financial-system failures.

## Duplicate Transfer Request

Symptoms: A client retries `POST /api/v1/transfers` after a timeout and risks moving money twice.

Root cause: The client cannot know whether the original request committed before the network failure.

Detection: Search idempotency records by operation scope and key, then compare the stored request hash and response body. Use the audit event correlation id to connect the retry to the original transfer.

Fix: Transfer creation requires `Idempotency-Key`. Same key and same body returns the stored response. Same key and different body returns `409 IDEMPOTENCY_KEY_CONFLICT`.

Prevention: Keep idempotency validation before ledger posting and include replay/conflict cases in the HTTP demo and CI test suite.

## Overdraft Race Condition

Symptoms: Two concurrent transfers from the same source account both see enough funds and together overdraw the account.

Root cause: Balance validation and balance mutation are unsafe if requests read the same pre-transfer balance without serialization.

Detection: Inspect transfer audit events by account and timestamp, then review account postings and cached balance changes in the ledger investigation endpoint.

Fix: Transfer creation locks source and destination accounts pessimistically before validating funds or updating cached balances.

Prevention: Lock accounts in stable order, map lock conflicts to retryable `409` responses, and keep database balance constraints as a final guard.

## Failed Reversal Investigation

Symptoms: An ops user attempts to reverse a transfer and receives a rejected or failed reversal response.

Root cause: The original transfer may already be reversed, may not have a posted ledger transaction, or may violate reversal policy.

Detection: Query the transfer, existing reversal record, audit event, and original ledger transaction through the ledger investigation endpoint.

Fix: Reversal policy validates reversibility before posting opposite entries. Successful reversals create a new ledger transaction instead of editing the original.

Prevention: Preserve unique constraints for one reversal per original transfer and document reversal reason codes in the API contract.

## Reconciliation Mismatch

Symptoms: An external settlement item does not match internal ledger records.

Root cause: The settlement file may include a missing internal transaction, amount mismatch, currency mismatch, status mismatch, duplicate item, or settlement outside the expected window.

Detection: Import the settlement batch and query `/api/v1/ops/reconciliation/batches/{batchId}/results` by mismatch type, severity, and status.

Fix: Reconciliation results record the external item, matched ledger transaction when present, mismatch type, severity, and investigation detail.

Prevention: Keep deterministic reconciliation demo data and SQL reports for mismatch aging and operational review.

## Outbox Publish Failure And Replay

Symptoms: Financial data commits, but Kafka consumers do not receive the expected event.

Root cause: Broker outage, topic configuration, serialization failure, or transient network error after the database transaction committed.

Detection: Query outbox events by status, retry count, next retry time, and last error message. Use the correlation id to connect the outbox event to the business operation and audit event.

Fix: The publisher retries failed events with backoff and moves exhausted events to dead-letter status. Ops users can requeue eligible events with `POST /api/v1/ops/outbox/events/{eventId}/requeue`.

Prevention: Keep event rows transactional, scan outbox metrics, and require protected requeue operations for manual recovery.
