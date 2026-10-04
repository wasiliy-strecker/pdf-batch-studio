# ADR 0005: Add an offline JavaFX delivery adapter

## Status

Accepted.

## Decision

PDF Batch Studio includes a JavaFX 21 desktop module in the same public
repository. It reuses the framework-free core and document modules directly and
provides desktop-specific in-memory job storage, bounded dispatch, a managed
workspace, preview rendering, and FXML/CSS UI adapters. SQLite through JDBC and
Flyway stores saved projects, settings and history.

The desktop application is fully offline. It does not embed or start Spring
Boot and does not call the REST API. Desktop 1.0 opts into additional form field
types and password handling in the shared engine. The default server contract
remains compatible. Resource limits are configuration rather than product editions.

Native packages are built with the JDK `jpackage` tool on Windows, macOS and Linux.

## Consequences

- Shared PDF and CSV behavior is fixed and tested once.
- The repository demonstrates both SaaS backend and native desktop Java.
- Desktop use has no deployment or network prerequisite.
- Platform-specific JavaFX libraries and installers must be built on each target
  operating system.
- Initial packages are unsigned and may trigger operating-system warnings.
