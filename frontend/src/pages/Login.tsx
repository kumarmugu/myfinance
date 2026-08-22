import { useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { Lock, User, Mail, Eye, EyeOff, ArrowRight } from 'lucide-react';

type Mode = 'login' | 'register' | 'forgot';

export default function Login() {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<Mode>('login');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [form, setForm] = useState({
    username: '', email: '', password: '', displayName: '',
  });

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setLoading(true);
    try { await login(form.username, form.password); }
    catch (err: any) { setError(err.response?.data?.error || 'Login failed'); }
    finally { setLoading(false); }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setLoading(true);
    try { await register(form.username, form.email, form.password, form.displayName); }
    catch (err: any) { setError(err.response?.data?.error || 'Registration failed'); }
    finally { setLoading(false); }
  };

  const handleForgot = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setSuccess(''); setLoading(true);
    try {
      const api = (await import('../api')).default;
      await api.post('/auth/forgot-password', { email: form.email });
      setSuccess('If the email exists, a reset link has been sent.');
    } catch { setError('Something went wrong'); }
    finally { setLoading(false); }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-indigo-50 via-white to-cyan-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <img src="/logo.svg" alt="MyFinance" className="w-16 h-16 mx-auto mb-3" />
          <h1 className="text-3xl font-bold text-slate-800"><span className="text-indigo-600">My</span>Finance</h1>
          <p className="text-slate-500 mt-2">Personal Finance Manager</p>
        </div>

        {/* Card */}
        <div className="bg-white rounded-2xl shadow-xl border border-slate-200 p-8">
          <h2 className="text-xl font-semibold text-slate-800 mb-6">
            {mode === 'login' ? 'Welcome back' : mode === 'register' ? 'Create account' : 'Reset password'}
          </h2>

          {error && <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>}
          {success && <div className="mb-4 p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-700">{success}</div>}

          {mode === 'login' && (
            <form onSubmit={handleLogin} className="space-y-4">
              <InputField icon={<User size={18} />} type="text" placeholder="Username" value={form.username} onChange={v => setForm({...form, username: v})} />
              <div className="relative">
                <InputField icon={<Lock size={18} />} type={showPassword ? 'text' : 'password'} placeholder="Password" value={form.password} onChange={v => setForm({...form, password: v})} />
                <button type="button" onClick={() => setShowPassword(!showPassword)} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              <button type="submit" disabled={loading} className="w-full flex items-center justify-center gap-2 py-3 bg-indigo-600 text-white rounded-lg font-medium hover:bg-indigo-700 disabled:opacity-50 transition-colors">
                {loading ? 'Signing in...' : <><ArrowRight size={18} /> Sign In</>}
              </button>
            </form>
          )}

          {mode === 'register' && (
            <form onSubmit={handleRegister} className="space-y-4">
              <InputField icon={<User size={18} />} type="text" placeholder="Username" value={form.username} onChange={v => setForm({...form, username: v})} />
              <InputField icon={<Mail size={18} />} type="email" placeholder="Email" value={form.email} onChange={v => setForm({...form, email: v})} />
              <InputField icon={<User size={18} />} type="text" placeholder="Display Name (optional)" value={form.displayName} onChange={v => setForm({...form, displayName: v})} />
              <div className="relative">
                <InputField icon={<Lock size={18} />} type={showPassword ? 'text' : 'password'} placeholder="Password (min 6 chars)" value={form.password} onChange={v => setForm({...form, password: v})} />
                <button type="button" onClick={() => setShowPassword(!showPassword)} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              <button type="submit" disabled={loading} className="w-full py-3 bg-indigo-600 text-white rounded-lg font-medium hover:bg-indigo-700 disabled:opacity-50 transition-colors">
                {loading ? 'Creating...' : 'Create Account'}
              </button>
            </form>
          )}

          {mode === 'forgot' && (
            <form onSubmit={handleForgot} className="space-y-4">
              <InputField icon={<Mail size={18} />} type="email" placeholder="Your email address" value={form.email} onChange={v => setForm({...form, email: v})} />
              <button type="submit" disabled={loading} className="w-full py-3 bg-indigo-600 text-white rounded-lg font-medium hover:bg-indigo-700 disabled:opacity-50 transition-colors">
                {loading ? 'Sending...' : 'Send Reset Link'}
              </button>
            </form>
          )}

          {/* Mode Switchers */}
          <div className="mt-6 text-center text-sm text-slate-500 space-y-2">
            {mode === 'login' && (
              <>
                <button onClick={() => { setMode('forgot'); setError(''); }} className="text-indigo-600 hover:underline">Forgot password?</button>
                <p>Don't have an account? <button onClick={() => { setMode('register'); setError(''); }} className="text-indigo-600 font-medium hover:underline">Sign up</button></p>
              </>
            )}
            {mode === 'register' && (
              <p>Already have an account? <button onClick={() => { setMode('login'); setError(''); }} className="text-indigo-600 font-medium hover:underline">Sign in</button></p>
            )}
            {mode === 'forgot' && (
              <button onClick={() => { setMode('login'); setError(''); setSuccess(''); }} className="text-indigo-600 hover:underline">Back to login</button>
            )}
          </div>
        </div>

        {/* Default credentials hint */}
        <div className="mt-4 text-center text-xs text-slate-400">
          <p>Default: admin / admin123</p>
        </div>
      </div>
    </div>
  );
}

function InputField({ icon, type, placeholder, value, onChange }: { icon: React.ReactNode; type: string; placeholder: string; value: string; onChange: (v: string) => void }) {
  return (
    <div className="relative">
      <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">{icon}</span>
      <input
        type={type}
        placeholder={placeholder}
        value={value}
        onChange={e => onChange(e.target.value)}
        className="w-full pl-10 pr-4 py-3 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-colors"
        required
      />
    </div>
  );
}
