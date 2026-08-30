import { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, Home, MapPin, Building2, Store, LandPlot, Tractor, Wheat, type LucideIcon } from 'lucide-react';
import api, { getOwners } from '../api';
import SearchableSelect from '../components/SearchableSelect';
import { formatCurrency } from '../utils/formatters';
import { useToast } from '../contexts/ToastContext';
import type { Owner } from '../types';

interface Property {
  id: number;
  propertyName: string;
  propertyType: string;
  address: string;
  country: string;
  purchasePrice: number;
  currentValue: number;
  outstandingLoan: number;
  currency: string;
  purchaseDate: string;
  tenure: string;
  areaSize: number;
  areaUnit: string;
  ownership: string;
  includeInNetWorth: boolean;
  status: string;
  monthlyRental: number;
  notes: string;
  owner?: Owner | null;
}

const PROPERTY_TYPES = ['HDB', 'CONDO', 'LANDED', 'COMMERCIAL', 'LAND', 'AGRICULTURE', 'PADDY_FIELD'];

// Map each property type to a fitting icon + accent colors.
const PROPERTY_TYPE_STYLES: Record<string, { icon: LucideIcon; label: string; bg: string; fg: string }> = {
  HDB:         { icon: Building2, label: 'HDB',          bg: 'bg-indigo-100', fg: 'text-indigo-600' },
  CONDO:       { icon: Building2, label: 'Condo',        bg: 'bg-indigo-100', fg: 'text-indigo-600' },
  LANDED:      { icon: Home,      label: 'Landed',       bg: 'bg-indigo-100', fg: 'text-indigo-600' },
  COMMERCIAL:  { icon: Store,     label: 'Commercial',   bg: 'bg-purple-100', fg: 'text-purple-600' },
  LAND:        { icon: LandPlot,  label: 'Land',         bg: 'bg-amber-100',  fg: 'text-amber-600' },
  AGRICULTURE: { icon: Tractor,   label: 'Agriculture',  bg: 'bg-green-100',  fg: 'text-green-600' },
  PADDY_FIELD: { icon: Wheat,     label: 'Paddy Field',  bg: 'bg-lime-100',   fg: 'text-lime-600' },
};

const DEFAULT_TYPE_STYLE = { icon: Home, label: 'Property', bg: 'bg-slate-100', fg: 'text-slate-600' };

function typeStyle(propertyType?: string) {
  return (propertyType && PROPERTY_TYPE_STYLES[propertyType]) || DEFAULT_TYPE_STYLE;
}

