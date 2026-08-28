# Design — Liabilities Tracking

## Overview

A new user-owned module following the established asset-module pattern (mirrors Property / PreciousMetal / GenericFixedDeposit). One entity, one repository, one thin controller, plus a small change to net-worth aggregation and the feature-flag surfaces. No changes to existing tables — the `liabilities` table is additive, safe for the production database under `ddl-auto: update`.

## Architecture

```
Frontend (React)                         Backend (Spring Boot)
-----------------                        ---------------------
Liabilities.tsx --api.ts--> POST/GET/PUT/DELETE /api/liabilities --> LiabilityController
                                                                          |
                                                                          +-> LiabilityRepository -> liabilities (H2)
                                                                          +-> TenantContext (userId from JWT)

NetWorthService.calculate(userId) --> assets - sum(active, included liabilities)
UserManagement.tsx feature checklist --> AppUser.enabledFeatures CSV (+ "LIABILITIES")
```

## Data Model

New entity `Liability` -> table `liabilities`. Fields are nullable at the DB level except where noted, to keep the migration additive.

| Field | Type | Notes |
|-------|------|-------|
| id | Long | `@Id @GeneratedValue(IDENTITY)` |
| userId | Long | tenant key; set from `TenantContext` on create |
| name | String | required (`nullable = false`) |
| type | String | CREDIT_CARD / PERSONAL_LOAN / VEHICLE_LOAN / EDUCATION_LOAN / OTHER |
| lender | String | institution / person |
| outstandingBalance | BigDecimal | `precision = 18, scale = 2`, required |
| currency | String | user-known code (e.g. SGD, LKR); not seeded |
| interestRate | BigDecimal | optional, `precision = 8, scale = 4` |
| monthlyPayment | BigDecimal | optional, `precision = 18, scale = 2` |
| dueDayOfMonth | Integer | optional, 1-31 |
| startDate | LocalDate | optional |
| status | String | `@Builder.Default "ACTIVE"` (ACTIVE / CLOSED) |
| includeInNetWorth | Boolean | `@Builder.Default true` |
| notes | String | optional |
| createdAt / updatedAt | LocalDateTime | `@PrePersist` / `@PreUpdate` |

Lombok: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`. Uses `String` currency (matches the newer asset modules).

### Migration safety
- Adds one new table only. No existing column/table is renamed, dropped, or retyped.
- No NOT NULL column without a default that would fail on existing rows (there are no existing rows — new table).
- Consistent with the Schema Migration Guard hook rules.

## API

Base path `/api/liabilities`. Controller is `@RestController @RequiredArgsConstructor @Slf4j`, injects `LiabilityRepository` + `TenantContext`. All handlers scope to the current user.

| Method | Path | Body / Params | Returns |
|--------|------|---------------|---------|
| GET | `/api/liabilities` | optional `?type=` | list for current user |
| GET | `/api/liabilities/summary` | — | `{ totalOutstanding, activeCount, byType: {TYPE: total} }` (ACTIVE only) |
| POST | `/api/liabilities` | `Liability` | 201 + created |
| PUT | `/api/liabilities/{id}` | `Liability` | 200 + updated |
| DELETE | `/api/liabilities/{id}` | — | 204 |

Repository finders (all start with `userId`):
```java
List<Liability> findByUserIdOrderByOutstandingBalanceDesc(Long userId);
List<Liability> findByUserIdAndType(Long userId, String type);
List<Liability> findByUserIdAndStatus(Long userId, String status);
```

### Ownership on update/delete
Load by id, confirm `userId` equals `tenantContext.getCurrentUserId()` before mutating; otherwise treat as not found. This matches the security boundary in `security.md`.

## Net-Worth Integration

`NetWorthService` currently sums included assets. Extend it:

1. Load `findByUserIdAndStatus(userId, "ACTIVE")`.
2. Sum `outstandingBalance` where `includeInNetWorth == true`.
3. Subtract that total from the assets total.
4. Currency: reuse the existing conversion path used for other multi-currency values (user-maintained rates). If the net-worth calc already normalises to a base currency, convert liability balances the same way; otherwise sum in stored currency consistent with current behaviour.

This is the only change to existing business logic; it is additive (a subtraction term) and guarded by tests.

## Feature Flag

- Key: `LIABILITIES` in `AppUser.enabledFeatures` CSV.
- Add the checkbox to the User Management feature checklist and the create-user form.
- Frontend nav hides the Liabilities entry when the flag is off; empty CSV = all enabled.

## Frontend

- `types.ts`: `Liability` interface + `LIABILITY_TYPE_LABELS` map.
- `api.ts`: `getLiabilities`, `getLiabilitiesSummary`, `createLiability`, `updateLiability`, `deleteLiability`.
- `pages/Liabilities.tsx`: summary cards (total debt, active count, by-type), a table, and a create/edit form. Uses `useToast()` for all feedback. Route registered and gated behind the feature flag; blocked for admin sessions.

## Error Handling

Standard `GlobalExceptionHandler` behaviour — no custom error bodies. A blocked delete would use `ReferenceConstraintException`; not needed unless references are introduced later.

## Testing Strategy

- **`LiabilityControllerTest`** (`@SpringBootTest`, `@AutoConfigureMockMvc`, `extends BaseControllerTest`, `@WithMockUser`, `repository.deleteAll()` in `@BeforeEach`): create, list, filter by type, update, delete, summary (ACTIVE only, by-type totals), and an ownership-isolation case.
- **Net-worth test**: assert that an ACTIVE included liability reduces the net-worth figure and that a CLOSED or excluded one does not. Extend `MultiTenantIsolationTest` if practical so liabilities don't leak across users.
- **BigDecimal JSON quirk**: entity endpoints -> `is(5000)`; summary endpoint -> `is(5000.00)` / `closeTo(...)`.
- **Frontend `Liabilities.test.tsx`**: renders title + summary cards, lists a mocked liability, performs a create.
- Keep overall backend line coverage >= 80%; regenerate `frontend/public/test-results.json`.

## Documentation

Update `docs/DESIGN.md` (new table + endpoints), `docs/ARCHITECTURE.md` (module list), and `UserGuide.tsx` (how to use Liabilities, including the net-worth note) per `documentation.md`.
