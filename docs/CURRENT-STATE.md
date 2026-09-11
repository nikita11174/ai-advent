# AI Advent Challenge 9 — Current State

Единственный operational source of truth. Детали и evidence находятся в `docs/tasks/DAY-XX.md`.

## Current work

| Поле | Состояние |
|---|---|
| Product | Engineering Review Mentor |
| Current milestone | Days 6–8 — TECHNICALLY COMPLETE; Day 8 API and desktop UI acceptance PASS |
| Current branch | `day_8` from Day 7 commit `b2eb28e`; `developer` remains untouched until the whole week is complete and reviewed |
| Day 4 base | `0271bf8eb0d1415c71c985dae7b45bed075c0c75` — completed Day 3 knowledge checkpoint |
| Day 4 planning commit | `237126aa018de62e48a46e48f8ef2dd5f3ba5eb3` |
| Day 4 implementation checkpoint | `0ac3ca7b7e05b18570ba4dc438d3fc8b65768820` |
| Day 4 accepted UX checkpoint | `aacbbf32701a5107b6afb81178d637cafb6d00a4` |
| Status | Days 1–5 published/integrated; Day 7 committed; Day 8 backend 67/frontend 17 tests and builds PASS; 5-call real API/UI acceptance PASS |
| Next action | Review the complete Day 8 diff and prepare the Day 8 implementation checkpoint |

## Challenge days

| Day | Implementation | Verification | Submission / publication |
|---|---|---|---|
| Day 1 | DONE | DONE; recording demo executed | day_1 PUBLISHED; saved/uploaded video not confirmed |
| Day 2 | DONE (`da103f54`) | DONE; FREE → CONTROLLED recording demo executed | day_2 PUBLISHED; saved/uploaded video not confirmed |
| Day 3 | DONE | Automated/API/persistence DONE; four-strategy recording demo executed; separate owner visual acceptance not confirmed | day_3 PUBLISHED; saved/uploaded video not confirmed |
| Day 4 | DONE | Technical verification DONE; UX/responsive owner acceptance DONE; temperature recording demo executed | day_4 PUBLISHED; saved/uploaded video not confirmed |
| Day 5 | DONE (`951e65c`) | Backend 46 / frontend 15 tests + builds PASS; OpenAI access VERIFIED; experiment ACCEPTED — 7 completed / 9 attempts; browser replay/refresh PASS | Quality OWNER APPROVED; report and day_5 PUBLISHED; organizer submission not confirmed |
| Day 6 | DONE (`2966cbe`) | Backend 58 / frontend 17 tests + builds PASS; real API and UI 1440×1000 PASS; 5 total real provider calls; TECHNICALLY COMPLETE; video postponed by owner | NOT PUBLISHED |
| Day 7 | DONE (`b2eb28e`) | Backend 62 tests PASS; real restart/provider persistence PASS; 3 real provider calls | NOT PUBLISHED |
| Day 8 | DONE (uncommitted) | Backend 67 / frontend 17 tests and builds PASS; real API/UI 1440×1000 PASS; 5 provider calls | NOT PUBLISHED |
| Day 9 | PLANNED | Full versus summary + recent context comparison | NOT STARTED |
| Day 10 | PLANNED | Sliding Window, Sticky Facts and Branching comparison | NOT STARTED |

Day 6 2026-09-10: [canonical task](tasks/DAY-06.md), [implementation evidence](agent-runs/DAY-06.md).
Runtime-only agent per existing UUID, immutable config (model/systemPrompt/temperature/maxTokens),
whole conversation per turn, success-only commit and per-agent tryLock. Fresh backend/service clears
LLM memory. Day 7 persists the full raw stack separately under `docs/local/agent-histories/`; existing
UI JSON archive remains separate. Automated tests and real restart/provider persistence verification pass.
Commit `2966cbe` contains the Day 6 feature. Real API and desktop UI verification PASS; total 5
real provider calls. Video is postponed by owner. Frontend build passed with existing app.scss budget warning.

## Week 2 agent roadmap

**Memory** — canonical conversation state retained by the application. **Context** — representation
selected or built from memory for one LLM call. **Metrics** — observations about request, context
and response token usage.

Day 6 keeps runtime raw memory. Day 7 makes the complete ordered raw message stack durable. Day 8
observes the unchanged full context without reducing it: local estimates and optional provider usage
are observations, not memory. Day 9 first derives context from memory:
full history versus summary plus latest N messages; raw memory remains canonical. Day 10 compares
Sliding Window, Sticky Facts / Key-Value Memory and Branching. Branching has checkpointed independent
continuations and switching, and need not share an abstraction with linear context policies.

Branch progression: `day_6` → `day_7` → `day_8` → `day_9` → `day_10`. Days 1–5 remain separate
experiments; they are not restructured into the agent flow.

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
Publication/integration checkpoint 2026-09-07: `day_1` already matched origin; `day_2`–`day_5`
normally pushed with owner authorization. `developer` fast-forwarded from `c633ee6` to
`06355effc56d60b4d43b58c7e274b0ec20622337` and pushed; origin/developer matched.
All five day branch heads are ancestors. `main` untouched at `45c62e11d6c8ff36f64579809e59d30db6b148df`.
No tests or API calls for publication/integration. Local raw evidence/secrets were not published.
This documentation update is uncommitted on `day_6`; published day branches keep their historical docs.

Day 1–4 Chrome demos ran after explicit START with real DeepSeek responses. OBS was owner-controlled;
agent cannot confirm recording files, uploaded video links or organizer acceptance from UI execution alone.
Runtime stopped by owner request: no listeners on 18080/4201 at checkpoint; no other projects stopped.

Published branches: https://github.com/nikita11174/ai-advent/tree/day_1 through `day_5`.
Published Day 5 report: https://github.com/nikita11174/ai-advent/blob/day_5/docs/DAY-05-REPORT.md.

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
