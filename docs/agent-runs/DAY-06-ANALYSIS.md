# Day 6 — отдельный агент с историей сообщений

Дата: 2026-09-07. Исполнитель: Codex. Статус: **анализ, не реализация**.
Архитектурные предложения ниже — **RECOMMENDED**, не OWNER APPROVED.

**Уточнение 2026-09-10:** план финализирован против того же `developer` / `06355ef`.
Последующая реализация утверждённого плана выполнена на `day_6`; evidence —
[DAY-06.md](DAY-06.md), текущий статус — canonical task §12. Этот анализ сохраняет planning history.
Актуальный контракт — [docs/tasks/DAY-06.md](../tasks/DAY-06.md), дельта — раздел 11 ниже.
Разделы 1–4 сохраняют исходные факты исследования. Разделы 5–10 — историческое предложение
2026-09-07: их persistence/restore, version/turnId, storage reconciliation и открытые варианты
**заменены** разделом 11 и canonical-планом. Они не являются инструкцией к реализации Day 6.

## 1. Требование и границы исследования

Authoritative source — текущая инструкция владельца с требованием Day 6 и уточнением
организатора: агент принимает запрос, обращается к LLM через существующий low-level API,
возвращает ответ в существующий интерфейс. Это отдельная сущность, инкапсулирующая цикл
request/response и владеющая стеком сообщений; накопленные сообщения передаются модели
при последующих обращениях. Цель — инкапсулировать работу предыдущей недели в агент.
LangChain/Spring AI не нужны и запрещены текущим заданием.

Telegram export в этом исследовании не перечитывался: перечисленные требования взяты из
явно предоставленного authoritative текста, дополнительные слова организатору не приписываются.
Обращений к LLM, browser/runtime проверок и запуска тестов не было.

## 2. Repository truth

- Ветка `developer`, HEAD `06355effc56d60b4d43b58c7e274b0ec20622337`.
- В истории видны Day 5 implementation `951e65c`, finalization `4292beb`, workflow `06355ef`.
- До работы уже изменены девять документов: CURRENT-STATE, SESSION_START, DAY-05-REPORT,
  agent-runs/DAY-05 и tasks/DAY-01…05. Staged diff пуст. Эти изменения сохранены без правок.
- Product code не изменён. Ветка Day 6 и canonical tasks/DAY-06.md не создавались.
- CURRENT-STATE/SESSION_START ещё называют Day 6 NOT STARTED: это верно для implementation,
  но текущий analysis уже состоялся. Их актуализация не входит в этот запрос.
- README описывает UI-only history корректно, но не имеет пользовательской секции Day 5;
  это документный пробел, не свидетельство отсутствия реализованного MODELS experiment.
- IDEA MCP доступен: `get_project_modules` подтвердил Java module engineering-review-mentor;
  `search_symbol(q=DeepSeekClient)` подтвердил класс/bean/caller. Выводы ниже подтверждены
  прямым чтением исходников, не только поиском символов.

## 3. Кто чем владеет сейчас — подтверждено кодом

Все Java-пути ниже относительно `app/src/main/java/dev/aiadvent/mentor/`.

| Область | Текущий владелец / evidence |
|---|---|
| Day 1 system prompt | `DeepSeekClient.java:21`, `analyze` → `analyzeWithSystem` |
| Day 2 prompt / semantic constraints | `DeepSeekClient.controlledPrompt`; DTO/defaults/bounds в `ReviewControls.java`; routing в `ReviewController.java:22` |
| Day 3 prompts / two-call orchestration | `ReasoningReviewService.java`, DIRECT_PROMPT/STEP_PROMPT/PROMPT_GENERATOR/EXPERTS_PROMPT, `selfPrompt` |
| Day 4 prompt / allowed temperatures | `TemperatureReviewController.java`, SYSTEM_PROMPT и ALLOWED_TEMPERATURES |
| DeepSeek model / wire parameters | `DeepSeekClient.java:20,113,130`: fixed deepseek-v4-flash, stream=false, thinking.disabled; CONTROLLED добавляет max_tokens/JSON, Day 4 temperature |
| DeepSeek JSON и HTTP | `DeepSeekClient.java:74,113,156`: request building, Authorization, HttpClient.send, parsing/errors |
| Day 5 model allowlist / pricing | `ModelProfile.java`; `ModelReviewController` resolves modelKey, затем вызывает client |
| Day 5 developer prompt / preset / HTTP | `OpenAiResponsesClient.java:21,26,45`: fixed instruction, Responses API, effort none, ceiling 2000, default tier, store=false; tokens/cost parsing |
| DI и credentials DeepSeek | `EngineeringReviewMentorApplication.java:17–24`: singleton HttpClient/DeepSeekClient, key из environment |
| Видимые сообщения / сравнения | `frontend/src/app/app.ts:35` Exchange; `exchanges` signal; client-side orchestration |
| Восстановление UI | `app.ts:282–300`, loadDialogList/activateDialog; experiment и selectors из state.ui |
| Сохранение UI | `app.ts:371–387`: PUT title + state {exchanges, ui}; loading очищается |
| Файлы истории | `DialogStore.java:38–84,113`: UUID, JSON document, timestamps, synchronized операции, temporary file + atomic move с fallback |

