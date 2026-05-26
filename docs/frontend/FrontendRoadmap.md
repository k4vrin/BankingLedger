# Frontend Roadmap: Optional Admin And Customer Portal

This roadmap is optional and separate from the backend roadmap. Its purpose is to create a polished demo interface for the Banking Ledger API using Next.js, Tailwind CSS, and shadcn/ui.

## Phase 0: Frontend Project Foundation

Goal: Create a reliable Next.js foundation with the right tooling, structure, and local environment assumptions.

### Steps

- [ ] Choose repository layout:
    - [ ] Decide whether frontend lives in this repository or a separate repository.
    - [ ] If in this repository, choose `banking-ledger-web/` or `frontend/`.
    - [ ] Document backend API base URL configuration.
    - [ ] Document required Node.js and package manager versions.
- [ ] Scaffold Next.js app:
    - [ ] Use App Router.
    - [ ] Use TypeScript.
    - [ ] Use Tailwind CSS.
    - [ ] Use ESLint.
    - [ ] Use a source directory if preferred.
    - [ ] Confirm local dev server runs.
- [ ] Configure Tailwind CSS:
    - [ ] Use Tailwind CSS v4 conventions if the scaffold supports it.
    - [ ] Define semantic tokens for background, foreground, primary, secondary, muted, destructive, border, ring, card, and accent.
    - [ ] Add dark mode support if desired.
    - [ ] Avoid hard-coded color palettes in application components.
- [ ] Initialize shadcn/ui:
    - [ ] Configure aliases.
    - [ ] Add `cn()` utility.
    - [ ] Add base components needed for the shell.
    - [ ] Confirm generated components compile.
    - [ ] Document component install command.
- [ ] Add base app structure:
    - [ ] Add route groups for `(auth)`, `(customer)`, and `(admin)`.
    - [ ] Add global layout.
    - [ ] Add global error boundary.
    - [ ] Add not-found page.
    - [ ] Add forbidden page.
    - [ ] Add loading patterns for route segments.
- [ ] Add environment configuration:
    - [ ] Add `.env.example`.
    - [ ] Add `NEXT_PUBLIC_APP_NAME`.
    - [ ] Add backend API base URL.
    - [ ] Add demo auth configuration.
    - [ ] Validate missing required environment values at startup.
- [ ] Add developer scripts:
    - [ ] `dev`.
    - [ ] `build`.
    - [ ] `start`.
    - [ ] `lint`.
    - [ ] `typecheck`.
    - [ ] `test`.
    - [ ] `test:e2e` if end-to-end tests are added.

### Test Scenarios

- [ ] App starts locally.
- [ ] Production build succeeds.
- [ ] Lint command succeeds.
- [ ] Typecheck command succeeds.
- [ ] Required env validation fails clearly when backend URL is missing.
- [ ] Root route renders without hydration errors.
- [ ] Not-found page renders for unknown route.

### Acceptance Criteria

- [ ] Next.js app exists and builds.
- [ ] Tailwind and shadcn/ui are configured.
- [ ] Route group structure is in place.
- [ ] Local setup is documented.

## Phase 1: Design System And App Shell

Goal: Build a reusable, accessible shell and UI foundation for customer and admin workflows.

### Steps

- [ ] Define design direction:
    - [ ] Operational, dense, and professional for admin workflows.
    - [ ] Clear and calm for customer workflows.
    - [ ] Avoid marketing-style hero pages.
    - [ ] Prioritize tables, filters, and detail views.
- [ ] Add shadcn/ui components:
    - [ ] `Button`.
    - [ ] `Card`.
    - [ ] `Badge`.
    - [ ] `Table`.
    - [ ] `Sidebar`.
    - [ ] `Breadcrumb`.
    - [ ] `DropdownMenu`.
    - [ ] `Avatar`.
    - [ ] `Separator`.
    - [ ] `Skeleton`.
    - [ ] `Alert`.
    - [ ] `Dialog`.
    - [ ] `AlertDialog`.
    - [ ] `Sheet`.
    - [ ] `Tabs`.
    - [ ] `Select`.
    - [ ] `Input`.
    - [ ] `Textarea`.
    - [ ] `Field` and form primitives.
    - [ ] `Pagination`.
    - [ ] `Empty`.
    - [ ] `sonner`.
