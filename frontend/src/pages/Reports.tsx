import { useEffect, useState } from 'react';
import { AreaChart, Area, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { getNetWorthHistory, getTransactions, getActiveHoldings, takeSnapshot } from '../api';
import { formatCurrency } from '../utils/formatters';
import type { NetWorthSnapshot, Transaction, Holding } from '../types';
import { ASSET_TYPE_LABELS, ASSET_TYPE_COLORS } from '../types';

export default function Reports() {
  const [history, setHistory] = useState<NetWorthSnapshot[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [histRes, txRes, holdRes] = await Promise.all([getNetWorthHistory(), getTransactions(), getActiveHoldings()]);
      setHistory(histRes.data); setTransactions(txRes.data); setHoldings(holdRes.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  // Net worth chart
  const nwData = [...history].reverse().map(s => ({
    date: s.year ? `${s.year}` : new Date(s.snapshotDate).toLocaleDateString('en-SG', { month: 'short', year: '2-digit' }),
    total: s.totalNetWorth,
    indexFund: s.totalIndexFund,
    growthEquity: s.totalGrowthEquity,
    savings: s.totalSavings,
    crypto: s.totalCrypto,
  }));

  // Monthly flow
  const monthlyFlow: Record<string, { month: string; invested: number; withdrawn: number }> = {};
  transactions.forEach(tx => {
    const month = tx.transactionDate.substring(0, 7);
    if (!monthlyFlow[month]) monthlyFlow[month] = { month, invested: 0, withdrawn: 0 };
    if (tx.transactionType === 'BUY') monthlyFlow[month].invested += tx.totalAmount;
    else if (tx.transactionType === 'SELL') monthlyFlow[month].withdrawn += tx.totalAmount;
  });
  const flowData = Object.values(monthlyFlow).sort((a, b) => a.month.localeCompare(b.month)).slice(-12).map(d => ({
    ...d, month: new Date(d.month + '-01').toLocaleDateString('en-SG', { month: 'short', year: '2-digit' }),
  }));

  // By type summary
  const typeSummary: Record<string, { invested: number; current: number }> = {};
  holdings.forEach(h => {
    const type = h.asset.assetType;
    if (!typeSummary[type]) typeSummary[type] = { invested: 0, current: 0 };
    typeSummary[type].invested += h.investedAmount;
    typeSummary[type].current += h.quantity * (h.asset.currentPrice || h.averageBuyPrice);
  });
  const typeData = Object.entries(typeSummary).map(([type, data]) => ({
    type: ASSET_TYPE_LABELS[type as keyof typeof ASSET_TYPE_LABELS] || type, invested: data.invested, current: data.current, gainLoss: data.current - data.invested,
  }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Reports</h1><p className="text-slate-500 text-sm mt-1">Financial reports and analytics</p></div>
        <button onClick={() => { takeSnapshot(); loadData(); }} className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">Update Snapshot</button>
      </div>

      {/* Net Worth */}
      <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
        <h3 className="text-lg font-semibold text-slate-800 mb-4">Net Worth Over Time</h3>
        {nwData.length > 0 ? (
          <ResponsiveContainer width="100%" height={350}>
            <AreaChart data={nwData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="date" stroke="#94a3b8" fontSize={12} />
              <YAxis stroke="#94a3b8" fontSize={12} tickFormatter={v => `S$${(v/1000).toFixed(0)}K`} />
              <Tooltip formatter={(v) => formatCurrency(v as number)} /><Legend />
              <Area type="monotone" dataKey="indexFund" stackId="1" stroke={ASSET_TYPE_COLORS.INDEX_FUND} fill={ASSET_TYPE_COLORS.INDEX_FUND} fillOpacity={0.3} name="Index" />
              <Area type="monotone" dataKey="growthEquity" stackId="1" stroke={ASSET_TYPE_COLORS.GROWTH_EQUITY} fill={ASSET_TYPE_COLORS.GROWTH_EQUITY} fillOpacity={0.3} name="Growth" />
              <Area type="monotone" dataKey="savings" stackId="1" stroke={ASSET_TYPE_COLORS.SAVINGS} fill={ASSET_TYPE_COLORS.SAVINGS} fillOpacity={0.3} name="Savings" />
              <Area type="monotone" dataKey="crypto" stackId="1" stroke={ASSET_TYPE_COLORS.CRYPTO} fill={ASSET_TYPE_COLORS.CRYPTO} fillOpacity={0.3} name="Crypto" />
            </AreaChart>
          </ResponsiveContainer>
        ) : <div className="h-64 flex items-center justify-center text-slate-400">No snapshots yet</div>}
      </div>

      {/* Monthly Flow */}
      <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
        <h3 className="text-lg font-semibold text-slate-800 mb-4">Monthly Investment Flow</h3>
        {flowData.length > 0 ? (
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={flowData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="month" stroke="#94a3b8" fontSize={12} />
              <YAxis stroke="#94a3b8" fontSize={12} tickFormatter={v => `$${(v/1000).toFixed(0)}K`} />
              <Tooltip formatter={(v) => formatCurrency(v as number)} /><Legend />
              <Bar dataKey="invested" fill="#10b981" name="Invested" radius={[4, 4, 0, 0]} />
              <Bar dataKey="withdrawn" fill="#ef4444" name="Withdrawn" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        ) : <div className="h-64 flex items-center justify-center text-slate-400">No transactions</div>}
      </div>

      {/* Performance by Type */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-200"><h3 className="font-semibold text-slate-800">Performance by Asset Type</h3></div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Type</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Invested</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Current</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Gain/Loss</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Return %</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {typeData.map(row => {
                const pct = row.invested > 0 ? ((row.current - row.invested) / row.invested) * 100 : 0;
                return (
                  <tr key={row.type} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-800">{row.type}</td>
                    <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(row.invested)}</td>
                    <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(row.current)}</td>
                    <td className={`px-4 py-3 text-right font-medium ${row.gainLoss >= 0 ? 'text-green-600' : 'text-red-600'}`}>{formatCurrency(row.gainLoss)}</td>
                    <td className={`px-4 py-3 text-right font-medium ${pct >= 0 ? 'text-green-600' : 'text-red-600'}`}>{pct >= 0 ? '+' : ''}{pct.toFixed(2)}%</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
