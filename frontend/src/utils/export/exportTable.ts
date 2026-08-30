import type { ExportConfig, ExportFormat, ExportOptions } from './types';
import { buildFilename } from './format';
import { buildCsvBlob } from './csv';
import { buildExcelBlob } from './excel';
import { buildPdfBlob } from './pdf';
import { downloadBlob } from './download';

const EXT: Record<ExportFormat, string> = { csv: 'csv', excel: 'xlsx', pdf: 'pdf' };

/**
 * Export the given rows using the table's ExportConfig and trigger a browser download.
 *
 * The rows array is the COMPLETE authorized dataset the page holds (the backend list
 * endpoints already filter by the authenticated user's id — no server-side pagination),
 * so the export represents the full dataset for the current filters/sort, not just the
 * visible/rendered rows. All user-relevant columns from the config are exported, even
 * ones the UI table hides.
 */
export async function exportTable<T>(
  rows: T[],
  config: ExportConfig<T>,
  format: ExportFormat,
  options: ExportOptions = {},
): Promise<void> {
  const filename = buildFilename(config.entity, EXT[format]);

  let blob: Blob;
  switch (format) {
    case 'csv':
      blob = buildCsvBlob(rows, config.columns);
      break;
    case 'excel':
      blob = await buildExcelBlob(rows, config);
      break;
    case 'pdf':
      blob = buildPdfBlob(rows, config, options);
      break;
    default:
      throw new Error(`Unsupported export format: ${format}`);
  }

  downloadBlob(blob, filename);
}
