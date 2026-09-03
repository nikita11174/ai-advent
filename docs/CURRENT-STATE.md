# AI Advent Challenge 9 — Current State

Единственный operational source of truth. Repository/code имеет приоритет, если документ
расходится с working tree.

| Поле | Текущее состояние |
|---|---|
| Challenge | AI Advent Challenge 9 |
| Product direction | Engineering Review Mentor |
| Current milestone | Week 1 / Day 2 |
| Current executor | Codex |
| Status | DAY_2_READY_FOR_IMPLEMENTATION |
| Day 1 implementation | DONE |
| Day 1 verification | DONE |
| Day 1 submission | PENDING |
| Day 2 planning | DONE / READY_FOR_IMPLEMENTATION |
| Day 2 implementation | NOT_STARTED |
| Day 2 verification | NOT_STARTED |
| Day 2 submission | NOT_STARTED |
| Next action | Реализовать `docs/tasks/DAY-02.md` в ветке `day_2` |
| Blockers | Для Day 2 нет; отдельный Day 1 demo video ещё не записан |
| Submission evidence | Repository/code link: DONE — `https://github.com/nikita11174/ai-advent`; demo video: PENDING |

Текущая ветка: `day_2`, создана от актуальной `developer`. Branch workflow: `main` (stable) ←
`developer` (integration) ← `day_N` (Challenge Day work).

## Active Day 2 plan

Day 2 сравнивает один exact developer input в режимах FREE и CONTROLLED:

- FREE сохраняет Day 1 Russian Markdown response;
- CONTROLLED использует DeepSeek JSON Output и компактный фиксированный review shape; UI
  отображает summary/findings/recommendation, а `Показать JSON` раскрывает exact raw response;
- controlled settings имеют defaults (`maxTokens=600`, `maxFindings=3`, три word limits по 30 и
  default termination instruction), редактируются только в page state и передаются backend;
- server bounds: `maxTokens 100..2000`, `maxFindings 0..10`, word limits `1..100`, termination
  instruction `1..1000` символов после trim;
- JSON format fixed/read-only; `Сбросить настройки` восстанавливает defaults;
- comparison выполняет frontend двумя независимыми requests по одному input/settings snapshot,
  показывает одно user message и immutable metadata actual controls, без backend `COMPARE` mode
  и без conversation memory;
- invalid/truncated/empty controlled output не repair/retry и не fallback в Markdown; доступный
  raw response остаётся inspectable.

Официальные DeepSeek Chat Completions и JSON Output docs подтверждают `response_format`,
`max_tokens`, `stop` и `finish_reason`. Блокирующих решений нет; полный acceptance и план — в
`docs/tasks/DAY-02.md`.

Open product decisions: **NONE**. Day 2: **READY_FOR_IMPLEMENTATION**.

## Current architecture

```text
Browser :4200
  -> Angular 22
  -> /api dev proxy
  -> Spring Boot :18080
  -> POST /api/review
  -> DeepSeekClient
  -> deepseek-v4-flash
  -> Russian Markdown response
```

Каждый request содержит только текущий `{ "input": "..." }`; визуальная история браузера не
отправляется модели. Текущий локальный runtime использует frontend `:4201`, потому что `:4200`
занят dev server другого sandbox-проекта. Стандартная команда `npm start` использует `:4200`,
когда порт свободен.

## Baseline

- Java 21; Maven; Spring Boot 3.5.5.
- Node 22.22.3; npm 10.9.8.
- Angular Core 22.1.4; Angular CLI 22.1.7; Angular Material 22.1.5; TypeScript 6.0.3.
- Official DeepSeek API, model `deepseek-v4-flash`, explicit REST through JDK `HttpClient`.
- `DEEPSEEK_API_KEY` доступен только backend environment.
- Repository-root `.env.local` игнорируется Git и не должен читаться/печататься в отчётах.

## Current UI behavior

- Русский chat-style interface: user messages справа, более широкие Mentor responses слева.
- Session-only visual history; refresh может её очистить.
- Предыдущие сообщения не отправляются DeepSeek; LLM requests независимы.
- Sticky multiline composer; Enter создаёт строку, Ctrl+Enter отправляет.
- User bubble появляется сразу, затем inline bubble `Ментор анализирует...` заменяется ответом.
- Mentor Markdown обрабатывается `marked` и вставляется через Angular `[innerHTML]`.
- Raw HTML экранируется; действует штатная Angular sanitization; trust bypass отсутствует.
- Soft auto-scroll работает только около нижнего края; при чтении истории появляется
  `К последнему сообщению`.
- Conversation и composer используют одну центрированную колонку `960px`; empty state компактно
  расположен непосредственно над composer и полностью исчезает после первого submit.

## Verification evidence

### Verified before latest UI changes

- Предыдущий backend package: PASS, 7/7 tests.
- Предыдущий frontend build: PASS, 3/3 tests.
- Предыдущий real browser → backend → DeepSeek smoke: PASS.

### Latest chat / Markdown / Russian changes — verified 2026-09-03

- JDK 21 `mvn clean package`: PASS; 7 tests, 0 failures, 0 errors, 0 skipped.
- Node 22.22.3 `npm test -- --watch=false`: PASS; 6/6 tests.
- Angular production build: PASS; output `frontend/dist/frontend`.
- Tests подтверждают Russian shell, user/loading/result flow, Markdown headings/list/inline code,
  отсутствие исполняемого raw HTML/event handlers и unsafe `javascript:` URL, порядок двух
  exchanges, Ctrl+Enter и русскую network error.
- Fresh Chrome web smoke: PASS. Два независимых запроса прошли через frontend proxy → Spring
  Boot → DeepSeek; получены два непустых русских ответа, в DOM присутствовали Markdown headings
  и fenced code blocks, предыдущий exchange остался видимым.
- Scroll smoke: PASS. После ограничения chat на viewport появился независимый overflow; ручной
  scroll вверх не был перетянут вниз, кнопка возврата появилась и вернула к последнему сообщению.
- Backend `:18080` и AI Advent frontend `:4201` оставлены запущенными для manual review.

### Day 1 chat layout polish — verified 2026-09-03

- Frontend tests после polish: PASS, 6/6; Angular production build: PASS.
- Backend не менялся и повторно не собирался; актуальным остаётся предыдущий PASS 7/7.
- Fresh Chrome smoke на `:4201`: compact empty state и composer видимы сразу; после submit empty
  state исчезает, user bubble появляется справа, inline loading bubble — слева.
- Два свежих независимых `/api/review` request получили HTTP 200 и непустые русские Markdown
  responses; headings и fenced code blocks отрисованы, оба exchanges остались в правильном порядке.
- Conversation overflow, удержание нижнего края после большого Markdown response, scroll вверх
  без принудительного возврата и кнопка `К последнему сообщению` — PASS.
- В ходе smoke устранены два UI blocker: layout ограничен viewport, а programmatic scroll
  выполняется после render frame и не конфликтует с пользовательской прокруткой.

## Scope boundary

Не выходить за scope `docs/tasks/DAY-02.md`: не добавлять persistence, conversation backend state,
user-first learning loop, scoring, skill tracking, heuristics, RAG, MCP product integration,
agents, database, authentication или Day 3 reasoning strategies.
