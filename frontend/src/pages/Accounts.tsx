import { useEffect, useState } from 'react';
import { Plus, Trash2, Building2, TrendingUp, Bitcoin } from 'lucide-react';
import { getAccounts, createAccount, deleteAccount } from '../api';
import type { Account, AccountType, Currency } from '../types';

export default function Accounts() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ name: '', accountType: 'BROKER' as AccountType, currency: 'SGD' as Currency, description: '' });

  useEffect(() => { loadData(); }, []);
  const loadData = async () => { try { setAccounts((await getAccounts()).data); } catch (err) { console.error(err); } finally { setLoading(false); } };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try { await createAccount(form); setShowForm(false); setForm({ name: '', accountType: 'BROKER', currency: 'SGD', description: '' }); loadData(); }
    catch (err) { console.error(err); alert('Failed'); }
  };
  const handleDelete = async (id: number) => { if (confirm('Delete?')) { await deleteAccount(id); loadData(); } };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const iconFor = (type: AccountType) => type === 'BROKER' ? <TrendingUp size={20} className="text-indigo-600" /> : type === 'CRYPTO_EXCHANGE' ? <Bitcoin size={20} className="text-amber-600" /> : <Building2 size={20} className="text-green-600" />;
  const bgFor = (type: AccountType) => type === 'BROKER' ? 'bg-indigo-100' : type === 'CRYPTO_EXCHANGE' ? 'bg-amber-100' : 'bg-green-100';

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Accounts</h1><p className="text-slate-500 text-sm mt-1">Manage brokers, banks, and crypto exchanges</p></div>
        <button onClick={() => setShowForm(!showForm)} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700"><Plus size={16} /> New Account</button>
      </div>

      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Name</label><input type="text" value={form.name} onChange={e => setForm({...form, name: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Type</label>
              <select value={form.accountType} onChange={e => setForm({...form, accountType: e.target.value as AccountType})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm">
                <option value="BROKER">Broker</option><option value="BANK">Bank</option><option value="CRYPTO_EXCHANGE">Crypto Exchange</option>
              </select></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Currency</label>
              <select value={form.currency} onChange={e => setForm({...form, currency: e.target.value as Currency})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm">
                <option value="SGD">SGD</option><option value="USD">USD</option><option value="EUR">EUR</option><option value="LKR">LKR</option>
              </select></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium">Save</button>
              <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 bg-slate-200 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {accounts.map(acc => (
          <div key={acc.id} className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-3">
                <div className={`p-2 rounded-lg ${bgFor(acc.accountType)}`}>{iconFor(acc.accountType)}</div>
                <div>
                  <h4 className="font-medium text-slate-800">{acc.name}</h4>
                  <div className="flex gap-2 mt-0.5">
                    <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">{acc.accountType}</span>
                    <span className="text-xs px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-600">{acc.currency}</span>
                  </div>
                </div>
              </div>
              <button onClick={() => handleDelete(acc.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={16} /></button>
            </div>
            {acc.description && <p className="text-sm text-slate-500 mt-3">{acc.description}</p>}
            {acc.owner && <p className="text-xs text-slate-400 mt-1">Owner: {acc.owner.name}</p>}
          </div>
        ))}
      </div>
    </div>
  );
}
