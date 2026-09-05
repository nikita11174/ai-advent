# Day 5 — model comparison: implementation analysis

Дата: 2026-09-05. Агент: Codex. Тип: read-only analysis, сохранённый по owner request.
Code checkpoint: `aacbbf32701a5107b6afb81178d637cafb6d00a4` (`day_4`).

Это factual investigation + рекомендации, **не утверждённый implementation plan**.
Day 5 application code не создан. Конкретные модели/providers не выбраны и не одобрены.
Canonical будущий task document `docs/tasks/DAY-05.md` ещё не создан.
Источники: фактический код, project docs, условия и organizer clarifications из owner prompt;
точечная проверка официальных DeepSeek docs. Повторные builds/API calls в этом анализе не выполнялись.
Относительные ссылки ниже считаются от этого документа.

## A. Repository truth

- Репозиторий: `E:\sandbox\sandbox\ai-advent`.
- Branch: `day_4`; HEAD на момент анализа — `aacbbf32701a5107b6afb81178d637cafb6d00a4`,
  `Polish AI Advent Day 4 UX`. Это checkpoint анализа, не durable обещание current HEAD.
- Staged diff пуст. Незакоммиченные изменения только в
  [CURRENT-STATE](../CURRENT-STATE.md), [DAY-04](../tasks/DAY-04.md),
  [agent-runs/DAY-04](DAY-04.md): финализация принятого Day 4 UX. Они сохранены без правок.
- Day 4 implementation/technical verification/owner UX acceptance DONE;
  demo video и repository publication PENDING.
- Предыдущее verification evidence: backend 36 tests PASS, latest frontend 12 tests PASS,
  production builds PASS. Это ранее зафиксированные результаты, не новый запуск.
- IDEA MCP semantic lookup был доступен для этого проекта; symbols подтверждены exact source reads.

Stale/supporting docs, которые следует согласовать перед будущим branching:

1. [SESSION_START](../SESSION_START.md) всё ещё называет Day 4 owner visual acceptance PENDING,
   вопреки owner approval и текущему Day 4 documentation diff.
2. [README](../../README.md) предлагает `4200` как основной адрес. Для этой машины следует
   явно закрепить AI Advent `4201`: `4200` принадлежит PSP и не должен переиспользоваться.
3. Запись CURRENT-STATE «Day 5 не планировался» описывает предыдущий checkpoint; после принятия
   дальнейших решений потребуется указать active Day 5 task. Сейчас это анализ, не implementation.

Рекомендация: отдельно завершить Day 4 documentation checkpoint и устранить stale SESSION_START
до создания следующей ветки. Авторизации на эти Git/doc действия этот отчёт не даёт.

## B. Current architecture relevant to Day 5

### Backend paths and ownership

| Endpoint | Фактический контракт |
|---|---|
| `POST /api/review` | Missing mode → FREE; explicit FREE; CONTROLLED JSON + controls + application validation |
| `POST /api/reasoning-review` | DIRECT/STEP_BY_STEP/EXPERTS по 1 call; SELF_PROMPT 2 calls |
| `POST /api/temperature-review` | Только `0 / 0.7 / 1.2`, отдельный fixed Day 4 system prompt |
| `/api/dialogs` | Create/list/load/update local JSON document |

[DeepSeekClient.java](../../app/src/main/java/dev/aiadvent/mentor/DeepSeekClient.java):

- `API_URI` и `MODEL` — private constants: `https://api.deepseek.com/chat/completions` и
  `deepseek-v4-flash` (строки 19–20 на checkpoint).
- Constructor принимает `HttpClient`, Jackson `ObjectMapper`, key.
  [Spring wiring](../../app/src/main/java/dev/aiadvent/mentor/EngineeringReviewMentorApplication.java)
  читает `System.getenv("DEEPSEEK_API_KEY")`.
- `send` (строка 74) делает synchronous non-streaming `HttpClient.send`; explicit request/connect
  timeouts не заданы. Input/key проверяются до отправки; ошибки transport/status обёрнуты без key.
