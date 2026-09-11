# Day 7 — durable raw agent memory

Статус: **PLANNED — implementation not started**. Branch: `day_7` от documentation checkpoint
`c0d736369b7bfc033b59f6ee36ab43af5553b658`.

## OFFICIAL REQUIREMENT

Сохранить историю сообщений агента в JSON или SQLite. После restart приложения восстановить
историю, продолжить тот же dialog и отправить восстановленные предыдущие сообщения LLM.

## ORGANIZER CLARIFICATION

Нет дополнительных требований организатора сверх предоставленного owner текста: JSON или SQLite,
restore after restart и continuation same dialog.

## APPROVED PROJECT INTERPRETATION

JSON — минимальное решение. Один файл на dialog UUID в ignored
`docs/local/agent-histories/` хранит полный ordered raw stack сообщений, включая system message.
Это canonical **memory**, отдельная от UI dialog archive. При создании нового runtime agent service
валидирует dialog через существующий `DialogStore`, загружает history или создаёт fresh system
context при отсутствии файла. После успешного provider response completed user/assistant pair
сохраняется atomic file replace и фиксируется в runtime context; следующий LLM request использует
восстановленный полный stack.

## OUT OF SCOPE

Token/context metrics, limits, trimming, summary, compression, Sliding Window, Sticky Facts,
Branching, новая UI/API contract и перенос Days 1–5 в agent architecture.
