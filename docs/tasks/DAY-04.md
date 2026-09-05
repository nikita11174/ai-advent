# Day 4 — Temperature

Статус: **IMPLEMENTED / VERIFIED / OWNER UX ACCEPTED; SUBMISSION PENDING**

## 1. Original challenge requirement

Один и тот же запрос выполнить с `temperature = 0`, `0.7`, `1.2`. Сравнить точность,
креативность и разнообразие; сформулировать, для каких задач подходит каждая настройка.
Результат: примеры ответов и выводы. Submission: видео + код.

## 2. Organizer/chat evidence

- Official task: Telegram export message `1642`.
- Письменное задание редактировалось после видео (`1759`), поэтому текущий текст с `1.2` —
  canonical requirement.
- Автоматический выбор temperature через дополнительный classifier/model обсуждался как возможный
  two-chain approach (`1796`), но это не требование Day 4 и увеличивает scope.

## 3. Product increment

Отдельный эксперимент `Температура` в Engineering Review Mentor. Один PaymentReceived review
анализируется с тремя temperature при неизменных model, exact input, system prompt, presentation,
thinking mode и остальных sampling parameters.

## 4. Benchmark and fixed prompt

Используется exact PaymentReceived snippet и reference checklist из Day 3. System prompt просит
по-русски в Markdown выделить реальные engineering risks, practical reliability improvements и
отделить подтверждённые проблемы от assumptions. Reference checklist модели не передаётся.

## 5. API and experiment topology

```http
POST /api/temperature-review
{"input":"exact snapshot","temperature":0.7}
```

```json
{"temperature":0.7,"analysis":"Russian Markdown"}
```

- Допустимые temperature: `0`, `0.7`, `1.2`.
- Single selected temperature: 1 DeepSeek call.
- Compare all: три независимых frontend calls; backend compare endpoint не добавляется.
- DeepSeek request содержит `temperature`, `thinking.type=disabled`, current model и `stream=false`.
- `top_p`, penalties, seed и другие sampling controls не задаются и потому одинаково отсутствуют.

## 6. UX and persistence

- Experiment tabs: `Формат ответа`, `Стратегия анализа`, `Температура`.
- Selector: `0`, `0.7`, `1.2`.
- Actions: `Проанализировать`, `Сравнить температуры`.
- Compare result: три independently loading/error/result cards; адаптивные 3/2/1 columns по
  доступной ширине. Partial failure не скрывает successful cards.
- Exchange сохраняет immutable input, temperature/results и human observations в существующем
  local dialog store. Persisted dialog — UI history, не LLM conversation memory.
- `state.ui` сохраняет active experiment, output mode, reasoning strategy и selected temperature
  только как presentation state. Эти данные не добавляются к model requests.
- Sidebar скрываемый при ширине ≤900 px; composer и jump-to-last не перекрываются.
- Общий вывод редактируется четырьмя полями: «Точность», «Креативность», «Разнообразие»,
  «Для каких задач подходит», с одной кнопкой сохранения. Старый строковый вывод сохраняется
  целиком в поле «Точность»; автоматического смыслового разделения нет.

## 7. Evaluation

Для каждого result пользователь сохраняет human-readable observations:

- accuracy: expected found, expected missed, questionable claims;
- creativity: useful alternative remedies и novel defensible ideas;
- diversity: meaningful differences между independent generations;
- общий conclusion и suitable task types.

Fake numeric scores и authoritative LLM judge не используются. Выводы относятся только к
зафиксированному experiment evidence.

## 8. Acceptance criteria

1. Exact input независимо уходит с temperature `0`, `0.7`, `1.2`.
2. Между calls намеренно меняется только temperature; thinking остаётся disabled.
3. Selected analysis и three-way comparison доступны в UI; partial failure изолирован.
4. Русский safe Markdown и существующий chat/scroll/composer behavior сохраняются.
5. Day 4 snapshots и human conclusions восстанавливаются из local dialog JSON.
6. Старые dialog messages не отправляются модели.
7. Backend/frontend tests и builds проходят; Day 1–3 regressions проходят.
8. Выполнены 3 independent runs на каждую temperature — 9 real calls — и записаны фактические
   accuracy/creativity/diversity conclusions.
9. Secret и local evidence не попадают в Git.
10. Submission содержит video и repository/code link.

## 9. Non-goals

- temperature × FREE/CONTROLLED/reasoning matrix;
- automatic temperature classifier или second model;
- generic provider/model framework и Day 5;
- LLM-as-a-judge, fake scores или guaranteed deterministic output at temperature 0;
- conversation memory, database, auth, cloud persistence;
- изменение Day 1–3 contracts.

## 10. Open decisions

Open product decisions: **NONE**. Implementation blockers: **NONE**.

## 11. Implementation and verification evidence

