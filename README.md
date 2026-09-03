# AI Advent Challenge 9

Чистый sandbox-проект для последовательного выполнения заданий AI Advent Challenge 9.

Repository: https://github.com/nikita11174/ai-advent

Проект независим от рабочих PSP, LK и UCS репозиториев. Сюда нельзя переносить их бизнес-код,
историю задач, credentials, локальные базы данных или company-specific данные.

Это один развивающийся проект для ежедневных заданий challenge. Первый baseline — Java 21,
Spring Boot, Angular 22 и вызов DeepSeek REST API для Day 1. Claude Code и Codex —
взаимозаменяемые исполнители; продолжение работы опирается на состояние репозитория, а не на
память конкретной модели.

Product direction и правила постепенного развития продукта зафиксированы в
[`docs/PRODUCT-MANIFEST.md`](docs/PRODUCT-MANIFEST.md).

## Начало работы

1. Прочитать `AGENTS.md`.
2. Следовать краткому entrypoint `docs/SESSION_START.md`.
3. Проверить `git status` и `git diff` перед изменениями.

## Day 1

Требования:

- Java 21 and Maven;
- Node.js 22.22.3 and npm 10.9.8 (`nvm use 22.22.3`);
- `DEEPSEEK_API_KEY` in the ignored repository-root `.env.local`.

Day 1 демонстрирует минимальный web vertical slice: Java-код или инженерный вопрос из русского
Angular chat проходит через Spring Boot `/api/review` в DeepSeek, а Markdown-анализ возвращается
в браузер. Диалоги сохраняются локально, но не отправляются модели как conversation memory.

Backend build и tests:

```powershell
cd E:\sandbox\sandbox\ai-advent
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean package
```

Frontend install, tests и build:

```powershell
cd E:\sandbox\sandbox\ai-advent\frontend
nvm use 22.22.3
npm ci
npm test -- --watch=false
npm run build
```

### Локальный web-запуск

1. Скопировать `.env.example` в `.env.local` и заменить placeholder своим ключом. `.env.local`
   игнорируется Git; ключ нельзя добавлять в tracked-файлы или показывать в выводе.
2. Запустить backend в первом PowerShell:

```powershell
cd E:\sandbox\sandbox\ai-advent
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\scripts\run-backend.ps1
```

Backend слушает `http://localhost:18080`. Launcher загружает ключ только в environment дочернего
процесса и не печатает его. Java читает ключ только через
`System.getenv("DEEPSEEK_API_KEY")`.

3. Запустить frontend во втором PowerShell:

```powershell
cd E:\sandbox\sandbox\ai-advent\frontend
nvm use 22.22.3
npm start
```

4. Открыть `http://localhost:4200`. Angular dev proxy направляет `/api` на backend без CORS и
hardcoded backend URL в application code.

Первоначальный CLI остаётся доступен через `.\scripts\run.ps1`, но основной demo — web UI.

## Day 2

В интерфейсе доступны режимы `Свободный` и `Контролируемый`. Контролируемый режим позволяет
изменить лимиты JSON-ответа текущего запроса, сбросить их к defaults и увидеть одновременно
структурированный review и точный raw JSON. `Сравнить режимы` отправляет один input независимо в
оба режима и показывает результаты рядом. Настройки живут только в текущей вкладке; завершённые
exchanges сохраняются в локальном диалоге.

Команды build и локального запуска не изменились. При занятом `4200` можно запустить AI Advent на
другом порту, например:

```powershell
npm start -- --port 4201
```

## Day 3

Эксперимент `Стратегия анализа` запускает один input через `Прямой`, `Пошаговый`, `Самопромпт`
или `Эксперты`; сравнение всех стратегий использует четыре независимых результата и пять LLM calls.
PaymentReceived benchmark вставляется из UI. SELF_PROMPT показывает generated prompt, а comparison
сопоставляется вручную с фиксированным reference checklist без псевдоскоров.

`Новый диалог` создаёт JSON под ignored `docs/local/mentor-dialogs/`. Список восстанавливается
после refresh/restart; сохранённая история существует для UI и не включается в DeepSeek request.

Codex CLI `0.151.0` не предоставляет документированного transcript export. Для будущего сохранения
явно подготовленных prompt/final-report файлов используется:

```powershell
.\scripts\save-codex-session-evidence.ps1 -PromptFile .\prompt.md -FinalReportFile .\report.md -SessionId '<id>'
```

Результат записывается в ignored `docs/local/agent-sessions/`. Helper блокирует несколько явных
форматов secrets, но входные файлы всё равно нужно проверить вручную.
