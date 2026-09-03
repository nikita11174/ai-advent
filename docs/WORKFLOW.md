# Lightweight Agent Workflow

Процесс не привязан к конкретной модели. GPT/ChatGPT координирует acceptance criteria и owner
decisions вне репозитория, а Claude Code или Codex выступает взаимозаменяемым implementation
agent.

## Основной flow

```text
Challenge task
  -> clarify acceptance criteria
  -> choose executor (Claude Code or Codex)
  -> implementation
  -> verification
  -> optional independent review, only if justified
  -> demo/evidence
  -> DONE
```

1. Добавить условия challenge task в отдельный документ под `docs/tasks/` и сформулировать
   проверяемые acceptance criteria.
2. Выбрать одного исполнителя для текущего шага. Выбор модели не меняет workflow.
3. Перед изменениями проверить working tree и продолжить уже начатое решение.
4. Реализовать минимальный scope задачи.
5. Выполнить targeted verification, достаточную для acceptance criteria, и сохранить короткое
   evidence: команда/сценарий, результат и известные ограничения.
6. Independent review проводить только при конкретной причине: высокий риск, значимое решение,
   сложное поведение или явно сформулированная неизвестность. Review не является ритуальным gate.
7. Подготовить demo/submission evidence и обновить `CURRENT-STATE.md`.
8. Отметить DONE только когда criteria выполнены и evidence записано.

Для простой задачи допустим короткий путь: `implement -> verify -> evidence -> DONE`.

Prompt текущего исполнителя должен описывать только актуальный шаг и необходимый context. Durable
история, решения и evidence сохраняются в repository docs, а не переносятся бесконечно из чата.

После substantial run разделять evidence: task truth — `docs/tasks/DAY-XX.md`, operational state —
`docs/CURRENT-STATE.md`, concise tracked run record — `docs/agent-runs/DAY-XX.md`, detailed local
prompt/final report — ignored `docs/local/agent-sessions/`, когда он доступен. Raw/local history
не заменяет canonical docs; credentials и environment secrets в histories не сохраняются.

Перед новым research/review/agent cycle ответить на три вопроса:

1. Что именно ещё неизвестно?
2. Какой агент или источник лучше всего закрывает эту неизвестность?
3. Может ли ответ реально изменить решение или implementation?

Если неизвестности нет либо evidence уже достаточно, нужно реализовать/проверить/завершить текущий
шаг, а не запускать precautionary loops ради уверенности.

## Local verification and safety

- Локальные build, deterministic/unit tests и application runtime ожидаются, когда они нужны
  acceptance criteria текущего Day.
- Реальный external API smoke допустим, если его требует текущая задача, а secret передан только
  через environment/local ignored file.
- Secrets нельзя печатать, логировать или добавлять в Git.
- Destructive infrastructure operations требуют отдельной owner-авторизации.
- Commit/push выполняются только после явного owner approval.
- Merge/rebase и удаление веток также требуют отдельной явной owner-авторизации.
- Если более высокий execution policy запрещает запуск, project docs не обходят запрет: агент
  фиксирует непроверенное и передаёт точные команды владельцу.

## Seamless handoff: Claude Code ↔ Codex

Новый исполнитель восстанавливает состояние через четыре артефакта:

- Git working tree — фактические файлы, branch, staged/unstaged diff и незавершённые изменения;
- `docs/CURRENT-STATE.md` — текущий статус, исполнитель, следующий шаг и blockers;
- текущий документ в `docs/tasks/` — условия, acceptance criteria и существенные решения;
- tests/evidence — что реально проверено и с каким результатом.

Передающий агент приводит эти артефакты в соответствие с фактом. Принимающий агент читает их,
проверяет `git status`/diff и продолжает существующее решение. Полный отдельный handoff-документ
создаётся только если этих артефактов объективно недостаточно; повторять всю историю задачи не
нужно.

При конфликте между описанием и working tree конфликт фиксируется явно. Чужие изменения не
откатываются, а работа не начинается заново без доказанной причины.

## Branch convention

- `main` — стабильные завершённые Challenge increments;
- `developer` — integration branch;
- `day_N` — ветка очередного Challenge Day, обычно создаваемая от integration baseline; точный
  base задаётся текущим task/owner decision и проверяется до создания.

Текущая branch/state информация хранится только в `docs/CURRENT-STATE.md`.
