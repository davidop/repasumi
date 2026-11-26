# Repasumi — Product specification and roadmap (approved 2026-11-26)
## Product decisions (15/15, all D)
1. Integral evolution; stability is a release gate.
2. Hybrid pre-generation and progressive generation of fresh questions per topic visit; deduplication, validation, adaptive difficulty.
3. GGUF manager: import, explicit external downloads, hardware detection, compatibility checks, local benchmarks and automatic tuning. No cloud inference.
4. Local educational library: photos (1–30), PDF, documents, text, OCR, image correction, classification by grade/subject/topic, concepts, summaries, duplicate pages and editing.
5. Adaptive pedagogy: multiple choice, true/false, fill, match, order, flashcards, drag/drop, images, maps, diagrams, written answers, audio, problems, mock exams, spaced repetition and explanations grounded in sources.
6. Positive gamification: points, first-attempt stars, streaks, levels, badges, avatars, missions, adaptive goals; no ads, purchases, lives, time pressure or streak penalties.
7. Original design system, mascot, light/dark themes, age-adaptive UI, reduced motion and neurodivergent accessibility.
8. Independent local student profiles, parent dashboard, mastery maps, exam calendar and study plans.
9. Local RAG tutor: text/voice, Socratic hints, source references, age adaptation and reinforcement exercises. Offline speech and multimodality subject to verified hardware/model support.
10. Evaluation pipeline: schema, factual grounding, answers, semantic duplicates, curriculum and difficulty checks, independent evaluation, rejection/regeneration, calibrated confidence where feasible, parent review and automated evaluations. Never claim factual perfection.
11. Adaptive Android performance: RAM, thermal and battery limits, compatible acceleration, queues, cache, cancellation, timeouts, recovery and responsive UI.
12. Privacy and safety by design: no mandatory accounts, external telemetry or cloud fallback; least permissions, encrypted sensitive data/backups, local parental PIN, deletion/export/restore, child safety and untrusted-document prompt injection defenses. Explicit model downloads only; preserve existing no-INTERNET architecture where possible via external browser.
13. Modular incremental architecture, SDD, ADRs, tests, CI/CD, agent-assisted development, reproducible benchmarks, accessibility and security checks.
14. Incremental Preview/Beta/Stable releases, feature flags, migrations and acceptance gates.
15. Autonomous repository development through 1.0; ask only for real blockers, irreversible decisions or physical-device testing. Notify only verified deliverables or actionable blockers.
## Roadmap
- 0.5: prioritize real GGUF inference, fresh validated questions per visit, existing 5th Primary and 2nd ESO topics, new icon, model management, performance, cancellation and diagnostics.
- 0.6: educational library and local OCR/import.
- 0.7: adaptive exercises and local tutor.
- 0.8: design system, accessibility and gamification.
- 0.9: profiles, family dashboard and advanced security.
- 1.0: integration, optimization and final validation.
Basic privacy, security and accessibility apply from 0.5. Preserve existing features and data. Split milestones into testable increments.
## Acceptance and evidence
- Android CI green for exact release commit; downloadable APK exists, verify artifact provenance and SHA-256; document signing status and installation/migration.
- Real GGUF inference offline, no cloud fallback; distinguish generated questions from existing bank; target ten fresh validated questions per session, no silent substitution. If insufficient, show explicit status and allow retry; measure latency before fixing numerical performance budgets.
- Validate schema, grounding and duplicate detection; review representative factual samples. Never treat model self-evaluation as proof.
- Verify both existing educational grades/topics, icon, accessibility, retry/cancellation and persisted progress across updates.
- Use synthetic or appropriately licensed fixtures; no real child data or copyrighted textbook photos in public repo.
- Test airplane mode and actual inference on Xiaomi 12T, record latency, RAM, thermal behavior, failures and device details. CI alone is not physical validation; label Preview accordingly.
- Track issues, PRs, commits, CI, artifact links and limitations. Do not mark stable or 1.0 complete without physical validation and required quality gates.
## Execution
Inspect current source and existing specs/roadmap first; reconcile rather than overwrite. Start with 0.5 regression tests and inference diagnostics. Work in small reviewable changes; preserve signing and migration compatibility; never expose secrets. Record evidence in repository. Stop autonomous work at verified 1.0.
