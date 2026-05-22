# Compose Multiplatform Roadmap: Customer Mobile And Admin Desktop

This roadmap is optional and separate from the backend roadmap. Its purpose is to create a polished Compose Multiplatform demo client for the Banking Ledger API with Android and iOS customer apps plus a desktop admin app.

Architectural decisions for this roadmap are locked in [ADR: Compose Multiplatform Client Architecture](ADR-ComposeMultiplatformArchitecture.md). The Compose client lives in this monorepo at `banking-ledger-compose/`, uses shared Compose Multiplatform UI, treats customer mobile and desktop admin as equal first-class targets, and does not implement offline financial data caching in v1.

Use the checkboxes as the implementation tracker.

## Phase 0: Compose Multiplatform Project Foundation

Goal: Create a reliable Kotlin Multiplatform foundation that can target Android, iOS, and desktop without forcing platform-specific behavior into shared code.

### Steps

- [x] Create repository layout:
    - [x] Add the Compose app root at `banking-ledger-compose/`.
    - [x] Keep `banking-ledger-api/` as the Spring Boot backend.
    - [x] Keep architecture, roadmap, and demo documentation in `docs/`.
    - [x] Document backend API base URL configuration per platform.
    - [x] Document required JDK, Kotlin, Gradle, Android Studio, and Xcode versions.
- [x] Scaffold the Compose Multiplatform app:
    - [x] Add Android target for the customer app.
    - [x] Add iOS target for the customer app.
    - [x] Add desktop target for the admin app.
    - [x] Add shared `commonMain` source set for business, networking, state, and reusable UI.
    - [x] Add platform source sets only for platform bridges and launchers.
- [x] Configure build conventions:
    - [x] Add version catalog entries for Kotlin, Compose Multiplatform, Compose Material 3, Compose Resources, Ktor Client, Kotlinx Serialization, coroutines, Koin, AndroidX/KMP ViewModel, AndroidX Navigation 3, DataStore, and testing libraries.
    - [x] Do not pin dependency versions from the ADR; verify current coordinates during implementation.
    - [x] Verify every dependency added to `commonMain` supports Android, iOS, and desktop.
    - [x] Keep Android-only and desktop-only dependencies out of `commonMain`.
    - [x] Add Gradle tasks for Android build, iOS framework build, desktop run, and shared tests.
- [ ] Add base module structure:
    - [ ] `shared:core:model` for domain-facing models and value objects.
    - [ ] `shared:core:network` for API client infrastructure.
    - [ ] `shared:core:security` for token, role, and request context models.
    - [ ] `shared:core:persistence` for DataStore preferences and token-store abstractions.
    - [ ] `shared:core:designsystem` for theme, typography, dimensions, and reusable components.
    - [ ] `shared:core:navigation` for type-safe route objects.
    - [ ] `shared:feature:customer` for customer presentation state and screens.
    - [ ] `shared:feature:admin` for admin presentation state and screens.
    - [ ] Platform app modules for Android, iOS, and desktop launchers.
- [ ] Add developer scripts:
    - [ ] Build Android debug app.
    - [ ] Run desktop app.
    - [ ] Build shared tests.
    - [ ] Run formatting or linting.
    - [ ] Build iOS framework or Xcode project integration.

### Test Scenarios

- [ ] Shared code compiles for Android, iOS, and desktop targets.
- [ ] Android app launches locally.
- [ ] iOS app launches in simulator.
- [ ] Desktop admin app launches locally.
- [x] Shared unit test task succeeds.
- [ ] Platform launchers can read backend base URL configuration.

### Acceptance Criteria

- [ ] Compose Multiplatform project exists and builds for all planned targets.
- [x] Project layout follows `banking-ledger-compose/` as required by the CMP architecture ADR.
- [x] Shared source sets are clearly separated from platform source sets.
- [x] Dependency target support is documented before libraries are used in `commonMain`.
- [x] Local setup commands are documented.

### Foundation Notes

