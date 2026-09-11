# Day 7 — implementation evidence

## Automated checkpoint — 2026-09-11

Implemented durable canonical agent memory without changing the Agent UI or API contract.
`AgentHistoryStore` persists one raw ordered message stack per dialog UUID in ignored
`docs/local/agent-histories/`. `AgentDialogService` restores that stack when a fresh runtime agent
is created. `EngineeringReviewAgent` writes a complete user/assistant snapshot before its runtime
context advances; provider, save and malformed-load failures do not advance memory.

Focused backend command:

```powershell
mvn '-Dtest=AgentHistoryStoreTest,EngineeringReviewAgentTest,AgentDialogServiceTest,AgentControllerTest,MainTest' test
```

PASS: 33 tests, 0 failures/errors/skipped.

Full backend command:

```powershell
mvn test
```

PASS: 62 tests, 0 failures/errors/skipped.

## Real restart/provider checkpoint — 2026-09-11

PASS — exactly three real DeepSeek calls: A1, A2 after a real backend restart, and isolated B1.
Dialog A's JSON existed before restart with `system → A1 user → A1 assistant`. The A1-serving Java
process was stopped; port 18080 had no listener and HTTP was unreachable. A new Java process then
served port 18080. JDWP inspection at `DeepSeekClient.complete` showed A2's ordered outbound stack
included the restored A1 user/assistant messages; A2 answered «Сатурн». The final A JSON continued
with A2 user/assistant. B1's outbound stack had only system and B1 user, no Dialog A content, and its
provider response correctly reported no access to another dialog.

Sanitized local evidence: `docs/local/agent-sessions/day7-restart-verification/`. No credentials or
Authorization headers were captured. Browser/UI verification and frontend build were not needed:
Day 7 has no UI/API contract change.
