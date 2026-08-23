import { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, RefreshCw } from 'lucide-react';
import api, { getCurrencyRates, getAvailableCurrencies, createCurrencyRate, updateCurrencyRate, deleteCurrencyRate } from '../api';
import { formatDate } from '../utils/formatters';
import SearchableSelect from '../components/SearchableSelect';
import type { CurrencyRate } from '../types';
import { useToast } from '../contexts/ToastContext';

export default function FxRates() {
  const [rates, setRates] = useState<CurrencyRate[]>([]);
  const [currencies, setCurrencies] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const { showToast } = useToast();
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<CurrencyRate | null>(null);
  const [form, setForm] = useState({ fromCurrency: 'USD', toCurrency: 'SGD', rate: '', effectiveDate: new Date().toISOString().split('T')[0] });
  const [newCurrency, setNewCurrency] = useState('');
  const [error, setError] = useState('');

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [ratesRes, currRes] = await Promise.all([getCurrencyRates(), getAvailableCurrencies()]);
      setRates(ratesRes.data);
      setCurrencies(currRes.data);
    }
    catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const rateValue = parseFloat(form.rate);
    if (!rateValue || rateValue <= 0) { setError('Please enter a valid rate'); return; }
    if (form.fromCurrency === form.toCurrency) { setError('From and To currencies must be different'); return; }
    try {
      const payload = { fromCurrency: form.fromCurrency, toCurrency: form.toCurrency, rate: rateValue, effectiveDate: form.effectiveDate };
      if (editing) {
        await updateCurrencyRate(editing.id, payload);
      } else {
        await createCurrencyRate(payload);
      }
      setShowForm(false); setEditing(null);
      setForm({ fromCurrency: 'USD', toCurrency: 'SGD', rate: '', effectiveDate: new Date().toISOString().split('T')[0] });
      loadData();
    } catch (err: any) {
      console.error(err);
      setError(err.response?.data?.message || 'Failed to save rate');
    }
  };

  const startEdit = (rate: CurrencyRate) => {
    setEditing(rate);
    setForm({ fromCurrency: rate.fromCurrency, toCurrency: rate.toCurrency, rate: rate.rate.toString(), effectiveDate: rate.effectiveDate });
    setShowForm(true);
    setError('');
  };

  const handleDelete = async (id: number) => { if (confirm('Delete this rate?')) { await deleteCurrencyRate(id); loadData(); } };

  const handleAddCurrency = async () => {
    const code = newCurrency.trim().toUpperCase();
    if (!code || code.length < 2) return;
    if (currencies.includes(code)) { setNewCurrency(''); return; }
    try {
      await api.post('/currency-rates/currencies', { code });
      setCurrencies([...currencies, code].sort());
      setNewCurrency('');
    } catch (err: any) {
      showToast(err.response?.data?.error || 'Failed to add currency');
    }
  };

  const handleDeleteCurrency = async (code: string) => {
    const inUse = rates.some(r => r.fromCurrency === code || r.toCurrency === code);
    if (inUse) { showToast(`Cannot delete "${code}" — it is used in existing FX rates. Delete those rates first.`); return; }
    if (!confirm(`Remove "${code}" from the currency list?`)) return;
    try {
      await api.delete(`/currency-rates/currencies/${code}`);
      setCurrencies(currencies.filter(c => c !== code));
    } catch (err: any) {
      showToast(err.response?.data?.error || 'Failed to remove currency');
    }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  // Group rates by pair for quick view
  const latestRates: Record<string, CurrencyRate> = {};
  rates.forEach(r => {
    const key = `${r.fromCurrency}/${r.toCurrency}`;
    if (!latestRates[key] || r.effectiveDate > latestRates[key].effectiveDate) {
      latestRates[key] = r;
    }
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">FX Rates</h1><p className="text-slate-500 text-sm mt-0.5">Manage exchange rates for currency conversion</p></div>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); setError(''); setForm({ fromCurrency: 'USD', toCurrency: 'SGD', rate: '', effectiveDate: new Date().toISOString().split('T')[0] }); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
          <Plus size={16} /> Add FX Rate
        </button>
      </div>

      {/* Current Rates Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {Object.entries(latestRates).map(([pair, rate]) => (
          <div key={pair} className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold bg-indigo-100 text-indigo-700 px-2 py-0.5 rounded">{rate.fromCurrency}</span>
                <span className="text-slate-400">→</span>
                <span className="text-xs font-bold bg-emerald-100 text-emerald-700 px-2 py-0.5 rounded">{rate.toCurrency}</span>
              </div>
              <div className="flex gap-1">
                <button onClick={() => startEdit(rate)} className="p-1 text-slate-400 hover:text-indigo-600"><Pencil size={14} /></button>
                <button onClick={() => handleDelete(rate.id)} className="p-1 text-slate-400 hover:text-red-500"><Trash2 size={14} /></button>
              </div>
            </div>
            <p className="text-2xl font-bold text-slate-800">{rate.rate.toFixed(4)}</p>
            <p className="text-xs text-slate-500 mt-1">1 {rate.fromCurrency} = {rate.rate} {rate.toCurrency}</p>
            <p className="text-[10px] text-slate-400 mt-1">Updated: {formatDate(rate.effectiveDate)}</p>
          </div>
        ))}
        {Object.keys(latestRates).length === 0 && (
          <div className="col-span-3 text-center text-slate-400 py-12">No rates configured. Click "Add FX Rate" to get started.</div>
        )}
      </div>

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editing ? 'Edit FX Rate' : 'Add FX Rate'}</h3>
          {error && <div className="mb-3 p-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">{error}</div>}
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">From Currency</label>
              <SearchableSelect options={currencies.map(c => ({ value: c, label: c }))} value={form.fromCurrency} onChange={v => setForm({...form, fromCurrency: v})} placeholder="From..." disabled={!!editing} /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">To Currency</label>
              <SearchableSelect options={currencies.map(c => ({ value: c, label: c }))} value={form.toCurrency} onChange={v => setForm({...form, toCurrency: v})} placeholder="To..." disabled={!!editing} /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Rate *</label>
              <input type="number" step="any" value={form.rate} onChange={e => setForm({...form, rate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required placeholder="e.g. 1.3500" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Effective Date *</label>
              <input type="date" value={form.effectiveDate} onChange={e => setForm({...form, effectiveDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editing ? 'Update' : 'Save'}</button>
              <button type="button" onClick={() => { setShowForm(false); setEditing(null); setError(''); }} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Full History Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-200 flex items-center justify-between">
          <h3 className="font-semibold text-slate-800 text-sm">Rate History ({rates.length})</h3>
          <RefreshCw size={14} className="text-slate-400 cursor-pointer hover:text-indigo-600" onClick={loadData} />
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">From</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">To</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">Rate</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Effective Date</th>
                <th className="px-4 py-2.5 w-20"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {[...rates].sort((a, b) => b.effectiveDate.localeCompare(a.effectiveDate)).map(r => (
                <tr key={r.id} className="hover:bg-slate-50 group">
                  <td className="px-4 py-2.5"><span className="text-xs font-bold bg-indigo-50 text-indigo-700 px-2 py-0.5 rounded">{r.fromCurrency}</span></td>
                  <td className="px-4 py-2.5"><span className="text-xs font-bold bg-emerald-50 text-emerald-700 px-2 py-0.5 rounded">{r.toCurrency}</span></td>
                  <td className="px-4 py-2.5 text-right font-mono font-medium text-slate-800">{r.rate.toFixed(4)}</td>
                  <td className="px-4 py-2.5 text-slate-600 text-xs">{formatDate(r.effectiveDate)}</td>
                  <td className="px-4 py-2.5">
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => startEdit(r)} className="text-slate-400 hover:text-indigo-600"><Pencil size={13} /></button>
                      <button onClick={() => handleDelete(r.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {rates.length === 0 && <tr><td colSpan={5} className="px-4 py-12 text-center text-slate-400">No rates</td></tr>}
            </tbody>
          </table>
        </div>
      </div>

      {/* Add New Currency */}
      <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <h3 className="text-sm font-semibold text-slate-800 mb-3">Add New Currency</h3>
        <p className="text-xs text-slate-500 mb-3">Add a currency code that's not in the list. Once you add a rate for it, it becomes available across the app.</p>
        <div className="flex items-center gap-3 mb-4">
          <input type="text" value={newCurrency} onChange={e => setNewCurrency(e.target.value.toUpperCase())} placeholder="e.g. INR, BTC, KRW" maxLength={5} className="border border-slate-300 rounded-lg px-3 py-2 text-sm w-32 uppercase" onKeyDown={e => e.key === 'Enter' && handleAddCurrency()} />
          <button onClick={handleAddCurrency} className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">Add</button>
        </div>
        {/* Currency Table */}
        <div className="border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-4 py-2 font-medium text-slate-600">#</th>
                <th className="text-left px-4 py-2 font-medium text-slate-600">Currency Code</th>
                <th className="px-4 py-2 w-16"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {currencies.map((c, i) => (
                <tr key={c} className="hover:bg-slate-50 group">
                  <td className="px-4 py-1.5 text-slate-400 text-xs">{i + 1}</td>
                  <td className="px-4 py-1.5"><span className="text-xs font-bold bg-slate-100 text-slate-700 px-2 py-0.5 rounded">{c}</span></td>
                  <td className="px-4 py-1.5">
                    <button onClick={() => handleDeleteCurrency(c)} className="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-red-500 transition-opacity"><Trash2 size={13} /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Info */}
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
        <p className="text-sm text-blue-800 font-medium">How FX rates are used</p>
        <ul className="text-xs text-blue-600 mt-1 space-y-0.5 list-disc list-inside">
          <li>Dashboard and Reports use the latest rate for currency conversion</li>
          <li>SGD/USD toggle converts all displayed amounts using stored rates</li>
          <li>LKR rate is used for Fixed Deposit net worth calculations</li>
          <li>Historical rates can be stored for accurate point-in-time reporting</li>
        </ul>
      </div>
    </div>
  );
}
