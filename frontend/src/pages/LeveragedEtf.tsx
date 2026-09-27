import { useEffect, useState } from 'react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts';
import {
  Activity, Plus, Trash2, RefreshCw, Calculator, TrendingUp, ListChecks,
  Layers, LineChart as LineChartIcon, Bell, AlertTriangle, CheckCircle2, Package,
} from 'lucide-react';
import {
  levGetBenchmarks, levCreateBenchmark, levUpdateBenchmark, levDeleteBenchmark,
  levGetInstruments, levCreateInstrument, levUpdateInstrument, levDeleteInstrument,
  levGetStrategies, levGetStrategy, levCreateStrategy, levUpdateStrategy, levDeleteStrategy,
  levGetHistory, levRefreshBenchmark, levRefreshEtf, levAddManualBar,
  levGetPositions, levAddPosition, levDeletePosition,
  levCalculate, levGetSnapshots, levGetLatestSnapshot, levPreviewCurve,
  levGetPlans, levGeneratePlan, levApprovePlan, levRejectPlan, levExecutePlan, levCancelPlan,
  levGetBacktests, levGetBacktest, levRunBacktest,
  levGetAlertPrefs, levCreateAlertPref, levDeleteAlertPref,
  levGetNotifications, levMarkNotificationRead, levMarkAllNotificationsRead,
} from '../api';
import { formatCurrency, formatDate } from '../utils/formatters';
import { useToast } from '../contexts/ToastContext';
import {
  ALLOCATION_MODE_LABELS, REFERENCE_HIGH_MODE_LABELS, REBALANCE_ACTION_LABELS,
} from '../types';
import type {
  BenchmarkIndex, LevEtfInstrument, LevEtfStrategy, MarketDataBar, LevEtfPosition,
  AllocationSnapshot, RebalancePlan, LevEtfAlertPref, LevEtfNotification, LevEtfBacktest,
  AllocationMode, ReferenceHighMode, PortfolioScopeType, AlertTrigger, DataQuality,
} from '../types';

// ── Constants ─────────────────────────────────────────────────────────────
const CURRENCY_OPTIONS = ['SGD', 'USD', 'EUR', 'GBP', 'JPY', 'HKD', 'AUD', 'CNY', 'INR', 'CHF', 'CAD'];
const REBALANCE_FREQUENCIES = ['DAILY', 'WEEKLY', 'MONTHLY'];
const ALERT_TRIGGERS: AlertTrigger[] = ['DRAWDOWN_THRESHOLD', 'TARGET_CHANGE', 'GAP_EXCEEDS', 'NEW_HIGH', 'STALE_DATA', 'PLAN_PENDING'];
const ALLOCATION_MODES: AllocationMode[] = ['INITIAL_PLUS_HALF_DRAWDOWN', 'DRAWDOWN_ONLY_WITH_MIN', 'LADDER'];
const REFERENCE_HIGH_MODES: ReferenceHighMode[] = ['ALL_TIME', 'ROLLING_52_WEEK', 'CUSTOM_START_DATE', 'MANUAL'];
const PORTFOLIO_SCOPES: PortfolioScopeType[] = ['WHOLE', 'ACCOUNT', 'MANUAL'];

type TabKey = 'overview' | 'index' | 'instruments' | 'strategy' | 'holdings' | 'backtesting' | 'notifications';
const TABS: { key: TabKey; label: string; icon: React.ReactNode }[] = [
  { key: 'overview', label: 'Overview', icon: <Activity size={15} /> },
  { key: 'index', label: 'Index Monitor', icon: <TrendingUp size={15} /> },
  { key: 'instruments', label: 'Instruments', icon: <Package size={15} /> },
  { key: 'strategy', label: 'Strategy', icon: <Layers size={15} /> },
  { key: 'holdings', label: 'Holdings & Rebalance', icon: <ListChecks size={15} /> },
  { key: 'backtesting', label: 'Backtesting', icon: <LineChartIcon size={15} /> },
  { key: 'notifications', label: 'Notifications', icon: <Bell size={15} /> },
];

const errMsg = (err: any) => err?.response?.data?.message || 'Something went wrong';

// ── Small shared UI ─────────────────────────────────────────────────────────
const inputCls = 'w-full border border-slate-300 rounded-lg px-3 py-2 text-sm';
const btnPrimary = 'flex items-center gap-1.5 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700';
const btnGhost = 'px-3 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-200';

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-xs font-medium text-slate-600 mb-1">{label}</label>
      {children}
    </div>
  );
}

function Card({ title, children }: { title?: string; children: React.ReactNode }) {
  return (
    <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
      {title && <h3 className="text-sm font-semibold text-slate-800 mb-3">{title}</h3>}
      {children}
    </div>
  );
}

function StatCard({ label, value, tone }: { label: string; value: string; tone?: 'pos' | 'neg' }) {
  return (
    <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
      <p className="text-[11px] text-slate-500 uppercase tracking-wide">{label}</p>
      <p className={`text-lg font-bold mt-1 ${tone === 'pos' ? 'text-green-600' : tone === 'neg' ? 'text-red-600' : 'text-slate-800'}`}>{value}</p>
    </div>
  );
}

