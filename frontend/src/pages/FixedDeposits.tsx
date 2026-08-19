import { useEffect, useState } from 'react';
import { Plus, AlertTriangle, Calendar, Building2 } from 'lucide-react';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts';
import { getFixedDeposits, getFDSummary, getMaturingFDs, getBanks, getFDHolders, deleteFixedDeposit } from '../api';
import { formatDate, daysBetween } from '../utils/formatters';
import type { FixedDeposit, FDSummary, Bank, FDHolder } from '../types';

const BANK_COLORS = ['#6366f1', '#06b6d4', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#14b8a6'];

function formatLKR(amount: number): string {
  if (amount >= 1000000) return `₨${(amount / 1000000).toFixed(2)}M`;
  if (amount >= 1000) return `₨${(amount / 1000).toFixed(0)}K`;
  return `₨${amount.toFixed(0)}`;
}

export default function FixedDeposits() {
  const [fds, setFds] = useState<FixedDeposit[]>([]);
  const [summary, setSummary] = useState<FDSummary | null>(null);
  const [maturing, setMaturing] = useState<FixedDeposit[]>([]);
  const [banks, setBanks] = useState<Bank[]>([]);
  const [holders, setHolders] = useState<FDHolder[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [filterBank, setFilterBank] = useState<string>('');
  const [filterHolder, setFilterHolder] = useState<string>('');

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

  const handleDelete = async (id: number) => { if (confirm('Delete this FD?')) { await deleteFixedDeposit(id); loadData(); } };
  void handleDelete; // available for row actions

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const filteredFDs = fds.filter(fd => {
    if (filterBank && fd.bank.id.toString() !== filterBank) return false;
    if (filterHolder && fd.holder.id.toString() !== filterHolder) return false;
    return true;
  });

  // Pie data
  const bankPieData = summary?.byBank
    ? Object.entries(summary.byBank).map(([name, amount], i) => ({ name, value: amount as number, color: BANK_COLORS[i % BANK_COLORS.length] }))
    : [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Fixed Deposits</h1>
          <p className="text-slate-500 text-sm mt-1">Manage family fixed deposits across Sri Lankan banks</p>
        </div>
        <button onClick={() => setShowForm(!showForm)} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
          <Plus size={16} /> New FD
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <div className="flex items-center gap-3 mb-2"><Building2 size={18} className="text-indigo-600" /><span className="text-sm text-slate-500">Total FDs</span></div>
          <p className="text-2xl font-bold text-slate-800">{summary?.totalFDs || 0}</p>
        </div>
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <div className="flex items-center gap-3 mb-2"><Building2 size={18} className="text-cyan-600" /><span className="text-sm text-slate-500">Total Principal</span></div>
          <p className="text-2xl font-bold text-slate-800">{formatLKR(summary?.totalPrincipal || 0)}</p>
        </div>
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <div className="flex items-center gap-3 mb-2"><Building2 size={18} className="text-emerald-600" /><span className="text-sm text-slate-500">Expected Interest</span></div>
          <p className="text-2xl font-bold text-slate-800">{formatLKR(summary?.totalExpectedInterest || 0)}</p>
        </div>
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <div className="flex items-center gap-3 mb-2"><AlertTriangle size={18} className="text-amber-600" /><span className="text-sm text-slate-500">Alerts</span></div>
          <p className="text-sm text-slate-700"><span className="font-bold text-amber-600">{summary?.maturingWithin30Days}</span> maturing in 30d</p>
          <p className="text-sm text-slate-700"><span className="font-bold text-red-600">{summary?.requiresUpdate}</span> need update</p>
        </div>
      </div>

      {/* Charts + Maturing */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="font-semibold text-slate-800 mb-4">Distribution by Bank</h3>
          {bankPieData.length > 0 ? (
            <ResponsiveContainer width="100%" height={250}>
              <PieChart><Pie data={bankPieData} cx="50%" cy="50%" innerRadius={50} outerRadius={90} dataKey="value" label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`} labelLine={false}>
                {bankPieData.map((e, i) => <Cell key={i} fill={e.color} />)}
              </Pie><Tooltip formatter={(v) => formatLKR(v as number)} /></PieChart>
            </ResponsiveContainer>
          ) : <div className="h-64 flex items-center justify-center text-slate-400">No data</div>}
        </div>

        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="font-semibold text-slate-800 mb-4 flex items-center gap-2"><Calendar size={18} className="text-amber-600" /> Maturing Soon (90 days)</h3>
          <div className="space-y-2 max-h-64 overflow-y-auto">
            {maturing.map(fd => {
              const days = daysBetween(fd.maturityDate);
              return (
                <div key={fd.id} className={`p-3 rounded-lg border ${days <= 30 ? 'border-red-200 bg-red-50' : 'border-amber-200 bg-amber-50'}`}>
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-medium text-slate-800">{fd.holder.name}</span>
                    <span className={`text-xs font-bold ${days <= 30 ? 'text-red-600' : 'text-amber-600'}`}>{days}d</span>
                  </div>
                  <p className="text-xs text-slate-600">{fd.bank.shortName} | {formatLKR(fd.principalAmount)} | {fd.interestRate}%</p>
                </div>
              );
            })}
            {maturing.length === 0 && <p className="text-sm text-slate-400">No FDs maturing soon</p>}
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-4 items-center">
        <select value={filterBank} onChange={e => setFilterBank(e.target.value)} className="border border-slate-300 rounded-lg px-3 py-2 text-sm">
          <option value="">All Banks</option>
          {banks.map(b => <option key={b.id} value={b.id}>{b.shortName}</option>)}
        </select>
        <select value={filterHolder} onChange={e => setFilterHolder(e.target.value)} className="border border-slate-300 rounded-lg px-3 py-2 text-sm">
          <option value="">All Holders</option>
          {holders.map(h => <option key={h.id} value={h.id}>{h.name}</option>)}
        </select>
        <span className="text-sm text-slate-500">{filteredFDs.length} records</span>
      </div>

      {/* FD Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-3 py-3 font-medium text-slate-600">Holder</th>
                <th className="text-left px-3 py-3 font-medium text-slate-600">Bank</th>
                <th className="text-right px-3 py-3 font-medium text-slate-600">Principal</th>
                <th className="text-right px-3 py-3 font-medium text-slate-600">Rate</th>
                <th className="text-left px-3 py-3 font-medium text-slate-600">Start</th>
                <th className="text-left px-3 py-3 font-medium text-slate-600">Maturity</th>
                <th className="text-left px-3 py-3 font-medium text-slate-600">Period</th>
                <th className="text-right px-3 py-3 font-medium text-slate-600">Interest</th>
                <th className="text-left px-3 py-3 font-medium text-slate-600">Status</th>
                <th className="text-left px-3 py-3 font-medium text-slate-600">Branch</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredFDs.map(fd => (
                <tr key={fd.id} className={`hover:bg-slate-50 ${fd.requiresUpdate ? 'bg-amber-50' : ''}`}>
                  <td className="px-3 py-3">
                    <span className="font-medium text-slate-800">{fd.holder.name}</span>
                    {fd.jointHolder && <p className="text-xs text-slate-400">Joint: {fd.jointHolder.name}</p>}
                  </td>
                  <td className="px-3 py-3 text-slate-600">{fd.bank.shortName}</td>
                  <td className="px-3 py-3 text-right font-medium text-slate-800">{formatLKR(fd.principalAmount)}</td>
                  <td className="px-3 py-3 text-right text-slate-700">{fd.interestRate}%</td>
                  <td className="px-3 py-3 text-slate-600 text-xs">{formatDate(fd.startDate)}</td>
                  <td className="px-3 py-3 text-slate-600 text-xs">{formatDate(fd.maturityDate)}</td>
                  <td className="px-3 py-3 text-slate-500 text-xs">{fd.period}</td>
                  <td className="px-3 py-3 text-right text-emerald-600">{fd.expectedInterest ? formatLKR(fd.expectedInterest) : '-'}</td>
                  <td className="px-3 py-3">
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                      fd.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                      fd.status === 'REQUIRES_UPDATE' ? 'bg-amber-100 text-amber-700' :
                      fd.status === 'MATURED' ? 'bg-slate-100 text-slate-700' : 'bg-slate-100 text-slate-500'
                    }`}>{fd.status}</span>
                  </td>
                  <td className="px-3 py-3 text-slate-500 text-xs">{fd.branch}</td>
                </tr>
              ))}
              {filteredFDs.length === 0 && <tr><td colSpan={10} className="px-4 py-12 text-center text-slate-400">No fixed deposits found</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
