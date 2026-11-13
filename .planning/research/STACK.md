# Stack Research

**Domain:** Offline, child-directed Android study app (Java + WebView SPA + llama.cpp JNI) going to Google Play (Families program)
**Researched:** 2026-11-13
**Scope:** Only what must be ADDED for the v1.0 Play launch. The existing stack (Java 17, AGP 8.13.2, WebView SPA, llama.cpp v0.9.0, ML Kit text-recognition 16.0.1) is documented in `.planning/codebase/STACK.md` and is not re-researched.
**Confidence:** HIGH overall. Every version below was checked against the official docs, release feeds or Maven metadata on 2026-11-13. The two areas marked MEDIUM need a short on-device spike: zero-copy model loading, and the Play Console foreground-service declaration that applies only to the fast-follow path.

> **Confidence method.** The GSD `classify-confidence` seam rates every web provider available in this session (`websearch`, `webfetch`) as LOW, and no Context7/Exa/Brave provider is configured. The tiers below are therefore my own judgement under the verification protocol. **HIGH** means confirmed in official Google/Android/Gradle docs, or by inspecting the published artifact myself (AAR manifests, POMs, `llama.h` at tag v0.9.0, bundletool `config.proto`). **MEDIUM** means official docs plus inference, with no on-device proof yet. **LOW** means a single community source.

---

## Two findings that change the plan

1. **The app already ships the `INTERNET` permission through ML Kit. (HIGH, artifact inspected.)**
   `com.google.mlkit:text-recognition:16.0.1` depends on `com.google.mlkit:common:18.11.0`, which depends on `com.google.android.datatransport:transport-backend-cct:2.3.3`. That AAR's manifest declares `android.permission.INTERNET` and `ACCESS_NETWORK_STATE`. The manifest merger adds both to Repasumi's merged manifest. "Sin permiso INTERNET" is false today. CONCERNS.md flagged this as unverified; it is now confirmed.
   **Fix (no new dependency):** add `xmlns:tools` and `<uses-permission android:name="android.permission.INTERNET" tools:node="remove"/>` (and the same for `ACCESS_NETWORK_STATE`) to `app/src/main/AndroidManifest.xml`. Then add a CI gate that runs `bundletool dump manifest` on the release AAB and fails if `INTERNET` appears. With INTERNET removed, ML Kit's Firelog telemetry cannot leave the device, so the privacy policy and the Data safety form can truthfully say "no data collected".

2. **Use an install-time asset pack, not fast-follow. (HIGH for the facts, opinionated choice.)**
   I inspected `com.google.android.play:asset-delivery:2.3.0`, the library that fast-follow and on-demand packs require.
   - It merges `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` into the app.
   - It pulls in `androidx.work:work-runtime:2.9.1`, which adds `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`.
   - With targetSdk 36, a dataSync foreground service means a **Foreground service declaration in Play Console**, usually with a demo video. That is an extra review item on a 3-week clock with a Families review on top.

   Install-time packs need **no library and no new permissions**. The model is on the device at first launch, even offline, with no Wi-Fi consent dialog and no "download in progress" state. bundletool stores install-time asset content **uncompressed by default**, so `AssetManager.openFd()` works on it. llama.cpp v0.9.0 also exposes `llama_model_load_from_file_ptr(FILE*)`, which can read a GGUF embedded inside a larger file such as the split APK.

---

## Recommended Stack

### Core Technologies (additions and changes)

