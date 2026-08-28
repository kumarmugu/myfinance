import { Link } from 'react-router-dom';
import { site } from '../content/site';
import { useSeo } from '../hooks/useSeo';
import { useLoginUrl } from '../context/ConfigContext';
import MediaPlaceholder from '../components/MediaPlaceholder';

/**
 * Homepage: hero + value proposition + primary/secondary CTAs + feature highlights +
 * trust section + final CTA. Content is sourced from the config-driven `site` object.
 */
export default function Home() {
  useSeo(`${site.brand} - ${site.tagline}`, site.valueProp);
  const loginUrl = useLoginUrl();

  return (
    <div>
      {/* Hero */}
      <section className="max-w-6xl mx-auto px-4 pt-16 pb-12 grid gap-10 md:grid-cols-2 items-center">
        <div className="animate-fade-up">
          <h1 className="text-4xl md:text-5xl font-bold text-slate-900 leading-tight">
            {site.tagline}
          </h1>
          <p className="mt-4 text-lg text-slate-600">{site.valueProp}</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link
              to="/signup"
              className="bg-indigo-600 text-white font-semibold px-6 py-3 rounded-lg hover:bg-indigo-700 transition-colors"
            >
              Start Free Trial
            </Link>
            <a
              href={loginUrl}
              className="border border-slate-300 text-slate-700 font-semibold px-6 py-3 rounded-lg hover:bg-slate-50 transition-colors"
            >
              Login
            </a>
          </div>
          <p className="mt-3 text-sm text-slate-500">7-day free trial · No credit card required</p>
        </div>
        <MediaPlaceholder src={site.hero.image} alt="MyFinance dashboard preview" label="Dashboard preview" className="w-full" />
      </section>

      {/* Feature highlights */}
      <section className="max-w-6xl mx-auto px-4 py-12" aria-labelledby="features-heading">
        <h2 id="features-heading" className="text-2xl font-bold text-slate-900 text-center">
          Everything you need in one place
        </h2>
        <div className="mt-10 grid gap-6 md:grid-cols-3">
          {site.features.slice(0, 6).map((f) => (
            <div key={f.key} className="rounded-xl border border-slate-200 p-6">
              <h3 className="font-semibold text-slate-800">{f.title}</h3>
              <p className="mt-2 text-sm text-slate-600">{f.description}</p>
            </div>
          ))}
        </div>
        <div className="mt-8 text-center">
          <Link to="/features" className="text-indigo-600 font-medium hover:underline">
            See all features →
          </Link>
        </div>
      </section>

      {/* Trust */}
      <section className="bg-slate-50 border-y border-slate-200 py-12" aria-labelledby="trust-heading">
        <div className="max-w-6xl mx-auto px-4">
          <h2 id="trust-heading" className="text-xl font-bold text-slate-900 text-center">Built with security in mind</h2>
          <ul className="mt-6 grid gap-4 md:grid-cols-3">
            {site.trust.map((t) => (
              <li key={t} className="text-sm text-slate-600 text-center">{t}</li>
            ))}
          </ul>
        </div>
      </section>

      {/* Final CTA */}
      <section className="max-w-6xl mx-auto px-4 py-16 text-center">
        <h2 className="text-2xl font-bold text-slate-900">Ready to take control of your money?</h2>
        <Link
          to="/signup"
          className="inline-block mt-6 bg-indigo-600 text-white font-semibold px-6 py-3 rounded-lg hover:bg-indigo-700 transition-colors"
        >
          Start your free trial
        </Link>
      </section>
    </div>
  );
}
