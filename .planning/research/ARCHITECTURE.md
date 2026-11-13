# Architecture Research

**Domain:** Hybrid Android study app (Java host + WebView single-file vanilla JS SPA + llama.cpp over JNI), fully offline, Google Play Families
**Researched:** 2026-11-13
**Milestone:** 1.0 · Google Play (subsequent milestone on top of v0.5.0-study-preview, commit 0938eec)
**Confidence:** MEDIUM-HIGH overall. Codebase facts are HIGH: I read `web/index.html`, `MainActivity.java`, `NativeGguf.java`, `local_llm.cpp`, `tools/verify_source.py` and the workflows directly. The Play Asset Delivery facts come from official Android and Play docs that I fetched; the research seam rates `webfetch` as LOW, but the content is first-party. The llama.cpp abort-callback and WebView localStorage flush details are LOW-MEDIUM: they come from secondary or old sources and need checking against the pinned llama.cpp tag.

---

## Standard Architecture

### Guiding decision

Keep the stack: Java Activity + WebView + one HTML file + llama.cpp JNI. Make three seams explicit so each launch feature has one clear owner:

1. **Core / UI seam inside the single HTML file.** All domain logic is pure and has no DOM access. It covers schema and migrations, validation, source validation, chunk planning, SRS, review selectors and PIN hashing. It lives in its own `<script id="repasumi-core">` block that Node can load and test with no build step and no dependencies. The UI block consumes it.
2. **Bridge contract seam (JS ↔ Java).** One versioned contract with job-scoped events and a few synchronous snapshot getters. A fake bridge for Playwright implements the same contract.
3. **Process-scoped native runtime seam (Java).** A `ModelRuntime` that belongs to the `Application`, not the `Activity`. It owns the model file, the engine handle, the single in-flight generation job, cancellation and staged progress. The Activity only attaches and detaches an event sink.

### System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  web/index.html  ==  app/src/main/assets/index.html   (byte-identical)      │
├─────────────────────────────────────────────────────────────────────────────┤
│ <script id="repasumi-core">  globalThis.RepasumiCore  (PURE, Node-testable) │
│  ┌──────────┐ ┌───────────┐ ┌────────────┐ ┌──────────┐ ┌──────────────┐    │
│  │ schema + │ │ validate  │ │ chunk plan │ │ SRS /    │ │ review        │   │
│  │migrations│ │ Question/ │ │ + source   │ │ grading  │ │ selectors +   │   │
│  │ (v1→v2)  │ │ Topic     │ │ validation │ │ (uid)    │ │ PIN helpers   │   │
│  └──────────┘ └───────────┘ └────────────┘ └──────────┘ └──────────────┘    │
├─────────────────────────────────────────────────────────────────────────────┤
│ <script> UI block (DOM, imperative render, one dispatcher)                  │
│  ┌──────────────┐ ┌──────────────┐ ┌───────────────┐ ┌──────────────────┐   │
│  │ StoreService │ │ dispatch()   │ │ render() +    │ │ NativeClient     │   │
│  │ boot/save/   │ │ + ParentGate │ │ patchProgress │ │ (bridge adapter, │   │
│  │ recovery     │ │ (GATED set)  │ │ (no re-render │ │  event router,   │   │
│  │ (adapter)    │ │              │ │  on progress) │ │  snapshot sync)  │   │
│  └──────┬───────┘ └──────────────┘ └───────────────┘ └────────┬─────────┘   │
└─────────┼────────────────────────────────────────────────────┼─────────────┘
          │ localStorage (WebView DOM storage)                  │ AndroidBridge (sync getters,
          ▼                                                     │ fire-and-forget commands)
   ┌──────────────────────┐                                     │ ▲ window.repasumiNativeEvent
   │ KEY / KEY.bak /      │                                     ▼ │ (job-scoped events)
   │ KEY.corrupt.<ts>     │        ┌────────────────────────────────────────────────┐
   └──────────────────────┘        │ MainActivity (thin): WebView host, SAF pickers,│
                                   │ TTS, clipboard, attaches EventSink, onPause    │
                                   │ → 'app_paused', configChanges (no recreate)    │
                                   └───────┬───────────────────────┬────────────────┘
                                           │                       │
                     ┌─────────────────────▼───┐   ┌───────────────▼──────────────────┐
                     │ NativeLearningEngine    │   │ RepasumiApp (Application)         │
                     │ (OCR, ML Kit; app ctx;  │   │  └─ ModelRuntime (process-scoped) │
                     │ downsample; pages[])    │   │     ├─ ModelLocator → file path   │
                     └─────────────────────────┘   │     │   (install-time pack copy   │
                                                   │     │    + SHA-256; DEBUG: SAF)   │
                                                   │     ├─ GenerationJob (single-     │
                                                   │     │   flight, cancel flag,      │
                                                   │     │   chunk results buffer)     │
                                                   │     ├─ PromptBuilder (pure Java)  │
                                                   │     └─ inference executor (1 thr) │
                                                   └───────────────┬──────────────────┘
                                                                   │ JNI
                                                   ┌───────────────▼──────────────────┐
                                                   │ local_llm.cpp: load / complete    │
                                                   │ (TokenCallback → continue? bool,  │
                                                   │  optional GBNF grammar) / close   │
                                                   └──────────────────────────────────┘
   :model_pack (install-time asset pack) ──AssetManager stream──▶ ModelLocator (one-time copy)
   site/ (landing + privacidad) ──▶ GitHub Pages   (web/ is no longer published)
