# Glossary

| Term | Meaning |
| --- | --- |
| Account | A customer or internal balance container denominated in one currency. |
| Available balance | Cached spendable balance used for fast reads and transfer validation. |
| Ledger balance | Cached posted ledger balance derived from accepted journal postings. |
| Ledger transaction | The business-level financial transaction, such as a transfer, reversal, fee, or adjustment. |
| Journal entry | A balanced accounting entry attached to one ledger transaction. |
| Posting | A single debit or credit line in a journal entry. |
| Double-entry | Accounting model requiring every journal entry to have equal total debits and credits. |
| Transfer | Movement between two accounts posted through the ledger engine. |
| Reversal | Immutable correction that posts opposite entries instead of editing the original transaction. |
| Adjustment | Operational correction with explicit balanced posting lines and reason codes. |
| Reconciliation | Comparison between internal ledger transactions and external settlement records. |
| Settlement batch | Imported group of external settlement items. |
| Mismatch | A reconciliation result showing amount, currency, status, duplicate, or missing-record differences. |
| Audit event | Append-only operational event containing actor, role, entity, correlation id, and redacted payload. |
| Outbox event | Database-backed event record published asynchronously to Kafka after commit. |
| Idempotency key | Client-provided key allowing safe retry of a mutating request. |
| Correlation id | Request identifier propagated through logs, audit records, outbox events, and error responses. |
