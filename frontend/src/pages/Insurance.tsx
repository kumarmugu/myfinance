import React, { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, AlertCircle, Calendar, ChevronDown, ChevronUp } from 'lucide-react';
import { getInsurancePolicies, createInsurancePolicy, updateInsurancePolicy, deleteInsurancePolicy, getInsuranceBonusEntries, createInsuranceBonusEntry, deleteInsuranceBonusEntry } from '../api';
import { formatCurrency, formatDate } from '../utils/formatters';
import type { InsurancePolicy, Currency } from '../types';

const POLICY_TYPES = ['TERM_LIFE', 'WHOLE_LIFE', 'ENDOWMENT', 'ILP', 'HEALTH', 'CRITICAL_ILLNESS', 'OTHER'];

export default function Insurance() {
  const [policies, setPolicies] = useState<InsurancePolicy[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<InsurancePolicy | null>(null);
  const [form, setForm] = useState({ policyName: '', provider: '', policyNumber: '', policyType: 'TERM_LIFE', annualPremium: 0, currency: 'SGD' as Currency, coverageAmount: 0, cashValue: 0, startDate: '', maturityDate: '', includeInNetWorth: false, beneficiary: '', notes: '' });
  const [expandedPolicy, setExpandedPolicy] = useState<number | null>(null);
  const [bonusEntries, setBonusEntries] = useState<any[]>([]);
  const [showBonusForm, setShowBonusForm] = useState(false);
  const [bonusForm, setBonusForm] = useState({ yearNumber: 1, yearDate: '', age: 0, premiumAmount: 0, expectedBonus: 0, expectedBonusTotal: 0, expectedTotal: 0, actualBonus: 0, actualBonusTotal: 0 });

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

  const toggleBonusSchedule = async (policyId: number) => {
    if (expandedPolicy === policyId) { setExpandedPolicy(null); return; }
    try {
      const res = await getInsuranceBonusEntries(policyId);
      setBonusEntries(res.data);
      setExpandedPolicy(policyId);
    } catch (err) { console.error(err); }
  };

  const handleBonusSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!expandedPolicy) return;
    try {
      await createInsuranceBonusEntry(expandedPolicy, bonusForm);
      const res = await getInsuranceBonusEntries(expandedPolicy);
      setBonusEntries(res.data);
      setShowBonusForm(false);
      setBonusForm({ yearNumber: bonusEntries.length + 2, yearDate: '', age: 0, premiumAmount: 0, expectedBonus: 0, expectedBonusTotal: 0, expectedTotal: 0, actualBonus: 0, actualBonusTotal: 0 });
    } catch (err) { console.error(err); alert('Failed'); }
  };

  const handleDeleteBonusEntry = async (entryId: number) => {
    if (!confirm('Delete this entry?')) return;
    await deleteInsuranceBonusEntry(entryId);
    if (expandedPolicy) {
      const res = await getInsuranceBonusEntries(expandedPolicy);
      setBonusEntries(res.data);
    }
  };

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
            <div className="lg:col-span-2"><label className="block text-xs font-medium text-slate-600 mb-1">Type</label>
              <div className="flex flex-wrap gap-1.5">
                {POLICY_TYPES.map(t => (
                  <button key={t} type="button" onClick={() => setForm({...form, policyType: t})}
                    className={`px-3 py-1.5 rounded-lg text-[11px] font-medium transition-colors border ${form.policyType === t ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-300 hover:border-indigo-300'}`}>{t.replace(/_/g, ' ')}</button>
                ))}
              </div></div>
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
                <React.Fragment key={p.id}>
                <tr className="hover:bg-slate-50 group">
                  <td className="px-4 py-2.5"><span className="font-medium text-slate-800">{p.policyName}</span>{p.includeInNetWorth && <span className="ml-1 text-[9px] bg-indigo-100 text-indigo-700 px-1 py-0.5 rounded">NW</span>}</td>
                  <td className="px-4 py-2.5 text-slate-600">{p.provider || '-'}</td>
                  <td className="px-4 py-2.5"><span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">{p.policyType?.replace(/_/g, ' ')}</span></td>
                  <td className="px-4 py-2.5 text-right text-red-600 font-medium">{formatCurrency(p.annualPremium, p.currency)}</td>
                  <td className="px-4 py-2.5 text-right text-slate-700">{p.coverageAmount ? formatCurrency(p.coverageAmount, p.currency) : '-'}</td>
                  <td className="px-4 py-2.5 text-right text-green-600">{p.cashValue ? formatCurrency(p.cashValue, p.currency) : '-'}</td>
                  <td className="px-4 py-2.5 text-slate-500 text-xs">{p.maturityDate ? formatDate(p.maturityDate) : '-'}</td>
                  <td className="px-4 py-2.5"><div className="flex gap-1 opacity-0 group-hover:opacity-100"><button onClick={() => startEdit(p)} className="text-slate-400 hover:text-indigo-600"><Pencil size={13} /></button><button onClick={() => handleDelete(p.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button></div></td>
                </tr>
                {/* Bonus Schedule Row */}
                <tr>
                  <td colSpan={8} className="p-0">
                    <div className="flex items-center px-4 py-1.5 bg-slate-50 border-t border-slate-100">
                      <button onClick={() => toggleBonusSchedule(p.id)} className="flex items-center gap-1 text-xs text-indigo-600 hover:text-indigo-800 font-medium">
                        {expandedPolicy === p.id ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                        <Calendar size={12} /> Bonus Schedule ({expandedPolicy === p.id ? bonusEntries.length : '...'})
                      </button>
                    </div>
                    {expandedPolicy === p.id && (
                      <div className="px-4 pb-4 bg-slate-50/50">
                        <div className="flex justify-end mb-2">
                          <button onClick={() => { setShowBonusForm(!showBonusForm); setBonusForm({ yearNumber: bonusEntries.length + 1, yearDate: '', age: 0, premiumAmount: p.annualPremium, expectedBonus: 0, expectedBonusTotal: 0, expectedTotal: 0, actualBonus: 0, actualBonusTotal: 0 }); }} className="text-xs px-2.5 py-1 bg-indigo-600 text-white rounded font-medium hover:bg-indigo-700">+ Add Year</button>
                        </div>
                        {showBonusForm && (
                          <form onSubmit={handleBonusSubmit} className="grid grid-cols-5 md:grid-cols-10 gap-2 mb-3 p-3 bg-white rounded-lg border border-slate-200">
                            <div><label className="block text-[10px] text-slate-500">#</label><input type="number" value={bonusForm.yearNumber} onChange={e => setBonusForm({...bonusForm, yearNumber: parseInt(e.target.value) || 0})} className="w-full border rounded px-2 py-1 text-xs" /></div>
                            <div><label className="block text-[10px] text-slate-500">Date</label><input type="text" value={bonusForm.yearDate} onChange={e => setBonusForm({...bonusForm, yearDate: e.target.value})} className="w-full border rounded px-2 py-1 text-xs" placeholder="10/2025" /></div>
                            <div><label className="block text-[10px] text-slate-500">Age</label><input type="number" value={bonusForm.age || ''} onChange={e => setBonusForm({...bonusForm, age: parseInt(e.target.value) || 0})} className="w-full border rounded px-2 py-1 text-xs" /></div>
                            <div><label className="block text-[10px] text-slate-500">Premium</label><input type="number" step="any" value={bonusForm.premiumAmount || ''} onChange={e => setBonusForm({...bonusForm, premiumAmount: parseFloat(e.target.value) || 0})} className="w-full border rounded px-2 py-1 text-xs" /></div>
                            <div><label className="block text-[10px] text-slate-500">Exp Bonus</label><input type="number" step="any" value={bonusForm.expectedBonus || ''} onChange={e => setBonusForm({...bonusForm, expectedBonus: parseFloat(e.target.value) || 0})} className="w-full border rounded px-2 py-1 text-xs" /></div>
                            <div><label className="block text-[10px] text-slate-500">Exp Total</label><input type="number" step="any" value={bonusForm.expectedBonusTotal || ''} onChange={e => setBonusForm({...bonusForm, expectedBonusTotal: parseFloat(e.target.value) || 0})} className="w-full border rounded px-2 py-1 text-xs" /></div>
                            <div><label className="block text-[10px] text-slate-500">Exp Grand</label><input type="number" step="any" value={bonusForm.expectedTotal || ''} onChange={e => setBonusForm({...bonusForm, expectedTotal: parseFloat(e.target.value) || 0})} className="w-full border rounded px-2 py-1 text-xs" /></div>
                            <div><label className="block text-[10px] text-slate-500">Act Bonus</label><input type="number" step="any" value={bonusForm.actualBonus || ''} onChange={e => setBonusForm({...bonusForm, actualBonus: parseFloat(e.target.value) || 0})} className="w-full border rounded px-2 py-1 text-xs" /></div>
                            <div><label className="block text-[10px] text-slate-500">Act Total</label><input type="number" step="any" value={bonusForm.actualBonusTotal || ''} onChange={e => setBonusForm({...bonusForm, actualBonusTotal: parseFloat(e.target.value) || 0})} className="w-full border rounded px-2 py-1 text-xs" /></div>
                            <div className="flex items-end gap-1"><button type="submit" className="px-2 py-1 bg-indigo-600 text-white rounded text-[10px] font-medium">Save</button><button type="button" onClick={() => setShowBonusForm(false)} className="px-2 py-1 bg-slate-200 rounded text-[10px]">X</button></div>
                          </form>
                        )}
                        {bonusEntries.length > 0 ? (
                          <table className="w-full text-[11px]">
                            <thead><tr className="border-b border-slate-200">
                              <th className="text-left px-2 py-1 text-slate-500">#</th>
                              <th className="text-left px-2 py-1 text-slate-500">Date</th>
                              <th className="text-left px-2 py-1 text-slate-500">Age</th>
                              <th className="text-right px-2 py-1 text-slate-500">Premium</th>
                              <th className="text-right px-2 py-1 text-slate-500">Exp Bonus</th>
                              <th className="text-right px-2 py-1 text-slate-500">Exp Bonus Total</th>
                              <th className="text-right px-2 py-1 text-slate-500">Expected Total</th>
                              <th className="text-right px-2 py-1 text-slate-500">Act Bonus</th>
                              <th className="text-right px-2 py-1 text-slate-500">Act Bonus Total</th>
                              <th className="px-2 py-1"></th>
                            </tr></thead>
                            <tbody>
                              {bonusEntries.map((be: any) => (
                                <tr key={be.id} className="border-b border-slate-100 hover:bg-white">
                                  <td className="px-2 py-1.5 font-medium">{be.yearNumber}</td>
                                  <td className="px-2 py-1.5">{be.yearDate || '-'}</td>
                                  <td className="px-2 py-1.5">{be.age || '-'}</td>
                                  <td className="px-2 py-1.5 text-right">{formatCurrency(be.premiumAmount)}</td>
                                  <td className="px-2 py-1.5 text-right text-indigo-600">{formatCurrency(be.expectedBonus)}</td>
                                  <td className="px-2 py-1.5 text-right">{formatCurrency(be.expectedBonusTotal)}</td>
                                  <td className="px-2 py-1.5 text-right font-medium">{formatCurrency(be.expectedTotal)}</td>
                                  <td className="px-2 py-1.5 text-right text-green-600">{formatCurrency(be.actualBonus)}</td>
                                  <td className="px-2 py-1.5 text-right font-medium text-green-700">{formatCurrency(be.actualBonusTotal)}</td>
                                  <td className="px-2 py-1.5"><button onClick={() => handleDeleteBonusEntry(be.id)} className="text-slate-300 hover:text-red-500"><Trash2 size={11} /></button></td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        ) : <p className="text-xs text-slate-400 text-center py-4">No bonus entries. Click "+ Add Year" to start tracking.</p>}
                      </div>
                    )}
                  </td>
                </tr>
                </React.Fragment>
              ))}
              {policies.length === 0 && <tr><td colSpan={8} className="px-4 py-12 text-center text-slate-400">No insurance policies tracked</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
