-- Report: Account Statement Summary
-- Purpose:
--   List all postings for a single account within a date range, including transaction details
--   and a running balance calculated with an analytic function.
--
-- Parameters:
--   :account_id
--     The account id to report on.
--
--   :from_date
--     Inclusive start timestamp/date.
--
--   :to_date
--     Exclusive end timestamp/date.
--
-- Expected Columns:
--   ledger_transaction_id
--   posting_id
--   posting_direction
--   amount_minor
--   currency_code
--   signed_amount_minor
--   running_balance_minor
--   description
--   posted_at
--
-- Notes:
--   Only ledger transactions with status = 'POSTED' are included.
--   Date range is inclusive from :from_date and exclusive before :to_date.
--   Money values are represented in minor units.
--   Debit postings increase the running balance.
--   Credit postings decrease the running balance.

select
    lt.id as ledger_transaction_id,
    p.id as posting_id,
    p.direction as posting_direction,
    p.amount_minor,
    p.currency_code,

    case
        when p.direction = 'DEBIT'
            then p.amount_minor
        when p.direction = 'CREDIT'
            then -p.amount_minor
        end as signed_amount_minor,

    sum(
            case
                when p.direction = 'DEBIT'
                    then p.amount_minor
                when p.direction = 'CREDIT'
                    then -p.amount_minor
                end
    ) over (
                partition by p.account_id, p.currency_code
                order by p.posted_at, p.id
                rows between unbounded preceding and current row
                ) as running_balance_minor,

    lt.description,
    p.posted_at

from postings p
         join journal_entries je
              on je.id = p.journal_entry_id
         join ledger_transactions lt
              on lt.id = je.ledger_transaction_id
where p.account_id = :account_id
  and p.posted_at >= :from_date
  and p.posted_at < :to_date
  and lt.status = 'POSTED'
order by
    p.posted_at,
    p.id;