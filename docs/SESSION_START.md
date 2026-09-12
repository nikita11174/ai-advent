# Session Start

## Project

**Local AI Worker / AI Advent Challenge 9** — независимый sandbox-проект, который
превращает ежедневные задания challenge в небольшие additive increments local developer tool.
Engineering Review Mentor остаётся его специализированным use case.

## Reading order

1. `AGENTS.md`
2. `docs/PRODUCT-MANIFEST.md`
3. `docs/ARCHITECTURE.md`
4. `docs/CURRENT-STATE.md`
5. текущий `docs/tasks/DAY-XX.md`
6. `docs/WORKFLOW.md`
7. `README.md`, когда нужны команды build/test/run

## Agent workflow

- GPT координирует acceptance criteria и owner decisions вне репозитория.
- Codex и Claude Code — взаимозаменяемые implementation agents.
- Перед работой проверить `git status` и staged/unstaged diff.
- Продолжать существующее решение; не уничтожать незавершённую работу другого агента.
- Commit/push разрешены только после явной owner-авторизации.

## Current task

**Week 2 CLOSED** at `95c54858ddd671824ed022720d10e87cb7ba2e02` (`fix: resolve week 2 ui races`).
Product direction authority — `docs/PRODUCT-MANIFEST.md`; architecture-boundary authority —
`docs/ARCHITECTURE.md`; decision rationale —
`docs/decisions/ADR-001-local-ai-worker-direction.md`. Future Days are requirement-driven,
additive increments; do not pre-build MCP, RAG, local-model or pipeline infrastructure.

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
**Day 8 TECHNICALLY COMPLETE** (`e0085941`): exact full outbound agent stack получает deterministic local estimates request/context/response; provider usage nullable и отображается отдельно. `contextTokenLimit` проверяется до provider/save/runtime commit и возвращает HTTP 413 без mutation canonical memory. Backend 67 и frontend 17 tests, оба builds PASS. Реальная API-приёмка: 5 успешных DeepSeek calls; short request после growth сохранил estimate request `26`, а context вырос с `89` до `3426`; overflow `3510 > 1` дал HTTP 413 без provider call и без изменения JSON; recovery сохранил порядок истории и исключил marker. Desktop UI 1440×1000 PASS: loading, Markdown, metrics, error state и dialog switching; console только с ожидаемым 413 deliberate-overflow.

**Day 9 TECHNICALLY COMPLETE — automated and targeted restart/reuse PASS**: `FullContextPolicy` сохраняет
Day 8 full stack, `SummaryRecentContextPolicy` строит `system + summary + latest N + pending user`;
raw Day 7 memory остаётся durable source of truth, summary хранится отдельно. `recentMessageCount` —
положительный count raw messages, default 4; summary-generation metrics отделены от main metrics.
Backend full suite: 74 tests PASS; frontend 18/18 и build PASS с существующим app.scss budget warning.
Targeted real restart/reuse использовал тот же Dialog B и один
DeepSeek call: fresh process загрузил raw history 9 сообщений и persisted summary coverage=4,
summary generation не выполнялся, outbound содержал summary + latest 4 raw + pending user, history
выросла до 11 на том же пути, summary hash не изменился. Initial contradictory smoke сохранён как
локальная фактическая запись; persistence defect не воспроизведён. Следующий шаг — Day 10 planning;
FULL/SUMMARY benchmark повторно не запускать.

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
Следующий шаг — Day 10 planning from completed Day 9.

**Day 10 COMMITTED — automated and controlled real acceptance PASS**: branch `day_10`, original feature commit
`f2db8634453182de927f21412fd069b8e33b8297` (`feat: add context management strategies`),
создана от Day 9 documentation checkpoint `314c57e`. Sliding Window использует exact latest-N
linear context без summary; Sticky Facts хранит отдельный LLM-derived key/value state с coverage
по committed user messages, recovery и отдельными facts metrics; Branching хранит immutable
checkpoint topology вне `ContextPolicy` и принимает explicit branch ID. Independent review ran 89/89 backend tests;
frontend 20/20 и build PASS с существующими budget warnings. Controlled acceptance использовал
9 provider calls: Sliding потерял четыре ранних facts, Sticky тремя maintenance calls сохранил
все четыре, Branching восстановил изолированные PostgreSQL/ClickHouse continuations после
restart. Desktop UI 1440×1000 PASS. Post-review remediation: backend 96/96, frontend 22/22 и
build PASS; targeted real acceptance A–E PASS, F/G — AUTOMATED_TEST_EVIDENCE_ONLY. Временный
branch-UI blocker был 4.042-секундным legitimate provider request, тогда как local harness ждал
3 секунды; product defect не найден. Day 9 FULL/SUMMARY benchmark повторно не запускать:
historical metrics сохранены, raw artifact недоступен.

**Second post-review frontend remediation committed and accepted** (`95c548`): independent targeted
verification закрыла сохранение explicit checkpoint selection, error-side maintenance metrics и
topology-load/branch-send race. Frontend 26/26 и production build PASS. Narrow desktop 1440×1000
acceptance подтвердила C2 → create-branch URL, disabled send во время задержанного topology GET и
видимые facts metrics у structured 502; provider не вызывался.

Не реализовывать будущие Challenge Days заранее. Требования текущего Day всегда имеют приоритет
над long-term vision.
