import { useEffect, useState } from 'react';
import { Check, X, Save } from 'lucide-react';
import api from '../api';

interface NetWorthConfigItem {
  id: number;
  assetType: string;
  includeInNetWorth: boolean;
  label: string;
}

export default function NetWorthConfig() {
  const [configs, setConfigs] = useState<NetWorthConfigItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const res = await api.get<NetWorthConfigItem[]>('/net-worth-config');
      setConfigs(res.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const toggle = (id: number) => {
    setConfigs(prev => prev.map(c => c.id === id ? { ...c, includeInNetWorth: !c.includeInNetWorth } : c));
  };

  const saveAll = async () => {
    setSaving(true);
    try {
      await api.put('/net-worth-config/batch', configs);
      loadData();
    } catch (err) { console.error(err); alert('Failed to save'); }
    finally { setSaving(false); }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const includedCount = configs.filter(c => c.includeInNetWorth).length;
  const excludedCount = configs.filter(c => !c.includeInNetWorth).length;

  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Net Worth Configuration</h1>
          <p className="text-slate-500 text-sm mt-0.5">Choose which asset types are included in your net worth calculation</p>
        </div>
        <button onClick={saveAll} disabled={saving} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:opacity-50">
          <Save size={16} /> {saving ? 'Saving...' : 'Save Changes'}
        </button>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-2 gap-3">
        <div className="bg-green-50 border border-green-200 rounded-lg p-4">
          <p className="text-xs font-medium text-green-700 uppercase">Included in Net Worth</p>
          <p className="text-2xl font-bold text-green-700 mt-1">{includedCount} types</p>
        </div>
        <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
          <p className="text-xs font-medium text-slate-500 uppercase">Excluded from Net Worth</p>
          <p className="text-2xl font-bold text-slate-500 mt-1">{excludedCount} types</p>
        </div>
      </div>

      {/* Info */}
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-3">
        <p className="text-xs text-blue-700">Toggle each asset type to include or exclude it from net worth calculations. This affects Dashboard, Reports, Planning allocation, and snapshots. Changes apply after clicking <b>Save Changes</b>.</p>
      </div>

      {/* Config Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 border-b border-slate-200">
            <tr>
              <th className="text-left px-5 py-3 font-medium text-slate-600">Asset Type</th>
              <th className="text-center px-5 py-3 font-medium text-slate-600">Include in Net Worth</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {configs.map(c => (
              <tr key={c.id} className="hover:bg-slate-50">
                <td className="px-5 py-3.5">
                  <span className="font-medium text-slate-800">{c.label || c.assetType}</span>
                  <span className="ml-2 text-[10px] text-slate-400 font-mono">{c.assetType}</span>
                </td>
                <td className="px-5 py-3.5 text-center">
                  <button
                    onClick={() => toggle(c.id)}
                    className={`w-12 h-6 rounded-full relative transition-colors ${c.includeInNetWorth ? 'bg-green-500' : 'bg-slate-300'}`}
                  >
                    <span className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform ${c.includeInNetWorth ? 'translate-x-6' : 'translate-x-0.5'}`} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Quick Actions */}
      <div className="flex gap-3">
        <button onClick={() => setConfigs(prev => prev.map(c => ({ ...c, includeInNetWorth: true })))} className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-green-700 border border-green-200 rounded-lg hover:bg-green-50">
          <Check size={12} /> Include All
        </button>
        <button onClick={() => setConfigs(prev => prev.map(c => ({ ...c, includeInNetWorth: false })))} className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-600 border border-slate-200 rounded-lg hover:bg-slate-50">
          <X size={12} /> Exclude All
        </button>
      </div>
    </div>
  );
}
