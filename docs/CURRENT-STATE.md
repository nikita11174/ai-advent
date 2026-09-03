# AI Advent Challenge 9 — Current State

Единственный operational source of truth: текущая задача, branch, статусы и следующий шаг.
Подробные требования, решения и evidence каждого increment находятся в `docs/tasks/DAY-XX.md`;
repository/code имеет приоритет при расхождении с документом.

## Current work

| Поле | Состояние |
|---|---|
| Product | Engineering Review Mentor |
| Current milestone | Week 1 / Day 3 |
| Current branch | `day_3` |
| HEAD / Day 3 base | `da103f54ac0e2ce4d3e258da04ca2b0d3bee04fb` — verified Day 2 implementation |
| Working tree | Documentation-only Day 3 planning/normalization changes; no Day 3 application code |
| Status | `DAY_3_PLANNING / OWNER_DECISIONS_REQUIRED` |
| Next action | Owner resolves four decisions in `docs/tasks/DAY-03.md`; implementation remains blocked |

`day_3` создан напрямую от verified `day_2` HEAD без merge в `developer`/`main`. Ветка не
опубликована.

## Challenge days

| Day | Implementation | Verification | Submission / publication | Canonical detail |
|---|---|---|---|---|
| Day 1 | DONE | DONE | Repository/code link DONE; demo video PENDING | `docs/tasks/DAY-01.md` |
| Day 2 | DONE | DONE | Demo video PENDING; `day_2` push/merge/publication PENDING | `docs/tasks/DAY-02.md` |
| Day 3 | NOT_STARTED | NOT_STARTED | NOT_STARTED | `docs/tasks/DAY-03.md` |

Day 2 planning commit: `58f1ae4` (`Plan AI Advent Day 2`).

Day 2 implementation commit:
`da103f54ac0e2ce4d3e258da04ca2b0d3bee04fb` (`Implement AI Advent Day 2`).

Day 1 repository: `https://github.com/nikita11174/ai-advent`. Day 1 demo video остаётся отдельным
pending item и не блокирует Day 3 planning.

## Implemented baseline

```text
Browser / Angular 22
  -> /api dev proxy
  -> Spring Boot 3.5.5 / Java 21 :18080
  -> POST /api/review
  -> DeepSeekClient / deepseek-v4-flash / non-thinking / non-streaming
```

- Day 1 FREE: русский safe Markdown engineering analysis; request без `mode` остаётся FREE.
- Day 2: explicit FREE или validated CONTROLLED JSON с per-request output controls, raw response
  inspection и frontend FREE-vs-CONTROLLED comparison.
- UI history/settings существуют только в page state; предыдущая история модели не отправляется.
- `DEEPSEEK_API_KEY` доступен только backend environment; `.env.local` игнорируется Git.

Фактические Day 1/2 test counts, runtime smoke и negative validation evidence не дублируются здесь:
они зафиксированы в соответствующих task documents.

## Day 3 planning boundary

Открыты и принадлежат owner:

1. final benchmark;
2. evaluation method;
3. coexistence Day 2 output mode и Day 3 reasoning;
4. expert council composition.

Recommendations не являются approvals. До решений owner не менять Java/Angular application code,
не добавлять dependencies и не реализовывать Day 3. Полные варианты и trade-offs —
`docs/tasks/DAY-03.md`.
