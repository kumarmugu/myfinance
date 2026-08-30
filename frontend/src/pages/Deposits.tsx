import { useEffect, useState } from 'react';
import { Plus, ArrowDownCircle, ArrowUpCircle, Trash2, Pencil } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend, PieChart, Pie, Cell, ComposedChart, Line } from 'recharts';
import { getAccountDeposits, createAccountDeposit, updateAccountDeposit, deleteAccountDeposit, getAccounts, getDashboardSummary, getCurrencyRates } from '../api';
import SearchableSelect from '../components/SearchableSelect';
import { formatCurrency, formatDate } from '../utils/formatters';
import type { AccountDeposit, Account, Currency, CurrencyRate } from '../types';
import { useToast } from '../contexts/ToastContext';

const CURRENCY_OPTIONS = ['SGD', 'USD', 'EUR', 'LKR', 'INR', 'GBP', 'AUD', 'JPY', 'CNY', 'MYR', 'THB', 'HKD', 'NZD', 'CHF', 'CAD'];

export default function Deposits() {
  const [deposits, setDeposits] = useState<AccountDeposit[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [rates, setRates] = useState<CurrencyRate[]>([]);
  const [baseCurrency, setBaseCurrency] = useState<Currency>('SGD');
  const [displayRates, setDisplayRates] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);
  const { showToast } = useToast();
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [filterOwner, setFilterOwner] = useState<string>('');
  const [filterAccount, setFilterAccount] = useState<string>('');
  const [displayCurrency, setDisplayCurrency] = useState<Currency>('SGD');

  const [form, setForm] = useState({ ownerId: 0, accountId: 0, amount: 0, depositType: 'DEPOSIT', currency: 'SGD' as Currency, depositDate: new Date().toISOString().split('T')[0], notes: '' });

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [depRes, accRes, sumRes, rateRes] = await Promise.all([
        getAccountDeposits(), getAccounts(), getDashboardSummary(), getCurrencyRates(),
      ]);
      setDeposits(depRes.data); setAccounts(accRes.data);
      setRates(rateRes.data);
      setBaseCurrency((sumRes.data.baseCurrency as Currency) || 'SGD');
      setDisplayRates(sumRes.data.displayRates || {});
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const resetForm = () => {
    setForm({ ownerId: 0, accountId: 0, amount: 0, depositType: 'DEPOSIT', currency: 'SGD', depositDate: new Date().toISOString().split('T')[0], notes: '' });
    setEditingId(null);
    setShowForm(false);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = { account: { id: form.accountId } as Account, amount: form.amount, depositType: form.depositType, currency: form.currency, depositDate: form.depositDate, notes: form.notes };
      if (editingId) {
        await updateAccountDeposit(editingId, payload);
        showToast('Record updated', 'success');
      } else {
        await createAccountDeposit(payload);
        showToast('Record added', 'success');
      }
      resetForm();
      loadData();
    } catch (err) { console.error(err); showToast('Failed to save record', 'error'); }
  };

  const startEdit = (d: AccountDeposit) => {
    setForm({
      ownerId: d.account?.owner?.id || 0,
      accountId: d.account?.id || 0,
      amount: d.amount,
      depositType: d.depositType,
      currency: (d.currency as Currency) || 'SGD',
      depositDate: d.depositDate,
      notes: d.notes || '',
    });
    setEditingId(d.id);
    setShowForm(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleDelete = async (id: number) => { if (confirm('Delete?')) { await deleteAccountDeposit(id); loadData(); } };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const ownerName = (d: AccountDeposit) => d.account?.owner?.name || '—';

  // Owners present in the loaded accounts (for the filter dropdown).
  const owners = [...new Map(accounts.filter(a => a.owner).map(a => [a.owner.id, a.owner])).values()];

  // ── Currency conversion (no hardcoded FX) ─────────────────────────────
  // Convert a record's amount from its own currency to the user's base currency
  // using the user's own CurrencyRate entries (latest by effectiveDate; direct →
  // inverse → identity fallback — mirrors backend CurrencyConversionService).
  const latestRate = (from: string, to: string): number | null => {
    if (from === to) return 1;
    const direct = rates.filter(r => r.fromCurrency === from && r.toCurrency === to)
      .sort((a, b) => (a.effectiveDate < b.effectiveDate ? 1 : -1))[0];
    if (direct) return direct.rate;
    const inverse = rates.filter(r => r.fromCurrency === to && r.toCurrency === from)
      .sort((a, b) => (a.effectiveDate < b.effectiveDate ? 1 : -1))[0];
    if (inverse && inverse.rate) return 1 / inverse.rate;
    return null;
  };
  const toBase = (amount: number, currency?: string): number => {
    const r = latestRate(currency || baseCurrency, baseCurrency);
    return r != null ? amount * r : amount; // identity fallback if no rate
  };

  // Display-currency toggle: options are exactly the currencies the backend
  // returned a display rate for; the factor converts a base amount to display.
  const displayOptions = Object.keys(displayRates).length > 0 ? Object.keys(displayRates) : [baseCurrency];
  const effectiveCurrency = (displayRates[displayCurrency] != null ? displayCurrency : baseCurrency) as Currency;
  const cFactor = displayRates[effectiveCurrency] ?? 1;
  // base-currency amount of a record, then a display-currency formatter
  const baseAmt = (d: AccountDeposit) => toBase(d.amount, d.currency);
  const fmt = (baseValue: number) => formatCurrency(baseValue * cFactor, effectiveCurrency);

  // Filters: by owner and/or by account.
  const filtered = deposits.filter(d =>
    (!filterOwner || (d.account?.owner?.id?.toString() === filterOwner)) &&
    (!filterAccount || d.account.id.toString() === filterAccount)
  );

  // Summary per account (respects the owner filter so it stays per-owner when one is selected).
  const summaryScope = filterOwner ? deposits.filter(d => d.account?.owner?.id?.toString() === filterOwner) : deposits;

  // All summary math is done in the user's BASE currency (each record converted
  // from its own currency), so mixed-currency records aggregate correctly.
  const accountSummary: Record<string, { deposits: number; withdrawals: number; net: number }> = {};
  summaryScope.forEach(d => {
    const name = d.account.name;
    const amt = baseAmt(d);
    if (!accountSummary[name]) accountSummary[name] = { deposits: 0, withdrawals: 0, net: 0 };
    if (d.depositType === 'DEPOSIT') { accountSummary[name].deposits += amt; accountSummary[name].net += amt; }
    else { accountSummary[name].withdrawals += amt; accountSummary[name].net -= amt; }
  });

  const chartData = Object.entries(accountSummary)
    .map(([name, data]) => ({ name, deposits: data.deposits, withdrawals: data.withdrawals, net: data.net }))
    .sort((a, b) => b.net - a.net);

  // Pie: distribution of net deposits per account. Pie slices can't represent
  // negatives, so only accounts with a positive net balance are shown.
  const PIE_COLORS = ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#14b8a6'];
  const pieData = chartData.filter(d => d.net > 0).map(d => ({ name: d.name, value: d.net }));
  const pieTotal = pieData.reduce((s, d) => s + d.value, 0);

  // Yearly net deposits: per-year net (deposits - withdrawals) plus a running cumulative total.
  const yearlyMap: Record<string, number> = {};
  summaryScope.forEach(d => {
    const year = (d.depositDate || '').slice(0, 4);
    if (!year) return;
    const amt = baseAmt(d);
    const signed = d.depositType === 'DEPOSIT' ? amt : -amt;
    yearlyMap[year] = (yearlyMap[year] || 0) + signed;
  });
  let running = 0;
  const yearlyData = Object.keys(yearlyMap).sort().map(year => {
    running += yearlyMap[year];
    return { year, net: yearlyMap[year], cumulative: running };
  });

  // Summary grouped by OWNER.
  const ownerSummary: Record<string, { deposits: number; withdrawals: number; net: number }> = {};
  summaryScope.forEach(d => {
    const name = ownerName(d);
    const amt = baseAmt(d);
    if (!ownerSummary[name]) ownerSummary[name] = { deposits: 0, withdrawals: 0, net: 0 };
    if (d.depositType === 'DEPOSIT') { ownerSummary[name].deposits += amt; ownerSummary[name].net += amt; }
    else { ownerSummary[name].withdrawals += amt; ownerSummary[name].net -= amt; }
  });
  const ownerRows = Object.entries(ownerSummary)
    .map(([name, data]) => ({ name, ...data }))
    .sort((a, b) => b.net - a.net);

  // Totals in base currency (displayed via fmt()).
  const totalDeposits = Object.values(accountSummary).reduce((s, v) => s + v.deposits, 0);
  const totalWithdrawals = Object.values(accountSummary).reduce((s, v) => s + v.withdrawals, 0);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Deposits & Withdrawals</h1><p className="text-slate-500 text-sm mt-0.5">Track cash flows to and from broker accounts</p></div>
        <div className="flex items-center gap-3">
          {/* Owner Selector (top, same as Dashboard) */}
          <div className="w-44">
            <SearchableSelect
              options={[{ value: '', label: 'All Owners' }, ...owners.map(o => ({ value: o.id.toString(), label: o.name, icon: o.name[0] }))]}
              value={filterOwner}
              onChange={v => { setFilterOwner(v.toString()); setFilterAccount(''); }}
              placeholder="All Owners"
            />
          </div>
          <div className="flex bg-white border border-slate-200 rounded-lg overflow-hidden">
            {displayOptions.map(code => (
              <button key={code} onClick={() => setDisplayCurrency(code as Currency)} title={`Show values in ${code}`}
                className={`px-3 py-1.5 text-xs font-medium transition-colors ${effectiveCurrency === code ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>{code}</button>
            ))}
          </div>
          <button onClick={() => { if (showForm) { resetForm(); } else { setEditingId(null); setShowForm(true); } }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
            <Plus size={16} /> Add Record
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Total Deposited</p>
          <p className="text-lg font-bold text-green-600 mt-1">{fmt(totalDeposits)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Total Withdrawn</p>
          <p className="text-lg font-bold text-red-600 mt-1">{fmt(totalWithdrawals)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Net Deposited</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{fmt(totalDeposits - totalWithdrawals)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Records</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{deposits.length}</p>
        </div>
      </div>

      {/* Charts */}
      {chartData.length > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
            <h3 className="text-sm font-semibold text-slate-800 mb-3">Deposits & Withdrawals by Account ({effectiveCurrency})</h3>
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="name" stroke="#94a3b8" fontSize={11} />
                <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `${((v * cFactor)/1000).toFixed(0)}K`} />
                <Tooltip formatter={(v) => fmt(v as number)} />
                <Legend />
                <Bar dataKey="deposits" fill="#10b981" name="Deposits" radius={[4, 4, 0, 0]} />
                <Bar dataKey="withdrawals" fill="#ef4444" name="Withdrawals" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
            <h3 className="text-sm font-semibold text-slate-800 mb-3">Net Deposit Distribution</h3>
            {pieData.length > 0 ? (
              <ResponsiveContainer width="100%" height={280}>
                <PieChart>
                  <Pie data={pieData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={100}
                    label={({ name, value }) => `${name} ${pieTotal ? ((value / pieTotal) * 100).toFixed(0) : 0}%`}
                    labelLine={false} fontSize={11}>
                    {pieData.map((_, i) => <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />)}
                  </Pie>
                  <Tooltip formatter={(v) => fmt(v as number)} />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-[280px] flex items-center justify-center text-sm text-slate-400">No positive net balances to chart</div>
            )}
          </div>
        </div>
      )}

      {/* Yearly net deposit trend */}
      {yearlyData.length > 0 && (
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Net Deposits by Year ({effectiveCurrency})</h3>
          <ResponsiveContainer width="100%" height={300}>
            <ComposedChart data={yearlyData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="year" stroke="#94a3b8" fontSize={11} />
              <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `${((v * cFactor)/1000).toFixed(0)}K`} />
              <Tooltip formatter={(v, name) => [fmt(v as number), name === 'net' ? 'Net (year)' : 'Cumulative']} />
              <Legend />
              <Bar dataKey="net" name="Net (year)" radius={[4, 4, 0, 0]}>
                {yearlyData.map((d, i) => <Cell key={i} fill={d.net >= 0 ? '#10b981' : '#ef4444'} />)}
              </Bar>
              <Line type="monotone" dataKey="cumulative" name="Cumulative" stroke="#6366f1" strokeWidth={2} dot={{ r: 3 }} />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Summary tables (right after the graphs) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {ownerRows.length > 0 && (
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="p-4 border-b border-slate-200"><h3 className="font-semibold text-slate-800 text-sm">Summary by Owner ({effectiveCurrency})</h3></div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-4 py-2.5 font-medium text-slate-600">Owner</th>
                    <th className="text-right px-4 py-2.5 font-medium text-slate-600">Total Deposited</th>
                    <th className="text-right px-4 py-2.5 font-medium text-slate-600">Total Withdrawn</th>
                    <th className="text-right px-4 py-2.5 font-medium text-slate-600">Net</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {ownerRows.map(row => (
                    <tr key={row.name} className="hover:bg-slate-50">
                      <td className="px-4 py-2.5 font-medium text-slate-800">{row.name}</td>
                      <td className="px-4 py-2.5 text-right text-green-600">{fmt(row.deposits)}</td>
                      <td className="px-4 py-2.5 text-right text-red-600">{fmt(row.withdrawals)}</td>
                      <td className="px-4 py-2.5 text-right font-medium text-slate-800">{fmt(row.net)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {Object.keys(accountSummary).length > 0 && (
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="p-4 border-b border-slate-200"><h3 className="font-semibold text-slate-800 text-sm">Net Balances by Account ({effectiveCurrency})</h3></div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-4 py-2.5 font-medium text-slate-600">Account</th>
                    <th className="text-right px-4 py-2.5 font-medium text-slate-600">Total Deposited</th>
                    <th className="text-right px-4 py-2.5 font-medium text-slate-600">Total Withdrawn</th>
                    <th className="text-right px-4 py-2.5 font-medium text-slate-600">Net</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {chartData.map(row => (
                    <tr key={row.name} className="hover:bg-slate-50">
                      <td className="px-4 py-2.5 font-medium text-slate-800">{row.name}</td>
                      <td className="px-4 py-2.5 text-right text-green-600">{fmt(row.deposits)}</td>
                      <td className="px-4 py-2.5 text-right text-red-600">{fmt(row.withdrawals)}</td>
                      <td className="px-4 py-2.5 text-right font-medium text-slate-800">{fmt(row.net)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editingId ? 'Edit Deposit/Withdrawal' : 'Record Deposit/Withdrawal'}</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Owner *</label>
              <SearchableSelect options={owners.map(o => ({ value: o.id, label: o.name }))} value={form.ownerId}
                onChange={v => setForm({...form, ownerId: Number(v), accountId: 0})} placeholder="Select owner..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Account *</label>
              <SearchableSelect options={accounts.filter(a => (a.accountType === 'BROKER' || a.accountType === 'CRYPTO_EXCHANGE') && (!form.ownerId || a.owner?.id === form.ownerId)).map(a => ({ value: a.id, label: `${a.name} (${a.currency})` }))} value={form.accountId}
                onChange={v => { const id = Number(v); const acc = accounts.find(a => a.id === id); setForm({...form, accountId: id, currency: (acc?.currency as Currency) || form.currency}); }} placeholder="Select account..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Type</label>
              <div className="flex rounded-lg overflow-hidden border border-slate-300">
                <button type="button" onClick={() => setForm({...form, depositType: 'DEPOSIT'})} className={`flex-1 py-2 text-sm font-medium ${form.depositType === 'DEPOSIT' ? 'bg-green-600 text-white' : 'bg-white text-slate-600'}`}>Deposit</button>
                <button type="button" onClick={() => setForm({...form, depositType: 'WITHDRAWAL'})} className={`flex-1 py-2 text-sm font-medium ${form.depositType === 'WITHDRAWAL' ? 'bg-red-600 text-white' : 'bg-white text-slate-600'}`}>Withdrawal</button>
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Amount *</label>
              <input type="number" step="any" value={form.amount || ''} onChange={e => setForm({...form, amount: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Currency</label>
              <SearchableSelect options={CURRENCY_OPTIONS.map(c => ({ value: c, label: c }))} value={form.currency} onChange={v => setForm({...form, currency: String(v) as Currency})} placeholder="Currency" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Date *</label>
              <input type="date" value={form.depositDate} onChange={e => setForm({...form, depositDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Notes</label>
              <input type="text" value={form.notes} onChange={e => setForm({...form, notes: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Optional" /></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editingId ? 'Update' : 'Save'}</button>
              <button type="button" onClick={resetForm} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Filter + Table */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className="w-48"><SearchableSelect options={[{ value: '', label: 'All Accounts' }, ...accounts.filter(a => (a.accountType === 'BROKER' || a.accountType === 'CRYPTO_EXCHANGE') && (!filterOwner || a.owner?.id?.toString() === filterOwner)).map(a => ({ value: a.id.toString(), label: a.name }))]} value={filterAccount} onChange={v => setFilterAccount(v.toString())} placeholder="All Accounts" /></div>
        <span className="text-xs text-slate-500">{filtered.length} records</span>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Date</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Owner</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Account</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Type</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Ccy</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">Amount</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Notes</th>
                <th className="px-4 py-2.5 w-16"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtered.map(d => (
                <tr key={d.id} className="hover:bg-slate-50 group">
                  <td className="px-4 py-2.5 text-slate-700 text-xs">{formatDate(d.depositDate)}</td>
                  <td className="px-4 py-2.5 text-slate-700 text-xs">{ownerName(d)}</td>
                  <td className="px-4 py-2.5 text-slate-700">{d.account.name}</td>
                  <td className="px-4 py-2.5">
                    <span className={`inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded-full ${d.depositType === 'DEPOSIT' ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>
                      {d.depositType === 'DEPOSIT' ? <ArrowDownCircle size={11} /> : <ArrowUpCircle size={11} />}{d.depositType}
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-xs font-medium text-indigo-600">{d.currency || 'SGD'}</td>
                  <td className={`px-4 py-2.5 text-right font-medium ${d.depositType === 'DEPOSIT' ? 'text-green-600' : 'text-red-600'}`}>{formatCurrency(d.amount, d.currency)}</td>
                  <td className="px-4 py-2.5 text-slate-500 text-xs">{d.notes || '-'}</td>
                  <td className="px-4 py-2.5">
                    <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100">
                      <button onClick={() => startEdit(d)} className="text-slate-400 hover:text-indigo-600" title="Edit"><Pencil size={14} /></button>
                      <button onClick={() => handleDelete(d.id)} className="text-slate-400 hover:text-red-500" title="Delete"><Trash2 size={14} /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && <tr><td colSpan={8} className="px-4 py-12 text-center text-slate-400">No records</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
