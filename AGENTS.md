# AGENTS.md

Единые project instructions для Claude Code и Codex. Оба агента — взаимозаменяемые исполнители;
имя модели не определяет процесс, полномочия или качество результата.

## Session bootstrap

Перед работой:

1. Прочитать `README.md`.
2. Прочитать `docs/CURRENT-STATE.md` — единственный operational source of truth.
3. Прочитать документ текущего challenge task в `docs/tasks/`, если он существует.
4. Проверить `git status`, затем staged и unstaged diff.
5. Сверить фактическое состояние working tree с `CURRENT-STATE.md`; при конфликте сначала
   зафиксировать конфликт, не продолжать на догадках.

Не полагаться на память предыдущей сессии или отдельный чат-handoff, если репозиторий говорит
иначе. Не читать исторические материалы без конкретной причины.

## Implementation rules

- До изменений назвать acceptance criteria и способ проверки.
- Решение помечать `OWNER APPROVED` только после явного заявления владельца об одобрении.
  До этого предложения и рекомендации агента остаются рекомендациями, не решениями владельца.
- Перед изменениями кратко назвать что меняется, зачем и какой слой затронут.
- Не уничтожать, не откатывать и не перезаписывать незавершённую работу другого агента.
- Продолжать существующее решение. Начинать заново можно только при подтверждённой проблеме и с
  зафиксированным обоснованием.
- Делать минимальное корректное изменение без speculative abstractions, массового форматирования
  и unrelated cleanup.
- Существенные решения и изменения scope фиксировать в текущем task document; актуальный итог и
  следующий шаг — в `docs/CURRENT-STATE.md`.
- После substantial planning/implementation/review сохранить concise run evidence в
  `docs/agent-runs/DAY-XX.md`. Детальный prompt/final report при наличии сохранять только в
  ignored `docs/local/agent-sessions/`; local history не является canonical truth и не содержит
  secrets.
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

- Единственный routine/default viewport — desktop **1440×1000**; сохранять desktop layout.
- Не выполнять mobile/tablet/responsive проверки и не включать device/mobile emulation по умолчанию.
- В обычной проверке не уменьшать viewport ниже 1440×1000; завершать работу при 1440×1000.
- Mobile/responsive проверки и соответствующая эмуляция разрешены только по явному запросу
  владельца для текущей задачи. После такой проверки восстановить **1440×1000** desktop.

## Lightweight workflow

Для простой и ясной задачи достаточно: `implement -> verify -> record evidence`. Research,
отдельный planning round и independent review не являются обязательными стадиями.

Дополнительного агента подключать только для конкретной открытой неизвестности или оправданной
независимой проверки: например, high-risk/security change, существенное архитектурное решение
или сложное runtime-поведение. Не запускать multi-agent циклы ради дополнительной уверенности.

Полный flow и handoff описаны в `docs/WORKFLOW.md`.
