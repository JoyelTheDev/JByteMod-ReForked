#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APP_NAME="JByteMod-ReForked"
MAIN_CLASS="de.xbrowniecodez.jbytemod.Main"
DESCRIPTION="Java bytecode editor and analyzer"
VENDOR="${VENDOR:-JByteMod ReForked contributors}"
COPYRIGHT="${COPYRIGHT:-Copyright (C) JByteMod ReForked contributors}"
BUNDLE_ID="${BUNDLE_ID:-io.github.jbytemod.reforked}"
MAINTAINER_EMAIL="${MAINTAINER_EMAIL:-jbytemod-reforked@users.noreply.github.com}"
WIN_UPGRADE_UUID="7c1e5b52-3f0a-4d63-9a58-1b8f0e6d2a47"
DRY_RUN="${DRY_RUN:-0}"

VERSION="${VERSION:-$(awk -F'[<>]' '/<version>/ {print $3; exit}' pom.xml)}"
APP_VERSION="${VERSION%%-*}"

detect_os() {
  if [ -n "${TARGET_OS:-}" ]; then
    echo "$TARGET_OS"
    return
  fi
  case "$(uname -s)" in
    Linux*) echo linux ;;
    Darwin*) echo macos ;;
    MINGW* | MSYS* | CYGWIN*) echo windows ;;
    *)
      echo "Unsupported operating system: $(uname -s)" >&2
      exit 1
      ;;
  esac
}

detect_arch() {
  case "$(uname -m)" in
    x86_64 | amd64) echo x64 ;;
    aarch64 | arm64) echo arm64 ;;
    *) uname -m ;;
  esac
}

OS="$(detect_os)"
ARCH="${TARGET_ARCH:-$(detect_arch)}"

if [ "$OS" = "windows" ]; then
  export MSYS_NO_PATHCONV=1
  export MSYS2_ARG_CONV_EXCL="*"
fi

WORK="target/installer-work"
INPUT="$WORK/input"
IMAGE="$WORK/image"
RAW="$WORK/raw"
OUT="target/installers"

run() {
  echo "+ $*"
  if [ "$DRY_RUN" != "1" ]; then
    "$@"
  fi
}

