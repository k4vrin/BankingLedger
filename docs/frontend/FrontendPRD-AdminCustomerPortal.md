# PRD: Optional Admin And Customer Frontend

## Status

Draft

## Purpose

Create an optional frontend demo for the Banking Ledger project using Next.js, Tailwind CSS, and shadcn/ui. The frontend is not part of the backend roadmap and should not block backend completion. Its purpose is to make the project easier to demo, inspect, and explain to recruiters, interviewers, and reviewers.

## Goals

- Provide a polished customer portal for account and transfer workflows.
- Provide an admin portal for operations, audit, reconciliation, and investigation workflows.
- Demonstrate the backend’s financial correctness features visually.
- Make role-based access, idempotency, reversals, adjustments, audit trail, and reconciliation easy to show.
- Keep the frontend pragmatic and demo-focused rather than building a full banking product.

## Non-Goals

- Do not build real production banking onboarding.
- Do not store real credentials or customer PII.
- Do not implement real external identity provider integration in the frontend.
- Do not replace backend authorization with frontend-only checks.
- Do not make this frontend a required backend phase.

## Target Users

- Customer user: views accounts, transactions, transfer status, and transfer history.
- Operations admin: reverses transfers, posts adjustments, runs reconciliation, and investigates failures.
- Auditor: reviews audit events and ledger transaction details.
- Demo reviewer: runs the project locally and follows the guided flow.

## Technology

- Next.js App Router.
- TypeScript.
- Tailwind CSS v4 with semantic tokens.
- shadcn/ui components.
- Server Components for read-heavy pages where practical.
- Client Components only for interactive forms, filters, tables, dialogs, and toasts.
- Server Actions or route handlers for authenticated backend mutations if a frontend BFF layer is useful.
- `next/font` for fonts.
- `next/image` only if real images are added.

## Design Principles

- Build an operational product UI, not a marketing site.
- Use dense, scannable layouts for admin workflows.
- Use shadcn/ui components before custom markup.
- Use semantic colors and design tokens instead of raw Tailwind color classes.
- Use tables, filters, badges, tabs, dialogs, sheets, alerts, and forms consistently.
- Keep cards for discrete entities and summaries; avoid nested cards.
- Use icons in actions where helpful, with accessible labels.
- Use clear empty, loading, error, and forbidden states.

## Information Architecture

### Shared

- `/login`
- `/logout`
- `/demo`
- `/forbidden`
- `/not-found`

### Customer Portal

- `/customer`
- `/customer/accounts`
- `/customer/accounts/[accountId]`
- `/customer/accounts/[accountId]/transactions`
- `/customer/transfers`
- `/customer/transfers/new`
- `/customer/transfers/[transferId]`

### Admin Portal

- `/admin`
- `/admin/transfers`
- `/admin/transfers/[transferId]`
- `/admin/reversals`
- `/admin/adjustments`
- `/admin/adjustments/new`
- `/admin/reconciliation`
- `/admin/reconciliation/batches`
- `/admin/reconciliation/batches/[batchId]`
- `/admin/audit`
- `/admin/audit/[auditEventId]`
- `/admin/ledger/transactions/[transactionId]`
- `/admin/outbox`
- `/admin/reports`

## Core Features

### Authentication Shell

- Login page for demo roles.
- Role switcher for local demo mode if backend supports sample tokens.
- Store token securely for local demo constraints.
- Attach bearer token to API requests.
- Show active role and actor id in app shell.
- Redirect unauthorized users to forbidden page.
- Preserve correlation id per user action where practical.

### Customer Dashboard

- Show account summary.
- Show available and ledger balances.
- Show recent transactions.
- Show recent transfers.
- Highlight pending, completed, failed, reversed statuses with badges.
- Provide quick action to create transfer.

### Account Detail

- Show account metadata.
- Show available and ledger balances.
- Show transaction table with direction, amount, currency, date, and linked ledger transaction.
- Filter transactions by date and direction.
- Link ledger transaction ids to admin investigation only for authorized users.

### Transfer Creation

