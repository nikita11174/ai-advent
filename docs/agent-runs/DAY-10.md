# Day 10 — implementation run

Дата: 2026-09-11. Ветка: `day_10`, base `314c57e`.

Реализованы три switchable approaches без использования summary в Day 10 benchmark:

- Sliding Window строит `system + latest N committed raw messages + pending user`.
- Sticky Facts хранит отдельный derived key/value state, обновляемый LLM extraction call; raw
  history остаётся canonical, extraction/recovery metrics отделены от main `TokenMetrics`.
- Branching хранит immutable checkpoint и любое число независимых branch histories в отдельной
  topology store; branch ID используется явно, sibling continuations изолированы.

Проверено автоматически:

- Independent Week 2 review (`mvn test`, Java 21) — 89/89 backend tests PASS.
- `npm test -- --watch=false --no-progress` — 20/20 frontend tests PASS.
- `npm run build` — PASS; сохранены существующие budget warnings для initial bundle и `app.scss`.

Добавлены tests для exact-N, отсутствия summary в Sliding, facts update/overwrite/recovery,
maintenance metrics, provider/storage failures, persistence/restart, checkpoint/branch creation,
isolation, switching и branch-aware concurrency. Изменённые старые dialogs/history JSON покрыты
существующим suite.

## Controlled real acceptance

Дата: 2026-09-11. Ожидаемый и фактический результат — ровно 9 успешных provider calls, без
retries: Sliding 1;
Sticky 2 recovery + 1 current update + 1 main; Branching 2 decisions + 2 post-restart queries.

Одинаковый linear fixture имел 5 canonical messages до probes и одинаковый SHA-256 history.
Sliding с `N=2` реально отправил system, U2, A2 и pending probe; U1/A1 отсутствовали, summary и
facts отсутствовали. Все четыре ранних facts получили классификацию MISSED. Main local metrics:
`67/365/266`; provider: `253/160/413`.

Sticky recovery/update вызовы покрыли U1, U2 и текущий probe. Maintenance local metrics:
`36/119/38`, `167/276/30`, `67/176/30`; provider totals: `150`, `237`, `170`; суммарный
maintenance overhead `270/571/98` local и `438/119/557` provider; total provider work Sticky
составил `896`. Main outbound содержал
system, persisted facts, U2, A2 и pending; main local metrics `67/398/47`, provider
`292/47/339`. Все четыре факта были RETAINED, `coveredUserMessageCount=3`, facts persisted
отдельно.

Branching acceptance создал immutable checkpoint и две ветки с общим checkpoint ID. A выбрала
PostgreSQL, B ClickHouse; sibling user decisions отсутствовали в чужих contexts, linear history
не получила divergent continuations. После подтверждённой остановки порта 18080 и старта нового
backend process checkpoint/branch IDs/histories восстановились. Post-restart queries вернули
PostgreSQL и ClickHouse. A metrics local/provider `19/953/477` / `735/393/1128`; B query
через Angular local/provider `19/1131/235` / `862/195/1057`.

Desktop-only UI acceptance на 1440×1000 PASS: strategy selectors, N, facts, maintenance/main
metrics, checkpoint/branch controls, switching, Markdown/history и старые FULL/SUMMARY_RECENT
controls. Console errors отсутствовали; остался non-blocking Chrome issue о двух form fields без
id/name.
Evidence: ignored `docs/local/agent-sessions/day10-acceptance/`. Первый Java 17 launcher failure был до
provider call; повторный старт с Java 21 прошёл. Product code не изменялся во время acceptance.

## Post-independent-review corrections — 2026-09-12

Day 10 уже committed в `f2db8634453182de927f21412fd069b8e33b8297`; прежняя отметка `uncommitted`
и число 87 backend tests устарели. Исправления по independent review держатся отдельными
unstaged changes до targeted re-acceptance. Day 9 FULL/SUMMARY real benchmark намеренно не
повторялся: historical metrics сохранены, однако raw provider artifact для него недоступен.

Проверки post-review fixes (без real provider/browser):

- `JAVA_HOME=C:\Program Files\Java\jdk-21; mvn test` — 96/96 backend tests PASS.
- Node `22.22.3`: `ng test --watch=false` (эквивалент `npm test -- --watch=false`) — 22/22 PASS.
- Node `22.22.3`: `npm run build` — PASS; только существующие budget warnings для initial bundle и `app.scss`.

## Targeted post-review real re-acceptance — 2026-09-12

Реальный desktop run на 1440×1000 закрыт PASS без product-code changes. Первое наблюдение о якобы
зависшем branch UI классифицировано как `TEST/ACCEPTANCE_ENVIRONMENT_ISSUE`: точный UI
`POST /api/dialogs/{id}/agent/messages` оставался pending 4.042 s, затем вернул HTTP 200 с provider
usage и разблокировал checkpoint action; старый local harness пытался нажать checkpoint через 3 s.

Две sibling branches от общего checkpoint получили PostgreSQL и ClickHouse соответственно. UI
показал изолированную visible history при A → B → A, branch requests передавали `branchId` и `FULL`,
а linear mode восстановил ранее выбранный `STICKY_FACTS`. После реального backend restart/reload UI
загрузил оба persisted checkpoint и обе ветки. Explicit selection orphan checkpoint создал branch с
этим, а не inferred, `checkpointId`. Persisted linear history не содержала ClickHouse continuation.

Дополнительный provider accounting: 7 calls, без retry — diagnostic MAIN (1), ошибочная первая
версия local CDP harness: STICKY_FACTS maintenance (3) + MAIN (1), branch MAIN (2). Это local
acceptance harness error, а не product regression. Browser console содержала только Vite/Angular
development messages; application errors и UI alerts не наблюдались. Raw evidence — ignored
`docs/local/agent-sessions/day10-post-review-acceptance/`.
