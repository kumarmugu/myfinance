import { useEffect, useState } from 'react';
import { AreaChart, Area, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { Users } from 'lucide-react';
import { getNetWorthHistory, getTransactions, getActiveHoldings, getOwners, takeSnapshot } from '../api';
import { formatCurrency, formatPercent } from '../utils/formatters';
import type { NetWorthSnapshot, Transaction, Holding, Owner, Currency } from '../types';
import { ASSET_TYPE_LABELS, ASSET_TYPE_COLORS } from '../types';

export default function Reports() {
  const [history, setHistory] = useState<NetWorthSnapshot[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [owners, setOwners] = useState<Owner[]>([]);
  const [selectedOwner, setSelectedOwner] = useState<number | undefined>();
  const [displayCurrency, setDisplayCurrency] = useState<Currency>('SGD');
  const [loading, setLoading] = useState(true);

  useEffect(() => { loadOwners(); }, []);
  useEffect(() => { loadData(); }, [selectedOwner]);

  const loadOwners = async () => { try { setOwners((await getOwners()).data); } catch {} };

  const loadData = async () => {
    setLoading(true);
    try {
      const [histRes, txRes, holdRes] = await Promise.all([
        getNetWorthHistory(selectedOwner), getTransactions(selectedOwner), getActiveHoldings(selectedOwner)
      ]);
      setHistory(histRes.data); setTransactions(txRes.data); setHoldings(holdRes.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const cFactor = displayCurrency === 'USD' ? 1 / 1.35 : 1;
  const cSymbol = displayCurrency === 'USD' ? '$' : 'S$';
  const fmt = (v: number) => formatCurrency(v * cFactor, displayCurrency);

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  // Net worth chart
  const nwData = [...history].reverse().map(s => ({
    date: s.year ? `${s.year}` : new Date(s.snapshotDate).toLocaleDateString('en-SG', { month: 'short', year: '2-digit' }),
    total: s.totalNetWorth * cFactor,
    indexFund: s.totalIndexFund * cFactor,
    growthEquity: s.totalGrowthEquity * cFactor,
    savings: s.totalSavings * cFactor,
    crypto: s.totalCrypto * cFactor,
  }));

  // YoY growth
  const yoyData = nwData.length > 1 ? nwData.map((d, i) => {
    const prev = i > 0 ? nwData[i - 1].total : 0;
    const growth = prev > 0 ? d.total - prev : 0;
    const pct = prev > 0 ? ((d.total - prev) / prev) * 100 : 0;
    return { ...d, growth, pct };
  }).slice(1) : [];

  // Monthly flow
  const monthlyFlow: Record<string, { month: string; invested: number; withdrawn: number }> = {};
  transactions.forEach(tx => {
    const month = tx.transactionDate.substring(0, 7);
    if (!monthlyFlow[month]) monthlyFlow[month] = { month, invested: 0, withdrawn: 0 };
    if (tx.transactionType === 'BUY') monthlyFlow[month].invested += tx.totalAmount * cFactor;
    else if (tx.transactionType === 'SELL') monthlyFlow[month].withdrawn += tx.totalAmount * cFactor;
  });
  const flowData = Object.values(monthlyFlow).sort((a, b) => a.month.localeCompare(b.month)).slice(-12).map(d => ({
    ...d, month: new Date(d.month + '-01').toLocaleDateString('en-SG', { month: 'short', year: '2-digit' }),
  }));

  // Performance by type
  const typeSummary: Record<string, { invested: number; current: number }> = {};
  holdings.forEach(h => {
    const type = h.asset.assetType;
    if (!typeSummary[type]) typeSummary[type] = { invested: 0, current: 0 };
    typeSummary[type].invested += h.investedAmount;
    typeSummary[type].current += h.quantity * (h.asset.currentPrice || h.averageBuyPrice);
  });
  const typeData = Object.entries(typeSummary).map(([type, data]) => ({
    type: ASSET_TYPE_LABELS[type as keyof typeof ASSET_TYPE_LABELS] || type,
    invested: data.invested * cFactor, current: data.current * cFactor, gainLoss: (data.current - data.invested) * cFactor,
  })).sort((a, b) => b.current - a.current);

  // Performance by broker
  const brokerSummary: Record<string, { invested: number; current: number }> = {};
  holdings.forEach(h => {
    const name = h.account.name;
    if (!brokerSummary[name]) brokerSummary[name] = { invested: 0, current: 0 };
    brokerSummary[name].invested += h.investedAmount;
    brokerSummary[name].current += h.quantity * (h.asset.currentPrice || h.averageBuyPrice);
  });
  const brokerData = Object.entries(brokerSummary).map(([name, data]) => ({
    name, invested: data.invested * cFactor, current: data.current * cFactor, gainLoss: (data.current - data.invested) * cFactor,
    pct: data.invested > 0 ? ((data.current - data.invested) / data.invested) * 100 : 0,
  })).sort((a, b) => b.current - a.current);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div><h1 className="text-2xl font-bold text-slate-800">Reports</h1><p className="text-slate-500 text-sm mt-0.5">Financial reports and analytics</p></div>
        <div className="flex items-center gap-3">
          {/* Owner Filter */}
          <div className="flex items-center gap-2 bg-white border border-slate-200 rounded-lg px-3 py-1.5">
            <Users size={14} className="text-slate-400" />
            <select value={selectedOwner || ''} onChange={e => setSelectedOwner(e.target.value ? Number(e.target.value) : undefined)} className="text-sm border-none bg-transparent focus:outline-none text-slate-700">
              <option value="">All Owners</option>
              {owners.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
            </select>
          </div>
          {/* Currency Toggle */}
          <div className="flex bg-white border border-slate-200 rounded-lg overflow-hidden">
            <button onClick={() => setDisplayCurrency('SGD')} className={`px-3 py-1.5 text-xs font-medium transition-colors ${displayCurrency === 'SGD' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>SGD</button>
            <button onClick={() => setDisplayCurrency('USD')} className={`px-3 py-1.5 text-xs font-medium transition-colors ${displayCurrency === 'USD' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>USD</button>
          </div>
          <button onClick={() => { takeSnapshot(selectedOwner); loadData(); }} className="px-3 py-1.5 bg-indigo-600 text-white rounded-lg text-xs font-medium hover:bg-indigo-700">Snapshot</button>
        </div>
      </div>

      {/* Net Worth Over Time */}
      <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <h3 className="text-sm font-semibold text-slate-800 mb-3">Net Worth Over Time ({cSymbol})</h3>
        {nwData.length > 0 ? (
          <ResponsiveContainer width="100%" height={320}>
            <AreaChart data={nwData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="date" stroke="#94a3b8" fontSize={11} />
              <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `${cSymbol}${(v/1000).toFixed(0)}K`} />
              <Tooltip formatter={(v) => fmt(v as number)} /><Legend />
              <Area type="monotone" dataKey="indexFund" stackId="1" stroke={ASSET_TYPE_COLORS.INDEX_FUND} fill={ASSET_TYPE_COLORS.INDEX_FUND} fillOpacity={0.3} name="Index" />
              <Area type="monotone" dataKey="growthEquity" stackId="1" stroke={ASSET_TYPE_COLORS.GROWTH_EQUITY} fill={ASSET_TYPE_COLORS.GROWTH_EQUITY} fillOpacity={0.3} name="Growth" />
              <Area type="monotone" dataKey="savings" stackId="1" stroke={ASSET_TYPE_COLORS.SAVINGS} fill={ASSET_TYPE_COLORS.SAVINGS} fillOpacity={0.3} name="Savings" />
              <Area type="monotone" dataKey="crypto" stackId="1" stroke={ASSET_TYPE_COLORS.CRYPTO} fill={ASSET_TYPE_COLORS.CRYPTO} fillOpacity={0.3} name="Crypto" />
            </AreaChart>
          </ResponsiveContainer>
        ) : <div className="h-64 flex items-center justify-center text-sm text-slate-400">No snapshots</div>}
      </div>

      {/* YoY Growth */}
      {yoyData.length > 0 && (
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Year-over-Year Growth</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-2.5 font-medium text-slate-600">Period</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Net Worth</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Growth</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">%</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {yoyData.map(d => (
                  <tr key={d.date} className="hover:bg-slate-50">
                    <td className="px-4 py-2.5 font-medium text-slate-800">{d.date}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{fmt(d.total / cFactor)}</td>
                    <td className={`px-4 py-2.5 text-right font-medium ${d.growth >= 0 ? 'text-green-600' : 'text-red-600'}`}>{fmt(d.growth / cFactor)}</td>
                    <td className={`px-4 py-2.5 text-right font-medium ${d.pct >= 0 ? 'text-green-600' : 'text-red-600'}`}>{formatPercent(d.pct)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Monthly Flow */}
      {flowData.length > 0 && (
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Monthly Investment Flow ({cSymbol})</h3>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={flowData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="month" stroke="#94a3b8" fontSize={11} />
              <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `${cSymbol}${(v/1000).toFixed(0)}K`} />
              <Tooltip formatter={(v) => fmt(v as number)} /><Legend />
              <Bar dataKey="invested" fill="#10b981" name="Invested" radius={[4, 4, 0, 0]} />
              <Bar dataKey="withdrawn" fill="#ef4444" name="Withdrawn" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Performance by Broker */}
      {brokerData.length > 0 && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 border-b border-slate-200"><h3 className="font-semibold text-slate-800 text-sm">Performance by Broker ({cSymbol})</h3></div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-2.5 font-medium text-slate-600">Broker</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Invested</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Current</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">P&L</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">%</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {brokerData.map(row => (
                  <tr key={row.name} className="hover:bg-slate-50">
                    <td className="px-4 py-2.5 font-medium text-slate-800">{row.name}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{fmt(row.invested / cFactor)}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{fmt(row.current / cFactor)}</td>
                    <td className={`px-4 py-2.5 text-right font-medium ${row.gainLoss >= 0 ? 'text-green-600' : 'text-red-600'}`}>{fmt(row.gainLoss / cFactor)}</td>
                    <td className={`px-4 py-2.5 text-right font-medium ${row.pct >= 0 ? 'text-green-600' : 'text-red-600'}`}>{formatPercent(row.pct)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Performance by Type */}
      {typeData.length > 0 && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 border-b border-slate-200"><h3 className="font-semibold text-slate-800 text-sm">Performance by Asset Type ({cSymbol})</h3></div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-2.5 font-medium text-slate-600">Type</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Invested</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Current</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">P&L</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">%</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {typeData.map(row => {
                  const pct = row.invested > 0 ? ((row.current - row.invested) / row.invested) * 100 : 0;
                  return (
                    <tr key={row.type} className="hover:bg-slate-50">
                      <td className="px-4 py-2.5 font-medium text-slate-800">{row.type}</td>
                      <td className="px-4 py-2.5 text-right text-slate-700">{fmt(row.invested / cFactor)}</td>
                      <td className="px-4 py-2.5 text-right text-slate-700">{fmt(row.current / cFactor)}</td>
                      <td className={`px-4 py-2.5 text-right font-medium ${row.gainLoss >= 0 ? 'text-green-600' : 'text-red-600'}`}>{fmt(row.gainLoss / cFactor)}</td>
                      <td className={`px-4 py-2.5 text-right font-medium ${pct >= 0 ? 'text-green-600' : 'text-red-600'}`}>{formatPercent(pct)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
