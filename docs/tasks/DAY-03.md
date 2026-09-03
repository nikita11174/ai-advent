# Day 3 — Разные подходы к рассуждению

Статус: **READY_FOR_IMPLEMENTATION**

Product direction: **Engineering Review Mentor**

## 1. Original challenge requirement

Взять одну логическую, алгоритмическую или аналитическую задачу и решить один и тот же task через
API четырьмя способами:

1. **DIRECT** — прямой ответ без дополнительной инструкции о рассуждении.
2. **STEP_BY_STEP** — добавить инструкцию, эквивалентную «решай пошагово».
3. **SELF_PROMPT** — сначала попросить LLM создать prompt для исходной задачи, затем использовать
   сгенерированный prompt для решения той же исходной задачи.
4. **EXPERTS** — задать в prompt группу экспертов и получить решение от каждого эксперта.

Нужно сравнить, различаются ли ответы и какой подход дал наиболее точный результат.

Required result: несколько решений одной задачи и их сравнение. Submission: видео + код.

## 2. Organizer clarifications

- В существующем chat UI допустимо реализовать Day 3 как разные prompting strategies.
- SYSTEM prompt рекомендуется использовать для влияния на стратегию; council/consilium особенно
  естественно задавать именно там.
- Исходная задача может быть hardcoded, predefined или введена свободно.
- LLM-as-a-judge обсуждался как optional technique, но не является требованием Day 3.
- Нельзя заранее объявить универсального победителя: на разных задачах выигрывали SELF_PROMPT,
  EXPERTS или STEP_BY_STEP.
- Наблюдался SELF_PROMPT failure, когда generated prompt потерял существенное ограничение исходной
  задачи. Generated prompt поэтому должен быть inspectable и не должен автоматически repair/retry.
- Hidden chain-of-thought не требуется и не должен быть product dependency. STEP_BY_STEP меняет
  инструкцию анализа, но продукт показывает только итоговый ответ.

## 3. Product fit

Day 3 использует небольшую аналитическую code-review задачу. Один Java snippet анализируется
четырьмя стратегиями, а пользователь сравнивает полноту и обоснованность найденных рисков.

Это полезный increment Mentor: он показывает, как prompting approach влияет на engineering
judgment и coverage, но ещё не вводит user skill profile, persistent heuristics или автоматическую
замену human review.

## 4. Current Day 1/2 baseline

- Spring Boot `POST /api/review` и reusable `DeepSeekClient`; model `deepseek-v4-flash`,
  non-thinking, non-streaming.
- FREE: русский safe Markdown; отсутствующий mode совместим с Day 1.
- CONTROLLED: validated JSON review с per-request settings и inspectable raw response.
- Angular exchange хранит один immutable input; FREE/CONTROLLED results и errors независимы;
  comparison выполняется frontend без backend compare mode.
- History/settings существуют только в текущей странице и не отправляются модели.

Day 2 JSON schema можно переиспользовать технически, но он не содержит отдельных expert
conclusions. Расширять его только ради Day 3 означало бы смешать output-format и reasoning
experiments.

## 5. Candidate benchmark tasks

Reference findings используются только для evaluation после получения ответов и не передаются в
prompts моделей.

### Final benchmark — повторная доставка события оплаты (OWNER-APPROVED)

```java
@Transactional
public void handle(PaymentReceived event) {
    Order order = orders.findById(event.orderId()).orElseThrow();
    order.markPaid();
    email.sendReceipt(order.customerEmail());
}
```

Фиксированный reference checklist:

1. Нет idempotency/deduplication: повторная доставка может повторно выполнить side effects.
2. Email и DB transaction не атомарны: email может уйти, а transaction затем откатиться.
3. Concurrent delivery одного события/order создаёт race между проверкой/изменением состояния и
   допускает повторный внешний side effect.
4. Ошибка email внутри transaction откатывает DB change, после чего повторная доставка может
   снова выполнить обработку и внешний side effect.

Почему подходит: короткий код, но объективно показывает transaction boundary, idempotency,
concurrency и external side effects — центральные области Engineering Review Mentor. Проверка
допустимости state transition не включена в reference checklist: реализация `markPaid()` не дана,
поэтому такой finding может быть только условным extra, а не ожидаемым дефектом.

Ambiguity risks: framework delivery guarantees, transaction semantics repository и реализация
`markPaid()` не показаны. Ответы, которые утверждают конкретную гарантию без условия, считаются
questionable.

### Candidate B — небезопасный локальный cache

```java
private final Map<String, Price> cache = new HashMap<>();

public Price getPrice(String sku) {
    if (!cache.containsKey(sku)) {
        cache.put(sku, priceClient.load(sku));
    }
    return cache.get(sku);
}
```

Reference findings:

