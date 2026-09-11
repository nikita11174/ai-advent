# Day 6 — фактический отчёт реализации

Дата: 2026-09-10. Исполнитель: Codex. Implementation и deterministic verification **DONE**.
Real API **PASS** (4 API + 1 UI call); UI **PASS 1440×1000**; **TECHNICALLY COMPLETE**, video **PENDING**.
Актуальный runtime checkpoint — ниже; остальные разделы сохраняют историю implementation-прогона.

## Runtime checkpoint — 2026-09-10, 23:52:14–23:52:46 +03:00

По последующей авторизации владельца выполнены A1 → A2 → B1 → A3 через реальный backend :18080.
A1 принял правило идемпотентности; A2 вспомнил его; B1 не знал прежнего приоритета;
A3 назвал двойное зачисление при повторном PaymentReceived. **4/4 PASS**, retries отсутствуют.
DeepSeek endpoint `https://api.deepseek.com/chat/completions`, model `deepseek-v4-flash`,
четыре HTTP 200 подтверждены debugger trace после реального HTTP send.
Фактические requestBody содержат 2/4/2/6 сообщений; предыдущие пары A точно совпадают с
полученными ответами, B не содержит A. Сценарий полностью приведён в task §13.
Evidence без credentials/headers: ignored `docs/local/agent-sessions/day6-api-verification/`.

Backend доступен; frontend localhost:4201 недоступен из shell и Chrome (ERR_CONNECTION_REFUSED),
IPv6 shell probe отклонён окружением. Chrome viewport 1440×1000; UI acceptance не выполнен,
UI defect не установлен. Browser/process repair не выполнялся. Product code не менялся.
Временные debugger logpoints/attach/config убраны; пользовательские breakpoints сохранены.
Следующий шаг — отдельно проверить desktop Angular UI после восстановления доступа к :4201.
Общий Day 6 completion и video не подтверждены; Days 7–9 вне scope.
Canonical contract — [DAY-06 task](../tasks/DAY-06.md), исходный
[анализ](DAY-06-ANALYSIS.md) сохранён как planning evidence.

## Авторизация и база

Владелец явно утвердил план, разрешил создать `day_6` от текущего `developer`, реализовать код
и выполнить deterministic local tests/build. До кода проверено: developer = HEAD =
`06355effc56d60b4d43b58c7e274b0ec20622337`, product diff и staged diff пусты.
`git switch -c day_6 developer` выполнен; merge-base и текущий HEAD равны исходному developer.
Commit/push/merge не выполнялись; ветка developer не сдвигалась.

На старте уже изменены CURRENT-STATE, SESSION_START, DAY-05-REPORT, agent-runs/DAY-05,
tasks/DAY-01…05; Day 6 analysis/task были untracked. Эти изменения не откатывались.
В CURRENT-STATE/SESSION_START добавлена текущая информация Day 6 поверх существующей;
остальные семь pre-existing modified документов не редактировались.

## Что реализовано

`POST /api/dialogs/{UUID}/agent/messages {input}` → AgentController → AgentDialogService →
EngineeringReviewAgent.reply → DeepSeekClient.complete → существующий HttpClient/DeepSeek API.
В проверках внешняя граница заменена mock, реальных LLM calls нет.

- AgentConfig: immutable model/systemPrompt/temperature/maxTokens. Defaults — existing Day 1
  model и exact mentor instruction; optional параметры не включаются в JSON без значения.
  Existing temperature values 0/0.7/1.2 и maxTokens bounds 100…2000. Thinking disabled и
  stream=false сохранены. Нет top_p/top_k, config UI или multi-provider dispatch.
- ConversationContext: system + ordered user/assistant messages, immutable request snapshots.
  EngineeringReviewAgent единолично владеет config/context и полным циклом turn.
- Успех фиксирует user/assistant только после client response. Transport/malformed/empty-content
  failure оставляет context неизменным. Следующий запрос не содержит failed turn. Retry нет.
- Per-agent ReentrantLock.tryLock исключает пересечение turns; занятый агент → 409.
  Lock освобождается в finally; другие экземпляры отвечают независимо.
- AgentDialogService: ConcurrentHashMap<UUID, EngineeringReviewAgent>, atomic computeIfAbsent.
  DialogStore.load проверяет существование при первом lookup; история JSON не импортируется.
  Fresh service начинает с system/user даже для старого UUID. Store не сохраняет agent context.
- Angular: отдельная вкладка AGENT, input-only POST, existing free ResultState/Markdown,
  sidebar/new dialog. UI archive сохраняется прежним механизмом и не восстанавливает LLM memory.
  Текст вкладки объясняет потерю памяти при backend restart. Comparisons/controls Days 1–5 отделены.

Фактические AS-IS/TO-BE — canonical task §§4/12. Новые storage/context-strategy/provider interfaces,
очереди, executors, persistence, metrics, summary, trimming и dependency changes отсутствуют.

## Файлы

Production prefix: `app/src/main/java/dev/aiadvent/mentor/`.

- Новые: AgentConfig.java, ConversationContext.java, EngineeringReviewAgent.java,
  AgentDialogService.java, AgentController.java.
- Изменён DeepSeekClient.java: additive ordered-message JSON/completion и reuse send.
- Frontend: frontend/src/app/app.ts, app.html.

Test prefix: `app/src/test/java/dev/aiadvent/mentor/`.

- Новые: EngineeringReviewAgentTest.java, AgentDialogServiceTest.java, AgentControllerTest.java.
- Дополнен MainTest.java; frontend/src/app/app.spec.ts.