```

### Component Responsibilities

| Component | Owns | Implementation | New / Modified |
|-----------|------|----------------|----------------|
| `RepasumiCore` (core script block) | Store schema constants, `migrate()`, `validateTopic/validateQuestion`, `validateSource`, `planChunks`, SRS/grading by `uid`, review selectors (`studyableTopics`, `studyableQuestions`), PIN hash/verify/lockout math, `mergeChunkResult` (idempotent) | Pure functions in an IIFE that assigns `globalThis.RepasumiCore`. No `document`, no `localStorage`, no `Date.now()` (time is passed in as `now`), no `AndroidBridge` | New, extracted from the current IIFE |
| `StoreService` (UI block) | Boot sequence (read → parse → back up corrupt data → migrate → seed sync → save), debounced save, write-error surfacing, recovery banner | Wraps a `StorageAdapter {read(k), write(k,v), remove(k)}`. The 1.0 adapter is `localStorage`. Tests use an in-memory map | Modified (replaces lines 298–308 and `save()`) |
| `dispatch(action, data)` + `ParentGate` | Every user action goes through one function. The gate intercepts actions in the `GATED` set, asks for the PIN, then replays the intent | Refactor `handleClick(e)` into `handleClick → dispatch(a, {...btn.dataset})` | Modified + New |
| Review editor (UI) | Per-question edit/approve/discard, publish topic, badge for pending practice questions | Inputs are bound with `data-draft="<uid>.<field>"` and write straight into the store. `input` never calls `render()` | New (replaces the `#native-json` textarea flow) |
| `NativeClient` (UI) | Wraps `AndroidBridge` calls, routes events by `jobId`, calls `snapshot()` on boot to reconcile, `patchProgress()` for stage and token events | Small adapter over `window.repasumiNativeEvent` | Modified (replaces the monolithic handler at line 454) |
| `MainActivity` | WebView host and hardening, SAF pickers, TTS, clipboard, attaches/detaches `EventSink`, emits `app_paused`, keep-screen-on while a job runs | Stays thin. No model lifetime here. `onDestroy` detaches the sink and sets `web = null` | Modified |
| `RepasumiApp` / `ModelRuntime` | Model location and verification, engine handle, single-flight `GenerationJob`, cancellation, stage events, chunk-result buffer, `onTrimMemory` unload when idle | Process singleton created in `Application.onCreate`. One inference thread (keeps the existing "handle is touched only on the worker" invariant) plus one IO thread for copying and hashing | New (absorbs most of `NativeGguf`) |
| `ModelLocator` | Turns "the model" into an absolute file path that llama.cpp can mmap | `InstallTimePackLocator` (streams from `AssetManager` to `filesDir`, hashing while it copies, writes a verified marker). `DebugSafImportLocator` only under `BuildConfig.DEBUG` | New |
| `PromptBuilder` / `JsonExtractor` | ChatML wrapping, OCR sanitisation (`<|`, `|>`), per-chunk type rotation, optional GBNF with the allowed `Foto N` values, first-`{`-to-last-`}` extraction | Pure Java with no Android imports, so JUnit can test it on the JVM | New (extracted from `NativeGguf.generate`) |
| `local_llm.cpp` | Load, complete with a per-token Java callback that returns `continue?`, optional grammar sampler, close | Same `Engine` struct. Adds a `jobject` callback and an optional grammar string | Modified |
| `NativeLearningEngine` | OCR of 1–30 photos into structured `pages[{n, text, readable}]` | Uses the application context. Downsamples to about 2400 px. `try/finally` resets `busy` | Modified |
| `:model_pack` | Ships the GGUF through Play | Gradle `com.android.asset-pack`, `deliveryType = "install-time"` | New module |
| `site/` | Public landing page and privacy policy only | Static HTML. `pages.yml` publishes `site/` | New; `web/` is no longer published |

---

## Recommended Project Structure

```
repasumi/
├── web/
│   └── index.html              # CANONICAL SPA source (no longer published). Two script blocks:
│                               #   <script id="repasumi-core"> pure domain  +  <script> UI
├── app/src/main/assets/
│   └── index.html              # byte-identical copy (tools/sync_assets.py + verify in CI)
├── app/src/main/java/com/davidop/estudiaenelbus/   # keep the package (see Anti-Pattern 9)
│   ├── RepasumiApp.java        # Application: creates ModelRuntime, onTrimMemory
│   ├── MainActivity.java       # thin host + AndroidBridge (contract v2)
│   ├── NativeLearningEngine.java
│   ├── runtime/
│   │   ├── ModelRuntime.java   # process-scoped: job, cancel, stages, sink
│   │   ├── GenerationJob.java  # id, status, chunk index, results buffer, AtomicBoolean cancel
│   │   ├── ModelLocator.java   # interface + InstallTimePackLocator + DebugSafImportLocator
│   │   └── ModelVerifier.java  # streaming SHA-256 + marker {sha,size,mtime,versionCode}
│   └── llm/
│       ├── LlmEngine.java      # JNI declarations (native methods; R8 keep rules)
│       ├── PromptBuilder.java  # pure Java, JUnit-tested
│       └── JsonExtractor.java  # pure Java, JUnit-tested
├── app/src/test/java/...       # JVM unit tests (PromptBuilder, JsonExtractor, ModelVerifier marker logic)
├── app/proguard-rules.pro      # keep @JavascriptInterface methods, native methods, JNI callback
├── model_pack/
│   ├── build.gradle            # plugins { id 'com.android.asset-pack' }  deliveryType install-time
│   └── src/main/assets/qwen2.5-0.5b-instruct-q4_k_m.gguf   # NOT committed; CI downloads + hash-checks
├── site/
│   ├── index.html              # landing
│   └── privacidad/index.html   # privacy policy (stable URL for the Play listing)
├── tests/
│   ├── core/*.test.mjs         # node --test: loads <script id="repasumi-core"> via vm
│   ├── fixtures/store-v1-*.json# 0.3 / 0.4 / 0.5 stores, corrupt blobs
│   └── e2e/smoke.spec.mjs      # Playwright + FakeAndroidBridge (dev-only dependency)
├── tools/
│   ├── verify_source.py        # tempfile (not /tmp), checks both blocks + equality
│   └── sync_assets.py          # copies web/index.html → app/src/main/assets/index.html
└── docs/BRIDGE.md              # the JS↔Java contract (methods, events, payload shapes, version)
```

### Structure Rationale

- **Two script blocks, one file:** this makes the code testable without breaking the byte-identical rule and without adding a bundler. The extraction pattern already exists: `verify_source.py` splits on `<script>`. A test harness extracts the block by its `id` and runs it in `vm.runInNewContext` with `crypto` and `structuredClone` from Node 20+. Splitting into ES modules with a build-time inliner would turn `index.html` into generated output and add a build step in week 1. That is not worth it for 1.0.
- **`web/` stays canonical even though Pages stops publishing it:** Playwright needs to serve the SPA, and the existing verifier and habits already point there. Pages moves to `site/`, so the published surface is only the landing page and the privacy page.
- **`runtime/` vs `llm/`:** the code that touches the Android lifecycle (`ModelRuntime`, `ModelLocator`) is separate from pure logic (`PromptBuilder`, `JsonExtractor`) that can be tested on the JVM without an emulator. CI runners are x86_64 and the native library is arm64-only, so JNI cannot run in CI anyway.
- **`model_pack/` as its own Gradle module:** this is what Play Asset Delivery requires. The GGUF never enters git. CI fetches it from the pinned Hugging Face revision and checks it against `EXPECTED_SHA256` before `bundleRelease`.

