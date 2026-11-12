---
last_mapped_commit: 0938eec730b766f55e975c6539ed7b22a60e3333
last_mapped_at: 2026-11-12
---
# Testing Patterns

**Analysis Date:** 2026-11-12

## Test Framework

**Runner:**
- No unit or instrumented test framework is configured. There is no JUnit, Robolectric, AndroidX Test, Espresso, Jest, Vitest, Playwright test config, or pytest suite in the tracked files.
- No test directories exist: `app/src/test/` and `app/src/androidTest/` are absent.
- The only automated checks are a static Python script (`tools/verify_source.py`) and the Gradle build (`:app:assembleDebug`) in CI.
- Config: none for tests. The relevant build files are `build.gradle`, `settings.gradle`, `app/build.gradle`, and `app/src/main/cpp/CMakeLists.txt`.

**Assertion Library:**
- Python `assert` statements in `tools/verify_source.py`. No other assertion library is in use.

**Run Commands:**

```bash

# Static checks (run from repository root: paths are relative)

python3 tools/verify_source.py      # Checks HTML identity, required strings, and node --check of the inline script

# Android build (CI runs this; requires vendor/llama.cpp v0.9.0 and NDK 27.2.12479018 and CMake 3.22.1)

gradle --no-daemon --stacktrace :app:assembleDebug

# Syntax check of the web module (manual; not wired into CI)

node --check web/local-ai.js        # ES module syntax check only; does not execute code
```

- No watch mode, coverage command, or test-runner command exists.
- The CI workflow `.github/workflows/android-debug.yml` runs `python3 tools/verify_source.py` before the Gradle build, and `.github/workflows/pages.yml` runs it before deploying `web/`.

## Static Verification (`tools/verify_source.py`)

The script is the de facto test suite. Its header says: "Comprobaciones estáticas; no sustituyen las pruebas en Android real." (static checks; they do not replace testing on real Android).

What it asserts, in order:
1. `app/src/main/assets/index.html` equals `web/index.html` byte-for-byte (`assert html == web`). Any UI change must be copied to both paths.
2. Required content is present: the sample title `'Relieve y ríos de España (ejemplo)'`, `<title>Repasumi</title>`, the tagline `'Del libro al reto'`, the `copy-report` element id and `data-action="flag"` attribute.
3. All five question types are present as literal `type:'<kind>'` strings for `mc`, `tf`, `fill`, `match`, `order`.
4. The HTML contains a `<script>` block, and the JavaScript is extracted with `split('<script>', 1)` and `split('</script>', 1)`.
5. The extracted JavaScript passes `node --check` (syntax only).

Pattern for extending it: add one `assert` per invariant that must never break in the shipped HTML. Keep the checks string-based and cheap. Example:

```python
assert 'some-required-id' in html, 'Falta el elemento some-required-id.'
```

Messages are Spanish, matching the rest of the project.

**Known limits of this script (do not rely on it for behavior):**
- It writes the extracted script to a hard-coded `/tmp/repasumi-script-check.js` (`tools/verify_source.py`, line 16). That path works on the Ubuntu CI runner but not on Windows. On Windows, set up a local path or run through WSL/Git Bash.
- It only checks the first `<script>` block (`split(..., 1)`). A second inline script would not be syntax-checked.
- `web/local-ai.js` is not part of the `node --check` step. It is an ES module and is checked only by the browser at runtime, or manually with `node --check`.
- It does not check the Android Java/C++ code.

## Test File Organization

**Location:**
- No test files exist. If unit tests are added, the standard Android locations are `app/src/test/java/com/davidop/estudiaenelbus/` (JVM unit tests) and `app/src/androidTest/java/com/davidop/estudiaenelbus/` (instrumented tests).
- For the web module, a `web/tests/` directory is the natural location, but note that `.github/workflows/pages.yml` uploads the whole `web/` folder as the Pages artifact. Keep test files out of `web/` or exclude them in the workflow.

**Naming:**
- None established. Use the Android convention `<ClassName>Test.java` (for example `NativeGguf` → `NativeGgufTest.java`).

**Structure:**

```
app/src/test/java/com/davidop/estudiaenelbus/      (proposed: JVM unit tests)
app/src/androidTest/java/com/davidop/estudiaenelbus/ (proposed: device tests)
tools/verify_source.py                              (existing: static checks)
```