| Technology | Version | Purpose | Why Recommended | Conf. |
|---|---|---|---|---|
| **Play Asset Delivery, install-time asset pack** (AGP `com.android.asset-pack` plugin) | AGP 8.13.2 (already used) | Ships the 491,400,032-byte Qwen GGUF inside the AAB as module `qwen_model` | Pack limit is 1.5 GB per pack and 4 GB total for install-time packs, so 491 MB fits. Needs no runtime library and no permissions. The pack updates together with the app. Content is uncompressed in the generated split, so it can be read through a file descriptor. | HIGH |
| **Gradle Wrapper** | **8.14.7** (released 2026-11-11, latest 8.x) | Reproducible builds and `./gradlew` in CI | AGP 8.13 needs Gradle 8.13 or later. 8.14.x is the last patched 8.x line. Gradle 9.x is supported only from AGP 9 onwards, and migrating AGP is out of scope for 1.0. | HIGH |
| **Android NDK** pinned in Gradle | **r28c, `28.2.13676358`** | Build `librepasumi_native.so` | NDK r28+ produces 16 KB-aligned ELF by default, which Play requires for apps targeting API 35+ on 64-bit devices. Today CI installs r27.2 and Gradle never pins it. r28c is the default NDK of the current AGP 9.5, so it is widely used. Alternative: stay on r27 and add `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`. | HIGH |
| **R8** (bundled with AGP 8.13.2, R8 8.13.19) | built-in | `minifyEnabled true` + `shrinkResources true` for release | ML Kit, play-services and androidx bring a lot of dead code. R8 shrinks it, and AGP puts the mapping file into the AAB, so Play deobfuscates crashes automatically. The cost is three explicit keep rules (JNI, `@JavascriptInterface`, native→Java callbacks). | HIGH |
| **Play App Signing + upload key** | n/a (Play Console) | Google holds the app signing key and CI signs the AAB with the upload key | Mandatory for new apps that publish AABs. If the upload key leaks, it can be reset; the app signing key cannot. | HIGH |
| **`javax.crypto` PBKDF2WithHmacSHA256** (platform) | API 26+ (minSdk will be 29) | Parental PIN hash on the Android side | Built into the platform, so no dependency. Keeps the PIN gate out of `localStorage`, which the SPA renders through `innerHTML` and which the corruption-recovery/export code touches. | HIGH |
| **llama.cpp v0.9.0 APIs** (already vendored) | v0.9.0 | Cancellation and progress | `llama_context_params.abort_callback` / `llama_set_abort_callback` (CPU backend; `llama_decode` returns `2` when aborted), `llama_model_params.progress_callback` (return `false` aborts loading), `llama_model_load_from_file_ptr(FILE*)`. All confirmed in `include/llama.h` at tag v0.9.0. | HIGH |

### Supporting Libraries (test-only, so nothing ships in the APK)

| Library | Version | Purpose | When to Use | Conf. |
|---|---|---|---|---|
| **Node.js built-in `node:test` + `node:vm` + `node:assert`** | Node **24 LTS** (24.22.x "Krypton"; 26 "Lithium" is also LTS) | Unit tests for JS domain logic: grading, `isDue`/spaced repetition, `validate`/`parseInput`, migrations, mastery, streak | Always. Zero npm dependencies. The inline `<script>` is a classic script of top-level `function`/`const` declarations. Extract it (as `tools/verify_source.py` already does), run it once in a `vm` context with a stub `document`/`localStorage`, then call functions through `vm.runInContext('isDue(...)', ctx)`. Later scripts in the same context share the top-level lexical scope. | HIGH |
| **@playwright/test** | **1.65.0** (2026-11-05) | One end-to-end smoke test against `web/index.html` in Chromium, with a fake `AndroidBridge` injected via `page.addInitScript` that emits canned `ai_result` / OCR events | The single npm dev dependency for 1.0. Chromium is the engine behind Android System WebView, so it is the closest desktop proxy. Install only Chromium in CI: `npx playwright install --with-deps chromium`. | HIGH |
| **JUnit 4** | **4.13.2** | Local JVM unit tests for pure-Java logic you extract: OCR chunking, prompt building, JSON/"Fuente" validation, PIN hashing/lockout, SHA verification | `testImplementation`. Run with `./gradlew :app:testReleaseUnitTest`. AGP's JUnit 4 support needs no extra configuration. | HIGH |
| **org.json:json** | **20260814** | Real `org.json` for JVM unit tests | **Required** if any tested class touches `JSONObject`/`JSONArray`. Local tests run against a stub `android.jar` where `org.json` methods throw "Method … not mocked". `testImplementation` only. | HIGH |

### Development Tools

