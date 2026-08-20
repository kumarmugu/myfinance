import { useEffect, useState } from 'react';
import { Plus, Calendar, Globe, Check } from 'lucide-react';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts';
import { getFixedDeposits, getFDSummary, getMaturingFDs, getBanks, getFDHolders, toggleFDNetWorth } from '../api';
import { formatDate, daysBetween } from '../utils/formatters';
import type { FixedDeposit, FDSummary, Bank, FDHolder } from '../types';

const BANK_COLORS = ['#6366f1', '#06b6d4', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#14b8a6'];

function formatLKR(amount: number): string {
  if (amount >= 1000000) return `₨${(amount / 1000000).toFixed(2)}M`;
  if (amount >= 1000) return `₨${(amount / 1000).toFixed(0)}K`;
  return `₨${amount.toFixed(0)}`;
}

function formatSGD(amount: number): string {
  return `S$${amount.toLocaleString('en-SG', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

export default function FixedDeposits() {
  const [fds, setFds] = useState<FixedDeposit[]>([]);
  const [summary, setSummary] = useState<FDSummary | null>(null);
  const [maturing, setMaturing] = useState<FixedDeposit[]>([]);
  const [banks, setBanks] = useState<Bank[]>([]);
  const [holders, setHolders] = useState<FDHolder[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterBank, setFilterBank] = useState<string>('');
  const [filterHolder, setFilterHolder] = useState<string>('');
  const [netWorthInput, setNetWorthInput] = useState<{ id: number; amount: string } | null>(null);

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [fdRes, sumRes, matRes, bankRes, holderRes] = await Promise.all([
        getFixedDeposits(), getFDSummary(), getMaturingFDs(90), getBanks(), getFDHolders()
      ]);
      setFds(fdRes.data); setSummary(sumRes.data); setMaturing(matRes.data); setBanks(bankRes.data); setHolders(holderRes.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleToggleNetWorth = async (fd: FixedDeposit) => {
    if (fd.includeInNetWorth) {
      // Turn off
      await toggleFDNetWorth(fd.id, false, undefined);
      loadData();
    } else {
      // Show input for SGD amount
      setNetWorthInput({ id: fd.id, amount: '' });
    }
  };

  const submitNetWorthAmount = async () => {
    if (!netWorthInput) return;
    const amount = parseFloat(netWorthInput.amount);
    if (isNaN(amount) || amount <= 0) return;
    await toggleFDNetWorth(netWorthInput.id, true, amount);
    setNetWorthInput(null);
    loadData();
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const filteredFDs = fds.filter(fd => {
    if (filterBank && fd.bank.id.toString() !== filterBank) return false;
    if (filterHolder && fd.holder.id.toString() !== filterHolder) return false;
    return true;
  });

  const bankPieData = summary?.byBank
    ? Object.entries(summary.byBank).map(([name, amount], i) => ({ name, value: amount as number, color: BANK_COLORS[i % BANK_COLORS.length] }))
    : [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Sri Lanka Fixed Deposits</h1>
          <p className="text-slate-500 text-sm mt-0.5">Managed separately from your investment net worth</p>
        </div>
        <button className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
          <Plus size={16} /> New FD
        </button>
      </div>

      {/* Info Banner */}
      <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 flex items-start gap-3">
        <Globe size={18} className="text-amber-600 mt-0.5 shrink-0" />
        <div>
          <p className="text-sm font-medium text-amber-800">Separate from Net Worth</p>
          <p className="text-xs text-amber-600 mt-0.5">Sri Lanka FDs are not automatically included in your portfolio net worth. Use the toggle in the table to manually include specific FDs with their SGD equivalent value.</p>
          {summary && summary.includedInNetWorthCount > 0 && (
            <p className="text-xs font-medium text-amber-800 mt-1">Currently included: {summary.includedInNetWorthCount} FDs totaling {formatSGD(summary.includedInNetWorth)}</p>
          )}
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Total FDs</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{summary?.totalFDs || 0}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Principal (LKR)</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{formatLKR(summary?.totalPrincipal || 0)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Interest (LKR)</p>
          <p className="text-lg font-bold text-emerald-600 mt-1">{formatLKR(summary?.totalExpectedInterest || 0)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Maturing Soon</p>
          <p className="text-lg font-bold text-amber-600 mt-1">{summary?.maturingWithin30Days || 0} <span className="text-xs text-slate-400">in 30d</span></p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">In Net Worth</p>
          <p className="text-lg font-bold text-indigo-600 mt-1">{formatSGD(summary?.includedInNetWorth || 0)}</p>
        </div>
      </div>

      {/* Charts + Maturity */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Distribution by Bank</h3>
          {bankPieData.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart><Pie data={bankPieData} cx="50%" cy="50%" innerRadius={45} outerRadius={80} dataKey="value" label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`} labelLine={false}>
                {bankPieData.map((e, i) => <Cell key={i} fill={e.color} />)}
              </Pie><Tooltip formatter={(v) => formatLKR(v as number)} /></PieChart>
            </ResponsiveContainer>
          ) : <div className="h-56 flex items-center justify-center text-sm text-slate-400">No data</div>}
        </div>

        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3 flex items-center gap-2"><Calendar size={15} className="text-amber-600" /> Maturing Within 90 Days</h3>
          <div className="space-y-2 max-h-56 overflow-y-auto">
            {maturing.map(fd => {
              const days = daysBetween(fd.maturityDate);
              return (
                <div key={fd.id} className={`p-2.5 rounded-lg border ${days <= 30 ? 'border-red-200 bg-red-50' : 'border-amber-200 bg-amber-50'}`}>
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-medium text-slate-800">{fd.holder.name}</span>
                    <span className={`text-xs font-bold ${days <= 30 ? 'text-red-600' : 'text-amber-600'}`}>{days}d</span>
                  </div>
                  <p className="text-xs text-slate-600">{fd.bank.shortName} | {formatLKR(fd.principalAmount)} | {fd.interestRate}%</p>
                </div>
              );
            })}
            {maturing.length === 0 && <p className="text-sm text-slate-400 py-4 text-center">No FDs maturing soon</p>}
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-3 items-center flex-wrap">
        <select value={filterBank} onChange={e => setFilterBank(e.target.value)} className="border border-slate-300 rounded-lg px-3 py-1.5 text-sm">
          <option value="">All Banks</option>
          {banks.map(b => <option key={b.id} value={b.id}>{b.shortName}</option>)}
        </select>
        <select value={filterHolder} onChange={e => setFilterHolder(e.target.value)} className="border border-slate-300 rounded-lg px-3 py-1.5 text-sm">
          <option value="">All Holders</option>
          {holders.map(h => <option key={h.id} value={h.id}>{h.name}</option>)}
        </select>
        <span className="text-xs text-slate-500">{filteredFDs.length} records</span>
      </div>

      {/* FD Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Holder</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Bank</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Principal</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Rate</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Maturity</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Period</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Interest</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Status</th>
                <th className="text-center px-3 py-2.5 font-medium text-slate-600" title="Include in Net Worth">NW</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredFDs.map(fd => (
                <tr key={fd.id} className={`hover:bg-slate-50 ${fd.requiresUpdate ? 'bg-amber-50/50' : ''}`}>
                  <td className="px-3 py-2.5">
                    <span className="font-medium text-slate-800 text-xs">{fd.holder.name}</span>
                    {fd.jointHolder && <p className="text-[10px] text-slate-400">Joint: {fd.jointHolder.name}</p>}
                  </td>
                  <td className="px-3 py-2.5 text-slate-600 text-xs">{fd.bank.shortName}</td>
                  <td className="px-3 py-2.5 text-right font-medium text-slate-800 text-xs">{formatLKR(fd.principalAmount)}</td>
                  <td className="px-3 py-2.5 text-right text-slate-700 text-xs">{fd.interestRate}%</td>
                  <td className="px-3 py-2.5 text-slate-600 text-[11px]">{formatDate(fd.maturityDate)}</td>
                  <td className="px-3 py-2.5 text-slate-500 text-[11px]">{fd.period}</td>
                  <td className="px-3 py-2.5 text-right text-emerald-600 text-xs">{fd.expectedInterest ? formatLKR(fd.expectedInterest) : '-'}</td>
                  <td className="px-3 py-2.5">
                    <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${
                      fd.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                      fd.status === 'REQUIRES_UPDATE' ? 'bg-amber-100 text-amber-700' :
                      'bg-slate-100 text-slate-600'
                    }`}>{fd.status}</span>
                  </td>
                  <td className="px-3 py-2.5 text-center">
                    <button onClick={() => handleToggleNetWorth(fd)} className={`w-5 h-5 rounded border flex items-center justify-center transition-colors ${fd.includeInNetWorth ? 'bg-indigo-600 border-indigo-600' : 'border-slate-300 hover:border-indigo-400'}`} title={fd.includeInNetWorth ? `Included: ${formatSGD(fd.netWorthAmount || 0)}` : 'Click to include in net worth'}>
                      {fd.includeInNetWorth && <Check size={12} className="text-white" />}
                    </button>
                  </td>
                </tr>
              ))}
              {filteredFDs.length === 0 && <tr><td colSpan={9} className="px-4 py-12 text-center text-slate-400">No fixed deposits</td></tr>}
            </tbody>
          </table>
        </div>
      </div>

      {/* Net Worth Amount Modal */}
      {netWorthInput && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl p-6 w-full max-w-sm shadow-2xl">
            <h3 className="font-semibold text-slate-800 mb-2">Include in Net Worth</h3>
            <p className="text-sm text-slate-500 mb-4">Enter the SGD equivalent amount for this FD to include in your net worth calculation.</p>
            <div className="flex items-center gap-2 mb-4">
              <span className="text-sm font-medium text-slate-600">S$</span>
              <input type="number" step="any" value={netWorthInput.amount} onChange={e => setNetWorthInput({...netWorthInput, amount: e.target.value})} placeholder="e.g. 5000" className="flex-1 border border-slate-300 rounded-lg px-3 py-2.5 text-sm focus:ring-2 focus:ring-indigo-500" autoFocus onKeyDown={e => e.key === 'Enter' && submitNetWorthAmount()} />
            </div>
            <div className="flex gap-2">
              <button onClick={submitNetWorthAmount} className="flex-1 py-2.5 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">Include</button>
              <button onClick={() => setNetWorthInput(null)} className="flex-1 py-2.5 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-200">Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
