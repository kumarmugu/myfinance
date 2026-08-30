import { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2 } from 'lucide-react';
import api from '../api';
import { formatCurrency } from '../utils/formatters';
import { useToast } from '../contexts/ToastContext';

interface PreciousMetal {
  id: number;
  metalType: string;
  form: string;
  description: string;
  weight: number;
  weightUnit: string;
  purity: string;
  purchasePrice: number;
  currentPrice: number;
  currency: string;
  purchaseDate: string;
  purchasedFrom: string;
  storageLocation: string;
  includeInNetWorth: boolean;
  status: string;
  soldPrice: number;
  soldDate: string;
  notes: string;
}

const METAL_TYPES = ['GOLD', 'SILVER', 'PLATINUM'];
const FORMS = ['COIN', 'BAR', 'JEWELLERY', 'DIGITAL', 'ETF'];
const METAL_COLORS: Record<string, string> = { GOLD: 'bg-amber-100 text-amber-700', SILVER: 'bg-slate-200 text-slate-700', PLATINUM: 'bg-slate-100 text-slate-600' };

export default function PreciousMetals() {
  const [items, setItems] = useState<PreciousMetal[]>([]);
  const [summary, setSummary] = useState<{ totalPurchaseValue: number; totalCurrentValue: number; baseCurrency?: string } | null>(null);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<PreciousMetal | null>(null);
  const [filterType, setFilterType] = useState('');
  const { showToast } = useToast();
  const [form, setForm] = useState({
    metalType: 'GOLD', form: 'COIN', description: '', weight: 0, weightUnit: 'g',
    purity: '999', purchasePrice: 0, currentPrice: 0, currency: 'SGD',
    purchaseDate: '', purchasedFrom: '', storageLocation: '',
    includeInNetWorth: true, status: 'HELD', soldPrice: 0, soldDate: '', notes: ''
  });

  useEffect(() => { loadData(); }, [filterType]);

  const loadData = async () => {
    try {
      const params = filterType ? `?metalType=${filterType}` : '';
      const [listRes, sumRes] = await Promise.all([
        api.get(`/precious-metals${params}`),
        api.get('/precious-metals/summary'),
      ]);
      setItems(listRes.data);
      setSummary(sumRes.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const resetForm = () => setForm({ metalType: 'GOLD', form: 'COIN', description: '', weight: 0, weightUnit: 'g', purity: '999', purchasePrice: 0, currentPrice: 0, currency: 'SGD', purchaseDate: '', purchasedFrom: '', storageLocation: '', includeInNetWorth: true, status: 'HELD', soldPrice: 0, soldDate: '', notes: '' });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editing) { await api.put(`/precious-metals/${editing.id}`, form); }
      else { await api.post('/precious-metals', form); }
      setShowForm(false); setEditing(null); resetForm(); loadData();
      showToast(editing ? 'Updated' : 'Added', 'success');
    } catch (err: any) { showToast(err.response?.data?.message || 'Failed'); }
  };

  const startEdit = (item: PreciousMetal) => {
    setEditing(item);
    setForm({ metalType: item.metalType, form: item.form || 'COIN', description: item.description || '', weight: item.weight, weightUnit: item.weightUnit || 'g', purity: item.purity || '', purchasePrice: item.purchasePrice || 0, currentPrice: item.currentPrice || 0, currency: item.currency || 'SGD', purchaseDate: item.purchaseDate || '', purchasedFrom: item.purchasedFrom || '', storageLocation: item.storageLocation || '', includeInNetWorth: item.includeInNetWorth, status: item.status || 'HELD', soldPrice: item.soldPrice || 0, soldDate: item.soldDate || '', notes: item.notes || '' });
    setShowForm(true);
  };

  const handleDelete = async (id: number) => { if (confirm('Delete?')) { await api.delete(`/precious-metals/${id}`); loadData(); } };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const held = items.filter(i => i.status === 'HELD');
  // Money totals from the backend summary (FX-converted to base currency); rows keep original currency.
  const baseCurrency = summary?.baseCurrency || 'SGD';
  const totalPurchase = summary?.totalPurchaseValue ?? 0;
  const totalCurrent = summary?.totalCurrentValue ?? 0;
  const totalGoldG = held.filter(i => i.metalType === 'GOLD').reduce((s, i) => s + i.weight, 0);
  const totalSilverG = held.filter(i => i.metalType === 'SILVER').reduce((s, i) => s + i.weight, 0);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Gold & Silver</h1><p className="text-slate-500 text-sm mt-0.5">Track precious metal holdings</p></div>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); resetForm(); }} className="flex items-center gap-2 px-4 py-2 bg-amber-600 text-white rounded-lg text-sm font-medium hover:bg-amber-700"><Plus size={16} /> Add Metal</button>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Items</p><p className="text-lg font-bold text-slate-800 mt-1">{held.length}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Invested <span className="text-slate-400 normal-case">({baseCurrency})</span></p><p className="text-lg font-bold text-slate-800 mt-1">{formatCurrency(totalPurchase, baseCurrency)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Current Value <span className="text-slate-400 normal-case">({baseCurrency})</span></p><p className="text-lg font-bold text-amber-600 mt-1">{formatCurrency(totalCurrent, baseCurrency)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Gold</p><p className="text-lg font-bold text-amber-600 mt-1">{totalGoldG.toFixed(1)}g</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Silver</p><p className="text-lg font-bold text-slate-600 mt-1">{totalSilverG.toFixed(1)}g</p></div>
      </div>

      {/* Filter */}
      <div className="flex gap-1 bg-slate-100 rounded-lg p-1 w-fit">
        <button onClick={() => setFilterType('')} className={`px-3 py-1.5 rounded-md text-xs font-medium ${!filterType ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600'}`}>All</button>
        {METAL_TYPES.map(t => (
          <button key={t} onClick={() => setFilterType(t)} className={`px-3 py-1.5 rounded-md text-xs font-medium ${filterType === t ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600'}`}>{t}</button>
        ))}
      </div>

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editing ? 'Edit Item' : 'Add Precious Metal'}</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Metal *</label>
              <div className="flex gap-1">
                {METAL_TYPES.map(t => (<button key={t} type="button" onClick={() => setForm({...form, metalType: t})} className={`flex-1 py-2 text-xs font-medium rounded-lg border ${form.metalType === t ? 'bg-amber-600 text-white border-amber-600' : 'bg-white text-slate-600 border-slate-300'}`}>{t}</button>))}
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Form</label>
              <select value={form.form} onChange={e => setForm({...form, form: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm">
                {FORMS.map(f => <option key={f} value={f}>{f}</option>)}
              </select></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Description</label>
              <input type="text" value={form.description} onChange={e => setForm({...form, description: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. 1oz Gold Coin" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Weight *</label>
              <div className="flex gap-2">
                <input type="number" step="any" value={form.weight || ''} onChange={e => setForm({...form, weight: parseFloat(e.target.value) || 0})} className="flex-1 border border-slate-300 rounded-lg px-3 py-2 text-sm" required />
                <select value={form.weightUnit} onChange={e => setForm({...form, weightUnit: e.target.value})} className="w-16 border border-slate-300 rounded-lg px-2 py-2 text-sm">
                  <option>g</option><option>oz</option><option>tola</option>
                </select>
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Purity</label>
              <input type="text" value={form.purity} onChange={e => setForm({...form, purity: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="999, 916, 750" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Purchase Price</label>
              <input type="number" step="any" value={form.purchasePrice || ''} onChange={e => setForm({...form, purchasePrice: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Current Value</label>
              <input type="number" step="any" value={form.currentPrice || ''} onChange={e => setForm({...form, currentPrice: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Currency</label>
              <select value={form.currency} onChange={e => setForm({...form, currency: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm">
                <option>SGD</option><option>USD</option><option>LKR</option><option>INR</option>
              </select></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Purchase Date</label>
              <input type="date" value={form.purchaseDate} onChange={e => setForm({...form, purchaseDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Purchased From</label>
              <input type="text" value={form.purchasedFrom} onChange={e => setForm({...form, purchasedFrom: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Shop/dealer" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Storage</label>
              <input type="text" value={form.storageLocation} onChange={e => setForm({...form, storageLocation: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Safe, bank vault" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Notes</label>
              <input type="text" value={form.notes} onChange={e => setForm({...form, notes: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-amber-600 text-white rounded-lg text-sm font-medium hover:bg-amber-700">{editing ? 'Update' : 'Save'}</button>
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
              <th className="text-left px-4 py-3 font-medium text-slate-600">Item</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Metal</th>
              <th className="text-right px-4 py-3 font-medium text-slate-600">Weight</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Purity</th>
              <th className="text-right px-4 py-3 font-medium text-slate-600">Cost</th>
              <th className="text-right px-4 py-3 font-medium text-slate-600">Value</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Status</th>
              <th className="px-4 py-3 w-16"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {items.map(item => (
              <tr key={item.id} className="hover:bg-slate-50 group">
                <td className="px-4 py-3">
                  <p className="font-medium text-slate-800">{item.description || `${item.metalType} ${item.form}`}</p>
                  <p className="text-[10px] text-slate-400">{item.form}{item.purchasedFrom ? ` • ${item.purchasedFrom}` : ''}</p>
                </td>
                <td className="px-4 py-3"><span className={`text-[10px] px-2 py-0.5 rounded-full font-semibold ${METAL_COLORS[item.metalType] || 'bg-slate-100 text-slate-600'}`}>{item.metalType}</span></td>
                <td className="px-4 py-3 text-right font-mono text-slate-700">{item.weight}{item.weightUnit || 'g'}</td>
                <td className="px-4 py-3 text-slate-600">{item.purity || '-'}</td>
                <td className="px-4 py-3 text-right text-slate-700">{item.purchasePrice ? formatCurrency(item.purchasePrice, item.currency) : '-'}</td>
                <td className="px-4 py-3 text-right font-medium text-amber-700">{item.currentPrice ? formatCurrency(item.currentPrice, item.currency) : '-'}</td>
                <td className="px-4 py-3"><span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${item.status === 'HELD' ? 'bg-green-100 text-green-700' : item.status === 'SOLD' ? 'bg-slate-100 text-slate-500' : 'bg-purple-100 text-purple-700'}`}>{item.status}</span></td>
                <td className="px-4 py-3">
                  <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button onClick={() => startEdit(item)} className="text-slate-400 hover:text-indigo-600"><Pencil size={13} /></button>
                    <button onClick={() => handleDelete(item.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
                  </div>
                </td>
              </tr>
            ))}
            {items.length === 0 && <tr><td colSpan={8} className="px-4 py-12 text-center text-slate-400">No precious metals. Click "Add Metal" to start tracking.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  );
}
