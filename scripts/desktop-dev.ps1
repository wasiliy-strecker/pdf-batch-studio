$ErrorActionPreference = "Stop"

& "$PSScriptRoot\..\mvnw.cmd" --quiet -pl desktop/pdf-batch-desktop -am install -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& "$PSScriptRoot\..\mvnw.cmd" --quiet -pl desktop/pdf-batch-desktop javafx:run
exit $LASTEXITCODE
