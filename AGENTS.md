# AGENTS.md

Единые project instructions для Claude Code и Codex. Оба агента — взаимозаменяемые исполнители;
имя модели не определяет процесс, полномочия или качество результата.

## Session bootstrap

Перед работой:

1. Прочитать `docs/PRODUCT-MANIFEST.md` — product authority.
2. Прочитать `docs/ARCHITECTURE.md` — architecture-boundary authority.
3. Прочитать `docs/CURRENT-STATE.md` — operational source of truth.
4. Прочитать документ текущего challenge task в `docs/tasks/`, только если он существует и нужен
   для текущего scope.
5. Прочитать `README.md`, когда нужны команды build/test/run.
6. Проверить `git status`, затем staged и unstaged diff.
7. Сверить фактическое состояние working tree с `CURRENT-STATE.md`; при конфликте сначала
   зафиксировать конфликт, не продолжать на догадках.

Не полагаться на память предыдущей сессии или отдельный чат-handoff, если репозиторий говорит
иначе. Не читать исторические материалы без конкретной причины.

## Implementation rules

- До изменений назвать acceptance criteria и способ проверки.
- Решение помечать `OWNER APPROVED` только после явного заявления владельца об одобрении.
  До этого предложения и рекомендации агента остаются рекомендациями, не решениями владельца.
- Перед изменениями кратко назвать что меняется, зачем и какой слой затронут.
- Не уничтожать, не откатывать и не перезаписывать незавершённую работу другого агента.
- `docs/**` is owner-private local state and must not be staged/committed unless explicitly authorized for publication.
- Продолжать существующее решение. Начинать заново можно только при подтверждённой проблеме и с
  зафиксированным обоснованием.
- Делать минимальное корректное изменение без speculative abstractions, массового форматирования
  и unrelated cleanup.
- Документация и evidence подчиняются канонической global policy. Обновлять существующий
  authoritative документ только когда его owned truth материально изменился.
- Тестировать изменённое подходящими targeted checks. Не объявлять DONE без evidence; явно
  указывать, что не проверено.
- Не выполнять commit, push, merge, rebase или удаление веток без явной команды владельца.
- Secrets, API keys, tokens, credentials и чувствительные данные никогда не хранить в Git и не
  выводить в отчёты.

## Local execution

- Локальные Maven build, deterministic/unit tests и запуск приложения разрешены и ожидаются,
  когда проверяют текущий Challenge task.
- Реальный внешний API smoke разрешён только когда его явно требует текущая задача; не выводить
  credentials или чувствительные request data.
- IDE и MCP tooling можно использовать для анализа и проверки, когда это полезно. Они являются
  локальными инструментами разработки, а не product dependency.
- Commit/push и destructive infrastructure operations выполняются только по отдельной явной
  команде владельца.

## Browser verification

- Выбор browser automation tool и fallback policy определяет каноническая global policy.
- **BROWSER WINDOW OWNERSHIP**
  - Never resize or reposition the user's existing browser window automatically.
  - Never maximize, minimize, snap or otherwise change its window bounds.
  - Preserve the user's current Chrome window geometry.
  - Do not call `resize_page` / viewport resize merely to satisfy a nominal acceptance resolution.
  - Responsive-size testing must be an explicit separate task.
  - If a requested viewport cannot be tested without changing the user's window, report the limitation instead.
  - A disposable/separate browser may be resized only when explicitly authorized.
- Единственный routine/default viewport — desktop **1440×1000**; сохранять desktop layout.
- Не выполнять mobile/tablet/responsive проверки и не включать device/mobile emulation по умолчанию.
- В обычной проверке не уменьшать viewport ниже 1440×1000; завершать работу при 1440×1000.
- Mobile/responsive проверки и соответствующая эмуляция разрешены только по явному запросу
  владельца для текущей задачи. После такой проверки восстановить **1440×1000** desktop.

## Lightweight workflow

Для простой и ясной задачи достаточно: `implement -> verify -> compact chat result`. Research,
отдельный planning round и independent review не являются обязательными стадиями.

Дополнительного агента подключать только для конкретной открытой неизвестности или оправданной
независимой проверки: например, high-risk/security change, существенное архитектурное решение
или сложное runtime-поведение. Не запускать multi-agent циклы ради дополнительной уверенности.

Начинать отдельный research/review/agent cycle только при concrete unresolved question, ответ на
который может изменить implementation или decision; не проводить broad audit «на всякий случай».
Для нетривиального цикла явно задать: Goal → Evidence/Context → Constraints → Done when.
Для follow-up в той же сессии использовать delta prompt, а не повторять полный handoff.
