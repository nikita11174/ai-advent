# Reusable Workflow References

Это ссылки на локальные reference-документы. Они не являются инструкциями этого проекта и не
должны копироваться сюда целиком.

| Reference | Что переиспользовано | Что сознательно не переносится |
|---|---|---|
| `E:\sandbox\sandbox\docs\agent-workflow\GLOBAL-SANDBOX-AGENT-WORKFLOW.md` | Иерархия актуального state, startup-read, проверка dirty tree, сохранение чужой работы, minimal handoff, evidence и optional review по конкретной причине | PSP/LK/UCS routing ролей, IntelliJ/runtime/DB policies и sandbox-specific authorization details |
| `E:\sandbox\sandbox\docs\agent-workflow\ENGINEERING-QUALITY-MANIFEST.md` | Минимальный diff, KISS/YAGNI, хирургический scope, acceptance criteria до реализации, targeted verification | Языковые/framework-specific правила и любые нормы, не нужные до выбора стека |
| `E:\sandbox\sandbox\docs\agent-workflow\CURRENT-STATE.md` | `CURRENT-STATE.md` как компактный authoritative snapshot, а не changelog | Состояние rollout Agent Skills и история universal workflow |
| `E:\sandbox\sandbox\psp-sanbox\psp\docs\psp\_meta\reading-policy.md` | Current docs выше historical notes; evidence относится к конкретному прогону; архив читается только по необходимости | PSP task taxonomy, runtime knowledge base и stale-pattern правила конкретной задачи |
| `E:\sandbox\sandbox\psp-sanbox\psp\docs\psp\PSP-5662\00-START-HERE.md` и `PSP-5662-CURRENT-TRUTH.md` | Короткий task entrypoint, явный next step, разделение текущих фактов и исторического контекста | PSP business decisions, implementation plan, test counts, endpoints, DB/runtime данные |
| `E:\sandbox\sandbox\lk-sandbox\docs\lk\LK-295\assistant-notes\LK-715-PORT-RUN\AGENT-WORKFLOW.md` и `CURRENT-STATE.md` | Один operational source of truth и передача через фактическое состояние, отчёт о выполненном вместо переписывания плана | Жёстко назначенные model roles, поэтапная owner-авторизация, нумерованные отчёты и LK security/runtime/business specifics |
| `E:\sandbox\sandbox\lk-sandbox\docs\SESSION_START.md` | Идея короткого session bootstrap и чтения только нужного task context | Устаревающие active-task статусы, LK команды запуска, порты и окружение |

## Актуальность и конфликты

Наиболее актуальные universal sources — три файла в
`E:\sandbox\sandbox\docs\agent-workflow\`: их snapshot и filesystem timestamps относятся к
2026-09-03. Root `SANDBOX_DOCS_MAP.md` также помечает старые PSP/LK indexes как stale или
требующие проверки, поэтому они не использовались как источник процесса.

Обнаружен содержательный конфликт поколений workflow: LK task workflow от 2026-08-06 закрепляет
Sonnet как основного исполнителя, Codex — только как specialist, и требует нумерованный отчёт на
каждый subtask. Более свежий universal workflow уже запрещает превращать independent review в
ритуал, но всё ещё описывает project-specific routing. Для AI Advent выбран явно заданный
владельцем model-agnostic вариант: Claude Code и Codex взаимозаменяемы, а отдельный review и
расширенный отчёт появляются только при обоснованной необходимости.

PSP `docs/psp/README.md`, `PROJECT_INDEX.md`, `current-sources-index.md`, `docs-inventory.md`,
старые handoff и `assistant-notes` не считаются актуальным universal source: карта документации
прямо отмечает часть из них как stale/historical. Из них не переносились статусы задач или
business/runtime данные.

## External API references

Official DeepSeek documentation used for Day 1:

- [Your First API Call](https://api-docs.deepseek.com/) — base URL,
  Bearer authentication, Chat Completions request and non-streaming response example.
- [Chat Completions API](https://api-docs.deepseek.com/api/create-chat-completion/) — endpoint,
  request fields, `thinking` toggle and response shape.
- [Thinking Mode](https://api-docs.deepseek.com/guides/thinking_mode/) — explicit
  `thinking.type = disabled` contract.
- [Models & Pricing](https://api-docs.deepseek.com/quick_start/pricing) — current model IDs and
  legacy alias deprecation; pricing is not part of the Day 1 contract.

Документация API изменяема; перед будущим изменением provider/model контракт следует сверить с
official sources повторно. Большие фрагменты внешней документации в репозиторий не копируются.