| Tool | Version | Purpose | Notes | Conf. |
|---|---|---|---|---|
| **bundletool** | **1.18.3** | `build-apks` / `install-apks` from the AAB for device testing; `dump manifest` / `dump config` for CI gates | Install-time packs install normally with `install-apks`. `--local-testing` only matters for fast-follow/on-demand packs. `bundletool dump config --bundle app.aab \| grep alignment` should print `PAGE_ALIGNMENT_16K`. | HIGH |
| **Play Console internal app sharing** | n/a | Real Play delivery of the asset pack to the Xiaomi 12T and the 4 GB device before the closed test | Google's docs recommend it as "the exact same behavior as your users". The upload is re-signed by Play, so test PIN and model logic there, not signature checks. | HIGH |
| **GitHub Actions (versions current as of 2026-11-13)** | `actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v6`, `android-actions/setup-android@v4`, `actions/upload-artifact@v7`, `actions/setup-node@v7`, `actions/cache@v4`+ | CI for test, debug and release-AAB jobs | The existing workflows use @v3/@v4/@v5 majors and can be bumped in the same PR that adds the wrapper. `setup-gradle` validates the wrapper JAR checksum. | HIGH (versions from GitHub releases API) |
| `zipalign -c -P 16 4`, `llvm-readelf -Wl` | build-tools 35.0.0 / NDK r28c | 16 KB alignment check on the `.so` in an APK built from the AAB | Optional CI step. Run once by hand at minimum. | HIGH |

---

## Configuration (prescriptive)

### 1. Asset pack module (install-time)

```groovy
// settings.gradle
include ':app'
include ':qwen_model'          // name: starts with a letter; letters, digits and _ only

// build.gradle (root)
plugins {
    id 'com.android.application' version '8.13.2' apply false
    id 'com.android.asset-pack'  version '8.13.2' apply false
}

// qwen_model/build.gradle
plugins { id 'com.android.asset-pack' }
assetPack {
    packName = 'qwen_model'
    dynamicDelivery { deliveryType = 'install-time' }
}
// File (NOT committed; .gitignore it):
// qwen_model/src/main/assets/models/qwen2.5-0.5b-instruct-q4_k_m.gguf

// app/build.gradle
android {
    assetPacks = [':qwen_model']
    androidResources { noCompress += ['gguf'] }   // belt and braces; install-time packs are uncompressed by default
}
```

- **Do not commit the 491 MB GGUF** (GitHub rejects files over 100 MB, and LFS has bandwidth quotas). In CI, download it from the pinned Hugging Face commit `9217f5db…`, run `sha256sum -c` against `74a4da8c…a9db`, and cache it with `actions/cache` keyed on the hash. Provide `tools/fetch_model.sh` (or `.py`) for local builds.
- **Runtime access (Java):** use the app's own `getAssets()`; install-time packs are merged into the app's `AssetManager`. Recommended order:
  1. **Zero-copy (spike first, 1 day):** `AssetFileDescriptor afd = getAssets().openFd("models/…gguf")`. This throws if the entry is compressed, which doubles as a self-check. Pass `afd.getParcelFileDescriptor().getFd()`, `afd.getStartOffset()` and `afd.getLength()` to JNI. In C++: `FILE* f = fdopen(dup(fd), "rb"); fseeko(f, offset, SEEK_SET); llama_model_load_from_file_ptr(f, mp);`. **Caveat (HIGH, read in source):** with mmap, v0.9.0 throws if the GGUF data section's absolute file offset is not 32-byte aligned. Zip entries are typically only 4-byte aligned, so set `mp.load_mode = LLAMA_LOAD_MODE_AUTO`, and on that error retry with `LLAMA_LOAD_MODE_NONE` (reads ~491 MB into anonymous RAM). Measure RSS on the 4 GB device.
  2. **Fallback (zero risk, reuses existing code):** stream `getAssets().open(...)` into `filesDir/repasumi-model.gguf` with SHA-256 in the same pass. This is the current `importModel` loop with the SAF `Uri` swapped for the asset stream. Load by path as today. Cost: a second 491 MB on disk.
