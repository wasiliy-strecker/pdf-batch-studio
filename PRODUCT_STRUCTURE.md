# Product and repository structure

PDF Batch Studio is one independently buildable Apache-2.0 repository with a
shared Java engine, an offline JavaFX desktop application and an optional
self-hosted Spring Boot server. Development currently focuses on the desktop.

The desktop uses Controller → Service → Repository layering. JDBC persistence,
spreadsheet import and project exchange stay in the existing desktop module.
The core application model has no JavaFX, Spring or database dependency.

Desktop 1.0 uses local SQLite for projects and history. The existing server uses
JPA with PostgreSQL or its H2 development profile. They share processing code,
not a runtime database or network requirement.

One product version identifies all artifacts. Native installer metadata uses
its own monotonically increasing version. Windows, macOS and Linux packages
include Java. No paid edition, runtime licensing or private infrastructure is
required. Preserve Apache and third-party notices in every distribution.
