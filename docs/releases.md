# Release contract

## Version source

`studio.version` in the root Maven build is the single public product version.
Maven artifacts may retain a `-SNAPSHOT` suffix during development, while native
packages, server downloads, container tags, and the desktop `--version` output
use the public Studio version.

## Local packaging

```bash
./scripts/package-server.sh
./scripts/smoke-server.sh target/release/pdf-batch-studio-server-*.jar
./scripts/package-desktop.sh linux
```

The server command produces an executable JAR, a Docker Compose ZIP, and an
aggregate CycloneDX JSON SBOM below `target/release/`. Desktop application
images include the project license, required notice, evaluation grant,
commercial licensing information, and third-party notices.

## GitHub Actions

The `Release packages` workflow can be run manually without publishing a
release. A tag matching `v*` additionally:

1. checks that all tests and the container build pass;
2. builds server, Linux, Windows, and both macOS architectures;
3. publishes the versioned and `latest` images to GHCR;
4. merges the platform artifacts;
5. creates `SHA256SUMS`;
6. creates the GitHub release with all downloads.

The initial packages are unsigned. Signing certificates, Apple notarization,
and their credentials must be added only through protected repository secrets
after the unsigned pipeline is stable.

## First publication checklist

- create `wasiliy-strecker/pdf-batch-studio` as a public repository;
- enable GitHub Discussions for commercial licensing contact;
- push `main` and wait for `Verify` to pass;
- run `Release packages` manually and inspect every artifact;
- set `studio.version` and tag `v0.1.0` only after approval;
- make the first GHCR package public after its initial tagged publication;
- verify the release checksums and container pull instructions.