- Select source account.
- Enter destination account id or account number based on backend support.
- Enter amount and currency.
- Enter description.
- Generate or accept idempotency key.
- Submit transfer.
- Show success response.
- Show idempotency replay result.
- Show idempotency conflict error clearly.
- Prevent duplicate form submission while request is pending.

### Transfer Detail

- Show transfer metadata.
- Show source and destination accounts.
- Show status, amount, currency, ledger transaction id, and timestamps.
- Show failure reason if present.
- Show reversal status if reversed.
- Link to audit trail entries if authorized.

### Admin Dashboard

- Show operational summary:
    - Pending outbox events.
    - Failed transfers.
    - Reversed transfers.
    - Recent adjustments.
    - Reconciliation mismatches.
- Provide quick links to reversal, adjustment, reconciliation, audit, and investigation pages.

### Admin Transfer Operations

- Search transfers by id, external reference, status, date range, and amount.
- View transfer detail.
- Reverse completed transfer through a confirmation dialog.
- Require reason code.
- Allow optional reason detail.
- Show structured backend errors.
- Refresh transfer status after reversal.

### Adjustment Posting

- Build a balanced adjustment form.
- Add debit and credit posting lines.
- Validate at least two posting lines.
- Validate debit and credit totals match before submit.
- Show account lookup or account id input.
- Require reason code.
- Allow reason detail.
- Show created adjustment and ledger transaction id.

### Reconciliation

- Import settlement batch from JSON form or pasted payload.
- Show batch status and counts.
- Show mismatch table by type and severity.
- Filter results by mismatch type, severity, and status.
- Link mismatches to ledger investigation where available.

### Audit Trail

- Query audit events by event type, entity type, entity id, actor, correlation id, and date range.
- Use paginated table.
- Show audit detail page with payload.
- Redact or omit sensitive values.
- Link audit events to related transfer, adjustment, reconciliation, or ledger pages.

### Ledger Investigation

- Search by ledger transaction id.
- Show transaction summary.
- Show journal entry and postings.
- Show related transfer, reversal, or adjustment.
- Show related audit events.
- Show related outbox events.
- Make it easy to explain immutable ledger corrections.

### Outbox Monitor

- Show outbox events by status.
- Filter by event type, aggregate id, status, and created date.
- Show retry count and last error.
- Allow authorized replay if backend supports it.
- Confirm replay action with dialog.

### Reports

- Show links or embedded views for report outputs:
    - Daily trial balance.
    - Account statement.
    - Reconciliation mismatch report.
    - Suspense aging.
    - Failed transfer reasons.
- Support CSV download only if backend exposes report exports.

## API Integration Requirements

- Use one backend API client wrapper.
- Add bearer token to all protected requests.
- Add `X-Correlation-Id` to mutating requests.
- Add `Idempotency-Key` to transfer creation.
- Parse `ApiErrorResponse` consistently.
- Preserve backend error codes for UI display.
- Map `401` to login flow.
- Map `403` to forbidden page or inline permission message.
- Avoid swallowing backend validation field errors.

## Component Requirements

- App shell: shadcn `Sidebar`, `Breadcrumb`, `DropdownMenu`, `Avatar`, `Separator`.
- Tables: shadcn `Table`, `Pagination`, `Badge`, `Skeleton`.
- Forms: shadcn `Field`, `FieldGroup`, `Input`, `Select`, `Textarea`, `Checkbox`, `Button`.
- Filters: `Popover`, `Command`, `Calendar` if date picker is added.
- Mutations: `Dialog` or `AlertDialog` for destructive/irreversible actions.
- Feedback: `sonner` toasts, `Alert`, `Progress`, `Spinner`.
- Empty states: shadcn `Empty`.
- Charts: shadcn `Chart` with Recharts only for dashboard summaries.

## State And Data Strategy

- Prefer Server Components for initial page data.
- Use Client Components for tables with live filters, forms, dialogs, and toasts.
- Use URL search params for table filters and pagination.
- Use optimistic UI only for low-risk UI state, not financial mutations.
- Always refresh server data after successful financial mutation.
- Use stable loading skeletons for tables and detail pages.
- Use explicit error boundaries for admin and customer route groups.

