# Mini Core Banking Ledger Roadmap

This roadmap breaks the portfolio project into practical development phases. Each phase should leave the project in a working, reviewable state with clear acceptance criteria.

Use the checkboxes as the implementation tracker.

## Phase 0: Project Foundation

Goal: Prepare the repository, runtime configuration, local infrastructure, and basic project conventions.

### Steps

- [x] Create Spring Boot API project in `banking-ledger-api`.
- [x] Add Oracle JDBC and Flyway Oracle support.
- [x] Add Docker Compose files for development and production.
- [x] Add Oracle Database Free for local development.
- [x] Add CloudBeaver browser database manager.
- [x] Add Kafka for future ledger event publishing.
- [x] Add `dev` and `prod` Spring profiles.
- [x] Add environment examples and ignore files.
- [x] Add root README with setup instructions.
- [x] Create base package structure by feature:
    - [x] `account`
    - [x] `ledger`
    - [x] `transfer`
    - [x] `reconciliation`
    - [x] `audit`
    - [x] `outbox`
    - [x] `security`
    - [x] `shared`
- [x] Add global API error response model.
- [x] Add global exception handler.
- [x] Add correlation ID request filter.
- [x] Add basic logging configuration for correlation IDs.

### Acceptance Criteria

- [x] `docker compose -f compose.dev.yaml up -d` starts Oracle, Kafka, and CloudBeaver.
- [x] CloudBeaver can connect to Oracle using the `ledger_dev` user.
- [x] `./mvnw -DskipTests validate` succeeds.
- [x] `./mvnw test` succeeds.
- [x] The API starts locally with the `dev` profile.
- [x] `/actuator/health` returns `UP`.
- [x] Project setup is documented in `README.md`.

## Phase 1: Database Schema And Core Domain Model

Goal: Design the first version of the ledger database and model the most important financial concepts.

### Steps

- [x] Create Flyway migration folder:
    - [x] `src/main/resources/db/migration`
- [x] Add initial schema migration:
    - [x] `customers`
    - [x] `accounts`
    - [x] `ledger_transactions`
    - [x] `journal_entries`
    - [x] `postings`
    - [x] `transfer_requests`
    - [x] `audit_events`
    - [x] `idempotency_records`
    - [x] `outbox_events`
- [x] Add required constraints:
    - [x] Unique account number.
    - [x] Unique idempotency key per operation scope.
    - [x] Foreign keys between postings, journal entries, transactions, and accounts.
    - [x] Positive amount checks.
    - [x] Valid debit/credit direction checks.
    - [x] Version column for optimistic locking.
- [x] Add required indexes:
    - [x] Account transaction history by account and posted time.
    - [x] Transaction lookup by external reference.
    - [x] Idempotency key lookup.
    - [x] Outbox status and retry lookup.
- [x] Create Java enums:
    - [x] `AccountType`
    - [x] `AccountStatus`
    - [x] `TransactionStatus`
    - [x] `PostingDirection`
    - [x] `OutboxStatus`