---

## Architectural Patterns

### Pattern 1: Pure core block + thin UI block (testability without a build step)

**What:** Split the current single IIFE into `RepasumiCore` (pure) and the UI IIFE. The core receives time and randomness as parameters: `ctx = {now, newId}`.
**When to use:** For every function that the issue #13 minimum tests list: grading, SRS, migrations, validation, source validation, chunk planning, PIN logic.
**Trade-offs:** + no tooling, byte-identical copies are kept, Node built-ins only. − Still one big file. Merge conflicts in parallel worktrees stay a risk until the script is reformatted (see Anti-Pattern 8).

**Example:**
```html
<script id="repasumi-core">
globalThis.RepasumiCore = (() => { 'use strict';
  const SCHEMA_VERSION = 2;
  function migrate(raw, ctx) { /* see Pattern 2 */ }
  function studyableQuestions(topic) {
    return topic.status === 'published' ? topic.questions.filter(q => q.review === 'approved') : [];
  }
  return Object.freeze({ SCHEMA_VERSION, migrate, studyableQuestions /* … */ });
})();
</script>
<script>
(() => { 'use strict'; const C = globalThis.RepasumiCore; /* DOM, store, bridge, render */ })();
</script>
```
```js
// tests/core/harness.mjs
import { readFileSync } from 'node:fs'; import vm from 'node:vm';
const html = readFileSync('web/index.html', 'utf8');
const src = html.match(/<script id="repasumi-core">([\s\S]*?)<\/script>/)[1];
const ctx = vm.createContext({ crypto: globalThis.crypto, structuredClone });
vm.runInContext(src, ctx);
export const Core = ctx.RepasumiCore;
```

### Pattern 2: Versioned store with append-only ordered migrations and stable UIDs

**What:** `schemaVersion` (it reads the legacy `version: 1`), plus `MIGRATIONS[i]` that turns version i+1 into i+2. Migrations are pure and are never edited after a release. Questions get `uid`. Stats, flags and session queues are keyed by `uid`.
**When to use:** At boot, before anything reads `store`.
**Trade-offs:** + one choke point that is easy to test with fixtures. − Moving from index to uid touches about 12 functions (`questionStats`, `flagged`, `eligible`, `planForTopic`, `planQuick`, `cardsStart`, `lesson`, `grade`, `restore`, `finishTopicGeneration`, `dueTotal`, `mastery`). Write characterization tests before the refactor.

**Key decision: put every 1.0 field into v2 at once.** That means `uid`, `topic.status`, `question.review`, `question.origin`, `question.evidence`, `store.seeds`, `store.parent` (PIN) and `topic.source` (OCR pages for drafts). 1.0 then ships with exactly one schema bump. `com.repasumi` installs start empty because the new `applicationId` gives a fresh WebView origin, so v2 is the first production schema. Every later version will have to migrate from it, so its shape matters more than the v1→v2 migration, which only testers' data will exercise.

```js
const MIGRATIONS = [
  /* v1 → v2 */ (s, ctx) => {
    for (const t of s.topics) {
      t.status ??= 'published';                       // grandfather: already in use
      t.questions = t.questions.map((q, i) => ({
        ...q,
        uid: q.uid ?? (t.id.startsWith('builtin-') ? `${t.id}#${i}` : ctx.newId()),
        review: q.review ?? 'approved',
        origin: q.origin ?? { kind: t.id.startsWith('builtin-') ? 'seed' : 'legacy' },
      }));
    }
    for (const p of Object.values(s.profiles ?? {})) remapIndexKeysToUid(p, s.topics); // stats + flags
    s.seeds = Object.fromEntries(s.topics.filter(t => t.id.startsWith('builtin-')).map(t => [t.id, 1]));
    s.parent ??= { pin: null, failures: 0, lockedUntil: 0 };
    s.schemaVersion = 2; delete s.version; return s;
  },
];
function migrate(raw, ctx) {
  let s = structuredClone(raw), v = s.schemaVersion ?? s.version ?? 1;
  if (v > SCHEMA_VERSION) throw Object.assign(Error('future schema'), { code: 'FUTURE' }); // read-only mode
  while (v < SCHEMA_VERSION) { s = MIGRATIONS[v - 1](s, ctx); v = s.schemaVersion; }
  return s;
}
```

**Boot and recovery sequence (StoreService):**
1. `raw = adapter.read(KEY)`. If it is null, build `fresh(seeds)`.
2. `JSON.parse`. On failure, write `KEY.corrupt.<ts> = raw` (best effort), try `KEY.bak`, then show a **blocking recovery screen** ("Restaurar copia" / "Empezar de nuevo"). Writes stay enabled afterwards. Never set `storageOK = false` silently.
3. `migrate`. If `FUTURE`, enter read-only mode with a banner and never overwrite. On any other migration error, keep `raw`, write `KEY.pre-v<N>` and show the recovery screen.
4. Run `validateStoreShape` (structural only).
5. `syncSeeds(store, SEEDS)`: inject a built-in topic only when `SEEDS[id].version > store.seeds[id]`. Deletions are tombstoned (`store.seeds[id] = version`), which fixes the "deleted built-in reappears" bug.
6. On the first successful boot of a new app version, copy the previous good `raw` to `KEY.bak`, then `save()`.
7. `save()` is debounced (about 300 ms) and also runs immediately on structural actions such as publish, delete and session end. A quota or write error sets a persistent `state.storageError` banner and retries on the next save.

**Storage location decision: stay in WebView `localStorage` for 1.0, behind `StorageAdapter`.** Reasons:
- Every launch device starts empty, and the realistic store size (a few topics, 700 sessions per learner, OCR pages of at most 150 KB per draft) fits the per-origin quota with room for one `.bak` copy.
- A native `AtomicFile` store would add two synchronous `@JavascriptInterface` methods, a one-time cross-backend import, and changes to `MainActivity`, which is the hottest Java file in parallel work. It would also need its own tests. The gain (stronger durability and room for very large stores) does not decide anything for 1.0.
- Multi-tab clobbering does not apply on Android (one WebView).
- **Do not** run localStorage as primary with a native mirror. Two sources of truth would contradict each other after a partial failure.
- Revisit in v1.1 alongside export/import (#11). The adapter seam lets `NativeFileAdapter` (AtomicFile in `filesDir`) replace it with one migration hook.
- Durability caveat (LOW confidence): Chromium batches localStorage commits to LevelDB. Older code used a 5 s delay, and Android WebView was later changed to flush aggressively. Assume the last write before a process kill can be lost. Call `save()` immediately, not debounced, for publish, approve-all, delete, and the end of a session.

### Pattern 3: Store-backed drafts + targeted patching (no lost edits under a render-everything UI)

**What:** Treat the review editor as a *view of persisted data*, not as DOM state. Every editable field carries `data-draft="<uid>.<path>"`. The delegated `input` listener calls `Core.setDraftField(topic, uid, path, value)` and then a debounced `save()`. It **never** calls `render()`. Progress events call `patchProgress()`, which updates `#gen-status` text and the progress bar width only. `render()` is reserved for structural changes such as approve, discard, publish, a new chunk arriving or a tab change. Because the data lives in the store, a full re-render cannot lose anything. `renderPreservingFocus()` restores `activeElement.id` and `selectionStart/End`.
**When to use:** For the review editor, PIN entry, and any form that is open while native events arrive.
**Trade-offs:** + Minimal change to the existing renderer. − Each new form needs `data-draft` wiring and stable element ids.

