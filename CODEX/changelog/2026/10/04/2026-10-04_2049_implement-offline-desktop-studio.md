# Implement offline PDF Batch Studio 1.0

Implemented the agreed desktop-first Java product in the existing modules.
Added generated examples, persistent projects and portable project archives,
CSV/TSV/XLSX import, mapping suggestions, preflight, record/page preview, zoom,
additional form field types, session-only passwords, safe folder/ZIP export
and durable processing history. The UI supports German and English.

Persistence uses SQLite, JDBC and Flyway with an exclusive application lock,
transactional project updates and interrupted-run recovery. The established
Spring Boot/JPA/PostgreSQL server remains compatible. No additional Maven
module or desktop server was introduced.

Applied the owner's Apache License 2.0 decision for free personal and business
use. Updated architecture, security, release documentation and safe screenshots.
Prepared native packaging for Windows, macOS and Linux, including a Linux
Debian package with explicit JavaFX system dependencies.

Verification:
- Formatted changed Java files with google-java-format 1.28.0, AOSP style.
- Maven verify passed all 52 tests with no failures or skips, including the
  existing PostgreSQL Testcontainers and HTTP workflows.
- Added two focused desktop workflow tests and extended the existing FXML
  smoke test through demo preview and project saving.
- Linux archive and Debian package built successfully. Checked launcher
  version, installer metadata and dependencies.
- A temporary harness ran against a copy of the actual packaged runtime and
  jars. SQLite migration, XLSX import, project persistence, preflight, rendered
  PDF preview, ZIP export and history all passed.
- Reviewed generated screenshots, shell syntax and diff whitespace.

Windows and macOS native builds and installation checks require their target
systems and were not executed locally. Linux installer metadata was inspected,
but the package was not installed into the host OS. No remote push or release
publication was performed. Build artifacts stay in ignored target directories.
