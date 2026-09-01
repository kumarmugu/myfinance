import { useEffect, useState } from 'react';
import { Plus, Trash2, Pencil, ArrowUpCircle, ArrowDownCircle, Lock } from 'lucide-react';
import { getTransactions, createTransaction, updateTransaction, deleteTransaction, getAssets, getAccounts, getOwners, getSoldPositions, getCurrencyRates, getActiveHoldings } from '../api';
import { useAuth } from '../contexts/AuthContext';
import { formatCurrency, formatDate, formatPercent } from '../utils/formatters';
import SearchableSelect from '../components/SearchableSelect';
import ExportMenu from '../components/ExportMenu';
import { transactionsExportConfig } from '../utils/export/configs';
import { useToast } from '../contexts/ToastContext';
import type { Transaction, Asset, Account, Owner, TransactionRequest, SoldPosition, CurrencyRate, Holding } from '../types';

/** Latest rate to convert `from` → `to` from the user's stored FX rates (direct, then inverse). */
function resolveRate(rates: CurrencyRate[], from: string, to: string): number | null {
  if (!from || !to) return null;
  if (from.toUpperCase() === to.toUpperCase()) return 1;
  const f = from.toUpperCase(), t = to.toUpperCase();
  const direct = rates.filter(r => r.fromCurrency.toUpperCase() === f && r.toCurrency.toUpperCase() === t)
    .sort((a, b) => b.effectiveDate.localeCompare(a.effectiveDate))[0];
  if (direct) return direct.rate;
  const inverse = rates.filter(r => r.fromCurrency.toUpperCase() === t && r.toCurrency.toUpperCase() === f)
    .sort((a, b) => b.effectiveDate.localeCompare(a.effectiveDate))[0];
  if (inverse && inverse.rate) return 1 / inverse.rate;
  return null;
}

