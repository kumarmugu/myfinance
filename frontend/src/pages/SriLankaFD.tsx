import { useEffect, useState } from 'react';
import { Plus, Calendar, Globe, Pencil, Trash2 } from 'lucide-react';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts';
import { getFixedDeposits, getFDSummary, getMaturingFDs, getBanks, getFDHolders, createFixedDeposit, updateFixedDeposit, deleteFixedDeposit, toggleFDNetWorth } from '../api';
import { formatDate, daysBetween } from '../utils/formatters';
import SearchableSelect from '../components/SearchableSelect';
import type { FixedDeposit, FDSummary, Bank, FDHolder } from '../types';
import { useToast } from '../contexts/ToastContext';

const BANK_COLORS = ['#6366f1', '#06b6d4', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#14b8a6'];

function formatLKR(amount: number): string {
  if (amount >= 1000000) return `₨${(amount / 1000000).toFixed(2)}M`;
  if (amount >= 1000) return `₨${(amount / 1000).toFixed(0)}K`;
  return `₨${amount.toFixed(0)}`;
}

function formatSGD(amount: number): string {
  return `S$${amount.toLocaleString('en-SG', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

export default function SriLankaFD() {
  const [fds, setFds] = useState<FixedDeposit[]>([]);
  const [summary, setSummary] = useState<FDSummary | null>(null);
  const [maturing, setMaturing] = useState<FixedDeposit[]>([]);
  const [banks, setBanks] = useState<Bank[]>([]);
  const { showToast } = useToast();
  const [holders, setHolders] = useState<FDHolder[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterBank, setFilterBank] = useState<string>('');
  const [filterHolder, setFilterHolder] = useState<string>('');
  const [netWorthInput, setNetWorthInput] = useState<{ id: number; amount: string } | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<FixedDeposit | null>(null);
  const [fdForm, setFdForm] = useState({
    holderId: 0, jointHolderId: 0, bankId: 0, accountNumber: '', principalAmount: 0,
    interestRate: 0, startDate: '', maturityDate: '', period: '', branch: '',
    category: 'NORMAL', status: 'ACTIVE', expectedInterest: 0, beneficiary: '', purpose: '', notes: ''
  });

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

  const submitNetWorthAmount = async () => {
    if (!netWorthInput) return;
    const amount = parseFloat(netWorthInput.amount);
    if (isNaN(amount) || amount <= 0) return;
    await toggleFDNetWorth(netWorthInput.id, true, amount);
    setNetWorthInput(null);
    loadData();
  };

  const resetFdForm = () => setFdForm({
    holderId: 0, jointHolderId: 0, bankId: 0, accountNumber: '', principalAmount: 0,
    interestRate: 0, startDate: '', maturityDate: '', period: '', branch: '',
    category: 'NORMAL', status: 'ACTIVE', expectedInterest: 0, beneficiary: '', purpose: '', notes: ''
  });

  const handleFdSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const payload: any = {
      holder: { id: fdForm.holderId },
      jointHolder: fdForm.jointHolderId ? { id: fdForm.jointHolderId } : null,
      bank: { id: fdForm.bankId },
      accountNumber: fdForm.accountNumber || null,
      principalAmount: fdForm.principalAmount,
      interestRate: fdForm.interestRate,
      startDate: fdForm.startDate,
      maturityDate: fdForm.maturityDate,
      period: fdForm.period || null,
      branch: fdForm.branch || null,
      category: fdForm.category,
      status: fdForm.status,
      expectedInterest: fdForm.expectedInterest || null,
      beneficiary: fdForm.beneficiary || null,
      purpose: fdForm.purpose || null,
      notes: fdForm.notes || null,
    };
    try {
      if (editing) { await updateFixedDeposit(editing.id, payload); }
      else { await createFixedDeposit(payload); }
      setShowForm(false); setEditing(null); resetFdForm(); loadData();
    } catch (err) { console.error(err); showToast('Failed to save FD'); }
  };

  const startEditFd = (fd: FixedDeposit) => {
    setEditing(fd);
    setFdForm({
      holderId: fd.holder.id, jointHolderId: fd.jointHolder?.id || 0, bankId: fd.bank.id,
      accountNumber: fd.accountNumber || '', principalAmount: fd.principalAmount,
      interestRate: fd.interestRate, startDate: fd.startDate, maturityDate: fd.maturityDate,
      period: fd.period || '', branch: fd.branch || '', category: fd.category || 'NORMAL',
      status: fd.status, expectedInterest: fd.expectedInterest || 0,
      beneficiary: fd.beneficiary || '', purpose: fd.purpose || '', notes: fd.notes || ''
    });
    setShowForm(true);
  };

  const handleDeleteFd = async (id: number) => {
    if (confirm('Delete this Fixed Deposit?')) { await deleteFixedDeposit(id); loadData(); }
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
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <p className="text-sm text-slate-500">Sri Lanka family fixed deposits — managed separately from investment net worth</p>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); resetFdForm(); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
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

      {/* FD Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editing ? 'Edit Fixed Deposit' : 'New Fixed Deposit'}</h3>
          <form onSubmit={handleFdSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Holder *</label>
              <SearchableSelect options={holders.map(h => ({ value: h.id, label: h.name }))} value={fdForm.holderId} onChange={v => setFdForm({...fdForm, holderId: Number(v)})} placeholder="Select holder..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Joint Holder</label>
              <SearchableSelect options={[{ value: 0, label: 'None' }, ...holders.map(h => ({ value: h.id, label: h.name }))]} value={fdForm.jointHolderId} onChange={v => setFdForm({...fdForm, jointHolderId: Number(v)})} placeholder="None" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Bank *</label>
              <SearchableSelect options={banks.map(b => ({ value: b.id, label: `${b.shortName} - ${b.name}` }))} value={fdForm.bankId} onChange={v => setFdForm({...fdForm, bankId: Number(v)})} placeholder="Select bank..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Account Number</label>
              <input type="text" value={fdForm.accountNumber} onChange={e => setFdForm({...fdForm, accountNumber: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Principal Amount (LKR) *</label>
              <input type="number" step="any" value={fdForm.principalAmount || ''} onChange={e => setFdForm({...fdForm, principalAmount: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Interest Rate (%) *</label>
              <input type="number" step="0.01" value={fdForm.interestRate || ''} onChange={e => setFdForm({...fdForm, interestRate: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Start Date *</label>
              <input type="date" value={fdForm.startDate} onChange={e => setFdForm({...fdForm, startDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Maturity Date *</label>
              <input type="date" value={fdForm.maturityDate} onChange={e => setFdForm({...fdForm, maturityDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Period</label>
              <input type="text" value={fdForm.period} onChange={e => setFdForm({...fdForm, period: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. 12 Months" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Branch</label>
              <input type="text" value={fdForm.branch} onChange={e => setFdForm({...fdForm, branch: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Category</label>
              <div className="flex rounded-lg overflow-hidden border border-slate-300">
                <button type="button" onClick={() => setFdForm({...fdForm, category: 'NORMAL'})} className={`flex-1 py-2 text-xs font-medium transition-colors ${fdForm.category === 'NORMAL' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}>Normal</button>
                <button type="button" onClick={() => setFdForm({...fdForm, category: 'SENIOR_CITIZEN'})} className={`flex-1 py-2 text-xs font-medium transition-colors ${fdForm.category === 'SENIOR_CITIZEN' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}>Senior</button>
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Status</label>
              <div className="flex flex-wrap gap-1.5">
                {([['ACTIVE', 'Active'], ['MATURED', 'Matured'], ['RENEWED', 'Renewed'], ['CLOSED', 'Closed'], ['REQUIRES_UPDATE', 'Needs Update']] as const).map(([val, label]) => (
                  <button key={val} type="button" onClick={() => setFdForm({...fdForm, status: val})}
                    className={`px-2.5 py-1.5 rounded-lg text-[11px] font-medium transition-colors border ${fdForm.status === val ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-300 hover:border-indigo-300'}`}>{label}</button>
                ))}
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Expected Interest (LKR)</label>
              <input type="number" step="any" value={fdForm.expectedInterest || ''} onChange={e => setFdForm({...fdForm, expectedInterest: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Beneficiary</label>
              <input type="text" value={fdForm.beneficiary} onChange={e => setFdForm({...fdForm, beneficiary: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Purpose</label>
              <input type="text" value={fdForm.purpose} onChange={e => setFdForm({...fdForm, purpose: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. deed, car" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Notes</label>
              <input type="text" value={fdForm.notes} onChange={e => setFdForm({...fdForm, notes: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div className="flex items-end gap-2 lg:col-span-4">
              <button type="submit" className="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editing ? 'Update' : 'Save'}</button>
              <button type="button" onClick={() => { setShowForm(false); setEditing(null); }} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-200">Cancel</button>
            </div>
          </form>
        </div>
      )}

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
        <div className="w-48"><SearchableSelect options={[{ value: '', label: 'All Banks' }, ...banks.map(b => ({ value: b.id.toString(), label: b.shortName }))]} value={filterBank} onChange={v => setFilterBank(v.toString() === '0' ? '' : v.toString())} placeholder="All Banks" /></div>
        <div className="w-48"><SearchableSelect options={[{ value: '', label: 'All Holders' }, ...holders.map(h => ({ value: h.id.toString(), label: h.name }))]} value={filterHolder} onChange={v => setFilterHolder(v.toString() === '0' ? '' : v.toString())} placeholder="All Holders" /></div>
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
                <th className="px-3 py-2.5 w-16"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredFDs.map(fd => (
                <tr key={fd.id} className={`hover:bg-slate-50 group ${fd.requiresUpdate ? 'bg-amber-50/50' : ''}`}>
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
                  <td className="px-3 py-2.5">
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => startEditFd(fd)} className="text-slate-400 hover:text-indigo-600"><Pencil size={13} /></button>
                      <button onClick={() => handleDeleteFd(fd.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
                    </div>
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
