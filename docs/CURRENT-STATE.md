# AI Advent Challenge 9 — Current State

Единственный operational source of truth. Product authority —
[`PRODUCT-MANIFEST.md`](PRODUCT-MANIFEST.md); architecture-boundary authority —
[`ARCHITECTURE.md`](ARCHITECTURE.md); requirements и evidence конкретного Day —
`docs/tasks/DAY-XX.md` и `docs/agent-runs/DAY-XX.md`.

## Current work

| Поле | Состояние |
|---|---|
| Product | Local AI Worker; Engineering Review Mentor — specialized use case |
| Current milestone | Week 2 CLOSED; product direction formally recorded |
| Current branch | `day_10`; verify HEAD with `git rev-parse HEAD` |
| Current implementation | Days 1–10 complete; Week 2 agent/context/topology behavior accepted |
| Immediate next step | Owner defines the next challenge task; implement only its smallest additive capability |

## Challenge status

| Day | Factual status |
|---|---|
| 1–5 | Complete; day_1–day_5 published/integrated; organizer publication artifacts where noted in each Day record remain owner-controlled |
| 6 | Technically complete; Day 6 browser recording scenario executed 2026-09-13; OBS file and submission not verified |
| 7 | Complete; durable raw conversation history/restart verification and generic backend availability indicator established |
| 8 | Complete; context-limit/token metrics and inherited availability indicator established; browser video scenario executed 2026-09-13 (OBS file/submission not verified) |
| 9 | Complete; FULL/SUMMARY_RECENT and persisted derived summary established; browser comparison executed 2026-09-13: both modes retained four early facts and SUMMARY_RECENT main context was 2007 vs FULL 2492 local estimated tokens (OBS file/submission not verified) |
| 10 | Complete: `f2db863` feature, `b538516` post-review fixes, `95c548` UI-race fixes; controlled acceptance and independent verification closed |

## Important current constraints

- Current agent model integration is `DeepSeekClient`; a formal interchangeable
  model-provider boundary is future work, not current implementation.
- Task/Run, MCP/tool boundary, retrieval/RAG, local-model runtime and pipelines
  are intentional future extension points. Do not pre-build them.
- Raw history is canonical; summary and Sticky Facts are rebuildable derived
  memory; branches/checkpoints are topology. See `ARCHITECTURE.md` for the
  complete contract.
- Day 9 real FULL/SUMMARY benchmark metrics are historical; its missing raw
  artifact must not be recreated with new provider calls.
- Detailed local runtime evidence is ignored under `docs/local/`; it is not
  canonical truth and must not contain secrets.

## Navigation

Read the current `docs/tasks/DAY-XX.md` before implementation. Load a historical
task/run document only when the current task, a concrete question or an evidence
claim requires it; do not read Day 1–10 history by default.
