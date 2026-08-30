import { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, Copy } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { getSalaryRecords, getSalarySummary, createSalaryRecord, updateSalaryRecord, deleteSalaryRecord, getWorkExperiences } from '../api';
import { formatCurrency } from '../utils/formatters';
import SearchableSelect from '../components/SearchableSelect';
import { useToast } from '../contexts/ToastContext';

interface SalaryRecord {
  id: number; year: number; month: number; company: string; amount: number;
  basic: number; allowance: number; mobile: number; support: number;
  weekend: number; mealAllowance: number; deductions: number;
  employeeContribution: number; employerContribution: number; contributionScheme: string;
  currency: string;
  isBonus: boolean; bonusMonths: number; country: string; notes: string;
}

const MONTHS = ['', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const CURRENCY_OPTIONS = ['SGD', 'USD', 'EUR', 'LKR', 'INR', 'GBP', 'AUD', 'JPY', 'CNY', 'MYR', 'THB', 'HKD', 'NZD', 'CHF', 'CAD'];

// Contribution scheme presets by country (default when the user picks a country).
const SCHEME_BY_COUNTRY: Record<string, string> = { 'Singapore': 'CPF', 'Sri Lanka': 'EPF_ETF' };
const SCHEME_LABELS: Record<string, string> = {
  CPF: 'CPF', EPF_ETF: 'EPF + ETF', EPF: 'EPF', ETF: 'ETF', NONE: 'None', OTHER: 'Other',
};

export default function Salary() {
  const [records, setRecords] = useState<SalaryRecord[]>([]);
  const [summary, setSummary] = useState<any>(null);
  const { showToast } = useToast();
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [showBulkForm, setShowBulkForm] = useState(false);
  const [editing, setEditing] = useState<SalaryRecord | null>(null);
  const [filterYear, setFilterYear] = useState<number | undefined>();
  const [companies, setCompanies] = useState<string[]>([]);
  const [form, setForm] = useState({
    year: new Date().getFullYear(), month: new Date().getMonth() + 1, company: '', amount: 0,
    basic: 0, allowance: 0, mobile: 0, support: 0, weekend: 0, mealAllowance: 0, deductions: 0,
    employeeContribution: 0, employerContribution: 0, contributionScheme: 'CPF',
    currency: 'SGD',
    isBonus: false, bonusMonths: 0, country: 'Singapore', notes: ''
  });
  const [bulkForm, setBulkForm] = useState({
    year: new Date().getFullYear(), company: '', fromMonth: 1, toMonth: 12,
    amount: 0, basic: 0, allowance: 0, mobile: 0, deductions: 0, country: 'Singapore',
    currency: 'SGD', contributionScheme: 'CPF', employeeContribution: 0, employerContribution: 0
  });

  useEffect(() => { loadData(); loadCompanies(); }, [filterYear]);

  const loadData = async () => {
    try {
      const [recRes, sumRes] = await Promise.all([getSalaryRecords(filterYear), getSalarySummary()]);
      setRecords(recRes.data); setSummary(sumRes.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const loadCompanies = async () => {
    try {
      const res = await getWorkExperiences();
      const companyNames = [...new Set(res.data.map((e: any) => e.company))];
      setCompanies(companyNames);
    } catch {}
  };

  const resetForm = () => setForm({
    year: new Date().getFullYear(), month: new Date().getMonth() + 1, company: '', amount: 0,
    basic: 0, allowance: 0, mobile: 0, support: 0, weekend: 0, mealAllowance: 0, deductions: 0,
    employeeContribution: 0, employerContribution: 0, contributionScheme: 'CPF',
    currency: 'SGD',
    isBonus: false, bonusMonths: 0, country: 'Singapore', notes: ''
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editing) { await updateSalaryRecord(editing.id, form); }
      else { await createSalaryRecord(form); }
      setShowForm(false); setEditing(null); resetForm(); loadData();
    } catch (err) { console.error(err); showToast('Failed'); }
  };

  const startEdit = (r: SalaryRecord) => {
    setEditing(r);
    setForm({
      year: r.year, month: r.month, company: r.company, amount: r.amount,
      basic: r.basic || 0, allowance: r.allowance || 0, mobile: r.mobile || 0,
      support: r.support || 0, weekend: r.weekend || 0, mealAllowance: r.mealAllowance || 0,
      deductions: r.deductions || 0,
      employeeContribution: r.employeeContribution || 0, employerContribution: r.employerContribution || 0,
      contributionScheme: r.contributionScheme || SCHEME_BY_COUNTRY[r.country || 'Singapore'] || 'NONE',
      currency: r.currency || 'SGD',
      isBonus: r.isBonus, bonusMonths: r.bonusMonths || 0,
      country: r.country || 'Singapore', notes: r.notes || ''
    });
    setShowForm(true);
  };

  const handleDelete = async (id: number) => { if (confirm('Delete?')) { await deleteSalaryRecord(id); loadData(); } };

  const handleBulkSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      for (let m = bulkForm.fromMonth; m <= bulkForm.toMonth; m++) {
        await createSalaryRecord({
          year: bulkForm.year, month: m, company: bulkForm.company,
          amount: bulkForm.amount, basic: bulkForm.basic, allowance: bulkForm.allowance,
          mobile: bulkForm.mobile, deductions: bulkForm.deductions, country: bulkForm.country,
          currency: bulkForm.currency, contributionScheme: bulkForm.contributionScheme,
          employeeContribution: bulkForm.employeeContribution, employerContribution: bulkForm.employerContribution,
          isBonus: false
        });
      }
      setShowBulkForm(false); loadData();
    } catch (err) { console.error(err); showToast('Failed to bulk add'); }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const yearlyChart = summary?.yearly?.map((y: any) => ({ year: y.year, total: y.total })) || [];
  const years = [...new Set(records.map(r => r.year))].sort((a, b) => b - a);
  const yearTotal = records.reduce((s, r) => s + r.amount, 0);
  const bonusTotal = records.filter(r => r.isBonus).reduce((s, r) => s + r.amount, 0);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Salary Records</h1><p className="text-slate-500 text-sm mt-0.5">Track monthly salary and bonuses</p></div>
        <div className="flex gap-2">
          <button onClick={() => { setShowBulkForm(!showBulkForm); setShowForm(false); }} className="flex items-center gap-2 px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-200"><Copy size={16} /> Bulk Add</button>
          <button onClick={() => { setShowForm(!showForm); setShowBulkForm(false); setEditing(null); resetForm(); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700"><Plus size={16} /> Add Entry</button>
        </div>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Grand Total</p><p className="text-lg font-bold text-slate-800 mt-1">{formatCurrency(summary?.grandTotal || 0)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">{filterYear ? `Year ${filterYear}` : 'Showing'} Total</p><p className="text-lg font-bold text-indigo-600 mt-1">{formatCurrency(yearTotal)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Bonus ({filterYear || 'All'})</p><p className="text-lg font-bold text-green-600 mt-1">{formatCurrency(bonusTotal)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Monthly Avg</p><p className="text-lg font-bold text-slate-800 mt-1">{formatCurrency(records.length > 0 ? yearTotal / records.filter(r => !r.isBonus).length : 0)}</p></div>
      </div>

      {/* Chart */}
      {yearlyChart.length > 0 && (
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Annual Salary</h3>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={yearlyChart}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="year" stroke="#94a3b8" fontSize={11} />
              <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `$${(v/1000).toFixed(0)}K`} />
              <Tooltip formatter={(v) => formatCurrency(v as number)} />
              <Bar dataKey="total" fill="#6366f1" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Bulk Add Form */}
      {showBulkForm && (
        <div className="bg-white rounded-xl p-6 border border-indigo-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-1">Bulk Add Salary</h3>
          <p className="text-xs text-slate-500 mb-4">Add the same salary for multiple months in one go (useful when salary is fixed for the year).</p>
          <form onSubmit={handleBulkSubmit} className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Year *</label>
              <input type="number" value={bulkForm.year} onChange={e => setBulkForm({...bulkForm, year: parseInt(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Company *</label>
              <SearchableSelect options={companies.map(c => ({ value: c, label: c }))} value={bulkForm.company} onChange={v => setBulkForm({...bulkForm, company: v})} placeholder="Select..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">From Month</label>
              <SearchableSelect options={MONTHS.slice(1).map((m, i) => ({ value: i+1, label: m }))} value={bulkForm.fromMonth} onChange={v => setBulkForm({...bulkForm, fromMonth: Number(v)})} placeholder="From" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">To Month</label>
              <SearchableSelect options={MONTHS.slice(1).map((m, i) => ({ value: i+1, label: m }))} value={bulkForm.toMonth} onChange={v => setBulkForm({...bulkForm, toMonth: Number(v)})} placeholder="To" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Amount *</label>
              <input type="number" step="any" value={bulkForm.amount || ''} onChange={e => setBulkForm({...bulkForm, amount: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Basic</label>
              <input type="number" step="any" value={bulkForm.basic || ''} onChange={e => setBulkForm({...bulkForm, basic: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Allowance</label>
              <input type="number" step="any" value={bulkForm.allowance || ''} onChange={e => setBulkForm({...bulkForm, allowance: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Mobile</label>
              <input type="number" step="any" value={bulkForm.mobile || ''} onChange={e => setBulkForm({...bulkForm, mobile: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Deductions</label>
              <input type="number" step="any" value={bulkForm.deductions || ''} onChange={e => setBulkForm({...bulkForm, deductions: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Currency</label>
              <SearchableSelect options={CURRENCY_OPTIONS.map(c => ({ value: c, label: c }))} value={bulkForm.currency} onChange={v => setBulkForm({...bulkForm, currency: String(v)})} placeholder="Currency" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Country</label>
              <SearchableSelect options={['Singapore','Sri Lanka'].map(c => ({ value: c, label: c }))} value={bulkForm.country}
                onChange={v => { const c = String(v); setBulkForm({...bulkForm, country: c, contributionScheme: SCHEME_BY_COUNTRY[c] || bulkForm.contributionScheme}); }} placeholder="Country" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Contribution Scheme</label>
              <SearchableSelect options={Object.keys(SCHEME_LABELS).map(k => ({ value: k, label: SCHEME_LABELS[k] }))} value={bulkForm.contributionScheme} onChange={v => setBulkForm({...bulkForm, contributionScheme: String(v)})} placeholder="Scheme" /></div>
            {bulkForm.contributionScheme !== 'NONE' && <>
              <div><label className="block text-xs font-medium text-slate-600 mb-1">Employee {SCHEME_LABELS[bulkForm.contributionScheme] || ''}</label>
                <input type="number" step="any" value={bulkForm.employeeContribution || ''} onChange={e => setBulkForm({...bulkForm, employeeContribution: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employee share" /></div>
              <div><label className="block text-xs font-medium text-slate-600 mb-1">Employer {SCHEME_LABELS[bulkForm.contributionScheme] || ''}</label>
                <input type="number" step="any" value={bulkForm.employerContribution || ''} onChange={e => setBulkForm({...bulkForm, employerContribution: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employer share" /></div>
            </>}
            <div className="flex items-end gap-2 col-span-2 lg:col-span-3">
              <button type="submit" className="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">Add {bulkForm.toMonth - bulkForm.fromMonth + 1} Months</button>
              <button type="button" onClick={() => setShowBulkForm(false)} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Year Filter */}
      <div className="flex items-center gap-2 flex-wrap">
        <button onClick={() => setFilterYear(undefined)} className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors ${!filterYear ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-300 hover:border-indigo-300'}`}>All</button>
        {years.map(y => (
          <button key={y} onClick={() => setFilterYear(y)} className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors ${filterYear === y ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-300 hover:border-indigo-300'}`}>{y}</button>
        ))}
      </div>

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editing ? 'Edit Entry' : 'Add Salary Entry'}</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Year *</label>
              <input type="number" value={form.year} onChange={e => setForm({...form, year: parseInt(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Month *</label>
              <SearchableSelect options={MONTHS.slice(1).map((m, i) => ({ value: i+1, label: m }))} value={form.month} onChange={v => setForm({...form, month: Number(v)})} placeholder="Month" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Company *</label>
              <SearchableSelect options={companies.map(c => ({ value: c, label: c }))} value={form.company} onChange={v => setForm({...form, company: v})} placeholder="Select company..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Amount *</label>
              <input type="number" step="any" value={form.amount || ''} onChange={e => setForm({...form, amount: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Basic</label>
              <input type="number" step="any" value={form.basic || ''} onChange={e => setForm({...form, basic: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Allowance</label>
              <input type="number" step="any" value={form.allowance || ''} onChange={e => setForm({...form, allowance: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Mobile</label>
              <input type="number" step="any" value={form.mobile || ''} onChange={e => setForm({...form, mobile: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Support</label>
              <input type="number" step="any" value={form.support || ''} onChange={e => setForm({...form, support: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Weekend</label>
              <input type="number" step="any" value={form.weekend || ''} onChange={e => setForm({...form, weekend: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Deductions</label>
              <input type="number" step="any" value={form.deductions || ''} onChange={e => setForm({...form, deductions: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Currency</label>
              <SearchableSelect options={CURRENCY_OPTIONS.map(c => ({ value: c, label: c }))} value={form.currency} onChange={v => setForm({...form, currency: String(v)})} placeholder="Currency" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Country</label>
              <SearchableSelect options={['Singapore','Sri Lanka'].map(c => ({ value: c, label: c }))} value={form.country}
                onChange={v => { const c = String(v); setForm({...form, country: c, contributionScheme: SCHEME_BY_COUNTRY[c] || form.contributionScheme}); }} placeholder="Country" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Contribution Scheme</label>
              <SearchableSelect options={Object.keys(SCHEME_LABELS).map(k => ({ value: k, label: SCHEME_LABELS[k] }))} value={form.contributionScheme} onChange={v => setForm({...form, contributionScheme: String(v)})} placeholder="Scheme" /></div>
            {form.contributionScheme !== 'NONE' && <>
              <div><label className="block text-xs font-medium text-slate-600 mb-1">Employee {SCHEME_LABELS[form.contributionScheme] || ''}</label>
                <input type="number" step="any" value={form.employeeContribution || ''} onChange={e => setForm({...form, employeeContribution: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employee share" /></div>
              <div><label className="block text-xs font-medium text-slate-600 mb-1">Employer {SCHEME_LABELS[form.contributionScheme] || ''}</label>
                <input type="number" step="any" value={form.employerContribution || ''} onChange={e => setForm({...form, employerContribution: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employer share" /></div>
            </>}
            <div className="flex items-end">
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={form.isBonus} onChange={e => setForm({...form, isBonus: e.target.checked})} className="rounded border-slate-300 text-indigo-600" />
                <span className="text-sm text-slate-700">Bonus</span>
              </label></div>
            {form.isBonus && <div><label className="block text-xs font-medium text-slate-600 mb-1">Bonus Months</label>
              <input type="number" step="0.1" value={form.bonusMonths || ''} onChange={e => setForm({...form, bonusMonths: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>}
            <div className="flex items-end gap-2 col-span-2">
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
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Month</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Company</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Amount</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Basic</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Contributions (Emp / Empr)</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Type</th>
                <th className="px-3 py-2.5 w-16"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {records.map(r => (
                <tr key={r.id} className={`hover:bg-slate-50 group ${r.isBonus ? 'bg-green-50/50' : ''}`}>
                  <td className="px-3 py-2.5 text-slate-800 font-medium text-xs">{MONTHS[r.month]} {r.year}</td>
                  <td className="px-3 py-2.5 text-slate-600 text-xs">{r.company}</td>
                  <td className="px-3 py-2.5 text-right font-medium text-slate-800">{formatCurrency(r.amount, r.currency || 'SGD')}</td>
                  <td className="px-3 py-2.5 text-right text-slate-600">{r.basic ? formatCurrency(r.basic, r.currency || 'SGD') : '-'}</td>
                  <td className="px-3 py-2.5 text-right text-slate-600">
                    {(r.employeeContribution || r.employerContribution) ? (
                      <span className="text-xs">
                        {formatCurrency(r.employeeContribution || 0, r.currency || 'SGD')} / {formatCurrency(r.employerContribution || 0, r.currency || 'SGD')}
                        {r.contributionScheme && r.contributionScheme !== 'NONE' && <span className="ml-1 text-[10px] text-slate-400">{SCHEME_LABELS[r.contributionScheme] || r.contributionScheme}</span>}
                      </span>
                    ) : '-'}
                  </td>
                  <td className="px-3 py-2.5">{r.isBonus ? <span className="text-[10px] px-1.5 py-0.5 rounded bg-green-100 text-green-700 font-medium">BONUS {r.bonusMonths ? `(${r.bonusMonths}mo)` : ''}</span> : <span className="text-[10px] text-slate-400">Salary</span>}</td>
                  <td className="px-3 py-2.5">
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => startEdit(r)} className="text-slate-400 hover:text-indigo-600"><Pencil size={13} /></button>
                      <button onClick={() => handleDelete(r.id)} className="text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {records.length === 0 && <tr><td colSpan={7} className="px-4 py-12 text-center text-slate-400">No salary records</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
