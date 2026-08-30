import { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2 } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { getTaxRecords, getTaxSummary, createTaxRecord, updateTaxRecord, deleteTaxRecord, getOwners } from '../api';
import { formatCurrency } from '../utils/formatters';
import SearchableSelect from '../components/SearchableSelect';
import ExportMenu from '../components/ExportMenu';
import { taxExportConfig } from '../utils/export/configs';
import { useToast } from '../contexts/ToastContext';
import type { Owner } from '../types';

interface TaxRecord {
  id: number;
  assessmentYear: number;
  employment: number;
  donations: number;
  reliefs: number;
  srsDeduction: number;
  chargeableIncome: number;
  tax: number;
  taxRebate: number;
  taxPayable: number;
  country: string;
  notes: string;
  owner?: Owner | null;
}

export default function Tax() {
  const [records, setRecords] = useState<TaxRecord[]>([]);
  const [owners, setOwners] = useState<Owner[]>([]);
  const [filterOwner, setFilterOwner] = useState<string>('');
  const [summary, setSummary] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<TaxRecord | null>(null);
  const { showToast } = useToast();
  const [form, setForm] = useState({
    assessmentYear: new Date().getFullYear(), employment: 0, donations: 0, reliefs: 0,
    srsDeduction: 0, chargeableIncome: 0, tax: 0, taxRebate: 0, taxPayable: 0,
    country: 'Singapore', notes: '', ownerId: 0
  });

  useEffect(() => { getOwners().then(r => setOwners(r.data)).catch(console.error); }, []);
  useEffect(() => { loadData(); }, [filterOwner]);

  const loadData = async () => {
    try {
      const ownerId = filterOwner ? Number(filterOwner) : undefined;
      const [recRes, sumRes] = await Promise.all([getTaxRecords(ownerId), getTaxSummary()]);
      setRecords(recRes.data); setSummary(sumRes.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const resetForm = () => setForm({
    assessmentYear: new Date().getFullYear(), employment: 0, donations: 0, reliefs: 0,
    srsDeduction: 0, chargeableIncome: 0, tax: 0, taxRebate: 0, taxPayable: 0,
    country: 'Singapore', notes: '', ownerId: 0
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = { ...form, owner: form.ownerId ? { id: form.ownerId } : null };
      if (editing) { await updateTaxRecord(editing.id, payload); }
      else { await createTaxRecord(payload); }
      setShowForm(false); setEditing(null); resetForm(); loadData();
    } catch (err) { console.error(err); showToast('Failed'); }
  };

  const startEdit = (r: TaxRecord) => {
    setEditing(r);
    setForm({
      assessmentYear: r.assessmentYear, employment: r.employment, donations: r.donations,
      reliefs: r.reliefs, srsDeduction: r.srsDeduction, chargeableIncome: r.chargeableIncome,
      tax: r.tax, taxRebate: r.taxRebate, taxPayable: r.taxPayable,
      country: r.country || 'Singapore', notes: r.notes || '', ownerId: r.owner?.id || 0
    });
    setShowForm(true);
  };

  const handleDelete = async (id: number) => { if (confirm('Delete?')) { await deleteTaxRecord(id); loadData(); } };

  // Auto-calc chargeable income
  const autoCalc = () => {
    const chargeable = form.employment - form.donations - form.reliefs - form.srsDeduction;
    const payable = form.tax - form.taxRebate;
    setForm({ ...form, chargeableIncome: Math.max(0, chargeable), taxPayable: Math.max(0, payable) });
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const chartData = [...records].reverse().map(r => ({
    year: `YA${r.assessmentYear}`,
    income: r.employment,
    tax: r.taxPayable,
  }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Tax Records</h1><p className="text-slate-500 text-sm mt-0.5">Track tax paid by assessment year</p></div>
        <div className="flex items-center gap-3">
          <div className="w-44">
            <SearchableSelect
              options={[{ value: '', label: 'All Owners' }, ...owners.map(o => ({ value: o.id.toString(), label: o.name, icon: o.name[0] }))]}
              value={filterOwner}
              onChange={v => setFilterOwner(v.toString())}
              placeholder="All Owners"
            />
          </div>
          <ExportMenu rows={records} config={taxExportConfig} />
          <button onClick={() => { setShowForm(!showForm); setEditing(null); resetForm(); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700"><Plus size={16} /> Add Year</button>
        </div>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Years Tracked</p><p className="text-lg font-bold text-slate-800 mt-1">{summary?.years || 0}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Total Income</p><p className="text-lg font-bold text-slate-800 mt-1">{formatCurrency(summary?.totalIncome || 0)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Total Tax Paid</p><p className="text-lg font-bold text-red-600 mt-1">{formatCurrency(summary?.totalTaxPaid || 0)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Effective Rate</p><p className="text-lg font-bold text-amber-600 mt-1">{summary?.totalIncome > 0 ? ((summary.totalTaxPaid / summary.totalIncome) * 100).toFixed(1) : 0}%</p></div>
      </div>

      {/* Chart */}
      {chartData.length > 0 && (
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Income vs Tax by Year</h3>
          <ResponsiveContainer width="100%" height={250}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="year" stroke="#94a3b8" fontSize={11} />
              <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `$${(v/1000).toFixed(0)}K`} />
              <Tooltip formatter={(v) => formatCurrency(v as number)} />
              <Legend />
              <Bar dataKey="income" fill="#6366f1" name="Employment" radius={[4, 4, 0, 0]} />
              <Bar dataKey="tax" fill="#ef4444" name="Tax Payable" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editing ? 'Edit Tax Record' : 'Add Tax Record'}</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Owner</label>
              <SearchableSelect options={[{ value: 0, label: 'Unassigned' }, ...owners.map(o => ({ value: o.id, label: o.name }))]} value={form.ownerId} onChange={v => setForm({...form, ownerId: Number(v)})} placeholder="Select owner..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Assessment Year *</label>
              <input type="number" value={form.assessmentYear} onChange={e => setForm({...form, assessmentYear: parseInt(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Employment Income *</label>
              <input type="number" step="any" value={form.employment || ''} onChange={e => setForm({...form, employment: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Donations</label>
              <input type="number" step="any" value={form.donations || ''} onChange={e => setForm({...form, donations: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Reliefs</label>
              <input type="number" step="any" value={form.reliefs || ''} onChange={e => setForm({...form, reliefs: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">SRS Deduction</label>
              <input type="number" step="any" value={form.srsDeduction || ''} onChange={e => setForm({...form, srsDeduction: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Chargeable Income</label>
              <input type="number" step="any" value={form.chargeableIncome || ''} onChange={e => setForm({...form, chargeableIncome: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Tax Computed</label>
              <input type="number" step="any" value={form.tax || ''} onChange={e => setForm({...form, tax: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Tax Rebate</label>
              <input type="number" step="any" value={form.taxRebate || ''} onChange={e => setForm({...form, taxRebate: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Tax Payable</label>
              <input type="number" step="any" value={form.taxPayable || ''} onChange={e => setForm({...form, taxPayable: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Country</label>
              <input type="text" value={form.country} onChange={e => setForm({...form, country: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Notes</label>
              <input type="text" value={form.notes} onChange={e => setForm({...form, notes: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div className="flex items-end gap-2">
              <button type="button" onClick={autoCalc} className="px-3 py-2 bg-slate-200 text-slate-700 rounded-lg text-xs font-medium hover:bg-slate-300">Auto Calc</button>
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
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Year</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Owner</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Employment</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Donations</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Reliefs</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">SRS</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Chargeable</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Tax</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Rebate</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600 text-red-600">Payable</th>
                <th className="px-3 py-2.5 w-16"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {records.map(r => (
                <tr key={r.id} className="hover:bg-slate-50 group">
                  <td className="px-3 py-2.5 font-medium text-slate-800">YA {r.assessmentYear}</td>
                  <td className="px-3 py-2.5 text-slate-500 text-xs">{r.owner?.name || '-'}</td>
                  <td className="px-3 py-2.5 text-right text-slate-700">{formatCurrency(r.employment)}</td>
                  <td className="px-3 py-2.5 text-right text-slate-500">{r.donations > 0 ? formatCurrency(r.donations) : '-'}</td>
                  <td className="px-3 py-2.5 text-right text-slate-500">{r.reliefs > 0 ? formatCurrency(r.reliefs) : '-'}</td>
                  <td className="px-3 py-2.5 text-right text-slate-500">{r.srsDeduction > 0 ? formatCurrency(r.srsDeduction) : '-'}</td>
                  <td className="px-3 py-2.5 text-right text-slate-700">{formatCurrency(r.chargeableIncome)}</td>
                  <td className="px-3 py-2.5 text-right text-slate-700">{formatCurrency(r.tax)}</td>
                  <td className="px-3 py-2.5 text-right text-green-600">{r.taxRebate > 0 ? formatCurrency(r.taxRebate) : '-'}</td>
                  <td className="px-3 py-2.5 text-right font-medium text-red-600">{formatCurrency(r.taxPayable)}</td>
                  <td className="px-3 py-2.5">
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => startEdit(r)} className="text-slate-400 hover:text-indigo-600"><Pencil size={13} /></button>
                      <button onClick={() => handleDelete(r.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {records.length === 0 && <tr><td colSpan={11} className="px-4 py-12 text-center text-slate-400">No tax records</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
