import { describe, it, expect } from 'vitest';
import { toDisplayString, toNumber, toIsoDate, toIsoDateTime, toDate, buildFilename } from './format';

describe('export/format', () => {
  describe('toNumber', () => {
    it('parses numbers and numeric strings', () => {
      expect(toNumber(1000)).toBe(1000);
      expect(toNumber('1234.56')).toBe(1234.56);
    });
    it('returns undefined for null/empty/non-numeric', () => {
      expect(toNumber(null)).toBeUndefined();
      expect(toNumber('')).toBeUndefined();
      expect(toNumber('abc')).toBeUndefined();
      expect(toNumber(undefined)).toBeUndefined();
    });
  });

  describe('toIsoDate', () => {
    it('formats a date-only string as YYYY-MM-DD', () => {
      expect(toIsoDate('2026-08-31')).toBe('2026-08-31');
    });
    it('formats an ISO datetime to just the date', () => {
      expect(toIsoDate('2026-08-31T12:34:56Z')).toBe('2026-08-31');
    });
    it('returns empty string for empty input', () => {
      expect(toIsoDate('')).toBe('');
      expect(toIsoDate(null)).toBe('');
    });
  });

  describe('toIsoDateTime', () => {
    it('formats to YYYY-MM-DD HH:mm:ss', () => {
      expect(toIsoDateTime('2026-08-31T12:34:56Z')).toBe('2026-08-31 12:34:56');
    });
  });

  describe('toDate', () => {
    it('parses valid values and rejects invalid', () => {
      expect(toDate('2026-01-01')).toBeInstanceOf(Date);
      expect(toDate('not-a-date')).toBeUndefined();
      expect(toDate(null)).toBeUndefined();
    });
  });

  describe('toDisplayString', () => {
    it('handles null/undefined/empty consistently as empty string', () => {
      expect(toDisplayString(null)).toBe('');
      expect(toDisplayString(undefined)).toBe('');
      expect(toDisplayString('')).toBe('');
    });
    it('renders booleans as Yes/No', () => {
      expect(toDisplayString(true, 'boolean')).toBe('Yes');
      expect(toDisplayString(false, 'boolean')).toBe('No');
    });
    it('keeps full numeric precision without truncation or separators', () => {
      expect(toDisplayString(1000000, 'currency')).toBe('1000000');
      expect(toDisplayString(1234.5678, 'number')).toBe('1234.5678');
    });
    it('preserves unicode strings', () => {
      expect(toDisplayString('café ☕ 日本語', 'string')).toBe('café ☕ 日本語');
    });
  });

  describe('buildFilename', () => {
    it('builds entity_YYYY-MM-DD.ext', () => {
      const d = new Date('2026-08-31T00:00:00Z');
      expect(buildFilename('transactions', 'csv', d)).toBe('transactions_2026-08-31.csv');
      expect(buildFilename('fixed-deposits', 'xlsx', d)).toBe('fixed-deposits_2026-08-31.xlsx');
    });
    it('sanitizes unsafe characters', () => {
      const d = new Date('2026-08-31T00:00:00Z');
      expect(buildFilename('My Table!', 'pdf', d)).toBe('my-table-_2026-08-31.pdf');
    });
  });
});
