#!/usr/bin/env sh
set -eu

if [ -z "${JAVA_HOME:-}" ] && [ -x "$HOME/.local/jdk-21/bin/java" ]; then
  JAVA_HOME="$HOME/.local/jdk-21"
  export JAVA_HOME
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
fi

./mvnw --quiet -pl desktop/pdf-batch-desktop -am install -DskipTests
exec ./mvnw --quiet -pl desktop/pdf-batch-desktop javafx:run