## Test Structure

**Current pattern (static script):**

```python
html = Path('app/src/main/assets/index.html').read_text(encoding='utf-8')
web = Path('web/index.html').read_text(encoding='utf-8')
assert html == web, 'Los HTML de Android y Web no coinciden.'
```

Read files with explicit `encoding='utf-8'`. Keep the script linear, with no test classes and no fixtures.

**Patterns for any new test:**
- Setup: none exists. Construct objects directly, and pass in test doubles for the `Events` callback interfaces (`NativeGguf.Events`, `NativeLearningEngine.Events`), which are single-method and easy to lambda-stub.
- Teardown: `NativeGguf.close()` and `NativeLearningEngine.close()` release native and worker resources. Call them in test teardown.
- Assertion style: compare the emitted `(event, payload)` pairs, for example `"error"` with a Spanish message, or `"model"` with `AVAILABLE` / `IMPORT_REQUIRED` / `UNAVAILABLE`.

## Mocking

**Framework:** None configured. No Mockito, MockK, or sinon/jest mocks are present.

**Seams that are already available without a framework:**
- `NativeGguf.Events` and `NativeLearningEngine.Events` are functional interfaces. Pass a lambda that records events into a list.
- `NativeGguf.check()` reports `UNAVAILABLE` when `System.loadLibrary("repasumi_native")` fails (`libraryLoaded` static flag). This path can be tested on the JVM without the native library.
- `NativeGguf` reads the model from `context.getFilesDir()`. Use a context whose files dir points to a temp folder.
- In the web module, `globalThis.repasumiNativeEvent` is the event sink, and `globalThis.AndroidBridge` can be replaced by a stub object in tests. `web/index.html` only installs `webBridge()` when the bridge is absent and the page is served over `http(s)` with WebAssembly support (`if(!globalThis.AndroidBridge&&...)`).
- `web/local-ai.js` exports `_sha256ForTests`. Use it to verify the incremental SHA-256 against known digests, for example with a small `Blob`. No test consumes this export yet.

**What to Mock:**
- Android `Context`, `Uri`, and `ContentResolver` for `NativeGguf.importModel`.
- The `TextRecognizer` from ML Kit for `NativeLearningEngine` (it is created inside the class, so an injected factory is needed to mock it cleanly; currently it is created with `TextRecognition.getClient(...)` in the field initializer).
- The `wllama` library and `ModelManager` / `CacheManager` in `web/local-ai.js`. These come from a CDN import (`WLLAMA`), so they must be stubbed by replacing the module in tests, not by global monkeypatching.
- `navigator.storage`, `localStorage`, and `createImageBitmap` for web code.

**What NOT to Mock:**
- The SHA-256 verification path: test it with real bytes. The integrity check is the security property (`EXPECTED_SHA256` in `NativeGguf.java`, `MODEL_SHA256` in `web/local-ai.js`).
- The JSON extraction and validation (`extractJson`, the `question count != 2` checks). Test with real model-like outputs, including truncated and fenced outputs.
- The Spanish user-visible error strings. They are part of the contract, so assert the text.

## Fixtures and Factories

**Test Data:**
- No fixture files exist. The only sample content is embedded in `web/index.html`: the `EXAMPLE` topic about Spanish relief and rivers, plus `PRIMARIA_LENGUA_5` and `ESO_MATERIA_2` built-ins (`web/index.html`, lines 268-291). The static check asserts that the example title `'Relieve y ríos de España (ejemplo)'` exists.
- Question objects follow a fixed shape. Use these as factories:

```javascript
{type:'mc', q:'¿...?', a:'correct', opts:['correct','other','other'], why:'...', src:'Foto 1'}
{type:'tf', q:'...', a:true, why:'...', src:'Foto 1'}
{type:'match', q:'...', pairs:[['left','right'],['left','right']], why:'...', src:'...'}
{type:'order', q:'...', items:['first','second','third'], why:'...', src:'...'}
```

- The AI output contract is `{title, emoji, summary[4-6], questions[2]}` for lessons and `{questions[2]}` for practice (`NativeGguf.java`, `web/local-ai.js`).

