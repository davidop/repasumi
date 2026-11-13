# Feature Research

**Domain:** Offline, child-directed (ages 6–16) Android study app on Google Play (Families program) that turns textbook photos into parent-reviewed questions with an on-device LLM
**Researched:** 2026-11-13
**Confidence:** MEDIUM overall. Google Play policy facts come from official Play Console Help and developer.android.com pages, fetched directly. The GSD classify-confidence seam rates web sources LOW (single fetch) to MEDIUM (cross-checked), so policy claims here are MEDIUM. UX patterns such as PIN recovery, review workflow and recovery screens are opinionated synthesis from vendor patterns plus this repo's constraints, and are LOW–MEDIUM.

**Milestone scope:** This file covers only the five v1.0 target areas: (A) parental gate / PIN, (B) parent review and approval of AI questions, (C) data recovery and migrations, (D) first-run onboarding and on-device model availability, and (E) Families store-compliance artifacts. Existing study features (5 exercise types, SRS, flashcards, exam, profiles, metrics, TTS) are already validated and not re-researched here.

**Lens used for every call:** Core value = *"textbook photos become reliable, parent-reviewed questions, and nothing leaves the phone."* A feature counts as table stakes if, without it, (1) Play or the Families program rejects the app, (2) a child can see unreviewed AI output, (3) the privacy promise becomes false, or (4) a family loses data or gets stuck with no recovery path.

---

## Feature Landscape

### Table Stakes (Users Expect These / Store Rejects Without Them)

#### A. Parental gate (parent PIN)

| ID | Feature | Why Expected | Complexity | Notes |
|----|---------|--------------|------------|-------|
| A1 | **PIN setup during first-run onboarding**: 4 digits, entered twice, can't be skipped before entering any parent area | Without a PIN the child can generate and approve their own content, which breaks the "parent-reviewed" promise. Google's Families policy names a PIN as an acceptable form of "adult action" (it lists "PIN, password, birthdate, email verification…") | LOW | 4 digits is enough. The threat is a curious child at the UI, not a forensic attacker. Make 6 digits optional, not required. |
| A2 | **Gate scope.** Gated: "Padres" (metrics, reports), "Añadir" (photos, OCR, generate, review, approve, publish), deleting a topic, learner or all data, export (when it exists), every external link or intent leaving the app, editing learner profiles, changing the PIN. **Not gated:** studying, flashcards, exam, TTS, text size, reduced motion, the 🚩 flag | Matches PROJECT.md. A gate on study screens adds friction for no protective value. The child must be able to flag without the parent present. | LOW | Implement as one `requireParent(action)` guard in the SPA, not ad-hoc checks per button. |
| A3 | **Unlock session with auto-relock**: unlock lasts until the user leaves the parent area, after ~5 min inactivity, or when the app goes to background (`onPause`) | Kids pick up the phone right after the parent. Every parental-control product relocks. | LOW | Show a visible "Modo padres" banner with a "Bloquear" button while unlocked (pairs with X3). |
| A4 | **Escalating lockout on wrong PIN**: after 5 failures, wait 30 s, then 1 min, 5 min, 15 min, capped at 1 h. Persist across app restarts. **Never wipe data.** | Standard pattern (Apple Screen Time escalates 1 min→1 h; Bitdefender 5 min→24 h). Without it a child brute-forces 10,000 PINs. | LOW–MEDIUM | Persist counter and `lockedUntil`. Clock tampering is an accepted residual risk. |
| A5 | **PIN stored as salted hash on the Java side** (SharedPreferences), checked through a bridge call. The SPA never holds the PIN or the hash in `localStorage`. | A plain-text PIN in `localStorage` is readable through WebView debugging and ends up inside any future export or recovery blob. | LOW | PBKDF2 or SHA-256 with a per-install salt. Keep it simple: a 4-digit space can't be made strong by hashing, so the goal is keeping it out of app data and exports. |
| A6 | **"He olvidado el PIN" recovery that works offline with no account.** Primary path: confirm the **device screen lock** (BiometricPrompt with device credential, or `KeyguardManager.createConfirmDeviceCredentialIntent` on API 29). Fallback when the device has no screen lock: **delayed reset** (24 h cooldown) that shows a banner in Padres afterwards: "El PIN se restableció el {fecha}". | Every vendor recovery path found (Bitdefender, Apple, Fire Kids) needs an account or network, which Repasumi can't have. Without a recovery path a forgotten PIN means "clear app data", which destroys every topic. | MEDIUM | This makes recovery **tamper-evident** rather than tamper-proof. That suits a family threat model. Only show "¿Has olvidado el PIN?" after the first wrong attempt so it doesn't clutter normal use. |
| A7 | **Change PIN** from Padres (needs the current PIN) | Expected in every PIN product. | LOW | |

#### B. Parent review and approval of AI-generated questions

