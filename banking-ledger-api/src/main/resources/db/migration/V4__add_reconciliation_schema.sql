-- Phase 10 - Reconciliation schema

create table settlement_batches
(
    id                 RAW(16) primary key,
    source             varchar2(100) not null,
    reference_name     varchar2(255) not null,
    imported_by_actor  varchar2(100) not null,
    correlation_id     varchar2(100),
    status             varchar2(30) not null,
    imported_at        timestamp with time zone not null,
    completed_at       timestamp with time zone,
    item_count         number(10, 0) default 0 not null,
    matched_count      number(10, 0) default 0 not null,
    mismatch_count     number(10, 0) default 0 not null,
    version            number(19, 0) default 0 not null,

    constraint chk_settlement_batch_status check (status in ('PENDING', 'IMPORTED', 'COMPLETED', 'FAILED')),
    constraint chk_settlement_batch_counts check (
        item_count >= 0
            and matched_count >= 0
            and mismatch_count >= 0
            and matched_count + mismatch_count <= item_count
    ),
    constraint chk_settlement_batch_completed check (
        (status = 'COMPLETED' and completed_at is not null)
            or (status <> 'COMPLETED')
    ),
    constraint chk_settlement_batch_version check (version >= 0)
);

create table settlement_items
(
    id                             RAW(16) primary key,
    batch_id                       RAW(16) not null,
    source                         varchar2(100) not null,
    external_transaction_reference varchar2(100) not null,
    amount_minor                   number(19, 0) not null,
    currency_code                  char(3) not null,
    status                         varchar2(30) not null,
    settlement_date                date not null,
    raw_line_hash                  varchar2(128) not null,
    metadata_json                  clob,
    created_at                     timestamp with time zone not null,
    version                        number(19, 0) default 0 not null,

    constraint fk_settlement_items_batch foreign key (batch_id) references settlement_batches (id),
    constraint uk_settlement_source_external_ref unique (source, external_transaction_reference),
    constraint chk_settlement_items_currency check (regexp_like(currency_code, '^[A-Z]{3}$')),
    constraint chk_settlement_items_amount check (amount_minor >= 0),
    constraint chk_settlement_items_status check (status in ('SETTLED', 'PENDING', 'FAILED', 'REJECTED', 'REVERSED')),
    constraint chk_settlement_items_version check (version >= 0)
);

create table reconciliation_results
(
    id                    RAW(16) primary key,
    batch_id              RAW(16) not null,
    item_id               RAW(16),
    ledger_transaction_id RAW(16),
    mismatch_type         varchar2(50) not null,
    severity              varchar2(30) not null,
    status                varchar2(30) not null,
    detail                varchar2(1000),
    created_at            timestamp with time zone not null,
    resolved_at           timestamp with time zone,
    version               number(19, 0) default 0 not null,

    constraint fk_reconciliation_results_batch foreign key (batch_id) references settlement_batches (id),
    constraint fk_reconciliation_results_item foreign key (item_id) references settlement_items (id),
    constraint fk_reconciliation_results_ledger_tx foreign key (ledger_transaction_id) references ledger_transactions (id),
    constraint chk_reconciliation_mismatch_type check (
        mismatch_type in (
            'MATCHED',
            'MISSING_INTERNAL_TRANSACTION',
            'MISSING_EXTERNAL_SETTLEMENT',
            'AMOUNT_MISMATCH',
            'CURRENCY_MISMATCH',
            'STATUS_MISMATCH',
            'DUPLICATE_EXTERNAL_ITEM',
            'DUPLICATE_INTERNAL_TRANSACTION',
            'REVERSED_TRANSACTION_SETTLED',
            'SETTLEMENT_DATE_OUT_OF_WINDOW'
        )
    ),
    constraint chk_reconciliation_severity check (severity in ('INFO', 'WARNING', 'CRITICAL')),
    constraint chk_reconciliation_status check (status in ('OPEN', 'RESOLVED', 'IGNORED')),
    constraint chk_reconciliation_resolved_at check (
        (status in ('RESOLVED', 'IGNORED') and resolved_at is not null)
            or (status = 'OPEN' and resolved_at is null)
    ),
    constraint chk_reconciliation_version check (version >= 0)
);

create index idx_settlement_batches_status on settlement_batches (status);
create index idx_settlement_batches_source_imported on settlement_batches (source, imported_at);

create index idx_settlement_items_batch on settlement_items (batch_id);
create index idx_settlement_items_external_ref on settlement_items (external_transaction_reference);
create index idx_settlement_items_status on settlement_items (status);

create index idx_reconciliation_results_batch on reconciliation_results (batch_id);
create index idx_reconciliation_results_item on reconciliation_results (item_id);
create index idx_reconciliation_results_ledger_tx on reconciliation_results (ledger_transaction_id);
create index idx_reconciliation_results_mismatch on reconciliation_results (mismatch_type);
create index idx_reconciliation_results_status on reconciliation_results (status);
