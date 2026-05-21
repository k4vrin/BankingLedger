-- Dev-only deterministic seed data for manual API demos.
-- IDs are stable so README, HTTP files, and OpenAPI examples can reference them.

merge into customers target
using (
    select hextoraw('00000000000000000000000000000001') id, 'demo-customer-1' external_customer_reference,
           'Demo Customer One' full_name, 'demo.customer.one@example.com' email from dual
    union all
    select hextoraw('00000000000000000000000000000002') id, 'demo-customer-2' external_customer_reference,
           'Demo Customer Two' full_name, 'demo.customer.two@example.com' email from dual
) source
on (target.id = source.id)
when not matched then
    insert (id, external_customer_reference, full_name, email, status, created_at, updated_at, version)
    values (source.id, source.external_customer_reference, source.full_name, source.email, 'ACTIVE', systimestamp, systimestamp, 0);

merge into accounts target
using (
    select hextoraw('00000000000000000000000000001001') id, hextoraw('00000000000000000000000000000001') customer_id,
           'DEMO-CHECKING-001' account_number, 'CURRENT' account_type, 'CUSTOMER' account_category,
           'ACTIVE' status, 'USD' currency_code, 10250 available_balance_minor, 10250 ledger_balance_minor from dual
    union all
    select hextoraw('00000000000000000000000000001002') id, hextoraw('00000000000000000000000000000001') customer_id,
           'DEMO-SAVINGS-001' account_number, 'SAVINGS' account_type, 'CUSTOMER' account_category,
           'ACTIVE' status, 'USD' currency_code, 15750 available_balance_minor, 15750 ledger_balance_minor from dual
    union all
    select hextoraw('00000000000000000000000000001003') id, hextoraw('00000000000000000000000000000002') customer_id,
           'DEMO-CHECKING-002' account_number, 'CURRENT' account_type, 'CUSTOMER' account_category,
           'ACTIVE' status, 'USD' currency_code, 5000 available_balance_minor, 5000 ledger_balance_minor from dual
    union all
    select hextoraw('00000000000000000000000000001101') id, hextoraw('00000000000000000000000000000002') customer_id,
           'DEMO-SUSPENSE-USD' account_number, 'SUSPENSE' account_type, 'INTERNAL' account_category,
           'ACTIVE' status, 'USD' currency_code, 100000 available_balance_minor, 100000 ledger_balance_minor from dual
) source
on (target.id = source.id)
when not matched then
    insert (
        id, customer_id, account_number, account_type, account_category, status, currency_code,
        available_balance_minor, ledger_balance_minor, created_at, updated_at, version
    )
    values (
        source.id, source.customer_id, source.account_number, source.account_type, source.account_category, source.status,
        source.currency_code, source.available_balance_minor, source.ledger_balance_minor, systimestamp, systimestamp, 0
    );

merge into ledger_transactions target
using (
    select hextoraw('00000000000000000000000000002001') id, 'demo-transfer-completed-001' external_reference,
           'TRANSFER' transaction_type, 'POSTED' status, 'USD' currency_code, 1500 amount_minor,
           'Seeded completed transfer' description from dual
    union all
    select hextoraw('00000000000000000000000000002002') id, 'demo-transfer-reversed-001' external_reference,
           'TRANSFER' transaction_type, 'REVERSED' status, 'USD' currency_code, 500 amount_minor,
           'Seeded transfer later reversed' description from dual
    union all
    select hextoraw('00000000000000000000000000002003') id, 'demo-reversal-001' external_reference,
           'REVERSAL' transaction_type, 'POSTED' status, 'USD' currency_code, 500 amount_minor,
           'Seeded reversal transaction' description from dual
    union all
    select hextoraw('00000000000000000000000000002004') id, 'demo-adjustment-001' external_reference,
           'ADJUSTMENT' transaction_type, 'POSTED' status, 'USD' currency_code, 250 amount_minor,
           'Seeded operational adjustment' description from dual
) source
on (target.id = source.id)
when not matched then
    insert (
        id, external_reference, transaction_type, status, currency_code, amount_minor, description,
        posted_at, created_at, updated_at, version
    )
    values (
        source.id, source.external_reference, source.transaction_type, source.status, source.currency_code,
        source.amount_minor, source.description, systimestamp, systimestamp, systimestamp, 0
    );

