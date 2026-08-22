import { BrowserRouter as Router, Routes, Route, NavLink } from 'react-router-dom';
import { LayoutDashboard, TrendingUp, ArrowLeftRight, Landmark, Target, FileBarChart, Building2, Coins, FileText, DollarSign, LogOut, Bitcoin, Banknote, RefreshCw, Calculator, Shield, HelpCircle, Receipt, Briefcase, Wallet, Home, Settings, FlaskConical, Users } from 'lucide-react';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Transactions from './pages/Transactions';
import Portfolio from './pages/Portfolio';
import Reports from './pages/Reports';
import Accounts from './pages/Accounts';
import Assets from './pages/Assets';
import FixedDeposits from './pages/FixedDeposits';
import Planning from './pages/Planning';
import Dividends from './pages/Dividends';
import Crypto from './pages/Crypto';
import Deposits from './pages/Deposits';
import FxRates from './pages/FxRates';
import SrsCpf from './pages/SrsCpf';
import Insurance from './pages/Insurance';
import Tax from './pages/Tax';
import WorkExperience from './pages/WorkExperience';
import Salary from './pages/Salary';
import HomeLoans from './pages/HomeLoans';
import NetWorthConfig from './pages/NetWorthConfig';
import TestResults from './pages/TestResults';
import UserManagement from './pages/UserManagement';
import UserGuide from './pages/UserGuide';
import Docs from './pages/Docs';

function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}

function AppContent() {
  const { isAuthenticated, isLoading, user, logout, isAdmin } = useAuth();

  if (isLoading) {
    return <div className="min-h-screen flex items-center justify-center bg-slate-50"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;
  }

  if (!isAuthenticated) {
    return <Login />;
  }

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
        { to: '/crypto', icon: Bitcoin, label: 'Crypto' },
        { to: '/deposits', icon: Banknote, label: 'Deposits' },
      ],
    },
    {
      label: 'Fixed Deposits',
      items: [
        { to: '/fixed-deposits', icon: Landmark, label: 'All Deposits' },
        { to: '/insurance', icon: Shield, label: 'Insurance' },
        { to: '/home-loans', icon: Home, label: 'Home Loans' },
      ],
    },
    {
      label: 'Planning',
      items: [
        { to: '/planning', icon: Target, label: 'Allocation & Net Worth' },
        { to: '/srs-cpf', icon: Calculator, label: 'SRS & CPF' },
        { to: '/tax', icon: Receipt, label: 'Tax Records' },
        { to: '/work-experience', icon: Briefcase, label: 'Work Experience' },
        { to: '/salary', icon: Wallet, label: 'Salary' },
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
        { to: '/fx-rates', icon: RefreshCw, label: 'FX Rates' },
        { to: '/net-worth-config', icon: Settings, label: 'Net Worth Config' },
        { to: '/guide', icon: HelpCircle, label: 'User Guide' },
        ...(isAdmin ? [
          { to: '/admin/users', icon: Users, label: 'User Management' },
          { to: '/test-results', icon: FlaskConical, label: 'Test Results' },
          { to: '/docs', icon: FileText, label: 'Documentation' },
        ] : []),
      ],
    },
  ];

  return (
    <Router>
      <div className="flex h-screen bg-slate-50">
        {/* Sidebar */}
        <aside className="w-64 bg-white border-r border-slate-200 flex flex-col shrink-0">
          <div className="p-6 border-b border-slate-200">
            <div className="flex items-center gap-3">
              <img src="/logo.svg" alt="MyFinance" className="w-9 h-9" />
              <div>
                <h1 className="text-xl font-bold text-slate-800">
                  <span className="text-indigo-600">My</span>Finance
                </h1>
                <p className="text-xs text-slate-500">Personal Finance Manager</p>
              </div>
            </div>
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
          <div className="p-4 border-t border-slate-200 space-y-2">
            <div className="flex items-center gap-2 px-2 py-1">
              <div className="w-7 h-7 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-700 text-xs font-bold">{user?.displayName?.[0] || 'U'}</div>
              <span className="text-sm text-slate-700 font-medium truncate">{user?.displayName || user?.username}</span>
            </div>
            <button onClick={logout} className="flex items-center gap-2 w-full px-4 py-2 text-sm text-slate-600 hover:bg-red-50 hover:text-red-600 rounded-lg transition-colors">
              <LogOut size={16} /> Sign Out
            </button>
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
              <Route path="/crypto" element={<Crypto />} />
              <Route path="/deposits" element={<Deposits />} />
              <Route path="/fixed-deposits" element={<FixedDeposits />} />
              <Route path="/insurance" element={<Insurance />} />
              <Route path="/home-loans" element={<HomeLoans />} />
              <Route path="/tax" element={<Tax />} />
              <Route path="/work-experience" element={<WorkExperience />} />
              <Route path="/salary" element={<Salary />} />
              <Route path="/planning" element={<Planning />} />
              <Route path="/srs-cpf" element={<SrsCpf />} />
              <Route path="/reports" element={<Reports />} />
              <Route path="/accounts" element={<Accounts />} />
              <Route path="/assets" element={<Assets />} />
              <Route path="/fx-rates" element={<FxRates />} />
              <Route path="/net-worth-config" element={<NetWorthConfig />} />
              <Route path="/admin/users" element={<UserManagement />} />
              <Route path="/test-results" element={<TestResults />} />
              <Route path="/guide" element={<UserGuide />} />
              <Route path="/docs" element={<Docs />} />
            </Routes>
          </div>
        </main>
      </div>
    </Router>
  );
}

export default App;
