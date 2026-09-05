# Day 4 — Temperature

Статус: **READY_FOR_IMPLEMENTATION**

## 1. Original challenge requirement

Один и тот же запрос выполнить с `temperature = 0`, `0.7`, `1.2`. Сравнить точность,
креативность и разнообразие; сформулировать, для каких задач подходит каждая настройка.
Результат: примеры ответов и выводы. Submission: видео + код.

## 2. Organizer/chat evidence

- Official task: Telegram export message `1642`.
- Письменное задание редактировалось после видео (`1759`), поэтому текущий текст с `1.2` —
  canonical requirement.
- Автоматический выбор temperature через дополнительный classifier/model обсуждался как возможный
  two-chain approach (`1796`), но это не требование Day 4 и увеличивает scope.

## 3. Product increment

Отдельный эксперимент `Температура` в Engineering Review Mentor. Один PaymentReceived review
анализируется с тремя temperature при неизменных model, exact input, system prompt, presentation,
thinking mode и остальных sampling parameters.

## 4. Benchmark and fixed prompt

Используется exact PaymentReceived snippet и reference checklist из Day 3. System prompt просит
по-русски в Markdown выделить реальные engineering risks, practical reliability improvements и
отделить подтверждённые проблемы от assumptions. Reference checklist модели не передаётся.

## 5. API and experiment topology

```http
POST /api/temperature-review
{"input":"exact snapshot","temperature":0.7}
```

```json
{"temperature":0.7,"analysis":"Russian Markdown"}
```

- Допустимые temperature: `0`, `0.7`, `1.2`.
- Single selected temperature: 1 DeepSeek call.
- Compare all: три независимых frontend calls; backend compare endpoint не добавляется.
- DeepSeek request содержит `temperature`, `thinking.type=disabled`, current model и `stream=false`.
- `top_p`, penalties, seed и другие sampling controls не задаются и потому одинаково отсутствуют.

## 6. UX and persistence

- Experiment tabs: `Формат ответа`, `Стратегия анализа`, `Температура`.
- Selector: `0`, `0.7`, `1.2`.
- Actions: `Проанализировать`, `Сравнить температуры`.
- Compare result: три independently loading/error/result cards; desktop — три columns, narrow —
  stack. Partial failure не скрывает successful cards.
- Exchange сохраняет immutable input, temperature/results и human observations в существующем
  local dialog store. Persisted dialog — UI history, не LLM conversation memory.

## 7. Evaluation

Для каждого result пользователь сохраняет human-readable observations:

- accuracy: expected found, expected missed, questionable claims;
- creativity: useful alternative remedies и novel defensible ideas;
- diversity: meaningful differences между independent generations;
- общий conclusion и suitable task types.

Fake numeric scores и authoritative LLM judge не используются. Выводы относятся только к
зафиксированному experiment evidence.

## 8. Acceptance criteria

1. Exact input независимо уходит с temperature `0`, `0.7`, `1.2`.
2. Между calls намеренно меняется только temperature; thinking остаётся disabled.
3. Selected analysis и three-way comparison доступны в UI; partial failure изолирован.
4. Русский safe Markdown и существующий chat/scroll/composer behavior сохраняются.
5. Day 4 snapshots и human conclusions восстанавливаются из local dialog JSON.
6. Старые dialog messages не отправляются модели.
7. Backend/frontend tests и builds проходят; Day 1–3 regressions проходят.
8. Выполнены 3 independent runs на каждую temperature — 9 real calls — и записаны фактические
   accuracy/creativity/diversity conclusions.
9. Secret и local evidence не попадают в Git.
10. Submission содержит video и repository/code link.

## 9. Non-goals

- temperature × FREE/CONTROLLED/reasoning matrix;
- automatic temperature classifier или second model;
- generic provider/model framework и Day 5;
- LLM-as-a-judge, fake scores или guaranteed deterministic output at temperature 0;
- conversation memory, database, auth, cloud persistence;
- изменение Day 1–3 contracts.

## 10. Open decisions

Open product decisions: **NONE**. Implementation blockers: **NONE**.

## 11. Implementation and verification evidence

Заполняется только фактическими результатами implementation round.

## 12. Submission status

- [ ] implementation
- [ ] automated tests/builds
- [ ] 9-call real experiment
- [ ] owner visual browser review / demo video
- [ ] repository/code publication

DAY 4 IMPLEMENTATION = **NOT_STARTED**

DAY 4 VERIFICATION = **NOT_STARTED**

DAY 4 SUBMISSION = **PENDING**
