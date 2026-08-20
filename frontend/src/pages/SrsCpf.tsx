import { useState } from 'react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { Calculator, PiggyBank } from 'lucide-react';
import { formatCurrency } from '../utils/formatters';

type Tab = 'srs' | 'cpf';

export default function SrsCpf() {
  const [tab, setTab] = useState<Tab>('srs');

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">SRS & CPF Planning</h1>
        <p className="text-slate-500 text-sm mt-0.5">Plan your retirement savings and projections</p>
      </div>

      <div className="flex gap-1 bg-slate-100 rounded-lg p-1 w-fit">
        <button onClick={() => setTab('srs')} className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${tab === 'srs' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600'}`}>
          <PiggyBank size={15} /> SRS Plan
        </button>
        <button onClick={() => setTab('cpf')} className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${tab === 'cpf' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600'}`}>
          <Calculator size={15} /> CPF Tracker
        </button>
      </div>

      {tab === 'srs' ? <SRSPlan /> : <CPFTracker />}
    </div>
  );
}

// ═══════════════════════════════════════════════════════
// SRS PLAN
// ═══════════════════════════════════════════════════════
function SRSPlan() {
  const [params, setParams] = useState({
    currentAge: 39,
    currentBalance: 37485,
    annualContribution: 15300,
    contributionYears: 12, // contribute until age 50
    growthRate: 8,
    withdrawalAge: 62,
    annualWithdrawal: 60000,
  });

  // Generate projection
  const projections = generateSRSProjection(params);
  const totalContributed = projections.filter(p => p.contribution > 0).reduce((s, p) => s + p.contribution, 0);
  const peakValue = Math.max(...projections.map(p => p.accumulated));
  const taxRelief = totalContributed * 0.195; // 19.5% tax relief

  // Chart data
  const chartData = projections.map(p => ({
    age: p.age,
    value: p.accumulated,
    contribution: p.contribution > 0 ? p.contribution : undefined,
    withdrawal: p.withdrawal > 0 ? p.withdrawal : undefined,
  }));

  return (
    <div className="space-y-6">
      {/* Parameters */}
      <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <h3 className="text-sm font-semibold text-slate-800 mb-3 flex items-center gap-2"><Calculator size={16} className="text-indigo-600" /> SRS Parameters</h3>
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-3">
          <Field label="Current Age" value={params.currentAge} onChange={v => setParams({...params, currentAge: v})} />
          <Field label="Current Balance" value={params.currentBalance} onChange={v => setParams({...params, currentBalance: v})} prefix="S$" />
          <Field label="Annual Contribution" value={params.annualContribution} onChange={v => setParams({...params, annualContribution: v})} prefix="S$" />
          <Field label="Contribute Until Age" value={params.currentAge + params.contributionYears} onChange={v => setParams({...params, contributionYears: v - params.currentAge})} />
          <Field label="Growth Rate %" value={params.growthRate} onChange={v => setParams({...params, growthRate: v})} suffix="%" />
          <Field label="Withdrawal Age" value={params.withdrawalAge} onChange={v => setParams({...params, withdrawalAge: v})} />
          <Field label="Annual Withdrawal" value={params.annualWithdrawal} onChange={v => setParams({...params, annualWithdrawal: v})} prefix="S$" />
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Total Contributed</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{formatCurrency(totalContributed)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Peak Value</p>
          <p className="text-lg font-bold text-indigo-600 mt-1">{formatCurrency(peakValue)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Tax Relief (19.5%)</p>
          <p className="text-lg font-bold text-green-600 mt-1">{formatCurrency(taxRelief)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Withdrawal Years</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{projections.filter(p => p.withdrawal > 0).length} yrs</p>
        </div>
      </div>

      {/* Chart */}
      <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <h3 className="text-sm font-semibold text-slate-800 mb-3">SRS Projection</h3>
        <ResponsiveContainer width="100%" height={300}>
          <AreaChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
            <XAxis dataKey="age" stroke="#94a3b8" fontSize={11} label={{ value: 'Age', position: 'insideBottom', offset: -5 }} />
            <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `S$${(v/1000).toFixed(0)}K`} />
            <Tooltip formatter={(v) => formatCurrency(v as number)} labelFormatter={l => `Age ${l}`} />
            <Area type="monotone" dataKey="value" stroke="#6366f1" fill="#6366f1" fillOpacity={0.1} name="Accumulated Value" strokeWidth={2} />
          </AreaChart>
        </ResponsiveContainer>
      </div>

      {/* Projection Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-200"><h3 className="font-semibold text-slate-800 text-sm">Year-by-Year Projection</h3></div>
        <div className="overflow-x-auto max-h-96">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200 sticky top-0">
              <tr>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Year</th>
                <th className="text-center px-4 py-2.5 font-medium text-slate-600">Age</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">Contribution</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">Growth</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">Withdrawal</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">Accumulated</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {projections.map(p => (
                <tr key={p.year} className={`hover:bg-slate-50 ${p.withdrawal > 0 ? 'bg-red-50/30' : p.contribution > 0 ? '' : 'bg-slate-50/50'}`}>
                  <td className="px-4 py-2 text-slate-700">{p.year}</td>
                  <td className="px-4 py-2 text-center text-slate-700">{p.age}</td>
                  <td className="px-4 py-2 text-right text-green-600">{p.contribution > 0 ? formatCurrency(p.contribution) : '-'}</td>
                  <td className="px-4 py-2 text-right text-indigo-600">{formatCurrency(p.growth)}</td>
                  <td className="px-4 py-2 text-right text-red-600">{p.withdrawal > 0 ? formatCurrency(p.withdrawal) : '-'}</td>
                  <td className="px-4 py-2 text-right font-medium text-slate-800">{formatCurrency(p.accumulated)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Info */}
      <div className="bg-indigo-50 border border-indigo-200 rounded-lg p-4">
        <p className="text-sm font-medium text-indigo-800">SRS Key Facts (Singapore)</p>
        <ul className="text-xs text-indigo-600 mt-1 space-y-0.5 list-disc list-inside">
          <li>Annual contribution cap: S$15,300 (non-citizen) / S$35,700 (citizen/PR first year top-up)</li>
          <li>Tax relief: Contributions reduce taxable income at marginal rate</li>
          <li>Withdrawal penalty: 5% if before statutory retirement age (currently 62)</li>
          <li>Withdrawal tax: 50% of amount is taxable at prevailing rate</li>
          <li>Spread withdrawals over 10 years from age 62 to minimize tax</li>
        </ul>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════
// CPF TRACKER
// ═══════════════════════════════════════════════════════
function CPFTracker() {
  const [cpf, setCpf] = useState({
    ordinaryAccount: 50000,
    specialAccount: 30000,
    medisaveAccount: 20000,
    monthlySalary: 8000,
    age: 39,
    oaRate: 2.5,
    saRate: 4.0,
    maRate: 4.0,
  });

  const totalCpf = cpf.ordinaryAccount + cpf.specialAccount + cpf.medisaveAccount;

  // Simple CPF projection (contribution + interest)
  const projections: Array<{ year: number; age: number; oa: number; sa: number; ma: number; total: number }> = [];
  let oa = cpf.ordinaryAccount, sa = cpf.specialAccount, ma = cpf.medisaveAccount;
  const currentYear = new Date().getFullYear();

  for (let i = 0; i < 20; i++) {
    const age = cpf.age + i;
    // Simplified contribution allocation (varies by age, simplified here)
    const monthlyContrib = cpf.monthlySalary * 0.37; // ~37% total rate
    const annualContrib = monthlyContrib * 12;
    const oaContrib = annualContrib * (age < 55 ? 0.6217 : 0.35);
    const saContrib = annualContrib * (age < 55 ? 0.1621 : 0.25);
    const maContrib = annualContrib * (age < 55 ? 0.2162 : 0.40);

    oa = (oa + oaContrib) * (1 + cpf.oaRate / 100);
    sa = (sa + saContrib) * (1 + cpf.saRate / 100);
    ma = (ma + maContrib) * (1 + cpf.maRate / 100);

    projections.push({ year: currentYear + i, age, oa, sa, ma, total: oa + sa + ma });
  }

  return (
    <div className="space-y-6">
      {/* Current Balances */}
      <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <h3 className="text-sm font-semibold text-slate-800 mb-3 flex items-center gap-2"><PiggyBank size={16} className="text-green-600" /> CPF Balances</h3>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <Field label="Ordinary Account (OA)" value={cpf.ordinaryAccount} onChange={v => setCpf({...cpf, ordinaryAccount: v})} prefix="S$" />
          <Field label="Special Account (SA)" value={cpf.specialAccount} onChange={v => setCpf({...cpf, specialAccount: v})} prefix="S$" />
          <Field label="Medisave (MA)" value={cpf.medisaveAccount} onChange={v => setCpf({...cpf, medisaveAccount: v})} prefix="S$" />
          <Field label="Monthly Salary" value={cpf.monthlySalary} onChange={v => setCpf({...cpf, monthlySalary: v})} prefix="S$" />
        </div>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Total CPF</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{formatCurrency(totalCpf)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">OA (2.5%)</p>
          <p className="text-lg font-bold text-blue-600 mt-1">{formatCurrency(cpf.ordinaryAccount)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">SA (4.0%)</p>
          <p className="text-lg font-bold text-purple-600 mt-1">{formatCurrency(cpf.specialAccount)}</p>
        </div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">MA (4.0%)</p>
          <p className="text-lg font-bold text-green-600 mt-1">{formatCurrency(cpf.medisaveAccount)}</p>
        </div>
      </div>

      {/* Projection Chart */}
      <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <h3 className="text-sm font-semibold text-slate-800 mb-3">20-Year CPF Projection</h3>
        <ResponsiveContainer width="100%" height={300}>
          <AreaChart data={projections}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
            <XAxis dataKey="age" stroke="#94a3b8" fontSize={11} />
            <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={v => `S$${(v/1000).toFixed(0)}K`} />
            <Tooltip formatter={(v) => formatCurrency(v as number)} labelFormatter={l => `Age ${l}`} />
            <Area type="monotone" dataKey="oa" stackId="1" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.3} name="OA" />
            <Area type="monotone" dataKey="sa" stackId="1" stroke="#8b5cf6" fill="#8b5cf6" fillOpacity={0.3} name="SA" />
            <Area type="monotone" dataKey="ma" stackId="1" stroke="#10b981" fill="#10b981" fillOpacity={0.3} name="MA" />
          </AreaChart>
        </ResponsiveContainer>
      </div>

      {/* Projection Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-200"><h3 className="font-semibold text-slate-800 text-sm">Projection Table</h3></div>
        <div className="overflow-x-auto max-h-80">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200 sticky top-0">
              <tr>
                <th className="text-left px-4 py-2.5 font-medium text-slate-600">Year</th>
                <th className="text-center px-4 py-2.5 font-medium text-slate-600">Age</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">OA</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">SA</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">MA</th>
                <th className="text-right px-4 py-2.5 font-medium text-slate-600">Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {projections.map(p => (
                <tr key={p.year} className="hover:bg-slate-50">
                  <td className="px-4 py-2 text-slate-700">{p.year}</td>
                  <td className="px-4 py-2 text-center text-slate-700">{p.age}</td>
                  <td className="px-4 py-2 text-right text-blue-600">{formatCurrency(p.oa)}</td>
                  <td className="px-4 py-2 text-right text-purple-600">{formatCurrency(p.sa)}</td>
                  <td className="px-4 py-2 text-right text-green-600">{formatCurrency(p.ma)}</td>
                  <td className="px-4 py-2 text-right font-medium text-slate-800">{formatCurrency(p.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="bg-green-50 border border-green-200 rounded-lg p-4">
        <p className="text-sm font-medium text-green-800">CPF Key Facts</p>
        <ul className="text-xs text-green-600 mt-1 space-y-0.5 list-disc list-inside">
          <li>OA interest: 2.5% p.a. (first S$20K gets extra 1%)</li>
          <li>SA/MA interest: 4.0% p.a. (first S$40K of combined gets extra 1%)</li>
          <li>OA can be used for housing and investments</li>
          <li>SA is for retirement and approved investments</li>
          <li>At 55, OA + SA merge into Retirement Account (RA)</li>
        </ul>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════
function Field({ label, value, onChange, prefix, suffix }: { label: string; value: number; onChange: (v: number) => void; prefix?: string; suffix?: string }) {
  return (
    <div>
      <label className="block text-[10px] font-medium text-slate-500 mb-1 uppercase">{label}</label>
      <div className="flex items-center gap-1">
        {prefix && <span className="text-xs text-slate-400">{prefix}</span>}
        <input type="number" step="any" value={value || ''} onChange={e => onChange(parseFloat(e.target.value) || 0)} className="w-full border border-slate-300 rounded px-2 py-1.5 text-sm focus:ring-1 focus:ring-indigo-500" />
        {suffix && <span className="text-xs text-slate-400">{suffix}</span>}
      </div>
    </div>
  );
}

interface SRSProjection {
  year: number; age: number; contribution: number; growth: number; withdrawal: number; accumulated: number;
}

function generateSRSProjection(params: { currentAge: number; currentBalance: number; annualContribution: number; contributionYears: number; growthRate: number; withdrawalAge: number; annualWithdrawal: number }): SRSProjection[] {
  const results: SRSProjection[] = [];
  let balance = params.currentBalance;
  const currentYear = new Date().getFullYear();
  const endAge = params.withdrawalAge + 10; // withdraw for 10 years

  for (let age = params.currentAge; age <= endAge && balance > 0; age++) {
    const yearsFromNow = age - params.currentAge;
    const isContributing = yearsFromNow < params.contributionYears;
    const isWithdrawing = age >= params.withdrawalAge;

    const contribution = isContributing ? params.annualContribution : 0;
    balance += contribution;
    const growth = balance * (params.growthRate / 100);
    balance += growth;
    const withdrawal = isWithdrawing ? Math.min(params.annualWithdrawal, balance) : 0;
    balance -= withdrawal;

    results.push({ year: currentYear + yearsFromNow, age, contribution, growth, withdrawal, accumulated: Math.max(0, balance) });
  }

  return results;
}