- **SHA-256 "before use":** hash once per `(versionCode, asset length)` through the stream and cache the verdict in `SharedPreferences`. Do not re-hash 491 MB on every launch (several seconds on low-end CPUs).
- **Debug builds:** `assembleDebug` APKs do **not** contain asset packs. Keep the SAF import path in **debug only**, or test with `bundleDebug` + `bundletool build-apks` + `install-apks`.

### 2. Release signing + AAB in GitHub Actions

```groovy
// app/build.gradle
android {
    namespace 'com.davidop.estudiaenelbus'   // KEEP: JNI symbols are Java_com_davidop_estudiaenelbus_*
    ndkVersion '28.2.13676358'
    defaultConfig {
        applicationId 'com.repasumi'           // permanent once uploaded
        minSdk 29
        targetSdk 36                           // already meets Play's API-36 rule for new apps (since 2026-08-31)
        ndk { abiFilters 'arm64-v8a'; debugSymbolLevel 'SYMBOL_TABLE' }  // native crash symbolication in Play
    }
    signingConfigs {
        release {
            def ks = System.getenv('REPASUMI_UPLOAD_KEYSTORE')
            if (ks) {
                storeFile file(ks)
                storePassword System.getenv('REPASUMI_UPLOAD_STORE_PASSWORD')
                keyAlias System.getenv('REPASUMI_UPLOAD_KEY_ALIAS')
                keyPassword System.getenv('REPASUMI_UPLOAD_KEY_PASSWORD')
            }
        }
    }
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            if (System.getenv('REPASUMI_UPLOAD_KEYSTORE')) signingConfig signingConfigs.release
        }
    }
}
```

- **Do not change `namespace` or the Java package.** Only `applicationId` must become `com.repasumi`. Renaming the package silently breaks every `Java_com_davidop_estudiaenelbus_*` JNI export (`UnsatisfiedLinkError`, which `NativeGguf` currently swallows as "UNAVAILABLE").
- **Upload key:** `keytool -genkeypair -v -keystore upload.jks -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000 -alias upload`. Store `base64 upload.jks` as the secret `UPLOAD_KEYSTORE_BASE64`, plus `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS` and `UPLOAD_KEY_PASSWORD`. Keep an offline backup of the keystore outside the repo and outside Obsidian.
- **Workflow:** a separate `release-aab.yml` triggered by `workflow_dispatch` and `v*` tags, bound to a GitHub **Environment** (`play-release`) with required reviewers so PRs from forks never see the secrets. Steps:
  1. Decode the keystore to `$RUNNER_TEMP/upload.jks`.
  2. Fetch and verify the GGUF (cached).
  3. `./gradlew --no-daemon :app:bundleRelease`.
  4. Gate: `bundletool dump manifest` contains no `android.permission.INTERNET`.
  5. Gate: `bundletool dump config` shows `PAGE_ALIGNMENT_16K`.
  6. `jarsigner -verify app-release.aab`.
  7. `upload-artifact` with short retention.
  Do **not** attach the release AAB to a public GitHub Release; it contains the model and the code is "all rights reserved".
- **First upload to Play is manual** through Play Console. Defer automated upload (Play Developer API / `r0adkll/upload-google-play` with a service account) to v1.1. (MEDIUM: the first release of a new app is normally created manually; automation adds a service-account secret for no gain at 12 testers.)

### 3. Gradle wrapper

```bash
gradle wrapper --gradle-version 8.14.7 --distribution-type bin \
  --gradle-distribution-sha256-sum <sha from https://gradle.org/release-checksums/>
# commit: gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.jar, gradle/wrapper/gradle-wrapper.properties
```
In CI, drop `gradle-version: 8.13` from `setup-gradle` and call `./gradlew` instead.

### 4. R8 keep rules (`app/proguard-rules.pro`)

```proguard
# JNI: native methods and their classes (C++ symbol names depend on them)
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
# WebView bridge: methods reached by name from JavaScript
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
# Methods called FROM C++ via GetMethodID (progress/cancel callbacks); R8 cannot see these uses
-keep class com.davidop.estudiaenelbus.NativeGguf$Progress { *; }
-keepattributes *Annotation*
```
The AGP default file already contains the first two rules in most versions. Declare them explicitly anyway, so a change in the AGP default cannot break the JS bridge silently. Verify on the release build by running one OCR → generate → review flow on a device before every upload.

