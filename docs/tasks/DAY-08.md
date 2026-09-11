# Day 8 — context token observability

Статус: **PLANNED**. Branch progression: `day_7` → `day_8`.

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
