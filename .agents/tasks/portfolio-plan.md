# Implementation Plan — Portfolio Enhancements

## Findings

### 1. cashBalance on Account
`cashBalance` (`BigDecimal`, nullable) already exists on `Account.java` (with `includeCashInNetWorth`
companion field, `@Column(precision=18, scale=2)`). It is present in the frontend `Account` type in
`frontend/src/types/index.ts` (`cashBalance: number | null`). It is managed in the Accounts page.
**No backend or type changes are needed for cashBalance.**

Each `Holding` returned from `/api/holdings` carries a full `Account` object (`h.account`) which
includes `cashBalance`. The Portfolio page loads this data already — no new API call is needed.

### 2. Holdings grouping structure (current state)
Holdings in Portfolio.tsx are rendered as a **flat sortable table** — columns: Asset, Type, Account,
Owner, Purpose, Qty, Avg Price, Current, Invested, Value, P&L, FX P/L. There is NO expand/collapse
row grouping. The user-facing "group" mechanism is just a filter dropdown per account.

The Sold table (`SoldTable` component at the bottom of Portfolio.tsx) is simpler: Asset, Account,
Qty, Buy Price, Sell Price, Profit, %, Period, Sold Date. It has no owner column and no grouping.

**"Group. Same as holdings"** means: restructure the Sold table to visually group rows by account —
one collapsible section per account, with a group-header row (account name + owner badge + row count
+ total profit for that group), and the individual sold rows indented beneath it. This mirrors the
visual organisation the user associates with Holdings (broker-account centric).

---

## Change 1 — Show cashBalance in Portfolio (Holdings tab)

### Where to render
In the Holdings tab, **between the three charts and the Holdings table**, add a "Cash Balances"
card section. One pill/card per account that has a non-null, non-zero `cashBalance`. Each card shows:
account name, currency badge, cash amount (display currency via FX), original amount in brackets when
it differs from display currency, and an "excl. NW" badge when `includeCashInNetWorth` is false.
A total-cash line appears in the card header. If no account has cash, the section is hidden.

### Data source
Deduplicate `holdings` by `h.account.id` — same pattern as `accountOptions` already in the file.
Filter to `cashBalance != null && cashBalance !== 0`. Convert via existing `resolveRate` helper.

---

## Change 2 — Group Sold Positions by Account

### Design decision
Add **account-level grouping with collapsible sections** to `SoldTable`. Each group header row spans
all columns and shows: chevron icon, account name, owner badge, row count badge, and group total
profit (right-aligned, green/red). Clicking the header toggles that group. All groups start expanded.
The Short-Term tab reuses `SoldTable` so it gets the same grouping for free.

---

## File-by-File Change List

| File | Change |
|------|--------|
| `frontend/src/pages/Portfolio.tsx` | **Only file changed.** (1) Cash balance cards in Holdings tab. (2) Account grouping in `SoldTable`. |

No backend changes. No type changes. No new API calls. No new files.

---

## Implementation Steps (ordered)

- [ ] 1. **Cash balance cards — derive data and total**
      In `Portfolio` component body, after the existing `accountOptions` derivation, add:
      ```ts
      const accountsWithCash = Array.from(
        new Map(holdings.map(h => [h.account.id, h.account])).values()
      ).filter(a => a.cashBalance != null && a.cashBalance !== 0);

      const totalCash = accountsWithCash.reduce((sum, a) => {
        const r = resolveRate(fxRates, a.currency, displayCurrency) ?? 1;
        return sum + (a.cashBalance! * r);
      }, 0);
      ```
      Files: `frontend/src/pages/Portfolio.tsx`
      Verify: no TypeScript error; variables are in scope for the JSX below.

- [ ] 2. **Cash balance cards — render section**
      In the Holdings tab JSX (`{tab === 'holdings' && ...}`), between the closing `</div>` of the
      charts grid and the opening `<div>` of the Holdings Table card, insert:
      ```tsx
      {accountsWithCash.length > 0 && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
          <div className="flex items-center justify-between mb-3">
            <h3 className="font-semibold text-slate-800 text-sm">Cash Balances</h3>
            <span className="text-sm font-medium text-slate-700">
              Total: {formatCurrency(totalCash, displayCurrency)}
            </span>
          </div>
          <div className="flex flex-wrap gap-3">
            {accountsWithCash.map(a => {
              const r = resolveRate(fxRates, a.currency, displayCurrency) ?? 1;
              const disp = a.cashBalance! * r;
              const excluded = a.includeCashInNetWorth === false;
              return (
                <div key={a.id} className={`flex flex-col px-4 py-2.5 rounded-lg border ${excluded ? 'border-slate-200 bg-slate-50' : 'border-indigo-100 bg-indigo-50'}`}>
                  <span className="text-xs font-medium text-slate-700 flex items-center gap-1.5">
                    {a.name}
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-200 text-slate-600">{a.currency}</span>
                    {excluded && <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-200 text-slate-400">excl. NW</span>}
                  </span>
                  <span className={`text-sm font-semibold mt-0.5 ${excluded ? 'text-slate-400' : 'text-indigo-700'}`}>
                    {formatCurrency(disp, displayCurrency)}
                    {a.currency !== displayCurrency && (
                      <span className="text-[10px] text-slate-400 font-normal ml-1">
                        ({formatCurrency(a.cashBalance!, a.currency)})
                      </span>
                    )}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}
      ```
      Files: `frontend/src/pages/Portfolio.tsx`
      Verify: cash cards appear in Holdings tab when accounts have non-zero cashBalance; hidden otherwise.

