# Branch Protection

Recommended GitHub branch protection for `main`:

- Require pull requests before merging.
- Require the latest branch to be up to date before merging.
- Require status checks from `Backend CI`.
- Require conversation resolution.
- Restrict force pushes and branch deletion.

Required checks:

- `Build, Test, Package`
- `Secret Scan`
- `Dependency Review` after GitHub dependency graph is enabled.

Local parity commands from `banking-ledger-api/`:

```bash
make ci-deps-up
./mvnw -DskipTests validate
./mvnw test
./mvnw verify
make dependency-check
make docker-build
make ci-deps-down
```

If a check fails:

- Review the failing step logs first.
- Download uploaded test, coverage, dependency, or scan artifacts when present.
- Rerun failed jobs from the Actions run page after confirming the failure is transient or after pushing a fix.
- Do not suppress vulnerability findings without documenting the false-positive reason and review date.
