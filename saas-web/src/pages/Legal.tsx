import { site } from '../content/site';
import { useSeo } from '../hooks/useSeo';

/**
 * Simple legal pages (terms / privacy). Content is placeholder boilerplate — replace with
 * reviewed legal copy before launch.
 */
export function Terms() {
  useSeo(`Terms of Service - ${site.brand}`, 'MyFinance terms of service.');
  return (
    <article className="max-w-3xl mx-auto px-4 py-16 prose prose-slate">
      <h1 className="text-3xl font-bold text-slate-900">Terms of Service</h1>
      <p className="mt-4 text-slate-600">
        These terms govern your use of {site.legal.company}. By creating an account you agree to
        use the service lawfully and accept that access depends on an active trial or subscription.
      </p>
      <p className="mt-4 text-sm text-slate-400">Placeholder — replace with reviewed legal copy before launch.</p>
    </article>
  );
}

export function Privacy() {
  useSeo(`Privacy Policy - ${site.brand}`, 'How MyFinance handles your data.');
  return (
    <article className="max-w-3xl mx-auto px-4 py-16 prose prose-slate">
      <h1 className="text-3xl font-bold text-slate-900">Privacy Policy</h1>
      <p className="mt-4 text-slate-600">
        We collect only the information needed to provide the service and never sell your data.
        Payment details are handled securely by our payment provider and are never stored by us.
      </p>
      <p className="mt-4 text-sm text-slate-400">Placeholder — replace with reviewed legal copy before launch.</p>
    </article>
  );
}
