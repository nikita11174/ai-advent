# Day 5 — Model comparison

Статус: **IMPLEMENTED / REAL EXPERIMENT PARTIAL**.
Implementation: **DONE в рабочем дереве, commit pending**. Automated verification: **PASS**.
Real experiment: **7 completed / 9 attempts**; browser replay/refresh: **PASS**. Submission: **PENDING**.
Дата проверки документации: **2026-09-05**. Ветка `day_5` от finalized Day 4 `9ff5b29`.

Этот документ — canonical Day 5 contract. Owner-approved решения ниже заменяют открытые варианты
исторического [анализа](../agent-runs/DAY-05-ANALYSIS.md), который сохраняется без переписывания.
API mapping, output ceiling и детали DTO ниже — engineering baseline, выбранный при финализации,
не отдельные цитаты/решения владельца. Account access всех трёх exact models проверен реальными calls.

## 1. Original challenge requirement

Отправить одинаковый запрос через API слабой, средней и сильной моделям. Измерить response time,
token usage и стоимость при платном использовании. Сравнить качество, скорость и расход ресурсов.
Результат: короткое сравнение/вывод, ссылки на модели, код.

## 2. Organizer clarifications

Источник: подтверждённые владельцем условия задания, не старые model examples из чата.

- Позиция HuggingFace ranking не принципиальна; нужны weak/medium/strong.
- Сила multi-factor, не один строгий score.
- Existing logical/analytical task разрешено переиспользовать.
- Нужны API calls, не ручное открытие model chats; model switcher в приложении допустим.
- Generated report может заменить видео **только для Day 5**. Не требуется LLM report generator.

## 3. Owner-approved model/provider decisions

| Tier key | UI label | Exact model id | Official model link |
|---|---|---|---|
| WEAK | GPT-5.6 Luna | `gpt-5.6-luna` | [Luna][luna] |
| MEDIUM | GPT-5.6 Terra | `gpt-5.6-terra` | [Terra][terra] |
| STRONG | GPT-5.6 Sol | `gpt-5.6-sol` | [Sol][sol] |

- Только **direct OpenAI API** для обязательной тройки; не OpenRouter/Groq/DeepSeek.
- Не заменять IDs на alias `gpt-5.6`, другие snapshots или новые модели без решения владельца.
- Budget verification: **до USD 5 суммарно**, включая дополнительные smokes/failed attempts.
- Benchmark: existing PaymentReceived; ничья допустима; усложнение ради победителя запрещено.
- Real experiment: 3 rounds × 3 models с точным порядком раздела 12.
- Day 1–4 продолжают использовать существующий DeepSeek, их contracts не мигрируют в OpenAI.

## 4. Model tier rationale

WEAK/MEDIUM/STRONG — относительные labels **в выбранной GPT-5.6 family**, не универсальная оценка.
Luna позиционируется для cost-sensitive workloads, Terra — balance intelligence/cost,
Sol — flagship professional work [Luna][luna], [Terra][terra], [Sol][sol].
Ни цена, ни tier не доказывают качество конкретного review. В UI показывать реальные имена;
пояснить относительность tiers, не называть Luna универсально слабой.
Для нужного baseline все три документируют Responses и `reasoning.effort=none`.
Документированных различий, препятствующих этому общему preset, не найдено; account limits/access
могут различаться и проверяются только при runtime.

## 5. Benchmark

Exact user input (как существующий `BENCHMARK` в `frontend/src/app/app.ts`):

````text
Проанализируй Java-код и найди инженерные риски. Не предполагай скрытые гарантии, которых нет в snippet.

```java
@Transactional
public void handle(PaymentReceived event) {
    Order order = orders.findById(event.orderId()).orElseThrow();
    order.markPaid();
    email.sendReceipt(order.customerEmail());
}
```
````

Reuse fixed [Day 3 reference](DAY-03.md), без передачи checklist модели:

1. Нет показанной idempotency/deduplication: duplicate delivery может повторить side effects.
2. Email и DB transaction не атомарны: email может уйти до последующего rollback.
3. Concurrent delivery допускает race и повторный внешний side effect без показанных гарантий.
4. Failure email внутри transaction и повторная доставка могут вызвать rollback/reprocessing.

