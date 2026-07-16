# Architecture

PDF Batch Community is a Java 21 modular monolith with two delivery adapters: a
Spring Boot web application and an offline JavaFX desktop application. Its
module boundaries keep document processing and use cases independent of both UI
frameworks so a separate worker can be introduced later without rewriting the
domain.

```mermaid
flowchart LR
    Browser[Thymeleaf + HTMX UI] --> API[Spring MVC /api/v1]
    Desktop[JavaFX desktop UI] --> DesktopAdapter[In-memory repository + local dispatcher]
    API --> App[Application services]
    DesktopAdapter --> App
    App --> Domain[Domain model]
    App --> DocPort[DocumentEngine port]
    App --> RepoPort[JobRepository port]
    App --> StorePort[JobWorkspace port]
    DocPort --> PDF[PDFBox + Commons CSV]
    RepoPort --> JPA[JPA + Flyway]
    JPA --> DB[(H2 local / PostgreSQL)]
    StorePort --> Files[(isolated job directories)]
    App --> Queue[bounded in-process worker]
    Queue --> PDF
    DesktopAdapter --> Temp[(session temp workspace)]
```

## Module responsibilities

### `pdf-batch-core`

Owns `BatchJob`, explicit state transitions, validation, application use cases,
worker orchestration, and ports. It has no framework dependencies.

### `pdf-batch-document`

Inspects and fills AcroForm text fields, validates and streams CSV records,
generates safe collision-free filenames, writes ZIP entries, and implements the
isolated local workspace.

### `pdf-batch-persistence`

Maps the domain aggregate to normalized JPA tables. Flyway is the only schema
creation mechanism. The adapter shields the core from JPA.

### `pdf-batch-app`

Wires adapters, exposes REST/OpenAPI and the server-rendered UI, applies HTTP
security headers and request limits, runs retention cleanup, and owns the
single-thread bounded dispatcher.

### `pdf-batch-desktop`

Owns the JavaFX/FXML/CSS interface, desktop ViewModel, defensive in-memory job
repository, single-thread dispatcher, PDF-to-PNG preview rendering, temporary
session workspace, and result export. It depends only on `pdf-batch-core` and
`pdf-batch-document`; it does not depend on Spring, JPA, H2, or PostgreSQL.

## Workflow

```mermaid
sequenceDiagram
    actor User
    participant Web
    participant Jobs as JobApplicationService
    participant Docs as DocumentEngine
    participant Worker
    participant Store

    User->>Web: upload PDF + CSV
    Web->>Store: save generated job files
    Web->>Docs: inspect form and CSV
    Docs-->>User: fields, headers, delimiter, row count
    User->>Web: save mapping
    User->>Web: preview
    Web->>Docs: fill first CSV row
    Docs-->>User: preview.pdf
    User->>Web: start job
    Web->>Worker: enqueue UUID
    Worker->>Docs: stream rows and create ZIP
    Worker->>Web: persist progress
    User->>Web: poll status and download ZIP
```

## State model

The implemented main path is:

```text
DRAFT -> READY -> QUEUED -> PROCESSING -> PACKAGING
      -> COMPLETED | COMPLETED_WITH_ERRORS
```

Active jobs can move through `CANCELLING` to `CANCELLED`. Unexpected processing
errors move non-terminal jobs to `FAILED`; retention removes non-active expired
jobs and their files. Transitions and monotonic counters are tested in core.

## Local and PostgreSQL profiles

The default `local` profile uses an H2 file database in PostgreSQL compatibility
mode for a zero-setup test loop. `postgres` uses PostgreSQL 17 through Docker
Compose. Both use the same Flyway migration and Hibernate schema validation.
H2 is deliberately not presented as the future hosted deployment database.

## Offline desktop lifecycle

The desktop composition uses the same `JobApplicationService`, `JobWorker`,
`DocumentEngine`, state transitions, limits, and collision-safe ZIP generation
as the web application. Selected files are copied into a UUID job below a
per-launch operating-system temporary directory. After a terminal job, the ZIP
is copied atomically where possible to the user-selected path, then job inputs,
intermediate output, repository state, and the session directory are deleted.

No network client is present in the desktop module. One job and one worker are
allowed at a time, and the Community row limit remains 25.

## Scaling seam

`JobDispatchPort`, `JobRepository`, `DocumentEngine`, and `JobWorkspace` are
explicit ports. A later worker process can consume job IDs and reuse the domain
and document adapter. No message broker or distributed worker is part of this
release.
