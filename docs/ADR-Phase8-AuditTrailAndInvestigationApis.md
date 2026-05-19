# ADR: Phase 8 Audit Trail And Investigation APIs

## Status

Accepted

## Context

Financial operations already create audit rows for account creation, ledger posting, transfer reversal, and adjustment posting. Phase 8 makes those rows queryable and adds investigation APIs that can connect a ledger transaction to its journal entry, postings, related operational request, audit events, and outbox events.

The API must support operational debugging without exposing sensitive request material such as bearer tokens, passwords, verification codes, secrets, signing keys, or full request bodies.

## Decision

Audit writes go through a single `AuditEventWriter` component. The writer accepts normalized enum values for event type and entity type, actor fields, channel, correlation id, and a small structured payload map. It serializes payloads with `ObjectMapper`, rejects sensitive payload keys, and participates in the caller transaction so rollback behavior stays aligned with the financial write.

Audit event query APIs are exposed as:

- `GET /api/v1/audit/events`
- `GET /api/v1/audit/events/{auditEventId}`

The collection endpoint supports filters for event type, entity type, entity id, actor type, actor role, actor id, correlation id, and created timestamp range. Results are paged and sorted deterministically by `createdAt desc, id desc`. The default page size is 20 and the maximum page size is 100.

Ledger investigation is exposed as:

- `GET /api/v1/ops/ledger/transactions/{transactionId}`

The response includes the ledger transaction, journal entry, postings, related transfer, related reversal, related adjustment, related audit event ids, and related outbox event summaries when available.

Role access follows the Phase 7 security model:

- Audit event APIs require `AUDITOR` or `OPS_ADMIN`.
- Ledger investigation requires `AUDITOR`, `OPS_ADMIN`, or `SERVICE`.

## Payload Policy

Allowed audit payload fields include identifiers, reason codes, statuses, amounts, currency codes, and high-level operation metadata.

Disallowed payload fields include authorization headers, bearer tokens, passwords, verification codes, secrets, signing material, and full request bodies by default. PII is not stored unless a future investigation use case explicitly requires it and documents the reason.

## Consequences

Audit events remain immutable through the API because only read endpoints are exposed. Existing financial workflows keep audit writes in the same transaction boundary as the business write. Investigation queries may perform several related lookups, which is acceptable for operational troubleshooting endpoints and can be optimized with fetch joins or projections later if production volume requires it.
