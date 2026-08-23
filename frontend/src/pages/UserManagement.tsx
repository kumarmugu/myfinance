import { useEffect, useState } from 'react';
import { Plus, Shield, UserCheck, UserX } from 'lucide-react';
import api from '../api';

interface AppUser {
  id: number;
  username: string;
  email: string;
  displayName: string;
  role: string;
  isActive: boolean;
  slFdEnabled: boolean;
  createdAt: string;
}

export default function UserManagement() {
  const [users, setUsers] = useState<AppUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ username: '', email: '', password: '', displayName: '', role: 'USER' });
  const [error, setError] = useState('');

  useEffect(() => { loadUsers(); }, []);

  const loadUsers = async () => {
    try { setUsers((await api.get('/admin/users')).data); }
    catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      await api.post('/admin/users', form);
      setShowForm(false);
      setForm({ username: '', email: '', password: '', displayName: '', role: 'USER' });
      loadUsers();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to create user');
    }
  };

  const toggleActive = async (id: number) => {
    await api.put(`/admin/users/${id}/toggle-active`);
    loadUsers();
  };

  const toggleSlFd = async (id: number) => {
    await api.put(`/admin/users/${id}/toggle-sl-fd`);
    loadUsers();
  };

  const changeRole = async (id: number, role: string) => {
    await api.put(`/admin/users/${id}/role`, { role });
    loadUsers();
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">User Management</h1>
          <p className="text-slate-500 text-sm mt-0.5">Create and manage user accounts</p>
        </div>
        <button onClick={() => setShowForm(!showForm)} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
          <Plus size={16} /> New User
        </button>
      </div>

      {/* Create Form */}
      {showForm && (
        <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
          <h3 className="text-base font-semibold text-slate-800 mb-4">Create New User</h3>
          {error && <div className="mb-3 p-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">{error}</div>}
          <form onSubmit={handleCreate} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Username *</label>
              <input type="text" value={form.username} onChange={e => setForm({...form, username: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Email *</label>
              <input type="email" value={form.email} onChange={e => setForm({...form, email: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Password *</label>
              <input type="password" value={form.password} onChange={e => setForm({...form, password: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" required /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Display Name</label>
              <input type="text" value={form.displayName} onChange={e => setForm({...form, displayName: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
            <div><label className="block text-xs font-medium text-slate-600 mb-1">Role</label>
              <div className="flex rounded-lg overflow-hidden border border-slate-300">
                <button type="button" onClick={() => setForm({...form, role: 'USER'})} className={`flex-1 py-2 text-sm font-medium transition-colors ${form.role === 'USER' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600'}`}>User</button>
                <button type="button" onClick={() => setForm({...form, role: 'ADMIN'})} className={`flex-1 py-2 text-sm font-medium transition-colors ${form.role === 'ADMIN' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600'}`}>Admin</button>
              </div></div>
            <div className="flex items-end gap-2">
              <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">Create</button>
              <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Users Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 border-b border-slate-200">
            <tr>
              <th className="text-left px-5 py-3 font-medium text-slate-600">User</th>
              <th className="text-left px-5 py-3 font-medium text-slate-600">Email</th>
              <th className="text-left px-5 py-3 font-medium text-slate-600">Role</th>
              <th className="text-center px-5 py-3 font-medium text-slate-600">SL FD</th>
              <th className="text-left px-5 py-3 font-medium text-slate-600">Status</th>
              <th className="px-5 py-3 w-32"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {users.map(u => (
              <tr key={u.id} className="hover:bg-slate-50">
                <td className="px-5 py-3.5">
                  <div className="flex items-center gap-3">
                    <div className={`w-9 h-9 rounded-full flex items-center justify-center text-white text-sm font-bold ${u.role === 'ADMIN' ? 'bg-gradient-to-br from-amber-400 to-orange-500' : 'bg-gradient-to-br from-indigo-400 to-indigo-600'}`}>
                      {u.displayName?.[0] || u.username[0]}
                    </div>
                    <div>
                      <p className="font-medium text-slate-800">{u.displayName || u.username}</p>
                      <p className="text-xs text-slate-400">@{u.username}</p>
                    </div>
                  </div>
                </td>
                <td className="px-5 py-3.5 text-slate-600">{u.email}</td>
                <td className="px-5 py-3.5">
                  <button onClick={() => changeRole(u.id, u.role === 'ADMIN' ? 'USER' : 'ADMIN')} className={`inline-flex items-center gap-1 text-[11px] font-semibold px-2.5 py-1 rounded-full cursor-pointer transition-colors ${u.role === 'ADMIN' ? 'bg-amber-100 text-amber-700 hover:bg-amber-200' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}>
                    <Shield size={11} /> {u.role}
                  </button>
                </td>
                <td className="px-5 py-3.5 text-center">
                  <button onClick={() => toggleSlFd(u.id)} className={`text-[11px] font-medium px-2.5 py-1 rounded-full transition-colors ${u.slFdEnabled ? 'bg-green-100 text-green-700 hover:bg-green-200' : 'bg-slate-100 text-slate-400 hover:bg-slate-200'}`}>
                    {u.slFdEnabled ? 'Enabled' : 'Disabled'}
                  </button>
                </td>
                <td className="px-5 py-3.5">
                  <span className={`inline-flex items-center gap-1 text-[11px] font-medium px-2 py-0.5 rounded-full ${u.isActive ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}>
                    {u.isActive ? <><UserCheck size={11} /> Active</> : <><UserX size={11} /> Disabled</>}
                  </span>
                </td>
                <td className="px-5 py-3.5">
                  <button onClick={() => toggleActive(u.id)} className={`text-xs px-3 py-1.5 rounded-lg font-medium transition-colors ${u.isActive ? 'bg-red-50 text-red-600 hover:bg-red-100' : 'bg-green-50 text-green-600 hover:bg-green-100'}`}>
                    {u.isActive ? 'Disable' : 'Enable'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Info */}
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
        <p className="text-sm text-blue-800 font-medium">How user management works</p>
        <ul className="text-xs text-blue-600 mt-1.5 space-y-1 list-disc list-inside">
          <li><b>Admin</b> can see all users, create accounts, toggle active/disabled, change roles</li>
          <li><b>User</b> can only see their own data — completely isolated from other users</li>
          <li>Click the role badge to toggle between ADMIN and USER</li>
          <li>Disabled users cannot log in but their data is preserved</li>
          <li>Users can also self-register via the login page</li>
        </ul>
      </div>
    </div>
  );
}
