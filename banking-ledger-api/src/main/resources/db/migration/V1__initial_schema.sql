-- Mini Core Banking Ledger - Initial Schema
-- Oracle-compatible Flyway migration

create table customers
(
    id                          RAW(16) primary key,
    external_customer_reference varchar2(100) not null,
    full_name                   varchar2(255) not null,
    email                       varchar2(320),
    status                      varchar2(30) default 'ACTIVE' not null,
    created_at                  timestamp with time zone not null,
    updated_at                  timestamp with time zone not null,
    version                     number(19, 0) default 0 not null,

    constraint uk_customers_external_ref unique (external_customer_reference),
    constraint uk_customers_email unique (email),
    constraint chk_customers_status check (status in ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    constraint chk_customers_version check (version >= 0)
);

create table accounts
(
    id                      RAW(16) primary key,
    customer_id             RAW(16) not null,
    account_number          varchar2(34) not null,
    account_type            varchar2(30) not null,
    account_category        varchar2(20) not null,
    status                  varchar2(30) not null,
    currency_code           char(3) not null,
--     cached balances, not the source of truth.
    available_balance_minor number(19, 0) default 0 not null,
    ledger_balance_minor    number(19, 0) default 0 not null,
    created_at              timestamp with time zone not null,
    updated_at              timestamp with time zone not null,
    version                 number(19, 0) default 0 not null,

    constraint uk_accounts_account_number unique (account_number),
    constraint uk_accounts_id_currency unique (id, currency_code),
    constraint fk_accounts_customer foreign key (customer_id) references customers (id),
    constraint chk_accounts_type check (account_type in ('CURRENT', 'SAVINGS', 'WALLET', 'SUSPENSE', 'FEE_INCOME', 'CLEARING')),
    constraint chk_accounts_category check (account_category in ('CUSTOMER', 'INTERNAL')),
    constraint chk_accounts_status check (status in ('ACTIVE', 'FROZEN', 'CLOSED')),
    constraint chk_accounts_currency_code check (regexp_like(currency_code, '^[A-Z]{3}$')),
    constraint chk_accounts_available_balance check (available_balance_minor >= 0),
    constraint chk_accounts_ledger_balance check (ledger_balance_minor >= 0),
    constraint chk_accounts_version check (version >= 0)
);

create table ledger_transactions
(
    id                    RAW(16) primary key,
    external_reference    varchar2(100),
    transaction_type      varchar2(50) not null,
    status                varchar2(30) not null,
    currency_code         char(3) not null,
    amount_minor          number(19, 0) not null,
    description           varchar2(500),
    failure_reason_code   varchar2(100),
    failure_reason_detail varchar2(1000),
    posted_at             timestamp with time zone,
    created_at            timestamp with time zone not null,
    updated_at            timestamp with time zone not null,
    version               number(19, 0) default 0 not null,

    constraint uk_ledger_transactions_external_ref unique (external_reference),
    constraint uk_ledger_transactions_id_currency unique (id, currency_code),
    constraint chk_ledger_transactions_status check (status in ('PENDING', 'POSTED', 'REJECTED', 'REVERSED', 'FAILED')),
    constraint chk_ledger_transactions_type check (transaction_type in ('TRANSFER', 'REVERSAL', 'FEE', 'ADJUSTMENT')),
    constraint chk_ledger_transactions_currency check (regexp_like(currency_code, '^[A-Z]{3}$')),
    constraint chk_ledger_transactions_amount check (amount_minor > 0),
    constraint chk_ledger_transactions_posted_at check (
        (status in ('POSTED', 'REVERSED') and posted_at is not null)
            or (status in ('PENDING', 'REJECTED', 'FAILED') and posted_at is null)
    ),
    constraint chk_ledger_transactions_failure check (
        status not in ('REJECTED', 'FAILED') or failure_reason_code is not null
    ),
    constraint chk_ledger_transactions_version check (version >= 0)
);

create table journal_entries
(
    id                    RAW(16) primary key,
    ledger_transaction_id RAW(16) not null,
    entry_type            varchar2(50) not null,
    currency_code         char(3) not null,
    total_debit_minor     number(19, 0) not null,
    total_credit_minor    number(19, 0) not null,
    description           varchar2(500),
    posted_at             timestamp with time zone not null,
    created_at            timestamp with time zone not null,
    version               number(19, 0) default 0 not null,

    constraint uk_journal_entries_id_currency unique (id, currency_code),
    constraint fk_journal_entries_transaction foreign key (ledger_transaction_id) references ledger_transactions (id),
    constraint fk_journal_entries_transaction_currency foreign key (ledger_transaction_id, currency_code) references ledger_transactions (id, currency_code),
    constraint chk_journal_entries_type check (entry_type in ('TRANSFER', 'REVERSAL', 'FEE', 'ADJUSTMENT')),
    constraint chk_journal_entries_currency check (regexp_like(currency_code, '^[A-Z]{3}$')),
    constraint chk_journal_entries_total_debit check (total_debit_minor > 0),
    constraint chk_journal_entries_total_credit check (total_credit_minor > 0),
    constraint chk_journal_entries_balanced check (total_debit_minor = total_credit_minor),
    constraint chk_journal_entries_version check (version >= 0)
);

create table postings
(
    id                    RAW(16) primary key,
    journal_entry_id      RAW(16) not null,
    account_id            RAW(16) not null,
    direction             varchar2(10) not null,
    currency_code         char(3) not null,
    amount_minor          number(19, 0) not null,
    posted_at             timestamp with time zone not null,
    created_at            timestamp with time zone not null,

    constraint fk_postings_journal_entry foreign key (journal_entry_id) references journal_entries (id),
    constraint fk_postings_journal_entry_currency foreign key (journal_entry_id, currency_code) references journal_entries (id, currency_code),
    constraint fk_postings_account foreign key (account_id) references accounts (id),
    constraint fk_postings_account_currency foreign key (account_id, currency_code) references accounts (id, currency_code),
    constraint chk_postings_direction check (direction in ('DEBIT', 'CREDIT')),
    constraint chk_postings_currency check (regexp_like(currency_code, '^[A-Z]{3}$')),
    constraint chk_postings_amount check (amount_minor > 0)
);

create table transfer_requests
(
    id                         RAW(16) primary key,
    source_account_id          RAW(16) not null,
    destination_account_id     RAW(16) not null,
    requested_by_customer_id   RAW(16),
    ledger_transaction_id      RAW(16),
    external_reference         varchar2(100),
    transfer_type              varchar2(50) not null,
    requested_by_actor_type    varchar2(30) not null,
    status                     varchar2(30) not null,
    currency_code              char(3) not null,
    amount_minor               number(19, 0) not null,
    description                varchar2(500),
    failure_reason_code        varchar2(100),
    failure_reason_detail      varchar2(1000),
    requested_at               timestamp with time zone not null,
    completed_at               timestamp with time zone,
    created_at                 timestamp with time zone not null,
    updated_at                 timestamp with time zone not null,
    version                    number(19, 0) default 0 not null,

    constraint uk_transfer_requests_external_ref unique (external_reference),
    constraint uk_transfer_requests_ledger_tx unique (ledger_transaction_id),
    constraint fk_transfer_source_account foreign key (source_account_id) references accounts (id),
    constraint fk_transfer_source_account_currency foreign key (source_account_id, currency_code) references accounts (id, currency_code),
    constraint fk_transfer_destination_account foreign key (destination_account_id) references accounts (id),
    constraint fk_transfer_destination_account_currency foreign key (destination_account_id, currency_code) references accounts (id, currency_code),
    constraint fk_transfer_requested_by_customer foreign key (requested_by_customer_id) references customers (id),
    constraint fk_transfer_ledger_transaction foreign key (ledger_transaction_id) references ledger_transactions (id),
    constraint fk_transfer_ledger_tx_currency foreign key (ledger_transaction_id, currency_code) references ledger_transactions (id, currency_code),
    constraint chk_transfer_different_accounts check (source_account_id <> destination_account_id),
    constraint chk_transfer_type check (transfer_type in ('INTERNAL', 'EXTERNAL', 'FEE', 'ADJUSTMENT')),
    constraint chk_transfer_actor_type check (requested_by_actor_type in ('CUSTOMER', 'TELLER', 'OPS_ADMIN', 'SERVICE', 'SYSTEM')),
    constraint chk_transfer_status check (status in ('PENDING', 'COMPLETED', 'REJECTED', 'REVERSED', 'FAILED')),
    constraint chk_transfer_currency check (regexp_like(currency_code, '^[A-Z]{3}$')),
    constraint chk_transfer_amount check (amount_minor > 0),
    constraint chk_transfer_completed_at check (
        (status in ('COMPLETED', 'REVERSED') and completed_at is not null)
            or (status in ('PENDING', 'REJECTED', 'FAILED') and completed_at is null)
    ),
    constraint chk_transfer_failure check (
        status not in ('REJECTED', 'FAILED') or failure_reason_code is not null
    ),
    constraint chk_transfer_version check (version >= 0)
);

create table audit_events
(
    id             RAW(16) primary key,
    event_type     varchar2(100) not null,
    entity_type    varchar2(100) not null,
    entity_id      RAW(16) not null,
    actor_id       varchar2(100),
    actor_role     varchar2(50),
    actor_type     varchar2(30) not null,
    channel        varchar2(50),
    correlation_id varchar2(100),
    event_payload  clob,
    created_at     timestamp with time zone not null,

    constraint chk_audit_actor_role check (actor_role is null or actor_role in ('CUSTOMER', 'TELLER', 'AUDITOR', 'OPS_ADMIN', 'SERVICE', 'SYSTEM')),
    constraint chk_audit_actor_type check (actor_type in ('CUSTOMER','EMPLOYEE','SERVICE','SYSTEM','EXTERNAL_PARTNER'))
);

create table idempotency_records
(
    id               RAW(16) primary key,
    operation_scope  varchar2(100) not null,
    idempotency_key  varchar2(255) not null,
    request_hash     varchar2(128) not null,
    response_status  number(3, 0) not null,
    response_body    clob,
    resource_type    varchar2(100),
    resource_id      RAW(16),
    created_at       timestamp with time zone not null,
    expires_at       timestamp with time zone not null,

    constraint uk_idempotency_scope_key unique (operation_scope, idempotency_key),
    constraint chk_idempotency_response_status check (response_status between 100 and 599),
    constraint chk_idempotency_expiry check (expires_at > created_at)
);

create table outbox_events
(
    id                  RAW(16) primary key,
    aggregate_type      varchar2(100) not null,
    aggregate_id        RAW(16) not null,
    event_type          varchar2(100) not null,
    destination         varchar2(200),
    correlation_id      varchar2(100),
    event_payload       clob not null,
    status              varchar2(30) not null,
    retry_count         number(10, 0) default 0 not null,
    next_retry_at       timestamp with time zone,
    last_error_message  varchar2(1000),
    created_at          timestamp with time zone not null,
    published_at        timestamp with time zone,
    version             number(19, 0) default 0 not null,

    constraint chk_outbox_status check (status in ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER')),
    constraint chk_outbox_retry_count check (retry_count >= 0),
    constraint chk_outbox_published_at check (
        (status = 'PUBLISHED' and published_at is not null)
            or (status <> 'PUBLISHED' and published_at is null)
    ),
    constraint chk_outbox_next_retry_at check (
        (status = 'FAILED' and next_retry_at is not null)
            or (status in ('PENDING', 'PUBLISHED', 'DEAD_LETTER'))
    ),
    constraint chk_outbox_version check (version >= 0)
);

-- Account lookup and history support
create index idx_accounts_customer_id on accounts (customer_id);
create index idx_accounts_status on accounts (status);
create index idx_accounts_currency on accounts (currency_code);

-- Ledger transaction lookup
create index idx_ledger_transactions_status on ledger_transactions (status);
create index idx_ledger_transactions_posted_at on ledger_transactions (posted_at);
create index idx_ledger_transactions_type_created on ledger_transactions (transaction_type, created_at);

-- Journal lookup
create index idx_journal_entries_transaction on journal_entries (ledger_transaction_id);
create index idx_journal_entries_posted_at on journal_entries (posted_at);

-- Posting lookup and account transaction history
create index idx_postings_journal_entry on postings (journal_entry_id);
create index idx_postings_account_posted on postings (account_id, posted_at);
create index idx_postings_account_created on postings (account_id, created_at);
create index idx_postings_account_direction on postings (account_id, direction);

-- Transfer lookup
create index idx_transfer_source_account on transfer_requests (source_account_id);
create index idx_transfer_destination_account on transfer_requests (destination_account_id);
create index idx_transfer_requested_by_customer on transfer_requests (requested_by_customer_id);
create index idx_transfer_type_created on transfer_requests (transfer_type, created_at);
create index idx_transfer_status_created on transfer_requests (status, created_at);

-- Audit investigation lookup
create index idx_audit_entity on audit_events (entity_type, entity_id);
create index idx_audit_event_type_created on audit_events (event_type, created_at);
create index idx_audit_correlation_id on audit_events (correlation_id);

-- Idempotency cleanup and replay lookup
create index idx_idempotency_expires_at on idempotency_records (expires_at);
create index idx_idempotency_resource on idempotency_records (resource_type, resource_id);

-- Outbox worker lookup
create index idx_outbox_status_retry on outbox_events (status, next_retry_at);
create index idx_outbox_aggregate on outbox_events (aggregate_type, aggregate_id);
create index idx_outbox_destination_status on outbox_events (destination, status);
create index idx_outbox_correlation_id on outbox_events (correlation_id);
create index idx_outbox_event_type_created on outbox_events (event_type, created_at);
