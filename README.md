# To-Do List & Task Manager
### Smart Task Management & Productivity System

## 1. Project Title
**To-Do List & Task Manager – Smart Task Management & Productivity System**

## 2. Team Details
- **Project Type:** Individual Java Mini Project
- **Department:** Electrical and Electronics Engineering (EEE)
- **Institution:** KIT - KalaignarKarunanidhi Institute of Technology

## 3. Team Number
- **Team Number:** Individual Project 

## 4. Name of Team Member
- **Name:** MEENA S

## 5. Register Number
- **Register Number:** 711525BEE039

## 6. Brief Project Description
The To-Do List & Task Manager is a Java-based web application designed to help users organize, manage, and track their daily tasks efficiently. It provides a user-friendly interface for creating, updating, completing, and deleting tasks.

The application uses a Spring Boot backend and an H2 database to manage task information. It aims to improve productivity through structured task management and a simple, responsive web interface. Users can register, log in, and manage their tasks through the application.

## 7. Technologies / Tools Used

### Frontend
- HTML5
- CSS3
- JavaScript
- Bootstrap 5
- Bootstrap Icons
- Fetch API

### Backend
- Java 17 (current working configuration)
- Spring Boot 3.3.5
- Spring MVC
- Spring Security
- Spring JdbcTemplate
- JDBC

### Database
- H2 Database (file-based)

### Development Tools
- Visual Studio Code (VS Code)
- Gradle
- Gradle Wrapper
- JUnit 5
- Spring Boot Test

## How to Run the Project
1. Open the project folder in VS Code.
2. Open the integrated terminal.
3. Navigate to the folder containing `gradlew.bat`.
4. Run the following command:

   `gradlew.bat bootRun --args="--server.port=9090"`

5. Open your browser and visit:

   `http://localhost:9090`

**Note:** The project currently runs using Java 17. The database is configured with H2, so a separate MySQL installation is not required for this configuration.

## 1. Features

| Area | What you get |
|---|---|
| **Accounts** | Register, log in, log out. Passwords hashed with BCrypt. Session cookie + CSRF protection. Every user sees only their own data. |
| **Dashboard** | Total / completed / pending / in-progress / overdue / due-today counts, completion %, priority doughnut chart, weekly created-vs-completed bar chart, 7-day productivity trend, upcoming deadlines, recent activity, quick-add form, empty states. |
| **Tasks** | Create, view, edit, delete (with confirmation), complete / reopen. Title, description, status (To Do / In Progress / Completed), priority (Low–Urgent), due date, category, multiple tags. Search, filter (status, priority, category, tag, due date) and sort (due date, priority, created, title). |
| **Kanban** | Three real-data columns. Move cards by drag-and-drop **or** the status menu on each card. Failed saves are never shown as saved. |
| **Calendar** | Monthly grid, previous/next/today, priority colours, overdue highlight, click a task to open it, “+” to add a task on a date, day panel. |
| **Subtasks** | One level of nesting, progress bar, add / rename / complete / delete. Deleting a parent deletes its subtasks (no orphans). |
| **Categories & tags** | Colour categories (add/edit/delete), reusable per-user tags. |
| **History** | Timeline of created / edited / priority / due-date / status / completed / reopened / subtask / deleted events, newest first. History is kept after a task is deleted. |
| **CSV export** | Download all your tasks; correct escaping; protected against spreadsheet formula injection. |
| **Dark mode** | Toggle in the top bar; remembered in the browser; charts adapt. |

## 2. Technology stack

Frontend: HTML5, CSS3, vanilla JavaScript, Bootstrap 5.3, Bootstrap Icons, Fetch API, Chart.js 4 (all libraries are *bundled* in `static/vendor`, so the app also works offline).
Backend: Java 17, Spring Boot 3.3.5, Spring MVC, Spring Security, Spring `JdbcTemplate`/JDBC, Bean Validation.
Database: embedded H2 (file mode). Build: Gradle 8.10.2 via the included Gradle Wrapper. Tests: JUnit 5 + Spring Boot Test.

## 3. Prerequisites (Windows)

