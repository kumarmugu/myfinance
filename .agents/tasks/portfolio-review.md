# Portfolio page: cash balance display and grouped sold positions

The Portfolio page gains two new UI features: a "Cash Balances" card that surfaces uninvested cash sitting in each broker account, and grouped sold positions where rows are collapsed by account (mirroring the Holdings grouping pattern). These features build on a broader refactor of the Account, Holding, and SoldPosition backend entities, which added `cashBalance`, `userId`, `owner`, `currency`, and `purpose` fields and introduced `SoldPosition` as a dedicated entity.

**Watch for:**
- **Confirmed** — `SoldPositionController.getAll()` with `?ownerId=` or `?accountId=` bypasses the userId filter, allowing cross-tenant data exposure via query param.
- **Confirmed** — `SoldPositionController.delete()` deletes any sold position by id with no ownership check.
- **Confirmed** — `SoldTable` total profit line hardcodes `'USD'` even when all positions are in a different currency.
- **Likely** — Cash balance card sources accounts from `holdings`, so accounts with cash but no active holdings are invisible.

**Verdict**: NEEDS_CHANGES

---

## High-level view

Cash balances are derived from accounts that appear in the active holdings list, not fetched from a dedicated accounts endpoint. This is a reasonable approach — the holdings data is already loaded — but it creates a blind spot: an account with uninvested cash and no open positions is invisible in the card, which is likely wrong for users who have sold everything and are sitting on cash.

The sold-positions grouping is structurally identical to Holdings grouping. Groups are built client-side by reducing on `account.id`, sorted alphabetically by account name, with rows within each group sorted by sold date descending. All groups open by default. The pattern is consistent with the existing Accounts page grouping.

The `SoldPositionController` has two tenant isolation holes. When `?ownerId=` is passed, the service calls `findByOwnerIdOrderBySoldDateDesc` with no `userId` constraint, returning that owner's sold positions for any caller who knows the owner id. The `?accountId=` path has the same shape. The delete endpoint calls `repository.deleteById` with no ownership check. These are security-boundary violations per the project's multi-tenancy rules.

The schema changes are additive: new nullable columns on existing tables (`cashBalance`, `includeCashInNetWorth`, `userId`, `owner_id`, `currency`, `purpose`, `averageBuyFxRate`; a new `sold_positions` table). No columns are dropped or renamed. The removal of the DB-level unique constraint on `holdings (asset_id, account_id)` is noted in a detailed comment explaining why `ddl-auto=update` cannot safely swap it on prod. Acceptable.

The `AccountService.getAllAccounts()` method still calls `findAll()` unscoped but is not invoked from any controller in this diff. The dead method is a maintenance trap.

---

<details>
<summary>Issues (5)</summary>

1. **Sold positions cross-tenant leak via ownerId/accountId param** — `SoldPositionController.getAll(?ownerId=X)` and `getAll(?accountId=X)` return data without checking the caller's userId. Add `findByOwnerIdAndUserIdOrderBySoldDateDesc` / `findByAccountIdAndUserIdOrderBySoldDateDesc` finders, or pass `uid` into the service methods and filter there.

2. **Sold positions delete without ownership check** — `DELETE /api/sold-positions/{id}` calls `deleteById` directly. Load the record first, verify `sp.getUserId().equals(uid)`, and throw 403/404 otherwise.

3. **SoldTable total profit hardcodes USD** — `formatCurrency(totalProfit, 'USD')` is wrong when positions are in SGD or mixed currencies. Pass `displayCurrency` as a prop and apply FX conversion before summing, consistent with how the Holdings tab handles it.

4. **Cash card misses accounts with no active holdings** — `accountsWithCash` is built from `holdings.map(h => h.account)`, so any account with cash but zero open positions (e.g. fully-sold) is invisible. Fetch accounts independently or document the limitation.

5. **Dead `AccountService.getAllAccounts()` calls `findAll()` unscoped** — Not called from the controller but it exists and returns all users' accounts. Remove it or scope it before it gets wired somewhere.

</details>

<details>
<summary>Details</summary>

### Tenant isolation gaps in SoldPositionController

Three paths in the new controller fail to enforce ownership:

```java
// GET /api/sold-positions?ownerId=X  — no userId check
if (ownerId != null) return soldPositionService.getByOwner(ownerId);

// GET /api/sold-positions?accountId=X  — no userId check
if (accountId != null) return soldPositionService.getByAccount(accountId);
```

`findByOwnerIdOrderBySoldDateDesc` and `findByAccountIdOrderBySoldDateDesc` are pure FK queries with no `userId` predicate. Any authenticated user who knows another user's owner id or account id can read their sold positions. The default path (`getByUser(uid)`) is correctly scoped.

