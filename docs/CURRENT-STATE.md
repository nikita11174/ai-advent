# AI Advent Challenge 9 — Current State

Единственный operational source of truth. Product authority —
[`PRODUCT-MANIFEST.md`](PRODUCT-MANIFEST.md); architecture-boundary authority —
[`ARCHITECTURE.md`](ARCHITECTURE.md); requirements и evidence конкретного Day —
`docs/tasks/DAY-XX.md` и `docs/agent-runs/DAY-XX.md`.

## Current work

| Поле | Состояние |
|---|---|
| Product | Local AI Worker; Engineering Review Mentor — specialized use case |
| Current milestone | Day 13 code accepted; final video pending |
| Current branch | `day_13`; Day 13 product commit `15c7598` |
| Current implementation | Day 11 memory/provider-model/UX baseline plus Day 12 Profile and Day 13 Task/TaskState capabilities complete; structural refactor remains accepted |
| Immediate next step | Create `day_14` from accepted Day 13 HEAD and start Day 14 Invariants; Day 13 video may be recorded later |

## Day 12 — CLOSED

- Product commit: `24a11b1 feat: add agent profiles`.
- `DAY12_CLOSED: YES`; `VIDEO_RECORDED: YES`; следующий Day 13 впоследствии принят по коду.
- Final independent review: **ACCEPTED_WITH_NONBLOCKING_NOTES**; BLOCKER/HIGH/MEDIUM отсутствуют.
- Profile is orchestration configuration describing **HOW** the agent works; Memory is retained/
  accumulated information describing **WHAT** is remembered. `Profile config != Memory`.
- Delivered: Profile domain/store/service/API with independent local JSON persistence; optional
  normal-message `profileId`; orchestration resolution; supplemental main-context projection and
  token accounting; Profile selector, «Без профиля», create/edit, safe Inspector metadata and
  per-dialog `DialogUiState.selectedProfileId`.
- Isolation: Profile is not raw history, summary, Sticky Facts, `SHORT_TERM`, `WORKING` or
  `LONG_TERM`; it persists independently of dialog deletion. Provider execution remains unaware
  of Profile persistence.
- Consistency fixes: empty catalog stale IDs normalize to «Без профиля» after successful load;
  async creation is owned by origin dialog/editor operation; narrow
  `PUT /api/dialogs/{id}/profile-selection` preserves unrelated DialogUiState fields.
- Verification: frontend 48/48 PASS; relevant backend tests PASS; frontend build PASS;
  browser A/B/no-profile, restart persistence, refresh and per-dialog selection PASS.
  Final automated walkthrough PASS; external video recorded and technically verified at
  `E:\Video-AI\day-12.mkv` (04:10, 1920x1080). `VIDEO_RECORDED: YES`.

## Day 13 — CODE ACCEPTED

- Product commit: `15c7598 feat: add task state machine`.
- `DAY13_CODE_ACCEPTED: YES`; `READY_FOR_VIDEO: YES`; `VIDEO_RECORDED: NO`; `DAY14_STARTED: NO`.
- Delivered: persisted Task/TaskState with stage, current step, expected action, status and revision;
  happy path `PLANNING -> EXECUTION -> VALIDATION -> DONE`; pause/resume and restart persistence.
- Dialog != Task. One Task may be used by multiple Dialogs; Task.id is the `WORKING` Memory scope,
  while dialog history and `SHORT_TERM` Memory remain separate. Legacy UUID scopes require explicit adoption.
- `TaskService` owns persisted lifecycle mutation. Task context is projected into each main model call;
  ModelExecutor and storage boundaries remain unchanged.
- Verification: backend targeted tests and compile PASS; frontend 64/64 PASS and build PASS;
  browser lifecycle, restart, multi-dialog and final consistency scenarios PASS. Video has not been recorded.

## Structural refactor — OWNER ACCEPTED, 2026-09-17

- **ACCEPTED**: independent final acceptance на HEAD `7843b3f`; BLOCKER/HIGH/MEDIUM нет.
  Неблокирующее LOW: часть production visibility расширена для межпакетных тестов.
- Backend namespace — `dev.aiadvent.worker`; root содержит только
  `LocalAiWorkerApplication` и `Main`; capabilities — `model`, `dialog`, `memory`,
  `context`, `agent`, `api`, `review`. Dependency graph соответствует
  [architecture contract](ARCHITECTURE.md); capability cycles отсутствуют.
- Accepted commit chain: `2c40187` execution boundary → `0d1c561` model extraction →
  `4295f7c` worker naming → `21266f1` state extraction → `7843b3f` orchestration extraction.