- [ ] Build authenticated app shell:
    - [ ] Sidebar navigation.
    - [ ] Mobile sheet navigation.
    - [ ] Header with breadcrumbs.
    - [ ] User menu.
    - [ ] Role indicator.
    - [ ] Environment indicator for demo mode.
    - [ ] Content region with responsive width constraints.
- [ ] Add navigation model:
    - [ ] Shared navigation entries.
    - [ ] Customer navigation entries.
    - [ ] Admin navigation entries.
    - [ ] Auditor navigation entries.
    - [ ] Hide unauthorized links for usability.
    - [ ] Do not rely on hidden links for security.
- [ ] Add status and money display utilities:
    - [ ] Money formatting by currency.
    - [ ] Minor unit to display amount conversion.
    - [ ] Status badge variants for transfer, reversal, adjustment, outbox, and reconciliation statuses.
    - [ ] Date/time formatting.
    - [ ] Relative time formatting where helpful.
- [ ] Add reusable page primitives:
    - [ ] Page header.
    - [ ] Page actions.
    - [ ] Data table shell.
    - [ ] Filter toolbar.
    - [ ] Detail section.
    - [ ] Empty state.
    - [ ] Error state.
    - [ ] Loading state.

### Test Scenarios

- [ ] Sidebar renders customer navigation for customer role.
- [ ] Sidebar renders admin navigation for admin role.
- [ ] Mobile navigation opens and closes.
- [ ] Breadcrumbs match current route.
- [ ] Status badges render correct labels.
- [ ] Money formatter renders minor units correctly.
- [ ] Loading skeletons do not shift layout.
- [ ] Dialogs have accessible titles.

### Acceptance Criteria

- [ ] App shell supports desktop and mobile.
- [ ] Shared UI primitives are reusable across phases.
- [ ] shadcn/ui components are composed according to accessibility rules.
- [ ] Design tokens are semantic and documented.

## Phase 2: API Client, Auth, And Error Handling

Goal: Connect the frontend to the backend safely and consistently.

### Steps

- [ ] Add API client:
    - [ ] Centralize base URL.
    - [ ] Add JSON request helper.
    - [ ] Add typed response parsing.
    - [ ] Add typed error parsing for `ApiErrorResponse`.
    - [ ] Add timeout handling.
    - [ ] Add request cancellation where useful.
- [ ] Add correlation support:
    - [ ] Generate correlation id for mutating operations.
    - [ ] Attach `X-Correlation-Id`.
    - [ ] Display correlation id on error details.
    - [ ] Preserve correlation id in demo flow.
- [ ] Add idempotency support:
    - [ ] Generate idempotency key for transfer creation.
    - [ ] Let demo users replay same idempotency key.
    - [ ] Show idempotency conflict response.
    - [ ] Prevent accidental duplicate submit while request is pending.
- [ ] Add demo authentication:
    - [ ] Add login page.
    - [ ] Add role selection for local demo.
    - [ ] Store token according to chosen demo constraints.
    - [ ] Attach bearer token to protected API requests.
    - [ ] Add logout.
    - [ ] Add expired token handling.
- [ ] Add authorization UX:
    - [ ] Route unauthorized users to forbidden page.
    - [ ] Hide unavailable navigation items.
    - [ ] Show inline permission message for blocked actions.
    - [ ] Keep backend as source of truth.
- [ ] Add error handling:
    - [ ] Map `400` validation errors to forms.
    - [ ] Map `401` to login.
    - [ ] Map `403` to forbidden.
    - [ ] Map `404` to not-found or inline missing state.
    - [ ] Map `409` to conflict alert.
    - [ ] Map `500` to generic supportable error.
    - [ ] Show backend error code when useful for demo.

### Test Scenarios

- [ ] API client attaches bearer token.
- [ ] API client attaches correlation id for mutating requests.
- [ ] Transfer creation attaches idempotency key.
- [ ] `ApiErrorResponse` parses into UI-friendly error.
- [ ] Validation field errors render under fields.
- [ ] `401` redirects to login.
- [ ] `403` renders forbidden state.
- [ ] `409` renders conflict state.
- [ ] Logout clears demo auth state.

### Acceptance Criteria

- [ ] API integration is centralized.
- [ ] Auth and role state are available to layouts and pages.
- [ ] Backend errors are shown consistently.
- [ ] Financial mutations use correlation and idempotency where required.

## Phase 3: Customer Portal

Goal: Provide customer-facing account, transaction, and transfer workflows.

### Steps