- [x] Create money representation:
    - [x] Add a dedicated package for value objects:
        - [x] `src/main/java/dev/kavrin/banking_ledger/shared/money`
        - [x] `src/test/java/dev/kavrin/banking_ledger/shared/money`
    - [x] Create a `CurrencyCode` value object:
        - [x] Store the ISO-style code as a `String`.
        - [x] Normalize input by trimming whitespace and converting to uppercase.
        - [x] Reject null, blank, non-3-letter, and non-ASCII alphabetic values.
        - [x] Add a `value()` accessor for persistence and DTO mapping.
    - [x] Create a `Money` value object:
        - [x] Store `amountMinor` as `long`.
        - [x] Store `currencyCode` as `CurrencyCode`.
        - [x] Add factory methods such as `ofMinor(long amountMinor, CurrencyCode currencyCode)` and `zero(CurrencyCode currencyCode)`.
        - [x] Reject null currency codes.
        - [x] Reject negative amounts for normal money values.
        - [x] Add `isZero()` and `isPositive()` helpers.
    - [x] Add money arithmetic helpers:
        - [x] `plus(Money other)` for same-currency addition.
        - [x] `minus(Money other)` for same-currency subtraction.
        - [x] `isGreaterThanOrEqualTo(Money other)` for balance checks.
        - [x] Reject arithmetic between different currencies.
        - [x] Reject subtraction that would produce a negative result.
        - [x] Use `Math.addExact` and `Math.subtractExact` to fail on `long` overflow.
    - [x] Add persistence mapping guidance:
        - [x] Keep database columns as `amount_minor number(19, 0)` and `currency_code char(3)`.
        - [x] Map entities to primitive columns first; convert to/from `Money` at entity boundaries or factory methods.
        - [x] Do not use floating-point types for money.
    - [x] Add unit tests for valid values:
        - [x] Creates money from positive minor units.
        - [x] Creates zero money.
        - [x] Normalizes lowercase currency input.
        - [x] Adds same-currency money.
        - [x] Subtracts same-currency money.
        - [x] Compares same-currency money for sufficient balance.
    - [x] Add unit tests for invalid values:
        - [x] Rejects null currency.
        - [x] Rejects blank currency.
        - [x] Rejects currency codes with fewer or more than 3 characters.
        - [x] Rejects currency codes with digits or symbols.
        - [x] Rejects negative amounts.
        - [x] Rejects cross-currency addition, subtraction, and comparison.
        - [x] Rejects subtraction below zero.
        - [x] Rejects arithmetic overflow.
- [x] Create JPA entities for the initial schema.
- [x] Create Spring Data repositories.

### Acceptance Criteria

- [x] Flyway creates the schema from a clean Oracle database.
- [x] JPA starts with `ddl-auto=validate` and no schema mismatch.
- [x] Database constraints reject invalid postings and negative amounts.
- [x] Unit tests verify money validation rules.

## Phase 2: Account Service

Goal: Implement account creation, account lookup, balance views, and account status validation.

### Steps

- [x] Prepare account package structure:
    - [x] `account/api` for REST controllers.
    - [x] `account/api/dto` for request and response DTOs.
    - [x] `account/application` for account use-case orchestration.
    - [x] `account/application/command` for write-side command objects.
    - [x] `account/application/query` for read-side query objects.
    - [x] `account/application/service` for application services.
    - [x] `account/domain/policy` for account business rules.
- [x] Add account DTOs:
    - [x] Create `CreateAccountRequest`.
    - [x] Add bean validation annotations:
        - [x] `customerId` is required.
        - [x] `accountNumber` is required and at most 34 characters.
        - [x] `accountType` is required.
        - [x] `currencyCode` is required and exactly 3 uppercase letters.
    - [x] Create `AccountResponse`.
    - [x] Include `id`, `customerId`, `accountNumber`, `accountType`, `accountCategory`, `status`, `currencyCode`, balances, and timestamps.
    - [x] Create `BalanceResponse`.
    - [x] Include account id, currency code, available balance minor, and ledger balance minor.
    - [x] Create `AccountTransactionSummaryResponse`.
    - [x] Include posting id, ledger transaction id, direction, amount minor, currency code, description, and posted time.
- [x] Add account command/query objects:
    - [x] Create `CreateAccountCommand`.
    - [x] Create `GetAccountByIdQuery`.
    - [x] Create `GetAccountByNumberQuery`.
    - [x] Create `GetAccountBalanceQuery`.
    - [x] Create `GetAccountTransactionsQuery`.
- [x] Add repository lookup methods:
    - [x] `existsByAccountNumber`.
    - [x] `findByAccountNumber`.
    - [x] Account transaction history query through postings by account id and posted time.
- [x] Implement account creation use case:
    - [x] Load the owning customer.
    - [x] Reject duplicate account numbers.
    - [x] Validate account type and account category.
    - [x] Validate and normalize currency code using `CurrencyCode`.
    - [x] Create accounts with `ACTIVE` status.
    - [x] Initialize available and ledger balances to zero.
    - [x] Save the account inside a transaction.
    - [x] Return `AccountResponse`.
