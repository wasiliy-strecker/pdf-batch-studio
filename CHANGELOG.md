# Changelog

All notable changes will be documented here. The format follows Keep a
Changelog, and releases will use semantic versioning.

## [Unreleased]

### Added

- Desktop 1.0 home screen with two offline examples, saved projects,
  portable project archives, German/English UI and processing history.
- SQLite/JDBC storage with Flyway migrations, normalized project tables,
  exclusive application locking and interrupted-run recovery.
- XLSX worksheet import, TSV support, data preview, mapping suggestions,
  preflight, selected-record/page PDF preview and zoom.
- Desktop checkbox, dropdown and radio fields, protected input and optional
  AES-256 output protection with session-only passwords.
- Safe folder export alongside ZIP export, retry after destination errors,
  row-specific error history and Debian/Ubuntu installation packages.
- Configurable 10,000-row safety default plus bounded server worker, queue, and
  active-job capacities without edition-based restrictions.
- Unified Windows, macOS, Linux, server-JAR, Docker/GHCR, checksum, and
  CycloneDX-SBOM release pipeline driven by one product version.
- Cross-platform native installer metadata compatible with macOS `jpackage`
  plus deterministic POM version loading in the Windows packaging script.
- Fully offline JavaFX desktop application with field mapping, semantic PDF
  preview, progress, cancellation, ZIP export, native packaging scripts, and
  Windows/macOS package workflows.
- Safe comma, semicolon, tab, UTF-8, filename-collision, row-limit, and invalid
  CSV samples for manual product testing.
- Java 21 and Spring Boot 4.1 modular-monolith foundation.
- AcroForm and CSV inspection, mapping validation, preview, batch generation,
  safe ZIP output, progress, cancellation, and retention cleanup.
- REST/OpenAPI API and responsive Thymeleaf/HTMX Studio UI.
- zero-setup local H2 profile and PostgreSQL Docker Compose profile.
- Flyway persistence, safe samples, automated semantic PDF/ZIP tests, real HTTP
  end-to-end test, and optional PostgreSQL Testcontainers verification.
- Third-party notices, security model and architecture documentation.

### Changed

- License original project code and documentation under Apache License 2.0 for
  free personal and commercial use. Remove evaluation and commercial grants.
- Set the desktop product version to 1.0.0 and native installer metadata to
  1.0.1. Preserve the established server API and default document behavior.
