# Repository agent guide

## Scope

This repository contains only PDF Batch Studio. It must remain independently
cloneable, buildable, testable, and useful without access to a private PRO
repository or AppFabrik production infrastructure.

Do not add credentials, customer files, license secrets, payment code, private
endpoints, or private PRO implementations. Safe generated fixtures belong in
`samples/` or test code.

## Architecture

- `pdf-batch-core`: framework-free domain, application services, and ports.
- `pdf-batch-document`: PDFBox, Commons CSV, ZIP, and filesystem adapters.
- `pdf-batch-persistence`: JPA entities, repositories, and Flyway migrations.
- `pdf-batch-server`: Spring Boot wiring, REST API, web UI, and configuration.
- `pdf-batch-desktop`: JavaFX UI and offline desktop-only adapters.

Dependencies point inward. Core must not import Spring, JPA, PDFBox, or web
types. Keep the initial product a modular monolith with one bounded worker.

## Commands

Run from the repository root:

```bash
./scripts/test-all.sh
./scripts/dev.sh
./scripts/desktop-dev.sh
./scripts/package-desktop.sh app-image
./scripts/generate-sample.sh
```

Before committing Java changes, run `./mvnw verify`. If Docker is unavailable,
the PostgreSQL Testcontainers test may skip, but all H2-backed and document
workflow tests must pass.

## Change rules

- Add or update a focused test for behavior changes.
- Keep `/api/v1` backward compatible unless a documented breaking version is
  intentionally introduced.
- Keep the desktop module free of Spring, JPA, databases, network clients, and
  private PRO dependencies.
- Never trust upload filenames or use them as paths.
- Keep streaming CSV iteration, bounded queues, generated UUID directories,
  deterministic ZIP entry names, and cleanup behavior intact.
- Update `CHANGELOG.md` and relevant docs for user-visible changes.
- Do not introduce Community-to-PRO dependencies.
