# Pitfalls Research

**Domain:** Offline, child-directed Android app (WebView SPA + JNI llama.cpp + ML Kit OCR) launching on Google Play under the Families policy, with a ~491 MB on-device LLM delivered by Play Asset Delivery. Hard deadline 2026-12-03.
**Researched:** 2026-11-13
**Confidence:** MEDIUM overall. Two findings are HIGH because they come from direct inspection of the current `v0.5.0-preview` APK (see Pitfalls 1 and 2). Play policy and PAD facts come from official Google pages (MEDIUM per the classify-confidence seam). Runtime behaviour of LLMs on 4 GB devices comes from llama.cpp/Android experience and is not benchmarked here (MEDIUM/LOW, flagged per item).

> **Reading guide for the roadmapper.** Phase names below are descriptive because the roadmap does not exist yet. Suggested mapping:
> - **P-Release**: release foundation (identity rename, signing, versionCode, 16 KB, permission gate, CI AAB) (#16)
> - **P-Model**: model delivery through Play Asset Delivery (#16)
> - **P-Gen**: generation stability on 4 GB devices (#2, #1)
> - **P-Quality**: chunked generation and source validation (#2)
> - **P-Data**: versioned persistence and migrations (#6)
> - **P-Parent**: parental PIN gate and review/approval editor (#5, gate)
> - **P-Tests**: minimum automated tests and CI gates (#13)
> - **P-Store**: compliance artifacts, Play Console declarations and submission (#14)
>
> **The most important roadmap implication:** store review is on the critical path, and every *update* of a Families app also gets an extended review (up to 7 days or longer). Compliance and the release pipeline must come first, not last. The build that passes review has to be the real 1.0. A fix after approval costs another review cycle of several days.

---

## Critical Pitfalls

### Pitfall 1: The shipped APK already has the INTERNET permission (ML Kit telemetry), which contradicts the core privacy claim

**What goes wrong:**
`PROJECT.md` lists "sin permiso INTERNET" as *Validated*, and the README, the privacy promise and the planned Data safety answer ("no data collected") all depend on it. **It is false today.** I inspected the binary manifest of the current CI release asset `app-debug.apk` (v0.5.0-preview, updated 2026-11-12). Its merged manifest contains:
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `com.google.android.datatransport.cct.CctBackendFactory`, `TransportBackendDiscovery`, `JobInfoSchedulerService`, `AlarmManagerSchedulerBroadcastReceiver`

These come in transitively from `com.google.mlkit:text-recognition:16.0.1` (Google DataTransport, the channel ML Kit uses for telemetry). ML Kit's own data-disclosure page says it collects device information (manufacturer, model, OS), application information (package name, version), performance metrics and API configuration, and sends them over HTTPS. With INTERNET present, the app *does* transmit data off the device. That counts as "collection" for Data safety, and it falsifies "nada sale del dispositivo" in the privacy policy.

**Why it happens:**
The app's own `AndroidManifest.xml` does not declare INTERNET, so everyone assumes it is absent. Manifest merging silently adds library permissions, and nothing in CI inspects the merged manifest (CONCERNS.md: "Nothing in CI checks this today").

**Consequences:** Data safety, privacy policy and actual behaviour disagree. For a child-directed app, that is grounds for rejection or later removal ("misrepresenting app information may lead to removal or suspension"), and it breaks the product's core value.

**How to avoid:**
1. In `app/src/main/AndroidManifest.xml`, add the `tools` namespace and:
   ```xml
   <uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" tools:node="remove" />
   ```
   **Do not** remove the DataTransport `service` or `receiver` components. If ML Kit then schedules a job against a component that no longer exists, `JobScheduler.schedule` can throw and crash the app. Without network access the upload simply fails and stays silent.
2. Re-test OCR end to end on a physical device after removing the permissions. The bundled model is local and should keep working, but verify it (LOW confidence until tested).
3. Add a CI gate on the **release AAB** (not the debug APK): `bundletool dump manifest --bundle app-release.aab` must not contain `android.permission.INTERNET`. The same gate also catches anything the Play Asset Delivery library might add.
4. Remove `ACCESS_NETWORK_STATE` as well, so the "offline" claim is exact.

**Warning signs:** a `uses-permission` you did not write appears in `app/build/intermediates/merged_manifests/`. The Play Console "App bundle explorer" lists INTERNET. The Play listing shows "Network access".

**Phase to address:** P-Release (gate in CI), with P-Store using the result. **Fix this first.** Every compliance artifact depends on it.

---

### Pitfall 2: The llama.cpp native library is not 16 KB page-aligned

**What goes wrong:**
In the same APK, `lib/arm64-v8a/librepasumi_native.so` has ELF `LOAD` segments aligned to **4096** bytes. ML Kit's `libmlkit_google_ocr_pipeline.so` is correctly aligned to 16384. The official Android guide says: "all apps targeting Android 15 (API level 35) and higher must support 16 KB memory page sizes on 64-bit devices on Google Play. **Starting February 1, 2027**, if your app updates don't support 16 KB … you won't be able to release these updates." The app targets SDK 36.

So 1.0 may get through with a Console warning, but **the first 1.1 update after 2027-02-01 would be blocked**. On 16 KB devices (newer Pixels and other Android 15+ devices) the app also runs in "page size compat mode" and shows a warning dialog at launch, which is a poor first impression for a parent.

**Why it happens:** `app/build.gradle` pins no `ndkVersion`. The CMake build uses the AGP default NDK, which here produced 4 KB alignment (NDK r27 or lower without the flags). Only NDK r28+ aligns to 16 KB by default.

**How to avoid:**
- Pin `ndkVersion` to r28 or later in `app/build.gradle` (preferred), **or** add `target_link_options(repasumi_native PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")` in `CMakeLists.txt`. Because llama/ggml are static (`BUILD_SHARED_LIBS OFF`), only the final `.so` link needs the flags.
- Make sure AGP is 8.5.1 or later (8.13.2 is fine) so uncompressed libs are zip-aligned to 16 KB inside APKs that Play generates.
- Add a CI check: `zipalign -c -P 16 -v 4` on an APK built from the AAB (`bundletool build-apks --mode=universal`), plus `llvm-readelf -l` asserting `LOAD` align >= `0x4000`. Also check the RELRO end alignment, as the guide describes.

**Warning signs:** "16 KB" warning in Play Console or in the Android Studio APK Analyzer; `LOAD ... align 2**12`.

**Phase to address:** P-Release. It is a one-line fix with a CI assertion, so do it in the first week.

---

### Pitfall 3: Choosing install-time PAD for a model that llama.cpp must open by file path

**What goes wrong:**
Install-time asset packs are served as **split APKs**. The official integration guide says you read them "using the Java AssetManager API" (`assetManager.open(...)` returns an `InputStream`). **There is no filesystem path.** `llama_model_load_from_file` needs a path and mmaps the file; it cannot take an `InputStream` or an fd with an offset into a zip. The only way to use install-time delivery is to **copy** 491 MB from the split APK into `filesDir` on first launch. That means:
- ~1 GB of storage used permanently (split APK plus the copy). That is a real problem on the 4 GB, often low-storage devices the app targets.
- A first-launch copy plus SHA-256 that takes tens of seconds, can be interrupted by process death, and needs resumable or atomic handling.

**Why it happens:** "install-time = available at launch, no library needed" sounds simplest, and the AssetManager-only constraint is easy to miss.

**How to avoid (opinionated):** Use a **`fast-follow`** asset pack.
- Play downloads it automatically right after install (the user does not need to open the app). It is expanded to internal storage, and `AssetPackManager.getPackLocation(pack).assetsPath()` gives an absolute path that llama.cpp can mmap directly, with **no duplicate copy**.
- It needs `com.google.android.play:asset-delivery` (2.3.0 per the current guide). Re-run the Pitfall 1 permission gate after adding it.
- Per-pack limit is 1.5 GB (Play size-limits page), so 491 MB fits with wide margin.
- Do **not** adopt "Play for On-device AI" AI packs for 1.0. They are still beta and require a different library. They are worth revisiting in 1.1 for RAM-based device targeting.

**Warning signs:** a design doc that says "copy model from assets on first run"; storage usage around 1 GB in App info.

**Phase to address:** P-Model. Decide this before writing any loading code.

---

### Pitfall 4: Treating the fast-follow pack as "always there" (the Play delivery state machine)

**What goes wrong:** The app works in Android Studio and in a sideloaded build, then fails for real users from Play. Specific failure modes from the official guide:
- **The app can be opened before the pack finishes**: "These downloads do not prevent the user from entering the app."
- **Larger than 200 MB on mobile data**: the download "does not start until the user explicitly gives their consent." The state is `WAITING_FOR_WIFI`, and if the app never calls `showConfirmationDialog()`, the download only resumes on Wi-Fi. A family installing the app on 4G sees "IA no disponible" indefinitely.
- **`REQUIRES_USER_CONFIRMATION`** when Play does not recognise the install (sideloaded, or some tester flows). Accepting it updates the app.
- **App updates invalidate the pack**: "All previously-downloaded asset packs are invalidated" during an update, then a patch is applied. A path cached from the previous launch is wrong. The guide says "Do not rely on cached asset pack locations between app launches."
- **"Clear storage" in Settings deletes the pack.** The app must call `fetch()` again. Play performs the download, so the app still needs no INTERNET permission.
- **Changing even one byte of the GGUF in a later release** makes every user download the model again. Keep the model file byte-identical across releases unless the model actually changes.

**How to avoid:**
- Build a single `ModelAvailability` state machine in Java (`NOT_INSTALLED`, `PENDING`, `DOWNLOADING(pct)`, `WAITING_FOR_WIFI`, `NEEDS_CONFIRMATION`, `TRANSFERRING`, `VERIFYING`, `READY`, `FAILED(code)`) and send it to the SPA as one event type. Re-query it on every `onResume`, never only at startup.
- Use the current `showConfirmationDialog(ActivityResultLauncher)` API. That pushes the migration from `startActivityForResult` (deprecated, CONCERNS.md) into this phase.
- Verify SHA-256 **off the main thread** once per (pack versionCode, file size, mtime) and cache only the verification verdict, never the path. Show "Comprobando el modelo…" progress.
- Keep the manual GGUF import, and the Hugging Face link, **only** in debug builds. Release builds must contain no external link (Pitfall 9).

**Warning signs:** testing only with `Run ▶` from Android Studio, which deploys an APK *without* asset packs unless configured to deploy from the bundle. QA never tried a mobile-data install. Nobody has tested "Clear storage".

**Phase to address:** P-Model. Test matrix: `bundletool build-apks --local-testing` for the code paths (in local-testing mode, treat fast-follow as if it needs an explicit `fetch()`, MEDIUM confidence), then the **internal testing track** for the real Play path, then the closed track.

---

### Pitfall 5: Irreversible Play Console decisions made casually on the first upload

**What goes wrong:** Several choices are permanent once the first AAB is uploaded to *any* track, internal testing included:
- **Package name** `com.repasumi` can never be changed, reused or deleted.
- **App signing key**: Play App Signing is mandatory for AABs. Let Google generate the app signing key (the default). Never upload your own app signing key that exists only in CI.
- **Free vs paid**: an app published as free can never become paid. Since the revenue model is undecided, choosing "free" rules out a paid-upfront model forever (IAP later stays possible, but in a Families app IAP needs its own gating).
- **versionCode**: every uploaded bundle consumes its versionCode permanently, even if the release is discarded. The hand-edited `versionCode 7` will collide on retries.

**Upload key specifics:**
- GitHub Actions secrets are **write-only**: you cannot read them back. If the only copy of the upload keystore lives in GitHub Secrets, you have effectively lost it for any build outside CI. Keep an offline encrypted backup (password manager, plus a second medium) before the first upload.
- A lost upload key *can* be reset through Play Console. But no new AAB can be uploaded until Google processes the reset (hours to days, LOW confidence on timing), which is fatal if a hotfix is needed during the launch window.

**Rename-specific traps (the identity is split across three names, CONCERNS.md):**
- If the Java package or `namespace` changes, the JNI symbol names in `local_llm.cpp` (`Java_com_davidop_estudiaenelbus_NativeGguf_*`) must change in the same commit. Otherwise `System.loadLibrary` succeeds, but the first native call throws `UnsatisfiedLinkError`. That error is caught as `LinkageError` and appears as a generic "Error de inferencia", so the cause is hidden. **Recommendation: change only `applicationId` for 1.0** (allowed independently of `namespace`) and leave the Java package and namespace alone. That removes the JNI risk entirely.
- `minifyEnabled true` (planned) lets R8 strip or rename `@JavascriptInterface` methods and JNI-called members. The JS then sees `AndroidBridge.x` as `undefined` **only in release**. Add explicit keep rules: `-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }` and `-keepclasseswithmembernames class * { native <methods>; }`, plus keeps for any class the C++ side looks up by name.
- In AGP 8+, `BuildConfig` is not generated by default. A `BuildConfig.DEBUG` guard needs `buildFeatures { buildConfig true }`.
- The debug variant uses `applicationIdSuffix '.preview'`, so testing only debug misses every release-only behaviour (minify, signing, PAD, gating of the debug-only import).
- The new applicationId is a new app with a new data directory. Testers of `com.davidop.estudiaenelbus.dev` / `.preview` **lose all their topics and progress**, and export/import (#11) is out of scope. Tell the closed-test families before they start.

**Warning signs:** the first upload is attempted "just to see"; the keystore exists only in Secrets; release builds are never installed on a device before submission.

**Phase to address:** P-Release. Write a one-page "irreversible decisions" checklist and get it signed off before the first upload. versionCode derives from CI (`github.run_number` plus an offset above 7).

---

### Pitfall 6: Underestimating Families review time and its effect on the schedule

**What goes wrong:** Google's guidance for apps with children in the target audience: extended reviews on **every update**, "up to 7 days (or longer in certain exceptional circumstances)". Google also advises submitting at least a week before launch and not resubmitting while a review is in progress (MEDIUM/LOW: the source is the 2019 Android Developers Blog post; no current SLA was found). The deadline is 20 days away (2026-12-03). One rejection plus a fix plus a re-review takes about 7 to 10 days.

**Related facts:**
- The 12-tester/14-day closed-testing requirement applies only to personal accounts created after 2023-11-13. The project says its account predates that, so production access is not gated. **Confirm this in Play Console now.** If the Console shows the requirement anyway, the deadline is already impossible.
- Closed-track releases also go through review and surface the same policy problems. Use that to find rejections early.
- Testers on **supervised child accounts (Family Link)** may not be able to join test programs (LOW confidence). Have parents install the app on their own account and device.

**How to avoid:** Use a back-planned calendar:
| Date (2026) | Gate |
|---|---|
| by Nov 17 | First signed AAB on the **internal testing** track (validates signing, PAD fast-follow, 16 KB, permission gate, R8). |
| by Nov 19 | App content fully declared (target audience, Data safety, IARC, privacy policy URL live, store listing). **Closed track** release submitted to 12 families. This is the policy dry run. |
| by Nov 24 | **Production** submission with managed publishing on. Leaves about 9 days for one rejection cycle. |
| Nov 24 to Dec 3 | Only policy fixes. No features. |

**Warning signs:** compliance work scheduled in the last phase; "we'll fix it in an update right after launch".

**Phase to address:** Roadmap ordering. P-Release and P-Store start in week 1. P-Store has two milestones (closed-track submission, production submission), not one.

---

### Pitfall 7: Data safety, privacy policy, in-app policy and real behaviour disagree

**What goes wrong:** Reviewers compare the Data safety form, the hosted privacy policy, the store listing and the app's real behaviour. Repasumi has several subtle mismatch sources:
1. INTERNET via ML Kit (Pitfall 1). If it is not removed, ML Kit collection must be declared and the "nada sale del móvil" copy is false.
2. **On-device processing is not "collection"** (Google: "Collect" means transmitting data off a user's device). But **passing data to another app on the device is "sharing"** unless the user initiates it in a way they would expect. Inventory these exits:
   - `copy-report` to the clipboard (child name, results). Parent-initiated, so it is fine *only if* behind the PIN and disclosed in the policy.
   - TTS: question text goes to the system TTS engine (another app). Treat it as user-initiated read-aloud and mention it in the policy.
   - Any "Informar al desarrollador" email intent (Pitfall 10). When a parent chooses to send it, the developer receives content. Disclose that.
3. **`allowBackup="false"` does not block device-to-device transfer on Android 12+** (official Auto Backup docs: "device-to-device migrations are still permitted"; without a `<device-transfer>` rule, *all* app data moves). Write an explicit `dataExtractionRules` file. Decide whether WebView storage (`app_webview/`) and the asset pack folder are excluded, and make the policy say the same thing.
4. The privacy policy must be reachable at a stable public URL, as HTML (not PDF), and must name the app and developer contact. The plan to restructure GitHub Pages (landing plus privacy only) can **break the URL** that is already in Play Console. Fix the final URL first, then never move it.
5. Play's user-data rules expect the policy to be available **inside the app** as well. Do not link out (that is a gated external link in a kids' app). Embed the same text offline in the Padres area and keep it in sync with the hosted copy, for example with a CI diff.

**How to avoid:** Keep one `docs/privacy/` source rendered both to Pages and into the SPA. Put a "data inventory" table in the repo (data item, where stored, leaves the device? which app?) and derive both the policy and the Data safety answers from it.

**Warning signs:** the policy is written from memory instead of from the merged manifest and code; the Pages workflow changes on the same day as submission.

**Phase to address:** P-Store, which depends on P-Release (Pitfall 1) and P-Parent (gated exits).

---

### Pitfall 8: The OCR-to-LLM pipeline feeds children confident, wrong "facts" with a fake "Fuente"

**What goes wrong:** Qwen2.5-0.5B Q4 in Spanish has a high error rate: invented facts, wrong numbers, multiple-choice distractors that are *also* correct, true/false statements broken by negation, "ordenar" items with no single valid order, and fill-in answers that the grader rejects because of accents, case or articles ("Ebro" vs "el Ebro"). Today `src` is only length-checked, and the GBNF on the web side allows "Foto 1" to "Foto 99" regardless of the photo count (CONCERNS.md). Children trust the "Fuente: Foto 3" line more than the question itself.

**Why it happens:** The pipeline validates *shape* (JSON) and never *grounding*. Only the first ~1200 OCR characters are used, so most of the book is never seen while the questions still look authoritative.

**How to avoid:**
- **Grammar-constrain the native path** (llama.cpp sampler supports GBNF). Today the native path has no grammar, so the output is only regex-extracted between `{` and `}`.
- Chunk by page (the `Foto N:` markers exist). Generate per chunk, and set `src` **deterministically from the chunk** instead of asking the model for it. That removes the citation hallucination by construction.
- Grounding check: normalise text (lowercase, strip accents and punctuation) and reject any question whose answer tokens (and, for mc, the correct option) do not appear as an n-gram in the cited page. Reject mc items where two options normalise to the same string.
- Grader tolerance for `fill`: normalise accents, case, leading articles and surrounding punctuation before comparing. Store accepted variants per question.
- **The parent approval gate is the real safety net.** Nothing generated reaches the child view until a parent approves it (#5). Show the cited page's OCR snippet next to each question during review so the parent can verify it in seconds.
- Label generated content in the UI as generated by AI and reviewed by the parent.

**Warning signs:** the share of generated questions rejected by the grounding check is unknown; review takes longer than about 30 s per question (the parent will start rubber-stamping); testers report "the answer was right but the app said wrong".

**Phase to address:** P-Quality (grounding, grammar, deterministic src). P-Parent (approval gate with evidence snippet). P-Tests (golden fixtures of OCR to expected rejections).

---

### Pitfall 9: The parental gate doesn't cover every exit or sensitive action, or locks parents out

**What goes wrong:** Reviewers tap through the child-facing UI looking for anything that leaves the app or exposes data. Known exits today: `AndroidBridge.openModelPage()` (an `ACTION_VIEW` to huggingface.co), `copy-report`, delete topic and delete student (native `confirm()`), and the Padres tab one tap from child navigation. Common misses in this domain: a privacy-policy or licence link, "rate the app" or "more apps" links to the Play Store, a mailto or email intent for feedback, the system TTS "install voice data" intent (opens Settings or Play), and the photo picker and file chooser from the "Añadir" flow.

PIN-specific traps:
- **No recovery path.** There are no accounts, `allowBackup` is false, and reinstalling wipes everything. A forgotten PIN means the parent loses all data or is locked out of review. Provide an adult recovery challenge (for example a randomised multi-digit multiplication shown in words) that resets the PIN without deleting data.
- The PIN stored in plain text in `localStorage` lets an older child read it through any future XSS. Store a salted hash (WebCrypto `SHA-256` is enough at this threat level).
- A gate implemented only in the SPA while the bridge methods (`copyText`, the picker, `openModelPage`) stay callable from any view. Remove dangerous bridge methods from release builds instead of relying on JS gating.
- An arithmetic gate that is too easy for a 10 to 16 year old ("2+3"). The target audience includes teens, so a parent-set PIN is the right primary gate.

**How to avoid:** Inventory every `startActivity*`, `ACTION_*` intent and `href` in a test that fails when a new exit appears. Today the grep finds only the Hugging Face `ACTION_VIEW` and three picker intents, and no `http` links in `index.html`. Gate: Padres, Añadir (photos, generate, review), delete, export, and any link or intent.

**Phase to address:** P-Parent, with a P-Tests test that inventories exits.

---

### Pitfall 10: The AI-generated content policy's in-app reporting requirement in an app that cannot phone home

**What goes wrong:** Google Play's AI-Generated Content policy: "Apps that generate content using AI must contain in-app user reporting or flagging features" that "allow users to report or flag offensive content **to developers** without needing to exit the app", and must prevent restricted content. The 2023 announcement mentioned exemptions for productivity and summarisation apps, but the current policy page lists none. Treat Repasumi as in scope. The existing 🚩 flag is stored locally and never reaches the developer.

**How to avoid (works offline, no INTERNET):**
- Keep 🚩 on every generated question (child and parent). The child's flag hides the question from the child's queue and adds it to the parent's review list.
- In Padres (behind the PIN): "Informar al desarrollador" composes a report (question text, model or app version, **no child name or results**) and hands it to an email app via `ACTION_SENDTO`. It is parent-initiated, so it counts as user-initiated. Disclose it in the privacy policy. Mention the flagging and reporting mechanism in the review notes for Play reviewers.
- Restricted content: the model only rewrites content from the user's own textbook pages, but sensitive curricular topics (history, biology/sexuality in ESO) can still produce inappropriate phrasing. A small Spanish blocklist check on generated text before the parent review, plus the mandatory approval gate, gives a defensible "prevent" story.

**Warning signs:** the store listing describes "IA" but the app has no visible report path; reviewers cannot find the flag because it is hidden in practice mode.

**Phase to address:** P-Parent (flag routing and report action) and P-Store (review notes and listing copy). LOW to MEDIUM confidence on how strictly reviewers apply this to non-chat apps, so build it anyway. It is cheap.

---

### Pitfall 11: Out-of-memory, low-memory kills and duplicate model instances on 4 GB devices

**What goes wrong (MEDIUM, from llama.cpp and Android memory behaviour, not yet measured on the target devices):**
- **Rotation, and *any* config change, loads a second model.** `onDestroy` queues `close()` on the *old* executor, behind a generation that may still run for 90 s. The new Activity creates a new `NativeGguf` with a new executor and loads the model again. Two contexts, two KV caches and two compute buffers coexist, and 8 inference threads compete for 4 big cores. Adding `android:configChanges="orientation|screenSize"` is not enough: dark-mode toggle (`uiMode`), font scale, display size (`density`), locale, `screenLayout`/`smallestScreenSize` (foldables, split screen) and `keyboard` also recreate the Activity.
- **Process death in the background.** A parent switches to WhatsApp during a multi-minute chunked generation. On 4 GB devices the cached process is killed. On return, Android restores the Activity, the WebView reloads, and in-memory review drafts and partial results are gone.
- **Peak memory stacking.** ML Kit decoding 12 to 50 MP photos at full resolution (CONCERNS.md), plus the model mmap (file-backed, reclaimable but then re-read, which slows decoding), plus the KV cache and compute buffers (anonymous memory), plus the WebView renderer process. Doing OCR and LLM in the same session without releasing the recogniser raises the peak.
- A full `render()` with `innerHTML` on every progress event adds GC churn in the renderer.

**How to avoid:**
- Hold the model in a **process-scoped singleton** (Application-level holder with its own executor). The Activity only subscribes. Results are kept and replayed as the last state when a new WebView attaches. Model lifetime is: load on demand, keep for N minutes, free on `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL / UI_HIDDEN when idle)`.
- Declare a complete `configChanges` list *and* still make recreation safe (singleton). Do not lock orientation as the only fix: tablets in landscape are a realistic family setup.
- **Checkpoint per chunk:** persist each chunk's validated questions as a draft before starting the next one. After process death the UI offers "Continuar donde lo dejaste". Prefer this to a foreground service: Android 14+ requires a typed FGS, `shortService` caps at about 3 minutes, and notifications need the `POST_NOTIFICATIONS` runtime prompt, which is friction in a kids' app.
- Use `FLAG_KEEP_SCREEN_ON` during generation and tell the parent clearly to keep the app open.
- Downsample photos to about 2400 px with `inSampleSize` before OCR, `recycle()`, and close the recogniser before loading the model.
- Gate at runtime: `ActivityManager.MemoryInfo.totalMem` < ~3.5 GiB (a "4 GB" phone reports less) means a clear "tu móvil no llega al mínimo" message. In Play Console, use **Device catalog exclusion rules by RAM** to keep the app off sub-4 GB devices. `abiFilters arm64-v8a` already filters by ABI. Settle `minSdk` at 29: `PowerManager` thermal APIs need 29, and README, CONCERNS and the issues currently disagree (26 vs 29 vs 13+).

**Warning signs:** `adb shell dumpsys meminfo com.repasumi` shows two native heaps of a similar size after a rotation; `logcat` shows `lowmemorykiller` / `ActivityManager: Killing ... (adj 900+)` during tests; the "10 consecutive runs without ANR" acceptance (#2) has still not run.

**Phase to address:** P-Gen.

---

### Pitfall 12: Thermal throttling and the wrong thread count make generation slow on real devices

**What goes wrong (MEDIUM):** Sustained 4-thread CPU inference throttles within about 30 to 90 s on mid-range SoCs, and tokens/s can drop by a large fraction. Chunked generation over 30 photos means minutes of sustained load on a device in a child's hands. `n_threads = 4` is hard-coded. On 2+6 big.LITTLE parts (common in 4 GB phones) two of those threads run on little cores and slow the whole batch. Decoding is memory-bandwidth bound, so more threads is not faster.

**Related build trap:** with `GGML_NATIVE OFF` and `GGML_CPU_ALL_VARIANTS OFF`, the library builds for baseline ARMv8-A (no dotprod or i8mm kernels), which is slower. The tempting fix, `-march=armv8.2-a+dotprod`, **crashes with SIGILL on Cortex-A53/A73-class SoCs** that still ship in budget 4 GB phones. Keep the baseline for 1.0 unless you add runtime CPU-feature dispatch.

**How to avoid:**
- Set threads = number of performance cores (read `/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq`), clamped to 2 to 4. Benchmark 2 vs 4 on the 4 GB test device.
- `PowerManager.addThermalStatusListener` (API 29). At `THERMAL_STATUS_SEVERE`, pause between chunks and show "El móvil está caliente, esperamos un momento".
- Emit stage events (load, tokenise, generate with tokens/s) and record them in `docs/perf/` per device run. That is #2's acceptance evidence.

**Phase to address:** P-Gen.

---

### Pitfall 13: localStorage data loss that migrations don't cover

**What goes wrong:**
- **Origin change equals total data loss.** WebView localStorage is keyed by origin. The app loads `https://appassets.androidplatform.net/assets/index.html`. Changing `ORIGIN` (to `file://`, a custom domain, or another `WebViewAssetLoader` host) silently orphans every family's data. Freeze it and add a unit test that asserts the constant.
- **Write durability.** Chromium batches DOM-storage commits, and WebView historically did not flush DOM storage at shutdown the way Chrome does (Chromium review 1129233003). A low-memory kill shortly after `setItem` can lose the last writes, such as "tema aprobado" or session results (MEDIUM/LOW: current WebView flush timing not verified). This matters more here because LLM work makes those kills likely.
- **Quota.** Plan for about 5 MB per origin. Storing per-page OCR text for source validation and review previews (30 pages × 3 to 5 k chars, UTF-16) adds about 300 KB per topic. Twenty topics plus 700 sessions per learner exceeds the quota, and `save()` then fails. A migration that writes a full backup copy (`KEY.bak`) next to the live store *doubles* usage at exactly the wrong moment.
- **Migration traps:** non-idempotent steps that re-run after a crash mid-migration; giving questions random `uid`s before re-keying index-based stats and flags (all history detaches); the built-in topic re-injection (lines 305 to 306) running *before* migrations and re-adding deleted seeds; the `slice(-60)` trim running before uids exist.
- **"Clear data" and uninstall delete everything** (and with fast-follow, the model too). With export/import deferred to 1.1, the first-run screen and the policy should say so honestly.

**How to avoid:**
- Ordered `migrations[]` keyed by `schemaVersion`. Run them on a *copy* in memory, validate, then write once. Keep the pre-migration raw string in a **native file** through a bridge method (atomic `write tmp → fsync → rename`), not in localStorage. Same for corrupt-store quarantine (CONCERNS.md: "Silent loss of all writes").
- Deterministic uid at migration: a hash of `topicId + normalised question text`. Re-key `stats`, `flags`, `queue` and `practiceHistory` in the same migration step, before any trim.
- Store bulky OCR page text in native files (`filesDir/topics/<id>/page-N.txt`) through the bridge. Keep only evidence snippets in the JS store.
- For critical transitions (approve topic, finish session), also mirror a snapshot to the native file. On boot, if localStorage is missing or older than the snapshot, restore from the file. That also covers the commit-delay case.
- Fixtures: capture real v0.5 stores from testers' devices (with consent, anonymised) plus synthetic 0.3/0.4 stores, a corrupt store, and a near-quota store.

**Warning signs:** a migration test passes only against empty or fresh stores; no test kills the process right after a save; `QuotaExceededError` never appears in tests.

**Phase to address:** P-Data, with P-Tests fixtures.

---

### Pitfall 14: Adding tests breaks the web/Android byte parity, or tests the wrong runtime

**What goes wrong:**
- **Line endings.** There is no `.gitattributes`. On Windows (Git for Windows defaults to `core.autocrlf=true`), editors and worktrees can write CRLF into one copy, so `verify_source.py` fails in CI or, worse, passes locally and fails on Linux. Multiple parallel Claude sessions and worktrees on Windows make this likely.
- **Exporting for tests.** Making the IIFE "testable" by adding `module.exports`/ES exports changes behaviour in the WebView or breaks `node --check`. Exposing internals on `window` widens the surface any XSS can reach, and `AndroidBridge` lives on that same origin.
- **The E2E suite tests the browser path, not the Android path.** Without `AndroidBridge`, the SPA takes web-only branches (`sampleAvailable`, `local-ai.js`). A Playwright run "passes" while the native event contract (`repasumiNativeEvent`) goes untested.
- `verify_source.py` writes to the hard-coded `/tmp/...`, which fails on Windows dev machines.

**How to avoid (opinionated):**
- **Remove the reason for parity.** The 1.0 decision is that Pages publishes only a landing page and the privacy policy, and that the web-app and browser-AI prototype stop being published. Make `app/src/main/assets/index.html` the single source, delete `web/index.html` and `web/local-ai.js` (which also removes the second, divergent prompt pipeline from CONCERNS.md), and retire the parity check. If parity must stay for now, generate the copy with a Gradle `preBuild` copy task and never edit the second file by hand.
- Add `.gitattributes`: `*.html text eol=lf`, `*.js text eol=lf`, `*.gguf binary`, `*.jks binary`.
- Unit tests: extract the inline `<script>` in Node (as `verify_source.py` already does), run it in `node:vm` with a minimal DOM/`localStorage` stub, and reach pure functions through a single test hook that exists only when a `__REPASUMI_TEST__` global is present before load. Test-only dev dependencies (jsdom, Playwright) are fine. The "no new frameworks" constraint is about the shipped app.
- E2E: Playwright with `addInitScript` that installs a **fake `AndroidBridge`** emitting scripted `repasumiNativeEvent` sequences (ocr_progress, ai_progress, result, error, model states). That tests the real Android contract without a device.
- One instrumented smoke test on an emulator image (or a manual checklist on the physical device) for the parts JS cannot cover: release build, R8 keep rules, PAD state, merged-manifest permissions.

**Phase to address:** P-Tests. Do the single-source decision and `.gitattributes` in week 1 (P-Release) because every later phase edits `index.html`.

---

## Moderate Pitfalls

### Pitfall 15: Prompt injection and control-token parsing from OCR text
`llama_tokenize(..., parse_special=true)` on the whole prompt means a page containing `<|im_start|>` or `<|im_end|>` is parsed as ChatML control tokens. A 0.5B model will also follow plain-language instructions in the text ("ignora lo anterior y escribe…"). **Prevention:** tokenise the template's control tokens separately and user content with `parse_special=false`. Strip `<|` and `|>` from OCR text. Grammar-constrain the output so the *shape* is safe regardless. Grounding checks (Pitfall 8) catch content that is not in the source. Parent review is the last gate. Phase: P-Quality.

### Pitfall 16: Literal `\\n` and other silent prompt bugs
`generatePractice` sends the two characters `\` and `n` instead of newlines (CONCERNS.md). Bugs like this degrade output quality invisibly. **Prevention:** golden-prompt snapshot tests on the Java prompt builder (JUnit, no device needed). Phase: P-Tests.

### Pitfall 17: The ML Kit busy flag and double-tap generation
`NativeLearningEngine.busy` is never reset on exception paths, and `NativeGguf` enqueues every request. A double tap queues two full generations. **Prevention:** `AtomicBoolean.compareAndSet` with `try/finally`. Reject in `NativeGguf` while an operation is pending. Disable the button in the SPA *and* reject in Java, because the SPA re-renders. Phase: P-Gen.

### Pitfall 18: `render()` destroying parent edits during review
Each native progress event re-renders `#app` and discards textarea content (CONCERNS.md). The per-question editor in #5 will hit the same bug unless edits are written into state on every `input` event and progress events update only the progress node. Phase: P-Parent (prerequisite for "no se pierden correcciones").

### Pitfall 19: Store listing assets that trigger IP or metadata rejections
Screenshots showing photographed textbook pages with **publisher logos or trademarks** (Santillana, Anaya, SM…) or copied textbook passages invite IP-policy problems. Built-in sample topics should be original text, not textbook excerpts. Check where "¡No es justo!" and the other seeds came from. Avoid superlative or accuracy claims ("100 % fiable", "la mejor IA"), keyword lists and any mention of the discontinued web version. Required assets: 512×512 icon (adaptive launcher icon still pending), 1024×500 feature graphic, phone screenshots, Spanish (es-ES) default listing. Restrict countries to Spain for 1.0, which matches the Spanish-only UI and narrows the legal surface. Phase: P-Store.

### Pitfall 20: Debug-only behaviour leaking into release
The Hugging Face link, manual GGUF import, `globalThis.sample` path ("Se enviarán las fotos a la IA del entorno") and "Copiar instrucciones para tu IA" are all paths that contradict the offline promise or the Families gate. **Prevention:** strip them from the release build, not just hide them. A CI grep on the release `index.html` and the merged code (e.g. no `huggingface.co`, no `globalThis.sample`) fails the build. Phase: P-Release / P-Store.

### Pitfall 21: Mis-declaring the target audience
6 to 16 maps to the Play groups 6–8, 9–12, 13–15 and 16–17. That is a *mixed* audience including under-13s, so the Families policy applies in full. Answer the target audience, IARC, Data safety and "appeals to children" consistently. The store listing language must not imply a different audience. A later change to target audience requires an app update and another extended review. Phase: P-Store.

---

## Minor Pitfalls

### Pitfall 22: Publishing debug APKs with `--clobber` during the test period
Closed-test families and side-loaders end up on different builds and cannot report which one they have. Stop `--clobber`, tag builds with `versionName+sha`, and make Play's closed track the only tester channel once the AAB exists. Phase: P-Release.

### Pitfall 23: No `.gitignore` while adding signing
The first `git add .` after generating `upload.jks` or `local.properties` commits them. Add `.gitignore` (`*.jks`, `*.keystore`, `*.p12`, `local.properties`, `build/`, `.gradle/`, `vendor/`) **before** creating the keystore. Phase: P-Release.

### Pitfall 24: Mutable llama.cpp tag and floating Actions versions
A moved tag changes the ABI, sampling behaviour or alignment between the internal-test build and the production build. Pin llama.cpp by commit SHA and pin Actions by SHA before the first Play upload. Phase: P-Release.

### Pitfall 25: The WebView-app look under the spam policy
Policy text (inside the Families page) says apps "must not merely provide a webview of a website". Repasumi serves local assets with native OCR and LLM, so the risk is low. Avoid any browser-like chrome, external page loads or references to the web version in the app or listing. Phase: P-Store.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Install-time pack + copy to `filesDir` | No PAD library, model at launch | ~1 GB storage, slow first run, fragile copy | Never for a 491 MB GGUF; use fast-follow |
| Lock orientation instead of a process-scoped model holder | One-line fix | Other config changes and process death still lose work | Only *together with* the singleton |
| Keep localStorage as the only store | No architecture change | Quota, commit-delay loss, no recovery copy | Acceptable for 1.0 **if** critical snapshots are mirrored to a native file |
| Keep `web/` + parity check | No file moves | Every edit twice, CRLF breakage, dead browser pipeline | Not after the web-app stops being published |
| Ask the model for `src` | Simple prompt | Hallucinated citations children trust | Never; derive `src` from the chunk |
| Arithmetic-only parental gate | No PIN UX | Teens in the audience pass it trivially | Only as PIN *recovery*, with harder challenges |
| Hard-coded `n_threads=4`, baseline ARMv8 build | Works everywhere | Slow on 2+6 SoCs | OK for 1.0 if measured; optimise in 1.1 |
| Remove INTERNET but leave DataTransport components | ML Kit stays intact | Harmless failed jobs | Acceptable; removing the components risks crashes |
| Change only `applicationId`, keep the Java package | Avoids JNI rename risk | Three names coexist internally | Acceptable for 1.0; clean up in 1.1 |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| ML Kit text-recognition (bundled) | Assuming "bundled = offline" | It merges INTERNET + DataTransport. Remove permissions with `tools:node="remove"` and gate in CI |
| Play Asset Delivery | Testing via Android Studio Run / sideload only | `bundletool --local-testing`, then the internal testing track. Handle `WAITING_FOR_WIFI`, `REQUIRES_USER_CONFIRMATION`, update invalidation, clear-data |
| Play Asset Delivery | Caching `assetsPath()` across launches | Re-query `getPackLocation()` every launch and `onResume` |
| Play App Signing | Keystore only in GitHub Secrets | Offline encrypted backup. Google-generated app signing key |
| R8 / minify | Enabling it on submission day | Enable in week 1 with keep rules for `@JavascriptInterface` and `native`, and test the release build on a device |
| llama.cpp JNI | Renaming the Java package without the JNI symbols | Change only `applicationId`, or rename everything in one commit with a smoke test |
| llama.cpp tokenizer | `parse_special=true` on user text | Separate tokenisation, `parse_special=false` for OCR content |
| NDK / CMake | Default NDK with 4 KB alignment | Pin NDK r28+ or add 16 KB linker flags. `zipalign -P 16` in CI |
| GitHub Pages | Restructuring Pages after the policy URL is in the Console | Freeze the policy URL first and monitor it (CI `curl -f`) |
| System TTS / clipboard / email | Not counting them as data leaving the app | Parent-initiated, gated, and disclosed in the policy |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Two model instances after recreation | Doubled native heap, LMK kill, 2× slower | Process-scoped holder | First rotation or theme change mid-generation |
| Full-resolution OCR decode | OOM on 30-photo runs | `inSampleSize` to ~2400 px, recycle | 4 GB devices, 12–50 MP photos |
| Sustained 4-thread inference | Tokens/s falls after ~1 min, hot device | Big-core thread count, thermal listener, pauses between chunks | Topics over ~5 chunks |
| SHA-256 of 491 MB on every launch | Several seconds of startup delay | Verify once per pack version, cache the verdict | Every cold start |
| Full `render()` per progress event | Jank, lost edits | Update only the progress node | Any progress-heavy flow |
| Whole-store `JSON.stringify` per answer | Growing write latency, quota errors | Per-key stores, OCR text in native files | Several topics + hundreds of sessions |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Bridge methods callable from any view | A child or XSS triggers export, links or picker | Remove debug-only methods from release. Check the gate on the Java side for sensitive calls |
| PIN stored in plain text in localStorage | An older child reads it | Salted hash, plus a recovery challenge |
| `innerHTML` with OCR or model text and no CSP | XSS inside the origin that exposes `AndroidBridge` | Strict CSP meta (`default-src 'self'`), lint that every template interpolation is wrapped in `esc()` |
| Model integrity checked only on manual import | A tampered pack generates content for children | One `verifyModel()` in front of every load path, including PAD |
| Debug APK with a public release, ephemeral keys | Testers run unidentifiable builds | Versioned tags; the Play closed track as the only tester channel |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| "IA no disponible" with no reason while the pack is pending or waiting for Wi-Fi | The family thinks the app is broken | Show the pack state, a "Descargar con datos móviles" button and progress |
| Strict exact-match grading of `fill` | The child is marked wrong for an accent or article and loses trust | Normalised comparison and accepted variants |
| Multi-minute generation with no stages | The parent leaves the app, the process dies, work is lost | Stage progress, per-chunk checkpoints, "Continuar" |
| Review screen without evidence | The parent rubber-stamps or gives up | Show the cited page snippet next to each question |
| Forgotten PIN | Locked out of review, or data wiped | Adult recovery challenge |
| Silent data loss on a new applicationId | Testers lose v0.5 progress | Tell them before the closed test |

## "Looks Done But Isn't" Checklist

- [ ] **No INTERNET:** the release **AAB** merged manifest has no `INTERNET` or `ACCESS_NETWORK_STATE`. Check with `bundletool dump manifest`, not just the source manifest.
- [ ] **16 KB:** `librepasumi_native.so` `LOAD` align is `2**14` and `zipalign -c -P 16` passes on a Play-generated APK.
- [ ] **PAD:** tested from the internal testing track with (a) Wi-Fi, (b) mobile data (consent dialog), (c) "Clear storage" then relaunch, (d) an app update with an unchanged model.
- [ ] **Model verify:** SHA-256 runs off the main thread, once per pack version. A corrupted file produces a clear error, not a crash.
- [ ] **Release build:** installed from Play on a physical 4 GB device. Every `AndroidBridge` method works with R8 on.
- [ ] **Rotation / dark mode / font scale** mid-generation: one model instance, and the result still arrives.
- [ ] **Process death:** `adb shell am kill com.repasumi` while backgrounded mid-generation, then return. Drafts survive and "Continuar" appears.
- [ ] **Parental gate:** no child-reachable path to Padres, Añadir, delete, export, any link or intent (including privacy policy, licences, TTS install, rate app).
- [ ] **AI reporting:** 🚩 exists on every generated question, and a parent can send a report to the developer without INTERNET.
- [ ] **Source validation:** a question whose answer is not on the cited page is rejected. `src` never exceeds the photo count.
- [ ] **Migrations:** a real v0.5 store, a corrupt store and a near-quota store all load. Stats and flags survive, keyed by uid.
- [ ] **Privacy:** the hosted URL returns 200, the in-app text matches it, Data safety matches the data inventory, and `dataExtractionRules` matches the policy's backup/transfer statement.
- [ ] **Keystore:** the upload key is backed up offline. versionCode comes from CI and is greater than 7.
- [ ] **Account:** Play Console confirms there is no 14-day closed-testing requirement for this developer account.
- [ ] **Debug paths:** the release build has no `huggingface.co`, `globalThis.sample` or copy-prompt-to-cloud path.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Rejected for a policy mismatch (Data safety or privacy) | MEDIUM (one review cycle, ~7 days) | Fix the declaration or build, reply through the Policy Center, do not resubmit repeatedly while under review |
| Upload key lost | HIGH during launch | Request an upload-key reset with a new PEM. No uploads until it is processed |
| App signing key lost | N/A with Play App Signing | Google holds it, which is why you must not self-manage it |
| Wrong package name uploaded | HIGH (permanent) | Create a new app. The old name is burned forever |
| 16 KB not fixed by 2027-02-01 | MEDIUM | Rebuild with NDK r28+. Updates are blocked until then |
| Model bytes changed by accident | LOW to MEDIUM | Revert to the byte-identical GGUF in the next release. Users re-download once |
| Data lost after a bad migration | HIGH (families lose progress) | Restore from the native pre-migration copy (if it was built). Otherwise there is no recovery, which is why that copy is mandatory |
| Families review exceeds the deadline | HIGH | No technical recovery. Only early submission (Pitfall 6) prevents it |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1 INTERNET via ML Kit | P-Release (week 1) | CI `bundletool dump manifest` gate; OCR works on device |
| 2 16 KB alignment | P-Release (week 1) | CI `zipalign -P 16` + `readelf` gate |
| 3 Install-time vs fast-follow | P-Model (decide first) | Model loads by path from `assetsPath()`; storage ≈ 0.5 GB |
| 4 PAD state machine | P-Model | Internal-track test matrix (Wi-Fi, mobile data, clear storage, update) |
| 5 Irreversible Console choices, signing, R8, JNI | P-Release | Signed release from CI installed from the internal track; keystore backup confirmed |
| 6 Review timeline | Roadmap ordering + P-Store | Closed track submitted by ~Nov 19; production by ~Nov 24 |
| 7 Data safety / policy consistency | P-Store | Data inventory → policy → Data safety diff review; URL monitor |
| 8 Hallucinated facts and citations | P-Quality + P-Parent | Fixture suite: rejected-question rate; deterministic `src` |
| 9 Parental gate coverage | P-Parent + P-Tests | Exit-inventory test; manual child-path walkthrough |
| 10 AI reporting requirement | P-Parent + P-Store | Flag → parent list → email report demo in review notes |
| 11 OOM, LMK, duplicate model | P-Gen | `dumpsys meminfo` after rotation; kill-and-resume test; 10-run soak |
| 12 Thermal and threads | P-Gen | `docs/perf/` logs with tokens/s per chunk on the 4 GB device |
| 13 localStorage loss and migrations | P-Data + P-Tests | Fixture migrations; kill-after-save test; quota test |
| 14 Tests vs parity | P-Release (single source, `.gitattributes`) + P-Tests | CI green on Windows-edited commits; fake-bridge E2E |
| 15 Prompt injection | P-Quality | Injection fixtures produce no control tokens and grammar-valid output |
| 19 Listing IP and metadata | P-Store | Listing review against the checklist; original seed content |
| 20 Debug paths in release | P-Release | CI grep on release assets |

## Sources

**Direct evidence (HIGH):**
- Inspection of `app-debug.apk` from GitHub release `v0.5.0-preview` (asset updated 2026-11-12): binary-manifest strings (INTERNET, ACCESS_NETWORK_STATE, DataTransport components) and ELF program headers (`librepasumi_native.so` LOAD align 4096; `libmlkit_google_ocr_pipeline.so` 16384).
- Repository code: `app/build.gradle` (no `ndkVersion`, `minifyEnabled false`, `versionCode 7`), `CMakeLists.txt` (GGML flags), `NativeGguf.java` (`close()` queued on a per-instance executor), `local_llm.cpp` (`n_threads=4`, `n_ctx=4096`, `parse_special=true`), `MainActivity.java` (`ORIGIN = https://appassets.androidplatform.net`, the Hugging Face `ACTION_VIEW`).

**Official documentation (MEDIUM per the classify-confidence seam; authoritative source):**
- [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes): Play requirement, 2027-02-01 update block, NDK r28 default, r27 flags, zipalign/readelf checks, compat mode.
- [Play Asset Delivery overview](https://developer.android.com/guide/playcore/asset-delivery): delivery modes, update invalidation, "can't assume the existence of these files".
- [Integrate asset delivery (Kotlin/Java)](https://developer.android.com/guide/playcore/asset-delivery/integrate-java): install-time via AssetManager only, `getPackLocation`/`assetsPath`, >200 MB mobile-data consent, `WAITING_FOR_WIFI`, `REQUIRES_USER_CONFIRMATION`, do not cache locations, library 2.3.0.
- [Google Play maximum size limits](https://support.google.com/googleplay/android-developer/answer/9859372): 1.5 GB per asset pack, 4 GB cumulative install-time, 200 MB mobile-data warning.
- [Play for On-device AI (beta)](https://developer.android.com/google/play/on-device-ai): AI packs, device targeting.
- [Families policy](https://support.google.com/googleplay/android-developer/answer/9893335): privacy policy accuracy, SDK restrictions, declarations, misrepresentation.
- [AI-Generated Content policy](https://support.google.com/googleplay/android-developer/answer/13985936): in-app reporting to developers, restricted content.
- [Data safety section guidance](https://support.google.com/googleplay/android-developer/answer/10787469): on-device processing is not collection; on-device transfer to other apps is sharing.
- [ML Kit Android data disclosure](https://developers.google.com/ml-kit/android-data-disclosure): device, app, performance and config data sent over HTTPS.
- [Auto Backup](https://developer.android.com/identity/data/autobackup): `allowBackup=false` does not stop device-to-device transfer on Android 12+.
- [App testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465): 12 testers / 14 days, accounts after 2023-11-13.

**Secondary (LOW to MEDIUM):**
- [Android Developers Blog, 2019: Committed to a safer Google Play for families](https://android-developers.googleblog.com/2019/08/committed-to-safer-google-play-for.html): extended reviews up to 7 days or longer, on every update.
- Upload-key reset process: third-party guides ([ProAndroidDev](https://proandroiddev.com/what-happened-when-i-lost-my-keystore-file-8ddfc0b35843), [Expo forums](https://forums.expo.dev/t/what-to-do-after-reset-of-google-play-upload-key/64590)). Timing not officially stated.
- [Chromium code review 1129233003](https://codereview.chromium.org/1129233003): WebView DOM-storage flush at shutdown differs from Chrome. Current behaviour not re-verified.
- [Google Play 2023 AI policy announcement coverage (TechCrunch)](https://techcrunch.com/2023/10/25/google-plays-policy-update-cracks-down-on-offensive-ai-apps-disruptive-notifications/).
- llama.cpp and Android memory and thermal behaviour (thread count on big.LITTLE, SIGILL with `+dotprod` on A53/A73, low-memory-kill behaviour of cached processes): practitioner knowledge, **not benchmarked on the target devices**. Validate in P-Gen.

---
*Pitfalls research for: offline child-directed Android app with an on-device LLM on Google Play (Families)*
*Researched: 2026-11-13*