Сегодня DeepSeekClient совмещает transport, provider mapping и часть прикладных правил
review. ReasoningReviewService уже показывает небольшой service-level orchestration, но он
не владеет историей и сам по себе не закрывает уточнённый Day 6.

## 4. Фактическая история: UI-only, не LLM memory

Доказательная цепочка:

1. `app.ts:327–335`: `/api/review` получает `{input, mode[, controls]}` — нет dialogId/messages.
2. `app.ts:314–325`: reasoning/temperature получают только input и strategy/temperature.
3. `app.ts:143–159`: models получает только input/modelKey; сравнения захватывают один input.
4. `DeepSeekClient.buildFreeRequestBody` создаёт новый массив ровно из system + current user.
   CONTROLLED делает то же с другим system prompt. Temperature расширяет этот же JSON.
5. `OpenAiResponsesClient.analyze` создаёт developer + current user. Ни previous_response_id,
   ни conversation id, ни накопленных сообщений в запросе нет.
6. Review controllers/clients не зависят от DialogStore. DialogController только сохраняет/
   читает frontend state; он не вызывает LLM.

SELF_PROMPT — не исключение: output первого вызова становится system prompt второго, а user
снова получает точный исходный input. Это связь двух стадий одного анализа, не память прошлых turns.

**Вывод:** все Day 1–5 остаются независимыми запросами. Наличие JSON-диалога и видимых старых
сообщений не означает контекстную беседу. Просто переименовать client/service в Agent недостаточно.

## 5. Минимальная рекомендуемая граница Day 6

Добавить отдельный эксперимент **«Агент»** в существующий chat, не переключать Day 1–5 на
историю молча. Предлагаемый baseline — существующий DeepSeek low-level client и fixed Russian
Markdown; выбор provider/model для Day 6 пока не утверждён владельцем.

```text
Angular / Агент: current dialogId + только новый input
  -> POST /api/dialogs/{id}/agent/messages
  -> AgentController: HTTP validation / status mapping
  -> AgentDialogService: load, serialize turn execution, save
  -> EngineeringReviewAgent: system + prior user/assistant + new user
  -> DeepSeekClient: wire JSON -> HttpClient -> parse response
  -> Agent: append successful user/assistant pair
  -> DialogStore: persist agent state
  -> Angular: show Russian Markdown + existing local UI history
```

| Слой | Ответственность | Не принадлежит слою |
|---|---|---|
| EngineeringReviewAgent | Persona/system instruction, фиксированная политика запроса Day 6, упорядоченный message stack, добавление turn, вызов client, приём ответа, обновление своего state | HTTP serialization и файловая запись |
| Low-level client | Provider endpoint/auth, сериализация переданных сообщений/параметров, HTTP, декодирование ответа/ошибки | Выбор dialogId, чтение UI history, lifecycle агента |
| Controller | Принять id/input, вернуть response/status | Prompt construction, ручное собирание history, прямой вызов LLM |
| AgentDialogService | Привязка к dialogId, загрузить/восстановить agent, исключить одновременные turns, сохранить state | Подменять agent orchestration или редактировать ответы |
| DialogStore | Безопасные filenames, load/update/atomic write, сохранение обеих независимых частей документа | Prompt logic, выбор сообщений для модели |
| Frontend | Input/loading/result, выбор диалога/эксперимента, Markdown, визуальная история | Авторитетная история модели или system-role input от браузера |

