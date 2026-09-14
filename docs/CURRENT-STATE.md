# AI Advent Challenge 9 — Current State

Единственный operational source of truth. Product authority —
[`PRODUCT-MANIFEST.md`](PRODUCT-MANIFEST.md); architecture-boundary authority —
[`ARCHITECTURE.md`](ARCHITECTURE.md); requirements и evidence конкретного Day —
`docs/tasks/DAY-XX.md` и `docs/agent-runs/DAY-XX.md`.

## Current work

| Поле | Состояние |
|---|---|
| Product | Local AI Worker; Engineering Review Mentor — specialized use case |
| Current milestone | Week 2 CLOSED AND PUBLISHED; integrated into local and origin `developer`; product direction formally recorded |
| Current branch | `developer`; verify HEAD with `git rev-parse HEAD` |
| Current implementation | Days 1–10 complete; Week 2 agent/context/topology behavior accepted and integrated into local `developer` |
| Immediate next step | Owner defines the next challenge task; implement only its smallest additive capability |

## Challenge status

| Day | Factual status |
|---|---|
| 1–5 | Complete; day_1–day_5 published/integrated; organizer publication artifacts where noted in each Day record remain owner-controlled |
| 6 | Technically complete; Day 6 browser recording scenario executed 2026-09-13; OBS file and submission not verified |
| 7 | Complete; durable raw conversation history/restart verification and generic backend availability indicator established; browser availability scenario executed 2026-09-13 |
| 8 | Complete; context-limit/token metrics and inherited availability indicator established; browser video scenario executed 2026-09-13 (OBS file/submission not verified) |
| 9 | Complete; FULL/SUMMARY_RECENT and persisted derived summary established; browser comparison executed 2026-09-13: both modes retained four early facts and SUMMARY_RECENT main context was 2007 vs FULL 2492 local estimated tokens (OBS file/submission not verified) |
| 10 | Complete: `f2db863` feature, `b538516` post-review fixes, `95c548` UI-race fixes; controlled acceptance and independent verification closed; browser video scenario executed 2026-09-14: Sliding N=2 retained 0/4 early facts, Sticky retained 4/4, branches A=PostgreSQL and B=ClickHouse with sibling isolation PASS (OBS file/submission not verified) |

Week 2 code branches are published on origin: `day_6` at `8b4524f`, `day_7` at
`69547b3`, `day_8` at `2cf1978`, `day_9` at `13ddba1`, and `day_10` at `98f9112`.
Local and origin HEADs matched at publication; `developer` remained at `06355ef`.
Browser scenarios for Days 6–10 have been executed. OBS recordings, uploads and
organizer submissions remain owner-controlled and are not verified here.

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
- Day 10 inherits the generic backend availability indicator; health polling is
  not provider, history or agent-metrics activity.
- Detailed local runtime evidence is ignored under `docs/local/`; it is not
  canonical truth and must not contain secrets.

## Navigation

Read the current `docs/tasks/DAY-XX.md` before implementation. Load a historical
task/run document only when the current task, a concrete question or an evidence
claim requires it; do not read Day 1–10 history by default.
