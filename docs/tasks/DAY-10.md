# Day 10 — context-management comparison

Статус: **ORIGINAL DAY 10 COMMIT FOLLOWED BY ACCEPTED POST-REVIEW REMEDIATION**. Branch: `day_10`;
original feature commit `f2db8634453182de927f21412fd069b8e33b8297`
(`feat: add context management strategies`) от
Day 9 documentation checkpoint `314c57e`.

## OFFICIAL REQUIREMENT

Реализовать и сравнить Sliding Window, Sticky Facts / Key-Value Memory и Branching по answer
quality, preservation важных деталей, token usage и practical usability. Branching требует
checkpoint, две независимые continuations и switching между ними.

## ORGANIZER CLARIFICATION

Нет дополнительных требований организатора сверх предоставленного owner текста.

## APPROVED PROJECT INTERPRETATION

Использовать raw canonical memory, сохранённую в Day 7, как source для подходов. Multiple actual
linear context-building behaviors могут оправдать policy abstraction. Branching не следует
принудительно включать в неё, если его conversation topology требует естественной отдельной модели.

## OUT OF SCOPE

Destructive replacement raw memory, ретроспективная перестройка Days 1–5 и abstractions, которые
не нужны фактическим Day 10 approaches.

## IMPLEMENTATION AND AUTOMATED VERIFICATION — 2026-09-11

Sliding Window добавлен как linear `ContextPolicy`: system не считается, `N` считает последние
committed raw messages, pending user не считается, summary не используется. `AgentHistoryStore`
остаётся полным canonical raw memory.

Sticky Facts используют отдельный `StickyFactsStore` под `docs/local/agent-facts/`. LLM extraction
возвращает validated key/value JSON, `coveredUserMessageCount` считает committed user messages.
Candidate facts используются только для текущего outbound после успешного extraction; authoritative
facts сохраняются после успешного main provider и raw-history commit. Отдельные extraction metrics
возвращаются как `factsMetrics`. При stale/missing coverage факты восстанавливаются из canonical
user messages; recovery calls также входят в `factsMetrics`.

Branching реализован вне `ContextPolicy`: `AgentBranchStore` хранит immutable checkpoints и любое
число branch continuations под `docs/local/agent-branches/`. Branch ID передаётся явно, sibling
messages не попадают в context, branch locks изолированы.

Добавлены checkpoint/branch API и Agent UI controls. FULL, SUMMARY_RECENT, Day 7 persistence,
Day 8 metrics/context limit и Day 9 summary persistence сохранены.

Проверка:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; mvn -q test
cd frontend
npm test -- --watch=false --no-progress
npm run build
```

Independent review: backend 89/89 PASS. Historical Day 10 frontend: 20/20 PASS. Frontend build PASS с существующими budget
warnings для initial bundle и `app.scss`. Реальные provider calls и browser acceptance описаны ниже.

## CONTROLLED REAL ACCEPTANCE — 2026-09-11

Ожидаемый и фактический budget: **ровно 9 успешных provider calls**. Provider retries не
выполнялись. Sliding использовал 1 main call. Sticky
использовал 2 recovery calls для U1/U2, 1 current-message update и 1 main call. Branching
использовал 2 branch-decision calls и 2 post-restart queries. Provider retries не выполнялись.

Линейный fixture был одинаковым для двух свежих dialogs: `system + U1 + A1 + U2 + A2`, 5
messages до probes, одинаковый SHA-256 raw history. При `N=2` Sliding outbound подтвердил
`system + U2 + A2 + pending`; U1/A1 отсутствовали, summary и facts отсутствовали. Все четыре
запрошенных ранних факта были **MISSED**; модель явно отказалась выдумывать значения. Main
metrics: local `67/365/266`, provider `253/160/413` (request/context/response и
prompt/completion/total соответственно).

Sticky восстановил facts из U1 и U2, затем обновил их текущим probe. Maintenance calls имели
local metrics `36/119/38`, `167/276/30`, `67/176/30`; provider totals `150`, `237`, `170`.
Maintenance overhead: local `270/571/98`, provider `438/119/557`. С учётом Sticky main call
total provider work для сценария составил `896`. Persisted state имел
`coveredUserMessageCount=3` и все четыре explicit facts. Main outbound был
`system + Sticky Facts + U2 + A2 + pending`; main metrics `67/398/47`, provider `292/47/339`.
Все четыре факта были **RETAINED**. Sticky дороже и операционно сложнее, но сохранил детали.

Branching создал один immutable checkpoint и две независимые ветки с одним checkpoint ID.
До restart A выбрала PostgreSQL, B — ClickHouse; branch user decisions и outbound contexts не
содержали sibling decision, а normal linear history осталась с 3 messages. После полной
остановки backend порт 18080 был unavailable; новый process восстановил checkpoint, обе branch
IDs и обе continuation histories. Post-restart ответы были PostgreSQL и ClickHouse.
Post-restart branch metrics: A local/provider `19/953/477` / `735/393/1128`, B через Angular
UI `19/1131/235` / `862/195/1057`. Branching проверен как topology/isolation experiment,
а не как решение forgetting problem.

Desktop acceptance на 1440×1000 прошёл: Sliding/Sticky selectors, N control, facts и отдельные
maintenance metrics, checkpoint/branch controls, switching, Markdown/history rendering и
FULL/SUMMARY_RECENT controls доступны. Console errors не обнаружены; Chrome сообщил только
non-blocking issue о двух form fields без id/name. Raw evidence сохранён в ignored
`docs/local/agent-sessions/day10-acceptance/`.

Наблюдённая инфраструктурная ошибка до acceptance: первый launcher унаследовал Java 17 при
class files Java 21 и завершился без provider call; после запуска с Java 21 backend стартовал.
Provider retries и product-code changes не потребовались.

## TARGETED POST-REVIEW REAL RE-ACCEPTANCE — 2026-09-12

Проверены только исправленные review-семантики. Sticky Facts success/restart и maintenance context-limit
PASS; deterministic injected failures derived-store/main-stage и stale topology race остаются
AUTOMATED_TEST_EVIDENCE_ONLY, как и было разрешено для безопасно невоспроизводимых injection paths.

Первоначальный blocker branch UI не подтвердил product defect. Один новый desktop request
`POST /api/dialogs/{id}/agent/messages` с `FULL` был pending 4.042 s, затем получил HTTP 200 с provider
usage; checkpoint button разблокировался. Предыдущий local harness нажимал checkpoint через 3 s, то есть
до завершения provider call. Это `TEST/ACCEPTANCE_ENVIRONMENT_ISSUE`, не frontend loading-state bug.

На desktop 1440×1000 две ветки от общего checkpoint показали только свои markers PostgreSQL и
ClickHouse при A → B → A; branch requests использовали explicit `branchId` и `FULL`. В linear mode
возврат восстановил `STICKY_FACTS`. После реального restart/reload UI заново загрузил оба checkpoint
и обе ветки; orphan checkpoint был выбран явно и новая ветка получила именно его `checkpointId`.
Persisted linear history не содержала ClickHouse branch continuation.

Дополнительно выполнены 7 provider calls без retry: 1 diagnostic MAIN; 1 unintended local
STICKY_FACTS MAIN + 3 maintenance calls из ошибочной первой версии local harness; 2 branch MAIN calls.
Raw evidence (без credentials) — ignored
`docs/local/agent-sessions/day10-post-review-acceptance/`. Product code during acceptance не менялся.