Агент должен реально иметь state и метод вроде `reply(input)`, а не быть пустой обёрткой вокруг
`client.analyze(input)`. Singleton service допустим; **один mutable singleton-agent на всех
пользователей/диалоги недопустим**. Не нужны интерфейсы Agent/ProviderFactory, плагины,
tool registry, autonomous loop или SDK framework.

### Low-level extension без переписывания прошлых Days

DeepSeekClient добавить узкий путь получения completion по ordered list типизированных
role/content сообщений. Existing analyze/buildFree/buildControlled/temperature signatures
и wire payload сохранить. Общий HTTP send можно переиспользовать через небольшой overload;
не заставлять multi-turn request притворяться одним `input` ради существующей сигнатуры.
Agent задаёт Day 6 instruction и явную fixed policy; client переводит её в provider fields.
Provider endpoint/key остаются внутри клиента, не в agent state и не во frontend.

Не требуется извлекать все старые prompts из Day 2–5 или делать их стратегиями нового агента:
это увеличит regression scope без требования Challenge. Предыдущая неделя переиспользуется
как low-level integration, UI и persistence, не как обязательная матрица режимов агента.

## 6. Привязка agent state к диалогу

Рекомендация: **одна логическая agent conversation на существующий UUID диалога**.
Agent object можно создавать на каждый запрос из сохранённого state; логическая идентичность
не требует постоянно живущего Java object или дополнительного реестра объектов в памяти.

Минимальная эволюция существующего JSON:

```text
DialogDocument
  id/title/createdAt/updatedAt
  state: { exchanges, ui }             # существующее frontend presentation state
  agentState?: { version, messages }   # новая backend-owned conversation
```

- Agent хранит ordered system/user/assistant stack; system добавляется один раз, не на каждый turn.
  Версия фиксирует policy при создании; новый prompt не должен молча менять старую беседу.
- Отсутствующий agentState в старых файлах = ещё не начатая агентная беседа. Новый диалог = пустая
  память. При первом обращении добавляется system. Restore/restart загружает прежний stack.
- **Никакого импорта старых Day 1–5 exchanges в память**: сравнения содержат несколько ответов,
  generated prompts и разные режимы, у них нет однозначной единственной assistant chronology.
- Agent memory пополняют только successful Day 6 turns. UI-only ошибки, checklist/evaluation,
  model metrics и настройки экспериментов не включаются в messages.
- `DialogStore.update` сейчас полностью заменяет state (строка 70). Поэтому нельзя просто
  положить agent messages внутрь state и ожидать сохранности: `app.ts:377` их не отправляет.
  Новый top-level agentState надо явно сохранять при existing PUT, не принимать от frontend.
  Backend updateAgentState аналогично должен сохранять последнюю UI-часть и metadata.
- GET может возвращать восстановимые successful agent turns, чтобы UI мог согласовать отображение
  после сбоя между server save и frontend PUT. System prompt не нужен в видимых bubbles.
  Использовать sequence/turn id для дедупликации отображения; не выводить второй экземпляр
  того же persisted turn после refresh.
- Хранение остаётся в ignored `docs/local/mentor-dialogs/`, без SQL, новых storage directories
  или frontend-configurable paths. Ключи и HTTP headers не сохраняются.

### Порядок и failures

Backend должен защищать один dialog от двух одновременных turns: UI loading guard не защищает
от второй вкладки. Рекомендуется небольшой per-dialog in-flight guard в service и явный 409
на конкурентный send; не удерживать глобальный DialogStore monitor во время HTTP.
Existing synchronized load/update по отдельности не делают load → LLM → save атомарной операцией.
При сохранении agentState store заново читает текущий документ под своей блокировкой, сохраняя
актуальный UI state. Защита рассчитана на один backend process; distributed locking вне scope.

На provider failure не добавлять фиктивный assistant или пустую пару в каноническую память.
Новый user turn хранить в candidate stack; фиксировать пару только после валидного ответа и
успешной локальной записи. UI показывает ошибку отдельно. При storage failure после LLM сообщить,
что turn не сохранён; не повторять платный вызов автоматически и не обещать exactly-once.
Не включать незавершённые loading state в память.