- [x] Implement account lookup use cases:
    - [x] Lookup by account id.
    - [x] Lookup by account number.
    - [x] Return not-found errors through the shared exception model.
- [x] Implement balance query use case:
    - [x] Load account by id.
    - [x] Return cached available and ledger balances.
    - [x] Preserve minor-unit money representation in the response.
- [x] Implement account transaction history use case:
    - [x] Query postings for the account ordered by `posted_at` descending.
    - [x] Support pagination with `Pageable`.
    - [x] Support optional `from` and `to` posted-time filters.
    - [x] Return transaction summary DTOs.
- [x] Add account status rules:
    - [x] Create an account status policy class.
    - [x] Active accounts can debit and credit.
    - [x] Frozen accounts cannot debit.
    - [x] Frozen accounts can receive credits unless the policy explicitly forbids it.
    - [x] Closed accounts cannot debit.
    - [x] Closed accounts cannot receive credits unless explicitly allowed by a future operational workflow.
- [x] Add validation for account creation:
    - [x] Reject customer accounts with internal-only account types if that rule is selected.
    - [x] Reject invalid currency codes before persistence.
    - [x] Reject blank account numbers.
    - [x] Reject account numbers longer than the schema limit.
- [x] Add audit event creation for account lifecycle operations:
    - [x] Write an audit event after account creation.
    - [x] Include entity type `ACCOUNT`.
    - [x] Include account id as entity id.
    - [x] Include actor type and correlation id when available.
    - [x] Store audit event in the same transaction as account creation.
- [x] Add account REST controller:
    - [x] `POST /api/v1/accounts`.
    - [x] `GET /api/v1/accounts/{accountId}`.
    - [x] `GET /api/v1/accounts/by-number/{accountNumber}`.
    - [x] `GET /api/v1/accounts/{accountId}/balance`.
    - [x] `GET /api/v1/accounts/{accountId}/transactions`.
- [x] Add account service tests:
    - [x] Successful account creation.
    - [x] Duplicate account number is rejected.
    - [x] Missing customer is rejected.
    - [x] Invalid currency is rejected.
    - [x] Lookup by id returns account.
    - [x] Lookup by account number returns account.
    - [x] Missing account returns not-found error.
    - [x] Balance query returns cached balances.
    - [x] Status policy allows active debit and credit.
    - [x] Status policy rejects frozen/closed debits.
- [x] Add account API tests:
    - [x] Create account returns `201`.
    - [x] Invalid request returns structured validation error.
    - [x] Get account returns account response.
    - [x] Get balance returns balance response.
    - [x] Get transaction history returns a paginated response.
- [x] Add account persistence tests:
    - [x] Account number uniqueness is enforced.
    - [x] Account currency check rejects invalid currency values.
    - [x] Account balance checks reject negative cached balances.

### Acceptance Criteria

- [x] `POST /api/v1/accounts` creates an account.
- [x] Duplicate account creation does not create a second account.
- [x] `GET /api/v1/accounts/{accountId}` returns account details.
- [x] `GET /api/v1/accounts/by-number/{accountNumber}` returns account details.
- [x] `GET /api/v1/accounts/{accountId}/balance` returns current balance.
- [x] `GET /api/v1/accounts/{accountId}/transactions` returns paginated history.
- [x] Invalid account creation requests return structured validation errors.
- [x] Account lifecycle changes create audit events.
- [x] Account service tests cover status and validation rules.
- [x] Account API tests cover success and validation failure paths.
- [x] Account persistence tests prove schema constraints reject invalid account rows.

## Phase 3: Ledger Posting Engine

Goal: Implement double-entry journal creation and enforce financial invariants before data is persisted.

### Steps

- [ ] Prepare package structure:
    - [ ] Add `ledger.application.command` for posting input commands.
    - [ ] Add `ledger.application.service` for ledger posting use cases.
    - [ ] Add `ledger.domain.factory` for journal and posting construction.
    - [ ] Add `ledger.domain.policy` for double-entry validation rules.
    - [ ] Add package-level tests under `ledger.domain` and `ledger.application` as implementation begins.
