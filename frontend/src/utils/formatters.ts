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
 * Compact abbreviation of a number using K (thousand), M (million), B (billion),
 * T (trillion) with one decimal, e.g. 150000 -> "150K", 1200000 -> "1.2M",
 * 2300000000 -> "2.3B". Returns null when the value isn't "high" enough to be
 * worth abbreviating (|value| < 100,000).
 */
export function abbreviateNumber(value: number): string | null {
  const abs = Math.abs(value);
  if (!Number.isFinite(value) || abs < 100_000) return null;
  const units: Array<[number, string]> = [
    [1_000_000_000_000, 'T'],
    [1_000_000_000, 'B'],
    [1_000_000, 'M'],
    [1_000, 'K'],
  ];
  for (const [threshold, suffix] of units) {
    if (abs >= threshold) {
      const scaled = value / threshold;
      // One decimal, but drop a trailing ".0" (e.g. 150.0K -> 150K).
      const text = (Math.round(scaled * 10) / 10).toString();
      return `${text}${suffix}`;
    }
  }
  return null;
}

/**
 * Format a monetary amount, standardised across the whole app as a full number with
 * comma thousands separators and exactly two decimals, e.g. "S$1,000,000.00".
 * For high values (|amount| >= 100,000) a compact abbreviation is appended in
 * brackets, e.g. "S$1,200,000.00 (1.2M)". Pass `{ exact: true }` to suppress the
 * abbreviation (useful for inputs/edit fields).
 */
export function formatCurrency(amount: number, currency: string = 'SGD', opts?: { exact?: boolean }): string {
  const code = (currency || 'SGD').toUpperCase();
  // Known symbol, else prefix with the ISO code so the currency is never ambiguous.
  const symbol = CURRENCY_SYMBOLS[code] || `${code} `;
  const value = Number.isFinite(amount) ? amount : 0;
  const formatted = new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Math.abs(value));
  const base = `${value < 0 ? '-' : ''}${symbol}${formatted}`;
  if (opts?.exact) return base;
  const abbr = abbreviateNumber(value);
  return abbr ? `${base} (${abbr})` : base;
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
