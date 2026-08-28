import { useEffect, useState, type FormEvent } from 'react';
import { getToken, clearToken } from '../api/client';
import { login } from '../api/authApi';
import {
  getSubscription, getPayments, startCheckout, cancelSubscription,
  type SubscriptionView, type PaymentView,
} from '../api/portalApi';
import { fetchPlans, type PlanView } from '../api/publicApi';
import { site } from '../content/site';
import { useSeo } from '../hooks/useSeo';
import { useLoginUrl } from '../context/ConfigContext';

/**
 * Billing/subscription portal. This is SEPARATE from the finance app: it only manages
 * subscription and payments, and never links to the finance app's internal pages.
 * Authentication uses the portal token; if absent, an inline portal login is shown.
 */
export default function Portal() {
  useSeo(`Billing Portal - ${site.brand}`);
  const [authed, setAuthed] = useState<boolean>(!!getToken());

  if (!authed) {
    return <PortalLogin onLoggedIn={() => setAuthed(true)} />;
  }
  return <PortalDashboard onLogout={() => { clearToken(); setAuthed(false); }} />;
}

function PortalLogin({ onLoggedIn }: { onLoggedIn: () => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login({ email, password });
      onLoggedIn();
    } catch {
      setError('Invalid email or password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-md mx-auto px-4 py-16">
      <h1 className="text-2xl font-bold text-slate-900 text-center">Billing portal login</h1>
      <p className="text-center text-sm text-slate-500 mt-1">Manage your subscription and payments.</p>
      <form onSubmit={onSubmit} className="mt-8 space-y-4">
        {error && <div role="alert" className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>}
        <div>
          <label htmlFor="email" className="block text-sm font-medium text-slate-700 mb-1">Email</label>
          <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" autoComplete="email" required />
        </div>
        <div>
          <label htmlFor="password" className="block text-sm font-medium text-slate-700 mb-1">Password</label>
          <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" autoComplete="current-password" required />
        </div>
        <button type="submit" disabled={loading}
          className="w-full bg-indigo-600 text-white font-semibold py-2.5 rounded-lg hover:bg-indigo-700 disabled:opacity-50">
          {loading ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}

function PortalDashboard({ onLogout }: { onLogout: () => void }) {
  const loginUrl = useLoginUrl();
  const [sub, setSub] = useState<SubscriptionView | null>(null);
  const [payments, setPayments] = useState<PaymentView[]>([]);
  const [plans, setPlans] = useState<PlanView[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');

  const load = () => {
    setLoading(true);
    Promise.all([getSubscription().catch(() => null), getPayments().catch(() => []), fetchPlans().catch(() => [])])
      .then(([s, p, pl]) => { setSub(s); setPayments(p); setPlans(pl); })
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const onCheckout = async (planCode: string, method: string) => {
    try {
      const { redirectUrl } = await startCheckout(planCode, method);
      // Redirect to the provider's hosted, secure checkout page.
      window.location.href = redirectUrl;
    } catch {
      setMessage('Unable to start checkout. Please try again.');
    }
  };

  const onCancel = async () => {
    try {
      await cancelSubscription(true);
      setMessage('Cancellation requested. You keep access until the end of your period.');
      load();
    } catch {
      setMessage('Unable to cancel right now.');
    }
  };

  if (loading) {
    return <div className="max-w-3xl mx-auto px-4 py-16 text-slate-500">Loading…</div>;
  }

  const purchasablePlans = plans.filter((p) => p.price > 0);

  return (
    <div className="max-w-3xl mx-auto px-4 py-12">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-900">Your subscription</h1>
        <div className="flex gap-3">
          <a href={loginUrl} className="text-sm text-indigo-600 hover:underline">Open the app</a>
          <button onClick={onLogout} className="text-sm text-slate-500 hover:text-slate-800">Sign out</button>
        </div>
      </div>

      {message && <div className="mt-4 p-3 bg-indigo-50 border border-indigo-200 rounded-lg text-sm text-indigo-700">{message}</div>}

      {/* Subscription status */}
      <section className="mt-6 rounded-xl border border-slate-200 p-6" aria-label="Subscription status">
        {sub ? (
          <>
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-slate-500">Current plan</p>
                <p className="text-lg font-semibold text-slate-900">{sub.planName ?? sub.planCode ?? '—'}</p>
              </div>
              <span className="text-xs font-semibold px-3 py-1 rounded-full bg-slate-100 text-slate-700">{sub.state}</span>
            </div>
            {sub.inTrial && (
              <p className="mt-3 text-sm text-amber-600">
                Trial: {sub.trialDaysRemaining} day(s) remaining
                {sub.trialEndsAt ? ` (ends ${new Date(sub.trialEndsAt).toLocaleDateString()})` : ''}
              </p>
            )}
            {sub.currentPeriodEnd && (
              <p className="mt-2 text-sm text-slate-500">Renews/ends {new Date(sub.currentPeriodEnd).toLocaleDateString()}</p>
            )}
            {(sub.state === 'ACTIVE' || sub.state === 'PAST_DUE') && (
              <button onClick={onCancel} className="mt-4 text-sm text-red-600 hover:underline">Cancel subscription</button>
            )}
          </>
        ) : (
          <p className="text-slate-500">No subscription found.</p>
        )}
      </section>

      {/* Upgrade / choose a plan */}
      {purchasablePlans.length > 0 && (
        <section className="mt-8" aria-label="Choose a plan">
          <h2 className="text-lg font-bold text-slate-900">Choose a plan</h2>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            {purchasablePlans.map((p) => (
              <div key={p.code} className="rounded-xl border border-slate-200 p-4">
                <p className="font-semibold text-slate-800">{p.name}</p>
                <p className="text-sm text-slate-500">{p.currency} {p.price.toFixed(2)}</p>
                <div className="mt-3 flex gap-2">
                  <button onClick={() => onCheckout(p.code, 'card')}
                    className="text-sm bg-indigo-600 text-white px-3 py-1.5 rounded-lg hover:bg-indigo-700">Pay by card</button>
                  <button onClick={() => onCheckout(p.code, 'paynow')}
                    className="text-sm border border-slate-300 text-slate-700 px-3 py-1.5 rounded-lg hover:bg-slate-50">PayNow</button>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Payment history */}
      <section className="mt-8" aria-label="Payment history">
        <h2 className="text-lg font-bold text-slate-900">Payment history</h2>
        {payments.length === 0 ? (
          <p className="mt-2 text-sm text-slate-500">No payments yet.</p>
        ) : (
          <table className="mt-4 w-full text-sm">
            <thead>
              <tr className="text-left text-slate-400">
                <th className="py-2">Date</th><th>Amount</th><th>Status</th><th>Method</th><th>Receipt</th>
              </tr>
            </thead>
            <tbody>
              {payments.map((p) => (
                <tr key={p.id} className="border-t border-slate-100">
                  <td className="py-2">{p.date ? new Date(p.date).toLocaleDateString() : '—'}</td>
                  <td>{p.amount != null ? `${p.currency ?? ''} ${p.amount.toFixed(2)}` : '—'}</td>
                  <td>{p.status}</td>
                  <td>{p.method ?? '—'}</td>
                  <td>{p.receiptUrl ? <a href={p.receiptUrl} className="text-indigo-600 hover:underline" target="_blank" rel="noreferrer">View</a> : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
