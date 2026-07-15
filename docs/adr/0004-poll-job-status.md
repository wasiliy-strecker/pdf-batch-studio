# ADR 0004: Poll job status in the first release

- Status: accepted
- Date: 2026-07-15

## Decision

Expose persisted progress through `GET /api/v1/jobs/{id}` and let the HTMX UI
poll every second while a job is active.

## Consequences

The complete flow is reliable and easy to test without connection lifecycle
complexity. Server-sent events may be added later while polling remains the
fallback.