1. **JDK 17** (Temurin / Microsoft / Oracle). Verify in *Command Prompt* or the VS Code terminal:
   ```
   java -version
   ```
   It must print version `17.x`. If `java` is not recognised, install a JDK 17 and tick *“Set JAVA_HOME”* / *“Add to PATH”* in the installer, then open a **new** terminal.
2. **VS Code** with the *Extension Pack for Java* (optional but recommended).
3. An **internet connection for the first run only** — Gradle and the Java libraries are downloaded once and cached in `C:\Users\<you>\.gradle`.

You do **not** need to install Gradle, Maven, MySQL, Node.js or Python.

## 4. Open and run the project

1. Unzip `ToDoListProject.zip`. Avoid very long paths and avoid folders synced by OneDrive.
2. VS Code → **File → Open Folder…** → choose the `ToDoListProject` folder.
3. Open the terminal (**Terminal → New Terminal**) and run:
   ```
   gradlew.bat bootRun
   ```
   The first run downloads Gradle (~130 MB) and dependencies, so it takes a few minutes. When you see `Started TodoApplication`, the app is ready.
4. Open your browser at **http://localhost:8080**
5. To **stop** the app press `Ctrl + C` in the terminal. To **restart** run `gradlew.bat bootRun` again — all data is still there.

> macOS / Linux: use `./gradlew bootRun` instead.

**Port 8080 already in use?** Run `gradlew.bat bootRun --args="--server.port=9090"` and open http://localhost:9090.

## 5. Using the application

1. **Register** (name, email, password: 8+ characters with a letter and a number). You are logged in automatically. Later use **Log in**.
2. **Dashboard** – use *Quick add* or the blue **New task** button. Click a stat card (e.g. *Overdue*) to jump to the filtered list.
3. **Tasks** – search, filter, sort. Click the circle to complete/reopen. Click a title for details, subtasks and history; pencil = edit; bin = delete (asks to confirm).
4. **Tags** – type comma-separated tags in the task form (`exam, lab`). Existing tags are reused (case-insensitive).
5. **Kanban** – drag a card or use its status drop-down.
6. **Calendar** – use the arrows to change month; click a day number to see that day; “+” adds a task for that date.
7. **Categories & Tags** – manage categories and tags; clicking a name filters the task list.
8. **Export CSV** – click **Export CSV** at the bottom of the sidebar. The browser downloads `tasks.csv` (opens in Excel/Sheets).
9. **Dark mode** – moon/sun icon in the top bar. **Log out** – click your avatar.

### Optional demo account
No password is built in. To create a demo user with sample tasks, *you* choose the password at start-up:
```
gradlew.bat bootRun --args="--app.demo.enabled=true --app.demo.password=ChooseYourOwn1"
```
Then log in as `demo@example.com` with the password you chose. (It is only created if it does not exist yet.)

## 6. How the H2 file database works

* The JDBC URL is `jdbc:h2:file:./data/tododb` (see `src/main/resources/application.properties`).
* `./` means the **folder the app is started from** — when you use `gradlew.bat bootRun` that is the `ToDoListProject` folder. Data is stored in **`ToDoListProject/data/tododb.mv.db`** (created automatically; the `data` folder is created if missing).
* `schema.sql` runs on every start but uses only `CREATE TABLE/INDEX IF NOT EXISTS`, so **existing data is never deleted**. To start from scratch, stop the app and delete the `data` folder.
* Only one copy of the app can open the database at a time (H2 file lock). Stop the old one before starting a new one.
* Back-up = copy `data/tododb.mv.db` while the app is stopped.
* Schema changes in a future version would be added as new `ALTER TABLE ... IF NOT EXISTS` statements at the end of `schema.sql`, never by dropping tables.

Tables: `users`, `categories`, `tasks` (tasks **and** subtasks, via `parent_id`), `tags`, `task_tags`, `task_history`. All user-owned rows carry `user_id`.

## 7. Time-zone and date behaviour

* Due dates are plain calendar dates (`YYYY-MM-DD`) — no time zone shifting.
* “Today”, **overdue** and all timestamps use the server time zone configured by `app.timezone` (default **Asia/Kolkata**). Change it in `application.properties`.
* **Overdue** = due date is *before today* **and** status is not *Completed*. Tasks with no due date are never overdue; a task due today is not overdue yet.
* “Due today” on the dashboard counts tasks due today that are not yet completed.
* Dashboard counts use top-level tasks only; subtasks are shown as progress inside their parent.

