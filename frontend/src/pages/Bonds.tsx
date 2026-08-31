import { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, ScrollText } from 'lucide-react';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid, Legend } from 'recharts';
import { getBonds, getBondSummary, createBond, updateBond, deleteBond, getOwners, getDashboardSummary, getCurrencyRates } from '../api';
import SearchableSelect from '../components/SearchableSelect';
import { formatCurrency, formatDate } from '../utils/formatters';
import { useToast } from '../contexts/ToastContext';
import type { Owner, CurrencyRate } from '../types';

interface Bond {
  id: number;
  name: string;
  issuer: string;
  bondType: string;
  isin: string;
  currency: string;
  faceValue: number;
  purchasePrice: number;
  currentValue: number;
  couponRate: number;
  couponFrequency: string;
  purchaseDate: string;
  maturityDate: string;
  status: string;
  includeInNetWorth: boolean;
  notes: string;
  owner?: Owner | null;
}

const BOND_TYPES = ['GOVERNMENT', 'CORPORATE', 'MUNICIPAL', 'TREASURY', 'SAVINGS', 'OTHER'];
const COUPON_FREQUENCIES = ['ANNUAL', 'SEMI_ANNUAL', 'QUARTERLY', 'MONTHLY', 'ZERO_COUPON'];
const CURRENCY_OPTIONS = ['SGD', 'USD', 'EUR', 'LKR', 'INR', 'GBP', 'AUD', 'JPY', 'CNY', 'MYR', 'THB', 'HKD', 'NZD', 'CHF', 'CAD'];
const STATUS_COLORS: Record<string, string> = { HELD: 'bg-green-100 text-green-700', MATURED: 'bg-slate-100 text-slate-500', SOLD: 'bg-purple-100 text-purple-700' };
const TYPE_COLORS = ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4'];

const emptyForm = {
  name: '', issuer: '', bondType: 'GOVERNMENT', isin: '', currency: 'SGD',
  faceValue: 0, purchasePrice: 0, currentValue: 0, couponRate: 0, couponFrequency: 'SEMI_ANNUAL',
  purchaseDate: '', maturityDate: '', status: 'HELD', includeInNetWorth: true, notes: '', ownerId: 0,
};

