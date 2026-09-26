import { useEffect, useState } from 'react';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid } from 'recharts';
import { getActiveHoldings, getSoldPositions, getShortTermTrades, getOwners, getCurrencyRates } from '../api';
import { formatCurrency, formatPercent } from '../utils/formatters';
import ExportMenu from '../components/ExportMenu';
import SearchableSelect from '../components/SearchableSelect';
import { holdingsExportConfig, soldPositionsExportConfig } from '../utils/export/configs';
import type { Holding, SoldPosition, Currency, Owner, CurrencyRate } from '../types';
import { ASSET_TYPE_LABELS, ASSET_TYPE_COLORS } from '../types';

type Tab = 'holdings' | 'sold' | 'shortTerm';

/** Latest rate to convert `from` → `to` from the user's stored FX rates (direct, then inverse). */
function resolveRate(rates: CurrencyRate[], from: string, to: string): number | null {
  if (!from || !to) return null;
  if (from.toUpperCase() === to.toUpperCase()) return 1;
  const f = from.toUpperCase(), t = to.toUpperCase();
  const direct = rates.filter(r => r.fromCurrency.toUpperCase() === f && r.toCurrency.toUpperCase() === t)
    .sort((a, b) => b.effectiveDate.localeCompare(a.effectiveDate))[0];
  if (direct) return direct.rate;
  const inverse = rates.filter(r => r.fromCurrency.toUpperCase() === t && r.toCurrency.toUpperCase() === f)
    .sort((a, b) => b.effectiveDate.localeCompare(a.effectiveDate))[0];
  if (inverse && inverse.rate) return 1 / inverse.rate;
  return null;
}

