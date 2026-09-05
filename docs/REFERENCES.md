# Reusable Workflow References

Это ссылки на локальные reference-документы. Они не являются инструкциями этого проекта и не
должны копироваться сюда целиком.

| Reference | Что переиспользовано | Что сознательно не переносится |
|---|---|---|
| `<local-workspace>` | Иерархия актуального state, startup-read, проверка dirty tree, сохранение чужой работы, minimal handoff, evidence и optional review по конкретной причине | <external-project>/<external-project>/<external-project> routing ролей, IntelliJ/runtime/DB policies и sandbox-specific authorization details |
| `<local-workspace>` | Минимальный diff, KISS/YAGNI, хирургический scope, acceptance criteria до реализации, targeted verification | Языковые/framework-specific правила и любые нормы, не нужные до выбора стека |
| `<local-workspace>` | `CURRENT-STATE.md` как компактный authoritative snapshot, а не changelog | Состояние rollout Agent Skills и история universal workflow |
| `<local-workspace>` | Current docs выше historical notes; evidence относится к конкретному прогону; архив читается только по необходимости | <external-project> task taxonomy, runtime knowledge base и stale-pattern правила конкретной задачи |
| `<local-workspace>` и `<external-project>.md` | Короткий task entrypoint, явный next step, разделение текущих фактов и исторического контекста | <external-project> business decisions, implementation plan, test counts, endpoints, DB/runtime данные |
| `<local-workspace>` и `CURRENT-STATE.md` | Один operational source of truth и передача через фактическое состояние, отчёт о выполненном вместо переписывания плана | Жёстко назначенные model roles, поэтапная owner-авторизация, нумерованные отчёты и <external-project> security/runtime/business specifics |
| `<local-workspace>` | Идея короткого session bootstrap и чтения только нужного task context | Устаревающие active-task статусы, <external-project> команды запуска, порты и окружение |

## Актуальность и конфликты

Наиболее актуальные universal sources — три файла в
`<local-workspace>`: их snapshot и filesystem timestamps относятся к
2026-09-03. Root `SANDBOX_DOCS_MAP.md` также помечает старые <external-project>/<external-project> indexes как stale или
требующие проверки, поэтому они не использовались как источник процесса.

Обнаружен содержательный конфликт поколений workflow: <external-project> task workflow от 2026-08-06 закрепляет
Sonnet как основного исполнителя, Codex — только как specialist, и требует нумерованный отчёт на
каждый subtask. Более свежий universal workflow уже запрещает превращать independent review в
ритуал, но всё ещё описывает project-specific routing. Для AI Advent выбран явно заданный
владельцем model-agnostic вариант: Claude Code и Codex взаимозаменяемы, а отдельный review и
расширенный отчёт появляются только при обоснованной необходимости.

<external-project> `docs/<external-project>/README.md`, `PROJECT_INDEX.md`, `current-sources-index.md`, `docs-inventory.md`,
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

## AI Advent organizer/chat evidence

Локальный Telegram export: `<local-workspace>\Downloads\Telegram Desktop\ChatExport_2026-09-05\result.json`.
Он содержит группу **AI Advent Challenge #9** до 2026-09-05: исходные задания и пояснения Алексея
Гладкова, создателя курса.

- Тексты от `Mobile Developer Manager` и прямые пояснения Алексея — organizer evidence.
- Решения и результаты участников — community evidence, а не требования.
- Message IDs сохраняются в task docs для локальной проверки provenance.
- Личные и нерелевантные сообщения, usernames и вложения не переносятся.
- Сам export остаётся локальным reference и не добавляется в Git.

Проверенные anchors: Day 1 — `877`; Day 2 — `1160`, пояснения `1189–1219`, `1241–1246`,
`1293–1315`; Day 3 — `1474`, пояснения `1501–1502`, `1520`; model/session context —
`1399–1403`, `1479`, `1492–1498`; Day 3 community experiments — `1560–1565`, `1577–1584`,
`1638–1639`.

Уже опубликованные следующие задания, пока только как navigation evidence:

- Day 4, temperature `0 / 0.7 / 1.2` — `1642`; Алексей подтвердил, что письменное задание было
  отредактировано после видео (`1759`), поэтому текущий текст задания приоритетнее старого видео;
- Day 5, сравнение слабой/средней/сильной модели — `1798`; ссылки ожидаются на выбранные модели
  (`1812`), HuggingFace пояснён как каталог моделей (`1825–1826`), а «сила» — совокупность
  параметров, quantization, context window и других характеристик (`1832`).

Эти anchors не являются утверждённым планом Day 4/5 и не разрешают premature implementation.
