# Implementation Plan — Sri Lanka FD Enhancements

## Findings from codebase exploration

### Requirement 1 — Account Number column
- `accountNumber` already exists on `FixedDeposit` (backend entity: `private String accountNumber;`).
- It is already in the `FixedDeposit` TypeScript interface (`accountNumber: string` in `frontend/src/types/index.ts`).
- It is already sent in the create/edit payload inside `handleFdSubmit` in `SriLankaFD.tsx`.
- It is **not shown as a table column** — the table has: Holder, Bank, Principal, Rate, Maturity, Period, Interest, Status, Actions.
- **No backend changes needed.** Only the frontend table needs a new column.

### Requirement 2 — Search by account number and joint holder
- There is currently **no free-text search input** — filtering is done via two `SearchableSelect` dropdowns (Bank, Holder).
- A text search input must be added. It filters the `filteredFDs` array client-side on: bank short name, holder name, joint holder name, and account number.
- **No backend changes needed.** All data is already fetched; filtering is purely client-side.

### Requirement 3 — "Needs Update" badge when maturity date is null/empty
- The task says: show "Needs Update" badge **in addition to** the normal Active/Matured/etc. status badge when `maturityDate` is null or empty.
- The existing `requiresUpdate: boolean` flag is a separate, manual concept; the new badge is purely derived from `!fd.maturityDate`.
- This is a frontend-only display change.
- `maturityDate` is typed `string` in TypeScript; a null from the API arrives as `null`, so we widen the type to `string | null`.

---

## Implementation Steps

- [ ] 1. Add "Account Number" column to the FD table in `SriLankaFD.tsx`.

  The table header has 9 `<th>` cells. Add one new `<th>` **"Acct #"** after the "Bank" header (new order: Holder, Bank, Acct #, Principal, Rate, Maturity, Period, Interest, Status, Actions = 10 columns).

  In the `<tbody>` rows, add a corresponding `<td>` after the Bank cell:
  ```tsx
  <td className="px-3 py-2.5 text-slate-500 text-[11px]">{fd.accountNumber || '—'}</td>
  ```

  Update the empty-state row's `colSpan` from `9` to `10`.

  Files: `frontend/src/pages/SriLankaFD.tsx`

  Verify: `cd frontend && npm run build` — TypeScript compiles without errors.

- [ ] 2. Add a free-text search input and extend `filteredFDs` filter logic in `SriLankaFD.tsx`.

  **State:** Add alongside the existing filter state variables:
  ```ts
  const [searchText, setSearchText] = useState('');
  ```

  **Filter logic:** Replace the current `filteredFDs` computation:
  ```ts
  const filteredFDs = fds.filter(fd => {
    if (filterBank && fd.bank.id.toString() !== filterBank) return false;
    if (filterHolder && fd.holder.id.toString() !== filterHolder) return false;
    return true;
  });
  ```
  with:
  ```ts
  const searchLower = searchText.toLowerCase();
  const filteredFDs = fds.filter(fd => {
    if (filterBank && fd.bank.id.toString() !== filterBank) return false;
    if (filterHolder && fd.holder.id.toString() !== filterHolder) return false;
    if (searchLower) {
      const haystack = [
        fd.bank.shortName,
        fd.holder.name,
        fd.jointHolder?.name ?? '',
        fd.accountNumber ?? '',
      ].join(' ').toLowerCase();
      if (!haystack.includes(searchLower)) return false;
    }
    return true;
  });
  ```

  **UI:** In the Filters section (`<div className="flex gap-3 items-center flex-wrap">`), add a plain text input **before** the bank SearchableSelect:
  ```tsx
  <input
    type="text"
    value={searchText}
    onChange={e => setSearchText(e.target.value)}
    placeholder="Search account #, holder, bank..."
    className="border border-slate-300 rounded-lg px-3 py-2 text-sm w-64"
  />
  ```

  Files: `frontend/src/pages/SriLankaFD.tsx`

  Verify: `cd frontend && npm run build` — compiles without errors.

- [ ] 3. Add "Needs Update" badge in the status cell when `maturityDate` is null/empty, and widen the type.

  **Type change** — in `frontend/src/types/index.ts`, on the `FixedDeposit` interface, change:
  ```ts
  maturityDate: string;
  ```
  to:
  ```ts
  maturityDate: string | null;
  ```

  **Status cell** — in `SriLankaFD.tsx`, the current status cell:
  ```tsx
  <td className="px-3 py-2.5">
    <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${
      fd.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
      fd.status === 'REQUIRES_UPDATE' ? 'bg-amber-100 text-amber-700' :
      'bg-slate-100 text-slate-600'
    }`}>{fd.status}</span>
  </td>
  ```
  Replace with:
  ```tsx
  <td className="px-3 py-2.5">
    <div className="flex flex-wrap gap-1">
      <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${
        fd.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
        fd.status === 'REQUIRES_UPDATE' ? 'bg-amber-100 text-amber-700' :
        'bg-slate-100 text-slate-600'
      }`}>{fd.status}</span>
      {!fd.maturityDate && (
        <span className="text-[10px] px-1.5 py-0.5 rounded-full font-medium bg-yellow-100 text-yellow-700 border border-yellow-300">
          Needs Update
        </span>
      )}
    </div>
  </td>
  ```

  The normal status badge is always shown; the yellow "Needs Update" pill appears beside it only when `maturityDate` is falsy.

  Files:
  - `frontend/src/pages/SriLankaFD.tsx`
  - `frontend/src/types/index.ts`

  Verify: `cd frontend && npm run build` — compiles without errors. Also check that the `startEditFd` function that reads `fd.maturityDate` still compiles (it assigns to a string form field — safe because the string input will receive an empty string for null, which is already handled by the `|| ''` pattern used for other nullable fields).

---

## Summary of files changed

| File | Change |
|---|---|
| `frontend/src/pages/SriLankaFD.tsx` | (1) Add "Acct #" column header + cell to table, update colSpan to 10; (2) Add `searchText` state, extend filter logic to match accountNumber/joint/bank/holder, add search input in filters bar; (3) Replace single-badge status cell with flex container showing status + conditional "Needs Update" badge |
| `frontend/src/types/index.ts` | Widen `FixedDeposit.maturityDate` from `string` to `string | null` |

**No backend changes required.** `accountNumber` already exists on the entity and is already included in every API response (entity returned directly). The search is client-side against already-fetched data. The "Needs Update" badge is derived purely from a null-check on an already-returned field.