- Current scaffold uses `shared`, `androidApp`, `iosApp`, and `desktopApp` modules.
- Dependency versions were checked against official release pages, Maven Central, Google Maven, and Gradle resolution on May 22, 2026.
- `commonMain` dependency resolution, shared JVM tests, desktop Kotlin compilation, and Android debug assembly were verified by `./gradlew :shared:compileKotlinMetadata :shared:jvmTest :desktopApp:compileKotlin :androidApp:assembleDebug`.
- iOS simulator framework linking reached Kotlin/Native compilation but is blocked locally by Xcode command line tool configuration: `/usr/bin/xcrun -f ld` exits with code 69.
- AndroidX Navigation 3 uses the JetBrains-published multiplatform `org.jetbrains.androidx.navigation3:navigation3-ui` artifact because Google `androidx.navigation3:navigation3-ui` does not currently resolve iOS variants for this project.

## Phase 1: Shared Architecture, MVI, And Navigation

Goal: Establish predictable unidirectional data flow for customer and admin workflows.

### Steps

- [ ] Implement the ADR presentation pattern:
    - [ ] Use MVI for customer, admin, and auditor workflows.
    - [ ] Model each screen with `State`, `Event`, and `Effect`.
    - [ ] Use AndroidX/KMP ViewModel for shared presentation state.
    - [ ] Use one shared ViewModel per screen or workflow where behavior can run in common code.
    - [ ] Keep business rules out of composables.
- [ ] Add shared state primitives:
    - [ ] Immutable screen state data classes.
    - [ ] Sealed event interfaces for user actions.
    - [ ] One-shot effects for navigation, snackbar, dialog triggers, and platform actions.
    - [ ] Loading, refreshing, empty, forbidden, and error state models.
- [ ] Add shared public contracts:
    - [ ] `SessionState` for unauthenticated, authenticated, and expired sessions.
    - [ ] `UserRole` with backend-compatible `CUSTOMER`, `OPS_ADMIN`, and `AUDITOR` values.
    - [ ] `UiError` mapped from backend errors and network failures.
    - [ ] `FieldValidationError` for form validation.
    - [ ] `OperationResult` for financial mutation success, replay, conflict, and failure states.
- [ ] Add route and screen boundaries:
    - [ ] Route composables collect state and effects.
    - [ ] Screen composables render state and emit callbacks.
    - [ ] Leaf composables receive narrow state and specific callbacks.
    - [ ] Keep scroll, focus, and animation state local unless business-significant.
- [ ] Define navigation models:
    - [ ] Use AndroidX Navigation 3 for type-safe multiplatform navigation.
    - [ ] Add type-safe route objects for customer destinations.
    - [ ] Add type-safe route objects for admin and auditor destinations.
    - [ ] Semantic navigation effects from ViewModels.
    - [ ] Platform navigation adapters in launcher modules.
- [ ] Add role-aware access model:
    - [ ] Customer role for mobile workflows.
    - [ ] Ops admin role for reversal, adjustment, reconciliation, and outbox workflows.
    - [ ] Auditor role for audit and ledger investigation workflows.
    - [ ] Shared forbidden state for unauthorized screens and actions.

### Test Scenarios

- [ ] ViewModel event updates state without UI dependencies.
- [ ] Navigation effects are emitted once.
- [ ] Forbidden state renders for unauthorized role.
- [ ] Route composable does not contain business logic.
- [ ] Screen composable can be previewed with fake state.

### Acceptance Criteria

- [ ] Customer and admin features follow one coherent MVI structure.
- [ ] Presentation state is testable in `commonTest`.
- [ ] AndroidX Navigation 3 route objects are defined for customer and admin destinations.
- [ ] Platform-specific navigation remains outside core ViewModel logic.
- [ ] Composables are stateless except for visual-local state.

## Phase 2: Design System, App Shells, And Adaptive UI

Goal: Build a shared visual foundation that supports calm customer mobile workflows and dense desktop admin workflows.

### Steps

- [ ] Define design direction:
    - [ ] Customer mobile app is clear, calm, and optimized for common account actions.
    - [ ] Desktop admin app is dense, operational, and optimized for scanning tables and detail panes.
    - [ ] Avoid marketing-style landing pages.
    - [ ] Use real workflow screens as the first experience.
- [ ] Add shared design tokens:
    - [ ] Color scheme for background, surface, primary, secondary, error, warning, success, border, and muted content.
    - [ ] Typography scale suitable for mobile and desktop.
    - [ ] Spacing and size tokens.
    - [ ] Status badge variants for account, transfer, reversal, adjustment, outbox, and reconciliation statuses.
    - [ ] Money, date, and relative time display utilities.