```js
$('#app').addEventListener('input', e => {
  const path = e.target.dataset.draft; if (!path) return;
  Core.setDraftField(currentDraftTopic(), path, e.target.value); scheduleSave();   // no render()
});
function onNativeEvent(ev, p) {
  if (ev === 'gen_stage') return patchProgress(p);             // text + bar only
  if (ev === 'gen_chunk') { Core.mergeChunkResult(draft, p, ctx); saveNow();
    return isEditingReview() ? showNewItemsPill() : renderPreservingFocus(); }
}
```

### Pattern 4: Gate at dispatch, not at render (parental PIN)

**What:** `handleClick` becomes `dispatch(action, data)`. A `GATED` set (`tab:add`, `tab:parents`, `delete-topic`, `delete-student`, `copy-report`, `publish-topic`, `open-external`, `reset-progress`, plus any future export) is checked before the `switch`. When the gate is locked, the intent `{action, data}` goes into `state.pendingIntent`, a PIN modal opens, and a successful check calls `dispatch(pendingIntent)`. Unlock is a short session: `state.parentUntil = now + 5 min`. It relocks on the `app_paused` native event, on returning to "Estudiar", and on learner switch.
**PIN storage:** `store.parent.pin = {alg: 'PBKDF2-SHA256', iter, salt, hash}` through `crypto.subtle`. `https://appassets.androidplatform.net` is a secure context, so `crypto.subtle` is available. `failures` and `lockedUntil` give an escalating lockout. The hash, verify and lockout maths live in the core block and are unit-tested. The PIN is created the first time someone opens Padres or Añadir. "He olvidado el PIN" leads to a non-trivial adult challenge and then a reset. Whether that satisfies the policy is a Families question for FEATURES and PITFALLS.
**Trade-offs:** + One enforcement point that is easy to audit and test with Playwright. − `crypto.subtle` is async, so the gate flow must be async. That is already true because a modal is involved. Replace `confirm()` with the existing `modal()` while doing this.

### Pattern 5: Process-scoped runtime + single-flight job + cooperative cancel + staged events

**What:** `ModelRuntime` lives in `RepasumiApp`. `MainActivity` calls `runtime.attach(sink)` in `onCreate` and `runtime.detach()` in `onDestroy`. Exactly one `GenerationJob` can exist (`AtomicReference.compareAndSet`), and a second start returns `gen_done{status:'rejected_busy'}` immediately. Cancellation is an `AtomicBoolean` on the job. The C++ token loop calls back into Java every token (`TokenCallback.onToken(n)` returns `false` to stop) and checks between the 256-token prompt batches. The cancel flag is never set from inside native code, and the abort callback that runs on ggml threads is not used. A cancelled job **keeps the chunks it has already finished**.
**Activity recreation:** add `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden|uiMode|density|locale|layoutDirection"`. Rotation and dark-mode changes then no longer destroy the WebView. The runtime still survives the cases that do recreate the Activity, such as "Don't keep activities" or a system reclaim while the process stays alive. On WebView boot, JS calls the synchronous `AndroidBridge.snapshot()`, which returns `{model, device, job:{id,status,chunk,total,results:[…]}}`, and merges the results idempotently by `(jobId, chunkIndex)`.
**Process death:** the job is lost, but JS persisted every `gen_chunk` and the OCR pages into the draft topic. On boot, a draft with `job.status === 'running'` and no matching native job is marked `interrupted`, and "Continuar con las fotos restantes" calls `startGeneration` again with only the chunks that are still missing. No foreground service in 1.0. Set `FLAG_KEEP_SCREEN_ON` while a job runs.
**Trade-offs:** + Handles rotation, recreation and process death for about 200 lines of Java. − Adds a JNI upcall per token. The cost is microseconds, which is negligible next to decoding.

```java
// local_llm.cpp (sketch): per-token upcall on the SAME thread → safe JNIEnv use
jclass cb = env->GetObjectClass(callback);
jmethodID onToken = env->GetMethodID(cb, "onToken", "(II)Z");   // (generated, max) → continue?
for (int i = 0; i < maxOutput && !failed; i++) {
  /* sample + decode */
  if (!env->CallBooleanMethod(callback, onToken, i + 1, maxOutput)) { cancelled = true; break; }
}
```
```java
// ModelRuntime.startGeneration(jobJson) — single-flight
GenerationJob job = GenerationJob.parse(jobJson);
if (!current.compareAndSet(null, job)) { sink.emit("gen_done", busy(job.id)); return; }
inference.execute(() -> { try { ensureLoaded(job); for (Chunk c : job.chunks) {
    if (job.cancelled()) break;
    emitStage(job, "generating", c.index);
    String out = engine.complete(PromptBuilder.forChunk(c, job.level), 550, PromptBuilder.grammarFor(c),
                                 (n, max) -> { throttledTokenStage(job, c, n, max); return !job.cancelled(); });
    job.addResult(c.index, JsonExtractor.extract(out));     // buffered for snapshot()
    sink.emit("gen_chunk", job.resultJson(c.index)); }
  } finally { sink.emit("gen_done", job.doneJson()); current.set(null); } });
```

