import { useState } from 'react';
import { FileText, Server, Palette, Database, Layout, Layers, Globe, Users, BarChart3, Shield, Rocket } from 'lucide-react';
import SwaggerUI from 'swagger-ui-react';
import 'swagger-ui-react/swagger-ui.css';

type TabId = 'architecture' | 'design' | 'api';

export default function Docs() {
  const [activeTab, setActiveTab] = useState<TabId>('architecture');

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Documentation</h1>
        <p className="text-slate-500 text-sm mt-1">Architecture, design, and API documentation</p>
      </div>

      {/* Tab Navigation */}
      <div className="flex gap-1 bg-slate-100 rounded-lg p-1 w-fit">
        <button
          onClick={() => setActiveTab('architecture')}
          className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${
            activeTab === 'architecture'
              ? 'bg-white text-indigo-700 shadow-sm'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          <Server size={16} />
          Architecture
        </button>
        <button
          onClick={() => setActiveTab('design')}
          className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${
            activeTab === 'design'
              ? 'bg-white text-indigo-700 shadow-sm'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          <Palette size={16} />
          Design
        </button>
        <button
          onClick={() => setActiveTab('api')}
          className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${
            activeTab === 'api'
              ? 'bg-white text-indigo-700 shadow-sm'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          <Globe size={16} />
          API Docs (Swagger)
        </button>
      </div>

      {activeTab === 'architecture' ? <ArchitectureDoc /> : activeTab === 'design' ? <DesignDoc /> : <ApiDocsEmbed />}
    </div>
  );
}

/* ─────────────────────────────────────────────────────── */
/*                   EMBEDDED SWAGGER UI                     */
/* ─────────────────────────────────────────────────────── */

function ApiDocsEmbed() {
  return (
    <div className="space-y-4">
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-3 flex items-start gap-2">
        <Globe size={16} className="text-blue-600 mt-0.5 shrink-0" />
        <div>
          <p className="text-sm text-blue-800 font-medium">Live API Documentation</p>
          <p className="text-xs text-blue-600">OpenAPI spec auto-generated from the backend. Requires backend running on port 8080.</p>
        </div>
      </div>
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden swagger-container">
        <SwaggerUI url="http://localhost:8080/v3/api-docs" />
      </div>
    </div>
  );
}

/* ─────────────────────────────────────────────────────── */
/*                   ARCHITECTURE DOCUMENT                   */
/* ─────────────────────────────────────────────────────── */

function ArchitectureDoc() {
  return (
    <div className="space-y-8">
      {/* Overview */}
      <DocSection icon={<FileText size={20} className="text-indigo-600" />} title="1. Overview">
        <p>
          MyFinance is a personal finance management application designed to track, analyze, and plan
          investments across multiple brokerage accounts, fixed deposits, cryptocurrencies, and
          retirement plans. The application supports multi-currency portfolios (SGD, USD, EUR, LKR),
          multi-owner profiles, and comprehensive reporting with historical tracking.
        </p>
      </DocSection>

      {/* Tech Stack */}
      <DocSection icon={<Layers size={20} className="text-indigo-600" />} title="2. Tech Stack">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <h4 className="font-medium text-slate-700 mb-2">Backend</h4>
            <table className="w-full text-sm">
              <tbody className="divide-y divide-slate-100">
                <TechRow name="Java" version="17" purpose="Core language" />
                <TechRow name="Spring Boot" version="3.2.5" purpose="Framework" />
                <TechRow name="Spring Data JPA" version="6.x" purpose="ORM" />
                <TechRow name="H2 / PostgreSQL" version="" purpose="Database" />
                <TechRow name="Maven" version="3.9+" purpose="Build tool" />
                <TechRow name="Lombok" version="" purpose="Boilerplate reduction" />
              </tbody>
            </table>
          </div>
          <div>
            <h4 className="font-medium text-slate-700 mb-2">Frontend</h4>
            <table className="w-full text-sm">
              <tbody className="divide-y divide-slate-100">
                <TechRow name="React" version="18.3" purpose="UI framework" />
                <TechRow name="TypeScript" version="5.6" purpose="Type safety" />
                <TechRow name="Vite" version="5.4" purpose="Build tool" />
                <TechRow name="Tailwind CSS" version="4.3" purpose="Styling" />
                <TechRow name="Recharts" version="3.10" purpose="Charts" />
                <TechRow name="React Router" version="7.18" purpose="Routing" />
              </tbody>
            </table>
          </div>
        </div>
      </DocSection>

      {/* System Architecture */}
      <DocSection icon={<Server size={20} className="text-indigo-600" />} title="3. System Architecture">
        <div className="bg-slate-900 text-green-400 p-4 rounded-lg font-mono text-xs overflow-x-auto">
          <pre>{`┌─────────────────────────────────────────────────────────────┐
│                        Frontend (SPA)                        │
│   React 18 + TypeScript + Tailwind CSS + Recharts           │
│   ┌──────────┬──────────┬──────────┬──────────┬──────────┐  │
│   │Dashboard │Portfolio │Fixed Dep │Planning  │Reports   │  │
│   └──────────┴──────────┴──────────┴──────────┴──────────┘  │
└────────────────────────────┬────────────────────────────────┘
                             │ REST API (JSON)
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot)                      │
│   ┌─────────────────────────────────────────────────────┐   │
│   │  Controllers → Services → Repositories → Entities   │   │
│   └─────────────────────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────┘
                             │ JDBC
                             ▼
┌─────────────────────────────────────────────────────────────┐
│              Database (H2 dev / PostgreSQL prod)              │
└─────────────────────────────────────────────────────────────┘`}</pre>
        </div>
      </DocSection>

      {/* Modules */}
      <DocSection icon={<Layout size={20} className="text-indigo-600" />} title="4. Module Architecture">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <ModuleCard
            title="Core Module"
            items={['Owner/Profile Management', 'Account Management', 'Currency Management']}
            color="indigo"
          />
          <ModuleCard
            title="Investment Portfolio"
            items={['Asset Management', 'Transactions (Buy/Sell)', 'Holdings & P&L', 'Sold Positions', 'Dividends', 'Money Market']}
            color="cyan"
          />
          <ModuleCard
            title="Fixed Deposits"
            items={['FD CRUD Management', 'Maturity Tracking', 'Interest Calculation', 'Family Member Mapping', 'Bank/Branch Management']}
            color="emerald"
          />
          <ModuleCard
            title="Financial Planning"
            items={['Net Worth Tracking', 'Target Allocation', 'SRS Planning', 'Retirement Projections', 'Deposit/Withdrawal Tracking']}
            color="violet"
          />
          <ModuleCard
            title="Cryptocurrency"
            items={['Multi-exchange Portfolio', 'Multi-coin Support', 'Profit/Loss Tracking']}
            color="amber"
          />
          <ModuleCard
            title="Reporting & Analytics"
            items={['Dashboard Summary', 'Net Worth History', 'YoY Comparison', 'Performance by Broker', 'Allocation Analysis']}
            color="rose"
          />
        </div>
      </DocSection>

      {/* Enums */}
      <DocSection icon={<Database size={20} className="text-indigo-600" />} title="5. Key Enums">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <EnumCard title="AssetType" values={[
            'INDEX_FUND', 'MUTUAL_FUND', 'GROWTH_EQUITY', 'DIVIDEND_EQUITY',
            'LEVERAGED_ETF', 'MONEY_MARKET', 'FIXED_DEPOSIT', 'SAVINGS', 'CRYPTO'
          ]} />
          <EnumCard title="AccountType" values={['BROKER', 'BANK', 'CRYPTO_EXCHANGE', 'FIXED_DEPOSIT']} />
          <EnumCard title="TransactionType" values={['BUY', 'SELL', 'DIVIDEND', 'DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'INTEREST']} />
          <EnumCard title="Currency" values={['SGD', 'USD', 'EUR', 'LKR']} />
          <EnumCard title="FDStatus" values={['ACTIVE', 'MATURED', 'RENEWED', 'CLOSED', 'REQUIRES_UPDATE']} />
          <EnumCard title="OwnerRelationship" values={['SELF', 'SPOUSE', 'PARENT_FATHER', 'PARENT_MOTHER', 'SIBLING']} />
        </div>
      </DocSection>

      {/* Multi-Currency */}
      <DocSection icon={<Globe size={20} className="text-indigo-600" />} title="6. Multi-Currency Strategy">
        <div className="space-y-3">
          <p className="text-sm text-slate-600">All monetary values are stored with their original currency. Conversion to SGD is applied at display time for reporting.</p>
          <table className="w-full text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left px-3 py-2 font-medium text-slate-600">From</th>
                <th className="text-left px-3 py-2 font-medium text-slate-600">To</th>
                <th className="text-left px-3 py-2 font-medium text-slate-600">Rate</th>
                <th className="text-left px-3 py-2 font-medium text-slate-600">Usage</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              <tr><td className="px-3 py-2">USD</td><td className="px-3 py-2">SGD</td><td className="px-3 py-2">1.27</td><td className="px-3 py-2">US stocks (Tiger, Saxo, IBKR)</td></tr>
              <tr><td className="px-3 py-2">EUR</td><td className="px-3 py-2">SGD</td><td className="px-3 py-2">1.49</td><td className="px-3 py-2">Euro-denominated funds</td></tr>
              <tr><td className="px-3 py-2">LKR</td><td className="px-3 py-2">SGD</td><td className="px-3 py-2">0.0042</td><td className="px-3 py-2">Sri Lanka fixed deposits</td></tr>
            </tbody>
          </table>
        </div>
      </DocSection>

      {/* Multi-Owner */}
      <DocSection icon={<Users size={20} className="text-indigo-600" />} title="7. Multi-Owner Strategy">
        <div className="space-y-3">
          <p className="text-sm text-slate-600">The application supports tracking finances for multiple family members with data isolation per owner.</p>
          <table className="w-full text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left px-3 py-2 font-medium text-slate-600">Owner</th>
                <th className="text-left px-3 py-2 font-medium text-slate-600">Role</th>
                <th className="text-left px-3 py-2 font-medium text-slate-600">Accounts</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              <tr><td className="px-3 py-2">Primary User</td><td className="px-3 py-2">Self</td><td className="px-3 py-2">Tiger, Saxo, IBKR, Poems, Moomoo, DBS, OCBC, CIMB, Coinhako, Crypto.com</td></tr>
              <tr><td className="px-3 py-2">Spouse</td><td className="px-3 py-2">Wife</td><td className="px-3 py-2">Saxo (separate), Tiger (separate)</td></tr>
              <tr><td className="px-3 py-2">Parents</td><td className="px-3 py-2">Family</td><td className="px-3 py-2">Fixed Deposits (Sri Lanka)</td></tr>
            </tbody>
          </table>
        </div>
      </DocSection>

      {/* API Overview */}
      <DocSection icon={<Server size={20} className="text-indigo-600" />} title="8. API Endpoints Overview">
        <div className="space-y-4">
          <ApiGroup title="Core" endpoints={[
            { method: 'GET', path: '/api/owners', desc: 'List all owners' },
            { method: 'GET', path: '/api/accounts', desc: 'List accounts' },
            { method: 'GET', path: '/api/currencies/rates', desc: 'Exchange rates' },
          ]} />
          <ApiGroup title="Investments" endpoints={[
            { method: 'GET', path: '/api/assets', desc: 'List assets' },
            { method: 'GET/POST', path: '/api/transactions', desc: 'Manage transactions' },
            { method: 'GET', path: '/api/holdings', desc: 'Active holdings' },
            { method: 'GET', path: '/api/holdings/sold', desc: 'Sold positions' },
            { method: 'GET/POST', path: '/api/dividends', desc: 'Dividend records' },
          ]} />
          <ApiGroup title="Fixed Deposits" endpoints={[
            { method: 'GET/POST', path: '/api/fixed-deposits', desc: 'Manage FDs' },
            { method: 'GET', path: '/api/fixed-deposits/maturing', desc: 'Upcoming maturities' },
            { method: 'GET', path: '/api/fixed-deposits/summary', desc: 'FD summary' },
            { method: 'GET', path: '/api/fixed-deposits/interest-report', desc: 'Interest income' },
          ]} />
          <ApiGroup title="Planning" endpoints={[
            { method: 'GET', path: '/api/planning/allocation', desc: 'Current vs target' },
            { method: 'GET', path: '/api/planning/srs', desc: 'SRS projections' },
            { method: 'GET', path: '/api/planning/net-worth/history', desc: 'Net worth history' },
          ]} />
          <ApiGroup title="Reports" endpoints={[
            { method: 'GET', path: '/api/reports/dashboard', desc: 'Dashboard summary' },
            { method: 'GET', path: '/api/reports/performance', desc: 'Performance analysis' },
            { method: 'GET', path: '/api/reports/yoy', desc: 'Year-over-year' },
            { method: 'GET', path: '/api/reports/dividends/summary', desc: 'Dividend summary' },
          ]} />
        </div>
      </DocSection>

      {/* Roadmap */}
      <DocSection icon={<Rocket size={20} className="text-indigo-600" />} title="9. Version Roadmap">
        <div className="space-y-3">
          <RoadmapItem version="v1.0" status="current" title="Basic portfolio tracking" desc="Current implementation with sample Indian market data" />
          <RoadmapItem version="v2.0" status="next" title="Multi-currency & multi-owner" desc="SGD/USD support, owner profiles, Singapore/US market" />
          <RoadmapItem version="v2.1" status="planned" title="Fixed Deposits module" desc="Sri Lanka FD management with maturity tracking" />
          <RoadmapItem version="v2.2" status="planned" title="Financial Planning & SRS" desc="Target allocation, SRS projections, retirement planning" />
          <RoadmapItem version="v2.3" status="planned" title="Advanced reporting" desc="YoY comparison, historical charts, dividend reports" />
          <RoadmapItem version="v3.0" status="future" title="Data import & automation" desc="CSV/Excel import, automated price updates" />
        </div>
      </DocSection>
    </div>
  );
}

/* ─────────────────────────────────────────────────────── */
/*                      DESIGN DOCUMENT                      */
/* ─────────────────────────────────────────────────────── */

function DesignDoc() {
  return (
    <div className="space-y-8">
      {/* Database Schema */}
      <DocSection icon={<Database size={20} className="text-indigo-600" />} title="1. Database Schema">
        <div className="space-y-6">
          <SchemaTable title="Core Tables" tables={[
            { name: 'owners', cols: 'id, name, relationship, is_active, created_at' },
            { name: 'accounts', cols: 'id, name, account_type, owner_id, currency, description' },
            { name: 'assets', cols: 'id, name, symbol, asset_type, current_price, currency, exchange' },
            { name: 'currency_rates', cols: 'id, from_currency, to_currency, rate, effective_date' },
          ]} />
          <SchemaTable title="Investment Tables" tables={[
            { name: 'transactions', cols: 'id, asset_id, account_id, owner_id, type, quantity, price_per_unit, total_amount, currency, date' },
            { name: 'holdings', cols: 'id, asset_id, account_id, owner_id, quantity, avg_buy_price, invested_amount, currency' },
            { name: 'sold_positions', cols: 'id, asset_id, account_id, owner_id, quantity, buy_price, sell_price, profit, invested_date, sold_date' },
            { name: 'dividends', cols: 'id, asset_id, account_id, owner_id, amount, currency, received_date, year, quarter' },
            { name: 'account_deposits', cols: 'id, account_id, amount, deposit_type, currency, deposit_date' },
          ]} />
          <SchemaTable title="Fixed Deposit Tables" tables={[
            { name: 'banks', cols: 'id, name, short_name, country' },
            { name: 'fd_holders', cols: 'id, name, relationship, is_senior_citizen' },
            { name: 'fixed_deposits', cols: 'id, holder_id, joint_holder_id, bank_id, account_number, principal_amount, interest_rate, start_date, maturity_date, period, branch, category, status' },
          ]} />
          <SchemaTable title="Planning Tables" tables={[
            { name: 'net_worth_snapshots', cols: 'id, owner_id, snapshot_date, type_breakdowns, total_net_worth, currency' },
            { name: 'allocation_targets', cols: 'id, owner_id, asset_type, target_percentage, target_amount' },
            { name: 'srs_plans', cols: 'id, owner_id, year, age, contribution, accumulated_value, growth_rate, tax_relief' },
            { name: 'account_net_worth_history', cols: 'id, account_id, owner_id, year, value, currency' },
          ]} />
        </div>
      </DocSection>

      {/* UI Design */}
      <DocSection icon={<Layout size={20} className="text-indigo-600" />} title="2. UI Design System">
        <div className="space-y-4">
          <h4 className="font-medium text-slate-700">Color Palette</h4>
          <div className="flex flex-wrap gap-3">
            <ColorSwatch name="Primary" color="#6366f1" />
            <ColorSwatch name="Success" color="#10b981" />
            <ColorSwatch name="Danger" color="#ef4444" />
            <ColorSwatch name="Warning" color="#f59e0b" />
            <ColorSwatch name="Info" color="#06b6d4" />
            <ColorSwatch name="Neutral" color="#64748b" />
          </div>

          <h4 className="font-medium text-slate-700 mt-6">Chart Color Scheme</h4>
          <div className="flex flex-wrap gap-3">
            <ColorSwatch name="Index Fund" color="#6366f1" />
            <ColorSwatch name="Growth" color="#06b6d4" />
            <ColorSwatch name="Mutual" color="#10b981" />
            <ColorSwatch name="Dividend" color="#8b5cf6" />
            <ColorSwatch name="Leveraged" color="#f97316" />
            <ColorSwatch name="Money Mkt" color="#14b8a6" />
            <ColorSwatch name="FD" color="#64748b" />
            <ColorSwatch name="Savings" color="#eab308" />
            <ColorSwatch name="Crypto" color="#f59e0b" />
          </div>
        </div>
      </DocSection>

      {/* Fixed Deposit Design */}
      <DocSection icon={<BarChart3 size={20} className="text-indigo-600" />} title="3. Fixed Deposit Module Design">
        <div className="space-y-4">
          <h4 className="font-medium text-slate-700">FD Categories</h4>
          <table className="w-full text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left px-3 py-2 font-medium text-slate-600">Category</th>
                <th className="text-left px-3 py-2 font-medium text-slate-600">Holder Groups</th>
                <th className="text-left px-3 py-2 font-medium text-slate-600">Description</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              <tr><td className="px-3 py-2">Parents</td><td className="px-3 py-2">Parent A, Parent B</td><td className="px-3 py-2">Parents' FDs</td></tr>
              <tr><td className="px-3 py-2">Self</td><td className="px-3 py-2">Self/Parent B, Self/Parent A</td><td className="px-3 py-2">User's FDs (joint)</td></tr>
              <tr><td className="px-3 py-2">Sibling 1</td><td className="px-3 py-2">Sibling1/Parent B, Sibling1/Parent A</td><td className="px-3 py-2">Sibling's FDs</td></tr>
              <tr><td className="px-3 py-2">Sibling 2</td><td className="px-3 py-2">Sibling2/Parent B, Sibling2/Parent A</td><td className="px-3 py-2">Another sibling's FDs</td></tr>
            </tbody>
          </table>

          <h4 className="font-medium text-slate-700 mt-4">Supported Banks</h4>
          <div className="flex flex-wrap gap-2">
            {['NSB', 'BOC', 'Commercial Bank', 'Seylan', 'Peoples Bank', 'HNB', 'Sampath'].map(bank => (
              <span key={bank} className="px-3 py-1 bg-slate-100 rounded-full text-sm text-slate-700">{bank}</span>
            ))}
          </div>

          <h4 className="font-medium text-slate-700 mt-4">Status Workflow</h4>
          <div className="bg-slate-50 p-4 rounded-lg font-mono text-xs">
            <pre>{`ACTIVE → MATURED → CLOSED
ACTIVE → RENEWED (new term)
ACTIVE → REQUIRES_UPDATE (needs verification)`}</pre>
          </div>

          <h4 className="font-medium text-slate-700 mt-4">FD Reports</h4>
          <ul className="list-disc list-inside text-sm text-slate-600 space-y-1">
            <li>Summary Dashboard - Total by bank, by holder</li>
            <li>Maturity Calendar - Visual timeline of upcoming maturities</li>
            <li>Interest Income Report - Expected vs realized</li>
            <li>Beneficiary Report - Split between family members</li>
            <li>Renewal Alerts - FDs needing action</li>
          </ul>
        </div>
      </DocSection>

      {/* Planning Design */}
      <DocSection icon={<BarChart3 size={20} className="text-indigo-600" />} title="4. Financial Planning Design">
        <div className="space-y-4">
          <h4 className="font-medium text-slate-700">Target Allocation (S$1,000,000 Target)</h4>
          <table className="w-full text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="text-left px-3 py-2 font-medium text-slate-600">Asset Type</th>
                <th className="text-right px-3 py-2 font-medium text-slate-600">Target %</th>
                <th className="text-right px-3 py-2 font-medium text-slate-600">Target $</th>
                <th className="text-right px-3 py-2 font-medium text-slate-600">Current %</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              <tr><td className="px-3 py-2">Index Fund</td><td className="px-3 py-2 text-right">37%</td><td className="px-3 py-2 text-right">-</td><td className="px-3 py-2 text-right">-</td></tr>
              <tr><td className="px-3 py-2">Growth Equity</td><td className="px-3 py-2 text-right">23%</td><td className="px-3 py-2 text-right">-</td><td className="px-3 py-2 text-right">-</td></tr>
              <tr><td className="px-3 py-2">Dividend Equity</td><td className="px-3 py-2 text-right">10%</td><td className="px-3 py-2 text-right">-</td><td className="px-3 py-2 text-right">-</td></tr>
              <tr><td className="px-3 py-2">Money Market</td><td className="px-3 py-2 text-right">10%</td><td className="px-3 py-2 text-right">-</td><td className="px-3 py-2 text-right">-</td></tr>
              <tr><td className="px-3 py-2">Leveraged ETF</td><td className="px-3 py-2 text-right">5%</td><td className="px-3 py-2 text-right">-</td><td className="px-3 py-2 text-right">-</td></tr>
              <tr><td className="px-3 py-2">Fixed Deposit</td><td className="px-3 py-2 text-right">5%</td><td className="px-3 py-2 text-right">-</td><td className="px-3 py-2 text-right">-</td></tr>
              <tr><td className="px-3 py-2">Crypto</td><td className="px-3 py-2 text-right">5%</td><td className="px-3 py-2 text-right">-</td><td className="px-3 py-2 text-right">-</td></tr>
              <tr><td className="px-3 py-2">Savings</td><td className="px-3 py-2 text-right">3%</td><td className="px-3 py-2 text-right">-</td><td className="px-3 py-2 text-right">-</td></tr>
              <tr><td className="px-3 py-2">Mutual Fund</td><td className="px-3 py-2 text-right">2%</td><td className="px-3 py-2 text-right">-</td><td className="px-3 py-2 text-right">-</td></tr>
            </tbody>
          </table>

          <h4 className="font-medium text-slate-700 mt-4">SRS (Supplementary Retirement Scheme)</h4>
          <ul className="list-disc list-inside text-sm text-slate-600 space-y-1">
            <li>Annual contribution limit: S$15,300 (non-citizen max)</li>
            <li>Expected growth rate: 8% (configurable)</li>
            <li>Tax relief rate: 19.5% of contribution</li>
            <li>Withdrawal eligibility: After age 62</li>
            <li>Withdrawal strategy: S$60,000/year over 10 years</li>
            <li>Tax on withdrawal: 50% taxable at marginal rate</li>
          </ul>

          <h4 className="font-medium text-slate-700 mt-4">Net Worth Tracking Dimensions</h4>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase mb-1">By Account</p>
              <div className="flex flex-wrap gap-1">
                {['DBS', 'OCBC', 'CIMB', 'Tiger', 'Saxo', 'Poems', 'IBKR', 'Moomoo', 'Coinhako', 'Crypto.com'].map(a => (
                  <span key={a} className="px-2 py-0.5 bg-indigo-50 text-indigo-700 rounded text-xs">{a}</span>
                ))}
              </div>
            </div>
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase mb-1">By Asset Type</p>
              <div className="flex flex-wrap gap-1">
                {['Index', 'Mutual', 'Growth', 'Dividend', '3x ETF', 'Money Mkt', 'FD', 'Savings', 'Crypto'].map(t => (
                  <span key={t} className="px-2 py-0.5 bg-emerald-50 text-emerald-700 rounded text-xs">{t}</span>
                ))}
              </div>
            </div>
          </div>
        </div>
      </DocSection>

      {/* Charts */}
      <DocSection icon={<BarChart3 size={20} className="text-indigo-600" />} title="5. Chart Specifications">
        <table className="w-full text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className="text-left px-3 py-2 font-medium text-slate-600">Chart</th>
              <th className="text-left px-3 py-2 font-medium text-slate-600">Type</th>
              <th className="text-left px-3 py-2 font-medium text-slate-600">Page</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            <tr><td className="px-3 py-2">Net Worth Over Time</td><td className="px-3 py-2">Stacked Area</td><td className="px-3 py-2">Dashboard, Reports</td></tr>
            <tr><td className="px-3 py-2">Asset Allocation</td><td className="px-3 py-2">Donut/Pie</td><td className="px-3 py-2">Dashboard, Planning</td></tr>
            <tr><td className="px-3 py-2">Actual vs Target</td><td className="px-3 py-2">Horizontal Bar</td><td className="px-3 py-2">Planning</td></tr>
            <tr><td className="px-3 py-2">Account Values</td><td className="px-3 py-2">Bar Chart</td><td className="px-3 py-2">Dashboard</td></tr>
            <tr><td className="px-3 py-2">Monthly Investment</td><td className="px-3 py-2">Bar Chart</td><td className="px-3 py-2">Reports</td></tr>
            <tr><td className="px-3 py-2">Dividend Growth</td><td className="px-3 py-2">Line Chart</td><td className="px-3 py-2">Dividends</td></tr>
            <tr><td className="px-3 py-2">FD by Bank</td><td className="px-3 py-2">Pie Chart</td><td className="px-3 py-2">Fixed Deposits</td></tr>
            <tr><td className="px-3 py-2">SRS Projection</td><td className="px-3 py-2">Area Chart</td><td className="px-3 py-2">Planning</td></tr>
            <tr><td className="px-3 py-2">YoY Net Worth</td><td className="px-3 py-2">Combo Bar+Line</td><td className="px-3 py-2">Reports</td></tr>
          </tbody>
        </table>
      </DocSection>

      {/* Security */}
      <DocSection icon={<Shield size={20} className="text-indigo-600" />} title="6. Security & Performance">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <h4 className="font-medium text-slate-700 mb-2">Security (Current)</h4>
            <ul className="list-disc list-inside text-sm text-slate-600 space-y-1">
              <li>No authentication (single-user, local)</li>
              <li>CORS configured for localhost</li>
              <li>Input validation via Bean Validation</li>
              <li>H2 console in dev mode only</li>
            </ul>
          </div>
          <div>
            <h4 className="font-medium text-slate-700 mb-2">Performance</h4>
            <ul className="list-disc list-inside text-sm text-slate-600 space-y-1">
              <li>Target &lt; 500ms response for all APIs</li>
              <li>Indexed fields: owner_id, account_id, asset_id, date</li>
              <li>Pagination for transaction lists (50/page)</li>
              <li>Dashboard cache: 5 min TTL</li>
              <li>Exchange rates cached until manual update</li>
            </ul>
          </div>
        </div>
      </DocSection>

      {/* Migration */}
      <DocSection icon={<Rocket size={20} className="text-indigo-600" />} title="7. Migration Strategy (from Excel)">
        <div className="space-y-3">
          <div className="bg-indigo-50 border border-indigo-200 rounded-lg p-4">
            <h4 className="font-medium text-indigo-800 text-sm">Phase 1: Core Enhancement</h4>
            <p className="text-sm text-indigo-600 mt-1">Multi-owner support, multi-currency, expanded AssetType enum, SoldPosition entity</p>
          </div>
          <div className="bg-emerald-50 border border-emerald-200 rounded-lg p-4">
            <h4 className="font-medium text-emerald-800 text-sm">Phase 2: Fixed Deposits Module</h4>
            <p className="text-sm text-emerald-600 mt-1">FD entities, CRUD, maturity tracking, interest calculation, family mapping</p>
          </div>
          <div className="bg-violet-50 border border-violet-200 rounded-lg p-4">
            <h4 className="font-medium text-violet-800 text-sm">Phase 3: Financial Planning</h4>
            <p className="text-sm text-violet-600 mt-1">Target allocation, SRS projections, deposit/withdrawal tracking, YoY reports</p>
          </div>
          <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
            <h4 className="font-medium text-amber-800 text-sm">Phase 4: Advanced Reporting</h4>
            <p className="text-sm text-amber-600 mt-1">Historical charts, dividend reports, performance attribution, CSV export</p>
          </div>
        </div>
      </DocSection>
    </div>
  );
}

/* ─────────────────────────────────────────────────────── */
/*                    HELPER COMPONENTS                      */
/* ─────────────────────────────────────────────────────── */

function DocSection({ icon, title, children }: { icon: React.ReactNode; title: string; children: React.ReactNode }) {
  return (
    <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
      <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
        {icon}
        {title}
      </h3>
      <div className="text-slate-700">{children}</div>
    </div>
  );
}

function TechRow({ name, version, purpose }: { name: string; version: string; purpose: string }) {
  return (
    <tr>
      <td className="py-1.5 font-medium text-slate-800">{name}</td>
      <td className="py-1.5 text-slate-500 px-3">{version}</td>
      <td className="py-1.5 text-slate-600">{purpose}</td>
    </tr>
  );
}

function ModuleCard({ title, items, color }: { title: string; items: string[]; color: string }) {
  const colorMap: Record<string, string> = {
    indigo: 'bg-indigo-50 border-indigo-200',
    cyan: 'bg-cyan-50 border-cyan-200',
    emerald: 'bg-emerald-50 border-emerald-200',
    violet: 'bg-violet-50 border-violet-200',
    amber: 'bg-amber-50 border-amber-200',
    rose: 'bg-rose-50 border-rose-200',
  };
  return (
    <div className={`rounded-lg p-4 border ${colorMap[color] || 'bg-slate-50 border-slate-200'}`}>
      <h4 className="font-medium text-slate-800 text-sm mb-2">{title}</h4>
      <ul className="text-xs text-slate-600 space-y-1">
        {items.map(item => <li key={item}>• {item}</li>)}
      </ul>
    </div>
  );
}

function EnumCard({ title, values }: { title: string; values: string[] }) {
  return (
    <div className="bg-slate-50 rounded-lg p-3 border border-slate-200">
      <h4 className="font-mono text-sm font-medium text-slate-800 mb-2">{title}</h4>
      <div className="flex flex-wrap gap-1">
        {values.map(v => (
          <span key={v} className="px-2 py-0.5 bg-white border border-slate-200 rounded text-xs font-mono text-slate-600">{v}</span>
        ))}
      </div>
    </div>
  );
}

function ApiGroup({ title, endpoints }: { title: string; endpoints: { method: string; path: string; desc: string }[] }) {
  return (
    <div>
      <h4 className="font-medium text-slate-700 text-sm mb-2">{title}</h4>
      <div className="space-y-1">
        {endpoints.map(ep => (
          <div key={ep.path} className="flex items-center gap-3 text-xs">
            <span className="font-mono bg-slate-100 px-2 py-0.5 rounded text-slate-600 w-20 text-center shrink-0">{ep.method}</span>
            <span className="font-mono text-indigo-600">{ep.path}</span>
            <span className="text-slate-500">{ep.desc}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function ColorSwatch({ name, color }: { name: string; color: string }) {
  return (
    <div className="flex items-center gap-2">
      <div className="w-6 h-6 rounded-md border border-slate-200" style={{ backgroundColor: color }}></div>
      <span className="text-xs text-slate-600">{name}</span>
    </div>
  );
}

function SchemaTable({ title, tables }: { title: string; tables: { name: string; cols: string }[] }) {
  return (
    <div>
      <h4 className="font-medium text-slate-700 text-sm mb-2">{title}</h4>
      <div className="space-y-1">
        {tables.map(t => (
          <div key={t.name} className="flex items-start gap-3 text-xs">
            <span className="font-mono bg-indigo-50 text-indigo-700 px-2 py-0.5 rounded shrink-0 w-48">{t.name}</span>
            <span className="text-slate-500 font-mono">{t.cols}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function RoadmapItem({ version, status, title, desc }: { version: string; status: string; title: string; desc: string }) {
  const statusColors: Record<string, string> = {
    current: 'bg-green-100 text-green-700',
    next: 'bg-indigo-100 text-indigo-700',
    planned: 'bg-slate-100 text-slate-700',
    future: 'bg-slate-50 text-slate-500',
  };
  return (
    <div className="flex items-start gap-3">
      <span className="font-mono text-sm font-medium text-slate-800 w-12 shrink-0">{version}</span>
      <span className={`px-2 py-0.5 rounded text-xs font-medium shrink-0 ${statusColors[status]}`}>{status}</span>
      <div>
        <span className="text-sm font-medium text-slate-800">{title}</span>
        <p className="text-xs text-slate-500">{desc}</p>
      </div>
    </div>
  );
}