- [ ] Create immutable ledger command objects:
    - [ ] Add `PostLedgerTransactionCommand`.
    - [ ] Add `PostingLineCommand`.
    - [ ] Include `externalReference`, `transactionType`, `currencyCode`, `amountMinor`, `description`, `actorType`, and `correlationId`.
    - [ ] Include account id, posting direction, amount, and currency on each posting line.
    - [ ] Validate required command fields before domain construction.
- [ ] Create ledger domain objects:
    - [ ] Add `LedgerTransaction`.
    - [ ] Add `JournalEntry`.
    - [ ] Add `Posting`.
    - [ ] Keep domain objects persistence-free.
    - [ ] Represent money using integer minor units and explicit currency code.
    - [ ] Expose read-only posting collections from `JournalEntry`.
- [ ] Implement posting validation policy:
    - [ ] Reject fewer than two postings.
    - [ ] Reject entries without at least one debit and one credit.
    - [ ] Reject zero or negative posting amounts.
    - [ ] Reject mixed posting currencies.
    - [ ] Reject posting currency that differs from the transaction currency.
    - [ ] Reject total debit that differs from total credit.
    - [ ] Reject a journal total that differs from the transaction amount.
- [ ] Implement journal entry factory:
    - [ ] Accept `PostLedgerTransactionCommand`.
    - [ ] Normalize currency codes through `CurrencyCode`.
    - [ ] Build one `LedgerTransaction`.
    - [ ] Build one `JournalEntry`.
    - [ ] Build all `Posting` lines.
    - [ ] Run posting validation before returning the journal entry.
    - [ ] Return a domain object graph without touching repositories.
- [ ] Map domain objects to persistence entities:
    - [ ] Convert `LedgerTransaction` to `LedgerTransactionEntity`.
    - [ ] Convert `JournalEntry` to `JournalEntryEntity`.
    - [ ] Convert each `Posting` to `PostingEntity`.
    - [ ] Resolve `AccountEntity` references for posting accounts.
    - [ ] Set `postedAt` once and reuse it across transaction, journal entry, and postings.
- [ ] Implement `PostLedgerTransactionUseCase`:
    - [ ] Annotate the public handler with `@Transactional`.
    - [ ] Validate duplicate `externalReference` before insert when present.
    - [ ] Load all posting accounts.
    - [ ] Reject missing posting accounts.
    - [ ] Reject posting accounts whose currency differs from the posting currency.
    - [ ] Save the ledger transaction.
    - [ ] Save the journal entry.
    - [ ] Save all postings.
    - [ ] Flush before creating side effects that depend on generated ids.
- [ ] Update cached account balances inside the same transaction:
    - [ ] Debit customer asset accounts by reducing available and ledger balances.
    - [ ] Credit customer asset accounts by increasing available and ledger balances.
    - [ ] Document any internal account balance behavior that is deferred to later phases.
    - [ ] Keep balance update logic isolated so Phase 4 transfer validation can reuse it.
- [ ] Prevent destructive updates to posted ledger records:
    - [ ] Remove normal workflow paths that update posted `LedgerTransactionEntity` fields after posting.
    - [ ] Remove normal workflow paths that update `JournalEntryEntity` after posting.
    - [ ] Remove normal workflow paths that update `PostingEntity` after posting.
    - [ ] Add comments or method names that make append-only intent clear at repository/service boundaries.
    - [ ] Leave reversals and adjustments as future append-only workflows.
- [ ] Add audit event creation:
    - [ ] Create `LEDGER_TRANSACTION_POSTED` audit event.
    - [ ] Use `LEDGER_TRANSACTION` as the audited entity type.
    - [ ] Store the posted transaction id as the audited entity id.
    - [ ] Include actor type and correlation id from the command.
    - [ ] Keep audit save inside the posting transaction.
- [ ] Add outbox event creation:
    - [ ] Create `LedgerTransactionPosted` outbox event.
    - [ ] Use the ledger transaction id as the aggregate id.
    - [ ] Include transaction id, currency, amount, transaction type, and posted timestamp in the payload.
    - [ ] Save the outbox event with `PENDING` status inside the posting transaction.
