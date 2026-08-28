---
inclusion: always
---

# Coding Conventions & Patterns

Follow these established patterns exactly. They are load-bearing — deviating has caused real bugs in this codebase.

## Multi-Tenancy (MANDATORY)

Every user-owned entity has a `Long userId`. Data must never leak across users.

- On **create**: set `entity.setUserId(tenantContext.getCurrentUserId())` before saving. Never trust a `userId` from the request body.
- On **read/list**: query via `findByUserId...` finders — never a plain `findAll()`.
- On **update/delete by id**: the record's `userId` must match the current user. Load, verify ownership, then mutate.
- `TenantContext.getCurrentUserId()` reads the authenticated user from the JWT.
- The `MultiTenantIsolationTest` guards this — keep it green.

## Feature Flags (per user)

Feature access is stored on `AppUser.enabledFeatures` as a **CSV string** (NOT a new mechanism, NOT a separate table).

- **Empty string = all features enabled** (backward compatibility). Do not "fix" this to mean none.
- Admins edit the checklist in User Management (and the create-user form).
- The frontend hides nav/menu items for features the user hasn't enabled.
- When adding a new feature, register its flag key in the same CSV convention and add it to the admin checklist.

## Money & Numbers

- All monetary and rate values are **`BigDecimal`** — never `double`/`float`. FX rates need enough scale to store values like `0.0039`.
- JSON quirk to know when writing tests: entity-return endpoints serialize whole-number `BigDecimal`s as JSON **integers** (`3200`, not `3200.0`) — assert with `is(3200)`. Summary endpoints load fresh from the DB with column scale preserved — assert with `is(3200.00)` or `closeTo(...)`.

## Currency

- Some entities use the **`Currency` enum** (Account, BankSavings, Dividend, ...); newer asset modules (Property, PreciousMetal, GenericFixedDeposit) use a **`String` currency**. Match whatever the entity you're touching already uses.
- Currencies are **user-created in the DB**, not seeded. No external FX feed — rates are user-maintained.

## Error Handling

- Throw `ReferenceConstraintException(entityName, references)` when a delete is blocked by references → handled as **409 Conflict** with a `references` list.
- Other `RuntimeException`s → **400** with a message. Validation errors → **400** with per-field `errors`.
- All handled centrally in `GlobalExceptionHandler`; don't build ad-hoc error bodies in controllers.

## Logging

- Use Lombok `@Slf4j`. Log CREATE/UPDATE/DELETE at `info` with the entity id; log auth/JWT failures at `debug`.
- User actions are recorded to the **audit trail** (`AuditService`) — CREATE/UPDATE/DELETE with entity, userId, timestamp, details. Admin-only Audit page reads it.
- Logs go to `backend/logs/myfinance.log`.

## Persistence Notes

- `open-in-view: false` is set. If an entity is serialized outside a transaction and hits a lazy association, you'll get `LazyInitializationException`. Precedent: Budget's `BudgetIncome`/`BudgetAllocation` use `FetchType.EAGER` for this reason. Prefer eager (or a DTO) for associations that get serialized.
- `ddl-auto: update` means new columns/tables are auto-created. Schema changes must be **additive** and must not break the existing prod database (see the migration guard hook).

## Frontend

- Replace any `alert()` with the **toast** system (`useToast()` → error=red, success=green, info; auto-dismiss).
- All API calls go through `api.ts`; shared types in `types.ts`.
- Auto-logout after **1 hour** of inactivity is a product requirement — don't remove it.
- Admin users must not access user-facing asset routes (e.g. `/properties`).
