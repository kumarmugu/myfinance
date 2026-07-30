import { useEffect, useState } from 'react';
import {
  AreaChart, Area, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts';
import { getNetWorthHistory, getTransactions, getActiveHoldings, takeSnapshot, NetWorthSnapshot, Transaction, Holding } from '../api';

const ASSET_LABELS: Record<string, string> = {
  EQUITY: 'Equity',
  INDEX_FUND: 'Index Fund',
  MUTUAL_FUND: 'Mutual Fund',
  CRYPTO: 'Crypto',
  BANK_DEPOSIT: 'Bank Deposit',
};

function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(amount);
}

export default function Reports() {
  const [history, setHistory] = useState<NetWorthSnapshot[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [histRes, txRes, holdRes] = await Promise.all([
        getNetWorthHistory(),
        getTransactions(),
        getActiveHoldings(),
      ]);
      setHistory(histRes.data);
      setTransactions(txRes.data);
      setHoldings(holdRes.data);
    } catch (err) {
      console.error('Failed to load reports', err);
    } finally {
      setLoading(false);
    }
  };

  const handleTakeSnapshot = async () => {
    await takeSnapshot();
    loadData();
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
      </div>
    );
  }

  // Net worth history chart data
  const netWorthData = [...history].reverse().map((s) => ({
    date: new Date(s.snapshotDate).toLocaleDateString('en-IN', { month: 'short', day: 'numeric', year: '2-digit' }),
    total: s.totalNetWorth,
    equity: s.totalEquity,
    indexFund: s.totalIndexFund,
    mutualFund: s.totalMutualFund,
    crypto: s.totalCrypto,
    bankDeposit: s.totalBankDeposit,
  }));

  // Monthly investment flow
  const monthlyFlow = transactions.reduce((acc, tx) => {
    const month = tx.transactionDate.substring(0, 7); // YYYY-MM
    if (!acc[month]) acc[month] = { month, invested: 0, withdrawn: 0 };
    if (tx.transactionType === 'BUY') {
      acc[month].invested += tx.totalAmount;
    } else {
      acc[month].withdrawn += tx.totalAmount;
    }
    return acc;
  }, {} as Record<string, { month: string; invested: number; withdrawn: number }>);

  const flowData = Object.values(monthlyFlow)
    .sort((a, b) => a.month.localeCompare(b.month))
    .map((d) => ({
      ...d,
      month: new Date(d.month + '-01').toLocaleDateString('en-IN', { month: 'short', year: '2-digit' }),
      net: d.invested - d.withdrawn,
    }));

  // Holdings by asset type summary
  const typeSummary = holdings.reduce((acc, h) => {
    const type = h.asset.assetType;
    if (!acc[type]) acc[type] = { invested: 0, current: 0 };
    const currentPrice = h.asset.currentPrice || h.averageBuyPrice;
    acc[type].invested += h.investedAmount;
    acc[type].current += h.quantity * currentPrice;
    return acc;
  }, {} as Record<string, { invested: number; current: number }>);

  const typeSummaryData = Object.entries(typeSummary).map(([type, data]) => ({
    type: ASSET_LABELS[type] || type,
    invested: data.invested,
    current: data.current,
    gainLoss: data.current - data.invested,
  }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Reports</h1>
          <p className="text-slate-500 text-sm mt-1">Detailed financial reports and analytics</p>
        </div>
        <button
          onClick={handleTakeSnapshot}
          className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 transition-colors"
        >
          Update Snapshot
        </button>
      </div>

      {/* Net Worth Over Time */}
      <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
        <h3 className="text-lg font-semibold text-slate-800 mb-4">Net Worth Over Time</h3>
        {netWorthData.length > 0 ? (
          <ResponsiveContainer width="100%" height={350}>
            <AreaChart data={netWorthData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="date" stroke="#94a3b8" fontSize={12} />
              <YAxis stroke="#94a3b8" fontSize={12} tickFormatter={(v) => `₹${(v / 100000).toFixed(0)}L`} />
              <Tooltip formatter={(value) => formatCurrency(value as number)} />
              <Legend />
              <Area type="monotone" dataKey="equity" stackId="1" stroke="#6366f1" fill="#6366f1" fillOpacity={0.3} name="Equity" />
              <Area type="monotone" dataKey="indexFund" stackId="1" stroke="#06b6d4" fill="#06b6d4" fillOpacity={0.3} name="Index Fund" />
              <Area type="monotone" dataKey="mutualFund" stackId="1" stroke="#10b981" fill="#10b981" fillOpacity={0.3} name="Mutual Fund" />
              <Area type="monotone" dataKey="crypto" stackId="1" stroke="#f59e0b" fill="#f59e0b" fillOpacity={0.3} name="Crypto" />
              <Area type="monotone" dataKey="bankDeposit" stackId="1" stroke="#ef4444" fill="#ef4444" fillOpacity={0.3} name="Bank Deposit" />
            </AreaChart>
          </ResponsiveContainer>
        ) : (
          <div className="flex flex-col items-center justify-center h-64 text-slate-400">
            <p>No snapshots yet. Click "Update Snapshot" to record current net worth.</p>
          </div>
        )}
      </div>

      {/* Monthly Investment Flow */}
      <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
        <h3 className="text-lg font-semibold text-slate-800 mb-4">Monthly Investment Flow</h3>
        {flowData.length > 0 ? (
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={flowData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="month" stroke="#94a3b8" fontSize={12} />
              <YAxis stroke="#94a3b8" fontSize={12} tickFormatter={(v) => `₹${(v / 1000).toFixed(0)}K`} />
              <Tooltip formatter={(value) => formatCurrency(value as number)} />
              <Legend />
              <Bar dataKey="invested" fill="#10b981" name="Invested" radius={[4, 4, 0, 0]} />
              <Bar dataKey="withdrawn" fill="#ef4444" name="Withdrawn" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <div className="flex items-center justify-center h-64 text-slate-400">
            <p>No transactions recorded yet</p>
          </div>
        )}
      </div>

      {/* Performance by Type */}
      <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
        <h3 className="text-lg font-semibold text-slate-800 mb-4">Performance by Asset Type</h3>
        {typeSummaryData.length > 0 ? (
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={typeSummaryData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="type" stroke="#94a3b8" fontSize={12} />
              <YAxis stroke="#94a3b8" fontSize={12} tickFormatter={(v) => `₹${(v / 100000).toFixed(0)}L`} />
              <Tooltip formatter={(value) => formatCurrency(value as number)} />
              <Legend />
              <Bar dataKey="invested" fill="#94a3b8" name="Invested" radius={[4, 4, 0, 0]} />
              <Bar dataKey="current" fill="#6366f1" name="Current Value" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <div className="flex items-center justify-center h-64 text-slate-400">
            <p>No holdings to analyze</p>
          </div>
        )}
      </div>

      {/* Summary Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-200">
          <h3 className="font-semibold text-slate-800">Summary by Asset Type</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Asset Type</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Invested</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Current Value</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Gain/Loss</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Return %</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {typeSummaryData.map((row) => {
                const returnPct = row.invested > 0 ? ((row.current - row.invested) / row.invested) * 100 : 0;
                return (
                  <tr key={row.type} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-800">{row.type}</td>
                    <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(row.invested)}</td>
                    <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(row.current)}</td>
                    <td className={`px-4 py-3 text-right font-medium ${row.gainLoss >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                      {formatCurrency(row.gainLoss)}
                    </td>
                    <td className={`px-4 py-3 text-right font-medium ${returnPct >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                      {returnPct >= 0 ? '+' : ''}{returnPct.toFixed(2)}%
                    </td>
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