- [ ] Add focused domain tests:
    - [ ] Valid debit and credit postings are accepted.
    - [ ] Single-sided posting list is rejected.
    - [ ] Debit-only posting list is rejected.
    - [ ] Credit-only posting list is rejected.
    - [ ] Unbalanced totals are rejected.
    - [ ] Mixed currencies are rejected.
    - [ ] Zero amount is rejected.
    - [ ] Negative amount is rejected.
    - [ ] Transaction amount mismatch is rejected.
- [ ] Add service integration tests:
    - [ ] Valid command persists one ledger transaction.
    - [ ] Valid command persists one journal entry.
    - [ ] Valid command persists all postings.
    - [ ] Valid command creates one audit event.
    - [ ] Valid command creates one pending outbox event.
    - [ ] Duplicate external reference is rejected.
    - [ ] Missing posting account is rejected.
    - [ ] Account currency mismatch is rejected.
    - [ ] Failure after transaction save rolls back journal entries and postings.
    - [ ] Failure after journal entry save rolls back ledger transaction and postings.
    - [ ] Failure after posting save rolls back audit and outbox records.
- [ ] Add persistence guard tests:
    - [ ] Posting amount database check rejects non-positive values.
    - [ ] Journal debit and credit total check rejects unbalanced totals.
    - [ ] Ledger transaction status and posted timestamp checks reject invalid combinations.
    - [ ] Composite currency foreign keys reject mismatched ledger, journal, posting, and account currencies.

### Acceptance Criteria

- [ ] A valid journal entry can be posted.
- [ ] Unbalanced journal entries are rejected before persistence.
- [ ] Single-sided journal entries are rejected.
- [ ] Mixed-currency journal entries are rejected before persistence.
- [ ] Zero or negative posting amounts are rejected before persistence.
- [ ] Posting account currency mismatches are rejected before persistence.
- [ ] Posted ledger records cannot be destructively changed by normal workflows.
- [ ] Posting creates journal entries, postings, audit events, and outbox records atomically.
- [ ] Rollback tests prove partial postings are not committed.
- [ ] Domain tests cover all double-entry invariants.
- [ ] Service integration tests cover success, duplicate reference, missing account, currency mismatch, and rollback paths.
- [ ] Persistence tests prove schema constraints reject invalid ledger rows.

## Phase 4: Internal Transfer API

Goal: Implement safe account-to-account transfers using the ledger posting engine.

### Steps

- [ ] Add transfer request and response DTOs.
- [ ] Add transfer validation:
    - [ ] Source account exists.
    - [ ] Destination account exists.
    - [ ] Source and destination are different.
    - [ ] Accounts are active.
    - [ ] Currencies match.
    - [ ] Amount is valid.
    - [ ] Source account has sufficient available balance.
- [ ] Require an idempotency key for transfer creation.
- [ ] Store transfer request records.
- [ ] Implement duplicate request replay.
- [ ] Create balanced debit and credit postings for each transfer.
- [ ] Return transfer status and ledger transaction reference.
- [ ] Add transfer lookup endpoint.

### Acceptance Criteria

- [ ] `POST /api/v1/transfers` posts a valid transfer.
- [ ] The transfer creates exactly one debit posting and one credit posting.
- [ ] Duplicate requests with the same idempotency key return the original result.
- [ ] Duplicate requests do not create duplicate ledger postings.
- [ ] Overdraft attempts are rejected consistently.
- [ ] Transfer validation failures return structured business errors.
- [ ] Integration tests cover successful transfer, duplicate replay, and validation failures.

## Phase 5: Concurrency And Transaction Isolation

Goal: Prove that concurrent transfers preserve correct balances and do not allow overdrafts.

### Steps

- [ ] Choose and document locking strategy:
    - [ ] Optimistic locking with version columns.
    - [ ] Pessimistic row locks for debit balance checks.
    - [ ] Or a clearly justified hybrid.
