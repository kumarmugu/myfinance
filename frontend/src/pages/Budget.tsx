import { useEffect, useState } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { Plus, Trash2, Pencil, Save, X, AlertTriangle } from 'lucide-react';
import api from '../api';
import { formatCurrency } from '../utils/formatters';
import { useToast } from '../contexts/ToastContext';

type Tab = 'plan' | 'expenses' | 'report' | 'categories';

interface Category { id: number; name: string; parentCategory?: string; }
interface BudgetPlan { id: number; year: number; month: number; savingsTargetPct: number; }
interface IncomeEntry { id: number; budgetPlan: { id: number }; source: string; amount: number; }
interface Allocation { id: number; budgetPlan: { id: number }; category: Category; plannedAmount: number; }
interface Expense { id: number; expenseDate: string; description: string; category: Category; amount: number; }
interface MonthlyReport {
  totalPlannedIncome: number; targetSavings: number; availableForExpenses: number;
  totalPlannedExpense: number; totalActualExpense: number; actualSavings: number;
  isOverBudget: boolean; excess: number;
  categories: { categoryName: string; planned: number; actual: number; variance: number; status: string; }[];
}

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

export default function Budget() {
  const [tab, setTab] = useState<Tab>('plan');
  const { showToast } = useToast();
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [loading, setLoading] = useState(true);

  // Plan state
  const [plan, setPlan] = useState<BudgetPlan | null>(null);
  const [savingsPct, setSavingsPct] = useState(20);
  const [incomes, setIncomes] = useState<IncomeEntry[]>([]);
  const [allocations, setAllocations] = useState<Allocation[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [newIncome, setNewIncome] = useState({ source: '', amount: '' });
  const [newAlloc, setNewAlloc] = useState({ categoryId: '', amount: '' });

  // Expenses state
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [expForm, setExpForm] = useState({ id: 0, expenseDate: '', description: '', categoryId: '', amount: '' });
  const [editingExpense, setEditingExpense] = useState(false);

  // Report state
  const [report, setReport] = useState<MonthlyReport | null>(null);

  // Categories state
  const [newCatName, setNewCatName] = useState('');
  const [newCatParent, setNewCatParent] = useState('Essential');
  const [editingCat, setEditingCat] = useState<Category | null>(null);

  useEffect(() => { loadCategories(); }, []);
  useEffect(() => { loadTabData(); }, [tab, year, month]);

  const loadCategories = async () => {
    try {
      const res = await api.get<Category[]>('/budget/categories');
      setCategories(res.data);
    } catch { /* categories might not exist yet */ }
  };

  const loadTabData = async () => {
    setLoading(true);
    try {
      if (tab === 'plan') await loadPlan();
      else if (tab === 'expenses') await loadExpenses();
      else await loadReport();
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const loadPlan = async () => {
    try {
      const res = await api.get(`/budget/plans/${year}/${month}`);
      if (res.status === 204 || !res.data) { setPlan(null); setIncomes([]); setAllocations([]); setSavingsPct(20); return; }
      const p = res.data as BudgetPlan;
      setPlan(p);
      setSavingsPct(p.savingsTargetPct);
      const [incRes, allocRes] = await Promise.all([
        api.get<IncomeEntry[]>(`/budget/income/${p.id}`),
        api.get<Allocation[]>(`/budget/allocations/${p.id}`),
      ]);
      setIncomes(incRes.data);
      setAllocations(allocRes.data);
    } catch (err: any) {
      if (err.response?.status === 204) { setPlan(null); setIncomes([]); setAllocations([]); }
    }
  };

  const loadExpenses = async () => {
    const res = await api.get<Expense[]>(`/expenses?year=${year}&month=${month}`);
    setExpenses(res.data);
  };

  const loadReport = async () => {
    try {
      const res = await api.get<MonthlyReport>(`/budget/report/${year}/${month}`);
      setReport(res.data);
    } catch { setReport(null); }
  };

  // ─── Plan Actions ───
  const savePlan = async () => {
    try {
      const res = await api.post<BudgetPlan>('/budget/plans', { year, month, savingsTargetPct: savingsPct });
      setPlan(res.data);
      showToast('Budget plan saved', 'success');
    } catch { showToast('Failed to save plan'); }
  };

  const addIncome = async () => {
    if (!plan || !newIncome.source || !newIncome.amount) return;
    try {
      await api.post('/budget/income', { budgetPlan: { id: plan.id }, source: newIncome.source, amount: parseFloat(newIncome.amount) });
      setNewIncome({ source: '', amount: '' });
      loadPlan();
    } catch { showToast('Failed to add income'); }
  };

  const deleteIncome = async (id: number) => {
    try { await api.delete(`/budget/income/${id}`); loadPlan(); } catch { showToast('Failed to delete'); }
  };

  const addAllocation = async () => {
    if (!plan || !newAlloc.categoryId || !newAlloc.amount) return;
    try {
      await api.post('/budget/allocations', { budgetPlan: { id: plan.id }, category: { id: parseInt(newAlloc.categoryId) }, plannedAmount: parseFloat(newAlloc.amount) });
      setNewAlloc({ categoryId: '', amount: '' });
      loadPlan();
    } catch { showToast('Failed to add allocation'); }
  };

  const deleteAllocation = async (id: number) => {
    try { await api.delete(`/budget/allocations/${id}`); loadPlan(); } catch { showToast('Failed to delete'); }
  };

  // ─── Category Actions ───
  const addCategory = async () => {
    if (!newCatName.trim()) return;
    try {
      await api.post('/budget/categories', { name: newCatName.trim(), parentCategory: newCatParent, sortOrder: categories.length + 1 });
      setNewCatName('');
      loadCategories();
      showToast('Category added', 'success');
    } catch (err: any) {
      showToast(err.response?.data?.error || 'Failed to add category');
    }
  };

  const saveEditCategory = async () => {
    if (!editingCat) return;
    try {
      await api.put(`/budget/categories/${editingCat.id}`, { name: editingCat.name, parentCategory: editingCat.parentCategory, sortOrder: (editingCat as any).sortOrder || 0, isActive: true });
      setEditingCat(null);
      loadCategories();
      showToast('Category updated', 'success');
    } catch { showToast('Failed to update category'); }
  };

  const deleteCategory = async (id: number) => {
    if (!confirm('Delete this category? It will be hidden from future use.')) return;
    try {
      await api.delete(`/budget/categories/${id}`);
      loadCategories();
      showToast('Category deleted', 'success');
    } catch { showToast('Failed to delete category'); }
  };

  // ─── Expense Actions ───
  const saveExpense = async () => {
    if (!expForm.expenseDate || !expForm.description || !expForm.categoryId || !expForm.amount) return;
    const payload = { expenseDate: expForm.expenseDate, description: expForm.description, category: { id: parseInt(expForm.categoryId) }, amount: parseFloat(expForm.amount) };
    try {
      if (editingExpense && expForm.id) {
        await api.put(`/expenses/${expForm.id}`, payload);
        showToast('Expense updated', 'success');
      } else {
        await api.post('/expenses', payload);
        showToast('Expense added', 'success');
      }
      resetExpForm();
      loadExpenses();
    } catch { showToast('Failed to save expense'); }
  };

  const editExpense = (e: Expense) => {
    setExpForm({ id: e.id, expenseDate: e.expenseDate, description: e.description, categoryId: String(e.category.id), amount: String(e.amount) });
    setEditingExpense(true);
  };

  const deleteExpense = async (id: number) => {
    try { await api.delete(`/expenses/${id}`); loadExpenses(); showToast('Expense deleted', 'success'); } catch { showToast('Failed to delete'); }
  };

  const resetExpForm = () => { setExpForm({ id: 0, expenseDate: '', description: '', categoryId: '', amount: '' }); setEditingExpense(false); };

  // ─── Computed values ───
  const totalIncome = incomes.reduce((s, i) => s + i.amount, 0);
  const totalAllocated = allocations.reduce((s, a) => s + a.plannedAmount, 0);
  const savingsAmount = totalIncome * (savingsPct / 100);
  const availableForExpenses = totalIncome - savingsAmount;
  const isOverAllocated = totalAllocated > availableForExpenses;

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Budget & Expenses</h1>
        <p className="text-slate-500 text-sm mt-1">Plan your monthly budget, track expenses, and review performance</p>
      </div>

      {/* Month/Year Selector */}
      <div className="flex items-center gap-3">
        <select value={month} onChange={e => setMonth(parseInt(e.target.value))} className="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500">
          {MONTHS.map((m, i) => <option key={i} value={i + 1}>{m}</option>)}
        </select>
        <select value={year} onChange={e => setYear(parseInt(e.target.value))} className="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500">
          {[2024, 2025, 2026, 2027].map(y => <option key={y} value={y}>{y}</option>)}
        </select>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-slate-100 rounded-lg p-1 w-fit">
        {(['plan', 'expenses', 'report', 'categories'] as Tab[]).map(t => (
          <button key={t} onClick={() => setTab(t)} className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${tab === t ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'}`}>
            {t === 'plan' ? 'Plan' : t === 'expenses' ? 'Expenses' : t === 'report' ? 'Report' : 'Categories'}
          </button>
        ))}
      </div>

      {/* ═══ PLAN TAB ═══ */}
      {tab === 'plan' && (
        <div className="space-y-6">
          {/* Summary Cards */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
              <p className="text-xs text-slate-500 uppercase tracking-wide">Total Income</p>
              <p className="text-xl font-bold text-slate-800 mt-1">{formatCurrency(totalIncome)}</p>
            </div>
            <div className="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
              <p className="text-xs text-slate-500 uppercase tracking-wide">Savings Target</p>
              <p className="text-xl font-bold text-green-600 mt-1">{formatCurrency(savingsAmount)} <span className="text-sm font-normal text-slate-500">({savingsPct}%)</span></p>
            </div>
            <div className="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
              <p className="text-xs text-slate-500 uppercase tracking-wide">Available for Expenses</p>
              <p className="text-xl font-bold text-indigo-600 mt-1">{formatCurrency(availableForExpenses)}</p>
            </div>
            <div className="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
              <p className="text-xs text-slate-500 uppercase tracking-wide">Allocated</p>
              <p className={`text-xl font-bold mt-1 ${isOverAllocated ? 'text-red-600' : 'text-slate-800'}`}>{formatCurrency(totalAllocated)}</p>
            </div>
          </div>

          {isOverAllocated && (
            <div className="flex items-center gap-2 bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-red-700 text-sm">
              <AlertTriangle size={16} /> Allocated expenses exceed available budget by {formatCurrency(totalAllocated - availableForExpenses)}
            </div>
          )}

          {/* Savings Target */}
          <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-slate-800">Savings Target</h3>
              <button onClick={savePlan} className="flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700">
                <Save size={14} /> {plan ? 'Update Plan' : 'Create Plan'}
              </button>
            </div>
            <div className="flex items-center gap-4">
              <input type="range" min={0} max={100} value={savingsPct} onChange={e => setSavingsPct(parseInt(e.target.value))}
                className="flex-1 h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
              <input type="number" min={0} max={100} value={savingsPct} onChange={e => setSavingsPct(parseInt(e.target.value) || 0)}
                className="w-20 border border-slate-300 rounded-lg px-3 py-2 text-sm text-right focus:ring-2 focus:ring-indigo-500" />
              <span className="text-sm text-slate-500">%</span>
            </div>
          </div>

          {/* Income Sources */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="p-4 border-b border-slate-200">
              <h3 className="font-semibold text-slate-800">Income Sources</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Source</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Amount</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600 w-16"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {incomes.map(inc => (
                    <tr key={inc.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3 text-slate-700">{inc.source}</td>
                      <td className="px-4 py-3 text-right font-medium text-slate-800">{formatCurrency(inc.amount)}</td>
                      <td className="px-4 py-3 text-right">
                        <button onClick={() => deleteIncome(inc.id)} className="text-red-400 hover:text-red-600"><Trash2 size={14} /></button>
                      </td>
                    </tr>
                  ))}
                  {plan && (
                    <tr className="bg-slate-50">
                      <td className="px-4 py-2">
                        <input type="text" placeholder="Source" value={newIncome.source} onChange={e => setNewIncome(p => ({ ...p, source: e.target.value }))}
                          className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:ring-2 focus:ring-indigo-500" />
                      </td>
                      <td className="px-4 py-2">
                        <input type="number" placeholder="Amount" value={newIncome.amount} onChange={e => setNewIncome(p => ({ ...p, amount: e.target.value }))}
                          className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm text-right focus:ring-2 focus:ring-indigo-500" />
                      </td>
                      <td className="px-4 py-2 text-right">
                        <button onClick={addIncome} className="text-indigo-600 hover:text-indigo-800"><Plus size={16} /></button>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            {!plan && <p className="p-4 text-sm text-slate-400">Create a plan first to add income sources.</p>}
          </div>

          {/* Expense Allocations */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="p-4 border-b border-slate-200">
              <h3 className="font-semibold text-slate-800">Expense Allocations</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Category</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Planned Amount</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600 w-16"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {allocations.map(a => (
                    <tr key={a.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3 text-slate-700">{a.category.name}</td>
                      <td className="px-4 py-3 text-right font-medium text-slate-800">{formatCurrency(a.plannedAmount)}</td>
                      <td className="px-4 py-3 text-right">
                        <button onClick={() => deleteAllocation(a.id)} className="text-red-400 hover:text-red-600"><Trash2 size={14} /></button>
                      </td>
                    </tr>
                  ))}
                  {plan && (
                    <tr className="bg-slate-50">
                      <td className="px-4 py-2">
                        <select value={newAlloc.categoryId} onChange={e => setNewAlloc(p => ({ ...p, categoryId: e.target.value }))}
                          className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:ring-2 focus:ring-indigo-500">
                          <option value="">Select category</option>
                          {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                        </select>
                      </td>
                      <td className="px-4 py-2">
                        <input type="number" placeholder="Amount" value={newAlloc.amount} onChange={e => setNewAlloc(p => ({ ...p, amount: e.target.value }))}
                          className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm text-right focus:ring-2 focus:ring-indigo-500" />
                      </td>
                      <td className="px-4 py-2 text-right">
                        <button onClick={addAllocation} className="text-indigo-600 hover:text-indigo-800"><Plus size={16} /></button>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            {!plan && <p className="p-4 text-sm text-slate-400">Create a plan first to add allocations.</p>}
          </div>
        </div>
      )}

      {/* ═══ EXPENSES TAB ═══ */}
      {tab === 'expenses' && (
        <div className="space-y-6">
          {/* Summary */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
              <p className="text-xs text-slate-500 uppercase tracking-wide">Total Expenses</p>
              <p className="text-xl font-bold text-slate-800 mt-1">{formatCurrency(expenses.reduce((s, e) => s + e.amount, 0))}</p>
            </div>
            <div className="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
              <p className="text-xs text-slate-500 uppercase tracking-wide">Entries</p>
              <p className="text-xl font-bold text-slate-800 mt-1">{expenses.length}</p>
            </div>
            <div className="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
              <p className="text-xs text-slate-500 uppercase tracking-wide">Period</p>
              <p className="text-xl font-bold text-slate-800 mt-1">{MONTHS[month - 1]} {year}</p>
            </div>
          </div>

          {/* Add/Edit Form */}
          <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
            <h3 className="font-semibold text-slate-800 mb-4">{editingExpense ? 'Edit Expense' : 'Add Expense'}</h3>
            <div className="grid grid-cols-1 md:grid-cols-5 gap-3">
              <input type="date" value={expForm.expenseDate} onChange={e => setExpForm(p => ({ ...p, expenseDate: e.target.value }))}
                className="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" />
              <input type="text" placeholder="Description" value={expForm.description} onChange={e => setExpForm(p => ({ ...p, description: e.target.value }))}
                className="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" />
              <select value={expForm.categoryId} onChange={e => setExpForm(p => ({ ...p, categoryId: e.target.value }))}
                className="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500">
                <option value="">Category</option>
                {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
              <input type="number" placeholder="Amount" value={expForm.amount} onChange={e => setExpForm(p => ({ ...p, amount: e.target.value }))}
                className="border border-slate-300 rounded-lg px-3 py-2 text-sm text-right focus:ring-2 focus:ring-indigo-500" />
              <div className="flex gap-2">
                <button onClick={saveExpense} className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700">
                  {editingExpense ? <><Save size={14} /> Update</> : <><Plus size={14} /> Add</>}
                </button>
                {editingExpense && (
                  <button onClick={resetExpForm} className="px-3 py-2 text-sm font-medium text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">
                    <X size={14} />
                  </button>
                )}
              </div>
            </div>
          </div>

          {/* Expenses Table */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Date</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Description</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Category</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Amount</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600 w-20"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {expenses.map(e => (
                    <tr key={e.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3 text-slate-700">{e.expenseDate}</td>
                      <td className="px-4 py-3 text-slate-700">{e.description}</td>
                      <td className="px-4 py-3"><span className="text-xs px-2 py-0.5 rounded-full font-medium bg-slate-100 text-slate-700">{e.category.name}</span></td>
                      <td className="px-4 py-3 text-right font-medium text-slate-800">{formatCurrency(e.amount)}</td>
                      <td className="px-4 py-3 text-right flex justify-end gap-2">
                        <button onClick={() => editExpense(e)} className="text-slate-400 hover:text-indigo-600"><Pencil size={14} /></button>
                        <button onClick={() => deleteExpense(e.id)} className="text-slate-400 hover:text-red-600"><Trash2 size={14} /></button>
                      </td>
                    </tr>
                  ))}
                  {expenses.length === 0 && <tr><td colSpan={5} className="px-4 py-12 text-center text-slate-400">No expenses recorded for this month</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ═══ REPORT TAB ═══ */}
      {tab === 'report' && (
        <div className="space-y-6">
          {!report ? (
            <div className="bg-white rounded-xl p-12 border border-slate-200 shadow-sm text-center text-slate-400">
              No report data available for {MONTHS[month - 1]} {year}. Create a budget plan first.
            </div>
          ) : (
            <>
              {report.isOverBudget && (
                <div className="flex items-center gap-2 bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-red-700 text-sm">
                  <AlertTriangle size={16} /> Over budget by {formatCurrency(report.excess)}
                </div>
              )}

              {/* Summary Cards */}
              <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <div className="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
                  <p className="text-xs text-slate-500 uppercase tracking-wide">Planned Income</p>
                  <p className="text-xl font-bold text-slate-800 mt-1">{formatCurrency(report.totalPlannedIncome)}</p>
                </div>
                <div className="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
                  <p className="text-xs text-slate-500 uppercase tracking-wide">Planned Expense</p>
                  <p className="text-xl font-bold text-slate-800 mt-1">{formatCurrency(report.totalPlannedExpense)}</p>
                </div>
                <div className="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
                  <p className="text-xs text-slate-500 uppercase tracking-wide">Actual Expense</p>
                  <p className={`text-xl font-bold mt-1 ${report.totalActualExpense > report.totalPlannedExpense ? 'text-red-600' : 'text-green-600'}`}>{formatCurrency(report.totalActualExpense)}</p>
                </div>
                <div className="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
                  <p className="text-xs text-slate-500 uppercase tracking-wide">Actual Savings</p>
                  <p className={`text-xl font-bold mt-1 ${report.actualSavings >= report.targetSavings ? 'text-green-600' : 'text-amber-600'}`}>{formatCurrency(report.actualSavings)}</p>
                </div>
              </div>

              {/* Savings Progress */}
              <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
                <h3 className="font-semibold text-slate-800 mb-3">Savings Progress</h3>
                <div className="flex items-center gap-4">
                  <div className="flex-1 bg-slate-200 rounded-full h-4 overflow-hidden">
                    <div className={`h-full rounded-full transition-all ${report.actualSavings >= report.targetSavings ? 'bg-green-500' : 'bg-amber-500'}`}
                      style={{ width: `${Math.min((report.actualSavings / report.targetSavings) * 100, 100)}%` }} />
                  </div>
                  <span className="text-sm font-medium text-slate-700 whitespace-nowrap">
                    {formatCurrency(report.actualSavings)} / {formatCurrency(report.targetSavings)}
                  </span>
                </div>
              </div>

              {/* Plan vs Actual Bar Chart */}
              <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
                <h3 className="font-semibold text-slate-800 mb-4">Plan vs Actual by Category</h3>
                {report.categories.length > 0 ? (
                  <ResponsiveContainer width="100%" height={350}>
                    <BarChart data={report.categories} layout="vertical" margin={{ left: 30 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                      <XAxis type="number" stroke="#94a3b8" fontSize={12} tickFormatter={v => formatCurrency(v as number)} />
                      <YAxis type="category" dataKey="categoryName" stroke="#94a3b8" fontSize={11} width={120} />
                      <Tooltip formatter={(v) => formatCurrency(v as number)} />
                      <Legend />
                      <Bar dataKey="planned" fill="#94a3b8" name="Planned" radius={[0, 4, 4, 0]} />
                      <Bar dataKey="actual" fill="#6366f1" name="Actual" radius={[0, 4, 4, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                ) : <div className="h-64 flex items-center justify-center text-slate-400">No category data</div>}
              </div>

              {/* Category Comparison Table */}
              <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
                <div className="p-4 border-b border-slate-200">
                  <h3 className="font-semibold text-slate-800">Category Breakdown</h3>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-slate-50 border-b border-slate-200">
                      <tr>
                        <th className="text-left px-4 py-3 font-medium text-slate-600">Category</th>
                        <th className="text-right px-4 py-3 font-medium text-slate-600">Planned</th>
                        <th className="text-right px-4 py-3 font-medium text-slate-600">Actual</th>
                        <th className="text-right px-4 py-3 font-medium text-slate-600">Variance</th>
                        <th className="text-center px-4 py-3 font-medium text-slate-600">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {report.categories.map(c => (
                        <tr key={c.categoryName} className="hover:bg-slate-50">
                          <td className="px-4 py-3 font-medium text-slate-800">{c.categoryName}</td>
                          <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(c.planned)}</td>
                          <td className="px-4 py-3 text-right text-slate-700">{formatCurrency(c.actual)}</td>
                          <td className={`px-4 py-3 text-right font-medium ${c.variance >= 0 ? 'text-green-600' : 'text-red-600'}`}>{formatCurrency(c.variance)}</td>
                          <td className="px-4 py-3 text-center">
                            <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                              c.status === 'UNDER' ? 'bg-green-100 text-green-700' :
                              c.status === 'OVER' ? 'bg-red-100 text-red-700' :
                              'bg-slate-100 text-slate-700'
                            }`}>{c.status}</span>
                          </td>
                        </tr>
                      ))}
                      {report.categories.length === 0 && <tr><td colSpan={5} className="px-4 py-12 text-center text-slate-400">No category data</td></tr>}
                    </tbody>
                  </table>
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {/* ═══ CATEGORIES TAB ═══ */}
      {tab === 'categories' && (
        <div className="space-y-6">
          {/* Add Category */}
          <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
            <h3 className="font-semibold text-slate-800 mb-4">Add Expense Category</h3>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <input type="text" placeholder="Category name" value={newCatName} onChange={e => setNewCatName(e.target.value)}
                className="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" />
              <select value={newCatParent} onChange={e => setNewCatParent(e.target.value)}
                className="border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500">
                <option>Essential</option>
                <option>Lifestyle</option>
                <option>Education</option>
                <option>Family</option>
                <option>Special</option>
                <option>Other</option>
              </select>
              <button onClick={addCategory} className="flex items-center justify-center gap-1.5 px-3 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700">
                <Plus size={14} /> Add Category
              </button>
            </div>
          </div>

          {/* Categories List */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="p-4 border-b border-slate-200">
              <h3 className="font-semibold text-slate-800">Your Categories ({categories.length})</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Category</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Group</th>
                    <th className="text-right px-4 py-3 font-medium text-slate-600 w-24"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {categories.map(c => (
                    <tr key={c.id} className="hover:bg-slate-50">
                      {editingCat?.id === c.id ? (
                        <>
                          <td className="px-4 py-2">
                            <input type="text" value={editingCat.name} onChange={e => setEditingCat({ ...editingCat, name: e.target.value })}
                              className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:ring-2 focus:ring-indigo-500" />
                          </td>
                          <td className="px-4 py-2">
                            <select value={editingCat.parentCategory || 'Other'} onChange={e => setEditingCat({ ...editingCat, parentCategory: e.target.value })}
                              className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:ring-2 focus:ring-indigo-500">
                              <option>Essential</option><option>Lifestyle</option><option>Education</option>
                              <option>Family</option><option>Special</option><option>Other</option>
                            </select>
                          </td>
                          <td className="px-4 py-2 text-right flex justify-end gap-2">
                            <button onClick={saveEditCategory} className="text-green-600 hover:text-green-800"><Save size={14} /></button>
                            <button onClick={() => setEditingCat(null)} className="text-slate-400 hover:text-slate-600"><X size={14} /></button>
                          </td>
                        </>
                      ) : (
                        <>
                          <td className="px-4 py-3 font-medium text-slate-800">{c.name}</td>
                          <td className="px-4 py-3"><span className="text-xs px-2 py-0.5 rounded-full font-medium bg-slate-100 text-slate-600">{c.parentCategory || 'Other'}</span></td>
                          <td className="px-4 py-3 text-right flex justify-end gap-2">
                            <button onClick={() => setEditingCat(c)} className="text-slate-400 hover:text-indigo-600"><Pencil size={14} /></button>
                            <button onClick={() => deleteCategory(c.id)} className="text-slate-400 hover:text-red-600"><Trash2 size={14} /></button>
                          </td>
                        </>
                      )}
                    </tr>
                  ))}
                  {categories.length === 0 && <tr><td colSpan={3} className="px-4 py-12 text-center text-slate-400">No categories yet. Add one above.</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
