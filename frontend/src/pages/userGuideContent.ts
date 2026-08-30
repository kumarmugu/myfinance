/**
 * User Guide content model.
 *
 * This is the single source of truth for the interactive User Guide. Keep all
 * user-facing guide copy HERE (data), not inside the UI component. The UserGuide
 * page renders whatever this file describes, so updating the guide == editing this
 * file. When a feature/screen changes, update the matching page below (and, if the
 * UI element names change, the `steps`).
 *
 * Screenshots / videos: reference by a stable key/path. Images are optional — the
 * UI shows a labelled placeholder when a screenshot file is not present yet, so the
 * guide works today and real screenshots can be dropped in later without code edits.
 * Put screenshot files under `frontend/public/guide/` and reference them as
 * `/guide/<name>.png`. Videos can be a hosted URL or a `/guide/<name>.mp4` path.
 */

/** A single screenshot slot. `src` may not exist yet — the UI renders a placeholder. */
export interface GuideScreenshot {
  /** Public path (e.g. "/guide/fx-rates-add.png") or remote URL. Optional. */
  src?: string;
  /** Alt text — always required for accessibility, even when src is missing. */
  alt: string;
  /** Optional short caption shown under the image. */
  caption?: string;
}

/** A single optional video slot. */
export interface GuideVideo {
  /** Video URL (mp4 path under /guide, or an embeddable URL). */
  src?: string;
  /** Human-readable title, e.g. "How to configure FX rates". */
  title: string;
}

/** A link to another guide page (by page id) or to an app route. */
export interface GuideRelated {
  /** Human label shown on the chip. */
  label: string;
  /** Guide page id to jump to within the guide. */
  pageId?: string;
  /** App route to open (e.g. "/fx-rates"). Used when pointing at a real screen. */
  route?: string;
}

export interface GuidePage {
  /** Stable id, used for deep-links, search and progress. */
  id: string;
  /** Short page title shown in nav and header. */
  title: string;
  /** One-line summary shown in nav/search results. */
  summary: string;
  /**
   * Optional feature-flag key. When set, the page is only shown if the user has
   * that feature enabled (mirrors the app nav). Config pages have no flag.
   */
  feature?: string;
  /** "What is this feature?" — plain-language description. */
  what: string;
  /** "Why would I use it?" — the reason/benefit. */
  why: string;
  /** "What do I need before I start?" — prerequisites in plain language. */
  prerequisites?: string[];
  /** "How do I use it?" — ordered steps referencing real buttons/fields. */
  steps?: string[];
  /** "What happens after I save / how it affects other parts." */
  afterSave?: string;
  /** "What common mistakes should I avoid?" */
  commonMistakes?: string[];
  /** Handy tips. */
  tips?: string[];
  /** Related guide pages / screens. */
  related?: GuideRelated[];
  /** Screenshot slots (lazy-loaded, placeholder when missing). */
  screenshots?: GuideScreenshot[];
  /** Optional walkthrough video. */
  video?: GuideVideo;
}

export interface GuideSection {
  /** Stable id. */
  id: string;
  /** Section title shown in the sidebar. */
  title: string;
  /** Lucide icon name (resolved in the UI). */
  icon: string;
  /** One-line description of the section. */
  description: string;
  pages: GuidePage[];
}

/**
 * The ordered list of "Getting Started" page ids that make up the setup checklist.
 * The UI shows these as a progress strip so a new user knows what to do next.
 * Order reflects the real configuration dependency chain discovered in the app.
 */
export const SETUP_CHECKLIST: { pageId: string; label: string }[] = [
  { pageId: 'currencies-fx', label: 'Add your currencies & exchange rates' },
  { pageId: 'base-display-currency', label: 'Confirm your base & display currency' },
  { pageId: 'owners-accounts', label: 'Add owners and accounts' },
  { pageId: 'asset-catalog', label: 'Build your asset catalog (investors only)' },
  { pageId: 'net-worth-config', label: 'Choose what counts toward Net Worth' },
  { pageId: 'first-record', label: 'Add your first record' },
];

