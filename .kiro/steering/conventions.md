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

## Currency (original value is the source of truth)

**NON-NEGOTIABLE: never overwrite a record's original currency/amount just because the app has a base currency.** Every money record stores its *original* currency + amount; base and display values are always *derived* via FX, never persisted over the original.

- **Original currency + amount** = the source of truth (what the user entered). Preserve it on create/update; conversion must never mutate it.
- **Base currency** = per-user, configurable in User Management (`AppUser.baseCurrency`, null → default `SGD`). Used only for consolidation (Net Worth, summary totals).
- **Display currency** = per-user list (`AppUser.displayCurrencies` CSV, null → `SGD,USD`), shown as a UI toggle. Changing it re-derives displayed values; it never writes back.
- Conversion goes through **`CurrencyConversionService`** only. It uses the user's own `CurrencyRate` entries (latest by `effectiveDate`, direct → inverse → identity fallback). **No hardcoded FX anywhere** (frontend or backend).
- Entity currency type: older entities use the **`Currency` enum** (Account, BankSavings, Dividend, Holding, ...); newer/String-based ones (Property, PreciousMetal, GenericFixedDeposit, and the added `currency` on SalaryRecord/TaxRecord/RetirementFundEntry/FixedDeposit) use a **`String`**. Match the entity you're touching. `CurrencyConversionService.toBase(amount, code, userId)` takes a String code (call `.name()` on enums).
- **Summary/aggregation endpoints MUST FX-convert each record to the user's base before summing** — never `reduce` raw amounts across mixed currencies. Include `baseCurrency` in the response.
- **Broker vs investment currency (do not confuse):** a `Holding` carries the **asset's** currency (the instrument, e.g. an EUR fund), NOT the broker `Account`'s currency. The `Transaction` carries the settlement currency (the account's). Buying an EUR fund via a USD broker → Holding=EUR, Transaction=USD.
- **Base-only domains** (deliberately no currency field): CPF/SRS and Budget are SGD/base-only by design — don't add currency noise there.
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