| ID | Feature | Why Expected | Complexity | Notes |
|----|---------|--------------|------------|-------|
| B1 | **Topic lifecycle `draft → published`**, stored in the topic record. A child-facing list or study mode **never** shows drafts. Existing and sample topics migrate as `published`. | This is the core value. Any path that shows the child an unreviewed question breaks the product promise and weakens the Families "content appropriate for children" claim. | MEDIUM | Needs schema v-next (C1) and stable IDs (C5). The state lives on the topic. Each question also carries `reviewState: pending/approved/discarded`. |
| B2 | **Per-question actions: Aprobar / Editar / Descartar**, with editors for every type: MC (stem, options, correct option, explanation), TF, fill (sentence and answer), match (pairs), order (items and correct order) | The milestone's minimum scope (#5). Same draft → teacher review → assign pattern as AI quiz generators in edtech (Google Forms/Classroom Gemini drafts, Quizizz AI and similar). | HIGH | The biggest UI piece in the milestone. The five editors differ: match and order need add/remove and reorder controls. |
| B3 | **Edits are never lost**: autosave each field into the draft on input/blur. Survives `render()`, rotation, backgrounding, process death and coming back the next day. | CONCERNS.md: `render()` currently wipes review edits. Reviewing 20 questions takes the parent 5–10 minutes and is often interrupted. | MEDIUM | Re-render from state, never read form values back from the DOM at publish time. |
| B4 | **Per-question validation before Approve is allowed**: MC has exactly one correct option and no duplicates; fill answer is non-empty and appears in the sentence slot; match has ≥2 pairs and unique sides; order has ≥3 items; text isn't empty | Stops a parent's edit from creating a broken question that the child then "fails" unfairly. | LOW–MEDIUM | Reuse the same validators as generation-time validation (#2, #3-minimal). Unit-test them in CI (#13). |
| B5 | **Source citation on every question**: "Fuente: foto N" plus the OCR excerpt that holds the fact (highlighted). Tapping it opens the photo or the OCR text. A validation badge reads either "Dato encontrado en la foto N" or a ⚠ "No encuentro este dato en las fotos". | Without the source, the parent can only judge the question from memory, so review turns into rubber-stamping. The citation is what makes "reliable" credible. | MEDIUM | Depends on #2 source validation (chunked OCR and citation check). Sort ⚠ questions first. |
| B6 | **Review progress and publish rule**: "12 de 20 revisadas". **Publicar** is enabled only when no question is `pending` and at least one is `approved`. Bulk **"Descartar las pendientes"** is allowed; bulk approve is not (see anti-features). | Without a progress count the parent doesn't know when review is finished. Requiring every question to be decided is what makes "reviewed by the parent" true. | LOW | |
| B7 | **Flagged-question queue in Padres**: the child's 🚩 sends the question to "Preguntas marcadas", where the parent can edit, discard (removing it from the published topic) or dismiss the flag | Closes the loop for errors that get past review. It is also the most defensible in-app answer to Google Play's **AI-Generated Content policy**, which requires "in-app user reporting or flagging features" in apps that generate AI content. | LOW–MEDIUM | The 🚩 already exists; it needs to persist by stable question ID and surface in Padres. |
| B8 | **Edit a published topic** (fix, add or remove questions) without losing the child's progress on untouched questions | Needed to act on B7. Parents also spot errors after publishing. | MEDIUM | Stats keyed by stable ID (C5). Opinion: if the parent changes the **correct answer** of a question, reset that question's SRS state. Wording-only edits keep it. |
| B9 | **Resume an unfinished review**: drafts appear in "Añadir" as "Borradores (2)" | Generation takes minutes and review gets interrupted, so a draft lost on exit is a lost OCR and generation run. | LOW | Comes almost free with B1 and B3. |

#### C. Data integrity, recovery and migrations (local-only)

| ID | Feature | Why Expected | Complexity | Notes |
|----|---------|--------------|------------|-------|
| C1 | **Schema version and ordered migrations, run silently at startup** with no user-facing "migrating…" UI on success | Users don't expect to see migrations. They do expect their data to still be there after an update. | MEDIUM | Pure JS functions `migrate_n_to_n+1`, unit-tested with fixture blobs from v0.5.0 (#13). |
| C2 | **Pre-migration snapshot**: before migrating, copy the raw blob to a `…-backup-v{n}` key. Keep it until the next successful launch on the new version. | The only rollback path for a bad migration in an app with no cloud, no export (v1.1) and `allowBackup="false"`. | LOW | Watch the `localStorage` quota (~5–10 MB in WebView). Keep one snapshot, not several. |
| C3 | **Last-known-good (LKG) write discipline**: serialize, write, read back and parse, and only then update the LKG copy. **Surface write failures** (quota exceeded, exception) with a visible, non-alarming message. | CONCERNS.md: a corrupt store currently stops saving silently. Silent data loss is the worst failure for a family that spent an evening photographing a chapter. | MEDIUM | The toast is for the parent ("No se ha podido guardar. Libera espacio o borra un tema."), not an error code. |
| C4 | **Recovery screen when the stored blob can't be parsed or validated**. It is addressed to the parent, calm, in Spanish: "No hemos podido leer los datos guardados. Hemos recuperado la copia del {fecha}." Actions: **[Usar la copia]** (default), **[Empezar de cero]** (PIN-gated, double confirm). The corrupted blob is **quarantined under a separate key, never auto-deleted.** | Local-only apps can't fall back to a server, so this screen is the only safety net. Without it the app either crashes or silently overwrites with an empty state. | MEDIUM | Never show raw JSON or stack traces. The child may be the one holding the phone, so wording must not frighten them. |
| C5 | **Stable IDs for topics, questions and learners** (UUID or monotonic). Stats, SRS state, flags and review state are keyed by ID, not array index. | CONCERNS.md: stats currently go by question index, so editing or discarding a question corrupts history. B2, B7 and B8 depend on this. | MEDIUM | Migration assigns IDs to legacy data deterministically. |
| C6 | **"Newer schema than this app" guard**: if `schemaVersion` > known, open read-only and ask to update the app, never write | Prevents a downgrade (sideload or tester build) from overwriting data it doesn't understand. | LOW | |

#### D. First-run onboarding and on-device model availability

| ID | Feature | Why Expected | Complexity | Notes |
|----|---------|--------------|------------|-------|
| D1 | **Short first-run flow (≤4 screens)**: (1) welcome and value: "Fotos del libro → preguntas. Nada sale del móvil." (2) parent creates the PIN (A1). (3) create the first learner (nickname and year). (4) "Prueba un tema de ejemplo" or "Añade tu primer tema". | Successful closed testing depends on families doing it "sin ayuda" (PROJECT.md success metric). A long onboarding loses them, and no onboarding leaves the PIN unset. | LOW–MEDIUM | Ask for a nickname only; don't ask for the child's full name or age. |
| D2 | **Keep incapable devices from installing**: minSdk 29, arm64-v8a-only native library (Play filters by ABI), and a **Play Console device catalog RAM exclusion rule**. Optionally deliver the model only to a RAM ≥ X device group via **Play device targeting**. | Families on a 3 GB phone who install a 400 MB app that can never generate will leave 1-star reviews. Play supports RAM and SoC exclusion rules and device-targeted AI or asset packs. | LOW (console) / MEDIUM (targeting) | "4 GB" phones report about 3.5–3.8 GB `totalMem`. Set the threshold from the Device Catalog value for the 4 GB test device, not to 4,000,000,000. RAM rules can mark a model "partially excluded" because RAM varies across variants. |
| D3 | **In-app capability check (Java) on first run and before each generation**: `totalMem`, `isLowRamDevice`, ABI, free storage. Three outcomes: capable / capable-but-slow (warn about time) / not capable. | Pre-install filtering is imperfect (sideloads, RAM variants, low free storage). PROJECT.md requires "avisa claramente si el dispositivo no llega al mínimo". | LOW | Run the check before loading the model, not after an OOM. |
| D4 | **Graceful degradation when the device isn't capable or the model is unavailable**: study modes, sample topics and existing topics still work. Only "Generar preguntas" is disabled, with a one-line reason: "Este móvil no tiene memoria suficiente para crear preguntas." | Making the whole app unusable punishes the family. Studying the sample topics is still valuable, and it keeps the app usable during Play review. | LOW | Never block app launch on model availability. |
| D5 | **Model-availability state machine** surfaced in "Añadir". If the model is delivered **fast-follow or on-demand**: show "Preparando el asistente… n %" (from `AiPackState.bytesDownloaded/totalBytesToDownload`, non-blocking). For `WAITING_FOR_WIFI` / `REQUIRES_USER_CONFIRMATION`, call `showConfirmationDialog()`. For `FAILED`, show a retry button. If **install-time**: just verify. | Official Play for On-device AI docs: fast-follow packs "may not be ready" when the app opens. Downloads over 200 MB on mobile data wait for user consent. The app must check status at every launch. | MEDIUM | The delivery-mode choice belongs to STACK. Feature implication: fast-follow lands the GGUF as a plain file in internal storage, which llama.cpp can mmap directly. Install-time puts it inside an APK and probably needs a copy step. |
| D6 | **SHA-256 verification with a visible step**: "Comprobando el modelo…" once per install or app update, not on every launch. Cache the verified result keyed on `versionCode` and file size. On mismatch, refuse to load and show a retry or reinstall message. | Already in scope (#16). Hashing ~400 MB takes seconds on low-end phones and needs a progress state, or the app looks frozen. | LOW–MEDIUM | |
| D7 | **Staged, cancelable generation progress** (OCR → fragmentos → preguntas → comprobando fuentes), with an honest time estimate ("2–5 min, puedes dejar el móvil en la mesa"). Survives rotation. No double-run. | Already in scope (#2, #1). It is listed because onboarding sets the expectations that this screen then has to meet. | MEDIUM–HIGH | Covered by the generation-stability phase. Listed for dependency mapping. |
| D8 | **Remove manual GGUF import and the Hugging Face link from the production UI** (keep behind a debug build flag at most) | An external link to a model hub in a kids app is ungated external content. Manual import contradicts "no hay que descargarlo ni importarlo a mano". | LOW | |

#### E. Store-compliance artifacts (Families program)

| ID | Feature / Artifact | Why Expected | Complexity | Notes |
|----|-------------------|--------------|------------|-------|
| E1 | **Privacy policy** on GitHub Pages: public, non-geofenced, HTML (not PDF), titled "Política de privacidad", naming the app and developer. **The same text is bundled in the app** as an offline page, reachable from onboarding and Padres. | Play requires a policy URL in Console **and** "a privacy policy link or text within the app itself", even for apps that collect nothing. Bundled text meets the in-app requirement **without an external link** in a kids app. | LOW | Required contents are in the "Privacy policy contents" section below. |
| E2 | **Data safety form**: "No user data collected", "No user data shared". Opt in to the **"Committed to follow the Play Families Policy"** badge (Security practices → Target audience and content). | Mandatory even with zero collection. Play counts only data "transmitted off the device" as collected, and "only processed locally" data is exempt. | LOW | This is truthful **only if the merged manifest has no INTERNET permission** and nothing transmits. Verify the merged manifest in CI. ML Kit and Play libraries can merge permissions in. |
| E3 | **Target audience**: 6–8, 9–12, 13–15, 16–17. **Don't select 18+.** Store-listing graphics must not suggest under-6. | Play warns that selecting age groups the app isn't designed for, or marketing that doesn't match the declared audience, can lead to rejection or removal. Parents use only a gated management area, which is normal for kids apps. | LOW | Confidence MEDIUM. 16–17 is needed because 16 is in scope. Selecting 18+ adds "mixed audience" questions for no benefit. |
| E4 | **Content rating (IARC) questionnaire** answered truthfully: educational, no violence, no user-to-user interaction, no sharing, no purchases, no location. PEGI 3 expected. | Required for every app. | LOW | If the questionnaire asks about AI-generated content, answer yes. Parent review is the mitigation story. |
| E5 | **Families policy self-check**: no ads, no IAP, no third-party SDKs that aren't approved for child-directed use, no AD_ID, no location, no device identifiers, all child-accessible content appropriate | Families requirements. Repasumi already fits (zero SDKs). The work is proving it and keeping it that way. | LOW | Add a CI check that fails if the merged manifest gains INTERNET, AD_ID or location permissions. |
| E6 | **AI-content reporting path**: the child's 🚩 goes to the parent queue (B7). In Padres (PIN-gated), **"Informar al desarrollador"** opens the user's email app via `ACTION_SENDTO`, prefilled with the question text only (no photos, no names). | Play's AI-Generated Content policy requires a way to "report or flag offensive content to developers without needing to exit the app". With no INTERNET, a strictly in-app report to the developer is impossible. The in-app flag plus a user-initiated email is the closest compliant design. | LOW | **LOW confidence** on whether this policy applies to a question generator whose output is reviewed before display (the policy's examples are chatbots and image generators). Cheap insurance. A user-initiated transfer the user reasonably expects is exempt from Data safety "sharing". |
| E7 | **Play Console "App access" declaration**: "All functionality is available. The parent PIN is created by the user on first launch; no credentials are required." | Reviewers who hit a PIN screen they can't pass reject or delay the app. That is costly with a Dec 3 deadline and multi-day Families review. | LOW | Easy to forget. |
| E8 | **Store listing**: Spanish (es-ES) title, short and full description, screenshots, feature graphic. The description states requirements (Android 10+, 4 GB RAM, ~X MB) and the offline and privacy claims. | Accurate claims protect against "misrepresentation" removals. Stating requirements up front reduces bad reviews. | LOW | Claims must match the bundled policy word for word. |
| E9 | **LICENSE** (all rights reserved), plus third-party notices for llama.cpp (MIT), the Qwen2.5 model (Apache-2.0) and ML Kit terms, reachable in-app ("Licencias") | Apache-2.0 and MIT both require notice redistribution. The model ships inside the app. | LOW | |
| E10 | **GitHub Pages reduced to a landing page plus the privacy policy** | Already decided. The public web-app pulled jsDelivr and Hugging Face, which contradicts the privacy claim attached to the same domain the policy lives on. | LOW | |

### Differentiators (Competitive Advantage)

These build on the core value. Only X1, X2 and X3 are recommended for v1.0, because they are cheap and directly support "reliable" and "nothing leaves the phone".

| ID | Feature | Value Proposition | Complexity | Notes |
|----|---------|-------------------|------------|-------|
| X1 | **Visible source grounding in review** (B5 done well): highlighted excerpt, ⚠ questions sorted first, one-tap jump to the photo | Turns review from "trust the AI" into "check against the book in 5 seconds". Generic AI quiz tools rarely tie each question to the page it came from. | MEDIUM | **v1.0** (it is B5; the differentiation is in the polish). |
| X2 | **"Funciona sin Internet" trust screen** (in onboarding and Padres → Privacidad): "Esta app no tiene permiso de Internet", verified at runtime via `PackageManager` and shown as a fact, plus a "Pruébalo en modo avión" tip | Turns the privacy promise into something a parent can check. Most kids apps can only *claim* privacy. | LOW | **v1.0.** Same copy goes into the store listing. |
| X3 | **Tamper-evident parent log**: "Último acceso a Padres: hoy 18:42", "PIN restablecido el …", "3 intentos fallidos el …" | Gives parents of 10–16-year-olds, who may guess or shoulder-surf the PIN, a way to notice, without accounts. | LOW | **v1.0** (shares the A4/A6 storage). |
| X4 | **Regenerate a single question** ("Generar otra en su lugar") from the same source fragment | Faster than editing a bad question by hand. Keeps topic size stable. | MEDIUM | v1.1. Needs fragment-level provenance from #2. |
| X5 | **Partial recovery**: validate per entity and drop only the broken topic or learner ("1 tema no se ha podido recuperar") instead of falling back to LKG for everything | Less data loss than all-or-nothing. | MEDIUM–HIGH | v1.1. C4 is enough for launch. |
| X6 | **"Probar como el niño" preview** of a draft topic before publishing | Parent sees the exact child experience. | LOW–MEDIUM | v1.1. |
| X7 | **Per-device time estimate** learned from the first generation ("En este móvil tarda unos 3 min") | Sets honest expectations on slow 4 GB devices. | LOW | v1.1. |
| X8 | **Teacher Approved opt-in** | Badge and placement in the Kids tab. Play says Families-compliant apps "can opt in to be rated". | LOW (opt-in only) | Opt in at launch but **don't plan around it**. Reviewers are US teachers, and whether a Spanish-only app gets rated is unknown (LOW confidence). |
| X9 | **Direct camera capture** (`ACTION_IMAGE_CAPTURE` intent, no CAMERA permission) instead of only picking existing photos | Fewer steps for "hacer fotos del libro". | LOW–MEDIUM | v1.1. The current `ACTION_OPEN_DOCUMENT` picker works and needs no permission, so it doesn't block launch. |

### Anti-Features (Deliberately NOT Build)

| Anti-Feature | Why Requested | Why Problematic | Alternative |
|--------------|---------------|-----------------|-------------|
| **Math-problem / "press and hold" parental gate instead of a PIN** | It is the Apple Kids-category default and needs no setup | It is designed to stop under-6s. Children aged 8–16 solve "7 × 8" trivially and could then approve their own questions. | 4-digit PIN (A1) with lockout (A4). |
| **Biometric unlock of the parent area** | Convenient | The child's fingerprint or face is often enrolled on a shared or hand-me-down phone. | Device credential **only as PIN recovery** (A6), never as the routine gate. |
| **Wipe data after N wrong PINs** | Feels "secure" | A child can destroy the family's content on purpose or by accident. | Escalating lockout (A4). |
| **PIN recovery via email, SMS or account** | Industry norm | Needs network and an account, which contradicts zero-collection and no-INTERNET. | Device screen lock plus delayed reset (A6). |
| **"Aprobar todo" one-tap bulk approve** | Saves parent time with 20+ questions | Turns "parent-reviewed" into "parent-clicked". Unverified 0.5B-model output reaches the child, and the core-value claim becomes false. | Fast per-question review (X1). Bulk **discard** is allowed. |
| **Auto-publish / show drafts to the child while the parent reviews** | Child can start sooner | The child sees unreviewed AI content, which is the failure the product exists to prevent. | `draft → published` gate (B1). |
| **Numeric "confidence %" per question from the model** | Looks scientific | A 0.5B model's self-confidence is uncalibrated. False precision leads parents to approve wrong items. | Binary, checkable signals: "dato encontrado en foto N" / ⚠ (B5). |
| **Free-form chat with the model, or child-initiated generation** | "AI tutor" appeal | Falls squarely under Play's chatbot AI-content scope and invites prompt abuse. Unreviewed output goes straight to a child. | Only the parent generates; the child only answers reviewed questions. |
| **Cloud fallback model / cloud fact-check for weak devices** | Better quality on low-end phones | Breaks "nothing leaves the phone", needs INTERNET, changes the Data safety answers and Families scrutiny. | D4 graceful degradation. |
| **Analytics, crash reporting (Crashlytics, Sentry), "anonymous usage stats"** | Debugging the closed test | Third-party SDKs in a child-directed app plus transmission means Data safety is no longer "no data collected". | Local error log viewable in Padres. During closed testing, collect feedback by talking to the 12 families. |
| **Android Auto Backup / cloud backup of study data** | Data survives phone changes | Child nicknames and textbook text would go to Google Drive, which contradicts the policy wording. `allowBackup="false"` is already set; keep it. | Explicit, parent-gated export/import file (#11, v1.1). The privacy policy says uninstalling deletes everything. |
| **Migration progress dialogs / "Database upgraded!" notices** | Transparency | Noise. Users only care that data is still there. | Silent migrations (C1). Show UI only on failure (C4). |
| **Raw error details or JSON on the recovery screen** | Debuggability | Frightens a child and confuses a parent. | Calm, parent-addressed copy. The quarantined blob is kept for v1.1 export. |
| **In-app model download over HTTP / Hugging Face link / manual GGUF import in production** | Smaller APK, model choice | Needs INTERNET and an external link in a kids app, and contradicts #16. | Play for On-device AI / Play Asset Delivery (D5). |
| **Model picker or generation settings (temperature, model size) for parents** | Power users | Support burden, more device-capability failure modes, untested quality. | One model, one tested configuration. |
| **"Rate us" prompts, social share, links to the developer's site in child-facing screens** | Growth | External links and commercial nudges aimed at children are what Families reviewers look for. | Nothing in child areas. The store listing does the marketing. |
| **Asking the child's real name, birthdate or school** | Personalization | Personal data of minors with no functional need, even if it stays local. | Nickname and school year only (D1). |

---

## Feature Dependencies

```
[C5 Stable IDs] ──┐
[C1 Schema version + migrations] ──requires── [C2 Pre-migration snapshot]
        │
        └──enables──> [B1 draft→published lifecycle]
                           ├──requires──> [B3 Edit autosave / render-safe state]
                           ├──requires──> [B2 Per-question editors] ──requires──> [B4 Validators]
                           ├──requires──> [B6 Progress + publish rule]
                           └──enables──> [B8 Edit published topic] ──enables──> [B7 Flag queue] ──enables──> [E6 Report to developer]

[#2 Chunked OCR + source validation] ──enables──> [B5 Source citation + ⚠ badge] ──enhances──> [X1]

[A5 PIN storage (Java)] ──> [A1 PIN setup] ──> [A2 Gate scope] ──> [A3 Auto-relock]
                                   │                 └──gates──> [B*, delete, E6 email intent, privacy link]
                                   ├──> [A4 Lockout] ──> [A6 Recovery] ──> [X3 Tamper-evident log]
                                   └──> [D1 Onboarding] (PIN creation is onboarding step 2)

[D2 Play device filtering] + [D3 In-app capability check] ──> [D4 Graceful degradation]
[D5 Model delivery state machine] ──> [D6 SHA-256 verify] ──> [D7 Generation] ──> [B1 draft]

[Merged-manifest audit (no INTERNET/AD_ID/location)] ──required for──> [E2 Data safety "no data"] ──> [E1 Policy text] ──> [E8 Listing claims]
[D8 Remove HF link/import] ──required for──> [E1/E5 truthful "no external links"]

CONFLICTS:
[Bulk approve]      ──conflicts──> [Core value "parent-reviewed"]
[Biometric gate]    ──conflicts──> [A2 Gate effectiveness]
[Auto Backup]       ──conflicts──> [E1/E2 "nothing leaves the phone"]
[Analytics/crash SDK] ──conflicts──> [E2 "No data collected", E5 Families SDK rule]
```

### Dependency Notes

- **B1–B9 require C1 and C5 first.** Review state, flags and per-question stats need a versioned schema and stable IDs. Building the review UI on the index-keyed v0.5 store means writing it twice. Persistence (#6) must land in a phase **before** review (#5).
- **B5 requires the #2 source-validation work.** The citation UI can be built with a placeholder ("Fuente: foto N" from the model), but the ⚠ badge only means something once validation exists. Schedule #2 before or alongside B5.
- **A (PIN) is independent of C** if the PIN lives in Java SharedPreferences (A5). Do that: it lets the PIN phase run in parallel with persistence, and keeps the PIN out of the `localStorage` blob that recovery and export handle.
- **D1 onboarding depends on A1 and D3**, so it is assembled after both exist. It can be a thin sequencing layer built late.
- **E1, E2 and E8 depend on final engineering facts**: merged-manifest permissions, `allowBackup`, model delivery mode, third-party components. Draft the policy early. **Finalize it after** model delivery (#16) and the manifest audit are done, but early enough to account for multi-day Families review before 2026-12-03.
- **E6 depends on B7.** The developer report starts from the parent's flag queue.
- **No export in v1.0, and `allowBackup=false`.** That makes C2–C4 the *only* data safety net, so they are table stakes now even though export (#11) is deferred.

---

## Expected Behavior Specs (Requirement-Ready)

### Parental gate: behavior contract
1. First launch: parent area is unreachable until a PIN exists. Onboarding step 2 creates it (enter twice, mismatch shows "Los PIN no coinciden").
2. Gated actions open a PIN pad (large keys, no "show PIN" toggle), then perform the action. A successful unlock opens a session.
3. Session ends on: leaving the parent area, 5 min idle, `onPause`, or tapping "Bloquear".
4. Wrong PIN: shake plus "PIN incorrecto". After 5 failures, a lockout timer with a countdown. Escalation 30 s → 1 min → 5 min → 15 min → 1 h (cap). The counter resets on success.
5. "¿Has olvidado el PIN?" (visible after ≥1 failure): device-credential confirmation, then set a new PIN. If the device has no secure lock: "Podrás crear un PIN nuevo dentro de 24 h", followed by a mandatory banner in Padres.
6. The child-facing UI never shows how to bypass the gate. Copy is addressed to the adult.

### Review workflow: behavior contract
1. Generation ends, the draft topic opens in Review. The child doesn't see it.
2. Each card shows the type icon, question preview, citation (foto N plus excerpt), a ⚠ when the source isn't verified, and [Aprobar] [Editar] [Descartar].
3. Edit opens the type-specific editor with live validation. Save returns the question to `pending` with an "editada" mark, so the parent still approves explicitly.
4. Header: "12 de 20 revisadas · 9 aprobadas · 3 descartadas". [Descartar las pendientes] needs a confirm.
5. [Publicar] needs no pending questions and ≥1 approved. It moves the topic to `published` and shows it to the selected learners.
6. A child's 🚩 adds the question to "Preguntas marcadas (n)" in Padres → edit, discard or dismiss. Optional "Informar al desarrollador" opens a prefilled email (text only).

### Recovery: behavior contract
1. On start: read → parse → validate shape → check `schemaVersion`.
2. Older version: snapshot, migrate in order, validate, write, then confirm LKG. A failure in any step restores the snapshot and shows the recovery screen.
3. Newer version: read-only mode with the message "Actualiza Repasumi para ver tus datos".
4. Unparseable data: quarantine the raw blob, try LKG, then show the recovery screen with the LKG date and options.
5. Any failed save shows a persistent, parent-addressed notice. Never fail silently.

### Privacy policy contents (zero-collection, child-directed)
Spanish (and optionally English). Must include:
- Title "Política de privacidad"; app name "Repasumi"; developer name; contact email; effective date and a change-notice clause.
- **Plain statement:** the developer does not collect, receive, store or share any personal data. The app has no Internet permission.
- **What is processed on the device only:** photos the user chooses (read via the system picker, not stored as copies, *verify*), OCR text, generated questions, learner nicknames and school year, answers and progress, the parent PIN (as a hash). Purpose of each.
- **Retention and deletion:** stays on the device until deleted in the app (topic, learner, all data) or by uninstalling or clearing app data. No backups (Android backup disabled), so uninstalling deletes everything permanently.
- **Permissions:** none dangerous. Photos are chosen through Android's document picker. Text-to-speech uses the device's speech engine.
- **On-device components:** Google ML Kit text recognition (runs on device), llama.cpp, the Qwen2.5-0.5B-Instruct model (Apache-2.0). Downloads and updates come from Google Play under Google's privacy policy. Model delivery through Play involves no developer server.
- **Children:** designed for ages 6–16 under parental supervision. No ads, no purchases, no tracking, no third-party SDKs. Parent area protected by PIN. Aligns with the Google Play Families Policy, COPPA and GDPR/LOPDGDD (minors' data is not processed by the developer).
- **Security:** data stays in the app's private storage, protected by the device's own security. Recommend a screen lock on the device.
- **AI content:** questions are produced by an on-device AI model and reviewed by the parent before the child sees them. How to report a problem (flag in the app, optional email).

### Data safety form: expected answers
- Data collected: **No**. Data shared: **No**. (Valid only if no INTERNET in the merged manifest and no SDK transmits.)
- Security practices: the encryption-in-transit and deletion questions apply only if something is collected. Not applicable here.
- Families: opt in to the "Committed to follow the Play Families Policy" badge.
- Privacy policy URL: the GitHub Pages policy.

---

## MVP Definition

### Launch With (v1.0, by 2026-12-03)

- [ ] **C1, C2, C3, C4, C5, C6**: versioned persistence, snapshot, LKG, recovery screen, stable IDs. Without them the review workflow and family data are unsafe.
- [ ] **B1–B9**: draft/published lifecycle, per-question approve/edit/discard with all five editors, autosave, validators, citation with ⚠, progress and publish rule, flag queue, edit published topic, resumable drafts. **This is the core value.**
- [ ] **A1–A7**: PIN setup, gate scope, auto-relock, lockout, Java-side storage, offline recovery, change PIN. Required by Families intent and by the core value.
- [ ] **D1–D8**: short onboarding, Play device filtering, in-app capability check, graceful degradation, model-availability states, visible SHA-256 step, staged generation, production UI without import or HF link.
- [ ] **E1–E10**: policy (web and bundled), Data safety, audience 6–17 without 18+, IARC, Families self-check plus a CI manifest guard, AI-report path, App access note, listing, LICENSE and notices, Pages reduction.
- [ ] **X1, X2, X3**: cheap differentiators that make "reliable" and "nothing leaves the phone" visible.

### Add After Validation (v1.1)

- [ ] X4 single-question regenerate: add if closed-test parents edit or discard more than ~30 % of questions.
- [ ] X5 partial recovery: add if any tester hits the recovery screen.
- [ ] X6 child preview, X7 time estimate: polish once the core flow is stable.
- [ ] X9 direct camera capture: add if testers find "take photos first, then pick" clumsy.
- [ ] Export/import (#11), which also gives the quarantined blob a way out of the device.
- [ ] Teacher Approved follow-up (X8): only if Play actually rates the app.

### Future Consideration (v2+)

- [ ] Larger model variants delivered by Play device targeting to ≥8 GB devices (better questions on capable phones). Needs a quality evaluation harness first.
- [ ] Multi-device family sharing *without* cloud (local file or QR transfer). Defer until there is a monetization or retention reason.

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| B1 draft→published gate | HIGH | MEDIUM | P1 |
| B2 per-question editors (5 types) | HIGH | HIGH | P1 |
| B3 edit autosave / render-safe | HIGH | MEDIUM | P1 |
| B4 validators | MEDIUM | LOW | P1 |
| B5 source citation + ⚠ | HIGH | MEDIUM | P1 |
| B6 progress + publish rule | MEDIUM | LOW | P1 |
| B7 flag queue | MEDIUM | LOW | P1 (also store) |
| B8 edit published topic | MEDIUM | MEDIUM | P1 |
| B9 resumable drafts | MEDIUM | LOW | P1 |
| C1/C2 schema + migrations + snapshot | HIGH | MEDIUM | P1 |
| C3 LKG + visible save failures | HIGH | MEDIUM | P1 |
| C4 recovery screen | HIGH | MEDIUM | P1 |
| C5 stable IDs | HIGH | MEDIUM | P1 |
| C6 newer-schema guard | LOW | LOW | P1 (cheap) |
| A1–A5 PIN, scope, relock, lockout, storage | HIGH | LOW–MEDIUM | P1 |
| A6 offline PIN recovery | HIGH | MEDIUM | P1 |
| A7 change PIN | LOW | LOW | P1 |
| D1 onboarding | HIGH | LOW–MEDIUM | P1 |
| D2 Play device filtering | HIGH | LOW | P1 |
| D3/D4 capability check + degradation | HIGH | LOW | P1 |
| D5 model availability states | HIGH | MEDIUM | P1 |
| D6 visible SHA-256 step | MEDIUM | LOW | P1 |
| D8 remove import/HF link | HIGH (compliance) | LOW | P1 |
| E1–E10 compliance artifacts | HIGH (gate to launch) | LOW each | P1 |
| E6 report-to-developer email | LOW–MEDIUM | LOW | P1 (insurance) |
| X2 "sin Internet" trust screen | MEDIUM | LOW | P1 |
| X3 tamper-evident log | MEDIUM | LOW | P2 (ship if A4/A6 done early) |
| X4 regenerate one question | MEDIUM | MEDIUM | P2 |
| X5 partial recovery | MEDIUM | MEDIUM–HIGH | P2 |
| X6 child preview | LOW | LOW–MEDIUM | P3 |
| X7 per-device time estimate | LOW | LOW | P3 |
| X8 Teacher Approved opt-in | LOW–MEDIUM | LOW | P2 (opt-in only) |
| X9 direct camera capture | MEDIUM | LOW–MEDIUM | P2 |

---

## Competitor / Reference Pattern Analysis

| Feature | Typical kids/edtech apps | AI quiz generators (teacher tools) | Repasumi approach |
|---------|--------------------------|-----------------------------------|-------------------|
| Parental gate | Apple Kids-style math gate or hold-to-open for under-6. PIN plus account recovery for parental-control apps (Bitdefender, Fire Kids, Screen Time). | n/a (adult users) | PIN, lockout, **offline** device-credential recovery, tamper-evident log. |
| AI content review | Rare in kids apps. Most avoid generative AI or keep it server-side and moderated. | Draft → teacher reviews and edits → assign (Google Forms/Classroom Gemini drafts, Quizizz AI and similar). Usually **no per-question source citation**. | Draft → per-question review **with page citation and ⚠ check** → publish. The parent plays the teacher role. |
| AI content reporting | Server-side moderation queues | In-product feedback to vendor | Child flag → parent queue → optional user-initiated email (no network). |
| Data recovery | Account or cloud restore | Cloud | Local snapshot, LKG and quarantine; explicit export in v1.1. |
| Model delivery | Server inference (no download) | Server inference | Play for On-device AI / PAD, device filtering, graceful degradation. |
| Privacy claim | "We comply with COPPA" plus a Data safety list of collected data | Accounts, school data agreements | "No data collected" **with runtime-visible proof** (no INTERNET permission). |

Confidence on the competitor column: MEDIUM–LOW. It comes from vendor help pages and general product knowledge, not hands-on testing during this research.

---

## Gaps / Open Questions for Phase Research

1. **Does Play's AI-Generated Content policy formally apply** to a parent-reviewed, on-device question generator? The official page's examples are chatbots and image generators, and it doesn't address on-device models. E6 is cheap insurance. Confirm during Play review or in Policy Center. (LOW confidence)
2. **Do ML Kit, the Play AI Delivery or Asset Delivery library, or AndroidX Biometric merge INTERNET or other permissions** into the manifest? This decides whether the "no data collected" answer and the X2 trust screen are truthful. Verify with the merged-manifest report in the #16 phase.
3. **Install-time vs fast-follow delivery** affects onboarding UX (D5) and whether a 400 MB copy step is needed for llama.cpp mmap. Belongs in STACK and needs a spike in the #16 phase.
4. **The exact RAM value Play's Device Catalog shows for the 4 GB test device**: set the exclusion threshold from it (D2).
5. **Teacher Approved availability for Spanish-language apps** is unknown. Opt in; don't depend on it.
6. **Whether the photo picker path persists any copies** of photos in app storage. That decides the policy wording ("no se guardan copias" vs "se guardan hasta que borres el tema").
7. **IARC questionnaire wording on AI-generated content** wasn't verified. Answer truthfully when filling it in.

---

## Sources

Official (primary; MEDIUM per the classify-confidence seam, cross-checked where noted):
- Google Play Families Policies, including "adult action" examples (PIN, password, birthdate…), age screens, SDK, permission and content rules: https://support.google.com/googleplay/android-developer/answer/9893335 and https://support.google.com/googleplay/android-developer/answer/17122218
- Google Play AI-Generated Content policy (in-app reporting or flagging requirement, scope examples): https://support.google.com/googleplay/android-developer/answer/13985936
- Data safety section (collection = off-device transmission, on-device exemption, privacy policy required, Families badge opt-in, user-initiated sharing exemption): https://support.google.com/googleplay/android-developer/answer/10787469
- User Data policy / privacy policy requirements (Console link **and** in-app link or text, non-PDF, required contents): https://support.google.com/googleplay/android-developer/answer/10144311
- Target audience and app content settings (age groups, Teacher Approved eligibility, misrepresentation risk): https://support.google.com/googleplay/android-developer/answer/9867159
- Play for On-device AI (AI packs, delivery modes, 1.5 GB per pack, 4 GB cumulative, device targeting by RAM/SoC/model, `getPackStates`, `WAITING_FOR_WIFI` over 200 MB, `showConfirmationDialog`): https://developer.android.com/google/play/on-device-ai
- Device catalog and exclusion rules (RAM / SoC, "partially excluded" status): https://support.google.com/googleplay/android-developer/answer/1286017 and https://developer.android.com/google/play/device-targeting
- Android Auto Backup behavior and `allowBackup`: https://developer.android.com/identity/data/autobackup

Secondary (LOW–MEDIUM):
- Google Play generative AI policy coverage (TechCrunch, 9to5Google, Android Developers Blog, June 2024 "enabling safe AI experiences"): https://android-developers.googleblog.com/2024/06/enabling-safe-ai-experiences.html
- Teacher Approved program background and criticism: https://blog.google/products-and-platforms/platforms/google-play/teacher-approved-apps/ and https://educationhq.com/news/googles-teacher-approved-badge-on-kids-apps-is-problematic-study-209535/
- PIN lockout and recovery patterns: Bitdefender parental PIN (https://www.bitdefender.com/consumer/support/answer/99740/), Apple Screen Time passcode recovery and escalation (https://mackeeper.com/blog/forgot-screen-time-passcode)
- Shipping a large on-device model through Play (practitioner write-up): https://proandroiddev.com/shipping-a-584-mb-gemma-model-through-google-play-b4df1825c6b8

Project context:
- `.planning/PROJECT.md` (scope, constraints, decisions); `.planning/codebase/CONCERNS.md` findings as quoted in PROJECT.md. `AndroidManifest.xml` already has `allowBackup="false"`. Photos are picked with `ACTION_OPEN_DOCUMENT` (no media permissions). There is a `ACTION_VIEW` browser intent that needs gating or removal.

---
*Feature research for: offline child-directed study app with on-device generative AI (Google Play Families)*
*Researched: 2026-11-13*
