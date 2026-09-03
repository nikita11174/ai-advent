# AI Advent Challenge 9 — Current State

Единственный operational source of truth. Детали и evidence находятся в `docs/tasks/DAY-XX.md`.

## Current work

| Поле | Состояние |
|---|---|
| Product | Engineering Review Mentor |
| Current milestone | Week 1 / Day 3 |
| Current branch | `day_3` |
| Day 3 base | `da103f54ac0e2ce4d3e258da04ca2b0d3bee04fb` — verified Day 2 |
| Status | Implementation and technical verification DONE; submission PENDING |
| Next action | Owner visual review, demo video, then separately authorized publication flow |

## Challenge days

| Day | Implementation | Verification | Submission / publication |
|---|---|---|---|
| Day 1 | DONE | DONE | Repository link DONE; demo video PENDING |
| Day 2 | DONE (`da103f54`) | DONE | Demo video and `day_2` publication PENDING |
| Day 3 | DONE | DONE; owner visual acceptance pending | Demo video and publication PENDING |

Day 2 planning commit: `58f1ae4`. Day 1 repository:
`https://github.com/nikita11174/ai-advent`.

Day 3 planning commit: `ebe6e6ca8c465899008eb305f3ca147dc271643e`. Day 3 implementation
commit: `897ca53dd355a4df754e8c02e45e1feb8c956c47`.

## Current architecture

```text
Angular 22 :4201
  -> /api proxy
  -> Spring Boot 3.5.5 / Java 21 :18080
       -> /api/review (Day 1/2 FREE or CONTROLLED)
       -> /api/reasoning-review (Day 3 DIRECT/STEP_BY_STEP/SELF_PROMPT/EXPERTS)
       -> /api/dialogs (local JSON create/list/load/update)
  -> DeepSeek deepseek-v4-flash
```

- Day 3 использует fixed FREE Markdown и не образует матрицу с Day 2 controls.
- Four-way comparison делает 5 LLM calls: `1/1/2/1`; SELF_PROMPT prompt inspectable, EXPERTS имеет
  три fixed perspectives.
- Reference evaluation — manual `found/missed/questionable` + explainable winner, без LLM judge и
  fake scores.
- Dialog JSON находится в ignored `docs/local/mentor-dialogs/`. История восстанавливает UI, но
  **никогда не отправляется DeepSeek как conversation memory**.
- Concise run evidence — tracked `docs/agent-runs/DAY-03.md`; detailed local evidence — ignored
  `docs/local/agent-sessions/`.
- `DEEPSEEK_API_KEY` доступен только backend environment; `.env.local` игнорируется Git.

Фактические counts, real API evidence, ограничения и acceptance — `docs/tasks/DAY-03.md`.
