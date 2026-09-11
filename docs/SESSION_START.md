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
**Day 7 TECHNICALLY COMPLETE**: `AgentHistoryStore` сохраняет raw stack per dialog UUID,
fresh `AgentDialogService` восстанавливает его, а agent сохраняет completed snapshot до runtime commit.
Контракт — `docs/tasks/DAY-07.md`, evidence — `docs/agent-runs/DAY-07.md`. Focused 33 и полный
backend 62 tests PASS. Реальный restart/provider smoke PASS: 3 DeepSeek calls; A2 после нового
backend process получил «Сатурн» из восстановленного JSON, B1 не получил контекст A. Метрики и context
reduction не добавлены. `developer` не merge до завершения/review недели.
**Day 8 TECHNICALLY COMPLETE (uncommitted)**: exact full outbound agent stack получает deterministic local estimates request/context/response; provider usage nullable и отображается отдельно. `contextTokenLimit` проверяется до provider/save/runtime commit и возвращает HTTP 413 без mutation canonical memory. Backend 67 и frontend 17 tests, оба builds PASS. Реальная API-приёмка: 4 planned DeepSeek calls; short request после growth сохранил estimate request `26`, а context вырос с `89` до `3426`; overflow `3510 > 1` дал HTTP 413 без provider call и без изменения JSON; recovery сохранил порядок истории и исключил marker. После восстановления Chrome MCP owner авторизовал пятый вызов для desktop UI 1440×1000: loading, Markdown, local metrics `30 / 93 / 161`, provider usage `68 / 121 / 189`, error state и dialog switching PASS; console только с ожидаемым 413 deliberate-overflow.

**Day 9 TECHNICALLY COMPLETE — automated and targeted restart/reuse PASS**: `FullContextPolicy` сохраняет
Day 8 full stack, `SummaryRecentContextPolicy` строит `system + summary + latest N + pending user`;
raw Day 7 memory остаётся durable source of truth, summary хранится отдельно. `recentMessageCount` —
положительный count raw messages, default 4; summary-generation metrics отделены от main metrics.
Backend full suite: 74 tests PASS; frontend 18/18 и build PASS с существующим app.scss budget warning.
Targeted real restart/reuse использовал тот же Dialog B и один
DeepSeek call: fresh process загрузил raw history 9 сообщений и persisted summary coverage=4,
summary generation не выполнялся, outbound содержал summary + latest 4 raw + pending user, history
выросла до 11 на том же пути, summary hash не изменился. Initial contradictory smoke сохранён как
локальная фактическая запись; persistence defect не воспроизведён. Следующий шаг — Day 9
implementation checkpoint; FULL/SUMMARY benchmark повторно не запускать.

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
Следующий шаг — Day 9 implementation checkpoint.

Не реализовывать будущие Challenge Days заранее. Требования текущего Day всегда имеют приоритет
над long-term vision.
