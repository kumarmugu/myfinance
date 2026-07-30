import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { getAssets, createAsset, deleteAsset, Asset } from '../api';

const ASSET_LABELS: Record<string, string> = {
  EQUITY: 'Equity',
  INDEX_FUND: 'Index Fund',
  MUTUAL_FUND: 'Mutual Fund',
  CRYPTO: 'Crypto',
  BANK_DEPOSIT: 'Bank Deposit',
};

function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 }).format(amount);
}

export default function Assets() {
  const [assets, setAssets] = useState<Asset[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({
    name: '', symbol: '', assetType: 'EQUITY' as Asset['assetType'],
    currentPrice: 0, exchange: '', description: '',
  });

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try { const res = await getAssets(); setAssets(res.data); }
    catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await createAsset(form);
      setShowForm(false);
      setForm({ name: '', symbol: '', assetType: 'EQUITY', currentPrice: 0, exchange: '', description: '' });
      loadData();
    } catch (err) { console.error(err); alert('Failed to create asset'); }
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Delete this asset?')) return;
    try { await deleteAsset(id); loadData(); } catch (err) { console.error(err); }
  };

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
    </div>
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Assets</h1>
          <p className="text-slate-500 text-sm mt-1">Manage stocks, funds, crypto, and deposits</p>
        </div>
        <button onClick={() => setShowForm(!showForm)}
          className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
          <Plus size={16} /> New Asset
        </button>
      </div>

      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-lg font-semibold text-slate-800 mb-4">Add Asset</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Name</label>
              <input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" required />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Symbol</label>
              <input type="text" value={form.symbol} onChange={(e) => setForm({ ...form, symbol: e.target.value })}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" required />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Type</label>
              <select value={form.assetType} onChange={(e) => setForm({ ...form, assetType: e.target.value as Asset['assetType'] })}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500">
                <option value="EQUITY">Equity</option>
                <option value="INDEX_FUND">Index Fund</option>
                <option value="MUTUAL_FUND">Mutual Fund</option>
                <option value="CRYPTO">Crypto</option>
                <option value="BANK_DEPOSIT">Bank Deposit</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Current Price</label>
              <input type="number" step="any" value={form.currentPrice || ''} onChange={(e) => setForm({ ...form, currentPrice: parseFloat(e.target.value) || 0 })}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Exchange</label>
              <input type="text" value={form.exchange} onChange={(e) => setForm({ ...form, exchange: e.target.value })}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" />
            </div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">Save</button>
              <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 bg-slate-200 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-300">Cancel</button>
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
                <th className="text-right px-4 py-3 font-medium text-slate-600">Current Price</th>
                <th className="px-4 py-3"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {assets.map((asset) => (
                <tr key={asset.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-medium text-slate-800">{asset.symbol}</td>
                  <td className="px-4 py-3 text-slate-600">{asset.name}</td>
                  <td className="px-4 py-3">
                    <span className="text-xs px-2 py-1 rounded-full bg-slate-100 text-slate-600">
                      {ASSET_LABELS[asset.assetType] || asset.assetType}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-600">{asset.exchange || '-'}</td>
                  <td className="px-4 py-3 text-right text-slate-700">{asset.currentPrice ? formatCurrency(asset.currentPrice) : '-'}</td>
                  <td className="px-4 py-3">
                    <button onClick={() => handleDelete(asset.id)} className="text-slate-400 hover:text-red-500" aria-label="Delete asset">
                      <Trash2 size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
