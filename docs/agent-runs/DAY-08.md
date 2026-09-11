# Day 8 — implementation evidence

## Automated checkpoint — 2026-09-11

Implemented token observability for the existing full agent context without changing canonical raw
memory or adding context reduction. `ApproximateTokenEstimator` reports deterministic local estimates:
the current input alone, the exact final outbound message stack and the successful response. The UI
labels these values as local estimates rather than provider tokenization.

`DeepSeekClient` parses optional provider `prompt_tokens`, `completion_tokens` and `total_tokens`
separately. Missing provider usage remains nullable. `contextTokenLimit` is an optional agent setting
configured through `mentor.agent.context-token-limit`; an exceeded estimate returns HTTP 413 before
provider invocation, persistence or runtime commit.

Focused backend command:

```powershell
mvn '-Dtest=ApproximateTokenEstimatorTest,EngineeringReviewAgentTest,AgentDialogServiceTest,AgentControllerTest,MainTest' test
```

PASS: 36 tests, 0 failures/errors/skipped.

Full backend command:

```powershell
mvn test
```

PASS: 67 tests, 0 failures/errors/skipped.

Frontend commands, using temporary Node 22.22.3 on a machine whose default Node 22.22.0 is below
the Angular CLI minimum:

```powershell
npm test -- --watch=false
npm run build
```

PASS: 17 frontend tests. Build PASS with the pre-existing `app.scss` budget warning.

## Real API acceptance — 2026-09-11

PASS for the planned API scenario: four successful DeepSeek calls in one dialog. The repeated short
request had local metrics `26 / 89 / 214` before growth and `26 / 3426 / 71` after growth
(request/context/response); provider usage was respectively `61 / 138 / 199` and
`2656 / 52 / 2708` (prompt/completion/total). Debugger snapshots showed final full outbound stacks
of 2, 4 and 6 messages for the first three calls.

With a fresh backend and `mentor.agent.context-token-limit=1`, marker
`DAY8_REJECTED_MARKER_9F2C` returned HTTP 413 for estimate `3510 > 1`. The provider breakpoint was
not reached; the persisted JSON SHA-256 was identical before and after, and the marker was absent.
A fresh normal-limit backend then made the fourth successful call with eight outbound messages:
all successful earlier turns remained ordered and the rejected marker was absent. The resulting JSON
contained nine messages. Sanitized ignored evidence:
`docs/local/agent-sessions/day8-real-acceptance/`.

Chrome DevTools MCP was initially unavailable because its managed profile was in use; no browser process/profile was changed. It became available later, when the owner authorized one additional provider call for complete desktop verification.

Desktop UI PASS at 1440×1000: the Agent tab, loading state, Markdown rendering, overflow alert and dialog switching were observed. The fifth call displayed local estimates `30 / 93 / 161` and provider usage `68 / 121 / 189` with visibly distinct labels. Console output had only the expected HTTP 413 from the deliberate overflow and normal Vite/Angular development messages.


Total real provider calls: **5**. Sanitized UI evidence is stored only under
`docs/local/agent-sessions/day8-real-acceptance/`. Day 8 is technically complete; video was not made.
