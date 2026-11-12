---
last_mapped_commit: 0938eec730b766f55e975c6539ed7b22a60e3333
last_mapped_at: 2026-11-12
---
# Technology Stack

**Analysis Date:** 2026-11-12

## Languages

**Primary:**
- Java 17 (source/target compatibility) - Android host app: `app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java`, `app/src/main/java/com/davidop/estudiaenelbus/NativeGguf.java`, `app/src/main/java/com/davidop/estudiaenelbus/NativeLearningEngine.java`
- HTML/CSS/JavaScript (single-file app, inline `<script>`) - UI shared by Android WebView and web: `web/index.html`, mirrored byte-for-byte to `app/src/main/assets/index.html` (enforced by `tools/verify_source.py`)
- JavaScript (ES modules) - Browser-side AI prototype: `web/local-ai.js`
- C++17 - JNI bridge to llama.cpp: `app/src/main/cpp/local_llm.cpp`

**Secondary:**
- Groovy (Gradle DSL) - Build scripts: `build.gradle`, `settings.gradle`, `app/build.gradle`
- Python 3 - Static source checks run in CI: `tools/verify_source.py` (writes a temporary JS file and runs `node --check` on it)
- Markdown - Project docs: `README.md`, `docs/estado.md`, `brand/IDENTIDAD_VISUAL.md`
- SVG - Favicon: `web/favicon.svg`; Android launcher icon as vector: `app/src/main/res/drawable/ic_launcher.xml`
- XML - Android manifest and resources: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/styles.xml`
- YAML - GitHub Actions workflows: `.github/workflows/android-debug.yml`, `.github/workflows/pages.yml`

## Runtime

**Android (target device class):**
- Android application, `minSdk 26`, `targetSdk 36`, `compileSdk 36` (`app/build.gradle`)
- ABI restricted to `arm64-v8a` (`ndk { abiFilters 'arm64-v8a' }`); the native library is ARM64 only
- Application ID: `com.davidop.estudiaenelbus.dev` (debug build adds suffix `.preview`); namespace `com.davidop.estudiaenelbus`
- Current version: `versionCode 7`, `versionName '0.5.0-study-preview'`
- Release build type: `minifyEnabled false` (no R8/ProGuard shrinking configured)
- Target device used for physical validation (per `README.md`): Xiaomi 12T

**Web:**
- Static site, no server runtime. Deployed as-is from `web/` to GitHub Pages (`.github/workflows/pages.yml`)
- Browser requirements implied by code: `WebAssembly`, `navigator.storage.getDirectory` (OPFS), `localStorage`, optional WebGPU (`navigator.gpu`) and cross-origin isolation for multithreading (`web/local-ai.js`)

**Package Manager:**
- Gradle (Android Gradle Plugin driven). Version pinned in CI to `8.13` (`gradle/actions/setup-gradle@v4` in `.github/workflows/android-debug.yml`)
- Gradle wrapper: Not detected (no `gradlew`, `gradle/wrapper/` committed). Local builds require a locally installed Gradle compatible with AGP 8.13.2
- Lockfile: Not detected (no `gradle.lockfile`, no `package-lock.json`; the web app has no npm manifest)
- Android Gradle Plugin: `com.android.application` version `8.13.2` declared in root `build.gradle`
- Repositories: `google()` and `mavenCentral()` (plus `gradlePluginPortal()` for plugins), configured in `settings.gradle` with `FAIL_ON_PROJECT_REPOS`

**Node:**
- Not used as a project runtime. Node is only used in CI (`node --check`) via `tools/verify_source.py`

## Frameworks

**Core (Android):**
- Android SDK framework (`android.app.Activity`, `android.webkit.WebView`, `android.speech.tts.TextToSpeech`, `android.content.ClipboardManager`) - Hosts the WebView UI, TTS, clipboard, file pickers. Entry: `app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java`
- AndroidX enabled (`android.useAndroidX=true` in `gradle.properties`); no AndroidX libraries are declared as dependencies

**Core (Web/UI):**
- No UI framework. Hand-written vanilla JavaScript rendering in `web/index.html` (inline script starting near line 268)

**Native AI:**
- llama.cpp v0.9.0 (`ggml-org/llama.cpp`), built statically via CMake as a submodule-style `add_subdirectory` - Pinned by CI; see `app/src/main/cpp/CMakeLists.txt`
- ggml (bundled inside llama.cpp) - CPU backend only in this build (`GGML_NATIVE OFF`, `GGML_OPENMP OFF`, `GGML_LLAMAFILE OFF`, `GGML_CPU_KLEIDIAI OFF`, `GGML_BACKEND_DL OFF`, `GGML_CPU_ALL_VARIANTS OFF`)

**Browser AI (prototype):**
- wllama `3.8.1` (llama.cpp compiled to WebAssembly, WebGPU when available) - loaded from CDN in `web/local-ai.js`
- Tesseract.js `7.0.0` (OCR, Spanish language `spa`) - loaded from CDN in `web/local-ai.js`

**Testing:**
- No unit or instrumented test framework configured. No `src/test` or `src/androidTest` directories exist.
- Static checks only: `tools/verify_source.py` (asserts web/Android HTML parity, key strings, and that the inline JS passes `node --check`)

**Build/Dev:**
- Android Gradle Plugin `8.13.2` - Android build
- CMake `3.22.1` (`cmake { version '3.22.1' }` in `app/build.gradle`; `cmake_minimum_required(VERSION 3.22.1)` in `app/src/main/cpp/CMakeLists.txt`) - Native build
- Android NDK `27.2.12479018` (installed in CI via `sdkmanager`; not referenced by version in Gradle files)
- Android build-tools `35.0.0`, platform `android-36` (installed in CI via `android-actions/setup-android@v3`)
- Temurin JDK 17 (CI via `actions/setup-java@v4`)

## Key Dependencies

**Critical (Android, declared in `app/build.gradle`):**
- `com.google.mlkit:text-recognition:16.0.1` - On-device OCR. Used by `NativeLearningEngine.java` with `TextRecognizerOptions.DEFAULT_OPTIONS` (Latin script). Per `README.md`, the Latin model is bundled, so no network download is expected for OCR

**Critical (native, fetched at CI build time, not in repo):**
- `llama.cpp` tag `v0.9.0` - Cloned with `git clone --depth 1 --branch v0.9.0 https://github.com/ggml-org/llama.cpp.git vendor/llama.cpp` in `.github/workflows/android-debug.yml`. The directory `vendor/` is not committed (no `.gitignore` present in repo root). Local builds must reproduce the clone (see `README.md`)
- Native libraries produced: `repasumi_native` (shared lib, built from `app/src/main/cpp/local_llm.cpp`), linked against `llama`, `log`, `android`
- llama.cpp APIs used in `local_llm.cpp`: `llama_backend_init`, `llama_model_load_from_file`, `llama_init_from_model`, `llama_tokenize`, `llama_decode`, `llama_sampler_*` (temperature 0.18, distribution seed 12345), `llama_vocab_is_eog`, `llama_memory_clear`

