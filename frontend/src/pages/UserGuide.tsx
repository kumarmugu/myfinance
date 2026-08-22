import { LayoutDashboard, TrendingUp, Landmark, DollarSign, Bitcoin, Banknote, Target, Calculator, Shield, RefreshCw, Building2, FileBarChart, Receipt, Briefcase, Wallet, Home, Settings, ArrowRight } from 'lucide-react';

export default function UserGuide() {
  return (
    <div className="space-y-8 max-w-4xl">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">User Guide</h1>
        <p className="text-slate-500 text-sm mt-1">Everything you need to know to manage your finances with MyFinance.</p>
      </div>

      {/* Quick Start */}
      <div className="bg-gradient-to-r from-indigo-500 to-purple-600 rounded-2xl p-6 text-white">
        <h2 className="text-lg font-bold">Quick Start</h2>
        <p className="text-indigo-100 text-sm mt-1 mb-4">Get started in 3 steps:</p>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Step n={1} text="Add your broker accounts under Settings > Accounts" />
          <Step n={2} text="Add your stocks/ETFs under Settings > Assets" />
          <Step n={3} text="Record buy/sell transactions — holdings update automatically" />
        </div>
      </div>

      {/* Feature Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <FeatureCard icon={<LayoutDashboard size={20} />} color="indigo" title="Dashboard" actions={['See net worth, gain/loss at a glance', 'Filter by owner (Self, Spouse, etc.)', 'Toggle SGD/USD display', 'Take snapshots for historical tracking']} />
        <FeatureCard icon={<TrendingUp size={20} />} color="cyan" title="Portfolio" actions={['View all holdings with live P&L', 'Sold positions with realized gains', 'Short-term trades (tagged as Trading)']} />
        <FeatureCard icon={<Landmark size={20} />} color="emerald" title="Fixed Deposits" actions={['Manage Sri Lanka FDs by bank/holder', 'Track maturity dates and interest', 'Toggle individual FDs into net worth']} />
        <FeatureCard icon={<Shield size={20} />} color="slate" title="Insurance" actions={['Track life insurance policies', 'View yearly bonus projections', 'Expand each policy to see schedule']} />
        <FeatureCard icon={<Home size={20} />} color="violet" title="Home Loans" actions={['Track property value vs loan outstanding', 'Record monthly payments (principal + interest)', 'Home equity = Value - Outstanding']} />
        <FeatureCard icon={<DollarSign size={20} />} color="green" title="Dividends" actions={['Record dividend income per broker', 'Filter by year or quarter', 'See yearly dividend growth chart']} />
        <FeatureCard icon={<Bitcoin size={20} />} color="amber" title="Crypto" actions={['Separate page for crypto holdings', 'Track by exchange (Coinhako, Crypto.com)', 'Buy/Sell transactions per coin']} />
        <FeatureCard icon={<Banknote size={20} />} color="purple" title="Deposits" actions={['Track money in/out of broker accounts', 'See net deposited per account', 'Helps calculate true returns']} />
        <FeatureCard icon={<Target size={20} />} color="rose" title="Allocation" actions={['Set target % for each asset type', 'Click Edit Targets to update', 'See gap between target and actual']} />
        <FeatureCard icon={<Calculator size={20} />} color="blue" title="SRS & CPF" actions={['SRS: project growth and withdrawals', 'CPF: track OA/SA/MA projections', 'Contribution History: record deposits']} />
        <FeatureCard icon={<Receipt size={20} />} color="orange" title="Tax Records" actions={['Record tax paid per assessment year', 'Auto-calculate chargeable income', 'See income vs tax chart over time']} />
        <FeatureCard icon={<Briefcase size={20} />} color="teal" title="Work Experience" actions={['List companies, positions, dates', 'See total years of experience', 'Timeline view of career history']} />
        <FeatureCard icon={<Wallet size={20} />} color="pink" title="Salary" actions={['Monthly salary with breakdown', 'Bulk add: same salary for whole year', 'Track bonuses separately']} />
        <FeatureCard icon={<RefreshCw size={20} />} color="teal" title="FX Rates" actions={['Set exchange rates (USD/SGD, etc.)', 'Add new currencies anytime', 'Used for all currency conversions']} />
        <FeatureCard icon={<Settings size={20} />} color="slate" title="Net Worth Config" actions={['Toggle which asset types count', 'Exclude CPF, Insurance from net worth', 'Applies to Dashboard, Reports, Snapshots']} />
        <FeatureCard icon={<FileBarChart size={20} />} color="purple" title="Reports" actions={['Net worth breakdown by type over time', 'Year-over-year growth comparison', 'Monthly investment flow analysis']} />
      </div>

      {/* Key Concepts */}
      <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
        <h3 className="font-semibold text-slate-800 mb-4">Key Concepts</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
          <Concept title="Snapshots" desc="Manually save your net worth at a point in time. Take these monthly for best historical tracking." />
          <Concept title="Purpose (Transactions)" desc="Tag each buy as Long Term, Trading, or SRS. Trading transactions appear in the Short-Term tab." />
          <Concept title="Net Worth Config" desc="Control which asset types are counted. CPF and Insurance are typically excluded." />
          <Concept title="Delete Protection" desc="Can't delete an account/asset if transactions reference it. Clean up references first." />
          <Concept title="FX Conversion" desc="All pages with SGD/USD toggle convert using the latest rate you set in FX Rates." />
          <Concept title="Owners" desc="Track finances per person. Filter any page by owner to see individual portfolios." />
        </div>
      </div>

      {/* Tips */}
      <div className="bg-indigo-50 border border-indigo-200 rounded-xl p-5">
        <h3 className="font-semibold text-indigo-800 text-sm mb-3">Tips</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs text-indigo-700">
          <p>Take snapshots monthly for good historical charts</p>
          <p>Keep FX rates updated for accurate conversions</p>
          <p>Use Bulk Add in Salary for fixed monthly amounts</p>
          <p>Default login: admin / admin123</p>
        </div>
      </div>
    </div>
  );
}