- [ ] Add shared resource strategy:
    - [ ] Use Compose Multiplatform Resources with `Res` for shared strings and drawables.
    - [ ] Keep Android `R` resource access out of shared UI code.
    - [ ] Add platform-only resources only when the resource cannot be shared.
- [ ] Build shared components:
    - [ ] App top bar.
    - [ ] Mobile bottom navigation or navigation drawer.
    - [ ] Desktop sidebar navigation.
    - [ ] Page header.
    - [ ] Filter toolbar.
    - [ ] Data table shell for desktop.
    - [ ] Compact list rows for mobile.
    - [ ] Detail section.
    - [ ] Loading, empty, error, and forbidden states.
    - [ ] Confirmation dialog for destructive operations.
- [ ] Add adaptive layout rules:
    - [ ] Mobile uses single-column flows and compact rows.
    - [ ] Tablet can use list-detail layouts where practical.
    - [ ] Desktop admin uses persistent navigation, tables, and split detail panes.
    - [ ] Large financial forms keep labels and validation visible.

### Test Scenarios

- [ ] Customer mobile shell renders on narrow width.
- [ ] Desktop admin shell renders with persistent sidebar.
- [ ] Status badges render text and color variants.
- [ ] Money formatter preserves minor-unit correctness.
- [ ] Loading states do not shift core layouts.
- [ ] Dialogs have accessible titles and keyboard behavior.

### Acceptance Criteria

- [ ] Shared design system supports all target platforms.
- [ ] Shared strings and drawables use Compose Multiplatform Resources.
- [ ] Customer and admin shells reuse common primitives where useful.
- [ ] UI states are consistent across workflows.
- [ ] Accessibility expectations are documented for controls, dialogs, lists, and tables.

## Phase 3: API Client, Auth, Error Handling, And Local Persistence

Goal: Connect the Compose clients to the backend with consistent request, auth, idempotency, and error behavior.

### Steps

- [ ] Add shared API client:
    - [ ] Centralize backend base URL.
    - [ ] Add `BankingApiClient` facade for account, transfer, admin operation, audit, outbox, ledger investigation, and reconciliation calls.
    - [ ] Add `ApiResult<Success, Failure>` or equivalent sealed result for success and failure handling.
    - [ ] Use typed DTOs with Kotlin serialization.
    - [ ] Parse backend `ApiErrorResponse`.
    - [ ] Add request timeout and cancellation behavior.
    - [ ] Avoid leaking bearer tokens in logs.
- [ ] Add request context:
    - [ ] Add `RequestContext` containing token, correlation id, and optional idempotency key.
    - [ ] Attach bearer token to protected requests.
    - [ ] Generate and attach `X-Correlation-Id` for mutating requests.
    - [ ] Generate and reuse `Idempotency-Key` for the current transfer submit attempt.
    - [ ] Preserve correlation id for operation result screens.
- [ ] Add demo authentication:
    - [ ] Login screen for demo roles.
    - [ ] Customer role for mobile app.
    - [ ] Ops admin and auditor roles for desktop app.
    - [ ] Use backend-issued dev JWT tokens for demo authentication.
    - [ ] Add `TokenStore` interface or `expect`/`actual` abstraction for secure token persistence.
    - [ ] Logout and expired token handling.
- [ ] Add error mapping:
    - [ ] Map validation errors to form fields.
    - [ ] Map `401` to login.
    - [ ] Map `403` to forbidden.
    - [ ] Map `404` to not-found or inline missing state.
    - [ ] Map `409` to conflict alerts.
    - [ ] Map server errors to supportable error states with correlation id.
- [ ] Add local persistence:
    - [ ] Store tokens and session material only through secure platform token adapters.
    - [ ] Store non-sensitive demo preferences through DataStore.
    - [ ] Do not implement offline financial data caching in v1.
    - [ ] Do not store bearer tokens, customer private data, or sensitive payloads in DataStore.

### Test Scenarios

