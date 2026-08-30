import { describe, it, expect } from 'vitest';
import ExcelJS from 'exceljs';
import { buildExcelBlob } from './excel';
import type { ExportConfig } from './types';

interface Row { id: number; name: string; amount: number; date: string; active: boolean; note: string | null; }

const config: ExportConfig<Row> = {
  entity: 'sample',
  title: 'Sample Worksheet',
  columns: [
    { key: 'id', header: 'ID', accessor: r => r.id, type: 'number' },
    { key: 'name', header: 'Name', accessor: r => r.name },
    { key: 'amount', header: 'Amount', accessor: r => r.amount, type: 'currency' },
    { key: 'date', header: 'Date', accessor: r => r.date, type: 'date' },
    { key: 'active', header: 'Active', accessor: r => r.active, type: 'boolean' },
    { key: 'note', header: 'Note', accessor: r => r.note },
  ],
};

const rows: Row[] = [
  { id: 1, name: 'café ☕', amount: 1000000.55, date: '2026-08-31', active: true, note: null },
  { id: 2, name: 'Bob', amount: 42, date: '2026-01-01', active: false, note: 'hello' },
];

async function loadWorkbook(blob: Blob): Promise<ExcelJS.Workbook> {
  const buffer = await blob.arrayBuffer();
  const wb = new ExcelJS.Workbook();
  await wb.xlsx.load(buffer);
  return wb;
}

describe('export/excel', () => {
  it('creates a worksheet with a readable name', async () => {
    const wb = await loadWorkbook(await buildExcelBlob(rows, config));
    expect(wb.worksheets.length).toBe(1);
    expect(wb.worksheets[0].name).toBe('Sample Worksheet');
  });

  it('writes all columns as headers', async () => {
    const wb = await loadWorkbook(await buildExcelBlob(rows, config));
    const header = wb.worksheets[0].getRow(1);
    expect(header.getCell(1).value).toBe('ID');
    expect(header.getCell(2).value).toBe('Name');
    expect(header.getCell(3).value).toBe('Amount');
    expect(header.getCell(6).value).toBe('Note');
  });

  it('keeps numeric and currency values numeric', async () => {
    const wb = await loadWorkbook(await buildExcelBlob(rows, config));
    const ws = wb.worksheets[0];
    expect(ws.getRow(2).getCell(1).value).toBe(1); // id
    expect(ws.getRow(2).getCell(3).value).toBe(1000000.55); // currency stays a number
    expect(typeof ws.getRow(2).getCell(3).value).toBe('number');
  });

  it('keeps dates as Date objects (date-compatible)', async () => {
    const wb = await loadWorkbook(await buildExcelBlob(rows, config));
    const cell = wb.worksheets[0].getRow(2).getCell(4);
    expect(cell.value).toBeInstanceOf(Date);
  });

  it('renders booleans as Yes/No and null as empty', async () => {
    const wb = await loadWorkbook(await buildExcelBlob(rows, config));
    const ws = wb.worksheets[0];
    expect(ws.getRow(2).getCell(5).value).toBe('Yes');
    expect(ws.getRow(3).getCell(5).value).toBe('No');
    expect(ws.getRow(2).getCell(6).value).toBeNull();
  });

  it('preserves unicode', async () => {
    const wb = await loadWorkbook(await buildExcelBlob(rows, config));
    expect(wb.worksheets[0].getRow(2).getCell(2).value).toBe('café ☕');
  });

  it('freezes the header row', async () => {
    const wb = await loadWorkbook(await buildExcelBlob(rows, config));
    const view = wb.worksheets[0].views[0] as any;
    expect(view.state).toBe('frozen');
    expect(view.ySplit).toBe(1);
  });

  it('handles a large dataset', async () => {
    const big: Row[] = Array.from({ length: 3000 }, (_, i) => ({
      id: i, name: `n${i}`, amount: i * 1.5, date: '2026-01-01', active: i % 2 === 0, note: null,
    }));
    const wb = await loadWorkbook(await buildExcelBlob(big, config));
    // header + 3000 rows
    expect(wb.worksheets[0].rowCount).toBe(3001);
  });
});
