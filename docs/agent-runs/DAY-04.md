# Day 4 agent runs

## 2026-09-05 — implementation and verification

- Agent: Codex.
- Branch/base: `day_4` from Day 3 knowledge checkpoint `0271bf8`; planning commit `237126a`.
- Goal: compare one PaymentReceived review at temperature `0`, `0.7`, `1.2`, preserving Day 1–3.
- Decisions applied: separate Day 4 experiment; only temperature varies; thinking disabled;
  transparent human evaluation; local dialog persistence without LLM memory.
- Components: DeepSeek request builder, `/api/temperature-review`, Angular selected/compare UI,
  persisted observations, deterministic tests, 9-call harness.
- Verification: Maven package PASS (36 tests); Angular tests PASS (11), production build PASS;
  IntelliJ build PASS; 9/9 real experiment calls PASS; Angular-proxy real call PASS; Chrome
  three-card comparison PASS; dialog restore after backend restart PASS.
- Benchmark conclusion: temperature `0` was most repeatable and evidence-focused; `0.7` produced
  the best balance of coverage and useful remediation alternatives; `1.2` produced the greatest
  diversity but also the most speculative material. This is benchmark-specific, not universal.
- Security: `.env.local`, dialog JSON and raw experiment evidence remain ignored; no key is stored
  in tracked content.
- Implementation commit: `0ac3ca7b7e05b18570ba4dc438d3fc8b65768820`.
- Remaining: owner visual acceptance, demo video, repository publication.

## 2026-09-05 — accepted UX/responsive polish and documentation checkpoint

- Agent: Codex; branch `day_4`, implementation/evidence base `e230c0c`.
- Goal: finish owner-requested responsive layout, restore presentation state and improve human
  evaluation fields without changing experiment semantics.
- Components: `.gitignore`, Angular `app.ts/html/scss/spec.ts`, global `styles.scss`.
- Decisions: adaptive 3/2/1 comparison columns; collapsible sidebar ≤900 px; jump button in
  composer flow; long Markdown text wraps. Keep 9 kB CSS budget after removing duplicate rules.
- Persistence: `state.ui` stores presentation selections only; four conclusion fields restore;
  old string conclusion retained verbatim in accuracy. No model conversation memory added.
- Verification: frontend 12/12 tests PASS, production build PASS (known non-blocking 4 kB style
  warning). Chrome 1440×1000, 1024×768, 768×1024, 500×844 PASS; refresh restore PASS;
  shared-shell Day 1–3 checks PASS, including a real FREE response. Backend unchanged, not rerun.
- Evidence: seven ignored screenshots in `docs/local/screenshots/day-04-after-ux/`;
  no local/generated evidence tracked.
- Owner accepted implementation. Local commit:
  `aacbbf32701a5107b6afb81178d637cafb6d00a4` — `Polish AI Advent Day 4 UX`.
- Result: UX acceptance DONE; Day 4 submission still PENDING for video and publication.
  No push/merge; Day 5 not started.
- Follow-up: canonical task/current-state/run documentation updated after owner request;
  documentation-only update not committed in this run. No tests rerun for documentation edits.
