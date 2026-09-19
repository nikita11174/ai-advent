# Local AI Worker — Product Manifest

Живой документ product direction для AI Advent Challenge 9. Он определяет WHY
продукта и помогает выбирать между одинаково простыми реализациями, но не
заменяет требования текущего Day.

## Problem and primary user

Cloud coding agents тратят дорогой context на подготовительную работу: поиск по
репозиторию, чтение файлов и истории, восстановление архитектуры, поиск тестов,
сопоставление кода с документацией и сбор доказательств. Основной пользователь —
разработчик, который работает с локальным, контролируемым им workspace и затем
передаёт компактный проверенный контекст сильному cloud agent или использует его
сам.

## Product thesis

**Local AI Worker** — локальная система для исследования кода, документов и
других user-controlled данных, выполнения ограниченных задач и выпуска компактных
evidence-backed artifacts. Она переносит разумную часть preparatory/research work
на локальную машину, чтобы cloud model получала не весь репозиторий, а отобранный
и проверяемый контекст.

Worker — это продукт; local LLM — заменяемый reasoning component внутри него.
Детерминированные инструменты и локальная структурная навигация предшествуют
model reasoning. Сильные cloud models остаются уместны для трудных
архитектурных решений, дорогих по ошибке действий и независимого review.

## Product boundary and core use cases

Worker работает только с явно настроенными локальными workspace и данными,
контролируемыми пользователем. Его будущие use cases additive, а не plugin
framework, который нужно строить заранее:

- repository research по узкому техническому вопросу с путями, символами,
  командами и результатами как evidence;
- generation context pack для Codex / Claude вместо полного repository exploration
  в cloud context;
- **Engineering Review Mentor** как специализированный use case: реальные
  findings и project evidence объясняют значимость дефекта, независимый способ
  его найти, общий engineering principle и перенос на другой случай;
- bounded research/work tasks на личных проектах;
- local/private knowledge workflows для журналов, документов и других явно
  configured workspaces.

## Evidence-backed philosophy

Локальный LLM output не является authority. Важное утверждение должно опираться
на independently inspectable deterministic evidence, когда это practically
возможно: Git SHA, file path, symbol и line range, search result, AST/LSP/call
graph evidence, выполненная команда, test result или persisted source data.

Будущий research output концептуально включает два связанных artifact:

- `RESEARCH.md` — human-readable conclusion, relevant flow, findings,
  uncertainty и evidence references;
- `EVIDENCE.json` — workspace, Git HEAD, files/symbols/ranges, executed
  searches/tools, tests/results, run/model metadata и confidence.

Это направление формата, а не реализованный contract или обязательство
реализовать его до появления реальной задачи.

## Stable product boundaries

- Dialog — communication mechanism, а не будущая единица работы Task/Run.
- Local Worker не равен Local LLM: Worker объединяет state, tools, search,
  retrieval, model calls, evidence и artifacts.
- LLM не является repository search engine: сначала deterministic/local discovery,
  затем reasoning по выбранному context.
- Raw conversation history — canonical conversation memory; summary и Sticky
  Facts — rebuildable derived memory; branches/checkpoints — topology.
- ContextPolicy только проецирует доступное state в один model call; metrics не
  являются agent state; retrieval/RAG не становится conversation memory; tools/MCP
  не являются ContextPolicy.
- Model provider должен оставаться заменяемым. Future capabilities добавляются слоями и
  не меняют смысл существующих слоёв без явной необходимости.
- Vocabulary remains explicit: Dialog is the communication surface; Memory is retained
  information; Profile is orchestration configuration describing HOW agents work; Task is the work unit; TaskState is progress;
  Invariant is a non-violable rule; Context is the per-call projection; ModelExecutor executes a
  provider/model request; transition control decides which TaskState changes are legal.
- Memory retains interaction/state (`SHORT_TERM` dialog, `WORKING` task, `LONG_TERM` user scope);
  context/adaptation projects it into an effective request. Storage/state does not build provider
  prompts directly, and provider execution does not own repository/storage concerns.

Подробный architecture contract находится в
[`docs/ARCHITECTURE.md`](ARCHITECTURE.md).