- Free/temperature builders явно добавляют `thinking.type=disabled`; temperature builder меняет
  соответствующее поле. CONTROLLED builder отдельно добавляет `response_format` и `max_tokens`.
- Transport, prompt construction и Day 2 contract validation совмещены в одном классе.
- `extractCompletion` (строка 156) сохраняет только content и finish reason. Usage, returned model
  и остальная metadata теряются. При missing finish reason код подставляет `stop`.
- Для Day 5 нельзя представлять такую подстановку как измеренный provider fact. Это не повод
  менять legacy Day 1–4 поведение без необходимости; новый metadata path должен сохранять unknown.

[ReviewController](../../app/src/main/java/dev/aiadvent/mentor/ReviewController.java) отвергает controls
в FREE; CONTROLLED использует actual validated controls. Ответы: `{analysis}` либо
`{review, rawResponse}`. Общий advice выдаёт 400 для invalid input, 502 для DeepSeekException.

[ReasoningReviewService](../../app/src/main/java/dev/aiadvent/mentor/ReasoningReviewService.java)
меняет system prompt. SELF_PROMPT использует сгенерированный prompt как system во втором call и
передаёт exact original input как user. EXPERTS — один Markdown response с требованием трёх
разделённых экспертных выводов, не отдельные provider clients и не parsed expert DTO.

[TemperatureReviewController](../../app/src/main/java/dev/aiadvent/mentor/TemperatureReviewController.java)
содержит approved temperatures и fixed prompt, возвращает `{temperature, analysis}`.

### Frontend orchestration

[app.ts](../../frontend/src/app/app.ts):

- `Experiment = FORMAT | REASONING | TEMPERATURE`; отдельные selected values.
- `Exchange` хранит один input, mode, settings snapshots и result maps.
- `compareTemperatures` / `compareStrategies` захватывают input один раз; независимые HTTP
  subscriptions сохраняют successful side при failure другой.
- `ResultState` хранит loading, analysis, error, raw JSON/generated prompt и manual evaluation;
  latency/usage/cost/model metadata отсутствуют.
- Общий `pendingRequests`; `completeRequest` сохраняет dialog после завершения всех calls.
  Во время незавершённого comparison результаты не гарантированно переживут refresh.
- `withoutLoading` знает текущие result maps; при Day 5 нужно явно добавить новую ветку.
- `persistDialog` сохраняет `state.exchanges` и `state.ui`; `activateDialog` восстанавливает их
  с defaults для старых documents. LLM bodies формируются отдельно, history туда не попадает.
- Markdown — marked + escaped raw HTML + обычная Angular sanitization.

[app.html](../../frontend/src/app/app.html) уже содержит independent cards и manual evaluation UI;
[app.scss](../../frontend/src/app/app.scss) — adaptive 3/2/1 Day 4 geometry. Их можно переиспользовать.
Header badge сейчас жёстко говорит DeepSeek V4 Flash: в Day 5 он не должен неверно маркировать
ответы других моделей.

### Persistence and evaluation

[DialogStore](../../app/src/main/java/dev/aiadvent/mentor/DialogStore.java) хранит `state` как
`JsonNode`, без typed exchange schema. UUID filenames, synchronized operations, atomic move с
fallback, сортировка updatedAt. Default directory: ignored `docs/local/mentor-dialogs`,
переопределяемый через `mentor.dialogs.directory`. DB/migration для нового state не нужны.

`REFERENCE_FINDINGS` — frontend constant (app.ts:66); found/missed/questionable — ручные строки.
Checklist не versioned/persisted snapshot benchmark. Reasoning comparison показывает этот
checklist и для arbitrary input; Day 5 не должен автоматически оценивать любой код по нему.

## C. Day 5 requirement mapping

Источник requirement/clarifications: текущий owner prompt; не выбор моделей из старых chat examples.