- [ ] Build customer dashboard:
    - [ ] Account summary cards.
    - [ ] Recent transactions.
    - [ ] Recent transfers.
    - [ ] Quick action to create transfer.
    - [ ] Empty state for new customer.
- [ ] Build account list:
    - [ ] Show account number or masked identifier.
    - [ ] Show account type.
    - [ ] Show account status.
    - [ ] Show available and ledger balances.
    - [ ] Link to account detail.
- [ ] Build account detail:
    - [ ] Show account metadata.
    - [ ] Show balances.
    - [ ] Show latest postings.
    - [ ] Link to transaction list.
- [ ] Build account transaction list:
    - [ ] Paginated table.
    - [ ] Filter by date range.
    - [ ] Filter by posting direction.
    - [ ] Show ledger transaction id.
    - [ ] Show amount, currency, direction, and timestamp.
- [ ] Build transfer list:
    - [ ] Paginated table.
    - [ ] Filter by status.
    - [ ] Filter by date range.
    - [ ] Show amount, currency, source, destination, and status.
    - [ ] Link to transfer detail.
- [ ] Build transfer creation form:
    - [ ] Select source account.
    - [ ] Enter destination.
    - [ ] Enter amount.
    - [ ] Select or infer currency.
    - [ ] Enter description.
    - [ ] Generate idempotency key.
    - [ ] Submit transfer.
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
- [ ] Transaction filters update URL search params.
- [ ] Transfer form validates amount before submit.
- [ ] Transfer form prevents duplicate pending submit.
- [ ] Successful transfer navigates to transfer detail.
- [ ] Idempotency replay is visible to demo user.
- [ ] Idempotency conflict shows backend error code.
- [ ] Customer cannot see admin-only links.

### Acceptance Criteria

- [ ] Customer can view accounts.
- [ ] Customer can inspect account transactions.
- [ ] Customer can create a transfer.
- [ ] Customer can inspect transfer result and errors.
- [ ] Customer portal is usable on desktop and mobile.

## Phase 4: Admin Operations Portal

Goal: Provide operational workflows for transfer reversal, adjustment posting, and admin investigation entry points.

### Steps

- [ ] Build admin dashboard:
    - [ ] Operational metric cards.
    - [ ] Failed transfer count.
    - [ ] Reversed transfer count.
    - [ ] Recent adjustments.
    - [ ] Pending outbox count.
    - [ ] Reconciliation mismatch count.
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
    - [ ] Show audit correlation id if available.
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
    - [ ] Amount input.
    - [ ] Currency input.
    - [ ] Reason code select.
    - [ ] Reason detail textarea.
    - [ ] Client-side balanced total validation.
    - [ ] Submit adjustment.
    - [ ] Show adjustment and ledger transaction ids.
- [ ] Add operation result states:
    - [ ] Success toast plus persistent result panel.
    - [ ] Validation errors mapped to fields.
    - [ ] Conflict alerts for duplicate reversal or business conflict.
    - [ ] Retry guidance for lock/concurrency conflicts.

### Test Scenarios

- [ ] Admin dashboard shows summary cards.
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

- [ ] Ops admin can reverse eligible transfers.
- [ ] Ops admin can post balanced adjustments.
- [ ] Admin operation errors are understandable.
- [ ] Admin portal clearly links operations to ledger investigation.

## Phase 5: Audit, Ledger Investigation, And Outbox Monitor

Goal: Make backend traceability visible and easy to demonstrate.

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
- [ ] Add traceability navigation:
    - [ ] Link transfer detail to ledger investigation.
    - [ ] Link adjustment result to ledger investigation.
    - [ ] Link reconciliation mismatch to ledger investigation.
    - [ ] Link audit events to related pages.

### Test Scenarios

- [ ] Audit list filters by event type.
- [ ] Audit list filters by correlation id.
- [ ] Audit detail renders JSON payload safely.
- [ ] Ledger investigation shows transaction and postings.
- [ ] Ledger investigation links related reversal.
- [ ] Ledger investigation links related adjustment.
- [ ] Outbox monitor shows pending, published, failed, and dead-lettered statuses.
- [ ] Replay action requires confirmation.
- [ ] Unauthorized customer cannot access audit or investigation pages.

### Acceptance Criteria

- [ ] Auditor can query audit trail.
- [ ] Admin can inspect a ledger transaction end to end.
- [ ] Outbox status is visible for demo operations.
- [ ] Traceability pages make immutable ledger behavior explainable.

## Phase 6: Reconciliation And Reports UI