- [ ] API client attaches bearer token.
- [ ] `BankingApiClient` returns shared success/error results.
- [ ] Mutating requests include correlation id.
- [ ] Transfer creation reuses the same idempotency key for the current retry attempt.
- [ ] Backend error response maps to UI error model.
- [ ] Validation errors render under fields.
- [ ] `401` clears session and routes to login.
- [ ] `403` renders forbidden state.
- [ ] Token values are not logged.

### Acceptance Criteria

- [ ] Network integration is centralized and testable.
- [ ] Auth state is available to customer and admin flows.
- [ ] Financial mutations use correlation and idempotency consistently.
- [ ] Local persistence follows the ADR token/DataStore split.
- [ ] Shared error handling gives clear UI states on every target platform.

## Phase 4: Customer Mobile App For Android And iOS

Goal: Provide customer-facing account, transaction, and transfer workflows on Android and iOS.

### Steps

- [ ] Build customer dashboard:
    - [ ] Account summary.
    - [ ] Available and ledger balance overview.
    - [ ] Recent transactions.
    - [ ] Recent transfers.
    - [ ] Quick action to create transfer.
    - [ ] Empty state for new customer.
    - [ ] Load data from backend on demand rather than an offline financial cache.
- [ ] Build account list:
    - [ ] Show masked account number or identifier.
    - [ ] Show account type.
    - [ ] Show account status.
    - [ ] Show available and ledger balances.
    - [ ] Navigate to account detail.
- [ ] Build account detail:
    - [ ] Show account metadata.
    - [ ] Show balances.
    - [ ] Show latest postings.
    - [ ] Navigate to full transaction list.
- [ ] Build account transaction list:
    - [ ] Paginated or incremental loading list.
    - [ ] Filter by date range.
    - [ ] Filter by posting direction.
    - [ ] Show ledger transaction id where useful.
    - [ ] Show amount, currency, direction, and timestamp.
- [ ] Build transfer list:
    - [ ] Paginated or incremental loading list.
    - [ ] Filter by status.
    - [ ] Filter by date range.
    - [ ] Show source, destination, amount, currency, and status.
    - [ ] Navigate to transfer detail.
- [ ] Build transfer creation form:
    - [ ] Select source account.
    - [ ] Enter destination account id or account number.
    - [ ] Enter amount as raw editable text.
    - [ ] Select or infer currency.
    - [ ] Enter description.
    - [ ] Generate idempotency key.
    - [ ] Validate before submit.
    - [ ] Prevent duplicate pending submit.
    - [ ] Show success result.
    - [ ] Show idempotency replay and conflict results.
- [ ] Build transfer detail:
    - [ ] Show transfer status.
    - [ ] Show source and destination.
    - [ ] Show amount and currency.
    - [ ] Show ledger transaction id.
    - [ ] Show failure reason.
    - [ ] Show reversal status when applicable.

### Test Scenarios

- [ ] Customer dashboard loads account and transfer summaries.
- [ ] Account list renders active, frozen, and closed statuses.
- [ ] Account detail shows correct balances.
- [ ] Transaction filters update screen state.
- [ ] Transfer amount input preserves raw editable text while validating parsed value.
- [ ] Transfer form prevents duplicate pending submit.
- [ ] Successful transfer navigates to transfer detail.
- [ ] Idempotency replay is visible to demo user.
- [ ] Idempotency conflict shows backend error code.
- [ ] Customer app does not expose admin-only navigation.

### Acceptance Criteria

- [ ] Customer can view accounts on Android and iOS.
- [ ] Customer can inspect account transactions.
- [ ] Customer can create a transfer.
- [ ] Customer can inspect transfer results and errors.
- [ ] Customer financial data is not cached offline in v1.
- [ ] Customer mobile app is usable on common phone sizes.

## Phase 5: Desktop Admin Operations App

Goal: Provide desktop-first operational workflows for transfer reversal, adjustment posting, and investigation entry points.

### Steps

- [ ] Build admin dashboard:
    - [ ] Operational metric panels.
    - [ ] Failed transfer count.
    - [ ] Reversed transfer count.
    - [ ] Recent adjustments.
    - [ ] Pending outbox count.
    - [ ] Reconciliation mismatch count.
    - [ ] Load operational summaries from backend on demand rather than an offline cache.
