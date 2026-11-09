# DevTrack Study Guide

A personal project/dev-log tracker. Server-rendered Java web app: **no React, no JS framework** — the server builds the HTML.

---

## 0. Cheat sheet (read this first)

| Layer | Tech | Where to look |
|---|---|---|
| Language / runtime | Java 17 | `pom.xml` (`java.version`) |
| Framework | Spring Boot 3.2.5 | `pom.xml` parent |
| Web / routing | Spring MVC (`@Controller`) | `*Controller.java` |
| UI | Thymeleaf (server-side HTML templates) | `src/main/resources/templates/` |
| Styling | One hand-written CSS file, no framework | `static/css/main.css` |
| Database | PostgreSQL 16 | `application.yml` |
| ORM | Spring Data JPA + Hibernate | `*Repository.java`, entities |
| Migrations | Flyway (versioned SQL) | `db/migration/V1__…`, `V2__…` |
| Auth | Spring Security form login + BCrypt | `security/SecurityConfig.java` |
| Markdown | flexmark | `project/MarkdownService.java` |
| CSV/Excel import | OpenCSV + Apache POI | `feature/FeatureImportService.java` |
| AI assistant | Raw HTTP to an OpenAI-style `/chat/completions` API (OpenCode Zen, model `big-pickle`) | `ai/` package |
| Boilerplate killer | Lombok (`@Getter @Setter @RequiredArgsConstructor`) | all over |
| Build | Maven, packaged as one fat JAR | `pom.xml`, `Dockerfile` |
| Deploy | Docker + docker-compose on a Tencent Cloud Lighthouse Ubuntu box | `Dockerfile`, `docker-compose.yml` |

**Default login:** `admin` / `devtrack123` (seeded by `DataSeeder`, overridable via `app.admin.*`).
**Runs on:** port `8080`.

---

## 1. The big picture

```
Browser
  │  GET /projects/5
  ▼
Spring Security filter chain  ── not logged in? → redirect /login
  ▼
ProjectController  (@GetMapping("/{id}"))
  ▼
ProjectService  ──►  ProjectRepository (Spring Data JPA)  ──►  PostgreSQL
  ▼
Model (key/value bag)  ──►  Thymeleaf template  project/detail.html
  ▼
HTML response
```

This is classic **MVC**:
- **Model** = entities (`Project`, `Feature`, `LogEntry`, `User`)
- **View** = Thymeleaf templates
- **Controller** = classes with `@Controller` that return a *template name* (or `redirect:/...`)

Pattern you'll see in every controller:
1. Read request (`@PathVariable`, `@RequestParam`, `@ModelAttribute`)
2. Call a service/repository
3. Put results in `Model`
4. Return the template name

**POST → redirect → GET** (PRG pattern): after saving, controllers return `redirect:/projects/{id}` so refreshing the page doesn't resubmit the form.

---

## 2. Project layout

```
src/main/java/com/devtrack/app/
├── DevTrackApplication.java      entry point (@SpringBootApplication)
├── config/
│   ├── HomeController            "/" dashboard + "/login"
│   └── DataSeeder                creates admin user on first boot
├── project/                      the core domain
│   ├── Project, ProjectStatus    entity + enum
│   ├── ProjectRepository         DB queries
│   ├── ProjectService            business logic
│   ├── ProjectController         pages: list/detail/form/docs
│   └── MarkdownService           markdown → HTML
├── feature/                      per-project to-do items
│   ├── Feature, FeatureStatus, FeatureRepository, FeatureController
│   └── FeatureImportService      bulk import from .csv/.xlsx
├── logentry/                     per-project dev journal
├── user/                         User entity + repo
├── security/SecurityConfig       login rules, BCrypt, UserDetailsService
└── ai/                           chat assistant (see §6)

src/main/resources/
├── application.yml               config
├── db/migration/                 Flyway SQL
├── static/css/main.css           all styling
└── templates/                    Thymeleaf pages
    ├── layout/base.html          sidebar fragment reused everywhere
    ├── dashboard.html, login.html
    └── project/ feature/ logentry/
```

---

## 3. Data model

```
users 1 ──< projects 1 ──< features
                      1 ──< log_entries
```

- `projects`: name, description, repo_url, demo_url, **status**, tech_stack (comma-separated string), **documentation** (markdown TEXT, added in V2), created/updated timestamps, `owner_id → users`
- `features`: title, description, status (`PLANNED / IN_PROGRESS / DONE`), completed_at
- `log_entries`: note (TEXT), entry_date
- Features and logs have `ON DELETE CASCADE` → deleting a project deletes them.

Project statuses: `IDEA, IN_PROGRESS, PAUSED, DONE, ARCHIVED`.

