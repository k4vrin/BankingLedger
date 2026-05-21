# Feature Matrix

| Phase | Capability | Status | Main Artifacts |
| --- | --- | --- | --- |
| 0 | Project foundation | Complete | Spring Boot app, Docker Compose, health endpoint, README |
| 1 | Database schema and core model | Complete | Flyway schema, Oracle constraints, domain enums |
| 2 | Account service | Complete | Account create/read/balance/transaction APIs |
| 3 | Ledger posting engine | Complete | Ledger transactions, journal entries, postings, double-entry policy |
| 4 | Internal transfer API | Complete | Transfer create/read APIs, idempotency records |
| 5 | Concurrency and isolation | Complete | Pessimistic account locking, transaction boundary docs |
| 6 | Reversals and adjustments | Complete | Immutable reversal and adjustment APIs |
| 7 | Authentication and authorization | Complete | JWT resource server, dev tokens, role and ownership checks |
| 8 | Audit trail and investigation APIs | Complete | Audit search/detail and ledger investigation APIs |
| 9 | Outbox and Kafka publishing | Complete | Transactional outbox, publisher worker, requeue endpoints |
| 10 | Reconciliation | Complete | Settlement batch import and mismatch query APIs |
| 11 | Reporting and Oracle SQL | Complete | SQL and PL/SQL report examples |
| 12 | API documentation and developer experience | Complete | OpenAPI config, demo data, Makefile, HTTP collection, diagrams, docs |
| 13 | CI/CD and quality gates | Complete | GitHub Actions, Oracle CI compose, JaCoCo, dependency review, Gitleaks, Trivy, quality report |
| 14 | Portfolio polish | Complete | Final README, portfolio narrative, incident write-ups, demo flow, branch protection guidance |
