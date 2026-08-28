import { Link } from 'react-router-dom';
import { site } from '../content/site';
import { useSeo } from '../hooks/useSeo';
import MediaPlaceholder from '../components/MediaPlaceholder';

/**
 * Features page. Each feature is rendered from the config-driven `site.features` list, with a
 * description, benefits, a screenshot placeholder, and a CTA. Only real features are shown.
 */
export default function Features() {
  useSeo(`Features - ${site.brand}`, 'Explore what MyFinance can do for your finances.');

  return (
    <div className="max-w-6xl mx-auto px-4 py-16">
      <header className="text-center max-w-2xl mx-auto">
        <h1 className="text-3xl md:text-4xl font-bold text-slate-900">Powerful features, one place</h1>
        <p className="mt-3 text-slate-600">Every capability below is part of the MyFinance application.</p>
      </header>

      <div className="mt-14 space-y-20">
        {site.features.map((f, i) => (
          <section
            key={f.key}
            className={`grid gap-8 md:grid-cols-2 items-center ${i % 2 === 1 ? 'md:[&>*:first-child]:order-2' : ''}`}
            aria-labelledby={`feature-${f.key}`}
          >
            <div>
              <h2 id={`feature-${f.key}`} className="text-2xl font-bold text-slate-900">{f.title}</h2>
              <p className="mt-3 text-slate-600">{f.description}</p>
              <ul className="mt-4 space-y-2">
                {f.benefits.map((b) => (
                  <li key={b} className="flex items-start gap-2 text-sm text-slate-700">
                    <span className="text-indigo-600 mt-0.5">✓</span> {b}
                  </li>
                ))}
              </ul>
              <Link to="/signup" className="inline-block mt-6 text-indigo-600 font-medium hover:underline">
                Try it free →
              </Link>
            </div>
            <MediaPlaceholder alt={`${f.title} screenshot`} label={`${f.title} screenshot`} className="w-full" />
          </section>
        ))}
      </div>
    </div>
  );
}