Goal: Show settlement mismatch workflows and reporting outputs in the admin experience.

### Steps

- [ ] Build reconciliation batch list:
    - [ ] Paginated table.
    - [ ] Filter by source.
    - [ ] Filter by status.
    - [ ] Filter by date range.
    - [ ] Show item count and mismatch count.
- [ ] Build reconciliation import:
    - [ ] JSON textarea input.
    - [ ] File upload placeholder if backend supports it later.
    - [ ] Validate JSON before submit.
    - [ ] Show sample payload.
    - [ ] Submit batch.
    - [ ] Show created batch result.
- [ ] Build reconciliation batch detail:
    - [ ] Show batch metadata.
    - [ ] Show status and counts.
    - [ ] Show settlement items.
    - [ ] Show mismatch results.
    - [ ] Filter mismatch results by type and severity.
    - [ ] Link result to ledger investigation.
- [ ] Build reports page:
    - [ ] Daily trial balance panel.
    - [ ] Account statement panel.
    - [ ] Reconciliation mismatch report panel.
    - [ ] Suspense aging report panel.
    - [ ] Failed transfer reasons panel.
    - [ ] Link to report SQL documentation if backend API does not expose report data.

### Test Scenarios

- [ ] Batch list renders statuses and counts.
- [ ] Import form rejects invalid JSON.
- [ ] Import form submits valid sample batch.
- [ ] Batch detail shows mismatch results.
- [ ] Mismatch type filter works.
- [ ] Mismatch result links to investigation when ledger id exists.
- [ ] Reports page renders available report summaries or documentation links.
- [ ] Unauthorized role cannot import reconciliation batch.

### Acceptance Criteria

- [ ] Admin can import and inspect reconciliation batch.
- [ ] Auditor can inspect reconciliation results if backend permits it.
- [ ] Reports page supports demo storytelling.
- [ ] Mismatches can be traced to investigation pages.

## Phase 7: Demo Mode And Guided Flow

Goal: Make the frontend useful for a short, repeatable portfolio demo.

### Steps

- [ ] Add demo landing page:
    - [ ] Explain available demo roles.
    - [ ] Link to customer portal.
    - [ ] Link to admin portal.
    - [ ] Link to guided demo flow.
    - [ ] Show backend connection status.
- [ ] Add guided demo checklist:
    - [ ] Login as customer.
    - [ ] View balances.
    - [ ] Create transfer.
    - [ ] Replay transfer with same idempotency key.
    - [ ] Trigger idempotency conflict.
    - [ ] Login as ops admin.
    - [ ] Reverse transfer.
    - [ ] Post adjustment.
    - [ ] Import reconciliation batch.
    - [ ] Query audit by correlation id.
    - [ ] Open ledger investigation.
    - [ ] Inspect outbox events.
- [ ] Add demo data helpers:
    - [ ] Show seeded account ids.
    - [ ] Show sample transfer payload values.
    - [ ] Show sample adjustment posting lines.
    - [ ] Show sample reconciliation JSON.
    - [ ] Provide copy buttons for identifiers.
- [ ] Add backend readiness checks:
    - [ ] Check API health.
    - [ ] Check auth/token endpoint if available.
    - [ ] Show clear setup instructions when backend is unavailable.
- [ ] Add screenshots and fallback mode:
    - [ ] Capture representative screens.
    - [ ] Add screenshot links in docs.
    - [ ] Provide static fallback screenshots for reviewers who do not run locally.

### Test Scenarios

- [ ] Demo page shows backend online state.
- [ ] Demo checklist state persists during session.
- [ ] Copy buttons copy expected ids or payload snippets.
- [ ] Guided flow can be completed with seeded data.
- [ ] Backend offline state shows setup guidance.
- [ ] Demo flow remains under 10 minutes.

### Acceptance Criteria

- [ ] Reviewer can follow a guided demo without knowing the codebase.
- [ ] Demo highlights idempotency, reversal, adjustment, audit, reconciliation, and outbox.
- [ ] Offline fallback still communicates project value.

## Phase 8: Frontend Testing, Accessibility, And Quality

Goal: Add enough confidence that the demo UI is reliable and accessible.

### Steps

- [ ] Add unit tests:
    - [ ] API client error parsing.
    - [ ] Money formatting.
    - [ ] Status badge mapping.
    - [ ] Role navigation mapping.
    - [ ] Idempotency key helper.
