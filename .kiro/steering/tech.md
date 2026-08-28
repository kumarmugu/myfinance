---
inclusion: always
---

# Tech Stack & Commands

## Backend

- **Java 17** (Temurin). Build config pins `<release>17</release>`.
- **Spring Boot 3.2.5** — web, data-jpa, validation, security, aop.
- **Hibernate / JPA** with `ddl-auto: update` (schema auto-migrates; see the migration guard).
- **H2** file database (`./data/myfinance` dev, `./data-prod/` prod). Console at `/h2-console`.
- **JWT auth** via `io.jsonwebtoken` 0.12.6 (stateless, `Authorization: Bearer <token>`).
- **Lombok** 1.18.34 for models/builders (`@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`).
- **springdoc-openapi** 2.5.0 — Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`.
- **JaCoCo** 0.8.12 for coverage. `DataInitializer` and `AdminUserInitializer` are excluded.

## Frontend

- **React 18.3** + **TypeScript ~5.6** + **Vite 5.4**.
- **Tailwind CSS 4** (via `@tailwindcss/vite`).
- **react-router-dom 7**, **axios**, **recharts** (charts), **lucide-react** (icons), **swagger-ui-react**.
- **Vitest 4** + **@testing-library/react** + **happy-dom** for tests.
- Dev server on **5173**, backend on **8080** (CORS allows `http://localhost:5173`).

## CRITICAL: Java Version

Always run Maven with **JDK 17**. JDK 26 fails with an ICU `NullPointerException` during tests. Prefix Maven commands:

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./mvnw ...
```

## Common Commands

| Task | Command |
|------|---------|
| Run backend (dev) | `cd backend && JAVA_HOME=... ./mvnw spring-boot:run` |
| Run backend (prod) | `bash scripts/start-prod.sh` (profile `prod`, `./data-prod/`, no seed data) |
| Backend tests | `cd backend && JAVA_HOME=... ./mvnw test` |
| Backend coverage | `cd backend && JAVA_HOME=... ./mvnw test jacoco:report` → `target/site/jacoco/` |
| Coverage % (line) | `cat backend/target/site/jacoco/jacoco.csv \| awk -F',' 'NR>1{m+=$8;c+=$9} END{printf "%.1f%%\n",(c*100.0)/(m+c)}'` |
| Frontend dev | `cd frontend && npm run dev` |
| Frontend build | `cd frontend && npm run build` (runs `tsc -b` first) |
| Frontend tests | `cd frontend && npm test` (vitest `--run`) |
| Frontend lint | `cd frontend && npm run lint` |
| All tests + results | `bash scripts/run-tests.sh` |
| Regen test-results page | `python3 scripts/generate-results.py` |

## Rules

- Never start long-running dev servers as a blocking command; ask the user to run `npm run dev` / `spring-boot:run` themselves, or use a background process.
- Coverage target is **≥ 80%** backend line coverage (currently ~88%).
- Do NOT auto-commit unless a Stop hook or the user explicitly asks.
