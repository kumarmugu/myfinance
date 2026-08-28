import { Link } from 'react-router-dom';
import { site } from '../content/site';
import { useSeo } from '../hooks/useSeo';

export default function HowItWorks() {
  useSeo(`How it works - ${site.brand}`, 'Get started with MyFinance in a few simple steps.');

  return (
    <div className="max-w-4xl mx-auto px-4 py-16">
      <header className="text-center">
        <h1 className="text-3xl md:text-4xl font-bold text-slate-900">How it works</h1>
        <p className="mt-3 text-slate-600">From sign up to insights in minutes.</p>
      </header>

      <ol className="mt-12 space-y-6">
        {site.howItWorks.map((step, i) => (
          <li key={step.title} className="flex gap-4 items-start">
            <span
              className="shrink-0 w-9 h-9 rounded-full bg-indigo-600 text-white font-bold flex items-center justify-center"
              aria-hidden="true"
            >
              {i + 1}
            </span>
            <div>
              <h2 className="font-semibold text-slate-800">{step.title}</h2>
              <p className="text-sm text-slate-600 mt-1">{step.description}</p>
            </div>
          </li>
        ))}
      </ol>

      <div className="mt-12 text-center">
        <Link
          to="/signup"
          className="inline-block bg-indigo-600 text-white font-semibold px-6 py-3 rounded-lg hover:bg-indigo-700 transition-colors"
        >
          Start your free trial
        </Link>
      </div>
    </div>
  );
}