1. `HashMap` небезопасен при concurrent access.
2. Check-then-act допускает несколько одинаковых external calls.
3. Cache не имеет invalidation/TTL и может возвращать устаревшую цену.
4. Cache растёт без bound/eviction.
5. Поведение для `null` result зависит от contract `priceClient` и должно быть обозначено условно.

Почему подходит: очень короткий и понятный пример с concurrency/data-flow/lifecycle risks.

Ambiguity risks: thread usage, допустимая stale data и expected cache lifetime не заданы; эти
риски нельзя автоматически объявлять defects без assumptions.

### Candidate C — parallel processing внутри transaction

```java
@Transactional
public void activate(List<Account> accounts) {
    accounts.parallelStream().forEach(account -> {
        account.activate();
        repository.save(account);
    });
}
```

Reference findings:

1. Spring transaction context не переносится автоматически на worker threads.
2. Persistence context/entity manager обычно не предназначен для concurrent use.
3. Ошибка одного worker создаёт неочевидные partial-work/exception semantics.
4. Concurrent mutation переданных entities может нарушать thread-safety assumptions.
5. Parallel overhead может ухудшить работу при blocking DB I/O.

Почему подходит: хорошо различает поверхностный code review и system-level reasoning.

Ambiguity risks: требует знания Spring/JPA; конкретный repository/provider и размер input не
показаны. Для короткого публичного demo задача менее доступна, чем Candidate A.

Candidates B/C сохранены как история рассмотренных вариантов. Owner выбрал Candidate A; exact
snippet и четыре пункта checklist фиксируются до evaluation и не передаются модели.

## 6. Four reasoning strategies

Во всех calls неизменны model, original task и FREE Markdown presentation. Меняется только
strategy instruction.

### DIRECT

- System: существующий минимальный Engineering Review Mentor prompt без дополнительных reasoning
  instructions.
- User: exact original task snapshot.
- API calls: **1**.

### STEP_BY_STEP

- System: базовая роль Mentor плюс инструкция последовательно проверить assumptions, invariants,
  edge cases и соседние effects, затем дать только итоговый русский Markdown answer.
- User: тот же exact original task snapshot, без дописывания или переформулирования.
- Не запрашивать и не сохранять hidden chain-of-thought; «пошагово» — стратегия подготовки
  итогового ответа.
- API calls: **1**.

### SELF_PROMPT

Call 1 — prompt generation:

- System: создать solving prompt для engineering review; сохранить все ограничения original task,
  не решать сам task и вернуть только generated prompt.
- User: exact original task snapshot.

Call 2 — solution:

- System: generated prompt из Call 1 используется без automatic repair/constraint injection.
- User: тот же exact original task snapshot.
- UI хранит оба immutable artifacts и даёт действие `Показать сгенерированный промпт`.
- Если generated prompt упустил constraint, это остаётся наблюдаемым evidence; automatic retry или
  correction запрещены.
- API calls: **2**.

### EXPERTS

- System: фиксирует council roles, общую engineering-review цель и требует отдельный русский
  Markdown section/conclusion от каждого эксперта без финального «голосования» вместо их ответов.
- User: тот же exact original task snapshot.
- Один call достаточен: challenge требует вывод каждого эксперта, а не отдельные network calls или
  multi-agent architecture.
- API calls: **1**.

Candidate expert sets:

1. **OWNER-APPROVED:** Java correctness reviewer;
   reliability/idempotency/concurrency reviewer;
   architecture critic. Покрывает local code, runtime/system risks и assumptions без дублирования.
2. Application developer; QA/edge-case analyst; operations/SRE reviewer. Доступнее, но слабее
   фокусируется на transaction/idempotency semantics.
3. Transaction specialist; distributed-systems reviewer; maintainability critic. Глубокий, но
   слишком специализирован для произвольного engineering input.

## 7. API-call topology

Предлагаемый отдельный endpoint не смешивает Day 2 output controls с reasoning strategy:

```http
POST /api/reasoning-review
```

```json
{"input":"exact original task","strategy":"DIRECT"}
```

Обычный response:

```json
{"strategy":"DIRECT","analysis":"Russian Markdown"}
```

SELF_PROMPT response дополнительно содержит generated prompt:

```json
{"strategy":"SELF_PROMPT","generatedPrompt":"...","analysis":"Russian Markdown"}
```

| Strategy | Calls | Topology |
|---|---:|---|
| DIRECT | 1 | original → solution |
| STEP_BY_STEP | 1 | original + strategy system instruction → solution |
| SELF_PROMPT | 2 | original → generated prompt; generated prompt + same original → solution |
| EXPERTS | 1 | council system instruction + original → per-expert conclusions |
| Compare all | 5 | четыре независимых strategy paths; SELF_PROMPT использует два calls |

