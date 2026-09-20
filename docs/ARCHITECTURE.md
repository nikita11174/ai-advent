# Local AI Worker — Architecture Contract

Этот документ фиксирует устойчивые границы проекта. Он не является class catalog
и не утверждает существование будущих компонентов. Product authority —
[`PRODUCT-MANIFEST.md`](PRODUCT-MANIFEST.md); причина направления зафиксирована в
[`decisions/ADR-001-local-ai-worker-direction.md`](decisions/ADR-001-local-ai-worker-direction.md).

## CURRENT

Local AI Worker — product identity; Engineering Review Mentor — специализированный
исторический use case. Backend находится в `dev.aiadvent.worker`; в корне остаются
только `LocalAiWorkerApplication` и CLI launcher `Main`.

| Capability | Ответственность | Product dependencies |
|---|---|---|
| `model` | Neutral execution contract и provider implementations | нет |
| `dialog` | Canonical conversation, raw history, checkpoints/branches и persistence | нет |
| `memory` | Retained/derived state и его persistence | нет |
| `profile` | Orchestration configuration и её независимая JSON persistence | нет |
| `context` | Per-call projection, token estimation и limits | `dialog`, `memory` |
| `agent` | Conversation orchestration, main/summary/Sticky Facts calls | `model`, `dialog`, `memory`, `profile`, `context` |
| `api` | HTTP boundary | `agent`, `model`, `dialog`, `memory`, `profile`, `context` |
| `review` | Специализированный historical engineering-review use case и его HTTP endpoints | `model` |

Зависимости между capabilities следуют этой таблице; циклов нет. `model` не владеет
canonical conversation/domain state: сообщения принадлежат `dialog`. `memory` и
`ContextPolicy` не исполняют модели. `agent` использует neutral executor contract,
не concrete provider clients, и не зависит от `api`/`review`. Нижние capabilities
не знают о controllers/API; `review` не зависит от generic orchestration/state.
Зависимость `context -> memory` нужна для проекции summary и Sticky Facts.

Angular UI обращается к Spring API. `AgentDialogService` и `ConversationAgent`
координируют dialog, persistence и model calls. Current provider boundary
исполнен через `AgentModelCatalog` и `AgentModelExecutor`. Catalog разрешает
backend-owned selection key и default DeepSeek; тонкие DeepSeek/OpenAI adapters
исполняют единый model-call contract. Orchestration передаёт выбранный executor
в main, summary и Sticky Facts calls, не выбирая provider.
Execution contract — `complete(AgentModelRequest)`: ordered `AgentModelMessage`,
model, temperature и maxTokens; результат — content и optional `ProviderUsage`,
ошибка — `ModelExecutionException`. Conversation values преобразуются в execution
DTO выше этой границы; executor contract не зависит от `AgentConfig` или
`ConversationContext.Message`.

```mermaid
flowchart LR
  API[api] --> Agent[agent]
  API --> Model[model]
  API --> Dialog[dialog]
  API --> Memory[memory]
  API --> Profile[profile]
  API --> Context[context]
  Agent --> Model
  Agent --> Dialog
  Agent --> Memory
  Agent --> Profile
  Agent --> Context
  Context --> Dialog
  Context --> Memory
  Review[review] --> Model
```

### State and topology

**Canonical conversation memory** — complete ordered raw history, persisted per
dialog. It is the source of truth for linear conversation. A failed provider
call or context-limit rejection must not make rejected user input canonical.

**Derived memory** — summary and Sticky Facts. They may be regenerated from
canonical raw history; they do not replace or override it. Their maintenance
calls and metrics remain distinguishable from main calls.

**Topology** — immutable checkpoints and branch-local continuations. A branch
is not a ContextPolicy and branch state does not leak into linear raw history
or sibling branches. Dialog identity, checkpoint identity and branch identity
remain explicit.

### Context and model calls

`ContextPolicy` constructs the representation for exactly one model call from
available state. Existing linear modes include FULL, SUMMARY_RECENT,
SLIDING_WINDOW and STICKY_FACTS; branching uses branch-local FULL context. A
policy must not destructively mutate canonical raw memory.

Memory owns retained interaction/state; context/adaptation owns only the projection into one
effective model request. Storage/state components must not construct provider prompts directly;
the model/provider execution layer likewise does not own repository or storage concerns.

### Profiles — реализованная граница Day 12

**Profile** — orchestration configuration, описывающая **HOW** работает агент: `id`, `name`,
`instructions`, `responseStyle` и `responseFormat`. **Memory** — retained/accumulated information,
описывающая **WHAT** сохранено. `Profile config != Memory`.

`ProfileStore` сохраняет Profile как независимые JSON documents; он не строит provider prompts.
Profile не записывается в raw dialog history, summary, Sticky Facts, `SHORT_TERM`, `WORKING` или
`LONG_TERM` Memory. Удаление Dialog не удаляет Profile. `SHORT_TERM` остаётся dialog/chat scope,
`WORKING` — task scope, `LONG_TERM` — user scope; current single-user/local global representation
implements the last scope.

Для normal agent message `profileId` optional. `AgentDialogService` resolves an указанную Profile
до model execution; unknown ID fails explicitly. `ConversationAgent` projects resolved Profile into
the effective **main** model context ровно один раз как supplemental system message after the base
system contract. Base contract remains authoritative; without Profile outbound context preserves
the prior semantics. Token estimation runs after this projection, therefore Profile is included in
the main context budget and cannot silently vanish when history consumes available budget.

Browser selection is `DialogUiState.selectedProfileId`, not history or Memory. The narrow
`PUT /api/dialogs/{id}/profile-selection` atomically changes only that field, preserving unrelated
dialog UI state. `ModelExecutor` receives only its neutral model request and does not depend on
Profile persistence.

