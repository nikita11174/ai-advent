# Day 1 — Первый запрос к LLM через API

Статус: **SUBMISSION_PENDING**

- Day 1 implementation: **DONE**
- Day 1 verification: **DONE**
- Day 1 submission: **PENDING**
Product direction: **Engineering Review Mentor**

## 1. Original challenge requirement

> День 1. Первый запрос к LLM через API
>
> Напишите минимальный код, который:
> - отправляет запрос в LLM через API;
> - получает ответ;
> - выводит его в консоль или простой интерфейс (CLI / Web).
>
> Результат: код, который отправляет запрос в LLM через API и получает ответ.
> Формат: видео + код.

## 2. Goal в контексте Engineering Review Mentor

Создать первый минимальный web vertical slice: разработчик вводит небольшой Java code snippet или
инженерный вопрос в Angular UI, Spring Boot отправляет его cloud LLM через REST API, а текстовый
инженерный анализ отображается в браузере.

Это первый технический baseline и domain framing будущего Mentor, а не полноценная система code
review.

Challenge goal: application code вызывает cloud LLM API, получает ответ и показывает его через
пригодный для демонстрации интерфейс.

## 3. Acceptance criteria

1. Пользователь может вести локальную chat-сессию в Angular 22 UI: новые пары запрос/ответ
   добавляются вниз, а предыдущие остаются видимыми до обновления страницы.
2. Angular отправляет `POST /api/review` с `{ "input": "..." }` через dev proxy в Spring Boot.
3. Spring Boot через reusable `DeepSeekClient` и `java.net.http.HttpClient` отправляет запрос в
   официальный DeepSeek Chat Completions REST API.
4. DeepSeek request использует `POST https://api.deepseek.com/chat/completions`, model
   `deepseek-v4-flash`, `stream: false` и `thinking: {"type": "disabled"}`.
5. Messages содержат заданный system message и raw developer input как user message.
6. Backend извлекает `choices[0].message.content` и возвращает `{ "analysis": "..." }`.
7. Русскоязычный UI различает выровненные вправо запросы и более широкие ответы ментора слева,
   безопасно отображает обычный Markdown через штатную санитизацию Angular, показывает inline
   loading/error state и использует sticky multiline composer.
8. Auto-scroll следует за новыми сообщениями только около нижнего края; при чтении истории
   доступна кнопка перехода к последнему сообщению.
9. Каждый запрос независим: frontend не отправляет DeepSeek историю предыдущих сообщений.
10. `DEEPSEEK_API_KEY` доступен только backend environment, не Angular и не Git.
11. Backend/frontend builds и tests, а также реальный browser → proxy → backend → DeepSeek → UI
   flow подтверждены.
12. Submission содержит demo video и ссылку на repository/code.

## 4. Explicit non-goals

- полноценный code review;
- structured findings или JSON schema;
- persistent conversations и отправка visual chat history модели;
- сравнение, scoring или user-first review workflow;
- skill tracking и хранение engineering heuristics;
- database, persistence или authentication;
- RAG, MCP product integration, agents или local LLM;
- функциональность и архитектура будущих Challenge Days.

## 5. Minimal product increment

```text
developer input: Java snippet or engineering question
  -> Angular 22 web chat
  -> Spring Boot /api/review
  -> DeepSeek REST API
  -> Russian Markdown engineering analysis
  -> browser
```

Реализация содержит только необходимый API call, минимальное формирование запроса, извлечение
текста ответа и вывод. Product increment полезен сам по себе как простейший engineering-analysis
entrypoint и не предполагает заранее следующую архитектуру.

## 6. Proposed runtime/demo scenario

1. До начала записи настроить `.env.local`; значение ключа не показывать.
2. Запустить `.\scripts\run-backend.ps1`, затем в `frontend` выполнить `npm start`.
3. Открыть `http://localhost:4200` и вставить этот snippet:

   ```java
   int average(List<Integer> values) {
       return values.stream().mapToInt(Integer::intValue).sum() / values.size();
   }
   ```

4. Нажать `Проанализировать`, показать loading bubble, запрос и непустой Markdown-ответ ментора.
5. Завершить видео показом исходников/README либо repository URL; API key в кадр не попадает.

## 7. Verification criteria

- Backend и frontend команды запуска завершаются успешно.
- Внешний REST request действительно уходит выбранному LLM provider, а response успешно
  принимается приложением.
- Request соответствует зафиксированному endpoint/model/messages/non-thinking/non-stream
  контракту.
- Browser UI содержит непустой Mentor response, относящийся к переданному snippet/question.
- Credential отсутствует в исходниках, tracked-файлах и записываемом demo output.
- Повторный запуск по документированной инструкции не требует изменения кода.
- Проверка scope подтверждает отсутствие компонентов из Explicit non-goals.

## 8. Submission evidence

- **Web demo video:** короткая запись UI, input, loading state и полученного ответа без показа
  секрета.
