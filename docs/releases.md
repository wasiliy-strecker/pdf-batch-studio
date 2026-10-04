# Release contract

## Version source

`studio.version` in the root Maven build is the single public product version.
Maven artifacts may retain a `-SNAPSHOT` suffix during development, while native
packages, server downloads, container tags, and the desktop `--version` output
use the public Studio version.

`native.package.version` is a separate monotonically increasing installer
metadata value because `jpackage` rejects a leading-zero version on macOS. It
does not appear in release filenames or the desktop `--version` output. Never
decrease it between native package releases.

## Local packaging

```bash
./scripts/package-server.sh
./scripts/smoke-server.sh target/release/pdf-batch-studio-server-*.jar
./scripts/package-desktop.sh linux
```

The server command produces an executable JAR, a Docker Compose ZIP, and an
aggregate CycloneDX JSON SBOM below `target/release/`. Desktop application
images include Apache License 2.0, the project notice and third-party notices.
The Linux build also creates a Debian package when `fakeroot` is installed.

## GitHub Actions

The `Release packages` workflow can be run manually without publishing a
release. A tag matching `v*` additionally:

1. checks that all tests and the container build pass.
2. builds server, Linux, Windows, and both macOS architectures.
3. publishes the versioned and `latest` images to GHCR.
4. merges the platform artifacts.
5. creates `SHA256SUMS`.
6. creates the GitHub release with all downloads.

The initial packages are unsigned. Signing certificates, Apple notarization,
and their credentials must be added only through protected repository secrets
after the unsigned pipeline is stable.

## Desktop 1.0 publication checklist

- review the local changes and wait for `Verify` after the authorized push.
- run `Release packages` manually and inspect every artifact.
- exercise the built-in example, project reopening and export on each target OS.
- tag `v1.0.0` only when publication is requested and platform checks pass.
- confirm that release downloads and the GHCR package are publicly accessible.
- verify the release checksums and container pull instructions.

Building locally does not publish a GitHub release. Linux packages can be
verified on Linux. Windows and macOS require their own runners and installation
checks before claiming platform validation.