Документы: README.md, docs/CURRENT-STATE.md, docs/SESSION_START.md, docs/tasks/DAY-06.md,
docs/agent-runs/DAY-06-ANALYSIS.md и этот отчёт.
DialogStore, DialogController, application DI, остальные Day 1–5 классы/тесты, CSS, dependencies,
scripts и JSON schema не изменены.

## Verification

| Команда | Результат |
|---|---|
| `mvn clean package` — первый прогон | 58 tests, 1 failure в новом concurrency test; исправлен test barrier, production без изменений |
| `mvn package` — финальный прогон | 58 tests, failures/errors/skipped = 0; BUILD SUCCESS; 14.142 s; executable Spring Boot JAR создан |
| `npm test -- --watch=false` | 17 tests PASS, 1 file; Vitest 4.1.11; test phase 24.26 s |
| `npm run build` | PASS, 7.519 s; initial 492.23 kB, estimated transfer 111.56 kB |

Frontend warning: неизменённый app.scss 9.00 kB превышает warning budget 4 kB; error budget
не нарушен. CSS/budgets не менялись. Backend reports: target/surefire-reports, 10 suites.
Логи проверок в локальном TEMP: ai-advent-day6-backend.log,
ai-advent-day6-frontend-test.log, ai-advent-day6-frontend-build.log. Не tracked evidence.

Backend suite counts: AgentController 3, AgentDialogService 2, EngineeringReviewAgent 5,
Main 19, DialogStore 3, ReviewController 8, ReasoningReviewController 2,
ReasoningReviewService 4, TemperatureReviewController 2, ModelReview 10.
Прежние 46 backend и 15 frontend tests сохранены; добавлены 12 backend и 2 frontend.

Ключевые доказательства: captured ordered messages на трёх turns; immutable snapshots;
failure → последующий запрос без failed input; пустой ответ mocked HTTP отклоняется реальным
DeepSeekClient; A/B/A и fresh registry; архив JSON байт-в-байт неизменён после agent calls;
два concurrent initial requests дают один success и один busy, следующий turn видит один prior turn;
разные config; MockMvc success/400/404/409/502/500; Angular input-only A/follow-up/B/A,
safe Markdown, archive replay, failure и ручной следующий send без retry.

Первый concurrency test ошибочно ждал barrier внутри synchronized DialogStore.load mock,
поэтому второй поток не мог войти. Исправлено расположение barrier перед service.reply.
Это изменение test harness, не маскировка дефекта runtime registry.

## Точные команды повторения

```powershell
Set-Location <local-workspace>
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn package
```

```powershell
Set-Location <local-workspace>
$env:Path = "<local-workspace>\AppData\Local\nvm\v22.22.3;$env:Path"
npm test -- --watch=false
npm run build
```

Java 21, Maven 3.9.9; frontend Node 22.22.3/npm 10.9.8 выбраны process-local PATH,
глобальная активная версия nvm не переключалась. Сервисы не поднимались; MockMvc не является
ручным runtime/browser smoke. Dependencies не устанавливались и lock-файлы не менялись.

## Отклонение, риски и следующий шаг

Nullable maxTokens добавлен по новой инструкции владельца: это уже существующая low-level
настройка DeepSeek из Day 2. Default сохраняет отсутствие max_tokens в FREE/Day 6 запросе.
В остальном выполнен утверждённый план без scope Days 7–9.

Память растёт до остановки приложения и теряется при restart. UI archive остаётся видимым;
его сохранение не атомарно с agent turn, restore/deduplication/automatic retry не обещаются.
Реальная доступность API, качество follow-up ответа и desktop layout/видео ещё не проверены.

Следующий шаг после отдельного разрешения владельца: ручной real-API сценарий из task §9 —
A с синтетическим фактом и follow-up → B без контекста A → возврат в A. Видеозапись и её
загрузка подтверждаются отдельно. Ни один пункт real-API/video не помечен выполненным.

Rollback — только Day 6 additions/edits, с сохранением пользовательских изменений и архива;
миграции и очистка данных не нужны. Старый frontend не обязан читать новые AGENT cards.

## Desktop UI verification — 2026-09-11, 00:15 +03:00

**PASS; Day 6 TECHNICALLY COMPLETE.** Video PENDING; commit/push/merge не выполнялись.
Подтверждено: PID 28532 — Angular этого repo, listener только ::1:4201. Остановлен только
этот процесс; штатный запуск из frontend: npm start -- --port 4201 --host 127.0.0.1.
Новый PID 27672 слушает 127.0.0.1:4201; shell HTTP 200. Product code/config не менялись.

Chrome открыл http://127.0.0.1:4201/, фактический viewport 1440×1000.
Создан свежий диалог A, выбрана вкладка «Агент», отправлено:
«UI Day 6: кратко назови один риск повторной обработки PaymentReceived. Ответь Markdown:
жирный заголовок, один пункт списка и имя события в inline code.»
Ровно **1 дополнительный реальный LLM call**, без retries; суммарно Day 6 — 5 (4 API + 1 UI).
Loading: видны «Ментор анализирует...» и progressbar, submit отключён.
Успешный ответ о двойном зачислении отображён; DOM содержит strong, li и code,
визуальный screenshot просмотрен — layout и Markdown корректны.
Создан диалог B: пустой экран ввода. Возврат в A восстановил видимую пару user/assistant
и выбранную вкладку «Агент». Дополнительных сообщений в B/A не отправляли.
Chrome console error/warn/issue: NONE. Backend context/isolation повторно не проверялись.

Локальный evidence: ignored docs/local/agent-sessions/day6-verification/frontend-ipv4.log
и ui-result.json. Screenshot просмотрен в tool response; сохранение через Chrome MCP в repo
отклонено workspace policy инструмента, поэтому screenshot-файл не заявляется.
Предыдущая блокировка окружения снята IPv4 binding. Days 7–9 не выполнялись.