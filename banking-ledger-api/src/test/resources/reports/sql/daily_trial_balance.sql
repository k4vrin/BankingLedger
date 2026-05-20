-- Report: Daily Trial Balance
-- Purpose:
--   Summarize debit and credit posting movement by currency and account category for a single report date.
--
-- Parameters:
--   :report_date
--     The business date to report on. Expected as an Oracle DATE value.
--
-- Expected Columns:
--   report_date
--   currency_code
--   account_category
--   opening_debit_minor
--   opening_credit_minor
--   opening_net_minor
--   report_debit_minor
--   report_credit_minor
--   report_net_minor
--   closing_debit_minor
--   closing_credit_minor
--   closing_net_minor
--
-- Notes:
--   Only ledger transactions with status = 'POSTED' are included.
--   Date filter is inclusive from :report_date and exclusive before :report_date + 1.
--   Money values are represented in minor units.
--   Net movement is debit minus credit.

select
    trunc(:report_date) as report_date,
    p.currency_code,
    a.account_category,

    nvl(sum(case
                when p.posted_at < trunc(:report_date) and p.direction = 'DEBIT'
                    then p.amount_minor
                else 0
        end), 0) as opening_debit_minor,

    nvl(sum(case
                when p.posted_at < trunc(:report_date) and p.direction = 'CREDIT'
                    then p.amount_minor
                else 0
        end), 0) as opening_credit_minor,

    nvl(sum(case
                when p.posted_at < trunc(:report_date) and p.direction = 'DEBIT'
                    then p.amount_minor
                when p.posted_at < trunc(:report_date) and p.direction = 'CREDIT'
                    then -p.amount_minor
                else 0
        end), 0) as opening_net_minor,

    nvl(sum(case
                when p.posted_at >= trunc(:report_date)
                    and p.posted_at < trunc(:report_date) + 1
                    and p.direction = 'DEBIT'
                    then p.amount_minor
                else 0
        end), 0) as report_debit_minor,

    nvl(sum(case
                when p.posted_at >= trunc(:report_date)
                    and p.posted_at < trunc(:report_date) + 1
                    and p.direction = 'CREDIT'
                    then p.amount_minor
                else 0
        end), 0) as report_credit_minor,

    nvl(sum(case
                when p.posted_at >= trunc(:report_date)
                    and p.posted_at < trunc(:report_date) + 1
                    and p.direction = 'DEBIT'
                    then p.amount_minor
                when p.posted_at >= trunc(:report_date)
                    and p.posted_at < trunc(:report_date) + 1
                    and p.direction = 'CREDIT'
                    then -p.amount_minor
                else 0
        end), 0) as report_net_minor,

    nvl(sum(case
                when p.posted_at < trunc(:report_date) + 1 and p.direction = 'DEBIT'
                    then p.amount_minor
                else 0
        end), 0) as closing_debit_minor,

    nvl(sum(case
                when p.posted_at < trunc(:report_date) + 1 and p.direction = 'CREDIT'
                    then p.amount_minor
                else 0
        end), 0) as closing_credit_minor,

    nvl(sum(case
                when p.posted_at < trunc(:report_date) + 1 and p.direction = 'DEBIT'
                    then p.amount_minor
                when p.posted_at < trunc(:report_date) + 1 and p.direction = 'CREDIT'
                    then -p.amount_minor
                else 0
        end), 0) as closing_net_minor

from postings p
         join accounts a
              on a.id = p.account_id
         join journal_entries je
              on je.id = p.journal_entry_id
         join ledger_transactions lt
              on lt.id = je.ledger_transaction_id
where lt.status = 'POSTED'
  and p.posted_at < trunc(:report_date) + 1
group by
    trunc(:report_date),
    p.currency_code,
    a.account_category
order by
    p.currency_code,
    a.account_category;