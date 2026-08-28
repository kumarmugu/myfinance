import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { signup } from '../api/authApi';
import { site } from '../content/site';
import { useSeo } from '../hooks/useSeo';
import { useLoginUrl } from '../context/ConfigContext';

/**
 * Self-service signup. Includes a hidden honeypot field (bots fill it), client-side
 * validation (server re-validates authoritatively), and terms acceptance.
 */
export default function Signup() {
  useSeo(`Sign up - ${site.brand}`, 'Create your MyFinance account and start a free trial.');
  const [params] = useSearchParams();
  const planCode = params.get('plan') ?? undefined;
  const loginUrl = useLoginUrl();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [acceptTerms, setAcceptTerms] = useState(false);
  const [honeypot, setHoneypot] = useState(''); // must stay empty for humans
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const validate = (): string | null => {
    if (fullName.trim().length === 0) return 'Please enter your name';
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return 'Please enter a valid email';
    if (password.length < 8 || !/[a-zA-Z]/.test(password) || !/\d/.test(password)) {
      return 'Password must be at least 8 characters and include letters and numbers';
    }
    if (!acceptTerms) return 'You must accept the terms to continue';
    return null;
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    const v = validate();
    if (v) { setError(v); return; }
    setLoading(true);
    try {
      await signup({ fullName, email, password, planCode, acceptTerms }, honeypot);
      setDone(true);
    } catch (err: unknown) {
      const anyErr = err as { response?: { data?: { error?: string } } };
      setError(anyErr.response?.data?.error ?? 'Signup failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  if (done) {
    return (
      <div className="max-w-md mx-auto px-4 py-20 text-center">
        <h1 className="text-2xl font-bold text-slate-900">Check your email</h1>
        <p className="mt-3 text-slate-600">
          We've sent a verification link to <strong>{email}</strong>. Click it to activate your account.
        </p>
        <a href={loginUrl} className="inline-block mt-6 text-indigo-600 font-medium hover:underline">
          Go to login
        </a>
      </div>
    );
  }

  return (
    <div className="max-w-md mx-auto px-4 py-16">
      <h1 className="text-2xl font-bold text-slate-900 text-center">Start your free trial</h1>
      <p className="text-center text-sm text-slate-500 mt-1">7 days free · No credit card required</p>

      <form onSubmit={onSubmit} className="mt-8 space-y-4" noValidate>
        {error && (
          <div role="alert" className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
            {error}
          </div>
        )}

        <div>
          <label htmlFor="fullName" className="block text-sm font-medium text-slate-700 mb-1">Full name</label>
          <input id="fullName" type="text" value={fullName} onChange={(e) => setFullName(e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" autoComplete="name" required />
        </div>

        <div>
          <label htmlFor="email" className="block text-sm font-medium text-slate-700 mb-1">Email</label>
          <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" autoComplete="email" required />
        </div>

        <div>
          <label htmlFor="password" className="block text-sm font-medium text-slate-700 mb-1">Password</label>
          <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" autoComplete="new-password" required />
          <p className="mt-1 text-xs text-slate-500">At least 8 characters, with letters and numbers.</p>
        </div>

        {/* Honeypot: hidden from users; bots that fill it are silently ignored server-side. */}
        <div className="hidden" aria-hidden="true">
          <label htmlFor="company">Company</label>
          <input id="company" type="text" tabIndex={-1} autoComplete="off"
            value={honeypot} onChange={(e) => setHoneypot(e.target.value)} />
        </div>

        <label className="flex items-start gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={acceptTerms} onChange={(e) => setAcceptTerms(e.target.checked)} className="mt-1" />
          <span>
            I agree to the <Link to="/legal/terms" className="text-indigo-600 hover:underline">Terms</Link> and{' '}
            <Link to="/legal/privacy" className="text-indigo-600 hover:underline">Privacy Policy</Link>.
          </span>
        </label>

        <button type="submit" disabled={loading}
          className="w-full bg-indigo-600 text-white font-semibold py-2.5 rounded-lg hover:bg-indigo-700 disabled:opacity-50">
          {loading ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-slate-500">
        Already have an account? <a href={loginUrl} className="text-indigo-600 hover:underline">Log in</a>
      </p>
    </div>
  );
}