- [ ] Add repository methods for locked account loading.
- [ ] Add retry or conflict handling for concurrent updates.
- [ ] Add concurrent transfer test utilities.
- [ ] Simulate multiple transfers from the same source account.
- [ ] Simulate duplicate idempotency requests arriving concurrently.
- [ ] Add ADR for transaction isolation and locking strategy.

### Acceptance Criteria

- [ ] Concurrent transfer tests pass repeatedly.
- [ ] The final account balance is correct after concurrent activity.
- [ ] Concurrent overdraft attempts do not produce negative balances.
- [ ] Duplicate concurrent requests create one transfer result.
- [ ] Locking and isolation choices are documented in an ADR.

## Phase 6: Reversal And Adjustment Flows

Goal: Support operational correction without mutating posted financial records.

### Steps

- [ ] Add reversal schema:
    - [ ] `reversals`
    - [ ] Unique reversal per original transaction.
    - [ ] Link reversal transaction to original transaction.
- [ ] Add reversal request and response DTOs.
- [ ] Implement full transaction reversal.
- [ ] Require reversal reason.
- [ ] Require authorized role for reversal.
- [ ] Prevent duplicate reversals.
- [ ] Add adjustment entry workflow for operational corrections.
- [ ] Add audit events for reversals and adjustments.
- [ ] Add outbox events for reversal completion.

### Acceptance Criteria

- [ ] `POST /api/v1/transfers/{transferId}/reverse` reverses a posted transfer.
- [ ] Reversal creates equal and opposite postings.
- [ ] Original ledger records remain unchanged.
- [ ] Duplicate reversal attempts are rejected.
- [ ] Reversal requires a reason.
- [ ] Unauthorized users cannot reverse transactions.
- [ ] Reversal and adjustment tests cover success and failure paths.

## Phase 7: Authentication, Authorization, And API Security

Goal: Add a clear authentication model and protect customer, teller, auditor, operations, and service workflows with role-based access.

### Steps

- [ ] Choose authentication strategy for the portfolio version:
    - [ ] Use JWT bearer tokens for API authentication.
    - [ ] Use local signed JWTs for development and tests.
    - [ ] Keep the design compatible with a future external identity provider.
- [ ] Add security configuration:
    - [ ] Stateless session policy.
    - [ ] CSRF disabled for stateless REST APIs.
    - [ ] CORS configuration for future frontend access.
    - [ ] Public endpoints for health and API docs.
    - [ ] Protected endpoints for business APIs.
- [ ] Add development authentication support:
    - [ ] Create a local token issuing endpoint or test-only token helper.
    - [ ] Add sample users for development.
    - [ ] Add sample JWT claims for user ID, roles, and customer ID.
- [ ] Configure JWT resource server.
- [ ] Map JWT claims to Spring Security authorities.
- [ ] Define roles:
    - [ ] `CUSTOMER`
    - [ ] `TELLER`
    - [ ] `AUDITOR`
    - [ ] `OPS_ADMIN`
    - [ ] `SERVICE`
- [ ] Define permission rules:
    - [ ] Customers can view only their own accounts and transactions.
    - [ ] Tellers can create and manage customer accounts.
    - [ ] Auditors can read ledger, reports, and audit events.
    - [ ] Ops admins can reverse transactions and run reconciliation workflows.
    - [ ] Service clients can publish or consume internal integration workflows.
- [ ] Add method-level authorization.
- [ ] Protect customer account endpoints.
- [ ] Protect reversal and adjustment endpoints.
- [ ] Protect reconciliation and ops endpoints.
- [ ] Add ownership checks for customer-scoped resources.
- [ ] Add structured authentication and authorization error responses.
- [ ] Add test JWT support for integration tests.
- [ ] Ensure sensitive data is not logged.
- [ ] Add ADR for authentication and authorization design.

### Acceptance Criteria

- [ ] Unauthenticated requests are rejected.
- [ ] Invalid or expired JWTs are rejected.
- [ ] Valid JWTs are converted into the correct authenticated principal.
- [ ] Customer users can only access permitted customer APIs.
- [ ] Customer users cannot access another customer's account data.
- [ ] Tellers can perform teller workflows but cannot perform auditor-only or ops-admin-only actions.
- [ ] Auditors can query ledger and audit data.
- [ ] Ops admins can perform reversal and reconciliation workflows.
- [ ] Service role access is limited to internal service endpoints.
- [ ] Unauthorized role access returns `403`.
- [ ] Missing or invalid credentials return `401`.
- [ ] Security tests cover authentication and authorization rules.