Накопленный стек отправлять целиком: без скрытой summarization/truncation. Если контекст
перестаёт помещаться, показать явную ошибку и предложить новый диалог; memory compression
и retrieval не являются Day 6. Проверка конкретного provider context limit нужна при реализации,
не повод добавлять tokenizer/framework сейчас.

## 7. Минимальный API / UX sketch (предложение)

`POST /api/dialogs/{id}/agent/messages` принимает `{ "input": "…" }`.
Ответ: `{ "turnId": 2, "analysis": "…" }`; при восстановлении GET dialog содержит сохранённые
turns. ID неизвестен → 404, malformed id/empty input → 400 без LLM; busy dialog → 409;
provider failure → 502, storage failure → 500. Ошибки без headers/keys/provider raw dumps.

Форма `messages`, model/system overrides от клиента не принимается. Payload не содержит
old exchanges: историю агент берёт из backend state. Existing endpoints Day 1–5 не меняются.

В UI новая вкладка «Агент», тот же composer/Ctrl+Enter, loading, safe Markdown, sidebar.
Короткая подпись: «Помнит сообщения агента в этом диалоге». Другие experiments сохраняют
независимость запросов. state.ui запоминает выбранный experiment; switching dialog переключает
и agent memory. Для сброса достаточно «Новый диалог». Сравнение агентов/model selector не нужны.

## 8. Точные затрагиваемые компоненты

Если рекомендации приняты, вероятный минимальный набор:

| Файл | Изменение |
|---|---|
| `app/src/main/java/dev/aiadvent/mentor/EngineeringReviewAgent.java` (новый) | Stateful agent + typed messages/state, reply orchestration |
| `app/src/main/java/dev/aiadvent/mentor/AgentDialogService.java` (новый) | Dialog lifecycle/load/save и in-flight guard |
| `app/src/main/java/dev/aiadvent/mentor/AgentController.java` (новый) | Endpoint/DTO/status mapping |
| `app/src/main/java/dev/aiadvent/mentor/DeepSeekClient.java` | Additive ordered-message API, reusable transport; preserve legacy paths |
| `app/src/main/java/dev/aiadvent/mentor/DialogStore.java` | Optional backend-owned agentState, preserve across UI writes, updateAgentState |
| `frontend/src/app/app.ts` | AGENT experiment/exchange, endpoint, restore/reconciliation |
| `frontend/src/app/app.html` | Agent selection, label и existing Markdown presentation |
| `frontend/src/app/app.scss` | Только если существующих classes недостаточно; не обязательная переработка |
| `app/src/test/java/dev/aiadvent/mentor/EngineeringReviewAgentTest.java` (новый) | Message order, failures, no shared state |
| `app/src/test/java/dev/aiadvent/mentor/AgentControllerTest.java` (новый) | Routing, input/id/concurrent errors, persistence integration |
| `app/src/test/java/dev/aiadvent/mentor/DialogStoreTest.java` | Restart/legacy/merge preservation |
| `app/src/test/java/dev/aiadvent/mentor/MainTest.java` | New ordered wire-message mapping + old Day 1 contract |
| `frontend/src/app/app.spec.ts` | Agent multi-turn UI/restore + existing regression |

Typed records можно оставить вложенными, не создавать файлы ради каждого DTO.
Existing DI bean DeepSeekClient пригоден; `EngineeringReviewMentorApplication` менять не обязательно.
Без изменений переиспользуются ReviewController/ReviewMode/ReviewControls/ControlledReview,
ReasoningReviewController/Service/Strategy, TemperatureReviewController, ModelReviewController,
OpenAiResponsesClient/ModelProfile, Main CLI, model-result.ts, proxy/build dependencies/scripts.
DialogController не обязан менять routes: update сохраняет новый backend-owned state через store.
Старые tests остаются regression evidence; утверждения PASS здесь не обновляются.

При implementation понадобятся canonical DAY-06 task и точечные CURRENT-STATE/SESSION_START/README
изменения: глобальное «history никогда не отправляется» станет «Day 1–5 UI-only; Day 6 agent turns
передаются». Исторические task evidence Day 1–5 не переписывать задним числом.

## 9. План проверки — не запускался

1. Fake low-level client/captured requests: turn 1 = system,user1; turn 2 =
   system,user1,assistant1,user2. Проверить exact content/order и отсутствие повторного system.
