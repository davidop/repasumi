---
last_mapped_commit: 0938eec730b766f55e975c6539ed7b22a60e3333
last_mapped_at: 2026-11-12
---
<!-- refreshed: 2026-11-12 -->

# Architecture

**Analysis Date:** 2026-11-12

## System Overview

```text
┌──────────────────────────────────────────────────────────────────────┐
│                 Single-file UI  (HTML + CSS + vanilla JS)            │
│  web/index.html  ==  app/src/main/assets/index.html  (must match)    │
│  Tabs: Estudiar · Añadir · Padres   State: `state` + `store` objects  │
└───────────┬───────────────────────────────────────┬──────────────────┘
            │ Android: globalThis.AndroidBridge     │ Browser: webBridge()
            │ (injected by WebView before page JS)  │ (only if no AndroidBridge
            ▼                                       ▼  and https/http + WASM)
┌──────────────────────────────┐        ┌────────────────────────────────┐
│ MainActivity.AndroidBridge   │        │ web/local-ai.js (prototype)    │
│ `app/src/main/java/.../      │        │ Tesseract.js OCR + wllama      │
│  MainActivity.java`          │        │ (WASM llama.cpp), same events  │
└──────┬───────────────┬───────┘        └────────────────────────────────┘
       │               │
       ▼               ▼
┌──────────────────┐  ┌─────────────────────────────────────────────────┐
│NativeLearningEng.│  │ NativeGguf.java  (single-thread worker executor)│
│ML Kit OCR, RAM   │  │ GGUF import (SAF) · SHA-256 check · prompt build │
│ `NativeLearning  │  │ JSON extraction · JNI calls                     │
│  Engine.java`    │  │ `NativeGguf.java`                               │
└──────────────────┘  └───────────────────────┬─────────────────────────┘
                                              │ JNI (System.loadLibrary)
                                              ▼
                      ┌─────────────────────────────────────────────────┐
                      │ libllama-based shared lib `repasumi_native`     │
                      │ `app/src/main/cpp/local_llm.cpp` + llama.cpp    │
                      │ v0.9.0 built via `app/src/main/cpp/CMakeLists.txt`│
                      └─────────────────────────────────────────────────┘

Persistence (both targets): browser localStorage key `estudia-en-el-bus-v1`
(WebView DOM storage on Android). Model file: app-private `files/repasumi-model.gguf`.
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

**Overall:** Hybrid shell. A static, dependency-free single-page app is hosted either in a locked-down Android WebView or as a static GitHub Pages site. Platform-only capabilities (OCR, GGUF inference, TTS, clipboard, file picking) sit behind a JavaScript bridge object whose presence is the feature switch.

**Key Characteristics:**
- One HTML file contains all CSS, JS, and embedded data. There is no bundler, no framework, no module graph for the core app. Fonts are inlined as base64 `@font-face` data URIs because the WebView blocks external requests.
- Rendering is imperative string templating: `render()` in `web/index.html` rebuilds `#app` innerHTML from `state` on each change. Event handling is one delegated click handler that switches on `data-action`.
- Feature detection replaces configuration: `nativeAvailable()` checks `globalThis.AndroidBridge?.scanPhotos`; `webAI()` checks `.web`; `sampleAvailable()` checks a host-injected `globalThis.sample` API used for an optional cloud-free photo path.
- Native results return to JS through one channel: `window.repasumiNativeEvent(event, payload)`. Event names are `model`, `ocr_progress`, `ocr_result`, `ai_progress`, `ai_result`, `practice_result`, `practice_error`, `error`.
- The Android app is an offline, no-network application: no `INTERNET` permission, no `usesCleartextTraffic`, and `WebView` blocks any URL except the app asset origin.

## Layers

**Presentation (single-file UI):**
- Purpose: Study UI, lesson import, parent dashboard, accessibility controls.
- Location: `web/index.html` (script section starts near line 268; styles in the head).
- Contains: CSS in `<style>`, render functions (`study()`, `add()`, `parents()`, `lesson()`, `cardsPage()`, `donePage()`, `examDonePage()`), delegated handler `handleClick`.
- Depends on: `store`, `state`, `globalThis.AndroidBridge` (optional), `window.repasumiNativeEvent`.
- Used by: The user directly, through the WebView or the browser.

**Domain and persistence (in the same script):**
- Purpose: Topic/question model, spaced-review scheduling, mastery metrics, sessions, flags, learner profiles.
- Location: `web/index.html`, functions `topicStats`, `questionStats`, `mastery`, `weakest`, `planForTopic`, `planQuick`, `grade`, `save`, `getProfile`.
- Contains: Store shape persisted in localStorage key `estudia-en-el-bus-v1`. Built-in lessons are `EXAMPLE`, `ESO_MATERIA_2`, `PRIMARIA_LENGUA_5` inlined as constants and merged into existing installs at startup.
- Depends on: `localStorage` (guarded by `storageOK`; falls back to in-memory when unavailable).
- Used by: Presentation layer only.

