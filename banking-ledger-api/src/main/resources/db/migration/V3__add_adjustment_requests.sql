-- Phase 6 adjustment request data model.

create table adjustment_requests
(
    id                      RAW(16) primary key,
    ledger_transaction_id   RAW(16),
    reason_code             varchar2(100) not null,
    reason_detail           varchar2(1000),
    requested_by_actor_type varchar2(30) not null,
    requested_by_actor_role varchar2(50),
    requested_by_actor_id   varchar2(100),
    correlation_id          varchar2(100),
    requested_at            timestamp with time zone not null,
    completed_at            timestamp with time zone,
    status                  varchar2(30) not null,
    failure_reason_code     varchar2(100),
    failure_reason_detail   varchar2(1000),
    created_at              timestamp with time zone not null,
    updated_at              timestamp with time zone not null,
    version                 number(19, 0) default 0 not null,

    constraint uk_adjustment_requests_ledger_tx unique (ledger_transaction_id),
    constraint fk_adjustment_requests_ledger_tx foreign key (ledger_transaction_id) references ledger_transactions (id),
    constraint chk_adjustment_requests_status check (status in ('PENDING', 'COMPLETED', 'REJECTED', 'FAILED')),
    constraint chk_adjustment_requests_reason check (
        reason_code in (
            'MANUAL_CORRECTION',
            'FEE_CORRECTION',
            'INTEREST_CORRECTION',
            'SETTLEMENT_CORRECTION',
            'RECONCILIATION',
            'OTHER'
        )
    ),
    constraint chk_adjustment_requests_actor_type check (
        requested_by_actor_type in ('CUSTOMER', 'TELLER', 'OPS_ADMIN', 'SERVICE', 'SYSTEM')
    ),
    constraint chk_adjustment_requests_actor_role check (
        requested_by_actor_role is null
            or requested_by_actor_role in ('CUSTOMER', 'TELLER', 'AUDITOR', 'OPS_ADMIN', 'SERVICE', 'SYSTEM')
    ),
    constraint chk_adjustment_requests_completed check (
        (status = 'COMPLETED' and completed_at is not null and ledger_transaction_id is not null)
            or (status in ('PENDING', 'REJECTED', 'FAILED') and completed_at is null)
    ),
    constraint chk_adjustment_requests_failure check (
        status not in ('REJECTED', 'FAILED') or failure_reason_code is not null
    ),
    constraint chk_adjustment_requests_version check (version >= 0)
);

create index idx_adjustment_requests_status_requested on adjustment_requests (status, requested_at);
create index idx_adjustment_requests_reason_requested on adjustment_requests (reason_code, requested_at);
create index idx_adjustment_requests_correlation_id on adjustment_requests (correlation_id);
