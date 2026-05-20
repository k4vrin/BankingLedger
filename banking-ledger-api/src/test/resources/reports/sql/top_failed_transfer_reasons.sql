-- Report: Top Failed Transfer Reasons
-- Purpose:
--   Summarize failed transfers by failure reason code and currency within a date range.
--
-- Parameters:
--   :from_date
--     Inclusive start timestamp/date.
--
--   :to_date
--     Exclusive end timestamp/date.
--
-- Expected Columns:
--   failure_reason_code
--   currency_code
--   failure_count
--   total_failed_amount_minor
--   first_failure_at
--   last_failure_at

select
    nvl(tr.failure_reason_code, 'UNKNOWN') as failure_reason_code,
    tr.currency_code,
    count(*) as failure_count,
    nvl(sum(tr.amount_minor), 0) as total_failed_amount_minor,
    min(tr.created_at) as first_failure_at,
    max(tr.created_at) as last_failure_at

from transfer_requests tr
where tr.status = 'FAILED'
  and tr.created_at >= :from_date
  and tr.created_at < :to_date
group by
    nvl(tr.failure_reason_code, 'UNKNOWN'),
    tr.currency_code
order by
    failure_count desc,
    total_failed_amount_minor desc,
    failure_reason_code,
    currency_code;