find_jar() {
  if [ -n "${JAR_PATH:-}" ]; then
    echo "$JAR_PATH"
    return
  fi
  local expected="target/${APP_NAME}-${VERSION}.jar"
  if [ -f "$expected" ]; then
    echo "$expected"
    return
  fi
  local candidate
  for candidate in target/*.jar; do
    case "$candidate" in
      *original-* | *sources* | *javadoc*) continue ;;
    esac
    if [ -f "$candidate" ]; then
      echo "$candidate"
      return
    fi
  done
  echo "No application jar found in target/. Run 'mvn clean package' first or set JAR_PATH." >&2
  exit 1
}

detect_modules() {
  local extra="java.instrument,java.net.http,java.compiler,jdk.attach,jdk.compiler,jdk.jdi,jdk.jdwp.agent,jdk.management,jdk.management.agent,jdk.unsupported,jdk.zipfs,jdk.crypto.ec,jdk.localedata,jdk.charsets"
  local fallback="java.base,java.desktop,java.logging,java.xml,java.prefs,java.management,java.naming,java.sql,java.scripting,java.rmi,java.security.jgss,jdk.net,jdk.xml.dom,$extra"
  local detected=""
  if [ "$DRY_RUN" != "1" ] && command -v jdeps > /dev/null 2>&1; then
    detected="$(jdeps --multi-release 25 --ignore-missing-deps --print-module-deps "$INPUT/$JAR_NAME" 2> /dev/null || true)"
  fi
  if [ -n "$detected" ]; then
    echo "$detected,$extra"
  else
    echo "$fallback"
  fi
}

pick_output() {
  local pattern="$1"
  local match
  match="$(find "$RAW" -maxdepth 1 -type f -name "$pattern" | head -n 1)"
  if [ -z "$match" ]; then
    echo "Expected installer matching '$pattern' was not produced in $RAW" >&2
    exit 1
  fi
  echo "$match"
}

if [ "$DRY_RUN" != "1" ] && ! command -v jpackage > /dev/null 2>&1; then
  echo "jpackage was not found. Install a JDK 25 and make sure it is on PATH." >&2
  exit 1
fi

JAR_SOURCE="$(find_jar)"
JAR_NAME="$(basename "$JAR_SOURCE")"
BASENAME="${APP_NAME}-${VERSION}"

echo "Packaging $APP_NAME $VERSION for $OS-$ARCH from $JAR_SOURCE"

rm -rf "$WORK" "$OUT"
mkdir -p "$INPUT" "$IMAGE" "$RAW" "$OUT"
if [ "$DRY_RUN" = "1" ] && [ ! -f "$JAR_SOURCE" ]; then
  : > "$INPUT/$JAR_NAME"
else
  cp "$JAR_SOURCE" "$INPUT/$JAR_NAME"
fi

MODULES="$(detect_modules)"

case "$OS" in
  windows) ICON="packaging/icons/icon.ico" ;;
  macos) ICON="packaging/icons/icon.icns" ;;
  *) ICON="packaging/icons/icon.png" ;;
esac

IMAGE_OPTS=(
  --type app-image
  --dest "$IMAGE"
  --input "$INPUT"
  --main-jar "$JAR_NAME"
  --main-class "$MAIN_CLASS"
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --vendor "$VENDOR"
  --description "$DESCRIPTION"
  --copyright "$COPYRIGHT"
  --icon "$ICON"
  --add-modules "$MODULES"
  --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED"
  --java-options "--add-opens=java.desktop/javax.swing=ALL-UNNAMED"
  --java-options "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED"
  --java-options "--enable-native-access=ALL-UNNAMED"
  --java-options "-XX:MaxRAMPercentage=60"
  --java-options "-Dfile.encoding=UTF-8"
)

if [ "$OS" = "macos" ]; then
  IMAGE_OPTS+=(--mac-package-name "JByteMod" --mac-package-identifier "$BUNDLE_ID")
fi

run jpackage "${IMAGE_OPTS[@]}"

case "$OS" in
  windows)
    run jpackage --type msi \
      --app-image "$IMAGE/$APP_NAME" \
      --dest "$RAW" \
      --name "$APP_NAME" \
      --app-version "$APP_VERSION" \
      --vendor "$VENDOR" \
      --description "$DESCRIPTION" \
      --copyright "$COPYRIGHT" \
      --icon "$ICON" \
      --license-file LICENSE \
      --win-menu \
      --win-menu-group "JByteMod ReForked" \
      --win-shortcut \
      --win-dir-chooser \
      --win-per-user-install \
      --win-upgrade-uuid "$WIN_UPGRADE_UUID"
    run jar --create --no-manifest --file "$OUT/${BASENAME}-windows-${ARCH}-portable.zip" -C "$IMAGE" "$APP_NAME"
    if [ "$DRY_RUN" != "1" ]; then
      mv "$(pick_output '*.msi')" "$OUT/${BASENAME}-windows-${ARCH}.msi"
    fi
    ;;
  linux)
    run jpackage --type deb \
      --app-image "$IMAGE/$APP_NAME" \
      --dest "$RAW" \
      --name "$APP_NAME" \
      --app-version "$APP_VERSION" \
      --vendor "$VENDOR" \
      --description "$DESCRIPTION" \
      --copyright "$COPYRIGHT" \
      --icon "$ICON" \
      --license-file LICENSE \
      --linux-package-name "jbytemod-reforked" \
      --linux-deb-maintainer "$MAINTAINER_EMAIL" \
      --linux-menu-group "Development" \
      --linux-app-category "devel" \
      --linux-shortcut
    run tar -C "$IMAGE" -czf "$OUT/${BASENAME}-linux-${ARCH}-portable.tar.gz" "$APP_NAME"
    if [ "$DRY_RUN" != "1" ]; then
      mv "$(pick_output '*.deb')" "$OUT/${BASENAME}-linux-${ARCH}.deb"
    fi
    ;;
  macos)
    run jpackage --type dmg \
      --app-image "$IMAGE/$APP_NAME.app" \
      --dest "$RAW" \
      --name "$APP_NAME" \
      --app-version "$APP_VERSION" \
      --icon "$ICON" \
      --mac-package-name "JByteMod" \
      --mac-package-identifier "$BUNDLE_ID"
    if [ "$DRY_RUN" != "1" ]; then
      mv "$(pick_output '*.dmg')" "$OUT/${BASENAME}-macos-${ARCH}.dmg"
    fi
    ;;
esac

if [ "$DRY_RUN" != "1" ]; then
  rm -rf "$RAW"
  echo "Created:"
  ls -lh "$OUT"
fi
