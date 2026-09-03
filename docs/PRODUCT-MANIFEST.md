# Engineering Review Mentor — Product Manifest

Живой документ product direction для AI Advent Challenge 9. Он определяет WHY продукта и
направляет выбор между одинаково простыми реализациями, но не заменяет требования текущего Day.

## Product thesis

AI ускоряет разработку, но engineering judgment разработчика не должен отставать от способности
AI анализировать и ревьюить код. **Engineering Review Mentor** использует реальную разработку как
тренировочный материал, чтобы разработчик сам лучше замечал дефекты, нарушенные business/system
invariants, edge cases, последствия для соседних компонентов, transaction/concurrency/
idempotency/data-flow risks и долгосрочное влияние решений.

Главный обучающий вопрос: **«Как я сам мог до этого додуматься?»**

Продукт не заменяет human review. В learning-flow разработчик сначала формулирует собственный
анализ, затем получает независимый AI feedback. Обычную разработку нельзя искусственно тормозить,
если обучение не является целью действия.

## Long-term vision

Зрелый цикл: самостоятельный review реального diff/task → независимый AI review → сравнение
замеченного, пропущенного и ложных предположений → перенос findings в объяснимые engineering
heuristics → собственный вывод разработчика → поздняя проверка переноса знания на другой контекст.
Наблюдаемое evidence постепенно формирует профиль сильных и слабых областей и может помогать в
подготовке к code-review/system-design интервью.

Daily/work summary и personal learning summary — возможный побочный эффект, не основная ценность.

## Challenge development strategy

AI Advent Challenge — roadmap driver. Long-term vision заранее не реализуется.

```text
Challenge acceptance criteria
  -> minimum useful implementation
  -> small reusable product increment
```

Для каждого Day:

1. Буквально определить требования и acceptance criteria текущего задания.
2. Полностью закрыть их минимальным решением.
3. Среди одинаково простых вариантов выбрать естественно приближающий продукт к Mentor.
4. Не добавлять будущую функциональность и premature architecture для agents, MCP, RAG или
   local LLM.
5. Разрешить следующему заданию изменить предыдущую архитектуру; рефакторить только при возникшей
   необходимости.

Формула: **не MVP всего продукта, а MVP текущего шага продукта**.

Примеры направления, не roadmap: Day 1 может быть простым `developer input -> LLM -> response` с
инженерным framing; structured output позднее может оформить review result; сравнение prompting
approaches — улучшить поиск проблем. Review agent, repository context через MCP и база heuristics
через RAG появляются только тогда, когда этого требует соответствующий Day.

## Decision order

При выборе реализации соблюдать порядок:

1. Полное выполнение текущего Challenge Day.
2. Минимальный scope.
3. Простота verification/demo.
4. Совместимость с product direction.

Перед добавлением функции спросить:

1. Требуется ли она текущим Challenge Day?
2. Если нет, решает ли она конкретную текущую проблему, без которой нельзя двигаться?

Если оба ответа «нет» — функцию сейчас не реализовывать.

## Success and evidence

Успех означает, что каждый Day выполнен по своим требованиям, а последовательность increments
сложилась в полезный developer tool, а не набор независимых demo. Ценность — со временем
самостоятельно лучше анализировать код, замечать системные риски и аргументировать решения.

Skill tracking, если появится, основывается на наблюдаемом evidence: какие классы проблем
разработчик замечает или повторно пропускает, объясняет ли root cause, выводит ли transferable
heuristic и применяет ли её в новом контексте. Псевдоточные оценки вроде «Senior level 73%» без
объяснимой доказательной базы запрещены.

## Non-goals

- замена human code review или автоматическое исправление всего кода;
- максимизация количества findings;
- enterprise platform или сложный frontend;
- поддержка PSP/LK/UCSCONV и хранение proprietary company knowledge;
- заранее построенные agent architecture, RAG или MCP layer;
- skill scores без evidence.

## Relationship with agents

Claude Code и Codex — взаимозаменяемые implementation executors. Они строят продукт, но не
определяют vision самостоятельно. Перед реализацией каждого Day исполнитель читает этот manifest,
текущий task и `docs/CURRENT-STATE.md`.
