<!-- GSD:project-start source:PROJECT.md -->

## Project

**Repasumi**

Repasumi («Del libro al reto») es una app Android de estudio para alumnado de Primaria y ESO (6–16 años). La familia hace fotos del libro de texto; la app extrae el texto con OCR local y un modelo de lenguaje pequeño que se ejecuta en el propio móvil genera preguntas de cinco tipos (opción múltiple, verdadero/falso, completar, relacionar y ordenar), que el padre revisa antes de que el niño las use para repasar con práctica, repaso espaciado, tarjetas y modo examen. Nada sale del dispositivo.

**Core Value:** Una familia hace fotos del libro y obtiene, sin que nada salga del móvil, preguntas fiables y revisadas por el padre para que el niño repase.

### Constraints

- **Timeline**: publicación en Google Play antes del 2026-12-03, contando con que la revisión de las apps familiares tarda varios días — por eso el alcance de la 1.0 está cerrado.
- **Privacy**: ningún dato (fotos, OCR, respuestas o nombres) sale del dispositivo; sin permiso INTERNET — es el valor central y la base de la política de privacidad.
- **Platform**: Android 10+ (API 29), solo ARM64, 4 GB de RAM como mínimo; la librería nativa es ARM64 y el modelo de 0,5B necesita memoria.
- **Store policy**: programa de apps familiares de Google Play (público de 6 a 16 años) — exige control parental para enlaces externos y compras, política de privacidad y cero SDKs no certificados.
- **Identity**: el `applicationId` de producción `com.repasumi` es permanente una vez subido.
- **Tech stack**: se mantienen Java + WebView + SPA vanilla + llama.cpp; nada de frameworks nuevos en la 1.0 — no hay tiempo para migraciones.
- **License**: código público con todos los derechos reservados.
- **Workflow**: GSD + GitHub; un PR por issue en su propio worktree, con CI en cada PR.

<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->

## Technology Stack

## Languages

