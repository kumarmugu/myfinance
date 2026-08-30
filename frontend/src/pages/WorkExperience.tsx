import { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, Briefcase } from 'lucide-react';
import { getWorkExperiences, createWorkExperience, updateWorkExperience, deleteWorkExperience } from '../api';
import ExportMenu from '../components/ExportMenu';
import { workExperienceExportConfig } from '../utils/export/configs';

interface WorkExp {
  id: number;
  company: string;
  position: string;
  level: string;
  country: string;
  startDate: string;
  endDate: string | null;
  isCurrent: boolean;
  industry: string;
  notes: string;
}

function formatPeriod(start: string, end: string | null): string {
  const s = new Date(start);
  const e = end ? new Date(end) : new Date();
  const months = (e.getFullYear() - s.getFullYear()) * 12 + (e.getMonth() - s.getMonth());
  const years = Math.floor(months / 12);
  const rem = months % 12;
  if (years === 0) return `${rem} mo`;
  if (rem === 0) return `${years} yr`;
  return `${years} yr ${rem} mo`;
}

export default function WorkExperience() {
  const [experiences, setExperiences] = useState<WorkExp[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<WorkExp | null>(null);
  const [form, setForm] = useState({
    company: '', position: '', level: '', country: 'Singapore',
    startDate: '', endDate: '', isCurrent: false, industry: '', notes: ''
  });

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try { setExperiences((await getWorkExperiences()).data); }
    catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const resetForm = () => setForm({
    company: '', position: '', level: '', country: 'Singapore',
    startDate: '', endDate: '', isCurrent: false, industry: '', notes: ''
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const payload = { ...form, endDate: form.isCurrent ? null : form.endDate || null };
    try {
      if (editing) { await updateWorkExperience(editing.id, payload); }
      else { await createWorkExperience(payload); }
      setShowForm(false); setEditing(null); resetForm(); loadData();
    } catch (err) { console.error(err); alert('Failed'); }
  };

  const startEdit = (exp: WorkExp) => {
    setEditing(exp);
    setForm({
      company: exp.company, position: exp.position || '', level: exp.level || '',
      country: exp.country || 'Singapore', startDate: exp.startDate, endDate: exp.endDate || '',
      isCurrent: exp.isCurrent, industry: exp.industry || '', notes: exp.notes || ''
    });
    setShowForm(true);
  };

  const handleDelete = async (id: number) => { if (confirm('Delete?')) { await deleteWorkExperience(id); loadData(); } };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const totalYears = experiences.reduce((sum, exp) => {
    const s = new Date(exp.startDate);
    const e = exp.endDate ? new Date(exp.endDate) : new Date();
    return sum + (e.getTime() - s.getTime()) / (1000 * 60 * 60 * 24 * 365.25);
  }, 0);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Work Experience</h1><p className="text-slate-500 text-sm mt-0.5">Track your career history</p></div>
        <div className="flex items-center gap-3">
          <ExportMenu rows={experiences} config={workExperienceExportConfig} />
          <button onClick={() => { setShowForm(!showForm); setEditing(null); resetForm(); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700"><Plus size={16} /> Add Company</button>
        </div>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Companies</p><p className="text-lg font-bold text-slate-800 mt-1">{experiences.length}</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Total Experience</p><p className="text-lg font-bold text-indigo-600 mt-1">{totalYears.toFixed(1)} years</p></div>
        <div className="bg-white rounded-lg p-3.5 border border-slate-200 shadow-sm"><p className="text-[11px] text-slate-500 uppercase">Current</p><p className="text-lg font-bold text-green-600 mt-1">{experiences.find(e => e.isCurrent)?.company || '-'}</p></div>
      </div>

      {/* Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">{editing ? 'Edit Experience' : 'Add Experience'}</h3>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Company *</label>
              <input type="text" value={form.company} onChange={e => setForm({...form, company: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Position/Role</label>
              <input type="text" value={form.position} onChange={e => setForm({...form, position: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. Software Engineer" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Level</label>
              <input type="text" value={form.level} onChange={e => setForm({...form, level: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. PM5, Senior" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Country</label>
              <input type="text" value={form.country} onChange={e => setForm({...form, country: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Start Date *</label>
              <input type="date" value={form.startDate} onChange={e => setForm({...form, startDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">End Date</label>
              <input type="date" value={form.endDate} onChange={e => setForm({...form, endDate: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" disabled={form.isCurrent} /></div>
            <div className="flex items-end">
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={form.isCurrent} onChange={e => setForm({...form, isCurrent: e.target.checked, endDate: ''})} className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500" />
                <span className="text-sm text-slate-700">Currently working here</span>
              </label></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Industry</label>
              <input type="text" value={form.industry} onChange={e => setForm({...form, industry: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. Technology" /></div>
            <div className="lg:col-span-3"><label className="block text-xs font-medium text-slate-600 mb-1">Notes</label>
              <input type="text" value={form.notes} onChange={e => setForm({...form, notes: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editing ? 'Update' : 'Save'}</button>
              <button type="button" onClick={() => { setShowForm(false); setEditing(null); }} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Timeline */}
      <div className="space-y-3">
        {experiences.map(exp => (
          <div key={exp.id} className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm hover:shadow-md transition-shadow group">
            <div className="flex items-start justify-between">
              <div className="flex items-start gap-4">
                <div className={`p-2.5 rounded-lg ${exp.isCurrent ? 'bg-green-100' : 'bg-slate-100'}`}>
                  <Briefcase size={20} className={exp.isCurrent ? 'text-green-600' : 'text-slate-500'} />
                </div>
                <div>
                  <h4 className="font-semibold text-slate-800">{exp.company}</h4>
                  <div className="flex items-center gap-2 mt-0.5">
                    {exp.position && <span className="text-sm text-slate-600">{exp.position}</span>}
                    {exp.level && <span className="text-[10px] px-1.5 py-0.5 rounded bg-indigo-100 text-indigo-700 font-medium">{exp.level}</span>}
                    {exp.isCurrent && <span className="text-[10px] px-1.5 py-0.5 rounded bg-green-100 text-green-700 font-medium">CURRENT</span>}
                  </div>
                  <div className="flex items-center gap-3 mt-2 text-xs text-slate-500">
                    <span>{exp.startDate} — {exp.endDate || 'Present'}</span>
                    <span className="font-medium text-slate-700">{formatPeriod(exp.startDate, exp.endDate)}</span>
                    {exp.country && <span className="px-1.5 py-0.5 rounded bg-slate-100">{exp.country}</span>}
                  </div>
                  {exp.notes && <p className="text-xs text-slate-400 mt-1">{exp.notes}</p>}
                </div>
              </div>
              <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <button onClick={() => startEdit(exp)} className="p-1 text-slate-400 hover:text-indigo-600"><Pencil size={14} /></button>
                <button onClick={() => handleDelete(exp.id)} className="p-1 text-slate-400 hover:text-red-500"><Trash2 size={14} /></button>
              </div>
            </div>
          </div>
        ))}
        {experiences.length === 0 && <div className="text-center text-slate-400 py-12">No work experience recorded</div>}
      </div>
    </div>
  );
}