Evaluation сохраняет исходные области checklist, но учитывает documented ambiguity Day 3:
`markPaid()`, delivery/locking guarantees и exception/rollback policy не показаны. Четвёртый риск
условен соответствующей rollback policy, а не утверждение, что абсолютно любая ошибка откатит DB.
Не требовать неподтверждённых assumptions как correct findings. Исторический Day 3 не переписывать.
Для arbitrary input benchmark checklist не выдавать за его ground truth; reference evaluation
привязать к точному benchmark snapshot/version `payment-received-v1`.

## 6. API contract

### Local backend API (engineering baseline)

```text
GET /api/model-options
  -> три allowlisted profiles: key, tier, label, provider=OPENAI, modelId, modelUrl

POST /api/model-review
  { "input": "<exact input>", "modelKey": "WEAK" }
```

Один вызов → один результат. Comparison orchestration остаётся Angular; backend COMPARE нет.
Unknown key/blank input → 400 без external call. Endpoint/model/pricing нельзя задавать из UI.
Небольшой concrete OpenAI Responses client (Java HttpClient + Jackson) рядом с Day 5 service/catalog;
не превращать DeepSeekClient в framework и не менять Day 1–4 response DTO.
Credential: backend `System.getenv("OPENAI_API_KEY")`, local-only setup по существующему launcher
паттерну; key не выводится и не входит в dialog, response или tracked docs.
`scripts/run-backend.ps1` загружает `OPENAI_API_KEY` из ignored `.env.local` в backend environment.

### OpenAI wire contract

Responses рекомендован для text-generation; все три модели его поддерживают [Text guide][text].
`POST https://api.openai.com/v1/responses`, JSON, `Authorization: Bearer <backend key>`.

```json
{
  "model": "gpt-5.6-luna",
  "input": [
    {"role": "developer", "content": "<fixed Day 5 instruction below>"},
    {"role": "user", "content": "<exact immutable input>"}
  ],
  "reasoning": {"effort": "none"},
  "max_output_tokens": 2000,
  "stream": false,
  "store": false,
  "service_tier": "default"
}
```

Fixed developer instruction / preset `day5-model-review-v1`:

```text
You are an engineering review mentor.
Analyze the provided code and identify real engineering risks and practical reliability improvements.
Clearly separate confirmed issues from assumptions not proven by the snippet.
Respond in Russian using Markdown when it improves readability.
```

- `model` — exact wire ID; reasoning nested `effort`, **не** Chat Completions `reasoning_effort`.
- System/developer — instruction roles, user — task; Responses также допускает top-level
  `instructions` для system/developer instruction. Выбран один developer message, без дублирования
  в system/instructions. Developer instruction приоритетнее user input [Text guide][text].
- `max_output_tokens=2000` — одинаковый hard ceiling, включая visible и reasoning tokens [API][api].
  Это safety/output ceiling эксперимента, не Day 2 semantic word limits/JSON schema.
- Temperature, top_p, penalties, verbosity, tools, web search не задаются. Sampling defaults
  не объявляются численно одинаковыми: намеренно меняется только model ID.
- `service_tier=default` фиксирует standard pricing, исключая случайный project auto/priority.
  Это не sampling parameter. Ответный service tier сохранить и сверить.
- `store=false`; нет `previous_response_id`, `conversation`, background или history input.
  Store=false не означает обещание нулевого provider retention вне API response storage.

### Response parsing and errors

- Итерировать `output` в порядке ответа, брать assistant `type=message`, затем content
  `type=output_text` и объединять text. Не предполагать `output[0].content[0]`.
  SDK helper `output_text` не является гарантированным top-level raw REST field [Text guide][text].
- Сохранить response `model`, `status`, `incomplete_details.reason`, `error` при наличии.
  Responses не возвращает Chat Completions `finish_reason`: не изобретать `stop`.
- Успех: `status=completed`, non-empty text, без refusal/error. Incomplete, failed, refusal,
  missing/empty output — отдельное явное состояние ошибки; доступный partial text/metrics сохранить.
  `max_output_tokens`/`content_filter` и другие reasons отображать как returned facts [API][api].
- HTTP ошибки обычно имеют `error.message/type/param/code`; парсер терпит non-JSON error body.
  400 invalid request; 401 authentication; 403 access; 404 unavailable resource/model;
  429 rate/quota/credit; 500/503 provider failure [Errors][errors]. Не обещать универсальную форму.
- OpenAI body/message может содержать чувствительные данные: UI получает безопасное русское
  category/message и allowlisted code/status, не raw headers/body. Не печатать даже ошибочно
  отражённый API key. Local API может вернуть 502 с metadata; malformed local request — 400.