## Route Groups

- `(auth)` for login/logout.
- `(customer)` for customer portal.
- `(admin)` for admin/audit/ops portal.
- Shared layout for authenticated app shell.
- Separate role-aware navigation configuration.

## Security And Privacy

- Frontend role checks are UX only; backend remains authoritative.
- Do not log bearer tokens.
- Do not render full token claims in UI.
- Do not persist sensitive data beyond local demo requirements.
- Do not show customer-only data in admin examples unless the authenticated role permits it.
- Redact audit payload fields if backend marks them sensitive.

## Accessibility

- Keyboard-navigable sidebar and dialogs.
- Visible focus states.
- Proper labels for all form controls.
- Dialogs and sheets must have accessible titles.
- Tables need captions or accessible labels.
- Status badges must not rely only on color.
- Toasts must not be the only place critical errors appear.

## Responsive Behavior

- Desktop admin layout uses persistent sidebar and dense tables.
- Tablet layout collapses secondary panels.
- Mobile layout uses sheet navigation.
- Tables should support horizontal scrolling or card-like compact rows.
- Financial forms must remain readable and usable on mobile.

## Demo Flow

1. Log in as customer.
2. View accounts and balances.
3. Create transfer.
4. Replay transfer with same idempotency key.
5. Trigger idempotency conflict with changed request.
6. Log in as ops admin.
7. Reverse completed transfer.
8. Post adjustment.
9. Import reconciliation batch with a mismatch.
10. Query audit trail by correlation id.
11. Open ledger transaction investigation page.
12. Show outbox events for the flow.

## Milestones

### Milestone 1: Frontend Foundation

- Create Next.js app.
- Install Tailwind CSS and shadcn/ui.
- Add app shell.
- Add demo auth/token handling.
- Add API client and error parser.
- Add role-aware navigation.

### Milestone 2: Customer Portal

- Account list and account detail.
- Transaction list.
- Transfer creation.
- Transfer detail.
- Idempotency replay/conflict UI.

### Milestone 3: Admin Operations

- Admin dashboard.
- Transfer search/detail.
- Reversal dialog.
- Adjustment form.
- Structured operation errors.

### Milestone 4: Audit And Investigation

- Audit event query and detail.
- Ledger transaction investigation.
- Correlation id search.
- Outbox monitor.

### Milestone 5: Reconciliation And Demo Polish

- Reconciliation batch import.
- Mismatch results table.
- Reports page.
- Demo script and screenshots.

## Acceptance Criteria

- Customer can view accounts and create a transfer through the UI.
- Transfer idempotency replay and conflict are visible in the UI.
- Ops admin can reverse a completed transfer.
- Ops admin can post a balanced adjustment.
- Auditor or ops admin can query audit events.
- Ledger investigation page explains transfer, reversal, and adjustment transactions.
- UI handles `401`, `403`, validation errors, conflict errors, and not-found errors.
- UI is responsive enough for desktop, tablet, and mobile demo.
- No bearer token or secret value is logged or shown accidentally.
- Demo flow can be completed in under 10 minutes.

## Test Plan

- Unit tests for API client error parsing.
- Unit tests for idempotency key generation and reuse behavior.
- Component tests for transfer form validation.
- Component tests for adjustment posting line balancing.
- Component tests for role-aware navigation.
- Integration tests with mocked backend for customer flow.
- Integration tests with mocked backend for admin reversal flow.
- Integration tests with mocked backend for audit filters.
- End-to-end test for demo happy path if backend is available locally.
- Accessibility checks for dialogs, forms, sidebar, and tables.

## Open Questions

- Should the frontend live inside this repository or a separate repository?
- Should demo auth use a backend token endpoint or local mocked token fixtures?
- Should admin tables support live polling or manual refresh only?
- Should report pages render backend report data or link to SQL/report documentation?
- Should the UI include a guided demo mode with scripted steps?
