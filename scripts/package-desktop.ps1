param(
    [ValidateSet("app-image", "exe")]
    [string]$PackageType = "app-image"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Module = Join-Path $Root "desktop\pdf-batch-desktop"
$AppName = "PDF Batch Studio Desktop"
$MainClass = "de.appfabrik.pdfbatch.desktop.DesktopLauncher"

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw "JAVA_HOME must point to a JDK 21 installation containing jpackage."
}
$JPackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path $JPackage)) {
    throw "jpackage was not found below JAVA_HOME."
}

function Get-MavenValue([string]$Expression) {
    $Output = & "$Root\mvnw.cmd" --quiet -Dstyle.color=never -DforceStdout `
        help:evaluate "-Dexpression=$Expression"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    return ($Output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Last 1).Trim()
}

$MavenVersion = Get-MavenValue "project.version"
$AppVersion = Get-MavenValue "studio.version"
$MainJar = "pdf-batch-desktop-$MavenVersion.jar"

& "$Root\mvnw.cmd" --batch-mode --no-transfer-progress `
    -pl desktop/pdf-batch-desktop -am install -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$InputDirectory = Join-Path $Module "target\package-input"
$LegalDirectory = Join-Path $InputDirectory "legal"
$ImageDestination = Join-Path $Module "target\desktop-image"
$PackageDestination = Join-Path $Module "target\package-staging"
$Distribution = Join-Path $Module "target\desktop-dist"
Remove-Item $InputDirectory, $ImageDestination, $PackageDestination, $Distribution `
    -Recurse -Force -ErrorAction SilentlyContinue
New-Item $InputDirectory, $LegalDirectory, $ImageDestination, $PackageDestination, $Distribution `
    -ItemType Directory | Out-Null

& "$Root\mvnw.cmd" --batch-mode --no-transfer-progress `
    -pl desktop/pdf-batch-desktop dependency:copy-dependencies `
    -DincludeScope=runtime "-DoutputDirectory=$InputDirectory"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Copy-Item (Join-Path $Module "target\$MainJar") (Join-Path $InputDirectory $MainJar)
@("LICENSE", "NOTICE", "EVALUATION-GRANT.md", "COMMERCIAL-LICENSE.md", "THIRD_PARTY_NOTICES.md") |
    ForEach-Object { Copy-Item (Join-Path $Root $_) $LegalDirectory }

$Common = @(
    "--name", $AppName,
    "--app-version", $AppVersion,
    "--vendor", "Wasiliy Strecker",
    "--description", "Offline PDF batch generation from AcroForm templates and CSV data",
    "--copyright", "Copyright 2026 Wasiliy Strecker",
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
    & $JPackage @Common --type exe --dest $PackageDestination `
        --license-file (Join-Path $Root "LICENSE") `
        --win-dir-chooser --win-menu --win-menu-group "PDF Batch Studio" --win-shortcut
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $Generated = Get-ChildItem $PackageDestination -Filter "*.exe" | Select-Object -First 1
    if ($null -eq $Generated) { throw "jpackage did not create an EXE" }
    $Architecture = if ($env:PROCESSOR_ARCHITECTURE -eq "ARM64") { "arm64" } else { "x64" }
    $Target = Join-Path $Distribution "pdf-batch-studio-desktop-$AppVersion-windows-$Architecture.exe"
    Move-Item $Generated.FullName $Target
    Write-Host "Created $Target"
} else {
    Write-Host "Created and smoke-tested $(Join-Path $ImageDestination $AppName)"
}