| Literal requirement | Minimum useful increment |
|---|---|
| Weak / medium / strong | Три утверждённых profiles с объяснённым tier |
| Exact same request through API | Один immutable input, общий system prompt, 1 call на модель |
| Response time | Backend elapsed external HTTP time |
| Token usage | Provider-reported usage, не оценка по символам |
| Cost when paid | Проверенный тарифный расчёт или provider-reported charge |
| Quality/speed/resources comparison | Reference-based human evaluation + реальные metrics |
| Short conclusion + model links + code | Сохранённое evidence и краткий submission report |

Organizer clarifications, переданные владельцем:

- HuggingFace ranking position не важен; важно сравнить weak/medium/strong.
- Strength multi-factor, не один строгий score; tiers не гарантируют победителя на конкретной задаче.
- Existing logical/analytical tasks разрешены; in-app model switcher допустим.
- Нужны API calls, не ручное открытие model chats.
- Для Day 5 generated report может заменить video. Это не разрешение менять submission rules
  исторических Day 1–4 и не требование строить report generator в продукте.

## D. Implementation options with trade-offs

| Option | Benefits | Costs/risks |
|---|---|---|
| A. Один OpenAI-compatible provider с тремя tiers | Один protocol path; меньше auth/usage/pricing differences | Нужна подходящая тройка; gateway routing может скрывать различия serving |
| B. DeepSeek + один additional provider | Сохраняет знакомый baseline; больше выбора tiers | Credentials и различия sampling/billing/mapping |
| C. Несколько provider-specific clients | Свободный выбор моделей | Больше implementation/verification; сильнее смешиваются model и infrastructure effects |

**RECOMMENDED, не owner-approved:** A, если подходящая тройка реально существует и доступна;
иначе B. C только при необходимости, заданной окончательным выбором моделей.

Текущий client не универсален: DeepSeek thinking/key/errors, endpoint/model constants и Day 2
semantics. Одна замена `MODEL` недостаточна. Не строить generic provider framework, plugins или
универсальную capabilities system. При совместимом протоколе достаточно небольшого общего
transport/mapping seam; при несовместимом — одного дополнительного concrete client.

## E. Recommended minimal design

Все решения раздела — **RECOMMENDED**, не OWNER-APPROVED.

Отдельный experiment «Модели»: selected model / compare three, fixed FREE Russian Markdown.
Не показывать Day 2 controls, Day 3 strategies и Day 4 temperature внутри Day 5; не создавать matrix.

### Responsibility split

- Request DTO: exact input + allowlisted `modelKey`.
- Backend catalog: provider/model IDs, tier, model links, endpoint, env-key reference, checked
  request configuration и pricing snapshot. Key values остаются только в environment.
- Provider client: точный wire request, transport, response metadata extraction.
- Day 5 service: общий prompt/configuration, route profile, metrics/result и cost calculation.
- Frontend: orchestration/presentation; не задаёт произвольные endpoints, credentials и тарифы.

### Conceptual API sketch (не production code, не реальные измерения)

```text
GET /api/model-options
  -> три profiles: key, tier, label, provider, modelId, modelUrl

POST /api/model-review
  { input: "<exact input>", modelKey: "<approved backend key>" }

Response:
  model: { key, tier, provider, requestedId, returnedId?, modelUrl }
  analysis: string
  finishReason: string | null
  startedAt: UTC timestamp
  apiLatencyMs: number
  usage:
    inputTokens: number | null
    outputTokens: number | null
    totalTokens: number | null
    cachedInputTokens?: number
    reasoningTokens?: number
  cost:
    status: ESTIMATED | PROVIDER_REPORTED | FREE | UNKNOWN
    amount: decimal-string | null
    currency: string | null
    pricingSnapshotId?: string
    reason?: string
  configuration:
    presetVersion
    appliedParameters
    documentedDifferences
```

Missing usage — null/N/A, не 0. Error сохраняет безопасное описание и доступную metadata;
partial failure не стирает successful cards. Model not allowlisted → 400 без внешнего вызова.

