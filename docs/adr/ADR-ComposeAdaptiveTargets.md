# ADR: Compose Adaptive Targets And Shared Capabilities

## Status

Accepted

## Context

The Compose Multiplatform client targets Android, iOS, and desktop. The backend exposes one authorization and capability model across customer, operations admin, and auditor workflows, but the screens have different ergonomic needs by device class.

Customer workflows such as account review and transfer creation fit mobile-first single-column layouts. Operational workflows such as audit review, reconciliation, outbox monitoring, reversal, adjustment, and ledger investigation need dense tables, persistent navigation, filtering, and split panes that fit desktop better.

The project should avoid duplicating business logic per platform while still making each target feel intentionally designed. It should also avoid implying that mobile and desktop are separate products with different backend rules.

## Decision

The Compose client uses a unified shared capability model with adaptive presentation per target.

- Shared code owns API access, DTOs, domain-facing models, validation, formatting, session state, MVI state machines, navigation route models, design tokens, and reusable components where behavior is target-independent.
- Android and iOS are optimized first for customer workflows: dashboard, accounts, transactions, transfer creation, transfer status, and session handling.
- Desktop is optimized first for operations admin and auditor workflows: audit, reconciliation, outbox, transfer operations, reversals, adjustments, reports, and ledger investigation.
- All targets may expose all authorized roles and workflows when practical, but the app may present simplified mobile admin/audit surfaces and richer desktop admin/audit surfaces.
- Role authorization and business rules remain backend-authoritative. Client-side role checks only shape navigation, visibility, and usability.
- Platform modules own launchers, secure storage adapters, platform navigation hosting, desktop window behavior, and native integration.
- Feature implementation should start with shared ViewModels and state contracts, then provide mobile and desktop screen renderers when layout needs diverge.
- The design system should support both mobile customer and desktop admin surfaces from the same dark-first token set, with density and shell differences handled by adaptive components.

## Consequences

- The app avoids separate mobile and desktop business logic forks.
- Mobile can stay calm and customer-focused without blocking desktop-first admin tooling.
- Desktop can use dense operational layouts without forcing those layouts onto phone screens.
- Shared tests can cover most state transitions, validation, API error mapping, formatting, and navigation effects once.
- UI tests and previews still need target-specific coverage because shells, density, and layout differ.
- Some workflows may have multiple renderers over the same state contract, such as a mobile list/detail flow and a desktop table/detail flow.
- Future feature work should not add target-specific repositories, use cases, or ViewModels unless platform behavior genuinely requires it.
