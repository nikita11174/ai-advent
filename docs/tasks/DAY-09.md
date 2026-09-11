# Day 9 — summary plus recent context

Статус: **PLANNED**. Branch progression: `day_8` → `day_9`.

## OFFICIAL REQUIREMENT

Сохранять latest N messages, отдельно summarise older history и отправлять summary + recent
messages вместо full history. Сравнить token usage и answer quality с вариантом без compression.

## ORGANIZER CLARIFICATION

Нет дополнительных требований организатора сверх предоставленного owner текста.

## APPROVED PROJECT INTERPRETATION

Day 9 впервые вводит явное memory → context preparation: сравниваются FULL и SUMMARY + RECENT.
Summary — derived state; raw durable memory Day 7 остаётся источником для повторной подготовки и
comparison и не заменяется destructively.

## OUT OF SCOPE

Sliding Window, Sticky Facts / Key-Value Memory, Branching, удаление raw history и превращение
Days 1–5 в agent experiments.
