# Roadmap

## Studio v0.1 release candidate

- validate the PostgreSQL Testcontainers suite in CI with Docker access
- build all native desktop and server release artifacts from one version
- publish checksums, CycloneDX SBOMs, and reviewed third-party notices
- add startup reconciliation for interrupted queued and processing jobs
- add dependency, container, and source vulnerability scans
- tag `v0.1.0` only after the public repository and release workflow are ready

## Product workflow

- saved templates, field mappings, and filename presets
- searchable processing history and configurable retention
- native drag-and-drop and remembered non-sensitive desktop preferences
- optional server-sent events with polling retained as a tested fallback
- authentication, ownership, API keys, and rate limiting for public hosting
- accessibility audit and broader browser/desktop workflow coverage

## Advanced document automation

- flat-PDF text and image overlays
- signatures, QR codes, barcodes, and conditional content
- ZIP and batch-folder input
- webhooks, object storage, team workspaces, and audit events
- separately deployable workers through the existing ports
- code signing, Apple notarization, and opt-in desktop update checks

All application capabilities remain part of the Studio codebase. Commercial
rights, hosted service, support, and signed distribution can be offered under a
separate agreement without maintaining a reduced public edition.