- **Final verification**: JDK 21; `mvn -q -DskipTests compile` и
  `mvn -q -DskipTests test-compile` PASS; `mvn -q clean test` — 136 tests в 25 suites,
  0 failures/errors/skipped. Suites разделены при extraction; сценарии сохранены.
- Local startup через `scripts/run-backend.ps1` PASS;
  `GET /api/health` → HTTP 200 `{"status":"UP"}`. External providers не вызывались;
  agent-owned runtime остановлен после smoke. Это evidence запуска, не постоянно работающий backend.
- API/persistence compatibility и `git diff --check` PASS; data migration не требуется.
  Default DeepSeek, selected executor для main/summary/sticky и legacy
  `DeepSeekReviewClient -> DeepSeekTransport` сохранены.
- Frontend/build/browser и real-provider evidence относятся к ранее проверенному
  UX Shell baseline; в final backend acceptance они не повторялись.

## Challenge status

| Day | Factual status |
|---|---|
| 1–5 | Complete; day_1–day_5 published/integrated; organizer publication artifacts where noted in each Day record remain owner-controlled |
| 6 | Technically complete; Day 6 browser recording scenario executed 2026-09-13; OBS file and submission not verified |
| 7 | Complete; durable raw conversation history/restart verification and generic backend availability indicator established; browser availability scenario executed 2026-09-13 |
| 8 | Complete; context-limit/token metrics and inherited availability indicator established; browser video scenario executed 2026-09-13 (OBS file/submission not verified) |
| 9 | Complete; FULL/SUMMARY_RECENT and persisted derived summary established; browser comparison executed 2026-09-13: both modes retained four early facts and SUMMARY_RECENT main context was 2007 vs FULL 2492 local estimated tokens (OBS file/submission not verified) |
| 10 | Complete: `f2db863` feature, `b538516` post-review fixes, `95c548` UI-race fixes; controlled acceptance and independent verification closed; browser video scenario executed 2026-09-14: Sliding N=2 retained 0/4 early facts, Sticky retained 4/4, branches A=PostgreSQL and B=ClickHouse with sibling isolation PASS (OBS file/submission not verified) |
| 11 | CLOSED: implementation complete; structural acceptance ACCEPTED; backend targeted tests and frontend 40/40 PASS; runtime/browser acceptance PASS; final video demo PASS and VIDEO RECORDED: YES. `2ab727f` adds live-smoked supported dialog deletion and compact sidebar UX. |
| 12 | CLOSED: `24a11b1` Profile capability; final independent review ACCEPTED_WITH_NONBLOCKING_NOTES; tests/runtime/browser PASS; `VIDEO_RECORDED: YES`. |
| 13 | CODE ACCEPTED: `15c7598` Task/TaskState capability; backend/frontend/runtime/browser evidence PASS; `VIDEO_RECORDED: NO`; Day 14 not started. |

Week 2 code branches are published on origin: `day_6` at `8b4524f`, `day_7` at
`69547b3`, `day_8` at `2cf1978`, `day_9` at `13ddba1`, and `day_10` at `98f9112`.
Local and origin HEADs matched at publication; `developer` remained at `06355ef`.
Browser scenarios for Days 6–10 have been executed. OBS recordings, uploads and
organizer submissions remain owner-controlled and are not verified here.

## Important current constraints

- Agent provider/model choice is implemented in the current scope. Backend-owned keys are
  `DEEPSEEK` (default), `WEAK`, `MEDIUM`, `STRONG`; browser passes only the key. `AgentModelCatalog`
  and `AgentModelExecutor` isolate provider execution; main, summary and Sticky Facts use the
  selected executor while preserving history, memory, task scope and topology semantics.
- Run, MCP/tool boundary, retrieval/RAG, local-model runtime and pipelines are intentional future
  extension points. Do not pre-build them. Task/TaskState is implemented; Day 14 Invariants and Day 15
  controlled red-path transitions remain future work.
- Raw history is canonical; summary and Sticky Facts are rebuildable derived
  memory; branches/checkpoints are topology. See `ARCHITECTURE.md` for the
  complete contract.
- Memory scope terminology: `SHORT_TERM` is chat/dialog scope, `WORKING` is task
  scope, and `LONG_TERM` is user scope. The current single-user/local global
  installation representation is the implementation mapping of user-scoped
  `LONG_TERM`; physical storage does not define semantic scope. Memory owns
  retained interaction/state, while context owns the projection into one model call.
