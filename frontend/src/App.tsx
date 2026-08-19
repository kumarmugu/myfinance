import { BrowserRouter as Router, Routes, Route, NavLink } from 'react-router-dom';
import { LayoutDashboard, TrendingUp, ArrowLeftRight, Landmark, Target, FileBarChart, Building2, Coins, FileText, DollarSign } from 'lucide-react';
import Dashboard from './pages/Dashboard';
import Transactions from './pages/Transactions';
import Portfolio from './pages/Portfolio';
import Reports from './pages/Reports';
import Accounts from './pages/Accounts';
import Assets from './pages/Assets';
import FixedDeposits from './pages/FixedDeposits';
import Planning from './pages/Planning';
import Dividends from './pages/Dividends';
import Docs from './pages/Docs';

function App() {
  const navSections = [
    {
      label: null,
      items: [{ to: '/', icon: LayoutDashboard, label: 'Dashboard' }],
    },
    {
      label: 'Investments',
      items: [
        { to: '/portfolio', icon: TrendingUp, label: 'Portfolio' },
        { to: '/transactions', icon: ArrowLeftRight, label: 'Transactions' },
        { to: '/dividends', icon: DollarSign, label: 'Dividends' },
      ],
    },
    {
      label: 'Fixed Deposits',
      items: [
        { to: '/fixed-deposits', icon: Landmark, label: 'All Deposits' },
      ],
    },
    {
      label: 'Planning',
      items: [
        { to: '/planning', icon: Target, label: 'Allocation & Net Worth' },
      ],
    },
    {
      label: 'Reports',
      items: [
        { to: '/reports', icon: FileBarChart, label: 'Reports' },
      ],
    },
    {
      label: 'Settings',
      items: [
        { to: '/accounts', icon: Building2, label: 'Accounts' },
        { to: '/assets', icon: Coins, label: 'Assets' },
        { to: '/docs', icon: FileText, label: 'Documentation' },
      ],
    },
  ];

  return (
    <Router>
      <div className="flex h-screen bg-slate-50">
        {/* Sidebar */}
        <aside className="w-64 bg-white border-r border-slate-200 flex flex-col shrink-0">
          <div className="p-6 border-b border-slate-200">
            <h1 className="text-xl font-bold text-slate-800">
              <span className="text-indigo-600">My</span>Finance
            </h1>
            <p className="text-xs text-slate-500 mt-1">Personal Finance Manager</p>
          </div>
          <nav className="flex-1 p-4 space-y-4 overflow-y-auto">
            {navSections.map((section, si) => (
              <div key={si}>
                {section.label && (
                  <p className="text-[10px] uppercase font-semibold text-slate-400 tracking-wider px-4 mb-1">{section.label}</p>
                )}
                <div className="space-y-0.5">
                  {section.items.map(({ to, icon: Icon, label }) => (
                    <NavLink
                      key={to}
                      to={to}
                      end={to === '/'}
                      className={({ isActive }) =>
                        `flex items-center gap-3 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                          isActive ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                        }`
                      }
                    >
                      <Icon size={17} />
                      {label}
                    </NavLink>
                  ))}
                </div>
              </div>
            ))}
          </nav>
          <div className="p-4 border-t border-slate-200">
            <p className="text-xs text-slate-400 text-center">MyFinance v2.0</p>
          </div>
        </aside>

        {/* Main Content */}
        <main className="flex-1 overflow-auto">
          <div className="p-8">
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/portfolio" element={<Portfolio />} />
              <Route path="/transactions" element={<Transactions />} />
              <Route path="/dividends" element={<Dividends />} />
              <Route path="/fixed-deposits" element={<FixedDeposits />} />
              <Route path="/planning" element={<Planning />} />
              <Route path="/reports" element={<Reports />} />
              <Route path="/accounts" element={<Accounts />} />
              <Route path="/assets" element={<Assets />} />
              <Route path="/docs" element={<Docs />} />
            </Routes>
          </div>
        </main>
      </div>
    </Router>
  );
}

export default App;
