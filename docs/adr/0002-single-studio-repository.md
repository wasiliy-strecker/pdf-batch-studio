# ADR 0002: Keep the complete Studio product in one repository

- Status: accepted
- Date: 2026-07-16

## Decision

Maintain the shared engine, Spring Boot server, and offline JavaFX application
in one open-source repository. Build both delivery applications from the
same versioned core. As updated for desktop 1.0 on 2026-10-04, Apache License
2.0 permits both personal and commercial use. There are no paid editions or
runtime feature gates.

## Consequences

Shared PDF/CSV behavior is fixed and tested once, every checkout is complete,
and one GitHub release can offer server and desktop downloads. Module boundaries
remain explicit, while multi-repository version coordination and source drift
are avoided.
