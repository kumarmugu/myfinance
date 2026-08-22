import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { getDividends, getDividendSummary, createDividend, deleteDividend, getAccounts, getOwners } from '../api';
import { formatCurrency, formatDate } from '../utils/formatters';
import SearchableSelect from '../components/SearchableSelect';
import type { Dividend, Account, Owner, Currency } from '../types';

export default function Dividends() {
  const [dividends, setDividends] = useState<Dividend[]>([]);
  const [summary, setSummary] = useState<Array<{ year: number; total: number }>>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [owners, setOwners] = useState<Owner[]>([]);
  void owners; // used for form ownerId default
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [filterBroker, setFilterBroker] = useState<string>('');
  const [filterYear, setFilterYear] = useState<string>('');
  const [displayCurrency, setDisplayCurrency] = useState<Currency>('SGD');

  const [form, setForm] = useState({ accountId: 0, ownerId: 0, instrument: '', amount: 0, currency: 'SGD' as Currency, receivedDate: new Date().toISOString().split('T')[0], year: new Date().getFullYear(), quarter: 'Q1', notes: '' });

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [divRes, sumRes, accRes, ownRes] = await Promise.all([getDividends(), getDividendSummary(), getAccounts(), getOwners()]);
      setDividends(divRes.data);
      setSummary(sumRes.data.map(([year, total]: [number, number]) => ({ year, total })));
      setAccounts(accRes.data.filter(a => a.accountType === 'BROKER'));
      setOwners(ownRes.data);
      if (ownRes.data.length > 0 && form.ownerId === 0) setForm(f => ({ ...f, ownerId: ownRes.data[0].id }));
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await createDividend({ account: { id: form.accountId } as Account, owner: { id: form.ownerId } as Owner, instrument: form.instrument, amount: form.amount, currency: form.currency, receivedDate: form.receivedDate, year: form.year, quarter: form.quarter, notes: form.notes });
      setShowForm(false);
      setForm({ accountId: 0, ownerId: 0, instrument: '', amount: 0, currency: 'SGD', receivedDate: new Date().toISOString().split('T')[0], year: new Date().getFullYear(), quarter: 'Q1', notes: '' });
      loadData();
    } catch (err) { console.error(err); alert('Failed'); }
  };

  const handleDelete = async (id: number) => { if (confirm('Delete?')) { await deleteDividend(id); loadData(); } };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  // Filters
  const filtered = dividends.filter(d => {
    if (filterBroker && d.account.id.toString() !== filterBroker) return false;
    if (filterYear && d.year?.toString() !== filterYear) return false;
    return true;
  });

  const totalDividends = filtered.reduce((s, d) => s + d.amount, 0);

  // By broker
  const byBroker: Record<string, number> = {};
  filtered.forEach(d => { byBroker[d.account.name] = (byBroker[d.account.name] || 0) + d.amount; });

  // By instrument
  const byInstrument: Record<string, number> = {};
  filtered.forEach(d => { if (d.instrument) byInstrument[d.instrument] = (byInstrument[d.instrument] || 0) + d.amount; });
  const instrumentData = Object.entries(byInstrument).sort((a, b) => b[1] - a[1]);

  // Available years for filter
  const years = [...new Set(dividends.map(d => d.year).filter(Boolean))].sort((a, b) => b - a);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Dividends</h1><p className="text-slate-500 text-sm mt-0.5">Track and manage dividend income</p></div>
        <div className="flex items-center gap-3">
          <div className="flex bg-white border border-slate-200 rounded-lg overflow-hidden">
            <button onClick={() => setDisplayCurrency('SGD')} className={`px-3 py-1.5 text-xs font-medium transition-colors ${displayCurrency === 'SGD' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>SGD</button>
            <button onClick={() => setDisplayCurrency('USD')} className={`px-3 py-1.5 text-xs font-medium transition-colors ${displayCurrency === 'USD' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>USD</button>
          </div>
          <button onClick={() => setShowForm(!showForm)} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
            <Plus size={16} /> Record Dividend
          </button>
        </div>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Total Dividends</p>
          <p className="text-lg font-bold text-green-600 mt-1">{formatCurrency(totalDividends)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Records</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{filtered.length}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">By Broker</p>
          <div className="mt-1 space-y-0.5">
            {Object.entries(byBroker).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([name, amt]) => (
              <div key={name} className="flex justify-between text-xs"><span className="text-slate-600">{name}</span><span className="font-medium text-slate-800">{formatCurrency(amt)}</span></div>
            ))}
          </div>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Top Instruments</p>
          <div className="mt-1 space-y-0.5">
            {instrumentData.slice(0, 3).map(([inst, amt]) => (
              <div key={inst} className="flex justify-between text-xs"><span className="text-slate-600">{inst}</span><span className="font-medium text-slate-800">{formatCurrency(amt)}</span></div>
            ))}
          </div>
        </div>
      </div>

      {/* Yearly Chart */}
      {summary.length > 0 && (
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Dividend Income by Year</h3>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={summary}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="year" stroke="#94a3b8" fontSize={11} />
              <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `S$${v}`} />
              <Tooltip formatter={(v) => formatCurrency(v as number)} />
              <Bar dataKey="total" fill="#10b981" radius={[4, 4, 0, 0]} name="Dividend" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">Record Dividend</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Broker *</label>
              <SearchableSelect options={accounts.map(a => ({ value: a.id, label: a.name }))} value={form.accountId} onChange={v => setForm({...form, accountId: Number(v)})} placeholder="Select broker..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Instrument *</label>
              <input type="text" value={form.instrument} onChange={e => setForm({...form, instrument: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. VOO, D05" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Amount *</label>
              <input type="number" step="any" value={form.amount || ''} onChange={e => setForm({...form, amount: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Currency</label>
              <SearchableSelect options={['SGD','USD','EUR','LKR','INR'].map(c => ({ value: c, label: c }))} value={form.currency} onChange={v => setForm({...form, currency: v as Currency})} placeholder="Currency" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Date *</label>
              <input type="date" value={form.receivedDate} onChange={e => setForm({...form, receivedDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Year</label>
              <input type="number" value={form.year} onChange={e => setForm({...form, year: parseInt(e.target.value) || new Date().getFullYear()})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Quarter</label>
              <SearchableSelect options={['Q1','Q2','Q3','Q4'].map(q => ({ value: q, label: q }))} value={form.quarter} onChange={v => setForm({...form, quarter: v})} placeholder="Quarter" /></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-green-600 text-white rounded-lg text-sm font-medium hover:bg-green-700">Save</button>
              <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Filters */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className="w-44"><SearchableSelect options={[{ value: '', label: 'All Brokers' }, ...accounts.map(a => ({ value: a.id.toString(), label: a.name }))]} value={filterBroker} onChange={v => setFilterBroker(v.toString())} placeholder="All Brokers" /></div>
        <div className="w-36"><SearchableSelect options={[{ value: '', label: 'All Years' }, ...years.map(y => ({ value: y.toString(), label: y.toString() }))]} value={filterYear} onChange={v => setFilterYear(v.toString())} placeholder="All Years" /></div>
        </select>
        <span className="text-xs text-slate-500">{filtered.length} records | Total: {formatCurrency(totalDividends)}</span>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Date</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Instrument</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Broker</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Quarter</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">Amount</th>
                <th className="px-4 py-2.5 w-8"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtered.map(d => (
                <tr key={d.id} className="hover:bg-slate-50 group">
                  <td className="px-4 py-2 text-slate-700 text-xs">{formatDate(d.receivedDate)}</td>
                  <td className="px-4 py-2 font-medium text-slate-800">{d.instrument || d.asset?.symbol || '-'}</td>
                  <td className="px-4 py-2 text-slate-600">{d.account.name}</td>
                  <td className="px-4 py-2 text-slate-500 text-xs">{d.year}-{d.quarter}</td>
                  <td className="px-4 py-2 text-right font-medium text-green-600">{formatCurrency(d.amount, d.currency)}</td>
                  <td className="px-4 py-2"><button onClick={() => handleDelete(d.id)} className="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-red-500"><Trash2 size={14} /></button></td>
                </tr>
              ))}
              {filtered.length === 0 && <tr><td colSpan={6} className="px-4 py-12 text-center text-slate-400">No dividends recorded</td></tr>}
            </tbody>
          </table>
        </div>
      </div>

      {/* By Instrument Summary */}
      {instrumentData.length > 0 && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 border-b border-slate-200"><h3 className="font-semibold text-slate-800 text-sm">Summary by Instrument</h3></div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-2.5 font-medium text-slate-600">Instrument</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Total</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">% of Total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {instrumentData.map(([inst, amt]) => (
                  <tr key={inst} className="hover:bg-slate-50">
                    <td className="px-4 py-2 font-medium text-slate-800">{inst}</td>
                    <td className="px-4 py-2 text-right text-green-600 font-medium">{formatCurrency(amt)}</td>
                    <td className="px-4 py-2 text-right text-slate-500">{totalDividends > 0 ? ((amt / totalDividends) * 100).toFixed(1) : 0}%</td>
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
