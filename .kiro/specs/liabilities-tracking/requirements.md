# Requirements — Liabilities Tracking

## Introduction

MyFinance tracks assets thoroughly and has a Home Loans module, but has no place for **other liabilities** (credit-card balances, personal/vehicle/education loans, informal debts). Because net worth = assets − liabilities, this gap makes net-worth figures overstated for users who carry debt.

This feature adds a **Liabilities** module so users can record non-mortgage debts, see total outstanding debt, and have those balances subtracted from net worth. It follows the existing per-user, multi-tenant, feature-flagged module pattern. Home loans stay in their own module; this covers everything else.

## Glossary

- **Liability** — a debt the user owes: credit card, personal loan, vehicle loan, education loan, or other.
- **Outstanding balance** — current amount still owed (the figure used for net worth).
- **Included in net worth** — a per-liability toggle; when on, the outstanding balance is subtracted from net worth.

## Requirements

### Requirement 1 — Manage liabilities (CRUD)

**User Story:** As a user, I want to add, view, edit, and delete my liabilities, so that I can keep an accurate record of what I owe.

#### Acceptance Criteria
1. WHEN a user creates a liability with a name, type, and outstanding balance THEN the system SHALL persist it against that user's `userId` and return it with a generated id.
2. WHEN a user requests their liabilities THEN the system SHALL return only liabilities belonging to that user.
3. WHEN a user updates a liability they own THEN the system SHALL save the changes and return the updated record.
4. WHEN a user deletes a liability they own THEN the system SHALL remove it and return 204.
5. IF a user attempts to read, update, or delete a liability that belongs to another user THEN the system SHALL NOT expose or modify it.
6. WHEN a monetary value is stored THEN the system SHALL use `BigDecimal` (never floating point).

### Requirement 2 — Liability attributes

**User Story:** As a user, I want to capture the details that matter for each debt, so that the record is useful.

#### Acceptance Criteria
1. THE system SHALL support a `type` of one of: CREDIT_CARD, PERSONAL_LOAN, VEHICLE_LOAN, EDUCATION_LOAN, OTHER.
2. THE system SHALL store: name, type, lender/institution, outstanding balance, currency (String), interest rate (optional), minimum/monthly payment (optional), due day of month (optional, 1–31), start date (optional), notes (optional), and an `includeInNetWorth` flag defaulting to true.
3. THE system SHALL store a `status` of ACTIVE or CLOSED, defaulting to ACTIVE.
4. WHEN currency is provided THEN it SHALL be a user-known currency code (consistent with the currency-rates module); no currency is seeded.

### Requirement 3 — Summary and net-worth impact

**User Story:** As a user, I want to see my total debt and have it reduce my net worth, so that my financial picture is accurate.

#### Acceptance Criteria
1. WHEN a user requests the liabilities summary THEN the system SHALL return total outstanding balance, count of active liabilities, and totals grouped by type — considering only ACTIVE liabilities.
2. WHEN computing net worth THEN the system SHALL subtract the outstanding balance of every ACTIVE liability whose `includeInNetWorth` is true, for that user only.
3. WHEN a liability's `includeInNetWorth` is false OR its status is CLOSED THEN the system SHALL exclude it from the net-worth calculation.

### Requirement 4 — Per-user feature flag

**User Story:** As an admin, I want to enable/disable the Liabilities module per user, so that users only see modules they use.

#### Acceptance Criteria
1. THE Liabilities feature SHALL be represented by a key in the existing `AppUser.enabledFeatures` CSV mechanism (no new mechanism).
2. WHEN the admin toggles the feature in User Management THEN the change SHALL persist for that user.
3. WHEN the feature is disabled for a user THEN the frontend SHALL hide the Liabilities navigation and page.
4. IF `enabledFeatures` is empty THEN all features (including Liabilities) SHALL be considered enabled (backward compatibility).

### Requirement 5 — Frontend page

**User Story:** As a user, I want a clear Liabilities page, so that managing debts is easy.

#### Acceptance Criteria
1. THE page SHALL list liabilities with type, lender, outstanding balance, and status, plus summary cards (total debt, count, by-type breakdown).
2. WHEN a create/edit/delete succeeds or fails THEN the page SHALL show a toast (green/red) — never a browser `alert()`.
3. WHEN an admin is logged in THEN the user-facing Liabilities route SHALL NOT be accessible to them.

### Requirement 6 — Tests, quality, and safety

#### Acceptance Criteria
1. THE backend SHALL have a `LiabilityControllerTest` covering create, list, update, delete, summary, ownership isolation, and net-worth inclusion/exclusion.
2. THE net-worth change SHALL be covered so that liabilities correctly reduce the figure.
3. THE frontend SHALL have a `Liabilities.test.tsx` covering render, summary display, and a create action.
4. Overall backend line coverage SHALL remain ≥ 80%.
5. THE new `liabilities` table SHALL be additive (no change to existing tables) so the production database is unaffected.
6. Multi-tenant isolation SHALL be preserved; `MultiTenantIsolationTest` SHALL remain green (extend it if practical).
