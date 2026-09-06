# Day 5 — implementation / real experiment evidence

## Контекст запуска 2026-09-05

Agent: Codex. Ветка `day_5`; planning checkpoint
`f91cf5f03af492a72a482e1ff2a84a2ac20c5509`, finalized Day 4 `9ff5b296b692266cde73701bc250c3aed7b5351d`.
Implementation находится в рабочем дереве, implementation commit пока НЕ создан.
Непредвиденных изменений preflight не обнаружил. Продуктовый код в этом экспериментальном запуске
не менялся; tests/builds не повторялись. No push/merge.

Реализованы concrete `OpenAiResponsesClient`, allowlisted `ModelProfile` с BigDecimal pricing,
`ModelReviewController`, Angular `Модели`/`ModelResult` и local dialog snapshots. Day 1–4 clients
и endpoint contracts не изменены. Launcher загружает OpenAI из ignored `.env.local` в backend env.
Предыдущая deterministic проверка этой реализации: backend **46 tests, 0 failures/errors/skipped**,
`mvn clean package` PASS; frontend **15 tests PASS**, production build PASS.
Сохраняется известный stylesheet warning; budget в этом запуске не менялся.

## Preflight и протокол

- Backend Java 21 на `18080`, frontend на `4201`; PSP `4200` не затрагивался.
- Ключ присутствует в конфигурации launcher; предыдущий реальный Luna smoke подтвердил,
  что backend получил его. Новые ответы всех трёх моделей подтверждают доступ account.
- До эксперимента Luna smoke: 139 tokens, estimated USD **0.0000948**; НЕ часть девяти вызовов.
- Exact benchmark `payment-received-v1` извлечён из frontend `BENCHMARK`, newline LF,
  без trailing newline; соответствует тексту в DAY-05.md. SHA-256 UTF-8:
  `be7c7a0e95eddba87d17049a695d20a753649c9a607b8afc37a04c5c4bb9a5ad`.
- Один input во всех попытках. Один preset `day5-model-review-v1`: developer instruction,
  reasoning none, output ceiling 2000, service_tier default, stream/store false;
  без tools/history/sampling overrides. Reference checklist не передаётся моделям.
- Последовательно: **Luna → Terra → Sol / Terra → Sol → Luna / Sol → Luna → Terra**.
  Никаких retries, fallback, увеличения лимита или дополнительных judge calls.
- Каждая попытка немедленно сохранена через temporary JSON + atomic replacement до следующей.
  Все ответы backend JSON — безопасные DTO, не raw provider headers/error bodies.
- Верхняя резервация USD 0.10 на короткий input и 2000 output каждой модели покрывает
  этот запуск с большим запасом до owner USD 5. Реальный расчёт ниже, не prepaid=free.

Raw evidence (ignored, полный input, response, preset/pricing и metadata каждой попытки):
`docs/local/agent-sessions/day5-model-comparison-20260905-170439.json`.
Локальный harness: `docs/local/agent-sessions/run-day5-experiment.ps1`.
**Не запускать harness повторно без новой авторизации: каждый запуск делает девять платных calls.**

## Фактические результаты

