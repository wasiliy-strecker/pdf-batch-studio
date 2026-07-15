# Portfolio case study: PDF Batch Community

## Problem

Teams often have an interactive PDF template and a spreadsheet of recipients,
but no safe, repeatable way to create individually named documents. Manual
copy/paste is slow and difficult to audit.

## Product outcome

PDF Batch Community implements the whole local workflow: inspect an AcroForm
and CSV, map fields, preview real output, run a bounded asynchronous batch, and
download a deterministic archive with machine-readable and human-readable
reports.

## Engineering highlights

- framework-free domain state machine with explicit transitions
- ports and adapters across a four-module Maven build
- semantic PDF tests instead of brittle binary comparisons
- strict upload, path, filename, ZIP, queue, and retention boundaries
- normalized persistence with Flyway and PostgreSQL integration coverage
- server-rendered progressive UI plus a versioned REST/OpenAPI API
- reproducible safe PDF fixture generation
- public Community/private PRO repository boundary designed before paid code

## Validation

The end-to-end test creates a real AcroForm, uploads it through HTTP with a CSV,
saves mappings, verifies the preview value with PDFBox, waits for background
processing, downloads the ZIP, and checks every entry path and expected PDF.
The same workflow was also exercised manually against the running local app.

## Trade-offs

Polling, one worker, local files, and a 25-row limit keep the initial release
understandable and useful. The ports preserve a later path to distributed
workers and object storage without pretending that complexity is already
needed.
