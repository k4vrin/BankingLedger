# ADR: Phase 6 Immutable Ledger Reversals And Adjustments

## Status

Accepted

## Context

Posted ledger transactions, journal entries, and postings represent financial history. Updating those rows to correct a mistake would destroy the audit trail and make account balance movement hard to explain during investigation.

Phase 6 needs two correction workflows:

- Reversals for completed transfers.
- Manual operational adjustments for controlled correction cases.

Both workflows must preserve the original ledger records, write traceable request records, update balances only through the ledger posting engine, and commit audit/outbox rows atomically with the financial write.

## Decision

Reversals and adjustments are modeled as new ledger transactions.

For transfer reversal:

- The original transfer must be `COMPLETED`.
- A `reversals` row is created with `PENDING` status before ledger posting.
- The reversal ledger transaction uses transaction type `REVERSAL`.
- Reversal postings are built from the original postings with debit and credit directions swapped.
- The original transfer status changes to `REVERSED`.
- The original ledger transaction, journal entry, and postings remain unchanged.
- A `TRANSFER_REVERSED` audit event and `LedgerTransactionReversed` outbox event are written in the same transaction.

For adjustment:

- An `adjustment_requests` row is created with `PENDING` status before ledger posting.
- The adjustment ledger transaction uses transaction type `ADJUSTMENT`.
- Requested postings must contain at least one debit and one credit, with equal debit and credit totals.
- Account balance changes are applied by `PostLedgerTransactionUseCase`.
- The adjustment request is marked `COMPLETED` after the ledger transaction is posted.
- An `ADJUSTMENT_POSTED` audit event and `AdjustmentPosted` outbox event are written in the same transaction.

Until Phase 7 JWT authorization is implemented, both endpoints use explicit actor headers as authorization placeholders. `OPS_ADMIN` and `SERVICE` are allowed. Other roles receive structured `403` responses.

## Consequences

This preserves immutable ledger history and provides a direct correction chain from operational request to ledger transaction, audit event, and outbox event.

Queries that need net financial effect must include both original and correction ledger transactions. This is intentional: the ledger is append-only, and corrections are visible events rather than hidden mutations.

Rollback behavior is strict. If ledger posting, account validation, audit writing, or outbox writing fails, the reversal or adjustment request and all dependent financial writes roll back together.

## Guardrails

- Do not update original postings to reverse or adjust financial activity.
- Do not delete original ledger rows from application workflows.
- Do not write reversal or adjustment audit/outbox rows outside the financial transaction.
- Keep duplicate reversal checks stable even after the transfer is already marked `REVERSED`.
- Add dedicated request records for operational traceability rather than relying only on ledger transaction metadata.
