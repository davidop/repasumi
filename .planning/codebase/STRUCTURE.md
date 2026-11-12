---
last_mapped_commit: 0938eec730b766f55e975c6539ed7b22a60e3333
last_mapped_at: 2026-11-12
---
# Codebase Structure

**Analysis Date:** 2026-11-12

## Directory Layout

```
repasumi/                                  # repo root (git, branch main)
├── .github/
│   └── workflows/
│       ├── android-debug.yml              # APK debug build + GitHub Release upload
│       └── pages.yml                      # Deploy web/ to GitHub Pages
├── app/                                   # Android application module (Gradle :app)
│   ├── build.gradle                       # applicationId, SDK 36, arm64 ABI, CMake, ML Kit
│   └── src/main/
│       ├── AndroidManifest.xml            # LAUNCHER activity, no INTERNET permission
│       ├── assets/
│       │   └── index.html                 # MIRROR of web/index.html (byte-identical)
│       ├── cpp/                           # Native code (JNI + llama.cpp build)
│       │   ├── CMakeLists.txt             # Builds shared lib repasumi_native
│       │   └── local_llm.cpp              # JNI: load GGUF, tokenize, decode, sample
│       ├── java/com/davidop/estudiaenelbus/
│       │   ├── MainActivity.java          # WebView host, AndroidBridge, onActivityResult
│       │   ├── NativeGguf.java            # GGUF import, prompts, JSON, JNI wrapper
│       │   └── NativeLearningEngine.java  # ML Kit OCR over selected photos
│       └── res/
│           ├── drawable/ic_launcher.xml   # Launcher icon (vector)
│           └── values/
│               ├── strings.xml            # app_name = "Repasumi Beta"
│               └── styles.xml             # AppTheme
├── brand/
│   └── IDENTIDAD_VISUAL.md                # Brand identity notes (Spanish)
├── docs/
│   └── estado.md                          # Current beta status and open items
├── tools/
│   └── verify_source.py                   # Static checks for CI (mirror, strings, node --check)
├── web/                                   # Browser build; deployed as-is to Pages
│   ├── index.html                         # The single-file app (CSS, JS, data, fonts)
│   ├── local-ai.js                        # Browser AI prototype (Tesseract + wllama)
│   └── favicon.svg                        # Site icon
├── .claude/                               # Claude Code local config (excluded from mapping)
├── .planning/                             # GSD planning artifacts (this file lives here)
│   └── codebase/                          # Codebase map documents
├── build.gradle                           # Root: plugin version AGP 8.13.2 (apply false)
├── settings.gradle                        # rootProject.name = 'Repasumi', include ':app'
├── gradle.properties                      # JVM args, useAndroidX=true
└── README.md                              # Public overview, build and privacy notes
```

Not present (created at build or CI time, do not commit):
- `vendor/llama.cpp/` (cloned from `ggml-org/llama.cpp` tag `v0.9.0` by CI; required by `app/src/main/cpp/CMakeLists.txt`)
- `app/build/`, `.gradle/`, `build/`
- A Gradle wrapper (`gradlew`) is not committed. CI uses `gradle/actions/setup-gradle@v4` with Gradle `8.13`.
- A root `.gitignore` is not committed.

## Directory Purposes

**`app/src/main/java/com/davidop/estudiaenelbus/`:**
- Purpose: All Java code for the Android host.
- Contains: Three classes, all `final`, all in one package.
- Key files: `MainActivity.java` (entry), `NativeGguf.java` (model and prompts), `NativeLearningEngine.java` (OCR).
- Note: The package name `estudiaenelbus` is the legacy project name. The UI brand is "Repasumi" (see `app/src/main/res/values/strings.xml`). Keep the package stable because the native JNI symbols are named after it (`Java_com_davidop_estudiaenelbus_NativeGguf_*` in `app/src/main/cpp/local_llm.cpp`).

**`app/src/main/cpp/`:**
- Purpose: Native inference layer.
- Contains: `local_llm.cpp` (one translation unit) and `CMakeLists.txt`.
- Key files: `local_llm.cpp` JNI exports. `CMakeLists.txt` sets `LLAMA_ROOT` to `../../../../vendor/llama.cpp` relative to the cpp dir, which is the repo root `vendor/`.

**`app/src/main/assets/`:**
- Purpose: Packaged HTML the WebView loads from `https://appassets.androidplatform.net/assets/index.html`.
- Contains: Only `index.html`. It must match `web/index.html` exactly.
- Key files: `index.html`.
- Note: `web/local-ai.js` is not copied here. The APK does not include the browser AI prototype.

**`app/src/main/res/`:**
- Purpose: Android resources.
- Contains: `drawable/ic_launcher.xml`, `values/strings.xml`, `values/styles.xml`. No `mipmap`, no layouts, no translations folder.

**`web/`:**
- Purpose: The browser-deployable app. The GitHub Pages workflow uploads this directory as-is.
- Contains: `index.html` (the app), `local-ai.js` (browser AI module, loaded via `import('./local-ai.js')`), `favicon.svg`.
- Key files: `index.html` (about 461 lines, with CSS, base64 fonts, data, and script).