**Platform bridge (JS side):**
- Purpose: Adapter that turns the native or browser AI capabilities into the same async method and event contract.
- Location: `web/index.html` `webBridge()` (around line 326) and `repasumiNativeEvent` (around line 454). On Android, `AndroidBridge` is injected by `addJavascriptInterface` in `MainActivity.onCreate`.
- Contains: `copyText`, `speak`, `checkModel`, `importModel`, `scanPhotos`, `generateFromOCR`, `generatePractice`.
- Depends on: Native or browser implementation.
- Used by: `handleClick` and `generateAI`/`beginTopicPractice`.

**Android host (Java):**
- Purpose: Activity lifecycle, WebView setup, asset serving, system integrations, and routing of native events into the WebView.
- Location: `app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java`.
- Contains: `AndroidBridge` inner class, `onActivityResult` routing by request code, `ORIGIN`/`HOME` constants, TTS voice selection.
- Depends on: `NativeLearningEngine`, `NativeGguf`, Android WebKit APIs.
- Used by: Android launcher through the manifest.

**AI and OCR engines (Java):**
- Purpose: Local OCR and GGUF generation with no network.
- Location: `NativeLearningEngine.java` (OCR), `NativeGguf.java` (model file, prompts, JSON parsing, threading).
- Contains: Constants `MAX_IMAGES = 30`, `MAX_TEXT_CHARS = 150_000`, `MIN_BYTES`, `MAX_BYTES`, `EXPECTED_SHA256`, prompt `RULES`.
- Depends on: ML Kit Text Recognition `16.0.1`, JNI library `repasumi_native`.
- Used by: `MainActivity.AndroidBridge` and `onActivityResult`.

**Native inference (C++):**
- Purpose: Load a GGUF file and run greedy-ish sampling to produce text.
- Location: `app/src/main/cpp/local_llm.cpp`.
- Contains: Single `Engine` struct holding model, context, vocab. JNI exports `nativeLoadModel`, `nativeComplete`, `nativeClose` for `com.davidop.estudiaenelbus.NativeGguf`.
- Depends on: llama.cpp `llama.h`, Android `log` and `android` NDK libraries.
- Used by: `NativeGguf` only.

## Data Flow

### Primary Request Path: Study a topic

1. App starts, `store` loads from `localStorage[estudia-en-el-bus-v1]` or `fresh()` (`web/index.html`, near line 296–306).
2. `render()` renders the `study` tab (`web/index.html`, line 427). Topic cards are built from `store.topics`.
3. User taps `data-action="practice"` or `exam` or `quick`. `handleClick` (line 450) dispatches to `startSession(plan, mode)` (line 404) or `beginTopicPractice(id)` (line 369).
4. `lesson()` renders the current interaction. `grade(correct, given)` (line 412) updates `topicStats` / `questionStats`, pushes a session summary into `getProfile().sessions`, and calls `save()`.
5. `save()` writes the full `store` object to `localStorage` (line 308).

### Secondary Flow: Import and generate a lesson on Android

1. `add()` renders the capture card when `nativeAvailable()` is true (line 342).
2. User taps `native-photos`, which calls `AndroidBridge.scanPhotos()` (`MainActivity.java`). `startActivityForResult` with `PICK_PAGES + 1` (833) opens SAF with `EXTRA_ALLOW_MULTIPLE`.
3. `onActivityResult` builds `ArrayList<Uri>` (capped at 30) and calls `NativeLearningEngine.scanImages`.
4. `scanNext` runs ML Kit sequentially. Each page is `"Foto N:\n" + text`, skipping texts under 40 characters. `ocr_result` is emitted with JSON `{text, readable, total}`. The text is kept only in `extracted` (memory).
5. `native-generate` calls `AndroidBridge.generateFromOCR(level)`. `NativeGguf.generate` runs on its single-thread executor, lazily loads the model with `nativeLoadModel`, builds a ChatML prompt (`<|im_start|>` format) from `RULES` plus up to 1200 chars of OCR, and calls `nativeComplete(handle, prompt, 550)`.
6. The Java side extracts the first `{` to last `}`, parses with `org.json`, checks that exactly 2 questions were returned, and emits `ai_result`.
7. `window.repasumiNativeEvent` puts the JSON in `state.nativeJSON`. The user reviews it in a textarea and taps `native-import`. Only then is the lesson pushed into `store.topics` and saved.

