import { useEffect, useState } from 'react';
import { PieChart, Pie, Cell, AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, BarChart, Bar } from 'recharts';
import { TrendingUp, TrendingDown, Wallet, PiggyBank, Building2, Target, Camera } from 'lucide-react';
import { getDashboardSummary, getNetWorthHistory, getActiveHoldings, takeSnapshot, getOwners } from '../api';
import { formatCurrency, formatPercent } from '../utils/formatters';
import SearchableSelect from '../components/SearchableSelect';
import type { DashboardSummary, NetWorthSnapshot, Holding, Owner, Currency } from '../types';
import { ASSET_TYPE_LABELS, ASSET_TYPE_COLORS, NET_WORTH_MODULE_LABELS, NET_WORTH_MODULE_COLORS } from '../types';

export default function Dashboard() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [history, setHistory] = useState<NetWorthSnapshot[]>([]);
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [owners, setOwners] = useState<Owner[]>([]);
  const [selectedOwner, setSelectedOwner] = useState<number | undefined>();
  const [displayCurrency, setDisplayCurrency] = useState<Currency>('SGD');
  const [loading, setLoading] = useState(true);

  useEffect(() => { loadOwners(); }, []);
  useEffect(() => { loadData(); }, [selectedOwner]);

  const loadOwners = async () => {
    try { setOwners((await getOwners()).data); }
    catch (err) { console.error(err); }
  };

  const loadData = async () => {
    setLoading(true);
    try {
      const [sumRes, histRes, holdRes] = await Promise.all([
        getDashboardSummary(selectedOwner), getNetWorthHistory(selectedOwner), getActiveHoldings(selectedOwner)
      ]);
      setSummary(sumRes.data);
      setHistory(histRes.data);
      setHoldings(holdRes.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleSnapshot = async () => {
    await takeSnapshot(selectedOwner);
    loadData();
  };

  // Currency conversion driven by the user's own FX rates (from the backend). No hardcoded factors.
  // displayRates maps a currency code -> factor to multiply a base-currency (SGD) amount by.
  const baseCurrency = (summary?.baseCurrency as Currency) || 'SGD';
  const displayRates = summary?.displayRates || {};
  const usdAvailable = displayRates['USD'] != null;
  // If USD is selected but the user has no SGD<->USD rate, fall back to the base currency.
  const effectiveCurrency: Currency = displayCurrency === 'USD' && !usdAvailable ? baseCurrency : displayCurrency;
  const cFactor = displayRates[effectiveCurrency] ?? 1;
  const fmt = (v: number) => formatCurrency(v * cFactor, effectiveCurrency);

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  // Allocation pie data
  const allocationData = summary?.allocationByType
    ? Object.entries(summary.allocationByType)
        .filter(([, v]) => v > 0)
        .map(([key, value]) => ({
          name: ASSET_TYPE_LABELS[key as keyof typeof ASSET_TYPE_LABELS] || NET_WORTH_MODULE_LABELS[key] || key,
          value: value * cFactor,
          color: ASSET_TYPE_COLORS[key as keyof typeof ASSET_TYPE_COLORS] || NET_WORTH_MODULE_COLORS[key] || '#94a3b8'
        }))
        .sort((a, b) => b.value - a.value)
    : [];

  // Net worth history chart
  const chartData = [...history].reverse().map(s => ({
    date: s.year ? `${s.year}` : new Date(s.snapshotDate).toLocaleDateString('en-SG', { month: 'short', year: '2-digit' }),
    total: s.totalNetWorth * cFactor,
  }));

  // Holdings P&L
  const holdingsWithPL = holdings.map(h => {
    const current = (h.asset.currentPrice || h.averageBuyPrice) * h.quantity;
    const gain = current - h.investedAmount;
    const pct = h.investedAmount > 0 ? (gain / h.investedAmount) * 100 : 0;
    return { ...h, currentValue: current, gain, pct };
  }).sort((a, b) => b.pct - a.pct);

  const topPerformers = holdingsWithPL.filter(h => h.pct > 0).slice(0, 5);
  const worstPerformers = [...holdingsWithPL].filter(h => h.pct < 0).sort((a, b) => a.pct - b.pct).slice(0, 5);

  // By account
  const byAccount: Record<string, number> = {};
  holdingsWithPL.forEach(h => { byAccount[h.account.name] = (byAccount[h.account.name] || 0) + h.currentValue; });
  const accountData = Object.entries(byAccount).map(([name, value]) => ({ name, value: value * cFactor })).sort((a, b) => b.value - a.value);

  return (
    <div className="space-y-6">
      {/* Header with controls */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Dashboard</h1>
          <p className="text-slate-500 text-sm mt-0.5">Your financial overview</p>
        </div>
        <div className="flex items-center gap-3">
          {/* Owner Selector */}
          <div className="w-44">
            <SearchableSelect
              options={[{ value: '', label: 'All Owners' }, ...owners.map(o => ({ value: o.id, label: o.name, icon: o.name[0] }))]}
              value={selectedOwner || ''}
              onChange={v => setSelectedOwner(v ? Number(v) : undefined)}
              placeholder="All Owners"
            />
          </div>
          {/* Currency Toggle */}
          <div className="flex bg-white border border-slate-200 rounded-lg overflow-hidden">
            <button onClick={() => setDisplayCurrency('SGD')} className={`px-3 py-1.5 text-xs font-medium transition-colors ${effectiveCurrency === 'SGD' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>SGD</button>
            <button
              onClick={() => usdAvailable && setDisplayCurrency('USD')}
              disabled={!usdAvailable}
              title={usdAvailable ? 'Show values in USD' : 'Add an SGD/USD rate in Currency Rates to enable USD'}
              className={`px-3 py-1.5 text-xs font-medium transition-colors ${effectiveCurrency === 'USD' ? 'bg-indigo-600 text-white' : usdAvailable ? 'text-slate-600 hover:bg-slate-50' : 'text-slate-300 cursor-not-allowed'}`}>USD</button>
          </div>
          {/* Snapshot */}
          <button onClick={handleSnapshot} className="flex items-center gap-1.5 px-3 py-1.5 bg-indigo-600 text-white rounded-lg text-xs font-medium hover:bg-indigo-700 transition-colors" title="Take a point-in-time snapshot of current net worth for historical tracking">
            <Camera size={14} /> Snapshot
          </button>
        </div>
      </div>

      {/* Compact Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <CompactCard icon={<Wallet size={16} className="text-indigo-600" />} label="Net Worth" value={fmt(summary?.totalNetWorth || 0)} />
        <CompactCard icon={<PiggyBank size={16} className="text-cyan-600" />} label="Invested" value={fmt(summary?.totalInvested || 0)} />
        <CompactCard
          icon={(summary?.totalGainLoss || 0) >= 0 ? <TrendingUp size={16} className="text-green-600" /> : <TrendingDown size={16} className="text-red-600" />}
          label="Gain/Loss"
          value={fmt(summary?.totalGainLoss || 0)}
          sub={formatPercent(summary?.gainLossPercentage || 0)}
          positive={(summary?.totalGainLoss || 0) >= 0}
        />
        <CompactCard icon={<Building2 size={16} className="text-amber-600" />} label="Accounts" value={`${summary?.totalAccounts || 0}`} sub={`${summary?.totalHoldings || 0} positions`} />
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Net Worth Chart */}
        <div className="lg:col-span-2 bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3 flex items-center gap-2">
            <Target size={16} className="text-indigo-600" /> Net Worth History ({effectiveCurrency})
          </h3>
          {chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="date" stroke="#94a3b8" fontSize={11} />
                <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `${displayCurrency === 'USD' ? '$' : 'S$'}${(v/1000).toFixed(0)}K`} />
                <Tooltip formatter={(v) => fmt(v as number)} />
                <Area type="monotone" dataKey="total" stroke="#6366f1" fill="#6366f1" fillOpacity={0.08} name="Net Worth" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          ) : <EmptyState message="Take a snapshot to track net worth over time" />}
        </div>

        {/* Allocation Pie */}
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Asset Allocation</h3>
          {allocationData.length > 0 ? (
            <ResponsiveContainer width="100%" height={260}>
              <PieChart>
                <Pie data={allocationData} cx="50%" cy="50%" innerRadius={50} outerRadius={85} dataKey="value" label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`} labelLine={false}>
                  {allocationData.map((entry, i) => <Cell key={i} fill={entry.color} />)}
                </Pie>
                <Tooltip formatter={(v) => fmt(v as number)} />
              </PieChart>
            </ResponsiveContainer>
          ) : <EmptyState message="No holdings" />}
        </div>
      </div>

      {/* Bottom Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* By Account */}
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">By Account ({effectiveCurrency})</h3>
          {accountData.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={accountData.slice(0, 8)} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis type="number" tickFormatter={v => `${displayCurrency === 'USD' ? '$' : 'S$'}${(v/1000).toFixed(0)}K`} stroke="#94a3b8" fontSize={11} />
                <YAxis type="category" dataKey="name" stroke="#94a3b8" fontSize={11} width={65} />
                <Tooltip formatter={(v) => fmt(v as number)} />
                <Bar dataKey="value" fill="#6366f1" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : <EmptyState message="No data" />}
        </div>

        {/* Top/Worst Performers */}
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Performance</h3>
          <div className="space-y-1.5">
            {topPerformers.length > 0 && <p className="text-[10px] uppercase font-semibold text-green-600 tracking-wider">Top Gainers</p>}
            {topPerformers.map(h => (
              <div key={h.id} className="flex items-center justify-between py-1">
                <div className="flex items-center gap-2">
                  <span className="font-medium text-slate-800 text-sm">{h.asset.symbol}</span>
                  <span className="text-[10px] text-slate-400 bg-slate-100 px-1.5 py-0.5 rounded">{h.account.name}</span>
                </div>
                <span className="text-sm font-medium text-green-600">{formatPercent(h.pct)}</span>
              </div>
            ))}
            {worstPerformers.length > 0 && (
              <>
                <p className="text-[10px] uppercase font-semibold text-red-600 tracking-wider mt-3">Worst Performers</p>
                {worstPerformers.map(h => (
                  <div key={h.id} className="flex items-center justify-between py-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-slate-800 text-sm">{h.asset.symbol}</span>
                      <span className="text-[10px] text-slate-400 bg-slate-100 px-1.5 py-0.5 rounded">{h.account.name}</span>
                    </div>
                    <span className="text-sm font-medium text-red-600">{formatPercent(h.pct)}</span>
                  </div>
                ))}
              </>
            )}
            {topPerformers.length === 0 && worstPerformers.length === 0 && <EmptyState message="No holdings" />}
          </div>
        </div>
      </div>
    </div>
  );
}

function CompactCard({ icon, label, value, sub, positive }: { icon: React.ReactNode; label: string; value: string; sub?: string; positive?: boolean }) {
  return (
    <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
      <div className="flex items-center gap-2 mb-1.5">
        {icon}
        <span className="text-[11px] text-slate-500 uppercase tracking-wide">{label}</span>
      </div>
      <p className="text-lg font-bold text-slate-800 leading-tight">{value}</p>
      {sub && <p className={`text-xs mt-0.5 ${positive === true ? 'text-green-500' : positive === false ? 'text-red-500' : 'text-slate-500'}`}>{sub}</p>}
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return <div className="flex items-center justify-center h-40 text-sm text-slate-400">{message}</div>;
}
