import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchPlans, type PlanView } from '../api/publicApi';
import { site } from '../content/site';
import { useSeo } from '../hooks/useSeo';

/**
 * Pricing page. Plans are loaded from the backend (config-driven). Prices are placeholders
 * until real values are configured — a note is shown so nothing is misrepresented.
 */
export default function Pricing() {
  useSeo(`Pricing - ${site.brand}`, 'Simple, transparent pricing with a 7-day free trial.');
  const [plans, setPlans] = useState<PlanView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    fetchPlans()
      .then(setPlans)
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  const hasRealPrices = plans.some((p) => p.price > 0);

  return (
    <div className="max-w-6xl mx-auto px-4 py-16">
      <header className="text-center max-w-2xl mx-auto">
        <h1 className="text-3xl md:text-4xl font-bold text-slate-900">Simple, transparent pricing</h1>
        <p className="mt-3 text-slate-600">Start with a 7-day free trial. No credit card required.</p>
      </header>

      {loading && <p className="text-center mt-12 text-slate-500">Loading plans…</p>}
      {error && <p className="text-center mt-12 text-slate-500">Plans are unavailable right now. Please try again later.</p>}

      {!loading && !error && (
        <>
          {!hasRealPrices && (
            <p className="mt-6 text-center text-xs text-amber-600">
              Pricing shown is being finalized. Trial access is always free for 7 days.
            </p>
          )}
          <div className="mt-10 grid gap-6 md:grid-cols-3 lg:grid-cols-4">
            {plans.map((plan) => (
              <div
                key={plan.code}
                className={`rounded-2xl border p-6 flex flex-col ${
                  plan.recommended ? 'border-indigo-500 ring-2 ring-indigo-100' : 'border-slate-200'
                }`}
              >
                {plan.recommended && (
                  <span className="self-start text-xs font-semibold bg-indigo-100 text-indigo-700 px-2 py-1 rounded-full mb-2">
                    Recommended
                  </span>
                )}
                <h2 className="text-lg font-bold text-slate-900">{plan.name}</h2>
                <p className="text-sm text-slate-600 mt-1 min-h-10">{plan.description}</p>
                <div className="mt-4">
                  {plan.price > 0 ? (
                    <p className="text-3xl font-bold text-slate-900">
                      {plan.currency} {plan.price.toFixed(2)}
                      <span className="text-sm font-normal text-slate-500">
                        /{(plan.billingPeriod ?? 'MONTHLY').toLowerCase()}
                      </span>
                    </p>
                  ) : plan.trialDays > 0 ? (
                    <p className="text-3xl font-bold text-slate-900">Free<span className="text-sm font-normal text-slate-500"> for {plan.trialDays} days</span></p>
                  ) : (
                    <p className="text-2xl font-bold text-slate-400">Coming soon</p>
                  )}
                </div>
                <ul className="mt-4 space-y-2 flex-1">
                  {plan.features.map((f) => (
                    <li key={f} className="text-sm text-slate-700 flex items-start gap-2">
                      <span className="text-indigo-600 mt-0.5">✓</span> {f}
                    </li>
                  ))}
                </ul>
                <Link
                  to={`/signup?plan=${encodeURIComponent(plan.code)}`}
                  className="mt-6 text-center bg-indigo-600 text-white font-semibold px-4 py-2.5 rounded-lg hover:bg-indigo-700 transition-colors"
                >
                  Start Free Trial
                </Link>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
