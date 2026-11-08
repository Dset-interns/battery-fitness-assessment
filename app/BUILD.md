# Building the Android app

The shipping Android build is the module `webapp`: the same web app that runs in the iPhone and Android browsers
(`ios/iphone-pwa/`), shown in a WebView from the APK's own assets. The pose-AI runtime and models are inside the APK, so the
whole battery works with no network. The earlier native Compose app in `app/app/` is kept for reference only: it predates the
revised handbook and does not compile at the moment, so it is not part of the default build.

## Needs

- JDK 17
- Android SDK with platform 37 (`compileSdk 37`) and build-tools; `ANDROID_HOME` set
- Python 3 (for the one download below)
- Internet the first time, for Gradle and the AndroidX/AGP artifacts and the pose-AI files

## Debug APK

```bash
python3 ios/iphone-pwa/fetch_vendor.py          # pose runtime and models, about 34 MB, not committed
cd app
./gradlew :webapp:assembleDebug
# app/webapp/build/outputs/apk/debug/DSeTConsultancy_BatteryFitness_v0.2.0-debug.apk
```

## Signed release APK

Make a keystore once and keep it safe; every update of the app must be signed with the same one.

```bash
keytool -genkeypair -keystore ~/bfa-release.jks -alias bfa -keyalg RSA -keysize 2048 -validity 10000
cat > app/keystore.properties <<'PROPS'
storeFile=/Users/you/bfa-release.jks
storePassword=...
keyAlias=bfa
keyPassword=...
PROPS
cd app && ./gradlew :webapp:assembleRelease
# app/webapp/build/outputs/apk/release/DSeTConsultancy_BatteryFitness_v0.2.0-release.apk
```

`keystore.properties` and `*.jks` are ignored by git. Without `keystore.properties` the release build is produced unsigned.

Check it:

```bash
$ANDROID_HOME/build-tools/<version>/apksigner verify --verbose app/webapp/build/outputs/apk/release/*.apk
$ANDROID_HOME/build-tools/<version>/aapt2 dump badging app/webapp/build/outputs/apk/release/*.apk | head
```

## Reproducing it (ruling C57)

The release build is deterministic. Two clean builds with every cache turned off gave byte-identical APKs:

```bash
./gradlew clean :webapp:assembleRelease --no-build-cache --no-configuration-cache --rerun-tasks
shasum -a 256 app/webapp/build/outputs/apk/release/*.apk
```

An evaluator who builds from the same commit with the same pose-AI files gets the same code and assets. The signature depends on
the key, so with a different keystore the signing block differs while every other part is identical; compare `unzip -v` of the two
APKs (CRC and name per entry). What makes it deterministic: pinned plugin versions in `build.gradle.kts`, the Gradle wrapper, and
`dependenciesInfo { includeInApk = false }`, because AGP otherwise embeds randomly encrypted dependency metadata.

## What the shell does

- Serves the page from `https://appassets.androidplatform.net/` out of `assets/www`, so the camera and IndexedDB work.
- Asks for the camera permission when the page asks for the camera. Only `CAMERA` and `INTERNET` are requested.
- Gives the page `window.AndroidBridge.print()` (system print screen, "Save as PDF" there) and `saveFile()` (to Downloads),
  because a WebView can do neither by itself.
- Keeps the screen on, keeps rotating from reloading the page, and walks the page's own history on the back button.

## Not verified

This was built and signed here but **not run on a phone or an emulator**: none is available on the build machine. Before relying
on it, install the APK on a real Android 10+ phone and run one attempt of each kind (a pose test, a timed test, the ball test), save
a PDF, and take the weight photo. The page itself has been tested in a desktop browser; the WebView shell code has only been compiled.

## iPhone

The iPhone build is the web app added to the Home Screen from Safari. A native iOS project could not be built here: Xcode is not
installed on the build machine (only the command-line tools). The Swift package in `ios/BatteryFitnessIOS/` predates the revised
handbook and is not part of the app.