- **Repository/code link:** `https://github.com/nikita11174/ai-advent`.

Текущий код опубликован во всех трёх согласованных ветках GitHub. Web demo video ещё не записано.

Day 1 реализован в `day_1`, созданной от integration branch `developer`; завершённый increment
продвигается в stable branch `main`.

## 9. Resolved implementation decisions

| Decision | Resolution |
|---|---|
| Runtime / build | Java 21 / Maven / Spring Boot 3.5.5; Node 22.22.3 / Angular 22.1.x |
| Provider | Official DeepSeek API |
| API | OpenAI-compatible Chat Completions: `POST https://api.deepseek.com/chat/completions` |
| Model | `deepseek-v4-flash`; legacy `deepseek-chat` and `deepseek-reasoner` are forbidden |
| Mode | Non-thinking: `"thinking": {"type": "disabled"}`; non-streaming: `"stream": false` |
| HTTP / JSON | JDK `java.net.http.HttpClient` / Jackson |
| Authentication | `Authorization: Bearer ...` from `DEEPSEEK_API_KEY` |
| Input / output | Angular multiline textarea / `{ "analysis": "choices[0].message.content" }` |
| System message | Минимальный English prompt просит ясно и кратко назвать инженерные риски, ответить по-русски и использовать Markdown, когда он улучшает читаемость |
| User message | Raw developer input from the web textarea; CLI uses raw STDIN |

DeepSeek REST interaction остаётся явным и понятным. Не использовать OpenAI Java SDK,
third-party DeepSeek SDK или provider abstraction.

**Remaining open decisions:** нет. Имена Java package/class и точные Maven coordinates — локальные
implementation details, которые выбираются минимально и не требуют отдельного product decision.

## 10. Implementation evidence

Реализовано:

- Java 21 CLI с multiline STDIN;
- явный DeepSeek REST request через `java.net.http.HttpClient`;
- Jackson request/response mapping без SDK и framework;
- требуемые ошибки для credential, input, HTTP, response parsing и transport;
- local-only `.env.local` и PowerShell launcher без вывода ключа;
- deterministic unit tests request/response behavior без real API calls;
- Spring Boot `POST /api/review`, использующий тот же `DeepSeekClient`, что и сохранённый CLI;
- Angular 22 standalone chat UI с Material, session-only visual history, sticky composer,
  loading/error bubbles, soft auto-scroll и `/api` dev proxy;
- русский DeepSeek response и безопасное Markdown-представление через `marked` + штатную Angular
  sanitization без trust bypass.

Команды проверки:

```powershell
mvn -q clean package
git check-ignore -v .env.local
```

Package, unit tests и реальный DeepSeek API call проверены после настройки локального ключа.

## 11. Runtime verification

- JDK 21 `mvn clean package`: PASS.
- Unit tests: 4 run, 0 failures, 0 errors, 0 skipped — PASS.
- Реальный `deepseek-v4-flash` request через STDIN → Java CLI → DeepSeek → console: PASS.
- Process exit code: `0`; response содержал непустой анализ переданного Java snippet.
- Credential не выводился; `.env.local` подтверждён как ignored.
- IntelliJ IDEA MCP: AVAILABLE/USABLE для этого проекта; подтверждены Java module discovery,
  Git status и inspections обоих Java-файлов. Это local tooling capability, не часть продукта.
- Fresh Spring Boot 3.5.5 `mvn clean package`: PASS; 7 tests, 0 failures, 0 errors, 0 skipped.
- Fresh Angular 22.1.x tests: PASS, 6/6; production build: PASS.
- Frontend tests покрывают Russian shell, user/loading/result flow, Markdown presentation,
  unsafe HTML/event-handler/URL sanitization, порядок нескольких exchanges и Ctrl+Enter.
- Fresh real browser web path: PASS для двух независимых запросов через Angular proxy → Spring
  Boot → DeepSeek. Оба ответа непустые, русские, с Markdown headings/code blocks; первая пара
  сообщений сохранилась над второй.
- Viewport/scroll smoke: PASS; conversation scroll независим, чтение старых сообщений не
  перетягивается вниз, `К последнему сообщению` возвращает к нижнему краю.
- Final chat layout polish: PASS; 6/6 frontend tests и production build. Empty state стал
  компактным и связанным с composer, conversation/composer используют общую колонку, header
  читаемее, user/Mentor bubbles компактнее, фон визуально отступает перед сообщениями.
- Fresh final browser smoke на `:4201`: два `/api/review` request — HTTP 200; loading, русский
  Markdown, порядок exchanges, overflow, удержание низа после ответа и ручной scroll/return — PASS.
- Repository/code опубликован: `https://github.com/nikita11174/ai-advent`.
- Web demo video ещё не подготовлено.

## 12. Submission checklist

- [x] Implementation
- [x] Current build/tests after latest UI changes
- [x] Current real web smoke after latest UI changes
- [ ] Demo video
- [x] Repository/code link
