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

**Day 6 реализован локально** на `day_6` от developer `06355effc56d60b4d43b58c7e274b0ec20622337`.
HEAD равен базе; commit/push не выполнялись. Canonical — `docs/tasks/DAY-06.md`, фактический отчёт —
`docs/agent-runs/DAY-06.md`. Backend 58/58, frontend 17/17 и оба builds PASS.
Runtime agent per UUID отправляет полный собственный stack; restart очищает память.
Real API PASS: 4 calls A1/A2/B1/A3, точный outgoing context подтверждён. UI 1440×1000 PASS; TECHNICALLY COMPLETE; video PENDING.

**Week 1 / Day 5 финализирован**: implementation DONE, automated PASS, OpenAI access VERIFIED;
experiment ACCEPTED — 7 completed / 9 attempts, quality OWNER APPROVED. Report READY, publication PENDING.
Canonical contract — `docs/tasks/DAY-05.md`; исторический анализ — `docs/agent-runs/DAY-05-ANALYSIS.md`.
Ветка `day_5` от finalized Day 4 `9ff5b29`; implementation checkpoint `951e65c`.
Итог — `docs/DAY-05-REPORT.md`; история — `docs/agent-runs/DAY-05.md`; next action — CURRENT-STATE.
Day 6 TECHNICALLY COMPLETE. Следующий шаг — подготовка видео; дополнительные real calls/retries не нужны.
Day 4 implementation, technical verification и owner UX acceptance DONE; video/publication PENDING.
Day 1–3 visual/submission items остаются отдельными pending действиями.

Не реализовывать будущие Challenge Days заранее. Требования текущего Day всегда имеют приоритет
над long-term vision.