### Tertiary Flow: Native event return path

1. `NativeGguf` and `NativeLearningEngine` take an `Events` callback in `MainActivity`.
2. For `NativeGguf`, the callback calls `runOnUiThread` then `web.evaluateJavascript("window.repasumiNativeEvent&&window.repasumiNativeEvent(<event>,<payload>)", null)`. Payloads are encoded with `JSONObject.quote`.
3. For `NativeLearningEngine`, `emit` posts to the main `Handler` and then performs the same `evaluateJavascript` call.

### Browser-only flow (GitHub Pages or local HTTPS)

1. `web/index.html` detects no `AndroidBridge`, `http(s)` protocol, and WebAssembly. It installs `webBridge()` on `globalThis.AndroidBridge` (line 327). Methods are forwarded to `web/local-ai.js` via dynamic `import()`.
2. `local-ai.js` uses Tesseract.js (`spa`) for OCR and wllama 3.8.1 for generation, with a GBNF grammar (`GBNF_LESSON`, `GBNF_PRACTICE`) that constrains the JSON shape.
3. The model is cached through wllama's `ModelManager` / `CacheManager` in browser storage. Its SHA-256 is verified in JS (`sha256()` in `local-ai.js`) on import, and the `FLAG` localStorage key remembers availability.

**State Management:**
- UI state lives in a single `state` object in `web/index.html` (line ~312). It is not persisted, except through `store`.
- Durable state lives in one `store` object persisted as a whole to `localStorage`. There is no partial write path.
- Native state: a single model handle in `NativeGguf.handle` (a `long` pointer) guarded by the single-thread executor. OCR results live in `NativeLearningEngine.extracted`.

## Key Abstractions

**Topic (lesson):**
- Purpose: A unit of study with `title`, `emoji`, `summary[]`, `questions[]`, `id`, `createdAt`.
- Examples: `EXAMPLE`, `ESO_MATERIA_2`, `PRIMARIA_LENGUA_5` in `web/index.html`; generated objects from `ai_result`.
- Pattern: Plain JSON. The question schema uses `type` in `mc`, `tf`, `fill`, `match`, `order`, with `q`, `a`, `opts`, `pairs`, `items`, `why`, `src`. `verify_source.py` checks all five types are present.

**Question:**
- Purpose: One exercise. Its per-learner stats live in `profiles[learnerId].topics[topicId].questions[index]` as `{seen, firstCorrect, wrong}`.
- Examples: Rendering in `lesson()` at `web/index.html`; flags keyed as `"<topicId>::<index>"`.
- Pattern: Index-based identity, which means reordering `questions` breaks progress and flags.

**Learner profile:**
- Purpose: Per-child metrics and flags. Profiles are keyed by learner id under `store.profiles`.
- Examples: `getProfile()` in `web/index.html` (line ~315).
- Pattern: Lazy-initialized nested objects with `??=` defaults at each access.

**Bridge contract (`AndroidBridge` ↔ `webBridge`):**
- Purpose: One API surface with two implementations.
- Examples: `MainActivity.AndroidBridge` in `MainActivity.java` and `webBridge()` in `web/index.html`.
- Pattern: Methods are fire-and-forget. Results return as `repasumiNativeEvent` calls.

**Native engine (JNI handle):**
- Purpose: Owns a `llama_model`, `llama_context`, and vocab.
- Examples: `Engine` in `app/src/main/cpp/local_llm.cpp`.
- Pattern: Opaque `jlong` handle created by `nativeLoadModel`, freed by `nativeClose`. Only one instance is held per `NativeGguf`.

## Entry Points

**Android launcher activity:**
- Location: `app/src/main/java/com/davidop/estudiaenelbus/MainActivity.java` (`onCreate`), declared in `app/src/main/AndroidManifest.xml`.
- Triggers: App launch from the launcher (`MAIN` / `LAUNCHER`).
- Responsibilities: Build the WebView, register `AndroidBridge`, load `https://appassets.androidplatform.net/assets/index.html`, start TTS, wire native engines.

**Browser entry:**
- Location: `web/index.html` (served by GitHub Pages from the `web/` artifact, see `.github/workflows/pages.yml`).
- Triggers: Page load. Immediately runs the IIFE, reads `localStorage`, calls `render()`.
- Responsibilities: Same as Android UI, plus installs `webBridge()` if the page is served over http/https.

**Native library load:**
- Location: `NativeGguf` static initializer (`System.loadLibrary("repasumi_native")`) and `NativeLearningEngine` (no native library; uses ML Kit).
- Triggers: First class use.
- Responsibilities: On failure `libraryLoaded = false`, and `check()` emits `model=UNAVAILABLE`.

**Static verification:**
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