- Java 17 (source/target compatibility) - Android host app: `app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java`, `app/src/main/java/com/davidop/estudiaenelbus/NativeGguf.java`, `app/src/main/java/com/davidop/estudiaenelbus/NativeLearningEngine.java`
- HTML/CSS/JavaScript (single-file app, inline `<script>`) - UI shared by Android WebView and web: `web/index.html`, mirrored byte-for-byte to `app/src/main/assets/index.html` (enforced by `tools/verify_source.py`)
- JavaScript (ES modules) - Browser-side AI prototype: `web/local-ai.js`
- C++17 - JNI bridge to llama.cpp: `app/src/main/cpp/local_llm.cpp`
- Groovy (Gradle DSL) - Build scripts: `build.gradle`, `settings.gradle`, `app/build.gradle`
- Python 3 - Static source checks run in CI: `tools/verify_source.py` (writes a temporary JS file and runs `node --check` on it)
- Markdown - Project docs: `README.md`, `docs/estado.md`, `brand/IDENTIDAD_VISUAL.md`
- SVG - Favicon: `web/favicon.svg`; Android launcher icon as vector: `app/src/main/res/drawable/ic_launcher.xml`
- XML - Android manifest and resources: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/styles.xml`
- YAML - GitHub Actions workflows: `.github/workflows/android-debug.yml`, `.github/workflows/pages.yml`

## Runtime

- Android application, `minSdk 26`, `targetSdk 36`, `compileSdk 36` (`app/build.gradle`)
- ABI restricted to `arm64-v8a` (`ndk { abiFilters 'arm64-v8a' }`); the native library is ARM64 only
- Application ID: `com.davidop.estudiaenelbus.dev` (debug build adds suffix `.preview`); namespace `com.davidop.estudiaenelbus`
- Current version: `versionCode 7`, `versionName '0.5.0-study-preview'`
- Release build type: `minifyEnabled false` (no R8/ProGuard shrinking configured)
- Target device used for physical validation (per `README.md`): Xiaomi 12T
- Static site, no server runtime. Deployed as-is from `web/` to GitHub Pages (`.github/workflows/pages.yml`)
- Browser requirements implied by code: `WebAssembly`, `navigator.storage.getDirectory` (OPFS), `localStorage`, optional WebGPU (`navigator.gpu`) and cross-origin isolation for multithreading (`web/local-ai.js`)
- Gradle (Android Gradle Plugin driven). Version pinned in CI to `8.13` (`gradle/actions/setup-gradle@v4` in `.github/workflows/android-debug.yml`)
- Gradle wrapper: Not detected (no `gradlew`, `gradle/wrapper/` committed). Local builds require a locally installed Gradle compatible with AGP 8.13.2
- Lockfile: Not detected (no `gradle.lockfile`, no `package-lock.json`; the web app has no npm manifest)
- Android Gradle Plugin: `com.android.application` version `8.13.2` declared in root `build.gradle`
- Repositories: `google()` and `mavenCentral()` (plus `gradlePluginPortal()` for plugins), configured in `settings.gradle` with `FAIL_ON_PROJECT_REPOS`
- Not used as a project runtime. Node is only used in CI (`node --check`) via `tools/verify_source.py`

## Frameworks

- Android SDK framework (`android.app.Activity`, `android.webkit.WebView`, `android.speech.tts.TextToSpeech`, `android.content.ClipboardManager`) - Hosts the WebView UI, TTS, clipboard, file pickers. Entry: `app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java`
- AndroidX enabled (`android.useAndroidX=true` in `gradle.properties`); no AndroidX libraries are declared as dependencies
- No UI framework. Hand-written vanilla JavaScript rendering in `web/index.html` (inline script starting near line 268)
- llama.cpp v0.9.0 (`ggml-org/llama.cpp`), built statically via CMake as a submodule-style `add_subdirectory` - Pinned by CI; see `app/src/main/cpp/CMakeLists.txt`
- ggml (bundled inside llama.cpp) - CPU backend only in this build (`GGML_NATIVE OFF`, `GGML_OPENMP OFF`, `GGML_LLAMAFILE OFF`, `GGML_CPU_KLEIDIAI OFF`, `GGML_BACKEND_DL OFF`, `GGML_CPU_ALL_VARIANTS OFF`)
- wllama `3.8.1` (llama.cpp compiled to WebAssembly, WebGPU when available) - loaded from CDN in `web/local-ai.js`
- Tesseract.js `7.0.0` (OCR, Spanish language `spa`) - loaded from CDN in `web/local-ai.js`
- No unit or instrumented test framework configured. No `src/test` or `src/androidTest` directories exist.
- Static checks only: `tools/verify_source.py` (asserts web/Android HTML parity, key strings, and that the inline JS passes `node --check`)
- Android Gradle Plugin `8.13.2` - Android build
- CMake `3.22.1` (`cmake { version '3.22.1' }` in `app/build.gradle`; `cmake_minimum_required(VERSION 3.22.1)` in `app/src/main/cpp/CMakeLists.txt`) - Native build
- Android NDK `27.2.12479018` (installed in CI via `sdkmanager`; not referenced by version in Gradle files)
- Android build-tools `35.0.0`, platform `android-36` (installed in CI via `android-actions/setup-android@v3`)
- Temurin JDK 17 (CI via `actions/setup-java@v4`)

## Key Dependencies

- `com.google.mlkit:text-recognition:16.0.1` - On-device OCR. Used by `NativeLearningEngine.java` with `TextRecognizerOptions.DEFAULT_OPTIONS` (Latin script). Per `README.md`, the Latin model is bundled, so no network download is expected for OCR
- `llama.cpp` tag `v0.9.0` - Cloned with `git clone --depth 1 --branch v0.9.0 https://github.com/ggml-org/llama.cpp.git vendor/llama.cpp` in `.github/workflows/android-debug.yml`. The directory `vendor/` is not committed (no `.gitignore` present in repo root). Local builds must reproduce the clone (see `README.md`)
- Native libraries produced: `repasumi_native` (shared lib, built from `app/src/main/cpp/local_llm.cpp`), linked against `llama`, `log`, `android`
- llama.cpp APIs used in `local_llm.cpp`: `llama_backend_init`, `llama_model_load_from_file`, `llama_init_from_model`, `llama_tokenize`, `llama_decode`, `llama_sampler_*` (temperature 0.18, distribution seed 12345), `llama_vocab_is_eog`, `llama_memory_clear`
- Qwen2.5-0.5B-Instruct, GGUF Q4_K_M, pinned to Hugging Face commit `9217f5db79a29953eb74d5343926648285ec7e67`, about 491 MB (491,400,032 bytes). Verified by SHA-256 in both `app/src/main/java/com/davidop/estudiaenelbus/NativeGguf.java` (`EXPECTED_SHA256`) and `web/local-ai.js` (`MODEL_SHA256`)
- Imported by the user through the Android file picker (Android) or stored in browser cache (web). Stored as `repasumi-model.gguf` in app private files dir (Android)
- `@wllama/wllama@3.8.1` - `https://cdn.jsdelivr.net/npm/@wllama/wllama@3.8.1/esm/index.js` and `.../esm/wasm/wllama.wasm`. Used for `ModelManager`, `CacheManager`, `Wllama`, `LoggerWithoutDebug`
- `tesseract.js@7.0.0` - `https://cdn.jsdelivr.net/npm/tesseract.js@7.0.0/dist/tesseract.esm.min.js`. Used with `createWorker('spa', 1, ...)`
- AndroidX is enabled but no `androidx.*` artifacts are declared directly
- `org.json` (Android platform) - JSON parsing in `NativeGguf.java`, `NativeLearningEngine.java`, `MainActivity.java`
- `actions/checkout@v4`, `actions/setup-java@v4`, `android-actions/setup-android@v3`, `gradle/actions/setup-gradle@v4`, `actions/upload-artifact@v4`, `actions/configure-pages@v5`, `actions/upload-pages-artifact@v3`, `actions/deploy-pages@v4`

## Configuration

