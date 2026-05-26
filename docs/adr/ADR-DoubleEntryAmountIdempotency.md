# ADR: Double-Entry, Amounts, And Idempotency

## Status

Accepted.

## Context

The ledger needs to demonstrate financial correctness without becoming a full core banking platform. The most important invariants are balanced postings, explicit currency representation, immutable correction history, and safe client retries for money-moving requests.

## Decision

Financial mutations are posted through double-entry journal entries. Every journal entry must have equal debit and credit totals, and each posting carries an account, direction, currency, and positive minor-unit amount.

Amounts are represented as integer minor units plus an ISO-style three-letter currency code. The application does not use floating point values for money, and cross-currency posting is out of scope for the current backend.

Transfer creation requires an idempotency key. The service stores the request hash and response body. A retry with the same key and same body returns the stored response, while a retry with the same key and a different body returns an idempotency conflict.

Corrections never mutate posted financial history. Reversals and adjustments create new ledger transactions and journal entries linked back to the operational request.

## Consequences

- Database constraints and domain policies both enforce balanced entries and valid currency/amount data.
- API examples use `amountMinor` and `currencyCode` consistently.
- Reviewers can reason about financial history by following append-only records.
- Client retries can be safe without making transfer creation dependent on network reliability.
