# Day 3 — Agent runs

## 2026-09-03 — plan finalization and implementation

- Agent: Codex
- Branch/base: `day_3` / `fe22498`; product base `da103f54`
- Goal: four-strategy experiment, transparent benchmark evaluation и local dialog history.
- Applied decisions: PaymentReceived benchmark; fixed checklist; manual found/missed/questionable;
  fixed FREE Markdown; approved council; two-call SELF_PROMPT; 3-runs-per-strategy harness.
- Components: reasoning endpoint/service, Angular strategy UX, dialog API/store, local evidence
  helpers and workflow docs.
- Verification: backend 33/33 + package; frontend 9/9 + production build; 15 real DeepSeek calls;
  FREE/CONTROLLED proxy smoke; dialog create/update/list/load и restart-restore smoke.
- Result: technical acceptance PASS. EXPERTS strongest on this benchmark; one SELF_PROMPT run
  exposed a language-constraint omission. Chrome MCP unavailable; owner visual acceptance remains.
- Planning commit: `ebe6e6ca8c465899008eb305f3ca147dc271643e`.
- Implementation commit: `897ca53dd355a4df754e8c02e45e1feb8c956c47`.
- Evidence checkpoint: `4f61e21a417441481ad702f5a71b57fc5952fdad`.
- Remaining: browser review, demo video, separately authorized publication.

## 2026-09-05 — organizer/chat evidence update

- Agent: Codex
- Source: local Telegram export AI Advent Challenge #9 through 2026-09-05.
- Goal: проверить требования, советы автора курса и практические варианты решений.
- Result: Day 2/3 clarifications получили message-ID provenance; community experiments отделены
  от organizer requirements. Product code и operational status не менялись.
- Added knowledge: deterministic shape не означает одинаковые значения; prompt/API controls
  дополняют друг друга; system prompt подходит для council; LLM judge optional; SELF_PROMPT
  constraint loss и task-dependent winners подтверждены практикой. Day 4/5 проиндексированы как
  опубликованные, без planning или implementation.