### Things worth understanding in `Project.java`
- `@Enumerated(EnumType.STRING)` → stores `"IN_PROGRESS"` not `1`. Safe if you reorder the enum.
- `@ManyToOne(fetch = LAZY)` owner; `@OneToMany(mappedBy="project", cascade=ALL, orphanRemoval=true)` features/logs. `mappedBy` = "the other side owns the foreign key".
- `@OrderBy` on the collections = auto-sorted lists.
- `@PreUpdate` bumps `updatedAt` automatically on every update.
- `getTechStackList()` splits `"Spring Boot, HTML"` into a list — this is what renders the little tag chips.
- `@NotBlank` + `@Valid` in the controller + `BindingResult` = form validation.

---

## 4. Flyway + Hibernate: who owns the schema?

- `spring.jpa.hibernate.ddl-auto: validate` → Hibernate **never changes** tables; it only checks entities match the DB and fails startup if not.
- **Flyway** owns the schema. Files are `V<number>__<description>.sql`, run once in order, tracked in the `flyway_schema_history` table.
- **Rule:** never edit an applied migration. To change the schema, add `V3__something.sql`.
- On the server's startup log you saw: `Successfully validated 2 migrations … Schema "public" is up to date`.

---

## 5. Security walkthrough (`SecurityConfig.java`)

- Every URL requires login **except** `/css/**`, `/js/**`, `/favicon.ico`, **`/api/**`**.
- Form login page at `/login`, success → `/`. Logout → `/login?logout`.
- Passwords stored as **BCrypt** hashes. `UserDetailsService` loads the user from the `users` table; role becomes `ROLE_<role>`.
- CSRF protection is on for normal forms (Thymeleaf auto-inserts the token into `th:action` forms), and **disabled for `/api/**`**.
- `@AuthenticationPrincipal UserDetails user` in a controller = "the logged-in user".

> ⚠ **Study this as a known weakness:** because `/api/**` is `permitAll` with CSRF off, `POST /api/assistant` works **without logging in**. Anyone who finds the URL can make the AI create/delete your projects. Fixing this = remove `/api/**` from `permitAll()` (and send the session cookie + CSRF token from the dashboard JS).

---

## 6. The AI assistant (the most interesting part)

Not a library — it's ~200 lines of hand-rolled "tool calling":

```
dashboard.html JS  ──POST /api/assistant {message}──►  AiController
                                                         │
                                                  AiAssistantService.chat()
   1. build system prompt = rules + list of available actions (from AiTools)
   2. POST {baseUrl}/chat/completions  (Bearer apiKey, model "big-pickle")
   3. strip ``` fences from the reply
   4. extractJson(): find first balanced { ... }
   5. if it parses as {"action": "...", "params": {...}}
         → AiTools.execute(action, params)  → runs real DB code
      else → return the model's text as-is (e.g. "Hi!")
                                                         │
                                          {reply: "Created project …"} ──► chat bubble