merge into journal_entries target
using (
    select hextoraw('00000000000000000000000000003001') id, hextoraw('00000000000000000000000000002001') ledger_transaction_id,
           'TRANSFER' entry_type, 'USD' currency_code, 1500 total_debit_minor, 1500 total_credit_minor,
           'Seeded completed transfer journal' description from dual
    union all
    select hextoraw('00000000000000000000000000003002') id, hextoraw('00000000000000000000000000002002') ledger_transaction_id,
           'TRANSFER' entry_type, 'USD' currency_code, 500 total_debit_minor, 500 total_credit_minor,
           'Seeded reversed transfer original journal' description from dual
    union all
    select hextoraw('00000000000000000000000000003003') id, hextoraw('00000000000000000000000000002003') ledger_transaction_id,
           'REVERSAL' entry_type, 'USD' currency_code, 500 total_debit_minor, 500 total_credit_minor,
           'Seeded reversal journal' description from dual
    union all
    select hextoraw('00000000000000000000000000003004') id, hextoraw('00000000000000000000000000002004') ledger_transaction_id,
           'ADJUSTMENT' entry_type, 'USD' currency_code, 250 total_debit_minor, 250 total_credit_minor,
           'Seeded adjustment journal' description from dual
) source
on (target.id = source.id)
when not matched then
    insert (
        id, ledger_transaction_id, entry_type, currency_code, total_debit_minor, total_credit_minor,
        description, posted_at, created_at, version
    )
    values (
        source.id, source.ledger_transaction_id, source.entry_type, source.currency_code, source.total_debit_minor,
        source.total_credit_minor, source.description, systimestamp, systimestamp, 0
    );

merge into postings target
using (
    select hextoraw('00000000000000000000000000004001') id, hextoraw('00000000000000000000000000003001') journal_entry_id,
           hextoraw('00000000000000000000000000001001') account_id, 'DEBIT' direction, 'USD' currency_code, 1500 amount_minor from dual
    union all
    select hextoraw('00000000000000000000000000004002') id, hextoraw('00000000000000000000000000003001') journal_entry_id,
           hextoraw('00000000000000000000000000001002') account_id, 'CREDIT' direction, 'USD' currency_code, 1500 amount_minor from dual
    union all
    select hextoraw('00000000000000000000000000004003') id, hextoraw('00000000000000000000000000003002') journal_entry_id,
           hextoraw('00000000000000000000000000001002') account_id, 'DEBIT' direction, 'USD' currency_code, 500 amount_minor from dual
    union all
    select hextoraw('00000000000000000000000000004004') id, hextoraw('00000000000000000000000000003002') journal_entry_id,
           hextoraw('00000000000000000000000000001003') account_id, 'CREDIT' direction, 'USD' currency_code, 500 amount_minor from dual
    union all
    select hextoraw('00000000000000000000000000004005') id, hextoraw('00000000000000000000000000003003') journal_entry_id,
           hextoraw('00000000000000000000000000001003') account_id, 'DEBIT' direction, 'USD' currency_code, 500 amount_minor from dual
    union all
    select hextoraw('00000000000000000000000000004006') id, hextoraw('00000000000000000000000000003003') journal_entry_id,
           hextoraw('00000000000000000000000000001002') account_id, 'CREDIT' direction, 'USD' currency_code, 500 amount_minor from dual
    union all
    select hextoraw('00000000000000000000000000004007') id, hextoraw('00000000000000000000000000003004') journal_entry_id,
           hextoraw('00000000000000000000000000001101') account_id, 'DEBIT' direction, 'USD' currency_code, 250 amount_minor from dual
    union all
    select hextoraw('00000000000000000000000000004008') id, hextoraw('00000000000000000000000000003004') journal_entry_id,
           hextoraw('00000000000000000000000000001001') account_id, 'CREDIT' direction, 'USD' currency_code, 250 amount_minor from dual
) source
on (target.id = source.id)
when not matched then
    insert (id, journal_entry_id, account_id, direction, currency_code, amount_minor, posted_at, created_at)
    values (
        source.id, source.journal_entry_id, source.account_id, source.direction, source.currency_code,
        source.amount_minor, systimestamp, systimestamp
    );

merge into transfer_requests target
using (
    select hextoraw('00000000000000000000000000005001') id,
           hextoraw('00000000000000000000000000001001') source_account_id,
           hextoraw('00000000000000000000000000001002') destination_account_id,
           hextoraw('00000000000000000000000000000001') requested_by_customer_id,
           hextoraw('00000000000000000000000000002001') ledger_transaction_id,
           'demo-transfer-completed-001' external_reference, 'COMPLETED' status, 1500 amount_minor,
           'Seeded completed transfer' description from dual
    union all
    select hextoraw('00000000000000000000000000005002') id,
           hextoraw('00000000000000000000000000001002') source_account_id,
           hextoraw('00000000000000000000000000001003') destination_account_id,
           hextoraw('00000000000000000000000000000001') requested_by_customer_id,
           hextoraw('00000000000000000000000000002002') ledger_transaction_id,
           'demo-transfer-reversed-001' external_reference, 'REVERSED' status, 500 amount_minor,
           'Seeded transfer later reversed' description from dual
) source
on (target.id = source.id)
when not matched then
    insert (
        id, source_account_id, destination_account_id, requested_by_customer_id, ledger_transaction_id,
        external_reference, transfer_type, requested_by_actor_type, status, currency_code, amount_minor,
        description, requested_at, completed_at, created_at, updated_at, version
    )
    values (
        source.id, source.source_account_id, source.destination_account_id, source.requested_by_customer_id,
        source.ledger_transaction_id, source.external_reference, 'INTERNAL', 'CUSTOMER', source.status, 'USD',
        source.amount_minor, source.description, systimestamp, systimestamp, systimestamp, systimestamp, 0
    );

