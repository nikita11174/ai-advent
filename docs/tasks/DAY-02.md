# Day 2 — Управление форматом ответа

Статус: **SUBMISSION_PENDING**

- Day 2 implementation: **DONE**
- Day 2 verification: **DONE**
- Day 2 submission: **PENDING**

Product direction: **Engineering Review Mentor**

## 1. Original challenge requirement

> День 2 — формат ответа.
>
> Один и тот же запрос нужно отправить с разными уровнями контроля вывода.
> Контролируемый вариант должен добавить:
> - явный формат ответа;
> - ограничение длины ответа;
> - условие завершения ответа: stop sequence или явная инструкция.
>
> Сравнить свободный и контролируемый ответы.
>
> Результат: один запрос с разными уровнями контроля вывода через API.
> Формат submission: видео + код.

## 2. Organizer clarifications

По предоставленным owner task evidence:

- UI должен позволять переключаться между режимами;
- для controlled output желателен стабильный детерминированный JSON-формат;
- JSON рекомендован, а обязательная учебная цель — детерминированный формат;
- Day 2 посвящён управлению model request/output;
- некоторые модели поддерживают stop sequences, но исходное задание прямо разрешает вместо них
  явную инструкцию завершения.

Эти уточнения не означают требование идентичного текста ответа или переход к полноценной системе
structured code review.

### Telegram export provenance — 2026-09-05

Локальный export из `docs/REFERENCES.md` подтвердил и уточнил смысл задания:

- идеальная цель — стабильная схема при повторных запросах; значения могут различаться, формат
  должен сохраняться (`1189`, `1201`, `1219`, `1307–1308`);
- JSON рекомендован, но не является единственным допустимым форматом: детерминированная структура
  важнее конкретного синтаксиса (`1191`, `1216`, `1241`);
- завершение можно выразить поддерживаемым API stop sequence либо инструкцией в prompt
  (`1245–1246`, также `1785–1786`);
- на output влияют system и user prompt; стабильное application-level ограничение естественно
  закреплять в system prompt (`1295`, `1302–1315`);
- продолжение Day 1 HTTP client тем же проектом названо разумным вариантом (`1293`).

Совет Алексея «прогнать 20 раз» (`1294`, `1307`) — robustness recommendation, не буквальный
acceptance criterion сообщения `1160`. Три real default runs подтверждают наш agreed gate, но не
являются статистической гарантией для произвольного числа запросов.

## 3. Goal и minimal product increment

Добавить к существующему Mentor два режима анализа одного developer input:

```text
same Java snippet / engineering question
  -> FREE       -> Russian free-form Markdown analysis
  -> CONTROLLED -> validated compact JSON engineering analysis
  -> side-by-side comparison in the existing web chat
```

Это MVP текущего шага продукта: пользователь наглядно видит эффект управления форматом, длиной и
завершением ответа. Это не MVP всего будущего review workflow.

## 4. Verified DeepSeek capabilities

Для текущего `POST /chat/completions`, model `deepseek-v4-flash`, non-thinking mode:

- `response_format: {"type":"json_object"}` включает JSON Output и гарантирует валидную JSON
  строку; prompt также должен явно требовать JSON и показывать желаемую форму;
- JSON Output не гарантирует конкретную domain schema, поэтому shape задаётся prompt и проверяется
  приложением;
- документация предупреждает, что JSON Output изредка может вернуть пустой content; приложение
  должно обработать его как invalid upstream response, а не как успешный review;
- `max_tokens` ограничивает число генерируемых tokens; слишком малое значение может обрезать JSON;
- `stop` принимает строку или массив до 16 строк и останавливает генерацию до совпавшей
  последовательности;
- `finish_reason="stop"` означает natural stop либо срабатывание stop sequence;
  `finish_reason="length"` означает достижение `max_tokens` или context limit и предупреждает о
  возможном обрезании content.

