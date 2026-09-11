# Session Start

## Project

**Engineering Review Mentor / AI Advent Challenge 9** — независимый sandbox-проект, который
превращает ежедневные задания challenge в небольшие increments одного developer tool.

## Reading order

1. `AGENTS.md`
2. `docs/PRODUCT-MANIFEST.md`
3. `docs/CURRENT-STATE.md`
4. текущий `docs/tasks/DAY-XX.md`
5. `docs/WORKFLOW.md`
6. `README.md`, когда нужны команды build/test/run

## Agent workflow

- GPT координирует acceptance criteria и owner decisions вне репозитория.
- Codex и Claude Code — взаимозаменяемые implementation agents.
- Перед работой проверить `git status` и staged/unstaged diff.
- Продолжать существующее решение; не уничтожать незавершённую работу другого агента.
- Commit/push разрешены только после явной owner-авторизации.

## Current task

**Day 6 TECHNICALLY COMPLETE**: feature commit `2966cbe2fec86aceefe42af701cd39b3b4bdc9d1`,
documentation checkpoint `c0d736369b7bfc033b59f6ee36ab43af5553b658`.
Canonical — `docs/tasks/DAY-06.md`, фактический отчёт — `docs/agent-runs/DAY-06.md`.
Backend 58/58, frontend 17/17 и оба builds PASS.
Runtime agent per UUID отправляет полный собственный stack; restart очищает память.
Real API PASS; desktop UI 1440×1000 PASS; всего 5 реальных provider calls. Видео отложено владельцем.
**Day 7 TECHNICALLY COMPLETE (uncommitted)**: `AgentHistoryStore` сохраняет raw stack per dialog UUID,
fresh `AgentDialogService` восстанавливает его, а agent сохраняет completed snapshot до runtime commit.
Контракт — `docs/tasks/DAY-07.md`, evidence — `docs/agent-runs/DAY-07.md`. Focused 33 и полный
backend 62 tests PASS. Реальный restart/provider smoke PASS: 3 DeepSeek calls; A2 после нового
backend process получил «Сатурн» из восстановленного JSON, B1 не получил контекст A. Метрики и context
reduction не добавлены. `developer` не merge до завершения/review недели.

Week 2: Day 8 измеряет request/full-context/response tokens без reduction; Day 9 сравнивает full
history с summary + latest N, не уничтожая raw memory; Day 10 сравнивает Sliding Window, Sticky
Facts / Key-Value Memory и Branching. Memory — canonical state, context — представление для одного
LLM call, metrics — token observations.

**Week 1 / Day 5 финализирован**: implementation DONE, automated PASS, OpenAI access VERIFIED;
experiment ACCEPTED — 7 completed / 9 attempts, quality OWNER APPROVED. Report и day_1–day_5 опубликованы.
Canonical contract — `docs/tasks/DAY-05.md`; исторический анализ — `docs/agent-runs/DAY-05-ANALYSIS.md`.
Базовая ветка `developer`: fast-forward integration checkpoint `06355ef`, отправлен в origin;
implementation checkpoint Day 5 `951e65c`. `main` не изменялся.
Итог — `docs/DAY-05-REPORT.md`; история — `docs/agent-runs/DAY-05.md`; next action — CURRENT-STATE.
Дополнительные реальные calls/retries Day 5 и Day 6 не требуются.
Day 1–4 recording demos выполнены; сохранение/загрузка OBS-видео и отправка Day 5 report не подтверждены.
Frontend http://127.0.0.1:4201 доступен; desktop UI PASS с одним дополнительным реальным вызовом.
Следующий продуктовый шаг — Day 8; сначала нужен review/commit Day 7 checkpoint.

Не реализовывать будущие Challenge Days заранее. Требования текущего Day всегда имеют приоритет
над long-term vision.
