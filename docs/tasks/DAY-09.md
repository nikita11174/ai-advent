# Day 9 — summary plus recent context

Статус: **TECHNICALLY COMPLETE — automated and targeted real restart/reuse verification PASS**. Branch progression: `day_8` → `day_9`.

## OFFICIAL REQUIREMENT

Сохранять latest N messages, отдельно summarise older history и отправлять summary + recent
messages вместо full history. Сравнить token usage и answer quality с вариантом без compression.

## ORGANIZER CLARIFICATION

Нет дополнительных требований организатора сверх предоставленного owner текста.

## APPROVED PROJECT INTERPRETATION

Day 9 впервые вводит явное memory → context preparation: сравниваются FULL и SUMMARY + RECENT.
Summary — derived state; raw durable memory Day 7 остаётся источником для повторной подготовки и
comparison и не заменяется destructively.

## OUT OF SCOPE

Sliding Window, Sticky Facts / Key-Value Memory, Branching, удаление raw history и превращение
Days 1–5 в agent experiments.

## IMPLEMENTATION AND AUTOMATED VERIFICATION — 2026-09-11

`ContextPolicy` теперь имеет две реальные реализации: `FullContextPolicy` отправляет полный raw
stack, а `SummaryRecentContextPolicy` строит `system + summary + latest N committed raw messages +
pending user input`. `recentMessageCount` — любой положительный N, по умолчанию 4; system и pending
input в N не считаются. `ConversationSummaryService` создаёт derived summary из старых raw сообщений,
а `AgentSummaryStore` хранит его отдельно в `docs/local/agent-summaries/<dialog-id>.json`.
Canonical `AgentHistoryStore` raw history не изменяется и остаётся источником для повторной подготовки.

Основные ответы сохраняют Day 8 `TokenMetrics`; summary generation возвращает отдельные metrics и
не смешивается с main contextTokens. Ошибка summary/provider/save не коммитит новую raw history;
FULL сохраняет прежнее поведение. API Agent принимает optional `contextMode` и `recentMessageCount`,
UI показывает выбранный режим, summary и раздельные оценки.

Focused Day 9/backend command:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; mvn '-Dtest=EngineeringReviewAgentTest,AgentDialogServiceTest,AgentControllerTest,AgentSummaryStoreTest,MainTest' test
```

Focused tests и полный `mvn test` прошли без failures/errors; полный suite после Day 9 содержит 74
теста. `npm test -- --watch=false --no-progress` прошёл 18/18, `npm run build` прошёл с
существующим предупреждением budget для `app.scss`; использован Node 22.22.3, product code не
менялся.

## TARGETED REAL RE-ACCEPTANCE — 2026-09-11

Исходная contradictory restart smoke сгенерировала отдельную локальную запись
`docs/local/agent-sessions/day9-real-acceptance/restart-summary-failure.json`; она сохранена
как фактическая история и не переписана.

Targeted smoke использовал тот же Dialog B (`1894f763-243b-4cbc-b004-1816e1bb1eeb`) после
полного backend restart. До restart: history 9 сообщений (8 committed), SHA-256
`2084312089438A...`, summary coverage 4, SHA-256 `60F521AA6FBE051D...`. После fresh load
оба store использовали тот же абсолютный root `E:\sandbox\sandbox\ai-advent\docs\local`;
debugger подтвердил `raw=9`, `targetCoverage=4`, `stored=4`.

Выполнен ровно один успешный DeepSeek call (`SUMMARY_RECENT`, `recentMessageCount=4`).
Summary-generation path не входил; outbound stack содержал `system + persisted summary + latest
4 committed raw messages + pending user` (7 сообщений). Ответ подтвердил кодовое слово и
ограничение email после commit. Summary coverage и SHA-256 не изменились; history сохранена на
том же пути и выросла до 11 сообщений (10 committed), SHA-256
`8C938775FCF71CD6...`. Product persistence defect не воспроизведён; первоначальное расхождение
классифицировано как acceptance/runtime-evidence inconsistency.
