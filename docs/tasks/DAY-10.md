# Day 10 — context-management comparison

Статус: **PLANNED**. Branch progression: `day_9` → `day_10`.

## OFFICIAL REQUIREMENT

Реализовать и сравнить Sliding Window, Sticky Facts / Key-Value Memory и Branching по answer
quality, preservation важных деталей, token usage и practical usability. Branching требует
checkpoint, две независимые continuations и switching между ними.

## ORGANIZER CLARIFICATION

Нет дополнительных требований организатора сверх предоставленного owner текста.

## APPROVED PROJECT INTERPRETATION

Использовать raw canonical memory, сохранённую в Day 7, как source для подходов. Multiple actual
linear context-building behaviors могут оправдать policy abstraction. Branching не следует
принудительно включать в неё, если его conversation topology требует естественной отдельной модели.

## OUT OF SCOPE

Destructive replacement raw memory, ретроспективная перестройка Days 1–5 и abstractions, которые
не нужны фактическим Day 10 approaches.
