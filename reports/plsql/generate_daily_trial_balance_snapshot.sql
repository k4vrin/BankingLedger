-- Optional local-development script: daily trial balance snapshot workflow.
--
-- How to run locally:
--   1. Connect to the migrated Oracle schema with SQLcl, SQL*Plus, or SQL Developer.
--   2. Run:
--        @reports/plsql/generate_daily_trial_balance_snapshot.sql
--   3. Generate a snapshot:
--        exec generate_daily_trial_balance_snapshot(date '2026-05-20');
--   4. Inspect:
--        select * from report_daily_trial_balance_snapshot order by currency_code, account_category;
--
-- This script is intentionally not part of Flyway migrations.

create table report_daily_trial_balance_snapshot
(
    snapshot_id            raw(16) default sys_guid() primary key,
    report_date            date not null,
    currency_code          char(3) not null,
    account_category       varchar2(20) not null,
    opening_debit_minor    number(19, 0) not null,
    opening_credit_minor   number(19, 0) not null,
    opening_net_minor      number(19, 0) not null,
    report_debit_minor     number(19, 0) not null,
    report_credit_minor    number(19, 0) not null,
    report_net_minor       number(19, 0) not null,
    closing_debit_minor    number(19, 0) not null,
    closing_credit_minor   number(19, 0) not null,
    closing_net_minor      number(19, 0) not null,
    generated_at           timestamp with time zone default systimestamp not null
);

create or replace procedure generate_daily_trial_balance_snapshot(
    p_report_date in date
) as
begin
    delete from report_daily_trial_balance_snapshot
    where report_date = trunc(p_report_date);

    insert into report_daily_trial_balance_snapshot (
        report_date,
        currency_code,
        account_category,
        opening_debit_minor,
        opening_credit_minor,
        opening_net_minor,
        report_debit_minor,
        report_credit_minor,
        report_net_minor,
        closing_debit_minor,
        closing_credit_minor,
        closing_net_minor
    )
    select
        trunc(p_report_date) as report_date,
        p.currency_code,
        a.account_category,
        nvl(sum(case when p.posted_at < trunc(p_report_date) and p.direction = 'DEBIT' then p.amount_minor else 0 end), 0),
        nvl(sum(case when p.posted_at < trunc(p_report_date) and p.direction = 'CREDIT' then p.amount_minor else 0 end), 0),
        nvl(sum(case
                    when p.posted_at < trunc(p_report_date) and p.direction = 'DEBIT' then p.amount_minor
                    when p.posted_at < trunc(p_report_date) and p.direction = 'CREDIT' then -p.amount_minor
                    else 0
                end), 0),
        nvl(sum(case
                    when p.posted_at >= trunc(p_report_date)
                        and p.posted_at < trunc(p_report_date) + 1
                        and p.direction = 'DEBIT' then p.amount_minor
                    else 0
                end), 0),
        nvl(sum(case
                    when p.posted_at >= trunc(p_report_date)
                        and p.posted_at < trunc(p_report_date) + 1
                        and p.direction = 'CREDIT' then p.amount_minor
                    else 0
                end), 0),
        nvl(sum(case
                    when p.posted_at >= trunc(p_report_date)
                        and p.posted_at < trunc(p_report_date) + 1
                        and p.direction = 'DEBIT' then p.amount_minor
                    when p.posted_at >= trunc(p_report_date)
                        and p.posted_at < trunc(p_report_date) + 1
                        and p.direction = 'CREDIT' then -p.amount_minor
                    else 0
                end), 0),
        nvl(sum(case when p.posted_at < trunc(p_report_date) + 1 and p.direction = 'DEBIT' then p.amount_minor else 0 end), 0),
        nvl(sum(case when p.posted_at < trunc(p_report_date) + 1 and p.direction = 'CREDIT' then p.amount_minor else 0 end), 0),
        nvl(sum(case
                    when p.posted_at < trunc(p_report_date) + 1 and p.direction = 'DEBIT' then p.amount_minor
                    when p.posted_at < trunc(p_report_date) + 1 and p.direction = 'CREDIT' then -p.amount_minor
                    else 0
                end), 0)
    from postings p
             join accounts a on a.id = p.account_id
             join journal_entries je on je.id = p.journal_entry_id
             join ledger_transactions lt on lt.id = je.ledger_transaction_id
    where lt.status = 'POSTED'
      and p.posted_at < trunc(p_report_date) + 1
    group by
        p.currency_code,
        a.account_category;
end;
/
