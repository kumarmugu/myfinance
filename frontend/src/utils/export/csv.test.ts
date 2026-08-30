import { describe, it, expect } from 'vitest';
import { escapeCsvField, buildCsv, buildCsvBlob } from './csv';
import type { ExportColumn } from './types';

interface Row { name: string; amount: number; currency: string; note: string | null; date: string; }

const columns: ExportColumn<Row>[] = [
  { key: 'name', header: 'Name', accessor: r => r.name },
  { key: 'amount', header: 'Amount', accessor: r => r.amount, type: 'currency' },
  { key: 'currency', header: 'Currency', accessor: r => r.currency },
  { key: 'note', header: 'Note', accessor: r => r.note },
  { key: 'date', header: 'Date', accessor: r => r.date, type: 'date' },
];

describe('export/csv', () => {
  describe('escapeCsvField', () => {
    it('leaves plain values unquoted', () => {
      expect(escapeCsvField('hello')).toBe('hello');
    });
    it('quotes fields containing commas', () => {
      expect(escapeCsvField('a,b')).toBe('"a,b"');
    });
    it('escapes and quotes embedded double-quotes', () => {
      expect(escapeCsvField('she said "hi"')).toBe('"she said ""hi"""');
    });
    it('quotes fields containing newlines', () => {
      expect(escapeCsvField('line1\nline2')).toBe('"line1\nline2"');
      expect(escapeCsvField('line1\r\nline2')).toBe('"line1\r\nline2"');
    });
    it('defuses formula injection with a leading quote', () => {
      expect(escapeCsvField('=1+1')).toBe("'=1+1");
      expect(escapeCsvField('+cmd')).toBe("'+cmd");
      expect(escapeCsvField('-2')).toBe("'-2");
      expect(escapeCsvField('@x')).toBe("'@x");
    });
  });

  describe('buildCsv', () => {
    const rows: Row[] = [
      { name: 'Alice', amount: 1000000, currency: 'LKR', note: 'big, value', date: '2026-08-31' },
      { name: 'Bo "B" Jo', amount: 42.5, currency: 'USD', note: null, date: '2026-01-01T10:00:00Z' },
      { name: 'café ☕', amount: 0, currency: 'EUR', note: 'multi\nline', date: '2025-12-25' },
    ];

    it('includes a header row', () => {
      const csv = buildCsv(rows, columns);
      expect(csv.split('\r\n')[0]).toBe('Name,Amount,Currency,Note,Date');
    });

    it('preserves original currency amounts without conversion or truncation', () => {
      const csv = buildCsv(rows, columns);
      // 1,000,000 LKR stays exactly 1000000 with currency LKR — never converted to SGD.
      expect(csv).toContain('1000000,LKR');
    });

    it('escapes commas, quotes, and newlines correctly', () => {
      const lines = buildCsv(rows, columns).split('\r\n');
      expect(lines[1]).toContain('"big, value"');
      expect(lines[2]).toContain('"Bo ""B"" Jo"');
      expect(buildCsv(rows, columns)).toContain('"multi\nline"');
    });

    it('handles null values as empty fields', () => {
      const lines = buildCsv(rows, columns).split('\r\n');
      // Row 2 has null note between currency and date -> ,,
      expect(lines[2]).toContain('USD,,');
    });

    it('supports unicode content', () => {
      expect(buildCsv(rows, columns)).toContain('café ☕');
    });

    it('formats dates consistently as YYYY-MM-DD', () => {
      const csv = buildCsv(rows, columns);
      expect(csv).toContain('2026-08-31');
      expect(csv).toContain('2026-01-01'); // from the ISO datetime
    });

    it('handles a large dataset without truncation', () => {
      const big: Row[] = Array.from({ length: 5000 }, (_, i) => ({
        name: `row${i}`, amount: i, currency: 'SGD', note: null, date: '2026-01-01',
      }));
      const csv = buildCsv(big, columns);
      // 1 header + 5000 data rows
      expect(csv.split('\r\n').length).toBe(5001);
      expect(csv).toContain('row4999');
    });
  });

  describe('buildCsvBlob', () => {
    it('prepends a UTF-8 BOM', async () => {
      const blob = buildCsvBlob([{ name: 'x', amount: 1, currency: 'SGD', note: '', date: '2026-01-01' }], columns);
      const text = await blob.text();
      expect(text.charCodeAt(0)).toBe(0xfeff);
      expect(blob.type).toContain('text/csv');
    });
  });
});
