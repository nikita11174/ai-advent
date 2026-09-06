# AI Advent Challenge 9 — Day 5: сравнение моделей

Engineering Review Mentor. Эксперимент: 5 сентября 2026; вывод утверждён владельцем 6 сентября.

## Задание и метод

Отправить один запрос слабой, средней и сильной моделям через API; сравнить качество ответа,
время, токены и стоимость. В этой работе уровни относительны **внутри GPT-5.6 family**:
[Luna](https://developers.openai.com/api/docs/models/gpt-5.6-luna),
[Terra](https://developers.openai.com/api/docs/models/gpt-5.6-terra),
[Sol](https://developers.openai.com/api/docs/models/gpt-5.6-sol).

Benchmark `payment-received-v1`. Одинаковый пользовательский запрос:
«Проанализируй Java-код и найди инженерные риски. Не предполагай скрытые гарантии, которых нет в snippet.»

```java
@Transactional
public void handle(PaymentReceived event) {
    Order order = orders.findById(event.orderId()).orElseThrow();
    order.markPaid();
    email.sendReceipt(order.customerEmail());
}
```

Оценка по четырём фиксированным областям: duplicate delivery/idempotency, неатомарность email и
DB transaction, concurrent processing, email failure/rollback/reprocessing. Это условные риски:
скрытые delivery/locking/rollback гарантии не показаны. Reference checklist не передавался моделям.

Прямой OpenAI Responses API, exact IDs `gpt-5.6-luna`, `gpt-5.6-terra`, `gpt-5.6-sol`.
Общий preset `day5-model-review-v1`: `reasoning.effort=none`, `max_output_tokens=2000`,
`service_tier=default`, `store=false`, `stream=false`. Без tools, истории и sampling overrides.
Одинаковая developer-инструкция — найти реальные риски и практические улучшения,
отделить подтверждённое от assumptions, ответить на русском Markdown. Менялся только model ID.

Три последовательных раунда с ротацией порядка:

1. Luna → Terra → Sol.
2. Terra → Sol → Luna.
3. Sol → Luna → Terra.

Ровно **9 оплачиваемых попыток**, без retry, replacement и увеличения потолка.

## Результаты

Latency — backend wall-clock вокруг внешнего API request до полного body, не TTFT.
Медианы, токены и стоимость включают **все три попытки каждой модели**, в том числе incomplete.

| Модель | Completed / incomplete | Median API latency | Input / output / total tokens | Estimated USD |
|---|---|---:|---:|---:|
| GPT-5.6 Luna | 3 / 0 | 15.396 s | 384 / 3452 / 3836 | 0.0042192 |
| GPT-5.6 Terra | 1 / 2 | 19.793 s | 384 / 5660 / 6044 | 0.068688 |
| GPT-5.6 Sol | 3 / 0 | 14.282 s | 384 / 3578 / 3962 | 0.073096 |
| **Всего** | **7 / 2** | — | **1152 / 12690 / 13842** | **0.1460032** |

Terra в раундах 1 и 3 вернула `incomplete`, reason `max_output_tokens`, по 2000 output tokens.
Частичный анализ сохранён и отображается вместе с ошибкой, метриками и стоимостью; успешные
соседние карточки не теряются. Это **принятые наблюдения при общем preset**, не 9/9 успешных ответов.
Будущий эксперимент с другим потолком был бы отдельным и не заменял бы эти результаты.

Стоимость рассчитана backend через BigDecimal по snapshot
`openai-gpt56-standard-2026-09-05`, USD за миллион токенов:
Luna input/output 0.20/1.20; Terra 2.00/12.00; Sol 4.00/20.00.
Cached-read, cache-write и reasoning tokens фактически вернулись нулевыми во всех попытках.
Полный snapshot с источниками сохранён с каждым результатом.
**Это оценка по записанному тарифу, не сверка billing ledger.** Prepaid credits не означают бесплатный API.
Предварительный access smoke отдельно стоил USD 0.0000948; он не входит в девять попыток и таблицу.

## Качество и утверждённый вывод

- **Luna:** самая дешёвая; полезна для экономичного первичного engineering review. Все три ответа
  завершены, но раскрытие reference-рисков несколько менее стабильно, чем у Sol.
- **Terra:** доступный текст подробно раскрывает reference-риски, однако две попытки упёрлись
  в общий ceiling. На этом benchmark дополнительные затраты главным образом дали объём/детали,
  а не надёжное завершение. Это не универсальная характеристика Terra.
- **Sol:** все три ответа завершены, наиболее устойчиво покрывают четыре reference-области.
  Здесь лучший кандидат, когда надёжность покрытия важнее стоимости, но не универсальный победитель.

Для **этого benchmark и preset** family tier хорошо предсказывал стоимость и лишь частично —
стабильность качества; задержка не росла монотонно. MEDIUM не оказался автоматически практическим
компромиссом между WEAK и STRONG. Многословие не равно качеству: Luna — экономичный первый проход,
Sol — более надёжное по покрытию review здесь, Terra — подробный анализ с низкой эффективностью
завершения при общем потолке 2000.

## Ограничения и код

Всего один короткий benchmark и три запуска на модель; выводы ограничены `payment-received-v1`,
`reasoning.effort=none`, `max_output_tokens=2000`. Нет псевдоскоров, универсального ranking или
авторитетного LLM judge. Ответы содержали и спорные утверждения; даже Sol требует human review.
Провайдерская нагрузка и внутренние defaults могут влиять на задержку. Remote resource usage —
токены, API время, стоимость и число calls; GPU/RAM/энергия провайдера не измерялись.

UI показывает три независимые карточки и сохраняет результаты/метрики/оценку в локальных
JSON-диалогах; история не становится памятью модели. Automated builds/tests и browser
replay сохранённых реальных ответов с refresh проверены; дополнительных платных UI calls не было.

Код: [nikita11174/ai-advent](https://github.com/nikita11174/ai-advent).
Day 5 implementation checkpoint: `951e65c4516c4f01fb3321cbb86dc2574591d911`.
**Отчёт готов; публикация Day 5 commit/ветки пока PENDING** — ссылка на repository не означает,
что текущий локальный Day 5 уже опубликован.
