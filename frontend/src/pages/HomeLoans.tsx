import { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, Home, ChevronDown, ChevronUp } from 'lucide-react';
import { getHomeLoans, createHomeLoan, updateHomeLoan, deleteHomeLoan, getLoanPayments, createLoanPayment, deleteLoanPayment } from '../api';
import { formatCurrency, formatDate } from '../utils/formatters';

export default function HomeLoans() {
  const [loans, setLoans] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<any>(null);
  const [expandedLoan, setExpandedLoan] = useState<number | null>(null);
  const [payments, setPayments] = useState<any[]>([]);
  const [showPaymentForm, setShowPaymentForm] = useState(false);
  const [form, setForm] = useState({
    propertyName: '', propertyAddress: '', propertyValue: 0, loanAmount: 0,
    interestRate: 0, loanType: 'FIXED', tenureMonths: 300, monthlyEmi: 0,
    outstandingBalance: 0, startDate: '', expectedEndDate: '', bank: '',
    includeInNetWorth: true, notes: ''
  });
  const [paymentForm, setPaymentForm] = useState({ paymentDate: new Date().toISOString().split('T')[0], amount: 0, principalPortion: 0, interestPortion: 0, balanceAfter: 0, paymentType: 'REGULAR', notes: '' });

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try { setLoans((await getHomeLoans()).data); }
    catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const resetForm = () => setForm({ propertyName: '', propertyAddress: '', propertyValue: 0, loanAmount: 0, interestRate: 0, loanType: 'FIXED', tenureMonths: 300, monthlyEmi: 0, outstandingBalance: 0, startDate: '', expectedEndDate: '', bank: '', includeInNetWorth: true, notes: '' });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editing) { await updateHomeLoan(editing.id, form); }
      else { await createHomeLoan(form); }
      setShowForm(false); setEditing(null); resetForm(); loadData();
    } catch (err) { console.error(err); alert('Failed'); }
  };

  const startEdit = (loan: any) => {
    setEditing(loan);
    setForm({ propertyName: loan.propertyName, propertyAddress: loan.propertyAddress || '', propertyValue: loan.propertyValue, loanAmount: loan.loanAmount, interestRate: loan.interestRate, loanType: loan.loanType || 'FIXED', tenureMonths: loan.tenureMonths, monthlyEmi: loan.monthlyEmi || 0, outstandingBalance: loan.outstandingBalance || 0, startDate: loan.startDate || '', expectedEndDate: loan.expectedEndDate || '', bank: loan.bank || '', includeInNetWorth: loan.includeInNetWorth, notes: loan.notes || '' });
    setShowForm(true);
  };

  const handleDelete = async (id: number) => { if (confirm('Delete this loan?')) { await deleteHomeLoan(id); loadData(); } };

  const togglePayments = async (loanId: number) => {
    if (expandedLoan === loanId) { setExpandedLoan(null); return; }
    try { setPayments((await getLoanPayments(loanId)).data); setExpandedLoan(loanId); }
    catch (err) { console.error(err); }
  };

  const handlePaymentSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!expandedLoan) return;
    try {
      await createLoanPayment(expandedLoan, paymentForm);
      setPayments((await getLoanPayments(expandedLoan)).data);
      setShowPaymentForm(false);
      loadData();
    } catch (err) { console.error(err); alert('Failed'); }
  };

  const handleDeletePayment = async (paymentId: number) => {
    if (!confirm('Delete?')) return;
    await deleteLoanPayment(paymentId);
    if (expandedLoan) setPayments((await getLoanPayments(expandedLoan)).data);
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const totalPropertyValue = loans.reduce((s, l) => s + (l.propertyValue || 0), 0);
  const totalOutstanding = loans.reduce((s, l) => s + (l.outstandingBalance || 0), 0);
  const totalEquity = totalPropertyValue - totalOutstanding;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Home Loans & Mortgage</h1><p className="text-slate-500 text-sm mt-0.5">Track properties, loans, and payment history</p></div>
        <button onClick={() => { setShowForm(!showForm); setEditing(null); resetForm(); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700"><Plus size={16} /> Add Property</button>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Properties</p><p className="text-lg font-bold text-slate-800 mt-1">{loans.length}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Property Value</p><p className="text-lg font-bold text-green-600 mt-1">{formatCurrency(totalPropertyValue)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Outstanding Loan</p><p className="text-lg font-bold text-red-600 mt-1">{formatCurrency(totalOutstanding)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Home Equity</p><p className="text-lg font-bold text-indigo-600 mt-1">{formatCurrency(totalEquity)}</p></div>
      </div>

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editing ? 'Edit Property/Loan' : 'Add Property/Loan'}</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Property Name *</label>
              <input type="text" value={form.propertyName} onChange={e => setForm({...form, propertyName: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Address</label>
              <input type="text" value={form.propertyAddress} onChange={e => setForm({...form, propertyAddress: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Property Value *</label>
              <input type="number" step="any" value={form.propertyValue || ''} onChange={e => setForm({...form, propertyValue: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Loan Amount *</label>
              <input type="number" step="any" value={form.loanAmount || ''} onChange={e => setForm({...form, loanAmount: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Interest Rate (%) *</label>
              <input type="number" step="0.01" value={form.interestRate || ''} onChange={e => setForm({...form, interestRate: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Loan Type</label>
              <div className="flex rounded-lg overflow-hidden border border-slate-300">
                {['FIXED', 'FLOATING', 'HDB'].map(t => (
                  <button key={t} type="button" onClick={() => setForm({...form, loanType: t})} className={`flex-1 py-2 text-xs font-medium ${form.loanType === t ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600'}`}>{t}</button>
                ))}
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Tenure (months)</label>
              <input type="number" value={form.tenureMonths} onChange={e => setForm({...form, tenureMonths: parseInt(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Monthly EMI</label>
              <input type="number" step="any" value={form.monthlyEmi || ''} onChange={e => setForm({...form, monthlyEmi: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Outstanding Balance</label>
              <input type="number" step="any" value={form.outstandingBalance || ''} onChange={e => setForm({...form, outstandingBalance: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Start Date</label>
              <input type="date" value={form.startDate} onChange={e => setForm({...form, startDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Bank</label>
              <input type="text" value={form.bank} onChange={e => setForm({...form, bank: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div className="flex items-end gap-3">
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={form.includeInNetWorth} onChange={e => setForm({...form, includeInNetWorth: e.target.checked})} className="rounded border-slate-300 text-indigo-600" />
                <span className="text-xs text-slate-700">Include in Net Worth</span>
              </label>
            </div>
            <div className="flex items-end gap-2 lg:col-span-4">
              <button type="submit" className="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editing ? 'Update' : 'Save'}</button>
              <button type="button" onClick={() => { setShowForm(false); setEditing(null); }} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Loan Cards */}
      <div className="space-y-4">
        {loans.map(loan => (
          <div key={loan.id} className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="p-5">
              <div className="flex items-start justify-between">
                <div className="flex items-start gap-3">
                  <div className="p-2.5 rounded-lg bg-indigo-100"><Home size={20} className="text-indigo-600" /></div>
                  <div>
                    <h4 className="font-semibold text-slate-800">{loan.propertyName}</h4>
                    <p className="text-xs text-slate-500 mt-0.5">{loan.propertyAddress || loan.bank || ''} {loan.loanType && <span className="px-1.5 py-0.5 bg-slate-100 rounded text-[10px] ml-1">{loan.loanType}</span>}</p>
                  </div>
                </div>
                <div className="flex gap-1">
                  <button onClick={() => startEdit(loan)} className="p-1 text-slate-400 hover:text-indigo-600"><Pencil size={14} /></button>
                  <button onClick={() => handleDelete(loan.id)} className="p-1 text-slate-400 hover:text-red-500"><Trash2 size={14} /></button>
                </div>
              </div>
              <div className="grid grid-cols-2 md:grid-cols-5 gap-3 mt-4">
                <div><p className="text-[10px] text-slate-500 uppercase">Property Value</p><p className="text-sm font-bold text-green-600">{formatCurrency(loan.propertyValue)}</p></div>
                <div><p className="text-[10px] text-slate-500 uppercase">Loan Amount</p><p className="text-sm font-bold text-slate-700">{formatCurrency(loan.loanAmount)}</p></div>
                <div><p className="text-[10px] text-slate-500 uppercase">Outstanding</p><p className="text-sm font-bold text-red-600">{formatCurrency(loan.outstandingBalance || 0)}</p></div>
                <div><p className="text-[10px] text-slate-500 uppercase">EMI</p><p className="text-sm font-bold text-slate-700">{loan.monthlyEmi ? formatCurrency(loan.monthlyEmi) : '-'}/mo</p></div>
                <div><p className="text-[10px] text-slate-500 uppercase">Rate</p><p className="text-sm font-bold text-slate-700">{loan.interestRate}% | {loan.tenureMonths}mo</p></div>
              </div>
            </div>
            {/* Payment History Toggle */}
            <div className="border-t border-slate-100 px-5 py-2">
              <button onClick={() => togglePayments(loan.id)} className="flex items-center gap-1 text-xs text-indigo-600 hover:text-indigo-800 font-medium">
                {expandedLoan === loan.id ? <ChevronUp size={12} /> : <ChevronDown size={12} />} Payment History ({expandedLoan === loan.id ? payments.length : '...'})
              </button>
            </div>
            {expandedLoan === loan.id && (
              <div className="px-5 pb-4 bg-slate-50/50">
                <div className="flex justify-end mb-2">
                  <button onClick={() => setShowPaymentForm(!showPaymentForm)} className="text-xs px-2.5 py-1 bg-indigo-600 text-white rounded font-medium">+ Add Payment</button>
                </div>
                {showPaymentForm && (
                  <form onSubmit={handlePaymentSubmit} className="grid grid-cols-3 md:grid-cols-7 gap-2 mb-3 p-3 bg-white rounded-lg border border-slate-200">
                    <div><label className="block text-[10px] text-slate-500">Date</label><input type="date" value={paymentForm.paymentDate} onChange={e => setPaymentForm({...paymentForm, paymentDate: e.target.value})} className="w-full border rounded px-2 py-1 text-xs" /></div>
                    <div><label className="block text-[10px] text-slate-500">Amount</label><input type="number" step="any" value={paymentForm.amount || ''} onChange={e => setPaymentForm({...paymentForm, amount: parseFloat(e.target.value) || 0})} className="w-full border rounded px-2 py-1 text-xs" /></div>
                    <div><label className="block text-[10px] text-slate-500">Principal</label><input type="number" step="any" value={paymentForm.principalPortion || ''} onChange={e => setPaymentForm({...paymentForm, principalPortion: parseFloat(e.target.value) || 0})} className="w-full border rounded px-2 py-1 text-xs" /></div>
                    <div><label className="block text-[10px] text-slate-500">Interest</label><input type="number" step="any" value={paymentForm.interestPortion || ''} onChange={e => setPaymentForm({...paymentForm, interestPortion: parseFloat(e.target.value) || 0})} className="w-full border rounded px-2 py-1 text-xs" /></div>
                    <div><label className="block text-[10px] text-slate-500">Balance After</label><input type="number" step="any" value={paymentForm.balanceAfter || ''} onChange={e => setPaymentForm({...paymentForm, balanceAfter: parseFloat(e.target.value) || 0})} className="w-full border rounded px-2 py-1 text-xs" /></div>
                    <div><label className="block text-[10px] text-slate-500">Type</label>
                      <select value={paymentForm.paymentType} onChange={e => setPaymentForm({...paymentForm, paymentType: e.target.value})} className="w-full border rounded px-2 py-1.5 text-xs">
                        <option value="REGULAR">Regular</option><option value="LUMP_SUM">Lump Sum</option><option value="PREPAYMENT">Prepayment</option>
                      </select></div>
                    <div className="flex items-end gap-1"><button type="submit" className="px-2 py-1 bg-indigo-600 text-white rounded text-[10px] font-medium">Save</button><button type="button" onClick={() => setShowPaymentForm(false)} className="px-2 py-1 bg-slate-200 rounded text-[10px]">X</button></div>
                  </form>
                )}
                {payments.length > 0 ? (
                  <table className="w-full text-[11px]">
                    <thead><tr className="border-b border-slate-200">
                      <th className="text-left px-2 py-1 text-slate-500">Date</th>
                      <th className="text-right px-2 py-1 text-slate-500">Amount</th>
                      <th className="text-right px-2 py-1 text-slate-500">Principal</th>
                      <th className="text-right px-2 py-1 text-slate-500">Interest</th>
                      <th className="text-right px-2 py-1 text-slate-500">Balance</th>
                      <th className="text-left px-2 py-1 text-slate-500">Type</th>
                      <th className="px-2 py-1"></th>
                    </tr></thead>
                    <tbody>
                      {payments.map((p: any) => (
                        <tr key={p.id} className="border-b border-slate-100 hover:bg-white">
                          <td className="px-2 py-1.5">{formatDate(p.paymentDate)}</td>
                          <td className="px-2 py-1.5 text-right font-medium">{formatCurrency(p.amount)}</td>
                          <td className="px-2 py-1.5 text-right text-green-600">{p.principalPortion ? formatCurrency(p.principalPortion) : '-'}</td>
                          <td className="px-2 py-1.5 text-right text-red-600">{p.interestPortion ? formatCurrency(p.interestPortion) : '-'}</td>
                          <td className="px-2 py-1.5 text-right">{p.balanceAfter ? formatCurrency(p.balanceAfter) : '-'}</td>
                          <td className="px-2 py-1.5"><span className="text-[9px] px-1 py-0.5 rounded bg-slate-100">{p.paymentType}</span></td>
                          <td className="px-2 py-1.5"><button onClick={() => handleDeletePayment(p.id)} className="text-slate-300 hover:text-red-500"><Trash2 size={11} /></button></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ) : <p className="text-xs text-slate-400 text-center py-3">No payments recorded</p>}
              </div>
            )}
          </div>
        ))}
        {loans.length === 0 && <div className="text-center text-slate-400 py-12">No properties or loans tracked</div>}
      </div>
    </div>
  );
}
