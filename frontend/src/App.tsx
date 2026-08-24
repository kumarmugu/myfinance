import { useState } from 'react';
import { BrowserRouter as Router, Routes, Route, NavLink } from 'react-router-dom';
import { LayoutDashboard, TrendingUp, ArrowLeftRight, Landmark, Target, FileBarChart, Building2, Coins, FileText, DollarSign, LogOut, Bitcoin, Banknote, RefreshCw, Calculator, Shield, HelpCircle, Receipt, Briefcase, Wallet, Home, Settings, FlaskConical, Users, KeyRound, ClipboardList, Building } from 'lucide-react';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { ToastProvider } from './contexts/ToastContext';
import ToastContainer from './components/ToastContainer';
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
import BankSavings from './pages/BankSavings';
import NetWorthConfig from './pages/NetWorthConfig';
import TestResults from './pages/TestResults';
import UserManagement from './pages/UserManagement';
import UserGuide from './pages/UserGuide';
import Docs from './pages/Docs';
import AuditTrail from './pages/AuditTrail';
import Properties from './pages/Properties';
import PreciousMetals from './pages/PreciousMetals';

function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <AppContent />
        <ToastContainer />
      </AuthProvider>
    </ToastProvider>
  );
}

function AppContent() {
  const { isAuthenticated, isLoading, user, logout, isAdmin, hasFeature } = useAuth();
  const [showPasswordModal, setShowPasswordModal] = useState(false);

  if (isLoading) {
    return <div className="min-h-screen flex items-center justify-center bg-slate-50"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;
  }

  if (!isAuthenticated) {
    return <Login />;
  }

  const navSections = isAdmin ? [
    // ─── ADMIN-ONLY NAV ───
    {
      label: null,
      items: [
        { to: '/admin/users', icon: Users, label: 'User Management' },
        { to: '/admin/audit', icon: ClipboardList, label: 'Audit Trail' },
      ],
    },
    {
      label: 'Admin Tools',
      items: [
        { to: '/test-results', icon: FlaskConical, label: 'Test Results' },
        { to: '/docs', icon: FileText, label: 'Documentation' },
        { to: '/guide', icon: HelpCircle, label: 'User Guide' },
      ],
    },
  ] : [
    // ─── NORMAL USER NAV ───
    {
      label: null,
      items: [{ to: '/', icon: LayoutDashboard, label: 'Dashboard' }],
    },
    {
      label: 'Investments',
      items: [
        { to: '/portfolio', icon: TrendingUp, label: 'Portfolio', feature: 'PORTFOLIO' },
        { to: '/transactions', icon: ArrowLeftRight, label: 'Transactions', feature: 'PORTFOLIO' },
        { to: '/dividends', icon: DollarSign, label: 'Dividends', feature: 'DIVIDENDS' },
        { to: '/crypto', icon: Bitcoin, label: 'Crypto', feature: 'CRYPTO' },
        { to: '/deposits', icon: Banknote, label: 'Cash Flows', feature: 'CASH_FLOWS' },
      ],
    },
    {
      label: 'Savings & Loans',
      items: [
        { to: '/bank-savings', icon: Building2, label: 'Bank Savings', feature: 'BANK_SAVINGS' },
        { to: '/fixed-deposits', icon: Landmark, label: 'Fixed Deposits', feature: 'FIXED_DEPOSITS' },
        { to: '/properties', icon: Building, label: 'Real Estate', feature: 'REAL_ESTATE' },
        { to: '/precious-metals', icon: Coins, label: 'Gold & Silver', feature: 'PRECIOUS_METALS' },
        { to: '/insurance', icon: Shield, label: 'Life Insurance', feature: 'INSURANCE' },
        { to: '/home-loans', icon: Home, label: 'Home Loans', feature: 'HOME_LOANS' },
      ],
    },
    {
      label: 'Planning',
      items: [
        { to: '/planning', icon: Target, label: 'Allocation & Net Worth' },
        { to: '/srs-cpf', icon: Calculator, label: 'SRS & CPF', feature: 'SRS_CPF' },
        { to: '/tax', icon: Receipt, label: 'Tax Records', feature: 'TAX' },
        { to: '/work-experience', icon: Briefcase, label: 'Work Experience', feature: 'WORK_EXPERIENCE' },
        { to: '/salary', icon: Wallet, label: 'Salary', feature: 'SALARY' },
      ],
    },
    {
      label: 'Reports',
      items: [
        { to: '/reports', icon: FileBarChart, label: 'Reports', feature: 'REPORTS' },
      ],
    },
    {
      label: 'Settings',
      items: [
        { to: '/accounts', icon: Building2, label: 'Brokers & Owners' },
        { to: '/assets', icon: Coins, label: 'Assets' },
        { to: '/fx-rates', icon: RefreshCw, label: 'FX Rates' },
        { to: '/net-worth-config', icon: Settings, label: 'Net Worth Config' },
        { to: '/guide', icon: HelpCircle, label: 'User Guide' },
      ],
    },
  ].map(section => ({
    ...section,
    items: section.items.filter((item: any) => !item.feature || hasFeature(item.feature))
  })).filter(section => section.items.length > 0);

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
            <button onClick={() => setShowPasswordModal(true)} className="flex items-center gap-2 w-full px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
              <KeyRound size={16} /> Change Password
            </button>
            <button onClick={logout} className="flex items-center gap-2 w-full px-4 py-2 text-sm text-slate-600 hover:bg-red-50 hover:text-red-600 rounded-lg transition-colors">
              <LogOut size={16} /> Sign Out
            </button>
          </div>
        </aside>

        {/* Main Content */}
        <main className="flex-1 overflow-auto">
          <div className="p-8">
            <Routes>
              <Route path="/" element={isAdmin ? <UserManagement /> : <Dashboard />} />
              <Route path="/portfolio" element={!isAdmin ? <Portfolio /> : <UserManagement />} />
              <Route path="/transactions" element={!isAdmin ? <Transactions /> : <UserManagement />} />
              <Route path="/dividends" element={!isAdmin ? <Dividends /> : <UserManagement />} />
              <Route path="/crypto" element={!isAdmin ? <Crypto /> : <UserManagement />} />
              <Route path="/deposits" element={!isAdmin ? <Deposits /> : <UserManagement />} />
              <Route path="/fixed-deposits" element={!isAdmin ? <FixedDeposits /> : <UserManagement />} />
              <Route path="/bank-savings" element={!isAdmin ? <BankSavings /> : <UserManagement />} />
              <Route path="/insurance" element={!isAdmin ? <Insurance /> : <UserManagement />} />
              <Route path="/home-loans" element={!isAdmin ? <HomeLoans /> : <UserManagement />} />
              <Route path="/properties" element={!isAdmin ? <Properties /> : <UserManagement />} />
              <Route path="/precious-metals" element={!isAdmin ? <PreciousMetals /> : <UserManagement />} />
              <Route path="/tax" element={!isAdmin ? <Tax /> : <UserManagement />} />
              <Route path="/work-experience" element={!isAdmin ? <WorkExperience /> : <UserManagement />} />
              <Route path="/salary" element={!isAdmin ? <Salary /> : <UserManagement />} />
              <Route path="/planning" element={!isAdmin ? <Planning /> : <UserManagement />} />
              <Route path="/srs-cpf" element={!isAdmin ? <SrsCpf /> : <UserManagement />} />
              <Route path="/reports" element={!isAdmin ? <Reports /> : <UserManagement />} />
              <Route path="/accounts" element={!isAdmin ? <Accounts /> : <UserManagement />} />
              <Route path="/assets" element={!isAdmin ? <Assets /> : <UserManagement />} />
              <Route path="/fx-rates" element={!isAdmin ? <FxRates /> : <UserManagement />} />
              <Route path="/net-worth-config" element={!isAdmin ? <NetWorthConfig /> : <UserManagement />} />
              <Route path="/admin/users" element={isAdmin ? <UserManagement /> : <Dashboard />} />
              <Route path="/admin/audit" element={isAdmin ? <AuditTrail /> : <Dashboard />} />
              <Route path="/test-results" element={isAdmin ? <TestResults /> : <Dashboard />} />
              <Route path="/guide" element={<UserGuide />} />
              <Route path="/docs" element={isAdmin ? <Docs /> : <Dashboard />} />
            </Routes>
          </div>
        </main>
      </div>
      {showPasswordModal && <ChangePasswordModal onClose={() => setShowPasswordModal(false)} />}
    </Router>
  );
}

function ChangePasswordModal({ onClose }: { onClose: () => void }) {
  const { changePassword } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (newPassword.length < 6) { setError('New password must be at least 6 characters'); return; }
    if (newPassword !== confirmPassword) { setError('Passwords do not match'); return; }
    setLoading(true);
    try {
      await changePassword(currentPassword, newPassword);
      setSuccess(true);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to change password');
    } finally { setLoading(false); }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl p-6 w-full max-w-sm shadow-2xl">
        <h3 className="font-semibold text-slate-800 text-lg mb-4">Change Password</h3>
        {success ? (
          <div className="text-center py-4">
            <p className="text-green-600 font-medium">Password changed successfully!</p>
            <button onClick={onClose} className="mt-4 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium">Close</button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            {error && <div className="p-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">{error}</div>}
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Current Password</label>
              <input type="password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">New Password</label>
              <input type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Confirm New Password</label>
              <input type="password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div className="flex gap-2">
              <button type="submit" disabled={loading} className="flex-1 py-2.5 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:opacity-50">{loading ? 'Changing...' : 'Change Password'}</button>
              <button type="button" onClick={onClose} className="flex-1 py-2.5 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-200">Cancel</button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

export default App;
