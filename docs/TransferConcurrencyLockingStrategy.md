# Transfer Concurrency Locking Strategy

This document records the Phase 5 decision for concurrent balance-changing workflows.

## Decision

Use a hybrid concurrency strategy:

- Use Oracle `READ COMMITTED` as the default database isolation level.
- Use pessimistic row locks for account rows involved in balance-changing workflows.
- Keep JPA `@Version` columns as a secondary optimistic conflict guard.
- Map lock timeouts and optimistic conflicts to structured `409 Conflict` responses.
- Do not use global `SERIALIZABLE` isolation.

The first implementation target is transfer creation. Reversal and adjustment flows should use the same locked account loading pattern when they are implemented.

## Why Pessimistic Locks For Transfers

Transfer creation validates available balance and then updates cached account balances. That read-check-write sequence must be protected for the debit account, otherwise two concurrent transfers can both observe the same available balance and both decide that funds are sufficient.

The selected strategy is to lock all accounts participating in the transfer before validation:

1. Normalize and validate the transfer command.
2. Resolve idempotency replay or conflict.
3. Load source and destination account rows with pessimistic write locks.
4. Acquire locks in deterministic account id order.
5. Map the locked rows back to source and destination roles.
6. Validate account status, currency, and sufficient available balance after locks are held.
7. Save transfer, ledger transaction, journal entry, postings, audit, outbox, and idempotency response in one transaction.
8. Commit, releasing the account row locks.

This prevents lost updates because only one transaction at a time can validate and update the same locked account rows. A second transfer touching the same source account must wait until the first transaction commits or rolls back, then it validates against the new committed balance.

This prevents overdrafts because the insufficient-funds check runs after the debit account row is locked. The balance used for validation is the balance that this transaction has exclusive right to update until commit.

## Deterministic Lock Ordering

When a transfer touches two different accounts, lock both rows in ascending account id order.

The service must keep the business role mapping separate from lock order:

- `sourceAccountId` remains the account to debit.
- `destinationAccountId` remains the account to credit.
- Lock order is only a deadlock-avoidance implementation detail.

This matters for cross transfers such as `A -> B` and `B -> A`. If both requests lock accounts in request order, they can deadlock. If both requests lock accounts in the same sorted order, one request waits for the first account and the other proceeds, which avoids the circular wait.

## Repository Shape

Add locked account loading to `AccountRepository`.

The Spring Data JPA shape should be equivalent to:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
@Query("select a from AccountEntity a where a.id = :id")
Optional<AccountEntity> findByIdForUpdate(UUID id);
```

For multi-account transfer loading, the use case should sort ids first and call the locked finder in sorted order. A bulk `where id in (...)` query is acceptable only if the implementation can guarantee deterministic lock acquisition order for the target database.

## Lock Timeout Behavior

Lock waits must be bounded. A request should not wait indefinitely behind another transaction.

Expected behavior when lock acquisition times out:

- Roll back the current transaction.
- Do not create or update transfer rows.
- Do not create ledger transactions, journal entries, or postings.
- Do not update account balances.
- Do not write idempotency, audit, or outbox rows.
- Return a structured `409 Conflict` response.
- Mark the error as retryable in the application error message or metadata when the shared error model supports it.
- Log the failure with correlation id and account ids, without sensitive payload.

Implementation should translate JPA/Spring lock exceptions such as `PessimisticLockException`, `LockTimeoutException`, `CannotAcquireLockException`, or database lock timeout exceptions into the shared conflict error model.

Recommended API semantics:

```text
HTTP 409 Conflict
code: BUSINESS.CONCURRENT_ACCOUNT_UPDATE
message: Account is currently being updated. Retry the request.
```

The exact code can follow the project's `ApiErrorCode` naming style when the implementation adds it.

## Optimistic Conflict Behavior

The `accounts` table already has a `version` column and `AccountEntity` is annotated with `@Version`. That remains useful as a secondary guard for unexpected concurrent updates, normal non-financial entity edits, and workflows that do not take pessimistic locks.

Expected behavior when an optimistic conflict occurs:

- Roll back the current transaction.
- Do not leave partial transfer, ledger, posting, idempotency, audit, or outbox writes.
- Return a structured `409 Conflict` response.
- Treat the failure as retryable for callers that can safely retry with the same idempotency key.

Implementation should translate JPA/Spring optimistic exceptions such as `OptimisticLockException` or `ObjectOptimisticLockingFailureException` into the shared conflict error model.

Recommended API semantics:

```text
HTTP 409 Conflict
code: BUSINESS.CONCURRENT_ACCOUNT_UPDATE
message: Account was updated concurrently. Retry the request.
```

The transfer path should prefer pessimistic locks over optimistic retry loops because money movement is easier to reason about when balance validation happens after exclusive row ownership is acquired. If optimistic retry is introduced later, retry only before any external side effect and keep idempotency replay behavior stable.

## Idempotency Under Concurrency

Idempotency protects client retries; account locks protect balances. Both are required.

For concurrent duplicate idempotency requests:

1. The first transaction that completes transfer posting writes the idempotency record.
2. Another transaction with the same `(operation_scope, idempotency_key)` may race and hit the unique constraint.
3. The loser must re-read the existing idempotency record.
4. If the request hash matches, return the stored response.
5. If the request hash differs, return an idempotency conflict.

This avoids double posting while still giving retrying clients deterministic responses.

## Non-Goals

- Do not switch the whole application to `SERIALIZABLE`.
- Do not rely only on Java synchronization; the correctness boundary is the database transaction.
- Do not update cached balances outside the ledger posting transaction.
- Do not publish external messages before the database transaction commits.

## Acceptance Notes

The implementation is complete when concurrent tests prove:

- Same-source transfers cannot overdraft.
- Same-account cross transfers do not deadlock.
- Duplicate idempotency requests create one transfer result.
- Lock timeout and optimistic conflict responses are structured.
- Failed concurrent requests do not leave partial financial, audit, outbox, or idempotency writes.
