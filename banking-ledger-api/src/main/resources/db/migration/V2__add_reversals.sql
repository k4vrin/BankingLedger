-- Phase 6 reversal data model.

create table reversals
(
    id                             RAW(16) primary key,
    original_transfer_id           RAW(16) not null,
    original_ledger_transaction_id RAW(16) not null,
    reversal_ledger_transaction_id RAW(16),
    reason_code                    varchar2(100) not null,
    reason_detail                  varchar2(1000),
    requested_by_actor_type        varchar2(30) not null,
    requested_by_actor_role        varchar2(50),
    requested_by_actor_id          varchar2(100),
    correlation_id                 varchar2(100),
    requested_at                   timestamp with time zone not null,
    completed_at                   timestamp with time zone,
    status                         varchar2(30) not null,
    failure_reason_code            varchar2(100),
    failure_reason_detail          varchar2(1000),
    created_at                     timestamp with time zone not null,
    updated_at                     timestamp with time zone not null,
    version                        number(19, 0) default 0 not null,

    constraint uk_reversals_original_transfer unique (original_transfer_id),
    constraint uk_reversals_original_ledger_tx unique (original_ledger_transaction_id),
    constraint uk_reversals_reversal_ledger_tx unique (reversal_ledger_transaction_id),
    constraint fk_reversals_original_transfer foreign key (original_transfer_id) references transfer_requests (id),
    constraint fk_reversals_original_ledger_tx foreign key (original_ledger_transaction_id) references ledger_transactions (id),
    constraint fk_reversals_reversal_ledger_tx foreign key (reversal_ledger_transaction_id) references ledger_transactions (id),
    constraint chk_reversals_status check (status in ('PENDING', 'COMPLETED', 'REJECTED', 'FAILED')),
    constraint chk_reversals_reason_code check (
        reason_code in ('CUSTOMER_REQUESTED', 'DUPLICATE_TRANSFER', 'FRAUD', 'OPERATIONAL_ERROR', 'COMPLIANCE', 'OTHER')
    ),
    constraint chk_reversals_actor_type check (requested_by_actor_type in ('CUSTOMER', 'TELLER', 'OPS_ADMIN', 'SERVICE', 'SYSTEM')),
    constraint chk_reversals_actor_role check (
        requested_by_actor_role is null
            or requested_by_actor_role in ('CUSTOMER', 'TELLER', 'AUDITOR', 'OPS_ADMIN', 'SERVICE', 'SYSTEM')
    ),
    constraint chk_reversals_completed_at check (
        (status = 'COMPLETED' and completed_at is not null and reversal_ledger_transaction_id is not null)
            or (status in ('PENDING', 'REJECTED', 'FAILED') and completed_at is null)
    ),
    constraint chk_reversals_failure_reason check (
        status not in ('REJECTED', 'FAILED') or failure_reason_code is not null
    ),
    constraint chk_reversals_version check (version >= 0)
);

create index idx_reversals_status_requested on reversals (status, requested_at);
create index idx_reversals_reason_requested on reversals (reason_code, requested_at);
create index idx_reversals_correlation_id on reversals (correlation_id);
