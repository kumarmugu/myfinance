import { useEffect, useState } from 'react';
import { PieChart, Pie, Cell, AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, BarChart, Bar } from 'recharts';
import { TrendingUp, TrendingDown, Wallet, PiggyBank, Building2, Target } from 'lucide-react';
import { getDashboardSummary, getNetWorthHistory, getActiveHoldings, takeSnapshot } from '../api';
import { formatCurrency, formatPercent } from '../utils/formatters';
import type { DashboardSummary, NetWorthSnapshot, Holding } from '../types';
import { ASSET_TYPE_LABELS, ASSET_TYPE_COLORS } from '../types';

export default function Dashboard() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [history, setHistory] = useState<NetWorthSnapshot[]>([]);
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [sumRes, histRes, holdRes] = await Promise.all([
        getDashboardSummary(), getNetWorthHistory(), getActiveHoldings()
      ]);
      setSummary(sumRes.data);
      setHistory(histRes.data);
      setHoldings(holdRes.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleSnapshot = async () => { await takeSnapshot(); loadData(); };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  // Allocation pie data
  const allocationData = summary?.allocationByType
    ? Object.entries(summary.allocationByType)
        .filter(([, v]) => v > 0)
        .map(([key, value]) => ({ name: ASSET_TYPE_LABELS[key as keyof typeof ASSET_TYPE_LABELS] || key, value, color: ASSET_TYPE_COLORS[key as keyof typeof ASSET_TYPE_COLORS] || '#94a3b8' }))
        .sort((a, b) => b.value - a.value)
    : [];

  // Net worth history chart
  const chartData = [...history].reverse().map(s => ({
    date: s.year ? `${s.year}` : new Date(s.snapshotDate).toLocaleDateString('en-SG', { month: 'short', year: '2-digit' }),
    total: s.totalNetWorth,
    indexFund: s.totalIndexFund,
    growthEquity: s.totalGrowthEquity,
    savings: s.totalSavings,
  }));

  // Top performers from holdings
  const holdingsWithPL = holdings.map(h => {
    const current = (h.asset.currentPrice || h.averageBuyPrice) * h.quantity;
    const gain = current - h.investedAmount;
    const pct = h.investedAmount > 0 ? (gain / h.investedAmount) * 100 : 0;
    return { ...h, currentValue: current, gain, pct };
  }).sort((a, b) => b.pct - a.pct);

  const topPerformers = holdingsWithPL.slice(0, 5);
  const worstPerformers = [...holdingsWithPL].sort((a, b) => a.pct - b.pct).slice(0, 5);

  // By account
  const byAccount: Record<string, number> = {};
  holdingsWithPL.forEach(h => { byAccount[h.account.name] = (byAccount[h.account.name] || 0) + h.currentValue; });
  const accountData = Object.entries(byAccount).map(([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Dashboard</h1>
          <p className="text-slate-500 text-sm mt-1">Your financial overview at a glance</p>
        </div>
        <button onClick={handleSnapshot} className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 transition-colors">
          Take Snapshot
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <SummaryCard icon={<Wallet size={20} className="text-indigo-600" />} iconBg="bg-indigo-100" label="Net Worth" value={formatCurrency(summary?.totalNetWorth || 0)} />
        <SummaryCard icon={<PiggyBank size={20} className="text-cyan-600" />} iconBg="bg-cyan-100" label="Total Invested" value={formatCurrency(summary?.totalInvested || 0)} />
        <SummaryCard
          icon={(summary?.totalGainLoss || 0) >= 0 ? <TrendingUp size={20} className="text-green-600" /> : <TrendingDown size={20} className="text-red-600" />}
          iconBg={(summary?.totalGainLoss || 0) >= 0 ? 'bg-green-100' : 'bg-red-100'}
          label="Gain/Loss"
          value={formatCurrency(summary?.totalGainLoss || 0)}
          sub={formatPercent(summary?.gainLossPercentage || 0)}
          subColor={(summary?.gainLossPercentage || 0) >= 0 ? 'text-green-500' : 'text-red-500'}
        />
        <SummaryCard icon={<Building2 size={20} className="text-amber-600" />} iconBg="bg-amber-100" label="Accounts" value={`${summary?.totalAccounts || 0}`} sub={`${summary?.totalHoldings || 0} holdings`} />
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Net Worth Chart */}
        <div className="lg:col-span-2 bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
            <Target size={20} className="text-indigo-600" /> Net Worth History
          </h3>
          {chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <AreaChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="date" stroke="#94a3b8" fontSize={12} />
                <YAxis stroke="#94a3b8" fontSize={12} tickFormatter={v => `S$${(v/1000).toFixed(0)}K`} />
                <Tooltip formatter={(v) => formatCurrency(v as number)} />
                <Area type="monotone" dataKey="total" stroke="#6366f1" fill="#6366f1" fillOpacity={0.1} name="Total" />
              </AreaChart>
            </ResponsiveContainer>
          ) : <EmptyChart message="Take a snapshot to start tracking net worth" />}
        </div>

        {/* Allocation Pie */}
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-lg font-semibold text-slate-800 mb-4">Asset Allocation</h3>
          {allocationData.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie data={allocationData} cx="50%" cy="50%" innerRadius={55} outerRadius={95} dataKey="value" label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`} labelLine={false}>
                  {allocationData.map((entry, i) => <Cell key={i} fill={entry.color} />)}
                </Pie>
                <Tooltip formatter={(v) => formatCurrency(v as number)} />
              </PieChart>
            </ResponsiveContainer>
          ) : <EmptyChart message="No holdings yet" />}
        </div>
      </div>

      {/* Account breakdown + Performers */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* By Account */}
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-lg font-semibold text-slate-800 mb-4">By Account</h3>
          {accountData.length > 0 ? (
            <ResponsiveContainer width="100%" height={250}>
              <BarChart data={accountData} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis type="number" tickFormatter={v => `S$${(v/1000).toFixed(0)}K`} stroke="#94a3b8" fontSize={12} />
                <YAxis type="category" dataKey="name" stroke="#94a3b8" fontSize={12} width={70} />
                <Tooltip formatter={(v) => formatCurrency(v as number)} />
                <Bar dataKey="value" fill="#6366f1" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : <EmptyChart message="No data" />}
        </div>

        {/* Top Performers */}
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-lg font-semibold text-slate-800 mb-4">Top Performers</h3>
          <div className="space-y-2">
            {topPerformers.map(h => (
              <div key={h.id} className="flex items-center justify-between py-1.5">
                <div>
                  <span className="font-medium text-slate-800 text-sm">{h.asset.symbol}</span>
                  <span className="text-xs text-slate-400 ml-2">{h.account.name}</span>
                </div>
                <span className={`text-sm font-medium ${h.pct >= 0 ? 'text-green-600' : 'text-red-600'}`}>{formatPercent(h.pct)}</span>
              </div>
            ))}
            {topPerformers.length === 0 && <p className="text-sm text-slate-400">No holdings</p>}
          </div>
          {worstPerformers.length > 0 && worstPerformers[0].pct < 0 && (
            <>
              <h4 className="text-sm font-medium text-slate-500 mt-4 mb-2">Worst Performers</h4>
              {worstPerformers.filter(h => h.pct < 0).slice(0, 3).map(h => (
                <div key={h.id} className="flex items-center justify-between py-1.5">
                  <div>
                    <span className="font-medium text-slate-800 text-sm">{h.asset.symbol}</span>
                    <span className="text-xs text-slate-400 ml-2">{h.account.name}</span>
                  </div>
                  <span className="text-sm font-medium text-red-600">{formatPercent(h.pct)}</span>
                </div>
              ))}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function SummaryCard({ icon, iconBg, label, value, sub, subColor }: { icon: React.ReactNode; iconBg: string; label: string; value: string; sub?: string; subColor?: string }) {
  return (
    <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
      <div className="flex items-center gap-3 mb-3">
        <div className={`p-2 rounded-lg ${iconBg}`}>{icon}</div>
        <span className="text-sm text-slate-500">{label}</span>
      </div>
      <p className="text-2xl font-bold text-slate-800">{value}</p>
      {sub && <p className={`text-sm mt-1 ${subColor || 'text-slate-500'}`}>{sub}</p>}
    </div>
  );
}

function EmptyChart({ message }: { message: string }) {
  return <div className="flex items-center justify-center h-64 text-slate-400"><p>{message}</p></div>;
}