### 5. llama.cpp cancellation + progress (JNI)

```cpp
struct Engine { /* ... */ std::atomic<bool> cancel{false}; JNIEnv* env=nullptr; jobject sink=nullptr; jmethodID onProgress=nullptr; };

static bool abort_cb(void* d) { return static_cast<Engine*>(d)->cancel.load(std::memory_order_relaxed); }
// after llama_init_from_model:
llama_set_abort_callback(engine->context, abort_cb, engine.get());
// model load: mp.progress_callback = [](float p, void* d){ /* report */ return !static_cast<Engine*>(d)->cancel.load(); };

// nativeCancel(handle): called DIRECTLY from the UI/bridge thread, NOT via the single-thread executor (it is busy)
extern "C" JNIEXPORT void JNICALL Java_com_davidop_estudiaenelbus_NativeGguf_nativeCancel(JNIEnv*, jclass, jlong h) {
    if (auto* e = reinterpret_cast<Engine*>(h)) e->cancel.store(true);
}
```
- Check `cancel` in the per-token loop as well. `llama_decode` returning **2** means aborted: throw a dedicated "cancelled" exception, not the generic "inferencia ha fallado". Reset `cancel=false` at the start of each `nativeComplete`. The existing `llama_memory_clear` at the start already discards the partial KV state that an abort leaves behind.
- Report progress from the worker thread (the `JNIEnv` passed to `nativeComplete` is valid there). Throttle to about one call every 8 tokens or 250 ms. Cache the `jmethodID` and keep the callback class with R8 (rule above).
- Java side: an `AtomicBoolean running` plus a monotonically increasing `generationId`, so there is no double execution and stale results are dropped after cancel or rotation. Add `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"` on `MainActivity`, which is the standard way to keep the WebView and the in-flight job alive across rotation without a framework.

### 6. Parental PIN (Android side, no dependency)