Источники: [Chat Completions API](https://api-docs.deepseek.com/api/create-chat-completion/),
[JSON Output](https://api-docs.deepseek.com/guides/json_mode/).

Research outcome: **DOCUMENTED**. Миграция на Responses API или другую модель для Day 2 не нужна.

## 5. API contract

Существующий endpoint сохраняется:

```http
POST /api/review
```

```json
{"input":"...","mode":"FREE"}
```

или:

```json
{
  "input": "...",
  "mode": "CONTROLLED",
  "controls": {
    "maxTokens": 600,
    "maxFindings": 3,
    "summaryMaxWords": 30,
    "reasonMaxWords": 30,
    "recommendationMaxWords": 30,
    "terminationInstruction": "Return exactly one JSON object. Stop immediately after the final closing brace. Do not add Markdown, explanations or text outside the JSON object."
  }
}
```

`mode` в новом UI передаётся явно. Для обратной совместимости отсутствие `mode` трактуется как
`FREE`, поэтому существующий Day 1 request остаётся рабочим. FREE request не содержит `controls`.
Если у CONTROLLED request весь объект `controls` отсутствует, backend применяет Day 2 defaults;
если объект передан, все его поля обязательны и валидируются до обращения к DeepSeek.

FREE response сохраняет существующий контракт:

```json
{"analysis":"Russian Markdown text"}
```

CONTROLLED response возвращает уже распарсенный объект:

```json
{
  "review": {
    "summary": "string",
    "findings": [
      {
        "severity": "HIGH",
        "title": "string",
        "reason": "string"
      }
    ],
    "recommendation": "string"
  },
  "rawResponse": "{\"summary\":\"...\",...}"
}
```

`review` используется для основного structured UI, а `rawResponse` сохраняет точный исходный
JSON content от DeepSeek для действия `Показать JSON`. Raw JSON не проходит через Markdown
renderer.

Отдельный backend mode `COMPARE` не создаётся. Сравнение — frontend orchestration двух независимых
requests с одним неизменённым snapshot input.

## 6. CONTROLLED contract and settings

Day 2 defaults:

| Control | Default | Server-side range |
|---|---:|---:|
| Response format | JSON, fixed/read-only | Не настраивается |
| `maxTokens` | 600 | 100..2000 |
| `maxFindings` | 3 | 0..10 |
| `summaryMaxWords` | 30 | 1..100 |
| `reasonMaxWords` | 30 | 1..100 |
| `recommendationMaxWords` | 30 | 1..100 |
| `terminationInstruction` | См. ниже | 1..1000 символов после trim |

Default termination instruction:

> Return exactly one JSON object. Stop immediately after the final closing brace. Do not add
> Markdown, explanations or text outside the JSON object.

Settings существуют только в page/session state и после refresh могут сброситься. UI action
`Сбросить настройки` восстанавливает все defaults. JSON format остаётся fixed/read-only; TEXT,
XML и custom formats не добавляются.

Фактический contract каждого CONTROLLED request определяется snapshot переданных controls:

- `summary`: не более `summaryMaxWords` слов;
- `findings`: от 0 до `maxFindings` элементов;
- `severity`: только `HIGH`, `MEDIUM` или `LOW`;
- `title`: короткий непустой заголовок;
- `reason`: не более `reasonMaxWords` слов;
- `recommendation`: не более `recommendationMaxWords` слов;
- если существенных рисков нет, `findings` должен быть пустым — findings не выдумываются.

Ограничение слов проверяется простым подсчётом непустых whitespace-separated слов. Backend с
Jackson проверяет parseability, обязательные и допустимые поля, типы, severity, размер массива и
лимиты слов против settings snapshot именно этого request. Numeric values вне таблицы, blank или
слишком длинная termination instruction и controls в FREE request отклоняются как `400 Bad
Request` до внешнего вызова.

Controlled DeepSeek request использует:

- `response_format: {"type":"json_object"}`;
- prompt с явным JSON example/shape, фактическими semantic limits и фактической termination
  instruction из request snapshot;
- `max_tokens` из settings snapshot (`600` по умолчанию);
- `stream: false` и `thinking: {"type":"disabled"}`, как в Day 1.

Backend читает `finish_reason`: `length` считается неуспешным controlled result, поскольку JSON мог
быть обрезан; успешный content должен распарситься и пройти contract validation.

Цель — стабильная структура и проверяемые ограничения, а не идентичная формулировка модели.

## 7. Termination decision

Baseline — редактируемая явная инструкция в controlled prompt; default:

> Return exactly one JSON object. Stop immediately after the final closing brace. Do not add
> Markdown, explanations or text outside the JSON object.

Параметр DeepSeek `stop` в baseline не используется. Организатор разрешает explicit termination
instruction. Закрывающая фигурная скобка небезопасна как literal stop sequence: вложенные objects
тоже содержат закрывающие скобки, поэтому response может оборваться до завершения root JSON.

## 8. UI plan

- Сохранить текущий русский chat UI, sticky composer, session-only history, smart scroll,
  loading/error states и независимость LLM requests.
- Добавить компактный selector `Свободный` / `Контролируемый` рядом с composer/action.
- Обычное действие отправляет текущий input в выбранном режиме.
- В CONTROLLED mode показать компактную output-control panel: fixed `JSON`, редактируемые
  `maxTokens`, `maxFindings`, три word limit и termination instruction, а также
  `Сбросить настройки`. Не показывать другие model/provider controls.
- Добавить действие `Сравнить режимы`: зафиксировать один snapshot input и отправить его дважды —
  сначала FREE без controls, затем CONTROLLED с одним snapshot текущих settings.
- Показать одну user message и одну comparison exchange с явно подписанными результатами. Не
  дублировать user message. На desktop — две колонки, на узком viewport — последовательные блоки.
- FREE отображать существующим safe Markdown renderer.
- CONTROLLED прежде всего отображать обычными секциями/cards `Кратко`, `Замечания` и
  `Рекомендация`. Действие `Показать JSON` раскрывает точный raw JSON от DeepSeek; он не
  обрабатывается Markdown renderer.
- Comparison metadata фиксируется вместе с exchange, например
  `JSON · max 600 tokens · до 3 замечаний`, используя фактический settings snapshot. Последующие
  изменения panel не переписывают старую metadata.
- После сравнения оба API calls остаются независимыми; предыдущая chat history модели не
  отправляется.

Последовательные calls выбраны как минимальная orchestration: они проще для loading/error flow и
не добавляют конкурентное состояние. Это не мешает сравнению одинакового input.

## 9. Implementation steps

### Backend

1. Расширить request полем mode с backward-compatible default `FREE`.
2. Сохранить FREE path и Day 1 prompt/response без изменения поведения.
3. Добавить validated controls/defaults, controlled prompt/request параметры и минимальные
   Jackson records для результата.
4. Распарсить и провалидировать content против request controls snapshot и `finish_reason`;
   вернуть typed result вместе с неизменённым raw response.
5. Для malformed JSON, invalid contract, truncation или empty content вернуть отдельную
   controlled-response ошибку; приложить raw response, когда он существует.
6. Добавить targeted unit/controller tests для modes, bounds, settings propagation и invalid
   controlled responses.

### Frontend

1. Расширить API types и client для `FREE` / `CONTROLLED`.
2. Добавить selector, controlled settings/reset и compare action в существующий composer.
3. Добавить single-mode и comparison exchanges со snapshot input/settings без отправки history.
4. Сохранить safe Markdown для FREE; CONTROLLED показать structured sections с toggle raw JSON.
5. Добавить targeted tests selector/defaults/reset, request propagation, один user message на
   comparison, immutable metadata snapshot, порядка/results и controlled errors.

### Verification

1. Выполнить backend tests и `mvn clean package`.
2. Выполнить frontend tests и production build.
3. Сделать три реальных CONTROLLED calls с defaults и один с изменёнными settings.
4. Выполнить browser smoke одиночных режимов и comparison exchange.
5. Проверить, что secret остаётся только в backend environment и generated/local files не tracked.

## 10. Acceptance criteria

1. UI содержит selector `Свободный` / `Контролируемый` и действие `Сравнить режимы`.
2. Один и тот же exact input может быть отправлен как FREE и CONTROLLED; compare использует один
   неизменённый snapshot для обоих requests.
3. FREE сохраняет Day 1 Russian Markdown behavior и не получает Day 2 format/length/termination
   controls.
4. CONTROLLED request использует `response_format: {"type":"json_object"}`, documented JSON
   shape и фактические `maxTokens`, semantic limits и termination instruction из request snapshot.
5. Controlled content успешно парсится; присутствуют только ожидаемые root/finding fields,
   severity и cardinality соответствуют contract.
6. При отсутствии material risks `findings` пуст, а не заполнен искусственными замечаниями.
7. Invalid controls отклоняются до внешнего API call; результат валидируется против actual
   settings текущего request, а не hardcoded defaults.
8. Пустой content, `finish_reason="length"`, malformed JSON и нарушение contract не repair/retry,
   не fallback в Markdown и не выдаются за успешный review. UI показывает controlled-response
   error и позволяет раскрыть raw response, если он получен.
9. CONTROLLED primary UI показывает structured summary/findings/recommendation; `Показать JSON`
   раскрывает точный raw DeepSeek JSON без Markdown rendering.
10. Три реальных controlled requests с defaults дают valid JSON, ожидаемую schema и PASS
    validation; wording может различаться. Один customized request доказывает propagation и
    применение изменённых settings.
11. Comparison отображает одно user message и оба подписанных результата в одной exchange: рядом
    на desktop и друг под другом на узком viewport.
12. Comparison использует один snapshot input/settings; metadata показывает actual values и не
    меняется после последующего редактирования panel.
13. Session history и settings остаются только frontend state; ни single, ни compare request не отправляет
    предыдущую историю модели.
14. `Сбросить настройки` восстанавливает Day 2 defaults; JSON format нельзя изменить.
15. Targeted frontend/backend tests проходят; Maven и Angular production builds проходят.
16. Real browser comparison проходит через Angular → Spring Boot → DeepSeek и показывает оба
    непустых результата.
17. `DEEPSEEK_API_KEY` не попадает во frontend, output или Git.
18. Submission содержит demo video и repository/code link.

## 11. Explicit non-goals

- persistence, database или authentication;
- user-first review comparison, scoring или skill tracking;
- heuristics storage;
- RAG или MCP product functionality;
- agents;
- Day 3 reasoning strategies;
- generic provider abstraction или generic LLM settings panel;
- temperature, `top_p`, penalties, thinking/model selector, raw system prompt или provider
  settings;
- migration на Responses API;
- conversation memory;
- localStorage или иная settings persistence;
- complex review dashboard или schema framework;
- automatic retry, JSON repair или Markdown fallback для invalid controlled result.

## 12. Decisions and blockers

Все решения, необходимые до implementation, закрыты. Блокирующих неизвестностей нет.
**Open product decisions: NONE.**

## 13. Implementation and verification evidence

Реализовано:

- backward-compatible FREE (`mode` отсутствует или равен `FREE`) без JSON controls;
- CONTROLLED request с per-request settings, JSON Output и `max_tokens` из snapshot;
- Jackson parsing и строгая shape/severity/cardinality/word-limit validation;
- raw DeepSeek content в success/error contract без retry, repair или fallback;
- русский mode/settings UI, structured review, escaped raw JSON toggle и reset defaults;
- frontend comparison orchestration с одной user bubble, независимыми сторонами и immutable
  settings metadata.

Deterministic verification 2026-09-03:

- JDK 21 `mvn clean package`: PASS; **24 tests**, 0 failures, 0 errors, 0 skipped.
- Node 22.22.3 `npm test -- --watch=false`: PASS; **11 tests**.
- Angular production build: PASS; output `frontend/dist/frontend`. Осталось non-blocking budget
  warning: component SCSS 5.90 kB при configured budget 4.00 kB.
- FREE compatibility покрывает legacy request без mode, explicit FREE, неизменённый Day 1 DeepSeek
  request и русский safe Markdown frontend path.

Real DeepSeek verification 2026-09-03 на одном Java snippet:

- три CONTROLLED calls с defaults (`600 / 3 / 30 / 30 / 30`) — HTTP PASS 3/3, raw content
  non-empty 3/3, valid JSON 3/3, exact root schema `summary/findings/recommendation` 3/3,
  backend contract validation PASS 3/3; каждый ответ содержал 2 findings;
- customized call (`maxTokens=400`, `maxFindings=1`, word limits 12) — PASS: 1 finding,
  summary 10 слов, maximum reason 9 слов, recommendation 8 слов;
- negative case с tight `maxFindings` — модель вернула лишние findings, backend корректно
  отклонил contract; UI сохранил успешную FREE-сторону, показал controlled error и raw response.

Fresh Chrome browser smoke 2026-09-03:

- FREE single Russian Markdown, CONTROLLED single structured result и raw JSON show/hide — PASS;
- comparison с snapshot `maxTokens=500`, `maxFindings=3`, word limits 20 — обе стороны PASS,
  одна user bubble, desktop side-by-side layout;
- reset вернул `600 / 3 / 30 / 30 / 30`, старая metadata осталась `500 / 3` — PASS;
- independent partial failure, escaped raw output, conversation overflow, scroll-to-latest,
  composer availability и loading states — PASS;
- Ctrl+Enter и Markdown/unsafe HTML/URL sanitization подтверждены automated frontend tests.

Security/publish safety:

- `.env.local`, `target/`, `frontend/node_modules/`, `frontend/dist/`, `frontend/.angular/` и
  `tmp/` исключены; API key не печатался и не находится в tracked/staged files.

Known limitations: settings/history очищаются при refresh; calls не имеют conversation memory;
model contract violations намеренно видны пользователю и не исправляются автоматически.

Engineering conclusion: prompt и API-level JSON Output управляют форматом, но сами по себе не
гарантируют semantic business contract. Поэтому успешный CONTROLLED result требует отдельной
application-side проверки schema, cardinality, enum values и фактических length limits.

## 14. Submission checklist

- [x] Implementation
- [x] Backend/frontend tests and production builds
- [x] Three default and one customized real CONTROLLED verification
- [x] Real browser FREE/CONTROLLED/comparison smoke
- [ ] Demo video
- [ ] Repository/code publication through the approved branch flow

Day 2: **IMPLEMENTATION DONE / VERIFICATION DONE / SUBMISSION PENDING**.
