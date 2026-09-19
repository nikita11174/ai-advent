# AI Advent Challenge 9 — Current State

Единственный operational source of truth. Product authority —
[`PRODUCT-MANIFEST.md`](PRODUCT-MANIFEST.md); architecture-boundary authority —
[`ARCHITECTURE.md`](ARCHITECTURE.md); requirements и evidence конкретного Day —
`docs/tasks/DAY-XX.md` и `docs/agent-runs/DAY-XX.md`.

## Current work

| Поле | Состояние |
|---|---|
| Product | Local AI Worker; Engineering Review Mentor — specialized use case |
| Current milestone | Day 11 CLOSED: implementation complete, structural acceptance accepted, tests/runtime/browser/final video PASS; dialog delete UX live-smoked |
| Current branch | `day_11`; committed HEAD `2ab727f`; product baseline `b3d4d28` |
| Current implementation | Day 11 memory, provider/model selection, UX Shell V1 and supported dialog deletion complete; structural refactor complete/accepted |
| Immediate next step | Day 12 NOT STARTED; ждать явной owner instruction |

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
| 12 | NOT STARTED; await explicit owner instruction |

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
- Task/Run, MCP/tool boundary, retrieval/RAG, local-model runtime and pipelines
  are intentional future extension points. Do not pre-build them.
- Raw history is canonical; summary and Sticky Facts are rebuildable derived
  memory; branches/checkpoints are topology. See `ARCHITECTURE.md` for the
  complete contract.
- Memory scope terminology: `SHORT_TERM` is chat/dialog scope, `WORKING` is task
  scope, and `LONG_TERM` is user scope. The current single-user/local global
  installation representation is the implementation mapping of user-scoped
  `LONG_TERM`; physical storage does not define semantic scope. Memory owns
  retained interaction/state, while context owns the projection into one model call.
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

1. **Day 12 User Profile** — NOT STARTED; requires explicit owner instruction. Profile is
   orchestration configuration (style, response format, workflow, roles and constraints), not Memory.
   `Profile config != Memory` and Day 11 `LONG_TERM` remains user-scoped accumulated information.
2. **Day 13 Task State** — future only: persisted happy-path lifecycle
   `planning -> execution -> validation -> done`, including pause/resume; a Task may outlive a dialog.
3. **Day 14 Invariants** — future only: deterministic and semantic constraints remain distinct from
   dialog and memory; do not build a generic invariant engine.
4. **Day 15 Controlled transitions** — future only: application code validates allowed TaskState
   transitions, including invalid/red paths; a model proposes next action but never assigns TaskState.
5. **Privacy cleanup** — separate maintenance task; do not combine it with Day 11 acceptance or
   Day 12 implementation.

## Current UI/UX consolidation

Problem: continued challenge increments risk turning the interface into a permanently visible
engineering form. Target a conversational workspace: dialogs/navigation on the left,
conversation in the center, a collapsible context inspector on the right, and the composer at
the bottom. Current scope groups Task, Memory, Context, branches/checkpoints, Metrics,
run settings and experiments/comparison; Profile is excluded. Meanwhile,
compact current-state indicators keep technical details available on demand.

Principles: conversation-first, progressive disclosure, normal Russian labels where appropriate,
and interaction ideas from modern coding assistants without copying branding. Non-goals: a new
Angular state library, IDE clone, large design-system rewrite, Day 12 Profile or Day 13 Task State.
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
