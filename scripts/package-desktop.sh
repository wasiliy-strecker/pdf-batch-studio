#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
MODULE="$ROOT/desktop/pdf-batch-desktop"
APP_NAME="PDF Batch Studio Desktop"
MAIN_CLASS="de.appfabrik.pdfbatch.desktop.DesktopLauncher"
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
  app-image|linux|dmg) ;;
  *)
    printf 'Supported package types are app-image, linux, and dmg.\n' >&2
    exit 1
    ;;
esac
if [[ "$PACKAGE_TYPE" == "dmg" && "$(uname -s)" != "Darwin" ]]; then
  printf 'A DMG must be built on macOS.\n' >&2
  exit 1
fi
if [[ "$PACKAGE_TYPE" == "linux" && "$(uname -s)" != "Linux" ]]; then
  printf 'The Linux archive must be built on Linux.\n' >&2
  exit 1
fi

maven_value() {
  "$ROOT/mvnw" --quiet -Dstyle.color=never -DforceStdout \
    help:evaluate -Dexpression="$1" | tail -n 1 | tr -d '\r'
}

MAVEN_VERSION=$(maven_value project.version)
APP_VERSION=$(maven_value studio.version)
PACKAGE_VERSION=$(maven_value native.package.version)
MAIN_JAR="pdf-batch-desktop-$MAVEN_VERSION.jar"

"$ROOT/mvnw" --batch-mode --no-transfer-progress \
  -pl desktop/pdf-batch-desktop -am install -DskipTests

INPUT="$MODULE/target/package-input"
IMAGE_DEST="$MODULE/target/desktop-image"
PACKAGE_DEST="$MODULE/target/package-staging"
DIST="$MODULE/target/desktop-dist"
rm -rf "$INPUT" "$IMAGE_DEST" "$PACKAGE_DEST" "$DIST"
mkdir -p "$INPUT/legal" "$IMAGE_DEST" "$PACKAGE_DEST" "$DIST"

"$ROOT/mvnw" --batch-mode --no-transfer-progress \
  -pl desktop/pdf-batch-desktop dependency:copy-dependencies \
  -DincludeScope=runtime -DoutputDirectory="$INPUT"
cp "$MODULE/target/$MAIN_JAR" "$INPUT/$MAIN_JAR"
cp "$ROOT/LICENSE" "$ROOT/NOTICE" "$ROOT/THIRD_PARTY_NOTICES.md" "$INPUT/legal/"

common=(
  --name "$APP_NAME"
  --app-version "$PACKAGE_VERSION"
  --vendor "Wasiliy Strecker"
  --description "Offline PDF automation with projects, CSV and Excel"
  --copyright "Copyright 2026 Wasiliy Strecker"
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

architecture=$(uname -m)
case "$architecture" in
  x86_64|amd64) architecture=x64 ;;
  arm64|aarch64) architecture=arm64 ;;
esac

if [[ "$PACKAGE_TYPE" == "dmg" ]]; then
  "$JAVA_HOME/bin/jpackage" "${common[@]}" \
    --type dmg \
    --dest "$PACKAGE_DEST" \
    --license-file "$ROOT/LICENSE" \
    --mac-package-identifier "de.appfabrik.pdfbatch.studio.desktop"
  generated=$(find "$PACKAGE_DEST" -maxdepth 1 -type f -name '*.dmg' -print -quit)
  test -n "$generated"
  target="$DIST/pdf-batch-studio-desktop-$APP_VERSION-macos-$architecture.dmg"
  mv "$generated" "$target"
  printf 'Created %s\n' "$target"
elif [[ "$PACKAGE_TYPE" == "linux" ]]; then
  target="$DIST/pdf-batch-studio-desktop-$APP_VERSION-linux-$architecture.tar.gz"
  tar -C "$IMAGE_DEST" -czf "$target" "$APP_NAME"
  printf 'Created %s\n' "$target"
  if command -v fakeroot >/dev/null 2>&1; then
    "$JAVA_HOME/bin/jpackage" --type deb --name pdf-batch-studio \
      --app-image "$IMAGE_DEST/$APP_NAME" --app-version "$PACKAGE_VERSION" \
      --vendor "Wasiliy Strecker" --dest "$PACKAGE_DEST" \
      --linux-package-name pdf-batch-studio --linux-menu-group Office \
      --linux-shortcut --linux-package-deps "libgtk-3-0 | libgtk-3-0t64, libgl1, fontconfig" \
      --license-file "$ROOT/LICENSE"
    generated=$(find "$PACKAGE_DEST" -maxdepth 1 -type f -name '*.deb' -print -quit)
    test -n "$generated"
    mv "$generated" "$DIST/pdf-batch-studio-desktop-$APP_VERSION-linux-$architecture.deb"
  fi
else
  printf 'Created and smoke-tested %s\n' "$IMAGE_DEST/$APP_NAME"
fi
