# Local Development

## Prerequisites

- JDK 21.
- Docker Desktop or Docker Engine with Docker Compose.
- Git.
- A shell with `make`, `curl`, and standard Unix tools for the convenience commands.

The Maven wrapper is committed in `banking-ledger-api/`, so a local Maven installation is not required.

## Environment

Copy the local environment file:

```bash
cd banking-ledger-api
cp .env.example .env
```

Default development values:

```text
DB_URL=jdbc:oracle:thin:@localhost:1521/FREEPDB1
DB_USERNAME=ledger_dev
DB_PASSWORD=ledger_dev_password
ORACLE_PASSWORD=oracle_admin_password
CLOUDBEAVER_PORT=8978
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

Do not commit `.env` or real secrets.

## Commands

Run these from `banking-ledger-api/`.

| Command | Purpose |
| --- | --- |
| `make deps-up` | Start Oracle, Kafka, and CloudBeaver. |
| `make deps-down` | Stop local dependencies. |
| `make deps-reset` | Stop dependencies and remove local volumes. |
| `make ci-deps-up` | Start Oracle and Kafka with the CI compose file. |
| `make ci-deps-down` | Stop CI dependencies and remove volumes. |
| `make run` | Run the API with the `dev` profile. |
| `make test` | Run the Maven test phase. |
| `make integration-test` | Run the Maven verify phase. |
| `make validate` | Validate the Maven project without tests. |
| `make package` | Build the application jar. |
| `make docker-build` | Build the API Docker image with local OCI labels. |
| `make dependency-check` | Run OWASP dependency-check. |
| `make token-customer` | Issue a dev JWT for the seeded customer. |
| `make token-teller` | Issue a dev JWT for teller flows. |
| `make token-auditor` | Issue a dev JWT for audit/read-only flows. |
| `make token-ops` | Issue a dev JWT for operations workflows. |
| `make token-service` | Issue a dev JWT for service workflows. |
| `make reports` | List report SQL examples. |

Flyway migrations run automatically on application startup. The `dev` profile loads repeatable seed data from `src/main/resources/db/dev-migration`.

## CI Parity

The GitHub Actions workflow uses `compose.ci.yaml` instead of the development compose file. It starts Oracle Free and Kafka only, disables Spring Boot Docker Compose integration, and runs against the same Oracle-compatible migrations used locally.

Run a local CI-style check from `banking-ledger-api/`:

```bash
make ci-deps-up
./mvnw -DskipTests validate
./mvnw test
./mvnw verify
make dependency-check
make docker-build
make ci-deps-down
```

CI environment values:

```text
SPRING_PROFILES_ACTIVE=dev
SPRING_DOCKER_COMPOSE_ENABLED=false
DB_URL=jdbc:oracle:thin:@localhost:1521/FREEPDB1
DB_USERNAME=ledger_dev
DB_PASSWORD=ledger_dev_password
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

## Seed Data

The dev database is seeded idempotently with deterministic rows:

| Resource | ID or Reference |
| --- | --- |
| Customer | `00000000-0000-0000-0000-000000000001` |
| Checking account | `00000000-0000-0000-0000-000000001001` |
| Savings account | `00000000-0000-0000-0000-000000001002` |
| Second customer account | `00000000-0000-0000-0000-000000001003` |
| Suspense account | `00000000-0000-0000-0000-000000001101` |
| Completed transfer | `00000000-0000-0000-0000-000000005001` |
| Reversed transfer | `00000000-0000-0000-0000-000000005002` |
| Ledger transaction | `00000000-0000-0000-0000-000000002001` |
| Reconciliation batch | `00000000-0000-0000-0000-000000008001` |

Sample users are available at `GET /api/v1/dev/auth/sample-users`.

## Troubleshooting

- Oracle port already in use: stop the conflicting service or change the compose port mapping.
- CloudBeaver login does not work: the CloudBeaver volume may already be initialized. Run `make deps-reset`.
- API cannot connect to Oracle: verify `docker compose -f compose.dev.yaml ps` shows Oracle healthy and that `.env` matches the documented credentials.
- JWT authentication fails locally: make sure the API is running with `SPRING_PROFILES_ACTIVE=dev` and issue a fresh token because dev tokens expire quickly.
- Kafka topic errors: restart dependencies with `make deps-up`; the `kafka-init` service creates required topics.
- Migration validation fails after changing repeatable seed data: reset local volumes with `make deps-reset` in development.
