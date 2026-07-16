# ADR 0002: Keep the complete Studio product in one repository

- Status: accepted
- Date: 2026-07-16

## Decision

Maintain the shared engine, Spring Boot server, and offline JavaFX application
in one source-available repository. Build both delivery applications from the
same versioned core. Use licensing terms, not duplicated source or runtime
feature gates, to distinguish permitted noncommercial evaluation from
commercial use.

## Consequences

Shared PDF/CSV behavior is fixed and tested once, every checkout is complete,
and one GitHub release can offer server and desktop downloads. Module boundaries
remain explicit, while multi-repository version coordination and source drift
are avoided.
