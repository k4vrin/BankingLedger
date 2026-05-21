# Banking Ledger API

The local API documentation is available after the backend starts:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Grouped specs:
  - `http://localhost:8080/v3/api-docs/customer`
  - `http://localhost:8080/v3/api-docs/ops`
  - `http://localhost:8080/v3/api-docs/audit`
  - `http://localhost:8080/v3/api-docs/dev`

Protected endpoints use JWT bearer authentication. In the `dev` profile, issue sample tokens through `POST /api/v1/dev/auth/tokens`.

## Common Headers

| Header | Required | Applies To | Purpose |
| --- | --- | --- | --- |
| `Authorization: Bearer <token>` | Yes, except health/OpenAPI/dev token endpoints | Protected APIs | Authenticates the caller and provides role/customer claims. |
| `X-Correlation-Id` | Required for ops mutations, optional elsewhere | Business APIs | Carries an operation id into audit rows, outbox events, logs, and error responses. |
| `Idempotency-Key` | Yes for transfer creation | `POST /api/v1/transfers` | Allows safe retry of the same transfer request without duplicate movement. |

## Endpoint Contracts

| Area | Endpoint | Roles | Notes |
| --- | --- | --- | --- |
| Accounts | `POST /api/v1/accounts` | `TELLER`, `OPS_ADMIN` | Creates a customer account from a DTO. |
| Accounts | `GET /api/v1/accounts/{accountId}` | Owner, `TELLER`, `OPS_ADMIN`, `AUDITOR` depending ownership policy | Returns account details. |
| Accounts | `GET /api/v1/accounts/by-number/{accountNumber}` | Owner or authorized staff | Looks up by account number. |
| Accounts | `GET /api/v1/accounts/{accountId}/balance` | Owner or authorized staff | Returns cached available and ledger balances. |
| Accounts | `GET /api/v1/accounts/{accountId}/transactions` | Owner or authorized staff | Returns paged posting summaries with optional time filters. |
| Transfers | `POST /api/v1/transfers` | Source account owner or authorized staff | Posts a double-entry internal transfer with idempotency. |
| Transfers | `GET /api/v1/transfers/{transferId}` | Owner or authorized staff | Returns transfer status and ledger link. |
| Reversals | `POST /api/v1/transfers/{transferId}/reverse` | `OPS_ADMIN`, `SERVICE` | Creates immutable reversing ledger entries. |
| Adjustments | `POST /api/v1/ops/adjustments` | `OPS_ADMIN`, `SERVICE` | Posts balanced operational adjustment lines. |
| Reconciliation | `POST /api/v1/ops/reconciliation/batches` | `OPS_ADMIN`, `SERVICE` | Imports external settlement rows and computes results. |
| Reconciliation | `GET /api/v1/ops/reconciliation/batches` | `AUDITOR`, `OPS_ADMIN` | Searches batches. |
| Reconciliation | `GET /api/v1/ops/reconciliation/batches/{batchId}` | `AUDITOR`, `OPS_ADMIN` | Returns batch detail. |
| Reconciliation | `GET /api/v1/ops/reconciliation/batches/{batchId}/results` | `AUDITOR`, `OPS_ADMIN` | Searches mismatch results. |
| Audit | `GET /api/v1/audit/events` | `AUDITOR`, `OPS_ADMIN` | Searches audit events by entity, actor, correlation id, and time. |
| Audit | `GET /api/v1/audit/events/{auditEventId}` | `AUDITOR`, `OPS_ADMIN` | Returns one audit event. |
| Investigation | `GET /api/v1/ops/ledger/transactions/{transactionId}` | `AUDITOR`, `OPS_ADMIN`, `SERVICE` | Connects transaction, journal, postings, transfer/reversal/adjustment, audit, and outbox data. |
| Outbox | `GET /api/v1/ops/outbox/events` | `AUDITOR`, `OPS_ADMIN` | Searches outbox events by status. |
| Outbox | `GET /api/v1/ops/outbox/events/{eventId}` | `AUDITOR`, `OPS_ADMIN` | Returns one outbox event. |
| Outbox | `POST /api/v1/ops/outbox/events/{eventId}/requeue` | `OPS_ADMIN`, `SERVICE` | Requeues retryable or forced outbox events. |

## Error Shape

All application, validation, authentication, authorization, not-found, idempotency, and concurrency failures use `ApiErrorResponse`:

```json
{
  "timestamp": "2026-05-21T10:15:30Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/transfers",
  "correlationId": "demo-correlation-id",
  "fieldErrors": [
    {
      "field": "amountMinor",
      "message": "amountMinor must be positive"
    }
  ]
}
```

Common error families:

- Validation: `400` with `VALIDATION_ERROR`, `INVALID_REQUEST`, or `MALFORMED_REQUEST`.
- Authentication: `401` with `AUTHENTICATION_REQUIRED`, `INVALID_ACCESS_TOKEN`, or `EXPIRED_ACCESS_TOKEN`.
- Authorization: `403` with `ACCESS_DENIED` or `FORBIDDEN_RESOURCE`.
- Not found: `404` with `RESOURCE_NOT_FOUND` or a domain-specific not-found code.
- Idempotency conflict: `409` with `IDEMPOTENCY_KEY_CONFLICT`.
- Concurrency conflict: `409` with `CONCURRENT_TRANSFER_CONFLICT`.

## Examples

The executable demo collection is in [demo-flow.http](../banking-ledger-api/http/demo-flow.http). It covers:

- Role-specific dev token generation.
- Account lookup and creation.
- Successful transfer creation.
- Duplicate idempotency replay.
- Idempotency conflict.
- Transfer reversal.
- Operational adjustment.
- Reconciliation import and mismatch query.
- Ledger investigation, audit query, and outbox query.
