# Documentation

This directory groups project documentation by purpose so architecture decisions, backend design, frontend planning, and operational notes are easier to scan.

## Backend

- [Project brief](backend/Project.md)
- [Core business logic](backend/CoreBusinessLogic.md)
- [Database design](backend/DatabaseDesign.md)
- [API contracts and examples](backend/API.md)
- [Transfer concurrency locking strategy](backend/TransferConcurrencyLockingStrategy.md)
- [Transfer transaction boundary audit](backend/TransferTransactionBoundaryAudit.md)

## Architecture Decisions

- [Double-entry, amounts, and idempotency](adr/ADR-DoubleEntryAmountIdempotency.md)
- [Transaction isolation and account locking](adr/ADR-Phase5-TransactionIsolationAndLocking.md)
- [Immutable reversals and adjustments](adr/ADR-Phase6-ImmutableLedgerReversalsAndAdjustments.md)
- [JWT authentication and authorization](adr/ADR-Phase7-JWTAuthenticationAndAuthorization.md)
- [Audit trail and investigation APIs](adr/ADR-Phase8-AuditTrailAndInvestigationApis.md)
- [Outbox and Kafka publishing](adr/ADR-Phase9-OutboxKafkaPublishing.md)
- [Reconciliation design](adr/ADR-Phase10-Reconciliation.md)
- [Developer experience](adr/ADR-Phase12-DeveloperExperience.md)
- [Compose Multiplatform client architecture](adr/ADR-ComposeMultiplatformArchitecture.md)
- [Compose adaptive targets](adr/ADR-ComposeAdaptiveTargets.md)

## Frontend And Design

- [Compose Multiplatform roadmap](frontend/ComposeMultiplatformRoadmap.md)
- [Frontend PRD: Admin and customer portal](frontend/FrontendPRD-AdminCustomerPortal.md)
- [Frontend roadmap](frontend/FrontendRoadmap.md)
- [Design system](design/DesignSystem.md)
- [Mockups](design/mockups/)

## Operations

- [Local development guide](operations/LocalDevelopment.md)
- [Quality report](operations/QualityReport.md)
- [Branch protection](operations/BranchProtection.md)
- [Incident write-ups](operations/Incidents.md)
- [Backend roadmap](operations/Roadmap.md)
- [Feature matrix](operations/FeatureMatrix.md)
- [Known limitations](operations/KnownLimitations.md)
- [Glossary](operations/Glossary.md)
- [Portfolio narrative](operations/Portfolio.md)

## Diagrams

- [Architecture](diagrams/architecture.mmd)
- [Entity relationship diagram](diagrams/erd.mmd)
- [Transfer flow](diagrams/transfer-flow.mmd)
- [Reversal flow](diagrams/reversal-flow.mmd)
- [Outbox publishing](diagrams/outbox-publishing.mmd)
- [Transaction boundaries](diagrams/transaction-boundaries.mmd)