- [ ] Build admin transfer search:
    - [ ] Search by transfer id.
    - [ ] Search by external reference.
    - [ ] Filter by status.
    - [ ] Filter by date range.
    - [ ] Link to transfer detail.
- [ ] Build admin transfer detail:
    - [ ] Show transfer metadata.
    - [ ] Show ledger transaction id.
    - [ ] Show source and destination accounts.
    - [ ] Show audit correlation id when available.
    - [ ] Show reversal action only when eligible.
- [ ] Build reversal flow:
    - [ ] Confirmation dialog.
    - [ ] Reason code select.
    - [ ] Optional reason detail.
    - [ ] Submit reversal.
    - [ ] Show success response.
    - [ ] Refresh transfer status.
    - [ ] Link reversal ledger transaction.
- [ ] Build adjustment flow:
    - [ ] Adjustment posting form.
    - [ ] Add posting line.
    - [ ] Remove posting line.
    - [ ] Debit and credit direction select.
    - [ ] Account id input or lookup.
    - [ ] Amount input as raw editable text.
    - [ ] Currency input.
    - [ ] Reason code select.
    - [ ] Reason detail textarea.
    - [ ] Client-side balanced total validation.
    - [ ] Submit adjustment.
    - [ ] Show adjustment and ledger transaction ids.
- [ ] Add operation result states:
    - [ ] Success notification plus persistent result panel.
    - [ ] Validation errors mapped to fields.
    - [ ] Conflict alerts for duplicate reversal or business conflict.
    - [ ] Retry guidance for lock or concurrency conflicts.

### Test Scenarios

- [ ] Admin dashboard shows summary panels.
- [ ] Transfer search applies filters.
- [ ] Reversal action is hidden for non-completed transfer.
- [ ] Reversal dialog requires reason code.
- [ ] Successful reversal updates transfer status to reversed.
- [ ] Duplicate reversal shows conflict alert.
- [ ] Adjustment form requires at least two posting lines.
- [ ] Adjustment form detects unbalanced postings.
- [ ] Successful adjustment shows ledger transaction id.
- [ ] Unauthorized role sees forbidden state.

### Acceptance Criteria

- [ ] Ops admin can reverse eligible transfers from desktop.
- [ ] Ops admin can post balanced adjustments.
- [ ] Admin operation errors are understandable.
- [ ] Admin workflows clearly link operations to ledger investigation.
- [ ] Admin financial and operational data is not cached offline in v1.

## Phase 6: Audit, Ledger Investigation, Outbox, Reconciliation, And Reports

Goal: Make backend traceability and reconciliation workflows visible in the desktop admin app.

### Steps

- [ ] Build audit event list:
    - [ ] Paginated table.
    - [ ] Filter by event type.
    - [ ] Filter by entity type.
    - [ ] Filter by entity id.
    - [ ] Filter by actor.
    - [ ] Filter by correlation id.
    - [ ] Filter by date range.
    - [ ] Link to audit detail.
- [ ] Build audit event detail:
    - [ ] Show event metadata.
    - [ ] Show actor fields.
    - [ ] Show correlation id.
    - [ ] Show payload with safe formatting.
    - [ ] Link related entity where possible.
- [ ] Build ledger investigation page:
    - [ ] Search by ledger transaction id.
    - [ ] Show ledger transaction metadata.
    - [ ] Show journal entry.
    - [ ] Show postings table.
    - [ ] Show related transfer.
    - [ ] Show related reversal.
    - [ ] Show related adjustment.
    - [ ] Show related audit events.
    - [ ] Show related outbox events.
- [ ] Build outbox monitor:
    - [ ] Paginated table.
    - [ ] Filter by status.
    - [ ] Filter by event type.
    - [ ] Filter by aggregate id.
    - [ ] Show retry count.
    - [ ] Show last error.
    - [ ] Show published timestamp.
    - [ ] Add replay action if backend supports it.
- [ ] Build reconciliation workflow:
    - [ ] Batch list with status and counts.
    - [ ] JSON import form or pasted payload.
    - [ ] Batch detail with settlement items.
    - [ ] Mismatch table with filters.
    - [ ] Links from mismatch rows to ledger investigation when available.
