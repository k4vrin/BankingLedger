---
type: project
stack: [java, core-java, spring-boot, spring-security, spring-data-jpa, hibernate, rest, oracle, plsql, transactions, acid, concurrency, redis, kafka, jms, docker, testcontainers, git, cicd]
repo:
status: planned
created: 2026-05-02
updated: 2026-05-02
tags: [project, java, banking, fintech, ledger]
---

# Mini Core Banking Ledger (Double-Entry Accounting + Transaction Engine)

## Goal
Build a compact but serious banking-style ledger that demonstrates Java backend skills for financial systems: double-entry accounting, ACID transactions, auditability, concurrency control, Oracle-oriented SQL/PL-SQL, secure APIs, reconciliation, and operational reporting.

This project is designed for banking, fintech, payment, and enterprise Java roles where correctness, traceability, and transactional integrity matter more than feature volume.

## Target job fit
- Java backend roles requiring Java Core, Spring Boot, Hibernate/JPA, REST, Oracle, PL-SQL, transaction management, ACID, and clean code.
- Banking or payment roles that value ISO-8583 awareness, audit trails, reconciliation, secure logging, and operational troubleshooting.
- Senior Java roles that expect domain modeling, concurrency reasoning, data consistency, tests, and reviewable architecture.

## Scope
- `account-service`: customer accounts, account status, balance views, and account limits.
- `ledger-service`: journal entries, postings, double-entry validation, transaction lifecycle, and immutable ledger records.
- `transfer-service`: internal transfers between accounts with idempotency and concurrency protection.
- `reconciliation-service`: settlement file/import simulation, mismatch detection, and repair workflow.
- `reporting-service` (optional): operational reports using SQL and PL-SQL-style stored procedures/functions.
- `ops-api`: protected endpoints for investigation, reversal requests, reconciliation results, and audit lookup.

## Core domain
### Main concepts
- `Customer`
- `Account`
- `LedgerTransaction`
- `JournalEntry`
- `Posting`
- `TransferRequest`
- `Reversal`
- `SettlementBatch`
- `ReconciliationResult`
- `AuditEvent`
- `IdempotencyRecord`

### Account types
- `CURRENT`
- `SAVINGS`
- `WALLET`
- `SUSPENSE`
- `FEE_INCOME`
- `CLEARING`

### Transaction statuses
- `PENDING`
- `POSTED`
- `REJECTED`
- `REVERSED`
- `FAILED`

## Project requirements
1. **Double-entry ledger**
   - Every financial transaction must create balanced debit and credit postings.
   - Total debit must equal total credit for each journal entry.
   - Ledger records must be immutable after posting.
   - Corrections must use reversals or adjustment entries, not destructive updates.

2. **ACID transaction handling**
   - Use explicit transaction boundaries for posting, reversal, and transfer flows.
   - Guarantee that either all postings are committed or none are committed.
   - Use database constraints to enforce uniqueness, referential integrity, and safe state transitions.
   - Document isolation-level decisions for concurrent transfers.

3. **Concurrency-safe balance updates**
   - Prevent lost updates when multiple transfers hit the same account.
   - Demonstrate optimistic locking and/or pessimistic row locks.
   - Reject overdrafts consistently under concurrent load.
   - Include tests that simulate concurrent transfer requests.

4. **Internal transfer API**
   - Implement account-to-account transfers.
   - Require idempotency keys for transfer creation.
   - Support duplicate request replay without double posting.
   - Validate account status, currency, limits, and available balance.

5. **Reversal and adjustment flows**
   - Support full reversal of a posted transaction.
   - Link reversal entries to the original transaction.
   - Prevent duplicate reversals.
   - Provide adjustment entries for operational corrections with audit reasons.

6. **Oracle-oriented database design**
   - Design normalized tables for accounts, journal entries, postings, batches, and audit events.
   - Include Oracle-compatible SQL where possible.
   - Add at least one PL-SQL-style report/query, such as daily trial balance or reconciliation summary.
   - Use migration scripts through Flyway or Liquibase.

7. **Auditability and traceability**
   - Record who initiated each operation, when, from which channel, and with what correlation ID.
   - Keep immutable audit events for financial operations.
   - Mask sensitive data in logs.
   - Provide query APIs for transaction investigation.

8. **Security**
   - Use Spring Security with role-based access.
   - Define roles such as `CUSTOMER`, `TELLER`, `AUDITOR`, `OPS_ADMIN`, and `SERVICE`.
   - Protect reversal, adjustment, reconciliation, and reporting APIs.
   - Add method-level authorization for sensitive operations.

9. **Messaging and integration**
   - Publish ledger events through Kafka or JMS:
     - `LedgerTransactionPosted`
     - `LedgerTransactionReversed`
     - `AccountBalanceChanged`
     - `ReconciliationMismatchFound`
   - Use an outbox table if events are published from financial transactions.
   - Include dead-letter handling and replay strategy.

10. **Testing and verification**
   - Unit-test domain invariants such as balanced postings and reversal rules.
   - Integration-test transaction rollback behavior.
   - Use Testcontainers for database-backed tests.
   - Add concurrent tests for account balance correctness.
   - Add API tests for idempotency, validation, authorization, and error responses.

