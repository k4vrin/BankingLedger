# Transfer Transaction Boundary Audit

This document audits the current transfer creation write path for Phase 5. It records which use cases mutate account balances, how transfer creation and ledger posting participate in one database transaction, and which repositories are called before cached account balances are changed.

## Scope

The audited path is:

```text
POST /api/v1/transfers
-> TransferController.createTransfer
-> CreateTransferUseCase.handle
-> PostLedgerTransactionUseCase.handle
-> AccountBalanceUpdater.apply
```

The current balance-changing operation is ledger posting. Transfers change balances only by calling the ledger posting engine.

## Balance-Mutating Use Cases

| Use case | Transactional boundary | Mutates account balances | Notes |
| --- | --- | --- | --- |
| `PostLedgerTransactionUseCase.handle` | `@Transactional` on the public handler | Yes | Creates ledger transaction, journal entry, postings, audit event, outbox event, and updates managed `AccountEntity` balances through `AccountBalanceUpdater`. |
| `CreateTransferUseCase.handle` | `@Transactional` on the public handler | Indirectly | Creates transfer workflow state and calls `PostLedgerTransactionUseCase.handle` to post the debit and credit. |
| `CreateAccountUseCase.handle` | `@Transactional` on the public handler | Initializes balances | Creates an account with starting cached balance values. It does not post ledger movements. |

Read-only account query use cases are annotated with `@Transactional(readOnly = true)` and do not mutate balances.

## Transfer Transaction Confirmation

`CreateTransferUseCase.handle` is annotated with `@Transactional`. `PostLedgerTransactionUseCase.handle` is also annotated with `@Transactional`.

Because `CreateTransferUseCase` injects and calls `PostLedgerTransactionUseCase` as a separate Spring bean, the ledger posting call goes through Spring transaction interception. With Spring's default propagation, `Propagation.REQUIRED`, the inner ledger posting method joins the already-open transfer creation transaction instead of starting an independent transaction.

Effective transaction behavior for transfer creation:

1. The transfer request validation and idempotency lookup run inside the transfer transaction.
2. The pending transfer request insert runs inside the same transaction.
3. The ledger transaction, journal entry, postings, audit event, and outbox event inserts run inside the same transaction.
4. Cached account balance changes are made on managed account entities inside the same persistence context.
5. The completed transfer update and idempotency response insert run inside the same transaction.
6. A runtime exception from validation, duplicate detection, ledger posting, serialization, persistence, audit, outbox, or idempotency causes the full transaction to roll back.

There is no explicit `REQUIRES_NEW` boundary in this path, so transfer creation and ledger posting are not independently committed.

## Idempotency Atomicity

Transfer idempotency currently participates in the same transaction as transfer and ledger writes:

1. `IdempotencyService.findTransferCreate` reads an existing idempotency record before creating a new transfer.
2. If a matching record exists, `CreateTransferUseCase` returns the stored response without writing transfer or ledger rows.
3. If no record exists, `CreateTransferUseCase` posts the transfer and ledger records.
4. After the transfer is completed, `IdempotencyService.createTransferCreateRecord` saves the idempotency response in `idempotency_records`.

Because the idempotency record is saved before the outer `CreateTransferUseCase.handle` transaction commits, the idempotency record commits atomically with:

- `transfer_requests`,
- `ledger_transactions`,
- `journal_entries`,
- `postings`,
- cached `accounts` balance updates,
- `audit_events`,
- `outbox_events`.

If any later write fails before commit, the idempotency record is rolled back with the rest of the transfer.

Current concurrency gap: simultaneous requests with the same idempotency key can both miss the initial lookup. The unique constraint on `(operation_scope, idempotency_key)` prevents two committed idempotency records, but the service still needs Phase 5 duplicate-key race handling so the loser re-reads and replays or rejects based on the stored request hash.

## Repository Call Order Before Balance Mutation

The current transfer path calls repositories in this order before `AccountBalanceUpdater.apply` mutates cached account balances.

| Step | Component | Repository call | Purpose |
| --- | --- | --- | --- |
| 1 | `CreateTransferUseCase` | `IdempotencyRecordRepository.findByOperationScopeAndIdempotencyKey` | Detect replay or idempotency-key conflict before creating new records. |
| 2 | `CreateTransferUseCase` | `AccountRepository.findById` for source account | Load source account for transfer validation. Current implementation does not lock this row. |
| 3 | `CreateTransferUseCase` | `AccountRepository.findById` for destination account | Load destination account for transfer validation. Current implementation does not lock this row. |
| 4 | `CreateTransferUseCase` | `TransferRequestRepository.existsByExternalReference` | Reject duplicate transfer external reference before insert. |
| 5 | `CreateTransferUseCase` | `TransferRequestRepository.save` | Save transfer request with `PENDING` status. |
| 6 | `PostLedgerTransactionUseCase` | `LedgerTransactionRepository.existsByExternalReference` | Reject duplicate ledger external reference before posting. |
| 7 | `PostLedgerTransactionUseCase` | `AccountRepository.findById` for posting accounts | Load each posting account and validate posting currency. Current implementation does not lock these rows. |
| 8 | `PostLedgerTransactionUseCase` | `LedgerTransactionRepository.save` | Save posted ledger transaction. |
| 9 | `PostLedgerTransactionUseCase` | `JournalEntryRepository.save` | Save journal entry for the transaction. |
| 10 | `PostLedgerTransactionUseCase` | `PostingRepository.saveAll` | Save immutable debit and credit postings. |
| 11 | `PostLedgerTransactionUseCase` | Managed `AccountEntity` dirty checking | `AccountBalanceUpdater.apply` changes loaded account balances; Hibernate flushes account updates at transaction commit. |

After balance mutation, the flow saves audit and outbox records, completes the transfer request, flushes the transfer repository, and saves the idempotency response.

## Findings

The current transaction boundary is correct for atomic transfer creation: transfer workflow state, ledger records, cached balance updates, audit, outbox, and idempotency response share one transaction.

The current concurrency protection is incomplete for Phase 5:

- Source and destination accounts are loaded with ordinary `findById` calls.
- Posting accounts are loaded with ordinary `findById` calls.
- Balance validation can read stale cached balances under concurrent transfers.
- The `accounts.version` column provides optimistic conflict detection at flush time, but the transfer path does not yet convert those conflicts into a documented API error or retry strategy.
- Concurrent duplicate idempotency requests rely on the database unique constraint but do not yet re-read and replay after a duplicate-key race.

Phase 5 should add explicit locked account loading before balance validation and before cached account balance mutation.
