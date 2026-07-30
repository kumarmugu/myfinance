import { useEffect, useState } from 'react';
import { Plus, Trash2, Building2, TrendingUp } from 'lucide-react';
import { getAccounts, createAccount, deleteAccount, Account } from '../api';

export default function Accounts() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ name: '', accountType: 'BROKER' as 'BROKER' | 'BANK', description: '' });

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const res = await getAccounts();
      setAccounts(res.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await createAccount(form);
      setShowForm(false);
      setForm({ name: '', accountType: 'BROKER', description: '' });
      loadData();
    } catch (err) { console.error(err); alert('Failed to create account'); }
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Delete this account?')) return;
    try { await deleteAccount(id); loadData(); }
    catch (err) { console.error(err); }
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
          <h1 className="text-2xl font-bold text-slate-800">Accounts</h1>
          <p className="text-slate-500 text-sm mt-1">Manage your brokers and bank accounts</p>
        </div>
        <button onClick={() => setShowForm(!showForm)}
          className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
          <Plus size={16} /> New Account
        </button>
      </div>

      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-lg font-semibold text-slate-800 mb-4">Add Account</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Name</label>
              <input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" required />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Type</label>
              <select value={form.accountType} onChange={(e) => setForm({ ...form, accountType: e.target.value as 'BROKER' | 'BANK' })}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500">
                <option value="BROKER">Broker</option>
                <option value="BANK">Bank</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Description</label>
              <input type="text" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" />
            </div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">Save</button>
              <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 bg-slate-200 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-300">Cancel</button>
            </div>
          </form>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {accounts.map((acc) => (
          <div key={acc.id} className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-3">
                <div className={`p-2 rounded-lg ${acc.accountType === 'BROKER' ? 'bg-indigo-100' : 'bg-green-100'}`}>
                  {acc.accountType === 'BROKER' ? <TrendingUp size={20} className="text-indigo-600" /> : <Building2 size={20} className="text-green-600" />}
                </div>
                <div>
                  <h4 className="font-medium text-slate-800">{acc.name}</h4>
                  <span className={`text-xs px-2 py-0.5 rounded-full ${acc.accountType === 'BROKER' ? 'bg-indigo-100 text-indigo-700' : 'bg-green-100 text-green-700'}`}>
                    {acc.accountType}
                  </span>
                </div>
              </div>
              <button onClick={() => handleDelete(acc.id)} className="text-slate-400 hover:text-red-500" aria-label="Delete account">
                <Trash2 size={16} />
              </button>
            </div>
            {acc.description && <p className="text-sm text-slate-500 mt-3">{acc.description}</p>}
          </div>
        ))}
      </div>
    </div>
  );
}