export default function Bonds() {
  const [bonds, setBonds] = useState<Bond[]>([]);
  const [owners, setOwners] = useState<Owner[]>([]);
  const [filterOwner, setFilterOwner] = useState<string>('');
  const [summary, setSummary] = useState<{ totalInvested: number; totalCurrentValue: number; totalFaceValue: number; gainLoss: number; baseCurrency?: string } | null>(null);
  const [rates, setRates] = useState<CurrencyRate[]>([]);
  const [baseCurrency, setBaseCurrency] = useState<string>('SGD');
  const [displayRates, setDisplayRates] = useState<Record<string, number>>({});
  const [displayCurrency, setDisplayCurrency] = useState<string>('SGD');
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Bond | null>(null);
  const { showToast } = useToast();
  const [form, setForm] = useState({ ...emptyForm });

  useEffect(() => { getOwners().then(r => setOwners(r.data)).catch(console.error); }, []);
  useEffect(() => { loadData(); }, [filterOwner]);

  const loadData = async () => {
    try {
      const ownerId = filterOwner ? Number(filterOwner) : undefined;
      const [listRes, sumRes, dashRes, rateRes] = await Promise.all([
        getBonds(ownerId), getBondSummary(), getDashboardSummary(), getCurrencyRates(),
      ]);
      setBonds(listRes.data);
      setSummary(sumRes.data);
      setRates(rateRes.data);
      setBaseCurrency((dashRes.data.baseCurrency as string) || 'SGD');
      setDisplayRates(dashRes.data.displayRates || {});
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const resetForm = () => { setForm({ ...emptyForm }); setEditing(null); };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      // Send nested `owner`, not the flat `ownerId` (backend rejects unknown fields).
      const { ownerId, ...rest } = form;
      const payload = { ...rest, owner: ownerId ? { id: ownerId } : null };
      if (editing) { await updateBond(editing.id, payload); }
      else { await createBond(payload); }
      setShowForm(false); resetForm(); loadData();
      showToast(editing ? 'Bond updated' : 'Bond added', 'success');
    } catch (err: any) { showToast(err.response?.data?.message || 'Failed to save bond', 'error'); }
  };

  const startEdit = (b: Bond) => {
    setEditing(b);
    setForm({
      name: b.name, issuer: b.issuer || '', bondType: b.bondType || 'GOVERNMENT', isin: b.isin || '',
      currency: b.currency || 'SGD', faceValue: b.faceValue || 0, purchasePrice: b.purchasePrice || 0,
      currentValue: b.currentValue || 0, couponRate: b.couponRate || 0, couponFrequency: b.couponFrequency || 'SEMI_ANNUAL',
      purchaseDate: b.purchaseDate || '', maturityDate: b.maturityDate || '', status: b.status || 'HELD',
      includeInNetWorth: b.includeInNetWorth, notes: b.notes || '', ownerId: b.owner?.id || 0,
    });
    setShowForm(true);
  };

  const handleDelete = async (id: number) => { if (confirm('Delete this bond?')) { await deleteBond(id); loadData(); } };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  // ── Currency conversion (no hardcoded FX; mirrors CurrencyConversionService) ──
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
    return r != null ? amount * r : amount;
  };
  const displayOptions = Object.keys(displayRates).length > 0 ? Object.keys(displayRates) : [baseCurrency];
  const effectiveCurrency = displayRates[displayCurrency] != null ? displayCurrency : baseCurrency;
  const cFactor = displayRates[effectiveCurrency] ?? 1;
  const fmt = (baseValue: number) => formatCurrency(baseValue * cFactor, effectiveCurrency);

  const held = bonds.filter(b => b.status === 'HELD');
  // Totals from the backend summary (already FX-converted to base). Rows keep original currency.
  const totalInvested = summary?.totalInvested ?? 0;
  const totalCurrent = summary?.totalCurrentValue ?? 0;
  const totalFace = summary?.totalFaceValue ?? 0;
  const gainLoss = summary?.totalCurrentValue != null ? summary.totalCurrentValue - (summary.totalInvested ?? 0) : 0;

  // Charts (base currency; displayed via fmt)
  const pieData = held
    .map(b => ({ name: b.name, value: toBase(b.currentValue || 0, b.currency) }))
    .filter(d => d.value > 0)
    .sort((a, b) => b.value - a.value);
  const pieTotal = pieData.reduce((s, d) => s + d.value, 0);

  const byType: Record<string, number> = {};
  held.forEach(b => { const t = b.bondType || 'OTHER'; byType[t] = (byType[t] || 0) + toBase(b.currentValue || 0, b.currency); });
  const typeData = Object.entries(byType).map(([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Bonds</h1><p className="text-slate-500 text-sm mt-0.5">Track your bond holdings</p></div>
        <div className="flex items-center gap-3">
          <div className="w-44">
            <SearchableSelect
              options={[{ value: '', label: 'All Owners' }, ...owners.map(o => ({ value: o.id.toString(), label: o.name, icon: o.name[0] }))]}
              value={filterOwner}
              onChange={v => setFilterOwner(v.toString())}
              placeholder="All Owners"
            />
          </div>
          <div className="flex bg-white border border-slate-200 rounded-lg overflow-hidden">
            {displayOptions.map(code => (
              <button key={code} onClick={() => setDisplayCurrency(code)} title={`Show values in ${code}`}
                className={`px-3 py-1.5 text-xs font-medium transition-colors ${effectiveCurrency === code ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>{code}</button>
            ))}
          </div>
          <button onClick={() => { setShowForm(!showForm); resetForm(); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700"><Plus size={16} /> Add Bond</button>
        </div>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Bonds</p><p className="text-lg font-bold text-slate-800 mt-1">{held.length}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Face Value <span className="text-slate-400 normal-case">({effectiveCurrency})</span></p><p className="text-lg font-bold text-slate-800 mt-1">{fmt(totalFace)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Invested <span className="text-slate-400 normal-case">({effectiveCurrency})</span></p><p className="text-lg font-bold text-slate-800 mt-1">{fmt(totalInvested)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Current <span className="text-slate-400 normal-case">({effectiveCurrency})</span></p><p className="text-lg font-bold text-indigo-600 mt-1">{fmt(totalCurrent)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Gain / Loss</p><p className={`text-lg font-bold mt-1 ${gainLoss >= 0 ? 'text-green-600' : 'text-red-600'}`}>{fmt(gainLoss)}</p></div>
      </div>

      {/* Charts */}
      {pieData.length > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
            <h3 className="text-sm font-semibold text-slate-800 mb-3">Value Distribution</h3>
            <ResponsiveContainer width="100%" height={280}>
              <PieChart>
                <Pie data={pieData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={100}
                  label={({ name, value }) => `${name} ${pieTotal ? ((value / pieTotal) * 100).toFixed(0) : 0}%`} labelLine={false} fontSize={11}>
                  {pieData.map((_, i) => <Cell key={i} fill={TYPE_COLORS[i % TYPE_COLORS.length]} />)}
                </Pie>
                <Tooltip formatter={(v) => fmt(v as number)} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
            <h3 className="text-sm font-semibold text-slate-800 mb-3">By Type ({effectiveCurrency})</h3>
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={typeData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="name" stroke="#94a3b8" fontSize={11} />
                <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `${((v * cFactor) / 1000).toFixed(0)}K`} />
                <Tooltip formatter={(v) => fmt(v as number)} />
                <Legend />
                <Bar dataKey="value" name="Current Value" fill="#6366f1" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editing ? 'Edit Bond' : 'Add Bond'}</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Owner</label>
              <SearchableSelect options={[{ value: 0, label: 'Unassigned' }, ...owners.map(o => ({ value: o.id, label: o.name }))]} value={form.ownerId} onChange={v => setForm({ ...form, ownerId: Number(v) })} placeholder="Select owner..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Name *</label>
              <input type="text" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required placeholder="e.g. Singapore Savings Bond" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Issuer</label>
              <input type="text" value={form.issuer} onChange={e => setForm({ ...form, issuer: e.target.value })} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="MAS, Temasek, ..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Type</label>
              <SearchableSelect options={BOND_TYPES.map(t => ({ value: t, label: t }))} value={form.bondType} onChange={v => setForm({ ...form, bondType: String(v) })} placeholder="Type" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Currency</label>
              <SearchableSelect options={CURRENCY_OPTIONS.map(c => ({ value: c, label: c }))} value={form.currency} onChange={v => setForm({ ...form, currency: String(v) })} placeholder="Currency" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Face Value</label>
              <input type="number" step="any" value={form.faceValue || ''} onChange={e => setForm({ ...form, faceValue: parseFloat(e.target.value) || 0 })} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Purchase Price</label>
              <input type="number" step="any" value={form.purchasePrice || ''} onChange={e => setForm({ ...form, purchasePrice: parseFloat(e.target.value) || 0 })} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Current Value</label>
              <input type="number" step="any" value={form.currentValue || ''} onChange={e => setForm({ ...form, currentValue: parseFloat(e.target.value) || 0 })} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Coupon Rate (%)</label>
              <input type="number" step="any" value={form.couponRate || ''} onChange={e => setForm({ ...form, couponRate: parseFloat(e.target.value) || 0 })} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. 3.5" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Coupon Frequency</label>
              <SearchableSelect options={COUPON_FREQUENCIES.map(f => ({ value: f, label: f.replace('_', ' ') }))} value={form.couponFrequency} onChange={v => setForm({ ...form, couponFrequency: String(v) })} placeholder="Frequency" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Purchase Date</label>
              <input type="date" value={form.purchaseDate} onChange={e => setForm({ ...form, purchaseDate: e.target.value })} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Maturity Date</label>
              <input type="date" value={form.maturityDate} onChange={e => setForm({ ...form, maturityDate: e.target.value })} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">ISIN</label>
              <input type="text" value={form.isin} onChange={e => setForm({ ...form, isin: e.target.value })} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Optional" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Status</label>
              <div className="flex rounded-lg overflow-hidden border border-slate-300">
                {['HELD', 'MATURED', 'SOLD'].map(s => (
                  <button key={s} type="button" onClick={() => setForm({ ...form, status: s })} className={`flex-1 py-2 text-[11px] font-medium ${form.status === s ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600'}`}>{s}</button>
                ))}
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Notes</label>
              <input type="text" value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div className="flex items-end gap-2">
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={form.includeInNetWorth} onChange={e => setForm({ ...form, includeInNetWorth: e.target.checked })} className="rounded border-slate-300 text-indigo-600" />
                <span className="text-xs text-slate-700">Net Worth</span>
              </label>
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editing ? 'Update' : 'Save'}</button>
              <button type="button" onClick={() => { setShowForm(false); resetForm(); }} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Bond</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Owner</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Type</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Ccy</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Coupon</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Face</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Current</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Maturity</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Status</th>
                <th className="px-4 py-3 w-16"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {bonds.map(b => (
                <tr key={b.id} className="hover:bg-slate-50 group">
                  <td className="px-4 py-3">
                    <p className="font-medium text-slate-800">{b.name}</p>
                    {b.issuer && <p className="text-[10px] text-slate-400">{b.issuer}</p>}
                  </td>
                  <td className="px-4 py-3 text-xs text-slate-600">{b.owner?.name || '-'}</td>
                  <td className="px-4 py-3"><span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-medium">{b.bondType}</span></td>
                  <td className="px-4 py-3 text-xs font-medium text-indigo-600">{b.currency || baseCurrency}</td>
                  <td className="px-4 py-3 text-right text-slate-700">{b.couponRate ? `${b.couponRate}%` : '-'}</td>
                  <td className="px-4 py-3 text-right text-slate-700">{b.faceValue ? formatCurrency(b.faceValue, b.currency) : '-'}</td>
                  <td className="px-4 py-3 text-right font-medium text-slate-800">{b.currentValue ? formatCurrency(b.currentValue, b.currency) : '-'}</td>
                  <td className="px-4 py-3 text-xs text-slate-600">{b.maturityDate ? formatDate(b.maturityDate) : '-'}</td>
                  <td className="px-4 py-3"><span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[b.status] || 'bg-slate-100 text-slate-600'}`}>{b.status}</span></td>
                  <td className="px-4 py-3">
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => startEdit(b)} className="text-slate-400 hover:text-indigo-600"><Pencil size={13} /></button>
                      <button onClick={() => handleDelete(b.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {bonds.length === 0 && <tr><td colSpan={10} className="px-4 py-12 text-center text-slate-400"><ScrollText size={28} className="mx-auto mb-2 opacity-40" />No bonds yet. Click "Add Bond" to start tracking.</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