- No runtime environment variables are read by the app. No `.env` files are tracked in the repository
- Build configuration is in Gradle property file `gradle.properties` (`org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`, `android.useAndroidX=true`)
- Per-variant app identity in `app/build.gradle`: `applicationIdSuffix '.preview'` for debug; provisional application ID flagged with a Spanish comment to review before Play Store publication
- Android runtime permissions: none requested beyond defaults; `app/src/main/AndroidManifest.xml` intentionally omits `android.permission.INTERNET`
- Android manifest cleartext traffic disabled (`android:usesCleartextTraffic="false"`); backups disabled (`android:allowBackup="false"`)
- WebView hardening in `MainActivity.java`: `setAllowFileAccess(false)`, `setAllowContentAccess(false)`, `setMixedContentMode(MIXED_CONTENT_NEVER_ALLOW)`, `setJavaScriptCanOpenWindowsAutomatically(false)`, all WebView requests blocked except the home URL served from `https://appassets.androidplatform.net/assets/index.html`
- Native compile flags: `cppFlags '-std=c++17'` (`app/build.gradle`) and `CMAKE_CXX_STANDARD 17` (`app/src/main/cpp/CMakeLists.txt`)
- Model/runtime tuning constants (not environment-driven): generation temperature 0.18, seed 12345, 550 tokens (questions), 600 tokens (practice), context 4096 and batch 256 in web (`web/local-ai.js` `loadModel`); Android context and batch sizes are set in `local_llm.cpp` via `llama_context_default_params()`
- `build.gradle` (root): plugin declaration `com.android.application` `8.13.2` with `apply false`
- `settings.gradle`: `rootProject.name = 'Repasumi'`, `include ':app'`, repository rules
- `app/build.gradle`: `compileSdk 36`, `minSdk 26`, `targetSdk 36`, `versionCode 7`, `versionName '0.5.0-study-preview'`, `externalNativeBuild.cmake.path 'src/main/cpp/CMakeLists.txt'`, Java 17 compatibility, release `minifyEnabled false`
- `app/src/main/cpp/CMakeLists.txt`: `BUILD_SHARED_LIBS OFF`, `LLAMA_BUILD_EXAMPLES/TESTS/SERVER OFF`, `LLAMA_CURL OFF`, `LLAMA_OPENSSL OFF`, plus the GGML feature toggles listed above; fails configure with message if `vendor/llama.cpp/include/llama.h` is missing
- No `.gitignore` detected in repo root; `vendor/`, `app/build/`, and `.gradle/` are not excluded by a tracked ignore file

## CI/CD Pipelines

- `.github/workflows/android-debug.yml` (`Compilar APK Android (pruebas)`): triggers on push to `main` and manual dispatch. Runs on `ubuntu-latest`. Steps: JDK 17 (Temurin), Android SDK packages, NDK `27.2.12479018` and CMake `3.22.1`, clone llama.cpp `v0.9.0`, Gradle 8.13, `python3 tools/verify_source.py`, `gradle --no-daemon --stacktrace :app:assembleDebug`, uploads artifact `repasumi-beta-debug-apk` (30-day retention), publishes a GitHub prerelease `v0.5.0-preview` with the APK. Permissions: `contents: write`
- `.github/workflows/pages.yml` (`Publicar web en GitHub Pages`): triggers on push to `main` affecting `web/**` or itself, plus manual dispatch. Runs `python3 tools/verify_source.py`, then uploads `web/` as the Pages artifact and deploys. Permissions: `contents: read`, `pages: write`, `id-token: write`

## Platform Requirements

- JDK 17, Android SDK platform 36, build-tools 35.0.0, NDK `27.2.12479018`, CMake `3.22.1`, Gradle 8.13 (or AGP 8.13.2 compatible Gradle), Python 3, Node.js (for local run of `verify_source.py`), `git` for cloning llama.cpp
- Local build steps (from `README.md`): `mkdir -p vendor`, `git clone --depth 1 --branch v0.9.0 https://github.com/ggml-org/llama.cpp.git vendor/llama.cpp`, `gradle :app:assembleDebug`
- Android Studio is an alternative, documented as JDK 17 with Android SDK 36
- Not yet defined. The app is a beta distributed as a debug-signed APK via GitHub Releases (`README.md`: "No es una versión de producción"). Play Store signing, release keystore, and production application ID are explicitly pending review
- Web: static hosting on GitHub Pages (HTTPS). Must be served over HTTPS for model download and WebAssembly features
- Android: ARM64 (`arm64-v8a`) devices, Android 8.0 (API 26) or newer

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

## Naming Patterns

