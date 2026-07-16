# PDF Batch Studio

PDF Batch Studio turns rows from a UTF-8 CSV file into personalized PDFs.
It is available as a self-hosted Spring Boot web application and as a fully
offline JavaFX desktop application for Windows and macOS. Both inspect an
interactive AcroForm template, map CSV columns to text fields, preview the first
record, and create a ZIP archive.

The public source tree contains the complete product. It has no runtime license
check, watermark, payment integration, or unavailable dependency. Permitted use
is governed by the source-available license described below.

## What works

- AcroForm text-field inspection and filling with Apache PDFBox
- comma, semicolon, and tab-delimited UTF-8 CSV files
- manual CSV-to-PDF field mapping
- filename patterns such as `{customerId}-{name}.pdf`
- first-row PDF preview
- asynchronous jobs with polling, progress, cancellation, and partial success
- configurable processing safety limits with a 10,000-row default
- ZIP results with `documents/*.pdf`, `manifest.json`, and `errors.csv`
- bounded, configurable server workers, queue capacity, and active-job capacity
- automatic one-hour retention and idempotent cleanup
- local H2 persistence for zero-setup testing
- PostgreSQL, Docker Compose, Flyway, REST/OpenAPI, and a responsive web UI
- native JavaFX desktop UI with no server, database, account, or network access

## Quick start: local web application

Requirements:

- a full JDK 21 installation (`javac` must be available)
- Bash-compatible shell on Linux/macOS, or Maven Wrapper on Windows

Run:

```bash
./scripts/dev.sh
```

Open <http://localhost:8080/>. The local profile uses an embedded file-based H2
database and stores disposable job data below
`server/pdf-batch-server/.local-data/`. It is intended only for local testing.

Try the checked-in files:

- PDF: `samples/customer-template.pdf`
- CSV: `samples/customers.csv`
- map `fullName` to `name`
- map `customerNumber` to `customerId`
- use `{customerId}-{name}.pdf` as the filename pattern

## Quick start: offline desktop application

Run the JavaFX application on Linux or macOS:

```bash
./scripts/desktop-dev.sh
```

On Windows PowerShell:

```powershell
.\scripts\desktop-dev.ps1
```

The desktop application runs the shared Java document engine directly. It does
not start Spring Boot, open a network port, or use H2/PostgreSQL. Inputs are
copied into an isolated operating-system temporary directory and removed after
export, cancellation, failure, or normal application shutdown. Only the ZIP
location selected by the user persists.

![PDF Batch Studio Desktop](docs/screenshots/pdf-batch-desktop.png)

### Native packages

Create and smoke-test a local application image with:

```bash
./scripts/package-desktop.sh app-image
```

Windows uses `scripts/package-desktop.ps1`. The `Desktop packages` GitHub
Actions workflow creates a Windows x64 EXE plus macOS ARM64 and Intel DMGs on
their respective operating systems. The first release packages are intentionally
unsigned, so Windows SmartScreen or macOS Gatekeeper may show a warning.

## PostgreSQL with Docker Compose

```bash
cp .env.example .env
docker compose up --build
```

The web application is available at <http://localhost:8080/> and PostgreSQL is
bound to `127.0.0.1:54329`. Stop it with:

```bash
docker compose down
```

![PDF Batch Studio upload screen](docs/screenshots/studio-upload.png)

![Completed Studio batch job](docs/screenshots/studio-completed-job.png)

Use `docker compose down -v` only when you intentionally want to delete the
local database and job volumes.

## Verification

```bash
./scripts/test-all.sh
```

The build runs unit, PDF/CSV, security-boundary, application smoke, and real
HTTP workflow tests. The PostgreSQL/Testcontainers test runs when Docker is
available and is skipped with an explicit reason otherwise.

Useful individual commands:

```bash
./mvnw verify
./mvnw -pl core/pdf-batch-document -am test
./mvnw -pl server/pdf-batch-server -am test
./mvnw -pl desktop/pdf-batch-desktop -am test
./scripts/generate-sample.sh
```

On this machine, set `JAVA_HOME` to a full JDK if the system `java` command is
only a JRE. The scripts automatically use `$HOME/.local/jdk-21` when present.

## API and health

While the application is running:

- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- health: <http://localhost:8080/actuator/health>
- readiness: <http://localhost:8080/actuator/health/readiness>

The API is versioned below `/api/v1`. Its upload lifecycle is documented in
[`docs/api/upload-lifecycle.md`](docs/api/upload-lifecycle.md).

## Repository layout

```text
pdf-batch-studio/
├── core/
│   ├── pdf-batch-core/          domain, use cases, and ports
│   └── pdf-batch-document/      PDFBox, CSV, ZIP, and local files
├── server/
│   ├── pdf-batch-persistence/   JPA, PostgreSQL, and Flyway
│   └── pdf-batch-server/        Spring Boot, REST, Thymeleaf, and HTMX
├── desktop/
│   └── pdf-batch-desktop/       JavaFX offline application and adapters
├── docs/                        ADRs, API notes, and portfolio case study
├── infrastructure/              production-shaped container image
├── samples/                     safe PDF and CSV files
├── scripts/                     local development and verification helpers
└── compose.yml
```

This directory is one independent Git repository. Server, desktop, and shared
core are intentionally released together; see
[`PRODUCT_STRUCTURE.md`](PRODUCT_STRUCTURE.md).

## Known limitations

- AcroForm text fields only; checkboxes, signatures, image overlays, and flat
  PDFs are not supported.
- Resource limits remain configurable safeguards; desktop processing uses one
  local worker and server processing defaults to two workers and a bounded queue.
- Polling is used instead of server-sent events.
- Anonymous access assumes a trusted local/self-hosted environment. Random job
  UUIDs act as unguessable handles, not as user authentication.
- The local H2 profile is a convenience environment, not the future hosted
  production database.
- Desktop installers are not yet code-signed, notarized, or auto-updating.

## License

Copyright (c) 2026 Wasiliy Strecker.

Source-available under the PolyForm Noncommercial License 1.0.0. It is not open
source. Recruitment and technical evaluation by companies is additionally
permitted under [`EVALUATION-GRANT.md`](EVALUATION-GRANT.md). Other commercial
use requires a separate written agreement; see
[`COMMERCIAL-LICENSE.md`](COMMERCIAL-LICENSE.md).

The required copyright notice is in [`NOTICE`](NOTICE), the licensing rationale
is recorded in [`LICENSE-DECISION.md`](LICENSE-DECISION.md), and bundled
dependencies remain under the licenses listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
