import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { resetPassword } from '../api/authApi';
import { site } from '../content/site';
import { useSeo } from '../hooks/useSeo';
import { useLoginUrl } from '../context/ConfigContext';

export default function ResetPassword() {
  useSeo(`Set a new password - ${site.brand}`);
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const loginUrl = useLoginUrl();

  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    if (password.length < 8 || !/[a-zA-Z]/.test(password) || !/\d/.test(password)) {
      setError('Password must be at least 8 characters and include letters and numbers');
      return;
    }
    setLoading(true);
    try {
      await resetPassword(token, password);
      setDone(true);
    } catch (err: unknown) {
      const anyErr = err as { response?: { data?: { error?: string } } };
      setError(anyErr.response?.data?.error ?? 'Reset failed. The link may have expired.');
    } finally {
      setLoading(false);
    }
  };

  if (done) {
    return (
      <div className="max-w-md mx-auto px-4 py-20 text-center">
        <h1 className="text-2xl font-bold text-slate-900">Password updated</h1>
        <p className="mt-3 text-slate-600">You can now log in with your new password.</p>
        <a href={loginUrl} className="inline-block mt-6 text-indigo-600 font-medium hover:underline">Go to login</a>
      </div>
    );
  }

  return (
    <div className="max-w-md mx-auto px-4 py-16">
      <h1 className="text-2xl font-bold text-slate-900 text-center">Set a new password</h1>
      <form onSubmit={onSubmit} className="mt-8 space-y-4">
        {error && (
          <div role="alert" className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>
        )}
        <div>
          <label htmlFor="password" className="block text-sm font-medium text-slate-700 mb-1">New password</label>
          <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" autoComplete="new-password" required />
        </div>
        <button type="submit" disabled={loading || !token}
          className="w-full bg-indigo-600 text-white font-semibold py-2.5 rounded-lg hover:bg-indigo-700 disabled:opacity-50">
          {loading ? 'Updating…' : 'Update password'}
        </button>
      </form>
    </div>
  );
}