- `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`, 16-byte `SecureRandom` salt, 256-bit output. **Calibrate iterations to roughly 250–500 ms on the 4 GB device** (start at 210,000; OWASP's current figure for PBKDF2-SHA256 is 600,000, so store `iterations` next to the hash to allow raising it later). Compare with `MessageDigest.isEqual`.
- Store `{salt, hash, iterations, failedAttempts, lockedUntil}` in a `MODE_PRIVATE` `SharedPreferences` file (`allowBackup=false` already). Persist an exponential lockout natively so reloading the WebView cannot reset it.
- Bridge API: `pinState()`, `setPin(oldPin,newPin)`, `verifyPin(pin)`. Run hashing on the executor (not the `JavaBridge` thread) and answer through `repasumiNativeEvent('pin', {ok, lockedForMs})`.
- Be honest about the threat model. A 4-digit PIN has 10,000 combinations, so the hash only prevents casual reading and plaintext exposure. The real protection against a child is the lockout plus the fact that app-private storage is unreachable without root. Product decision still needed: a "forgot PIN" path (e.g., reset by clearing app data, documented in the help text).

---

## Installation

```bash
# JS tests (repo root): one dev dependency only
npm init -y            # creates package.json; add "private": true and "type": "module"
npm install -D @playwright/test@1.65.0
npx playwright install --with-deps chromium
# node:test needs nothing:  node --test tests/

# Android (app/build.gradle)
#   testImplementation 'junit:junit:4.13.2'
#   testImplementation 'org.json:json:20260814'
# No new implementation dependencies for 1.0.

# Tooling (CI / local)
curl -L -o bundletool.jar https://github.com/google/bundletool/releases/download/1.18.3/bundletool-all-1.18.3.jar
sdkmanager "ndk;28.2.13676358" "cmake;3.22.1" "build-tools;35.0.0" "platforms;android-36"
```

Commit `package-lock.json`, and add `node_modules/`, `test-results/`, `playwright-report/`, the GGUF, `vendor/`, `build/` and `.gradle/` to the new `.gitignore`.

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|---|---|---|
| **Install-time** asset pack | **Fast-follow** pack + `com.google.android.play:asset-delivery:2.3.0` | Use it if the ~520 MB listed install size or the doubled disk use of the copy fallback becomes a review or UX problem. Benefits: a real file path at `getPackLocation(pack).assetsPath()` (mmap, no copy), and it does not count toward the listed size. Costs: a download-state UI; `WAITING_FOR_WIFI` above 200 MB on mobile data, handled with `showConfirmationDialog(Activity)` (a non-deprecated overload that works with plain `android.app.Activity`); `REQUIRES_USER_CONFIRMATION` on sideloaded builds; an "update in progress" state; WorkManager plus 5 merged permissions; and the **Play Console Foreground service (dataSync) declaration**. Do **not** strip `FOREGROUND_SERVICE_DATA_SYNC` with `tools:node="remove"` on this path, because PAD's `ExtractionForegroundService` needs it on API 34+. |
| Install-time | **On-demand** pack | Never for 1.0. The model is required for core value, and on-demand needs the same library plus an explicit fetch while the app is in the foreground. |
| Zero-copy `openFd` + `llama_model_load_from_file_ptr` | Copy to `filesDir` once | Use the copy if the spike shows load failures or RSS too high on the 4 GB device with `LOAD_MODE_NONE`. It is the lowest-risk option: existing code, mmap by path. |
| Android-side PBKDF2 PIN | WebCrypto `crypto.subtle.deriveBits({name:'PBKDF2',hash:'SHA-256',...})` in the SPA | It works, because the WebView origin `https://appassets.androidplatform.net` is a secure context. Choose it only if Java changes must be avoided entirely. The PIN state would then live in `localStorage`, where the corruption-recovery and import/export code can reset it and an `innerHTML` injection could overwrite it. |
| `node:test` + `vm` | **Vitest 5.1.0** (+ `happy-dom` 21.0.4 / `jsdom` 31.0.1) | Use it if tests need a realistic DOM, snapshots or watch mode. It adds dozens of transitive packages for no 1.0 need, and the DOM paths are already covered by the Playwright smoke test. |
| JUnit 4 on extracted pure-Java classes | **Robolectric 4.18** | Use it only to test `MainActivity`/`AndroidBridge` with a real `Context`. It downloads large android-all jars, is slow on CI, and is not needed if logic is extracted into plain classes. |
| Gradle 8.14.7 wrapper | Gradle 9.x (9.9.1) + AGP 9.5 | That is the v1.1+ upgrade path. AGP 9 brings DSL and built-in-Kotlin changes, which is a migration the 1.0 constraints exclude. |
| NDK r28c pinned | NDK r27.2 + 16 KB linker flags | Use it if r28c triggers a llama.cpp v0.9.0 build regression. Add `target_link_options(repasumi_native PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")`. Static `llama`/`ggml` archives need no flags; only the final `.so` link does. |
| Manual first upload | Play Developer API upload from CI | v1.1, once releases become routine. |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|---|---|---|
| Committing the GGUF to git or Git LFS | Over GitHub's 100 MB file limit; LFS bandwidth quotas; bloats every clone and worktree (the user works with multiple worktrees) | CI download from the pinned HF commit, `sha256sum -c`, `actions/cache` |
| Putting the GGUF in the **base module** (`app/src/main/assets`) | Base module compressed-download limit is 500 MB. Model plus ML Kit plus the `.so` is at the edge, and the base APK may compress the entry, which breaks `openFd` | `:qwen_model` install-time asset pack |
| `INTERNET` permission or a model download from your own server | Breaks the core privacy promise and the Families data story | PAD (Play does the transfer) + `tools:node="remove"` on merged INTERNET |
| `androidx.security:security-crypto` (EncryptedSharedPreferences) | Deprecated by Google (MEDIUM: deprecation announced in 2025; verify before use). Adds Tink and Keystore failure modes for a PIN hash that is not secret material anyway | Plain `SharedPreferences` + PBKDF2 hash |
| Plain SHA-256 or unsalted hashes for the PIN | Instant lookup table for 10,000 PINs | Salted PBKDF2 + native lockout |
| Changing the Java package to `com.repasumi` | Breaks JNI export names; `NativeGguf` swallows `UnsatisfiedLinkError` and the app silently shows "UNAVAILABLE" | Change only `applicationId` |
| `useLegacyPackaging true` / compressed `.so` as a 16 KB workaround | Increases install size and is only meant for AGP < 8.5.1 | AGP 8.13.2 (already 16 KB zip-aligns) + NDK r28c |
| Instrumented Android tests on GitHub-hosted emulators for 1.0 | The native library is arm64-only and runners use x86_64 images; running a 491 MB model under translation is slow and flaky | JVM unit tests + Playwright SPA smoke + a manual device checklist (Xiaomi 12T + 4 GB device) |
| Jest, Mocha, Karma, Cypress | Extra dependency trees that duplicate `node:test` / Playwright | `node:test`, `@playwright/test` |
| Third-party SDKs for crash reporting or analytics (Firebase Crashlytics, Sentry) | They need INTERNET, collect data, and complicate the Families program and the Data safety form | Play Console Android vitals + uploaded native symbols (`debugSymbolLevel`) + R8 mapping embedded in the AAB |

---

## Stack Patterns by Variant

**If the zero-copy spike succeeds (load works via `FILE*`, RSS acceptable on the 4 GB device):**
- Use `openFd` → `llama_model_load_from_file_ptr`, hash once per versionCode, and keep no copy on disk.
- This avoids 491 MB of duplicate storage on family phones.

**If the zero-copy spike fails or is inconclusive within one day:**
- Copy once into `filesDir` (with SHA-256 in the same pass and a progress UI), then load by path with mmap.
- The required free space is ~1 GB at install (Play needs 2× the pack size) and +491 MB at first run, so state the storage requirement in the store listing.

**If Play review or testers object to the ~520 MB install size:**
- Switch `deliveryType` to `fast-follow` and add `asset-delivery:2.3.0`.
- Prepare the Foreground service (dataSync) declaration and video before the upload, and budget the extra UI states.

**If the PR comes from a fork or has no secrets:**
- The signing block stays unset. `bundleRelease` produces an unsigned AAB and the test jobs still run; only the protected `release-aab` job signs.

---

## Version Compatibility

| Package A | Compatible With | Notes |
|---|---|---|
| AGP 8.13.2 | Gradle **8.13 to 8.14.x**; JDK 17; build-tools 35.0.0; max API 36.1 | Default NDK is 27.0.12077973, so pin `ndkVersion` explicitly |
| AGP 8.13.2 | `com.android.asset-pack` 8.13.2 | Same artifact version as the application plugin |
| AGP ≥ 8.5.1 | 16 KB zip alignment of uncompressed `.so` in APKs built from AABs | Already satisfied |
| NDK r28c (28.2.13676358) | 16 KB ELF alignment by default | r27 needs the two linker flags. Play blocks updates without 16 KB support from 2027-02-01 (current doc), and the Console already warns |
| targetSdk 36 | Play requirement for new apps since 2026-08-31 | Already set |
| llama.cpp v0.9.0 | `abort_callback`, `progress_callback`, `llama_load_mode` enum, `llama_model_load_from_file_ptr` | `abort_callback` works only on the CPU backend, which is all this build uses. The `FILE*` + mmap path requires a 32-byte-aligned data offset |
| `asset-delivery` 2.3.0 (fast-follow alternative only) | minSdk 21; pulls `work-runtime` 2.9.1, `play-services-basement` 18.4.0, `play-services-tasks` 18.2.0, `core-common` 2.0.4 | Merges FOREGROUND_SERVICE(+_DATA_SYNC), WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED |
| ML Kit `text-recognition` 16.0.1 | `mlkit:common` 18.11.0 → `transport-backend-cct` ≥ 2.3.3 | **Merges INTERNET + ACCESS_NETWORK_STATE**, so remove them with `tools:node="remove"` |
| @playwright/test 1.65.0 | Node 20+/22/24/26 | Chromium only |
| bundletool 1.18.3 | AABs from AGP 8.13 | Latest release (2025-12-15) |

---

## Sources

- https://developer.android.com/guide/playcore/asset-delivery (updated 2026-10-06): delivery modes; install-time served as split APKs; fast-follow/on-demand expanded to internal storage; update behaviour. HIGH
- https://developer.android.com/guide/playcore/asset-delivery/integrate-java: `com.android.asset-pack` DSL, `assetPacks`, `asset-delivery:2.3.0`, `getPackLocation`/`assetsPath`, 200 MB Wi-Fi consent, `REQUIRES_USER_CONFIRMATION`. HIGH
- https://developer.android.com/reference/com/google/android/play/core/assetpacks/AssetPackManager: `showConfirmationDialog(Activity)` (not deprecated), `getAssetLocation`, `fetch` foreground-only. HIGH
- https://developer.android.com/guide/playcore/asset-delivery/test: bundletool local testing (fast-follow behaves as on-demand), internal app sharing. HIGH
- https://support.google.com/googleplay/android-developer/answer/9859372: size limits (base 500 MB, asset pack 1.5 GB, install-time total 4 GB, fast-follow/on-demand total 30 GB, app 34 GB). HIGH
- bundletool `src/main/proto/config.proto` (master): install-time asset module content uncompressed by default. HIGH
- Maven artifacts inspected directly (dl.google.com, repo1.maven.org): `asset-delivery-2.3.0.aar` manifest + POM; `work-runtime-2.9.1.aar` manifest; `mlkit/common-18.11.0.pom`; `transport-backend-cct-2.3.3.aar` manifest (INTERNET). JUnit 4.13.2, org.json 20260814, Robolectric 4.18 metadata. HIGH
- llama.cpp tag v0.9.0, `include/llama.h`, `src/llama.cpp`, `src/llama-model-loader.cpp`: `abort_callback`, `progress_callback`, `llama_decode` return codes, `llama_model_load_from_file_ptr`, 32-byte mmap alignment check. HIGH
- https://developer.android.com/guide/practices/page-sizes: 16 KB requirement, NDK r28 default, r27 flags, AGP 8.5.1 alignment, verification commands, 2027-02-01 update deadline. HIGH
- https://developer.android.com/build/releases/agp-8-13-0-release-notes and https://developer.android.com/build/releases/gradle-plugin: AGP 8.13 compatibility table; AGP 9.5 default NDK 28.2.13676358. HIGH
- https://developer.android.com/ndk/downloads: NDK r30 is the current LTS (not needed for 1.0). HIGH
- https://gradle.org/releases/: Gradle 8.14.7 (2026-11-11), 9.9.1 (2026-11-10). HIGH
- https://developer.android.com/google/play/requirements/target-sdk: API 36 for new apps from 2026-08-31. HIGH
- https://developer.android.com/reference/javax/crypto/SecretKeyFactory: PBKDF2withHmacSHA256 on API 26+. HIGH
- OWASP Password Storage Cheat Sheet (via secondary summaries): PBKDF2-HMAC-SHA256 600,000 iterations. MEDIUM
- GitHub releases API: actions/checkout v7.0.1, setup-java v6.2.0, gradle/actions v6.4.1, setup-android v4.0.4, upload-artifact v7.0.2, setup-node v7.1.0, playwright v1.65.0, bundletool 1.18.3. HIGH
- nodejs.org/dist/index.json: Node 24.22.3 (LTS Krypton), 26.13.1 (LTS Lithium). npm registry: vitest 5.1.0, jsdom 31.0.1, happy-dom 21.0.4. HIGH
- Play Console foreground-service declaration for dataSync, which applies to the fast-follow path only: support.google.com/googleplay/android-developer/answer/13392821 plus developer reports (CleverTap docs, Unreal forums, Zoom devforum). MEDIUM (no PAD-specific Google statement found)
- llama.cpp PR #20402 (closed; fd-based loader rejected in favour of `FILE*`), seen in search results. LOW, superseded by direct header inspection

---
*Stack research for: offline child-directed Android study app, v1.0 Google Play launch*
*Researched: 2026-11-13*
