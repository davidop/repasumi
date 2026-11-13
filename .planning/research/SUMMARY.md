# Project Research Summary

**Project:** Repasumi («Del libro al reto»), milestone 1.0 · Google Play
**Domain:** Offline Android study app for children aged 6–16: Java host + WebView single-file SPA + llama.cpp over JNI (Qwen2.5-0.5B Q4_K_M, 491 MB) + ML Kit OCR, shipped through Google Play under the Families program
**Researched:** 2026-11-13 (hard deadline 2026-12-03, 20 days away)
**Confidence:** MEDIUM-HIGH. Build and toolchain facts are HIGH because the artifacts, headers and shipped APK were inspected. Play policy and review timing are MEDIUM. On-device performance on 4 GB phones is MEDIUM-LOW until measured.

## Executive Summary

Repasumi is a working v0.5 prototype. What it lacks is launch plumbing, not product features. Hybrid WebView apps with an on-device model usually ship with four things: a native runtime that lives for the whole process rather than one Activity, a pure and testable domain core, a single choke point for what the child can see, and Play Asset Delivery for the model so the app never needs the network. The research agrees on the stack. Java, WebView, the vanilla SPA and llama.cpp stay. The only additions are build, release and test tooling: Gradle wrapper 8.14.7, NDK r28c, R8 with keep rules, Play App Signing, an asset-pack module, `node:test`, Playwright 1.65 and JUnit 4. No new runtime dependencies.

The biggest risk is the schedule, and Play review drives it more than code does. Families apps get extended review on every submission, up to 7 days or longer, so the build that passes review has to be the real 1.0. Working back from 2026-12-03:

- **~Nov 17:** first signed AAB on the internal track.
- **~Nov 19:** closed track, with all App content declarations filled in.
- **~Nov 24:** production submission.
- **Nov 24 – Dec 3:** policy fixes only.

That leaves about 11 days of feature work. The roadmap therefore puts the release pipeline and compliance in week 1, and runs Java/C++ work in parallel with JS work. Two JS phases must never edit `web/index.html` at the same time.

Inspecting the shipped APK turned up two problems that change the plan:

1. **The app already has the `INTERNET` and `ACCESS_NETWORK_STATE` permissions.** ML Kit's DataTransport telemetry merges them in. As things stand, the core privacy claim is false, and so would be the planned Data safety answer ("no data collected").
2. **`librepasumi_native.so` is aligned to 4 KB pages.** From 2027-02-01 Play blocks updates that lack 16 KB support, and 16 KB devices already show a compatibility warning.

Each is a one-line fix plus a CI check.

The main product risk is the 0.5B model producing confident but wrong "facts" with invented citations. Three things mitigate it:

- **A hard rule** that nothing reaches the child until a parent approves it. `studyableQuestions()` is the only accessor child screens may use.
- **Chunked generation** where the citation (`src`) is taken from the chunk, never from the model.
- **Evidence snippets** shown to the parent during review.

## Key Findings

### Recommended Stack

Full detail in STACK.md. Everything listed is an addition to the existing stack (Java 17, AGP 8.13.2, llama.cpp v0.9.0, ML Kit 16.0.1), with no new runtime dependencies. Moving to AGP 9 or Gradle 9 waits until v1.1.

**Core technologies:**

- **Install-time asset pack `:qwen_model`** (AGP plugin `com.android.asset-pack` 8.13.2): delivers the GGUF without any library or new permission. Install-time content is stored uncompressed, so `openFd()` works. The 1.5 GB per-pack limit is not a concern.
- **NDK r28c (`28.2.13676358`), pinned:** produces 16 KB ELF alignment by default. Fallback: r27 plus `-Wl,-z,max-page-size=16384`.
- **Gradle wrapper 8.14.7:** the last patched 8.x release that works with AGP 8.13.
- **R8** (`minifyEnabled` + `shrinkResources`) with explicit keep rules for `native` methods, `@JavascriptInterface` methods and the class that C++ calls back into. The mapping file and native symbols (`debugSymbolLevel 'SYMBOL_TABLE'`) go to Play vitals, so no crash-reporting SDK is needed.
- **Play App Signing with an upload key** in GitHub Secrets, plus an offline backup. A protected `release-aab.yml` environment with two gates:
  - `bundletool dump manifest` shows no INTERNET;
  - `bundletool dump config` shows `PAGE_ALIGNMENT_16K`.
- **`javax.crypto` PBKDF2WithHmacSHA256** for the parent PIN. It lives on the Java side in SharedPreferences, with the lockout enforced natively.
- **llama.cpp v0.9.0 APIs already vendored:** `abort_callback`, `progress_callback` and `llama_model_load_from_file_ptr(FILE*)`, all confirmed in the header.
- **Test-only tooling:**
  - Node 24 `node:test` + `vm`, with no dependencies;
  - `@playwright/test` 1.65.0, Chromium only, against a fake `AndroidBridge`;
  - JUnit 4.13.2 + `org.json` 20260814;
  - bundletool 1.18.3.

