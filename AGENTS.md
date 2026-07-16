# Repository agent guide

## Scope

This repository contains the complete PDF Batch Studio product. It must remain
independently cloneable, buildable, testable, and useful without private
infrastructure, payment services, or unavailable source dependencies.

Do not add credentials, customer files, license secrets, payment code,
production endpoints, or private documents. Safe generated fixtures belong in
`samples/` or test code.

## Architecture

- `core/pdf-batch-core`: framework-free domain, application services, and ports.
- `core/pdf-batch-document`: PDFBox, Commons CSV, ZIP, and filesystem adapters.
- `server/pdf-batch-persistence`: JPA entities, repositories, and Flyway.
- `server/pdf-batch-server`: Spring Boot, REST/OpenAPI, web UI, and configuration.
- `desktop/pdf-batch-desktop`: JavaFX UI and offline desktop adapters.

Dependencies point inward. Core must not import Spring, JPA, PDFBox, JavaFX, or
web types. Desktop must not depend on Spring, JPA, a database, or a network
client. Keep queues, workers, document limits, and file lifecycles bounded and
configurable.

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
the PostgreSQL Testcontainers test may skip, but all H2-backed, document, HTTP,
and desktop workflow tests must pass.

## Change rules

- Add or update a focused test for behavior changes.
- Keep `/api/v1` backward compatible unless a documented version change is
  intentional.
- Never trust upload filenames or use them as paths.
- Preserve streaming CSV iteration, bounded queues, generated UUID directories,
  deterministic ZIP entry names, and idempotent cleanup.
- Update `CHANGELOG.md`, architecture/security docs, and safe screenshots for
  user-visible changes.
- Keep original product code under PolyForm Noncommercial 1.0.0 and retain the
  required notice and all third-party notices.
- Do not accept outside code contributions until the owner has approved an
  appropriate contributor agreement.
