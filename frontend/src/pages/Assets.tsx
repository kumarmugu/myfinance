import { useEffect, useState } from 'react';
import { Plus, Trash2, Pencil, RefreshCw } from 'lucide-react';
import { getAssets, createAsset, updateAsset, deleteAsset, refreshAssetPrice, refreshAllAssetPrices } from '../api';
import SearchableSelect from '../components/SearchableSelect';
import type { Asset, AssetType, Currency } from '../types';
import { ASSET_TYPE_LABELS } from '../types';
import { formatCurrency, formatDate } from '../utils/formatters';
import { useToast } from '../contexts/ToastContext';

export default function Assets() {
  const [assets, setAssets] = useState<Asset[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Asset | null>(null);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ name: '', symbol: '', assetType: 'GROWTH_EQUITY' as AssetType, currency: 'USD' as Currency, exchange: '', description: '', currentPrice: 0 });
  const [refreshingId, setRefreshingId] = useState<number | null>(null);
  const [refreshingAll, setRefreshingAll] = useState(false);
  const { showToast } = useToast();

  useEffect(() => { loadData(); }, []);
  const loadData = async () => { try { setAssets((await getAssets()).data); } catch (err) { console.error(err); } finally { setLoading(false); } };

  const resetForm = () => setForm({ name: '', symbol: '', assetType: 'GROWTH_EQUITY', currency: 'USD', exchange: '', description: '', currentPrice: 0 });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editing) {
        await updateAsset(editing.id, form);
      } else {
        await createAsset(form);
      }
      setShowForm(false); setEditing(null); resetForm(); loadData();
      showToast(editing ? 'Asset updated' : 'Asset created', 'success');
    } catch (err: any) {
      console.error(err);
      showToast(err.response?.data?.message || 'Failed to save asset');
    }
  };

  const startEdit = (asset: Asset) => {
    setEditing(asset);
    setForm({
      name: asset.name,
      symbol: asset.symbol,
      assetType: asset.assetType,
      currency: asset.currency,
      exchange: asset.exchange || '',
      description: asset.description || '',
      currentPrice: asset.currentPrice || 0,
    });
    setShowForm(true);
  };

  const handleDelete = async (id: number) => {
    if (confirm('Delete?')) {
      try { await deleteAsset(id); loadData(); }
      catch (err: any) {
        const msg = err.response?.data?.message || 'Failed to delete';
        const refs = err.response?.data?.references;
        showToast(refs ? `${msg}\n\nReferenced by:\n• ${refs.join('\n• ')}` : msg);
      }
    }
  };

  // Fetch the latest online price for a single asset. Unfetchable symbols (e.g. Sri Lanka/CSE)
  // are left unchanged and reported so the user can update them manually.
  const handleRefreshPrice = async (id: number, symbol: string) => {
    setRefreshingId(id);
    try {
      const res = await refreshAssetPrice(id);
      if (res.data.updated) { showToast(`Updated ${symbol} price`, 'success'); loadData(); }
      else { showToast(res.data.message || `No online price for ${symbol} — update it manually`, 'info'); }
    } catch (err: any) {
      showToast(err.response?.data?.error || 'Failed to fetch price');
    } finally {
      setRefreshingId(null);
    }
  };

  // Refresh prices for all assets at once.
  const handleRefreshAll = async () => {
    setRefreshingAll(true);
    try {
      const res = await refreshAllAssetPrices();
      const { updated, skipped, total } = res.data;
      showToast(`Updated ${updated} of ${total} prices${skipped?.length ? ` (${skipped.length} not found: ${skipped.slice(0, 5).join(', ')}${skipped.length > 5 ? '…' : ''})` : ''}`, updated > 0 ? 'success' : 'info');
      loadData();
    } catch (err: any) {
      showToast(err.response?.data?.error || 'Failed to refresh prices');
    } finally {
      setRefreshingAll(false);
    }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Assets</h1><p className="text-slate-500 text-sm mt-1">Manage stocks, ETFs, funds, and crypto</p></div>
        <div className="flex items-center gap-3">
          <button onClick={handleRefreshAll} disabled={refreshingAll || assets.length === 0} className="flex items-center gap-2 px-4 py-2 bg-white text-slate-700 border border-slate-300 rounded-lg text-sm font-medium hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed" title="Fetch the latest online prices for all assets">
            <RefreshCw size={16} className={refreshingAll ? 'animate-spin' : ''} /> {refreshingAll ? 'Updating...' : 'Update All Prices'}
          </button>
          <button onClick={() => { setShowForm(!showForm); setEditing(null); resetForm(); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700"><Plus size={16} /> New Asset</button>
        </div>
      </div>

      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editing ? 'Edit Asset' : 'Add Asset'}</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Name *</label><input type="text" value={form.name} onChange={e => setForm({...form, name: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Symbol *</label><input type="text" value={form.symbol} onChange={e => setForm({...form, symbol: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div className="lg:col-span-2"><label className="block text-sm font-medium text-slate-700 mb-1">Type</label>
              <div className="flex flex-wrap gap-1.5">
                {Object.entries(ASSET_TYPE_LABELS).map(([k, v]) => (
                  <button key={k} type="button" onClick={() => setForm({...form, assetType: k as AssetType})}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors border ${form.assetType === k ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-300 hover:border-indigo-300'}`}>{v}</button>
                ))}
              </div></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Currency</label>
              <SearchableSelect options={['USD','SGD','EUR','LKR','INR','GBP','AUD','JPY','CNY','MYR','HKD','CAD'].map(c => ({ value: c, label: c }))} value={form.currency} onChange={v => setForm({...form, currency: v as Currency})} placeholder="Select currency..." /></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Exchange</label><input type="text" value={form.exchange} onChange={e => setForm({...form, exchange: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. NYSE, SGX" /></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Current Price</label><input type="number" step="any" value={form.currentPrice || ''} onChange={e => setForm({...form, currentPrice: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Latest market price" /></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Description</label><input type="text" value={form.description} onChange={e => setForm({...form, description: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Optional" /></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editing ? 'Update' : 'Save'}</button>
              <button type="button" onClick={() => { setShowForm(false); setEditing(null); }} className="px-4 py-2 bg-slate-200 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Symbol</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Name</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Type</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Exchange</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Currency</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Current Price</th>
                <th className="px-4 py-3 w-20"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {assets.map(a => (
                <tr key={a.id} className="hover:bg-slate-50 group">
                  <td className="px-4 py-3 font-medium text-slate-800">{a.symbol}</td>
                  <td className="px-4 py-3 text-slate-600">{a.name}</td>
                  <td className="px-4 py-3"><span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">{ASSET_TYPE_LABELS[a.assetType] || a.assetType}</span></td>
                  <td className="px-4 py-3 text-slate-600">{a.exchange || '-'}</td>
                  <td className="px-4 py-3 text-slate-600">{a.currency}</td>
                  <td className="px-4 py-3 text-right text-slate-700">
                    {a.currentPrice != null ? (
                      <div>
                        <span>{formatCurrency(a.currentPrice, a.currency)}</span>
                        {a.priceUpdatedAt && <p className="text-[10px] text-slate-400">as of {formatDate(a.priceUpdatedAt)}</p>}
                      </div>
                    ) : <span className="text-slate-300">-</span>}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => handleRefreshPrice(a.id, a.symbol)} disabled={refreshingId === a.id} className="text-slate-400 hover:text-emerald-600 disabled:opacity-50" title="Fetch latest price online">
                        <RefreshCw size={14} className={refreshingId === a.id ? 'animate-spin' : ''} />
                      </button>
                      <button onClick={() => startEdit(a)} className="text-slate-400 hover:text-indigo-600"><Pencil size={14} /></button>
                      <button onClick={() => handleDelete(a.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={14} /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {assets.length === 0 && <tr><td colSpan={7} className="px-4 py-12 text-center text-slate-400">No assets configured. Click "New Asset" to add one.</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