### Текущий scope: выбор модели агента

OWNER APPROVED — 2026-09-16: в рамках [UX Shell V1](tasks/UX-SHELL-V1.md)
пользователь явно выбирает provider/API и поддерживаемую модель самого агента:
текущий DeepSeek либо существующие OpenAI модели. Это требование перенесено из
future direction в текущий product scope; UX Shell V1 и provider/model boundary реализованы.
Backend владеет безопасным каталогом и concrete mappings; браузер передаёт только
selection key, без произвольных model IDs, endpoints или credentials.
Выбор модели меняет только исполнение модельных вызовов, сохраняя историю,
ContextPolicy, derived memory, Day 11 memory/task scope и branch/checkpoint semantics.
Provider-agnostic orchestration и небольшой executor boundary обязательны;
plugin framework и универсальная provider platform не входят в scope.
Конкретная граница зафиксирована в `ARCHITECTURE.md`; package/module refactor остаётся
вне текущего scope.

### Текущий scope: Day 12 Profile

Day 12 реализован в product commit `24a11b1`: Profile — независимая orchestration configuration
(`id`, `name`, `instructions`, `responseStyle`, `responseFormat`), описывающая HOW работает агент.
Memory остаётся retained/accumulated information, описывающей WHAT сохранено;
`Profile config != Memory`. Profile хранится независимо, выбирается per-dialog через UI state,
optional `profileId` resolves in orchestration and is projected into the effective main model
context. No-profile сохраняет прежнее поведение. Profile не становится raw history, summary,
Sticky Facts или Memory bucket; storage не строит provider prompts, а ModelExecutor не зависит от
Profile persistence.

## Challenge development strategy

AI Advent остаётся roadmap driver. Долгосрочное vision заранее не реализуется.

```text
Challenge acceptance criteria
  -> minimum useful implementation
  -> small reusable product increment
```

Для каждого Day:

1. Буквально определить требования и acceptance criteria текущего задания.
2. Полностью закрыть их минимальным решением.
3. Среди одинаково простых вариантов выбрать естественно приближающий Worker
   increment.
4. Не добавлять будущую функциональность и premature architecture.
5. Рефакторить только при подтверждённой необходимости следующего задания.

Формула: **не MVP всего продукта, а MVP текущего шага продукта**.

Day 12 Profile — реализованный шаг Week 3. Future only: Day 13 introduces first-class persisted
Task / TaskState (`stage`, `currentStep`, `expectedAction`) and its happy path with pause/resume;
Dialog != Task, and current `taskId` is only a `WORKING`-memory scope key until then. Day 14 adds
invariants while keeping deterministic enforcement distinct from semantic evaluation, not another
Memory bucket. Day 15 adds explicit controlled TaskState transitions and red-path handling: model
or user proposes an action, application validates it and owns persisted state mutation.
Further direction: Week 3 — remaining Task/TaskState, invariants and controlled transitions;
Week 4 — tool boundary и eventual Codex/Claude delegation через MCP; Week 5 —
retrieval над кодом и документами; Week 6 — RTX 3090-backed local provider за
model boundary; Week 7 — orchestration multi-step tasks из существующих
компонентов.

## Success direction and non-goals

Успех — последовательность challenge increments складывается в полезный local
worker, который сокращает дорогое cloud exploration и создаёт проверяемые
артефакты, а не в набор несвязанных demo. Mentor сохраняет обучающую ценность,
но не ограничивает продукт одним review flow.

Сейчас не являются целями: замена human review, autonomous execution без
границ, enterprise platform, заранее построенные MCP server, RAG/vector DB,
workflow engine, local-model runtime, plugin framework или generic agent
framework. Не выбираются заранее конкретные local model/runtime, vector DB,
MCP transport и orchestration engine.

## Relationship with agents

Codex и Claude — сильные cloud implementation/review agents, а не product
direction. Local Worker готовит для них inspectable local evidence и context;
они могут выполнять сложное reasoning и independent review. Перед работой над
Day исполнитель читает этот manifest, architecture contract, current task и
`docs/CURRENT-STATE.md`.