**`tools/`:**
- Purpose: Static checks run in CI before build or deploy.
- Contains: `verify_source.py`. It reads `app/src/main/assets/index.html` and `web/index.html` by relative path, so it must run from the repo root (`python3 tools/verify_source.py`). It writes its JS to `/tmp/repasumi-script-check.js`, which is not portable to Windows.

**`brand/`:**
- Purpose: Brand reference. No code depends on it.
- Contains: `IDENTIDAD_VISUAL.md`. The binary PNG reference is noted there as pending.

**`docs/`:**
- Purpose: Project status. Read by humans; not consumed by build.
- Contains: `estado.md`.

**`.github/workflows/`:**
- Purpose: CI.
- Contains: `android-debug.yml` (triggers on push to `main` and `workflow_dispatch`; runs `verify_source.py`, then `gradle :app:assembleDebug`, uploads the APK artifact, and publishes a prerelease `v0.5.0-preview` on `main`). `pages.yml` (triggers on push to `main` when `web/**` or itself changes).

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java`: Android launcher activity (`onCreate`).
- `app/src/main/AndroidManifest.xml`: Declares the launcher intent for `.MainActivity`.
- `web/index.html`: Browser entry (IIFE starts near line 269).

**Configuration:**
- `app/build.gradle`: `applicationId 'com.davidop.estudiaenelbus.dev'`, `minSdk 26`, `targetSdk 36`, `compileSdk 36`, `versionCode 7`, `versionName '0.5.0-study-preview'`, `abiFilters 'arm64-v8a'`, debug `applicationIdSuffix '.preview'`.
- `build.gradle` (root): AGP `8.13.2`.
- `settings.gradle`: `FAIL_ON_PROJECT_REPOS` for dependency repositories. Add repositories only in `settings.gradle`.
- `gradle.properties`: `-Xmx2048m` for the Gradle daemon, `android.useAndroidX=true`.
- `app/src/main/cpp/CMakeLists.txt`: NDK-side configuration. Turns off `LLAMA_CURL`, `GGML_NATIVE`, `GGML_OPENMP`, and other features for reproducible ARM64 builds. Expects CMake `3.22.1`.
- `app/src/main/AndroidManifest.xml`: `allowBackup="false"`, `usesCleartextTraffic="false"`.
- `app/src/main/res/values/strings.xml`: App label "Repasumi Beta".

**Core Logic:**
- `web/index.html`: Study session logic (`startSession`, `grade`, `planForTopic`, `planQuick`), metrics (`mastery`, `metrics`, `weakest`), rendering (`render`, `study`, `add`, `parents`, `lesson`), persistence (`save`, `getProfile`), and bridge wiring (`webBridge`, `repasumiNativeEvent`).
- `app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java`: WebView configuration and bridge (`AndroidBridge`).
- `app/src/main/java/com/davidop/estudiaenelbus/NativeGguf.java`: `importModel`, `generate`, `generatePractice`, `RULES`, `EXPECTED_SHA256`.
- `app/src/main/java/com/davidop/estudiaenelbus/NativeLearningEngine.java`: `scanImages`, `scanNext`.
- `app/src/main/cpp/local_llm.cpp`: `nativeLoadModel`, `nativeComplete`, `nativeClose`.
- `web/local-ai.js`: `checkModel`, `downloadModel`, `importModel`, `scanPhotos`, `generateFromOCR`, `generatePractice`, `sha256`, `GBNF_LESSON`, `GBNF_PRACTICE`.

**Testing:**
- `tools/verify_source.py`: The only automated check. Static: HTML mirror, required strings, the five question types, and `node --check` on the script.
- No unit tests, instrumentation tests, or test directories exist in the repository.

**Documentation:**
- `README.md`: Public overview, build instructions, privacy and limits, next milestones.
- `docs/estado.md`: Beta status summary.
- `brand/IDENTIDAD_VISUAL.md`: Brand notes.

## Naming Conventions

**Files:**
- Java classes: `PascalCase.java`, one top-level class per file (`MainActivity.java`, `NativeGguf.java`).
- C++ source: `snake_case.cpp` (`local_llm.cpp`).
- Browser JS module: `kebab-case.js` (`local-ai.js`).
- Web entry and mirror: `index.html` (same name in `web/` and `app/src/main/assets/`).
- Documentation: `UPPERCASE_WITH_UNDERSCORES.md` for brand (`IDENTIDAD_VISUAL.md`) and `lowercase.md` for status (`docs/estado.md`). Mapping documents go in `.planning/codebase/` as UPPERCASE.md.
- Workflows: `kebab-case.yml` (`android-debug.yml`, `pages.yml`).
- Scripts: `snake_case.py` (`verify_source.py`).

**Directories:**
- Android source uses the reverse-DNS package path `com/davidop/estudiaenelbus`. Keep this for JNI compatibility.
- Resource folders follow the Android standard (`drawable`, `values`).
- Top-level folders are lowercase single words (`app`, `web`, `docs`, `tools`, `brand`).

**Identifiers (JS):**
- Constants: `UPPER_SNAKE` for module-level configuration (`KEY`, `MAX_IMAGES`, `MODEL_URL`, `EXPECTED_SHA256` in Java).
- Built-in lessons: `EXAMPLE`, `ESO_MATERIA_2`, `PRIMARIA_LENGUA_5`. Built-in ids: `builtin-eso2-materia-v1`, `builtin-primaria5-lengua-u3-v1`. Keep these ids stable, because the startup merge depends on them.
- Native event names are `snake_case` strings (`ocr_progress`, `ai_result`, `practice_error`). Keep them in sync between Java, `local-ai.js`, and `repasumiNativeEvent`.

**Identifiers (Java):**
- Classes: `PascalCase`. Methods: `camelCase`. Constants: `UPPER_SNAKE`.
- JNI-exported native methods are `static native` in `NativeGguf` and named `nativeLoadModel`, `nativeComplete`, `nativeClose`.

**Identifiers (C++):**
- JNI functions use the mangled name `Java_com_davidop_estudiaenelbus_NativeGguf_<method>`. Renaming the Java class or package breaks the library load.

## Where to Add New Code

**New study exercise type (for example, a new question kind):**
- Rendering and grading: `web/index.html` (`lesson()` and `handleClick` cases `choose`, `truth`, `match-*`, `move`, `check-order`). Add the type to the rendering switch and to `verify_source.py`'s `kinds` assertion if it becomes required.
- Prompts: update `RULES`-style prompt text in `app/src/main/java/com/davidop/estudiaenelbus/NativeGguf.java` and the matching prompt in `web/local-ai.js` together.
- Then mirror: copy `web/index.html` to `app/src/main/assets/index.html`.

**New UI screen or tab:**
- Add a tab label in `nav()` and a render function returning an HTML string, then add a branch to `render()` in `web/index.html`.
- Mirror the file to `app/src/main/assets/index.html`.

**New native capability (Android only):**
- Java method on `AndroidBridge` in `app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java` with `@JavascriptInterface`.
- Matching no-op or web fallback in `webBridge()` in `web/index.html`, so `nativeAvailable()` and `webAI()` keep working in the browser.
- If it runs off the UI thread, deliver results via `runOnUiThread` or the `Handler` pattern used in `NativeLearningEngine.emit`.
- Event names must match what `window.repasumiNativeEvent` handles in `web/index.html`.

**New on-device engine logic:**
- Java wrapper: `app/src/main/java/com/davidop/estudiaenelbus/` (new class, following `NativeGguf`'s `Events` callback pattern).
- Native code: `app/src/main/cpp/local_llm.cpp`. Add new JNI exports named after the Java class and method. Update `app/src/main/cpp/CMakeLists.txt` only if you add a new source file.

**New shared JS helpers for the browser build:**
- Put them in `web/` next to `local-ai.js`, and import them dynamically from `web/index.html`. They will be deployed by the Pages workflow. They will not reach the APK unless the asset pipeline is changed to copy them into `app/src/main/assets/`.

**New Android tests:**
- Create `app/src/test/java/com/davidop/estudiaenelbus/` for JVM unit tests or `app/src/androidTest/java/com/davidop/estudiaenelbus/` for instrumented tests. Neither exists yet, and `app/build.gradle` has no test dependencies.

**New static check:**
- Add it to `tools/verify_source.py`. It runs from the repo root in both workflows.

**New asset for the APK:**
- `app/src/main/res/` (drawables, values). Icons are vector XML only, currently `ic_launcher.xml`.

**Model files:**
- Never commit GGUF files, keystores, or user data. The model is downloaded by the user and imported at runtime into app-private storage.

## Special Directories

**`app/src/main/assets/`:**
- Purpose: Packaged WebView content.
- Generated: No, but it is a manual copy of `web/index.html`.
- Committed: Yes. Keep it identical to `web/index.html`.

**`vendor/`:**
- Purpose: llama.cpp source tree for the native build.
- Generated: Yes, by CI (`git clone --depth 1 --branch v0.9.0`) or manually following the README.
- Committed: No. Required by `app/src/main/cpp/CMakeLists.txt` (fails with `FATAL_ERROR` if missing).

**`.planning/`:**
- Purpose: GSD planning and codebase maps.
- Generated: Yes, by GSD commands.
- Committed: Managed by the GSD workflow, not by this document.

**`.claude/`:**
- Purpose: Local Claude Code configuration. Excluded from this map by request.
- Generated: Not applicable.
- Committed: Currently untracked.

**`app/build/`:**
- Purpose: Gradle outputs (APK at `app/build/outputs/apk/debug/app-debug.apk`).
- Generated: Yes.
- Committed: No.

---

*Structure analysis: 2026-11-12*