merge into reversals target
using (
    select hextoraw('00000000000000000000000000006001') id,
           hextoraw('00000000000000000000000000005002') original_transfer_id,
           hextoraw('00000000000000000000000000002002') original_ledger_transaction_id,
           hextoraw('00000000000000000000000000002003') reversal_ledger_transaction_id
    from dual
) source
on (target.id = source.id)
when not matched then
    insert (
        id, original_transfer_id, original_ledger_transaction_id, reversal_ledger_transaction_id,
        reason_code, reason_detail, requested_by_actor_type, requested_by_actor_role, requested_by_actor_id,
        correlation_id, requested_at, completed_at, status, created_at, updated_at, version
    )
    values (
        source.id, source.original_transfer_id, source.original_ledger_transaction_id, source.reversal_ledger_transaction_id,
        'OPERATIONAL_ERROR', 'Seeded demo reversal', 'OPS_ADMIN', 'OPS_ADMIN', 'dev-ops-admin',
        'seed-reversal-correlation', systimestamp, systimestamp, 'COMPLETED', systimestamp, systimestamp, 0
    );

merge into adjustment_requests target
using (
    select hextoraw('00000000000000000000000000007001') id,
           hextoraw('00000000000000000000000000002004') ledger_transaction_id
    from dual
) source
on (target.id = source.id)
when not matched then
    insert (
        id, ledger_transaction_id, reason_code, reason_detail, requested_by_actor_type, requested_by_actor_role,
        requested_by_actor_id, correlation_id, requested_at, completed_at, status, created_at, updated_at, version
    )
    values (
        source.id, source.ledger_transaction_id, 'MANUAL_CORRECTION', 'Seeded demo adjustment',
        'OPS_ADMIN', 'OPS_ADMIN', 'dev-ops-admin', 'seed-adjustment-correlation',
        systimestamp, systimestamp, 'COMPLETED', systimestamp, systimestamp, 0
    );

merge into settlement_batches target
using (
    select hextoraw('00000000000000000000000000008001') id from dual
) source
on (target.id = source.id)
when not matched then
    insert (
        id, source, reference_name, imported_by_actor, correlation_id, status, imported_at, completed_at,
        item_count, matched_count, mismatch_count, version
    )
    values (
        source.id, 'DEMO_CLEARING', 'demo-reconciliation-batch', 'dev-ops-admin', 'seed-reconciliation-correlation',
        'COMPLETED', systimestamp, systimestamp, 2, 1, 1, 0
    );

merge into settlement_items target
using (
    select hextoraw('00000000000000000000000000008101') id, hextoraw('00000000000000000000000000008001') batch_id,
           'demo-transfer-completed-001' external_transaction_reference, 1500 amount_minor,
           'SETTLED' status, 'seed-demo-item-1' raw_line_hash from dual
    union all
    select hextoraw('00000000000000000000000000008102') id, hextoraw('00000000000000000000000000008001') batch_id,
           'external-only-demo-mismatch' external_transaction_reference, 999 amount_minor,
           'SETTLED' status, 'seed-demo-item-2' raw_line_hash from dual
) source
on (target.id = source.id)
when not matched then
    insert (
        id, batch_id, source, external_transaction_reference, amount_minor, currency_code, status,
        settlement_date, raw_line_hash, metadata_json, created_at, version
    )
    values (
        source.id, source.batch_id, 'DEMO_CLEARING', source.external_transaction_reference, source.amount_minor,
        'USD', source.status, trunc(current_date), source.raw_line_hash, '{}', systimestamp, 0
    );

merge into reconciliation_results target
using (
    select hextoraw('00000000000000000000000000008201') id, hextoraw('00000000000000000000000000008001') batch_id,
           hextoraw('00000000000000000000000000008101') item_id, hextoraw('00000000000000000000000000002001') ledger_transaction_id,
           'MATCHED' mismatch_type, 'INFO' severity, 'OPEN' status, 'Seeded matched item' detail from dual
    union all
    select hextoraw('00000000000000000000000000008202') id, hextoraw('00000000000000000000000000008001') batch_id,
           hextoraw('00000000000000000000000000008102') item_id, null ledger_transaction_id,
           'MISSING_INTERNAL_TRANSACTION' mismatch_type, 'CRITICAL' severity, 'OPEN' status,
           'External settlement item has no matching ledger transaction' detail from dual
) source
on (target.id = source.id)
when not matched then
    insert (
        id, batch_id, item_id, ledger_transaction_id, mismatch_type, severity, status, detail, created_at, version
    )
    values (
        source.id, source.batch_id, source.item_id, source.ledger_transaction_id, source.mismatch_type,
        source.severity, source.status, source.detail, systimestamp, 0
    );