Итог **PARTIAL: 7 completed, 2 incomplete**. Все 9 provider requests вернули HTTP 200 и текст;
локальный backend вернул 502 для двух `incomplete/max_output_tokens` (Terra, #2/#9).
Они оплачиваемые, сохранены полностью в доступном объёме и не считаются успешными завершёнными ответами.
Никакого blocking implementation defect не выявлено: output ceiling работает по контракту.

| № | Round / position | Модель | Local HTTP / status | API ms | Input | Output | Total | Estimated USD |
|---|---|---|---|---:|---:|---:|---:|---:|
| 1 | 1 / 1 | Luna | 200 completed | 12807 | 128 | 1036 | 1164 | 0.0012688 |
| 2 | 1 / 2 | Terra | 502 incomplete | 19793 | 128 | 2000 | 2128 | 0.024256 |
| 3 | 1 / 3 | Sol | 200 completed | 12187 | 128 | 1101 | 1229 | 0.022532 |
| 4 | 2 / 1 | Terra | 200 completed | 17319 | 128 | 1660 | 1788 | 0.020176 |
| 5 | 2 / 2 | Sol | 200 completed | 18444 | 128 | 1318 | 1446 | 0.026872 |
| 6 | 2 / 3 | Luna | 200 completed | 16518 | 128 | 1264 | 1392 | 0.0015424 |
| 7 | 3 / 1 | Sol | 200 completed | 14282 | 128 | 1159 | 1287 | 0.023692 |
| 8 | 3 / 2 | Luna | 200 completed | 15396 | 128 | 1152 | 1280 | 0.001408 |
| 9 | 3 / 3 | Terra | 502 incomplete | 21259 | 128 | 2000 | 2128 | 0.024256 |

В каждом ответе requested=returned exact model ID; service tier `default`; cached input,
cache-write и reasoning tokens **реально возвращены как 0**, не заполнены предположениями.
`startedAt`, `apiLatencyMs`, `status`, `incompleteReason`, full pricing snapshot находятся в raw DTO.
Единый pricing id: `openai-gpt56-standard-2026-09-05`. Проверено Decimal суммирование.

| Модель | Completed | Median API ms (все 3 попытки) | Range ms | Total tokens | Total estimated USD |
|---|---|---:|---|---:|---:|
| Luna | 3/3 | 15396 | 12807–16518 | 3836 | 0.0042192 |
| Terra | 1/3 | 19793 | 17319–21259 | 6044 | 0.068688 |
| Sol | 3/3 | 14282 | 12187–18444 | 3962 | 0.073096 |

Итого: input **1152**, output **12690**, total **13842**; estimated USD **0.1460032**.
С предыдущим Luna smoke: estimated USD **0.1460980**. Это snapshot-based estimate,
не сверка provider billing ledger/фактического остатка prepaid account.
Latency — backend elapsed до полного API body, не TTFT/GPU inference time. n=3 — описательные
наблюдения, недостаточно для универсальных выводов о скорости и качестве моделей.

## Подготовка quality comparison — ANALYSIS EVIDENCE, не OWNER-APPROVED conclusion

Это явная текстовая классификация Codex по прочитанным ответам, не отдельный LLM judge,
не автоматический keyword matcher и не числовой score. Владелец проверяет и утверждает итог.
Для incomplete оценивается только сохранившаяся часть, а не предполагаемое продолжение.

Фиксированные области reference из canonical task:

- **R1**: повторная доставка / отсутствие показанной deduplication / повторный email.
- **R2**: email может уйти до последующего rollback; email и DB не атомарны.
- **R3**: конкурентная обработка и повторный side effect при отсутствии показанных гарантий.
- **R4**: ошибка email при соответствующей rollback policy откатывает DB и допускает reprocessing.

Это условные риски: snippet не доказывает отсутствие гарантий в скрытых методах/инфраструктуре.
Слова «найдено» относятся к объяснённой области риска, не доказательству production bug.

### #1 / Round 1 Luna

- **Ясно найдено:** R1 (повторный receipt, неизвестная идемпотентность `markPaid`), R4
  (явное «если исключение приведёт к rollback» и повторная доставка).
- **Не раскрыто ясно:** R2 упомянута как отсутствие атомарности, но конкретный сценарий
  successful email → failed DB commit не разобран. R3 — лишь вопрос о блокировках в конце,
  без сценария параллельных обработчиков. Не засчитывать это как полное раскрытие.
- **Спорное:** commit → последующее падение email введено с оговоркой о нетипичной семантике,
  но это не обычная последовательность показанного синхронного метода. Заголовок «подтверждённые»
  слишком сильный для проверок суммы/валюты. Пример `isPaid()` сам по себе не закрывает race.
- **Полезное сверх эталона:** unknown-order retry/DLQ policy, transaction resource holding,
  outbox worker idempotency и observability; payment checks только как условные вопросы.

### #2 / Round 1 Terra — INCOMPLETE

- **Ясно найдено:** R1–R4 в доступной части: email до rollback, unique event ID, параллельное
  чтение одного состояния, runtime exception и rollback. Проверка `isPaid()` названа недостаточной.
- **Пропущено по reference:** явных пропусков четырёх областей нет. Финальная рекомендация
  оборвана на «DLQ или»; это не полноценный завершённый результат.
- **Спорное:** DB commit → ошибка синхронного email в первом разделе не отделено от assumptions.
  Конкретные lost effects зависят от sync/async и rollback policy.
- **Полезное сверх эталона:** DB connection/lock holding, unknown-order classification,
  managed-vs-detached distinction, outbox unique constraint и idempotent worker.

### #3 / Round 1 Sol

- **Ясно найдено:** R1–R4: repeat delivery, email до rollback, два обработчика, unchecked exception
  и повторный вызов с неопределённым исходом email timeout.
- **Пропущено по reference:** явных пропусков нет.
- **Спорное:** утверждение DB зафиксировалась → email упал требует отдельной семантики и не
  доказано обычным синхронным flow. Пример `exists`/`save` без constraints не самостоятельный fix,
  но необходимые unique index и concurrency protection явно перечислены после него.
- **Полезное сверх эталона:** afterCommit не гарантирует доставку при crash; outbox фиксирует
  намерение атомарно; доменное исключение и retry/DLQ policy.

### #4 / Round 2 Terra

- **Ясно найдено:** R1–R4: receipt до failed commit, repeat delivery, concurrent old-state reads,
  email error/rollback/retry с дальнейшим уточнением checked/unchecked policy.
- **Пропущено по reference:** явных пропусков нет; единственный completed Terra run.
- **Спорное:** «email ... идемпотентным не является» слишком категорично: повтор вызова не
  доказывает повтор доставки при неизвестной внутренней deduplication. Detached/save discussion
  не является подтверждённым дефектом; в тексте есть корректная оговорка о repository contract.
- **Полезное сверх эталона:** afterCommit crash gap, unique notification key, неизвестный заказ,
  поддержка idempotency key провайдером; sample code требует дополнений, названных в prose.

### #5 / Round 2 Sol

- **Ясно найдено:** R1–R4: email до rollback, uncertainty после timeout, concurrent handling,
  `if (!isPaid())` недостаточно, rollback policy отдельно отмечена неизвестной.
- **Пропущено по reference:** явных пропусков нет.
- **Спорное:** фраза «атомарно фиксирует результат» worker-а не должна читаться как атомарность
  email+DB; outbox не создаёт такую гарантию. Некоторые ранние фразы о rollback категоричнее
  последующего раздела с корректными оговорками.
- **Полезное сверх эталона:** crash gap после commit, pool exhaustion от I/O внутри транзакции,
  unique registration, timeout/ограниченные retries и диагностический контекст.

### #6 / Round 2 Luna

- **Ясно найдено:** R1–R4: email до failed commit, повторный receipt, две транзакции и возможные
  два email даже при защите DB, exception policy и повторная обработка.
- **Пропущено по reference:** явных пропусков нет.
- **Спорное:** раннее «email недоступен — транзакция откатывается» требует rollback policy,
  что уточнено позднее. `null`/payment validation — вопросы, не доказанные дефекты.
- **Полезное сверх эталона:** unknown-order retry policy, connection/lock duration,
  unique outbox key, границы exactly-once внешнего email.

### #7 / Round 3 Sol

- **Ясно найдено:** R1–R4: email до failed commit, duplicate delivery/ack failure, два обработчика,
  unchecked exception rollback; checked exception policy явно отличена.
- **Пропущено по reference:** явных пропусков нет.
- **Спорное:** под заголовком «подтверждённые» проверка допустимости `markPaid` остаётся
  неизвестной, и это признаётся текстом. Outbox worker «не отправляет повторно» — требуемое
  свойство, не гарантия одной лишь схемы. API `boolean markPaid()` в примере гипотетический.
- **Полезное сверх эталона:** потерянный acknowledgement, unknown-order parking/retry policy,
  afterCommit gap, стабильный eventId и идемпотентная регистрация.

### #8 / Round 3 Luna

- **Ясно найдено:** R1, R2, R3: duplicate receipt, successful email → failed commit, параллельные
  обработчики с неизвестными locking guarantees.
- **Не раскрыто ясно:** R4 затронута через retry/rollback, но фраза «состояние ... зафиксировано,
  ... откат всей транзакции» смешивает commit и rollback и не даёт чистого корректного сценария.
- **Спорное:** уже committed DB transaction нельзя откатить последующей ошибкой; возможно,
  автор подразумевал ещё не committed change, но молча исправлять ответ нельзя. Payment checks
  условны. `isPaid()` в sample требует DB concurrency guard, упомянутого только отдельно.
- **Полезное сверх эталона:** afterCommit crash gap, outbox, bounded retry/DLQ, traces/metrics.

### #9 / Round 3 Terra — INCOMPLETE

- **Ясно найдено:** R1–R4 в доступной части; особенно явен SMTP accepted → timeout → rollback
  → retry duplicate. Есть concurrency/unique-constraint discussion.
- **Пропущено по reference:** явных пропусков областей нет; финальный outbox-worker section
  оборван на открывающем code fence. Продолжение нельзя считать существующим.
- **Спорное:** таблица допускает «БД успешно закоммитилась ... транзакция может быть откатана» —
  некорректное смешение событий. Lost update/audit corruption не доказаны snippet и зависят от
  hidden implementation. Payment checks корректно условны, не отдельные reference hits.
- **Полезное сверх эталона:** poison-message/retry classification, timeout unknown outcome,
  conditional transition, resource holding и явные вопросы к transaction manager.

## Кандидат сравнения (для owner review)

Luna во всех трёх попытках завершилась и значительно дешевле; качество раскрытия отдельных
reference областей нестабильно (#1 R2/R3, #8 R4). Terra подробно покрывает области, но дважды
не помещается в общий ceiling; verbosity не равна качеству. Sol завершает все три и устойчиво
раскрывает четыре области, но тоже содержит спорные формулировки и дороже Luna.
На этом коротком benchmark Sol — кандидат на более устойчивое покрытие, Luna — на экономичный
первичный review с обязательной проверкой. Это НЕ owner-approved winner и НЕ универсальный ranking.
Разница median Sol/Luna мала для n=3 и не доказывает превосходство скорости в общем случае.
Требование canonical acceptance «три completed на модель» НЕ выполнено для Terra;
не менять потолок и не добирать результаты без отдельного решения владельца.

## Chrome UI smoke — PASS с повторным показом реальных сохранённых ответов

После API experiment выполнен click-path `Новый диалог → Модели → Вставить PaymentReceived →
Сравнить модели`. Временный browser fetch replay вернул exact DTO первого раунда; model-review XHR
защищён от случайного сетевого вызова. Replay записал **ровно три одинаковых input** и разные
allowlisted keys. **Дополнительных платных calls: 0**. Это UI replay, не второй live experiment.

- Один user bubble, три cards; official model links, requested/returned identity, API latency,
  tokens, decimal cost, snapshot, status/incomplete reason и Markdown видны.
- Real incomplete Terra показывает inline error + partial Markdown/metrics; Luna/Sol не теряются.
- Через UI введены candidate reference classifications и conclusion; штатный PUT `/api/dialogs`
  сохранил их. Refresh удалил временный replay и восстановил `MODELS`, один exchange, три cards,
  exact metrics, оценки, conclusion и Terra error. Model-review requests при refresh: **0**.
- 1440×1000: три readable columns, composer и jump разделены; 500×844: sidebar hidden,
  одна comparison column, composer в viewport, горизонтального document overflow нет.
  Оба screenshot просмотрены через Chrome MCP; явного overlap/clipping shell не обнаружено.
- Chrome MCP отказал в записи screenshot в project-local path (tool workspace-root restriction),
  поэтому скриншоты просмотрены inline; сохранённых screenshot-файлов не заявляем.
- Backend/frontend оставлены работающими на 18080/4201; backend restart в этом запуске не делался.
  Это refresh/file persistence evidence, не новый restart smoke.

## Safety / remaining

`.env.local`, raw experiment, harness и dialog JSON ignored; keys/headers не выводились и не
записывались в evidence. Dialog state остаётся только UI history, не input history модели.
Новых product changes/fix commits в этом запуске нет. Не повторяли tests/builds при неизменном коде.
Остаются owner review кандидатного сравнения и принятие partial experiment/следующего шага для Terra,
implementation checkpoint commit отдельной незавершённой задачи, submission report/code publication.