- [ ] Add component tests:
    - [ ] Transfer form validation.
    - [ ] Adjustment posting line balancing.
    - [ ] Reversal dialog validation.
    - [ ] Audit filters.
    - [ ] Reconciliation import JSON validation.
- [ ] Add integration tests with mocked backend:
    - [ ] Customer account flow.
    - [ ] Transfer creation flow.
    - [ ] Admin reversal flow.
    - [ ] Adjustment flow.
    - [ ] Audit search flow.
    - [ ] Reconciliation import flow.
- [ ] Add end-to-end tests if backend is available:
    - [ ] Customer transfer happy path.
    - [ ] Idempotency replay path.
    - [ ] Reversal path.
    - [ ] Adjustment path.
    - [ ] Audit/investigation path.
- [ ] Add accessibility checks:
    - [ ] Forms have labels.
    - [ ] Dialogs have titles.
    - [ ] Tables have accessible labels or captions.
    - [ ] Keyboard navigation works for menus and dialogs.
    - [ ] Color is not the only status signal.
    - [ ] Focus states are visible.
- [ ] Add performance checks:
    - [ ] Avoid client-side waterfalls.
    - [ ] Use Server Components for read-heavy initial data.
    - [ ] Use pagination for large tables.
    - [ ] Avoid loading large payloads in dashboard.

### Test Scenarios

- [ ] Unit test suite passes.
- [ ] Component test suite passes.
- [ ] Mocked integration suite passes.
- [ ] E2E demo flow passes when backend is running.
- [ ] Accessibility checks pass for core routes.
- [ ] Production build passes.
- [ ] No hydration errors on core routes.

### Acceptance Criteria

- [ ] Core forms and workflows are covered by tests.
- [ ] Accessibility issues are addressed for demo-critical routes.
- [ ] Production build is stable.
- [ ] Demo flow is repeatable.

## Phase 9: Frontend Documentation And Portfolio Polish

Goal: Make the optional frontend easy to run, evaluate, and discuss.

### Steps

- [ ] Add frontend README:
    - [ ] Setup instructions.
    - [ ] Environment variables.
    - [ ] Backend dependency requirements.
    - [ ] Development commands.
    - [ ] Build command.
    - [ ] Test commands.
    - [ ] Demo walkthrough.
- [ ] Update root documentation:
    - [ ] Link frontend PRD.
    - [ ] Link frontend roadmap.
    - [ ] Link screenshots.
    - [ ] Clarify frontend is optional.
    - [ ] Clarify backend remains source of truth.
- [ ] Add screenshots:
    - [ ] Customer dashboard.
    - [ ] Transfer form.
    - [ ] Admin dashboard.
    - [ ] Reversal dialog.
    - [ ] Adjustment form.
    - [ ] Audit query.
    - [ ] Ledger investigation.
    - [ ] Reconciliation results.
- [ ] Add demo script:
    - [ ] Step-by-step browser flow.
    - [ ] Expected backend state changes.
    - [ ] Troubleshooting section.
    - [ ] Reset instructions.
- [ ] Add deployment notes:
    - [ ] Local-only demo setup.
    - [ ] Optional Docker build.
    - [ ] Optional static screenshots for GitHub.
    - [ ] Environment variable safety notes.

### Test Scenarios

- [ ] README commands work.
- [ ] Screenshot links resolve.
- [ ] Demo script matches current UI.
- [ ] Root documentation links to frontend docs.
- [ ] No secrets are present in frontend docs or screenshots.

### Acceptance Criteria

- [ ] Frontend can be run by a reviewer.
- [ ] Frontend demo path is documented.
- [ ] Screenshots support portfolio review.
- [ ] Optional nature of frontend is clear.

## Suggested Build Order

1. Frontend foundation
2. Design system and app shell
3. API client, auth, and error handling
4. Customer portal
5. Admin operations portal
6. Audit, investigation, and outbox monitor
7. Reconciliation and reports UI
8. Demo mode and guided flow
9. Testing, accessibility, and quality
10. Documentation and portfolio polish

## Definition Of Done For Each Phase

- [ ] Feature code is implemented.
- [ ] Meaningful tests are added.
- [ ] `npm run lint` or equivalent passes.
- [ ] `npm run typecheck` or equivalent passes.
- [ ] Production build passes.
- [ ] Responsive behavior is checked on desktop and mobile.
- [ ] Core accessibility expectations are met.
- [ ] Relevant docs or screenshots are updated.
- [ ] No secrets, bearer tokens, or private data are committed.
