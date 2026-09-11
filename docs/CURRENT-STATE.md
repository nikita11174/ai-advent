# AI Advent Challenge 9 — Current State

Единственный operational source of truth. Детали и evidence находятся в `docs/tasks/DAY-XX.md`.

## Current work

| Поле | Состояние |
|---|---|
| Product | Engineering Review Mentor |
| Current milestone | Day 6 — implementation DONE; real API PASS; UI 1440×1000 PASS; technically complete; video PENDING |
| Current branch | `day_6` from `developer` / `06355effc56d60b4d43b58c7e274b0ec20622337`; no new commit |
| Day 4 base | `0271bf8eb0d1415c71c985dae7b45bed075c0c75` — completed Day 3 knowledge checkpoint |
| Day 4 planning commit | `237126aa018de62e48a46e48f8ef2dd5f3ba5eb3` |
| Day 4 implementation checkpoint | `0ac3ca7b7e05b18570ba4dc438d3fc8b65768820` |
| Day 4 accepted UX checkpoint | `aacbbf32701a5107b6afb81178d637cafb6d00a4` |
| Status | Day 6 implemented locally; backend 58 / frontend 17 tests and builds PASS; real API 4/4 PASS; UI 1440×1000 PASS; TECHNICALLY COMPLETE |
| Next action | Day 6 video preparation |

## Challenge days

| Day | Implementation | Verification | Submission / publication |
|---|---|---|---|
| Day 1 | DONE | DONE | Repository link DONE; demo video PENDING |
| Day 2 | DONE (`da103f54`) | DONE | Demo video and `day_2` publication PENDING |
| Day 3 | DONE | Automated/API/persistence DONE; owner visual browser review PENDING | Demo video and publication PENDING |
| Day 4 | DONE | Technical verification DONE; UX/responsive owner acceptance DONE | Demo video and publication PENDING |
| Day 5 | DONE (`951e65c`) | Backend 46 / frontend 15 tests + builds PASS; OpenAI access VERIFIED; experiment ACCEPTED — 7 completed / 9 attempts; browser replay/refresh PASS | Quality OWNER APPROVED; report READY; publication PENDING |
| Day 6 | DONE, uncommitted on `day_6` | Backend 58 / frontend 17 tests + builds PASS; real API 4/4 PASS; UI 1440×1000 PASS; TECHNICALLY COMPLETE; video PENDING | NOT PUBLISHED |

Day 6 2026-09-10: [canonical task](tasks/DAY-06.md), [implementation evidence](agent-runs/DAY-06.md).
Runtime-only agent per existing UUID, immutable config (model/systemPrompt/temperature/maxTokens),
whole conversation per turn, success-only commit and per-agent tryLock. Fresh backend/service clears
LLM memory. Existing UI JSON archive does not restore agent context. Days 7–9 not implemented.
No commit/push/merge or real LLM calls. Frontend build passed with existing app.scss budget warning.

Day 4 planning начат по official Telegram message `1642`. Day 5 (`1798`) design:
`docs/tasks/DAY-05.md` — direct OpenAI Luna/Terra/Sol, relative family tiers, PaymentReceived,
9 sequential calls with rotating order, budget ≤USD 5. Open product decisions NONE;
Owner confirmed key created and prepaid balance USD 5; application access всех трёх models VERIFIED.
Day 5 branch created from `9ff5b296b692266cde73701bc250c3aed7b5351d`; planning checkpoint `f91cf5f`.
Implementation checkpoint: `951e65c4516c4f01fb3321cbb86dc2574591d911` (не обозначение current HEAD).
Девять calls: Terra дважды incomplete/max_output_tokens; retries не было. 13842 tokens,
USD 0.1460032 estimated (USD 0.1460980 со smoke). Owner acceptance 2026-09-06: incomplete Terra
приняты без replacement/higher-ceiling runs; quality conclusion утверждён только для этого benchmark/preset.
Готовый submission artifact — `docs/DAY-05-REPORT.md`; подробная история — `docs/agent-runs/DAY-05.md`.
Универсальный победитель не объявлен. Новых платных вызовов при финализации нет.
Day 4 video/publication remain separately pending.

Day 2 planning commit: `58f1ae4`. Day 1 repository:
`https://github.com/nikita11174/ai-advent`.

Day 3 planning commit: `ebe6e6ca8c465899008eb305f3ca147dc271643e`. Day 3 implementation
commit: `897ca53dd355a4df754e8c02e45e1feb8c956c47`. Evidence checkpoint:
`4f61e21a417441481ad702f5a71b57fc5952fdad`.

## Current architecture

```text
Angular 22 :4201
  -> /api proxy
  -> Spring Boot 3.5.5 / Java 21 :18080
       -> /api/review (Day 1/2 FREE or CONTROLLED)
       -> /api/reasoning-review (Day 3 DIRECT/STEP_BY_STEP/SELF_PROMPT/EXPERTS)
       -> /api/temperature-review (Day 4 temperature 0/0.7/1.2)
       -> /api/model-options + /api/model-review (Day 5 direct OpenAI Responses, Luna/Terra/Sol)
       -> /api/dialogs/{id}/agent/messages (Day 6 runtime agent, whole message stack)
       -> /api/dialogs (local JSON create/list/load/update)
  -> DeepSeek deepseek-v4-flash (Day 1–4); OpenAI GPT-5.6 family (Day 5)
```

- Day 3 использует fixed FREE Markdown и не образует матрицу с Day 2 controls.
- Four-way comparison делает 5 LLM calls: `1/1/2/1`; SELF_PROMPT prompt inspectable, EXPERTS имеет
  три fixed perspectives.
- Reference evaluation — manual `found/missed/questionable` + explainable winner, без LLM judge и
  fake scores.
- Dialog JSON находится в ignored `docs/local/mentor-dialogs/`. История восстанавливает UI, но
  **никогда не отправляется DeepSeek/OpenAI как conversation memory**.
- Day 6 отдельно держит собственные successful turns в памяти backend и отправляет их DeepSeek
  целиком. UI archive не используется для восстановления этого context; restart очищает память.
- Concise run evidence — tracked `docs/agent-runs/DAY-XX.md`; detailed local evidence — ignored
  `docs/local/agent-sessions/`.
- `DEEPSEEK_API_KEY` и `OPENAI_API_KEY` доступны только backend environment; `.env.local` игнорируется Git.
- Day 4 сравнивает один immutable input при fixed prompt/configuration; отличается только
  temperature. Backend baseline: 36 tests PASS; latest UX frontend: 12 tests and production build
  PASS. 9/9 real experiment calls and Chrome web comparison PASS; responsive checks at
  1440×1000, 1024×768, 768×1024 and 500×844 PASS. Detailed evidence — Day 4 task.

Historical Day 4 evidence — `docs/tasks/DAY-04.md`; текущий Day 5 contract/evidence —
`docs/tasks/DAY-05.md` и `docs/agent-runs/DAY-05.md`.