- Java sources use PascalCase class names matching the file name, under `app/src/main/java/com/davidop/estudiaenelbus/`: `MainActivity.java`, `NativeGguf.java`, `NativeLearningEngine.java`.
- The C++ JNI bridge is a single snake_case file, `app/src/main/cpp/local_llm.cpp`, built by `app/src/main/cpp/CMakeLists.txt`.
- The web app is a single file, `web/index.html`, with inline `<style>` and `<script>` blocks. The companion module is `web/local-ai.js` (kebab-case).
- The Android asset `app/src/main/assets/index.html` is a byte-identical copy of `web/index.html`. Never edit one without the other (enforced by `tools/verify_source.py`).
- Documentation files use UPPERCASE for generated analysis docs in `.planning/codebase/` and lowercase for hand-written docs (`docs/estado.md`, `README.md`, `brand/IDENTIDAD_VISUAL.md`).
- CI workflows use lowercase kebab-case: `.github/workflows/android-debug.yml`, `.github/workflows/pages.yml`.
- Classes: PascalCase, `final` when not designed for extension: `public final class MainActivity extends Activity` (`app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java`), `public final class NativeGguf`.
- Methods and fields: camelCase (`importModel`, `scanImages`, `localSpanishVoice`, `pendingPhotos`).
- Constants: UPPER_SNAKE_CASE with `static final`: `ORIGIN`, `HOME`, `PICK_GGUF`, `MAX_IMAGES`, `MAX_TEXT_CHARS`, `EXPECTED_SHA256`, `MODEL`, `MIN_BYTES`.
- Request codes are magic numeric constants that are adjacent by convention: `PICK_PAGES = 832`, and `PICK_PAGES + 1` is used for the photo-scan request. Follow this pattern when adding new codes (`MainActivity.java`, lines 39-40 and 140).
- Nested callback interfaces are named `Events` with a single `emit(String event, String payload)` method (`NativeGguf.Events`, `NativeLearningEngine.Events`).
- JavaScript bridge class: `public final class AndroidBridge` nested in `MainActivity`, exposed to the WebView as the global `AndroidBridge`.
- Anonymous namespace for file-private helpers (`namespace { struct Engine ... }` in `app/src/main/cpp/local_llm.cpp`).
- Struct and type names PascalCase (`Engine`); local variables camelCase (`maxOutput`, `needed`, `failed`); JNI entry points follow the mangled name pattern `Java_com_davidop_estudiaenelbus_NativeGguf_<method>`.
- Functions: camelCase, and many are plain `function` declarations (`function save()`, `function toast(msg,type='success')`, `function studio()`).
- Constants: UPPER_SNAKE_CASE for configuration and storage keys: `const KEY='estudia-en-el-bus-v1'` (`web/index.html`, line ~270), `MODEL_URL`, `MODEL_SHA256`, `MAX_IMAGES`, `MAX_TEXT_CHARS`, `GBNF_LESSON`, `GBNF_BASE` (`web/local-ai.js`).
- Domain identifiers are in Spanish: `topics`, `learners`, `flagged`, `mastery`, `planForTopic`, `cardsStart`, `weakest`, `dueTotal`, `smartPriority`, `streak`. Use Spanish for new domain concepts to match existing code.
- Short local helpers in Spanish or English where precise: `mb`, `secs`, `decimal`, `emit`, `flag`.
- Question object `type` values are fixed short codes: `'mc'`, `'tf'`, `'fill'`, `'match'`, `'order'`. Keep these exact, since `tools/verify_source.py` asserts that all five appear in the HTML.
- Storage key versioning: suffix `-v1` in `KEY` and `FLAG = 'repasumi-web-model-v1'`. Bump the suffix on incompatible schema changes.
- Strings in `app/src/main/res/values/strings.xml` use snake_case keys (`app_name`). Styles use PascalCase (`AppTheme`).

## Language and Copy

- All user-facing text is Spanish (Spain): labels, toasts, error messages, and prompts. Keep new strings in Spanish and use Spanish typography (`¿…?`, `¡…!`, `…` as the ellipsis, and `"…"` as the ellipsis character in progress messages).
- Code comments and Javadoc are in Spanish: `/** Beta: interfaz embebida. Sin permiso INTERNET. */` (`MainActivity.java`, line 32). Write new comments in Spanish to match.
- Error messages surfaced to the user never expose stack traces. They are built from `e.getMessage()` or a fixed Spanish fallback (`NativeGguf.error`, `NativeLearningEngine.message`).

## Code Style

- 4-space indentation, opening braces on the same line, and one statement per line in most blocks.
- Single-line lambdas and small anonymous classes are common for callbacks (`tts = new TextToSpeech(this, code -> {...})`, `@Override public boolean shouldOverrideUrlLoading(...) { ... }`).
- Java 17 language level is set in `app/build.gradle` (`sourceCompatibility JavaVersion.VERSION_17`). Lambdas, try-with-resources, and plain string concatenation are the norm. Do not use features above Java 17.
- No formatter config (no `.editorconfig`, no Spotless, no Checkstyle). Match the surrounding file's style.
- The page's script is wrapped in an IIFE with strict mode: `(()=>{'use strict';` (`web/index.html`, around line 269). Keep new logic inside this wrapper rather than adding globals.
- The inline script is compact and dense: many statements per line, short arrow functions, and minimal whitespace (`const $=sel=>document.querySelector(sel);`). Preserve this density within the existing code; do not reformat the whole file in one change, because the diff would be unreviewable.
- `web/local-ai.js` is an ES module with more standard formatting: two-space indentation, one function per block, and `export async function` for the public surface (`checkModel`, `downloadModel`, `importModel`, `scanPhotos`, `generateFromOCR`, `generatePractice`).
- Quotes: single quotes are dominant in JavaScript; template literals are used for HTML fragments (`` `<header class="top">${...}</header>` ``).
- Semicolons are always used.
- Modern syntax is used freely: optional chaining (`globalThis.AndroidBridge?.web`), nullish assignment (`lib ??= import(WLLAMA)`), `??`, and `catch {}` without a binding. Target a modern evergreen browser.
- C++17 (`set(CMAKE_CXX_STANDARD 17)` in `app/src/main/cpp/CMakeLists.txt`, and `-std=c++17` in `app/build.gradle`).
- 4-space indentation. The pointer style is `llama_context *context` (star attached to the name). Follow the file's style.
- Raw owning pointers are wrapped in `std::unique_ptr` during construction, then released into the JNI `jlong` handle (`engine.release()` in `nativeLoadModel`).
- No linter is configured. There is no ESLint, Biome, Prettier, Checkstyle, or clang-tidy config in the repository. Rely on the conventions in this document.
- The only static check is `tools/verify_source.py` (see `.planning/codebase/TESTING.md`).

## Import Organization

