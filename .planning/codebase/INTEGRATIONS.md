---
last_mapped_commit: 0938eec730b766f55e975c6539ed7b22a60e3333
last_mapped_at: 2026-11-12
---
# External Integrations

**Analysis Date:** 2026-11-12

## Summary

Repasumi has no backend, no database, no user accounts, and no webhooks. The Android app declares no `INTERNET` permission (`app/src/main/AndroidManifest.xml`), and all user content stays on the device. External network use is limited to build-time downloads (CI and local builds) and, in the web build only, one-time downloads of the AI model and JavaScript libraries from third-party CDNs and Hugging Face.

## APIs & External Services

**Build-time source and artifact hosting:**
- GitHub (`github.com`) - Source of the pinned llama.cpp tree, cloned in CI and locally
  - Used in: `.github/workflows/android-debug.yml`, `README.md`
  - Command: `git clone --depth 1 --branch v0.9.0 https://github.com/ggml-org/llama.cpp.git vendor/llama.cpp`
  - Auth: none (public repository)
- Maven repositories (Google Maven and Maven Central) - Android dependencies, including `com.google.mlkit:text-recognition:16.0.1`
  - Declared in: `settings.gradle` (`google()`, `mavenCentral()`), `build.gradle` (`gradlePluginPortal()` for plugin resolution)
  - Auth: none
- Android SDK / NDK package repository - Installed in CI via `android-actions/setup-android@v3` and `sdkmanager`
  - Packages: `platform-tools`, `platforms;android-36`, `build-tools;35.0.0`, `ndk;27.2.12479018`, `cmake;3.22.1`
  - Auth: none

**Model and browser AI runtime (web build only, `web/local-ai.js`):**
- Hugging Face Hub - Source of the Qwen2.5-0.5B-Instruct GGUF model, pinned by commit
  - URL: `https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf`
  - Integrity: SHA-256 check (`MODEL_SHA256`) and exact byte size check (`MODEL_BYTES = 491400032`) in `web/local-ai.js`
  - Used by: `downloadModel()` (through wllama `ModelManager.downloadModel`) and `importModel()` (manual file import, hashed locally before caching)
  - Auth: none (public model)
- jsDelivr CDN (`cdn.jsdelivr.net`) - Serves npm packages
  - `@wllama/wllama@3.8.1`: ES module `.../esm/index.js` and WebAssembly binary `.../esm/wasm/wllama.wasm` (constants `WLLAMA`, `WLLAMA_WASM`)
  - `tesseract.js@7.0.0`: `.../dist/tesseract.esm.min.js` (constant `TESSERACT`), loaded via dynamic `import()` in `scanPhotos()`
  - Loaded lazily: wllama on first model use, Tesseract on first OCR run
  - Auth: none
  - Integrity: no Subresource Integrity (`integrity=`) is used, and the URLs are version-pinned but not hash-pinned
- Tesseract.js default language data (`spa` traineddata) - Tesseract.js fetches the Spanish language model at runtime when `createWorker('spa', ...)` runs, using its library default source (not configured explicitly in `web/local-ai.js`; the default CDN host is not pinned in the code). Treat as an implicit network dependency on first OCR use

**Android on-device services (no network):**
- Google ML Kit Text Recognition - On-device OCR (Latin). Used in `app/src/main/java/com/davidop/estudiaenelbus/NativeLearningEngine.java` via `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`. Dependency: `app/build.gradle`. The README states the model is bundled; the runtime is provided through Google Play services on the device, so this is the only Google-branded component in the app
- Android Text-to-Speech (`android.speech.tts.TextToSpeech`) - Spanish voice selected only if it is installed and does not require a network connection (`isNetworkConnectionRequired() == false`). Bridged as `AndroidBridge.speak` in `app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java`; web falls back to `speechSynthesis` (`web/index.html`)
- Android Storage Access Framework (`Intent.ACTION_OPEN_DOCUMENT`) - File selection for GGUF import (`PICK_GGUF`) and photo selection for OCR (`PICK_PAGES`, `PICK_PAGES + 1`) in `MainActivity.java`. Reads through `ContentResolver.openInputStream`
- Android Clipboard - `AndroidBridge.copyText` in `MainActivity.java`; web uses `navigator.clipboard.writeText` (`web/index.html`)
- External browser hand-off - `AndroidBridge.openModelPage()` opens the Hugging Face model URL in the user's browser through `Intent.ACTION_VIEW` (`MainActivity.java`). The app never downloads the model itself on Android

## Data Storage

**Databases:**
- None. No database client, ORM, SQL, or NoSQL library is present in the repository

**Local persistence (Android):**
- Progress and study data: stored by the HTML app in WebView `localStorage` (`DOM storage` enabled via `settings.setDomStorageEnabled(true)` in `MainActivity.java`). Storage key defined in `web/index.html` (`KEY` constant and `fresh()` store structure, with `store.topics` and `store.learners`)
- Local model file: `repasumi-model.gguf` copied to the app's private files directory `context.getFilesDir()` (`app/src/main/java/com/davidop/estudiaenelbus/NativeGguf.java`). Imported in a temporary file `repasumi-model.gguf.part`, hashed with SHA-256 while streaming, then renamed. Size guards: `MIN_BYTES = 100_000_000`, `MAX_BYTES = 1_000_000_000`
- Android backups disabled: `android:allowBackup="false"` in `AndroidManifest.xml`