- Profile is independent orchestration configuration, not Memory or Context. `AgentDialogService`
  resolves an optional Profile for a normal agent message; `ConversationAgent` adds one supplemental
  main-context instruction after the authoritative base system contract, before token estimation.
  Profile storage does not build provider prompts and ModelExecutor does not know Profile persistence.
- Profile selection is per-dialog `DialogUiState`, while Profile documents persist independently of
  dialog deletion. `PUT /api/dialogs/{id}/profile-selection` changes only `selectedProfileId`.
- Day 9 real FULL/SUMMARY benchmark metrics are historical; its missing raw
  artifact must not be recreated with new provider calls.
- Day 10 inherits the generic backend availability indicator; health polling is
  not provider, history or agent-metrics activity.
- Detailed local runtime evidence is ignored under `docs/local/`; it is not
  canonical truth and must not contain secrets.
- All `docs/**` content is private local workspace state and must not be staged or committed
  without explicit publication authorization.

## Local run baseline

- Frontend uses Node `22.22.0`, Angular 21 and TypeScript 5.9; run it through the npm `start`
  script on `http://127.0.0.1:4200`.
- Backend debug uses JDK 21 and `--server.port=18080`; frontend `/api` proxy targets that endpoint.
- The exact PowerShell and IntelliJ IDEA Debug procedure is in [`README.md`](../README.md).

## Near-term queue

1. **Day 14 Invariants** — NOT STARTED. Create `day_14` from accepted Day 13 HEAD. Rules/constraints remain distinct from Memory; deterministic
   enforcement and contextual/semantic evaluation remain separate. Do not build a universal engine.
2. **Day 15 Controlled transitions** — NOT STARTED. Model/user proposes an action; application
   validates legal TaskState transition and exclusively mutates persisted state. Red paths, invalid
   skips, rework and malformed model output remain under application control.
3. **Privacy cleanup** — separate maintenance task; do not combine it with Day 13 code acceptance.

## Current UI/UX consolidation

Problem: continued challenge increments risk turning the interface into a permanently visible
engineering form. Target a conversational workspace: dialogs/navigation on the left,
conversation in the center, a collapsible context inspector on the right, and the composer at
the bottom. Current scope groups Task, Memory, Context, branches/checkpoints, Metrics,
run settings and experiments/comparison; Day 12 adds Profile selection/create/edit and safe
Inspector metadata. Meanwhile,
compact current-state indicators keep technical details available on demand.

Principles: conversation-first, progressive disclosure, normal Russian labels where appropriate,
and interaction ideas from modern coding assistants without copying branding. Non-goals: a new
Angular state library, IDE clone or large design-system rewrite.
The owner explicitly expanded backend scope to safe agent provider/model dispatch, while existing
memory/context/history/topology semantics must remain unchanged.

UX Shell V1 uses Local AI Worker branding, a three-area shell and compact task/memory editors.
Technical conversation metadata is progressively disclosed under «Технические детали»; primary UI
does not use Day-specific wording. Chrome desktop and drawer-resize smoke passed; local screenshots
are ignored under `docs/local/ui-evidence/day11`. The Day 11 final video demo PASS and video is recorded.
The Day 11 polish adds compact single-line recent dialog rows, batches of 15 with «Показать ещё»,
and supported delete with confirmation. `DELETE /api/dialogs/{id}` is orchestrated by
`AgentDialogService`: it removes only dialog document, raw/derived/topology state, dialog `SHORT_TERM`
and cached dialog state; task `WORKING` and global/user `LONG_TERM` remain intact.

## Challenge execution note

Set explicit acceptance criteria first, then implement directly within a bounded scope. Avoid
review-of-review loops and extra orchestration/process layers without a concrete need. Keep UI work
intentionally bounded because it can be token-expensive. The submission video demonstrates the
feature; complete verification remains internal evidence and need not be reproduced in full on video.

## Separate privacy maintenance

Known state: `docs/**` is private local workspace; earlier history tracks 31 files under `docs/**`;
the public README contains internal workflow material and private-doc references; `/docs/` is
protected by both `.gitignore` and `.git/info/exclude`. A future separate task must decide which
tracked docs leave future snapshots, preserve local copies, sanitize README, decide whether
`AGENTS.md` remains public, and separately assess historical Git cleanup. `git rm --cached` alone
does not erase already published history. No privacy cleanup is authorized in the current pass.

## Navigation

Read the current `docs/tasks/DAY-XX.md` before implementation. Load a historical
task/run document only when the current task, a concrete question or an evidence
claim requires it; do not read Day 1–10 history by default.
