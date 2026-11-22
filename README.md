# KAR7 PRO

Android client application built with Kotlin and Jetpack Compose for Base44 license authentication, Keystore-encrypted session storage, SHA-256 verified downloads, Storage Access Framework (SAF) export, PackageManager target app launching, and Android Wireless Debugging pairing guidance.

## Project Details

- **Project Name:** KAR7 PRO
- **Package Name (`applicationId`):** `com.kar7.pro`
- **Minimum Android Version:** Android 7.0 (`minSdk = 24`)
- **Target Android Version:** Android 16 (`targetSdk = 36`, `compileSdk = 36`)
- **Supported Architectures:** Pure Kotlin/Android bytecode (`armeabi-v7a` and `arm64-v8a` compatible; no 64-bit-only native binaries)
- **JDK & Gradle:** JDK 21 (Temurin) + Gradle 9.3.1 + Android Gradle Plugin 9.1.1

## Build Instructions (Local)

1. Ensure **JDK 21** and the **Android SDK** (`ANDROID_HOME` / `ANDROID_SDK_ROOT` or an untracked `local.properties`) are installed.
2. Make the Gradle Wrapper executable (macOS/Linux):
   ```bash
   chmod +x ./gradlew
   ```
3. Run unit tests and build the Debug APK:
   ```bash
   ./gradlew clean testDebugUnitTest assembleDebug
   ```
   On Windows:
   ```bat
   gradlew.bat clean testDebugUnitTest assembleDebug
   ```
4. Output Debug APK path:
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

> **Note:** `app/build.gradle.kts` expects a local `debug.keystore` in the repository root for the `debugConfig` signing configuration. Never commit keystores to Git. For local builds without an existing `debug.keystore`, generate one with:
> ```bash
> keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
> ```

## Automatic APK Builds with GitHub Actions

This repository includes `.github/workflows/android-build.yml`, which runs automatically on every push to `main` and manually via **workflow_dispatch**:

1. Sets up **JDK 21** (Temurin) and the **Android SDK** (`platform-tools`, `platforms;android-36.1`, `platforms;android-36`, `build-tools;36.0.0`, accepting SDK licenses automatically).
2. Ensures the Gradle 9.3.1 wrapper is present/executable (`chmod +x gradlew` and `./gradlew --version`).
3. Runs `./gradlew clean testDebugUnitTest assembleDebug` (failing the workflow if unit tests fail).
4. Verifies and uploads `app/build/outputs/apk/debug/app-debug.apk` as artifact **`KAR7-PRO-DEBUG-APK`** with **30-day retention**.

### How to Download the APK from GitHub

1. Open your GitHub repository in a browser.
2. Click the **Actions** tab.
3. Select the latest successful **KAR7 PRO Android Build** workflow run.
4. Scroll down to **Artifacts** and click **`KAR7-PRO-DEBUG-APK`** to download the ZIP containing `app-debug.apk`.

## Optional Signed Release APK (GitHub Secrets)

The workflow supports optional Release APK signing without committing any keystore or password to the repository.

Configure all four repository secrets under **GitHub Repository → Settings → Secrets and variables → Actions**:

| Secret Name | Description |
| :--- | :--- |
| `KEYSTORE_BASE64` | Base64-encoded `.jks` / `.keystore` upload key file (`base64 -w 0 release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore store password |
| `KEY_ALIAS` | Key alias inside the keystore (e.g., `upload`) |
| `KEY_PASSWORD` | Key password for `KEY_ALIAS` |

- If all four secrets are present, GitHub Actions builds and uploads **`KAR7-PRO-RELEASE-APK`** (`app/build/outputs/apk/release/app-release.apk`).
- If any signing secret is missing, the workflow skips release signing, reports that the Release APK has not been signed, and still succeeds with **`KAR7-PRO-DEBUG-APK`**.

## Required Configuration Notes

- **Backend Gateway:** Configured for HTTPS requests to `https://kar-vault-pro.base44.app/api/v1` (`usesCleartextTraffic="false"`).
- **Target App Visibility & SAF Export:** Package visibility uses standard `ACTION_MAIN` / `CATEGORY_LAUNCHER` `<queries>` intent filtering in `AndroidManifest.xml` so `getLaunchIntentForPackage()` and launcher queries resolve the admin-configured package on Android 11+. Verified downloads are exported only to a user-selected Android Storage Access Framework (SAF) folder.
- **Notifications & ADB Pairing:** On Android 13+, `POST_NOTIFICATIONS` is requested at runtime when initiating the persistent KAR7 PRO inline-reply pairing notification. On Android 11–12, no Android 13 runtime notification prompt is shown.
- **Secrets Safety:** Keep `.env`, `*.jks`, `*.keystore`, `*.keystore.base64`, and `local.properties` untracked (already excluded in `.gitignore`). Never commit API keys, session tokens, or signing credentials.