- Finite HTTP timeout; no automatic retry, fallback model, repair или hidden extra calls.
  Partial failure не стирает siblings. Unknown usage/cost у failed call не означает free call.

## 7. Experiment invariants / fairness

Один immutable input + fixed developer instruction + 2000 ceiling + effort none + standard tier +
non-streaming + no tools/history для всей тройки. Only intended variable: model tier/model ID.
Actual request preset/model identity сохраняются для каждой попытки.
Reasoning none поддержан всеми тремя, но returned reasoning tokens не подменять ожидаемым нулём.
Tokenizers, provider load/cache и скрытые defaults могут давать различия; не обещать perfect equivalence.
Caching остаётся provider default, без искусственного padding, warming или cache-control experiment.
Cached/cache-write counts записываются и учитываются в стоимости.

## 8. Metrics and pricing model

Latency — monotonic elapsed вокруг внешнего `HttpClient.send` до полного body; включает сеть,
provider wait/generation, не browser render, не TTFT или чистое inference time. Отдельно UTC startedAt.

Raw response mappings [API][api], [Caching][caching]:

| Local metadata | OpenAI response |
|---|---|
| returnedModel | `model` |
| status / incompleteReason | `status` / `incomplete_details.reason` |
| inputTokens | `usage.input_tokens` |
| outputTokens | `usage.output_tokens` |
| totalTokens | `usage.total_tokens` |
| cachedInputTokens | `usage.input_tokens_details.cached_tokens` |
| cacheWriteInputTokens | `usage.input_tokens_details.cache_write_tokens` |
| reasoningTokens | `usage.output_tokens_details.reasoning_tokens` |
| actual service tier | `service_tier` |

Отсутствующее = null/UNKNOWN, не 0. Нельзя вычислять provider token usage из длины строки.
Reasoning tokens входят в output и не тарифицируются повторно поверх него.

### Pricing snapshot: `openai-gpt56-standard-2026-09-05`

USD / 1,000,000 text tokens, standard service, short-context baseline [Luna][luna], [Terra][terra], [Sol][sol]:

| Model | Uncached ordinary input | Cached read | Output | Cache write |
|---|---:|---:|---:|---:|
| gpt-5.6-luna | 0.20 | 0.02 | 1.20 | 0.25 |
| gpt-5.6-terra | 2.00 | 0.20 | 12.00 | 2.50 |
| gpt-5.6-sol | 4.00 | 0.40 | 20.00 | 5.00 |

Cache-write rates выведены из официального multiplier 1.25× uncached input. Cache-read 0.1×.
Не считать cache writes обычным input без наценки. Для GPT-5.6 caching minimum — 1024 visible
input tokens; нельзя предполагать zero caching только потому, что benchmark короткий [Caching][caching].

```text
ordinary = inputTokens - cachedInputTokens - cacheWriteInputTokens
estimated USD = (ordinary * inputRate
               + cachedInputTokens * cachedRate
               + cacheWriteInputTokens * writeRate
               + outputTokens * outputRate) / 1,000,000
```

Backend BigDecimal; no premature rounding. Counts должны быть nonnegative и согласованными.
Если нужная категория отсутствует/некорректна — amount=null, status=UNKNOWN с причиной, не
подстановка 0. UI всегда показывает реальные available metrics, даже если cost UNKNOWN.
Pricing snapshot сохраняется целиком: ID, checkedAt, source URLs, rates/unit/currency, применимые
условия, returned tier. Estimated cost не является точным списанием; кредиты не делают тариф free.

Material pricing rules:

- Для prompts >272K input tokens model pages указывают 2× input и 1.5× output для всего запроса.
  Это вне фиксированного Day 5 benchmark. Не применять short-context estimate к такому ответу:
  UNKNOWN/unsupported pricing regime, без тихого занижения.
- Sol promotional rates заявлены как доступные как минимум до 2026-11-21; перед будущими runs
  проверить snapshot заново. Старые results не пересчитываются по новым ценам.
- Batch/Flex/Priority/tools pricing не входит в baseline; unexpected tier → explicit mismatch,
  не calculation по standard без основания.

Resource usage здесь = tokens, API elapsed, monetary estimate, число attempts.
GPU/RAM/energy provider неизвестны и не выдумываются. Model metadata показывается отдельно.

## 9. UX / persistence contract