function Step({ n, text }: { n: number; text: string }) {
  return (
    <div className="flex items-start gap-3 bg-white/10 rounded-lg p-3">
      <span className="w-6 h-6 rounded-full bg-white/20 flex items-center justify-center text-xs font-bold shrink-0">{n}</span>
      <p className="text-sm text-indigo-50">{text}</p>
    </div>
  );
}

function FeatureCard({ icon, color, title, actions }: { icon: React.ReactNode; color: string; title: string; actions: string[] }) {
  const bgMap: Record<string, string> = { indigo: 'bg-indigo-100 text-indigo-600', cyan: 'bg-cyan-100 text-cyan-600', emerald: 'bg-emerald-100 text-emerald-600', green: 'bg-green-100 text-green-600', amber: 'bg-amber-100 text-amber-600', purple: 'bg-purple-100 text-purple-600', rose: 'bg-rose-100 text-rose-600', blue: 'bg-blue-100 text-blue-600', orange: 'bg-orange-100 text-orange-600', teal: 'bg-teal-100 text-teal-600', slate: 'bg-slate-100 text-slate-600', violet: 'bg-violet-100 text-violet-600', pink: 'bg-pink-100 text-pink-600' };

  return (
    <div className="bg-white rounded-xl p-4 border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
      <div className="flex items-center gap-3 mb-3">
        <div className={`p-2 rounded-lg ${bgMap[color] || 'bg-slate-100 text-slate-600'}`}>{icon}</div>
        <h4 className="font-semibold text-slate-800">{title}</h4>
      </div>
      <ul className="space-y-1.5">
        {actions.map((a, i) => (
          <li key={i} className="flex items-start gap-2 text-xs text-slate-600">
            <ArrowRight size={10} className="text-slate-400 mt-0.5 shrink-0" />
            {a}
          </li>
        ))}
      </ul>
    </div>
  );
}

function Concept({ title, desc }: { title: string; desc: string }) {
  return (
    <div className="bg-slate-50 rounded-lg p-3">
      <p className="font-medium text-slate-800 text-sm">{title}</p>
      <p className="text-xs text-slate-500 mt-0.5">{desc}</p>
    </div>
  );
}