Backend compare mode не добавляется. Frontend запускает paths независимо и сохраняет успешные
результаты, если другая strategy завершилась ошибкой.

## 8. Accuracy/evaluation options

| Option | Challenge compliance | Product usefulness | Reliability | Complexity / risks |
|---|---|---|---|---|
| A. Только human comparison | Да | Средняя: тренирует judgment | Зависит от reviewer | Минимально; субъективность и слабая воспроизводимость |
| B. Curated benchmark + owner-approved reference + human classification | Да | Высокая: сравнение связано с реальными engineering invariants | Высокая для выбранного task при явных assumptions | Небольшая reference card/checklist; нельзя показывать reference модели |
| C. LLM-as-a-judge | Да, но не требуется | Потенциально удобно | Judge bias, position/style bias, может предпочесть уверенный/длинный ответ | Дополнительные calls/prompt/schema; результат нельзя считать ground truth |
| D. Text/keyword matching | Формально частично | Низкая | Низкая: не распознаёт paraphrase и поощряет keywords | Просто, но даёт ложную точность |

Owner decision: **B**, с explicit human classification каждого результата:

- expected findings found;
- expected findings missed;
- questionable/extra findings;
- краткое основание выбора winner.

Accuracy не определяется длиной, количеством findings или уверенностью формулировки. LLM judge
остаётся optional future experiment, не Day 3 dependency. Reference checklist может быть показан
после answers; автоматическое semantic matching не добавляется.

## 9. Relationship with Day 2 output controls

### Option A — новый fixed Day 3 contract

Хорошо изолирует strategy и облегчает машинное сравнение, но требует новой schema; EXPERTS и
SELF_PROMPT добавляют variant-specific поля. Scope заметно растёт.

### Option B — existing FREE Markdown for all four (RECOMMENDED, not owner-approved)

Минимальное расширение, естественно показывает generated prompt и expert sections, не смешивает
reasoning с output control. Evaluation выполняется против reference checklist человеком. Минус —
нет автоматического structured matching.

### Option C — existing CONTROLLED review with fixed settings

Структура облегчает визуальное сравнение, но current schema не выражает conclusion каждого
эксперта. Её расширение меняет Day 2 contract или создаёт special cases и смешивает две
экспериментальные переменные.

### Option D — разрешить FREE/CONTROLLED для каждой strategy

Даёт матрицу `4 × 2 × editable settings`, усложняет UX и нарушает experimental invariant без
требования challenge.

Owner decision: **B**. В UI Day 2 остаётся отдельным
experiment `Формат ответа`, Day 3 —
`Стратегия анализа`; внутри Day 3 output всегда FREE Markdown. Model, original task и presentation
фиксированы, меняется только reasoning prompt.

## 10. Proposed UX

- Добавить компактный верхнеуровневый experiment switch: `Формат ответа` (существующий Day 2) /
  `Стратегия анализа` (Day 3). Это не settings dashboard.
- В Day 3 показать selector: `Прямой`, `Пошаговый`, `Самопромпт`, `Эксперты`.
- `Проанализировать` запускает выбранную strategy; `Сравнить все стратегии` фиксирует один exact
  input snapshot и создаёт одну exchange с четырьмя result slots.
- Desktop grid `DIRECT / STEP_BY_STEP` и `SELF_PROMPT / EXPERTS`; narrow viewport — stack.
- Каждая side имеет независимые loading/error/result states; failure не удаляет другие answers.
- SELF_PROMPT содержит toggle `Показать сгенерированный промпт` как escaped plain text.
- EXPERTS показывает три Markdown sections с явными role labels/conclusions.
- Comparison metadata хранит strategy names, benchmark identity/version (если выбран predefined
  task) и не меняется после изменения текущего UI state.
- Для approved benchmark после ответов показать reference checklist и явные поля ручной
  классификации `found / missed / questionable`; winner выбирается человеком с объяснением по
  reference evidence, без псевдоскоров и unreliable keyword matching.
- Добавить локальные файловые диалоги: `Новый диалог`, список по времени обновления и восстановление
  выбранного диалога. Это visual/application history; сохранённые сообщения не отправляются LLM.

## 11. Planned backend changes

1. Добавить enum reasoning strategy и отдельный `/api/reasoning-review` request/response contract.
2. Переиспользовать transport/model/error handling `DeepSeekClient`, не дублировать HTTP client.
3. Сформировать фиксированные system messages для четырёх strategies; user content всегда exact
   original input snapshot.
4. Реализовать SELF_PROMPT orchestration из двух calls и вернуть generated prompt неизменённым.
5. EXPERTS выполнить одним call с approved fixed roles и требованием отдельных conclusions.
6. Не менять `/api/review`, ReviewMode/ReviewControls или Day 2 JSON validation.
7. Добавить deterministic tests message topology, call counts, unchanged original input,
   self-prompt propagation и partial/upstream failures без real API.

