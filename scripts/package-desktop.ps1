param(
    [ValidateSet("app-image", "exe")]
    [string]$PackageType = "app-image"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Module = Join-Path $Root "desktop\pdf-batch-desktop"
$AppName = "PDF Batch Studio Desktop"
$AppVersion = "0.1.0"
$MainClass = "de.appfabrik.pdfbatch.desktop.DesktopLauncher"
$MainJar = "pdf-batch-desktop-0.1.0-SNAPSHOT.jar"

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw "JAVA_HOME must point to a JDK 21 installation containing jpackage."
}
$JPackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path $JPackage)) {
    throw "jpackage was not found below JAVA_HOME."
}

& "$Root\mvnw.cmd" --batch-mode --no-transfer-progress `
    -pl desktop/pdf-batch-desktop -am install -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$InputDirectory = Join-Path $Module "target\package-input"
$ImageDestination = Join-Path $Module "target\desktop-image"
$Distribution = Join-Path $Module "target\desktop-dist"
Remove-Item $InputDirectory, $ImageDestination, $Distribution -Recurse -Force -ErrorAction SilentlyContinue
New-Item $InputDirectory, $ImageDestination, $Distribution -ItemType Directory | Out-Null

& "$Root\mvnw.cmd" --batch-mode --no-transfer-progress `
    -pl desktop/pdf-batch-desktop dependency:copy-dependencies `
    -DincludeScope=runtime "-DoutputDirectory=$InputDirectory"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Copy-Item (Join-Path $Module "target\$MainJar") (Join-Path $InputDirectory $MainJar)

$Common = @(
    "--name", $AppName,
    "--app-version", $AppVersion,
    "--vendor", "AppFabrik",
    "--description", "Offline PDF batch generation from AcroForm templates and CSV data",
    "--input", $InputDirectory,
    "--main-jar", $MainJar,
    "--main-class", $MainClass,
    "--java-options", "-Dfile.encoding=UTF-8"
)

& $JPackage @Common --type app-image --dest $ImageDestination
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$Launcher = Join-Path $ImageDestination "$AppName\$AppName.exe"
$VersionOutput = (& $Launcher --version | Out-String).Trim()
if ($VersionOutput -ne "$AppName $AppVersion") {
    throw "Packaged launcher returned unexpected version: $VersionOutput"
}

if ($PackageType -eq "exe") {
    & $JPackage @Common --type exe --dest $Distribution `
        --license-file (Join-Path $Root "LICENSE") `
        --win-dir-chooser --win-menu --win-shortcut
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "Created $Distribution"
} else {
    Write-Host "Created and smoke-tested $(Join-Path $ImageDestination $AppName)"
}
