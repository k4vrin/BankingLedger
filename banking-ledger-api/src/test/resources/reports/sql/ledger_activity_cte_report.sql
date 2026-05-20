-- Report: Ledger Activity CTE Report
-- Purpose:
--   Demonstrate an Oracle-style CTE-heavy report for ledger activity analysis.
--   Summarizes posted ledger movement by currency, account category, account type,
--   and age bucket.
--
-- Parameters:
--   :from_date
--     Inclusive start timestamp/date.
--
--   :to_date
--     Exclusive end timestamp/date.
--
-- Expected Columns:
--   currency_code
--   account_category
--   account_type
--   age_bucket
--   posting_count
--   total_debit_minor
--   total_credit_minor
--   net_movement_minor
--   category_running_net_minor

with filtered_postings as (
    select
        p.id as posting_id,
        p.account_id,
        p.direction,
        p.amount_minor,
        p.currency_code,
        p.posted_at,
        a.account_category,
        a.account_type,
        lt.id as ledger_transaction_id,
        lt.status as ledger_transaction_status
    from postings p
             join accounts a
                  on a.id = p.account_id
             join journal_entries je
                  on je.id = p.journal_entry_id
             join ledger_transactions lt
                  on lt.id = je.ledger_transaction_id
    where lt.status = 'POSTED'
      and p.posted_at >= :from_date
      and p.posted_at < :to_date
),

     classified_postings as (
         select
             fp.*,

             case
                 when fp.direction = 'DEBIT' then fp.amount_minor
                 when fp.direction = 'CREDIT' then -fp.amount_minor
                 else 0
                 end as signed_amount_minor,

             case
                 when cast(:to_date as date) - cast(fp.posted_at as date) between 0 and 1 then '0-1 days'
                 when cast(:to_date as date) - cast(fp.posted_at as date) between 2 and 7 then '2-7 days'
                 when cast(:to_date as date) - cast(fp.posted_at as date) between 8 and 30 then '8-30 days'
                 else '31+ days'
                 end as age_bucket
         from filtered_postings fp
     ),

     grouped_activity as (
         select
             cp.currency_code,
             cp.account_category,
             cp.account_type,
             cp.age_bucket,

             count(*) as posting_count,

             nvl(sum(case
                         when cp.direction = 'DEBIT' then cp.amount_minor
                         else 0
                 end), 0) as total_debit_minor,

             nvl(sum(case
                         when cp.direction = 'CREDIT' then cp.amount_minor
                         else 0
                 end), 0) as total_credit_minor,

             nvl(sum(cp.signed_amount_minor), 0) as net_movement_minor
         from classified_postings cp
         group by
             cp.currency_code,
             cp.account_category,
             cp.account_type,
             cp.age_bucket
     )

select
    ga.currency_code,
    ga.account_category,
    ga.account_type,
    ga.age_bucket,
    ga.posting_count,
    ga.total_debit_minor,
    ga.total_credit_minor,
    ga.net_movement_minor,

    sum(ga.net_movement_minor) over (
        partition by ga.currency_code, ga.account_category
        order by
            ga.account_type,
            case ga.age_bucket
                when '0-1 days' then 1
                when '2-7 days' then 2
                when '8-30 days' then 3
                else 4
                end
        rows between unbounded preceding and current row
        ) as category_running_net_minor

from grouped_activity ga
order by
    ga.currency_code,
    ga.account_category,
    ga.account_type,
    case ga.age_bucket
        when '0-1 days' then 1
        when '2-7 days' then 2
        when '8-30 days' then 3
        else 4
        end;