- Backend: `POST /api/temperature-review`; передаёт approved temperature, fixed Day 4 system
  prompt, exact input, `stream=false` и `thinking.type=disabled`. Остальные sampling controls не
  добавляются.
- Implementation checkpoint: `0ac3ca7b7e05b18570ba4dc438d3fc8b65768820`.
- Frontend: отдельный experiment `Температура`, selected run и independent three-card comparison.
  Human observations и conclusion входят в persisted exchange.
- Backend `mvn clean package`: PASS, 36 tests, failures/errors/skipped = `0/0/0`.
- Initial implementation frontend: PASS, 11 tests; production build PASS. Component-style warning остаётся ниже `9 kB`
  error budget (`8.16 kB`). IntelliJ project build: PASS, problems = 0.
- 9-call harness: PASS для трёх independent runs на каждой temperature; все ответы non-empty и
  endpoint возвращал requested temperature. Raw evidence:
  `docs/local/agent-sessions/day4-temperature-20260905-100605.json` (ignored).
- Accuracy: все девять ответов стабильно нашли idempotency и transaction/external-side-effect
  boundary. `0` наиболее последовательно держался основных рисков; в одном из трёх ответов `0.7`
  concurrency/race был выражен лишь косвенно. `1.2` давал широкое покрытие, но чаще добавлял
  недоказанные детали.
- Creativity: `0.7` и `1.2` чаще предлагали разные defensible remediation options (outbox,
  after-commit event, unique event key, locking). У `1.2` вместе с этим выросла доля speculative
  claims; они не засчитаны как полезная креативность.
- Diversity: `0` дал наиболее похожие reviews; `0.7` — умеренную вариативность; `1.2` —
  максимальную вариативность формулировок и дополнительных идей.
- Вывод для этого benchmark: `0` лучше для repeatable review по known invariants; `0.7` — наиболее
  сбалансирован для exploratory review; `1.2` полезен для controlled brainstorming с последующей
  human-проверкой предположений.
- Web path `Angular :4201 -> proxy -> Spring :18080 -> DeepSeek`: PASS. Chrome comparison:
  3 cards, 3 results, 0 errors, 1 user message; narrow viewport stacks to one column.
- Dialog create/save/load и restore после backend restart: PASS. Dialog history не включается в
  последующие model requests.
- Day 1–3 regressions покрыты полными backend/frontend suites; existing FREE, CONTROLLED,
  reasoning и persistence tests прошли.
- Initial owner visual acceptance was pending; the accepted UX checkpoint below supersedes it.

### Accepted UX/responsive checkpoint — 2026-09-05

- Commit: `aacbbf32701a5107b6afb81178d637cafb6d00a4` — `Polish AI Advent Day 4 UX`.
  Owner accepted the UX/responsive implementation for commit. No backend/API/DeepSeek changes.
- Latest frontend verification: `npm test -- --watch=false` — 12/12 PASS;
  `npm run build` — PASS. CSS duplication removed; error budget retained at 9 kB, stylesheet
  reported as 9.00 kB (rounded). Only the existing non-blocking 4 kB style warning remains.
  Backend tests were not rerun for CSS/presentation-only changes; 36-test baseline above remains.
- Chrome MCP: 1440×1000 — 3 comparison columns, visible sidebar; 1024×768 — 2 columns,
  visible sidebar; 768×1024 — 2 columns, collapsible sidebar; 500×844 — 1 column, hidden sidebar.
  All PASS for selected result, comparison, selector, evaluation fields, composer, benchmark
  action and scrolling/jump-to-last. Narrow-column and long-inline-code overflow defects fixed.
- Saved Temperature experiment, three comparison results and all four evaluation fields restore
  after refresh. Existing legacy conclusion text is preserved. History remains independent of
  subsequent LLM calls.
- Targeted shared-shell regression PASS: dialog navigation, composer, scroll/jump-to-last,
  Day 1 real FREE Russian Markdown response, Day 2 format controls and Day 3 strategy controls.
  No repeat of the 9-call experiment was needed for this presentation-only checkpoint.
- Screenshots (ignored): `docs/local/screenshots/day-04-after-ux/`:
  `01-desktop-temperature-screen.png`, `02-compare-all.png`, `03-human-evaluation-fields.png`,
  `04-mobile-layout.png`, `05-restored-temperature-dialog.png`,
  `06-small-laptop-1024x768.png`, `07-tablet-768x1024.png`.
- Publish safety: screenshots, dialog/session data and `.env.local` ignored; generated/local
  artifacts not tracked. Commit contains only frontend presentation/tests and `.gitignore`.

## 12. Submission status

- [x] implementation
- [x] automated tests/builds
- [x] 9-call real experiment
- [x] owner UX/responsive acceptance
- [ ] demo video
- [ ] repository/code publication

DAY 4 IMPLEMENTATION = **DONE**

DAY 4 VERIFICATION = **DONE**

DAY 4 SUBMISSION = **PENDING**
