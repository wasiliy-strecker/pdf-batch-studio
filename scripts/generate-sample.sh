#!/usr/bin/env sh
set -eu

if [ -z "${JAVA_HOME:-}" ] && [ -x "$HOME/.local/jdk-21/bin/java" ]; then
  JAVA_HOME="$HOME/.local/jdk-21"
  export JAVA_HOME
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
fi

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"
./mvnw --quiet -pl core/pdf-batch-document -am install -DskipTests
./mvnw --quiet -pl core/pdf-batch-document \
  org.codehaus.mojo:exec-maven-plugin:3.6.3:java \
  -Dexec.mainClass=de.appfabrik.pdfbatch.document.SampleTemplateGenerator \
  -Dexec.args="$root/samples/customer-template.pdf"