### Pattern 6: JS plans and validates; Java executes (chunked generation pipeline)

**What:** Split responsibilities by testability and by lifecycle.
- **JS core:** `planChunks(pages, {maxChars ≈ 2000, maxChunks})` groups consecutive OCR pages and splits oversized pages while keeping their `Foto N` label. `validateSource(q, chunk, pagesByN)` checks that `src` is exactly `Foto N` with N in `chunk.pages` (hard rule; failures are auto-discarded and counted) and that the normalised content tokens of `q`/`a`/`opts` overlap the text of page N (soft rule; failures are kept as `pending` with an `issues:['low_overlap']` badge for the parent). `mergeChunkResult` runs per-question validation and dedupe, assigns uids, and sets `review:'pending'` and `origin:{kind:'ocr', jobId, chunk, pages}`.
- **Java:** iterates the chunk list it was given, owns the prompt (single producer, sanitised), enforces the hard token budget, and returns raw JSON per chunk. Java does **not** validate the schema beyond "a JSON object with a `questions` array". Validation exists only in JS, in one place.
- **Optional (stretch, MEDIUM confidence):** build a per-chunk GBNF in `PromptBuilder` where `src ::= "\"Foto 3\"" | "\"Foto 4\""`, using llama.cpp's grammar sampler. The model then cannot cite pages outside the chunk, and the hard rule becomes a safety net. Check `llama_sampler_init_grammar` against the pinned llama.cpp tag first.
**Trade-offs:** + The trust-critical logic (source validation) runs in Node tests with fixtures. The job loop survives WebView reloads. − The OCR page text has to reach JS. It already does through `ocr_result`; the change is to make it structured (`pages[]`) and to persist it in the draft.

---

## Data Flow

### Flow A — Boot

```
WebView loads index.html
  → RepasumiCore defined (pure)
  → StoreService.boot(): read KEY → parse ─fail→ backup corrupt → .bak? → recovery screen
                                     → migrate(v1→v2) → syncSeeds → save
  → NativeClient: AndroidBridge.snapshot()  (sync)  → {model state, device floor, job}
  → reconcile drafts (merge buffered chunk results / mark interrupted)
  → render()
```

### Flow B — Photos → reviewed, published topic (parent, PIN-gated)

```
[Añadir] ─dispatch('tab:add')→ ParentGate (PIN) → add view
  → AndroidBridge.scanPhotos() → SAF → NativeLearningEngine (downsample, ML Kit)
      ⇢ ocr_progress (patchProgress)   ⇢ ocr_result {pages:[{n,text,readable}]}
  → JS: create draft topic {status:'draft', source:{pages}, job:null}; saveNow()
  → JS: chunks = Core.planChunks(pages); job = {id, level, chunks}
  → AndroidBridge.startGeneration(JSON(job))          (fire-and-forget)
      ModelRuntime: single-flight → ModelLocator.ensureReady (copy+verify once) → load
      ⇢ gen_stage {jobId, stage:'verifying'|'loading'|'tokenizing'|'generating', chunk, total, tokens, max}
      ⇢ gen_chunk {jobId, chunk, pages, raw}  ──▶ Core.mergeChunkResult (validate, validateSource,
                                                   dedupe, uid, review:'pending') → saveNow()
      ⇢ gen_done {jobId, status:'completed'|'cancelled'|'failed'|'rejected_busy', error?}
  [Cancelar] → AndroidBridge.cancelGeneration(jobId) → AtomicBoolean → loop exits → gen_done(cancelled)
  → Review editor: edit (data-draft → store), approve / discard per question
  → [Publicar] (GATED) → Core.canPublish(topic) (≥1 approved, 0 pending) → status:'published',
     drop source.pages (keep per-question evidence snippet) → saveNow()
```

### Flow C — Child study (must only see approved content)

```
[Estudiar] → Core.studyableTopics(store) → planForTopic/planQuick/cardsStart use
             Core.studyableQuestions(topic)   ← SINGLE choke point
  → lesson() → grade(uid, correct) → profiles[learner].topics[topicId].questions[uid] → save()
```
"Reto nuevo" (practice generation) currently starts a session with brand-new, unreviewed questions straight away. Under the review requirement those questions must arrive as `review:'pending'` in the topic and show a "N preguntas nuevas por revisar" badge in Padres. Meanwhile the child keeps practising from the approved bank. **Roadmap decision needed:** either change "Reto nuevo" to this queue model or move it behind the PIN.

### Flow D — Native event return path (contract v2)

```
ModelRuntime / NativeLearningEngine
  → EventSink (attached Activity or null)  → runOnUiThread → web != null ? evaluateJavascript(...)
  → window.repasumiNativeEvent(event, payloadJSON)
  → NativeClient routes: stage/progress → patchProgress | chunk/result → core merge + save + render
Detached sink: events dropped, BUT job results are buffered in GenerationJob → recovered via snapshot()
```

### State Management

```
Durable (localStorage KEY, schema v2)            Ephemeral (state)            Native (process)
 learners, currentId                              tab, session, cards          ModelRuntime.handle
 topics[{id,status,questions[{uid,review,…}],     pendingIntent, parentUntil   GenerationJob(current)
         source?{pages}, job?{id,status}}]        storageError, progress       model state (verified?)
 profiles[learner].topics[tid].questions[uid]     editing focus                OCR pages (until sent)
 profiles[learner].flags["tid::uid"]
 seeds{id:version}, parent{pin,failures,lockedUntil}, settings
```
Rule: anything that must survive rotation, WebView reload or process death goes into the store or the `ModelRuntime`. Nothing important lives only in `state` or in the DOM.

### Key Data Flows

