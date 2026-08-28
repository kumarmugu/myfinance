import { site } from '../content/site';
import { useSeo } from '../hooks/useSeo';

export default function Contact() {
  useSeo(`Contact - ${site.brand}`, 'Get in touch with the MyFinance team.');

  return (
    <div className="max-w-2xl mx-auto px-4 py-16 text-center">
      <h1 className="text-3xl md:text-4xl font-bold text-slate-900">Get in touch</h1>
      <p className="mt-4 text-slate-600">
        Questions about MyFinance? We're happy to help.
      </p>
      <a
        href={`mailto:${site.legal.contactEmail}`}
        className="inline-block mt-6 text-indigo-600 font-medium hover:underline"
      >
        {site.legal.contactEmail}
      </a>
    </div>
  );
}
