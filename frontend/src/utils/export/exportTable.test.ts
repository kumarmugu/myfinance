import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// Mock the download side-effect so no real DOM download happens.
const downloadBlob = vi.fn();
vi.mock('./download', () => ({ downloadBlob: (...args: any[]) => downloadBlob(...args) }));

import { exportTable } from './exportTable';
import type { ExportConfig } from './types';

interface Row { id: number; name: string; amount: number; }

const config: ExportConfig<Row> = {
  entity: 'transactions',
  title: 'Transactions',
  columns: [
    { key: 'id', header: 'ID', accessor: r => r.id, type: 'number' },
    { key: 'name', header: 'Name', accessor: r => r.name },
    { key: 'amount', header: 'Amount', accessor: r => r.amount, type: 'currency' },
  ],
};

const rows: Row[] = [
  { id: 1, name: 'Alice', amount: 100 },
  { id: 2, name: 'Bob', amount: 200 },
];

describe('exportTable', () => {
  beforeEach(() => {
    downloadBlob.mockClear();
    // Freeze the clock without fake timers so exceljs's async writeBuffer still resolves.
    vi.spyOn(Date.prototype, 'toISOString').mockReturnValue('2026-08-31T00:00:00.000Z');
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('downloads a CSV with a dated filename', async () => {
    await exportTable(rows, config, 'csv');
    expect(downloadBlob).toHaveBeenCalledTimes(1);
    const [blob, filename] = downloadBlob.mock.calls[0];
    expect(filename).toBe('transactions_2026-08-31.csv');
    expect(blob).toBeInstanceOf(Blob);
  });

  it('downloads an Excel file with .xlsx extension', async () => {
    await exportTable(rows, config, 'excel');
    const [, filename] = downloadBlob.mock.calls[0];
    expect(filename).toBe('transactions_2026-08-31.xlsx');
  });

  it('downloads a PDF with .pdf extension', async () => {
    await exportTable(rows, config, 'pdf');
    const [, filename] = downloadBlob.mock.calls[0];
    expect(filename).toBe('transactions_2026-08-31.pdf');
  });

  it('rejects an unsupported format', async () => {
    await expect(exportTable(rows, config, 'xml' as any)).rejects.toThrow(/Unsupported/);
  });
});
