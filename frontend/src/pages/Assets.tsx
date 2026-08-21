import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { getAssets, createAsset, deleteAsset } from '../api';
import SearchableSelect from '../components/SearchableSelect';
import type { Asset, AssetType, Currency } from '../types';
import { ASSET_TYPE_LABELS } from '../types';

export default function Assets() {
  const [assets, setAssets] = useState<Asset[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ name: '', symbol: '', assetType: 'GROWTH_EQUITY' as AssetType, currency: 'USD' as Currency, exchange: '', description: '' });

  useEffect(() => { loadData(); }, []);
  const loadData = async () => { try { setAssets((await getAssets()).data); } catch (err) { console.error(err); } finally { setLoading(false); } };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try { await createAsset(form); setShowForm(false); setForm({ name: '', symbol: '', assetType: 'GROWTH_EQUITY', currency: 'USD', exchange: '', description: '' }); loadData(); }
    catch (err) { console.error(err); alert('Failed'); }
  };
  const handleDelete = async (id: number) => {
    if (confirm('Delete?')) {
      try { await deleteAsset(id); loadData(); }
      catch (err: any) {
        const msg = err.response?.data?.message || 'Failed to delete';
        const refs = err.response?.data?.references;
        alert(refs ? `${msg}\n\nReferenced by:\n• ${refs.join('\n• ')}` : msg);
      }
    }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Assets</h1><p className="text-slate-500 text-sm mt-1">Manage stocks, ETFs, funds, and crypto</p></div>
        <button onClick={() => setShowForm(!showForm)} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700"><Plus size={16} /> New Asset</button>
      </div>

      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Name</label><input type="text" value={form.name} onChange={e => setForm({...form, name: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Symbol</label><input type="text" value={form.symbol} onChange={e => setForm({...form, symbol: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
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
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium">Save</button>
              <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 bg-slate-200 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
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
                <th className="px-4 py-3"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {assets.map(a => (
                <tr key={a.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-medium text-slate-800">{a.symbol}</td>
                  <td className="px-4 py-3 text-slate-600">{a.name}</td>
                  <td className="px-4 py-3"><span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">{ASSET_TYPE_LABELS[a.assetType] || a.assetType}</span></td>
                  <td className="px-4 py-3 text-slate-600">{a.exchange || '-'}</td>
                  <td className="px-4 py-3 text-slate-600">{a.currency}</td>
                  <td className="px-4 py-3"><button onClick={() => handleDelete(a.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={16} /></button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