export const GUIDE_SECTIONS: GuideSection[] = [
  // ─────────────────────────────────────────────────────────────
  {
    id: 'getting-started',
    title: 'Getting Started',
    icon: 'Rocket',
    description: 'Set up MyFinance in the right order before you start entering data.',
    pages: [
      {
        id: 'welcome',
        title: 'Welcome & Key Concepts',
        summary: 'What MyFinance does and the ideas you need to know.',
        what:
          'MyFinance is your personal finance and net-worth tracker. It keeps all your money in one place — investments, bank savings, fixed deposits, property, gold, retirement funds, insurance, loans, income and expenses — and shows your total net worth over time.',
        why:
          'Instead of juggling spreadsheets, you get one consolidated picture that automatically converts everything into a single currency so you can see how you are really doing.',
        steps: [
          'Sign in with the username and password your administrator gave you.',
          'Your available features appear in the left sidebar — you only see the modules that are turned on for your account.',
          'Follow the Getting Started section below in order. It walks you through the small amount of setup needed before tracking finances.',
        ],
        tips: [
          'You do not have to use every module. Track only what matters to you.',
          'Nothing is shared with other users. Your data is private to your account.',
          'You are signed out automatically after one hour of inactivity for safety.',
        ],
        related: [
          { label: 'Currencies & Exchange Rates', pageId: 'currencies-fx' },
          { label: 'Owners & Accounts', pageId: 'owners-accounts' },
        ],
        screenshots: [{ alt: 'The MyFinance dashboard after first login', caption: 'Your dashboard — the home screen after sign-in.' }],
      },
      {
        id: 'currencies-fx',
        title: 'Currencies & Exchange Rates',
        summary: 'Add the currencies you use and the rates between them.',
        what:
          'The Currencies and FX Rates screen (FX Rates in the sidebar, under Configuration) is where you list the currencies you deal with and record the exchange rate between them — for example how many SGD one USD is worth.',
        why:
          'MyFinance never guesses exchange rates and has no live rate feed. It uses the rates YOU enter to convert amounts held in different currencies into one currency for your totals and Net Worth. Without a rate, foreign amounts cannot be converted and are counted as-is.',
        prerequisites: ['You know which currencies you hold money in (for example SGD, USD, EUR, LKR).'],
        steps: [
          'Open FX Rates from the sidebar (Configuration group).',
          'If a currency you need is missing, scroll to "Add New Currency", type the 2–5 letter code (e.g. INR, GBP) and click Add.',
          'Click "Add FX Rate".',
          'Choose the From currency and the To currency (they must be different).',
          'Enter the Rate — how many units of the To currency equal one unit of the From currency (e.g. From USD, To SGD, Rate 1.35 means 1 USD = 1.35 SGD).',
          'Pick the Effective Date, then click Save.',
        ],
        afterSave:
          'The rate is stored with its date. Any screen that shows totals or lets you switch display currency will now use the latest rate you entered. You can keep older rates for point-in-time accuracy.',
        commonMistakes: [
          'Entering the rate the wrong way round. Read it as: 1 [From] = [Rate] [To].',
          'Forgetting to add a rate for a currency you actually hold — its amounts will not convert.',
          'A currency code that is still used by a rate cannot be deleted; remove the rate first.',
        ],
        tips: ['Update rates periodically so your totals stay realistic. There is no automatic feed.'],
        related: [
          { label: 'Open FX Rates screen', route: '/fx-rates' },
          { label: 'Base & Display Currency', pageId: 'base-display-currency' },
          { label: 'Net Worth', pageId: 'net-worth' },
        ],
        screenshots: [
          { alt: 'FX Rates screen showing rate cards and the Add FX Rate button', caption: 'FX Rates: current rates plus the Add FX Rate form.' },
          { alt: 'The Add New Currency section at the bottom of the FX Rates screen', caption: 'Add a currency code that is not yet in the list.' },
        ],
        video: { title: 'How to configure FX rates' },
      },
      {
        id: 'base-display-currency',
        title: 'Base & Display Currency',
        summary: 'The one currency your totals are shown in, and the display toggle.',
        what:
          'Your base currency is the single currency used to add everything up (default is SGD). Display currencies are the options offered in the currency toggle on screens like the Dashboard (default is SGD and USD).',
        why:
          'Because you can hold money in many currencies, MyFinance needs one currency to total them into — that is your base currency. The display toggle lets you view the same totals in another currency without changing anything that is stored.',
        prerequisites: ['Exchange rates entered for the currencies involved (see Currencies & Exchange Rates).'],
        steps: [
          'Your base currency and display currencies are set by your administrator on your user account.',
          'If you need them changed, ask your administrator to update them in User Management.',
          'On screens with a currency toggle, switch between your display currencies to view totals differently.',
        ],
        afterSave:
          'Changing the display currency only changes what you see — it re-calculates using your exchange rates. It never changes the original currency or amount stored on any record.',
        commonMistakes: [
          'Expecting the display toggle to edit your data. It only changes the view.',
          'Assuming a foreign amount will convert without a matching exchange rate.',
        ],
        tips: ['The most important rule in MyFinance: your original currency and amount are always preserved. Everything else is derived from it.'],
        related: [
          { label: 'Currencies & Exchange Rates', pageId: 'currencies-fx' },
          { label: 'Understanding Multi-Currency', pageId: 'multi-currency' },
        ],
      },
      {
        id: 'owners-accounts',
        title: 'Owners & Accounts',
        summary: 'Say who owns the money and where it is held.',
        what:
          'On the Brokers & Owners screen (Configuration group) you set up Owners (the people whose finances you track, such as yourself or a spouse) and Accounts (where money lives — broker, bank or crypto exchange).',
        why:
          'Owners let you track and filter finances per person. Accounts tell MyFinance where each investment, balance or transaction belongs, and each account has its own currency.',
        prerequisites: ['Currencies added if any account is not in your base currency.'],
        steps: [
          'Open Brokers & Owners from the sidebar.',
          'Add an Owner: enter a name and choose the relationship (Self, Spouse, Son, Daughter, Father, Mother, Brother, Sister).',
          'Add an Account: enter a name (e.g. "DBS Savings", "Tiger Brokerage"), choose the type (Broker, Bank or Crypto Exchange), pick the owner, and set the account currency.',
          'Save.',
        ],
        afterSave:
          'Owners and accounts become selectable in the other modules (transactions, dividends, bank savings, and so on). Most pages let you filter by owner.',
        commonMistakes: [
          'You cannot delete an owner or account that is still referenced by records — remove or reassign those records first.',
          'Setting the wrong account currency. The account currency is the settlement currency used for transactions in that account.',
        ],
        tips: ['Create at least one owner (usually yourself) before adding financial records.'],
        related: [
          { label: 'Open Brokers & Owners', route: '/accounts' },
          { label: 'Asset Catalog', pageId: 'asset-catalog' },
          { label: 'Investment currencies explained', pageId: 'investment-currencies' },
        ],
        screenshots: [{ alt: 'Brokers & Owners screen listing owners and accounts', caption: 'Manage owners and the accounts that belong to them.' }],
      },
      {
        id: 'asset-catalog',
        title: 'Asset Catalog',
        summary: 'Define the stocks, ETFs and funds you invest in.',
        feature: 'PORTFOLIO',
        what:
          'The Asset Catalog (Configuration group) is your list of investable instruments — each with a name, ticker symbol, asset type (Index Fund, Growth Equity, Crypto, and so on), its own currency and latest known price.',
        why:
          'When you record a buy or sell, you pick an asset from this catalog. Defining assets once keeps your transactions consistent and lets MyFinance group holdings by type.',
        prerequisites: ['Accounts set up (so you have somewhere to hold the asset).'],
        steps: [
          'Open Asset Catalog from the sidebar.',
          'Add an asset: enter its name and ticker symbol.',
          'Choose the asset type and the asset\'s own currency (e.g. a US ETF is usually in USD).',
          'Optionally record the current price and exchange, then save.',
        ],
        afterSave: 'The asset is now selectable when you add transactions and appears in portfolio groupings by type.',
        commonMistakes: [
          "Confusing the asset's currency with the broker account's currency — they can differ (see Investment currencies explained).",
          'Duplicate symbols. Each symbol should appear once in your catalog.',
        ],
        related: [
          { label: 'Open Asset Catalog', route: '/assets' },
          { label: 'Transactions', pageId: 'transactions' },
          { label: 'Investment currencies explained', pageId: 'investment-currencies' },
        ],
      },
      {
        id: 'net-worth-config',
        title: 'Net Worth Configuration',
        summary: 'Choose which asset types and modules count toward Net Worth.',
        what:
          'Net Worth Config (Configuration group) lets you turn individual asset types and modules on or off for your Net Worth total — for example you might exclude CPF or Insurance.',
        why:
          'Everyone defines "net worth" a little differently. This lets you decide what should and should not be counted so the figure reflects your view.',
        steps: [
          'Open Net Worth Config from the sidebar.',
          'Toggle each asset type or module on or off.',
          'Your choices apply immediately to the Dashboard, Reports and any new snapshots.',
        ],
        afterSave: 'The Dashboard, Reports and Net Worth snapshots recalculate using only the items you left switched on.',
        tips: ['Revisit this after adding a new type of asset to decide whether it should count.'],
        related: [
          { label: 'Open Net Worth Config', route: '/net-worth-config' },
          { label: 'Net Worth', pageId: 'net-worth' },
        ],
      },
      {
        id: 'first-record',
        title: 'Add Your First Record',
        summary: 'You are set up — start tracking.',
        what: 'With currencies, owners, accounts and (for investors) assets in place, you are ready to enter real data.',
        why: 'This is the point where MyFinance starts to show your actual financial picture.',
        steps: [
          'Pick the module that matters most to you from the sidebar (for example Bank Savings, Portfolio or Fixed Deposits).',
          'Add your first record following that module\'s guide page.',
          'Return to the Dashboard to see it reflected in your totals.',
          'Take a Net Worth snapshot so you begin building history.',
        ],
        related: [
          { label: 'Bank Savings', pageId: 'bank-savings' },
          { label: 'Portfolio', pageId: 'portfolio' },
          { label: 'Dashboard', pageId: 'dashboard' },
        ],
      },
      {
        id: 'multi-currency',
        title: 'Understanding Multi-Currency',
        summary: 'How original, base and display currencies work together.',
        what:
          'MyFinance keeps three ideas separate: the original currency and amount you entered, your base currency for totalling, and the display currency you choose to view.',
        why: 'This design means your records are always accurate to what really happened, while your totals can still be shown in whatever currency you prefer.',
        steps: [
          'Original: a savings account of LKR 1,000,000 is always stored as LKR 1,000,000.',
          'Base: if your base currency is SGD, MyFinance converts it to SGD using your LKR→SGD rate for Net Worth and summaries.',
          'Display: if you switch the display toggle to USD, the same value is shown in USD using your rates — the stored LKR value never changes.',
        ],
        commonMistakes: ['Believing that switching display currency edits your data. It never does.'],
        tips: ['If a converted total looks off, check your FX Rates first — that is almost always the cause.'],
        related: [
          { label: 'Currencies & Exchange Rates', pageId: 'currencies-fx' },
          { label: 'Base & Display Currency', pageId: 'base-display-currency' },
          { label: 'Investment currencies explained', pageId: 'investment-currencies' },
        ],
      },
      {
        id: 'investment-currencies',
        title: 'Investment Currencies Explained',
        summary: 'Why a single investment can involve several currencies.',
        feature: 'PORTFOLIO',
        what:
          'An investment can touch more than one currency: the broker account currency, the asset (instrument) currency, and the transaction (settlement) currency.',
        why:
          'These are genuinely different things, and MyFinance keeps each one so your records stay truthful. For example, you might buy a EUR-denominated fund through a USD brokerage account.',
        steps: [
          'Broker account currency: the currency your brokerage settles in (e.g. USD).',
          'Asset currency: the currency the instrument itself trades in (e.g. a EUR index fund is EUR).',
          'Transaction currency: what you actually paid in for that trade — usually the account currency (USD here).',
          'Result: the holding is recorded in EUR (the asset), while the purchase transaction is in USD (the account). Neither overwrites the other.',
        ],
        tips: ['Make sure you have exchange rates covering all the currencies involved so totals convert correctly.'],
        related: [
          { label: 'Asset Catalog', pageId: 'asset-catalog' },
          { label: 'Transactions', pageId: 'transactions' },
          { label: 'Understanding Multi-Currency', pageId: 'multi-currency' },
        ],
      },
    ],
  },

  // ─────────────────────────────────────────────────────────────
  {
    id: 'investments',
    title: 'Investments',
    icon: 'TrendingUp',
    description: 'Track stocks, ETFs, funds, crypto, dividends and cash flows.',
    pages: [
      {
        id: 'portfolio',
        title: 'Portfolio',
        summary: 'See your holdings, cost, current value and gain/loss.',
        feature: 'PORTFOLIO',
        what:
          'The Portfolio screen lists everything you currently hold, with your average buy price, invested amount, current value and profit or loss. It also has tabs for sold positions and short-term trades.',
        why: 'It answers "what do I own and how is it doing?" across all your broker accounts in one place.',
        prerequisites: ['Accounts and assets set up.', 'At least one buy transaction recorded.'],
        steps: [
          'Open Portfolio from the sidebar.',
          'Use the tabs to switch between active holdings, sold positions and short-term trades.',
          'Filter by owner to view one person\'s portfolio.',
          'Toggle the display currency to view values in another currency.',
        ],
        afterSave: 'Holdings are calculated automatically from your transactions — you do not edit holdings directly.',
        commonMistakes: ['Trying to add a holding directly. Record a buy transaction instead and the holding updates itself.'],
        related: [
          { label: 'Open Portfolio', route: '/portfolio' },
          { label: 'Transactions', pageId: 'transactions' },
          { label: 'Dividends', pageId: 'dividends' },
        ],
        screenshots: [{ alt: 'Portfolio screen showing a holdings table with profit and loss', caption: 'Holdings with live cost, value and P&L.' }],
      },
      {
        id: 'transactions',
        title: 'Transactions',
        summary: 'Record buys and sells; holdings update automatically.',
        feature: 'PORTFOLIO',
        what: 'The Transactions screen is where you record each Buy or Sell of an asset, including quantity, price, fees, currency and date.',
        why: 'Transactions are the source of truth for your portfolio. Your holdings, average cost and realised gains are all calculated from them.',
        prerequisites: ['Assets in your catalog.', 'An account to trade through.'],
        steps: [
          'Open Transactions from the sidebar.',
          'Add a transaction: pick the asset, account and owner.',
          'Choose Buy or Sell, then enter quantity, price per unit, fees and the transaction date.',
          'Tag the purpose (e.g. Long Term, Trading) if you want it grouped that way, then save.',
        ],
        afterSave:
          'A buy creates or increases a holding and recalculates the average cost. A sell reduces the holding and records a realised gain/loss under sold positions. Selling more than you own is rejected.',
        commonMistakes: [
          'Selling more units than you hold — this is blocked.',
          'Using the wrong currency. The transaction currency is normally your broker account\'s currency.',
        ],
        related: [
          { label: 'Open Transactions', route: '/transactions' },
          { label: 'Portfolio', pageId: 'portfolio' },
          { label: 'Investment currencies explained', pageId: 'investment-currencies' },
        ],
      },
      {
        id: 'dividends',
        title: 'Dividends',
        summary: 'Record dividend income and see it grow over time.',
        feature: 'DIVIDENDS',
        what: 'The Dividends screen records dividend payments you receive, by asset, account and date, with the year and quarter.',
        why: 'It lets you track passive income, filter by year or quarter, and see a yearly dividend-growth chart.',
        prerequisites: ['Assets and accounts set up.'],
        steps: [
          'Open Dividends from the sidebar.',
          'Add a dividend: choose the asset, account and owner.',
          'Enter the amount, currency and received date; the year and quarter are captured automatically.',
          'Save.',
        ],
        afterSave: 'The payment appears in your dividend totals and yearly growth chart.',
        related: [
          { label: 'Open Dividends', route: '/dividends' },
          { label: 'Reports', pageId: 'reports' },
        ],
      },
      {
        id: 'crypto',
        title: 'Crypto',
        summary: 'Track cryptocurrency holdings by exchange.',
        feature: 'CRYPTO',
        what: 'A dedicated screen for cryptocurrency holdings and trades, organised by exchange (for example Coinhako or Crypto.com).',
        why: 'Keeps crypto separate from equities while still counting toward your overall net worth.',
        prerequisites: ['A crypto exchange account set up under Brokers & Owners.'],
        steps: [
          'Open Crypto from the sidebar.',
          'Record buys and sells per coin, similar to stock transactions.',
          'Review your crypto positions and profit/loss.',
        ],
        related: [
          { label: 'Open Crypto', route: '/crypto' },
          { label: 'Owners & Accounts', pageId: 'owners-accounts' },
        ],
      },
      {
        id: 'cash-flows',
        title: 'Cash Flows',
        summary: 'Track money moving in and out of broker accounts.',
        feature: 'CASH_FLOWS',
        what: 'The Cash Flows screen records deposits into and withdrawals out of your broker/investment accounts.',
        why: 'Knowing how much cash you actually put into an account lets MyFinance show your true return on the money invested.',
        prerequisites: ['An account to record the flow against.'],
        steps: [
          'Open Cash Flows from the sidebar.',
          'Add a deposit or withdrawal: choose the account, amount, currency and date.',
          'Save to update the net amount deposited for that account.',
        ],
        related: [{ label: 'Open Cash Flows', route: '/deposits' }],
      },
    ],
  },

  // ─────────────────────────────────────────────────────────────
  {
    id: 'cash-savings',
    title: 'Cash & Savings',
    icon: 'Banknote',
    description: 'Bank balances and fixed deposits.',
    pages: [
      {
        id: 'bank-savings',
        title: 'Bank Savings',
        summary: 'Track savings account balances across banks.',
        feature: 'BANK_SAVINGS',
        what: 'The Bank Savings screen tracks balances in your savings and current accounts across different banks, in their own currencies.',
        why: 'Cash is part of your net worth. This keeps balances visible and lets you include or exclude each one from the total.',
        prerequisites: ['Currencies and rates for any non-base-currency accounts.'],
        steps: [
          'Open Bank Savings from the sidebar.',
          'Add an account balance: enter the bank/account name, balance, currency and owner.',
          'Choose whether it should be included in Net Worth, then save.',
        ],
        afterSave: 'Included balances are converted to your base currency and added to your Net Worth and summaries.',
        commonMistakes: ['Forgetting to update a balance after big movements — balances are entered manually.'],
        related: [
          { label: 'Open Bank Savings', route: '/bank-savings' },
          { label: 'Net Worth', pageId: 'net-worth' },
        ],
      },
      {
        id: 'fixed-deposits',
        title: 'Fixed Deposits',
        summary: 'Track fixed/term deposits and their maturity.',
        feature: 'FIXED_DEPOSITS',
        what:
          'MyFinance tracks fixed deposits with their principal, interest rate, start and maturity dates. There is a general fixed-deposit module for any bank, plus a specialised Sri Lanka fixed-deposit module.',
        why: 'Fixed deposits lock money away for a term. Tracking maturity dates helps you plan renewals and see expected interest.',
        prerequisites: ['Currencies/rates if a deposit is not in your base currency.'],
        steps: [
          'Open Fixed Deposits from the sidebar.',
          'Add a deposit: enter the bank, principal amount, interest rate, start date and maturity date.',
          'Set its status (e.g. Active) and whether it counts toward Net Worth, then save.',
          'Use the summary and maturing views to see upcoming maturities and expected interest.',
        ],
        afterSave: 'Active deposits are included in your totals (if enabled) and appear in maturity and interest reports.',
        tips: ['Review the maturing list regularly so no deposit lapses without a decision.'],
        related: [
          { label: 'Open Fixed Deposits', route: '/fixed-deposits' },
          { label: 'Net Worth', pageId: 'net-worth' },
        ],
      },
    ],
  },

  // ─────────────────────────────────────────────────────────────
  {
    id: 'other-assets',
    title: 'Other Assets',
    icon: 'Building',
    description: 'Property, precious metals and retirement funds.',
    pages: [
      {
        id: 'real-estate',
        title: 'Real Estate',
        summary: 'Track property value, loan and equity.',
        feature: 'REAL_ESTATE',
        what: 'The Real Estate screen tracks properties with their current valuation, any outstanding loan, the resulting equity, and rental income.',
        why: 'Property is often the largest asset. Tracking value minus loan gives you your true equity in each property.',
        steps: [
          'Open Real Estate from the sidebar.',
          'Add a property: enter its name, current value, outstanding loan and currency.',
          'Record rental income if relevant, then save.',
        ],
        afterSave: 'Your equity (value minus outstanding loan) is included in Net Worth when enabled.',
        related: [
          { label: 'Open Real Estate', route: '/properties' },
          { label: 'Home Loans', pageId: 'home-loans' },
        ],
      },
      {
        id: 'precious-metals',
        title: 'Precious Metals',
        summary: 'Track gold, silver and platinum holdings.',
        feature: 'PRECIOUS_METALS',
        what: 'The Gold & Silver screen tracks precious-metal holdings by weight, purity and value.',
        why: 'Metals are a store of value many people hold outside markets. This keeps them in your overall picture.',
        steps: [
          'Open Gold & Silver from the sidebar.',
          'Add a holding: choose the metal, enter weight, purity, value and currency.',
          'Save.',
        ],
        related: [{ label: 'Open Gold & Silver', route: '/precious-metals' }],
      },
      {
        id: 'srs-cpf',
        title: 'SRS & CPF',
        summary: 'Track retirement contributions and projections.',
        feature: 'SRS_CPF',
        what: 'The SRS & CPF screen tracks retirement funds — Supplementary Retirement Scheme, CPF and employer contributions (Singapore-oriented).',
        why: 'Retirement savings are a big part of long-term net worth and planning. This records contributions and projects growth.',
        steps: [
          'Open SRS & CPF from the sidebar.',
          'Record contributions by year with the relevant fund type.',
          'Review projected values.',
        ],
        tips: ['These modules work in your base currency by design — there is no separate currency field.'],
        related: [
          { label: 'Open SRS & CPF', route: '/srs-cpf' },
          { label: 'Net Worth & Planning', pageId: 'planning' },
        ],
      },
    ],
  },

  // ─────────────────────────────────────────────────────────────
  {
    id: 'income-career',
    title: 'Income & Career',
    icon: 'Wallet',
    description: 'Salary, work history and tax records.',
    pages: [
      {
        id: 'salary',
        title: 'Salary',
        summary: 'Record monthly salary and bonuses.',
        feature: 'SALARY',
        what: 'The Salary screen records your monthly salary with its breakdown, and tracks bonuses separately.',
        why: 'Income history feeds planning and gives context to your savings and net-worth growth.',
        steps: [
          'Open Salary from the sidebar.',
          'Add a monthly salary entry, or use Bulk Add to enter the same amount for a whole year.',
          'Record bonuses separately, then save.',
        ],
        tips: ['Use Bulk Add when your monthly salary is fixed to save time.'],
        related: [{ label: 'Open Salary', route: '/salary' }],
      },
      {
        id: 'work-experience',
        title: 'Work Experience',
        summary: 'Keep a timeline of your career.',
        feature: 'WORK_EXPERIENCE',
        what: 'The Work Experience screen lists companies, positions and dates, and totals your years of experience.',
        why: 'A tidy career timeline is handy for planning and record-keeping.',
        steps: ['Open Work Experience from the sidebar.', 'Add each role with company, position and start/end dates.', 'View the timeline and total experience.'],
        related: [{ label: 'Open Work Experience', route: '/work-experience' }],
      },
      {
        id: 'tax',
        title: 'Tax Records',
        summary: 'Record tax paid per year and see summaries.',
        feature: 'TAX',
        what: 'The Tax Records screen records tax paid per assessment year and shows income-versus-tax over time.',
        why: 'Keeping tax records in one place makes year-on-year comparison and planning easier.',
        steps: ['Open Tax Records from the sidebar.', 'Add a record for the assessment year with the amounts and currency.', 'Review the income-versus-tax chart.'],
        related: [{ label: 'Open Tax Records', route: '/tax' }],
      },
    ],
  },

  // ─────────────────────────────────────────────────────────────
  {
    id: 'protection-liabilities',
    title: 'Protection & Liabilities',
    icon: 'Shield',
    description: 'Insurance policies and loans you owe.',
    pages: [
      {
        id: 'insurance',
        title: 'Life Insurance',
        summary: 'Track policies and yearly bonuses.',
        feature: 'INSURANCE',
        what: 'The Life Insurance screen tracks policies with premiums, coverage, cash value and annual bonus entries.',
        why: 'Some policies build cash value that forms part of your wealth, and tracking premiums helps budgeting.',
        steps: [
          'Open Life Insurance from the sidebar.',
          'Add a policy: enter the provider, policy number, type, premium, coverage and currency.',
          'Record annual bonus entries and choose whether cash value counts toward Net Worth, then save.',
        ],
        related: [
          { label: 'Open Life Insurance', route: '/insurance' },
          { label: 'Net Worth Config', pageId: 'net-worth-config' },
        ],
      },
      {
        id: 'home-loans',
        title: 'Home Loans',
        summary: 'Track mortgages and payment schedules.',
        feature: 'HOME_LOANS',
        what: 'The Home Loans screen tracks a mortgage against a property, including the outstanding balance and payment schedule.',
        why: 'A loan is a liability that reduces your net worth. Tracking it against the property shows your true equity.',
        steps: [
          'Open Home Loans from the sidebar.',
          'Add a loan: enter the property value, outstanding balance and currency.',
          'Record monthly payments split into principal and interest, then save.',
        ],
        afterSave: 'Home equity is calculated as property value minus outstanding balance.',
        related: [
          { label: 'Open Home Loans', route: '/home-loans' },
          { label: 'Real Estate', pageId: 'real-estate' },
        ],
      },
    ],
  },

  // ─────────────────────────────────────────────────────────────
  {
    id: 'budgeting',
    title: 'Budgeting',
    icon: 'PiggyBank',
    description: 'Plan income and spending, and track actual expenses.',
    pages: [
      {
        id: 'budget',
        title: 'Budget & Expenses',
        summary: 'Plan monthly income and allocations, track spending.',
        feature: 'BUDGET',
        what:
          'The Budget & Expenses screen lets you plan income and allocations by month, manage spending categories, and record actual expenses to compare against your plan.',
        why: 'Budgeting shows whether your spending matches your intentions and helps you find room to save.',
        steps: [
          'Open Budget & Expenses from the sidebar.',
          'Set up your spending categories.',
          'Plan expected income and allocations for the month.',
          'Record actual expenses against categories and review the plan-versus-actual report.',
        ],
        tips: ['Budgeting works in your base currency by design — there is no separate currency field here.'],
        commonMistakes: ['A category that still has expenses cannot be removed until those expenses are reassigned or deleted.'],
        related: [{ label: 'Open Budget & Expenses', route: '/budget' }],
      },
    ],
  },

  // ─────────────────────────────────────────────────────────────
  {
    id: 'networth-planning',
    title: 'Net Worth & Planning',
    icon: 'Target',
    description: 'Your total wealth, targets and history.',
    pages: [
      {
        id: 'net-worth',
        title: 'Net Worth',
        summary: 'What net worth means and how it is calculated.',
        what:
          'Net worth is everything you own (assets) minus everything you owe (liabilities). MyFinance adds up your investments, savings, deposits, property equity, metals, retirement funds and more, subtracts loans, and shows a single figure.',
        why: 'It is the clearest single measure of your financial progress over time.',
        steps: [
          'Each asset and liability is converted to your base currency using your exchange rates.',
          'Only the asset types and modules you enabled in Net Worth Config are counted.',
          'The Dashboard shows the current figure; take snapshots to build a history you can chart.',
        ],
        afterSave: 'Snapshots you take are stored so Reports can show how your net worth changes month to month and year to year.',
        commonMistakes: [
          'Missing exchange rates make foreign holdings count without conversion — keep rates current.',
          'Forgetting to take snapshots means no history to chart later.',
        ],
        related: [
          { label: 'Net Worth Config', pageId: 'net-worth-config' },
          { label: 'Understanding Multi-Currency', pageId: 'multi-currency' },
          { label: 'Open Planning', route: '/planning' },
        ],
        screenshots: [{ alt: 'Net worth history chart over several years', caption: 'Net worth history built from your snapshots.' }],
      },
      {
        id: 'planning',
        title: 'Allocation & Planning',
        summary: 'Set target allocations and track net-worth history.',
        what:
          'The Allocation & Net Worth screen lets you set target percentages for each asset type, compare them with your actual mix, and view your net-worth history and snapshots.',
        why: 'Targets keep your portfolio balanced to your plan, and history shows whether you are on track.',
        steps: [
          'Open Allocation & Net Worth from the sidebar.',
          'Set a target percentage for each asset type (they should add up to 100%).',
          'Compare your actual allocation with the target and see the gap.',
          'Take a snapshot to record your net worth at this point in time.',
        ],
        related: [
          { label: 'Open Allocation & Net Worth', route: '/planning' },
          { label: 'Net Worth', pageId: 'net-worth' },
          { label: 'Reports', pageId: 'reports' },
        ],
      },
    ],
  },

  // ─────────────────────────────────────────────────────────────
  {
    id: 'reports-dashboard',
    title: 'Reports & Dashboard',
    icon: 'FileBarChart',
    description: 'Your at-a-glance summaries and deeper reports.',
    pages: [
      {
        id: 'dashboard',
        title: 'Dashboard',
        summary: 'Your financial health at a glance.',
        what:
          'The Dashboard is the home screen. It shows your net worth, invested amount, gain/loss and recent activity, with charts for allocation and history. You can filter by owner and switch display currency.',
        why: 'It is the fastest way to see where you stand without opening every module.',
        steps: [
          'Open the Dashboard (the home icon at the top of the sidebar).',
          'Use the owner filter to focus on one person.',
          'Switch the display currency to view totals differently.',
          'Take a snapshot to record the current net worth.',
        ],
        related: [
          { label: 'Open Dashboard', route: '/' },
          { label: 'Net Worth', pageId: 'net-worth' },
          { label: 'Reports', pageId: 'reports' },
        ],
        screenshots: [{ alt: 'Dashboard with net worth cards and charts', caption: 'The Dashboard summarises your whole financial picture.' }],
      },
      {
        id: 'reports',
        title: 'Reports',
        summary: 'Deeper analysis, trends and comparisons.',
        feature: 'REPORTS',
        what: 'The Reports screen provides net-worth breakdowns over time, year-over-year growth comparisons and investment-flow analysis.',
        why: 'Reports turn your data into trends so you can understand what is driving your progress.',
        steps: [
          'Open Reports from the sidebar.',
          'Choose the report and any filters (such as date range or currency).',
          'Read the charts and tables.',
        ],
        related: [
          { label: 'Open Reports', route: '/reports' },
          { label: 'Dashboard', pageId: 'dashboard' },
        ],
      },
    ],
  },

  // ─────────────────────────────────────────────────────────────
  {
    id: 'settings-account',
    title: 'Settings & Account',
    icon: 'Settings',
    description: 'Your account, password and preferences.',
    pages: [
      {
        id: 'account-settings',
        title: 'Account & Password',
        summary: 'Manage your sign-in and preferences.',
        what:
          'Your profile shows your display name at the bottom of the sidebar, where you can also change your password. Currency preferences (base and display currencies) and which features are enabled are managed by your administrator.',
        why: 'Keeping your password strong and your preferences correct keeps your data safe and shown the way you want.',
        steps: [
          'Click Change Password at the bottom of the sidebar to update your password.',
          'Ask your administrator to change your base currency, display currencies, or enabled features.',
        ],
        tips: ['You are signed out automatically after one hour of inactivity — this cannot be disabled and protects your data.'],
        related: [{ label: 'Base & Display Currency', pageId: 'base-display-currency' }],
      },
    ],
  },
];

/** Flat list of all pages, for search and lookup. */
export const ALL_GUIDE_PAGES: (GuidePage & { sectionId: string; sectionTitle: string })[] =
  GUIDE_SECTIONS.flatMap((s) => s.pages.map((p) => ({ ...p, sectionId: s.id, sectionTitle: s.title })));
