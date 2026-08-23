import { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2 } from 'lucide-react';
import api from '../api';
import { formatCurrency, formatDate } from '../utils/formatters';
import { useToast } from '../contexts/ToastContext';

interface GenericFixedDeposit {
  id: number;
  bankName: string;
  accountNumber: string;
  principalAmount: number;
  interestRate: number;
  startDate: string;
  maturityDate: string;
  tenure: string;
  expectedInterest: number;
  currency: string;
  status: string;
  includeInNetWorth: boolean;
  notes: string;
}

export default function GenericFD() {
  const [fds, setFds] = useState<GenericFixedDeposit[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<GenericFixedDeposit | null>(null);
  const { showToast } = useToast();
  const [form, setForm] = useState({
    bankName: '', accountNumber: '', principalAmount: 0, interestRate: 0,
    startDate: '', maturityDate: '', tenure: '', currency: 'SGD',
    status: 'ACTIVE', includeInNetWorth: true, notes: ''
  });

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try { setFds((await api.get('/generic-fd')).data); }
    catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const resetForm = () => setForm({ bankName: '', accountNumber: '', principalAmount: 0, interestRate: 0, startDate: '', maturityDate: '', tenure: '', currency: 'SGD', status: 'ACTIVE', includeInNetWorth: true, notes: '' });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editing) { await api.put(`/generic-fd/${editing.id}`, form); }
      else { await api.post('/generic-fd', form); }
      setShowForm(false); setEditing(null); resetForm(); loadData();
      showToast(editing ? 'Fixed deposit updated' : 'Fixed deposit created', 'success');
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to save');
    }
  };

  const startEdit = (fd: GenericFixedDeposit) => {
    setEditing(fd);
    setForm({
      bankName: fd.bankName, accountNumber: fd.accountNumber || '',
      principalAmount: fd.principalAmount, interestRate: fd.interestRate,
      startDate: fd.startDate, maturityDate: fd.maturityDate,
      tenure: fd.tenure || '', currency: fd.currency || 'SGD',
      status: fd.status, includeInNetWorth: fd.includeInNetWorth,
      notes: fd.notes || ''
    });
    setShowForm(true);
  };

  const handleDelete = async (id: number) => {
    if (confirm('Delete this fixed deposit?')) {
      await api.delete(`/generic-fd/${id}`);
      loadData();
    }
  };

  if (loading) return <div className="flex items-center justify-center h-32"><div className="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600"></div></div>;

  const totalPrincipal = fds.filter(f => f.status === 'ACTIVE').reduce((s, f) => s + f.principalAmount, 0);
  const totalInterest = fds.filter(f => f.status === 'ACTIVE').reduce((s, f) => s + (f.expectedInterest || 0), 0);

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div className="grid grid-cols-3 gap-3 flex-1">
          <div className="bg-white rounded-lg p-3 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Active FDs</p><p className="text-lg font-bold text-slate-800 mt-0.5">{fds.filter(f => f.status === 'ACTIVE').length}</p></div>
          <div className="bg-white rounded-lg p-3 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Total Principal</p><p className="text-lg font-bold text-indigo-600 mt-0.5">{formatCurrency(totalPrincipal)}</p></div>
          <div className="bg-white rounded-lg p-3 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Expected Interest</p><p className="text-lg font-bold text-green-600 mt-0.5">{formatCurrency(totalInterest)}</p></div>
        </div>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); resetForm(); }} className="ml-4 flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
          <Plus size={16} /> New FD
        </button>
      </div>

      {showForm && (
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">{editing ? 'Edit Fixed Deposit' : 'Add Fixed Deposit'}</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Bank *</label>
              <input type="text" value={form.bankName} onChange={e => setForm({...form, bankName: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required placeholder="e.g. DBS, OCBC" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Principal *</label>
              <input type="number" step="any" value={form.principalAmount || ''} onChange={e => setForm({...form, principalAmount: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Interest Rate (%) *</label>
              <input type="number" step="0.01" value={form.interestRate || ''} onChange={e => setForm({...form, interestRate: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Currency</label>
              <select value={form.currency} onChange={e => setForm({...form, currency: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm">
                <option value="SGD">SGD</option><option value="USD">USD</option><option value="LKR">LKR</option><option value="INR">INR</option>
              </select></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Start Date *</label>
              <input type="date" value={form.startDate} onChange={e => setForm({...form, startDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Maturity Date *</label>
              <input type="date" value={form.maturityDate} onChange={e => setForm({...form, maturityDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Tenure</label>
              <input type="text" value={form.tenure} onChange={e => setForm({...form, tenure: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. 12 months" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Account Number</label>
              <input type="text" value={form.accountNumber} onChange={e => setForm({...form, accountNumber: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Notes</label>
              <input type="text" value={form.notes} onChange={e => setForm({...form, notes: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editing ? 'Update' : 'Save'}</button>
              <button type="button" onClick={() => { setShowForm(false); setEditing(null); }} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 border-b border-slate-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Bank</th>
              <th className="text-right px-4 py-3 font-medium text-slate-600">Principal</th>
              <th className="text-right px-4 py-3 font-medium text-slate-600">Rate</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Start</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Maturity</th>
              <th className="text-right px-4 py-3 font-medium text-slate-600">Interest</th>
              <th className="text-left px-4 py-3 font-medium text-slate-600">Status</th>
              <th className="px-4 py-3 w-16"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {fds.map(fd => (
              <tr key={fd.id} className="hover:bg-slate-50 group">
                <td className="px-4 py-3 font-medium text-slate-800">{fd.bankName}</td>
                <td className="px-4 py-3 text-right text-slate-800">{formatCurrency(fd.principalAmount, fd.currency)}</td>
                <td className="px-4 py-3 text-right text-slate-600">{fd.interestRate}%</td>
                <td className="px-4 py-3 text-slate-600 text-xs">{formatDate(fd.startDate)}</td>
                <td className="px-4 py-3 text-slate-600 text-xs">{formatDate(fd.maturityDate)}</td>
                <td className="px-4 py-3 text-right text-green-600">{fd.expectedInterest ? formatCurrency(fd.expectedInterest, fd.currency) : '-'}</td>
                <td className="px-4 py-3">
                  <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${fd.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : fd.status === 'MATURED' ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-600'}`}>{fd.status}</span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button onClick={() => startEdit(fd)} className="text-slate-400 hover:text-indigo-600"><Pencil size={13} /></button>
                    <button onClick={() => handleDelete(fd.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
                  </div>
                </td>
              </tr>
            ))}
            {fds.length === 0 && <tr><td colSpan={8} className="px-4 py-12 text-center text-slate-400">No fixed deposits. Click "New FD" to add one.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  );
}
