import { useEffect, useRef, useState } from 'react';
import { Plus, Pencil, Trash2, Copy } from 'lucide-react';
import { BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { getSalaryRecords, getSalarySummary, createSalaryRecord, updateSalaryRecord, deleteSalaryRecord, getWorkExperiences } from '../api';
import { formatCurrency } from '../utils/formatters';
import SearchableSelect from '../components/SearchableSelect';
import { useToast } from '../contexts/ToastContext';

interface SalaryRecord {
  id: number; year: number; month: number; company: string; amount: number;
  basic: number; allowance: number; mobile: number; support: number;
  weekend: number; mealAllowance: number; deductions: number;
  cpfEmployee: number; cpfEmployer: number;
  epfEmployee: number; epfEmployer: number; etfEmployer: number;
  contributionScheme: string; contributionRemitted: boolean;
  netTakeHome: number; employeeContributionTotal: number; employerContributionTotal: number;
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
  const [showBonusForm, setShowBonusForm] = useState(false);
  const [editing, setEditing] = useState<SalaryRecord | null>(null);
  const [filterYear, setFilterYear] = useState<number | undefined>();
  const [companies, setCompanies] = useState<string[]>([]);
  const [displayCurrency, setDisplayCurrency] = useState<string>('SGD');
  const formRef = useRef<HTMLDivElement>(null);
  const emptyForm = {
    year: new Date().getFullYear(), month: new Date().getMonth() + 1, company: '', amount: 0,
    basic: 0, allowance: 0, mobile: 0, support: 0, weekend: 0, mealAllowance: 0, deductions: 0,
    cpfEmployee: 0, cpfEmployer: 0, epfEmployee: 0, epfEmployer: 0, etfEmployer: 0,
    contributionScheme: 'CPF', contributionRemitted: false, currency: 'SGD',
    isBonus: false, bonusMonths: 0, country: 'Singapore', notes: ''
  };
  const [form, setForm] = useState({ ...emptyForm });
  const [bulkForm, setBulkForm] = useState({
    year: new Date().getFullYear(), company: '', fromMonth: 1, toMonth: 12,
    amount: 0, basic: 0, allowance: 0, mobile: 0, support: 0, weekend: 0, mealAllowance: 0, deductions: 0, country: 'Singapore',
    currency: 'SGD', contributionScheme: 'CPF', contributionRemitted: false,
    cpfEmployee: 0, cpfEmployer: 0, epfEmployee: 0, epfEmployer: 0, etfEmployer: 0
  });
  const emptyBonus = {
    year: new Date().getFullYear(), month: new Date().getMonth() + 1, company: '',
    amount: 0, bonusMonths: 0, currency: 'SGD', country: 'Singapore', notes: ''
  };
  const [bonusForm, setBonusForm] = useState({ ...emptyBonus });

  useEffect(() => { loadData(); loadCompanies(); }, []);

  // When any form opens, scroll it into view so the user doesn't have to hunt for it below the summary.
  useEffect(() => {
    if (showForm || showBulkForm || showBonusForm) {
      // Defer to next paint so the conditional form is mounted before we scroll to it.
      requestAnimationFrame(() => formRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
    }
  }, [showForm, showBulkForm, showBonusForm]);

  const loadData = async () => {
    try {
      // Always load ALL records; the year filter only highlights rows, it never hides other years.
      const [recRes, sumRes] = await Promise.all([getSalaryRecords(), getSalarySummary()]);
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

  const resetForm = () => setForm({ ...emptyForm });

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
      cpfEmployee: r.cpfEmployee || 0, cpfEmployer: r.cpfEmployer || 0,
      epfEmployee: r.epfEmployee || 0, epfEmployer: r.epfEmployer || 0, etfEmployer: r.etfEmployer || 0,
      contributionScheme: r.contributionScheme || SCHEME_BY_COUNTRY[r.country || 'Singapore'] || 'NONE',
      contributionRemitted: !!r.contributionRemitted,
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
          mobile: bulkForm.mobile, support: bulkForm.support, weekend: bulkForm.weekend, mealAllowance: bulkForm.mealAllowance,
          deductions: bulkForm.deductions, country: bulkForm.country,
          currency: bulkForm.currency, contributionScheme: bulkForm.contributionScheme,
          contributionRemitted: bulkForm.contributionRemitted,
          cpfEmployee: bulkForm.cpfEmployee, cpfEmployer: bulkForm.cpfEmployer,
          epfEmployee: bulkForm.epfEmployee, epfEmployer: bulkForm.epfEmployer, etfEmployer: bulkForm.etfEmployer,
          isBonus: false
        });
      }
      setShowBulkForm(false); loadData();
    } catch (err) { console.error(err); showToast('Failed to bulk add'); }
  };

  const handleBonusSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await createSalaryRecord({
        year: bonusForm.year, month: bonusForm.month, company: bonusForm.company,
        amount: bonusForm.amount, bonusMonths: bonusForm.bonusMonths,
        currency: bonusForm.currency, country: bonusForm.country, notes: bonusForm.notes,
        isBonus: true,
      });
      setShowBonusForm(false); setBonusForm({ ...emptyBonus }); loadData();
      showToast('Bonus added', 'success');
    } catch (err) { console.error(err); showToast('Failed to add bonus'); }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const years = [...new Set(records.map(r => r.year))].sort((a, b) => b - a);

  // Consolidated figures come from the backend summary in the user's base currency (FX-converted
  // per record). A display-currency toggle (driven by the user's own rates) re-scales for display
  // only — it never changes stored values. Mirrors the Bank Savings / Dashboard pattern.
  const baseCurrency = summary?.baseCurrency || 'SGD';
  const displayRates: Record<string, number> = summary?.displayRates || {};
  const displayOptions = Object.keys(displayRates).length > 0 ? Object.keys(displayRates) : [baseCurrency];
  const effectiveCurrency = displayRates[displayCurrency] != null ? displayCurrency : baseCurrency;
  const cFactor = displayRates[effectiveCurrency] ?? 1;
  // Summary/total figures show the compact abbreviation for high values (e.g. "(1.2M)").
  const disp = (baseAmount: number) => formatCurrency((baseAmount || 0) * cFactor, effectiveCurrency);

  const yearlyRows: any[] = summary?.yearly || [];
  const grandTotal = summary?.grandTotal || 0;
  const bonusTotalBase = yearlyRows.reduce((s, y) => s + (y.bonusTotal || 0), 0);
  const filteredYear = filterYear ? yearlyRows.find(y => y.year === filterYear) : null;
  const shownTotal = filteredYear ? filteredYear.total : grandTotal;
  const latestMonthlyAvg = yearlyRows.length > 0 ? yearlyRows[yearlyRows.length - 1].monthlyAvg : 0;

  // Annual bar chart + monthly-average growth line, in the selected display currency.
  const yearlyChart = yearlyRows.map((y: any) => ({ year: y.year, total: (y.total || 0) * cFactor }));
  const growthChart = (summary?.monthlyAvgSeries || []).map((p: any) => ({ year: p.year, monthlyAvg: (p.monthlyAvg || 0) * cFactor }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div><h1 className="text-2xl font-bold text-slate-800">Salary Records</h1><p className="text-slate-500 text-sm mt-0.5">Track monthly salary and bonuses</p></div>
        <div className="flex items-center gap-2">
          {/* Display currency toggle */}
          <div className="flex bg-white border border-slate-200 rounded-lg overflow-hidden">
            {displayOptions.map(code => (
              <button key={code} onClick={() => setDisplayCurrency(code)} title={`Show values in ${code}`}
                className={`px-3 py-1.5 text-xs font-medium transition-colors ${effectiveCurrency === code ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>{code}</button>
            ))}
          </div>
          <button onClick={() => { setShowBulkForm(!showBulkForm); setShowForm(false); setShowBonusForm(false); }} className="flex items-center gap-2 px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-200"><Copy size={16} /> Bulk Add</button>
          <button onClick={() => { setShowBonusForm(!showBonusForm); setShowForm(false); setShowBulkForm(false); setBonusForm({ ...emptyBonus }); }} className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg text-sm font-medium hover:bg-green-700"><Plus size={16} /> Add Bonus</button>
          <button onClick={() => { setShowForm(!showForm); setShowBulkForm(false); setShowBonusForm(false); setEditing(null); resetForm(); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700"><Plus size={16} /> Add Entry</button>
        </div>
      </div>

      {/* Summary (base currency, converted to the selected display currency) */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Grand Total <span className="normal-case text-slate-400">({effectiveCurrency})</span></p><p className="text-lg font-bold text-slate-800 mt-1">{disp(grandTotal)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">{filterYear ? `Year ${filterYear}` : 'All'} Total</p><p className="text-lg font-bold text-indigo-600 mt-1">{disp(shownTotal)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Bonus (All)</p><p className="text-lg font-bold text-green-600 mt-1">{disp(bonusTotalBase)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Latest Avg Take-Home</p><p className="text-lg font-bold text-slate-800 mt-1">{disp(latestMonthlyAvg)}</p></div>
      </div>

      {/* Yearly summary table */}
      {yearlyRows.length > 0 && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 border-b border-slate-200"><h3 className="text-sm font-semibold text-slate-800">Yearly Summary <span className="text-slate-400 font-normal">({effectiveCurrency})</span></h3></div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-2.5 font-medium text-slate-600">Year</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Take-Home Total</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Bonus</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Total</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Months</th>
                  <th className="text-right px-4 py-2.5 font-medium text-slate-600">Avg Monthly Take-Home</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {[...yearlyRows].sort((a, b) => b.year - a.year).map((y: any) => (
                  <tr key={y.year} className="hover:bg-slate-50">
                    <td className="px-4 py-2.5 font-medium text-slate-800">{y.year}</td>
                    <td className="px-4 py-2.5 text-right text-slate-600">{disp(y.salaryTotal)}</td>
                    <td className="px-4 py-2.5 text-right text-green-600">{y.bonusTotal ? disp(y.bonusTotal) : '-'}</td>
                    <td className="px-4 py-2.5 text-right font-medium text-slate-800">{disp(y.total)}</td>
                    <td className="px-4 py-2.5 text-right text-slate-500">{y.months}</td>
                    <td className="px-4 py-2.5 text-right text-indigo-600 font-medium">{disp(y.monthlyAvg)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Monthly-average growth chart */}
      {growthChart.length > 0 && (
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Monthly Average Take-Home Growth <span className="text-slate-400 font-normal">({effectiveCurrency})</span></h3>
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={growthChart}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="year" stroke="#94a3b8" fontSize={11} />
              <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `${(v/1000).toFixed(0)}K`} />
              <Tooltip formatter={(v) => formatCurrency(v as number, effectiveCurrency)} />
              <Line type="monotone" dataKey="monthlyAvg" stroke="#10b981" strokeWidth={2} dot={{ r: 3 }} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Chart */}
      {yearlyChart.length > 0 && (
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Annual Salary <span className="text-slate-400 font-normal">({effectiveCurrency})</span></h3>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={yearlyChart}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="year" stroke="#94a3b8" fontSize={11} />
              <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `${(v/1000).toFixed(0)}K`} />
              <Tooltip formatter={(v) => formatCurrency(v as number, effectiveCurrency)} />
              <Bar dataKey="total" fill="#6366f1" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Bulk Add Form */}
      {showBulkForm && (
        <div ref={formRef} className="bg-white rounded-xl p-6 border border-indigo-200 shadow-sm scroll-mt-4">
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
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Support</label>
              <input type="number" step="any" value={bulkForm.support || ''} onChange={e => setBulkForm({...bulkForm, support: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Weekend</label>
              <input type="number" step="any" value={bulkForm.weekend || ''} onChange={e => setBulkForm({...bulkForm, weekend: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Meal Allowance</label>
              <input type="number" step="any" value={bulkForm.mealAllowance || ''} onChange={e => setBulkForm({...bulkForm, mealAllowance: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Deductions</label>
              <input type="number" step="any" value={bulkForm.deductions || ''} onChange={e => setBulkForm({...bulkForm, deductions: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Currency</label>
              <SearchableSelect options={CURRENCY_OPTIONS.map(c => ({ value: c, label: c }))} value={bulkForm.currency} onChange={v => setBulkForm({...bulkForm, currency: String(v)})} placeholder="Currency" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Country</label>
              <SearchableSelect options={['Singapore','Sri Lanka'].map(c => ({ value: c, label: c }))} value={bulkForm.country}
                onChange={v => { const c = String(v); setBulkForm({...bulkForm, country: c, contributionScheme: SCHEME_BY_COUNTRY[c] || bulkForm.contributionScheme}); }} placeholder="Country" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Contribution Scheme</label>
              <SearchableSelect options={Object.keys(SCHEME_LABELS).map(k => ({ value: k, label: SCHEME_LABELS[k] }))} value={bulkForm.contributionScheme} onChange={v => setBulkForm({...bulkForm, contributionScheme: String(v)})} placeholder="Scheme" /></div>
            {(bulkForm.contributionScheme === 'CPF') && <>
              <div><label className="block text-xs font-medium text-slate-600 mb-1">CPF Employee</label>
                <input type="number" step="any" value={bulkForm.cpfEmployee || ''} onChange={e => setBulkForm({...bulkForm, cpfEmployee: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employee" /></div>
              <div><label className="block text-xs font-medium text-slate-600 mb-1">CPF Employer</label>
                <input type="number" step="any" value={bulkForm.cpfEmployer || ''} onChange={e => setBulkForm({...bulkForm, cpfEmployer: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employer" /></div>
            </>}
            {(bulkForm.contributionScheme === 'EPF_ETF' || bulkForm.contributionScheme === 'EPF') && <>
              <div><label className="block text-xs font-medium text-slate-600 mb-1">EPF Employee</label>
                <input type="number" step="any" value={bulkForm.epfEmployee || ''} onChange={e => setBulkForm({...bulkForm, epfEmployee: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employee ~8%" /></div>
              <div><label className="block text-xs font-medium text-slate-600 mb-1">EPF Employer</label>
                <input type="number" step="any" value={bulkForm.epfEmployer || ''} onChange={e => setBulkForm({...bulkForm, epfEmployer: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employer ~12%" /></div>
            </>}
            {(bulkForm.contributionScheme === 'EPF_ETF' || bulkForm.contributionScheme === 'ETF') && (
              <div><label className="block text-xs font-medium text-slate-600 mb-1">ETF Employer</label>
                <input type="number" step="any" value={bulkForm.etfEmployer || ''} onChange={e => setBulkForm({...bulkForm, etfEmployer: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employer ~3%" /></div>
            )}
            {bulkForm.contributionScheme !== 'NONE' && (
              <div className="flex items-end">
                <label className="flex items-center gap-2 cursor-pointer" title="Applies to all months added: employer actually remitted the contribution to the fund.">
                  <input type="checkbox" checked={bulkForm.contributionRemitted} onChange={e => setBulkForm({...bulkForm, contributionRemitted: e.target.checked})} className="rounded border-slate-300 text-indigo-600" />
                  <span className="text-sm text-slate-700">Remitted to fund</span>
                </label>
              </div>
            )}
            <div className="flex items-end gap-2 col-span-2 lg:col-span-3">
              <button type="submit" className="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">Add {bulkForm.toMonth - bulkForm.fromMonth + 1} Months</button>
              <button type="button" onClick={() => setShowBulkForm(false)} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Add Bonus Form */}
      {showBonusForm && (
        <div ref={formRef} className="bg-white rounded-xl p-6 border border-green-200 shadow-sm scroll-mt-4">
          <h3 className="text-base font-semibold text-slate-800 mb-1">Add Bonus</h3>
          <p className="text-xs text-slate-500 mb-4">Record a bonus for a specific year, month and company.</p>
          <form onSubmit={handleBonusSubmit} className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Year *</label>
              <input type="number" value={bonusForm.year} onChange={e => setBonusForm({...bonusForm, year: parseInt(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Month *</label>
              <SearchableSelect options={MONTHS.slice(1).map((m, i) => ({ value: i+1, label: m }))} value={bonusForm.month} onChange={v => setBonusForm({...bonusForm, month: Number(v)})} placeholder="Month" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Company *</label>
              <SearchableSelect options={companies.map(c => ({ value: c, label: c }))} value={bonusForm.company} onChange={v => setBonusForm({...bonusForm, company: v})} placeholder="Select company..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Bonus Amount *</label>
              <input type="number" step="any" value={bonusForm.amount || ''} onChange={e => setBonusForm({...bonusForm, amount: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Bonus Months</label>
              <input type="number" step="0.1" value={bonusForm.bonusMonths || ''} onChange={e => setBonusForm({...bonusForm, bonusMonths: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. 2.5" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Currency</label>
              <SearchableSelect options={CURRENCY_OPTIONS.map(c => ({ value: c, label: c }))} value={bonusForm.currency} onChange={v => setBonusForm({...bonusForm, currency: String(v)})} placeholder="Currency" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Country</label>
              <SearchableSelect options={['Singapore','Sri Lanka'].map(c => ({ value: c, label: c }))} value={bonusForm.country} onChange={v => setBonusForm({...bonusForm, country: String(v)})} placeholder="Country" /></div>
            <div className="col-span-2 md:col-span-3 lg:col-span-4"><label className="block text-xs font-medium text-slate-600 mb-1">Notes</label>
              <input type="text" value={bonusForm.notes} onChange={e => setBonusForm({...bonusForm, notes: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. Annual performance bonus, 13th month" /></div>
            <div className="flex items-end gap-2 col-span-2">
              <button type="submit" className="px-4 py-2 bg-green-600 text-white rounded-lg text-sm font-medium hover:bg-green-700">Add Bonus</button>
              <button type="button" onClick={() => setShowBonusForm(false)} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
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
        <div ref={formRef} className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm scroll-mt-4">
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
            {(form.contributionScheme === 'CPF') && <>
              <div><label className="block text-xs font-medium text-slate-600 mb-1">CPF Employee</label>
                <input type="number" step="any" value={form.cpfEmployee || ''} onChange={e => setForm({...form, cpfEmployee: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employee" /></div>
              <div><label className="block text-xs font-medium text-slate-600 mb-1">CPF Employer</label>
                <input type="number" step="any" value={form.cpfEmployer || ''} onChange={e => setForm({...form, cpfEmployer: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employer" /></div>
            </>}
            {(form.contributionScheme === 'EPF_ETF' || form.contributionScheme === 'EPF') && <>
              <div><label className="block text-xs font-medium text-slate-600 mb-1">EPF Employee</label>
                <input type="number" step="any" value={form.epfEmployee || ''} onChange={e => setForm({...form, epfEmployee: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employee ~8%" /></div>
              <div><label className="block text-xs font-medium text-slate-600 mb-1">EPF Employer</label>
                <input type="number" step="any" value={form.epfEmployer || ''} onChange={e => setForm({...form, epfEmployer: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employer ~12%" /></div>
            </>}
            {(form.contributionScheme === 'EPF_ETF' || form.contributionScheme === 'ETF') && (
              <div><label className="block text-xs font-medium text-slate-600 mb-1">ETF Employer</label>
                <input type="number" step="any" value={form.etfEmployer || ''} onChange={e => setForm({...form, etfEmployer: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Employer ~3%" /></div>
            )}
            {form.contributionScheme !== 'NONE' && (
              <div className="flex items-end">
                <label className="flex items-center gap-2 cursor-pointer" title="Tick once you've confirmed (e.g. via the EPF/CPF statement) that the employer actually paid the contribution to the fund.">
                  <input type="checkbox" checked={form.contributionRemitted} onChange={e => setForm({...form, contributionRemitted: e.target.checked})} className="rounded border-slate-300 text-indigo-600" />
                  <span className="text-sm text-slate-700">Remitted to fund</span>
                </label>
              </div>
            )}
            <div className="col-span-2 md:col-span-3 lg:col-span-6"><label className="block text-xs font-medium text-slate-600 mb-1">Notes</label>
              <input type="text" value={form.notes} onChange={e => setForm({...form, notes: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Optional notes" /></div>
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
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Ccy</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Amount</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">Net Take-Home</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">CPF (Emp/Empr)</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">EPF (Emp/Empr)</th>
                <th className="text-right px-3 py-2.5 font-medium text-slate-600">ETF</th>
                <th className="text-center px-3 py-2.5 font-medium text-slate-600">Fund</th>
                <th className="text-left px-3 py-2.5 font-medium text-slate-600">Type</th>
                <th className="px-3 py-2.5 w-16"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {records.filter(r => !filterYear || r.year === filterYear).map(r => (
                <tr key={r.id} className={`hover:bg-slate-50 group ${r.isBonus ? 'bg-green-50/50' : ''}`}>
                  <td className="px-3 py-2.5 text-slate-800 font-medium text-xs">{MONTHS[r.month]} {r.year}</td>
                  <td className="px-3 py-2.5 text-slate-600 text-xs">
                    {r.company}
                    {r.notes && <span className="block text-[10px] text-slate-400" title={r.notes}>{r.notes.length > 40 ? r.notes.slice(0, 40) + '…' : r.notes}</span>}
                  </td>
                  <td className="px-3 py-2.5 text-xs font-medium text-indigo-600">{r.currency || 'SGD'}</td>
                  <td className="px-3 py-2.5 text-right font-medium text-slate-800">{formatCurrency(r.amount, r.currency || 'SGD', { exact: true })}</td>
                  <td className="px-3 py-2.5 text-right text-slate-700">{formatCurrency(r.netTakeHome ?? r.amount, r.currency || 'SGD', { exact: true })}</td>
                  <td className="px-3 py-2.5 text-right text-slate-600 text-xs">{(r.cpfEmployee || r.cpfEmployer) ? `${formatCurrency(r.cpfEmployee || 0, r.currency || 'SGD', { exact: true })} / ${formatCurrency(r.cpfEmployer || 0, r.currency || 'SGD', { exact: true })}` : '-'}</td>
                  <td className="px-3 py-2.5 text-right text-slate-600 text-xs">{(r.epfEmployee || r.epfEmployer) ? `${formatCurrency(r.epfEmployee || 0, r.currency || 'SGD', { exact: true })} / ${formatCurrency(r.epfEmployer || 0, r.currency || 'SGD', { exact: true })}` : '-'}</td>
                  <td className="px-3 py-2.5 text-right text-slate-600 text-xs">{r.etfEmployer ? formatCurrency(r.etfEmployer, r.currency || 'SGD', { exact: true }) : '-'}</td>
                  <td className="px-3 py-2.5 text-center">
                    {(r.cpfEmployee || r.cpfEmployer || r.epfEmployee || r.epfEmployer || r.etfEmployer)
                      ? (r.contributionRemitted
                          ? <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-green-100 text-green-700 font-medium" title="Employer remitted to fund">Remitted</span>
                          : <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-amber-100 text-amber-700 font-medium" title="Deducted but not confirmed remitted to the fund">Not remitted</span>)
                      : <span className="text-[10px] text-slate-300">-</span>}
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
              {records.filter(r => !filterYear || r.year === filterYear).length === 0 && <tr><td colSpan={11} className="px-4 py-12 text-center text-slate-400">No salary records{filterYear ? ` for ${filterYear}` : ''}</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
