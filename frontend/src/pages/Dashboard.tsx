import { useEffect, useState } from 'react';
import { PieChart, Pie, Cell, AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { TrendingUp, TrendingDown, Wallet, PiggyBank, BarChart3, Building2 } from 'lucide-react';
import { getDashboardSummary, getNetWorthHistory, takeSnapshot, DashboardSummary, NetWorthSnapshot } from '../api';

const COLORS = ['#6366f1', '#06b6d4', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'];
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

export default function Dashboard() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [history, setHistory] = useState<NetWorthSnapshot[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [summaryRes, historyRes] = await Promise.all([
        getDashboardSummary(),
        getNetWorthHistory(),
      ]);
      setSummary(summaryRes.data);
      setHistory(historyRes.data);
    } catch (err) {
      console.error('Failed to load dashboard', err);
    } finally {
      setLoading(false);
    }
  };

  const handleTakeSnapshot = async () => {
    try {
      await takeSnapshot();
      loadData();
    } catch (err) {
      console.error('Failed to take snapshot', err);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
      </div>
    );
  }

  const allocationData = summary?.allocationByType
    ? Object.entries(summary.allocationByType).map(([key, value]) => ({
        name: ASSET_LABELS[key] || key,
        value: value,
      }))
    : [];

  const chartData = [...history].reverse().map((s) => ({
    date: new Date(s.snapshotDate).toLocaleDateString('en-IN', { month: 'short', day: 'numeric' }),
    netWorth: s.totalNetWorth,
    equity: s.totalEquity,
    funds: s.totalIndexFund + s.totalMutualFund,
    crypto: s.totalCrypto,
    deposits: s.totalBankDeposit,
  }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Dashboard</h1>
          <p className="text-slate-500 text-sm mt-1">Your financial overview at a glance</p>
        </div>
        <button
          onClick={handleTakeSnapshot}
          className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 transition-colors"
        >
          Take Snapshot
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <div className="flex items-center gap-3 mb-3">
            <div className="p-2 bg-indigo-100 rounded-lg">
              <Wallet size={20} className="text-indigo-600" />
            </div>
            <span className="text-sm text-slate-500">Net Worth</span>
          </div>
          <p className="text-2xl font-bold text-slate-800">{formatCurrency(summary?.totalNetWorth || 0)}</p>
        </div>

        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <div className="flex items-center gap-3 mb-3">
            <div className="p-2 bg-cyan-100 rounded-lg">
              <PiggyBank size={20} className="text-cyan-600" />
            </div>
            <span className="text-sm text-slate-500">Total Invested</span>
          </div>
          <p className="text-2xl font-bold text-slate-800">{formatCurrency(summary?.totalInvested || 0)}</p>
        </div>

        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <div className="flex items-center gap-3 mb-3">
            <div className={`p-2 rounded-lg ${(summary?.totalGainLoss || 0) >= 0 ? 'bg-green-100' : 'bg-red-100'}`}>
              {(summary?.totalGainLoss || 0) >= 0 ? (
                <TrendingUp size={20} className="text-green-600" />
              ) : (
                <TrendingDown size={20} className="text-red-600" />
              )}
            </div>
            <span className="text-sm text-slate-500">Gain/Loss</span>
          </div>
          <p className={`text-2xl font-bold ${(summary?.totalGainLoss || 0) >= 0 ? 'text-green-600' : 'text-red-600'}`}>
            {formatCurrency(summary?.totalGainLoss || 0)}
          </p>
          <p className={`text-sm mt-1 ${(summary?.gainLossPercentage || 0) >= 0 ? 'text-green-500' : 'text-red-500'}`}>
            {(summary?.gainLossPercentage || 0) >= 0 ? '+' : ''}{summary?.gainLossPercentage?.toFixed(2)}%
          </p>
        </div>

        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <div className="flex items-center gap-3 mb-3">
            <div className="p-2 bg-amber-100 rounded-lg">
              <Building2 size={20} className="text-amber-600" />
            </div>
            <span className="text-sm text-slate-500">Accounts</span>
          </div>
          <p className="text-2xl font-bold text-slate-800">{summary?.totalAccounts || 0}</p>
          <p className="text-sm text-slate-500 mt-1">{summary?.totalHoldings || 0} holdings</p>
        </div>
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Net Worth Chart */}
        <div className="lg:col-span-2 bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
            <BarChart3 size={20} className="text-indigo-600" />
            Net Worth History
          </h3>
          {chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <AreaChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="date" stroke="#94a3b8" fontSize={12} />
                <YAxis stroke="#94a3b8" fontSize={12} tickFormatter={(v) => `₹${(v / 100000).toFixed(0)}L`} />
                <Tooltip
                  formatter={(value) => formatCurrency(value as number)}
                  contentStyle={{ borderRadius: '8px', border: '1px solid #e2e8f0' }}
                />
                <Area type="monotone" dataKey="netWorth" stroke="#6366f1" fill="#6366f1" fillOpacity={0.1} name="Net Worth" />
              </AreaChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex items-center justify-center h-64 text-slate-400">
              <p>Take a snapshot to start tracking net worth history</p>
            </div>
          )}
        </div>

        {/* Allocation Pie */}
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-lg font-semibold text-slate-800 mb-4">Asset Allocation</h3>
          {allocationData.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={allocationData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={100}
                  dataKey="value"
                  label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`}
                  labelLine={false}
                >
                  {allocationData.map((_, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={(value) => formatCurrency(value as number)} />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex items-center justify-center h-64 text-slate-400">
              <p>No holdings yet</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