- [ ] Build reports page:
    - [ ] Daily trial balance panel.
    - [ ] Account statement panel.
    - [ ] Reconciliation mismatch report panel.
    - [ ] Suspense aging report panel.
    - [ ] Failed transfer reasons panel.
    - [ ] Link to backend report docs when API data is unavailable.

### Test Scenarios

- [ ] Audit list filters by event type.
- [ ] Audit list filters by correlation id.
- [ ] Audit detail renders payload safely.
- [ ] Ledger investigation shows transaction and postings.
- [ ] Outbox monitor shows pending, published, failed, and dead-lettered statuses.
- [ ] Reconciliation import rejects invalid JSON.
- [ ] Reconciliation batch detail shows mismatch results.
- [ ] Mismatch result links to investigation when ledger id exists.
- [ ] Unauthorized customer cannot access desktop admin routes.

### Acceptance Criteria

- [ ] Auditor can query audit trail.
- [ ] Admin can inspect a ledger transaction end to end.
- [ ] Outbox status is visible for demo operations.
- [ ] Admin can import and inspect reconciliation batches.
- [ ] Reports page supports demo storytelling.

## Phase 7: Demo Mode And Guided Flow

Goal: Make the Compose apps useful for a short, repeatable portfolio demo across mobile and desktop targets.

### Steps

- [ ] Add demo role entry points:
    - [ ] Customer demo login on Android and iOS.
    - [ ] Ops admin demo login on desktop.
    - [ ] Auditor demo login on desktop.
    - [ ] Backend connection status.
- [ ] Add guided demo checklist:
    - [ ] Login as customer.
    - [ ] View balances.
    - [ ] Create transfer.
    - [ ] Replay transfer with same idempotency key.
    - [ ] Trigger idempotency conflict.
    - [ ] Open desktop admin app.
    - [ ] Reverse transfer.
    - [ ] Post adjustment.
    - [ ] Import reconciliation batch.
    - [ ] Query audit by correlation id.
    - [ ] Open ledger investigation.
    - [ ] Inspect outbox events.
- [ ] Add demo data helpers:
    - [ ] Show seeded account ids.
    - [ ] Show sample transfer values.
    - [ ] Show sample adjustment posting lines.
    - [ ] Show sample reconciliation JSON.
    - [ ] Provide copy buttons where platform support is available.
- [ ] Add backend readiness checks:
    - [ ] Check API health.
    - [ ] Check auth or token endpoint if available.
    - [ ] Show clear setup instructions when backend is unavailable.
- [ ] Add screenshots and fallback documentation:
    - [ ] Android customer dashboard.
    - [ ] iOS transfer creation flow.
    - [ ] Desktop admin dashboard.
    - [ ] Desktop reversal dialog.
    - [ ] Desktop adjustment form.
    - [ ] Desktop audit and ledger investigation.

### Test Scenarios

- [ ] Demo flow can be completed with seeded data.
- [ ] Backend offline state shows setup guidance.
- [ ] Idempotency replay and conflict are visible.
- [ ] Desktop admin can continue from a mobile-created transfer.
- [ ] Demo flow remains under 10 minutes.

### Acceptance Criteria

- [ ] Reviewer can follow a guided demo without knowing the codebase.
- [ ] Demo highlights idempotency, reversal, adjustment, audit, reconciliation, and outbox.
- [ ] Mobile and desktop apps tell one coherent workflow story.
- [ ] Offline screenshots still communicate project value.

## Phase 8: Testing, Accessibility, Performance, And Quality

Goal: Add enough confidence that the Compose clients are reliable, accessible, and pleasant to demo.

### Steps

- [ ] Add shared unit tests:
    - [ ] API client error parsing.
    - [ ] Correlation id creation for mutating requests.
    - [ ] Money formatting.
    - [ ] Status badge mapping.
    - [ ] Role navigation mapping.
    - [ ] Idempotency key generation and reuse behavior.
    - [ ] Token and session state transitions.
    - [ ] Transfer form validation.
    - [ ] Adjustment posting line balancing.
- [ ] Add ViewModel tests:
    - [ ] Customer dashboard loading.
    - [ ] Transfer creation success.
    - [ ] Transfer validation failure.
    - [ ] Idempotency replay and conflict.
    - [ ] Reversal success and conflict.
    - [ ] Audit filter state.
    - [ ] Reconciliation import validation.
