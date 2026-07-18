# DevTrack

A personal coding project documentation tracker built with Spring Boot, Thymeleaf, and PostgreSQL.

## Features
- Track projects with status, tech stack, repo/demo links
- Feature tracking with done/not-done toggle
- Dev log for recording what you worked on
- Dashboard with stats and recent activity
- Search and filter by status
- Simple login (ready to expand to multi-user later)

## Tech Stack
- Spring Boot 3.2
- Spring Security (form login)
- Spring Data JPA + PostgreSQL
- Flyway (schema migrations)
- Thymeleaf (server-rendered UI)
- Lombok

---

## Setup

### 1. Prerequisites
- Java 17+
- Maven 3.8+
- PostgreSQL running locally

### 2. Create the database
```sql
CREATE DATABASE devtrack;
```

### 3. Configure credentials
Edit `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/devtrack
    username: YOUR_PG_USERNAME
    password: YOUR_PG_PASSWORD
```

### 4. Set your admin password

The default seeded user is `admin` / `devtrack123`.

To change the password, generate a new BCrypt hash and update the migration file:

```bash
# Using htpasswd or any BCrypt tool online
# Replace the hash in V1__init_schema.sql before first run
```

Or use this Spring Boot snippet to generate a hash:
```java
System.out.println(new BCryptPasswordEncoder().encode("yourpassword"));
```

### 5. Run the app
```bash
mvn spring-boot:run
```

Then open **http://localhost:8080** and log in with `admin` / `devtrack123`.

---

## Project Structure
```
src/main/java/com/devtrack/app/
├── config/         HomeController (dashboard + login routes)
├── feature/        Feature entity, repo, controller
├── logentry/       LogEntry entity, repo, controller
├── project/        Project entity, repo, service, controller, status enum
├── security/       Spring Security config + UserDetailsService
└── user/           User entity, repo

src/main/resources/
├── db/migration/   Flyway SQL scripts
├── static/css/     main.css
└── templates/      Thymeleaf HTML pages
    ├── project/    list, detail, form
    ├── feature/    form
    ├── logentry/   form
    └── dashboard, login
```

## Adding more users

Run this SQL against your database (replace the password hash):
```sql
INSERT INTO users (username, password, display_name, role)
VALUES ('newuser', '$2a$10$...bcrypt_hash...', 'New User', 'USER');
```

## Planned future additions
- [ ] Markdown rendering for descriptions and log entries
- [ ] Screenshot/link attachments per project
- [ ] User registration / self-service accounts
- [ ] Export project to PDF or Markdown
- [ ] Public project pages (shareable links)
