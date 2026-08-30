import ExcelJS from 'exceljs';
import type { ExportColumn, ExportConfig } from './types';
import { toDate, toNumber } from './format';

/** Excel worksheet names cannot exceed 31 chars or contain : \ / ? * [ ]. */
function safeSheetName(title: string): string {
  return (title || 'Sheet1').replace(/[:\\/?*[\]]/g, ' ').slice(0, 31) || 'Sheet1';
}

/**
 * Set a cell's value with the correct native Excel type so users can work with the
 * data (sum currencies, sort dates) rather than getting text. The underlying value
 * is preserved exactly — only presentation (numFmt) is applied.
 */
function assignCell(cell: ExcelJS.Cell, value: unknown, type: ExportColumn['type']) {
  if (value === null || value === undefined || value === '') {
    cell.value = null;
    return;
  }
  switch (type) {
    case 'number': {
      const n = toNumber(value);
      cell.value = n === undefined ? String(value) : n;
      break;
    }
    case 'currency': {
      const n = toNumber(value);
      if (n === undefined) { cell.value = String(value); break; }
      cell.value = n;
      cell.numFmt = '#,##0.00';
      break;
    }
    case 'date': {
      const d = toDate(value);
      if (!d) { cell.value = String(value); break; }
      cell.value = d;
      cell.numFmt = 'yyyy-mm-dd';
      break;
    }
    case 'datetime': {
      const d = toDate(value);
      if (!d) { cell.value = String(value); break; }
      cell.value = d;
      cell.numFmt = 'yyyy-mm-dd hh:mm:ss';
      break;
    }
    case 'boolean':
      cell.value = value ? 'Yes' : 'No';
      break;
    default:
      cell.value = String(value);
  }
}

/** Build an .xlsx workbook (as a Blob) from rows + config. */
export async function buildExcelBlob<T>(rows: T[], config: ExportConfig<T>): Promise<Blob> {
  const wb = new ExcelJS.Workbook();
  wb.creator = 'MyFinance';
  wb.created = new Date();
  const ws = wb.addWorksheet(safeSheetName(config.title), {
    views: [{ state: 'frozen', ySplit: 1 }], // freeze the header row
  });

  const columns = config.columns;

  // Header row.
  const headerRow = ws.addRow(columns.map(c => c.header));
  headerRow.font = { bold: true };
  headerRow.alignment = { vertical: 'middle' };

  // Data rows.
  for (const row of rows) {
    const excelRow = ws.addRow(new Array(columns.length).fill(null));
    columns.forEach((col, i) => {
      assignCell(excelRow.getCell(i + 1), col.accessor(row), col.type);
    });
  }

  // Auto-size columns to their content (bounded so a long note doesn't explode width).
  columns.forEach((col, i) => {
    const column = ws.getColumn(i + 1);
    let maxLen = col.header.length;
    column.eachCell({ includeEmpty: false }, cell => {
      const text = cell.value == null ? '' : String(cell.value);
      if (text.length > maxLen) maxLen = text.length;
    });
    column.width = Math.min(Math.max(col.width ?? maxLen + 2, 8), 60);
  });

  const buffer = await wb.xlsx.writeBuffer();
  return new Blob([buffer], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });
}
