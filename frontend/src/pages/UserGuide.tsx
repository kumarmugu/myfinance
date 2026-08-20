import { LayoutDashboard, TrendingUp, Landmark, DollarSign, Bitcoin, Banknote, Target, Calculator, Shield, RefreshCw, Building2, FileBarChart, Receipt, Briefcase, Wallet, Home } from 'lucide-react';

export default function UserGuide() {
  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">User Guide</h1>
        <p className="text-slate-500 text-sm mt-0.5">How to use MyFinance to manage your portfolio and finances</p>
      </div>

      <Section icon={<LayoutDashboard size={18} className="text-indigo-600" />} title="Dashboard" desc="Your financial overview at a glance">
        <ul className="space-y-1">
          <li>View total net worth, invested amount, and gain/loss</li>
          <li>Use the <b>Owner</b> dropdown to filter by person (Self, Spouse, Family)</li>
          <li>Toggle between <b>SGD</b> and <b>USD</b> to view amounts in different currencies</li>
          <li><b>Take Snapshot</b> saves the current net worth for historical tracking</li>
          <li>Allocation pie chart shows your asset distribution</li>
          <li>Top/worst performers show your best and worst positions</li>
          <li>By Account bar chart shows distribution across brokers</li>
        </ul>
      </Section>

      <Section icon={<TrendingUp size={18} className="text-cyan-600" />} title="Portfolio" desc="Manage your stock/ETF/fund holdings">
        <ul className="space-y-1">
          <li><b>Holdings tab</b>: All active positions with P&L calculation and Purpose column</li>
          <li><b>Sold tab</b>: Historical sold positions with realized gains</li>
          <li><b>Short-Term tab</b>: Trades marked as TRADING or SHORT_TERM purpose</li>
          <li>Purpose column shows investment intent (Long Term, Trading, SRS, etc.)</li>
          <li>Holdings by Type pie chart shows current allocation</li>
        </ul>
      </Section>

      <Section icon={<Landmark size={18} className="text-emerald-600" />} title="Fixed Deposits (Sri Lanka)" desc="Track family FDs across banks">
        <ul className="space-y-1">
          <li>FDs are <b>not included in net worth by default</b> (separate from investments)</li>
          <li>Use the <b>NW checkbox</b> to manually include an FD with SGD amount</li>
          <li>Click <b>New FD</b> to add deposits with holder, bank, rates, and dates</li>
          <li>Edit and delete FDs from the table (hover to see actions)</li>
          <li>Filter by bank or holder to find specific FDs</li>
          <li>Yellow highlighted rows indicate FDs needing renewal</li>
          <li>Maturity alerts show FDs due within 30/90 days</li>
        </ul>
      </Section>

      <Section icon={<Shield size={18} className="text-slate-600" />} title="Insurance" desc="Life insurance with bonus projections">
        <ul className="space-y-1">
          <li>Track policies: Term Life, Whole Life, Endowment, ILP, Health, Critical Illness</li>
          <li>Not included in net worth by default</li>
          <li><b>Bonus Schedule</b>: Click to expand each policy and see yearly projections</li>
          <li>Track expected vs actual bonus per year (per the policy schedule)</li>
          <li>Add yearly entries with premium, expected bonus, actual bonus, totals</li>
        </ul>
      </Section>

      <Section icon={<Home size={18} className="text-indigo-600" />} title="Home Loans" desc="Manage properties and mortgages">
        <ul className="space-y-1">
          <li>Track multiple properties with value, loan amount, interest rate, EMI</li>
          <li>Property value minus outstanding = <b>Home Equity</b></li>
          <li>Toggle <b>Include in Net Worth</b> per property</li>
          <li>Expand each loan to see and add <b>Payment History</b></li>
          <li>Track principal vs interest split per payment</li>
          <li>Supports FIXED, FLOATING, and HDB loan types</li>
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
          <li>Separate page for crypto holdings</li>
          <li>Shows holdings per coin with cost basis and current value</li>
          <li>Add buy/sell transactions specific to crypto exchanges</li>
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
          <li>Click <b>Edit Targets</b> to update target percentages and amounts</li>
          <li>Bar chart compares target vs actual allocation</li>
          <li>Table shows the gap - how much more to invest in each category</li>
          <li>Net worth history tab shows snapshots over time</li>
          <li>Total % indicator helps ensure targets sum to 100%</li>
        </ul>
      </Section>

      <Section icon={<Calculator size={18} className="text-blue-600" />} title="SRS & CPF" desc="Retirement planning and contribution tracking">
        <ul className="space-y-1">
          <li><b>SRS</b>: Set contribution amount, growth rate, and withdrawal plan</li>
          <li><b>CPF</b>: Enter current OA/SA/MA balances for 20-year projection</li>
          <li><b>Contribution History</b>: Track actual deposits/withdrawals to CPF/EPF/SRS</li>
          <li>Filter by fund type (CPF, EPF, SRS)</li>
          <li>Track employer contributions, interest, and withdrawals</li>
          <li>Note: CPF/EPF are NOT included in net worth (already counted in equity)</li>
        </ul>
      </Section>

      <Section icon={<Receipt size={18} className="text-orange-600" />} title="Tax Records" desc="Track tax paid by assessment year">
        <ul className="space-y-1">
          <li>Record employment income, donations, reliefs, SRS deductions per year</li>
          <li><b>Auto Calc</b> button computes chargeable income and tax payable</li>
          <li>Income vs Tax bar chart visualizes trends</li>
          <li>Track effective tax rate over time</li>
          <li>Supports multiple countries (Singapore, Sri Lanka)</li>
        </ul>
      </Section>

      <Section icon={<Briefcase size={18} className="text-teal-600" />} title="Work Experience" desc="Career history tracker">
        <ul className="space-y-1">
          <li>List all companies worked at with position, level, and dates</li>
          <li>Shows duration (years/months) and total experience</li>
          <li>Mark current employer with "Currently working here" checkbox</li>
          <li>Timeline-style cards for visual career history</li>
        </ul>
      </Section>

      <Section icon={<Wallet size={18} className="text-purple-600" />} title="Salary" desc="Monthly salary and bonus tracking">
        <ul className="space-y-1">
          <li>Record monthly salary with breakdown: Basic, Allowance, Mobile, etc.</li>
          <li>Mark bonus entries separately with number of months</li>
          <li>Filter by year using pill buttons</li>
          <li>Annual salary bar chart shows income growth</li>
          <li>Summary shows yearly total, bonus total, and monthly average</li>
        </ul>
      </Section>

      <Section icon={<RefreshCw size={18} className="text-teal-600" />} title="FX Rates" desc="Manage exchange rates">
        <ul className="space-y-1">
          <li>Supports 15+ currencies (SGD, USD, EUR, LKR, INR, GBP, AUD, etc.)</li>
          <li><b>Add New Currency</b> section lets you add any currency code</li>
          <li>Used by Dashboard and Reports for currency conversion</li>
          <li>Rate history table shows all historical rates</li>
        </ul>
      </Section>

      <Section icon={<Building2 size={18} className="text-orange-600" />} title="Accounts & Owners" desc="Configure your setup">
        <ul className="space-y-1">
          <li><b>Owners</b>: Self, Spouse, Son, Daughter, Father, Mother, Brother, Sister</li>
          <li><b>Accounts</b>: Brokers, Banks, Crypto Exchanges</li>
          <li>Account type uses button groups instead of dropdowns</li>
          <li>Account numbers are masked (shows last 4 digits, click eye to reveal)</li>
          <li><b>Delete protection</b>: Cannot delete if referenced by transactions/holdings</li>
        </ul>
      </Section>

      <Section icon={<FileBarChart size={18} className="text-purple-600" />} title="Reports" desc="Comprehensive analytics">
        <ul className="space-y-1">
          <li>Net worth stacked area chart broken down by asset type</li>
          <li>Year-over-year growth comparison table</li>
          <li>Monthly investment flow (buy vs sell volume)</li>
          <li>Performance breakdown by broker and by asset type</li>
          <li>Filter by owner and toggle SGD/USD</li>
        </ul>
      </Section>

      <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
        <p className="text-sm font-medium text-slate-700">Tips</p>
        <ul className="text-xs text-slate-600 mt-2 space-y-1 list-disc list-inside">
          <li>Take snapshots regularly (monthly or quarterly) to build historical data</li>
          <li>Keep FX rates updated for accurate currency conversion</li>
          <li>Use the owner filter to view data per person</li>
          <li>Delete operations require password re-entry for safety</li>
          <li>Use the NW toggle on Assets page to exclude specific assets from net worth</li>
          <li>Transaction purpose (Long Term/Trading) determines short-term classification</li>
          <li>Cannot delete entities with references - clean up related data first</li>
          <li>Default login: admin / admin123 (change after first login)</li>
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
