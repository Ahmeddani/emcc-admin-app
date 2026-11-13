# Builds the EMCC Admin desktop app as a native Windows installer (.msi), with
# the Spring Boot backend bundled inside and launched automatically as a child
# process (see BackendLauncher.java). Run this on Windows — jpackage does not
# cross-compile, so a macOS installer needs scripts/package-mac.sh run on a Mac
# instead.
#
# Prerequisites:
#   - JDK 17+ on PATH (need jlink and jpackage; both ship with the JDK)
#   - Maven on PATH
#   - WiX Toolset v3 installed — jpackage's --type msi/exe needs it on Windows
#     (https://wixtoolset.org/releases/, install the v3.x MSI, not v4+)
#
# Assumes emcc-admin-backend is checked out as a sibling directory of
# emcc-admin-app (the layout this repo already uses).

$ErrorActionPreference = "Stop"

$AppDir = Resolve-Path (Join-Path $PSScriptRoot "..")
$BackendDir = Resolve-Path (Join-Path $AppDir "..\emcc-admin-backend")

$AppName = "EMCC Admin"
$AppVersion = "1.0.0"
$Vendor = "Eritrean Muslims Council of Columbus"
$MainClass = "com.emcc.admin.app.MainApp"

Set-Location $AppDir

Write-Host "== [1/5] Building backend fat jar =="
Push-Location $BackendDir
mvn clean package -Dmaven.test.skip=true
if ($LASTEXITCODE -ne 0) { throw "Backend build failed" }
Pop-Location
Copy-Item (Join-Path $BackendDir "target\emcc-admin-backend.jar") `
    "src\main\resources\backend\emcc-admin-backend.jar" -Force

Write-Host "== [2/5] Building a standalone Java runtime to launch the backend with =="
# jpackage deliberately strips java.exe from the runtime it bundles for the app
# itself (it's meant to be launched only via its own native stub), so the app
# can't use its own runtime to spawn the backend as a second process. jlink,
# unlike jpackage, always keeps java.exe in what it builds, so we ship this
# separate runtime purely for that purpose. Rebuilt fresh each run to track
# whatever JDK is on PATH.
Remove-Item -Recurse -Force "target\backend-jre" -ErrorAction SilentlyContinue
Remove-Item -Force "target\jre.zip" -ErrorAction SilentlyContinue
jlink `
    --add-modules ALL-MODULE-PATH `
    --output target\backend-jre `
    --no-header-files `
    --no-man-pages `
    --strip-debug `
    --compress=2
if ($LASTEXITCODE -ne 0) { throw "jlink failed" }
Compress-Archive -Path "target\backend-jre\*" -DestinationPath "target\jre.zip" -Force
Copy-Item "target\jre.zip" "src\main\resources\backend\jre.zip" -Force

Write-Host "== [3/5] Building app jar =="
mvn clean package -Dmaven.test.skip=true
if ($LASTEXITCODE -ne 0) { throw "App build failed" }

Write-Host "== [4/5] Collecting runtime dependencies (incl. platform-specific JavaFX jars) =="
Remove-Item -Recurse -Force "target\libs" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path "target\libs" | Out-Null
mvn dependency:copy-dependencies "-DoutputDirectory=target\libs"
if ($LASTEXITCODE -ne 0) { throw "Dependency collection failed" }
Copy-Item "target\emcc-admin-app.jar" "target\libs\" -Force

Write-Host "== [5/5] Running jpackage =="
Remove-Item -Recurse -Force "dist" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path "dist" | Out-Null
# The app's JavaFX jars sit on the classpath (this isn't a modular build), so the
# generated launcher needs to be told explicitly to add the JavaFX modules —
# otherwise it fails at startup with "JavaFX runtime components are missing".
# $APPDIR is a jpackage macro resolved at launch to the app's own jar directory;
# it must reach jpackage literally, so this uses a single-quoted PowerShell
# string (no $-interpolation) rather than double quotes.
$JavaFxModules = "javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.swing,javafx.web"
jpackage `
    --type msi `
    --input target\libs `
    --main-jar emcc-admin-app.jar `
    --main-class $MainClass `
    --name $AppName `
    --app-version $AppVersion `
    --vendor $Vendor `
    --icon scripts\icons\emcc-icon.ico `
    --java-options ('--module-path $APPDIR --add-modules ' + $JavaFxModules) `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut `
    --dest dist
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

Write-Host ""
Write-Host "Done. Installer at: $AppDir\dist\$AppName-$AppVersion.msi"