1. **Model provisioning:** at first launch the IO thread runs `InstallTimePackLocator.ensureReady()`. It streams `assets/qwen…gguf` from the install-time pack to `filesDir/model/qwen.gguf.part`, hashing as it copies, checks against `EXPECTED_SHA256`, does an atomic rename, and writes the marker `{sha, size, mtime, versionCode}`. Later loads compare the marker against file size and mtime, which is cheap. When `versionCode` changes, it rehashes. UI: `model_state` = `preparing → ready | unsupported(reason) | error`.
2. **Device floor:** `snapshot().device = {totalRamMb, sdk, abi}`. Below the threshold, `ModelRuntime` refuses to load and the UI says so before any OCR work. Also set `minSdk 29`. Device-catalog RAM exclusion in Play Console is a store-side complement.
3. **Persistence write path:** UI mutation → `Core` pure update → `save()` (immediate for structural actions, debounced for typing) → adapter → error banner on failure.

---

## Scaling Considerations

All data and compute are local, so "scale" here means data per device and the spread of devices, not user count.

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 1 family, 1–3 learners, <20 topics (1.0 target) | One JSON blob in localStorage, whole-store save, one model in memory. All fine. |
| Heavy family: 5 learners, 100+ topics, 700 sessions each, OCR drafts kept | Store approaches several MB, and whole-store `JSON.stringify` per answer gets noticeable. Mitigate in 1.0 by dropping `source.pages` on publish and capping sessions (already 700). v1.1: per-key storage or a `NativeFileAdapter` through the existing seam. |
| Long topics: 30 photos ≈ 15+ chunks | Wall-clock time dominates. At roughly 30–60 s per chunk on a 0.5B CPU model, that is 10–15 min. Mitigate with `maxChunks`, an ETA in `gen_stage`, cancel-keeps-results, and resume-remaining. Measure on the Xiaomi 12T and the 4 GB device before choosing the numbers. |

### Scaling Priorities

1. **First bottleneck: generation time and RAM on 4 GB devices.** Use a single model instance, unload in `onTrimMemory` when idle, set threads to `min(4, availableProcessors)`, and downsample OCR images.
2. **Second bottleneck: store size and serialisation cost.** Drop OCR pages on publish, then move to per-key storage in v1.1.

---

## Anti-Patterns

### Anti-Pattern 1: Re-rendering the whole view on every native event
**What people do:** `repasumiNativeEvent` → `render()` for every `ai_progress`/`ocr_progress`, which is what happens today.
**Why it's wrong:** It wipes un-persisted edits and focus. This is the CONCERNS bug "render() borra las ediciones de revisión", and with per-token stage events it would also burn CPU.
**Do this instead:** `patchProgress()` for stage and progress events, store-backed drafts, and `render()` only on structural changes.

### Anti-Pattern 2: Model lifetime owned by the Activity
**What people do:** Create `NativeGguf` in `MainActivity.onCreate` and close it in `onDestroy`, which is what happens today.
**Why it's wrong:** Rotation reloads the 491 MB model and kills the job in progress, and late callbacks hit a destroyed WebView.
**Do this instead:** `ModelRuntime` in `Application`, attach/detach a sink, `configChanges`, and `web = null` on destroy.

### Anti-Pattern 3: Treating an install-time asset as a file path
**What people do:** Expect `assets/model.gguf` from an install-time pack to have a path that `llama_model_load_from_file` can open.
**Why it's wrong:** Install-time packs are split APKs that can only be read through `AssetManager`, with no filesystem path, and llama.cpp's loader takes a path. The model also does not exist in a plain `assembleDebug` APK.
**Do this instead:** Copy it once to `filesDir` while hashing (Flow 1), and keep `DebugSafImportLocator` for debug or CI APKs. Fast-follow packs would give a real path (`getPackLocation().assetsPath()`), but they add the Play asset-delivery library, async states, `WAITING_FOR_WIFI` / `REQUIRES_USER_CONFIRMATION` handling, and "re-check at every launch". That is more moving parts than 1.0 can absorb. Costs of install-time: about 2× the model size in storage (APK split plus the copy, roughly 1 GB) and 2× free space at install. **Flag for confirmation in the release phase.** `ModelLocator` keeps the swap to fast-follow in v1.1 local to one class.

### Anti-Pattern 4: Two validators, or validation in Java
**What people do:** Check question shape in `NativeGguf` and again in JS with different rules, which already drifted between `NativeGguf` and `local-ai.js`.
**Why it's wrong:** The rules diverge and the Java side cannot be tested in Node.
**Do this instead:** Java only extracts JSON and enforces the token budget. Schema and source validation live only in `RepasumiCore`.

### Anti-Pattern 5: Gating by hiding buttons
**What people do:** Leave PIN-protected actions out of the rendered HTML.
**Why it's wrong:** Any other code path or stale DOM can still call the action. The rule cannot be audited.
**Do this instead:** Check the `GATED` set in `dispatch()` and test it in the Playwright smoke run ("tap Padres without PIN → modal").

### Anti-Pattern 6: Leaking unreviewed content to child surfaces
**What people do:** Add `status`/`review` fields but leave planners reading `topic.questions` directly.
**Why it's wrong:** One missed call site, such as `dueTotal`, `cardsStart` or `planQuick`, shows unapproved AI questions to a child, which breaks the core promise.
**Do this instead:** Make `Core.studyableQuestions()` the only accessor for child surfaces. A unit test asserts that `planQuick` over a store containing pending questions returns zero pending uids.

### Anti-Pattern 7: A generic `complete(prompt)` bridge method
**What people do:** Let JS build full prompts and pass them to Java, so the "single prompt producer" moves into JS.
**Why it's wrong:** It widens the WebView-exposed capability surface and puts ChatML control-token handling where it cannot be sanitised consistently.
**Do this instead:** The bridge takes a job (`level`, `chunks[{index,pages,text}]`) and `PromptBuilder` (Java, JUnit-tested) owns templates and sanitisation.

### Anti-Pattern 8: Parallel worktrees editing one-line mega-functions
**What people do:** Run several PRs at once against `web/index.html`, where `handleClick` and `validate` are each a single line of more than 900 characters.
**Why it's wrong:** Every concurrent change conflicts on the same physical line. The user explicitly works with parallel Claude sessions and worktrees.
**Do this instead:** In the first JS phase, do a mechanical one-statement-per-line reformat of the inline script, in its own behaviour-neutral PR verified by `node --check` plus the characterization tests. Then **serialize JS phases** and parallelize across the JS/Java boundary instead (P3 ∥ P4).