**Critical constraints:**

- **Change only `applicationId` to `com.repasumi`.** Keep `namespace` and the Java package, because the JNI symbols are named `Java_com_davidop_estudiaenelbus_*`.
- **Never commit the GGUF.** CI downloads it from the pinned Hugging Face commit, checks it with `sha256sum -c`, and caches it.
- **Debug APKs have no model.** `assembleDebug` APKs don't include asset packs, so device QA has to use `bundletool build-apks` or internal app sharing.

### Expected Features

Full detail in FEATURES.md (IDs A1–E10, X1–X9). A feature counts as table stakes if leaving it out would do any of these:

- get the app rejected by Play;
- let a child see unreviewed AI output;
- make the privacy promise false;
- leave a family with lost data and no way to recover it.

**Must have (table stakes):**

- **Review workflow (B1–B9).** This is the core value and the largest piece of UI.
  - Topics move from `draft` to `published`.
  - Each question can be approved, edited or discarded (Aprobar/Editar/Descartar), with an editor for each of the five question types.
  - Edits save automatically and survive `render()`, rotation and process death.
  - Validators check each question.
  - Each question shows "Fuente: foto N" with the OCR excerpt and a ⚠ badge when the fact isn't found.
  - A topic can be published only when no questions are pending and at least one is approved.
  - A queue for flagged questions.
  - Published topics stay editable, and drafts can be resumed.
- **Data integrity (C1–C6):**
  - schema version plus ordered migrations;
  - a snapshot taken before each migration;
  - writes that keep a last-known-good copy, with save failures shown to the user;
  - a calm recovery screen;
  - stable question uids;
  - a guard for data written by a newer schema.
- **Parental gate (A1–A7):**
  - a 4-digit PIN, set during onboarding;
  - the gate covers Padres, Añadir, deleting, exporting, external links and intents, profile edits and changing the PIN; it does not cover study modes or the 🚩 flag;
  - automatic relock after 5 minutes and on `onPause`;
  - an escalating lockout after wrong attempts that never wipes data;
  - the PIN hash is stored on the Java side;
  - offline recovery of a forgotten PIN, and a way to change the PIN.
- **Onboarding and model availability (D1–D8):**
  - a first run of four screens or fewer;
  - exclusion of low-RAM devices through the Play device catalog;
  - an in-app capability check;
  - graceful degradation: studying keeps working and only "Generar" is disabled;
  - a visible one-time SHA-256 check of the model;
  - generation that is cancelable and reports progress by stage;
  - manual model import and the Hugging Face link removed from release builds.
- **Store compliance (E1–E10):**
  - a privacy policy, hosted online and bundled in the app;
  - a Data safety answer of "no data collected";
  - target audience 6–8 / 9–12 / 13–15 / 16–17, never 18+;
  - the IARC rating;
  - the Families self-check, with a CI guard on the manifest;
  - a path for reporting AI content: 🚩 → parent queue → an email the parent starts (`ACTION_SENDTO`);
  - an App access note for reviewers;
  - a Spanish (es-ES) store listing;
  - LICENSE and third-party notices;
  - GitHub Pages reduced to a landing page plus the policy.

**Should have (cheap differentiators for 1.0):**

- **X1, source grounding polish:** questions with a ⚠ come first, and one tap opens the photo.
- **X2, "Funciona sin Internet" trust screen:** checked at runtime through `PackageManager`.
- **X3, tamper-evident parent log:** last access, PIN resets and failed attempts.

**Anti-features (do not build):**

- a bulk "Aprobar todo" button;
- a math-problem gate, or a biometric gate;
- wiping data after N wrong PINs;
- PIN recovery by email or account;
- a confidence percentage;
- chat with the model, or generation started by the child;
- any cloud fallback;
- analytics or crash SDKs;
- Auto Backup;
- a model picker.

**Defer:**

