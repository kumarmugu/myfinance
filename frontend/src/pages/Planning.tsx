import { useEffect, useState } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { getAllocationPlan, getAccountDeposits, getNetWorthHistory } from '../api';
import { formatCurrency } from '../utils/formatters';
import type { AllocationTarget, AccountDeposit, NetWorthSnapshot } from '../types';
import { ASSET_TYPE_LABELS, ASSET_TYPE_COLORS } from '../types';

type Tab = 'allocation' | 'networth' | 'deposits';

export default function Planning() {
  const [tab, setTab] = useState<Tab>('allocation');
  const [targets, setTargets] = useState<AllocationTarget[]>([]);
  const [current, setCurrent] = useState<Record<string, number>>({});
  const [deposits, setDeposits] = useState<AccountDeposit[]>([]);
  const [history, setHistory] = useState<NetWorthSnapshot[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [allocRes, depRes, histRes] = await Promise.all([getAllocationPlan(), getAccountDeposits(), getNetWorthHistory()]);
      setTargets(allocRes.data.targets);
      setCurrent(allocRes.data.current);
      setDeposits(depRes.data);
      setHistory(histRes.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const totalCurrent = Object.values(current).reduce((s, v) => s + v, 0);

  // Allocation chart data
  const allocData = targets.map(t => {
    const currentAmount = current[t.assetType] || 0;
    const currentPct = totalCurrent > 0 ? (currentAmount / totalCurrent) * 100 : 0;
    return {
      type: ASSET_TYPE_LABELS[t.assetType] || t.assetType,
      target: t.targetPercentage,
      actual: parseFloat(currentPct.toFixed(1)),
      gap: currentAmount - (t.targetAmount || 0),
      currentAmount,
      targetAmount: t.targetAmount || 0,
    };
  });

  // Net worth history chart
  const nwData = [...history].reverse().map(s => ({
    year: s.year || new Date(s.snapshotDate).getFullYear(),
    total: s.totalNetWorth,
    indexFund: s.totalIndexFund,
    growthEquity: s.totalGrowthEquity,
    savings: s.totalSavings,
    crypto: s.totalCrypto,
  }));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Financial Planning</h1>
        <p className="text-slate-500 text-sm mt-1">Target allocation, net worth tracking, and deposit history</p>
      </div>

      <div className="flex gap-1 bg-slate-100 rounded-lg p-1 w-fit">
        {(['allocation', 'networth', 'deposits'] as Tab[]).map(t => (
          <button key={t} onClick={() => setTab(t)} className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${tab === t ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'}`}>
            {t === 'allocation' ? 'Allocation' : t === 'networth' ? 'Net Worth' : 'Deposits'}
          </button>
        ))}
      </div>

      {tab === 'allocation' && (
        <div className="space-y-6">
          {/* Allocation Bar Chart */}
          <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
            <h3 className="font-semibold text-slate-800 mb-4">Target vs Actual Allocation (%)</h3>
            <ResponsiveContainer width="100%" height={350}>
              <BarChart data={allocData} layout="vertical" margin={{ left: 20 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis type="number" domain={[0, 40]} tickFormatter={v => `${v}%`} stroke="#94a3b8" fontSize={12} />
                <YAxis type="category" dataKey="type" stroke="#94a3b8" fontSize={11} width={110} />
                <Tooltip formatter={(v) => `${v}%`} />
                <Legend />
                <Bar dataKey="target" fill="#94a3b8" name="Target %" radius={[0, 4, 4, 0]} />
                <Bar dataKey="actual" fill="#6366f1" name="Actual %" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Allocation Table */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="p-4 border-b border-slate-200">
              <h3 className="font-semibold text-slate-800">Allocation Detail</h3>
              <p className="text-sm text-slate-500">Total Portfolio: {formatCurrency(totalCurrent)}</p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Asset Type</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Current</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Actual %</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Target %</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Target $</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Gap</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {allocData.map(row => (
                    <tr key={row.type} className="hover:bg-slate-50">
                      <td className="px-4 py-3 font-medium text-slate-800">{row.type}</td>
                      <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(row.currentAmount)}</td>
                      <td className="px-4 py-3 text-right text-slate-700">{row.actual}%</td>
                      <td className="px-4 py-3 text-right text-slate-700">{row.target}%</td>
                      <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(row.targetAmount)}</td>
                      <td className={`px-4 py-3 text-right font-medium ${row.gap >= 0 ? 'text-green-600' : 'text-red-600'}`}>{formatCurrency(row.gap)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {tab === 'networth' && (
        <div className="space-y-6">
          <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
            <h3 className="font-semibold text-slate-800 mb-4">Net Worth Over Time</h3>
            {nwData.length > 0 ? (
              <ResponsiveContainer width="100%" height={400}>
                <BarChart data={nwData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="year" stroke="#94a3b8" fontSize={12} />
                  <YAxis stroke="#94a3b8" fontSize={12} tickFormatter={v => `S$${(v/1000).toFixed(0)}K`} />
                  <Tooltip formatter={(v) => formatCurrency(v as number)} />
                  <Legend />
                  <Bar dataKey="indexFund" stackId="a" fill={ASSET_TYPE_COLORS.INDEX_FUND} name="Index Fund" />
                  <Bar dataKey="growthEquity" stackId="a" fill={ASSET_TYPE_COLORS.GROWTH_EQUITY} name="Growth Equity" />
                  <Bar dataKey="savings" stackId="a" fill={ASSET_TYPE_COLORS.SAVINGS} name="Savings" />
                  <Bar dataKey="crypto" stackId="a" fill={ASSET_TYPE_COLORS.CRYPTO} name="Crypto" />
                </BarChart>
              </ResponsiveContainer>
            ) : <div className="h-64 flex items-center justify-center text-slate-400">No snapshots yet</div>}
          </div>

          {/* History Table */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Year</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Net Worth</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Index</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Growth</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Savings</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Crypto</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {history.map(s => (
                    <tr key={s.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3 font-medium text-slate-800">{s.year || s.snapshotDate}</td>
                      <td className="px-4 py-3 text-right font-bold text-slate-800">{formatCurrency(s.totalNetWorth)}</td>
                      <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(s.totalIndexFund)}</td>
                      <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(s.totalGrowthEquity)}</td>
                      <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(s.totalSavings)}</td>
                      <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(s.totalCrypto)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {tab === 'deposits' && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 border-b border-slate-200">
            <h3 className="font-semibold text-slate-800">Deposit & Withdrawal History</h3>
            <p className="text-sm text-slate-500">{deposits.length} records</p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Date</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Account</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Type</th>
                  <th className="text-right px-4 py-3 font-medium text-slate-600">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {deposits.map(d => (
                  <tr key={d.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 text-slate-700">{d.depositDate}</td>
                    <td className="px-4 py-3 text-slate-700">{d.account.name}</td>
                    <td className="px-4 py-3"><span className={`text-xs px-2 py-0.5 rounded-full font-medium ${d.depositType === 'DEPOSIT' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>{d.depositType}</span></td>
                    <td className={`px-4 py-3 text-right font-medium ${d.depositType === 'DEPOSIT' ? 'text-green-600' : 'text-red-600'}`}>{formatCurrency(d.amount, d.currency)}</td>
                  </tr>
                ))}
                {deposits.length === 0 && <tr><td colSpan={4} className="px-4 py-12 text-center text-slate-400">No deposits recorded</td></tr>}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