- [ ] 3. **Sold grouping — imports and state**
      Add to the top-level import from `lucide-react`:
      ```ts
      import { ChevronDown, ChevronRight } from 'lucide-react';
      ```
      (lucide-react is already imported; add these two named exports to the existing import.)

      Inside `SoldTable`, add state and derived groups:
      ```ts
      const [expandedGroups, setExpandedGroups] = useState<Set<number>>(
        () => new Set(data.map(p => p.account.id))
      );

      type GroupEntry = { account: Account; rows: SoldPosition[] };
      const groups: GroupEntry[] = Object.values(
        data.reduce((acc, p) => {
          const key = p.account.id;
          if (!acc[key]) acc[key] = { account: p.account, rows: [] };
          acc[key].rows.push(p);
          return acc;
        }, {} as Record<number, GroupEntry>)
      ).sort((a, b) => a.account.name.localeCompare(b.account.name));
      groups.forEach(g => g.rows.sort((a, b) => b.soldDate.localeCompare(a.soldDate)));
      ```
      `Account` and `SoldPosition` are already imported via `import type { ... } from '../types'`
      at the top of Portfolio.tsx — no new import needed.
      Files: `frontend/src/pages/Portfolio.tsx`
      Verify: no TypeScript error; `SoldTable` compiles cleanly.

- [ ] 4. **Sold grouping — render grouped tbody**
      Replace the current flat `<tbody>` in `SoldTable` with:
      ```tsx
      <tbody className="divide-y divide-slate-100">
        {groups.map(g => {
          const isOpen = expandedGroups.has(g.account.id);
          const groupProfit = g.rows.reduce((s, p) => s + p.profit, 0);
          const toggle = () => setExpandedGroups(prev => {
            const next = new Set(prev);
            if (next.has(g.account.id)) next.delete(g.account.id); else next.add(g.account.id);
            return next;
          });
          return (
            <React.Fragment key={`grp-${g.account.id}`}>
              <tr className="bg-slate-50 cursor-pointer hover:bg-slate-100 select-none" onClick={toggle}>
                <td colSpan={9} className="px-4 py-2.5">
                  <div className="flex items-center gap-2">
                    {isOpen
                      ? <ChevronDown size={14} className="text-slate-400 shrink-0" />
                      : <ChevronRight size={14} className="text-slate-400 shrink-0" />}
                    <span className="font-semibold text-slate-800 text-sm">{g.account.name}</span>
                    {g.account.owner && (
                      <span className="text-[10px] px-2 py-0.5 rounded-full bg-indigo-100 text-indigo-700 font-medium">
                        {g.account.owner.name}
                      </span>
                    )}
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-200 text-slate-600 font-medium">
                      {g.rows.length} position{g.rows.length !== 1 ? 's' : ''}
                    </span>
                    <span className={`ml-auto text-sm font-semibold ${groupProfit >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                      {formatCurrency(groupProfit, g.rows[0]?.currency ?? 'USD')}
                    </span>
                  </div>
                </td>
              </tr>
              {isOpen && g.rows.map(p => (
                <tr key={p.id} className="hover:bg-slate-50">
                  <td className="pl-8 pr-4 py-3">
                    <span className="font-medium text-slate-800">{p.asset.symbol}</span>
                    <p className="text-xs text-slate-400">{p.asset.name}</p>
                  </td>
                  <td className="px-4 py-3 text-slate-600">{p.account.name}</td>
                  <td className="px-4 py-3 text-right">{p.quantity}</td>
                  <td className="px-4 py-3 text-right">{formatCurrency(p.buyPrice, p.currency)}</td>
                  <td className="px-4 py-3 text-right">{formatCurrency(p.sellPrice, p.currency)}</td>
                  <td className={`px-4 py-3 text-right font-medium ${p.profit >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                    {formatCurrency(p.profit, p.currency)}
                  </td>
                  <td className={`px-4 py-3 text-right ${p.profitPercentage >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                    {formatPercent(p.profitPercentage)}
                  </td>
                  <td className="px-4 py-3 text-slate-500 text-xs">{p.holdingPeriod}</td>
                  <td className="px-4 py-3 text-slate-500">{p.soldDate}</td>
                </tr>
              ))}
            </React.Fragment>
          );
        })}
        {data.length === 0 && (
          <tr><td colSpan={9} className="px-4 py-12 text-center text-slate-400">No records</td></tr>
        )}
      </tbody>
      ```
      Note: `React.Fragment` requires `import React from 'react'` or use the fragment shorthand
      `<>...</>` with a `key` prop — since keys on `<>` aren't supported, use
      `<React.Fragment key={...}>`. Ensure `React` is in scope (add `import React from 'react'`
      if needed — currently the file uses `import { useEffect, useState } from 'react'` so add
      `React` as a default import: `import React, { useEffect, useState } from 'react'`).
      Files: `frontend/src/pages/Portfolio.tsx`
      Verify: Sold tab shows collapsible group headers; Short-Term tab also groups; total profit
      in the card header is unchanged; clicking a header collapses/expands that group.

- [ ] 5. **Build verification**
      Run `cd frontend && npm run build` — must complete with 0 TypeScript errors.
      Files: none
      Verify: `npm run build` exits 0.
