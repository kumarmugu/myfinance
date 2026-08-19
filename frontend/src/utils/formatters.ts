import type { Currency } from '../types';

const CURRENCY_SYMBOLS: Record<Currency, string> = {
  SGD: 'S$',
  USD: '$',
  EUR: '€',
  LKR: '₨',
};

export function formatCurrency(amount: number, currency: Currency = 'SGD'): string {
  const symbol = CURRENCY_SYMBOLS[currency] || '$';
  const absAmount = Math.abs(amount);
  let formatted: string;

  if (absAmount >= 1000000) {
    formatted = `${symbol}${(absAmount / 1000000).toFixed(2)}M`;
  } else if (absAmount >= 100000) {
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
