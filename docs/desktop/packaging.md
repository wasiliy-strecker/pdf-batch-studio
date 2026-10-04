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

Linux release archive and Debian package (install `fakeroot` for the latter):

```bash
./scripts/package-desktop.sh linux
```

## Installers

Installers must be built on the target platform:

- Windows x64: `package-desktop.ps1 -PackageType exe`. WiX 3 is required.
- macOS ARM64 or Intel: `package-desktop.sh dmg`.
- Linux x64: `package-desktop.sh linux` creates a compressed application image
  and a `.deb` when `fakeroot` is available.

The `Release packages` GitHub Actions workflow builds all variants and uploads
them as workflow artifacts for tags matching `v*` or a manual run.

The current packages are unsigned. Code-signing certificates, Apple
notarization credentials, and update infrastructure are deliberately absent.
No signing secrets belong in the public repository.

The application image includes a Java 21 runtime, JavaFX, PDFBox, the XLSX
reader and SQLite driver. The installed application works offline. Project
data lives in the operating system's user data directory, independently of
the installation directory. Updating the application does not delete it.

The Linux launcher can be started after extracting the archive:

```bash
"PDF Batch Studio Desktop/bin/PDF Batch Studio Desktop"
```

For Debian or Ubuntu, install the downloaded package through the system's
package installer or `sudo apt install ./pdf-batch-studio-desktop-1.0.0-linux-x64.deb`.
Native dependencies are derived from the build host and include GTK 3, OpenGL
and fontconfig for JavaFX. Use a compatible distribution release. The archive
also needs these system libraries even though Java itself is bundled.
