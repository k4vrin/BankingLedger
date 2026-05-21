-- Report: Suspense Account Aging Report
-- Purpose:
--   Group open suspense/internal account postings into aging buckets by currency.
--
-- Parameters:
--   :as_of_date
--     Report cutoff date. Expected as an Oracle DATE value.
--
-- Expected Columns:
--   currency_code
--   age_bucket
--   posting_count
--   total_debit_minor
--   total_credit_minor
--   net_amount_minor

with suspense_postings as (
    select
        p.currency_code,
        p.direction,
        p.amount_minor,
        case
            when trunc(:as_of_date) - trunc(p.posted_at) between 0 and 1 then '0-1 days'
            when trunc(:as_of_date) - trunc(p.posted_at) between 2 and 7 then '2-7 days'
            when trunc(:as_of_date) - trunc(p.posted_at) between 8 and 30 then '8-30 days'
            else '31+ days'
            end as age_bucket
    from postings p
             join accounts a
                  on a.id = p.account_id
             join journal_entries je
                  on je.id = p.journal_entry_id
             join ledger_transactions lt
                  on lt.id = je.ledger_transaction_id
    where lt.status = 'POSTED'
      and p.posted_at < trunc(:as_of_date) + 1
      and (
        a.account_type = 'SUSPENSE'
            or a.account_category = 'INTERNAL'
        )
)
select
    currency_code,
    age_bucket,
    count(*) as posting_count,
    nvl(sum(case
                when direction = 'DEBIT' then amount_minor
                else 0
        end), 0) as total_debit_minor,
    nvl(sum(case
                when direction = 'CREDIT' then amount_minor
                else 0
        end), 0) as total_credit_minor,
    nvl(sum(case
                when direction = 'DEBIT' then amount_minor
                when direction = 'CREDIT' then -amount_minor
                else 0
        end), 0) as net_amount_minor
from suspense_postings
group by
    currency_code,
    age_bucket
order by
    currency_code,
    case age_bucket
        when '0-1 days' then 1
        when '2-7 days' then 2
        when '8-30 days' then 3
        else 4
        end;
