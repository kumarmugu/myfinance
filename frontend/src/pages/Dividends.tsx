import { useEffect, useState } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { getDividends, getDividendSummary } from '../api';
import { formatCurrency, formatDate } from '../utils/formatters';
import type { Dividend } from '../types';

export default function Dividends() {
  const [dividends, setDividends] = useState<Dividend[]>([]);
  const [summary, setSummary] = useState<Array<{ year: number; total: number }>>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [divRes, sumRes] = await Promise.all([getDividends(), getDividendSummary()]);
      setDividends(divRes.data);
      setSummary(sumRes.data.map(([year, total]: [number, number]) => ({ year, total })));
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const totalDividends = dividends.reduce((s, d) => s + d.amount, 0);

  // Group by broker
  const byBroker: Record<string, number> = {};
  dividends.forEach(d => { byBroker[d.account.name] = (byBroker[d.account.name] || 0) + d.amount; });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Dividends</h1>
        <p className="text-slate-500 text-sm mt-1">Track dividend income across all brokers</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <p className="text-sm text-slate-500 mb-1">Total Dividends</p>
          <p className="text-2xl font-bold text-slate-800">{formatCurrency(totalDividends)}</p>
        </div>
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <p className="text-sm text-slate-500 mb-1">Total Records</p>
          <p className="text-2xl font-bold text-slate-800">{dividends.length}</p>
        </div>
        <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
          <p className="text-sm text-slate-500 mb-1">By Broker</p>
          <div className="space-y-1 mt-1">
            {Object.entries(byBroker).sort((a, b) => b[1] - a[1]).map(([broker, amt]) => (
              <div key={broker} className="flex justify-between text-sm"><span className="text-slate-600">{broker}</span><span className="font-medium text-slate-800">{formatCurrency(amt)}</span></div>
            ))}
          </div>
        </div>
      </div>

      {/* Yearly Chart */}
      {summary.length > 0 && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="font-semibold text-slate-800 mb-4">Dividend Income by Year</h3>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={summary}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="year" stroke="#94a3b8" fontSize={12} />
              <YAxis stroke="#94a3b8" fontSize={12} tickFormatter={v => `S$${v}`} />
              <Tooltip formatter={(v) => formatCurrency(v as number)} />
              <Bar dataKey="total" fill="#10b981" radius={[4, 4, 0, 0]} name="Dividend" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Dividend Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Date</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Instrument</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Broker</th>
                <th className="text-left px-4 py-3 font-medium text-slate-600">Quarter</th>
                <th className="text-right px-4 py-3 font-medium text-slate-600">Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {dividends.map(d => (
                <tr key={d.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 text-slate-700">{formatDate(d.receivedDate)}</td>
                  <td className="px-4 py-3 font-medium text-slate-800">{d.instrument || d.asset?.symbol || '-'}</td>
                  <td className="px-4 py-3 text-slate-600">{d.account.name}</td>
                  <td className="px-4 py-3 text-slate-500">{d.year}-{d.quarter}</td>
                  <td className="px-4 py-3 text-right font-medium text-green-600">{formatCurrency(d.amount, d.currency)}</td>
                </tr>
              ))}
              {dividends.length === 0 && <tr><td colSpan={5} className="px-4 py-12 text-center text-slate-400">No dividends recorded</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
