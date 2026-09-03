# Lightweight Agent Workflow

Процесс не привязан к конкретной модели. GPT/ChatGPT координирует работу вне репозитория, а
Claude Code или Codex выступает текущим implementation agent.

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

## Local verification and safety

- Локальные build, deterministic/unit tests и application runtime ожидаются, когда они нужны
  acceptance criteria текущего Day.
- Реальный external API smoke допустим, если его требует текущая задача, а secret передан только
  через environment/local ignored file.
- Secrets нельзя печатать, логировать или добавлять в Git.
- Destructive infrastructure operations требуют отдельной owner-авторизации.
- Commit/push выполняются только после явного owner approval.
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
- `day_N` — ветка очередного Challenge Day, создаваемая от `developer`.

Текущая рабочая ветка Day 1 — `day_1`.
