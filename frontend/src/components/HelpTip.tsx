import { useEffect, useId, useRef, useState } from 'react';
import { HelpCircle } from 'lucide-react';

/**
 * A small, accessible contextual-help control: a "?" button that reveals a short
 * explanation on click. Purely additive — drop it next to a label/field to explain
 * a concept without cluttering the screen.
 *
 * Accessibility:
 * - Rendered as a real <button> (keyboard focusable, Enter/Space activate).
 * - aria-expanded reflects open state; the popover is linked via aria-controls.
 * - Escape closes; clicking outside closes; focus stays manageable.
 */
export default function HelpTip({
  text,
  label = 'More information',
  className = '',
}: {
  /** The help text to show. Keep it short and plain-language. */
  text: string;
  /** Accessible label for the button (screen readers). */
  label?: string;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLSpanElement>(null);
  const popId = useId();

  useEffect(() => {
    if (!open) return;
    const onDocClick = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDocClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <span ref={wrapRef} className={`relative inline-flex align-middle ${className}`}>
      <button
        type="button"
        aria-label={label}
        aria-expanded={open}
        aria-controls={open ? popId : undefined}
        onClick={() => setOpen((o) => !o)}
        className="text-slate-400 hover:text-indigo-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 rounded-full"
      >
        <HelpCircle size={14} />
      </button>
      {open && (
        <span
          id={popId}
          role="tooltip"
          className="absolute z-50 left-1/2 -translate-x-1/2 top-6 w-64 rounded-lg bg-slate-800 text-white text-xs leading-relaxed p-3 shadow-lg"
        >
          {text}
        </span>
      )}
    </span>
  );
}
