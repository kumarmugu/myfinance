import { useEffect, useState } from 'react';
import { Plus, ArrowDownCircle, ArrowUpCircle, Trash2 } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend, PieChart, Pie, Cell } from 'recharts';
import { getAccountDeposits, createAccountDeposit, deleteAccountDeposit, getAccounts } from '../api';
import SearchableSelect from '../components/SearchableSelect';
import { formatCurrency, formatDate } from '../utils/formatters';
import type { AccountDeposit, Account, Currency } from '../types';
import { useToast } from '../contexts/ToastContext';

const CURRENCY_OPTIONS = ['SGD', 'USD', 'EUR', 'LKR', 'INR', 'GBP', 'AUD', 'JPY', 'CNY', 'MYR', 'THB', 'HKD', 'NZD', 'CHF', 'CAD'];

export default function Deposits() {
  const [deposits, setDeposits] = useState<AccountDeposit[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const { showToast } = useToast();
  const [showForm, setShowForm] = useState(false);
  const [filterOwner, setFilterOwner] = useState<string>('');
  const [filterAccount, setFilterAccount] = useState<string>('');
  const [displayCurrency, setDisplayCurrency] = useState<Currency>('SGD');

  const [form, setForm] = useState({ ownerId: 0, accountId: 0, amount: 0, depositType: 'DEPOSIT', currency: 'SGD' as Currency, depositDate: new Date().toISOString().split('T')[0], notes: '' });

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [depRes, accRes] = await Promise.all([getAccountDeposits(), getAccounts()]);
      setDeposits(depRes.data); setAccounts(accRes.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await createAccountDeposit({ account: { id: form.accountId } as Account, amount: form.amount, depositType: form.depositType, currency: form.currency, depositDate: form.depositDate, notes: form.notes });
      setShowForm(false);
      setForm({ ownerId: 0, accountId: 0, amount: 0, depositType: 'DEPOSIT', currency: 'SGD', depositDate: new Date().toISOString().split('T')[0], notes: '' });
      loadData();
    } catch (err) { console.error(err); showToast('Failed'); }
  };

  const handleDelete = async (id: number) => { if (confirm('Delete?')) { await deleteAccountDeposit(id); loadData(); } };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const ownerName = (d: AccountDeposit) => d.account?.owner?.name || '—';

  // Owners present in the loaded accounts (for the filter dropdown).
  const owners = [...new Map(accounts.filter(a => a.owner).map(a => [a.owner.id, a.owner])).values()];

  // Filters: by owner and/or by account.
  const filtered = deposits.filter(d =>
    (!filterOwner || (d.account?.owner?.id?.toString() === filterOwner)) &&
    (!filterAccount || d.account.id.toString() === filterAccount)
  );

  // Summary per account (respects the owner filter so it stays per-owner when one is selected).
  const summaryScope = filterOwner ? deposits.filter(d => d.account?.owner?.id?.toString() === filterOwner) : deposits;

  const accountSummary: Record<string, { deposits: number; withdrawals: number; net: number }> = {};
  summaryScope.forEach(d => {
    const name = d.account.name;
    if (!accountSummary[name]) accountSummary[name] = { deposits: 0, withdrawals: 0, net: 0 };
    if (d.depositType === 'DEPOSIT') { accountSummary[name].deposits += d.amount; accountSummary[name].net += d.amount; }
    else { accountSummary[name].withdrawals += d.amount; accountSummary[name].net -= d.amount; }
  });

  const chartData = Object.entries(accountSummary)
    .map(([name, data]) => ({ name, deposits: data.deposits, withdrawals: data.withdrawals, net: data.net }))
    .sort((a, b) => b.net - a.net);

  // Pie: distribution of net deposits per account. Pie slices can't represent
  // negatives, so only accounts with a positive net balance are shown.
  const PIE_COLORS = ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#14b8a6'];
  const pieData = chartData.filter(d => d.net > 0).map(d => ({ name: d.name, value: d.net }));
  const pieTotal = pieData.reduce((s, d) => s + d.value, 0);

  // Summary grouped by OWNER.
  const ownerSummary: Record<string, { deposits: number; withdrawals: number; net: number }> = {};
  summaryScope.forEach(d => {
    const name = ownerName(d);
    if (!ownerSummary[name]) ownerSummary[name] = { deposits: 0, withdrawals: 0, net: 0 };
    if (d.depositType === 'DEPOSIT') { ownerSummary[name].deposits += d.amount; ownerSummary[name].net += d.amount; }
    else { ownerSummary[name].withdrawals += d.amount; ownerSummary[name].net -= d.amount; }
  });
  const ownerRows = Object.entries(ownerSummary)
    .map(([name, data]) => ({ name, ...data }))
    .sort((a, b) => b.net - a.net);

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
            <button onClick={() => setDisplayCurrency('SGD')} className={`px-3 py-1.5 text-xs font-medium transition-colors ${displayCurrency === 'SGD' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>SGD</button>
            <button onClick={() => setDisplayCurrency('USD')} className={`px-3 py-1.5 text-xs font-medium transition-colors ${displayCurrency === 'USD' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>USD</button>
          </div>
          <button onClick={() => setShowForm(!showForm)} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
            <Plus size={16} /> Add Record
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Total Deposited</p>
          <p className="text-lg font-bold text-green-600 mt-1">{formatCurrency(totalDeposits)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Total Withdrawn</p>
          <p className="text-lg font-bold text-red-600 mt-1">{formatCurrency(totalWithdrawals)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Net Deposited</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{formatCurrency(totalDeposits - totalWithdrawals)}</p>
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
            <h3 className="text-sm font-semibold text-slate-800 mb-3">Deposits & Withdrawals by Account</h3>
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="name" stroke="#94a3b8" fontSize={11} />
                <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `S$${(v/1000).toFixed(0)}K`} />
                <Tooltip formatter={(v) => formatCurrency(v as number)} />
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
                  <Tooltip formatter={(v) => formatCurrency(v as number)} />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-[280px] flex items-center justify-center text-sm text-slate-400">No positive net balances to chart</div>
            )}
          </div>
        </div>
      )}

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">Record Deposit/Withdrawal</h3>
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
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">Save</button>
              <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
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
                <th className="px-4 py-2.5 w-8"></th>
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
                  <td className="px-4 py-2.5"><button onClick={() => handleDelete(d.id)} className="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-red-500"><Trash2 size={14} /></button></td>
                </tr>
              ))}
              {filtered.length === 0 && <tr><td colSpan={8} className="px-4 py-12 text-center text-slate-400">No records</td></tr>}
            </tbody>
          </table>
        </div>
      </div>

      {/* Per-Account Summary */}
      {ownerRows.length > 0 && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 border-b border-slate-200"><h3 className="font-semibold text-slate-800 text-sm">Summary by Owner</h3></div>
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
                    <td className="px-4 py-2.5 text-right text-green-600">{formatCurrency(row.deposits)}</td>
                    <td className="px-4 py-2.5 text-right text-red-600">{formatCurrency(row.withdrawals)}</td>
                    <td className="px-4 py-2.5 text-right font-medium text-slate-800">{formatCurrency(row.net)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {Object.keys(accountSummary).length > 0 && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 border-b border-slate-200"><h3 className="font-semibold text-slate-800 text-sm">Net Balances by Account</h3></div>
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
                    <td className="px-4 py-2.5 text-right text-green-600">{formatCurrency(row.deposits)}</td>
                    <td className="px-4 py-2.5 text-right text-red-600">{formatCurrency(row.withdrawals)}</td>
                    <td className="px-4 py-2.5 text-right font-medium text-slate-800">{formatCurrency(row.net)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
