import { Link } from 'react-router-dom';
import { site } from '../content/site';

export default function Footer() {
  const year = new Date().getFullYear();
  return (
    <footer className="border-t border-slate-200 bg-slate-50 mt-24">
      <div className="max-w-6xl mx-auto px-4 py-12 grid gap-8 md:grid-cols-4">
        <div>
          <p className="text-lg font-bold text-slate-800">
            <span className="text-indigo-600">My</span>Finance
          </p>
          <p className="text-sm text-slate-500 mt-2">{site.tagline}</p>
        </div>
        <nav aria-label="Product">
          <p className="text-xs font-semibold uppercase text-slate-400 mb-3">Product</p>
          <ul className="space-y-2 text-sm text-slate-600">
            <li><Link to="/features" className="hover:text-slate-900">Features</Link></li>
            <li><Link to="/pricing" className="hover:text-slate-900">Pricing</Link></li>
            <li><Link to="/how-it-works" className="hover:text-slate-900">How it works</Link></li>
          </ul>
        </nav>
        <nav aria-label="Company">
          <p className="text-xs font-semibold uppercase text-slate-400 mb-3">Company</p>
          <ul className="space-y-2 text-sm text-slate-600">
            <li><Link to="/faq" className="hover:text-slate-900">FAQ</Link></li>
            <li><Link to="/contact" className="hover:text-slate-900">Contact</Link></li>
          </ul>
        </nav>
        <nav aria-label="Legal">
          <p className="text-xs font-semibold uppercase text-slate-400 mb-3">Legal</p>
          <ul className="space-y-2 text-sm text-slate-600">
            <li><Link to="/legal/terms" className="hover:text-slate-900">Terms</Link></li>
            <li><Link to="/legal/privacy" className="hover:text-slate-900">Privacy</Link></li>
          </ul>
        </nav>
      </div>
      <div className="border-t border-slate-200 py-4 text-center text-xs text-slate-400">
        © {year} {site.legal.company}. All rights reserved.
      </div>
    </footer>
  );
}
