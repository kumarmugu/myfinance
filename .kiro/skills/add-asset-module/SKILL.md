---
name: add-asset-module
description: End-to-end recipe for adding a new user-owned asset/tracking module to MyFinance (backend entity + repository + controller, per-user feature flag, frontend page + API + types, tests, and docs). Use when the user asks to add a new asset type, tracking section, or feature module (e.g. "add a section to manage X", "let users track Y").
---

# Add a New Asset / Tracking Module

This project has a repeatable shape for every user-owned module (Property, PreciousMetal, GenericFixedDeposit are the cleanest recent examples — imitate them). Follow every step; skipping the multi-tenant or feature-flag steps has caused bugs before.

Refer to the always-on steering (`conventions.md`, `security.md`, `structure.md`) for the rules referenced below.

## 0. Confirm scope first

Ask/decide: field list, which are monetary (`BigDecimal`), currency as `Currency` enum or `String` (new modules use `String`), does it have a status lifecycle, does it contribute to net worth, does it need a summary endpoint. Don't invent fields beyond what's asked.

## 1. Backend entity — `backend/.../model/Xxx.java`

- `@Entity @Table(name = "snake_case_plural")`, Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`.
- `@Id @GeneratedValue(strategy = IDENTITY) Long id;` and **`private Long userId;`** (mandatory).
- Money fields = `BigDecimal` with `@Column(precision = 18, scale = 2)` (rates need more scale). Dates = `LocalDate`.
- `@Builder.Default` for defaults like `status = "ACTIVE"` and `includeInNetWorth = true`.
- `createdAt`/`updatedAt` with `@PrePersist onCreate()` / `@PreUpdate onUpdate()`.
- Any association that gets serialized → `FetchType.EAGER` (open-in-view is false).

## 2. Repository — `backend/.../repository/XxxRepository.java`

```java
public interface XxxRepository extends JpaRepository<Xxx, Long> {
    List<Xxx> findByUserIdOrderBy<Field>Asc(Long userId);
    // add filtered finders as needed, always starting with userId
}
```

## 3. Controller — `backend/.../controller/XxxController.java`

- `@RestController @RequestMapping("/api/<kebab-plural>") @RequiredArgsConstructor @Slf4j`.
- Inject the repository and `TenantContext`.
- `getAll()` → `repository.findByUserId...(tenantContext.getCurrentUserId())`.
- `create()` → set `x.setUserId(tenantContext.getCurrentUserId())`, log at info, return `201`.
- `update(id)` → load, (verify ownership), copy fields, save.
- `delete(id)` → delete, return `204`. Throw `ReferenceConstraintException` if referenced.
- Optional `GET /summary` → aggregate with `BigDecimal` reductions; filter to the relevant status (e.g. OWNED/HELD).
- Log CREATE/UPDATE/DELETE; record to `AuditService` if wiring audit.

## 4. Feature flag (per user)

- Add the feature key to the `AppUser.enabledFeatures` CSV convention (do NOT invent a new mechanism).
- Add it to the admin checklist in `UserManagement.tsx` and the create-user form.
- Frontend hides the nav item when the flag is off. Empty CSV = all enabled.

## 5. Frontend

- `frontend/src/types.ts` — add the TS interface (+ any label maps).
- `frontend/src/api.ts` — add axios functions (`getXxx`, `createXxx`, `updateXxx`, `deleteXxx`, `getXxxSummary`).
- `frontend/src/pages/Xxx.tsx` — page with list + create/edit form; use `useToast()` for feedback (never `alert()`); charts via `recharts` if useful.
- Register the route and nav entry; gate it behind the feature flag; keep admins off user-facing asset routes.

## 6. Tests (required — see `testing.md`)

- `XxxControllerTest extends BaseControllerTest` with `@SpringBootTest @AutoConfigureMockMvc`, `@WithMockUser` on every test, `repository.deleteAll()` in `@BeforeEach`.
- Cover create, list, update, delete, summary, and at least one edge case (e.g. status excluded from summary).
- **BigDecimal JSON quirk:** entity endpoints return whole numbers as ints → `is(3200)`; summary endpoints preserve scale → `is(3200.00)` / `closeTo(...)`.
- Frontend: a `Xxx.test.tsx` for the page (render, tab/section switches, a create action).

## 7. Verify & document

- Backend: `cd backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./mvnw test jacoco:report` — all green, coverage ≥ 80%.
- Frontend: `cd frontend && npm run build && npm test`.
- Regenerate results page: `python3 scripts/generate-results.py`.
- Update `docs/DESIGN.md` (schema/API), `docs/ARCHITECTURE.md`, and `UserGuide.tsx` per `documentation.md`.
- Do NOT auto-commit.

## Checklist

- [ ] Entity has `userId` + timestamps + sensible `@Builder.Default`s
- [ ] All queries filter by `userId`; create sets `userId` from `TenantContext`
- [ ] Money is `BigDecimal`
- [ ] Feature flag added + admin checklist + nav gating
- [ ] Controller/summary/frontend/api/types done
- [ ] Tests written and green; coverage ≥ 80%
- [ ] Docs + test-results regenerated