```text
Immutable input snapshot
  -> Angular: три independent requests
  -> Day 5 service -> backend model catalog
  -> concrete HTTP transport -> выбранный API
  -> text + latency + usage + cost provenance
  -> comparison cards -> existing local dialog JSON
```

UI: compact selector трёх profiles, «Проанализировать», «Сравнить модели», карточки с model links,
metrics и human evaluation. Одно user message на comparison.

Persisted exchange дополнить ordered model-profile snapshots, result/error/metrics, applied config,
pricing snapshot, benchmark/reference version или snapshot, manual observations/conclusion.
Старые карточки не должны переименовываться и пересчитываться по обновлённому catalog/pricing.
Snapshot цен должен содержать достаточно данных для воспроизведения расчёта, не только текущий ID.
`state.ui` получает selected model key; старые dialogs сохраняют defaults.
История **не становится model conversation memory**.

## F. Benchmark recommendation

| Option | Discriminating power | Cost/demo |
|---|---|---|
| Reuse PaymentReceived | Проверяет системные риски; возможен ceiling effect и ничья | Максимальный reuse, короткое demo |
| Harder synthetic Java handler | Больше различий в causal analysis/remediation | Новый явно заданный contract/checklist, больше подготовки и объяснения |

**RECOMMENDED:** PaymentReceived для минимального Day 5. Если все модели дают сходное качество,
это честный результат; различия speed/cost всё равно полезны. Не усложнять задачу ради победителя.

Existing snippet и canonical reference: [DAY-03, benchmark](../tasks/DAY-03.md):

```java
@Transactional
public void handle(PaymentReceived event) {
    Order order = orders.findById(event.orderId()).orElseThrow();
    order.markPaid();
    email.sendReceipt(order.customerEmail());
}
```

Reference areas: duplicate delivery/idempotency; email vs DB atomic boundary; concurrency/race;
failure/retry around external effects. **Ограничение:** snippet не раскрывает `markPaid()`,
delivery guarantees и точные rollback rules. «Любая ошибка email откатывает transaction» не
доказано без условий. Для Day 5 reference фиксировать как defensible risk scenarios с явными
предположениями; не переписывать исторический Day 3 evidence.

Если owner выбирает более сильный discriminator: небольшой synthetic handler с явно заданными
duplicate/concurrent delivery, check-then-act dedup, processed-marker до завершения операции и
external effect вне атомарной DB-границы. Точный snippet/условия/checklist фиксировать **до** runs.
Никаких proprietary PSP/LK знаний. Reference не отправлять модели как готовый ответ.

Human evaluation: каждому reference item сопоставить найден/пропущен, цитату или causal explanation;
отдельно questionable extras. Корректное дополнительное замечание не ошибочно только потому, что
его нет в checklist. Допускается ничья. Ни длина, ни число findings, ни уверенная речь не accuracy.
Вывод — explainable trade-off quality/speed/cost на этом benchmark, не universal model score.

## G. Metrics / cost / resource model

### Observed data and measurement boundaries

| Metric | Correct location/meaning |
|---|---|
| API latency | Monotonic elapsed timer непосредственно вокруг `HttpClient.send` |
| UTC timestamp | Перед отправкой, отдельно от monotonic duration |
| Input/output/total tokens | Parser provider response; missing не заменять нулями |
| Requested provider/model | Backend profile snapshot |
| Returned model | Provider response отдельно от requested identity; aliases могут различаться |
| Finish reason | Реальное response field или unknown |
| Cost | Backend calculation после usage extraction; provenance обязателен |

Latency включает сеть, очередь provider, генерацию и получение body; не чистое inference time,
не TTFT и не браузерный render time. Failed attempt duration не выдавать за успешную генерацию.
Не делать скрытые retries/fallback models: они меняют расходы, latency и смысл comparison.

### Official source check, 2026-09-05

- [DeepSeek Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/):
  response содержит model, finish_reason и usage prompt/completion/total tokens; описаны
  prompt cache hit/miss и reasoning token details. Эти поля текущий client не сохраняет.
