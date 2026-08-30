// Symbols for the common currencies; any other currency falls back to its code
// (e.g. "INR 1,000") so we never show a misleading wrong symbol.
const CURRENCY_SYMBOLS: Record<string, string> = {
  SGD: 'S$',
  USD: 'US$',
  EUR: '€',
  LKR: '₨',
  GBP: '£',
  INR: '₹',
  JPY: '¥',
  CNY: '¥',
  AUD: 'A$',
  HKD: 'HK$',
  NZD: 'NZ$',
  CAD: 'C$',
  CHF: 'CHF ',
  MYR: 'RM',
  THB: '฿',
};

/**
 * Format a monetary amount, standardised across the whole app as a full number with
 * comma thousands separators and exactly two decimals, e.g. "S$1,000,000.00".
 * No K/M abbreviation. The (now legacy) `opts` argument is accepted but ignored so
 * existing callers keep working.
 */
export function formatCurrency(amount: number, currency: string = 'SGD', _opts?: { exact?: boolean }): string {
  const code = (currency || 'SGD').toUpperCase();
  // Known symbol, else prefix with the ISO code so the currency is never ambiguous.
  const symbol = CURRENCY_SYMBOLS[code] || `${code} `;
  const value = Number.isFinite(amount) ? amount : 0;
  const formatted = new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Math.abs(value));
  return `${value < 0 ? '-' : ''}${symbol}${formatted}`;
}

export function formatNumber(amount: number, decimals = 2): string {
  return new Intl.NumberFormat('en-SG', {
    minimumFractionDigits: 0,
    maximumFractionDigits: decimals,
  }).format(amount);
}

export function formatPercent(value: number): string {
  const sign = value >= 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}%`;
}

export function formatDate(dateStr: string): string {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleDateString('en-SG', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function daysBetween(dateStr: string): number {
  const target = new Date(dateStr);
  const now = new Date();
  return Math.ceil((target.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
}
