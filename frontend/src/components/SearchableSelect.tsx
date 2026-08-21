import { useEffect, useRef, useState } from 'react';
import { ChevronDown, Search, X } from 'lucide-react';

export interface SelectOption {
  value: string | number;
  label: string;
  icon?: string; // single character for avatar circle
}

interface SearchableSelectProps {
  options: SelectOption[];
  value: string | number;
  onChange: (v: any) => void;
  placeholder?: string;
  disabled?: boolean;
}

/**
 * A searchable dropdown component with a filter box.
 * Matches the design used in the Add Transaction form's asset selector.
 */
export default function SearchableSelect({ options, value, onChange, placeholder = 'Select...', disabled = false }: SearchableSelectProps) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const filtered = options.filter(o => o.label.toLowerCase().includes(search.toLowerCase()));
  const selected = options.find(o => o.value === value);

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => !disabled && setOpen(!open)}
        disabled={disabled}
        className={`w-full flex items-center justify-between border border-slate-300 rounded-lg px-3 py-2 text-sm text-left focus:ring-2 focus:ring-indigo-500 bg-white ${disabled ? 'opacity-60 cursor-not-allowed' : 'hover:border-slate-400'}`}
      >
        <span className={`flex items-center gap-2 ${selected ? 'text-slate-800' : 'text-slate-400'} truncate`}>
          {selected?.icon && <span className="w-5 h-5 rounded-full bg-gradient-to-br from-indigo-400 to-indigo-600 flex items-center justify-center text-white text-[10px] font-bold shrink-0">{selected.icon}</span>}
          {selected ? selected.label : placeholder}
        </span>
        <ChevronDown size={14} className={`text-slate-400 transition-transform shrink-0 ml-1 ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && !disabled && (
        <div className="absolute z-50 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg max-h-60 overflow-hidden">
          <div className="p-2 border-b border-slate-100">
            <div className="relative">
              <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <input type="text" value={search} onChange={e => setSearch(e.target.value)} placeholder="Search..." className="w-full pl-8 pr-3 py-1.5 text-sm border border-slate-200 rounded-md focus:ring-1 focus:ring-indigo-500" autoFocus />
              {search && <button type="button" onClick={() => setSearch('')} className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400"><X size={12} /></button>}
            </div>
          </div>
          <div className="max-h-48 overflow-y-auto">
            {filtered.map(o => (
              <button key={o.value} type="button" onClick={() => { onChange(o.value); setOpen(false); setSearch(''); }} className={`w-full text-left px-3 py-2 text-sm hover:bg-indigo-50 transition-colors flex items-center gap-2 ${o.value === value ? 'bg-indigo-50 text-indigo-700 font-medium' : 'text-slate-700'}`}>
                {o.icon && <span className="w-5 h-5 rounded-full bg-gradient-to-br from-indigo-400 to-indigo-600 flex items-center justify-center text-white text-[10px] font-bold shrink-0">{o.icon}</span>}
                {o.label}
              </button>
            ))}
            {filtered.length === 0 && <p className="px-3 py-2 text-sm text-slate-400">No results</p>}
          </div>
        </div>
      )}
    </div>
  );
}