- Imports are unordered in the existing files. `MainActivity.java` mixes `android.*`, `java.*`, and `android.*` again (lines 3-30). Do not reorder existing imports in unrelated changes. New imports may be appended in the same block.
- Use explicit imports, never wildcards. Fully qualified names are used inline for JSON classes in one place (`org.json.JSONObject.quote(...)` in `MainActivity.java`, lines 67-68). Prefer an import for new code.
- Dynamic imports load heavy dependencies on demand, and they are cached through a memoized promise: `const wllamaLib = () => (lib ??= import(WLLAMA));` (`web/local-ai.js`, line 24). Apply the same pattern for new lazy libraries.
- In `web/index.html`, the local AI module is loaded lazily: `import('./local-ai.js').then(m=>m[name](...args))` (line ~326).
- No path aliases are used.

## Error Handling

- Background work runs on a single-thread `ExecutorService` (`NativeGguf.worker`, `NativeLearningEngine` uses the main `Handler`). Results return through an `Events` callback. Do not run blocking I/O on the UI thread.
- Errors are reported to the WebView as events, never thrown across the boundary: `events.emit("error", "Importación: " + error(e))`. The JavaScript side listens through `window.repasumiNativeEvent`.
- `catch (Exception | LinkageError e)` is used in model code, because a missing native library throws `UnsatisfiedLinkError` (`NativeGguf.generate`, `generatePractice`).
- Temporary files are cleaned up in `finally` blocks (`if (temp.exists()) temp.delete();`).
- Empty catch blocks with `ignored` are used only for best-effort cleanup or optional behavior: `catch (Exception ignored) { localSpanishVoice = false; }` (`MainActivity.java`, line 62). Do not swallow errors in new data paths.
- Input validation happens at the entry point and returns a user-facing Spanish message: `error("Selecciona entre 1 y 30 imágenes.")`.
- Async functions use `try/catch/finally`. Failures are emitted as UI events, not rethrown: `catch (e) { emit('error', 'IA local: ' + (e?.message || e) + '...'); }`.
- Errors are normalized with `(e?.message || e)` before display.
- Non-critical storage calls are wrapped and degrade silently: `try { return localStorage.getItem(FLAG) === '1'; } catch { return false; }` (`web/local-ai.js`, line 20). Storage failures set `storageOK=false` and show a notice rather than crashing (`web/index.html`, `save()`).
- `console.warn` and `console.info` are used for diagnostics, prefixed with `[Repasumi IA web]`. `console.error` appears once, in `web/index.html`.
- Model output is always validated before use: JSON is extracted with `indexOf('{')` / `lastIndexOf('}')`, then parsed, and the question count is checked (`extractJson`, `generateFromOCR`). Keep this validation step for any new AI output.
- Errors are raised as Java `IllegalStateException` through a `fail(env, msg)` helper and then the function returns a sentinel (`0` or `nullptr`). Always check the JNI return value after calling `fail`.
- Early returns guard invalid handles before use (`if (!engine || !engine->context || !engine->vocab)`).

## Logging

- Android: no logging framework is used. There are no `Log.*` calls, `System.out`, or `printStackTrace` calls in `app/src/main`. Diagnostics reach the user through events and `Toast`.
- Web: `console.info`/`console.warn` with the `[Repasumi IA web]` prefix. Do not log OCR text, photos, or question content. `web/local-ai.js` header states that photos and text never leave the browser, and logs follow the same rule.
- Native C++: the file header states "No sockets, telemetry, model download or OCR logging." Do not add logging of prompts or OCR output.

## Comments

- Explain privacy, security, and limit decisions, which is the dominant comment style: `// Las fotos y el texto nunca salen del navegador` (`web/local-ai.js`, line 4), `// Nunca se usa una API de inferencia cloud.` (`NativeLearningEngine.java`, line 19).
- Explain why a magic number exists, for example `// Modelo compacto: lotes de 2 preguntas` and `// Reparte páginas OCR entre lotes, con máximo de 2400 caracteres` (`NativeGguf.java`).
- Record the reason for a pinned external value: `// Si se cambia el modelo, hay que actualizar esta suma tras validación.` (`NativeGguf.java`, line 23).
- Keep the CSS banner comment at the top of the inline `<style>` block (`web/index.html`, line 9).
- Javadoc `/** ... */` one-line summaries are used on classes and important methods. Method-level Javadoc is optional. Example: `/** Genera preguntas de práctica nuevas usando exclusivamente el tema guardado. */` (`NativeGguf.java`, line 172).
- JSDoc is not used in the JavaScript files. Describe behavior in short Spanish comments above the function.
- Do not add comments that restate the code.

## Function Design

- Java methods are short, delegating to a worker or `runOnUiThread`. Long methods exist in `NativeGguf.generate` and `local_llm.cpp::nativeComplete`. Do not add more logic to these; extract helpers instead.
- The web inline script defines most behavior as one-line functions. Keep helpers this small when possible.
- Pass Android `Context`/`Activity` explicitly in constructors (`NativeGguf(Context, Events)`, `NativeLearningEngine(Activity, Events)`).
- JavaScript functions use positional arguments with default values (`function toast(msg,type='success')`, `function complete(prompt, maxTokens, label, grammar)`).
- Bridge methods that the WebView calls take `String` arguments that are JSON-encoded when they carry structure (`generatePractice(String topicJson, String recentJson)`). Parse JSON on the receiving side.
- Java workers return `void` and report through `Events`. Synchronous getters return plain values (`getExtracted()`).
- JNI functions return `jlong` handles (0 on failure), `jstring` (nullptr on failure), or `void`.
- JavaScript async helpers return `{ text, stats }` objects (`complete`) or emit events and return `undefined`.

## Module Design

