# Portfolio case study: PDF Batch Studio

## Problem

Teams often have an interactive PDF template and a spreadsheet of recipients,
but no safe, repeatable way to create individually named documents. Manual
copy/paste is slow and difficult to audit.

## Product outcome

PDF Batch Studio implements the whole local workflow twice over one shared
core: as a self-hosted Spring Boot application and as a native offline JavaFX
application. Both inspect an AcroForm and CSV, map fields, preview real output,
run a bounded asynchronous batch, and create a deterministic archive with
machine-readable and human-readable reports.

## Engineering highlights

- framework-free domain state machine with explicit transitions
- ports and adapters across a five-module Maven build
- semantic PDF tests instead of brittle binary comparisons
- strict upload, path, filename, ZIP, queue, and retention boundaries
- normalized persistence with Flyway and PostgreSQL integration coverage
- server-rendered progressive UI plus a versioned REST/OpenAPI API
- JavaFX/FXML/CSS desktop UI with an in-memory adapter and no network dependency
- `jpackage` app images and CI-native Windows/macOS installer builds
- reproducible safe PDF fixture generation
- public Community/private PRO repository boundary designed before paid code

## Validation

The end-to-end test creates a real AcroForm, uploads it through HTTP with a CSV,
saves mappings, verifies the preview value with PDFBox, waits for background
processing, downloads the ZIP, and checks every entry path and expected PDF.
The offline end-to-end test independently runs the shared engine without HTTP or
a database, exports a ZIP, and semantically checks generated PDFs. The packaged
desktop launcher is smoke-tested before installer creation.

## Trade-offs

Polling, one worker, local files, and a 25-row limit keep the initial release
understandable and useful. The ports preserve a later path to distributed
workers and object storage without pretending that complexity is already
needed.