- [DeepSeek pricing](https://api-docs.deepseek.com/quick_start/pricing/): тариф различает
  input cache hit/miss, output и peak/off-peak. Простая одна ставка на весь input может быть неверна.

Это точечная проверка текущего DeepSeek, **не выбор Day 5 моделей**. Перед implementation/runs
нужна fresh official verification всех выбранных providers/models и применимых тарифов.
Числовые цены не закреплены этим анализом как approved runtime configuration.

### Pricing snapshot and calculation

Маленький versioned snapshot: provider/model, source URL, checkedAt/effective period, currency,
rates per unit, cache categories и time/account conditions при наличии.

```text
estimated cost = sum(billable tokens каждой категории × ставка категории)
```

Backend decimal arithmetic; округлять только для display. Не считать cached tokens дважды с
полным input; не прибавлять reasoning tokens к output без verified provider billing semantics.
Для time-dependent tariffs сохранить применённую категорию и основание. Если её определить
невозможно — UNKNOWN или явно обозначенный обоснованный диапазон, не псевдоточная сумма.

ESTIMATED — расчёт по тарифу, не доказанное списание. PROVIDER_REPORTED — только при наличии
авторитетного charge field/источника. Free credits не делают платный тариф бесплатным.
FREE — только подтверждённые условия; unknown != zero. На transport error charge тоже может
быть неизвестен. Старые сохранённые результаты не пересчитывать по новым ценам.

### Honest remote resource usage

Наблюдаемое: tokens, число API calls, elapsed time, доступный cache/reasoning breakdown;
стоимость — reported или labelled estimate. Provider GPU/RAM/energy usage не измеряются.
Model parameter count, architecture/context size — отдельная sourced metadata, не telemetry и
не достаточное основание силы модели. Tokenizers различаются: равный текст не означает равные tokens.

### Fairness and provider-dependent unknowns

Общие invariants: exact input/system prompt, русский Markdown, отсутствие tools/history/Day 3
strategies, число calls; единый output ceiling и non-thinking — где действительно поддерживаются.

**Зависит от окончательной тройки и требует fresh official docs:** system role support,
thinking controls, sampling/defaults, название/semantics output-token limit, tokenizer/usage,
model aliases/revisions, gateway routing, quotas/rate limits, cache и pricing.
Равные numeric temperature/max tokens не доказывают semantic equivalence. Неподдержанные или
опущенные controls записывать как differences, не как якобы одинаково применённые параметры.

## H. Expected files/components to change

Предварительный scope, не patch authorization:

- Новый Day 5 controller/service, DTO и небольшой backend model catalog/pricing calculation.
- HTTP response metadata seam; точный объём выделения transport из DeepSeekClient зависит от A/B/C.
  Day 1–4 external DTO и prompt/configuration contracts сохранить.
- [Spring wiring](../../app/src/main/java/dev/aiadvent/mentor/EngineeringReviewMentorApplication.java).
- Frontend `app.ts`, `app.html`, `app.spec.ts`; минимально `app.scss`, включая правдивый model badge.
- Backend routing/metadata/cost tests; dialog round-trip fixture с Day 5 state.
- `scripts/verify-day5.ps1`; после решения owner — DAY-05 task, current/session docs, README/run evidence.
- Launcher и `.env.example` только если нужен новый env credential; никогда key value в tracked data.

DialogStore обычно не менять: generic JsonNode state уже достаточен. DB migrations, SDK,
dependencies и generic provider framework заранее не нужны.

## I. Verification plan

### Existing seams

- [MainTest](../../app/src/test/java/dev/aiadvent/mentor/MainTest.java): pure JSON builders/parsing,
  Day 1 request baseline, Day 2 validation, Day 4 invariant checks.
- Controller tests: MockMvc + mocked client; service tests: Mockito call topology/input capture.
- [app.spec.ts](../../frontend/src/app/app.spec.ts): HttpTestingController, comparison snapshots,
  independent errors, rendering/sanitization, dialog restore.
- [DialogStoreTest](../../app/src/test/java/dev/aiadvent/mentor/DialogStoreTest.java): TempDir,
  fixed Clock, re-instantiation persistence, ordering, invalid IDs.
- [verify-day3.ps1](../../scripts/verify-day3.ps1), [verify-day4.ps1](../../scripts/verify-day4.ps1):
  real local-backend harnesses, ignored evidence. Сейчас metrics не собирают и итог записывают
  только после всего цикла: промежуточный failure может оставить уже полученные ответы несохранёнными.

### Deterministic verification to add

1. Три keys → правильные allowlisted endpoint/model; invalid key → 400 без external call.
2. Exact input/system/config invariant на wire, без history/reference answers.
3. Provider fixtures: реальные usage/model/finish поля; missing/malformed metadata не превращается
   в выдуманные значения. Нужен transport-level fake/fixture: builder/mock-service tests недостаточны.
4. Latency boundary around send; controllable timer для deterministic assertion; finite timeout
   завершает зависшую сторону. Не делать timing assertions через хрупкие sleeps.
5. Decimal cost: categories/cache/unit conversion, missing/free, pricing snapshot и rounding.
6. Frontend: одна user bubble; три independent cards; model snapshot immutability; partial failure;
   metrics N/A; Markdown safety; old history не меняется с catalog.
7. Save/load/restart Day 5 state с metrics/evaluations; backwards-compatible Day 1–4 dialogs.
8. Backend suite + Maven package, frontend suite + production build; Day 1–4 regression safety.
9. Secret/local/generated artifacts safety; не логировать Authorization/env values.

### Real verification proposal — not yet authorized execution

- Literal minimum: 3 successful calls, один на модель, один exact input.
- RECOMMENDED для сравнения speed: 3 rounds × 3 models, sequential с ротацией порядка;
  сохранить все durations и variability, не делать статистических универсальных выводов из n=3.
- Сохранять каждый result сразу с timestamp/config/model/usage/pricing; не терять evidence при FAIL.
- Один browser comparison + refresh smoke; deterministic partial-failure test вместо расходных
  искусственных provider errors. Учесть стоимость дополнительных smoke calls в owner budget.
- При parallel UI comparison зафиксировать concurrency: backend latency всё равно включает
  provider scheduling. Harness sequential runs не выдавать за идентичные условия parallel UI.
- Quality classification manual/reference-based, reference скрыт от модели. Завершить коротким
  report с model links, actual metrics и ограничениями; без fake winner/LLM judge.

## J. Open owner decisions / blockers

Все перечисленное ниже **OPEN**, рекомендации не утверждены владельцем.

| Decision | Recommendation / trade-off |
|---|---|
| Три model IDs/providers и объяснение tiers | A: один compatible provider при подходящей тройке; иначе B. Цена/название сами по себе tier не доказывают |
| Credentials source/access и расход | Backend env only; согласовать budget до платных calls, включая smokes |
| Benchmark | PaymentReceived для minimal increment; synthetic только при нужде в более сильном discriminator |
| Общий inference preset | Fixed FREE Markdown, non-thinking где поддерживается; exact limits/sampling после capability check |
| Объём experiment | 9 calls recommended; 3 literal minimum, меньше evidence о вариативности |
| Submission presentation | Краткий evidence-based report + model links + code; in-app report generator не обязателен |

Главный implementation blocker — окончательная тройка: от неё зависят transport, fairness, usage,
pricing и доступ. Model IDs из старых Telegram примеров не считаются current truth/approval.
Protocol details, которые однозначно следуют из выбранных models/docs, не нужно превращать в
дополнительные owner questions. Но если общая non-thinking/configuration equivalence недостижима,
различие experiment scope следует явно согласовать.

Следующий разрешённый шаг определяется новым owner instruction. Этот отчёт не создаёт branch,
task implementation, Git authorization или разрешение расходовать API budget.