Metrics observe concrete provider calls — request/context/response estimates and
optional provider usage — but are not agent state. Summary and facts maintenance
metrics are kept separate from main response metrics.

`DeepSeekTransport` владеет request JSON, HTTP и извлечением completion/finish_reason/usage.
Он не зависит от conversation/domain state, context/memory/dialog или review DTO.
`DeepSeekAgentModelExecutor` зависит только от transport, а не от review facade.
Исторический review facade `DeepSeekReviewClient` использует тот же Spring-managed transport и сохраняет
review prompts, controlled parsing/validation и free/reasoning/temperature methods.
Endpoint, model IDs, payload fields, message ordering, sampling options, error/retry
и rawResponse semantics сохранены при package separation; миграция persisted data не требуется.

### Evidence today

Current challenge evidence consists of tracked task/run documentation plus
ignored local raw runtime evidence where appropriate. Git state, source files,
commands and test results remain independently inspectable inputs; an LLM prose
answer alone is not authority.

### UX Shell V1 — реализованная граница

UX Shell V1 реализован: `AgentModelService` получает backend-owned catalog, а
`AgentInspector` предоставляет компактную правую панель. `App` сохраняет выбранный
key в dialog UI state и координирует существующие действия; backend принимает только
безопасный key, без arbitrary model IDs, endpoints или credentials. Отсутствующий key
сохраняет DeepSeek default. Выбор не входит в identity истории или turn lock и не
меняет ContextPolicy, Memory, Task scope либо branches/checkpoints.

### Dialog lifecycle — реализованная граница

`DELETE /api/dialogs/{id}` is orchestrated by `AgentDialogService`, not by lower stores. It removes
only the dialog document, raw history, summary, Sticky Facts, branch/topology state, dialog-scoped
`SHORT_TERM` memory and cached in-process dialog state. `WORKING` task memory, global/user-scoped
`LONG_TERM`, other dialogs and shared task state are preserved. The Angular sidebar exposes this
supported lifecycle through compact recent rows, «Показать ещё» and a confirmed delete action.

Explicit memory terminology is semantic: `SHORT_TERM` = dialog scope, `WORKING` = task scope,
`LONG_TERM` = user scope. The current single-user/local global representation implements the last
scope; physical persistence does not define it.

### Task / TaskState — реализованная граница

**Task** is a persisted work unit with a goal, approved plan, validation evidence and **TaskState**.
TaskState owns progress: `stage`, `currentStep`, `expectedAction`, `status` and `revision`. Day 13
implements the happy path `PLANNING -> EXECUTION -> VALIDATION -> DONE` with pause/resume.

`Dialog != Task`: one Task may be selected by multiple Dialogs. `Task.id` is the `WORKING` Memory
scope; raw history and `SHORT_TERM` Memory remain dialog-scoped and separate. A legacy UUID memory
scope becomes a Task only through explicit adoption; no automatic conversion occurs.

`TaskService` and application code own persisted lifecycle mutation. `AgentDialogService` resolves
the selected/effective Task and `ConversationAgent` projects its state into each main model call.
Task context is not canonical history or derived Memory. Task persistence does not construct prompts,
and `ModelExecutor` remains unaware of Task storage.

## FUTURE DIRECTION

The following are extension points, not components that exist now.

Day 14 is future only: **Invariant** is a rule/constraint that must not be violated, not another
Memory bucket. Deterministic constraints that code can enforce remain distinct from semantic
constraints that require contextual/model evaluation; no universal rule engine is promised.

Day 15 is future only: it may strengthen Day 13 with explicit legal transitions and red-path
handling. A model or user may propose an action, but application code validates the transition and
alone mutates persisted TaskState. Required stages cannot be skipped; invalid transitions are
rejected; allowed rework/backward transitions and pause/resume remain explicit; malformed model
output cannot corrupt lifecycle.

### Deterministic tools, retrieval and artifacts

Future repository/document research should prefer deterministic local tools
(search, Git, symbol or structural discovery, tests) before LLM reasoning.
Retrieval/RAG may select code or knowledge context, but does not become
canonical conversation memory. Tools and a future MCP boundary are separate
from ContextPolicy.

Future research results may pair a human-readable `RESEARCH.md` with machine
inspectable `EVIDENCE.json`; the exact schemas are intentionally deferred.

### Replaceable model and orchestration boundaries

A local RTX 3090-backed model provider may be added behind a model-provider
boundary introduced only when a real requirement needs it. Choice of model,
Ollama, llama.cpp or another runtime is not made here. Week 4 may introduce a tool/MCP boundary for cloud-agent
delegation; its transport is deferred. Week 7 may orchestrate multi-step tasks
using existing state, tools, providers and artifacts; no workflow engine or
plugin architecture is implied.

## Architectural invariants

1. Dialog != Task/Run.
2. Local Worker != Local LLM; provider choice must remain replaceable.
3. LLM != repository search engine; deterministic discovery comes first.
4. Raw history is canonical; summary and facts are derived and rebuildable.
5. Checkpoints/branches are topology; ContextPolicy is a per-call projection.
6. Metrics are observations, not agent state.
7. Retrieval/RAG is neither canonical memory nor a replacement for evidence.
8. Tools/MCP are not ContextPolicy.
9. Important claims should expose inspectable evidence where practical.
10. New challenge capabilities are additive layers and do not silently change
    existing layer semantics.
11. Profile config != Memory; TaskState, invariants and transition control are distinct concepts.

When a future Day requires a real change to one of these boundaries, update
this contract and record the decision before introducing the corresponding
implementation.
