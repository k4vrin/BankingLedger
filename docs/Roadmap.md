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
- [ ] Create base package structure by feature:
  - [ ] `account`
  - [ ] `ledger`
  - [ ] `transfer`
  - [ ] `reconciliation`
  - [ ] `audit`
  - [ ] `outbox`
  - [ ] `security`
  - [ ] `shared`
- [ ] Add global API error response model.
- [ ] Add global exception handler.
- [ ] Add correlation ID request filter.
- [ ] Add basic logging configuration for correlation IDs.

### Acceptance Criteria

- [ ] `docker compose -f compose.dev.yaml up -d` starts Oracle, Kafka, and CloudBeaver.
- [ ] CloudBeaver can connect to Oracle using the `ledger_dev` user.
- [ ] `./mvnw -DskipTests validate` succeeds.
- [ ] `./mvnw test` succeeds.
- [ ] The API starts locally with the `dev` profile.
- [ ] `/actuator/health` returns `UP`.
- [ ] Project setup is documented in `README.md`.

## Phase 1: Database Schema And Core Domain Model

Goal: Design the first version of the ledger database and model the most important financial concepts.

### Steps

- [ ] Create Flyway migration folder:
  - [ ] `src/main/resources/db/migration`
- [ ] Add initial schema migration:
  - [ ] `customers`
  - [ ] `accounts`
  - [ ] `ledger_transactions`
  - [ ] `journal_entries`
  - [ ] `postings`
  - [ ] `transfer_requests`
  - [ ] `audit_events`
  - [ ] `idempotency_records`
  - [ ] `outbox_events`
- [ ] Add required constraints:
  - [ ] Unique account number.
  - [ ] Unique idempotency key per operation scope.
  - [ ] Foreign keys between postings, journal entries, transactions, and accounts.
  - [ ] Positive amount checks.
  - [ ] Valid debit/credit direction checks.
  - [ ] Version column for optimistic locking.
- [ ] Add required indexes:
  - [ ] Account transaction history by account and posted time.
  - [ ] Transaction lookup by external reference.
  - [ ] Idempotency key lookup.
  - [ ] Outbox status and retry lookup.
- [ ] Create Java enums:
  - [ ] `AccountType`
  - [ ] `AccountStatus`
  - [ ] `TransactionStatus`
  - [ ] `PostingDirection`
  - [ ] `OutboxStatus`
- [ ] Create money representation:
  - [ ] Use integer minor units or strict `BigDecimal` scale.
  - [ ] Add validation helpers.
  - [ ] Add unit tests for invalid money values.
- [ ] Create JPA entities for the initial schema.
- [ ] Create Spring Data repositories.

### Acceptance Criteria

- [ ] Flyway creates the schema from a clean Oracle database.
- [ ] JPA starts with `ddl-auto=validate` and no schema mismatch.
- [ ] Database constraints reject invalid postings and negative amounts.
- [ ] Unit tests verify money validation rules.
- [ ] The schema supports double-entry postings without exposing mutable ledger records.

## Phase 2: Account Service

Goal: Implement account creation, account lookup, balance views, and account status validation.

### Steps

- [ ] Add account DTOs:
  - [ ] Create account request.
  - [ ] Account response.
  - [ ] Balance response.
  - [ ] Account transaction summary response.
- [ ] Implement account creation use case.
- [ ] Implement account lookup by ID and account number.
- [ ] Implement balance query endpoint.
- [ ] Implement account transaction history query endpoint.
- [ ] Add account status rules:
  - [ ] Active accounts can transact.
  - [ ] Frozen or closed accounts cannot debit.
  - [ ] Closed accounts cannot receive credits unless explicitly allowed.
- [ ] Add validation for currency and account type.
- [ ] Add audit event creation for account lifecycle operations.

### Acceptance Criteria

- [ ] `POST /api/v1/accounts` creates an account.
- [ ] `GET /api/v1/accounts/{accountId}` returns account details.
- [ ] `GET /api/v1/accounts/{accountId}/balance` returns current balance.
- [ ] `GET /api/v1/accounts/{accountId}/transactions` returns paginated history.
- [ ] Invalid account creation requests return structured validation errors.
- [ ] Account lifecycle changes create audit events.
- [ ] Account service tests cover status and validation rules.

## Phase 3: Ledger Posting Engine

Goal: Implement double-entry journal creation and enforce financial invariants before data is persisted.

### Steps

- [ ] Create ledger domain objects:
  - [ ] `LedgerTransaction`
  - [ ] `JournalEntry`
  - [ ] `Posting`
- [ ] Implement journal entry factory.
- [ ] Enforce balanced postings:
  - [ ] Total debit equals total credit.
  - [ ] Currency is consistent across postings.
  - [ ] At least two postings exist.
  - [ ] Amounts are positive.
- [ ] Implement ledger posting service.
- [ ] Add explicit transaction boundaries with `@Transactional`.
- [ ] Prevent direct updates to posted records.
- [ ] Add audit events for posted transactions.
- [ ] Add initial outbox event records for posted ledger transactions.

### Acceptance Criteria

- [ ] A valid journal entry can be posted.
- [ ] Unbalanced journal entries are rejected before persistence.
- [ ] Single-sided journal entries are rejected.
- [ ] Posted ledger records cannot be destructively changed by normal workflows.
- [ ] Posting creates journal entries, postings, audit events, and outbox records atomically.
- [ ] Rollback tests prove partial postings are not committed.

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

## Phase 7: Security And Authorization

Goal: Protect customer, teller, auditor, operations, and service workflows with role-based access.

### Steps

- [ ] Configure JWT resource server.
- [ ] Define roles:
  - [ ] `CUSTOMER`
  - [ ] `TELLER`
  - [ ] `AUDITOR`
  - [ ] `OPS_ADMIN`
  - [ ] `SERVICE`
- [ ] Add method-level authorization.
- [ ] Protect customer account endpoints.
- [ ] Protect reversal and adjustment endpoints.
- [ ] Protect reconciliation and ops endpoints.
- [ ] Add test JWT support for integration tests.
- [ ] Ensure sensitive data is not logged.

### Acceptance Criteria

- [ ] Unauthenticated requests are rejected.
- [ ] Customer users can only access permitted customer APIs.
- [ ] Auditors can query ledger and audit data.
- [ ] Ops admins can perform reversal and reconciliation workflows.
- [ ] Unauthorized role access returns `403`.
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

