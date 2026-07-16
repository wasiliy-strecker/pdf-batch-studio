# Product and repository structure

## Decision

PDF Batch Studio is developed and released from one public source-available Git
repository. The repository contains one shared processing engine and two
complete delivery applications:

```text
pdf-batch-studio/
├── core/       shared domain, PDF, CSV, storage, and ZIP behavior
├── server/     Spring Boot web/API application and persistence
├── desktop/    fully offline JavaFX application
└── distribution/
```

Desktop and server are separate products from a user's perspective, but they do
not duplicate processing code. Both are built from the same tag and validated
against the same semantic document tests.

## Licensing boundary

The public repository contains the complete runnable product. There is no
runtime edition switch, hidden dependency, payment integration, or license-key
requirement. PolyForm Noncommercial controls permitted use of the original
source; a separate written agreement grants commercial rights.

Future product capabilities are added to this repository unless they contain
credentials, customer data, production-only infrastructure, or third-party
material that cannot be published.

## Release model

One version produces native desktop packages, a server JAR, a Docker server
bundle, a container image, checksums, and an SBOM. A fix to shared behavior is
made once in `core/`, then verified through both server and desktop workflows.

Separate edition branches, copied core trees, and a second product repository
are intentionally not used because they would drift and obscure the actual
architecture.
