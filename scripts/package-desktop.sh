#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
MODULE="$ROOT/desktop/pdf-batch-desktop"
APP_NAME="PDF Batch Desktop"
APP_VERSION="0.1.0"
MAIN_CLASS="de.appfabrik.pdfbatch.desktop.DesktopLauncher"
MAIN_JAR="pdf-batch-desktop-0.1.0-SNAPSHOT.jar"
PACKAGE_TYPE=${1:-app-image}

if [[ -z "${JAVA_HOME:-}" && -x "$HOME/.local/jdk-21/bin/java" ]]; then
  export JAVA_HOME="$HOME/.local/jdk-21"
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/jpackage" ]]; then
  printf 'JAVA_HOME must point to a JDK 21 installation containing jpackage.\n' >&2
  exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

case "$PACKAGE_TYPE" in
  app-image|dmg) ;;
  *)
    printf 'Supported package types on this script are app-image and dmg.\n' >&2
    exit 1
    ;;
esac
if [[ "$PACKAGE_TYPE" == "dmg" && "$(uname -s)" != "Darwin" ]]; then
  printf 'A DMG must be built on macOS.\n' >&2
  exit 1
fi

"$ROOT/mvnw" --batch-mode --no-transfer-progress \
  -pl desktop/pdf-batch-desktop -am install -DskipTests

INPUT="$MODULE/target/package-input"
IMAGE_DEST="$MODULE/target/desktop-image"
DIST="$MODULE/target/desktop-dist"
rm -rf "$INPUT" "$IMAGE_DEST" "$DIST"
mkdir -p "$INPUT" "$IMAGE_DEST" "$DIST"

"$ROOT/mvnw" --batch-mode --no-transfer-progress \
  -pl desktop/pdf-batch-desktop dependency:copy-dependencies \
  -DincludeScope=runtime -DoutputDirectory="$INPUT"
cp "$MODULE/target/$MAIN_JAR" "$INPUT/$MAIN_JAR"

common=(
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --vendor "AppFabrik"
  --description "Offline PDF batch generation from AcroForm templates and CSV data"
  --input "$INPUT"
  --main-jar "$MAIN_JAR"
  --main-class "$MAIN_CLASS"
  --java-options "-Dfile.encoding=UTF-8"
)

"$JAVA_HOME/bin/jpackage" "${common[@]}" --type app-image --dest "$IMAGE_DEST"
if [[ "$(uname -s)" == "Darwin" ]]; then
  launcher="$IMAGE_DEST/$APP_NAME.app/Contents/MacOS/$APP_NAME"
else
  launcher="$IMAGE_DEST/$APP_NAME/bin/$APP_NAME"
fi
version_output=$("$launcher" --version)
if [[ "$version_output" != "$APP_NAME $APP_VERSION" ]]; then
  printf 'Packaged launcher returned unexpected version: %s\n' "$version_output" >&2
  exit 1
fi

if [[ "$PACKAGE_TYPE" == "dmg" ]]; then
  "$JAVA_HOME/bin/jpackage" "${common[@]}" \
    --type dmg \
    --dest "$DIST" \
    --license-file "$ROOT/LICENSE" \
    --mac-package-identifier "de.appfabrik.pdfbatch.desktop"
  printf 'Created %s\n' "$DIST"
else
  printf 'Created and smoke-tested %s\n' "$IMAGE_DEST/$APP_NAME"
fi