- `web/local-ai.js` exports the public bridge surface as named async functions, and it exports `_sha256ForTests` (`web/local-ai.js`, line 323). The leading underscore marks a test-only export.
- `web/index.html` exposes only what the bridge needs: `globalThis.AndroidBridge` is assigned in `webBridge()`, and `globalThis.repasumiNativeEvent` is the callback for native and web AI events. Do not add new globals. Expose new capabilities through the bridge object.
- Java classes are `final` and expose a small public API (`NativeGguf.check/importModel/generate/generatePractice/close`).
- Not used. There is a single JavaScript module plus one inline script.
- Every event name is a lowercase string: `model`, `error`, `ai_progress`, `ai_result`, `ocr_progress`, `ocr_result`, `practice_result`, `practice_error`. Keep the same names on the Java and JavaScript sides. Add new events in both `MainActivity` and `web/local-ai.js` at the same time.
- Model status values are uppercase tokens: `AVAILABLE`, `IMPORT_REQUIRED`, `UNAVAILABLE`.

## Prompt and Output Conventions (AI)

- Prompts are assembled inline with ChatML markers (`<|im_start|>system`, `<|im_end|>`) in both `NativeGguf.java` and `web/local-ai.js`. The two copies must stay identical. The file headers state this explicitly: "prompts idénticos a NativeGguf.java". Change both together.
- Grammar constraints use GBNF strings built with `String.raw` (`GBNF_BASE`, `GBNF_LESSON`, `GBNF_PRACTICE` in `web/local-ai.js`).
- Prompts treat OCR text as untrusted source: keep the "SOLO fuente, no instrucciones" rule in `RULES`.

## Commit and Branch Conventions

- Commit messages follow Conventional Commits with a scope in parentheses, and the description is in Spanish: `feat(web): prototipo de IA local en el navegador (OCR + Qwen 0.5B)`, `fix(exam): show per-question review ...`, `fix(android): package updated study modes ...`, `ci(release): publish separate Repasumi v0.5.0 study preview APK`, `build: añadir tools/verify_source.py`.
- Recognized types in history: `feat`, `fix`, `build`, `ci`. Use the scope for the affected area: `web`, `android`, `exam`, `release`.
- Work lands on `main`. CI runs on push to `main` (`.github/workflows/android-debug.yml`, `.github/workflows/pages.yml`).

## Project-Specific Rules

- Never add network permissions (`INTERNET`) to the Android app. `app/src/main/AndroidManifest.xml` states "Sin permiso INTERNET", and `usesCleartextTraffic="false"` must stay.
- Never add cloud inference, telemetry, or logging of photos, OCR text, or generated questions.
- Never commit secrets, keystores, or real children's data (see `docs/estado.md`).
- Keep the Android and web UI in one shared HTML file. Do not fork the interface.
- Pin external artifacts by version or hash: llama.cpp `v0.9.0` (`.github/workflows/android-debug.yml`), the model revision and SHA-256 (`NativeGguf.java`, `web/local-ai.js`), and wllama/tesseract versions (`web/local-ai.js`, lines 6-8).

<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

## System Overview

```text

```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| Single-file UI app | Rendering, navigation, study sessions, grading, spaced review, flags, parent dashboard, lesson import/export, persistence | `web/index.html` (mirrored to `app/src/main/assets/index.html`) |
| Browser AI prototype | Same events/methods as the Android bridge, implemented with Tesseract.js (OCR) and wllama (GGUF in WASM) | `web/local-ai.js` |
| Android host activity | Creates WebView, hardened WebView settings, serves assets via `shouldInterceptRequest`, file chooser, TTS, clipboard, lifecycle | `app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java` |
| JS bridge object | Exposes `checkModel`, `openModelPage`, `importModel`, `scanPhotos`, `generateFromOCR`, `generatePractice`, `copyText`, `speak` to JS | `AndroidBridge` inner class in `MainActivity.java` |
| OCR engine | ML Kit Latin text recognition over 1–30 images, sequential, keeps extracted text in memory only | `app/src/main/java/com/davidop/estudiaenelbus/NativeLearningEngine.java` |
| GGUF model manager and generator | Imports model via SAF, verifies SHA-256 (`EXPECTED_SHA256`), loads native handle, builds prompts, parses JSON, emits events | `app/src/main/java/com/davidop/estudiaenelbus/NativeGguf.java` |
| Native inference | `llama_model_load_from_file`, tokenize, batched decode, temperature 0.18 sampler, `nativeComplete` output | `app/src/main/cpp/local_llm.cpp` |
| Native build | Pins llama.cpp source at `vendor/llama.cpp` (CI clones v0.9.0), builds static llama + shared `repasumi_native` | `app/src/main/cpp/CMakeLists.txt` |
| Android build | Application id, SDK levels, ABI filter `arm64-v8a`, ML Kit dependency, CMake wiring | `app/build.gradle`, `settings.gradle`, `build.gradle`, `gradle.properties` |
| Manifest | Launcher activity, no `INTERNET` permission, no cleartext traffic, backup disabled | `app/src/main/AndroidManifest.xml` |
| Static verifier | Asserts Android and web HTML are byte-identical, checks key strings, runs `node --check` on the script | `tools/verify_source.py` |
| CI pipelines | APK debug build and release to GitHub Releases; GitHub Pages deploy of `web/` | `.github/workflows/android-debug.yml`, `.github/workflows/pages.yml` |

## Pattern Overview

