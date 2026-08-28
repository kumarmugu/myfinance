# Implementation Plan — Liabilities Tracking

- [ ] 1. Create the `Liability` JPA entity
  - Add `backend/src/main/java/com/myfinance/model/Liability.java` per the design data model (userId, name, type, lender, outstandingBalance, currency, interestRate, monthlyPayment, dueDayOfMonth, startDate, status default ACTIVE, includeInNetWorth default true, notes, timestamps).
  - Lombok builder + `@PrePersist`/`@PreUpdate`. Money fields are `BigDecimal`. Table `liabilities` (additive only).
  - _Requirements: 1.1, 1.6, 2.1, 2.2, 2.3, 6.5_

- [ ] 2. Create `LiabilityRepository`
  - `extends JpaRepository<Liability, Long>` with `findByUserIdOrderByOutstandingBalanceDesc`, `findByUserIdAndType`, `findByUserIdAndStatus`.
  - _Requirements: 1.2_

- [ ] 3. Create `LiabilityController`
  - Endpoints GET (optional `?type=`), GET `/summary`, POST, PUT `/{id}`, DELETE `/{id}` under `/api/liabilities`.
  - Set `userId` from `TenantContext` on create; scope all reads to the current user; verify ownership on update/delete; log CREATE/UPDATE/DELETE at info.
  - Summary aggregates ACTIVE liabilities: totalOutstanding, activeCount, byType map.
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 3.1_

- [ ] 4. Integrate liabilities into net worth
  - Update `NetWorthService` to subtract the sum of ACTIVE + `includeInNetWorth` liabilities for the user; exclude CLOSED or non-included ones; apply the same currency handling as existing values.
  - _Requirements: 3.2, 3.3_

- [ ] 5. Wire the per-user feature flag
  - Add `LIABILITIES` to the `enabledFeatures` CSV handling; add the checkbox to the User Management checklist and create-user form.
  - Preserve "empty CSV = all enabled".
  - _Requirements: 4.1, 4.2, 4.4_

- [ ] 6. Frontend types and API
  - Add `Liability` interface + `LIABILITY_TYPE_LABELS` to `types.ts`; add CRUD + summary axios functions to `api.ts`.
  - _Requirements: 5.1_

- [ ] 7. Frontend Liabilities page
  - Build `pages/Liabilities.tsx`: summary cards (total debt, active count, by-type), list/table, create/edit form. Use `useToast()` (no `alert()`).
  - Register the route + nav entry, gate behind the feature flag, and block admin access to the route.
  - _Requirements: 4.3, 5.1, 5.2, 5.3_

- [ ] 8. Backend tests
  - `LiabilityControllerTest`: create, list, filter by type, update, delete, summary (ACTIVE only + byType), ownership isolation.
  - Net-worth test: ACTIVE+included reduces net worth; CLOSED/excluded does not. Extend `MultiTenantIsolationTest` for liability isolation if practical.
  - Mind the BigDecimal JSON quirk (int vs scaled).
  - _Requirements: 6.1, 6.2, 6.6_

- [ ] 9. Frontend tests
  - `Liabilities.test.tsx`: render title + summary cards, list a mocked liability, perform a create.
  - _Requirements: 6.3_

- [ ] 10. Verify, document, and refresh results
  - Run backend `test jacoco:report` (JDK 17) — all green, coverage >= 80%. Run frontend `build` + `test`.
  - Update `docs/DESIGN.md`, `docs/ARCHITECTURE.md`, `UserGuide.tsx`.
  - Regenerate `frontend/public/test-results.json` via `python3 scripts/generate-results.py` (update coverage numbers). Do NOT auto-commit.
  - _Requirements: 6.4_