- [ ] Add platform UI checks:
    - [ ] Android smoke test for customer flow.
    - [ ] iOS simulator smoke test for customer flow.
    - [ ] Desktop smoke test for admin flow.
    - [ ] Screenshot checks for important screens where practical.
- [ ] Add accessibility checks:
    - [ ] Touch targets are large enough on mobile.
    - [ ] Buttons and fields have labels.
    - [ ] Dialogs have titles.
    - [ ] Tables and lists are screen-reader understandable.
    - [ ] Status is not communicated by color alone.
    - [ ] Focus order works on desktop.
- [ ] Add performance checks:
    - [ ] Lazy lists use stable keys.
    - [ ] Large tables use pagination or incremental loading.
    - [ ] Recomposition hotspots are investigated before adding optimizations.
    - [ ] Network refresh preserves existing content where practical.

### Test Scenarios

- [ ] Shared unit test suite passes.
- [ ] ViewModel test suite passes.
- [ ] Android debug build succeeds.
- [ ] iOS framework or simulator build succeeds.
- [ ] Desktop app build succeeds.
- [ ] Demo-critical screens pass accessibility review.
- [ ] No token or secret appears in logs.
- [ ] Shared code compiles for Android, iOS, and desktop.

### Acceptance Criteria

- [ ] Core state and validation logic are covered in shared tests.
- [ ] Platform smoke checks cover Android, iOS, and desktop targets.
- [ ] Accessibility issues are addressed for demo-critical routes.
- [ ] Demo flow is repeatable.

## Phase 9: Packaging, Distribution, And Documentation

Goal: Make the Compose Multiplatform clients easy to run, evaluate, and discuss.

### Steps

- [ ] Add Compose app README:
    - [ ] Setup instructions.
    - [ ] Environment configuration.
    - [ ] Backend dependency requirements.
    - [ ] Android run command.
    - [ ] iOS run instructions.
    - [ ] Desktop run command.
    - [ ] Test commands.
    - [ ] Demo walkthrough.
- [ ] Update root documentation:
    - [ ] Link Compose architecture ADR.
    - [ ] Link Compose roadmap.
    - [ ] Clarify Compose app is optional.
    - [ ] Clarify backend remains source of truth.
    - [ ] Explain relationship to the optional web frontend.
- [ ] Add packaging notes:
    - [ ] Android debug APK generation.
    - [ ] iOS simulator instructions.
    - [ ] Desktop distribution format.
    - [ ] Signing and notarization as optional future work.
- [ ] Add screenshots:
    - [ ] Customer dashboard.
    - [ ] Transfer form.
    - [ ] Transfer detail.
    - [ ] Admin dashboard.
    - [ ] Reversal dialog.
    - [ ] Adjustment form.
    - [ ] Audit query.
    - [ ] Ledger investigation.
    - [ ] Reconciliation results.
- [ ] Add demo script:
    - [ ] Step-by-step mobile flow.
    - [ ] Step-by-step desktop admin flow.
    - [ ] Expected backend state changes.
    - [ ] Troubleshooting section.
    - [ ] Reset instructions.

### Test Scenarios

- [ ] README commands work.
- [ ] Screenshot links resolve.
- [ ] Demo script matches current UI.
- [ ] Root documentation links to Compose roadmap.
- [ ] Root documentation links to Compose architecture ADR.
- [ ] No secrets are present in docs or screenshots.

### Acceptance Criteria

- [ ] Android customer app can be run by a reviewer.
- [ ] iOS customer app can be run by a reviewer with Xcode.
- [ ] Desktop admin app can be run by a reviewer.
- [ ] Compose demo path is documented.
- [ ] Optional nature of the Compose apps is clear.

## Phase 10: Senior Android Role Alignment

Goal: Make the Android customer app clearly demonstrate the expectations of a Senior Android role while preserving the Compose Multiplatform architecture.

### Steps

- [ ] Add Android-first adaptive UI coverage:
    - [ ] Validate customer screens on compact phone, large phone, tablet, and foldable-like widths.
    - [ ] Add adaptive list-detail behavior for account and transfer detail where useful.
    - [ ] Ensure touch targets, typography, spacing, and bottom navigation work across Android screen sizes.
    - [ ] Add realistic previews for loading, empty, success, error, and permission states.