```

Key ideas:
- **The LLM never touches the DB.** It only emits JSON naming an action; `AiTools` is a whitelist (`listProjects`, `createProject`, `updateProject`, `deleteProject`, `listFeatures`, `createFeature`, `updateFeatureStatus`, `deleteFeature`).
- `register(name, description, handler, example)` builds both the lookup map **and** the prompt text, so adding a tool = one `register(...)` call.
- Projects/features can be referenced by **ID or name** (`resolveProject` → fuzzy `findFirstByNameContainingIgnoreCase…`).
- `createProject` always assigns the owner as user `"admin"`.
- Failure mode you saw in the logs: provider returned `503 Service Unavailable`; `callAi` catches it and returns `"Error: …"` as the reply.
- Config comes from `opencode.zen.*` in `application.yml`, overridable with env vars `OPENCODE_ZEN_API_KEY`, `OPENCODE_ZEN_BASE_URL`, `OPENCODE_ZEN_MODEL`.

---

## 7. Thymeleaf cheat sheet (all used in this repo)

| Syntax | Meaning | Example in repo |
|---|---|---|
| `th:text="${x}"` | print a value (HTML-escaped) | `th:text="${p.name}"` |
| `th:href="@{/projects/{id}(id=${p.id})}"` | build a URL | project rows |
| `th:href="@{/projects(status='DONE')}"` | URL with query string | dashboard stat cards |
| `th:each="p : ${recentProjects}"` | loop | project list |
| `th:if` / `th:unless` | conditional | empty-state vs list |
| `th:classappend` | conditionally add CSS class | active sidebar link |
| `th:fragment` / `th:replace` | reusable chunk | `layout/base.html` sidebar |
| `${#lists.isEmpty(x)}` | utility object | dashboard |
| `${#temporals.format(date,'dd MMM')}` | format Java time | log dates |
| `th:action` + POST form | form submit (auto CSRF token) | delete/save forms |

Because `thymeleaf.cache: false`, template edits show on refresh in dev.

---

## 8. Feature tour → where the code is

| Feature | Route(s) | Code |
|---|---|---|
| Dashboard stats + recents | `GET /` | `HomeController`, `dashboard.html`, `ProjectService.getDashboardStats()` |
| List / filter / search | `GET /projects?status=…&q=…` | `ProjectController.list`, `ProjectRepository.search` (JPQL `LIKE` on name + tech stack) |
| Create / edit / delete project | `/projects/new`, `/{id}/edit`, `POST /save`, `POST /{id}/delete` | `ProjectController` |
| Project docs (markdown) | `/projects/{id}/docs`, `/docs/edit`, `POST /docs/save` | `MarkdownService.toHtml` (tables + strikethrough) |
| Features + toggle done | `/projects/{id}/features/...`, `POST /{id}/toggle` | `FeatureController` |
| Bulk import features | `POST /projects/{id}/features/import` (multipart) | `FeatureImportService` — finds columns by header name (`title/name/feature`, `description/desc/details`, `status`), then reads rows |
| Dev log | `/projects/{id}/logs/...` | `LogEntryController` |
| Login/logout | `/login`, `/logout` | `SecurityConfig` |
| Chat assistant | `POST /api/assistant` | `ai/` package |

---

## 9. Configuration (`application.yml`)

- `${VAR:default}` = "use env var `VAR`, else the default". That's how Docker overrides DB URL/user/password and the AI key without code changes.
- `spring.servlet.multipart.max-file-size: 10MB` → import upload limit.
- `app.admin.username/password` → used only by `DataSeeder` when no such user exists.

> ⚠ Real-looking secrets are committed as defaults in this file (DB password, AI API key). Study point: move them to env vars only and **rotate the key**.

---

## 10. Build & deploy

**Local:** `mvn spring-boot:run` (needs Postgres on `localhost:5432`, DB `devtrack`).

**Dockerfile (multi-stage):**
1. Stage `build`: `maven:3.9-eclipse-temurin-17` → copies `pom.xml`, `mvn dependency:go-offline` (cached layer), copies `src`, `mvn package -DskipTests`.
2. Stage 2: `eclipse-temurin:17-jre-jammy` (small, JRE only) → copies the jar → `java -jar app.jar`.

**docker-compose:** two services — `db` (postgres:16 + `pgdata` volume + healthcheck) and `app` (`depends_on: db: condition: service_healthy`). App reaches DB by service name: `jdbc:postgresql://db:5432/devtrack`.

**Production (Tencent Lighthouse):**
- App folder: `~/devtrack/docker-compose.yml`; source copied to `~/devtrack-src`.
- Image built **on the server**: `docker build -t devtrack-app:local .` → compose `image: devtrack-app:local` → `docker compose up -d app`.
- Only `app` is recreated; the `db` container and `pgdata` volume stay, so data survives deploys.
- Port 8080 is published; an nginx site config (`/etc/nginx/sites-available/devtrack`) fronts it.

---

## 11. Self-quiz (cover the answers)

1. Why does `save()` return `redirect:/projects/…` instead of a template? *(PRG: avoid double-submit on refresh.)*
2. What stops Hibernate from creating tables? Who does? *(`ddl-auto: validate`; Flyway.)*
3. Why `EnumType.STRING`? *(Reordering/adding enum values can't corrupt stored data.)*
4. How does the sidebar get highlighted per page? *(`th:replace` fragment with `activeMenu` param + `th:classappend`.)*
5. Walk through what happens when you type "create project Foo" in the chat box. *(§6.)*
6. Why can't the LLM delete arbitrary tables? *(Only whitelisted `AiTools` actions execute.)*
7. What happens to a project's features when you delete the project? *(DB `ON DELETE CASCADE` + JPA `orphanRemoval/cascade`.)*
8. How does the app find the DB inside Docker? *(Compose service name `db` as hostname.)*
9. Which URLs are reachable without logging in, and why is `/api/**` a problem? *(§5.)*
10. How would you add a new AI action `addLogEntry`? *(New method in `AiTools` + `register(...)` in `init()`; needs `LogEntryRepository` injected.)*

---

## 12. Hands-on exercises (easy → harder)

1. **Add a status** `BLOCKED` to `ProjectStatus` — note it needs **no migration** (status is a string) but needs a CSS badge class `badge-blocked`.
2. **Add a `priority` column** to projects: write `V3__add_priority.sql`, add the field to `Project`, add it to `project/form.html`. Watch `validate` fail if you forget the SQL.
3. **Secure the API:** remove `/api/**` from `permitAll`, make the dashboard `fetch` send the CSRF header, confirm unauthenticated `curl` gets redirected.
4. **Add an AI action** `addLogEntry`(projectName, note).
5. **Show a "Recent Logs" page** with full-text notes using `white-space: pre-wrap` (like `.devlog-note`) instead of the clamped dashboard preview.
6. **Move secrets out** of `application.yml` into a `.env` used by compose.

---

## 13. Vocabulary to remember

`@SpringBootApplication` · dependency injection (constructor via `@RequiredArgsConstructor`) · bean · repository interface (query methods derived from names, e.g. `findByStatusOrderByUpdatedAtDesc`) · JPQL · lazy loading · cascade · Flyway migration · BCrypt · CSRF · PRG · fat JAR · multi-stage Docker build · healthcheck · volume.
