# Desktop packaging

## Local development

Use a full JDK 21. On Linux or macOS:

```bash
./scripts/desktop-dev.sh
```

On Windows PowerShell:

```powershell
.\scripts\desktop-dev.ps1
```

## Application images

`jpackage` bundles the application, JavaFX native libraries, dependencies, and
a Java runtime. A separate Java installation is not required on the target
computer.

Linux/macOS development image:

```bash
./scripts/package-desktop.sh app-image
```

Windows development image:

```powershell
.\scripts\package-desktop.ps1 -PackageType app-image
```

Each script executes the packaged launcher with `--version` before succeeding.
Generated files remain below `desktop/pdf-batch-desktop/target/` and are ignored
by Git.

Linux release archive:

```bash
./scripts/package-desktop.sh linux
```

## Installers

Installers must be built on the target platform:

- Windows x64: `package-desktop.ps1 -PackageType exe`; WiX 3 is required.
- macOS ARM64 or Intel: `package-desktop.sh dmg`.
- Linux x64: `package-desktop.sh linux` creates a compressed application image.

The `Release packages` GitHub Actions workflow builds all variants and uploads
them as workflow artifacts for tags matching `v*` or a manual run.

Version 0.1 packages are unsigned. Code-signing certificates, Apple
notarization credentials, and update infrastructure are deliberately absent;
no signing secrets belong in the public repository.
