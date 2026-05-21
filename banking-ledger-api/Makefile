SHELL := /bin/bash

.PHONY: help deps-up deps-down deps-reset ci-deps-up ci-deps-down run test integration-test validate package docker-build dependency-check migrate token-customer token-teller token-auditor token-ops token-service reports

help:
	@printf "Targets:\n"
	@printf "  deps-up            Start Oracle, Kafka, and CloudBeaver\n"
	@printf "  deps-down          Stop local dependencies\n"
	@printf "  deps-reset         Stop dependencies and remove development volumes\n"
	@printf "  ci-deps-up         Start Oracle and Kafka with the CI compose file\n"
	@printf "  ci-deps-down       Stop CI dependencies and remove volumes\n"
	@printf "  run                Run the API with the dev profile\n"
	@printf "  test               Run unit and slice tests\n"
	@printf "  integration-test   Run the Maven verify phase\n"
	@printf "  dependency-check   Run OWASP dependency vulnerability scan\n"
	@printf "  docker-build       Build the API Docker image\n"
	@printf "  migrate            Apply Flyway migrations through Spring Boot startup validation\n"
	@printf "  token-*            Issue a dev JWT for a sample role\n"
	@printf "  reports            List report SQL files\n"

deps-up:
	docker compose -f compose.dev.yaml up -d

deps-down:
	docker compose -f compose.dev.yaml down

deps-reset:
	docker compose -f compose.dev.yaml down -v

ci-deps-up:
	docker compose -f compose.ci.yaml up -d

ci-deps-down:
	docker compose -f compose.ci.yaml down -v

run:
	SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

test:
	./mvnw test

integration-test:
	./mvnw verify

validate:
	./mvnw -DskipTests validate

package:
	./mvnw clean package

docker-build:
	docker build --build-arg APP_VERSION=local --build-arg VCS_REF=$$(git rev-parse --short HEAD 2>/dev/null || echo local) --build-arg BUILD_DATE=$$(date -u +%Y-%m-%dT%H:%M:%SZ) --build-arg SOURCE_REPOSITORY=local -t banking-ledger-api:local .

dependency-check:
	./mvnw org.owasp:dependency-check-maven:check

migrate:
	SPRING_PROFILES_ACTIVE=dev ./mvnw -DskipTests spring-boot:run

token-customer:
	curl -sS -X POST http://localhost:8080/api/v1/dev/auth/tokens -H 'Content-Type: application/json' -d '{"role":"CUSTOMER","subject":"dev-customer","actorId":"customer-user-1","customerId":"00000000-0000-0000-0000-000000000001"}'

token-teller:
	curl -sS -X POST http://localhost:8080/api/v1/dev/auth/tokens -H 'Content-Type: application/json' -d '{"role":"TELLER","subject":"dev-teller","actorId":"teller-user-1"}'

token-auditor:
	curl -sS -X POST http://localhost:8080/api/v1/dev/auth/tokens -H 'Content-Type: application/json' -d '{"role":"AUDITOR","subject":"dev-auditor","actorId":"auditor-user-1"}'

token-ops:
	curl -sS -X POST http://localhost:8080/api/v1/dev/auth/tokens -H 'Content-Type: application/json' -d '{"role":"OPS_ADMIN","subject":"dev-ops-admin","actorId":"ops-user-1"}'

token-service:
	curl -sS -X POST http://localhost:8080/api/v1/dev/auth/tokens -H 'Content-Type: application/json' -d '{"role":"SERVICE","subject":"dev-service","actorId":"service-client-1"}'

reports:
	find reports/sql -maxdepth 1 -type f -name '*.sql' -print | sort
