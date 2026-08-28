---
inclusion: always
---

# Project Structure

```
myfinance/
├── backend/                     # Spring Boot app
│   ├── data/                    # H2 dev database (myfinance.mv.db)
│   ├── data-prod/               # H2 prod database (created by prod profile)
│   ├── logs/                    # myfinance.log
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/myfinance/
│       │   ├── MyFinanceApplication.java
│       │   ├── config/          # DataInitializer, AdminUserInitializer, GlobalExceptionHandler,
│       │   │                    # JacksonConfig, ReferenceConstraintException
│       │   ├── controller/      # @RestController per domain (one per feature)
│       │   ├── dto/             # request/response records & DTOs
│       │   ├── model/          # @Entity JPA classes (+ model/enums)
│       │   ├── repository/     # Spring Data JpaRepository interfaces (~35)
│       │   ├── security/       # SecurityConfig, JwtService, JwtAuthFilter,
│       │   │                    # CustomUserDetailsService, TenantContext
│       │   └── service/        # business logic (AccountService, BudgetService, etc.)
│       └── main/resources/
│           ├── application.yml         # default (dev) profile
│           └── application-prod.yml     # prod overrides (app.init-data=false)
├── frontend/                    # React + Vite app
│   ├── public/test-results.json # read by the Test Results page
│   └── src/
│       ├── pages/               # one .tsx per screen (Dashboard, Assets, Budget, ...)
│       │                        #   co-located *.test.tsx for page tests
│       ├── components/          # shared UI (ToastContainer, SearchableSelect, ...)
│       ├── context/             # React contexts (Toast, Auth)
│       ├── api.ts               # all axios API functions
│       └── types.ts             # shared TS types + enum label maps
├── docs/                        # DESIGN.md, ARCHITECTURE.md
│   └── saas/                    # multi-tenant SaaS: REQUIREMENTS, ARCHITECTURE, FLOWS, SECURITY
├── scripts/                     # run-tests.sh, generate-results.py, start-prod.sh, deploy.sh,
│                                #   cleanup-for-golive.sh
└── .kiro/                       # steering, hooks, skills, specs
```

## Layering (backend)

`controller` → `service` → `repository` → `model`. Controllers are thin; business logic lives in services. Simple CRUD controllers may call the repository directly (existing pattern), but anything with calculations or cross-entity rules belongs in a service.

## Naming Conventions

- **One feature = one controller + one repository (+ service if logic exists) + one model.**
- Controllers: `XxxController` mapped under `/api/<kebab-plural>` (e.g. `/api/precious-metals`, `/api/generic-fd`).
- Repositories: `XxxRepository extends JpaRepository<Xxx, Long>` with `findByUserId...` finders.
- Entities: `@Table(name = "snake_case_plural")`, Lombok builder, `@PrePersist`/`@PreUpdate` timestamps.
- Frontend pages: PascalCase `Xxx.tsx` in `pages/`; API calls added to `api.ts`; types in `types.ts`.
- Tests mirror the class name: `XxxControllerTest`, `XxxServiceTest`; supplemental gap tests use `XxxExtraTest`.
