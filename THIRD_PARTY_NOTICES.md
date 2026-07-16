# Third-party notices

PDF Batch Studio includes and depends on third-party software. Those components
remain under their own licenses; the PolyForm license applies only to original
PDF Batch Studio material owned by the licensor.

Important runtime and distribution components include:

| Component family | Primary license |
| --- | --- |
| Apache PDFBox and Apache Commons CSV | Apache License 2.0 |
| Spring Boot, Spring Framework, embedded Apache Tomcat, Jackson | Apache License 2.0 |
| springdoc-openapi | Apache License 2.0 |
| htmx | Zero-Clause BSD |
| OpenJFX / JavaFX | GNU GPL v2 with the Classpath Exception |
| Hibernate ORM | GNU LGPL 2.1 |
| Flyway database migration runtime | Apache License 2.0 |
| PostgreSQL JDBC driver | BSD-style PostgreSQL License |
| H2 Database Engine | MPL 2.0 or EPL 1.0 |
| Logback | EPL 1.0 or GNU LGPL 2.1 |
| Maven Wrapper | Apache License 2.0 |

Exact versions are pinned by the Maven build and recorded in the CycloneDX SBOM
created for each release. Transitive dependencies and their declared license
metadata must be reviewed from that SBOM before publishing a release.

Project and license sources:

- <https://pdfbox.apache.org/>
- <https://commons.apache.org/proper/commons-csv/>
- <https://spring.io/projects/spring-boot>
- <https://openjfx.io/>
- <https://hibernate.org/orm/>
- <https://documentation.red-gate.com/fd/licensing-277578981.html>
- <https://jdbc.postgresql.org/>
- <https://h2database.com/>
- <https://logback.qos.ch/license.html>
