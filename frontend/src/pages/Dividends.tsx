import { useEffect, useRef, useState } from 'react';
import { Plus, Trash2, Upload } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { getDividends, getDividendSummary, createDividend, deleteDividend, getAccounts, getOwners, importDividends, fetchIbkrDividends, getCurrencyRates, previewDividendBulkDelete, dividendBulkDelete } from '../api';
import { formatCurrency, formatDate } from '../utils/formatters';
import SearchableSelect from '../components/SearchableSelect';
import ExportMenu from '../components/ExportMenu';
import { dividendsExportConfig } from '../utils/export/configs';
import type { Dividend, Account, Owner, Currency, CurrencyRate } from '../types';
import { useToast } from '../contexts/ToastContext';
import { useAuth } from '../contexts/AuthContext';

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

export default function Dividends() {
  const [dividends, setDividends] = useState<Dividend[]>([]);
  const [summary, setSummary] = useState<Array<{ year: number; total: number }>>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const { showToast } = useToast();
  const [owners, setOwners] = useState<Owner[]>([]);
  const [filterOwner, setFilterOwner] = useState<string>('');
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [filterBroker, setFilterBroker] = useState<string>('');
  const [filterYear, setFilterYear] = useState<string>('');
  const [displayCurrency, setDisplayCurrency] = useState<Currency>('SGD');
  const [fxRates, setFxRates] = useState<CurrencyRate[]>([]);
  const { hasFeature } = useAuth();
  const canImportFile = hasFeature('DIVIDEND_IMPORT');
  const canIbkr = hasFeature('BROKER_SYNC') || hasFeature('IBKR_SYNC'); // IBKR_SYNC kept for back-compat
  const canImport = canImportFile || canIbkr; // the panel shows if either capability is enabled
  const [showImport, setShowImport] = useState(false);
  const [importAccountId, setImportAccountId] = useState(0);
  const [importOwnerId, setImportOwnerId] = useState(0);
  const [importing, setImporting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  // IBKR Flex fetch: token + query id are held only in component state for the request, never stored.
  // Default to whichever capability is available (file import if present, else IBKR).
  const [importMode, setImportMode] = useState<'file' | 'ibkr'>(canImportFile ? 'file' : 'ibkr');
  const [ibkrToken, setIbkrToken] = useState('');
  const [ibkrQueryId, setIbkrQueryId] = useState('');
  // Bulk cleanup: delete all dividends for a chosen owner+account (both required), password-confirmed.
  const [showBulk, setShowBulk] = useState(false);
  const [bulkOwnerId, setBulkOwnerId] = useState(0);
  const [bulkAccountId, setBulkAccountId] = useState(0);
  const [bulkCount, setBulkCount] = useState<number | null>(null);
  const [bulkPassword, setBulkPassword] = useState('');
  const [bulkBusy, setBulkBusy] = useState(false);
  // Client-side pagination for the records table.
  const PAGE_SIZE = 100;
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);

  const [form, setForm] = useState({ accountId: 0, ownerId: 0, instrument: '', amount: 0, currency: 'SGD' as Currency, receivedDate: new Date().toISOString().split('T')[0], year: new Date().getFullYear(), quarter: 'Q1', notes: '' });

  useEffect(() => { getOwners().then(r => setOwners(r.data)).catch(console.error); }, []);
  useEffect(() => { loadData(); }, [filterOwner]);
  // Reset the records table to the first page whenever the filters change.
  useEffect(() => { setVisibleCount(PAGE_SIZE); }, [filterBroker, filterYear, filterOwner]);

  const loadData = async () => {
    try {
      const ownerId = filterOwner ? Number(filterOwner) : undefined;
      const [divRes, sumRes, accRes, ownRes, fxRes] = await Promise.all([getDividends({ ownerId }), getDividendSummary(), getAccounts(), getOwners(), getCurrencyRates()]);
      setDividends(divRes.data);
      setSummary(sumRes.data.map(([year, total]: [number, number]) => ({ year, total })));
      setAccounts(accRes.data.filter(a => a.accountType === 'BROKER'));
      setOwners(ownRes.data);
      setFxRates(fxRes.data);
      if (ownRes.data.length > 0 && form.ownerId === 0) setForm(f => ({ ...f, ownerId: ownRes.data[0].id }));
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await createDividend({ account: { id: form.accountId } as Account, owner: form.ownerId ? { id: form.ownerId } as Owner : undefined, instrument: form.instrument, amount: form.amount, currency: form.currency, receivedDate: form.receivedDate, year: form.year, quarter: form.quarter, notes: form.notes });
      setShowForm(false);
      setForm({ accountId: 0, ownerId: 0, instrument: '', amount: 0, currency: 'SGD', receivedDate: new Date().toISOString().split('T')[0], year: new Date().getFullYear(), quarter: 'Q1', notes: '' });
      loadData();
    } catch (err) { console.error(err); showToast('Failed'); }
  };

  const handleDelete = async (id: number) => { if (confirm('Delete?')) { await deleteDividend(id); loadData(); } };

  const handleImport = async (file: File) => {
    if (!importAccountId) { showToast('Select a broker to import into', 'error'); return; }
    if (!importOwnerId) { showToast('Select an owner to import into', 'error'); return; }
    setImporting(true);
    try {
      const { data } = await importDividends(file, importAccountId, importOwnerId);
      showToast(`Imported ${data.imported} dividend${data.imported === 1 ? '' : 's'}${data.skipped ? `, skipped ${data.skipped} duplicate${data.skipped === 1 ? '' : 's'}` : ''}${data.assetsCreated ? ` (${data.assetsCreated} new asset${data.assetsCreated === 1 ? '' : 's'})` : ''}`, 'success');
      setShowImport(false);
      loadData();
    } catch (err) {
      console.error(err);
      showToast('Import failed — check the file format', 'error');
    } finally {
      setImporting(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleIbkrFetch = async () => {
    if (!importOwnerId) { showToast('Select an owner to import into', 'error'); return; }
    if (!importAccountId) { showToast('Select a broker to import into', 'error'); return; }
    setImporting(true);
    try {
      // Credentials are pulled from the account's stored config (Account page) — no token entry here.
      const { data } = await fetchIbkrDividends(importAccountId, importOwnerId);
      showToast(`Fetched ${data.imported} dividend${data.imported === 1 ? '' : 's'}${data.skipped ? `, skipped ${data.skipped} duplicate${data.skipped === 1 ? '' : 's'}` : ''}${data.assetsCreated ? ` (${data.assetsCreated} new asset${data.assetsCreated === 1 ? '' : 's'})` : ''}`, 'success');
      setShowImport(false);
      loadData();
    } catch (err: any) {
      console.error(err);
      showToast(err?.response?.data?.message || 'IBKR fetch failed — configure credentials on the Account page', 'error');
    } finally {
      setImporting(false);
    }
  };

  // Preview how many dividends would be deleted for the chosen owner+account.
  const handleBulkPreview = async () => {
    if (!bulkOwnerId || !bulkAccountId) { showToast('Select both an owner and an account', 'error'); return; }
    setBulkBusy(true); setBulkCount(null);
    try {
      const { data } = await previewDividendBulkDelete(bulkOwnerId, bulkAccountId);
      setBulkCount(data.dividends);
    } catch (err: any) {
      console.error(err);
      showToast(err?.response?.data?.message || 'Preview failed', 'error');
    } finally { setBulkBusy(false); }
  };

  const handleBulkDelete = async () => {
    if (!bulkOwnerId || !bulkAccountId) { showToast('Select both an owner and an account', 'error'); return; }
    if (!bulkPassword) { showToast('Enter your password to confirm', 'error'); return; }
    setBulkBusy(true);
    try {
      const { data } = await dividendBulkDelete(bulkOwnerId, bulkAccountId, bulkPassword);
      showToast(`Deleted ${data.deleted} dividend${data.deleted === 1 ? '' : 's'}`, 'success');
      setShowBulk(false); setBulkPassword(''); setBulkCount(null); setBulkOwnerId(0); setBulkAccountId(0);
      loadData();
    } catch (err: any) {
      console.error(err);
      showToast(err?.response?.status === 403 ? 'Incorrect password' : (err?.response?.data?.message || 'Delete failed'), 'error');
    } finally { setBulkBusy(false); }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  // Filters
  const filtered = dividends.filter(d => {
    if (filterBroker && d.account.id.toString() !== filterBroker) return false;
    if (filterYear && d.year?.toString() !== filterYear) return false;
    return true;
  });

  // Broker dropdown label: several accounts can share a name (e.g. two "Tiger" accounts owned by
  // different people). When that happens, append the owner so the options are distinguishable —
  // otherwise the dropdown looks broken because identical entries filter to different data.
  const brokerLabel = (a: Account) => {
    const dupName = accounts.filter(x => x.name === a.name).length > 1;
    return dupName && a.owner ? `${a.name} (${a.owner.name})` : a.name;
  };

  // Convert a dividend's amount into the display currency (SGD/USD toggle) via the user's FX rates.
  // Falls back to the raw amount if no rate is available for the pair.
  const dispAmount = (d: Dividend) => d.amount * (resolveRate(fxRates, d.currency, displayCurrency) ?? 1);

  const totalDividends = filtered.reduce((s, d) => s + dispAmount(d), 0);

  // Render only the first `visibleCount` rows (pagination reset is handled by an effect near the
  // other hooks, above the loading early-return — hooks must not sit after a conditional return).
  const visible = filtered.slice(0, visibleCount);

  // By broker (disambiguate accounts that share a name so totals aren't merged across owners)
  const byBroker: Record<string, number> = {};
  filtered.forEach(d => { const k = brokerLabel(d.account); byBroker[k] = (byBroker[k] || 0) + dispAmount(d); });

  // By instrument
  const byInstrument: Record<string, number> = {};
  filtered.forEach(d => { if (d.instrument) byInstrument[d.instrument] = (byInstrument[d.instrument] || 0) + dispAmount(d); });
  const instrumentData = Object.entries(byInstrument).sort((a, b) => b[1] - a[1]);

  // Available years for filter
  const years = [...new Set(dividends.map(d => d.year).filter(Boolean))].sort((a, b) => b - a);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Dividends</h1><p className="text-slate-500 text-sm mt-0.5">Track and manage dividend income</p></div>
        <div className="flex items-center gap-3">
          <div className="w-44">
            <SearchableSelect
              options={[{ value: '', label: 'All Owners' }, ...owners.map(o => ({ value: o.id.toString(), label: o.name, icon: o.name[0] }))]}
              value={filterOwner}
              onChange={v => setFilterOwner(v.toString())}
              placeholder="All Owners"
            />
          </div>
          <div className="flex bg-white border border-slate-200 rounded-lg overflow-hidden">
            <button onClick={() => setDisplayCurrency('SGD')} className={`px-3 py-1.5 text-xs font-medium transition-colors ${displayCurrency === 'SGD' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>SGD</button>
            <button onClick={() => setDisplayCurrency('USD')} className={`px-3 py-1.5 text-xs font-medium transition-colors ${displayCurrency === 'USD' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>USD</button>
          </div>
          <ExportMenu rows={filtered} config={dividendsExportConfig} />
          {canImport && (
            <button onClick={() => setShowImport(v => !v)} className="flex items-center gap-2 px-4 py-2 bg-white text-slate-700 border border-slate-300 rounded-lg text-sm font-medium hover:bg-slate-50">
              <Upload size={16} /> Import
            </button>
          )}
          <button onClick={() => { setShowBulk(v => !v); setBulkCount(null); }} className="flex items-center gap-2 px-4 py-2 bg-white text-red-600 border border-red-200 rounded-lg text-sm font-medium hover:bg-red-50">
            <Trash2 size={16} /> Bulk delete
          </button>
          <button onClick={() => setShowForm(!showForm)} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
            <Plus size={16} /> Record Dividend
          </button>
        </div>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Total Dividends</p>
          <p className="text-lg font-bold text-green-600 mt-1">{formatCurrency(totalDividends, displayCurrency)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Records</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{filtered.length}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">By Broker</p>
          <div className="mt-1 space-y-0.5">
            {Object.entries(byBroker).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([name, amt]) => (
              <div key={name} className="flex justify-between text-xs"><span className="text-slate-600">{name}</span><span className="font-medium text-slate-800">{formatCurrency(amt, displayCurrency)}</span></div>
            ))}
          </div>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Top Instruments</p>
          <div className="mt-1 space-y-0.5">
            {instrumentData.slice(0, 3).map(([inst, amt]) => (
              <div key={inst} className="flex justify-between text-xs"><span className="text-slate-600">{inst}</span><span className="font-medium text-slate-800">{formatCurrency(amt, displayCurrency)}</span></div>
            ))}
          </div>
        </div>
      </div>

      {/* Yearly Chart */}
      {summary.length > 0 && (
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Dividend Income by Year</h3>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={summary}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="year" stroke="#94a3b8" fontSize={11} />
              <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `S$${v}`} />
              <Tooltip formatter={(v) => formatCurrency(v as number)} />
              <Bar dataKey="total" fill="#10b981" radius={[4, 4, 0, 0]} name="Dividend" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Bulk cleanup panel — delete all dividends for one owner+account, password-confirmed. */}
      {showBulk && (
        <div className="bg-white rounded-xl p-6 border border-red-200 shadow-sm">
          <h3 className="text-base font-semibold text-red-700 mb-1">Bulk delete dividends</h3>
          <p className="text-xs text-slate-500 mb-4">Deletes every dividend for the selected owner <span className="font-medium">and</span> account. This can't be undone. Preview the count, then confirm with your password.</p>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 items-end">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Owner *</label>
              <SearchableSelect options={[{ value: 0, label: 'Select owner...' }, ...owners.map(o => ({ value: o.id, label: o.name }))]}
                value={bulkOwnerId} onChange={v => { setBulkOwnerId(Number(v)); setBulkAccountId(0); setBulkCount(null); }} placeholder="Select owner..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Account *</label>
              <SearchableSelect options={[{ value: 0, label: bulkOwnerId ? 'Select account...' : 'Select an owner first' }, ...accounts.filter(a => !bulkOwnerId || a.owner?.id === bulkOwnerId).map(a => ({ value: a.id, label: brokerLabel(a) }))]}
                value={bulkAccountId} onChange={v => { setBulkAccountId(Number(v)); setBulkCount(null); }} placeholder="Select account..." /></div>
            <button type="button" onClick={handleBulkPreview} disabled={bulkBusy || !bulkOwnerId || !bulkAccountId}
              className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-200 disabled:opacity-50">Preview</button>
          </div>
          {bulkCount !== null && (
            <div className="mt-4 border-t border-slate-100 pt-4">
              {bulkCount === 0 ? (
                <p className="text-sm text-slate-500">No dividends match this owner + account.</p>
              ) : (
                <div className="flex flex-wrap items-end gap-3">
                  <p className="text-sm text-slate-700"><span className="font-semibold text-red-700">{bulkCount}</span> dividend{bulkCount === 1 ? '' : 's'} will be permanently deleted.</p>
                  <input type="password" autoComplete="off" value={bulkPassword} onChange={e => setBulkPassword(e.target.value)} placeholder="Your password"
                    className="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-red-400" />
                  <button type="button" onClick={handleBulkDelete} disabled={bulkBusy || !bulkPassword}
                    className="px-4 py-2 bg-red-600 text-white rounded-lg text-sm font-medium hover:bg-red-700 disabled:opacity-50">
                    {bulkBusy ? 'Deleting…' : `Delete ${bulkCount}`}</button>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* Import panel (feature-gated) */}
      {canImport && showImport && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-1">Import Dividends</h3>
          <p className="text-xs text-slate-500 mb-3">Only dividend rows are imported (net after withholding tax); buys, deposits, interest and reversals are skipped. Missing assets are created automatically, and re-running skips duplicates.</p>

          {/* Mode toggle: upload a file (DIVIDEND_IMPORT) and/or fetch from IBKR (IBKR_SYNC). Each
              button appears only when its feature is enabled; the IBKR option also needs the chosen
              owner to actually have an IBKR account. */}
          {canImportFile && canIbkr && (
            <div className="inline-flex rounded-lg border border-slate-200 p-0.5 mb-4 bg-slate-50">
              <button type="button" onClick={() => setImportMode('file')}
                className={`px-3 py-1.5 text-xs font-medium rounded-md ${importMode === 'file' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-500'}`}>Upload file</button>
              <button type="button" onClick={() => setImportMode('ibkr')}
                className={`px-3 py-1.5 text-xs font-medium rounded-md ${importMode === 'ibkr' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-500'}`}>Fetch from IBKR</button>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 items-end">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Owner *</label>
              <SearchableSelect options={[{ value: 0, label: 'Select owner...' }, ...owners.map(o => ({ value: o.id, label: o.name }))]} value={importOwnerId} onChange={v => { setImportOwnerId(Number(v)); setImportAccountId(0); }} placeholder="Select owner..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Broker account *</label>
              <SearchableSelect
                options={[{ value: 0, label: importOwnerId ? 'Select broker...' : 'Select an owner first' },
                  ...accounts.filter(a => !importOwnerId || a.owner?.id === importOwnerId).map(a => ({ value: a.id, label: `${a.name} (${a.currency})` }))]}
                value={importAccountId}
                onChange={v => setImportAccountId(Number(v))}
                placeholder="Select broker..." /></div>
            {importMode === 'file' && (
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">File (.csv / .xlsx)</label>
                {/* A directly-clicked native file input opens the picker reliably in Chrome & Safari
                    (proxying a hidden input via a button is what Chrome intermittently blocks). */}
                <input ref={fileInputRef} type="file"
                  accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                  disabled={importing}
                  onChange={e => { const f = e.target.files?.[0]; if (f) handleImport(f); }}
                  className="block w-full text-sm text-slate-600 border border-slate-300 rounded-lg cursor-pointer file:mr-3 file:py-2 file:px-3 file:border-0 file:text-sm file:font-medium file:bg-indigo-600 file:text-white hover:file:bg-indigo-700 disabled:opacity-50" />
              </div>
            )}
          </div>

          {importMode === 'ibkr' && (
            <div className="mt-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div><label className="block text-xs font-medium text-slate-600 mb-1">IBKR Flex token *</label>
                  <input type="password" autoComplete="off" value={ibkrToken} onChange={e => setIbkrToken(e.target.value)}
                    placeholder="Flex Web Service token" disabled={importing}
                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 disabled:opacity-50" /></div>
                <div><label className="block text-xs font-medium text-slate-600 mb-1">Flex Query ID *</label>
                  <input type="text" inputMode="numeric" value={ibkrQueryId} onChange={e => setIbkrQueryId(e.target.value)}
                    placeholder="e.g. 123456" disabled={importing}
                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 disabled:opacity-50" /></div>
              </div>
              <p className="text-[11px] text-slate-400 mt-2">Enable the Flex Web Service in IBKR Client Portal (Settings → Reporting) and create an Activity Flex Query that includes dividends. Your token is used only for this fetch and is never stored.</p>
              <button type="button" onClick={handleIbkrFetch} disabled={importing}
                className="mt-3 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:opacity-50">
                {importing ? 'Fetching…' : 'Fetch dividends'}</button>
            </div>
          )}

          {importing && importMode === 'file' && <p className="text-xs text-indigo-600 mt-3">Importing…</p>}
        </div>
      )}

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">Record Dividend</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Owner</label>
              <SearchableSelect options={[{ value: 0, label: 'Unassigned' }, ...owners.map(o => ({ value: o.id, label: o.name }))]} value={form.ownerId} onChange={v => {
                const oid = Number(v);
                // Clear the broker if it doesn't belong to the newly-selected owner.
                const keep = accounts.find(a => a.id === form.accountId)?.owner?.id === oid;
                setForm({ ...form, ownerId: oid, accountId: keep ? form.accountId : 0 });
              }} placeholder="Select owner..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Broker *</label>
              <SearchableSelect options={accounts.filter(a => !form.ownerId || a.owner?.id === form.ownerId).map(a => ({ value: a.id, label: brokerLabel(a) }))} value={form.accountId} onChange={v => setForm({...form, accountId: Number(v)})} placeholder={form.ownerId ? 'Select broker...' : 'Select an owner first'} /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Instrument *</label>
              <input type="text" value={form.instrument} onChange={e => setForm({...form, instrument: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. VOO, D05" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Amount *</label>
              <input type="number" step="any" value={form.amount || ''} onChange={e => setForm({...form, amount: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Currency</label>
              <SearchableSelect options={['SGD','USD','EUR','LKR','INR'].map(c => ({ value: c, label: c }))} value={form.currency} onChange={v => setForm({...form, currency: v as Currency})} placeholder="Currency" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Date *</label>
              <input type="date" value={form.receivedDate} onChange={e => setForm({...form, receivedDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Year</label>
              <input type="number" value={form.year} onChange={e => setForm({...form, year: parseInt(e.target.value) || new Date().getFullYear()})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Quarter</label>
              <SearchableSelect options={['Q1','Q2','Q3','Q4'].map(q => ({ value: q, label: q }))} value={form.quarter} onChange={v => setForm({...form, quarter: v})} placeholder="Quarter" /></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-green-600 text-white rounded-lg text-sm font-medium hover:bg-green-700">Save</button>
              <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Filters */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className="w-44"><SearchableSelect options={[{ value: '', label: 'All Brokers' }, ...accounts.map(a => ({ value: a.id.toString(), label: brokerLabel(a) }))]} value={filterBroker} onChange={v => setFilterBroker(v.toString())} placeholder="All Brokers" /></div>
        <div className="w-36"><SearchableSelect options={[{ value: '', label: 'All Years' }, ...years.map(y => ({ value: y.toString(), label: y.toString() }))]} value={filterYear} onChange={v => setFilterYear(v.toString())} placeholder="All Years" /></div>
        <span className="text-xs text-slate-500">{filtered.length} records | Total: {formatCurrency(totalDividends, displayCurrency)}</span>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Date</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Instrument</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Broker</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Owner</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Quarter</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">Amount</th>
                <th className="px-4 py-2.5 w-8"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {visible.map(d => (
                <tr key={d.id} className="hover:bg-slate-50 group">
                  <td className="px-4 py-2 text-slate-700 text-xs">{formatDate(d.receivedDate)}</td>
                  <td className="px-4 py-2 font-medium text-slate-800">{d.instrument || d.asset?.symbol || '-'}</td>
                  <td className="px-4 py-2 text-slate-600">{d.account.name}</td>
                  <td className="px-4 py-2 text-slate-500 text-xs">{d.owner?.name || '-'}</td>
                  <td className="px-4 py-2 text-slate-500 text-xs">{d.year}-{d.quarter}</td>
                  <td className="px-4 py-2 text-right font-medium text-green-600" title={formatCurrency(d.amount, d.currency)}>{formatCurrency(dispAmount(d), displayCurrency)}</td>
                  <td className="px-4 py-2"><button onClick={() => handleDelete(d.id)} className="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-red-500"><Trash2 size={14} /></button></td>
                </tr>
              ))}
              {filtered.length === 0 && <tr><td colSpan={7} className="px-4 py-12 text-center text-slate-400">No dividends recorded</td></tr>}
            </tbody>
          </table>
        </div>
        {visible.length < filtered.length && (
          <div className="p-4 border-t border-slate-200 flex items-center justify-center gap-3">
            <span className="text-xs text-slate-400">Showing {visible.length} of {filtered.length}</span>
            <button onClick={() => setVisibleCount(c => c + PAGE_SIZE)} className="px-4 py-2 text-sm font-medium text-indigo-600 border border-indigo-200 rounded-lg hover:bg-indigo-50">
              Show more
            </button>
          </div>
        )}
      </div>

      {/* By Instrument Summary */}
      {instrumentData.length > 0 && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 border-b border-slate-200"><h3 className="font-semibold text-slate-800 text-sm">Summary by Instrument</h3></div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-2.5 font-medium text-slate-600">Instrument</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Total</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">% of Total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {instrumentData.map(([inst, amt]) => (
                  <tr key={inst} className="hover:bg-slate-50">
                    <td className="px-4 py-2 font-medium text-slate-800">{inst}</td>
                    <td className="px-4 py-2 text-right text-green-600 font-medium">{formatCurrency(amt, displayCurrency)}</td>
                    <td className="px-4 py-2 text-right text-slate-500">{totalDividends > 0 ? ((amt / totalDividends) * 100).toFixed(1) : 0}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
