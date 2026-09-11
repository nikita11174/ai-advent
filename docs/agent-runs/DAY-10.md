# Day 10 — implementation run

Дата: 2026-09-11. Ветка: `day_10`, base `314c57e`.

Реализованы три switchable approaches без использования summary в Day 10 benchmark:

- Sliding Window строит `system + latest N committed raw messages + pending user`.
- Sticky Facts хранит отдельный derived key/value state, обновляемый LLM extraction call; raw
  history остаётся canonical, extraction/recovery metrics отделены от main `TokenMetrics`.
- Branching хранит immutable checkpoint и любое число независимых branch histories в отдельной
  topology store; branch ID используется явно, sibling continuations изолированы.

Проверено автоматически:

- `mvn -q test` — 87 backend tests PASS.
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
