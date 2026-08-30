// ─── Reusable table-export type definitions ───
//
// The export system is driven by a per-table ExportConfig. The UI table may show
// only a subset of columns, but the ExportConfig.columns list describes the COMPLETE
// set of user-relevant columns for the underlying data model. Sensitive/internal
// fields (password hashes, tokens, masked account numbers, etc.) must never be added
// to a config.

export type ExportFormat = 'csv' | 'excel' | 'pdf';

/** Logical type of a column value. Drives Excel cell typing and formatting. */
export type ColumnType = 'string' | 'number' | 'currency' | 'date' | 'datetime' | 'boolean';

export interface ExportColumn<T = any> {
  /** Stable key (also used as a fallback header). */
  key: string;
  /** Human-readable column header shown in the exported file. */
  header: string;
  /**
   * Extract the raw value for this column from a row. Returning the RAW value
   * (number, Date-like string, boolean) — not a pre-formatted string — lets Excel
   * keep numbers numeric and dates date-compatible.
   */
  accessor: (row: T) => unknown;
  /** Logical type. Defaults to 'string'. */
  type?: ColumnType;
  /**
   * For 'currency' columns: an accessor returning the ISO currency code of the value
   * on that row (original currency — never overwritten). Optional; when present the
   * PDF/CSV output can annotate the amount and Excel can apply a code-aware format.
   */
  currencyAccessor?: (row: T) => string | undefined;
  /** Suggested column width (characters) for Excel; auto-sized when omitted. */
  width?: number;
}

export interface ExportConfig<T = any> {
  /** Machine name of the entity/table, e.g. "transactions". Used for the filename. */
  entity: string;
  /** Human title used as the PDF report title and Excel worksheet name. */
  title: string;
  /** Complete ordered list of user-relevant columns. */
  columns: ExportColumn<T>[];
}

export interface ExportOptions {
  /**
   * Extra context appended below the PDF title (e.g. active filters). Purely
   * cosmetic — never affects which rows are exported.
   */
  subtitle?: string;
}