## 8. Running the automated tests

```
gradlew.bat test
```
Tests use a separate **in-memory** H2 database (profile `test`), so your real data in `./data` is never touched. They cover: context start-up, registration validation, password hashing, login/logout, unauthenticated access, isolation between two users, task CRUD & ownership, search/filter/sort, dashboard statistics, overdue rule, categories & tags, subtasks, history, CSV export (escaping, formula injection, isolation).
An HTML report is written to `build/reports/tests/test/index.html`.

## 9. Troubleshooting

| Problem | Fix |
|---|---|
| `'gradlew.bat' is not recognized` | The terminal is not in the project folder. `cd` into `ToDoListProject` (the folder that contains `gradlew.bat`). |
| `JAVA_HOME is not set` / wrong Java version | Install JDK 17, set `JAVA_HOME`, open a new terminal, check `java -version`. |
| `Could not resolve ...` / `Connection timed out` | First run needs internet. Check proxy/VPN/firewall and retry. |
| `Port 8080 was already in use` | Stop the other program or use `--server.port=9090` (see above). |
| `Database may be already in use` | Another copy of the app is running. Stop it (`Ctrl + C`) first. |
| Login page keeps showing after login | Browser blocks cookies for `localhost` – allow cookies. |
| Page looks unstyled | Hard refresh with `Ctrl + F5`. |
| “request was rejected (security token)” | Reload the page (the session expired). |

## 10. Architecture

```
Browser (HTML/CSS/JS, fetch)  →  Controller  →  Service  →  Repository  →  JdbcTemplate  →  H2 file DB
```

```
src/main/java/com/example/todo
├── TodoApplication.java         entry point
├── config/      SecurityConfig (login, logout, CSRF, headers), AppConfig (Clock/time zone)
├── security/    AuthUser, AppUserDetailsService (users from H2)
├── controller/  Auth, Task, Category, Tag, History, Export controllers  (HTTP only)
├── service/     AuthService, TaskService, CategoryService, TagService, HistoryService,
│                StatsService, CsvExportService, DemoDataSeeder          (business rules, transactions)
├── repository/  User, Task, Category, Tag, History, Stats repositories  (parameterised SQL)
├── model/       User, Task, Category, HistoryEntry, TaskStatus, TaskPriority
├── dto/         request/response objects (never contain password data)
└── exception/   ApiException, GlobalExceptionHandler (consistent JSON errors)
src/main/resources
├── application.properties, schema.sql
└── static/      index.html, login.html, css/style.css, js/script.js, js/auth.js, vendor/ (Bootstrap, Icons, Chart.js)
```

## 11. REST API

All endpoints except those marked *public* need a logged-in session. All `POST/PUT/PATCH/DELETE` calls need the CSRF header (the front end does this automatically: fetch the token from `GET /api/auth/csrf`, send it as header `X-CSRF-TOKEN`). Errors have the form `{"timestamp","status","error","message","fieldErrors"?}`.

