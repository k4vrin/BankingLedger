-- PL/SQL-Style Script: Generate Daily Trial Balance
-- Purpose:
--   Demonstrates an Oracle-style stored procedure for producing a daily trial balance report.
--   The procedure returns a SYS_REFCURSOR instead of mutating ledger data.
--
-- Safety:
--   Read-only.
--   Does not create, update, or delete ledger records.
--   Optional portfolio/demo script only.
--
-- How to run locally:
--   1. Open SQL Developer, DataGrip, or another Oracle-compatible SQL client.
--   2. Execute this file to create or replace the procedure.
--   3. Run the example block at the bottom with a chosen report date.
--
-- Input Parameters:
--   p_report_date
--     Business date to report on.
--
-- Output:
--   p_result_cursor
--     Cursor containing daily trial balance rows.

create or replace procedure generate_daily_trial_balance (
    p_report_date   in date,
    p_result_cursor out sys_refcursor
)
as
begin
    open p_result_cursor for
        select
            trunc(p_report_date) as report_date,
            p.currency_code,
            a.account_category,

            nvl(sum(case
                        when p.direction = 'DEBIT' then p.amount_minor
                        else 0
                end), 0) as total_debit_minor,

            nvl(sum(case
                        when p.direction = 'CREDIT' then p.amount_minor
                        else 0
                end), 0) as total_credit_minor,

            nvl(sum(case
                        when p.direction = 'DEBIT' then p.amount_minor
                        when p.direction = 'CREDIT' then -p.amount_minor
                        else 0
                end), 0) as net_movement_minor

        from postings p
                 join accounts a
                      on a.id = p.account_id
                 join journal_entries je
                      on je.id = p.journal_entry_id
                 join ledger_transactions lt
                      on lt.id = je.ledger_transaction_id
        where lt.status = 'POSTED'
          and p.posted_at >= trunc(p_report_date)
          and p.posted_at < trunc(p_report_date) + 1
        group by
            trunc(p_report_date),
            p.currency_code,
            a.account_category
        order by
            p.currency_code,
            a.account_category;
end;
/

-- Example local execution:
--
-- variable result_cursor refcursor;
--
-- exec generate_daily_trial_balance(
--     p_report_date => date '2026-05-20',
--     p_result_cursor => :result_cursor
-- );
--
-- print result_cursor;