-- Report: Reconciliation Mismatch Report
-- Purpose:
--   List reconciliation results for a settlement batch, optionally filtered by mismatch type.
--
-- Parameters:
--   :batch_id
--   :mismatch_type
--     Optional. Pass NULL to include all mismatch types.
--
-- Expected Columns:
--   reconciliation_result_id
--   batch_id
--   external_reference
--   ledger_transaction_id
--   mismatch_type
--   severity
--   result_status
--   settlement_item_status
--   expected_amount_minor
--   actual_amount_minor
--   currency_code
--   detail
--   created_at

select
    rr.id as reconciliation_result_id,
    rr.batch_id,
    si.external_transaction_reference as external_reference,
    rr.ledger_transaction_id,
    rr.mismatch_type,
    rr.severity,
    rr.status as result_status,
    si.status as settlement_item_status,
    lt.amount_minor as expected_amount_minor,
    si.amount_minor as actual_amount_minor,
    nvl(lt.currency_code, si.currency_code) as currency_code,
    rr.detail,
    rr.created_at

from reconciliation_results rr
         left join settlement_items si
                   on si.id = rr.item_id
         left join ledger_transactions lt
                   on lt.id = rr.ledger_transaction_id
where rr.batch_id = :batch_id
  and (:mismatch_type is null or rr.mismatch_type = :mismatch_type)
order by
    case rr.severity
        when 'CRITICAL' then 1
        when 'WARNING' then 2
        when 'INFO' then 3
        else 4
        end,
    rr.created_at;