## User stories
- `CB01` As a customer, I want to view my account balance and recent transactions.
- `CB02` As a customer, I want to transfer money to another account safely.
- `CB03` As the system, I want every posted transfer to produce balanced debit and credit postings.
- `CB04` As the system, I want duplicate transfer requests to return the original result without double posting.
- `CB05` As the system, I want concurrent transfers to preserve correct account balances.
- `CB06` As an operator, I want to reverse a transaction with a required reason and audit trail.
- `CB07` As an auditor, I want to query immutable ledger entries and verify daily balances.
- `CB08` As the reconciliation process, I want to compare internal ledger totals with an external settlement batch and flag mismatches.
- `CB09` As an engineer, I want logs, metrics, and correlation IDs for every financial workflow.

## API examples
### Customer/account APIs
- `GET /api/v1/accounts/{accountId}`
- `GET /api/v1/accounts/{accountId}/balance`
- `GET /api/v1/accounts/{accountId}/transactions?from=&to=&page=&size=`

### Transfer APIs
- `POST /api/v1/transfers`
- `GET /api/v1/transfers/{transferId}`
- `POST /api/v1/transfers/{transferId}/reverse`

### Operations/audit APIs
- `GET /api/v1/ops/ledger/transactions/{transactionId}`
- `GET /api/v1/ops/reconciliation/batches/{batchId}`
- `POST /api/v1/ops/reconciliation/batches`
- `GET /api/v1/audit/events?entityType=&entityId=&from=&to=`

## Database design expectations
### Required tables
- `customers`
- `accounts`
- `ledger_transactions`
- `journal_entries`
- `postings`
- `transfer_requests`
- `reversals`
- `settlement_batches`
- `settlement_items`
- `reconciliation_results`
- `audit_events`
- `idempotency_records`
- `outbox_events`

### Required constraints
- Unique account number.
- Unique idempotency key per operation scope.
- Unique reversal per original transaction.
- Foreign keys from postings to journal entries and accounts.
- Check constraints for debit/credit direction and positive amounts.
- Version column for optimistic locking where used.

### Required indexes
- account transaction history by account and posted time.
- transaction lookup by external reference.
- idempotency key lookup.
- reconciliation item lookup by settlement reference.
- outbox lookup by status and next retry time.

## PL-SQL / reporting ideas
- Daily trial balance by currency.
- Account statement summary by period.
- Reconciliation mismatch report.
- Suspense account aging report.
- Top failed transfer reasons by day.

## Design patterns to demonstrate
- Domain model with strong invariants for journal entries and postings.
- Repository pattern through Spring Data JPA.
- Factory method for creating ledger transactions.
- Strategy pattern for fee calculation or transfer validation.
- Specification pattern for transaction search filters.
- Outbox pattern for reliable financial event publishing.
- Command pattern for reversal and adjustment operations.

## Interview talking points
- Why financial systems use double-entry accounting.
- Why posted ledger entries should be immutable.
- How reversals differ from deleting or editing records.
- How ACID transactions protect multi-posting financial operations.
- How isolation levels affect concurrent transfers.
- How optimistic and pessimistic locking differ.
- How idempotency prevents duplicate money movement.
- How to design database constraints so bugs fail safely.
- How to reconcile internal ledger state with external settlement data.
- How to build audit trails that are useful for compliance and debugging.

## Non-functional requirements
- Correctness over throughput for financial posting.
- Consistent money representation using integer minor units or `BigDecimal` with strict scale rules.
- No floating-point arithmetic for money.
- No direct mutation of posted financial records.
- Clear DTO/entity separation.
- No sensitive data in logs.
- Structured error codes for business rejections.
- Graceful shutdown for workers and consumers.
- Metrics for transaction volume, failed transfers, reversal count, reconciliation mismatch count, outbox lag, and API latency.

## Deliverables for CV/portfolio
- Architecture diagram.
- ERD diagram.
- README with local run steps.
- OpenAPI spec.
- Docker Compose setup.
- Migration scripts.
- PL-SQL/reporting examples.
- CI/CD pipeline.
- Test report and coverage summary.
- Concurrent transfer test results.
- Reconciliation example with mismatch report.
- Incident write-up: duplicate request, overdraft race condition, or failed reversal investigation.
- ADRs for:
  - double-entry model
  - money representation
  - transaction isolation
  - locking strategy
  - immutable ledger and reversal model
  - idempotency design
  - outbox/event publishing strategy

## Suggested implementation phases
1. Build account and ledger schema with migrations.
2. Implement money value object and ledger domain invariants.
3. Implement transfer posting with balanced debit/credit postings.
4. Add idempotency records and duplicate request handling.
5. Add reversal flow and audit trail.
6. Add concurrency-safe balance checks and tests.
7. Add reconciliation batch import and mismatch detection.
8. Add Spring Security roles and protected ops APIs.
9. Add outbox events with Kafka/JMS publishing.
10. Add Docker Compose, CI, documentation, reports, and ADRs.