- Новая вкладка «Модели», три approved model labels с relative tier explanation.
- «Проанализировать» → одна модель; «Сравнить модели» → один input, три independent cards.
- Cards: actual requested/returned identity, official link, latency/tokens/cost/status, safe Markdown.
  Existing header badge не должен подписывать OpenAI results как DeepSeek.
- Reuse adaptive comparison geometry, sidebar, composer, Ctrl+Enter, smart scroll и inline errors.
- Manual found/missed/questionable + short human conclusion; ties allowed, no fake score/judge.
- Persist exchange input, model/profile/config snapshots, response/error/metadata, exact pricing
  snapshot, benchmark/reference identity и human notes. Changing selection/rates не меняет old cards.
- `state.ui` добавляет selected model; restore old dialogs backward-compatible. Existing local
  JsonNode dialog store достаточен, database/schema migration не требуется.
- Loading не сохранять как eternal pending; successful sides остаются видны при other errors.
  Model API body строится только из current input и preset, никогда из persisted exchanges.

## 10. Acceptance criteria

1. Все три exact approved model IDs вызываются direct OpenAI API с одним exact input.
2. Effort none, одинаковые prompt/output ceiling/service tier, no matrix/history/tools.
3. Selector/single/comparison, independent errors, responsive cards работают.
4. Returned text/status/model/usage и backend latency сохраняются без fabricated values.
5. Pricing formula покрывает ordinary/cache read/cache write/output; unknowns явны; snapshot immutable.
6. Девять attempts protocol выполнены; полноценное успешное evidence — три валидных ответа на модель.
   Ошибки не скрываются и не заменяются тайными повторениями.
7. Reference-based human comparison и вывод записаны; ничья допустима.
8. Dialog refresh/restart восстанавливает results/metrics/evaluations/UI, без model memory.
9. Backend/frontend tests/builds PASS, Day 1–4 не регрессируют; secrets/local artifacts не tracked.
10. Submission report, model links и code готовы; budget ≤USD 5.

## 11. Verification plan

План ниже выполнен в deterministic части: backend **46 tests / 0 failures/errors/skipped**, Maven
package PASS; frontend **15 tests PASS**, production build PASS. Day 4 historical baseline: 36/12.
В экспериментальном запуске 2026-09-05 product code не менялся, tests/builds не повторялись.
Фактические девять результатов, ограничения и Chrome evidence: [DAY-05 run](../agent-runs/DAY-05.md).

- Backend: allowlist/400-no-call; exact wire messages; effort none/2000/store false; output traversal
  с несколькими items; refusal/incomplete/error; returned metadata, nulls и usage validation.
- Deterministic transport seam/timer; timeout/non-2xx; отсутствие automatic retries/key leaks.
- BigDecimal fixture tests для всех трёх тарифов, cache write/read, missing categories,
  long-context/tier mismatch, tiny amounts и immutable pricing snapshot.
- Frontend: selector, one bubble/three results, partial failure, metrics/N/A, safe Markdown,
  human evaluation, snapshots и persisted restore; existing Day 1–4 tests.
- Full backend `mvn clean package`; frontend `npm test -- --watch=false`, `npm run build`.
  Фактические counts записать после implementation, не придумывать сейчас.
- Chrome UI single/comparison, responsive layout, evaluation + refresh; successful real responses
  брать из planned experiment где возможно, дополнительные calls входят в budget.
- Secret checks: `.env.local`, screenshots, dialog/session evidence ignored; OPENAI_API_KEY только
  backend env, не history/Git/UI. Day 4 docs delta сохраняется отдельно.

## 12. Real experiment protocol

**Owner-approved order; sequential independent calls:**

| Round | First | Second | Third |
|---|---|---|---|
| 1 | Luna | Terra | Sol |
| 2 | Terra | Sol | Luna |
| 3 | Sol | Luna | Terra |

Итого 9 calls. Main UI comparison может делать три independent parallel requests; latency вывод
основывать на harness с зафиксированным порядком, не смешивать условия без пометки.

1. До запуска проверить key/access, актуальность standard pricing и остаток USD 5 budget.
2. Зафиксировать один benchmark/prompt/preset snapshot; сохранить точный input и hash для сверки.
3. Каждую попытку сразу записывать в ignored `docs/local/agent-sessions/`, включая round/order,
   timestamp, requested/returned identity, safe response/error, usage/latency/cost snapshot.