The delete path:

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    soldPositionService.delete(id);  // straight deleteById
}
```

No ownership check. Any authenticated user can delete any sold position by id. The fix is to load the record, verify `sp.getUserId().equals(uid)`, and throw before deleting.

`MultiTenantIsolationTest` should be extended to cover `GET /api/sold-positions?ownerId=` and `DELETE /api/sold-positions/{id}` with cross-tenant payloads.

### SoldTable total profit currency

```tsx
<p className={`text-sm ${totalProfit >= 0 ? 'text-green-600' : 'text-red-600'}`}>
  Total Profit: {formatCurrency(totalProfit, 'USD')}
</p>
```

`totalProfit` is a raw sum of `p.profit` across all rows. `SoldPosition` carries its own `currency` field per row, so the sum is already apples-to-oranges when positions are in different currencies, and stamping `'USD'` on it makes it actively misleading. The Holdings tab correctly applies `resolveRate` before aggregating. The Sold table should receive `displayCurrency` as a prop and convert each row's profit before summing, or at minimum not label the total as USD when it may not be.

### Cash balance card sourcing

```tsx
const accountsWithCash = Array.from(
  new Map(holdings.map(h => [h.account.id, h.account])).values()
).filter(a => a.cashBalance != null && a.cashBalance !== 0)
```

The account list comes from the embedded `account` on each active holding. An account that has uninvested cash but no open positions (the user sold everything) will not appear in `holdings`, and therefore will not appear in the card. This is the most practically important case for the feature. The fix is to load accounts via a separate API call or, as a fallback, show a footnote that accounts with no current holdings are not shown here.

The FX conversion for the card total (`resolveRate(...) ?? 1`) correctly falls back to rate 1 when no rate exists, consistent with holdings behaviour.

### `includeCashInNetWorth` null on existing rows

`@Builder.Default private Boolean includeCashInNetWorth = true` sets the Java default for new objects, but existing rows in the database will have `NULL` after the `ddl-auto=update` migration. Any code reading this column to decide whether to include cash in Net Worth must treat `NULL` as `true` (opt-in) rather than `false` (opt-out). The Portfolio page already handles this correctly via `a.includeCashInNetWorth === false` (so null → included). Worth checking the Net Worth aggregation service applies the same rule.

### Sold grouping consistency

Groups are reduced by `account.id`, sorted alphabetically by account name, rows within each group sorted by `soldDate` descending. All groups start expanded. The chevron icons and owner badge in the group header match the existing Accounts page pattern. The table renders `colSpan={9}` for group headers, matching the 9-column definition. The implementation is clean and consistent.

</details>

---

## File map

<details>
<summary>Changed files</summary>

- `frontend/src/pages/Portfolio.tsx` — Cash balances card (new in fe72a07); `SoldTable` refactored to group by account with chevron expand/collapse (new in fe72a07).
- `backend/src/main/java/com/myfinance/model/Account.java` — Added `userId`, `owner`, `currency`, `accountNumber`, `cashBalance`, `includeCashInNetWorth`.
- `backend/src/main/java/com/myfinance/model/Holding.java` — Added `userId`, `owner`, `currency`, `purpose`, `averageBuyFxRate`; removed DB-level unique constraint.
- `backend/src/main/java/com/myfinance/model/SoldPosition.java` — New entity with asset, account, owner, buy/sell prices, profit, currency, purpose.
- `backend/src/main/java/com/myfinance/controller/AccountController.java` — `getAll` now userId-scoped; added `getByOwner`, `reassign` endpoint.
- `backend/src/main/java/com/myfinance/controller/HoldingController.java` — All endpoints now userId-scoped.
- `backend/src/main/java/com/myfinance/controller/SoldPositionController.java` — New controller; `ownerId`/`accountId` query paths and delete have tenant isolation gaps.
- `backend/src/main/java/com/myfinance/service/AccountService.java` — Added `update`, `delete` with reference checks, `reassignOwnerPositions` with holding merge logic.
- `backend/src/main/java/com/myfinance/service/HoldingService.java` — All methods userId-scoped; purpose-aware `getHolding` overload added.
- `backend/src/main/java/com/myfinance/service/SoldPositionService.java` — New service; `delete` has no ownership check.
- `backend/src/main/java/com/myfinance/repository/SoldPositionRepository.java` — New repository; `findByOwnerId` and `findByAccountId` finders are not userId-scoped.
- `backend/src/main/java/com/myfinance/repository/AccountRepository.java` — Added `findByOwnerId`, `findByUserId`.
- `backend/src/main/java/com/myfinance/repository/HoldingRepository.java` — Added userId-scoped active-holdings finders and purpose-aware `findByPosition`.
- `frontend/src/types/index.ts` — `Account` gains `cashBalance`, `includeCashInNetWorth`; `Holding` gains `averageBuyFxRate`, `currency`, `purpose`; `SoldPosition` type added.
- `frontend/src/api/index.ts` — Added `getSoldPositions`, `getShortTermTrades`, `createSoldPosition`, `deleteSoldPosition`.

Full diff: `git show fe72a07`

</details>