**What happens:** `app/src/main/assets/index.html` is a copy of `web/index.html`. A developer must copy the file after each change.
**Why it's wrong:** Any missed copy causes the APK to diverge from the Pages site. The only guard is the byte-equality check in `tools/verify_source.py`, which runs in CI after the APK build starts, not before commit.
**Do this instead:** Edit `web/index.html`, then copy it to `app/src/main/assets/index.html`, and run `python3 tools/verify_source.py` locally before pushing. Or add a Gradle `Copy` task that sources from `web/`. Do not change one copy alone.

### Prompt and parser logic duplicated across Java and JS

**What happens:** `RULES`, the ChatML prompt body, and the JSON extraction logic are written twice: `NativeGguf.java` and `web/local-ai.js` (`generateFromOCR`, `generatePractice`, `extractJson`). They have already drifted: the JS version uses `max_tokens` 700 and a GBNF grammar (`GBNF_LESSON`, `GBNF_PRACTICE`), while Java uses 550 with no grammar.
**Why it's wrong:** The same lesson produced on Android and in the browser can differ in output shape and length. The Java `generate` also has a `for (int batch = 0; batch < 1; batch++)` loop with a `switch` for batch types 1 to 6 that can never run. The README describes "7 bloques de 2 preguntas", which the code does not implement.
**Do this instead:** Treat the Java `NativeGguf.generate` as the only producer of the Android prompt. When changing prompts, change both files together and note the divergence in `docs/estado.md`. Prefer removing the dead `batch` loop so the code matches the single-block behaviour.

### Index-based question identity

**What happens:** Progress (`questions[index]`) and flags (`qKey(id, index)`) identify questions by array position.
**Why it's wrong:** Editing, reordering, or deleting a question silently reassigns its stats and flags to a different question. `delete-topic` clears flags by prefix, but an in-place edit does not.
**Do this instead:** Give each question a stable `id` when a topic is created or imported, and key stats and flags by that id. Keep index-based keys only for built-in lessons that are never edited.

### Stale comments that contradict behaviour

**What happens:** `NativeLearningEngine.java` describes "OCR y Gemini Nano" and AICore, but the class only runs ML Kit. `web/index.html` line 353 says "Máximo 6 imágenes" for the browser photo path, while the Android path allows 30. `docs/estado.md` correctly says Gemini Nano is not implemented.
**Why it's wrong:** Future work may assume a capability that is absent, or a limit that does not apply.
**Do this instead:** Update the comments when behaviour changes, and treat `docs/estado.md` as the status reference.

## Error Handling

**Strategy:** Errors are converted to user-facing Spanish strings and delivered through the same event channel as success results. Native code throws `IllegalStateException`, which becomes `error` or `practice_error` events. JS catches the rest and shows them in `state.nativeError` or with `toast`.

**Patterns:**
- Native exceptions map to Spanish messages in `NativeGguf.java` (for example "El modelo no coincide con el GGUF Qwen verificado (SHA-256).").
- `local_llm.cpp` throws via `fail(env, msg)` into `IllegalStateException`, not via return codes. Failed model load returns `0`, which Java converts to an exception.
- In JS, `JSON.parse` failures become a visible error. The README states that invalid JSON fails visibly and requires human review before saving.
- Local storage failure sets `storageOK = false` and keeps the app running in memory with a warning (`notice()`).

## Cross-Cutting Concerns

**Logging:** Native code uses `__android_log` headers but does not log in the current source. Java has no logger. `local-ai.js` writes `console.info` and `console.warn` with the `[Repasumi IA web]` prefix. The app does not log OCR text or questions, by design.
**Validation:** Question JSON is validated by count (`questions.length === 2`) and by `optString`/`getJSONArray` access. Full schema validation is not performed in Java. The GBNF grammar in `local-ai.js` is the only structural constraint. Image size and count are checked in both `MainActivity` (30) and `NativeLearningEngine` (1–30).
**Authentication:** None. No accounts. Learner profiles are local names only (`add-student` action in `handleClick`, `web/index.html`).
**Accessibility:** Implemented in the UI: `aria-*` attributes, `role="status"` and `role="progressbar"`, `prefers-reduced-motion`, a large-text toggle (`store.largeText`), and TTS through `AndroidBridge.speak` with a fallback to `speechSynthesis`.
**Security boundary:** WebView settings: JavaScript on, `setAllowFileAccess(false)`, `setAllowContentAccess(false)`, `setJavaScriptCanOpenWindowsAutomatically(false)`, `MIXED_CONTENT_NEVER_ALLOW`, permission requests denied. Only `AndroidBridge` is exposed to page JS.

---

*Architecture analysis: 2026-11-12*