**Model (runtime asset, not bundled in APK):**
- Qwen2.5-0.5B-Instruct, GGUF Q4_K_M, pinned to Hugging Face commit `9217f5db79a29953eb74d5343926648285ec7e67`, about 491 MB (491,400,032 bytes). Verified by SHA-256 in both `app/src/main/java/com/davidop/estudiaenelbus/NativeGguf.java` (`EXPECTED_SHA256`) and `web/local-ai.js` (`MODEL_SHA256`)
- Imported by the user through the Android file picker (Android) or stored in browser cache (web). Stored as `repasumi-model.gguf` in app private files dir (Android)

**Critical (web, loaded at runtime from CDN, versions pinned in URL):**
- `@wllama/wllama@3.8.1` - `https://cdn.jsdelivr.net/npm/@wllama/wllama@3.8.1/esm/index.js` and `.../esm/wasm/wllama.wasm`. Used for `ModelManager`, `CacheManager`, `Wllama`, `LoggerWithoutDebug`
- `tesseract.js@7.0.0` - `https://cdn.jsdelivr.net/npm/tesseract.js@7.0.0/dist/tesseract.esm.min.js`. Used with `createWorker('spa', 1, ...)`

**Infrastructure (Android, transitive via AGP/Android SDK):**
- AndroidX is enabled but no `androidx.*` artifacts are declared directly
- `org.json` (Android platform) - JSON parsing in `NativeGguf.java`, `NativeLearningEngine.java`, `MainActivity.java`

**Build-time GitHub Actions (third-party):**
- `actions/checkout@v4`, `actions/setup-java@v4`, `android-actions/setup-android@v3`, `gradle/actions/setup-gradle@v4`, `actions/upload-artifact@v4`, `actions/configure-pages@v5`, `actions/upload-pages-artifact@v3`, `actions/deploy-pages@v4`

## Configuration

