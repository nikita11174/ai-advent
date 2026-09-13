# Day 7 — durable raw agent memory

Статус: **TECHNICALLY_COMPLETE**. Branch: `day_7` от
documentation checkpoint `c0d736369b7bfc033b59f6ee36ab43af5553b658`.

## OFFICIAL REQUIREMENT

Сохранить историю сообщений агента в JSON или SQLite. После restart приложения восстановить
историю, продолжить тот же dialog и отправить восстановленные предыдущие сообщения LLM.

## ORGANIZER CLARIFICATION

Нет дополнительных требований организатора сверх предоставленного owner текста: JSON или SQLite,
restore after restart и continuation same dialog.

## APPROVED PROJECT INTERPRETATION

JSON — минимальное решение. Один файл на dialog UUID в ignored
`docs/local/agent-histories/` хранит полный ordered raw stack сообщений, включая system message.
Это canonical **memory**, отдельная от UI dialog archive. При создании нового runtime agent service
валидирует dialog через существующий `DialogStore`, загружает history или создаёт fresh system
context при отсутствии файла. После успешного provider response completed user/assistant pair
сохраняется atomic file replace и фиксируется в runtime context; следующий LLM request использует
восстановленный полный stack.

## OUT OF SCOPE

Token/context metrics, limits, trimming, summary, compression, Sliding Window, Sticky Facts,
Branching, новая UI/API contract и перенос Days 1–5 в agent architecture.

## IMPLEMENTATION AND AUTOMATED VERIFICATION — 2026-09-11

Implemented `AgentHistoryStore`: one JSON file per dialog UUID under
`docs/local/agent-histories/`, written through temporary file plus atomic replace with the same
fallback as existing `DialogStore`. `AgentDialogService` validates the UI dialog, restores the raw
stack when it creates a runtime agent and starts with the normal system-only context if no history
file exists. `EngineeringReviewAgent` writes the completed raw snapshot before updating runtime
context, while holding the existing per-agent lock.

Focused command:

```powershell
mvn '-Dtest=AgentHistoryStoreTest,EngineeringReviewAgentTest,AgentDialogServiceTest,AgentControllerTest,MainTest' test
```

PASS: 33 tests, 0 failures/errors/skipped. Full `mvn test`: PASS, 62 tests, 0 failures/errors/skipped.
Coverage includes exact JSON roles/order, missing and malformed history, restored outbound context,
A/B isolation, provider/save rollback and existing busy-turn behavior.

## REAL RESTART / PROVIDER VERIFICATION — 2026-09-11

Three real DeepSeek calls passed. Before restart, Dialog A stored `system → A1 user → A1 assistant`
after A1 recorded the code word «Сатурн». The serving backend process was stopped, its port no longer
served HTTP, and a fresh process started. A2 with the same UUID returned «Сатурн»; a temporary JDWP
inspection of `DeepSeekClient.complete` showed the restored A1 user/assistant pair before the new A2
user message. The persisted JSON then contained the continued five-message sequence. Dialog B's B1
outbound stack contained only its system and user messages, no A content, and its real-provider answer
correctly reported no access to another dialog. Sanitized local evidence is under
`docs/local/agent-sessions/day7-restart-verification/`; no credentials are stored there.

No browser/UI verification or frontend build was required because the UI/API contract did not change.

## POST-DAY-7 BACKEND AVAILABILITY — 2026-09-13

After the historical Day 7 implementation, the Agent UI gained a compact backend-process
indicator. `GET /api/health` returns only `{"status":"UP"}`; it does not call an LLM,
read dialog history or mutate state. While the Agent tab is active, the frontend performs one
non-overlapping health request at selection and then approximately every two seconds, with a
1.5-second timeout. Its states are «Подключение…», «Сервер подключён» and «Сервер недоступен».

At desktop 1440×1000, the Day 7 runtime was observed as `connected → unavailable → connected`:
the Java backend process was really stopped, then a newly started Day 7 process again returned
`200 {"status":"UP"}`. Browser network evidence during this check contained health polling only,
with no `/agent/messages` request; the visible dialog history was unchanged. Focused
`HealthControllerTest` (1 test), Angular tests (18 tests) and frontend build passed. This is a
generic product availability capability; it does not change Day 7 canonical raw-history semantics.
