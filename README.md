# AI Advent Challenge 9

Чистый sandbox-проект для последовательного выполнения заданий AI Advent Challenge 9.

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
в браузер. Визуальная история хранится только в текущей вкладке и не отправляется модели.

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

Первоначальный CLI остаётся доступен через `.\scripts\run.ps1`, но основной Day 1 demo — web UI.
