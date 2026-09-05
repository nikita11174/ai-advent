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