**Environment:**
- No runtime environment variables are read by the app. No `.env` files are tracked in the repository
- Build configuration is in Gradle property file `gradle.properties` (`org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`, `android.useAndroidX=true`)
- Per-variant app identity in `app/build.gradle`: `applicationIdSuffix '.preview'` for debug; provisional application ID flagged with a Spanish comment to review before Play Store publication
- Android runtime permissions: none requested beyond defaults; `app/src/main/AndroidManifest.xml` intentionally omits `android.permission.INTERNET`
- Android manifest cleartext traffic disabled (`android:usesCleartextTraffic="false"`); backups disabled (`android:allowBackup="false"`)
- WebView hardening in `MainActivity.java`: `setAllowFileAccess(false)`, `setAllowContentAccess(false)`, `setMixedContentMode(MIXED_CONTENT_NEVER_ALLOW)`, `setJavaScriptCanOpenWindowsAutomatically(false)`, all WebView requests blocked except the home URL served from `https://appassets.androidplatform.net/assets/index.html`
- Native compile flags: `cppFlags '-std=c++17'` (`app/build.gradle`) and `CMAKE_CXX_STANDARD 17` (`app/src/main/cpp/CMakeLists.txt`)
- Model/runtime tuning constants (not environment-driven): generation temperature 0.18, seed 12345, 550 tokens (questions), 600 tokens (practice), context 4096 and batch 256 in web (`web/local-ai.js` `loadModel`); Android context and batch sizes are set in `local_llm.cpp` via `llama_context_default_params()`

**Build:**
- `build.gradle` (root): plugin declaration `com.android.application` `8.13.2` with `apply false`
- `settings.gradle`: `rootProject.name = 'Repasumi'`, `include ':app'`, repository rules
- `app/build.gradle`: `compileSdk 36`, `minSdk 26`, `targetSdk 36`, `versionCode 7`, `versionName '0.5.0-study-preview'`, `externalNativeBuild.cmake.path 'src/main/cpp/CMakeLists.txt'`, Java 17 compatibility, release `minifyEnabled false`
- `app/src/main/cpp/CMakeLists.txt`: `BUILD_SHARED_LIBS OFF`, `LLAMA_BUILD_EXAMPLES/TESTS/SERVER OFF`, `LLAMA_CURL OFF`, `LLAMA_OPENSSL OFF`, plus the GGML feature toggles listed above; fails configure with message if `vendor/llama.cpp/include/llama.h` is missing
- No `.gitignore` detected in repo root; `vendor/`, `app/build/`, and `.gradle/` are not excluded by a tracked ignore file

## CI/CD Pipelines

- `.github/workflows/android-debug.yml` (`Compilar APK Android (pruebas)`): triggers on push to `main` and manual dispatch. Runs on `ubuntu-latest`. Steps: JDK 17 (Temurin), Android SDK packages, NDK `27.2.12479018` and CMake `3.22.1`, clone llama.cpp `v0.9.0`, Gradle 8.13, `python3 tools/verify_source.py`, `gradle --no-daemon --stacktrace :app:assembleDebug`, uploads artifact `repasumi-beta-debug-apk` (30-day retention), publishes a GitHub prerelease `v0.5.0-preview` with the APK. Permissions: `contents: write`
- `.github/workflows/pages.yml` (`Publicar web en GitHub Pages`): triggers on push to `main` affecting `web/**` or itself, plus manual dispatch. Runs `python3 tools/verify_source.py`, then uploads `web/` as the Pages artifact and deploys. Permissions: `contents: read`, `pages: write`, `id-token: write`

## Platform Requirements

**Development:**
- JDK 17, Android SDK platform 36, build-tools 35.0.0, NDK `27.2.12479018`, CMake `3.22.1`, Gradle 8.13 (or AGP 8.13.2 compatible Gradle), Python 3, Node.js (for local run of `verify_source.py`), `git` for cloning llama.cpp
- Local build steps (from `README.md`): `mkdir -p vendor`, `git clone --depth 1 --branch v0.9.0 https://github.com/ggml-org/llama.cpp.git vendor/llama.cpp`, `gradle :app:assembleDebug`
- Android Studio is an alternative, documented as JDK 17 with Android SDK 36

**Production:**
- Not yet defined. The app is a beta distributed as a debug-signed APK via GitHub Releases (`README.md`: "No es una versión de producción"). Play Store signing, release keystore, and production application ID are explicitly pending review
- Web: static hosting on GitHub Pages (HTTPS). Must be served over HTTPS for model download and WebAssembly features
- Android: ARM64 (`arm64-v8a`) devices, Android 8.0 (API 26) or newer

---

*Stack analysis: 2026-11-12*
