/**
 * Config-driven marketing content.
 *
 * All copy, feature descriptions, screenshots, videos, and FAQs live here (or could be
 * loaded from a CMS/API later) so content can change WITHOUT editing UI components.
 *
 * IMPORTANT: only advertise features that genuinely exist in the MyFinance application.
 * Media fields use placeholder paths under /media — replace with real assets when available.
 */

export interface FeatureContent {
  key: string;
  title: string;
  description: string;
  benefits: string[];
  /** Screenshot placeholder path (put real images under public/media). */
  image?: string;
  /** Optional demo video URL (YouTube/Vimeo/self-hosted). */
  videoUrl?: string;
  icon: string; // lucide-react icon name
}

export interface FaqItem {
  question: string;
  answer: string;
}

export interface HowItWorksStep {
  title: string;
  description: string;
}

export const site = {
  brand: 'MyFinance',
  tagline: 'Take control of your money',
  valueProp:
    'Track investments, savings, budgets, and net worth in one secure, private place. Built for people who want a clear picture of their finances.',

  hero: {
    // Placeholders — swap for a real product screenshot/video.
    image: '/media/hero-dashboard.png',
    videoUrl: '',
  },

  // Only real MyFinance capabilities (derived from the actual application).
  features: [
    {
      key: 'PORTFOLIO',
      title: 'Investment Portfolio',
      description:
        'Track holdings across multiple brokerage accounts and currencies, with buy/sell transactions and realized/unrealized profit.',
      benefits: ['Multi-account & multi-currency', 'Automatic holdings from transactions', 'Realized P/L on sold positions'],
      image: '/media/feature-portfolio.png',
      icon: 'TrendingUp',
    },
    {
      key: 'DIVIDENDS',
      title: 'Dividend Tracking',
      description: 'Record and analyze dividend income by instrument, account, and quarter.',
      benefits: ['Quarterly breakdowns', 'Per-instrument history', 'Income trends'],
      image: '/media/feature-dividends.png',
      icon: 'DollarSign',
    },
    {
      key: 'CRYPTO',
      title: 'Crypto Holdings',
      description: 'Keep your crypto positions alongside the rest of your portfolio.',
      benefits: ['Exchange & wallet tracking', 'Unified net worth view'],
      image: '/media/feature-crypto.png',
      icon: 'Bitcoin',
    },
    {
      key: 'FIXED_DEPOSITS',
      title: 'Fixed Deposits',
      description: 'Manage fixed deposits with maturity tracking and expected interest.',
      benefits: ['Maturity reminders', 'Interest projections', 'Multi-holder support'],
      image: '/media/feature-fd.png',
      icon: 'Landmark',
    },
    {
      key: 'BUDGET',
      title: 'Budget & Expenses',
      description: 'Plan budgets and track expenses to stay on top of your cash flow.',
      benefits: ['Category budgets', 'Spending insights'],
      image: '/media/feature-budget.png',
      icon: 'Receipt',
    },
    {
      key: 'REPORTS',
      title: 'Reports & Net Worth',
      description: 'Historical net-worth snapshots, allocation targets, and clear reports.',
      benefits: ['Net-worth over time', 'Allocation vs targets', 'Exportable reports'],
      image: '/media/feature-reports.png',
      icon: 'FileBarChart',
    },
  ] as FeatureContent[],

  howItWorks: [
    { title: 'Create your account', description: 'Sign up in under a minute with just your email.' },
    { title: 'Start your 7-day free trial', description: 'Explore every feature — no card required.' },
    { title: 'Set up your finances', description: 'Add accounts, holdings, and budgets.' },
    { title: 'Choose a plan', description: 'Pick the plan that fits when your trial ends.' },
    { title: 'Keep growing', description: 'Continue with uninterrupted access and insights.' },
  ] as HowItWorksStep[],

  faqs: [
    { question: 'Is there a free trial?', answer: 'Yes — every account starts with a 7-day free trial. No credit card required to begin.' },
    { question: 'What happens after the trial?', answer: 'Choose a paid plan to keep access. If you do nothing, access pauses until you subscribe.' },
    { question: 'How is my data protected?', answer: 'We use encryption in transit, secure authentication, and strict data isolation between accounts.' },
    { question: 'Can I cancel anytime?', answer: 'Yes. You can cancel from your billing portal and keep access until the end of your period.' },
    { question: 'Which payment methods are supported?', answer: 'Credit/debit cards and PayNow, processed securely by our payment provider.' },
  ] as FaqItem[],

  trust: [
    'Encryption in transit (HTTPS everywhere)',
    'Your data is isolated from other customers',
    'We never store your card details',
  ],

  legal: {
    company: 'MyFinance',
    contactEmail: 'support@myfinance.example.com',
  },
};

export type SiteContent = typeof site;
