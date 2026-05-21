# ADR: Compose Multiplatform Client Architecture

## Status

Accepted

## Context

The Banking Ledger backend is the source of truth for accounts, transfers, reversals, adjustments, reconciliation, audit, ledger investigation, outbox operations, authentication, authorization, idempotency, and correlation tracing.

The project needs an optional Compose Multiplatform client that makes those backend capabilities easier to demo. The client should support Android and iOS customer workflows plus a desktop admin and auditor experience without becoming a production banking app or blocking backend completion.

The architecture must keep shared business and presentation behavior in common code while leaving platform-specific responsibilities, such as launchers, secure token persistence, desktop window behavior, and native integration, in platform modules.

## Decision

The Compose client lives in this repository beside the backend:

- `banking-ledger-api/` remains the Spring Boot backend.
- `banking-ledger-compose/` is the Compose Multiplatform app root.
- `docs/` holds shared architecture, roadmap, and demo documentation.

The first-version Compose app treats customer mobile and desktop admin as equal first-class targets:

- Android and iOS provide the customer app.
- Desktop provides the admin and auditor app.
- Shared code owns API integration, models, validation, presentation state, reusable UI primitives, and design tokens where the target behavior is the same.
- Platform modules own app entry points, secure token storage, platform navigation hosting, desktop window behavior, and platform-specific adapters.

## Stack

The Compose app uses a modern rich Compose Multiplatform stack:

- Compose Multiplatform for shared UI.
- Compose Material 3 for the visual system.
- Compose Multiplatform Resources with `Res`, not Android `R`, for shared strings and drawables.
- Ktor Client and Kotlinx Serialization for networking and JSON.
- Coroutines and `StateFlow` for reactive state.
- Koin for dependency injection across shared and platform modules.
- AndroidX/KMP ViewModel for shared presentation state.
- AndroidX Navigation 3 for type-safe multiplatform navigation.
- DataStore for non-sensitive local preferences.
- Platform secure-token adapters for auth token persistence.

Exact dependency coordinates and versions are intentionally not pinned in this ADR. Before adding any dependency to `commonMain`, implementation must verify that the artifact exists, supports Android, iOS, and desktop targets, and exposes the API shape being used.

## Presentation Architecture

The app uses MVI for customer, admin, and auditor workflows.

Each screen or workflow has:

- Immutable `State`.
- Sealed `Event` inputs for user actions.
- One-shot `Effect` outputs for navigation, snackbars, dialogs, and platform actions.
- A shared ViewModel where the behavior can run in common code.

Composable boundaries are:

- Route composables obtain ViewModels, collect state, collect effects, and bind navigation or platform APIs.
- Screen composables render state and emit callbacks.
- Leaf composables receive narrow state and specific callbacks.

Composables do not run network requests, enforce financial business rules, or mutate backend-facing state directly. Financial forms keep raw editable input text separate from parsed and validated values so typing states such as blank input, partial decimals, and invalid values remain representable.

## API And Error Boundaries

The client uses one shared `BankingApiClient` facade for backend communication across account, transfer, admin, audit, outbox, ledger investigation, and reconciliation workflows.

Protected requests attach the bearer token from the current session. Mutating requests generate and attach `X-Correlation-Id`. Transfer creation also generates and reuses an `Idempotency-Key` for the current submit attempt so retries can replay safely without duplicate money movement.

Backend `ApiErrorResponse` is mapped into shared UI error models. The client handles these response categories consistently:

- `401` clears or expires the session and routes to login.
- `403` renders a forbidden state or inline permission message.
- `404` renders not-found or missing-resource UI.
- `409` renders a conflict state, including idempotency conflicts and duplicate reversal conflicts.
- Validation errors map to field-level errors.
- Timeout and server errors show supportable error states with correlation id when available.

Client-side role checks are for usability only. Backend authorization remains authoritative.

## Shared Interfaces And Models

The shared architecture defines these public contracts:

- `SessionState`: unauthenticated, authenticated, and expired session states.
- `UserRole`: `CUSTOMER`, `OPS_ADMIN`, and `AUDITOR` using backend-compatible names.
- `TokenStore`: interface or `expect`/`actual` abstraction for secure token persistence.
- `BankingApiClient`: API facade for account, transfer, admin operation, audit, outbox, ledger investigation, and reconciliation calls.
- `RequestContext`: request metadata containing token, correlation id, and optional idempotency key.
- `ApiResult<Success, Failure>` or equivalent sealed result for success and failure handling.
- `UiError`: user-facing error model mapped from backend errors and network failures.
- `FieldValidationError`: form field validation model.
- `OperationResult`: financial mutation result model for success, replay, conflict, and failure states.
- Type-safe route objects for customer and admin destinations.

## Persistence Policy

The v1 app does not implement an offline financial data cache. Account, transfer, audit, reconciliation, ledger, and outbox data are read from the backend when needed.

Local persistence is limited to:

- Secure platform token/session storage through `TokenStore`.
- Non-sensitive demo preferences through DataStore.
- Temporary in-memory form state and operation state inside ViewModels.

No bearer token, signing key, customer private data, or sensitive payload is logged or stored in non-secure preferences.

## Testing Strategy

Shared unit tests cover:

- API error parsing from backend `ApiErrorResponse`.
- Correlation id creation for mutating requests.
- Idempotency key generation and reuse.
- Token and session state transitions.
- Money formatting and minor-unit conversion.
- Transfer form validation.
- Adjustment posting balance validation.

ViewModel tests cover:

- Customer account dashboard loading.
- Transfer creation success.
- Transfer idempotency replay.
- Transfer idempotency conflict.
- Admin reversal success and conflict.
- Audit filter state.
- Reconciliation import validation.

Platform smoke checks cover:

- Android customer app launches and reaches login.
- iOS customer app launches and reaches login.
- Desktop admin app launches and reaches the admin shell.
- Shared code compiles for Android, iOS, and desktop.

## Consequences

The chosen stack gives the demo app a realistic modern CMP architecture and keeps the customer and admin experiences coherent across targets. It increases initial setup work compared with a minimal client, but avoids re-deciding navigation, state ownership, dependency injection, error handling, and persistence during implementation.

Keeping the client in the same repository makes the portfolio demo easier to run and review. The tradeoff is that frontend and backend changes share repository history, so the Compose app should keep its Gradle build, README, and module boundaries independent from the backend.

Avoiding offline financial caching keeps v1 simpler and safer. If offline read support becomes useful later, it must be introduced through a separate ADR that defines cache scope, invalidation, data sensitivity, and reconciliation behavior.

## References

- [Compose Multiplatform resources](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-resources.html)
- [Compose Multiplatform navigation](https://kotlinlang.org/docs/multiplatform/compose-navigation.html)
- [Navigation 3 in Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
- [Ktor client engines](https://ktor.io/docs/client-engines.html)
- [KMP ViewModel](https://developer.android.com/kotlin/multiplatform/viewmodel)
- [DataStore releases](https://developer.android.com/jetpack/androidx/releases/datastore)
- [Koin Compose ViewModel](https://insert-koin.io/docs/reference/koin-compose/compose-viewmodel/)
