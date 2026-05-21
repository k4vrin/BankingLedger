# Banking Ledger Reports

This folder contains Oracle-oriented reporting SQL used for operational review and portfolio demos. The scripts are read-only unless a file explicitly says otherwise.

## Running Locally

Run a report from SQL Developer, SQLcl, or SQL*Plus after applying Flyway migrations and seeding data:

```sql
@reports/sql/daily_trial_balance.sql
```

Bind variables are used for dynamic inputs. In SQLcl or SQL*Plus, define variables before running a report, for example:

```sql
var report_date date
exec :report_date := date '2026-05-20';
@reports/sql/daily_trial_balance.sql
```

## Report Catalog

| Report | File | Purpose | Main Parameters |
| --- | --- | --- | --- |
| Daily trial balance | `sql/daily_trial_balance.sql` | Summarizes debit, credit, net, opening, and closing movement by currency and account category. | `:report_date` |
| Account statement summary | `sql/account_statement_summary.sql` | Lists postings for one account and calculates running balance with an analytic function. | `:account_id`, `:from_date`, `:to_date` |
| Reconciliation mismatch report | `sql/reconciliation_mismatch_report.sql` | Reviews open or historical settlement mismatches for an imported batch. | `:batch_id`, `:mismatch_type` |
| Suspense account aging | `sql/suspense_account_aging_report.sql` | Groups internal suspense postings into operational age buckets. | `:as_of_date` |
| Top failed transfer reasons | `sql/top_failed_transfer_reasons.sql` | Ranks failed transfer reasons by count and amount. | `:from_date`, `:to_date` |
| Ledger activity CTE report | `sql/ledger_activity_cte_report.sql` | Demonstrates a CTE-heavy Oracle report over ledger movement and age buckets. | `:from_date`, `:to_date` |

## Demo Flow

Use the reports in this order for a review walkthrough:

1. Start with `daily_trial_balance.sql` to show balanced debit and credit movement.
2. Drill into `account_statement_summary.sql` for transfer, reversal, and adjustment rows on a customer account.
3. Use `reconciliation_mismatch_report.sql` to explain settlement exception handling.
4. Use `suspense_account_aging_report.sql` for operational aging and internal account review.
5. Use `top_failed_transfer_reasons.sql` to discuss failure analysis and product/ops feedback loops.

## PL/SQL Example

`plsql/generate_daily_trial_balance_snapshot.sql` is an optional local-development example. It creates a snapshot table plus a procedure that inserts daily trial balance rows into that table. The script is intentionally separate from Flyway migrations.