Новая generic provider abstraction или general prompt framework не нужны: текущая внешняя граница
`DeepSeekClient` уже является достаточным reuse point.

## 12. Planned frontend changes

1. Разделить Day 2 format experiment и Day 3 strategy experiment компактным switch.
2. Расширить exchange model отдельным reasoning comparison snapshot и четырьмя независимыми slots.
3. Добавить selected-strategy и compare-all actions без backend compare endpoint.
4. Переиспользовать safe Markdown renderer; generated prompt показывать escaped text, не innerHTML.
5. Добавить optional predefined benchmark loader/reference panel после owner choice.
6. Сохранить sticky composer, Ctrl+Enter, smart scroll и immutable old exchanges; заменить
   session-only history на backend file persistence под `docs/local/mentor-dialogs/`.
7. Targeted tests selector, exact identical input, 1/1/2/1 call topology, four results,
   generated-prompt toggle, expert sections, partial failure и Day 1/2 regression.
8. Добавить минимальные create/list/load/update dialog API и сохранять complete exchanges без
   transient loading state. ID генерируется приложением, title выводится из первого input.

## 13. Verification plan

Implementation round должен проверить:

- backend tests/build и frontend tests/production build с фактическими counts;
- exact same original task во всех четырёх paths;
- DIRECT и STEP_BY_STEP: по одному real response;
- SELF_PROMPT: Call 1 возвращает inspectable prompt, Call 2 использует его и тот же original task;
- EXPERTS: один response содержит отдельный conclusion каждого approved expert;
- browser four-way comparison и сохранение успешных slots при failure другого;
- human reference classification и обоснованный winner без length/count proxy;
- Day 1 FREE и Day 2 CONTROLLED regressions;
- `.env.local`/secret safety.

**Три независимых run каждой strategy рекомендуются только для verification harness/evidence**, не
для основного UI и не как literal challenge acceptance. Они полезны из-за stochastic output и
наблюдаемой смены winner между runs/tasks. Для approved benchmark это 15 LLM calls: по 3 для
DIRECT, STEP_BY_STEP и EXPERTS, плюс 6 calls для трёх SELF_PROMPT pipelines. Сравнивается coverage
reference findings и questionable extras по runs; identical wording не ожидается.

Если стоимость/время важнее оценки stability, минимальный acceptance smoke — один four-way run
(5 calls). Выбор объёма verification не меняет product architecture.

## 14. Acceptance criteria

1. Один immutable original task snapshot анализируется DIRECT, STEP_BY_STEP, SELF_PROMPT и EXPERTS.
2. DIRECT выполняет один call без additional reasoning instruction.
3. STEP_BY_STEP выполняет один call с strategy system instruction и не требует hidden reasoning.
4. SELF_PROMPT выполняет ровно два calls; generated prompt доступен пользователю и не repair/retry.
5. SELF_PROMPT Call 2 использует generated prompt и тот же exact original task.
6. EXPERTS выполняет один call и показывает отдельный conclusion каждого approved expert.
7. Four-way comparison показывает несколько решений одной задачи и сохраняет успешные results при
   partial failure.
8. Сравнение использует постоянные model/input/output presentation; intentional variable — только
   reasoning strategy.
9. Accuracy оценивается утверждённым методом, учитывающим expected found/missed и questionable
   extras; длина и finding count сами по себе не являются accuracy.
10. Old exchanges и generated prompt остаются immutable после изменения current input/UI state.
11. Day 1 FREE и Day 2 FREE/CONTROLLED behavior не регрессируют.
12. Targeted automated tests, production builds и required real API/browser smoke проходят.
13. Secret не попадает во frontend, responses/log reports или Git.
14. Submission содержит video и repository/code link.

## 15. Explicit non-goals

- hidden chain-of-thought capture/display;
- LLM-as-a-judge как обязательный component;
- generic multi-agent/council architecture или отдельные expert network calls;
- матрица reasoning strategy × FREE/CONTROLLED/settings;
- database, authentication, cloud persistence или отправка persisted history модели;
- user-first review comparison, skill scoring/profile или long-term heuristics;
- RAG или MCP product functionality;
- automatic code fixes;
- Day 4+ functionality, generic provider abstraction или prompt playground.

## 16. Owner-approved decisions and blockers

**OWNER-APPROVED:** PaymentReceived benchmark; curated reference checklist + transparent human
classification; отдельный Day 3 experiment с fixed FREE Markdown; council из Java Correctness,
Reliability / Concurrency / Idempotency и Architecture perspectives; real SELF_PROMPT two-call
flow; отдельный 3-runs-per-strategy verification harness; local file-backed dialog history.

Open product decisions: **NONE**. Implementation blockers: **NONE**.