### Anti-Pattern 9: Renaming the Java package together with `applicationId`
**What people do:** Change `namespace`, the Java package and the JNI symbols (`Java_com_davidop_estudiaenelbus_…`) in the same change as `applicationId 'com.repasumi'`.
**Why it's wrong:** JNI symbol names depend on the class's package, not on `applicationId`. The rename adds risk and gives nothing for 1.0.
**Do this instead:** Change only `applicationId` (and the app label). Keep `namespace` and the package until v1.1.

### Anti-Pattern 10: Release-only breakage from R8
**What people do:** Turn on `minifyEnabled true` for the release AAB and test only debug builds.
**Why it's wrong:** R8 can strip or rename `@JavascriptInterface` methods, native methods and the `TokenCallback.onToken` method that C++ looks up by name. The bridge then fails silently in production only.
**Do this instead:** Add `-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }`, keep `native` methods, and keep the callback interface. Install the release bundle (bundletool local testing or Play internal track) on a device as part of the phase's verification.

---

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Play Asset Delivery (install-time) | `:model_pack` module, `assetPacks = [':model_pack']`, read through `AssetManager` → one-time copy | Packs ship as split APKs. Limits are 1.5 GB per pack and 4 GB cumulative for install-time, so the roughly 491 MB GGUF fits. Only works when installed from an AAB (Play tracks, internal app sharing, `bundletool --local-testing`). Needs 2× free space at install. |
| Play Asset Delivery (fast-follow, rejected for 1.0) | `com.google.android.play:asset-delivery:2.3.0`, `getPackLocation().assetsPath()` | Gives a real file path and no duplicate. Costs: async states, >200 MB mobile-data consent dialog, invalidation on update. If adopted later, verify the merged manifest still has no `INTERNET` permission (not confirmed in research, so check with `apkanalyzer`). |
| Play Console | Upload key from GitHub Secrets → `bundleRelease` → internal testing track | Upload to the internal track in **week 1**. That fixes `com.repasumi` for good, proves signing and PAD end to end, and starts the Families/data-safety paperwork early. |
| ML Kit Text Recognition 16.0.1 (bundled) | Unchanged API. Feed it downsampled bitmaps (`InputImage.fromBitmap`) | CI must assert that the merged manifest of the AAB has no `INTERNET` permission. Transitive dependencies are the risk. |
| llama.cpp (pinned tag, cloned at build) | JNI `Engine`. Adds a token callback and an optional grammar | Pin by commit SHA. Check that `llama_sampler_init_grammar` and the abort-callback signatures exist in the pinned version before relying on them. |
| GitHub Pages | `pages.yml` publishes `site/` (landing + `privacidad/`) | Choose the privacy URL once and do not move it, because the Play listing and Families declarations point to it. Removing `web/` from Pages also removes the jsDelivr and Hugging Face exposure. Delete `web/local-ai.js`, `webBridge()`, the `sample` path and `copy-prompt` from the SPA. |

### Internal Boundaries

| Boundary | Communication | Direction | Notes |
|----------|---------------|-----------|-------|
| UI block ↔ `RepasumiCore` | Direct function calls | UI → Core (Core never calls UI) | Core is pure. Time and ids are injected. |
| UI ↔ `StorageAdapter` | `read/write/remove` (sync) | UI → storage | Only `StoreService` touches it. |
| JS → Java commands | `AndroidBridge.startGeneration(jobJson)`, `cancelGeneration(jobId)`, `scanPhotos()`, `copyText()`, `speak()` | JS → Java, fire-and-forget | Single-flight is enforced in Java, not only by `state.nativeBusy`. |
| JS → Java queries | `AndroidBridge.snapshot()`, `bridgeVersion()` (sync, return JSON strings) | JS → Java → JS | Used at boot and on resume to reconcile. Keep them cheap: no IO on the JS-bridge thread. |
| Java → JS events | `window.repasumiNativeEvent(event, payload)`: `model_state`, `ocr_progress`, `ocr_result`, `gen_stage`, `gen_chunk`, `gen_done`, `app_paused`, `error` | Java → JS | Every generation event carries `jobId`. JS ignores stale job ids. Document in `docs/BRIDGE.md`. |
| `MainActivity` ↔ `ModelRuntime` | `attach(sink)` / `detach()`, command passthrough | Activity → Runtime; Runtime → sink | Runtime never holds an Activity reference. |
| `ModelRuntime` ↔ `local_llm.cpp` | JNI: `load(path)`, `complete(handle, prompt, max, grammar, cb)`, `close(handle)` | Java → C++; C++ → Java (same-thread callback only) | The handle is touched only on the inference thread. |
| Test harness ↔ app | Node `vm` on the core block. Playwright + `FakeAndroidBridge` via `addInitScript` | Tests → app | The fake implements contract v2 with canned OCR pages and chunk JSON, so the smoke test covers gate → generate → review → publish → study. |

---

## Suggested Build Order (3 weeks, 2026-11-13 → 2026-12-03)

The order is driven by data shape (everything depends on store v2), JS/Java parallelism (the only safe parallel axis) and Play lead time (Families review takes days, and the closed test with 12+ families needs a build out by about 24–26 Nov).

```
P0 Release skeleton ───────────────────────────────────────────────▶ (Play internal track)
P1 Testability seam ─▶ P3 Persistence v2 ─▶ P5 Gate + Review UI ─▶ P6 Chunked gen + src validation ─▶ P7
P2 Pages/legal (independent, tiny) ┘          ▲                         ▲
                       P4 Native runtime ──────┴─────────────────────────┘  (Java/C++ only, ∥ P3/P5)
```

