# Day 9 — implementation run

Дата: 2026-09-11. Ветка: `day_9`, базовый Day 8 commit `e0085941`.

Реализованы FULL и SUMMARY_RECENT context policies. Полная raw history Day 7 остаётся canonical
memory; summary хранится отдельно per dialog и создаётся только для older messages. Latest N —
положительный count raw committed messages без system и pending input, default 4. Summary-generation
metrics возвращаются отдельно от main Day 8 metrics. Добавлены API-поля режима/N и отображение режима,
summary и обоих наборов наблюдений в Agent UI.

Проверено автоматически: focused Day 9/backend tests и полный Maven suite PASS (74 теста, 0
failures/errors). `npm test -- --watch=false --no-progress` PASS (18/18), `npm run build` PASS с
существующим предупреждением budget для `app.scss`; использован Node 22.22.3. Реальные API calls и
browser acceptance на момент этой записи не выполнялись.

Real Day 9 acceptance и targeted restart/reuse завершены; следующий шаг — Day 10 planning.

## Targeted real restart/reuse re-acceptance — 2026-09-11 17:33 MSK

Исходная contradictory smoke сохранена в ignored `docs/local/agent-sessions/day9-real-acceptance/`
и не переписана. Для того же Dialog B (`1894f763-243b-4cbc-b004-1816e1bb1eeb`) до restart
были зафиксированы history 9/8 committed, SHA-256 `2084312089438A...`, summary coverage 4,
SHA-256 `60F521AA6FBE051D...`.

После полного restart debugger подтвердил общий абсолютный root
`E:\sandbox\sandbox\ai-advent\docs\local`, загрузку raw=9 и summary coverage=4; при N=4
targetCoverage=4, summary generation не требовалась. Один API turn успешно выполнил один
основной DeepSeek call. Outbound: `system + persisted summary + latest 4 committed raw messages
+ pending user`, 7 сообщений; `summaryMetrics=null`. Summary SHA-256 остался прежним, history
на том же пути выросла до 11 сообщений; новый SHA-256 `8C938775FCF71CD6...`. Ответ сохранил
«Сатурн» и post-commit email constraint. Product persistence defect не воспроизведён; прошлый
FAIL сохранён как acceptance/runtime-evidence inconsistency.