2. Два UUID не обмениваются контекстом; новый диалог пустой; restore/re-instantiation продолжает stack.
3. Legacy UI PUT не стирает agentState; agent save не стирает evaluation/exchanges; старый JSON читается.
4. Failed LLM turn не загрязняет stack; следующий successful turn не содержит error/loading.
   Save failure виден; нет automatic retry; concurrent turn блокируется без второго LLM call.
5. Wire JSON содержит полный ordered stack и fixed provider settings; frontend передаёт только input.
6. Frontend: AGENT selection, one send/one response, restore без двойных bubbles, Markdown sanitization,
   изоляция Day 1–5 и old-dialog compatibility. Existing comparison paths не меняются.
7. После owner implementation approval — targeted tests, затем relevant full backend/frontend builds
   и regression. Current test seams: MainTest проверяет request JSON без сети, DialogStoreTest использует
   TempDir/Clock/re-instantiation; frontend tests — HttpTestingController, включая request body assertions.
8. Только по отдельному разрешению runtime: два реальных turns, второй ссылается на уникальную
   информацию первого; request evidence подтверждает history, не полагаться только на правдоподобный ответ.
   Затем restart/restore, новый dialog isolation. Chrome desktop 1440×1000, без mobile.
9. Проверить ignored dialog files и отсутствие secrets в state/logs/Git. Приватный/корпоративный код
   для демонстрации не использовать.

## 10. Развилки, требующие решения перед implementation

1. **Где включить память?** Рекомендация: отдельный «Агент» с памятью только собственных turns.
   Альтернатива — превратить основной FREE chat в агента, сохранив отдельный legacy path; это заметнее
   меняет UX и увеличивает риск регрессии Day 1/2. Requirement не требует менять все experiments.
2. **Provider/model Day 6:** рекомендация existing DeepSeek/deepseek-v4-flash для минимального reuse.
   Альтернатива direct OpenAI потребует multi-turn path другого concrete client и отдельного
   разрешения paid verification. Текущий текст не выбирает provider/model; Day 5 budget не является
   автоматическим разрешением новых calls.

Agent per dialog, optional persisted state, error handling и typed records — рекомендуемые
реализационные детали, не дополнительные owner questionnaires. Requirement о message stack уже
явно задан; уточнять, нужна ли вообще история, не требуется.

**Результат:** существующая архитектура подходит для малого additive increment. Day 6 ещё
не реализован; настоящий недостающий элемент — отдельный stateful agent с backend-owned
упорядоченным стеком и передачей этого стека low-level клиенту. Единственный созданный файл
этого исследования — данный отчёт; без branches/commits/tests/API/browser.

## 11. Финализация по новым уточнениям — 2026-09-10

Источник требований — свежий текст владельца с уточнениями организатора и границами Days 7–9.
Повторного исследования с нуля нет: HEAD не изменился, точечно перепроверены существующие
DeepSeekClient, DialogStore/DialogController, ReviewController с вложенным exception advice,
ReviewControls, TemperatureReviewController, application DI, app.ts/app.html и тестовые seams.
Существующие CURRENT-STATE и Day 5 task подтверждают интеграцию Days 1–5; последний общий daily
датирован 2026-08-19 и не содержит ai-advent/Day 5/Day 6. Общая memory отсутствует.
MCP evidence раздела 2 относится к предыдущему исследованию; в этой сверке использовано чтение файлов.

### Решения по шести вопросам

1. **dialogId → agent подходит.** `DialogStore.create` выдаёт UUID; frontend `currentDialogId`,
   `openDialog` и `activateDialog` уже обеспечивают выбор идентичности. Новый singleton
   `AgentDialogService` держит runtime `ConcurrentHashMap<UUID, EngineeringReviewAgent>`.
   UUID нормализуется до ключа, создание через `computeIfAbsent` атомарно. Один агент на UUID
   живёт до завершения backend process. Не создавать агент заново на каждый HTTP request.
2. **AgentConfig:** immutable record `model`, `systemPrompt`, nullable `temperature`.
   Default: `deepseek-v4-flash`, существующая Day 1 mentor-инструкция, temperature отсутствует
   в wire JSON. Temperature уже поддержана Day 4; разные экземпляры могут иметь разные
   prompt/model/temperature без общей mutable config. Thinking disabled и stream=false остаются
   фиксированными параметрами существующего транспорта. Day 2 controls/JSON, Day 3 стратегии,
   Day 5 ModelProfile/OpenAI presets, credentials и UI selectors не переносятся в AgentConfig.