| Method & path | Purpose |
|---|---|
| `GET /api/auth/csrf` *(public)* | Get CSRF token |
| `POST /api/auth/register` *(public)* | Register `{name,email,password}` → 201 / 400 / 409 |
| `POST /api/auth/login` *(public)* | Form login (Spring Security): fields `email`, `password` → 200 / 401 |
| `POST /api/auth/logout` | Log out → 204 |
| `GET /api/auth/me` | Current user |
| `GET /api/tasks` | List. Query: `q`, `status`, `priority`, `categoryId`, `tagId`, `due` (`today`,`overdue`,`week`,`none`,`YYYY-MM-DD`), `sort` (`dueDate`,`priority`,`createdAt`,`updatedAt`,`title`), `dir` (`asc`,`desc`), `includeSubtasks` |
| `GET /api/tasks/{id}` | Details incl. subtasks |
| `POST /api/tasks` | Create `{title,description,status,priority,dueDate,categoryId,tags[]}` → 201 |
| `PUT /api/tasks/{id}` | Update (missing status/priority/tags keep old values; missing dueDate/categoryId clear them) |
| `PATCH /api/tasks/{id}/status` | Kanban / complete / reopen `{status}` |
| `DELETE /api/tasks/{id}` | Delete (also deletes subtasks) → 204 |
| `GET /api/tasks/stats` | Dashboard statistics |
| `GET /api/tasks/calendar?year=&month=` | Tasks due in a month |
| `GET /api/tasks/{id}/history` | History of one task |
| `GET /api/tasks/{id}/subtasks` · `POST /api/tasks/{id}/subtasks` | List / add subtasks (edit, complete and delete use the task endpoints above) |
| `GET/POST /api/categories` · `PUT/DELETE /api/categories/{id}` | Categories `{name,color}` |
| `GET /api/tags` · `DELETE /api/tags/{id}` | Tags |
| `GET /api/history?limit=` | Activity timeline, newest first |
| `GET /api/export/tasks.csv` | CSV download |

Status codes: 200/201/204 success, 400 validation, 401 not logged in, 403 CSRF/forbidden, 404 not found **or not yours**, 409 duplicate, 500 server error.

## 12. Security design

* **Authentication:** Spring Security form login against the `users` table; BCrypt password hashes; generic “Invalid email or password” message; session id is changed on login.
* **Sessions & cookies:** `HttpOnly`, `SameSite=Lax`, 30-minute timeout. Set `server.servlet.session.cookie.secure=true` when serving over HTTPS.
* **CSRF protection is enabled** (token kept in the session, sent in a header). It is *not* disabled for convenience.
* **Authorisation:** every task/category/tag/history query includes `AND user_id = ?`. Another user’s record behaves like a missing one (404), so changing IDs in URLs reveals nothing. Category ownership is checked when assigning; subtasks are checked against their parent’s owner.
* **Validation & errors:** Bean Validation + explicit rules (status/priority/date/tag formats). Stack traces, SQL and hashes are never sent to the browser.
* **SQL injection:** only parameterised `JdbcTemplate` calls; sort columns come from a fixed whitelist; `LIKE` wildcards typed by users are escaped.
* **XSS:** all user text is HTML-escaped in the browser; a Content-Security-Policy (`script-src 'self'`) is sent.
* **CSV injection:** cells starting with `=`, `+`, `-`, `@`, tab or CR are prefixed with `'`.
* **Passwords** are never logged, stored in plain text, or returned by the API.

### Known limitations
* No password reset / e-mail verification / “remember me” and no login rate-limiting (acceptable for a local single-machine project; add before internet deployment).
* The H2 database uses the default `sa` user with an empty password and is not encrypted — protect the `data` folder, or set a DB password and `CIPHER` for real deployments.
* Subtasks are limited to one level; a subtask’s parent cannot be changed after creation.
* Only one application instance can use the H2 file at once.
* The server time zone (not each user’s browser) decides what “today” is.
* Drag-and-drop on touch screens depends on the browser; the status drop-down on every card always works.
* Task history is retained forever (no purge feature).

## 13. Verification notes (honest status)

The package was authored in a sandbox that could reach GitHub and npm but **not** Maven Central or `services.gradle.org`, so `gradlew.bat build` / `gradlew.bat test` could **not** be executed there. What *was* verified with real tools:

* The genuine Gradle 8.10.2 wrapper files were downloaded from the official Gradle repository (`gradle-wrapper.jar` is the real 43,583-byte file).
* `schema.sql` and every SQL query used by the repositories were executed against the real **H2 2.2.224** engine (the version Spring Boot 3.3.5 manages): schema is re-runnable, generated keys, LIKE-escaping, aggregates, ON DELETE CASCADE / SET NULL, history surviving deletion, unique and CHECK constraints.
* All Java sources parse without syntax errors; all JavaScript passes `node --check`.
* The real `index.html` + `script.js` were run in a DOM simulator against a fake API: all views render, user text is escaped, create/edit/delete/subtask/Kanban/theme flows work.

**Please run `gradlew.bat test` once on your machine** (needs internet the first time) and fix anything it reports before your demonstration.
