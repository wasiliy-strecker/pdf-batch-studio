#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
if [[ -z "${JAVA_HOME:-}" && -x "$HOME/.local/jdk-21/bin/java" ]]; then
  export JAVA_HOME="$HOME/.local/jdk-21"
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
  printf 'JAVA_HOME must point to a full JDK 21 installation.\n' >&2
  exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

maven_value() {
  "$ROOT/mvnw" --quiet -Dstyle.color=never -DforceStdout \
    help:evaluate -Dexpression="$1" | tail -n 1 | tr -d '\r'
}

MAVEN_VERSION=$(maven_value project.version)
STUDIO_VERSION=$(maven_value studio.version)
CYCLONEDX_VERSION=$(maven_value cyclonedx-maven-plugin.version)
SERVER_TARGET="$ROOT/server/pdf-batch-server/target"
RELEASE_ROOT="$ROOT/target/release"
BUNDLE_NAME="pdf-batch-studio-server-$STUDIO_VERSION-docker"
BUNDLE="$ROOT/target/$BUNDLE_NAME"

"$ROOT/mvnw" --batch-mode --no-transfer-progress package -DskipTests
"$ROOT/mvnw" --batch-mode --no-transfer-progress \
  "org.cyclonedx:cyclonedx-maven-plugin:$CYCLONEDX_VERSION:makeAggregateBom" \
  -DincludeTestScope=false \
  -DoutputFormat=json \
  -DoutputName=pdf-batch-studio.cdx \
  -DschemaVersion=1.6

rm -rf "$RELEASE_ROOT" "$BUNDLE"
mkdir -p "$RELEASE_ROOT" "$BUNDLE"

SERVER_JAR="$RELEASE_ROOT/pdf-batch-studio-server-$STUDIO_VERSION.jar"
cp "$SERVER_TARGET/pdf-batch-server-$MAVEN_VERSION.jar" "$SERVER_JAR"
cp "$ROOT/target/pdf-batch-studio.cdx.json" \
  "$RELEASE_ROOT/pdf-batch-studio-$STUDIO_VERSION.cdx.json"

cp "$ROOT/distribution/server/compose.yml" "$ROOT/distribution/server/README.md" \
  "$ROOT/distribution/server/.env.example" "$BUNDLE/"
cp "$ROOT/LICENSE" "$ROOT/NOTICE" "$ROOT/THIRD_PARTY_NOTICES.md" "$BUNDLE/"
sed -i "s/@STUDIO_VERSION@/$STUDIO_VERSION/g" "$BUNDLE/.env.example"

"$JAVA_HOME/bin/jar" --create \
  --file "$RELEASE_ROOT/$BUNDLE_NAME.zip" \
  -C "$ROOT/target" "$BUNDLE_NAME"

printf 'Created server release files in %s\n' "$RELEASE_ROOT"
