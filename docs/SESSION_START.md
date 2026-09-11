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

**Day 6 TECHNICALLY COMPLETE**: commit `2966cbe2fec86aceefe42af701cd39b3b4bdc9d1` на `day_6`.
Canonical — `docs/tasks/DAY-06.md`, фактический отчёт — `docs/agent-runs/DAY-06.md`.
Backend 58/58, frontend 17/17 и оба builds PASS.
Runtime agent per UUID отправляет полный собственный stack; restart очищает память.
Real API PASS; desktop UI 1440×1000 PASS; всего 5 реальных provider calls. Видео отложено владельцем.
**Следующая задача — Day 7**: создать `day_7` от завершённой `day_6`; `developer` не merge до завершения/review недели.

**Week 1 / Day 5 финализирован**: implementation DONE, automated PASS, OpenAI access VERIFIED;
experiment ACCEPTED — 7 completed / 9 attempts, quality OWNER APPROVED. Report и day_1–day_5 опубликованы.
Canonical contract — `docs/tasks/DAY-05.md`; исторический анализ — `docs/agent-runs/DAY-05-ANALYSIS.md`.
Базовая ветка `developer`: fast-forward integration checkpoint `06355ef`, отправлен в origin;
implementation checkpoint Day 5 `951e65c`. `main` не изменялся.
Итог — `docs/DAY-05-REPORT.md`; история — `docs/agent-runs/DAY-05.md`; next action — CURRENT-STATE.
Дополнительные реальные calls/retries Day 5 и Day 6 не требуются.
Day 1–4 recording demos выполнены; сохранение/загрузка OBS-видео и отправка Day 5 report не подтверждены.
Frontend http://127.0.0.1:4201 доступен; desktop UI PASS с одним дополнительным реальным вызовом. Day 7 — следующий task; evidence — последний раздел Day 6 task.

Не реализовывать будущие Challenge Days заранее. Требования текущего Day всегда имеют приоритет
над long-term vision.
