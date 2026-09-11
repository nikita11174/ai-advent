# Day 8 — context token observability

Статус: **TECHNICALLY COMPLETE — API and desktop UI acceptance PASS**. Branch: `day_8` от Day 7
commit `b2eb28e05fc49be7ea89f46d02b40e675c6f4aae`.

## OFFICIAL REQUIREMENT

Измерять tokens нового user request, полного context stack и response. Допустим approximate counting;
расчёт денег не требуется. Overflow можно показать искусственно уменьшенным context limit; достаточно
показать ошибку.

## ORGANIZER CLARIFICATION

Нет дополнительных требований организатора сверх предоставленного owner текста.

## APPROVED PROJECT INTERPRETATION

Собирать metrics отдельно от canonical memory и передавать пользователю наблюдения request/context/
response. Context не сокращается: Day 8 наблюдает существующий full context. Artificial limit
используется только для воспроизводимой демонстрации overflow error.

## OUT OF SCOPE

Стоимость, automatic retries, trimming, summary, compression, Sliding Window, Sticky Facts,
Branching и destructive mutation raw history.

## IMPLEMENTATION AND AUTOMATED VERIFICATION — 2026-09-11

`ApproximateTokenEstimator` produces deterministic local estimates from UTF-8 text and explicit
message framing. For every successful agent turn, `TokenMetrics` reports estimates for only the
new user input, the exact immutable outbound message list and the assistant response. These are
labelled as local estimates, not provider tokenization. `DeepSeekClient` independently extracts
optional `prompt_tokens`, `completion_tokens` and `total_tokens` from a DeepSeek response into
nullable `ProviderUsage`; the values are never equated with local estimates.

`AgentConfig` now contains nullable `contextTokenLimit`; its production default is disabled and
the optional `mentor.agent.context-token-limit` property supplies an artificial limit when an
agent is created. Before calling the provider, `EngineeringReviewAgent` measures the final outbound
stack and rejects a limit overflow with HTTP 413. It neither calls the provider nor saves or commits
the rejected input. The Agent response now returns metrics, and the existing UI displays them for
successful agent turns. Metrics are UI observations, not canonical history.

Focused backend command:

```powershell
mvn '-Dtest=ApproximateTokenEstimatorTest,EngineeringReviewAgentTest,AgentDialogServiceTest,AgentControllerTest,MainTest' test
```

Focused suite: 36 tests, 0 failures/errors/skipped. Full backend `mvn test`, Angular
`npm test -- --watch=false` and `npm run build` pass. The build
retains the pre-existing `app.scss` budget warning.

## REAL API ACCEPTANCE — 2026-09-11

The planned API acceptance completed with four successful DeepSeek calls in one dialog. The short
request `Кратко назови главный риск повторной обработки PaymentReceived.` was used as A1 and A3.
A1 local metrics were `26 / 89 / 214` (request/context/response), provider usage `61 / 138 / 199`
(prompt/completion/total). After the content-rich A2 turn, A3 preserved local request estimate `26`
while full outbound context increased to `3426`; provider usage was `2656 / 52 / 2708`. Debugger
inspection at `DeepSeekClient.complete` confirmed outbound message counts 2 (A1), 4 (A2) and 6 (A3),
with all successful messages ordered.

A fresh backend with `mentor.agent.context-token-limit=1` rejected marker
`DAY8_REJECTED_MARKER_9F2C` as HTTP 413 (`3510 > 1`). The provider breakpoint was not reached.
The per-dialog history JSON had an identical SHA-256 before and after and did not contain the marker.
A fresh normal-limit backend made the fourth successful provider call; its eight outbound messages
retained the three successful turns in order and excluded the marker. The resulting persisted JSON
contained nine messages.

Sanitized raw evidence is ignored under `docs/local/agent-sessions/day8-real-acceptance/`; it contains
no credential or Authorization-header value. Chrome DevTools MCP was initially unavailable because its managed profile was in use; no browser process/profile was changed. It became available later, when the owner authorized one additional provider call for complete desktop verification.


## DESKTOP UI ACCEPTANCE — 2026-09-11

Chrome MCP became available after the API scenario. At 1440×1000, the Agent tab opened, loading state
was visible, and an owner-authorized fifth successful provider call rendered Markdown plus distinct
metrics labels: local estimate `30 / 93 / 161` (request/context/response) and provider usage
`68 / 121 / 189` (prompt/completion/total). Switching between dialogs preserved their visible
histories. The UI also rendered the deliberate local overflow alert. Console output had only the
expected HTTP 413 for that deliberate overflow and normal Vite/Angular development messages; there
were no unexpected runtime errors. Total real provider calls: **5**. Day 8 is technically complete;
video remains out of scope.
