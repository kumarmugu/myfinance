import { useEffect, useState } from 'react';
import { Plus, Trash2, ArrowUpCircle, ArrowDownCircle } from 'lucide-react';
import { getTransactions, createTransaction, deleteTransaction, getAssets, getAccounts, getOwners } from '../api';
import { formatCurrency, formatDate } from '../utils/formatters';
import type { Transaction, Asset, Account, Owner, TransactionRequest } from '../types';

export default function Transactions() {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [owners, setOwners] = useState<Owner[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState<TransactionRequest>({
    assetId: 0, accountId: 0, ownerId: 0, transactionType: 'BUY',
    quantity: 0, pricePerUnit: 0, fees: 0, transactionDate: new Date().toISOString().split('T')[0], notes: '',
  });

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [txRes, assetRes, accRes, ownerRes] = await Promise.all([getTransactions(), getAssets(), getAccounts(), getOwners()]);
      setTransactions(txRes.data); setAssets(assetRes.data); setAccounts(accRes.data); setOwners(ownerRes.data);
      if (ownerRes.data.length > 0 && form.ownerId === 0) setForm(f => ({ ...f, ownerId: ownerRes.data[0].id }));
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try { await createTransaction(form); setShowForm(false); loadData(); }
    catch (err) { console.error(err); alert('Failed to create transaction'); }
  };

  const handleDelete = async (id: number) => { if (confirm('Delete?')) { await deleteTransaction(id); loadData(); } };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Transactions</h1><p className="text-slate-500 text-sm mt-1">Record buy and sell transactions</p></div>
        <button onClick={() => setShowForm(!showForm)} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700"><Plus size={16} /> New Transaction</button>
      </div>

      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Owner</label>
              <select value={form.ownerId} onChange={e => setForm({...form, ownerId: Number(e.target.value)})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required>
                {owners.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
              </select></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Asset</label>
              <select value={form.assetId} onChange={e => setForm({...form, assetId: Number(e.target.value)})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required>
                <option value={0}>Select...</option>{assets.map(a => <option key={a.id} value={a.id}>{a.symbol} - {a.name}</option>)}
              </select></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Account</label>
              <select value={form.accountId} onChange={e => setForm({...form, accountId: Number(e.target.value)})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required>
                <option value={0}>Select...</option>{accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Type</label>
              <select value={form.transactionType} onChange={e => setForm({...form, transactionType: e.target.value as 'BUY'|'SELL'})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm">
                <option value="BUY">Buy</option><option value="SELL">Sell</option>
              </select></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Quantity</label>
              <input type="number" step="any" value={form.quantity || ''} onChange={e => setForm({...form, quantity: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Price per Unit</label>
              <input type="number" step="any" value={form.pricePerUnit || ''} onChange={e => setForm({...form, pricePerUnit: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Date</label>
              <input type="date" value={form.transactionDate} onChange={e => setForm({...form, transactionDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-sm font-medium text-slate-700 mb-1">Notes</label>
              <input type="text" value={form.notes} onChange={e => setForm({...form, notes: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">Save</button>
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
                <th className="text-left px-4 py-3 font-medium text-slate-600">Date</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Type</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Asset</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Account</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Owner</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Qty</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Price</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Total</th>
                <th className="px-4 py-3"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {transactions.map(tx => (
                <tr key={tx.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 text-slate-700">{formatDate(tx.transactionDate)}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-1 rounded-full ${tx.transactionType === 'BUY' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                      {tx.transactionType === 'BUY' ? <ArrowDownCircle size={12} /> : <ArrowUpCircle size={12} />}{tx.transactionType}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-medium text-slate-800">{tx.asset.symbol}</td>
                  <td className="px-4 py-3 text-slate-600">{tx.account.name}</td>
                  <td className="px-4 py-3 text-slate-500">{tx.owner.name}</td>
                  <td className="px-4 py-3 text-right text-slate-700">{tx.quantity}</td>
                  <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(tx.pricePerUnit, tx.currency)}</td>
                  <td className="px-4 py-3 text-right font-medium text-slate-800">{formatCurrency(tx.totalAmount, tx.currency)}</td>
                  <td className="px-4 py-3"><button onClick={() => handleDelete(tx.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={15} /></button></td>
                </tr>
              ))}
              {transactions.length === 0 && <tr><td colSpan={9} className="px-4 py-12 text-center text-slate-400">No transactions yet</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
