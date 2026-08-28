import { useState } from 'react';
import { site } from '../content/site';
import { useSeo } from '../hooks/useSeo';

export default function Faq() {
  useSeo(`FAQ - ${site.brand}`, 'Answers to common questions about MyFinance.');
  const [open, setOpen] = useState<number | null>(0);

  return (
    <div className="max-w-3xl mx-auto px-4 py-16">
      <header className="text-center">
        <h1 className="text-3xl md:text-4xl font-bold text-slate-900">Frequently asked questions</h1>
      </header>

      <dl className="mt-10 divide-y divide-slate-200 border-t border-b border-slate-200">
        {site.faqs.map((faq, i) => {
          const expanded = open === i;
          return (
            <div key={faq.question} className="py-4">
              <dt>
                <button
                  className="w-full flex justify-between items-center text-left"
                  aria-expanded={expanded}
                  onClick={() => setOpen(expanded ? null : i)}
                >
                  <span className="font-medium text-slate-800">{faq.question}</span>
                  <span className="text-indigo-600 text-xl" aria-hidden="true">{expanded ? '−' : '+'}</span>
                </button>
              </dt>
              {expanded && <dd className="mt-2 text-sm text-slate-600">{faq.answer}</dd>}
            </div>
          );
        })}
      </dl>
    </div>
  );
}