export default function Transactions() {
  const { verifyPassword } = useAuth();
  const { showToast } = useToast();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [owners, setOwners] = useState<Owner[]>([]);
  const [soldPositions, setSoldPositions] = useState<SoldPosition[]>([]);
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [fxRates, setFxRates] = useState<CurrencyRate[]>([]);
  // Client-side column filters (owner stays server-side via filterOwner).
  const [filterType, setFilterType] = useState<string>('');
  const [filterAssetId, setFilterAssetId] = useState<string>('');
  const [filterAccountId, setFilterAccountId] = useState<string>('');
  const [filterPurpose, setFilterPurpose] = useState<string>('');
  const [filterDateFrom, setFilterDateFrom] = useState<string>('');
  const [filterDateTo, setFilterDateTo] = useState<string>('');
  const [search, setSearch] = useState<string>('');
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [filterOwner, setFilterOwner] = useState<number | undefined>();
  const [deleteModal, setDeleteModal] = useState<{ id: number; symbol: string } | null>(null);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteError, setDeleteError] = useState('');

  const [form, setForm] = useState<TransactionRequest>({
    assetId: 0, accountId: 0, ownerId: 0, transactionType: 'BUY',
    quantity: 0, pricePerUnit: 0, fees: 0, feeCurrency: undefined, fxRateToBase: undefined,
    transactionDate: new Date().toISOString().split('T')[0], notes: '', purpose: 'LONG_TERM',
  });

  useEffect(() => { loadData(); }, [filterOwner]);

  const loadData = async () => {
    try {
      const [txRes, assetRes, accRes, ownerRes, soldRes, fxRes, holdRes] = await Promise.all([getTransactions(filterOwner), getAssets(), getAccounts(), getOwners(), getSoldPositions(filterOwner), getCurrencyRates(), getActiveHoldings(filterOwner)]);
      setTransactions(txRes.data); setAssets(assetRes.data); setAccounts(accRes.data); setOwners(ownerRes.data); setSoldPositions(soldRes.data); setFxRates(fxRes.data); setHoldings(holdRes.data);
      if (ownerRes.data.length > 0 && form.ownerId === 0) setForm(f => ({ ...f, ownerId: ownerRes.data[0].id }));
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingId != null) {
        await updateTransaction(editingId, form);
        showToast('Transaction updated', 'success');
      } else {
        await createTransaction(form);
        showToast('Transaction saved', 'success');
      }
      setShowForm(false); setEditingId(null); loadData();
    }
    catch (err) { console.error(err); showToast(editingId != null ? 'Failed to update transaction' : 'Failed to create transaction'); }
  };

  const startEdit = (tx: Transaction) => {
    setEditingId(tx.id);
    setForm({
      assetId: tx.asset.id,
      accountId: tx.account.id,
      ownerId: tx.owner.id,
      transactionType: tx.transactionType,
      quantity: tx.quantity,
      pricePerUnit: tx.pricePerUnit,
      fees: tx.fees ?? 0,
      feeCurrency: tx.feeCurrency ?? undefined,
      fxRateToBase: tx.fxRateToBase ?? undefined,
      currency: tx.currency,
      transactionDate: tx.transactionDate,
      notes: tx.notes ?? '',
      purpose: tx.purpose ?? 'LONG_TERM',
    });
    setShowForm(true);
  };

  const confirmDelete = async () => {
    if (!deleteModal) return;
    setDeleteError('');
    const valid = await verifyPassword(deletePassword);
    if (!valid) { setDeleteError('Incorrect password'); return; }
    await deleteTransaction(deleteModal.id);
    setDeleteModal(null); setDeletePassword('');
    loadData();
  };

  // Look up the live asset (for its current price + when that price was last updated).
  const assetById = new Map(assets.map(a => [a.id, a]));

  // Remaining held quantity per (asset, account, owner) — used to classify a BUY as open/closed.
  const heldQtyKey = (assetId: number, accountId: number, ownerId: number) => `${assetId}:${accountId}:${ownerId}`;
  const heldQtyByKey = new Map<string, number>();
  holdings.forEach(h => {
    if (h.asset && h.account && h.owner) heldQtyByKey.set(heldQtyKey(h.asset.id, h.account.id, h.owner.id), h.quantity);
  });

  type BuyStatus = 'OPEN' | 'PARTIAL' | 'CLOSED';
  type Pnl =
    | { kind: 'unrealized'; amount: number; pct: number; currency: string; priceUpdatedAt?: string | null }
    | { kind: 'realized'; amount: number; pct: number; currency: string }
    | { kind: 'status'; status: BuyStatus }
    | { kind: 'noRate' }
    | null;

  /**
   * Per-row P/L semantics (Option A — P/L is a property of a position, not a raw row):
   *
   *  BUY row:
   *    - Fully OPEN (all bought shares still held) → unrealized P/L vs the asset's current price,
   *      in the broker account's currency (FX-inclusive: purchase FX for cost, today's FX for value).
   *    - PARTIALLY sold → no single P/L number is meaningful (the lot straddles realized + open),
   *      so we show a "Partially sold" status; the realized part shows on the SELL row(s).
   *    - Fully CLOSED (nothing of this asset still held) → "Closed"; realized P/L is on the SELL row(s).
   *
   *  SELL row → realized P/L for that sale, from the matching SoldPosition.
   */
  const rowPnl = (tx: Transaction): Pnl => {
    const acctCcy = tx.account?.currency || tx.currency;

    if (tx.transactionType === 'BUY') {
      const held = heldQtyByKey.get(heldQtyKey(tx.asset.id, tx.account.id, tx.owner.id)) ?? 0;
      // Classify against what this buy added. Partial sells make a single P/L number meaningless.
      if (held <= 0) return { kind: 'status', status: 'CLOSED' };
      if (held + 1e-9 < tx.quantity) return { kind: 'status', status: 'PARTIAL' };

      // Fully open: show unrealized P/L on this lot.
      const live = assetById.get(tx.asset.id);
      const marketPrice = live?.currentPrice; // asset's own currency
      const assetCcy = live?.currency || tx.currency;
      if (marketPrice == null || !tx.pricePerUnit) return { kind: 'status', status: 'OPEN' };

      const purchaseRate = tx.fxRateToBase ?? resolveRate(fxRates, tx.currency, acctCcy);
      if (purchaseRate == null) return { kind: 'noRate' };
      const tradeCostAcct = tx.pricePerUnit * tx.quantity * purchaseRate;
      const feeCcy = tx.feeCurrency || tx.currency;
      const feeAcct = (tx.fees || 0) * (resolveRate(fxRates, feeCcy, acctCcy) ?? 1);
      const costAcct = tradeCostAcct + feeAcct;

      const marketRate = resolveRate(fxRates, assetCcy, acctCcy);
      if (marketRate == null) return { kind: 'noRate' };
      const valueAcct = marketPrice * tx.quantity * marketRate;

      const amount = valueAcct - costAcct;
      return { kind: 'unrealized', amount, pct: costAcct ? (amount / costAcct) * 100 : 0, currency: acctCcy, priceUpdatedAt: live?.priceUpdatedAt };
    }

    if (tx.transactionType === 'SELL') {
      const match = soldPositions.find(sp =>
        sp.asset?.id === tx.asset.id &&
        sp.account?.id === tx.account.id &&
        sp.owner?.id === tx.owner.id &&
        sp.soldDate === tx.transactionDate &&
        Math.abs(sp.quantity - tx.quantity) < 1e-9
      ) ?? soldPositions.find(sp =>
        sp.asset?.id === tx.asset.id && sp.soldDate === tx.transactionDate && Math.abs(sp.quantity - tx.quantity) < 1e-9
      );
      if (!match) return null;
      return { kind: 'realized', amount: match.profit, pct: match.profitPercentage, currency: match.currency || acctCcy };
    }
    return null;
  };

  // Apply the client-side column filters (combined with AND). The owner filter is applied
  // server-side (re-queries), so it is not repeated here.
  const searchLc = search.trim().toLowerCase();
  const filtered = transactions.filter(tx => {
    if (filterType && tx.transactionType !== filterType) return false;
    if (filterAssetId && String(tx.asset?.id) !== filterAssetId) return false;
    if (filterAccountId && String(tx.account?.id) !== filterAccountId) return false;
    if (filterPurpose && (tx.purpose ?? '') !== filterPurpose) return false;
    if (filterDateFrom && tx.transactionDate < filterDateFrom) return false;
    if (filterDateTo && tx.transactionDate > filterDateTo) return false;
    if (searchLc) {
      const hay = [tx.asset?.symbol, tx.asset?.name, tx.account?.name, tx.owner?.name, tx.notes]
        .filter(Boolean).join(' ').toLowerCase();
      if (!hay.includes(searchLc)) return false;
    }
    return true;
  });

  const filtersActive = !!(filterType || filterAssetId || filterAccountId || filterPurpose || filterDateFrom || filterDateTo || searchLc);
  const clearFilters = () => {
    setFilterType(''); setFilterAssetId(''); setFilterAccountId('');
    setFilterPurpose(''); setFilterDateFrom(''); setFilterDateTo(''); setSearch('');
  };

  const PURPOSE_OPTIONS: [string, string][] = [
    ['LONG_TERM', 'Long Term'], ['TRADING', 'Trading'], ['DIVIDEND_REINVESTMENT', 'Div Reinvest'],
    ['SRS', 'SRS'], ['RETIREMENT', 'Retirement'], ['SHORT_TERM', 'Short Term'],
  ];

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div><h1 className="text-2xl font-bold text-slate-800">Transactions</h1><p className="text-slate-500 text-sm mt-0.5">Record buy and sell transactions</p></div>
        <div className="flex items-center gap-3">
          {/* Owner Filter */}
          <div className="w-44">
            <SearchableSelect
              options={[{ value: '', label: 'All Owners' }, ...owners.map(o => ({ value: o.id, label: o.name, icon: o.name[0] }))]}
              value={filterOwner || ''}
              onChange={v => setFilterOwner(v ? Number(v) : undefined)}
              placeholder="All Owners"
            />
          </div>
          <ExportMenu
            rows={filtered}
            config={transactionsExportConfig}
            subtitle={(filterOwner || filtersActive) ? 'Filtered view' : undefined}
          />
          <button onClick={() => { if (showForm) { setShowForm(false); setEditingId(null); } else { setEditingId(null); setShowForm(true); } }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
            <Plus size={16} /> New Transaction
          </button>
        </div>
      </div>

      {/* Filter bar */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-3">
        <div className="flex flex-wrap items-end gap-3">
          <div className="w-32">
            <label className="block text-[11px] font-medium text-slate-500 mb-1">Type</label>
            <select value={filterType} onChange={e => setFilterType(e.target.value)} className="w-full border border-slate-300 rounded-lg px-2 py-2 text-sm bg-white">
              <option value="">All</option>
              <option value="BUY">Buy</option>
              <option value="SELL">Sell</option>
            </select>
          </div>
          <div className="w-44">
            <label className="block text-[11px] font-medium text-slate-500 mb-1">Asset</label>
            <SearchableSelect
              options={[{ value: '', label: 'All Assets' }, ...assets.map(a => ({ value: a.id.toString(), label: `${a.symbol}` }))]}
              value={filterAssetId}
              onChange={v => setFilterAssetId(v.toString())}
              placeholder="All Assets"
            />
          </div>
          <div className="w-44">
            <label className="block text-[11px] font-medium text-slate-500 mb-1">Account</label>
            <SearchableSelect
              options={[{ value: '', label: 'All Accounts' }, ...accounts.map(a => ({ value: a.id.toString(), label: a.name }))]}
              value={filterAccountId}
              onChange={v => setFilterAccountId(v.toString())}
              placeholder="All Accounts"
            />
          </div>
          <div className="w-40">
            <label className="block text-[11px] font-medium text-slate-500 mb-1">Purpose</label>
            <select value={filterPurpose} onChange={e => setFilterPurpose(e.target.value)} className="w-full border border-slate-300 rounded-lg px-2 py-2 text-sm bg-white">
              <option value="">All</option>
              {PURPOSE_OPTIONS.map(([val, label]) => <option key={val} value={val}>{label}</option>)}
            </select>
          </div>
          <div className="w-36">
            <label className="block text-[11px] font-medium text-slate-500 mb-1">From</label>
            <input type="date" value={filterDateFrom} onChange={e => setFilterDateFrom(e.target.value)} className="w-full border border-slate-300 rounded-lg px-2 py-2 text-sm" />
          </div>
          <div className="w-36">
            <label className="block text-[11px] font-medium text-slate-500 mb-1">To</label>
            <input type="date" value={filterDateTo} onChange={e => setFilterDateTo(e.target.value)} className="w-full border border-slate-300 rounded-lg px-2 py-2 text-sm" />
          </div>
          <div className="flex-1 min-w-[10rem]">
            <label className="block text-[11px] font-medium text-slate-500 mb-1">Search</label>
            <input type="text" value={search} onChange={e => setSearch(e.target.value)} placeholder="Symbol, account, owner, notes..." className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" />
          </div>
          {filtersActive && (
            <button onClick={clearFilters} className="px-3 py-2 text-sm font-medium text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">Clear</button>
          )}
        </div>
        <p className="text-[11px] text-slate-400 mt-2">Showing {filtered.length} of {transactions.length} transactions</p>
      </div>

      {/* Transaction Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editingId != null ? 'Modify Transaction' : 'Add Transaction'}</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Owner</label>
              <SearchableSelect options={owners.map(o => ({ value: o.id, label: o.name }))} value={form.ownerId} onChange={v => setForm({...form, ownerId: v})} placeholder="Select owner..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Asset</label>
              <SearchableSelect options={assets.map(a => ({ value: a.id, label: `${a.symbol} - ${a.name}` }))} value={form.assetId} onChange={v => setForm({...form, assetId: v})} placeholder="Search asset..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Account</label>
              <SearchableSelect options={accounts.map(a => ({ value: a.id, label: `${a.name} (${a.currency})` }))} value={form.accountId} onChange={v => setForm({...form, accountId: v})} placeholder="Select account..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Type</label>
              <div className="flex rounded-lg overflow-hidden border border-slate-300">
                <button type="button" onClick={() => setForm({...form, transactionType: 'BUY'})} className={`flex-1 py-2 text-sm font-medium transition-colors ${form.transactionType === 'BUY' ? 'bg-green-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}>Buy</button>
                <button type="button" onClick={() => setForm({...form, transactionType: 'SELL'})} className={`flex-1 py-2 text-sm font-medium transition-colors ${form.transactionType === 'SELL' ? 'bg-red-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}>Sell</button>
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Quantity</label>
              <input type="number" step="any" value={form.quantity || ''} onChange={e => setForm({...form, quantity: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Price per Unit</label>
              <input type="number" step="any" value={form.pricePerUnit || ''} onChange={e => setForm({...form, pricePerUnit: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Date</label>
              <input type="date" value={form.transactionDate} onChange={e => setForm({...form, transactionDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Fees</label>
              <input type="number" step="any" value={form.fees || ''} onChange={e => setForm({...form, fees: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Optional" /></div>
            {(() => {
              // Cross-currency block: shown only when the selected account's currency differs from
              // the selected asset's currency (e.g. USD-priced Tesla bought via an SGD Saxo account).
              const acct = accounts.find(a => a.id === form.accountId);
              const asset = assets.find(a => a.id === form.assetId);
              const tradeCcy = asset?.currency;              // instrument/trade currency
              const acctCcy = acct?.currency;                // settlement/account currency
              if (!acct || !asset || !tradeCcy || !acctCcy || tradeCcy === acctCcy) {
                return (
                  <div><label className="block text-xs font-medium text-slate-600 mb-1">Fee Currency</label>
                    <input type="text" value={form.feeCurrency || (acctCcy || tradeCcy || '')} disabled className="w-full border border-slate-200 bg-slate-50 rounded-lg px-3 py-2 text-sm text-slate-500" /></div>
                );
              }
              const suggested = resolveRate(fxRates, tradeCcy, acctCcy);
              const rate = form.fxRateToBase ?? suggested ?? 0;
              const costPreview = (form.pricePerUnit || 0) * (form.quantity || 0) * (rate || 0)
                + (form.fees || 0) * (((form.feeCurrency || acctCcy) === acctCcy) ? 1 : (resolveRate(fxRates, form.feeCurrency || acctCcy, acctCcy) ?? 1));
              return (
                <>
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1">FX Rate ({tradeCcy}→{acctCcy}) at purchase</label>
                    <input type="number" step="any"
                      value={form.fxRateToBase ?? (suggested != null ? Number(suggested.toFixed(6)) : '')}
                      onChange={e => setForm({ ...form, fxRateToBase: parseFloat(e.target.value) || undefined })}
                      className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm"
                      placeholder={suggested != null ? `≈ ${suggested.toFixed(4)}` : 'Enter rate'} />
                    <p className="text-[10px] text-slate-400 mt-0.5">{suggested != null ? `Configured rate ≈ ${suggested.toFixed(4)} — override with your actual purchase rate` : 'No configured rate; enter your actual purchase rate'}</p>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1">Fee Currency</label>
                    <SearchableSelect
                      options={[acctCcy, tradeCcy].map(c => ({ value: c, label: c }))}
                      value={form.feeCurrency || acctCcy}
                      onChange={v => setForm({ ...form, feeCurrency: String(v) })}
                      placeholder="Fee currency" />
                  </div>
                  <div className="lg:col-span-2 flex items-end">
                    <p className="text-xs text-slate-500">Est. cost in {acctCcy}: <span className="font-semibold text-slate-700">{formatCurrency(costPreview, acctCcy)}</span> <span className="text-slate-400">(reconcile against what {acct.name} debited)</span></p>
                  </div>
                </>
              );
            })()}
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Notes</label>
              <input type="text" value={form.notes} onChange={e => setForm({...form, notes: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Optional" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Purpose</label>
              <div className="flex flex-wrap gap-1.5">
                {([['LONG_TERM', 'Long Term'], ['TRADING', 'Trading'], ['DIVIDEND_REINVESTMENT', 'Div Reinvest'], ['SRS', 'SRS'], ['RETIREMENT', 'Retirement'], ['SHORT_TERM', 'Short Term']] as const).map(([val, label]) => (
                  <button key={val} type="button" onClick={() => setForm({...form, purpose: val as any})}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors border ${form.purpose === val ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-300 hover:border-indigo-300'}`}>{label}</button>
                ))}
              </div></div>
            <div className="flex items-end gap-2 lg:col-span-4">
              <button type="submit" className="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editingId != null ? 'Update Transaction' : 'Save Transaction'}</button>
              <button type="button" onClick={() => { setShowForm(false); setEditingId(null); }} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-200">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Transaction Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Date</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Type</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Asset</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Account</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Owner</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Purpose</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Qty</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Price</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Current</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Total</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">P/L</th>
                <th className="px-4 py-3 w-10"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtered.map(tx => (
                <tr key={tx.id} className="hover:bg-slate-50 group">
                  <td className="px-4 py-2.5 text-slate-700 text-xs">{formatDate(tx.transactionDate)}</td>
                  <td className="px-4 py-2.5">
                    <span className={`inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded-full ${tx.transactionType === 'BUY' ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>
                      {tx.transactionType === 'BUY' ? <ArrowDownCircle size={11} /> : <ArrowUpCircle size={11} />}{tx.transactionType}
                    </span>
                  </td>
                  <td className="px-4 py-2.5"><span className="font-medium text-slate-800">{tx.asset.symbol}</span></td>
                  <td className="px-4 py-2.5 text-slate-600">{tx.account.name}</td>
                  <td className="px-4 py-2.5 text-slate-500 text-xs">{tx.owner.name}</td>
                  <td className="px-4 py-2.5"><span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">{tx.purpose ? tx.purpose.replace(/_/g, ' ') : '-'}</span></td>
                  <td className="px-4 py-2.5 text-right text-slate-700">{tx.quantity}</td>
                  <td className="px-4 py-2.5 text-right text-slate-700">{formatCurrency(tx.pricePerUnit, tx.currency)}</td>
                  <td className="px-4 py-2.5 text-right text-slate-600">
                    {(() => {
                      const live = assetById.get(tx.asset.id);
                      if (tx.transactionType !== 'BUY' || live?.currentPrice == null) return <span className="text-slate-300">-</span>;
                      return (
                        <div>
                          <span>{formatCurrency(live.currentPrice, tx.currency)}</span>
                          {live.priceUpdatedAt && <p className="text-[10px] text-slate-400">as of {formatDate(live.priceUpdatedAt)}</p>}
                        </div>
                      );
                    })()}
                  </td>
                  <td className="px-4 py-2.5 text-right font-medium text-slate-800">{formatCurrency(tx.totalAmount, tx.currency)}</td>
                  <td className="px-4 py-2.5 text-right">
                    {(() => {
                      const pnl = rowPnl(tx);
                      if (!pnl) return <span className="text-slate-300">-</span>;
                      if (pnl.kind === 'noRate') return <span className="text-[10px] text-amber-500" title="Set an FX rate for this currency pair to see P/L">no FX rate</span>;
                      if (pnl.kind === 'status') {
                        const label = pnl.status === 'CLOSED' ? 'Closed' : pnl.status === 'PARTIAL' ? 'Partially sold' : 'Open';
                        const tip = pnl.status === 'OPEN'
                          ? 'Held — set the asset current price to see unrealized P/L'
                          : 'Realized P/L is shown on the matching Sell row (see Portfolio → Sold Positions)';
                        return <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-500" title={tip}>{label}</span>;
                      }
                      // unrealized (open BUY) or realized (SELL)
                      const cls = pnl.amount >= 0 ? 'text-green-600' : 'text-red-600';
                      const pctCls = pnl.pct >= 0 ? 'text-green-500' : 'text-red-500';
                      return (
                        <div>
                          <span className={`font-medium ${cls}`}>{formatCurrency(pnl.amount, pnl.currency)}</span>
                          <p className={`text-[10px] ${pctCls}`}>{formatPercent(pnl.pct)}{pnl.kind === 'realized' ? ' realized' : ''}</p>
                        </div>
                      );
                    })()}
                  </td>
                  <td className="px-4 py-2.5">
                    <div className="flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => startEdit(tx)} className="text-slate-400 hover:text-indigo-600" title="Modify transaction">
                        <Pencil size={14} />
                      </button>
                      <button onClick={() => setDeleteModal({ id: tx.id, symbol: tx.asset.symbol })} className="text-slate-400 hover:text-red-500" title="Delete (requires password)">
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && <tr><td colSpan={11} className="px-4 py-12 text-center text-slate-400">{transactions.length === 0 ? 'No transactions yet' : 'No transactions match the filters'}</td></tr>}
            </tbody>
          </table>
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      {deleteModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl p-6 w-full max-w-sm shadow-2xl">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 bg-red-100 rounded-lg"><Lock size={20} className="text-red-600" /></div>
              <div>
                <h3 className="font-semibold text-slate-800">Confirm Delete</h3>
                <p className="text-sm text-slate-500">Transaction: {deleteModal.symbol}</p>
              </div>
            </div>
            <p className="text-sm text-slate-600 mb-4">Enter your password to confirm deletion. This action cannot be undone.</p>
            {deleteError && <p className="text-sm text-red-600 mb-3 bg-red-50 p-2 rounded">{deleteError}</p>}
            <input type="password" value={deletePassword} onChange={e => setDeletePassword(e.target.value)} placeholder="Enter your password" className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm mb-4 focus:ring-2 focus:ring-red-500 focus:border-red-500" autoFocus onKeyDown={e => e.key === 'Enter' && confirmDelete()} />
            <div className="flex gap-2">
              <button onClick={confirmDelete} disabled={!deletePassword} className="flex-1 py-2.5 bg-red-600 text-white rounded-lg text-sm font-medium hover:bg-red-700 disabled:opacity-40 transition-colors">Delete</button>
              <button onClick={() => { setDeleteModal(null); setDeletePassword(''); setDeleteError(''); }} className="flex-1 py-2.5 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-200">Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