- To v1.1:
  - X4, regenerating a single question;
  - X5, partial recovery;
  - X6, a child preview;
  - X7, a time estimate;
  - X9, taking photos directly with the camera;
  - export/import (#11);
  - following up on Teacher Approved.
- To v2 or later:
  - larger models through device targeting;
  - sharing between devices on the local network.

### Architecture Approach

Full detail in ARCHITECTURE.md. The hybrid shell stays, with three boundaries made explicit:

1. **Core and UI split inside the single HTML file.** A `<script id="repasumi-core">` block holds pure functions: migrations, validation, source validation, chunk planning, spaced repetition keyed by uid, and the review selectors. Node loads that block through `vm` with no build step. The UI block uses those functions through a single `dispatch()`.
2. **A versioned JS↔Java bridge contract** (`docs/BRIDGE.md`). Java sends events tied to a job (`gen_stage`, `gen_chunk`, `gen_done`, `model_state`, `app_paused`). A synchronous `snapshot()` lets JS catch up after a reload. A matching `FakeAndroidBridge` serves the Playwright tests.
3. **A `ModelRuntime` owned by the `Application`, for the whole process.** It holds the model, allows one `GenerationJob` at a time, cancels, and buffers chunk results. The Activity only attaches and detaches an event sink.

**Major components:**

1. **`RepasumiCore`:** the pure domain. `studyableQuestions()` is the only accessor that child screens may use. All validation happens here; Java never validates.
2. **`StoreService` + `StorageAdapter`:**
   - The boot sequence is: parse → quarantine corrupt data → migrate → `syncSeeds` → save.
   - `syncSeeds` uses tombstones, which fixes the bug where deleted sample topics come back.
   - Storage is localStorage for 1.0, saved immediately after structural actions.
3. **`dispatch()` + `ParentGate`:** one `GATED` set is checked before the action switch. If an action is gated, it is replayed once the PIN is entered. Gating never works by hiding buttons.
4. **A review editor backed by the store:**
   - Fields marked `data-draft="<uid>.<field>"` write to the store on every `input` event and never call `render()`.
   - Progress events update only the progress element, through `patchProgress()`.
5. **`ModelRuntime` / `ModelLocator` / `PromptBuilder` / `JsonExtractor`:** inference that survives the Activity lifecycle.
   - `PromptBuilder` is pure Java, tested with JUnit, the only place prompts are built, and strips `<|` and `|>` from the text.
   - The manifest declares the full `configChanges` list.
   - Cancellation goes through the per-token callback.
   - `FLAG_KEEP_SCREEN_ON` during generation.
   - The model is unloaded on `onTrimMemory`.
   - After process death, generation resumes with the remaining chunks.
   - No foreground service.
6. **`NativeLearningEngine`:**
   - runs OCR on photos downsampled to about 2400 px;
   - returns structured `pages[]`;
   - resets `busy` in `try/finally`.

**Key decision: put every 1.0 field into schema v2 at once** (`uid`, `status`, `review`, `origin`, `evidence`, `seeds`, `source`). `com.repasumi` gets a new WebView origin, so every production install starts empty and v2 is the first production schema. The v1→v2 migration only ever runs on testers' data, so getting the v2 shape right matters more than the migration itself.

### Critical Pitfalls

Full list in PITFALLS.md (25 items). The six most important:

1. **ML Kit merges in INTERNET** (HIGH confidence, found in the shipped APK).
   - Remove INTERNET and ACCESS_NETWORK_STATE with `tools:node="remove"`.
   - Keep the DataTransport components; removing them risks a JobScheduler crash.
   - Add a CI gate on the merged manifest of the release AAB.
   - Re-test OCR on a device.
   - Every compliance document depends on this.
2. **Families review time.**
   - Submit early, and never resubmit while a review is in progress.
   - Confirm in Play Console now that the 12-tester / 14-day testing rule doesn't apply to this account. If it does, the deadline can't be met.
3. **Choices fixed at the first upload:** the package name, the Google-generated app signing key, free vs paid, and any versionCode already used.
   - Use a signed-off checklist of these decisions.
   - Have CI set the versionCode (`run_number` plus an offset, always above 7).
   - Back up the keystore offline, because GitHub Secrets can't be read back.
   - Add `.gitignore` before creating the keystore.
4. **Things that break only in release builds:**
   - R8 can strip bridge or JNI methods.
   - A JNI rename can surface only as a generic "Error de inferencia".
   - Debug paths (the HF link, `globalThis.sample`, copy-prompt) can leak into release.

   Enable R8 in week 1, install the release bundle on a device, and fail CI if `huggingface.co` or `globalThis.sample` appears in the release build.
5. **Invented facts and citations:**
   - take `src` from the chunk instead of asking the model for it;
   - reject any citation outside the chunk's page range;
   - flag questions with low word overlap with the source;
   - pass OCR text with `parse_special=false`;
   - show evidence snippets in review.

   The parent's review is the real safety net.
6. **Running out of memory, duplicate model copies and process death on 4 GB phones:**
   - one model holder for the whole process;
   - the full `configChanges` list;
   - a checkpoint into the draft after each chunk;
   - a RAM floor checked at runtime (`totalMem` below about 3.5 GiB);
   - exclusion through the device catalog;
   - measure 2 vs 4 threads, and heat;
   - never build with `+dotprod`, which crashes with SIGILL on A53/A73 cores.

Also important:

- **Device-to-device transfer:** on Android 12+, `allowBackup=false` doesn't block it. Add `dataExtractionRules` and make the policy say the same.
- **Privacy URL:** set it once and never move it.
- **Line endings:** add `.gitattributes` with `eol=lf`, because the user edits on Windows across parallel worktrees.
- **The WebView `ORIGIN` constant:** never change it, because doing so silently orphans all saved data.

## Reconciled Conflicts and Required Decisions

### 1. Model delivery: install-time (recommended) vs fast-follow

- **The disagreement:** STACK and ARCHITECTURE recommend an install-time pack. PITFALLS recommends fast-follow, because install-time content "has no file path" and the copy costs about 1 GB.
- **Resolution: an install-time pack, with a 1-day spike to decide whether the model can be loaded without copying.**
- **Why PITFALLS' main argument mostly falls away:** STACK found `llama_model_load_from_file_ptr(FILE*)` in llama.cpp v0.9.0. bundletool stores install-time content uncompressed, so `openFd()` plus the file offset gives llama.cpp direct access with no copy.
- **What fast-follow would cost:** it needs `asset-delivery:2.3.0`, which merges in `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE` and `RECEIVE_BOOT_COMPLETED`. That brings:
  - a **foreground-service (dataSync) declaration in Play Console, with a demo video**;
  - a download state machine: `WAITING_FOR_WIFI` above 200 MB, `REQUIRES_USER_CONFIRMATION`, packs invalidated on update and deleted by "Clear storage".

  That is more review items and more UI states on a 20-day clock.
- **What install-time gives:** the model is present, offline, at first launch, with no download screens.
- **Decision gate, by ~Nov 15** (before the first internal upload on ~Nov 17):
  - **The spike works:** the model loads through `openFd` → `fdopen(dup(fd))` → `fseeko(offset)` → `llama_model_load_from_file_ptr`, with acceptable memory use (RSS) on the 4 GB device. Result: **install-time, no copy.**
    - Watch out for one check: v0.9.0 can only memory-map the model if the GGUF data starts on a 32-byte boundary, and zip entries are usually only 4-byte aligned.
    - If that check fails, retry with `LLAMA_LOAD_MODE_NONE`, which reads about 491 MB into RAM. That may be too much on a 4 GB phone; measure it.
  - **The spike fails, or memory use is too high:** **install-time plus a one-time copy to `filesDir`**, hashing during the copy. This reuses the existing import loop. Accept about 1 GB on disk, and state the storage requirement in the store listing.
  - **Switch to fast-follow only if** Play review or the testers object to the install size. The switch has to happen before the closed-track submission (~Nov 19), with the foreground-service declaration and video ready. Keep it behind `ModelLocator` so the change touches one class.
- **Whatever the mode:**
  - hash the model once per `(versionCode, size)` and cache only the result;
  - keep the file-picker import in debug builds only;
  - keep the GGUF byte-identical across releases.

### 2. INTERNET permission: PROJECT.md needs correcting

PROJECT.md lists "sin permiso INTERNET" under **Validated**. That is **false**. STACK confirmed it by inspecting the library manifests, and PITFALLS by inspecting the shipped v0.5.0-preview APK: `transport-backend-cct` merges INTERNET and ACCESS_NETWORK_STATE in through ML Kit.

**Action for the roadmapper and requirements:**

- Move "no INTERNET permission" from Validated to Active.
- Make it a phase-1 requirement with these acceptance checks:
  - the merged manifest of the release AAB has no INTERNET, ACCESS_NETWORK_STATE, AD_ID or location permissions, enforced by a CI gate;
  - OCR still works on a device.
- Note that the "no data collected" Data safety answer and X2 both depend on it.

### 3. 16 KB page alignment

- **The problem:** `librepasumi_native.so` has its LOAD segments aligned to 4096 today. From **2027-02-01** Play blocks updates without 16 KB support. 1.0 may pass, but the first 1.1 update after that date would fail.
- **Fix in phase 1:** pin `ndkVersion '28.2.13676358'` (r28c).
- **CI gates:**
  - `bundletool dump config` shows `PAGE_ALIGNMENT_16K`;
  - `llvm-readelf -l` shows LOAD alignment of at least 0x4000, or `zipalign -c -P 16` passes.
- **Fallback if r28c breaks the llama.cpp build:** stay on r27 and add `target_link_options(... -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)`.

### 4. Families review timeline (critical path)

| Date (2026) | Gate | Prerequisites |
|---|---|---|
| Nov 13 | Confirm in Play Console that the 12-tester / 14-day rule does **not** apply; sign off the checklist of first-upload decisions | none |
| ~Nov 15 | Spike verdict on loading without a copy; delivery mode fixed | the spike |
| **~Nov 17** | First signed AAB on the **internal track**, proving signing, `com.repasumi`, the asset pack, 16 KB alignment, no INTERNET and R8 | Phase 1 |
| **~Nov 19** | **Closed track** for 12+ families, with App content fully declared (audience, Data safety, IARC, live privacy URL, listing, App access note). Works as a policy dry run | Phase 1 and the policy draft. Must already include the PIN gate and the guarantee that no unreviewed content reaches the child, or reviewers see a non-compliant app |
| **~Nov 24** | **Production** submission with managed publishing; this is the real 1.0 | all feature phases |
| Nov 24 – Dec 3 | Policy fixes only, no features; leaves room for about one rejection cycle | none |
| **Dec 3** | Hard deadline | none |

This leaves about 11 working days for persistence, review, the gate, the runtime and chunked generation together. The cut line in Phase 5 is part of the plan, not a fallback.

### 5. "Reto nuevo" skips parent review (decision needed)

Today "Reto nuevo" generates new practice questions and shows them to the child immediately, which breaks the core value. The options:

- **(a) Recommended for 1.0:** "Reto nuevo" becomes a parent-only action behind the PIN.
  - Its questions go into the topic's review queue as `review:'pending'`.
  - Padres shows a badge: "N preguntas nuevas por revisar".
  - The child keeps practising from the approved questions.
  - This reuses the review machinery and adds no new UI concept.
- **(b) Remove "Reto nuevo" from 1.0** (cheapest) and bring it back in v1.1.
- **(c) Not acceptable:** leave it ungated.

The user has to choose (a) or (b) before the review phase is planned.

### 6. Forgotten-PIN recovery

The researchers proposed three approaches:

- the device's screen lock, plus a reset after 24 hours (FEATURES);
- an arithmetic "adult" challenge (PITFALLS and ARCHITECTURE);
- clearing the app's data (STACK).

**Recommendation:**

- **Main path:** confirm the device's screen lock with `KeyguardManager.createConfirmDeviceCredentialIntent`. It is available on API 29 and needs no new dependency.
- **When the device has no secure lock:** allow a reset after a 24-hour delay.
- **In both cases:** write a mandatory "PIN restablecido el {fecha}" entry to the X3 tamper-evident log and show a banner in Padres.
- **When to offer it:** show "¿Has olvidado el PIN?" only after the first wrong attempt.
- **Rejected:**
  - the arithmetic challenge, because 10–16-year-olds can solve it;
  - clearing app data, because it destroys every topic and export doesn't exist until v1.1.

### Other reconciled points

- **Where the PIN is stored.**
  - The disagreement: ARCHITECTURE puts it in localStorage through `crypto.subtle`; STACK and FEATURES put it in Java SharedPreferences.
  - **Use Java:** PBKDF2 with a salt, the iteration count stored, and the lockout enforced natively.
  - Why: it keeps the PIN out of the localStorage blob, where recovery, export or any `innerHTML` injection could reach it, and it means the PIN phase no longer depends on the persistence phase.
  - The *decision* to gate still lives in the JS `dispatch()`.
  - Dangerous bridge methods are removed from release builds.
- **How durable saved data is.**
  - The disagreement: PITFALLS wants a native file copy alongside localStorage; ARCHITECTURE wants localStorage only.
  - **For 1.0, use localStorage behind `StorageAdapter`,** with:
    - a `KEY.bak` backup;
    - corrupt data quarantined;
    - immediate saves after structural actions;
    - visible save errors.
  - Why: `com.repasumi` starts with an empty origin, so 1.0 has no production data to migrate. That makes a native copy worth less for now.
  - Drop `source.pages` when a topic is published, to stay under the storage quota.
  - Revisit a `NativeFileAdapter` in v1.1 alongside export.
- **`web/` vs the assets copy.**
  - The disagreement: PITFALLS wants to delete `web/`; ARCHITECTURE keeps it as the source of truth.
  - **Keep `web/index.html` as the single file people edit,** since Playwright serves it.
  - Generate the assets copy with `tools/sync_assets.py` or a Gradle `preBuild` copy.
  - Add `.gitattributes` with `eol=lf`.
  - Stop publishing `web/` on Pages.
  - Delete `web/local-ai.js` and the browser-AI code.
- **Cancellation.**
  - The disagreement: STACK uses `abort_callback` plus an atomic flag; ARCHITECTURE uses a per-token Java callback on the same thread that returns whether to continue. Both work on the CPU backend.
  - **Use the per-token callback for the decode loop,** since it also carries progress.
  - Add `abort_callback` only if cancelling while the prompt is being processed turns out to be too slow.

## Implications for Roadmap

Coarse granularity: 3 weeks, 6 phases.

- **Parallelism:** Java/C++ phases run alongside JS phases.
- **The one rule:** two JS phases never touch `index.html` at the same time.

### Phase 1: Release foundation and Play pipeline (week 1, due ~Nov 17)

**Rationale:** it has the longest lead time, it contains choices that can't be undone, and every compliance claim depends on it. Doing it first proves signing, the asset pack and `applicationId` on real Play infrastructure while there is still time to change the delivery mode.

**Delivers:**

- **Repository:** `.gitignore` (before the keystore is created), `.gitattributes`, Gradle wrapper 8.14.7.
- **Build:**
  - `applicationId com.repasumi`, with the Java package unchanged;
  - `minSdk 29`, versionCode set by CI;
  - NDK r28c;
  - R8 with keep rules.
- **Signing:** upload key and `release-aab.yml` with a protected environment.
- **Model:**
  - the `:qwen_model` install-time pack, which CI downloads, hashes and caches;
  - the no-copy spike, leading to the `ModelLocator` decision.
- **Permissions:** remove INTERNET and ACCESS_NETWORK_STATE; add `dataExtractionRules`.
- **CI gates:**
  - manifest permissions;
  - 16 KB alignment;
  - a grep of the release build for debug paths.

  Pin llama.cpp and the GitHub Actions to commit SHAs.
- **Play:** first upload to the internal track, and the Play Console account check.

**Addresses:** D8 (removing debug paths), the E5 guard, part of D6.
**Avoids:** pitfalls 1, 2, 3/4 (the decision), 5, 14 (parity and CRLF), 20 and 22–24.

### Phase 2: Testability seam + persistence v2 (JS, weeks 1–2)

**Rationale:** review, the gate, flags and chunk merging all depend on the v2 data shape and stable uids. Reformatting to one statement per line also ends merge conflicts between parallel worktrees.

**Delivers:**

- **Testability:**
  - a reformat that doesn't change behaviour;
  - the `repasumi-core` block plus a `node:test` harness;
  - tests that pin down current behaviour (grading, spaced repetition, `validate`);
  - `verify_source.py` switched to using a temp file.
- **Schema v2** with **every** 1.0 field.
- **Stable ids:** the refactor from question index to uid, which touches about 12 functions.
- **Sample topics:** tombstones, so deleted sample topics stay deleted.
- **`StoreService`:**
  - a `.bak` backup;
  - quarantine for corrupt data;
  - a recovery screen;
  - visible save errors;
  - read-only mode for data from a newer schema.
- **Test fixtures:** data from 0.3, 0.4 and 0.5, a corrupt store, and a store near the quota.

**Addresses:** C1–C6, and the domain tests from #13.
**Avoids:** pitfalls 13 and 14, and lays groundwork for 18.

### Phase 3: Native runtime hardening (Java/C++, weeks 1–2, in parallel with Phase 2)

**Rationale:** it touches only Java and C++, so it can run alongside the JS phases. `docs/BRIDGE.md` v2 is written first, so the later JS phases can build against `FakeAndroidBridge`.

**Delivers:**

- **Runtime:**
  - `RepasumiApp` and `ModelRuntime`: one job at a time, cancellation through the token callback, the `gen_stage` / `gen_chunk` / `gen_done` events, and `snapshot()`;
  - the full `configChanges` list;
  - keep-screen-on;
  - `onTrimMemory`.
- **Model checks:**
  - a capability check (RAM, ABI, storage) that reports `model_state`;
  - a one-time SHA-256 check with the result cached.
- **OCR:** photo downsampling, structured `pages[]` output, and the `busy` fix.
- **Prompting:**
  - `PromptBuilder` and `JsonExtractor` with JUnit tests;
  - fixes the bug where `\\n` is sent literally;
  - content passed with `parse_special=false`.
- **A thread-count benchmark.**
- **The Java-side PIN API:** PBKDF2, lockout, and recovery through `KeyguardManager`.
- **Release cleanup:** `openModelPage` removed from release builds.

**Addresses:** the stability work from #2/#1, D3, D4, D6, D7 (native side), A5 and A6 (native side).
**Avoids:** pitfalls 11, 12, 15, 16 and 17.

### Phase 4: Parent gate + review/approval (JS, week 2)

**Rationale:** it needs the v2 fields from Phase 2 and the PIN bridge from Phase 3. The closed-track build (~Nov 19) should already contain at least the gate and the `studyableQuestions` guarantee.

**Delivers:**

- **Gate:**
  - the `dispatch()` refactor, with the `GATED` set and replay of the pending action after the PIN;
  - PIN setup during onboarding, automatic relock, the lockout screens, the recovery flow and changing the PIN;
  - the X3 log.
- **What the child sees:**
  - the `studyableQuestions()` choke point;
  - a unit test proving that no pending question reaches the child's study planners.
- **Review editor:**
  - backed by the store, covering all five types, with validators;
  - a progress header, the publish rule and discarding in bulk;
  - the flag queue, with an "Informar al desarrollador" email;
  - editing published topics, which resets spaced repetition when the correct answer changes;
  - drafts that can be resumed;
  - `patchProgress()`.
- **The "Reto nuevo" decision,** implemented.
- **Onboarding (D1)** and the X2 trust screen.
- **UI:** `confirm()` replaced by `modal()`.

**Addresses:** A1–A7, B1–B4, B6–B9, D1, E6, X2 and X3.
**Avoids:** pitfalls 9, 10 and 18; anti-patterns 5 and 6.

### Phase 5: Chunked generation + source validation (JS + small Java, weeks 2–3)

**Rationale:** it connects both sides, so it is the last feature phase. It needs the runtime from Phase 3 and the review model from Phase 4.

**Delivers:**

- **Chunking:**
  - `planChunks`, about 2000 characters per chunk, keeping the `Foto N` labels;
  - `startGeneration(job)`;
  - `mergeChunkResult`, safe to run twice on the same chunk.
- **Sources:**
  - `src` set deterministically from the chunk;
  - `validateSource`, with a hard page-range rule and a softer word-overlap badge.
- **Review:** evidence snippets, and questions with a ⚠ sorted first (B5/X1).
- **Drafts:**
  - `source.pages` saved with the draft;
  - "Continuar con las fotos restantes".
- **Grading:** `fill` answers graded without penalising accents, capitals or articles.
- **Optional:** a GBNF grammar per chunk.

**Addresses:** text coverage and source validation from #2, B5 and X1.
**Avoids:** pitfalls 8 and 15.

**Cut line if time slips:**

- keep the hard `src` rule and the deterministic `src`;
- drop the soft overlap scoring and the GBNF grammar.

### Phase 6: Compliance, E2E acceptance and store submission (starts in week 1, ends ~Nov 24)

**Rationale:** it has two submission milestones, the closed track (~Nov 19) and production (~Nov 24). The policy text has to reflect the final engineering facts, but drafting can't wait for them.

**Delivers:**

- **Website:**
  - a `site/` landing page and `privacidad/` at a URL that never changes, checked in CI with `curl -f`;
  - `pages.yml` pointed at `site/`.
- **Policy documents:**
  - a data inventory table, from which come the policy (hosted, and bundled offline in the app) and the Data safety answers;
  - LICENSE and third-party notices (llama.cpp MIT, Qwen Apache-2.0, ML Kit).
- **Store declarations:**
  - audience 6–17, never 18+;
  - IARC;
  - the App access note;
  - a Spanish listing with original sample content, no publisher logos, Spain only.
- **Automated tests:**
  - a Playwright smoke test (gate → generate → review → publish → study) against `FakeAndroidBridge`;
  - a test that checks every data exit.
- **Device acceptance:**
  - 10 generations in a row on the Xiaomi 12T and on the 4 GB device;
  - rotation;
  - resuming after `am kill`;
  - the release build installed from Play.
- **Closed test** with 12+ families. Warn them that their v0.5 data won't carry over.
- **Production submission.**

**Addresses:** E1–E10, the smoke test from #13, and the success metric.
**Avoids:** pitfalls 6, 7, 19, 21 and 25.

### Phase Ordering Rationale

- **Store review is the critical path.** The release pipeline (Phase 1) and compliance drafting (Phase 6) start on day 1, and the closed track doubles as the policy dry run.
- **Data shape comes before UI.** Every review, flag and gate feature reads v2 fields. Building them on the index-keyed store would mean writing them twice.
- **Parallelism follows the JS/Java boundary:**
  - P1 runs alongside P2;
  - P2 runs alongside P3;
  - P4 runs alongside the end of P3;
  - P6 runs alongside everything.

  The JS phases (P2 → P4 → P5) run one after another.
- **Never cut:**
  - the migration and recovery work in P2;
  - the `studyableQuestions` choke point and the PIN gate in P4;
  - the INTERNET and 16 KB gates in P1.
- **Can be cut:**
  - soft overlap scoring;
  - the GBNF grammar;
  - `onTrimMemory`;
  - X3;
  - X2 polish.

### Research Flags

These phases need more research during planning (`/gsd-plan-phase --research-phase`):

- **Phase 1:**
  - loading the model from a `FILE*` without a copy: the 32-byte alignment, and memory use with `LOAD_MODE_NONE`;
  - how the asset pack and the merged manifest behave under real Play delivery;
  - the Play Console foreground-service declaration, only if fast-follow is chosen.
- **Phase 3:**
  - tokens per second, peak memory and heat on the 4 GB device;
  - whether the pinned llama.cpp exposes the grammar sampler, and how the callback behaves.
- **Phase 5:** chunk size and overlap thresholds, tuned on real Spanish textbook OCR.
- **Phase 6:**
  - whether the AI-Generated Content policy applies to on-device generation that a parent reviews;
  - how to describe the AI in the IARC questionnaire;
  - the device-catalog RAM threshold for the 4 GB device.

These use standard patterns and can skip research:

- **Phase 2:** versioned migrations, `node:test` + `vm`.
- **Phase 4:** a gate in `dispatch`, forms backed by the store. The PIN recovery design is settled above.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Versions checked against release feeds; AAR manifests, `llama.h` at v0.9.0 and bundletool's `config.proto` inspected directly. MEDIUM only for loading without a copy (needs the spike) and the foreground-service declaration under fast-follow |
| Features | MEDIUM | Play policy taken from official pages. The PIN recovery and review UX are the researchers' own design. Whether the AI-content policy applies is LOW |
| Architecture | MEDIUM-HIGH | Codebase facts read directly (HIGH). Asset-pack access behaviour is from official docs. When localStorage actually flushes to disk is LOW (old Chromium sources) |
| Pitfalls | MEDIUM | INTERNET and the 4 KB alignment are HIGH (found in the shipped APK). Review timing comes from a 2019 blog post (LOW-MEDIUM). Memory and heat are not measured |

**Overall confidence:** MEDIUM-HIGH on what to build; MEDIUM on whether it fits the timeline.

### Gaps to Address

- **Loading the model without a copy:** a 1-day spike decides between install-time with no copy and install-time with a copy. It has to finish before the first internal upload.
- **Play account status:** confirm that the 14-day closed-testing rule doesn't apply. If it does, the deadline can't be met.
- **Families review time:** no current SLA was found. Assume 7+ days and submit early.
- **Performance on the 4 GB device:** tokens per second, memory use and heat are unmeasured. They set `maxChunks`, the thread count and the RAM floor.
- **OCR without INTERNET:** expected to work because the OCR model is bundled, but it has to be checked on a device after the permission is removed.
- **Scope of the AI-Generated Content policy:** build the flag-plus-email path anyway, and mention it in the notes for reviewers.
- **Photo copies:** check whether picking photos stores copies of them. That decides the policy wording.
- **The device-catalog RAM value** for the 4 GB test device, which sets the exclusion threshold.
- **Where the sample topics came from:** check that they are original and not copied from textbooks.
- **Decisions still pending from the user:**
  - "Reto nuevo", option (a) or (b);
  - confirming the PIN recovery design;
  - free vs paid, which is permanent once uploaded.

## Sources

### Primary (HIGH confidence)

- **Direct inspection:**
  - `app-debug.apk` v0.5.0-preview: the merged manifest contains INTERNET and DataTransport; `librepasumi_native.so` has LOAD alignment 4096.
  - AARs and POMs for `asset-delivery` 2.3.0, `work-runtime` 2.9.1, `mlkit/common` 18.11.0 and `transport-backend-cct` 2.3.3.
  - llama.cpp v0.9.0 `include/llama.h` and `src/llama-model-loader.cpp`.
  - bundletool `config.proto`.
- **Repository code:** `web/index.html`, `MainActivity.java`, `NativeGguf.java`, `local_llm.cpp`, `app/build.gradle`, `CMakeLists.txt`, `tools/verify_source.py` and the workflows.
- **developer.android.com:** the Play Asset Delivery overview and Java integration, 16 KB page sizes, AGP 8.13 release notes, target-SDK requirements, Auto Backup.
- **support.google.com/googleplay:** size limits (9859372) and app testing requirements (14151465).
- **Release feeds:** Gradle, GitHub Actions, Playwright, bundletool and Node.

### Secondary (MEDIUM confidence)

- Play policy pages:
  - Families policy (9893335, 17122218);
  - AI-Generated Content policy (13985936);
  - Data safety (10787469);
  - User Data (10144311);
  - Target audience (9867159);
  - device catalog (1286017);
  - Play for On-device AI.
- ML Kit's Android data disclosure page.
- OWASP guidance on PBKDF2 iteration counts (via secondary summaries).

### Tertiary (LOW confidence)

- An Android Developers Blog post from 2019 saying Families extended review takes up to 7 days.
- Chromium code reviews 1129233003 and 1140013003, on when WebView flushes DOM storage to disk.
- Practitioner write-ups:
  - llama.cpp on Android: thread count, SIGILL with `+dotprod`, low-memory-killer behaviour;
  - the Ertas write-up on GGUF delivery;
  - the ProAndroidDev write-up on shipping Gemma through Play.
- Reports of upload-key resets.

---
*Research completed: 2026-11-13*
*Ready for roadmap: yes*
