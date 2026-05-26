# Quality Report

## CI Strategy

The backend CI workflow is [Backend CI](../../.github/workflows/backend-ci.yml). It runs on pull requests and pushes to `main`. The slower OWASP dependency vulnerability scan runs separately in [Dependency Check](../../.github/workflows/dependency-check.yml) on a weekly schedule or manual dispatch.

CI uses Oracle Free and Kafka through `banking-ledger-api/compose.ci.yaml` with the `ci` Spring profile. This keeps migration validation and integration tests close to the local and production database assumptions instead of swapping in an in-memory database.

The `ci` profile runs the core Flyway migrations and intentionally excludes development repeatable seed data. Demo fixtures remain available through the `dev` profile, while CI integration tests create their own data and are not affected by seeded reversals, journal entries, or postings.

CI jobs cover:

- Maven validation.
- Unit, slice, and integration tests.
- Flyway migration execution through Spring Boot startup.
- JaCoCo coverage report generation.
- Spring Boot jar packaging.
- Docker image build with OCI labels.
- Dependency review for pull requests when GitHub dependency graph is enabled.
- Gitleaks secret scan.
- Trivy container image scan for high and critical findings.

The scheduled/manual Dependency Check workflow covers:

- OWASP dependency-check scan with a critical-only build failure threshold.
- Dependency-check report artifact upload.
- Optional `NVD_API_KEY` usage through a repository secret to avoid slow anonymous NVD updates.

## Local Commands Matching CI

Run from `banking-ledger-api/`:

```bash
make ci-deps-up
./mvnw -DskipTests validate
./mvnw test
./mvnw verify
make docker-build
make ci-deps-down
```

Run `make dependency-check` separately for local parity with the scheduled Dependency Check workflow.

For a narrower smoke check:

```bash
./mvnw -DskipTests compile
./mvnw -Dtest=DevTokenServiceTest test
```

## Coverage

JaCoCo writes coverage output to:

```text
banking-ledger-api/target/site/jacoco/index.html
banking-ledger-api/target/site/jacoco/jacoco.xml
```

CI uploads the coverage directory as the `backend-test-and-coverage-reports` artifact.

No coverage threshold is enforced yet. The project favors correctness-focused integration tests over a blanket percentage gate, and a threshold can be added once coverage stabilizes after CI is running publicly.

## Test Categories

Current source-level test inventory:

| Category | Count | Notes |
| --- | ---: | --- |
| Test classes total | 47 | JUnit tests under `banking-ledger-api/src/test/java`. |
| Controller/API slice tests | 7 | Controller validation, security, and structured error behavior. |
| Integration/constraint tests | 17 | Oracle schema constraints, Flyway-managed persistence, and transactional workflows. |
| Security-focused tests | 4 | JWT validation, role mapping, authorization, and dev token issuance. |

- Controller slice tests verify request validation, authorization, and structured errors.
- Application service tests verify business rules, idempotency, audit writing, outbox behavior, and query behavior.
- Persistence and integration tests verify Oracle constraints, Flyway-managed schema assumptions, rollback behavior, and financial workflow persistence.
- Security tests verify JWT validation, role mapping, ownership rules, and dev token issuance.

## Known Test Gaps

- No automated browser screenshot test for Swagger UI; OpenAPI is validated by Springdoc startup and CI compile/test coverage.
- No dedicated performance or load test for concurrent transfers.
- No external Kafka consumer contract test; outbox payloads are verified inside backend tests and documented in ADRs.
- No enforced formatting/checkstyle gate; this is intentional to keep Phase 13 low-friction until a team style profile is chosen.

## Branch Protection Guidance

When the repository is public, protect `main` and require:

- `Backend CI / Build, Test, Package`
- `Backend CI / Secret Scan`
- `Backend CI / Dependency Review` once GitHub dependency graph is enabled for the repository.

Rerun failed checks from the GitHub Actions run page. For infrastructure failures, inspect uploaded test reports and the `Show dependency status` step before rerunning.

## Vulnerability Handling

- Dependency review reports high or critical newly introduced vulnerabilities when GitHub dependency graph is enabled. It is non-blocking until that repository feature is available.
- OWASP dependency-check runs outside the PR build path and fails its own workflow on CVSS `9.0` or higher.
- Trivy reports high and critical fixed vulnerabilities in logs and SARIF, but only fails PR CI on critical fixed vulnerabilities. High findings remain visible without blocking every base-image patch cycle.
- False positives must be documented in `dependency-check-suppressions.xml` or a Trivy ignore file with a reason and review date.