- One HTML file contains all CSS, JS, and embedded data. There is no bundler, no framework, no module graph for the core app. Fonts are inlined as base64 `@font-face` data URIs because the WebView blocks external requests.
- Rendering is imperative string templating: `render()` in `web/index.html` rebuilds `#app` innerHTML from `state` on each change. Event handling is one delegated click handler that switches on `data-action`.
- Feature detection replaces configuration: `nativeAvailable()` checks `globalThis.AndroidBridge?.scanPhotos`; `webAI()` checks `.web`; `sampleAvailable()` checks a host-injected `globalThis.sample` API used for an optional cloud-free photo path.
- Native results return to JS through one channel: `window.repasumiNativeEvent(event, payload)`. Event names are `model`, `ocr_progress`, `ocr_result`, `ai_progress`, `ai_result`, `practice_result`, `practice_error`, `error`.
- The Android app is an offline, no-network application: no `INTERNET` permission, no `usesCleartextTraffic`, and `WebView` blocks any URL except the app asset origin.

## Layers

- Purpose: Study UI, lesson import, parent dashboard, accessibility controls.
- Location: `web/index.html` (script section starts near line 268; styles in the head).
- Contains: CSS in `<style>`, render functions (`study()`, `add()`, `parents()`, `lesson()`, `cardsPage()`, `donePage()`, `examDonePage()`), delegated handler `handleClick`.
- Depends on: `store`, `state`, `globalThis.AndroidBridge` (optional), `window.repasumiNativeEvent`.
- Used by: The user directly, through the WebView or the browser.
- Purpose: Topic/question model, spaced-review scheduling, mastery metrics, sessions, flags, learner profiles.
- Location: `web/index.html`, functions `topicStats`, `questionStats`, `mastery`, `weakest`, `planForTopic`, `planQuick`, `grade`, `save`, `getProfile`.
- Contains: Store shape persisted in localStorage key `estudia-en-el-bus-v1`. Built-in lessons are `EXAMPLE`, `ESO_MATERIA_2`, `PRIMARIA_LENGUA_5` inlined as constants and merged into existing installs at startup.
- Depends on: `localStorage` (guarded by `storageOK`; falls back to in-memory when unavailable).
- Used by: Presentation layer only.
- Purpose: Adapter that turns the native or browser AI capabilities into the same async method and event contract.
- Location: `web/index.html` `webBridge()` (around line 326) and `repasumiNativeEvent` (around line 454). On Android, `AndroidBridge` is injected by `addJavascriptInterface` in `MainActivity.onCreate`.
- Contains: `copyText`, `speak`, `checkModel`, `importModel`, `scanPhotos`, `generateFromOCR`, `generatePractice`.
- Depends on: Native or browser implementation.
- Used by: `handleClick` and `generateAI`/`beginTopicPractice`.
- Purpose: Activity lifecycle, WebView setup, asset serving, system integrations, and routing of native events into the WebView.
- Location: `app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java`.
- Contains: `AndroidBridge` inner class, `onActivityResult` routing by request code, `ORIGIN`/`HOME` constants, TTS voice selection.
- Depends on: `NativeLearningEngine`, `NativeGguf`, Android WebKit APIs.
- Used by: Android launcher through the manifest.
- Purpose: Local OCR and GGUF generation with no network.
- Location: `NativeLearningEngine.java` (OCR), `NativeGguf.java` (model file, prompts, JSON parsing, threading).
- Contains: Constants `MAX_IMAGES = 30`, `MAX_TEXT_CHARS = 150_000`, `MIN_BYTES`, `MAX_BYTES`, `EXPECTED_SHA256`, prompt `RULES`.
- Depends on: ML Kit Text Recognition `16.0.1`, JNI library `repasumi_native`.
- Used by: `MainActivity.AndroidBridge` and `onActivityResult`.
- Purpose: Load a GGUF file and run greedy-ish sampling to produce text.
- Location: `app/src/main/cpp/local_llm.cpp`.
- Contains: Single `Engine` struct holding model, context, vocab. JNI exports `nativeLoadModel`, `nativeComplete`, `nativeClose` for `com.davidop.estudiaenelbus.NativeGguf`.
- Depends on: llama.cpp `llama.h`, Android `log` and `android` NDK libraries.
- Used by: `NativeGguf` only.

## Data Flow

### Primary Request Path: Study a topic

### Secondary Flow: Import and generate a lesson on Android

### Tertiary Flow: Native event return path

### Browser-only flow (GitHub Pages or local HTTPS)

- UI state lives in a single `state` object in `web/index.html` (line ~312). It is not persisted, except through `store`.
- Durable state lives in one `store` object persisted as a whole to `localStorage`. There is no partial write path.
- Native state: a single model handle in `NativeGguf.handle` (a `long` pointer) guarded by the single-thread executor. OCR results live in `NativeLearningEngine.extracted`.

## Key Abstractions

