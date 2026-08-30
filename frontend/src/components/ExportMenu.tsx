import { useEffect, useRef, useState, useCallback } from 'react';
import { Download, FileText, FileSpreadsheet, FileType, ChevronDown, Loader2 } from 'lucide-react';
import { exportTable } from '../utils/export';
import type { ExportConfig, ExportFormat, ExportOptions } from '../utils/export';
import { useToast } from '../contexts/ToastContext';

interface ExportMenuProps<T> {
  /** Complete authorized dataset to export (already filtered/sorted as shown). */
  rows: T[];
  /** Column + metadata configuration for this table. */
  config: ExportConfig<T>;
  /** Optional PDF subtitle, e.g. a summary of active filters. */
  subtitle?: string;
  /** Disable the control (e.g. while the page is still loading). */
  disabled?: boolean;
}

const FORMATS: { key: ExportFormat; label: string; icon: typeof FileText }[] = [
  { key: 'csv', label: 'CSV', icon: FileText },
  { key: 'excel', label: 'Excel', icon: FileSpreadsheet },
  { key: 'pdf', label: 'PDF', icon: FileType },
];

/**
 * Accessible Export dropdown: a button that opens a menu of CSV / Excel / PDF options.
 * Exports the full provided dataset (all configured columns) client-side.
 */
export default function ExportMenu<T>({ rows, config, subtitle, disabled }: ExportMenuProps<T>) {
  const { showToast } = useToast();
  const [open, setOpen] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const ref = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);

  const empty = !rows || rows.length === 0;
  const isDisabled = disabled || empty || exporting;

  // Close on outside click.
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // Move focus to the active menu item when navigating.
  useEffect(() => {
    if (open) itemRefs.current[activeIndex]?.focus();
  }, [open, activeIndex]);

  const handleExport = useCallback(async (format: ExportFormat) => {
    if (exporting) return; // Guard against duplicate concurrent exports.
    setOpen(false);
    setExporting(true);
    const options: ExportOptions = subtitle ? { subtitle } : {};
    try {
      await exportTable(rows, config, format, options);
      showToast('Your export is ready.', 'success');
    } catch (err) {
      console.error('Export failed', err);
      showToast('Unable to export the data. Please try again.', 'error');
    } finally {
      setExporting(false);
      buttonRef.current?.focus();
    }
  }, [exporting, rows, config, subtitle, showToast]);

  const onButtonKeyDown = (e: React.KeyboardEvent) => {
    if (isDisabled) return;
    if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      setActiveIndex(0);
      setOpen(true);
    }
  };

  const onMenuKeyDown = (e: React.KeyboardEvent) => {
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setActiveIndex(i => (i + 1) % FORMATS.length);
        break;
      case 'ArrowUp':
        e.preventDefault();
        setActiveIndex(i => (i - 1 + FORMATS.length) % FORMATS.length);
        break;
      case 'Escape':
        e.preventDefault();
        setOpen(false);
        buttonRef.current?.focus();
        break;
      case 'Tab':
        setOpen(false);
        break;
      case 'Home':
        e.preventDefault();
        setActiveIndex(0);
        break;
      case 'End':
        e.preventDefault();
        setActiveIndex(FORMATS.length - 1);
        break;
    }
  };

  return (
    <div ref={ref} className="relative">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => !isDisabled && setOpen(o => !o)}
        onKeyDown={onButtonKeyDown}
        disabled={isDisabled}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={empty ? 'Export (no data available)' : 'Export table'}
        title={empty ? 'Nothing to export yet' : 'Export table'}
        className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${
          isDisabled
            ? 'bg-slate-100 text-slate-400 border-slate-200 cursor-not-allowed'
            : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50 hover:border-slate-400'
        }`}
      >
        {exporting ? <Loader2 size={16} className="animate-spin" /> : <Download size={16} />}
        {exporting ? 'Exporting...' : 'Export'}
        <ChevronDown size={14} className={`transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && !isDisabled && (
        <div
          role="menu"
          aria-label="Export format"
          onKeyDown={onMenuKeyDown}
          className="absolute right-0 z-50 mt-1 w-40 bg-white border border-slate-200 rounded-lg shadow-lg py-1"
        >
          {FORMATS.map((f, i) => {
            const Icon = f.icon;
            return (
              <button
                key={f.key}
                ref={el => { itemRefs.current[i] = el; }}
                role="menuitem"
                type="button"
                tabIndex={activeIndex === i ? 0 : -1}
                onClick={() => handleExport(f.key)}
                className={`w-full flex items-center gap-2.5 px-3 py-2 text-sm text-left transition-colors ${
                  activeIndex === i ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700 hover:bg-slate-50'
                }`}
              >
                <Icon size={15} className="text-slate-500" />
                {f.label}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
