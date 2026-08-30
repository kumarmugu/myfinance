import type { ExportColumn } from './types';
import { toDisplayString } from './format';

/**
 * Escape a single CSV field per RFC 4180:
 * - Fields containing comma, double-quote, CR or LF are wrapped in double quotes.
 * - Embedded double-quotes are doubled.
 * A leading '=', '+', '-' or '@' is prefixed with a single quote to defuse CSV
 * formula injection when the file is opened in a spreadsheet.
 */
export function escapeCsvField(raw: string): string {
  let value = raw;
  if (/^[=+\-@\t\r]/.test(value)) {
    value = `'${value}`;
  }
  if (/[",\r\n]/.test(value)) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}

/**
 * Build a full CSV document (with header row) from rows + column config.
 * Rows are used with their current order (respecting any UI sort/filter already applied).
 */
export function buildCsv<T>(rows: T[], columns: ExportColumn<T>[]): string {
  const header = columns.map(c => escapeCsvField(c.header)).join(',');
  const lines = rows.map(row =>
    columns
      .map(col => escapeCsvField(toDisplayString(col.accessor(row), col.type)))
      .join(',')
  );
  // CRLF line endings for maximum spreadsheet compatibility.
  return [header, ...lines].join('\r\n');
}

/** Build a CSV Blob including a UTF-8 BOM so Excel detects encoding correctly. */
export function buildCsvBlob<T>(rows: T[], columns: ExportColumn<T>[]): Blob {
  const csv = buildCsv(rows, columns);
  return new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
}
