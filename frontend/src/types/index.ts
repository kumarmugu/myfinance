// ─── Enums ───
export type AssetType = 'INDEX_FUND' | 'MUTUAL_FUND' | 'GROWTH_EQUITY' | 'DIVIDEND_EQUITY' | 'LEVERAGED_ETF' | 'MONEY_MARKET' | 'FIXED_DEPOSIT' | 'SAVINGS' | 'CRYPTO' | 'GOLD' | 'BOND' | 'REIT' | 'COMMODITY' | 'INSURANCE' | 'PENSION' | 'OTHER' | string;
export type AccountType = 'BROKER' | 'BANK' | 'CRYPTO_EXCHANGE';
export type TransactionType = 'BUY' | 'SELL' | 'DIVIDEND' | 'DEPOSIT' | 'WITHDRAWAL';
export type InvestmentPurpose = 'LONG_TERM' | 'TRADING' | 'DIVIDEND_REINVESTMENT' | 'SRS' | 'RETIREMENT' | 'SHORT_TERM';
export type Currency = 'SGD' | 'USD' | 'EUR' | 'LKR' | 'INR' | 'GBP' | 'AUD' | 'JPY' | 'CNY' | 'MYR' | 'THB' | 'HKD' | 'NZD' | 'CHF' | 'CAD' | string;
export type FDStatus = 'ACTIVE' | 'MATURED' | 'RENEWED' | 'CLOSED' | 'REQUIRES_UPDATE';
export type OwnerRelationship = 'SELF' | 'SPOUSE' | 'SON' | 'DAUGHTER' | 'FATHER' | 'MOTHER' | 'BROTHER' | 'SISTER';

// ─── Core Models ───
export interface Owner {
  id: number;
  name: string;
  relationship: OwnerRelationship;
  isActive: boolean;
  createdAt: string;
}

export interface Account {
  id: number;
  name: string;
  accountType: AccountType;
  owner: Owner;
  currency: Currency;
  accountNumber: string | null;
  description: string;
  createdAt: string;
  updatedAt: string;
}

