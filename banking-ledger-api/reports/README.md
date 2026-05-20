# Reporting

This folder contains standalone operational and financial reporting assets for the Banking Ledger project.

The reports are intended to demonstrate:
- SQL reporting skills
- Financial data investigation
- Reconciliation analysis
- Operational monitoring
- Oracle-style PL/SQL familiarity
- Read-only reporting patterns for ledger systems

---

# Folder Structure

```text
reports/
├── sql/
│   ├── Daily trial balance reports
│   ├── Account statement queries
│   ├── Reconciliation summaries
│   ├── Suspense account investigations
│   ├── Transfer failure analytics
│   └── Operational investigation queries
│
├── psql/
│   ├── PL/SQL-style procedures
│   ├── Stored function examples
│   ├── Reporting cursors
│   ├── Batch reconciliation helpers
│   └── Financial aggregation scripts
│
└── README.md
```

---

# Reporting Principles

All reports in this directory follow these principles:

- Financial data is immutable after posting.
- Reports must never mutate ledger records.
- Corrections are represented using reversals or adjustments.
- Reports prioritize correctness and traceability over speed.
- Money values are stored in minor units.
- Floating-point arithmetic must never be used for money calculations.

---

# Required Schema Assumptions

These reports assume the following core tables already exist:

## Core Ledger Tables
- `accounts`
- `ledger_transactions`
- `journal_entries`
- `postings`

## Transfer & Reversal Tables
- `transfer_requests`
- `reversals`

## Reconciliation Tables
- `settlement_batches`
- `settlement_items`
- `reconciliation_results`

## Operational Tables
- `audit_events`
- `outbox_events`
- `idempotency_records`

---

# Expected Financial Model

The reporting scripts assume the system uses:

- Double-entry accounting
- Balanced debit and credit postings
- Immutable posted transactions
- Explicit reversal flows
- ACID transactional guarantees
- Currency-aware money handling
- Auditable operational events

---

# Sample Data Assumptions

Most reports expect sample/demo data for:

## Accounts
- Customer accounts
- Internal clearing accounts
- Suspense accounts
- Fee income accounts
- Settlement accounts

## Transactions
- Posted transfers
- Failed transfers
- Reversed transfers
- Adjustment transactions
- Multi-posting journal entries

## Reconciliation
- Imported settlement batches
- Settlement mismatches
- Missing settlement items
- Amount mismatch scenarios

## Audit & Operations
- Audit trail entries
- Correlation IDs
- Outbox events
- Operational investigation events

---

# SQL Report Ideas

The `sql/` directory may include reports such as:

- Daily trial balance
- Account statement by date range
- Currency exposure summary
- Top failed transfer reasons
- Reconciliation mismatch report
- Suspense account aging
- Account balance verification
- Ledger transaction investigation
- Outbox retry backlog
- Reversal activity report

---

# PL/SQL-Style Script Ideas

The `psql/` directory may include:

- Trial balance procedures
- Reporting stored functions
- Reconciliation aggregation procedures
- Daily settlement summaries
- Batch processing examples
- Cursor-based financial reports

These scripts are intentionally Oracle-oriented to demonstrate familiarity with enterprise banking environments.

---

# Conventions

## Money Representation

Money is stored using:
- integer minor units (`BIGINT`)
- explicit currency codes

Example:
- `1050` = `10.50`
- `USD`
- `EUR`

## Posting Directions

Posting directions are expected to use:
- `DEBIT`
- `CREDIT`

## Transaction Statuses

Typical statuses:
- `PENDING`
- `POSTED`
- `FAILED`
- `REVERSED`
- `REJECTED`

---

# Important Notes

- Reports are intentionally written as standalone artifacts for interview and portfolio demonstration.
- Some SQL may be PostgreSQL-oriented while PL/SQL scripts are Oracle-inspired.
- Reporting queries should remain read-only unless explicitly documented otherwise.
- Financial correctness is prioritized over query optimization.

---


# Report Documentation

## Reports

| Report | File | Purpose |
| --- | --- | --- |
| Daily Trial Balance | `reports/sql/daily_trial_balance.sql` | Validates debit/credit movement by currency and account category. |
| Account Statement Summary | `reports/sql/account_statement_summary.sql` | Shows account-level postings, transaction details, and running balance. |
| Reconciliation Mismatch Report | `reports/sql/reconciliation_mismatch_report.sql` | Investigates settlement mismatches by batch and mismatch type. |
| Suspense Account Aging Report | `reports/sql/suspense_account_aging_report.sql` | Groups suspense/internal balances into aging buckets. |
| Top Failed Transfer Reasons | `reports/sql/top_failed_transfer_reasons.sql` | Ranks failed transfer reasons by count and amount. |
| Ledger Activity CTE Report | `reports/sql/ledger_activity_cte_report.sql` | Demonstrates CTE-heavy Oracle-style reporting with buckets and analytics. |
| Daily Trial Balance Procedure | `reports/plsql/generate_daily_trial_balance.sql` | Optional PL/SQL-style cursor procedure for trial balance reporting. |