| # | Phase | Depends on | Delivers | Why here |
|---|-------|------------|----------|----------|
| P0 | **Release skeleton** (week 1, Java/Gradle/CI) | none | `.gitignore`, Gradle wrapper, `applicationId com.repasumi`, `minSdk 29`, release signing from secrets, `:model_pack` install-time, `bundleRelease` in CI, R8 keep rules, CI check for no `INTERNET` permission, first upload to internal testing | Proves signing + PAD + `applicationId` on real Play infrastructure while there is still time to change the delivery mode. It is the longest lead-time item. |
| P1 | **Testability seam** (week 1, JS) | none | One-statement-per-line reformat of the script, `<script id="repasumi-core">` split, `node --test` harness, characterization tests for current grading, SRS and `validate`, `verify_source.py` using tempfile, `tools/sync_assets.py` | Every later JS change has a safety net, and the reformat removes merge-conflict hell for parallel worktrees. |
| P2 | **Pages + legal** (week 1, tiny, any worktree) | none | `site/` landing + privacy, `pages.yml` → `site/`, `LICENSE`, removal of `local-ai.js`, `webBridge` and `sample` from the SPA | The privacy URL is needed for the Play listing. Removing dead web paths shrinks the surface before the refactors. |
| P3 | **Persistence v2** (week 1–2, JS) | P1 | `schemaVersion`, migration v1→v2 with **all 1.0 fields**, uids, index→uid refactor, seed versioning, corruption backup and recovery screen, `StorageAdapter`, fixtures for 0.3/0.4/0.5 | Review, gate, chunk merge and flags all depend on the v2 shape. Do it once, before anything writes new fields. |
| P4 | **Native runtime hardening** (week 1–2, Java/C++; parallel with P1/P3) | P0 (package/Gradle), `docs/BRIDGE.md` v2 agreed | `RepasumiApp` + `ModelRuntime`, `configChanges`, single-flight, cancel through the token callback, `gen_stage` events, `snapshot()`, `ModelLocator` (install-time copy + verify; DEBUG SAF), device floor, `onTrimMemory`, OCR downsample + `pages[]` + `busy` fix, keep-screen-on, `openModelPage` removed from release, `PromptBuilder`/`JsonExtractor` with JUnit | Touches only Java/C++, so it runs alongside the JS phases. Write the bridge contract doc first so P5/P6 can build against `FakeAndroidBridge`. |
| P5 | **Parent gate + review/approval** (week 2, JS) | P3 | `dispatch()` refactor, `GATED` + PIN (PBKDF2, lockout), modal confirms, store-backed review editor, publish rules, `studyableQuestions` choke point, `patchProgress`, decision on the "Reto nuevo" pending queue | Needs the v2 fields. P6 drops questions into this model. |
| P6 | **Chunked generation + source validation** (week 2–3, JS + small Java) | P3, P4, P5 | `planChunks`, `startGeneration(job)`, `gen_chunk` merge, `validateSource` (hard range + soft overlap), draft `source.pages`, resume-remaining, optional per-chunk GBNF for `src` | Integrates both sides. Do it last among features, because it needs the runtime (P4) and the review model (P5). |
| P7 | **E2E smoke + device acceptance + store submission** (week 3) | P5, P6 (P0 for the AAB) | Playwright smoke with `FakeAndroidBridge`, 10 consecutive generations on Xiaomi 12T and the 4 GB device, data-safety form, Families declaration, closed test with 12+ families, production submission | The acceptance evidence that issues #2 and #13 require, then the Play review. |

**Parallelization rule:** never run two JS phases in parallel on `web/index.html`. Valid parallel pairs are P0 ∥ P1, P2 ∥ anything, P3 ∥ P4, and P5 ∥ the tail of P4.

**Cut line if time slips:** keep P6's hard `src`-range rule and drop the soft overlap scoring and the GBNF. Keep P4's cancel and single-flight and drop `onTrimMemory`. Never cut P3's migration and recovery or P5's `studyableQuestions` choke point, because those two carry the core promise.

**Research flags for later phases:**
- P0: confirm install-time copy vs fast-follow against the storage footprint (~1 GB on device). Confirm that `assembleDebug` CI artifacts cannot contain the pack (they cannot; use bundletool local testing for device QA).
- P4: verify the pinned llama.cpp API (sampler, grammar, `llama_memory_clear`, abort callback) and measure tokens/s and peak RSS on the 4 GB device.
- P6: tune chunk size and overlap thresholds on real textbook OCR. These are empirical.
- P5: check that the PIN-reset path satisfies the Families "parental gate" expectation (FEATURES/PITFALLS researcher).

---

## Sources

- Codebase, read directly (HIGH): `web/index.html` lines 268–459 (store boot 298–308, `qKey`/stats 312–316, `finishTopicGeneration` 383–403, `render` 427–434, `validate` 436–440, `handleClick` 450, `repasumiNativeEvent` 454); `app/src/main/java/com/davidop/estudiaenelbus/{MainActivity,NativeGguf}.java`; `app/src/main/cpp/local_llm.cpp`; `tools/verify_source.py`; `.github/workflows/pages.yml`; `app/build.gradle`; `AndroidManifest.xml`
- `.planning/PROJECT.md`, `.planning/codebase/ARCHITECTURE.md`, `.planning/codebase/CONCERNS.md`
- [Integrate asset delivery (Kotlin and Java), Android Developers](https://developer.android.com/guide/playcore/asset-delivery/integrate-java): install-time packs read through `AssetManager`; fast-follow `getPackLocation().assetsPath()`; >200 MB consent; re-check at every launch; must run as a bundle. Official doc; seam tier LOW for webfetch.
- [Play Asset Delivery overview](https://developer.android.com/guide/playcore/asset-delivery): delivery modes, 2× free-space requirement for install-time, update and invalidation semantics. Official doc.
- [Google Play maximum size limits](https://support.google.com/googleplay/android-developer/answer/9859372): 1.5 GB per asset pack, 4 GB cumulative for install-time, 30 GB for fast-follow/on-demand. Official doc.
- [llama.cpp PR #5409, abort callback](https://app.semanticdiff.com/gh/ggerganov/llama.cpp/pull/5409/overview) and [cyllama cancelling notes](https://shakfu.github.io/cyllama/cancelling/): `abort_callback` makes `llama_decode` return 2, CPU only, cooperative. Secondary sources, LOW-MEDIUM; the design deliberately uses a same-thread token callback instead.
- [Chromium DOM storage commit batching (code review)](https://codereview.chromium.org/1140013003) and [Chromium source, local_storage_context_mojo.cc](https://chromium.googlesource.com/chromium/src/+/72.0.3626.96/content/browser/dom_storage/local_storage_context_mojo.cc): delayed commits; WebView flushes aggressively. 2015–2017 sources, LOW.
- [Ertas: shipping GGUF models on mobile](https://www.ertas.ai/docs/ship/model-delivery-and-ux): copying the GGUF to internal storage for llama.cpp on Android. Secondary, LOW; consistent with the official AssetManager-only access.

---
*Architecture research for: offline hybrid Android study app (Repasumi 1.0 · Google Play)*
*Researched: 2026-11-13*
