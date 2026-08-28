import { useState, type FormEvent } from 'react';
import { forgotPassword } from '../api/authApi';
import { site } from '../content/site';
import { useSeo } from '../hooks/useSeo';

export default function ForgotPassword() {
  useSeo(`Reset password - ${site.brand}`);
  const [email, setEmail] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      await forgotPassword(email);
    } finally {
      // Always show the same message (enumeration-safe, mirrors backend behavior).
      setSubmitted(true);
      setLoading(false);
    }
  };

  return (
    <div className="max-w-md mx-auto px-4 py-16">
      <h1 className="text-2xl font-bold text-slate-900 text-center">Reset your password</h1>
      {submitted ? (
        <p className="mt-6 text-center text-slate-600">
          If an account exists for that email, we've sent a reset link.
        </p>
      ) : (
        <form onSubmit={onSubmit} className="mt-8 space-y-4">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-slate-700 mb-1">Email</label>
            <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" autoComplete="email" required />
          </div>
          <button type="submit" disabled={loading}
            className="w-full bg-indigo-600 text-white font-semibold py-2.5 rounded-lg hover:bg-indigo-700 disabled:opacity-50">
            {loading ? 'Sending…' : 'Send reset link'}
          </button>
        </form>
      )}
    </div>
  );
}
