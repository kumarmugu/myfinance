import { useEffect, useState } from 'react';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts';
import { Plus, TrendingUp, TrendingDown, Bitcoin, Wallet } from 'lucide-react';
import { getActiveHoldings, getTransactions, getAssets, getAccounts, getOwners, createTransaction } from '../api';
import { formatCurrency, formatPercent } from '../utils/formatters';
import type { Holding, Transaction, Asset, Account, Owner, TransactionRequest } from '../types';

const CRYPTO_COLORS = ['#f7931a', '#627eea', '#14f195', '#e84142', '#2775ca', '#26a17b', '#8247e5', '#00d395'];

export default function Crypto() {
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [owners, setOwners] = useState<Owner[]>([]);
  void owners; // used for form ownerId default
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(true);

  const [form, setForm] = useState<TransactionRequest>({
    assetId: 0, accountId: 0, ownerId: 0, transactionType: 'BUY',
    quantity: 0, pricePerUnit: 0, transactionDate: new Date().toISOString().split('T')[0], notes: '',
  });

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [holdRes, txRes, assetRes, accRes, ownRes] = await Promise.all([
        getActiveHoldings(), getTransactions(), getAssets(), getAccounts(), getOwners()
      ]);
      // Filter crypto only
      setHoldings(holdRes.data.filter(h => h.asset.assetType === 'CRYPTO'));
      setTransactions(txRes.data.filter(t => t.asset.assetType === 'CRYPTO'));
      setAssets(assetRes.data.filter(a => a.assetType === 'CRYPTO'));
      setAccounts(accRes.data.filter(a => a.accountType === 'CRYPTO_EXCHANGE'));
      setOwners(ownRes.data);
      if (ownRes.data.length > 0 && form.ownerId === 0) setForm(f => ({ ...f, ownerId: ownRes.data[0].id }));
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try { await createTransaction(form); setShowForm(false); loadData(); }
    catch (err) { console.error(err); alert('Failed'); }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  // Calculate P&L per holding
  const cryptoData = holdings.map(h => {
    const currentPrice = h.asset.currentPrice || h.averageBuyPrice;
    const currentValue = h.quantity * currentPrice;
    const gain = currentValue - h.investedAmount;
    const pct = h.investedAmount > 0 ? (gain / h.investedAmount) * 100 : 0;
    return { ...h, currentPrice, currentValue, gain, pct };
  });

  const totalInvested = cryptoData.reduce((s, c) => s + c.investedAmount, 0);
  const totalCurrent = cryptoData.reduce((s, c) => s + c.currentValue, 0);
  const totalGain = totalCurrent - totalInvested;
  const totalPct = totalInvested > 0 ? (totalGain / totalInvested) * 100 : 0;

  // Pie data
  const pieData = cryptoData.map((c, i) => ({ name: c.asset.symbol, value: c.currentValue, color: CRYPTO_COLORS[i % CRYPTO_COLORS.length] }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Crypto Portfolio</h1><p className="text-slate-500 text-sm mt-0.5">Manage your cryptocurrency holdings</p></div>
        <button onClick={() => setShowForm(!showForm)} className="flex items-center gap-2 px-4 py-2 bg-amber-600 text-white rounded-lg text-sm font-medium hover:bg-amber-700">
          <Plus size={16} /> Add Crypto
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <SummaryCard icon={<Wallet size={16} className="text-amber-600" />} label="Total Value" value={formatCurrency(totalCurrent, 'USD')} />
        <SummaryCard icon={<Bitcoin size={16} className="text-orange-500" />} label="Invested" value={formatCurrency(totalInvested, 'USD')} />
        <SummaryCard icon={totalGain >= 0 ? <TrendingUp size={16} className="text-green-600" /> : <TrendingDown size={16} className="text-red-600" />} label="P&L" value={formatCurrency(totalGain, 'USD')} sub={formatPercent(totalPct)} positive={totalGain >= 0} />
        <SummaryCard icon={<Bitcoin size={16} className="text-slate-600" />} label="Coins" value={`${holdings.length}`} sub={`${accounts.length} exchanges`} />
      </div>

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">Add Crypto Transaction</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Coin</label>
              <select value={form.assetId} onChange={e => setForm({...form, assetId: Number(e.target.value)})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required>
                <option value={0}>Select coin...</option>
                {assets.map(a => <option key={a.id} value={a.id}>{a.symbol} - {a.name}</option>)}
              </select></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Exchange</label>
              <select value={form.accountId} onChange={e => setForm({...form, accountId: Number(e.target.value)})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required>
                <option value={0}>Select exchange...</option>
                {accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Type</label>
              <div className="flex rounded-lg overflow-hidden border border-slate-300">
                <button type="button" onClick={() => setForm({...form, transactionType: 'BUY'})} className={`flex-1 py-2 text-sm font-medium ${form.transactionType === 'BUY' ? 'bg-green-600 text-white' : 'bg-white text-slate-600'}`}>Buy</button>
                <button type="button" onClick={() => setForm({...form, transactionType: 'SELL'})} className={`flex-1 py-2 text-sm font-medium ${form.transactionType === 'SELL' ? 'bg-red-600 text-white' : 'bg-white text-slate-600'}`}>Sell</button>
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Quantity</label>
              <input type="number" step="any" value={form.quantity || ''} onChange={e => setForm({...form, quantity: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Price (USD)</label>
              <input type="number" step="any" value={form.pricePerUnit || ''} onChange={e => setForm({...form, pricePerUnit: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Date</label>
              <input type="date" value={form.transactionDate} onChange={e => setForm({...form, transactionDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-amber-600 text-white rounded-lg text-sm font-medium hover:bg-amber-700">Save</button>
              <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Chart + Holdings */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Pie chart */}
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Allocation</h3>
          {pieData.length > 0 ? (
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie data={pieData} cx="50%" cy="50%" innerRadius={45} outerRadius={80} dataKey="value" label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`} labelLine={false}>
                  {pieData.map((e, i) => <Cell key={i} fill={e.color} />)}
                </Pie>
                <Tooltip formatter={(v) => formatCurrency(v as number, 'USD')} />
              </PieChart>
            </ResponsiveContainer>
          ) : <div className="h-60 flex items-center justify-center text-sm text-slate-400">No crypto holdings</div>}
        </div>

        {/* Holdings Table */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 border-b border-slate-200">
            <h3 className="font-semibold text-slate-800 text-sm">Holdings ({cryptoData.length})</h3>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-2.5 font-medium text-slate-600">Coin</th>
                  <th className="text-left px-4 py-2.5 font-medium text-slate-600">Exchange</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Qty</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Avg Buy</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Current</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Value</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">P&L</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {cryptoData.sort((a, b) => b.currentValue - a.currentValue).map(c => (
                  <tr key={c.id} className="hover:bg-slate-50">
                    <td className="px-4 py-2.5">
                      <span className="font-medium text-slate-800">{c.asset.symbol}</span>
                      <p className="text-[10px] text-slate-400">{c.asset.name}</p>
                    </td>
                    <td className="px-4 py-2.5 text-slate-600">{c.account.name}</td>
                    <td className="px-4 py-2.5 text-right font-mono text-slate-700">{c.quantity < 1 ? c.quantity.toFixed(6) : c.quantity.toFixed(4)}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{formatCurrency(c.averageBuyPrice, 'USD')}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{formatCurrency(c.currentPrice, 'USD')}</td>
                    <td className="px-4 py-2.5 text-right font-medium text-slate-800">{formatCurrency(c.currentValue, 'USD')}</td>
                    <td className="px-4 py-2.5 text-right">
                      <span className={`font-medium ${c.gain >= 0 ? 'text-green-600' : 'text-red-600'}`}>{formatPercent(c.pct)}</span>
                    </td>
                  </tr>
                ))}
                {cryptoData.length === 0 && <tr><td colSpan={7} className="px-4 py-12 text-center text-slate-400">No crypto holdings. Add your first crypto purchase above.</td></tr>}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Recent Crypto Transactions */}
      {transactions.length > 0 && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 border-b border-slate-200">
            <h3 className="font-semibold text-slate-800 text-sm">Recent Transactions ({transactions.length})</h3>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-2.5 font-medium text-slate-600">Date</th>
                  <th className="text-left px-4 py-2.5 font-medium text-slate-600">Type</th>
                  <th className="text-left px-4 py-2.5 font-medium text-slate-600">Coin</th>
                  <th className="text-left px-4 py-2.5 font-medium text-slate-600">Exchange</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Qty</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Price</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {transactions.slice(0, 20).map(tx => (
                  <tr key={tx.id} className="hover:bg-slate-50">
                    <td className="px-4 py-2 text-slate-600 text-xs">{tx.transactionDate}</td>
                    <td className="px-4 py-2">
                      <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${tx.transactionType === 'BUY' ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>{tx.transactionType}</span>
                    </td>
                    <td className="px-4 py-2 font-medium text-slate-800">{tx.asset.symbol}</td>
                    <td className="px-4 py-2 text-slate-600">{tx.account.name}</td>
                    <td className="px-4 py-2 text-right text-slate-700">{tx.quantity}</td>
                    <td className="px-4 py-2 text-right text-slate-700">{formatCurrency(tx.pricePerUnit, 'USD')}</td>
                    <td className="px-4 py-2 text-right font-medium text-slate-800">{formatCurrency(tx.totalAmount, 'USD')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

function SummaryCard({ icon, label, value, sub, positive }: { icon: React.ReactNode; label: string; value: string; sub?: string; positive?: boolean }) {
  return (
    <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
      <div className="flex items-center gap-2 mb-1.5">{icon}<span className="text-[11px] text-slate-500 uppercase tracking-wide">{label}</span></div>
      <p className="text-lg font-bold text-slate-800 leading-tight">{value}</p>
      {sub && <p className={`text-xs mt-0.5 ${positive === true ? 'text-green-500' : positive === false ? 'text-red-500' : 'text-slate-500'}`}>{sub}</p>}
    </div>
  );
}