3. **DialogStore:** только существующая проверка `load(id)` до регистрации агента, без чтения
   `state` для модели. Не менять store, его JSON-схему и тесты. UI archive продолжает сохраняться
   прежним PUT, включая видимые AGENT exchanges; это не сохранение ConversationContext и не
   восстановление памяти. После backend restart первый turn даже старого UUID начинается заново.
4. **API/UI:** один POST `/api/dialogs/{id}/agent/messages`, body `{input}`, response `{analysis}`.
   Отдельный `AGENT` experiment/exchange; existing `free` ResultState/Markdown можно переиспользовать.
   Вкладка «Агент», существующие composer/sidebar, пояснение об утрате памяти при restart.
   Явно ограничить temperature-only ветки `else` и добавить AGENT rendering до controlled fallback.
   GET history, turnId, reconciliation и UI config editor не нужны.
5. **Файлы:** пять новых production Java-файлов (AgentConfig, ConversationContext,
   EngineeringReviewAgent, AgentDialogService, AgentController); один изменяемый DeepSeekClient;
   app.ts/app.html; три новых Java test-файла, расширение MainTest и app.spec.ts.
   Полные пути и назначение — canonical §7. Existing DI пригоден без изменения application class.
6. **Преждевременно:** persisted agentState/version, restore API, sequence IDs, storage merge,
   repository/provider/agent interfaces, factories, context strategy hierarchy, eviction/TTL,
   summary/tokenizer/token budgets, multi-provider dispatch и превращение всех экспериментов
   в одну agent framework. Достаточны конкретные классы и существующий low-level client.

### Порядок turn и изоляция

Агент владеет config и своим ConversationContext; тот содержит ordered typed role/content
messages, system ровно один раз. `reply(input)` строит snapshot всей истории + нового user,
передаёт его DeepSeekClient, добавляет user/assistant в свой context только после успешного ответа.
Ошибка не меняет committed history и не вызывает retry. Per-agent `tryLock` на весь turn,
busy → 409; разные агенты не разделяют lock/context. Не держать monitor DialogStore во время HTTP.
Provider/send validation переиспользуется; ordered-message transport не получает фиктивный input.

### Что изменилось относительно первого анализа

| Прежнее предложение | Финальный план |
|---|---|
| Создание агента из JSON при каждом запросе | Реальный stateful runtime instance в registry |
| Backend-owned agentState + version в DialogDocument | ConversationContext только в памяти агента |
| Load → LLM → save, restore после restart | Runtime reply; после restart новый пустой контекст |
| turnId и согласование GET/PUT | Ответ только analysis; существующий UI archive отдельно |
| DialogStore changes + persistence tests | Store без изменений; проверка отсутствия restore в agent tests |
| Открытый выбор режима/provider | Отдельный AGENT + existing DeepSeek — минимальная реализация по текущему направлению |

Day 7 добавит JSON/SQLite save/restore. Day 8 — измерение контекста и эксперименты с лимитами.
Day 9 — отдельный summary + последние N raw messages. Day 6 отправляет полный стек без
обрезки, compression или измерений; новые интерфейсы под будущие дни не создаются.

Проверки и точные команды владельцу — canonical §§8–10. В этом проходе тесты/build/runtime,
LLM API и браузер не запускались. Изменены только этот анализ и новый canonical task;
девять ранее изменённых документов оставлены без правок. Ветка `day_6`, commits и push не создавались.
План готов для реализации по отдельной команде; существенных открытых вопросов **нет**.

## 12. Последующая реализация — ссылка на evidence

Владелец утвердил план и разрешил branch creation/implementation/deterministic verification.
Результат записан отдельно в [DAY-06.md](DAY-06.md), чтобы не заменять план отчётом задним числом.
Единственное уточнение config: nullable `maxTokens` переиспользует существующий DeepSeek
`max_tokens` из Day 2; default null, без новых sampling features. Backend 58/58 и frontend 17/17,
оба builds PASS. Real API/browser/video не выполнялись. Commit/push не выполнялись.
