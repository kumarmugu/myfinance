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
 * Format a monetary amount.
 * By default large values are abbreviated (K/M) for compact cards/charts.
 * Pass { exact: true } to render the full value with thousands separators and no
 * abbreviation — use this for tables where precision matters (e.g. salary records).
 */
export function formatCurrency(amount: number, currency: string = 'SGD', opts?: { exact?: boolean }): string {
  const code = (currency || 'SGD').toUpperCase();
  // Known symbol, else prefix with the ISO code so the currency is never ambiguous.
  const symbol = CURRENCY_SYMBOLS[code] || `${code} `;
  const absAmount = Math.abs(amount);
  let formatted: string;

  if (!opts?.exact && absAmount >= 1000000) {
    formatted = `${symbol}${(absAmount / 1000000).toFixed(2)}M`;
  } else if (!opts?.exact && absAmount >= 100000) {
    formatted = `${symbol}${(absAmount / 1000).toFixed(0)}K`;
  } else {
    formatted = new Intl.NumberFormat('en-SG', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 2,
    }).format(absAmount);
    formatted = `${symbol}${formatted}`;
  }

  return amount < 0 ? `-${formatted}` : formatted;
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