4. No warming, hidden retries или extra LLM evaluation. Failed attempt тоже остаётся evidence;
   planned nine с failures не объявлять полным successful experiment. Повтор — отдельная явная запись.
5. Вести cumulative cost/budget ledger, включая smokes. До call резервировать conservative upper
   bound по input/output ceiling и тарифу; не запускать, если нельзя обосновать запас до USD 5.
   При неизвестном charge после timeout не считать попытку бесплатной и не продолжать вслепую.
   Owner budget не является автоматически установленным provider spending limit.
6. Ручная classification по каждому response; таблица всех 9 metrics, medians/ranges как
   descriptive statistics n=3, без universal superiority. Короткий quality/speed/resource trade-off.

## 13. Non-goals

Generic provider plugins, LLM judge, numeric quality score, database/auth/cloud dialog storage,
model × temperature × strategy × format matrix, tools/web/RAG/MCP/agents, conversation memory,
automatic fixes, Day 6, migration Day 1–4 to OpenAI, general settings/report dashboard.

## 14. Open decisions / blockers

Open product decisions: **NONE**. Model/provider/benchmark/budget/round order утверждены владельцем.
API facts for this baseline verified from official docs. Design blocker: **NONE**.
Owner confirmed: OpenAI key created, prepaid balance USD 5.
Runtime prerequisite **VERIFIED**: credential works in application environment, все три exact IDs
вернули реальные ответы/usage/default tier. 9-call experiment owner-authorized и выполнен.
Результат PARTIAL: Terra #2/#9 `incomplete/max_output_tokens`; остальные семь completed.
Raw/quality evidence: `docs/agent-runs/DAY-05.md`. Acceptance «три completed на модель» не достигнуто.
Следующий шаг по этому ограничению и окончательный quality conclusion остаются за владельцем;
повторов, поднятия потолка и автоматического исправления не было.
Если реальный API противоречит docs (effort/model access/tier), сохранить факт и остановить
соответствующий verification path; модели/preset не заменять молча.
Первый Luna smoke был отдельным вызовом (USD 0.0000948); затем owner разрешил ровно девять
experiment calls. Всего experiment USD 0.1460032, вместе со smoke USD 0.1460980 estimated.
UI проверен replay сохранённых first-round DTO без новых платных calls, с реальным dialog save/refresh.
No push/merge/rebase/branch deletion.

## 15. Submission plan

- [x] Owner decisions and canonical design
- [x] Implementation (working tree; implementation commit pending)
- [x] Automated tests/builds
- [x] Nine-attempt protocol + browser replay/persistence verification
- [ ] Three completed results per model (Terra 1/3; two explicit incomplete outcomes preserved)
- [ ] Short human/evidence-based report with quality/speed/resources conclusion and model links
- [ ] Code publication/link for Day 5 (separate owner-authorized Git flow)

Report может заменить video по Day 5 clarification; in-app report generation не требуется.
Repository: https://github.com/nikita11174/ai-advent — это существующий repo, не evidence публикации
ещё не опубликованного Day 5. Day 1–4 pending submissions не закрываются этим документом.

## 16. Official external references — checked 2026-09-05

Проверены официальные страницы и их `.md` representations (часть HTML API reference слишком
велика для web reader, Markdown прочитан напрямую). Никакие chat examples не использованы как тариф.

- [GPT-5.6 Luna][luna] — exact ID, positioning, effort none, endpoints, pricing/long-context/cache writes.
- [GPT-5.6 Terra][terra] — то же для MEDIUM.
- [GPT-5.6 Sol][sol] — то же для STRONG, promotional period.
- [Text generation][text] — Responses recommendation, roles, output text traversal, SDK-vs-REST distinction.
- [Create response API][api] — request fields, status/incomplete, usage, service tier/store semantics.
- [Prompt caching][caching] — cache read/write counts, 1.25× writes, billing formula, minimum prefix.
- [API errors][errors] — status/error categories, quota vs rate-limit distinction.

[luna]: https://developers.openai.com/api/docs/models/gpt-5.6-luna
[terra]: https://developers.openai.com/api/docs/models/gpt-5.6-terra
[sol]: https://developers.openai.com/api/docs/models/gpt-5.6-sol
[text]: https://developers.openai.com/api/docs/guides/text
[api]: https://developers.openai.com/api/reference/resources/responses/methods/create
[caching]: https://developers.openai.com/api/docs/guides/prompt-caching
[errors]: https://developers.openai.com/api/docs/guides/error-codes