export default function Properties() {
  const [properties, setProperties] = useState<Property[]>([]);
  const [owners, setOwners] = useState<Owner[]>([]);
  const [filterOwner, setFilterOwner] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Property | null>(null);
  const { showToast } = useToast();
  const [form, setForm] = useState({
    propertyName: '', propertyType: 'HDB', address: '', country: 'Singapore',
    purchasePrice: 0, currentValue: 0, outstandingLoan: 0, currency: 'SGD',
    purchaseDate: '', tenure: '', areaSize: 0, areaUnit: 'sqft',
    ownership: 'SOLE', includeInNetWorth: true, status: 'OWNED',
    monthlyRental: 0, notes: '', ownerId: 0
  });

  useEffect(() => { getOwners().then(r => setOwners(r.data)).catch(console.error); }, []);
  useEffect(() => { loadData(); }, [filterOwner]);

  const loadData = async () => {
    try {
      const ownerId = filterOwner ? Number(filterOwner) : undefined;
      setProperties((await api.get('/properties', { params: { ownerId } })).data);
    }
    catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const resetForm = () => setForm({ propertyName: '', propertyType: 'HDB', address: '', country: 'Singapore', purchasePrice: 0, currentValue: 0, outstandingLoan: 0, currency: 'SGD', purchaseDate: '', tenure: '', areaSize: 0, areaUnit: 'sqft', ownership: 'SOLE', includeInNetWorth: true, status: 'OWNED', monthlyRental: 0, notes: '', ownerId: 0 });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = { ...form, owner: form.ownerId ? { id: form.ownerId } : null };
      if (editing) { await api.put(`/properties/${editing.id}`, payload); }
      else { await api.post('/properties', payload); }
      setShowForm(false); setEditing(null); resetForm(); loadData();
      showToast(editing ? 'Property updated' : 'Property added', 'success');
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to save property');
    }
  };

  const startEdit = (p: Property) => {
    setEditing(p);
    setForm({
      propertyName: p.propertyName, propertyType: p.propertyType || 'HDB',
      address: p.address || '', country: p.country || 'Singapore',
      purchasePrice: p.purchasePrice || 0, currentValue: p.currentValue || 0,
      outstandingLoan: p.outstandingLoan || 0, currency: p.currency || 'SGD',
      purchaseDate: p.purchaseDate || '', tenure: p.tenure || '',
      areaSize: p.areaSize || 0, areaUnit: p.areaUnit || 'sqft',
      ownership: p.ownership || 'SOLE', includeInNetWorth: p.includeInNetWorth,
      status: p.status || 'OWNED', monthlyRental: p.monthlyRental || 0,
      notes: p.notes || '', ownerId: p.owner?.id || 0
    });
    setShowForm(true);
  };

  const handleDelete = async (id: number) => {
    if (confirm('Delete this property?')) { await api.delete(`/properties/${id}`); loadData(); }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const owned = properties.filter(p => p.status === 'OWNED');
  const totalValue = owned.reduce((s, p) => s + (p.currentValue || 0), 0);
  const totalLoan = owned.reduce((s, p) => s + (p.outstandingLoan || 0), 0);
  const totalEquity = totalValue - totalLoan;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Real Estate</h1><p className="text-slate-500 text-sm mt-0.5">Manage your property portfolio</p></div>
        <div className="flex items-center gap-3">
          <div className="w-44">
            <SearchableSelect
              options={[{ value: '', label: 'All Owners' }, ...owners.map(o => ({ value: o.id.toString(), label: o.name, icon: o.name[0] }))]}
              value={filterOwner}
              onChange={v => setFilterOwner(v.toString())}
              placeholder="All Owners"
            />
          </div>
          <button onClick={() => { setShowForm(!showForm); setEditing(null); resetForm(); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700"><Plus size={16} /> Add Property</button>
        </div>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Properties</p><p className="text-lg font-bold text-slate-800 mt-1">{owned.length}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Total Value</p><p className="text-lg font-bold text-green-600 mt-1">{formatCurrency(totalValue)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Outstanding Loan</p><p className="text-lg font-bold text-red-600 mt-1">{formatCurrency(totalLoan)}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Equity</p><p className="text-lg font-bold text-indigo-600 mt-1">{formatCurrency(totalEquity)}</p></div>
      </div>

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editing ? 'Edit Property' : 'Add Property'}</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Owner</label>
              <SearchableSelect options={[{ value: 0, label: 'Unassigned' }, ...owners.map(o => ({ value: o.id, label: o.name }))]} value={form.ownerId} onChange={v => setForm({...form, ownerId: Number(v)})} placeholder="Select owner..." /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Property Name *</label>
              <input type="text" value={form.propertyName} onChange={e => setForm({...form, propertyName: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required placeholder="e.g. HDB Woodlands" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Type</label>
              <div className="flex flex-wrap gap-1">
                {PROPERTY_TYPES.map(t => (
                  <button key={t} type="button" onClick={() => setForm({...form, propertyType: t})}
                    className={`px-2.5 py-1 rounded-lg text-[11px] font-medium border ${form.propertyType === t ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-300'}`}>{typeStyle(t).label}</button>
                ))}
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Address</label>
              <input type="text" value={form.address} onChange={e => setForm({...form, address: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Country</label>
              <select value={form.country} onChange={e => setForm({...form, country: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm">
                <option>Singapore</option><option>Sri Lanka</option><option>Malaysia</option><option>Other</option>
              </select></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Purchase Price</label>
              <input type="number" step="any" value={form.purchasePrice || ''} onChange={e => setForm({...form, purchasePrice: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Current Value *</label>
              <input type="number" step="any" value={form.currentValue || ''} onChange={e => setForm({...form, currentValue: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Outstanding Loan</label>
              <input type="number" step="any" value={form.outstandingLoan || ''} onChange={e => setForm({...form, outstandingLoan: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Currency</label>
              <select value={form.currency} onChange={e => setForm({...form, currency: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm">
                <option>SGD</option><option>USD</option><option>LKR</option><option>MYR</option>
              </select></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Purchase Date</label>
              <input type="date" value={form.purchaseDate} onChange={e => setForm({...form, purchaseDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Tenure</label>
              <input type="text" value={form.tenure} onChange={e => setForm({...form, tenure: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. 99 years, Freehold" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Area</label>
              <div className="flex gap-2">
                <input type="number" step="any" value={form.areaSize || ''} onChange={e => setForm({...form, areaSize: parseFloat(e.target.value) || 0})} className="flex-1 border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Size" />
                <select value={form.areaUnit} onChange={e => setForm({...form, areaUnit: e.target.value})} className="w-20 border border-slate-300 rounded-lg px-2 py-2 text-sm">
                  <option>sqft</option><option>sqm</option>
                </select>
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Ownership</label>
              <div className="flex rounded-lg overflow-hidden border border-slate-300">
                <button type="button" onClick={() => setForm({...form, ownership: 'SOLE'})} className={`flex-1 py-2 text-xs font-medium ${form.ownership === 'SOLE' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600'}`}>Sole</button>
                <button type="button" onClick={() => setForm({...form, ownership: 'JOINT'})} className={`flex-1 py-2 text-xs font-medium ${form.ownership === 'JOINT' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600'}`}>Joint</button>
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Status</label>
              <div className="flex rounded-lg overflow-hidden border border-slate-300">
                {['OWNED', 'SOLD', 'RENTED_OUT'].map(s => (
                  <button key={s} type="button" onClick={() => setForm({...form, status: s})} className={`flex-1 py-2 text-[11px] font-medium ${form.status === s ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600'}`}>{s.replace('_', ' ')}</button>
                ))}
              </div></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Monthly Rental</label>
              <input type="number" step="any" value={form.monthlyRental || ''} onChange={e => setForm({...form, monthlyRental: parseFloat(e.target.value) || 0})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="If rented out" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Notes</label>
              <input type="text" value={form.notes} onChange={e => setForm({...form, notes: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div className="flex items-end gap-2">
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={form.includeInNetWorth} onChange={e => setForm({...form, includeInNetWorth: e.target.checked})} className="rounded border-slate-300 text-indigo-600" />
                <span className="text-xs text-slate-700">Net Worth</span>
              </label>
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editing ? 'Update' : 'Save'}</button>
              <button type="button" onClick={() => { setShowForm(false); setEditing(null); }} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Properties List */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {properties.map(p => (
          <div key={p.id} className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-3">
                {(() => {
                  const ts = typeStyle(p.propertyType);
                  const Icon = ts.icon;
                  return (
                    <div className={`w-10 h-10 rounded-lg ${ts.bg} flex items-center justify-center`}>
                      <Icon size={20} className={ts.fg} />
                    </div>
                  );
                })()}
                <div>
                  <h4 className="font-semibold text-slate-800 text-sm">{p.propertyName}</h4>
                  <div className="flex items-center gap-1 text-xs text-slate-500 mt-0.5">
                    <MapPin size={11} /> {p.address || p.country || '-'}
                  </div>
                </div>
              </div>
              <div className="flex gap-1">
                <button onClick={() => startEdit(p)} className="text-slate-400 hover:text-indigo-600 p-1"><Pencil size={14} /></button>
                <button onClick={() => handleDelete(p.id)} className="text-slate-400 hover:text-red-500 p-1"><Trash2 size={14} /></button>
              </div>
            </div>
            <div className="grid grid-cols-3 gap-3 mt-4">
              <div><p className="text-[10px] text-slate-400 uppercase">Value</p><p className="text-sm font-bold text-green-600">{formatCurrency(p.currentValue || 0, p.currency)}</p></div>
              <div><p className="text-[10px] text-slate-400 uppercase">Loan</p><p className="text-sm font-bold text-red-600">{p.outstandingLoan ? formatCurrency(p.outstandingLoan, p.currency) : '-'}</p></div>
              <div><p className="text-[10px] text-slate-400 uppercase">Equity</p><p className="text-sm font-bold text-indigo-600">{formatCurrency((p.currentValue || 0) - (p.outstandingLoan || 0), p.currency)}</p></div>
            </div>
            <div className="flex items-center gap-2 mt-3 flex-wrap">
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-medium">{typeStyle(p.propertyType).label}</span>
              <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${p.status === 'OWNED' ? 'bg-green-100 text-green-700' : p.status === 'SOLD' ? 'bg-slate-100 text-slate-500' : 'bg-blue-100 text-blue-700'}`}>{p.status}</span>
              {p.tenure && <span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 font-medium">{p.tenure}</span>}
              {p.ownership && <span className="text-[10px] text-slate-400">{p.ownership}</span>}
              {p.owner?.name && <span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-medium">{p.owner.name}</span>}
              {p.monthlyRental > 0 && <span className="text-[10px] px-2 py-0.5 rounded-full bg-blue-100 text-blue-700 font-medium">Rent: {formatCurrency(p.monthlyRental, p.currency)}/mo</span>}
            </div>
          </div>
        ))}
        {properties.length === 0 && (
          <div className="col-span-2 bg-white rounded-xl p-12 border border-slate-200 text-center text-slate-400">
            <Home size={32} className="mx-auto mb-2 opacity-50" />
            <p>No properties yet. Click "Add Property" to get started.</p>
          </div>
        )}
      </div>
    </div>
  );
}
