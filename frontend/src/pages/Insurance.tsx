import { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, AlertCircle } from 'lucide-react';
import { getInsurancePolicies, createInsurancePolicy, updateInsurancePolicy, deleteInsurancePolicy } from '../api';
import { formatCurrency, formatDate } from '../utils/formatters';
import type { InsurancePolicy, Currency } from '../types';

const POLICY_TYPES = ['TERM_LIFE', 'WHOLE_LIFE', 'ENDOWMENT', 'ILP', 'HEALTH', 'CRITICAL_ILLNESS', 'OTHER'];

export default function Insurance() {
  const [policies, setPolicies] = useState<InsurancePolicy[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<InsurancePolicy | null>(null);
  const [form, setForm] = useState({ policyName: '', provider: '', policyNumber: '', policyType: 'TERM_LIFE', annualPremium: 0, currency: 'SGD' as Currency, coverageAmount: 0, cashValue: 0, startDate: '', maturityDate: '', includeInNetWorth: false, beneficiary: '', notes: '' });

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try { setPolicies((await getInsurancePolicies()).data); }
    catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editing) { await updateInsurancePolicy(editing.id, form); }
      else { await createInsurancePolicy(form); }
      setShowForm(false); setEditing(null); resetForm(); loadData();
    } catch (err) { console.error(err); alert('Failed'); }
  };

  const startEdit = (p: InsurancePolicy) => {
    setEditing(p);
    setForm({ policyName: p.policyName, provider: p.provider || '', policyNumber: p.policyNumber || '', policyType: p.policyType || 'TERM_LIFE', annualPremium: p.annualPremium, currency: p.currency, coverageAmount: p.coverageAmount || 0, cashValue: p.cashValue || 0, startDate: p.startDate || '', maturityDate: p.maturityDate || '', includeInNetWorth: p.includeInNetWorth, beneficiary: p.beneficiary || '', notes: p.notes || '' });
    setShowForm(true);
  };

  const handleDelete = async (id: number) => { if (confirm('Remove this policy?')) { await deleteInsurancePolicy(id); loadData(); } };
  const resetForm = () => setForm({ policyName: '', provider: '', policyNumber: '', policyType: 'TERM_LIFE', annualPremium: 0, currency: 'SGD', coverageAmount: 0, cashValue: 0, startDate: '', maturityDate: '', includeInNetWorth: false, beneficiary: '', notes: '' });

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const totalPremium = policies.reduce((s, p) => s + p.annualPremium, 0);
  const totalCoverage = policies.reduce((s, p) => s + (p.coverageAmount || 0), 0);
  const totalCashValue = policies.reduce((s, p) => s + (p.cashValue || 0), 0);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Life Insurance</h1><p className="text-slate-500 text-sm mt-0.5">Track policies (not included in net worth by default)</p></div>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); resetForm(); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700"><Plus size={16} /> Add Policy</button>
      </div>

      {/* Info */}
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-3 flex items-start gap-2">
        <AlertCircle size={16} className="text-blue-600 mt-0.5 shrink-0" />
        <p className="text-xs text-blue-700">Insurance policies are tracked separately and not included in your net worth calculation by default. Only cash value (if toggled) will count toward net worth via the Settings page.</p>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Policies</p><p className="text-lg font-bold text-slate-800 mt-1">{policies.length}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Annual Premium</p><p className="text-lg font-bold text-red-600 mt-1">{formatCurrency(totalPremium)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Total Coverage</p><p className="text-lg font-bold text-indigo-600 mt-1">{formatCurrency(totalCoverage)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Cash Value</p><p className="text-lg font-bold text-green-600 mt-1">{formatCurrency(totalCashValue)}</p></div>
      </div>

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editing ? 'Edit Policy' : 'Add Policy'}</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Policy Name *</label><input type="text" value={form.policyName} onChange={e => setForm({...form, policyName: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Provider</label><input type="text" value={form.provider} onChange={e => setForm({...form, provider: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. AIA, Prudential" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Type</label>
              <select value={form.policyType} onChange={e => setForm({...form, policyType: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm">
                {POLICY_TYPES.map(t => <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>)}
              </select></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Annual Premium *</label><input type="number" step="any" value={form.annualPremium || ''} onChange={e => setForm({...form, annualPremium: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Coverage Amount</label><input type="number" step="any" value={form.coverageAmount || ''} onChange={e => setForm({...form, coverageAmount: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Cash Value</label><input type="number" step="any" value={form.cashValue || ''} onChange={e => setForm({...form, cashValue: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Start Date</label><input type="date" value={form.startDate} onChange={e => setForm({...form, startDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Maturity Date</label><input type="date" value={form.maturityDate} onChange={e => setForm({...form, maturityDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div className="flex items-end gap-2 lg:col-span-4">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editing ? 'Update' : 'Save'}</button>
              <button type="button" onClick={() => { setShowForm(false); setEditing(null); }} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Policy</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Provider</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Type</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">Premium/yr</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">Coverage</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">Cash Value</th>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Maturity</th>
                <th className="px-4 py-2.5 w-16"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {policies.map(p => (
                <tr key={p.id} className="hover:bg-slate-50 group">
                  <td className="px-4 py-2.5"><span className="font-medium text-slate-800">{p.policyName}</span>{p.includeInNetWorth && <span className="ml-1 text-[9px] bg-indigo-100 text-indigo-700 px-1 py-0.5 rounded">NW</span>}</td>
                  <td className="px-4 py-2.5 text-slate-600">{p.provider || '-'}</td>
                  <td className="px-4 py-2.5"><span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">{p.policyType?.replace(/_/g, ' ')}</span></td>
                  <td className="px-4 py-2.5 text-right text-red-600 font-medium">{formatCurrency(p.annualPremium, p.currency)}</td>
                  <td className="px-4 py-2.5 text-right text-slate-700">{p.coverageAmount ? formatCurrency(p.coverageAmount, p.currency) : '-'}</td>
                  <td className="px-4 py-2.5 text-right text-green-600">{p.cashValue ? formatCurrency(p.cashValue, p.currency) : '-'}</td>
                  <td className="px-4 py-2.5 text-slate-500 text-xs">{p.maturityDate ? formatDate(p.maturityDate) : '-'}</td>
                  <td className="px-4 py-2.5"><div className="flex gap-1 opacity-0 group-hover:opacity-100"><button onClick={() => startEdit(p)} className="text-slate-400 hover:text-indigo-600"><Pencil size={13} /></button><button onClick={() => handleDelete(p.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button></div></td>
                </tr>
              ))}
              {policies.length === 0 && <tr><td colSpan={8} className="px-4 py-12 text-center text-slate-400">No insurance policies tracked</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