export interface Asset {
  id: number;
  name: string;
  symbol: string;
  assetType: AssetType;
  currentPrice: number;
  /** When currentPrice was last changed (null if never set). Distinct from updatedAt. */
  priceUpdatedAt: string | null;
  currency: Currency;
  exchange: string;
  description: string;
  includeInNetWorth: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CurrencyRate {
  id: number;
  fromCurrency: string;
  toCurrency: string;
  rate: number;
  effectiveDate: string;
}

// ─── Investment Models ───
export interface Transaction {
  id: number;
  asset: Asset;
  account: Account;
  owner: Owner;
  transactionType: TransactionType;
  quantity: number;
  pricePerUnit: number;
  totalAmount: number;
  fees: number;
  currency: Currency;
  transactionDate: string;
  notes: string;
  purpose: InvestmentPurpose | null;
  createdAt: string;
}

export interface Holding {
  id: number;
  asset: Asset;
  account: Account;
  owner: Owner;
  quantity: number;
  averageBuyPrice: number;
  investedAmount: number;
  currency: Currency;
  purpose: InvestmentPurpose | null;
  updatedAt: string;
}

export interface SoldPosition {
  id: number;
  asset: Asset;
  account: Account;
  owner: Owner;
  quantity: number;
  buyPrice: number;
  sellPrice: number;
  investedAmount: number;
  soldAmount: number;
  profit: number;
  profitPercentage: number;
  currency: Currency;
  investedDate: string;
  soldDate: string;
  holdingPeriod: string;
  isShortTerm: boolean;
  notes: string;
}

export interface Dividend {
  id: number;
  asset: Asset | null;
  account: Account;
  owner: Owner;
  amount: number;
  currency: Currency;
  receivedDate: string;
  year: number;
  quarter: string;
  instrument: string;
  notes: string;
}

// ─── Fixed Deposit Models ───
export interface Bank {
  id: number;
  name: string;
  shortName: string;
  country: string;
}

export interface FDHolder {
  id: number;
  name: string;
  relationship: string;
  isSeniorCitizen: boolean;
  notes: string;
}

export interface FixedDeposit {
  id: number;
  holder: FDHolder;
  jointHolder: FDHolder | null;
  bank: Bank;
  accountNumber: string;
  principalAmount: number;
  interestRate: number;
  startDate: string;
  maturityDate: string;
  period: string;
  branch: string;
  category: string;
  status: FDStatus;
  expectedInterest: number;
  beneficiary: string;
  purpose: string;
  notes: string;
  requiresUpdate: boolean;
  includeInNetWorth: boolean;
  netWorthAmount: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface FDSummary {
  totalFDs: number;
  totalPrincipal: number;
  totalExpectedInterest: number;
  byBank: Record<string, number>;
  maturingWithin30Days: number;
  maturingWithin90Days: number;
  requiresUpdate: number;
  includedInNetWorth: number;
  includedInNetWorthCount: number;
}

// ─── Planning Models ───
export interface AllocationTarget {
  id: number;
  owner: Owner;
  assetType: AssetType;
  targetPercentage: number;
  targetAmount: number;
}

export interface AccountDeposit {
  id: number;
  account: Account;
  amount: number;
  depositType: string;
  currency: Currency;
  depositDate: string;
  notes: string;
}

export interface NetWorthSnapshot {
  id: number;
  owner: Owner | null;
  snapshotDate: string;
  year: number;
  totalIndexFund: number;
  totalMutualFund: number;
  totalGrowthEquity: number;
  totalDividendEquity: number;
  totalLeveragedEtf: number;
  totalMoneyMarket: number;
  totalFixedDeposit: number;
  totalSavings: number;
  totalCrypto: number;
  totalNetWorth: number;
  currency: Currency;
}

// ─── DTOs ───
export interface DashboardSummary {
  totalNetWorth: number;
  totalInvested: number;
  totalGainLoss: number;
  gainLossPercentage: number;
  allocationByType: Record<string, number>;
  totalHoldings: number;
  totalAccounts: number;
  /** Currency the monetary values above are expressed in (net-worth base, e.g. "SGD"). */
  baseCurrency?: string;
  /** Factors to convert a base-currency amount into other display currencies (from the user's own FX rates). */
  displayRates?: Record<string, number>;
}

export interface TransactionRequest {
  assetId: number;
  accountId: number;
  ownerId: number;
  transactionType: TransactionType;
  quantity: number;
  pricePerUnit: number;
  fees?: number;
  currency?: string;
  transactionDate?: string;
  notes?: string;
  purpose?: InvestmentPurpose;
}

// ─── Helpers ───
export const ASSET_TYPE_LABELS: Record<string, string> = {
  INDEX_FUND: 'Index Fund',
  MUTUAL_FUND: 'Mutual Fund',
  GROWTH_EQUITY: 'Growth Equity',
  DIVIDEND_EQUITY: 'Dividend Equity',
  LEVERAGED_ETF: 'Leveraged ETF (3x)',
  MONEY_MARKET: 'Money Market',
  FIXED_DEPOSIT: 'Fixed Deposit',
  SAVINGS: 'Cash / Savings (holding)',
  CRYPTO: 'Crypto',
  GOLD: 'Gold',
  BOND: 'Bond',
  REIT: 'REIT',
  COMMODITY: 'Commodity',
  INSURANCE: 'Insurance',
  PENSION: 'Pension',
  OTHER: 'Other',
};

export const ASSET_TYPE_COLORS: Record<string, string> = {
  INDEX_FUND: '#6366f1',
  GROWTH_EQUITY: '#06b6d4',
  MUTUAL_FUND: '#10b981',
  DIVIDEND_EQUITY: '#8b5cf6',
  LEVERAGED_ETF: '#f97316',
  MONEY_MARKET: '#14b8a6',
  FIXED_DEPOSIT: '#64748b',
  SAVINGS: '#eab308',
  CRYPTO: '#f59e0b',
  GOLD: '#d97706',
  BOND: '#0891b2',
  REIT: '#7c3aed',
  COMMODITY: '#b45309',
  INSURANCE: '#059669',
  PENSION: '#4f46e5',
  OTHER: '#6b7280',
};

// Labels/colors for standalone net-worth modules (keys from NetWorthConfig MODULE_KEYS).
// Kept separate from ASSET_TYPE_* which describe brokerage holding types.
export const NET_WORTH_MODULE_LABELS: Record<string, string> = {
  BANK_SAVINGS: 'Bank Savings',
  PROPERTY: 'Real Estate',
  PRECIOUS_METAL: 'Precious Metals',
  GENERIC_FD: 'Fixed Deposits',
};

export const NET_WORTH_MODULE_COLORS: Record<string, string> = {
  BANK_SAVINGS: '#0ea5e9',
  PROPERTY: '#22c55e',
  PRECIOUS_METAL: '#eab308',
  GENERIC_FD: '#94a3b8',
};

// ─── Insurance ───
export interface InsurancePolicy {
  id: number;
  policyName: string;
  provider: string;
  policyNumber: string;
  policyType: string;
  annualPremium: number;
  currency: Currency;
  coverageAmount: number;
  cashValue: number;
  startDate: string;
  maturityDate: string;
  isActive: boolean;
  includeInNetWorth: boolean;
  beneficiary: string;
  notes: string;
  owner: Owner | null;
  createdAt: string;
}