- Purpose: A unit of study with `title`, `emoji`, `summary[]`, `questions[]`, `id`, `createdAt`.
- Examples: `EXAMPLE`, `ESO_MATERIA_2`, `PRIMARIA_LENGUA_5` in `web/index.html`; generated objects from `ai_result`.
- Pattern: Plain JSON. The question schema uses `type` in `mc`, `tf`, `fill`, `match`, `order`, with `q`, `a`, `opts`, `pairs`, `items`, `why`, `src`. `verify_source.py` checks all five types are present.
- Purpose: One exercise. Its per-learner stats live in `profiles[learnerId].topics[topicId].questions[index]` as `{seen, firstCorrect, wrong}`.
- Examples: Rendering in `lesson()` at `web/index.html`; flags keyed as `"<topicId>::<index>"`.
- Pattern: Index-based identity, which means reordering `questions` breaks progress and flags.
- Purpose: Per-child metrics and flags. Profiles are keyed by learner id under `store.profiles`.
- Examples: `getProfile()` in `web/index.html` (line ~315).
- Pattern: Lazy-initialized nested objects with `??=` defaults at each access.
- Purpose: One API surface with two implementations.
- Examples: `MainActivity.AndroidBridge` in `MainActivity.java` and `webBridge()` in `web/index.html`.
- Pattern: Methods are fire-and-forget. Results return as `repasumiNativeEvent` calls.
- Purpose: Owns a `llama_model`, `llama_context`, and vocab.
- Examples: `Engine` in `app/src/main/cpp/local_llm.cpp`.
- Pattern: Opaque `jlong` handle created by `nativeLoadModel`, freed by `nativeClose`. Only one instance is held per `NativeGguf`.

## Entry Points

- Location: `app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java` (`onCreate`), declared in `app/src/main/AndroidManifest.xml`.
- Triggers: App launch from the launcher (`MAIN` / `LAUNCHER`).
- Responsibilities: Build the WebView, register `AndroidBridge`, load `https://appassets.androidplatform.net/assets/index.html`, start TTS, wire native engines.
- Location: `web/index.html` (served by GitHub Pages from the `web/` artifact, see `.github/workflows/pages.yml`).
- Triggers: Page load. Immediately runs the IIFE, reads `localStorage`, calls `render()`.
- Responsibilities: Same as Android UI, plus installs `webBridge()` if the page is served over http/https.
- Location: `NativeGguf` static initializer (`System.loadLibrary("repasumi_native")`) and `NativeLearningEngine` (no native library; uses ML Kit).
- Triggers: First class use.
- Responsibilities: On failure `libraryLoaded = false`, and `check()` emits `model=UNAVAILABLE`.
- Location: `tools/verify_source.py`.
- Triggers: CI in both workflows, and manually.
- Responsibilities: Compares `app/src/main/assets/index.html` and `web/index.html`, asserts strings, runs `node --check` on the extracted script. The check writes `/tmp/repasumi-script-check.js`, which is not portable to Windows.

## Architectural Constraints

- **Threading:** The WebView runs on the Android UI thread. OCR callbacks come from ML Kit on the main looper. Model load, tokenize, and generation run on a single-thread `ExecutorService` inside `NativeGguf`. Results return to the UI thread via `runOnUiThread`. `local-ai.js` uses async/await on the main thread, and `sha256` and the `Tesseract` worker yield periodically.
- **Global state:** `web/index.html` keeps all mutable state in module-level `let`/`const` inside one IIFE (`store`, `state`, `storageOK`). Native code keeps `NativeGguf.libraryLoaded` as a static and the `Engine` handle per instance. `local-ai.js` keeps module-level `lib`, `engine`, `extracted`, `busy`.
- **Circular imports:** None. The core app has no imports. `local-ai.js` is loaded only with dynamic `import('./local-ai.js')`, and it imports nothing local.
- **Single source of truth for HTML:** `web/index.html` and `app/src/main/assets/index.html` must stay identical. The copy is manual, and only `tools/verify_source.py` (run in CI) catches drift.
- **Network policy:** Android has no `INTERNET` permission and blocks every request not for the app asset origin (`shouldInterceptRequest` returns empty bodies). `openModelPage` opens an external browser intent for the Hugging Face download, which is outside the app's network stack.
- **Model file policy:** The GGUF is never bundled in the APK. It is imported from SAF, hashed, and copied to `context.getFilesDir()/repasumi-model.gguf` (`NativeGguf.importModel`). Size must be between 100 MB and 1 GB.
- **Context window:** Native `n_ctx = 4096`, `nativeComplete` rejects prompts over 13000 bytes and enforces `prompt_tokens <= 4096 - maxOutput - 32`. `local-ai.js` uses `n_ctx: 4096`.
- **Model ABI:** Only `arm64-v8a` is built (`app/build.gradle` `abiFilters`), and CMake uses `GGML_NATIVE OFF`. The JNI library will fail to load on other ABIs.
- **Host-injected AI:** `generateAI()` in `web/index.html` calls `globalThis.sample.limits()` and `globalThis.sample(...)` if a host provides them. Nothing in this repository defines them, so the photo-to-lesson path is inert outside such an environment.

## Anti-Patterns

### Hand-mirrored HTML

### Prompt and parser logic duplicated across Java and JS

### Index-based question identity

### Stale comments that contradict behaviour

## Error Handling

- Native exceptions map to Spanish messages in `NativeGguf.java` (for example "El modelo no coincide con el GGUF Qwen verificado (SHA-256).").
- `local_llm.cpp` throws via `fail(env, msg)` into `IllegalStateException`, not via return codes. Failed model load returns `0`, which Java converts to an exception.
- In JS, `JSON.parse` failures become a visible error. The README states that invalid JSON fails visibly and requires human review before saving.
- Local storage failure sets `storageOK = false` and keeps the app running in memory with a warning (`notice()`).

## Cross-Cutting Concerns

<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-fast` for a trivial task inline, with no subagents and no PLAN.md
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