## Common Parameters

| Parameter | Used By | Meaning |
| --- | --- | --- |
| `:report_date` | Daily trial balance | Business date for the report. |
| `:account_id` | Account statement | Account to investigate. |
| `:from_date` | Statement, failed transfers, CTE report | Inclusive start date/time. |
| `:to_date` | Statement, failed transfers, CTE report | Exclusive end date/time. |
| `:batch_id` | Reconciliation mismatch report | Settlement batch to inspect. |
| `:mismatch_type` | Reconciliation mismatch report | Optional mismatch type filter. |
| `:as_of_date` | Suspense aging report | Cutoff date for aging buckets. |

## Sample SQL Client Commands

```sql
var report_date date;
exec :report_date := date '2026-05-20';

@reports/sql/daily_trial_balance.sql
```

```sql
var account_id raw(16);
var from_date timestamp;
var to_date timestamp;

exec :account_id := hextoraw('11111111111111111111111111111111');
exec :from_date := timestamp '2026-05-20 00:00:00';
exec :to_date := timestamp '2026-05-21 00:00:00';

@reports/sql/account_statement_summary.sql
```

```sql
var batch_id raw(16);
var mismatch_type varchar2(50);

exec :batch_id := hextoraw('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');
exec :mismatch_type := null;

@reports/sql/reconciliation_mismatch_report.sql
```

## Expected Column Documentation

Each report file should document its own expected columns in the header comment.

Common output columns include:

- `currency_code`
- `account_category`
- `account_type`
- `posting_direction`
- `amount_minor`
- `signed_amount_minor`
- `running_balance_minor`
- `failure_reason_code`
- `mismatch_type`
- `severity`
- `created_at`

## Interview / Demo Story

These reports show that the project is not only REST CRUD.

They demonstrate:

- double-entry validation
- immutable ledger investigation
- account-level auditability
- reconciliation workflow understanding
- operational failure analysis
- suspense account monitoring
- Oracle-style SQL familiarity
- analytic functions and CTE usage
- safe read-only reporting over financial data

---

# Report Query Conventions

All reporting queries and PL/SQL-style scripts should follow these conventions.

## Oracle-Compatible SQL

Reports should prefer Oracle-compatible SQL syntax whenever practical.

Examples:
- Use `NVL` instead of `COALESCE` when demonstrating Oracle-oriented SQL.
- Use `SYSDATE` for Oracle-style current timestamp examples.
- Prefer Oracle-compatible join and aggregation syntax.
- Prefer explicit aliases and readable formatting.

The goal is not strict Oracle dependency, but demonstrating familiarity with enterprise Oracle environments commonly used in banking systems.

---

## Bind Variables

Reports should use bind variables for:
- date ranges
- account ids
- transaction ids
- customer ids
- reconciliation batch ids

Examples:

```sql
WHERE posted_at >= :from_date
  AND posted_at < :to_date
```

```sql
WHERE account_id = :account_id
```

This demonstrates:
- safer query practices
- reusable reporting scripts
- execution plan stability
- enterprise SQL conventions

---

## Avoid Unsupported Vendor-Neutral Syntax

Avoid SQL syntax that Oracle does not support or commonly handles differently.

Examples:
- Avoid PostgreSQL-specific casting syntax such as `::text`.
- Avoid `LIMIT` in Oracle-oriented examples.
- Prefer Oracle-compatible pagination examples.
- Avoid vendor-specific JSON operators unless intentionally documented.

When PostgreSQL-specific syntax is used for the actual project database, document it clearly in comments.

---

## Report Header Comments

Every report file should begin with comments describing:
- report purpose
- expected parameters
- important assumptions
- expected sorting
- important business rules

Example:

```sql
-- Report: Daily Trial Balance
-- Purpose:
--   Summarize total debits and credits by currency for a business date.
--
-- Parameters:
--   :business_date
--
-- Notes:
--   Only POSTED transactions are included.
```

---

## Expected Columns Documentation

Each report should explicitly document the expected output columns.

Example:

```sql
-- Expected Columns:
--   currency_code
--   total_debits_minor
--   total_credits_minor
--   imbalance_minor
```

This improves:
- operational readability
- audit investigation usability
- API/report integration
- interview demonstration quality

---

## Read-Only Principle

Reporting queries must never:
- mutate ledger data
- update balances
- delete transactions
- modify reconciliation results
- alter audit history

Reports should remain strictly read-only unless explicitly documented otherwise.