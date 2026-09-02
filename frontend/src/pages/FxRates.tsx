import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, Pencil, Trash2, RefreshCw, HelpCircle } from 'lucide-react';
import api, {
  getCurrencyRates, getAvailableCurrencies, createCurrencyRate, updateCurrencyRate,
  deleteCurrencyRate, refreshCurrencyRate, refreshAllCurrencyRates,
} from '../api';
import { formatDate } from '../utils/formatters';
import SearchableSelect from '../components/SearchableSelect';
import HelpTip from '../components/HelpTip';
import type { CurrencyRate } from '../types';
import { useToast } from '../contexts/ToastContext';

export default function FxRates() {
  const [rates, setRates] = useState<CurrencyRate[]>([]);
  const [currencies, setCurrencies] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const { showToast } = useToast();
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<CurrencyRate | null>(null);
  const [form, setForm] = useState({ fromCurrency: 'USD', toCurrency: 'SGD', rate: '', spreadPct: '' });
  const [newCurrency, setNewCurrency] = useState('');
  const [error, setError] = useState('');
  const [refreshingId, setRefreshingId] = useState<number | null>(null);
  const [refreshingAll, setRefreshingAll] = useState(false);

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

  const resetForm = () => setForm({ fromCurrency: 'USD', toCurrency: 'SGD', rate: '', spreadPct: '' });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const rateValue = parseFloat(form.rate);
    if (!rateValue || rateValue <= 0) { setError('Please enter a valid rate'); return; }
    if (form.fromCurrency === form.toCurrency) { setError('From and To currencies must be different'); return; }
    const spread = form.spreadPct.trim() === '' ? null : parseFloat(form.spreadPct);
    if (spread != null && (isNaN(spread) || spread < 0 || spread >= 100)) { setError('Spread % must be between 0 and 100'); return; }
    try {
      const payload = { fromCurrency: form.fromCurrency, toCurrency: form.toCurrency, rate: rateValue, spreadPct: spread };
      if (editing) {
        await updateCurrencyRate(editing.id, payload);
      } else {
        await createCurrencyRate(payload);
      }
      setShowForm(false); setEditing(null); resetForm();
      loadData();
      showToast(editing ? 'Rate updated' : 'Rate saved', 'success');
    } catch (err: any) {
      console.error(err);
      setError(err.response?.data?.message || 'Failed to save rate');
    }
  };

  const startEdit = (rate: CurrencyRate) => {
    setEditing(rate);
    setForm({
      fromCurrency: rate.fromCurrency,
      toCurrency: rate.toCurrency,
      rate: rate.rate.toString(),
      spreadPct: rate.spreadPct != null ? rate.spreadPct.toString() : '',
    });
    setShowForm(true);
    setError('');
  };

  const handleDelete = async (id: number) => { if (confirm('Delete this rate?')) { await deleteCurrencyRate(id); loadData(); } };

  // Fetch the latest mid-market rate online for one pair. Unquoted pairs (e.g. many LKR pairs)
  // are left unchanged and reported so the user can maintain them manually.
  const handleRefreshRate = async (id: number, pair: string) => {
    setRefreshingId(id);
    try {
      const res = await refreshCurrencyRate(id);
      if (res.data.updated) { showToast(`Updated ${pair} rate`, 'success'); loadData(); }
      else { showToast(res.data.message || `No online rate for ${pair} — update it manually`, 'info'); }
    } catch (err: any) {
      const status = err.response?.status;
      showToast(status === 503 ? 'Online rate lookup is disabled on this server.' : (err.response?.data?.error || 'Failed to fetch rate'), status === 503 ? 'info' : undefined);
    } finally {
      setRefreshingId(null);
    }
  };

  const handleRefreshAll = async () => {
    setRefreshingAll(true);
    try {
      const res = await refreshAllCurrencyRates();
      const { updated, skipped, total } = res.data;
      showToast(`Updated ${updated} of ${total} rates${skipped?.length ? ` (${skipped.length} not found: ${skipped.slice(0, 5).join(', ')}${skipped.length > 5 ? '…' : ''})` : ''}`, updated > 0 ? 'success' : 'info');
      loadData();
    } catch (err: any) {
      const status = err.response?.status;
      showToast(status === 503 ? 'Online rate lookup is disabled on this server.' : (err.response?.data?.error || 'Failed to refresh rates'), status === 503 ? 'info' : undefined);
    } finally {
      setRefreshingAll(false);
    }
  };

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

  // Effective rate after the broker spread, shown as a preview.
  const effectiveRate = (r: { rate: number; spreadPct: number | null }) =>
    r.spreadPct && r.spreadPct > 0 ? r.rate * (1 - r.spreadPct / 100) : r.rate;

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const formRateNum = parseFloat(form.rate);
  const formSpreadNum = form.spreadPct.trim() === '' ? 0 : parseFloat(form.spreadPct);
  const formEffective = !isNaN(formRateNum) && formRateNum > 0
    ? (formSpreadNum > 0 && formSpreadNum < 100 ? formRateNum * (1 - formSpreadNum / 100) : formRateNum)
    : null;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 flex items-center gap-1.5">
            FX Rates
            <HelpTip
              label="What are FX rates?"
              text="FX rates convert amounts held in other currencies into your base currency for Net Worth and totals. Enter one rate per pair; the broker spread % reduces it to a realistic value when converting into your base. Your original per-record amounts are never changed."
            />
          </h1>
          <p className="text-slate-500 text-sm mt-0.5">
            One rate per currency pair, with an optional broker spread.{' '}
            <Link to="/guide" className="text-indigo-600 hover:underline inline-flex items-center gap-0.5">
              <HelpCircle size={12} /> Learn more
            </Link>
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button onClick={handleRefreshAll} disabled={refreshingAll || rates.length === 0} className="flex items-center gap-2 px-4 py-2 bg-white text-slate-700 border border-slate-300 rounded-lg text-sm font-medium hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed" title="Fetch the latest online mid-market rates for all pairs">
            <RefreshCw size={16} className={refreshingAll ? 'animate-spin' : ''} /> {refreshingAll ? 'Updating...' : 'Update All Rates'}
          </button>
          <button onClick={() => { setShowForm(!showForm); setEditing(null); setError(''); resetForm(); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
            <Plus size={16} /> Add FX Rate
          </button>
        </div>
      </div>

      {/* Rate Cards (one per pair) */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {rates.map(rate => {
          const pair = `${rate.fromCurrency}/${rate.toCurrency}`;
          const hasSpread = rate.spreadPct != null && rate.spreadPct > 0;
          return (
            <div key={rate.id} className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold bg-indigo-100 text-indigo-700 px-2 py-0.5 rounded">{rate.fromCurrency}</span>
                  <span className="text-slate-400">→</span>
                  <span className="text-xs font-bold bg-emerald-100 text-emerald-700 px-2 py-0.5 rounded">{rate.toCurrency}</span>
                </div>
                <div className="flex gap-1">
                  <button onClick={() => handleRefreshRate(rate.id, pair)} disabled={refreshingId === rate.id} className="p-1 text-slate-400 hover:text-indigo-600 disabled:opacity-50" title="Fetch latest online rate">
                    <RefreshCw size={14} className={refreshingId === rate.id ? 'animate-spin' : ''} />
                  </button>
                  <button onClick={() => startEdit(rate)} className="p-1 text-slate-400 hover:text-indigo-600"><Pencil size={14} /></button>
                  <button onClick={() => handleDelete(rate.id)} className="p-1 text-slate-400 hover:text-red-500"><Trash2 size={14} /></button>
                </div>
              </div>
              <p className="text-2xl font-bold text-slate-800">{rate.rate.toFixed(4)}</p>
              <p className="text-xs text-slate-500 mt-1">1 {rate.fromCurrency} = {rate.rate} {rate.toCurrency} <span className="text-slate-400">(market)</span></p>
              {hasSpread && (
                <p className="text-xs text-amber-600 mt-1">
                  Broker spread {rate.spreadPct}% → effective {effectiveRate(rate).toFixed(4)}
                </p>
              )}
              <p className="text-[10px] text-slate-400 mt-1">Updated: {formatDate(rate.effectiveDate)}</p>
            </div>
          );
        })}
        {rates.length === 0 && (
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
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Market Rate *</label>
              <input type="number" step="any" value={form.rate} onChange={e => setForm({...form, rate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required placeholder="e.g. 1.3500" /></div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1 flex items-center gap-1">
                Broker Spread %
                <HelpTip label="Broker spread" text="Brokers pay less than the mid-market rate when you convert back to your base currency. Enter the approximate % they take (e.g. 1.5). Net Worth then uses rate × (1 − spread%) so it reflects what you'd actually receive. Leave blank for none." />
              </label>
              <input type="number" step="any" value={form.spreadPct} onChange={e => setForm({...form, spreadPct: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="optional, e.g. 1.5" /></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editing ? 'Update' : 'Save'}</button>
              <button type="button" onClick={() => { setShowForm(false); setEditing(null); setError(''); }} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
            {formEffective != null && (
              <div className="lg:col-span-5 text-xs text-slate-500">
                Effective conversion rate: <span className="font-medium text-slate-700">{formEffective.toFixed(4)}</span>
                {formSpreadNum > 0 && formSpreadNum < 100 && <span className="text-amber-600"> (after {formSpreadNum}% spread)</span>}
              </div>
            )}
          </form>
        </div>
      )}

      {/* Add New Currency */}
      <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <h3 className="text-sm font-semibold text-slate-800 mb-3 flex items-center gap-1.5">
          Add New Currency
          <HelpTip
            label="What is a currency here?"
            text="Currencies are the money types you hold (e.g. SGD, USD, LKR). Add one here, then add an exchange rate for it so the app can convert it into your base currency for totals."
          />
        </h3>
        <p className="text-xs text-slate-500 mb-3">Add a currency code that's not in the list. Once you add a rate for it, it becomes available across the app.</p>
        <div className="flex items-center gap-3 mb-4">
          <input type="text" value={newCurrency} onChange={e => setNewCurrency(e.target.value.toUpperCase())} placeholder="e.g. INR, BTC, KRW" maxLength={5} className="border border-slate-300 rounded-lg px-3 py-2 text-sm w-32 uppercase" onKeyDown={e => e.key === 'Enter' && handleAddCurrency()} />
          <button onClick={handleAddCurrency} className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">Add</button>
        </div>
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
          <li>One rate is kept per currency pair (no history) — updating replaces it.</li>
          <li>Net Worth and totals convert each amount into your base currency using rate × (1 − spread%).</li>
          <li>The broker spread % reflects that brokers pay below mid-market when you repatriate.</li>
          <li>Use "Update All Rates" to fetch the latest market rates online; unquoted pairs stay manual.</li>
        </ul>
      </div>
    </div>
  );
}
