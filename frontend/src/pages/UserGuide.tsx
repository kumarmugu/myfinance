import { LayoutDashboard, TrendingUp, Landmark, DollarSign, Bitcoin, Banknote, Target, Calculator, Shield, RefreshCw, Building2, FileBarChart } from 'lucide-react';

export default function UserGuide() {
  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">User Guide</h1>
        <p className="text-slate-500 text-sm mt-0.5">How to use MyFinance to manage your portfolio</p>
      </div>

      <Section icon={<LayoutDashboard size={18} className="text-indigo-600" />} title="Dashboard" desc="Your financial overview at a glance">
        <ul className="space-y-1">
          <li>View total net worth, invested amount, and gain/loss</li>
          <li>Use the <b>Owner</b> dropdown to filter by person (Self/Spouse)</li>
          <li>Toggle between <b>SGD</b> and <b>USD</b> to view amounts in different currencies</li>
          <li><b>Take Snapshot</b> saves the current net worth for historical tracking</li>
          <li>Allocation pie chart shows your asset distribution</li>
          <li>Top/worst performers show your best and worst positions</li>
        </ul>
      </Section>

      <Section icon={<TrendingUp size={18} className="text-cyan-600" />} title="Portfolio" desc="Manage your stock/ETF/fund holdings">
        <ul className="space-y-1">
          <li><b>Holdings tab</b>: All active positions with P&L calculation</li>
          <li><b>Sold tab</b>: Historical sold positions with realized gains</li>
          <li><b>Short-Term tab</b>: Short duration trades tracked separately</li>
          <li>Add transactions via the Transactions page to update holdings automatically</li>
        </ul>
      </Section>

      <Section icon={<Landmark size={18} className="text-emerald-600" />} title="Fixed Deposits (Sri Lanka)" desc="Track family FDs across banks">
        <ul className="space-y-1">
          <li>FDs are <b>not included in net worth by default</b> (separate from investments)</li>
          <li>Use the checkbox in the NW column to manually include an FD with SGD amount</li>
          <li>Filter by bank or holder to find specific FDs</li>
          <li>Yellow highlighted rows indicate FDs needing renewal</li>
          <li>Maturity alerts show FDs due within 30/90 days</li>
        </ul>
      </Section>

      <Section icon={<DollarSign size={18} className="text-green-600" />} title="Dividends" desc="Track dividend income">
        <ul className="space-y-1">
          <li>Record dividends with broker, instrument symbol, amount, and quarter</li>
          <li>Filter by broker or year to view specific periods</li>
          <li>Summary shows total by instrument and by broker</li>
          <li>Yearly bar chart shows dividend growth over time</li>
        </ul>
      </Section>

      <Section icon={<Bitcoin size={18} className="text-amber-600" />} title="Crypto" desc="Dedicated cryptocurrency tracking">
        <ul className="space-y-1">
          <li>Separate page for crypto - not mixed with main portfolio view</li>
          <li>Shows holdings per coin with cost basis and current value</li>
          <li>Add buy/sell transactions specific to crypto exchanges</li>
          <li>Pie chart shows allocation by coin</li>
        </ul>
      </Section>

      <Section icon={<Banknote size={18} className="text-violet-600" />} title="Deposits" desc="Track cash flows to/from brokers">
        <ul className="space-y-1">
          <li>Record when you deposit or withdraw money from broker accounts</li>
          <li>Summary shows net deposited per account</li>
          <li>Helps calculate true return on invested capital</li>
        </ul>
      </Section>

      <Section icon={<Target size={18} className="text-rose-600" />} title="Planning (Allocation)" desc="Target vs actual allocation">
        <ul className="space-y-1">
          <li>Set target percentages for each asset type (Index, Growth, etc.)</li>
          <li>Bar chart compares target vs actual allocation</li>
          <li>Table shows the gap - how much more to invest in each category</li>
          <li>Net worth history tab shows snapshots over time</li>
        </ul>
      </Section>

      <Section icon={<Calculator size={18} className="text-blue-600" />} title="SRS & CPF" desc="Retirement planning projections">
        <ul className="space-y-1">
          <li><b>SRS</b>: Set your contribution amount, growth rate, and withdrawal plan</li>
          <li>Year-by-year projection shows how your SRS grows and depletes</li>
          <li><b>CPF</b>: Enter current balances and salary for 20-year projection</li>
          <li>All calculations happen locally - adjust parameters to see scenarios</li>
        </ul>
      </Section>

      <Section icon={<Shield size={18} className="text-slate-600" />} title="Insurance" desc="Track life insurance policies">
        <ul className="space-y-1">
          <li>Not included in net worth by default</li>
          <li>Track premium, coverage, cash value, and maturity</li>
          <li>Supports various types: Term Life, Whole Life, Endowment, ILP, Health</li>
        </ul>
      </Section>

      <Section icon={<RefreshCw size={18} className="text-teal-600" />} title="FX Rates" desc="Manage exchange rates">
        <ul className="space-y-1">
          <li>Set rates for USD/SGD, EUR/SGD, LKR/SGD</li>
          <li>Used by Dashboard and Reports for currency conversion</li>
          <li>Update rates manually when they change</li>
        </ul>
      </Section>

      <Section icon={<Building2 size={18} className="text-orange-600" />} title="Accounts & Owners" desc="Configure your setup">
        <ul className="space-y-1">
          <li><b>Owners</b>: Add yourself and spouse for separate tracking</li>
          <li><b>Accounts</b>: Add brokers (Tiger, Saxo, IBKR) and banks (DBS, OCBC)</li>
          <li>Link accounts to owners for proper filtering</li>
          <li>Account numbers are masked for security (click eye icon to reveal)</li>
        </ul>
      </Section>

      <Section icon={<FileBarChart size={18} className="text-purple-600" />} title="Reports" desc="Comprehensive analytics">
        <ul className="space-y-1">
          <li>Filter by owner and toggle SGD/USD</li>
          <li>Net worth stacked area chart by asset type</li>
          <li>Year-over-year growth comparison</li>
          <li>Performance breakdown by broker and by asset type</li>
          <li>Monthly investment flow (buy vs sell volume)</li>
        </ul>
      </Section>

      <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
        <p className="text-sm font-medium text-slate-700">Tips</p>
        <ul className="text-xs text-slate-600 mt-2 space-y-1 list-disc list-inside">
          <li>Take snapshots regularly (monthly or quarterly) to build historical data</li>
          <li>Keep FX rates updated for accurate currency conversion</li>
          <li>Use the owner filter to view data per person</li>
          <li>Delete operations require password re-entry for safety</li>
          <li>Default login: admin / admin123 (change via Settings after first login)</li>
        </ul>
      </div>
    </div>
  );
}

function Section({ icon, title, desc, children }: { icon: React.ReactNode; title: string; desc: string; children: React.ReactNode }) {
  return (
    <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
      <div className="flex items-center gap-2 mb-2">{icon}<h3 className="text-sm font-semibold text-slate-800">{title}</h3><span className="text-xs text-slate-400">— {desc}</span></div>
      <div className="text-sm text-slate-600 pl-7">{children}</div>
    </div>
  );
}