function QualityBadge({ quality, blocked }: { quality?: DataQuality | null; blocked?: string | null }) {
  const q = blocked ? 'INVALID' : (quality || 'MISSING');
  const styles: Record<string, string> = {
    OK: 'bg-green-100 text-green-700',
    STALE: 'bg-amber-100 text-amber-700',
    INVALID: 'bg-red-100 text-red-700',
    MISSING: 'bg-red-100 text-red-700',
  };
  return <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${styles[q] || 'bg-slate-100 text-slate-600'}`}>{q}</span>;
}

const pct = (v?: number | null) => (v == null ? '-' : `${v.toFixed(2)}%`);

// ═══════════════════════════════════════════════════════════════════════════
export default function LeveragedEtf() {
  const { showToast } = useToast();
  const [tab, setTab] = useState<TabKey>('overview');

  // Shared reference data (loaded on mount)
  const [strategies, setStrategies] = useState<LevEtfStrategy[]>([]);
  const [benchmarks, setBenchmarks] = useState<BenchmarkIndex[]>([]);
  const [instruments, setInstruments] = useState<LevEtfInstrument[]>([]);

  const loadStrategies = async () => {
    try { const r = await levGetStrategies(); setStrategies(r.data); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };
  const loadBenchmarks = async () => {
    try { const r = await levGetBenchmarks(); setBenchmarks(r.data); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };
  const loadInstruments = async () => {
    try { const r = await levGetInstruments(); setInstruments(r.data); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  useEffect(() => { loadStrategies(); loadBenchmarks(); loadInstruments(); }, []);

  const benchmarkName = (id?: number | null) => benchmarks.find(b => b.id === id)?.symbol || '-';
  const instrumentName = (id?: number | null) => instruments.find(i => i.id === id)?.symbol || '-';

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Leveraged ETF Allocation Planner</h1>
        <p className="text-slate-500 text-sm mt-0.5">
          Plan how much of your portfolio to allocate to a leveraged ETF based on index drawdown. Plans are advisory only — nothing here places a broker order.
        </p>
      </div>

      {/* Tab bar */}
      <div className="flex flex-wrap gap-1 bg-white border border-slate-200 rounded-lg p-1 shadow-sm">
        {TABS.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${tab === t.key ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>
            {t.icon} {t.label}
          </button>
        ))}
      </div>

      {tab === 'overview' && <OverviewTab strategies={strategies} showToast={showToast} />}
      {tab === 'index' && <IndexMonitorTab benchmarks={benchmarks} reload={loadBenchmarks} showToast={showToast} />}
      {tab === 'instruments' && <InstrumentsTab instruments={instruments} benchmarks={benchmarks} reload={loadInstruments} showToast={showToast} />}
      {tab === 'strategy' && (
        <StrategyTab
          strategies={strategies} benchmarks={benchmarks} instruments={instruments}
          reload={loadStrategies} showToast={showToast}
          benchmarkName={benchmarkName} instrumentName={instrumentName}
        />
      )}
      {tab === 'holdings' && <HoldingsTab strategies={strategies} instruments={instruments} showToast={showToast} />}
      {tab === 'backtesting' && <BacktestingTab strategies={strategies} showToast={showToast} />}
      {tab === 'notifications' && <NotificationsTab showToast={showToast} />}
    </div>
  );
}

type ShowToast = (message: string, type?: 'success' | 'error' | 'info') => void;

// ─── 1. OVERVIEW ────────────────────────────────────────────────────────────
function OverviewTab({ strategies, showToast }: { strategies: LevEtfStrategy[]; showToast: ShowToast }) {
  const [strategyId, setStrategyId] = useState<number>(0);
  const [latest, setLatest] = useState<AllocationSnapshot | null>(null);
  const [snapshots, setSnapshots] = useState<AllocationSnapshot[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!strategyId && strategies.length > 0) setStrategyId(strategies[0].id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [strategies]);

  useEffect(() => { if (strategyId) loadSnapshots(); }, [strategyId]);

  const loadSnapshots = async () => {
    try {
      const [latestRes, listRes] = await Promise.all([
        levGetLatestSnapshot(strategyId), levGetSnapshots(strategyId),
      ]);
      // A 204 No Content resolves with empty data.
      setLatest(latestRes.data && (latestRes.data as any).id ? latestRes.data : null);
      setSnapshots(listRes.data || []);
    } catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const recalculate = async () => {
    if (!strategyId) return;
    setBusy(true);
    try {
      await levCalculate(strategyId);
      await loadSnapshots();
      showToast('Recalculated allocation', 'success');
    } catch (err: any) { showToast(errMsg(err), 'error'); }
    finally { setBusy(false); }
  };

  const currency = strategies.find(s => s.id === strategyId)?.tradingCurrency || 'SGD';
  const chartData = [...snapshots]
    .filter(s => s.calculationTimestamp)
    .sort((a, b) => (a.calculationTimestamp! < b.calculationTimestamp! ? -1 : 1))
    .map(s => ({
      ts: formatDate(s.calculationTimestamp!),
      drawdown: s.drawdownPercent ?? null,
      target: s.targetAllocationPercent ?? null,
    }));

  return (
    <div className="space-y-6">
      <Card>
        <Field label="Strategy">
          <select value={strategyId} onChange={e => setStrategyId(Number(e.target.value))} className={inputCls}>
            <option value={0}>Select a strategy…</option>
            {strategies.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </Field>
        {strategyId > 0 && (
          <button onClick={recalculate} disabled={busy} className={`${btnPrimary} mt-4`}>
            <Calculator size={15} /> {busy ? 'Recalculating…' : 'Recalculate'}
          </button>
        )}
      </Card>

      {strategyId > 0 && latest?.blockedReason && (
        <div className="flex items-start gap-2 bg-amber-50 border border-amber-200 text-amber-800 rounded-lg p-4 text-sm">
          <AlertTriangle size={18} className="shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold">Allocation blocked</p>
            <p>{latest.blockedReason}</p>
            <p className="text-xs mt-1 text-amber-700">No target is shown because the underlying data can't be trusted right now. Refresh the index/ETF history, then recalculate.</p>
          </div>
        </div>
      )}

      {strategyId > 0 && latest && !latest.blockedReason && (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
          <StatCard label="Drawdown" value={pct(latest.drawdownPercent)} tone={(latest.drawdownPercent ?? 0) < 0 ? 'neg' : undefined} />
          <StatCard label="Target Allocation" value={pct(latest.targetAllocationPercent)} />
          <StatCard label="Actual Allocation" value={pct(latest.actualAllocationPercent)} />
          <StatCard label="Portfolio Value" value={latest.portfolioValueBase != null ? formatCurrency(latest.portfolioValueBase, currency) : '-'} />
          <StatCard label="Rebalance Diff" value={latest.rebalanceDifferenceBase != null ? formatCurrency(latest.rebalanceDifferenceBase, currency) : '-'}
            tone={(latest.rebalanceDifferenceBase ?? 0) >= 0 ? 'pos' : 'neg'} />
          <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm flex flex-col justify-center gap-1">
            <p className="text-[11px] text-slate-500 uppercase tracking-wide">Data</p>
            <div><QualityBadge quality={latest.dataQuality} /></div>
          </div>
        </div>
      )}

      {strategyId > 0 && !latest && (
        <Card>
          <p className="text-sm text-slate-400">No snapshot yet. Click Recalculate to compute the first allocation snapshot.</p>
        </Card>
      )}

      {chartData.length > 0 && (
        <Card title="Snapshot History">
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="ts" stroke="#94a3b8" fontSize={11} />
              <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `${v}%`} />
              <Tooltip formatter={(v) => (v == null ? '-' : `${(v as number).toFixed(2)}%`)} />
              <Legend />
              <Line type="monotone" dataKey="drawdown" name="Drawdown %" stroke="#ef4444" dot={false} />
              <Line type="monotone" dataKey="target" name="Target Allocation %" stroke="#6366f1" dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </Card>
      )}
    </div>
  );
}

// ─── 2. INDEX MONITOR ───────────────────────────────────────────────────────
const emptyBenchmark: Partial<BenchmarkIndex> = { symbol: '', name: '', exchange: '', currency: 'USD', benchmarkType: '' };

function IndexMonitorTab({ benchmarks, reload, showToast }: { benchmarks: BenchmarkIndex[]; reload: () => void; showToast: ShowToast }) {
  const [form, setForm] = useState<Partial<BenchmarkIndex>>({ ...emptyBenchmark });
  const [editingId, setEditingId] = useState<number | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [selectedId, setSelectedId] = useState<number>(0);
  const [bars, setBars] = useState<MarketDataBar[]>([]);
  const [manual, setManual] = useState<{ date: string; close: string }>({ date: '', close: '' });

  useEffect(() => { if (selectedId) loadBars(selectedId); }, [selectedId]);

  const loadBars = async (id: number) => {
    try { const r = await levGetHistory('BENCHMARK', id); setBars(r.data); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const reset = () => { setForm({ ...emptyBenchmark }); setEditingId(null); setShowForm(false); };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingId) await levUpdateBenchmark(editingId, form);
      else await levCreateBenchmark(form);
      reset(); reload();
      showToast(editingId ? 'Benchmark updated' : 'Benchmark added', 'success');
    } catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const edit = (b: BenchmarkIndex) => {
    setEditingId(b.id);
    setForm({ symbol: b.symbol, name: b.name, exchange: b.exchange || '', currency: b.currency || 'USD', benchmarkType: b.benchmarkType || '' });
    setShowForm(true);
  };

  const remove = async (id: number) => {
    if (!confirm('Delete this benchmark?')) return;
    try { await levDeleteBenchmark(id); reload(); if (selectedId === id) setSelectedId(0); showToast('Benchmark deleted', 'success'); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const refresh = async (id: number) => {
    try { const r = await levRefreshBenchmark(id); showToast(r.data?.message || 'History refreshed', 'info'); if (selectedId === id) loadBars(id); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const addManual = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedId) return;
    try {
      await levAddManualBar('BENCHMARK', selectedId, { date: manual.date, close: parseFloat(manual.close) || 0 });
      setManual({ date: '', close: '' });
      loadBars(selectedId);
      showToast('Manual close added', 'success');
    } catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const latestBar = bars.length ? [...bars].sort((a, b) => (a.date < b.date ? 1 : -1))[0] : null;
  const barChart = [...bars].filter(b => b.close != null).sort((a, b) => (a.date < b.date ? -1 : 1)).map(b => ({ date: b.date, close: b.close }));

  return (
    <div className="space-y-6">
      <div className="flex justify-end">
        <button onClick={() => (showForm ? reset() : setShowForm(true))} className={btnPrimary}><Plus size={15} /> Add Benchmark</button>
      </div>

      {showForm && (
        <Card title={editingId ? 'Edit Benchmark' : 'Add Benchmark'}>
          <form onSubmit={submit} className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 gap-4">
            <Field label="Symbol *"><input className={inputCls} required value={form.symbol || ''} onChange={e => setForm({ ...form, symbol: e.target.value })} placeholder="^GSPC" /></Field>
            <Field label="Name *"><input className={inputCls} required value={form.name || ''} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="S&P 500" /></Field>
            <Field label="Exchange"><input className={inputCls} value={form.exchange || ''} onChange={e => setForm({ ...form, exchange: e.target.value })} /></Field>
            <Field label="Currency">
              <select className={inputCls} value={form.currency || 'USD'} onChange={e => setForm({ ...form, currency: e.target.value })}>
                {CURRENCY_OPTIONS.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </Field>
            <Field label="Type"><input className={inputCls} value={form.benchmarkType || ''} onChange={e => setForm({ ...form, benchmarkType: e.target.value })} placeholder="EQUITY_INDEX" /></Field>
            <div className="flex items-end gap-2">
              <button type="submit" className={btnPrimary}>{editingId ? 'Update' : 'Save'}</button>
              <button type="button" onClick={reset} className={btnGhost}>Cancel</button>
            </div>
          </form>
        </Card>
      )}

      <Card title="Benchmarks">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Symbol</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Name</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Exchange</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Ccy</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Type</th>
                <th className="px-3 py-2.5 w-40"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {benchmarks.map(b => (
                <tr key={b.id} className={`hover:bg-slate-50 ${selectedId === b.id ? 'bg-indigo-50/40' : ''}`}>
                  <td className="px-3 py-2.5 font-medium text-slate-800 cursor-pointer" onClick={() => setSelectedId(b.id)}>{b.symbol}</td>
                  <td className="px-3 py-2.5 text-slate-600">{b.name}</td>
                  <td className="px-3 py-2.5 text-slate-600">{b.exchange || '-'}</td>
                  <td className="px-3 py-2.5 text-indigo-600">{b.currency || '-'}</td>
                  <td className="px-3 py-2.5 text-slate-600">{b.benchmarkType || '-'}</td>
                  <td className="px-3 py-2.5">
                    <div className="flex gap-2 justify-end">
                      <button onClick={() => setSelectedId(b.id)} className="text-xs text-indigo-600 hover:underline">View</button>
                      <button onClick={() => refresh(b.id)} className="text-slate-400 hover:text-indigo-600" title="Refresh history"><RefreshCw size={13} /></button>
                      <button onClick={() => edit(b)} className="text-xs text-slate-500 hover:underline">Edit</button>
                      <button onClick={() => remove(b.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {benchmarks.length === 0 && <tr><td colSpan={6} className="px-3 py-10 text-center text-slate-400">No benchmarks yet.</td></tr>}
            </tbody>
          </table>
        </div>
      </Card>

      {selectedId > 0 && (
        <Card title={`History — ${benchmarks.find(b => b.id === selectedId)?.symbol || ''}`}>
          <div className="flex flex-wrap items-center gap-3 mb-4">
            <button onClick={() => refresh(selectedId)} className={btnGhost}><RefreshCw size={13} className="inline mr-1" /> Refresh history</button>
            {latestBar && <span className="text-xs text-slate-500 flex items-center gap-2">Latest: {formatDate(latestBar.date)} <QualityBadge quality={latestBar.dataQuality} /></span>}
          </div>
          <form onSubmit={addManual} className="flex flex-wrap items-end gap-3 mb-4">
            <Field label="Date"><input type="date" className={inputCls} required value={manual.date} onChange={e => setManual({ ...manual, date: e.target.value })} /></Field>
            <Field label="Close"><input type="number" step="any" className={inputCls} required value={manual.close} onChange={e => setManual({ ...manual, close: e.target.value })} /></Field>
            <button type="submit" className={btnPrimary}><Plus size={14} /> Add manual close</button>
          </form>
          {barChart.length > 0 ? (
            <ResponsiveContainer width="100%" height={280}>
              <LineChart data={barChart}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="date" stroke="#94a3b8" fontSize={11} />
                <YAxis stroke="#94a3b8" fontSize={11} domain={['auto', 'auto']} />
                <Tooltip />
                <Line type="monotone" dataKey="close" name="Close" stroke="#6366f1" dot={false} />
              </LineChart>
            </ResponsiveContainer>
          ) : <p className="text-sm text-slate-400">No stored bars. Refresh history or add a manual close.</p>}
        </Card>
      )}
    </div>
  );
}

// ─── 3. INSTRUMENTS ─────────────────────────────────────────────────────────
const emptyInstrument: Partial<LevEtfInstrument> = {
  symbol: '', name: '', exchange: '', issuer: '', leverageMultiple: 2, tradingCurrency: 'USD', underlyingBenchmarkId: null,
};

function InstrumentsTab({ instruments, benchmarks, reload, showToast }: {
  instruments: LevEtfInstrument[]; benchmarks: BenchmarkIndex[]; reload: () => void; showToast: ShowToast;
}) {
  const [form, setForm] = useState<Partial<LevEtfInstrument>>({ ...emptyInstrument });
  const [editingId, setEditingId] = useState<number | null>(null);
  const [showForm, setShowForm] = useState(false);

  const reset = () => { setForm({ ...emptyInstrument }); setEditingId(null); setShowForm(false); };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingId) await levUpdateInstrument(editingId, form);
      else await levCreateInstrument(form);
      reset(); reload();
      showToast(editingId ? 'Instrument updated' : 'Instrument added', 'success');
    } catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const edit = (i: LevEtfInstrument) => {
    setEditingId(i.id);
    setForm({
      symbol: i.symbol, name: i.name, exchange: i.exchange || '', issuer: i.issuer || '',
      leverageMultiple: i.leverageMultiple ?? 2, tradingCurrency: i.tradingCurrency || 'USD',
      underlyingBenchmarkId: i.underlyingBenchmarkId ?? null,
    });
    setShowForm(true);
  };

  const remove = async (id: number) => {
    if (!confirm('Delete this instrument?')) return;
    try { await levDeleteInstrument(id); reload(); showToast('Instrument deleted', 'success'); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const refresh = async (id: number) => {
    try { const r = await levRefreshEtf(id); showToast(r.data?.message || 'History refreshed', 'info'); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-end">
        <button onClick={() => (showForm ? reset() : setShowForm(true))} className={btnPrimary}><Plus size={15} /> Add ETF</button>
      </div>

      {showForm && (
        <Card title={editingId ? 'Edit ETF' : 'Add ETF'}>
          <form onSubmit={submit} className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 gap-4">
            <Field label="Symbol *"><input className={inputCls} required value={form.symbol || ''} onChange={e => setForm({ ...form, symbol: e.target.value })} placeholder="SPXL" /></Field>
            <Field label="Name *"><input className={inputCls} required value={form.name || ''} onChange={e => setForm({ ...form, name: e.target.value })} /></Field>
            <Field label="Exchange"><input className={inputCls} value={form.exchange || ''} onChange={e => setForm({ ...form, exchange: e.target.value })} /></Field>
            <Field label="Issuer"><input className={inputCls} value={form.issuer || ''} onChange={e => setForm({ ...form, issuer: e.target.value })} /></Field>
            <Field label="Leverage Multiple"><input type="number" step="any" className={inputCls} value={form.leverageMultiple ?? ''} onChange={e => setForm({ ...form, leverageMultiple: e.target.value === '' ? null : parseFloat(e.target.value) })} placeholder="3" /></Field>
            <Field label="Trading Currency">
              <select className={inputCls} value={form.tradingCurrency || 'USD'} onChange={e => setForm({ ...form, tradingCurrency: e.target.value })}>
                {CURRENCY_OPTIONS.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </Field>
            <Field label="Underlying Benchmark">
              <select className={inputCls} value={form.underlyingBenchmarkId ?? 0} onChange={e => setForm({ ...form, underlyingBenchmarkId: Number(e.target.value) || null })}>
                <option value={0}>None</option>
                {benchmarks.map(b => <option key={b.id} value={b.id}>{b.symbol} — {b.name}</option>)}
              </select>
            </Field>
            <div className="flex items-end gap-2">
              <button type="submit" className={btnPrimary}>{editingId ? 'Update' : 'Save'}</button>
              <button type="button" onClick={reset} className={btnGhost}>Cancel</button>
            </div>
          </form>
        </Card>
      )}

      <Card title="Leveraged ETFs">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Symbol</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Name</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Issuer</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Leverage</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Ccy</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Underlying</th>
                <th className="px-3 py-2.5 w-40"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {instruments.map(i => (
                <tr key={i.id} className="hover:bg-slate-50">
                  <td className="px-3 py-2.5 font-medium text-slate-800">{i.symbol}</td>
                  <td className="px-3 py-2.5 text-slate-600">{i.name}</td>
                  <td className="px-3 py-2.5 text-slate-600">{i.issuer || '-'}</td>
                  <td className="px-3 py-2.5 text-right text-slate-700">{i.leverageMultiple != null ? `${i.leverageMultiple}x` : '-'}</td>
                  <td className="px-3 py-2.5 text-indigo-600">{i.tradingCurrency || '-'}</td>
                  <td className="px-3 py-2.5 text-slate-600">{benchmarks.find(b => b.id === i.underlyingBenchmarkId)?.symbol || '-'}</td>
                  <td className="px-3 py-2.5">
                    <div className="flex gap-2 justify-end">
                      <button onClick={() => refresh(i.id)} className="text-slate-400 hover:text-indigo-600" title="Refresh history"><RefreshCw size={13} /></button>
                      <button onClick={() => edit(i)} className="text-xs text-slate-500 hover:underline">Edit</button>
                      <button onClick={() => remove(i.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {instruments.length === 0 && <tr><td colSpan={7} className="px-3 py-10 text-center text-slate-400">No ETFs yet.</td></tr>}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

// ─── 4. STRATEGY ────────────────────────────────────────────────────────────
const emptyStrategy: Partial<LevEtfStrategy> = {
  name: '', description: '', benchmarkIndexId: null, etfInstrumentId: null,
  allocationMode: 'INITIAL_PLUS_HALF_DRAWDOWN', initialAllocationPercent: 20, drawdownMultiplier: 0.5,
  minimumAllocationPercent: 0, maximumAllocationEnabled: false, maximumAllocationPercent: 100,
  referenceHighMode: 'ALL_TIME', referenceHighValue: null, referenceHighDate: null,
  portfolioScope: 'WHOLE', scopeManualValue: null, rebalanceTolerancePercent: 5,
  rebalanceFrequency: 'MONTHLY', tradingCurrency: 'SGD',
};

function StrategyTab({ strategies, benchmarks, instruments, reload, showToast, benchmarkName, instrumentName }: {
  strategies: LevEtfStrategy[]; benchmarks: BenchmarkIndex[]; instruments: LevEtfInstrument[];
  reload: () => void; showToast: ShowToast;
  benchmarkName: (id?: number | null) => string; instrumentName: (id?: number | null) => string;
}) {
  const [form, setForm] = useState<Partial<LevEtfStrategy>>({ ...emptyStrategy });
  const [editingId, setEditingId] = useState<number | null>(null);
  const [curve, setCurve] = useState<{ drawdownPercent: number; targetAllocationPercent: number }[]>([]);

  const set = <K extends keyof LevEtfStrategy>(key: K, value: LevEtfStrategy[K]) => setForm(f => ({ ...f, [key]: value }));

  // Live preview: re-fetch the allocation curve when key shape-defining fields change.
  useEffect(() => {
    const handle = setTimeout(async () => {
      try { const r = await levPreviewCurve(form); setCurve(r.data || []); }
      catch { setCurve([]); }
    }, 400);
    return () => clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    form.allocationMode, form.initialAllocationPercent, form.drawdownMultiplier,
    form.minimumAllocationPercent, form.maximumAllocationEnabled, form.maximumAllocationPercent,
    form.ladderJson,
  ]);

  const reset = () => { setForm({ ...emptyStrategy }); setEditingId(null); };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingId) await levUpdateStrategy(editingId, form);
      else await levCreateStrategy(form);
      reset(); reload();
      showToast(editingId ? 'Strategy updated' : 'Strategy created', 'success');
    } catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const edit = async (id: number) => {
    try {
      const r = await levGetStrategy(id);
      setEditingId(id);
      setForm({ ...r.data });
    } catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const remove = async (id: number) => {
    if (!confirm('Archive this strategy?')) return;
    try { await levDeleteStrategy(id); reload(); if (editingId === id) reset(); showToast('Strategy archived', 'success'); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <Card title={editingId ? 'Edit Strategy' : 'Create Strategy'}>
        <form onSubmit={submit} className="space-y-5">
          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase mb-2">Basics</p>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Field label="Name *"><input className={inputCls} required value={form.name || ''} onChange={e => set('name', e.target.value)} /></Field>
              <Field label="Trading Currency">
                <select className={inputCls} value={form.tradingCurrency || 'SGD'} onChange={e => set('tradingCurrency', e.target.value)}>
                  {CURRENCY_OPTIONS.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </Field>
              <div className="md:col-span-2">
                <Field label="Description"><input className={inputCls} value={form.description || ''} onChange={e => set('description', e.target.value)} /></Field>
              </div>
              <Field label="Benchmark Index">
                <select className={inputCls} value={form.benchmarkIndexId ?? 0} onChange={e => set('benchmarkIndexId', Number(e.target.value) || null)}>
                  <option value={0}>Select…</option>
                  {benchmarks.map(b => <option key={b.id} value={b.id}>{b.symbol} — {b.name}</option>)}
                </select>
              </Field>
              <Field label="ETF Instrument">
                <select className={inputCls} value={form.etfInstrumentId ?? 0} onChange={e => set('etfInstrumentId', Number(e.target.value) || null)}>
                  <option value={0}>Select…</option>
                  {instruments.map(i => <option key={i.id} value={i.id}>{i.symbol} — {i.name}</option>)}
                </select>
              </Field>
            </div>
          </div>

          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase mb-2">Allocation rule</p>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Field label="Allocation Mode">
                <select className={inputCls} value={form.allocationMode || 'INITIAL_PLUS_HALF_DRAWDOWN'} onChange={e => set('allocationMode', e.target.value as AllocationMode)}>
                  {ALLOCATION_MODES.map(m => <option key={m} value={m}>{ALLOCATION_MODE_LABELS[m]}</option>)}
                </select>
              </Field>
              <Field label="Initial Allocation %"><input type="number" step="any" className={inputCls} value={form.initialAllocationPercent ?? ''} onChange={e => set('initialAllocationPercent', e.target.value === '' ? undefined : parseFloat(e.target.value))} /></Field>
              <Field label="Drawdown Multiplier"><input type="number" step="any" className={inputCls} value={form.drawdownMultiplier ?? ''} onChange={e => set('drawdownMultiplier', e.target.value === '' ? undefined : parseFloat(e.target.value))} /></Field>
              <Field label="Minimum Allocation %"><input type="number" step="any" className={inputCls} value={form.minimumAllocationPercent ?? ''} onChange={e => set('minimumAllocationPercent', e.target.value === '' ? undefined : parseFloat(e.target.value))} /></Field>
              <div>
                <label className="flex items-center gap-2 mb-1 cursor-pointer">
                  <input type="checkbox" className="rounded border-slate-300 text-indigo-600" checked={!!form.maximumAllocationEnabled} onChange={e => set('maximumAllocationEnabled', e.target.checked)} />
                  <span className="text-xs font-medium text-slate-600">Cap maximum allocation</span>
                </label>
                <input type="number" step="any" className={inputCls} disabled={!form.maximumAllocationEnabled} value={form.maximumAllocationPercent ?? ''} onChange={e => set('maximumAllocationPercent', e.target.value === '' ? undefined : parseFloat(e.target.value))} />
              </div>
              {form.allocationMode === 'LADDER' && (
                <div className="md:col-span-2">
                  <Field label="Ladder JSON"><textarea className={inputCls} rows={3} value={form.ladderJson || ''} onChange={e => set('ladderJson', e.target.value)} placeholder='[{"drawdown":10,"allocation":25}]' /></Field>
                </div>
              )}
            </div>
          </div>

          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase mb-2">Reference high</p>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Field label="Reference High Mode">
                <select className={inputCls} value={form.referenceHighMode || 'ALL_TIME'} onChange={e => set('referenceHighMode', e.target.value as ReferenceHighMode)}>
                  {REFERENCE_HIGH_MODES.map(m => <option key={m} value={m}>{REFERENCE_HIGH_MODE_LABELS[m]}</option>)}
                </select>
              </Field>
              {form.referenceHighMode === 'MANUAL' && (
                <Field label="Reference High Value"><input type="number" step="any" className={inputCls} value={form.referenceHighValue ?? ''} onChange={e => set('referenceHighValue', e.target.value === '' ? null : parseFloat(e.target.value))} /></Field>
              )}
              {form.referenceHighMode === 'CUSTOM_START_DATE' && (
                <Field label="Reference High Since"><input type="date" className={inputCls} value={form.referenceHighDate || ''} onChange={e => set('referenceHighDate', e.target.value || null)} /></Field>
              )}
            </div>
          </div>

          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase mb-2">Portfolio scope & rebalancing</p>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Field label="Portfolio Scope">
                <select className={inputCls} value={form.portfolioScope || 'WHOLE'} onChange={e => set('portfolioScope', e.target.value as PortfolioScopeType)}>
                  {PORTFOLIO_SCOPES.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </Field>
              {form.portfolioScope === 'MANUAL' && (
                <Field label="Manual Portfolio Value"><input type="number" step="any" className={inputCls} value={form.scopeManualValue ?? ''} onChange={e => set('scopeManualValue', e.target.value === '' ? null : parseFloat(e.target.value))} /></Field>
              )}
              <Field label="Rebalance Tolerance %"><input type="number" step="any" className={inputCls} value={form.rebalanceTolerancePercent ?? ''} onChange={e => set('rebalanceTolerancePercent', e.target.value === '' ? undefined : parseFloat(e.target.value))} /></Field>
              <Field label="Rebalance Frequency">
                <select className={inputCls} value={form.rebalanceFrequency || 'MONTHLY'} onChange={e => set('rebalanceFrequency', e.target.value)}>
                  {REBALANCE_FREQUENCIES.map(f => <option key={f} value={f}>{f}</option>)}
                </select>
              </Field>
            </div>
          </div>

          <div className="flex gap-2">
            <button type="submit" className={btnPrimary}>{editingId ? 'Update Strategy' : 'Create Strategy'}</button>
            {editingId && <button type="button" onClick={reset} className={btnGhost}>New</button>}
          </div>
        </form>
      </Card>

      <div className="space-y-6">
        <Card title="Allocation Curve Preview">
          <p className="text-xs text-slate-500 mb-3">Target allocation as the index falls from its reference high. This is a live preview of the rule shape, not live market data.</p>
          {curve.length > 0 ? (
            <ResponsiveContainer width="100%" height={260}>
              <LineChart data={curve}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="drawdownPercent" stroke="#94a3b8" fontSize={11} type="number" domain={[0, 100]} tickFormatter={v => `${v}%`} />
                <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `${v}%`} />
                <Tooltip formatter={(v) => `${(v as number).toFixed(2)}%`} labelFormatter={(l) => `Drawdown ${l}%`} />
                <Line type="monotone" dataKey="targetAllocationPercent" name="Target %" stroke="#6366f1" dot={false} />
              </LineChart>
            </ResponsiveContainer>
          ) : <p className="text-sm text-slate-400">Adjust the allocation rule to preview the curve.</p>}
        </Card>

        <Card title="Existing Strategies">
          <div className="divide-y divide-slate-100">
            {strategies.map(s => (
              <div key={s.id} className="flex items-center justify-between py-2.5">
                <div>
                  <p className="font-medium text-slate-800 text-sm">{s.name}</p>
                  <p className="text-[11px] text-slate-400">{benchmarkName(s.benchmarkIndexId)} → {instrumentName(s.etfInstrumentId)} · {ALLOCATION_MODE_LABELS[s.allocationMode || ''] || s.allocationMode || '-'}</p>
                </div>
                <div className="flex gap-2">
                  <button onClick={() => edit(s.id)} className="text-xs text-indigo-600 hover:underline">Edit</button>
                  <button onClick={() => remove(s.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
                </div>
              </div>
            ))}
            {strategies.length === 0 && <p className="py-6 text-center text-sm text-slate-400">No strategies yet.</p>}
          </div>
        </Card>
      </div>
    </div>
  );
}

// ─── 5. HOLDINGS & REBALANCE ────────────────────────────────────────────────
const emptyPosition = {
  etfInstrumentId: 0, quantity: '', averageCost: '', marketPrice: '', marketValue: '', tradingCurrency: 'USD', snapshotDate: '',
};

function HoldingsTab({ strategies, instruments, showToast }: {
  strategies: LevEtfStrategy[]; instruments: LevEtfInstrument[]; showToast: ShowToast;
}) {
  const [strategyId, setStrategyId] = useState<number>(0);
  const [positions, setPositions] = useState<LevEtfPosition[]>([]);
  const [plans, setPlans] = useState<RebalancePlan[]>([]);
  const [pos, setPos] = useState({ ...emptyPosition });
  const [execFor, setExecFor] = useState<number | null>(null);
  const [exec, setExec] = useState({ executedQuantity: '', executedPrice: '', fees: '', partial: false });

  useEffect(() => { if (!strategyId && strategies.length) setStrategyId(strategies[0].id); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [strategies]);
  useEffect(() => { if (strategyId) { loadPositions(); loadPlans(); } /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [strategyId]);

  const loadPositions = async () => {
    try { const r = await levGetPositions(strategyId); setPositions(r.data); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };
  const loadPlans = async () => {
    try { const r = await levGetPlans(strategyId); setPlans(r.data); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const addPos = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await levAddPosition(strategyId, {
        etfInstrumentId: pos.etfInstrumentId || null,
        quantity: pos.quantity === '' ? null : parseFloat(pos.quantity),
        averageCost: pos.averageCost === '' ? null : parseFloat(pos.averageCost),
        marketPrice: pos.marketPrice === '' ? null : parseFloat(pos.marketPrice),
        marketValue: pos.marketValue === '' ? null : parseFloat(pos.marketValue),
        tradingCurrency: pos.tradingCurrency,
        snapshotDate: pos.snapshotDate || null,
      });
      setPos({ ...emptyPosition });
      loadPositions();
      showToast('Position added', 'success');
    } catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const removePos = async (id: number) => {
    if (!confirm('Delete this position?')) return;
    try { await levDeletePosition(id); loadPositions(); showToast('Position deleted', 'success'); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const generate = async () => {
    try { await levGeneratePlan(strategyId); loadPlans(); showToast('Rebalance plan generated', 'success'); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const approve = async (id: number) => {
    try { await levApprovePlan(id); loadPlans(); showToast('Plan approved', 'success'); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };
  const reject = async (id: number) => {
    try { await levRejectPlan(id); loadPlans(); showToast('Plan rejected', 'info'); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };
  const cancel = async (id: number) => {
    try { await levCancelPlan(id); loadPlans(); showToast('Plan cancelled', 'info'); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };
  const submitExec = async (e: React.FormEvent) => {
    e.preventDefault();
    if (execFor == null) return;
    try {
      await levExecutePlan(execFor, {
        executedQuantity: exec.executedQuantity === '' ? undefined : parseFloat(exec.executedQuantity),
        executedPrice: exec.executedPrice === '' ? undefined : parseFloat(exec.executedPrice),
        fees: exec.fees === '' ? undefined : parseFloat(exec.fees),
        partial: exec.partial,
      });
      setExecFor(null); setExec({ executedQuantity: '', executedPrice: '', fees: '', partial: false });
      loadPlans();
      showToast('Plan marked executed', 'success');
    } catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const instrName = (id?: number | null) => instruments.find(i => i.id === id)?.symbol || '-';

  return (
    <div className="space-y-6">
      <Card>
        <Field label="Strategy">
          <select value={strategyId} onChange={e => setStrategyId(Number(e.target.value))} className={inputCls}>
            <option value={0}>Select a strategy…</option>
            {strategies.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </Field>
      </Card>

      {strategyId > 0 && (
        <>
          <Card title="Add Position">
            <form onSubmit={addPos} className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 gap-4">
              <Field label="ETF">
                <select className={inputCls} value={pos.etfInstrumentId} onChange={e => setPos({ ...pos, etfInstrumentId: Number(e.target.value) })}>
                  <option value={0}>Select…</option>
                  {instruments.map(i => <option key={i.id} value={i.id}>{i.symbol}</option>)}
                </select>
              </Field>
              <Field label="Quantity"><input type="number" step="any" className={inputCls} value={pos.quantity} onChange={e => setPos({ ...pos, quantity: e.target.value })} /></Field>
              <Field label="Average Cost"><input type="number" step="any" className={inputCls} value={pos.averageCost} onChange={e => setPos({ ...pos, averageCost: e.target.value })} /></Field>
              <Field label="Market Price"><input type="number" step="any" className={inputCls} value={pos.marketPrice} onChange={e => setPos({ ...pos, marketPrice: e.target.value })} /></Field>
              <Field label="Market Value"><input type="number" step="any" className={inputCls} value={pos.marketValue} onChange={e => setPos({ ...pos, marketValue: e.target.value })} /></Field>
              <Field label="Currency">
                <select className={inputCls} value={pos.tradingCurrency} onChange={e => setPos({ ...pos, tradingCurrency: e.target.value })}>
                  {CURRENCY_OPTIONS.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </Field>
              <Field label="Snapshot Date"><input type="date" className={inputCls} value={pos.snapshotDate} onChange={e => setPos({ ...pos, snapshotDate: e.target.value })} /></Field>
              <div className="flex items-end"><button type="submit" className={btnPrimary}><Plus size={14} /> Add</button></div>
            </form>
          </Card>

          <Card title="Positions">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-3 py-2.5 font-medium text-slate-600">ETF</th>
                    <th className="text-right px-3 py-2.5 font-medium text-slate-600">Qty</th>
                    <th className="text-right px-3 py-2.5 font-medium text-slate-600">Avg Cost</th>
                    <th className="text-right px-3 py-2.5 font-medium text-slate-600">Mkt Price</th>
                    <th className="text-right px-3 py-2.5 font-medium text-slate-600">Mkt Value</th>
                    <th className="text-left px-3 py-2.5 font-medium text-slate-600">Date</th>
                    <th className="px-3 py-2.5 w-12"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {positions.map(p => (
                    <tr key={p.id} className="hover:bg-slate-50">
                      <td className="px-3 py-2.5 font-medium text-slate-800">{instrName(p.etfInstrumentId)}</td>
                      <td className="px-3 py-2.5 text-right text-slate-700">{p.quantity ?? '-'}</td>
                      <td className="px-3 py-2.5 text-right text-slate-700">{p.averageCost != null ? formatCurrency(p.averageCost, p.tradingCurrency || 'USD') : '-'}</td>
                      <td className="px-3 py-2.5 text-right text-slate-700">{p.marketPrice != null ? formatCurrency(p.marketPrice, p.tradingCurrency || 'USD') : '-'}</td>
                      <td className="px-3 py-2.5 text-right font-medium text-slate-800">{p.marketValue != null ? formatCurrency(p.marketValue, p.tradingCurrency || 'USD') : '-'}</td>
                      <td className="px-3 py-2.5 text-slate-600 text-xs">{p.snapshotDate ? formatDate(p.snapshotDate) : '-'}</td>
                      <td className="px-3 py-2.5"><button onClick={() => removePos(p.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button></td>
                    </tr>
                  ))}
                  {positions.length === 0 && <tr><td colSpan={7} className="px-3 py-8 text-center text-slate-400">No positions.</td></tr>}
                </tbody>
              </table>
            </div>
          </Card>

          <Card title="Rebalance Plans">
            <div className="flex items-center justify-between mb-3">
              <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-1.5">Plans are advisory only. Approving or executing here records your own decision — it never places a broker order.</p>
              <button onClick={generate} className={btnPrimary}><Calculator size={14} /> Generate rebalance plan</button>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-3 py-2.5 font-medium text-slate-600">Action</th>
                    <th className="text-right px-3 py-2.5 font-medium text-slate-600">Est. Amount</th>
                    <th className="text-left px-3 py-2.5 font-medium text-slate-600">Status</th>
                    <th className="text-left px-3 py-2.5 font-medium text-slate-600">Reason</th>
                    <th className="text-left px-3 py-2.5 font-medium text-slate-600">Created</th>
                    <th className="px-3 py-2.5 w-64"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {plans.map(p => (
                    <tr key={p.id} className="hover:bg-slate-50 align-top">
                      <td className="px-3 py-2.5 font-medium text-slate-800">{REBALANCE_ACTION_LABELS[p.action || ''] || p.action || '-'}</td>
                      <td className="px-3 py-2.5 text-right text-slate-700">{p.estimatedAmount != null ? formatCurrency(p.estimatedAmount, p.currency || 'USD') : '-'}</td>
                      <td className="px-3 py-2.5"><span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-medium">{p.status || '-'}</span></td>
                      <td className="px-3 py-2.5 text-slate-600 text-xs max-w-xs">{p.reason || '-'}</td>
                      <td className="px-3 py-2.5 text-slate-600 text-xs">{p.createdAt ? formatDate(p.createdAt) : '-'}</td>
                      <td className="px-3 py-2.5">
                        <div className="flex flex-wrap gap-2 justify-end">
                          <button onClick={() => approve(p.id)} className="text-xs text-green-600 hover:underline">Approve</button>
                          <button onClick={() => reject(p.id)} className="text-xs text-amber-600 hover:underline">Reject</button>
                          <button onClick={() => { setExecFor(p.id); setExec({ executedQuantity: '', executedPrice: '', fees: '', partial: false }); }} className="text-xs text-indigo-600 hover:underline">Execute</button>
                          <button onClick={() => cancel(p.id)} className="text-xs text-red-500 hover:underline">Cancel</button>
                        </div>
                        {execFor === p.id && (
                          <form onSubmit={submitExec} className="mt-2 grid grid-cols-2 gap-2 bg-slate-50 rounded-lg p-2 border border-slate-200">
                            <input type="number" step="any" placeholder="Qty" className={inputCls} value={exec.executedQuantity} onChange={e => setExec({ ...exec, executedQuantity: e.target.value })} />
                            <input type="number" step="any" placeholder="Price" className={inputCls} value={exec.executedPrice} onChange={e => setExec({ ...exec, executedPrice: e.target.value })} />
                            <input type="number" step="any" placeholder="Fees" className={inputCls} value={exec.fees} onChange={e => setExec({ ...exec, fees: e.target.value })} />
                            <label className="flex items-center gap-2 text-xs text-slate-600">
                              <input type="checkbox" className="rounded border-slate-300 text-indigo-600" checked={exec.partial} onChange={e => setExec({ ...exec, partial: e.target.checked })} /> Partial
                            </label>
                            <div className="col-span-2 flex gap-2">
                              <button type="submit" className={btnPrimary}>Confirm</button>
                              <button type="button" onClick={() => setExecFor(null)} className={btnGhost}>Cancel</button>
                            </div>
                          </form>
                        )}
                      </td>
                    </tr>
                  ))}
                  {plans.length === 0 && <tr><td colSpan={6} className="px-3 py-8 text-center text-slate-400">No rebalance plans.</td></tr>}
                </tbody>
              </table>
            </div>
          </Card>
        </>
      )}
    </div>
  );
}

// ─── 6. BACKTESTING ─────────────────────────────────────────────────────────
interface BacktestResults {
  initialValue?: number;
  finalValue?: number;
  totalReturnPercent?: number;
  maxDrawdownPercent?: number;
  dataPoints?: number;
  series?: { date: string; equity: number; indexClose?: number; etfClose?: number }[];
}

function BacktestingTab({ strategies, showToast }: { strategies: LevEtfStrategy[]; showToast: ShowToast }) {
  const [form, setForm] = useState({
    strategyId: 0, initialPortfolioValue: '100000', startDate: '', endDate: '',
    rebalanceFrequency: 'MONTHLY', feePercent: '0.1', slippagePercent: '0.05',
  });
  const [backtests, setBacktests] = useState<LevEtfBacktest[]>([]);
  const [selected, setSelected] = useState<LevEtfBacktest | null>(null);

  useEffect(() => { loadBacktests(); }, []);
  useEffect(() => { if (!form.strategyId && strategies.length) setForm(f => ({ ...f, strategyId: strategies[0].id })); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [strategies]);

  const loadBacktests = async () => {
    try { const r = await levGetBacktests(); setBacktests(r.data); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const run = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.strategyId) { showToast('Select a strategy first', 'error'); return; }
    try {
      await levRunBacktest({
        strategyId: form.strategyId,
        initialPortfolioValue: form.initialPortfolioValue === '' ? undefined : parseFloat(form.initialPortfolioValue),
        startDate: form.startDate || undefined,
        endDate: form.endDate || undefined,
        rebalanceFrequency: form.rebalanceFrequency,
        feePercent: form.feePercent === '' ? undefined : parseFloat(form.feePercent),
        slippagePercent: form.slippagePercent === '' ? undefined : parseFloat(form.slippagePercent),
      });
      loadBacktests();
      showToast('Backtest started', 'success');
    } catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const openBacktest = async (id: number) => {
    try { const r = await levGetBacktest(id); setSelected(r.data); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const strategyName = (id?: number) => strategies.find(s => s.id === id)?.name || `#${id}`;

  let results: BacktestResults | null = null;
  if (selected?.resultsJson) {
    try { results = JSON.parse(selected.resultsJson) as BacktestResults; }
    catch { results = null; }
  }
  const currency = strategies.find(s => s.id === selected?.strategyId)?.tradingCurrency || 'SGD';

  return (
    <div className="space-y-6">
      <Card title="Run Backtest">
        <form onSubmit={run} className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 gap-4">
          <Field label="Strategy">
            <select className={inputCls} value={form.strategyId} onChange={e => setForm({ ...form, strategyId: Number(e.target.value) })}>
              <option value={0}>Select…</option>
              {strategies.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="Initial Portfolio Value"><input type="number" step="any" className={inputCls} value={form.initialPortfolioValue} onChange={e => setForm({ ...form, initialPortfolioValue: e.target.value })} /></Field>
          <Field label="Start Date"><input type="date" className={inputCls} value={form.startDate} onChange={e => setForm({ ...form, startDate: e.target.value })} /></Field>
          <Field label="End Date"><input type="date" className={inputCls} value={form.endDate} onChange={e => setForm({ ...form, endDate: e.target.value })} /></Field>
          <Field label="Rebalance Frequency">
            <select className={inputCls} value={form.rebalanceFrequency} onChange={e => setForm({ ...form, rebalanceFrequency: e.target.value })}>
              {REBALANCE_FREQUENCIES.map(f => <option key={f} value={f}>{f}</option>)}
            </select>
          </Field>
          <Field label="Fee %"><input type="number" step="any" className={inputCls} value={form.feePercent} onChange={e => setForm({ ...form, feePercent: e.target.value })} /></Field>
          <Field label="Slippage %"><input type="number" step="any" className={inputCls} value={form.slippagePercent} onChange={e => setForm({ ...form, slippagePercent: e.target.value })} /></Field>
          <div className="flex items-end"><button type="submit" className={btnPrimary}><LineChartIcon size={14} /> Run Backtest</button></div>
        </form>
      </Card>

      <Card title="Past Backtests">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Strategy</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Period</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Status</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Created</th>
                <th className="px-3 py-2.5 w-16"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {backtests.map(b => (
                <tr key={b.id} className={`hover:bg-slate-50 ${selected?.id === b.id ? 'bg-indigo-50/40' : ''}`}>
                  <td className="px-3 py-2.5 font-medium text-slate-800">{strategyName(b.strategyId)}</td>
                  <td className="px-3 py-2.5 text-slate-600 text-xs">{b.startDate ? formatDate(b.startDate) : '?'} → {b.endDate ? formatDate(b.endDate) : '?'}</td>
                  <td className="px-3 py-2.5"><span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-medium">{b.status || '-'}</span></td>
                  <td className="px-3 py-2.5 text-slate-600 text-xs">{b.createdAt ? formatDate(b.createdAt) : '-'}</td>
                  <td className="px-3 py-2.5"><button onClick={() => openBacktest(b.id)} className="text-xs text-indigo-600 hover:underline">View</button></td>
                </tr>
              ))}
              {backtests.length === 0 && <tr><td colSpan={5} className="px-3 py-8 text-center text-slate-400">No backtests yet.</td></tr>}
            </tbody>
          </table>
        </div>
      </Card>

      {selected && (
        <Card title={`Backtest — ${strategyName(selected.strategyId)}`}>
          {selected.warnings && (
            <div className="flex items-start gap-2 bg-amber-50 border border-amber-200 text-amber-800 rounded-lg p-3 text-sm mb-4">
              <AlertTriangle size={16} className="shrink-0 mt-0.5" />
              <p>{selected.warnings}</p>
            </div>
          )}
          {results ? (
            <>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-5">
                <StatCard label="Final Value" value={results.finalValue != null ? formatCurrency(results.finalValue, currency) : '-'} />
                <StatCard label="Total Return" value={results.totalReturnPercent != null ? `${results.totalReturnPercent.toFixed(2)}%` : '-'}
                  tone={(results.totalReturnPercent ?? 0) >= 0 ? 'pos' : 'neg'} />
                <StatCard label="Max Drawdown" value={results.maxDrawdownPercent != null ? `${results.maxDrawdownPercent.toFixed(2)}%` : '-'} tone="neg" />
                <StatCard label="Data Points" value={results.dataPoints != null ? `${results.dataPoints}` : '-'} />
              </div>
              {results.series && results.series.length > 0 ? (
                <ResponsiveContainer width="100%" height={300}>
                  <LineChart data={results.series}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis dataKey="date" stroke="#94a3b8" fontSize={11} />
                    <YAxis stroke="#94a3b8" fontSize={11} />
                    <Tooltip formatter={(v) => formatCurrency(v as number, currency)} />
                    <Line type="monotone" dataKey="equity" name="Equity" stroke="#6366f1" dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              ) : <p className="text-sm text-slate-400">No equity series in results.</p>}
            </>
          ) : <p className="text-sm text-slate-400">Results not available yet (status: {selected.status || 'unknown'}).</p>}
        </Card>
      )}
    </div>
  );
}

// ─── 7. NOTIFICATIONS ───────────────────────────────────────────────────────
function NotificationsTab({ showToast }: { showToast: ShowToast }) {
  const [notifications, setNotifications] = useState<LevEtfNotification[]>([]);
  const [prefs, setPrefs] = useState<LevEtfAlertPref[]>([]);
  const [prefForm, setPrefForm] = useState<{ trigger: AlertTrigger; threshold: string; inAppEnabled: boolean; emailEnabled: boolean }>({
    trigger: 'DRAWDOWN_THRESHOLD', threshold: '', inAppEnabled: true, emailEnabled: false,
  });

  useEffect(() => { loadNotifications(); loadPrefs(); }, []);

  const loadNotifications = async () => {
    try { const r = await levGetNotifications(); setNotifications(r.data); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };
  const loadPrefs = async () => {
    try { const r = await levGetAlertPrefs(); setPrefs(r.data); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const markRead = async (id: number) => {
    try { await levMarkNotificationRead(id); loadNotifications(); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };
  const markAll = async () => {
    try { await levMarkAllNotificationsRead(); loadNotifications(); showToast('All marked read', 'success'); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const addPref = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await levCreateAlertPref({
        trigger: prefForm.trigger,
        threshold: prefForm.threshold === '' ? null : parseFloat(prefForm.threshold),
        inAppEnabled: prefForm.inAppEnabled,
        emailEnabled: prefForm.emailEnabled,
      });
      setPrefForm({ trigger: 'DRAWDOWN_THRESHOLD', threshold: '', inAppEnabled: true, emailEnabled: false });
      loadPrefs();
      showToast('Alert preference added', 'success');
    } catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  const removePref = async (id: number) => {
    if (!confirm('Delete this alert preference?')) return;
    try { await levDeleteAlertPref(id); loadPrefs(); showToast('Alert preference deleted', 'success'); }
    catch (err: any) { showToast(errMsg(err), 'error'); }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <Card>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-slate-800">Notifications</h3>
          <button onClick={markAll} className={btnGhost}><CheckCircle2 size={13} className="inline mr-1" /> Mark all read</button>
        </div>
        <div className="divide-y divide-slate-100">
          {notifications.map(n => (
            <button key={n.id} onClick={() => !n.readFlag && markRead(n.id)}
              className={`w-full text-left py-2.5 px-2 rounded-lg block ${n.readFlag ? 'text-slate-500' : 'bg-indigo-50/50 hover:bg-indigo-50'}`}>
              <div className="flex items-start justify-between gap-2">
                <p className={`text-sm ${n.readFlag ? '' : 'font-medium text-slate-800'}`}>{n.message || n.trigger || 'Notification'}</p>
                {!n.readFlag && <span className="text-[9px] px-1.5 py-0.5 rounded-full bg-indigo-600 text-white shrink-0">NEW</span>}
              </div>
              <p className="text-[11px] text-slate-400 mt-0.5">{n.createdAt ? formatDate(n.createdAt) : ''}{n.emailDeliveryStatus ? ` · email: ${n.emailDeliveryStatus}` : ''}</p>
            </button>
          ))}
          {notifications.length === 0 && <p className="py-6 text-center text-sm text-slate-400">No notifications.</p>}
        </div>
      </Card>

      <Card title="Alert Preferences">
        <p className="text-xs text-slate-500 mb-3">In-app notifications always work. Email is optional and is only delivered if your operator has configured SMTP.</p>
        <form onSubmit={addPref} className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-4">
          <Field label="Trigger">
            <select className={inputCls} value={prefForm.trigger} onChange={e => setPrefForm({ ...prefForm, trigger: e.target.value as AlertTrigger })}>
              {ALERT_TRIGGERS.map(t => <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>)}
            </select>
          </Field>
          <Field label="Threshold"><input type="number" step="any" className={inputCls} value={prefForm.threshold} onChange={e => setPrefForm({ ...prefForm, threshold: e.target.value })} /></Field>
          <label className="flex items-center gap-2 text-xs text-slate-600">
            <input type="checkbox" className="rounded border-slate-300 text-indigo-600" checked={prefForm.inAppEnabled} onChange={e => setPrefForm({ ...prefForm, inAppEnabled: e.target.checked })} /> In-app
          </label>
          <label className="flex items-center gap-2 text-xs text-slate-600">
            <input type="checkbox" className="rounded border-slate-300 text-indigo-600" checked={prefForm.emailEnabled} onChange={e => setPrefForm({ ...prefForm, emailEnabled: e.target.checked })} /> Email
          </label>
          <div className="md:col-span-2"><button type="submit" className={btnPrimary}><Plus size={14} /> Add preference</button></div>
        </form>
        <div className="divide-y divide-slate-100">
          {prefs.map(p => (
            <div key={p.id} className="flex items-center justify-between py-2.5">
              <div>
                <p className="text-sm font-medium text-slate-800">{p.trigger.replace(/_/g, ' ')}</p>
                <p className="text-[11px] text-slate-400">
                  {p.threshold != null ? `Threshold ${p.threshold}` : 'No threshold'} · {p.inAppEnabled ? 'in-app' : ''}{p.emailEnabled ? ' + email' : ''}
                </p>
              </div>
              <button onClick={() => removePref(p.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
            </div>
          ))}
          {prefs.length === 0 && <p className="py-6 text-center text-sm text-slate-400">No alert preferences.</p>}
        </div>
      </Card>
    </div>
  );
}
