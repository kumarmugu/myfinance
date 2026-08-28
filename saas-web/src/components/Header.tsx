import { Link, NavLink } from 'react-router-dom';
import { useLoginUrl } from '../context/ConfigContext';
import { site } from '../content/site';

/**
 * Public marketing header. The primary CTA is "Start Free Trial"; the secondary "Login"
 * button redirects to the EXISTING finance app (never a duplicate login here).
 */
export default function Header() {
  const loginUrl = useLoginUrl();

  const navClass = ({ isActive }: { isActive: boolean }) =>
    `text-sm font-medium ${isActive ? 'text-indigo-600' : 'text-slate-600 hover:text-slate-900'}`;

  return (
    <header className="sticky top-0 z-40 bg-white/90 backdrop-blur border-b border-slate-200">
      <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-2" aria-label={`${site.brand} home`}>
          <span className="text-xl font-bold text-slate-800">
            <span className="text-indigo-600">My</span>Finance
          </span>
        </Link>

        <nav className="hidden md:flex items-center gap-6" aria-label="Primary">
          <NavLink to="/features" className={navClass}>Features</NavLink>
          <NavLink to="/how-it-works" className={navClass}>How it works</NavLink>
          <NavLink to="/pricing" className={navClass}>Pricing</NavLink>
          <NavLink to="/faq" className={navClass}>FAQ</NavLink>
          <NavLink to="/contact" className={navClass}>Contact</NavLink>
        </nav>

        <div className="flex items-center gap-3">
          <a
            href={loginUrl}
            className="text-sm font-medium text-slate-700 hover:text-slate-900"
          >
            Login
          </a>
          <Link
            to="/signup"
            className="text-sm font-semibold bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 transition-colors"
          >
            Start Free Trial
          </Link>
        </div>
      </div>
    </header>
  );
}
