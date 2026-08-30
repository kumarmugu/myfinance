import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import type { ExportColumn, ExportConfig, ExportOptions } from './types';
import { toDisplayString } from './format';

/** Wide tables (> this many columns) are rendered in landscape for readability. */
const LANDSCAPE_COLUMN_THRESHOLD = 6;

/** Build a PDF (as a Blob) from rows + config. */
export function buildPdfBlob<T>(
  rows: T[],
  config: ExportConfig<T>,
  options: ExportOptions = {},
  now: Date = new Date(),
): Blob {
  const columns = config.columns;
  const orientation = columns.length > LANDSCAPE_COLUMN_THRESHOLD ? 'landscape' : 'portrait';

  const doc = new jsPDF({ orientation, unit: 'pt', format: 'a4' });
  const pageWidth = doc.internal.pageSize.getWidth();
  const generated = now.toISOString().slice(0, 19).replace('T', ' ') + ' UTC';

  const head = [columns.map(c => c.header)];
  const body = rows.map(row =>
    columns.map(col => toDisplayString(col.accessor(row), col.type)),
  );

  autoTable(doc, {
    head,
    body,
    startY: options.subtitle ? 78 : 64,
    margin: { top: 56, left: 32, right: 32, bottom: 40 },
    styles: { fontSize: 8, cellPadding: 3, overflow: 'linebreak' },
    headStyles: { fillColor: [79, 70, 229], textColor: 255, fontStyle: 'bold' },
    // Repeat the header row on every page.
    showHead: 'everyPage',
    // Right-align numeric/currency columns for readability.
    columnStyles: columns.reduce((acc, col, i) => {
      if (col.type === 'number' || col.type === 'currency') {
        acc[i] = { halign: 'right' };
      }
      return acc;
    }, {} as Record<number, { halign: 'right' }>),
    didDrawPage: (data) => {
      // Title.
      doc.setFontSize(14);
      doc.setTextColor(30);
      doc.text(config.title, 32, 36);
      // Subtitle (e.g. active filters) + generated timestamp.
      doc.setFontSize(9);
      doc.setTextColor(120);
      if (options.subtitle) doc.text(options.subtitle, 32, 52);
      doc.text(`Generated: ${generated}`, pageWidth - 32, 36, { align: 'right' });

      // Page number footer.
      const pageCount = doc.getNumberOfPages();
      const pageHeight = doc.internal.pageSize.getHeight();
      doc.setFontSize(8);
      doc.setTextColor(150);
      doc.text(
        `Page ${data.pageNumber} of ${pageCount}`,
        pageWidth - 32,
        pageHeight - 20,
        { align: 'right' },
      );
    },
  });

  // Fix the "of N" total now that all pages exist.
  const total = doc.getNumberOfPages();
  const pageHeight = doc.internal.pageSize.getHeight();
  for (let i = 1; i <= total; i++) {
    doc.setPage(i);
    doc.setFontSize(8);
    doc.setTextColor(150);
    doc.text(`Page ${i} of ${total}`, pageWidth - 32, pageHeight - 20, { align: 'right' });
  }

  return doc.output('blob');
}

// Re-export for callers that only need the column type.
export type { ExportColumn };
