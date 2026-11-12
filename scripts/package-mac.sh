#!/bin/bash
# Builds the EMCC Admin desktop app as a native macOS .dmg installer, with the
# Spring Boot backend bundled inside and launched automatically as a child
# process (see BackendLauncher). Run this on a Mac — jpackage does not
# cross-compile, so a Windows installer needs scripts/package-windows.ps1
# run on Windows instead.
#
# Assumes emcc-admin-backend is checked out as a sibling directory of
# emcc-admin-app (the layout this repo already uses).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$(cd "$APP_DIR/../emcc-admin-backend" && pwd)"

APP_NAME="EMCC Admin"
APP_VERSION="1.0.0"
VENDOR="Eritrean Muslims Council of Columbus"
MAIN_CLASS="com.emcc.admin.app.MainApp"

cd "$APP_DIR"

echo "== [1/5] Building backend fat jar =="
(cd "$BACKEND_DIR" && mvn clean package -Dmaven.test.skip=true)
cp "$BACKEND_DIR/target/emcc-admin-backend.jar" "$APP_DIR/src/main/resources/backend/emcc-admin-backend.jar"

echo "== [2/5] Building a standalone Java runtime to launch the backend with =="
# jpackage deliberately strips bin/java from the runtime it bundles for the app
# itself (it's meant to be launched only via its own native stub), so the app
# can't use its own runtime to spawn the backend as a second process. jlink,
# unlike jpackage, always keeps bin/java in what it builds, so we ship this
# separate runtime purely for that purpose. Rebuilt fresh each run to track
# whatever JDK is on PATH.
rm -rf target/backend-jre target/jre.zip
jlink \
  --add-modules ALL-MODULE-PATH \
  --output target/backend-jre \
  --no-header-files \
  --no-man-pages \
  --strip-debug \
  --compress=2
(cd target/backend-jre && zip -qr ../jre.zip .)
cp target/jre.zip "$APP_DIR/src/main/resources/backend/jre.zip"

echo "== [3/5] Building app jar =="
mvn clean package -Dmaven.test.skip=true

echo "== [4/5] Collecting runtime dependencies (incl. platform-specific JavaFX jars) =="
rm -rf target/libs
mkdir -p target/libs
mvn dependency:copy-dependencies -DoutputDirectory=target/libs
cp "target/emcc-admin-app.jar" target/libs/

echo "== [5/5] Running jpackage =="
rm -rf dist
mkdir -p dist
# The app's JavaFX jars sit on the classpath (this isn't a modular build), so the
# generated launcher needs to be told explicitly to add the JavaFX modules —
# otherwise it fails at startup with "JavaFX runtime components are missing".
# $APPDIR is a jpackage macro resolved at launch to the app's own jar directory.
JAVAFX_MODULES="javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.swing,javafx.web"
jpackage \
  --type dmg \
  --input target/libs \
  --main-jar emcc-admin-app.jar \
  --main-class "$MAIN_CLASS" \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "$VENDOR" \
  --icon scripts/icons/emcc-icon.icns \
  --java-options "--module-path \$APPDIR --add-modules $JAVAFX_MODULES" \
  --dest dist

DMG_PATH="dist/${APP_NAME}-${APP_VERSION}.dmg"

echo "== Setting the .dmg file's own Finder icon =="
# --icon above only sets the icon of the app *inside* the dmg (and the volume
# icon shown once it's mounted) — the .dmg file itself still shows the generic
# disk-image icon in Finder unless we set that separately via the classic
# resource-fork trick (Rez/SetFile, still shipped with Xcode Command Line Tools).
ICON_TMP="target/dmgicon.icns"
cp scripts/icons/emcc-icon.icns "$ICON_TMP"
sips -i "$ICON_TMP" > /dev/null
DeRez -only icns "$ICON_TMP" > target/dmgicon.rsrc
Rez -append target/dmgicon.rsrc -o "$DMG_PATH"
SetFile -a C "$DMG_PATH"

echo
echo "Done. Installer at: $APP_DIR/$DMG_PATH"
