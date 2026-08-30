import { useEffect, useState } from 'react';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid } from 'recharts';
import { getActiveHoldings, getSoldPositions, getShortTermTrades } from '../api';
import { formatCurrency, formatPercent } from '../utils/formatters';
import ExportMenu from '../components/ExportMenu';
import { holdingsExportConfig } from '../utils/export/configs';
import type { Holding, SoldPosition, Currency } from '../types';
import { ASSET_TYPE_LABELS, ASSET_TYPE_COLORS } from '../types';

type Tab = 'holdings' | 'sold' | 'shortTerm';

export default function Portfolio() {
  const [tab, setTab] = useState<Tab>('holdings');
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [sold, setSold] = useState<SoldPosition[]>([]);
  const [shortTerm, setShortTerm] = useState<SoldPosition[]>([]);
  const [loading, setLoading] = useState(true);
  const [displayCurrency, setDisplayCurrency] = useState<Currency>('SGD');

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [hRes, sRes, stRes] = await Promise.all([getActiveHoldings(), getSoldPositions(), getShortTermTrades()]);
      setHoldings(hRes.data);
      setSold(sRes.data);
      setShortTerm(stRes.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;


  const holdingsWithValue = holdings.map(h => {
    const currentPrice = h.asset.currentPrice || h.averageBuyPrice;
    const currentValue = h.quantity * currentPrice;
    const gainLoss = currentValue - h.investedAmount;
    const pct = h.investedAmount > 0 ? (gainLoss / h.investedAmount) * 100 : 0;
    return { ...h, currentValue, gainLoss, pct, currentPrice };
  });

  const totalValue = holdingsWithValue.reduce((s, h) => s + h.currentValue, 0);
  const totalInvested = holdingsWithValue.reduce((s, h) => s + h.investedAmount, 0);

  // Group by type
  const byType: Record<string, number> = {};
  holdingsWithValue.forEach(h => { byType[h.asset.assetType] = (byType[h.asset.assetType] || 0) + h.currentValue; });
  const pieData = Object.entries(byType).map(([k, v]) => ({ name: ASSET_TYPE_LABELS[k as keyof typeof ASSET_TYPE_LABELS] || k, value: v, color: ASSET_TYPE_COLORS[k as keyof typeof ASSET_TYPE_COLORS] || '#94a3b8' }));

  // Top holdings bar
  const topHoldings = [...holdingsWithValue].sort((a, b) => b.currentValue - a.currentValue).slice(0, 10).map(h => ({ name: h.asset.symbol, value: h.currentValue }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Portfolio</h1>
          <p className="text-slate-500 text-sm mt-1">Your current holdings, sold positions, and short-term trades</p>
        </div>
        <div className="flex bg-white border border-slate-200 rounded-lg overflow-hidden">
          <button onClick={() => setDisplayCurrency('SGD')} className={`px-3 py-1.5 text-xs font-medium transition-colors ${displayCurrency === 'SGD' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>SGD</button>
          <button onClick={() => setDisplayCurrency('USD')} className={`px-3 py-1.5 text-xs font-medium transition-colors ${displayCurrency === 'USD' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>USD</button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-slate-100 rounded-lg p-1 w-fit">
        {(['holdings', 'sold', 'shortTerm'] as Tab[]).map(t => (
          <button key={t} onClick={() => setTab(t)} className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${tab === t ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'}`}>
            {t === 'holdings' ? `Holdings (${holdings.length})` : t === 'sold' ? `Sold (${sold.length})` : `Short-Term (${shortTerm.length})`}
          </button>
        ))}
      </div>

      {tab === 'holdings' && (
        <>
          {/* Charts */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
              <h3 className="font-semibold text-slate-800 mb-4">Holdings by Type</h3>
              <ResponsiveContainer width="100%" height={260}>
                <PieChart><Pie data={pieData} cx="50%" cy="50%" innerRadius={50} outerRadius={90} dataKey="value" label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`} labelLine={false}>
                  {pieData.map((e, i) => <Cell key={i} fill={e.color} />)}
                </Pie><Tooltip formatter={(v) => formatCurrency(v as number)} /></PieChart>
              </ResponsiveContainer>
            </div>
            <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
              <h3 className="font-semibold text-slate-800 mb-4">Top Holdings</h3>
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={topHoldings} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis type="number" tickFormatter={v => `$${(v/1000).toFixed(0)}K`} stroke="#94a3b8" fontSize={12} />
                  <YAxis type="category" dataKey="name" stroke="#94a3b8" fontSize={11} width={60} />
                  <Tooltip formatter={(v) => formatCurrency(v as number, 'USD')} />
                  <Bar dataKey="value" fill="#6366f1" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* Holdings Table */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="p-4 border-b border-slate-200 flex justify-between items-center">
              <div>
                <h3 className="font-semibold text-slate-800">All Holdings ({holdings.length})</h3>
                <p className="text-sm text-slate-500">Total: {formatCurrency(totalValue)} | Invested: {formatCurrency(totalInvested)} | P&L: {formatCurrency(totalValue - totalInvested)}</p>
              </div>
              <ExportMenu rows={holdings} config={holdingsExportConfig} />
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Asset</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Type</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Account</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Purpose</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Qty</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Avg Price</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Current</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Invested</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Value</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">P&L</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {holdingsWithValue.sort((a, b) => b.currentValue - a.currentValue).map(h => (
                    <tr key={h.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3"><span className="font-medium text-slate-800">{h.asset.symbol}</span><p className="text-xs text-slate-400">{h.asset.name}</p></td>
                      <td className="px-4 py-3"><span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">{ASSET_TYPE_LABELS[h.asset.assetType] || h.asset.assetType}</span></td>
                      <td className="px-4 py-3 text-slate-600">{h.account.name}</td>
                      <td className="px-4 py-3"><span className={`text-[10px] px-1.5 py-0.5 rounded ${h.purpose === 'TRADING' || h.purpose === 'SHORT_TERM' ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-600'}`}>{h.purpose ? h.purpose.replace(/_/g, ' ') : 'LONG TERM'}</span></td>
                      <td className="px-4 py-3 text-right text-slate-700">{h.quantity.toFixed(h.quantity < 1 ? 4 : 2)}</td>
                      <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(h.averageBuyPrice, h.currency)}</td>
                      <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(h.currentPrice, h.currency)}</td>
                      <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(h.investedAmount, h.currency)}</td>
                      <td className="px-4 py-3 text-right font-medium text-slate-800">{formatCurrency(h.currentValue, h.currency)}</td>
                      <td className="px-4 py-3 text-right">
                        <span className={`font-medium ${h.gainLoss >= 0 ? 'text-green-600' : 'text-red-600'}`}>{formatCurrency(h.gainLoss, h.currency)}</span>
                        <p className={`text-xs ${h.pct >= 0 ? 'text-green-500' : 'text-red-500'}`}>{formatPercent(h.pct)}</p>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {tab === 'sold' && <SoldTable data={sold} title="Sold Positions" />}
      {tab === 'shortTerm' && <SoldTable data={shortTerm} title="Short-Term Trades" />}
    </div>
  );
}

function SoldTable({ data, title }: { data: SoldPosition[]; title: string }) {
  const totalProfit = data.reduce((s, p) => s + p.profit, 0);
  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
      <div className="p-4 border-b border-slate-200">
        <h3 className="font-semibold text-slate-800">{title} ({data.length})</h3>
        <p className={`text-sm ${totalProfit >= 0 ? 'text-green-600' : 'text-red-600'}`}>Total Profit: {formatCurrency(totalProfit, 'USD')}</p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 border-b border-slate-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Asset</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Account</th>
              <th className="text-right px-4 py-3 font-medium text-slate-600">Qty</th>
              <th className="text-right px-4 py-3 font-medium text-slate-600">Buy Price</th>
              <th className="text-right px-4 py-3 font-medium text-slate-600">Sell Price</th>
              <th className="text-right px-4 py-3 font-medium text-slate-600">Profit</th>
              <th className="text-right px-4 py-3 font-medium text-slate-600">%</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Period</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Sold Date</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {data.map(p => (
              <tr key={p.id} className="hover:bg-slate-50">
                <td className="px-4 py-3 font-medium text-slate-800">{p.asset.symbol}</td>
                <td className="px-4 py-3 text-slate-600">{p.account.name}</td>
                <td className="px-4 py-3 text-right">{p.quantity}</td>
                <td className="px-4 py-3 text-right">{formatCurrency(p.buyPrice, p.currency)}</td>
                <td className="px-4 py-3 text-right">{formatCurrency(p.sellPrice, p.currency)}</td>
                <td className={`px-4 py-3 text-right font-medium ${p.profit >= 0 ? 'text-green-600' : 'text-red-600'}`}>{formatCurrency(p.profit, p.currency)}</td>
                <td className={`px-4 py-3 text-right ${p.profitPercentage >= 0 ? 'text-green-600' : 'text-red-600'}`}>{formatPercent(p.profitPercentage)}</td>
                <td className="px-4 py-3 text-slate-500 text-xs">{p.holdingPeriod}</td>
                <td className="px-4 py-3 text-slate-500">{p.soldDate}</td>
              </tr>
            ))}
            {data.length === 0 && <tr><td colSpan={9} className="px-4 py-12 text-center text-slate-400">No records</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  );
}
