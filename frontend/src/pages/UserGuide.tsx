import { useMemo, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Search, ChevronRight, ChevronDown, CheckCircle2, Circle, ArrowRight, Menu, X,
  Play, ImageOff, Lightbulb, AlertTriangle, ListChecks, HelpCircle,
  Rocket, TrendingUp, Banknote, Building, Wallet, Shield, PiggyBank, Target,
  FileBarChart, Settings, BookOpen, LucideIcon,
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import {
  GUIDE_SECTIONS, ALL_GUIDE_PAGES, SETUP_CHECKLIST,
  type GuideSection, type GuidePage, type GuideScreenshot, type GuideVideo, type GuideRelated,
} from './userGuideContent';

const ICONS: Record<string, LucideIcon> = {
  Rocket, TrendingUp, Banknote, Building, Wallet, Shield, PiggyBank, Target,
  FileBarChart, Settings, BookOpen,
};

const LOCAL_KEY = 'myfinance.guide.progress';

function loadProgress(): Record<string, boolean> {
  try {
    return JSON.parse(localStorage.getItem(LOCAL_KEY) || '{}');
  } catch {
    return {};
  }
}

export default function UserGuide() {
  const { hasFeature } = useAuth();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [activePageId, setActivePageId] = useState<string>('welcome');
  const [expanded, setExpanded] = useState<Record<string, boolean>>({ 'getting-started': true });
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [progress, setProgress] = useState<Record<string, boolean>>(loadProgress);
  const contentRef = useRef<HTMLDivElement>(null);

  // Only show pages whose feature the user has (config pages have no feature).
  const visibleSections = useMemo<GuideSection[]>(() => {
    return GUIDE_SECTIONS
      .map((s) => ({ ...s, pages: s.pages.filter((p) => !p.feature || hasFeature(p.feature)) }))
      .filter((s) => s.pages.length > 0);
  }, [hasFeature]);

  const visiblePages = useMemo(() => visibleSections.flatMap((s) => s.pages), [visibleSections]);

  // Search across title, summary, what/why, steps, tips.
  const searchResults = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return null;
    const haystack = (p: GuidePage) =>
      [p.title, p.summary, p.what, p.why, ...(p.steps || []), ...(p.tips || []), ...(p.commonMistakes || [])]
        .join(' ')
        .toLowerCase();
    return ALL_GUIDE_PAGES
      .filter((p) => (!p.feature || hasFeature(p.feature)) && haystack(p).includes(q))
      .slice(0, 30);
  }, [query, hasFeature]);

  const activePage = useMemo(
    () => visiblePages.find((p) => p.id === activePageId) || visiblePages[0],
    [visiblePages, activePageId],
  );

  const openPage = (pageId: string) => {
    const section = visibleSections.find((s) => s.pages.some((p) => p.id === pageId));
    if (section) setExpanded((e) => ({ ...e, [section.id]: true }));
    setActivePageId(pageId);
    setQuery('');
    setMobileNavOpen(false);
    contentRef.current?.scrollTo?.({ top: 0, behavior: 'smooth' });
  };

  const toggleSection = (id: string) => setExpanded((e) => ({ ...e, [id]: !e[id] }));

  const toggleDone = (pageId: string) => {
    setProgress((prev) => {
      const next = { ...prev, [pageId]: !prev[pageId] };
      try {
        localStorage.setItem(LOCAL_KEY, JSON.stringify(next));
      } catch {
        /* ignore storage errors */
      }
      return next;
    });
  };

  const setupDone = SETUP_CHECKLIST.filter((c) => progress[c.pageId]).length;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 flex items-center gap-2">
            <BookOpen size={22} className="text-indigo-600" /> User Guide
          </h1>
          <p className="text-slate-500 text-sm mt-1">
            Learn what to set up first, why you need it, and how to use every part of MyFinance.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setMobileNavOpen((o) => !o)}
          className="lg:hidden flex items-center gap-2 px-3 py-2 rounded-lg border border-slate-200 text-sm text-slate-600"
          aria-label="Toggle guide navigation"
          aria-expanded={mobileNavOpen}
        >
          {mobileNavOpen ? <X size={16} /> : <Menu size={16} />} Contents
        </button>
      </div>

      {/* Search */}
      <div className="relative">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden />
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search the guide — e.g. FX rate, how do I add a bank account, net worth"
          aria-label="Search the user guide"
          className="w-full border border-slate-300 rounded-lg pl-9 pr-3 py-2.5 text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
        />
      </div>

      <div className="flex gap-6">
        {/* Sidebar navigation */}
        <nav
          aria-label="Guide sections"
          className={`${mobileNavOpen ? 'block' : 'hidden'} lg:block w-full lg:w-72 shrink-0`}
        >
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-3 lg:sticky lg:top-4">
            {/* Setup progress */}
            <div className="mb-3 p-3 rounded-lg bg-indigo-50 border border-indigo-100">
              <p className="text-xs font-semibold text-indigo-800 flex items-center gap-1.5">
                <ListChecks size={14} /> Getting Started Progress
              </p>
              <p className="text-[11px] text-indigo-600 mt-0.5">
                {setupDone} of {SETUP_CHECKLIST.length} steps done
              </p>
              <div className="mt-2 h-1.5 rounded-full bg-indigo-100 overflow-hidden" aria-hidden>
                <div
                  className="h-full bg-indigo-500 transition-all"
                  style={{ width: `${(setupDone / SETUP_CHECKLIST.length) * 100}%` }}
                />
              </div>
            </div>

            {visibleSections.map((section) => {
              const Icon = ICONS[section.icon] || BookOpen;
              const isOpen = !!expanded[section.id];
              return (
                <div key={section.id} className="mb-1">
                  <button
                    type="button"
                    onClick={() => toggleSection(section.id)}
                    aria-expanded={isOpen}
                    className="w-full flex items-center gap-2 px-2 py-2 rounded-lg text-sm font-medium text-slate-700 hover:bg-slate-100"
                  >
                    <Icon size={16} className="text-indigo-600 shrink-0" />
                    <span className="flex-1 text-left">{section.title}</span>
                    {isOpen ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                  </button>
                  {isOpen && (
                    <ul className="ml-4 mt-0.5 border-l border-slate-100 pl-2 space-y-0.5">
                      {section.pages.map((page) => (
                        <li key={page.id}>
                          <button
                            type="button"
                            onClick={() => openPage(page.id)}
                            aria-current={activePageId === page.id ? 'page' : undefined}
                            className={`w-full flex items-center gap-2 text-left px-2 py-1.5 rounded-md text-[13px] transition-colors ${
                              activePageId === page.id
                                ? 'bg-indigo-50 text-indigo-700 font-medium'
                                : 'text-slate-600 hover:bg-slate-100'
                            }`}
                          >
                            {progress[page.id] ? (
                              <CheckCircle2 size={13} className="text-emerald-500 shrink-0" />
                            ) : (
                              <Circle size={13} className="text-slate-300 shrink-0" />
                            )}
                            {page.title}
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              );
            })}
          </div>
        </nav>

        {/* Main content */}
        <div ref={contentRef} className="flex-1 min-w-0">
          {searchResults ? (
            <SearchResultsView results={searchResults} query={query} onOpen={openPage} />
          ) : activePage ? (
            <PageView
              page={activePage}
              done={!!progress[activePage.id]}
              onToggleDone={() => toggleDone(activePage.id)}
              onOpenPage={openPage}
              onOpenRoute={(r) => navigate(r)}
            />
          ) : (
            <p className="text-slate-500 text-sm">No guide content available for your enabled features.</p>
          )}
        </div>
      </div>
    </div>
  );
}

function SearchResultsView({
  results,
  query,
  onOpen,
}: {
  results: (GuidePage & { sectionTitle: string })[];
  query: string;
  onOpen: (id: string) => void;
}) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
      <p className="text-sm text-slate-600 mb-4">
        {results.length === 0 ? (
          <>No results for &ldquo;<span className="font-medium">{query}</span>&rdquo;. Try a simpler word.</>
        ) : (
          <>
            {results.length} result{results.length === 1 ? '' : 's'} for &ldquo;
            <span className="font-medium">{query}</span>&rdquo;
          </>
        )}
      </p>
      <ul className="divide-y divide-slate-100">
        {results.map((p) => (
          <li key={p.id}>
            <button
              type="button"
              onClick={() => onOpen(p.id)}
              className="w-full text-left py-3 px-2 rounded-lg hover:bg-slate-50 flex items-start gap-3"
            >
              <ChevronRight size={16} className="text-slate-400 mt-0.5 shrink-0" />
              <span>
                <span className="block text-sm font-medium text-slate-800">{p.title}</span>
                <span className="block text-xs text-slate-500">
                  {p.sectionTitle} — {p.summary}
                </span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

function PageView({
  page,
  done,
  onToggleDone,
  onOpenPage,
  onOpenRoute,
}: {
  page: GuidePage;
  done: boolean;
  onToggleDone: () => void;
  onOpenPage: (id: string) => void;
  onOpenRoute: (route: string) => void;
}) {
  return (
    <article className="space-y-6" aria-labelledby="guide-page-title">
      {/* Title + mark-done */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="guide-page-title" className="text-xl font-bold text-slate-800">
              {page.title}
            </h2>
            <p className="text-slate-500 text-sm mt-1">{page.summary}</p>
          </div>
          <button
            type="button"
            onClick={onToggleDone}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors shrink-0 ${
              done
                ? 'bg-emerald-50 border-emerald-200 text-emerald-700'
                : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'
            }`}
            aria-pressed={done}
          >
            {done ? <CheckCircle2 size={14} /> : <Circle size={14} />}
            {done ? 'Done' : 'Mark as done'}
          </button>
        </div>

        <QaBlock icon={<HelpCircle size={15} className="text-indigo-500" />} label="What is this?">
          {page.what}
        </QaBlock>
        <QaBlock icon={<Lightbulb size={15} className="text-amber-500" />} label="Why would I use it?">
          {page.why}
        </QaBlock>
      </div>

      {page.prerequisites && page.prerequisites.length > 0 && (
        <Card title="Before you start" icon={<ListChecks size={16} className="text-indigo-600" />}>
          <ul className="list-disc list-inside space-y-1 text-sm text-slate-600">
            {page.prerequisites.map((x, i) => (
              <li key={i}>{x}</li>
            ))}
          </ul>
        </Card>
      )}

      {page.steps && page.steps.length > 0 && (
        <Card title="How to do it" icon={<ArrowRight size={16} className="text-indigo-600" />}>
          <ol className="space-y-2">
            {page.steps.map((step, i) => (
              <li key={i} className="flex items-start gap-3 text-sm text-slate-700">
                <span className="w-5 h-5 rounded-full bg-indigo-100 text-indigo-700 text-xs font-bold flex items-center justify-center shrink-0 mt-0.5">
                  {i + 1}
                </span>
                {step}
              </li>
            ))}
          </ol>
        </Card>
      )}

      {page.screenshots && page.screenshots.length > 0 && (
        <Card title="What it looks like" icon={<ImageOff size={16} className="text-indigo-600" />}>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {page.screenshots.map((shot, i) => (
              <Screenshot key={i} shot={shot} />
            ))}
          </div>
        </Card>
      )}

      {page.video && <VideoBlock video={page.video} />}

      {page.afterSave && (
        <Card title="What happens after you save" icon={<CheckCircle2 size={16} className="text-emerald-600" />}>
          <p className="text-sm text-slate-600">{page.afterSave}</p>
        </Card>
      )}

      {page.commonMistakes && page.commonMistakes.length > 0 && (
        <Card title="Common mistakes to avoid" icon={<AlertTriangle size={16} className="text-amber-600" />}>
          <ul className="list-disc list-inside space-y-1 text-sm text-slate-600">
            {page.commonMistakes.map((x, i) => (
              <li key={i}>{x}</li>
            ))}
          </ul>
        </Card>
      )}

      {page.tips && page.tips.length > 0 && (
        <div className="bg-indigo-50 border border-indigo-200 rounded-xl p-4">
          <p className="text-sm font-semibold text-indigo-800 flex items-center gap-1.5 mb-2">
            <Lightbulb size={15} /> Tips
          </p>
          <ul className="space-y-1 text-xs text-indigo-700">
            {page.tips.map((x, i) => (
              <li key={i}>{x}</li>
            ))}
          </ul>
        </div>
      )}

      {page.related && page.related.length > 0 && (
        <Card title="Related" icon={<ArrowRight size={16} className="text-indigo-600" />}>
          <div className="flex flex-wrap gap-2">
            {page.related.map((rel, i) => (
              <RelatedChip key={i} rel={rel} onOpenPage={onOpenPage} onOpenRoute={onOpenRoute} />
            ))}
          </div>
        </Card>
      )}
    </article>
  );
}

function QaBlock({ icon, label, children }: { icon: React.ReactNode; label: string; children: React.ReactNode }) {
  return (
    <div className="mt-4">
      <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-1.5">
        {icon} {label}
      </p>
      <p className="text-sm text-slate-700 mt-1">{children}</p>
    </div>
  );
}

function Card({ title, icon, children }: { title: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <section className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
      <h3 className="text-base font-semibold text-slate-800 mb-3 flex items-center gap-2">
        {icon} {title}
      </h3>
      {children}
    </section>
  );
}

function Screenshot({ shot }: { shot: GuideScreenshot }) {
  const [failed, setFailed] = useState(false);
  const showPlaceholder = !shot.src || failed;
  return (
    <figure className="m-0">
      {showPlaceholder ? (
        <div
          className="flex flex-col items-center justify-center gap-2 h-40 rounded-lg border-2 border-dashed border-slate-200 bg-slate-50 text-slate-400"
          role="img"
          aria-label={shot.alt}
        >
          <ImageOff size={22} />
          <span className="text-xs px-4 text-center">{shot.alt}</span>
        </div>
      ) : (
        <img
          src={shot.src}
          alt={shot.alt}
          loading="lazy"
          onError={() => setFailed(true)}
          className="rounded-lg border border-slate-200 w-full"
        />
      )}
      {shot.caption && <figcaption className="text-xs text-slate-500 mt-1.5">{shot.caption}</figcaption>}
    </figure>
  );
}

function VideoBlock({ video }: { video: GuideVideo }) {
  const [playing, setPlaying] = useState(false);
  return (
    <Card title="Watch" icon={<Play size={16} className="text-indigo-600" />}>
      {video.src && playing ? (
        <video src={video.src} controls autoPlay className="w-full rounded-lg border border-slate-200" aria-label={video.title}>
          <track kind="captions" />
        </video>
      ) : (
        <button
          type="button"
          onClick={() => video.src && setPlaying(true)}
          disabled={!video.src}
          className="flex items-center gap-3 w-full text-left p-4 rounded-lg border border-slate-200 hover:bg-slate-50 disabled:cursor-default"
        >
          <span className="w-10 h-10 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center shrink-0">
            <Play size={18} />
          </span>
          <span>
            <span className="block text-sm font-medium text-slate-800">{video.title}</span>
            <span className="block text-xs text-slate-500">{video.src ? 'Click to play' : 'Video coming soon'}</span>
          </span>
        </button>
      )}
    </Card>
  );
}

function RelatedChip({
  rel,
  onOpenPage,
  onOpenRoute,
}: {
  rel: GuideRelated;
  onOpenPage: (id: string) => void;
  onOpenRoute: (route: string) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => (rel.pageId ? onOpenPage(rel.pageId) : rel.route ? onOpenRoute(rel.route) : undefined)}
      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-slate-100 hover:bg-indigo-50 hover:text-indigo-700 text-slate-600 text-xs font-medium transition-colors"
    >
      <ArrowRight size={12} /> {rel.label}
    </button>
  );
}
