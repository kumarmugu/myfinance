import { useEffect, useState } from 'react';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid } from 'recharts';
import { getActiveHoldings, Holding } from '../api';

const COLORS = ['#6366f1', '#06b6d4', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6'];
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
    minimumFractionDigits: 2,
  }).format(amount);
}

export default function Portfolio() {
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const res = await getActiveHoldings();
      setHoldings(res.data);
    } catch (err) {
      console.error('Failed to load holdings', err);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
      </div>
    );
  }

  const holdingsWithValue = holdings.map((h) => {
    const currentPrice = h.asset.currentPrice || h.averageBuyPrice;
    const currentValue = h.quantity * currentPrice;
    const gainLoss = currentValue - h.investedAmount;
    const gainLossPercent = h.investedAmount > 0 ? (gainLoss / h.investedAmount) * 100 : 0;
    return { ...h, currentValue, gainLoss, gainLossPercent, currentPrice };
  });

  const totalValue = holdingsWithValue.reduce((sum, h) => sum + h.currentValue, 0);

  // Group by asset type for pie chart
  const byType = holdingsWithValue.reduce((acc, h) => {
    const type = h.asset.assetType;
    acc[type] = (acc[type] || 0) + h.currentValue;
    return acc;
  }, {} as Record<string, number>);

  const pieData = Object.entries(byType).map(([key, value]) => ({
    name: ASSET_LABELS[key] || key,
    value,
  }));

  // Top holdings for bar chart
  const topHoldings = [...holdingsWithValue]
    .sort((a, b) => b.currentValue - a.currentValue)
    .slice(0, 8)
    .map((h) => ({
      name: h.asset.symbol,
      value: h.currentValue,
      gainLoss: h.gainLoss,
    }));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Portfolio</h1>
        <p className="text-slate-500 text-sm mt-1">Your current holdings and positions</p>
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-lg font-semibold text-slate-800 mb-4">Allocation by Type</h3>
          <ResponsiveContainer width="100%" height={280}>
            <PieChart>
              <Pie
                data={pieData}
                cx="50%"
                cy="50%"
                innerRadius={50}
                outerRadius={90}
                dataKey="value"
                label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`}
                labelLine={false}
              >
                {pieData.map((_, index) => (
                  <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip formatter={(value) => formatCurrency(value as number)} />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-lg font-semibold text-slate-800 mb-4">Top Holdings</h3>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={topHoldings} layout="vertical">
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis type="number" tickFormatter={(v) => `₹${(v / 1000).toFixed(0)}K`} stroke="#94a3b8" fontSize={12} />
              <YAxis type="category" dataKey="name" stroke="#94a3b8" fontSize={12} width={80} />
              <Tooltip formatter={(value) => formatCurrency(value as number)} />
              <Bar dataKey="value" fill="#6366f1" radius={[0, 4, 4, 0]} name="Value" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Holdings Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-200">
          <h3 className="font-semibold text-slate-800">All Holdings ({holdings.length})</h3>
          <p className="text-sm text-slate-500">Total Value: {formatCurrency(totalValue)}</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Asset</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Type</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Account</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Qty</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Avg Price</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Current Price</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Invested</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Current Value</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Gain/Loss</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {holdingsWithValue.map((h) => (
                <tr key={h.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3">
                    <span className="font-medium text-slate-800">{h.asset.symbol}</span>
                    <p className="text-xs text-slate-400">{h.asset.name}</p>
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-xs px-2 py-1 rounded-full bg-slate-100 text-slate-600">
                      {ASSET_LABELS[h.asset.assetType] || h.asset.assetType}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-600">{h.account.name}</td>
                  <td className="px-4 py-3 text-right text-slate-700">{h.quantity.toFixed(4)}</td>
                  <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(h.averageBuyPrice)}</td>
                  <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(h.currentPrice)}</td>
                  <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(h.investedAmount)}</td>
                  <td className="px-4 py-3 text-right font-medium text-slate-800">{formatCurrency(h.currentValue)}</td>
                  <td className="px-4 py-3 text-right">
                    <span className={`font-medium ${h.gainLoss >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                      {formatCurrency(h.gainLoss)}
                    </span>
                    <p className={`text-xs ${h.gainLossPercent >= 0 ? 'text-green-500' : 'text-red-500'}`}>
                      {h.gainLossPercent >= 0 ? '+' : ''}{h.gainLossPercent.toFixed(2)}%
                    </p>
                  </td>
                </tr>
              ))}
              {holdings.length === 0 && (
                <tr>
                  <td colSpan={9} className="px-4 py-12 text-center text-slate-400">
                    No holdings yet. Add transactions to see your portfolio.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
