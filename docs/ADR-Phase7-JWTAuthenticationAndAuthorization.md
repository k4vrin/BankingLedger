# ADR: Phase 7 JWT Authentication And Authorization

## Status

Accepted.

## Context

Earlier phases used temporary actor headers such as `X-Actor-Type`, `X-Actor-Role`, and `X-Actor-Id` on protected financial workflows. That was useful while the ledger, transfer, reversal, and adjustment flows were being built, but it is not an acceptable authorization boundary because callers can forge those headers.

Phase 7 introduces local JWT bearer authentication for the portfolio version while keeping the design compatible with an external identity provider later.

## Decision

The API uses stateless Spring Security OAuth2 Resource Server support with locally signed HMAC JWTs for development and tests.

Accepted token claims:

| Claim | Required | Meaning |
| --- | --- | --- |
| `iss` | Yes | Token issuer. Defaults locally to `banking-ledger-local`. |
| `aud` | Yes | API audience. Defaults locally to `banking-ledger-api`. |
| `sub` | Yes | Authenticated subject. Missing subjects are rejected. |
| `roles` or `role` | Yes | One or more recognized roles. |
| `actorId` | No | Audit actor id. Defaults to `sub` when absent. |
| `customerId` | Required for `CUSTOMER` | Customer ownership id as a UUID. |
| `jti` | No | Token id for traceability. |
| `exp` | Yes | Expiration timestamp. |
| `nbf` | Optional | Not-before timestamp. |

Recognized roles are:

- `CUSTOMER`
- `TELLER`
- `AUDITOR`
- `OPS_ADMIN`
- `SERVICE`

JWTs are mapped to an `AuthenticatedPrincipal` containing subject, actor id, audit actor type, roles, optional customer id, and token id. Controller and method authorization now use this principal instead of trusting actor headers.

## Endpoint Authorization Matrix

| Endpoint group | Roles | Additional rule |
| --- | --- | --- |
| `/actuator/health`, OpenAPI docs, Swagger UI | Public | No token required. |
| `POST /api/v1/accounts` | `TELLER`, `OPS_ADMIN` | Actor fields are derived from JWT. |
| Account reads and account transactions | `CUSTOMER`, `TELLER`, `AUDITOR`, `OPS_ADMIN` | `CUSTOMER` must own the account. |
| `POST /api/v1/transfers` | `CUSTOMER`, `TELLER`, `OPS_ADMIN` | `CUSTOMER` must own the source account. |
| `GET /api/v1/transfers/{id}` | `CUSTOMER`, `TELLER`, `AUDITOR`, `OPS_ADMIN`, `SERVICE` | `CUSTOMER` must own source or destination account. |
| `POST /api/v1/transfers/{id}/reverse` | `OPS_ADMIN`, `SERVICE` | Actor fields are derived from JWT. |
| `POST /api/v1/ops/adjustments` | `OPS_ADMIN`, `SERVICE` | Actor fields are derived from JWT. |
| Development token issuance | Public in `dev` profile | Controller is disabled outside `dev`. |

Unclassified business endpoints require authentication by default.

## Development Token Issuance

In the `dev` profile, sample JWTs can be issued through:

```http
POST /api/v1/dev/auth/tokens
Content-Type: application/json

{
  "role": "OPS_ADMIN",
  "subject": "dev-ops-admin",
  "actorId": "ops-user-1"
}
```

For customer tokens, include `customerId`:

```http
POST /api/v1/dev/auth/tokens
Content-Type: application/json

{
  "role": "CUSTOMER",
  "subject": "dev-customer",
  "actorId": "customer-user-1",
  "customerId": "00000000-0000-0000-0000-000000000001"
}
```

The response contains `tokenType`, `accessToken`, and `expiresInSeconds`. Use the value as:

```http
Authorization: Bearer <accessToken>
```

## Security Error Shape

Authentication and authorization failures use the existing `ApiErrorResponse` structure. Responses include the current correlation id and avoid returning token values, signing keys, stack traces, or cryptographic details.

## Consequences

- Protected financial workflows no longer trust request actor headers.
- Customer ownership checks prevent cross-customer account and transfer access.
- Unauthorized requests are rejected before controller logic runs in the full Spring Security filter chain.
- A future external identity provider can replace local signing by supplying equivalent claims and issuer/audience validation.
