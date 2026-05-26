# ADR: Phase 10 Reconciliation Design

## Status

Accepted

## Context

The ledger needs a portfolio-sized reconciliation workflow that imports an external settlement batch, compares it with internal ledger transactions, records results, and emits operational signals through audit and outbox records.

## Decision

Reconciliation runs synchronously in the settlement batch import transaction. The API persists the batch and items, runs deterministic matching, writes reconciliation results, writes audit records, and creates outbox events atomically. If reconciliation fails unexpectedly, the transaction rolls back and no partial batch is left behind.

Matching rules:

- Settlement items match internal ledger transactions by `external_reference`.
- `SETTLED` external items expect internal `POSTED` ledger transactions.
- A `REVERSED` internal transaction settled as `SETTLED` is a `REVERSED_TRANSACTION_SETTLED` critical mismatch.
- Amount and currency mismatches are critical.
- Status mismatches are warnings unless covered by the reversed-settled rule.
- Missing internal transactions are critical.
- Missing external settlements are checked against posted or reversed ledger transactions with non-null external references in the imported settlement date range.
- Exact matches create `MATCHED` informational results marked `RESOLVED`.

Supported mismatch types:

- `MATCHED`
- `MISSING_INTERNAL_TRANSACTION`
- `MISSING_EXTERNAL_SETTLEMENT`
- `AMOUNT_MISMATCH`
- `CURRENCY_MISMATCH`
- `STATUS_MISMATCH`
- `DUPLICATE_EXTERNAL_ITEM`
- `DUPLICATE_INTERNAL_TRANSACTION`
- `REVERSED_TRANSACTION_SETTLED`
- `SETTLEMENT_DATE_OUT_OF_WINDOW`

## API Payload

```json
{
  "source": "VISA",
  "referenceName": "visa-settlement-2026-05-20.csv",
  "importedByActor": "ops-1",
  "items": [
    {
      "externalTransactionReference": "transfer-1001",
      "amountMinor": 12500,
      "currencyCode": "USD",
      "status": "SETTLED",
      "settlementDate": "2026-05-20",
      "metadata": {
        "network": "visa",
        "merchantReference": "m-123"
      }
    }
  ]
}
```

The authenticated principal and `X-Correlation-Id` header are authoritative for audit actor and correlation context.

## Operational Review

Open reconciliation results are reviewed by operations users through batch and result query APIs. Critical mismatches also produce `ReconciliationMismatchFound` outbox events. A completed batch produces `ReconciliationCompleted`.

## Consequences

The synchronous approach keeps this phase easy to reason about and test. Large settlement files would need an asynchronous import/reconciliation workflow later.