**Local persistence (web):**
- Progress and study data: `localStorage` (same HTML store as Android). Built-in topics are re-inserted on load if missing (`builtin-primaria5-lengua-u3-v1`, `builtin-eso2-materia-v1`) in `web/index.html`
- AI model flag: `localStorage` key `repasumi-web-model-v1` (`FLAG` in `web/local-ai.js`)
- Model binary: wllama `ModelManager` and `CacheManager` store the GGUF in the browser's Origin Private File System (OPFS) via `navigator.storage.getDirectory()`. `navigator.storage.persist()` is requested to reduce eviction. Quota check requires about 1.1 x model size free before download
- Photos and OCR text: not persisted. OCR output is kept only in memory (`extracted` in `web/local-ai.js`, `NativeLearningEngine.extracted` in Java) with a cap of 150,000 characters (`MAX_TEXT_CHARS`)

**File Storage:**
- Local filesystem only. No cloud object storage (no S3, GCS, Azure Blob, etc.)

**Caching:**
- None beyond the browser model cache (OPFS, see above) and the Gradle/CI caches managed by `gradle/actions/setup-gradle@v4`

## Authentication & Identity

**Auth Provider:**
- None. No login, accounts, tokens, or identity provider. The app is single-user and local. Parent/teacher features are local only (README: "seguimiento familiar local")

**Secrets handled by the code:**
- None. The only credential-like value used in the repository is the workflow `GITHUB_TOKEN` (`${{ github.token }}`) passed as `GH_TOKEN` for `gh` CLI release publishing in `.github/workflows/android-debug.yml`
- The GGUF file SHA-256 (`EXPECTED_SHA256` in `NativeGguf.java`, `MODEL_SHA256` in `web/local-ai.js`) is an integrity hash, not a secret

## Monitoring & Observability

**Error Tracking:**
- None. No Sentry, Crashlytics, or similar SDK is declared

**Logs:**
- Android: `android/log.h` (`__android_log_print`) in the native layer (`app/src/main/cpp/local_llm.cpp`, linked with `log`); Java errors surface as UI events through `window.repasumiNativeEvent` (`MainActivity.java`)
- Web: `console.info` and `console.warn` in `web/local-ai.js` (performance stats and invalid model output). No remote log shipping

**Analytics:**
- None. No analytics or telemetry SDK. Public `README.md` states the Android build does not upload photos or metrics

## CI/CD & Deployment

**Hosting:**
- GitHub Pages - Web app from `web/` directory, deployed by `.github/workflows/pages.yml` using `actions/configure-pages@v5`, `actions/upload-pages-artifact@v3` and `actions/deploy-pages@v4` (environment `github-pages`)
- GitHub Releases - APK distribution. Prerelease tag `v0.5.0-preview`, asset `repasumi-0.5.0-preview.apk`, created or updated by `.github/workflows/android-debug.yml` with the `gh` CLI (`gh release view`, `gh release upload --clobber`, `gh release create --prerelease --target "${GITHUB_SHA}"`)
- GitHub Actions artifact - `repasumi-beta-debug-apk` (30-day retention) in `.github/workflows/android-debug.yml`

**CI Pipeline:**
- GitHub Actions (`ubuntu-latest`). Two workflows, described in `.planning/codebase/STACK.md` under CI/CD Pipelines
- Google Play: Not used. Production signing and store publication are pending (`README.md`, `docs/estado.md`)

## Environment Configuration

**Required env vars:**
- None for the application at runtime
- CI: `GITHUB_SHA` and `GITHUB_TOKEN` (provided by GitHub Actions); `GH_TOKEN` is set from `github.token` in the release step
- Local build: no environment variables required. Requires the manual clone of `vendor/llama.cpp` (`README.md`)

**Secrets location:**
- No secrets in the repository. No `.env`, keystore, `google-services.json`, or signing configuration files are tracked (verified against `git ls-files`)
- Release signing keystore: Not configured. The debug APK uses the default Android debug keystore generated in CI (`docs/estado.md`: no production keystores)
- Not applicable: no `secrets.*` references in workflows other than `github.token`

## Webhooks & Callbacks

**Incoming:**
- None. No HTTP server, no webhook receiver, no exported services other than the launcher Activity (`android:exported="true"` on `.MainActivity`, `app/src/main/AndroidManifest.xml`)

**Outgoing:**
- None from application code. Build-time outbound calls are limited to GitHub (llama.cpp clone, `gh` release operations), Maven/Google repositories, and the Android SDK repository
- Runtime outbound calls (web build only): Hugging Face model download and jsDelivr script/WASM downloads, described above

## JavaScript Bridge Contract (internal integration point)

The Android WebView and the browser expose the same surface to `web/index.html`:
- `AndroidBridge` (Java `@JavascriptInterface` methods in `MainActivity.java`): `checkModel`, `openModelPage`, `importModel`, `scanPhotos`, `generateFromOCR(level)`, `generatePractice(topicJson, recentJson)`, `copyText`, `speak`
- Web equivalent: `webBridge()` in `web/index.html` (installed only when `AndroidBridge` is absent, protocol is `http:` or `https:`, and `WebAssembly` is available), which dynamically imports `web/local-ai.js` and calls the matching exported function (`checkModel`, `downloadModel`, `importModel`, `scanPhotos`, and generation helpers)
- Events back to the page: `window.repasumiNativeEvent(event, payload)` with event names `model`, `ocr_progress`, `ocr_result`, `ai_progress`, `ai_result`, `practice_result`, `practice_error`, and `error`. Java sends them through `WebView.evaluateJavascript`; web calls them directly

## Data Flow Summary

- Photos (Android): picked with SAF -> decoded and recognized on-device with ML Kit -> text kept in memory -> generation via local llama.cpp (`nativeComplete`) -> result stored in `localStorage`
- Photos (web): picked with `<input type=file>` -> resized to 2200 px max in canvas -> OCR with Tesseract.js in a Web Worker -> text kept in memory -> generation with wllama -> result stored in `localStorage`
- No step sends user photos, OCR text, or generated content to a remote host

---

*Integration audit: 2026-11-12*