- [ ] Add Android observability:
    - [ ] Add Android-only Firebase Crashlytics integration if the app moves beyond local-only demo mode.
    - [ ] Add non-sensitive crash context such as app version, active role, screen name, and correlation id.
    - [ ] Do not log bearer tokens, account identifiers beyond demo-safe values, or sensitive backend payloads.
    - [ ] Document how Android crashes and non-fatal errors are triaged.
- [ ] Add Android performance profiling:
    - [ ] Profile startup time for the Android customer app.
    - [ ] Inspect recomposition behavior on dashboard, account list, transaction list, and transfer form.
    - [ ] Check memory behavior during long transaction lists and repeated navigation.
    - [ ] Add Baseline Profile or Macrobenchmark only when the app has enough implemented UI to justify it.
    - [ ] Document profiling findings and fixes.
- [ ] Add lifecycle and process-death resilience:
    - [ ] Preserve transfer form draft state through ViewModel state and saved state where appropriate.
    - [ ] Handle app background/foreground transitions without duplicate financial submissions.
    - [ ] Expire or refresh session state cleanly after token expiration.
    - [ ] Cancel in-flight requests when the owning ViewModel is cleared.
- [ ] Add senior-level Android quality checks:
    - [ ] Add Android lint to the documented quality gate.
    - [ ] Add Android UI smoke tests for login, dashboard, account detail, transfer creation, and transfer result.
    - [ ] Add screenshot checks for Android customer demo-critical screens where practical.
    - [ ] Add CI documentation for Android build, shared tests, lint, and UI smoke tests.
- [ ] Add optional finance-oriented UI proof points:
    - [ ] Add balance trend or transfer volume chart if backend/report data supports it.
    - [ ] Add portfolio-style summary panels that map to account balances and transfer history.
    - [ ] Keep chart code isolated behind reusable design-system components.
    - [ ] Avoid crypto-specific claims unless the backend actually supports crypto assets.

### Test Scenarios

- [ ] Android customer screens render correctly on compact and expanded widths.
- [ ] Transfer form survives rotation or process recreation strategy selected for the app.
- [ ] Duplicate transfer submit is still prevented after lifecycle transitions.
- [ ] Dashboard and transaction list avoid obvious recomposition hotspots.
- [ ] Long transaction lists remain scrollable without excessive memory growth.
- [ ] Crash/error logging excludes bearer tokens and sensitive payloads.
- [ ] Android lint and Android customer smoke tests pass.

### Acceptance Criteria

- [ ] The Android customer app can be presented as the flagship target for a Senior Android portfolio discussion.
- [ ] The project demonstrates Compose, MVI, Clean Architecture, REST integration, lifecycle handling, performance awareness, and Android quality gates.
- [ ] Android-specific tooling does not leak into shared `commonMain` code.
- [ ] Role-alignment documentation explains why CMP uses Koin and Ktor instead of Android-only Hilt and Retrofit.

## Suggested Build Order

1. Compose Multiplatform foundation
2. Shared architecture, MVI, and navigation
3. Design system, app shells, and adaptive UI
4. API client, auth, error handling, and local persistence
5. Customer mobile app for Android and iOS
6. Desktop admin operations app
7. Audit, investigation, outbox, reconciliation, and reports
8. Demo mode and guided flow
9. Testing, accessibility, performance, and quality
10. Packaging, distribution, and documentation
11. Senior Android role alignment

## Definition Of Done For Each Phase

- [ ] Feature code is implemented.
- [ ] Implementation follows `docs/ADR-ComposeMultiplatformArchitecture.md`.
- [ ] Shared ViewModel or domain behavior has meaningful tests.
- [ ] Android target builds when touched.
- [ ] iOS target builds when touched.
- [ ] Desktop target builds when touched.
- [ ] UI behavior is checked on the relevant target form factor.
- [ ] Core accessibility expectations are met.
- [ ] Relevant docs or screenshots are updated.
- [ ] No secrets, bearer tokens, or private data are committed.

## Open Questions

- [ ] Should admin desktop support polling for outbox and reconciliation status or manual refresh only?
- [ ] Should the optional Next.js frontend remain the primary demo UI, or should Compose become the main demo experience?
