# ADR 0005: Add an offline JavaFX delivery adapter

## Status

Accepted.

## Decision

PDF Batch Studio includes a JavaFX 21 desktop module in the same public
repository. It reuses the framework-free core and document modules directly and
provides desktop-specific in-memory repository, bounded dispatcher, temporary
workspace, preview renderer, and FXML/CSS UI adapters.

The desktop application is fully offline. It does not embed or start Spring
Boot, does not call the REST API, and does not use a database. Community limits
and document validation remain identical across the web and desktop adapters.

Native packages are built with the JDK `jpackage` tool on Windows and macOS.

## Consequences

- Shared PDF and CSV behavior is fixed and tested once.
- The repository demonstrates both SaaS backend and native desktop Java.
- Desktop use has no deployment or network prerequisite.
- Platform-specific JavaFX libraries and installers must be built on each target
  operating system.
- Initial packages are unsigned and may trigger operating-system warnings.
