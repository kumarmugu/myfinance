import { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, Check, Eye, EyeOff } from 'lucide-react';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts';
import api from '../api';
import { formatCurrency, formatNumber, formatDate } from '../utils/formatters';
import SearchableSelect from '../components/SearchableSelect';
import { useToast } from '../contexts/ToastContext';

// Palette for the currency-distribution pie (reused across slices if more currencies).
const PIE_COLORS = ['#4f46e5', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6'];

interface BankSavingsAccount {
  id: number;
  accountName: string;
  bankName: string;
  accountNumber: string;
  balance: number;
  currency: string;
  country: string;
  includeInNetWorth: boolean;
  lastUpdated: string;
  notes: string;
}

export default function BankSavings() {
  const [accounts, setAccounts] = useState<BankSavingsAccount[]>([]);
  const [summary, setSummary] = useState<{ totalBalance: number; inNetWorth: number; baseCurrency?: string } | null>(null);
  const [loading, setLoading] = useState(true);
  const [unmasked, setUnmasked] = useState<Set<number>>(new Set());
  // Currency shown in the "Balance by Bank" pie. Currencies are never mixed in one pie
  // (you can't compare LKR vs SGD directly), so the chart always shows a single currency.
  const [chartCurrency, setChartCurrency] = useState<string | null>(null);
  const { showToast } = useToast();
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<BankSavingsAccount | null>(null);
  const [form, setForm] = useState({
    accountName: '', bankName: '', accountNumber: '', balance: 0,
    currency: 'SGD', country: 'Singapore', includeInNetWorth: true, notes: ''
  });

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [listRes, sumRes] = await Promise.all([
        api.get('/bank-savings'),
        api.get('/bank-savings/summary'),
      ]);
      setAccounts(listRes.data);
      setSummary(sumRes.data);
    }
    catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const resetForm = () => setForm({ accountName: '', bankName: '', accountNumber: '', balance: 0, currency: 'SGD', country: 'Singapore', includeInNetWorth: true, notes: '' });

  const toggleMask = (id: number) => {
    setUnmasked(prev => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  };

  const maskNumber = (num: string | null, id: number) => {
    if (!num) return '-';
    if (unmasked.has(id)) return num;
    if (num.length <= 4) return '****';
    return '****' + num.slice(-4);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editing) { await api.put(`/bank-savings/${editing.id}`, form); }
      else { await api.post('/bank-savings', form); }
      setShowForm(false); setEditing(null); resetForm(); loadData();
      showToast(editing ? 'Account updated' : 'Account added', 'success');
    } catch (err: any) {
      console.error(err);
      showToast(err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to save account');
    }
  };

  const startEdit = (acc: BankSavingsAccount) => {
    setEditing(acc);
    setForm({ accountName: acc.accountName, bankName: acc.bankName || '', accountNumber: acc.accountNumber || '', balance: acc.balance, currency: acc.currency, country: acc.country || 'Singapore', includeInNetWorth: acc.includeInNetWorth, notes: acc.notes || '' });
    setShowForm(true);
  };

  const handleDelete = async (id: number) => { if (confirm('Delete?')) { await api.delete(`/bank-savings/${id}`); loadData(); } };

  const toggleNetWorth = async (id: number) => { await api.patch(`/bank-savings/${id}/net-worth`); loadData(); };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  // Consolidated totals come from the backend summary (FX-converted to the user's base
  // currency). Row values below still show each account's original currency.
  const baseCurrency = summary?.baseCurrency || 'SGD';
  const totalBalance = summary?.totalBalance ?? 0;
  const inNetWorth = summary?.inNetWorth ?? 0;
  const sgAccounts = accounts.filter(a => a.country === 'Singapore');
  const slAccounts = accounts.filter(a => a.country === 'Sri Lanka');

  // Distribution BY BANK, always for a SINGLE currency (LKR and SGD can't be compared
  // directly). A currency toggle at the top of the chart picks which currency to show;
  // within it, balances are summed per bank. Only positive balances are charted.
  const availableCurrencies = Array.from(
    new Set(accounts.filter(a => (a.balance || 0) > 0).map(a => (a.currency || 'SGD').toUpperCase()))
  ).sort();

  // Effective selection: the chosen currency if still available, else the currency holding
  // the largest total balance (most useful default).
  const currencyTotals = accounts.reduce<Record<string, number>>((acc, a) => {
    const cur = (a.currency || 'SGD').toUpperCase();
    if ((a.balance || 0) > 0) acc[cur] = (acc[cur] || 0) + (a.balance || 0);
    return acc;
  }, {});
  const defaultCurrency = Object.entries(currencyTotals).sort((x, y) => y[1] - x[1])[0]?.[0]
    ?? availableCurrencies[0];
  const selectedCurrency = (chartCurrency && availableCurrencies.includes(chartCurrency))
    ? chartCurrency : defaultCurrency;

  const bankTotals = accounts
    .filter(a => (a.balance || 0) > 0 && (a.currency || 'SGD').toUpperCase() === selectedCurrency)
    .reduce<Record<string, number>>((acc, a) => {
      const bank = a.bankName?.trim() || a.accountName || 'Unknown';
      acc[bank] = (acc[bank] || 0) + (a.balance || 0);
      return acc;
    }, {});
  const bankPieData = Object.entries(bankTotals)
    .map(([bank, value], i) => ({ name: bank, value, color: PIE_COLORS[i % PIE_COLORS.length] }))
    .sort((a, b) => b.value - a.value);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Bank Savings</h1><p className="text-slate-500 text-sm mt-0.5">Track your bank account balances</p></div>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); resetForm(); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700"><Plus size={16} /> Add Account</button>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Total Accounts</p><p className="text-lg font-bold text-slate-800 mt-1">{accounts.length}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Total Balance <span className="text-slate-400 normal-case">({baseCurrency})</span></p><p className="text-lg font-bold text-indigo-600 mt-1">{formatCurrency(totalBalance, baseCurrency)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">In Net Worth <span className="text-slate-400 normal-case">({baseCurrency})</span></p><p className="text-lg font-bold text-green-600 mt-1">{formatCurrency(inNetWorth, baseCurrency)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">SG / SL</p><p className="text-lg font-bold text-slate-800 mt-1">{sgAccounts.length} / {slAccounts.length}</p></div>
      </div>

      {/* Distribution by bank (single currency) */}
      {availableCurrencies.length > 0 && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
          <div className="flex flex-wrap items-center justify-between gap-3 mb-1">
            <h3 className="font-semibold text-slate-800">Balance by Bank</h3>
            {/* Currency toggle — one currency at a time (no cross-currency mixing). */}
            <div className="flex rounded-lg overflow-hidden border border-slate-300">
              {availableCurrencies.map(cur => (
                <button key={cur} type="button" onClick={() => setChartCurrency(cur)}
                  className={`px-3 py-1 text-xs font-medium ${selectedCurrency === cur ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}>
                  {cur}
                </button>
              ))}
            </div>
          </div>
          <p className="text-xs text-slate-400 mb-4">Share of {selectedCurrency} savings held at each bank.</p>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 items-center">
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie data={bankPieData} cx="50%" cy="50%" innerRadius={50} outerRadius={85} dataKey="value"
                     label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`} labelLine={false}>
                  {bankPieData.map((e, i) => <Cell key={i} fill={e.color} />)}
                </Pie>
                <Tooltip formatter={(v) => formatCurrency(v as number, selectedCurrency)} />
              </PieChart>
            </ResponsiveContainer>
            <div className="space-y-2 max-h-60 overflow-y-auto">
              {bankPieData.map(b => (
                <div key={b.name} className="flex items-center justify-between text-sm gap-3">
                  <span className="flex items-center gap-2 min-w-0">
                    <span className="w-3 h-3 rounded-sm shrink-0" style={{ backgroundColor: b.color }} />
                    <span className="font-medium text-slate-700 truncate">{b.name}</span>
                  </span>
                  <span className="text-slate-800 font-semibold whitespace-nowrap">{formatNumber(b.value)} {selectedCurrency}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editing ? 'Edit Account' : 'Add Savings Account'}</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Account Name *</label>
              <input type="text" value={form.accountName} onChange={e => setForm({...form, accountName: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required placeholder="e.g. DBS Savings" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Bank</label>
              <input type="text" value={form.bankName} onChange={e => setForm({...form, bankName: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. DBS, OCBC, BOC" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Balance *</label>
              <input type="number" step="any" value={form.balance || ''} onChange={e => setForm({...form, balance: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="0.00" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Currency</label>
              <SearchableSelect options={['SGD','USD','LKR','INR'].map(c => ({ value: c, label: c }))} value={form.currency} onChange={v => setForm({...form, currency: v})} placeholder="Currency" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Country</label>
              <div className="flex rounded-lg overflow-hidden border border-slate-300">
                <button type="button" onClick={() => setForm({...form, country: 'Singapore'})} className={`flex-1 py-2 text-xs font-medium ${form.country === 'Singapore' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600'}`}>Singapore</button>
                <button type="button" onClick={() => setForm({...form, country: 'Sri Lanka'})} className={`flex-1 py-2 text-xs font-medium ${form.country === 'Sri Lanka' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600'}`}>Sri Lanka</button>
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Account Number</label>
              <input type="text" value={form.accountNumber} onChange={e => setForm({...form, accountNumber: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div className="flex items-end">
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={form.includeInNetWorth} onChange={e => setForm({...form, includeInNetWorth: e.target.checked})} className="rounded border-slate-300 text-indigo-600" />
                <span className="text-sm text-slate-700">Include in Net Worth</span>
              </label></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editing ? 'Update' : 'Save'}</button>
              <button type="button" onClick={() => { setShowForm(false); setEditing(null); }} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 border-b border-slate-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Account</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Bank</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Account No.</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Country</th>
              <th className="text-right px-4 py-3 font-medium text-slate-600">Amount</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Currency</th>
              <th className="text-center px-4 py-3 font-medium text-slate-600">Net Worth</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Last Updated</th>
              <th className="px-4 py-3 w-20"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {accounts.map(acc => (
              <tr key={acc.id} className="hover:bg-slate-50 group">
                <td className="px-4 py-3 font-medium text-slate-800">{acc.accountName}</td>
                <td className="px-4 py-3 text-slate-600">{acc.bankName || '-'}</td>
                <td className="px-4 py-3">
                  {acc.accountNumber ? (
                    <div className="flex items-center gap-1.5">
                      <span className="text-xs font-mono text-slate-600">{maskNumber(acc.accountNumber, acc.id)}</span>
                      <button onClick={() => toggleMask(acc.id)} className="text-slate-400 hover:text-slate-700">
                        {unmasked.has(acc.id) ? <EyeOff size={12} /> : <Eye size={12} />}
                      </button>
                    </div>
                  ) : <span className="text-xs text-slate-400">-</span>}
                </td>
                <td className="px-4 py-3"><span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${acc.country === 'Singapore' ? 'bg-blue-100 text-blue-700' : 'bg-emerald-100 text-emerald-700'}`}>{acc.country}</span></td>
                <td className="px-4 py-3 text-right font-medium text-slate-800 tabular-nums">{formatNumber(acc.balance)}</td>
                <td className="px-4 py-3 text-slate-500 text-xs font-medium">{acc.currency}</td>
                <td className="px-4 py-3 text-center">
                  <button onClick={() => toggleNetWorth(acc.id)} className={`w-5 h-5 rounded border flex items-center justify-center transition-colors ${acc.includeInNetWorth ? 'bg-green-600 border-green-600' : 'border-slate-300 hover:border-green-400'}`}>
                    {acc.includeInNetWorth && <Check size={12} className="text-white" />}
                  </button>
                </td>
                <td className="px-4 py-3 text-slate-500 text-xs">{acc.lastUpdated ? formatDate(acc.lastUpdated) : '-'}</td>
                <td className="px-4 py-3">
                  <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button onClick={() => startEdit(acc)} className="text-slate-400 hover:text-indigo-600"><Pencil size={13} /></button>
                    <button onClick={() => handleDelete(acc.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
                  </div>
                </td>
              </tr>
            ))}
            {accounts.length === 0 && <tr><td colSpan={9} className="px-4 py-12 text-center text-slate-400">No savings accounts tracked</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  );
}
