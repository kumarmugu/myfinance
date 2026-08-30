import type { ColumnType } from './types';

/**
 * Convert a raw column value into a plain string for text-based formats (CSV, PDF).
 * - Dates are emitted in a consistent ISO-compatible form.
 * - Numbers/currencies keep full precision (no truncation, no thousands separators
 *   that would break CSV/Excel numeric parsing downstream).
 * - null/undefined become an empty string (consistent empty handling).
 * - Booleans become "Yes"/"No".
 */
export function toDisplayString(value: unknown, type: ColumnType = 'string'): string {
  if (value === null || value === undefined || value === '') return '';

  switch (type) {
    case 'boolean':
      return value ? 'Yes' : 'No';
    case 'date':
      return toIsoDate(value);
    case 'datetime':
      return toIsoDateTime(value);
    case 'number':
    case 'currency': {
      const n = toNumber(value);
      return n === undefined ? String(value) : String(n);
    }
    default:
      return String(value);
  }
}

/** Parse a value into a finite number, or undefined when not numeric. */
export function toNumber(value: unknown): number | undefined {
  if (value === null || value === undefined || value === '') return undefined;
  const n = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(n) ? n : undefined;
}

/** YYYY-MM-DD. Accepts Date, ISO strings, or date-only strings. Returns '' if unparseable. */
export function toIsoDate(value: unknown): string {
  const d = toDate(value);
  if (!d) return typeof value === 'string' ? value : '';
  return d.toISOString().slice(0, 10);
}

/** YYYY-MM-DD HH:mm:ss (UTC). Returns '' if unparseable. */
export function toIsoDateTime(value: unknown): string {
  const d = toDate(value);
  if (!d) return typeof value === 'string' ? value : '';
  return d.toISOString().slice(0, 19).replace('T', ' ');
}

/** Parse to a Date, or undefined. Date-only strings (YYYY-MM-DD) are treated as UTC midnight. */
export function toDate(value: unknown): Date | undefined {
  if (value === null || value === undefined || value === '') return undefined;
  if (value instanceof Date) return isNaN(value.getTime()) ? undefined : value;
  if (typeof value === 'string' || typeof value === 'number') {
    const d = new Date(value);
    return isNaN(d.getTime()) ? undefined : d;
  }
  return undefined;
}

/** Build a dated filename like "transactions_2026-08-31". */
export function buildFilename(entity: string, ext: string, now: Date = new Date()): string {
  const date = now.toISOString().slice(0, 10);
  const safe = entity.replace(/[^a-z0-9-]/gi, '-').toLowerCase();
  return `${safe}_${date}.${ext}`;
}
