# Roadmap

## Community v0.1 release candidate

- validate the PostgreSQL Testcontainers suite in CI with Docker access
- add browser screenshots after final visual review
- add startup reconciliation for interrupted `QUEUED`/`PROCESSING` jobs
- add rate limiting guidance for public self-hosting
- run dependency and container vulnerability scans
- tag `v0.1.0` after repository URL and release ownership are confirmed

## Community improvements

- optional server-sent events after polling remains the tested fallback
- accessibility audit and additional browser workflow tests
- more AcroForm appearance/font compatibility fixtures
- operational metrics without document content or personal data
- documented backup and restore for PostgreSQL deployments

## Private PRO milestones

The private repository is intentionally deferred. Its first milestone may add
higher limits, saved templates/mappings, filename presets, history, parallel
jobs, longer retention, and an adapter for the existing AppFabrik annual-license
system. See `REPO_SPLIT.md`.

Flat-PDF overlays, images, signatures, QR/barcodes, conditional content, API
keys, webhooks, teams, object storage, distributed workers, and white-label
features remain later options rather than initial scope.