export default function Portfolio() {
  const [tab, setTab] = useState<Tab>('holdings');
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [sold, setSold] = useState<SoldPosition[]>([]);
  const [shortTerm, setShortTerm] = useState<SoldPosition[]>([]);
  const [loading, setLoading] = useState(true);
  const [displayCurrency, setDisplayCurrency] = useState<Currency>('SGD');
  const [owners, setOwners] = useState<Owner[]>([]);
  const [fxRates, setFxRates] = useState<CurrencyRate[]>([]);
  const [filterOwner, setFilterOwner] = useState<number | undefined>();
  // Holdings-table column filters (client-side). Owner is handled globally, server-side.
  const [filterAssetId, setFilterAssetId] = useState<string>('');
  const [filterAccountId, setFilterAccountId] = useState<string>('');
  // Holdings-table sorting. Default: highest value first.
  type SortKey = 'asset' | 'type' | 'account' | 'owner' | 'quantity' | 'averageBuyPrice' | 'currentPrice' | 'investedAmount' | 'currentValue' | 'gainLoss';
  const [sortKey, setSortKey] = useState<SortKey>('currentValue');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');
  const toggleSort = (key: SortKey) => {
    if (sortKey === key) { setSortDir(d => (d === 'asc' ? 'desc' : 'asc')); }
    else { setSortKey(key); setSortDir(key === 'asset' || key === 'account' || key === 'owner' || key === 'type' ? 'asc' : 'desc'); }
  };

  useEffect(() => { getOwners().then(r => setOwners(r.data)).catch(console.error); }, []);
  useEffect(() => { loadData(); }, [filterOwner]);

  const loadData = async () => {
    try {
      // Holdings & sold positions filter server-side by owner; short-term trades have no owner
      // param on the API, so they're filtered client-side below.
      const [hRes, sRes, stRes, fxRes] = await Promise.all([getActiveHoldings(filterOwner), getSoldPositions(filterOwner), getShortTermTrades(), getCurrencyRates()]);
      setHoldings(hRes.data);
      setSold(sRes.data);
      setFxRates(fxRes.data);
      setShortTerm(filterOwner ? stRes.data.filter(sp => sp.owner?.id === filterOwner) : stRes.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;


  // Distinct assets / accounts present in the current holdings, for the filter dropdowns.
  const assetOptions = Array.from(new Map(holdings.filter(h => h.asset).map(h => [h.asset.id, h.asset])).values())
    .sort((a, b) => a.symbol.localeCompare(b.symbol));
  const accountOptions = Array.from(new Map(holdings.filter(h => h.account).map(h => [h.account.id, h.account])).values())
    .sort((a, b) => a.name.localeCompare(b.name));

  // Apply the client-side column filters (owner is already applied server-side).
  const filteredHoldings = holdings.filter(h => {
    if (filterAssetId && String(h.asset?.id) !== filterAssetId) return false;
    if (filterAccountId && String(h.account?.id) !== filterAccountId) return false;
    return true;
  });
  const holdingsFiltersActive = !!(filterAssetId || filterAccountId);
  const clearHoldingsFilters = () => { setFilterAssetId(''); setFilterAccountId(''); };

  const holdingsWithValue = filteredHoldings.map(h => {
    const currentPrice = h.asset.currentPrice || h.averageBuyPrice;
    const currentValue = h.quantity * currentPrice;
    const gainLoss = currentValue - h.investedAmount;
    const pct = h.investedAmount > 0 ? (gainLoss / h.investedAmount) * 100 : 0;
    // Convert money fields to the display currency (SGD/USD toggle) when a rate is available.
    // If no rate exists, fall back to showing the holding's own currency, unconverted.
    const rate = resolveRate(fxRates, h.currency, displayCurrency);
    const dispCurrency = rate != null ? displayCurrency : h.currency;
    const r = rate ?? 1;

    // ── FX P/L: the currency effect on the current value, in the display currency. ──
    // It's the difference between valuing the position at today's FX rate vs the rate paid at
    // purchase: qty * currentPrice * (todayRate - purchaseRate). Only meaningful when the holding's
    // currency differs from the display currency AND we know the purchase rate.
    //   - purchaseRate to the display ccy: if display == account ccy, it's averageBuyFxRate
    //     (stored trade->account rate); if display == holding ccy, there's no FX (rate 1).
    let fxPnl = 0;
    let fxPnlKnown = false;
    if (rate != null && h.currency.toUpperCase() !== displayCurrency.toUpperCase()) {
      const acctCcy = h.account?.currency;
      let purchaseRate: number | null = null;
      if (acctCcy && acctCcy.toUpperCase() === displayCurrency.toUpperCase()) {
        purchaseRate = h.averageBuyFxRate ?? null; // stored trade->account(display) rate at purchase
      }
      if (purchaseRate != null && purchaseRate > 0) {
        fxPnl = h.quantity * currentPrice * (r - purchaseRate);
        fxPnlKnown = true;
      }
    }

    return {
      ...h,
      currentPrice,
      dispCurrency,
      dispAvgPrice: h.averageBuyPrice * r,
      dispCurrentPrice: currentPrice * r,
      dispInvested: h.investedAmount * r,
      currentValue: currentValue * r,
      gainLoss: gainLoss * r,
      pct,
      fxPnl,
      fxPnlKnown,
    };
  });

  // Totals in the display currency (currentValue & dispInvested are already converted per row).
  const totalValue = holdingsWithValue.reduce((s, h) => s + h.currentValue, 0);
  const totalInvested = holdingsWithValue.reduce((s, h) => s + h.dispInvested, 0);

  // Sort a copy of the computed holdings by the active column/direction.
  const sortedHoldings = [...holdingsWithValue].sort((a, b) => {
    let av: number | string;
    let bv: number | string;
    switch (sortKey) {
      case 'asset': av = a.asset.symbol; bv = b.asset.symbol; break;
      case 'type': av = a.asset.assetType; bv = b.asset.assetType; break;
      case 'account': av = a.account?.name ?? ''; bv = b.account?.name ?? ''; break;
      case 'owner': av = a.owner?.name ?? ''; bv = b.owner?.name ?? ''; break;
      case 'quantity': av = a.quantity; bv = b.quantity; break;
      case 'averageBuyPrice': av = a.dispAvgPrice; bv = b.dispAvgPrice; break;
      case 'currentPrice': av = a.dispCurrentPrice; bv = b.dispCurrentPrice; break;
      case 'investedAmount': av = a.dispInvested; bv = b.dispInvested; break;
      case 'gainLoss': av = a.gainLoss; bv = b.gainLoss; break;
      case 'currentValue':
      default: av = a.currentValue; bv = b.currentValue; break;
    }
    const cmp = typeof av === 'string' ? av.localeCompare(bv as string) : (av as number) - (bv as number);
    return sortDir === 'asc' ? cmp : -cmp;
  });

  // Group by type
  const byType: Record<string, number> = {};
  holdingsWithValue.forEach(h => { byType[h.asset.assetType] = (byType[h.asset.assetType] || 0) + h.currentValue; });
  const pieData = Object.entries(byType).map(([k, v]) => ({ name: ASSET_TYPE_LABELS[k as keyof typeof ASSET_TYPE_LABELS] || k, value: v, color: ASSET_TYPE_COLORS[k as keyof typeof ASSET_TYPE_COLORS] || '#94a3b8' }));

  // Group by account/broker — how much value is held with each broker.
  const ACCOUNT_COLORS = ['#6366f1', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6', '#f97316', '#64748b'];
  const byAccount: Record<string, number> = {};
  holdingsWithValue.forEach(h => { const n = h.account?.name || 'Unknown'; byAccount[n] = (byAccount[n] || 0) + h.currentValue; });
  const accountPieData = Object.entries(byAccount)
    .sort((a, b) => b[1] - a[1])
    .map(([name, value], i) => ({ name, value, color: ACCOUNT_COLORS[i % ACCOUNT_COLORS.length] }));

  // Top holdings bar
  const topHoldings = [...holdingsWithValue].sort((a, b) => b.currentValue - a.currentValue).slice(0, 10).map(h => ({ name: h.asset.symbol, value: h.currentValue }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Portfolio</h1>
          <p className="text-slate-500 text-sm mt-1">Your current holdings, sold positions, and short-term trades</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="w-44">
            <SearchableSelect
              options={[{ value: '', label: 'All Owners' }, ...owners.map(o => ({ value: o.id, label: o.name, icon: o.name[0] }))]}
              value={filterOwner || ''}
              onChange={v => setFilterOwner(v ? Number(v) : undefined)}
              placeholder="All Owners"
            />
          </div>
          <div className="flex bg-white border border-slate-200 rounded-lg overflow-hidden">
            <button onClick={() => setDisplayCurrency('SGD')} className={`px-3 py-1.5 text-xs font-medium transition-colors ${displayCurrency === 'SGD' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>SGD</button>
            <button onClick={() => setDisplayCurrency('USD')} className={`px-3 py-1.5 text-xs font-medium transition-colors ${displayCurrency === 'USD' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>USD</button>
          </div>
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
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
              <h3 className="font-semibold text-slate-800 mb-4">Holdings by Type</h3>
              <ResponsiveContainer width="100%" height={260}>
                <PieChart><Pie data={pieData} cx="50%" cy="50%" innerRadius={50} outerRadius={90} dataKey="value" label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`} labelLine={false}>
                  {pieData.map((e, i) => <Cell key={i} fill={e.color} />)}
                </Pie><Tooltip formatter={(v) => formatCurrency(v as number, displayCurrency)} /></PieChart>
              </ResponsiveContainer>
            </div>
            <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
              <h3 className="font-semibold text-slate-800 mb-4">Holdings by Account</h3>
              <ResponsiveContainer width="100%" height={260}>
                <PieChart><Pie data={accountPieData} cx="50%" cy="50%" innerRadius={50} outerRadius={90} dataKey="value" label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`} labelLine={false}>
                  {accountPieData.map((e, i) => <Cell key={i} fill={e.color} />)}
                </Pie><Tooltip formatter={(v) => formatCurrency(v as number, displayCurrency)} /></PieChart>
              </ResponsiveContainer>
            </div>
            <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
              <h3 className="font-semibold text-slate-800 mb-4">Top Holdings</h3>
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={topHoldings} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis type="number" tickFormatter={v => `$${(v/1000).toFixed(0)}K`} stroke="#94a3b8" fontSize={12} />
                  <YAxis type="category" dataKey="name" stroke="#94a3b8" fontSize={11} width={60} />
                  <Tooltip formatter={(v) => formatCurrency(v as number, displayCurrency)} />
                  <Bar dataKey="value" fill="#6366f1" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* Holdings Table */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="p-4 border-b border-slate-200 flex justify-between items-center">
              <div>
                <h3 className="font-semibold text-slate-800">All Holdings ({filteredHoldings.length})</h3>
                <p className="text-sm text-slate-500">Total: {formatCurrency(totalValue, displayCurrency)} | Invested: {formatCurrency(totalInvested, displayCurrency)} | P&L: {formatCurrency(totalValue - totalInvested, displayCurrency)}</p>
              </div>
              <ExportMenu rows={filteredHoldings} config={holdingsExportConfig} />
            </div>
            {/* Column filters (owner is the global filter above) */}
            <div className="px-4 py-3 border-b border-slate-200 bg-slate-50/50 flex flex-wrap items-end gap-3">
              <div className="w-52">
                <label className="block text-[11px] font-medium text-slate-500 mb-1">Asset</label>
                <SearchableSelect
                  options={[{ value: '', label: 'All Assets' }, ...assetOptions.map(a => ({ value: a.id.toString(), label: `${a.symbol} - ${a.name}` }))]}
                  value={filterAssetId}
                  onChange={v => setFilterAssetId(v.toString())}
                  placeholder="All Assets"
                />
              </div>
              <div className="w-52">
                <label className="block text-[11px] font-medium text-slate-500 mb-1">Account</label>
                <SearchableSelect
                  options={[{ value: '', label: 'All Accounts' }, ...accountOptions.map(a => {
                    // Disambiguate accounts that share a name across owners (e.g. two "Tiger" accounts).
                    const dup = accountOptions.some(b => b.id !== a.id && b.name === a.name);
                    return { value: a.id.toString(), label: dup && a.owner?.name ? `${a.name} (${a.owner.name})` : a.name };
                  })]}
                  value={filterAccountId}
                  onChange={v => setFilterAccountId(v.toString())}
                  placeholder="All Accounts"
                />
              </div>
              {holdingsFiltersActive && (
                <button onClick={clearHoldingsFilters} className="px-3 py-2 text-sm font-medium text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">Clear</button>
              )}
              <p className="text-[11px] text-slate-400 ml-auto">Showing {filteredHoldings.length} of {holdings.length} holdings</p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    {(() => {
                      const arrow = (k: SortKey) => sortKey === k ? (sortDir === 'asc' ? ' ▲' : ' ▼') : '';
                      const cls = (align: 'left' | 'right') => `${align === 'left' ? 'text-left' : 'text-right'} px-4 py-3 font-medium text-slate-600 cursor-pointer select-none hover:text-slate-900`;
                      const H = ({ k, label, align = 'left' }: { k: SortKey; label: string; align?: 'left' | 'right' }) => (
                        <th className={cls(align)} onClick={() => toggleSort(k)}>{label}<span className="text-indigo-500">{arrow(k)}</span></th>
                      );
                      return (
                        <>
                          <H k="asset" label="Asset" />
                          <H k="type" label="Type" />
                          <H k="account" label="Account" />
                          <H k="owner" label="Owner" />
                          <th className="text-left px-4 py-3 font-medium text-slate-600">Purpose</th>
                          <H k="quantity" label="Qty" align="right" />
                          <H k="averageBuyPrice" label="Avg Price" align="right" />
                          <H k="currentPrice" label="Current" align="right" />
                          <H k="investedAmount" label="Invested" align="right" />
                          <H k="currentValue" label="Value" align="right" />
                          <H k="gainLoss" label="P&L" align="right" />
                          <th className="text-right px-4 py-3 font-medium text-slate-600" title="Currency gain/loss on the current value vs the FX rate at purchase">FX P/L</th>
                        </>
                      );
                    })()}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {sortedHoldings.map(h => (
                    <tr key={h.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3"><span className="font-medium text-slate-800">{h.asset.symbol}</span><p className="text-xs text-slate-400">{h.asset.name}</p></td>
                      <td className="px-4 py-3"><span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">{ASSET_TYPE_LABELS[h.asset.assetType] || h.asset.assetType}</span></td>
                      <td className="px-4 py-3 text-slate-600">{h.account.name}</td>
                      <td className="px-4 py-3 text-slate-500 text-xs">{h.owner?.name ?? '-'}</td>
                      <td className="px-4 py-3"><span className={`text-[10px] px-1.5 py-0.5 rounded ${h.purpose === 'TRADING' || h.purpose === 'SHORT_TERM' ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-600'}`}>{h.purpose ? h.purpose.replace(/_/g, ' ') : 'LONG TERM'}</span></td>
                      <td className="px-4 py-3 text-right text-slate-700">{h.quantity.toFixed(h.quantity < 1 ? 4 : 2)}</td>
                      <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(h.dispAvgPrice, h.dispCurrency)}</td>
                      <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(h.dispCurrentPrice, h.dispCurrency)}</td>
                      <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(h.dispInvested, h.dispCurrency)}</td>
                      <td className="px-4 py-3 text-right font-medium text-slate-800">{formatCurrency(h.currentValue, h.dispCurrency)}</td>
                      <td className="px-4 py-3 text-right">
                        <span className={`font-medium ${h.gainLoss >= 0 ? 'text-green-600' : 'text-red-600'}`}>{formatCurrency(h.gainLoss, h.dispCurrency)}</span>
                        <p className={`text-xs ${h.pct >= 0 ? 'text-green-500' : 'text-red-500'}`}>{formatPercent(h.pct)}</p>
                      </td>
                      <td className="px-4 py-3 text-right">
                        {h.fxPnlKnown
                          ? <span className={`font-medium ${h.fxPnl >= 0 ? 'text-green-600' : 'text-red-600'}`}>{formatCurrency(h.fxPnl, h.dispCurrency)}</span>
                          : <span className="text-slate-300" title="No purchase FX rate recorded — run Recompute P/L on the Transactions page or add the buy's FX rate">—</span>}
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
      <div className="p-4 border-b border-slate-200 flex justify-between items-center">
        <div>
          <h3 className="font-semibold text-slate-800">{title} ({data.length})</h3>
          <p className={`text-sm ${totalProfit >= 0 ? 'text-green-600' : 'text-red-600'}`}>Total Profit: {formatCurrency(totalProfit, 'USD')}</p>
        </div>
        <ExportMenu
          rows={data}
          config={{ ...soldPositionsExportConfig, entity: title.toLowerCase().replace(/\s+/g, '-'), title }}
        />
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