**Location:**
- Built-in content lives inline in `web/index.html`. If fixtures are added later, store them under a `tests/fixtures/` directory, and do not put real textbook photos or OCR text in the repository (`docs/estado.md` forbids book photos and real children's data).

## Coverage

**Requirements:** None enforced. No JaCoCo, Istanbul/c8, or coverage threshold is configured in CI.

**View Coverage:**

```bash

# Not configured. Add a tool before quoting any coverage figure.

```

## Test Types

**Static checks:**
- Implemented: `tools/verify_source.py`, run in both CI workflows (`.github/workflows/android-debug.yml`, `.github/workflows/pages.yml`).

**Unit Tests:**
- Not present. The best candidates, in priority order, are:
  1. `NativeGguf` validation logic: the size bounds (`MIN_BYTES`, `MAX_BYTES`), the SHA-256 comparison, and the JSON-block extraction in `generate` and `generatePractice`.
  2. `NativeLearningEngine.scanNext` text assembly: the 40-character minimum per photo, the 100-character minimum for the result, and the `MAX_TEXT_CHARS` cutoff.
  3. `web/index.html` pure functions: `shuffle`, `planForTopic`, `smartPriority`, `mastery`, `streak`, `localDay`, `escapeHtml`. These are inside an IIFE, so they need extraction into a module or a test-only export before they can be imported.
  4. `web/local-ai.js` `extractJson`, `sha256` (through `_sha256ForTests`), and `summaryLine`.

**Integration Tests:**
- Not present. Native inference (`local_llm.cpp`) requires a device with the GGUF model. Test on device only.

**E2E Tests:**
- Not used. The project's own docs list "Diseñar pruebas automatizadas de OCR y calidad factual" as a future milestone (`README.md`, "Próximos hitos").

**Manual device verification (current practice):**
- Install the debug APK from the CI artifact `repasumi-beta-debug-apk` (30-day retention) or the GitHub prerelease `v0.5.0-preview`.
- The README documents the device test plan: OCR with real photos, GGUF import on a Xiaomi 12T, latency and memory measurements, and manual review of JSON validity and facts.
- The browser prototype is verified by serving `web/` over HTTPS (the README notes that local-AI needs HTTPS). Chrome or Edge with WebGPU or WebAssembly is the reference.

## Common Patterns

**Verifying the web and Android copies stay in sync:**

```bash
cp web/index.html app/src/main/assets/index.html   # Copy, then
python3 tools/verify_source.py                      # Must pass before commit
```

**Error-path testing (Java, pattern to follow):**

```java
// Native library absent: check() must emit UNAVAILABLE, not crash.
List<String> seen = new ArrayList<>();
NativeGguf gguf = new NativeGguf(context, (event, payload) -> seen.add(event + ":" + payload));
gguf.check();
// Wait for the single-thread worker, then assert the emitted status.
```

**Async testing (web, pattern to follow):**

```javascript
// Stub the bridge, then assert the emitted events in order.
const events = [];
globalThis.repasumiNativeEvent = (event, payload) => events.push([event, payload]);
await generateFromOCR('Primaria');
// Expect 'error' when no OCR has run: 'Primero realiza el OCR de las fotografías.'
```

Note that `NativeGguf` and `NativeLearningEngine` run work on background threads and report through callbacks. Tests must wait for the worker (for example with a `CountDownLatch`) before asserting. The web functions are `async`, so `await` them.

**Error testing (static, pattern used today):**
- Errors are asserted by checking that a string is present in the source (`assert ... in html`). This is a weak check. Prefer behavior tests when the logic can be exercised.

## Recommended Next Steps for Testing

- Add `app/src/test/` JVM tests for `NativeGguf` validation and `NativeLearningEngine` text assembly. These require only the Android unit-test dependencies and no device.
- Make the `/tmp` path in `tools/verify_source.py` portable, for example with `tempfile.gettempdir()`.
- Extend `tools/verify_source.py` to run `node --check web/local-ai.js` alongside the inline script check.
- Add a test-only export or extraction for the pure helpers in `web/index.html`, so they can run under Node without a browser.
- Document in `README.md` which checks run locally versus in CI, because today the only local command is `python3 tools/verify_source.py`.

---

*Testing analysis: 2026-11-12*