## Phase 8: Audit Trail And Investigation APIs

Goal: Provide traceability for financial operations and operational troubleshooting.

### Steps

- [ ] Add audit event writer service.
- [ ] Capture actor, role, channel, correlation ID, timestamp, entity type, and entity ID.
- [ ] Add audit query filters.
- [ ] Add ops ledger transaction lookup endpoint.
- [ ] Add audit event query endpoint.
- [ ] Add structured error codes for business rejections.
- [ ] Add correlation ID to logs and responses.

### Acceptance Criteria

- [ ] Every financial operation creates an audit event.
- [ ] Audit events are immutable through application workflows.
- [ ] `GET /api/v1/ops/ledger/transactions/{transactionId}` returns investigation details.
- [ ] `GET /api/v1/audit/events` supports filtering.
- [ ] Correlation IDs appear in API responses and logs.
- [ ] Sensitive data is not written to logs or audit payloads.

## Phase 9: Outbox And Kafka Publishing

Goal: Publish financial events reliably without losing consistency between database commits and Kafka messages.

### Steps

- [ ] Finalize `outbox_events` schema.
- [ ] Write outbox records inside financial transactions.
- [ ] Implement outbox publisher worker.
- [ ] Publish events:
    - [ ] `LedgerTransactionPosted`
    - [ ] `LedgerTransactionReversed`
    - [ ] `AccountBalanceChanged`
    - [ ] `ReconciliationMismatchFound`
- [ ] Add retry handling.
- [ ] Add dead-letter handling strategy.
- [ ] Add replay strategy.
- [ ] Add metrics for outbox lag and failures.
- [ ] Add ADR for outbox/event publishing strategy.

### Acceptance Criteria

- [ ] Financial transaction and outbox record commit atomically.
- [ ] Kafka publishing marks outbox records as published.
- [ ] Failed publish attempts are retried.
- [ ] Poison messages are handled according to the dead-letter strategy.
- [ ] Tests prove event records are not lost during rollback.
- [ ] Outbox behavior is documented.

## Phase 10: Reconciliation

Goal: Simulate settlement batch import, compare external settlement data with internal ledger state, and report mismatches.

### Steps

- [ ] Add reconciliation schema:
    - [ ] `settlement_batches`
    - [ ] `settlement_items`
    - [ ] `reconciliation_results`
- [ ] Add settlement batch import DTOs.
- [ ] Implement settlement batch creation endpoint.
- [ ] Match settlement items to ledger transactions.
- [ ] Detect mismatches:
    - [ ] Missing internal transaction.
    - [ ] Missing external settlement item.
    - [ ] Amount mismatch.
    - [ ] Currency mismatch.
    - [ ] Status mismatch.
- [ ] Add reconciliation result query endpoint.
- [ ] Add audit events for reconciliation operations.
- [ ] Publish mismatch events through outbox.

### Acceptance Criteria

- [ ] `POST /api/v1/ops/reconciliation/batches` imports a settlement batch.
- [ ] `GET /api/v1/ops/reconciliation/batches/{batchId}` returns reconciliation results.
- [ ] Mismatches are detected and categorized.
- [ ] Mismatch events are added to the outbox.
- [ ] Reconciliation actions are audited.
- [ ] Tests cover matched, missing, and mismatched settlement items.

## Phase 11: Reporting And Oracle-Oriented SQL

Goal: Demonstrate Oracle-friendly reporting and operational SQL skills.

### Steps

- [ ] Add daily trial balance report.
- [ ] Add account statement summary report.
- [ ] Add reconciliation mismatch report.
- [ ] Add suspense account aging report.
- [ ] Add top failed transfer reasons report.
- [ ] Use Oracle-compatible SQL.
- [ ] Add at least one PL/SQL-style function or procedure script.
- [ ] Document how to run reports locally.

