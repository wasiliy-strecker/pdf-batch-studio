$ErrorActionPreference = "Stop"
& .\mvnw.cmd verify
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