### Acceptance Criteria

- [ ] Reports return correct values for seeded test data.
- [ ] Report SQL is stored under a documented folder.
- [ ] At least one Oracle PL/SQL-style script is included.
- [ ] Reporting queries are covered by integration tests where practical.
- [ ] README or docs explain report purpose and execution.

## Phase 12: API Documentation And Developer Experience

Goal: Make the project easy to inspect, run, and review as a portfolio project.

### Steps

- [ ] Add OpenAPI support.
- [ ] Document all public endpoints.
- [ ] Add request and response examples.
- [ ] Add error response examples.
- [ ] Add seed data for local development.
- [ ] Add useful Makefile or task commands if desired.
- [ ] Add architecture diagram.
- [ ] Add ERD diagram.
- [ ] Add ADRs:
    - [ ] Double-entry model.
    - [ ] Money representation.
    - [ ] Transaction isolation.
    - [ ] Locking strategy.
    - [ ] Immutable ledger and reversal model.
    - [ ] Idempotency design.
    - [ ] Outbox/event publishing strategy.

### Acceptance Criteria

- [ ] A reviewer can run the project from README instructions.
- [ ] API documentation is available locally.
- [ ] Example requests demonstrate the core flows.
- [ ] Architecture and ERD diagrams exist in `docs/`.
- [ ] ADRs explain the most important technical decisions.

## Phase 13: CI/CD And Quality Gates

Goal: Add automated verification so the project looks production-oriented.

### Steps

- [ ] Add GitHub Actions workflow.
- [ ] Run Maven tests in CI.
- [ ] Run integration tests with Testcontainers.
- [ ] Build the API artifact.
- [ ] Build the Docker image.
- [ ] Add dependency vulnerability scanning if desired.
- [ ] Add test coverage reporting.
- [ ] Add linting or formatting checks if desired.

### Acceptance Criteria

- [ ] CI runs on pull requests and pushes.
- [ ] CI fails if tests fail.
- [ ] CI builds the API package.
- [ ] CI verifies Docker image build.
- [ ] Test coverage summary is available.
- [ ] README shows CI status badge if repository is public.

## Phase 14: Portfolio Polish

Goal: Turn the completed implementation into a strong resume and interview artifact.

### Steps

- [ ] Add final README overview with screenshots or diagrams.
- [ ] Add sample API flow:
    - [ ] Create customer/account.
    - [ ] Fund account.
    - [ ] Transfer money.
    - [ ] Replay duplicate transfer.
    - [ ] Reverse transfer.
    - [ ] Run reconciliation.
    - [ ] Query audit trail.
- [ ] Add incident write-up:
    - [ ] Duplicate request investigation.
    - [ ] Overdraft race condition.
    - [ ] Failed reversal investigation.
- [ ] Add final test report and coverage summary.
- [ ] Add resume bullet points.
- [ ] Add project description for LinkedIn/GitHub.

### Acceptance Criteria

- [ ] Project can be explained in a 2-minute interview answer.
- [ ] README clearly states the banking/financial correctness goals.
- [ ] Demo flow proves the main business behavior.
- [ ] Incident write-up shows troubleshooting and systems thinking.
- [ ] Resume bullets highlight Java, Spring Boot, Oracle, transactions, concurrency, security, Kafka, Docker, and testing.

## Suggested Build Order

Follow this order unless a dependency forces a small adjustment:

1. Foundation
2. Schema and domain model
3. Account service
4. Ledger posting engine
5. Transfer API
6. Concurrency tests
7. Reversal flow
8. Security
9. Audit APIs
10. Outbox and Kafka
11. Reconciliation
12. Reporting
13. API documentation
14. CI/CD
15. Portfolio polish

## Definition Of Done For Each Phase

- [ ] Code is implemented.
- [ ] Tests are added for meaningful behavior.
- [ ] `./mvnw test` passes.
- [ ] The application starts locally.
- [ ] Relevant README or docs are updated.
- [ ] No secrets are committed.
- [ ] The implementation follows the banking correctness rules in `docs/Project.md`.
