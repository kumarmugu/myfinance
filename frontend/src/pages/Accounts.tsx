import { useEffect, useState } from 'react';
import { Plus, Trash2, Building2, TrendingUp, Bitcoin, Pencil, Eye, EyeOff, Users, UserPlus } from 'lucide-react';
import { getAccounts, createAccount, updateAccount, deleteAccount, getOwners, createOwner, updateOwner, deleteOwner } from '../api';
import SearchableSelect from '../components/SearchableSelect';
import type { Account, AccountType, Currency, Owner, OwnerRelationship } from '../types';
import { useToast } from '../contexts/ToastContext';

export default function Accounts() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [owners, setOwners] = useState<Owner[]>([]);
  const [tab, setTab] = useState<'accounts' | 'owners'>('accounts');
  const [showAccountForm, setShowAccountForm] = useState(false);
  const [showOwnerForm, setShowOwnerForm] = useState(false);
  const [editingAccount, setEditingAccount] = useState<Account | null>(null);
  const [editingOwner, setEditingOwner] = useState<Owner | null>(null);
  const [unmasked, setUnmasked] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const { showToast } = useToast();

  const [accForm, setAccForm] = useState({ name: '', accountType: 'BROKER' as AccountType, currency: 'SGD' as Currency, accountNumber: '', description: '', ownerId: 0 });
  const [ownerForm, setOwnerForm] = useState({ name: '', relationship: 'SELF' as OwnerRelationship });

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const [accRes, ownRes] = await Promise.all([getAccounts(), getOwners()]);
      setAccounts(accRes.data.filter((a: Account) => a.accountType !== 'BANK')); setOwners(ownRes.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  // ─── Account handlers ───
  const handleAccountSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const payload: any = { ...accForm, owner: accForm.ownerId ? { id: accForm.ownerId } : null };
    delete payload.ownerId;
    try {
      if (editingAccount) { await updateAccount(editingAccount.id, payload); }
      else { await createAccount(payload); }
      setShowAccountForm(false); setEditingAccount(null);
      setAccForm({ name: '', accountType: 'BROKER', currency: 'SGD', accountNumber: '', description: '', ownerId: 0 });
      loadData();
    } catch (err: any) { console.error(err); showToast(err.response?.data?.message || err.message || 'Failed to save account'); }
  };

  const startEditAccount = (acc: Account) => {
    setEditingAccount(acc);
    setAccForm({ name: acc.name, accountType: acc.accountType, currency: acc.currency, accountNumber: acc.accountNumber || '', description: acc.description || '', ownerId: acc.owner?.id || 0 });
    setShowAccountForm(true);
  };

  const handleDeleteAccount = async (id: number) => {
    if (confirm('Delete this account?')) {
      try { await deleteAccount(id); loadData(); }
      catch (err: any) {
        const msg = err.response?.data?.message || 'Failed to delete';
        const refs = err.response?.data?.references;
        showToast(refs ? `${msg}\n\nReferenced by:\n• ${refs.join('\n• ')}` : msg);
      }
    }
  };

  const toggleMask = (id: number) => {
    setUnmasked(prev => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  };

  const maskNumber = (num: string | null, id: number) => {
    if (!num) return '-';
    if (unmasked.has(id)) return num;
    if (num.length <= 4) return '****';
    return '****' + num.slice(-4);
  };

  // ─── Owner handlers ───
  const handleOwnerSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingOwner) { await updateOwner(editingOwner.id, ownerForm); }
      else { await createOwner(ownerForm); }
      setShowOwnerForm(false); setEditingOwner(null);
      setOwnerForm({ name: '', relationship: 'SELF' });
      loadData();
    } catch (err) { console.error(err); showToast('Failed'); }
  };

  const startEditOwner = (o: Owner) => { setEditingOwner(o); setOwnerForm({ name: o.name, relationship: o.relationship }); setShowOwnerForm(true); };
  const handleDeleteOwner = async (id: number) => {
    if (confirm('Delete this owner?')) {
      try { await deleteOwner(id); loadData(); }
      catch (err: any) {
        const msg = err.response?.data?.message || 'Failed to delete';
        const refs = err.response?.data?.references;
        showToast(refs ? `${msg}\n\nReferenced by:\n• ${refs.join('\n• ')}` : msg);
      }
    }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  const iconFor = (type: AccountType) => type === 'BROKER' ? <TrendingUp size={18} className="text-indigo-600" /> : type === 'CRYPTO_EXCHANGE' ? <Bitcoin size={18} className="text-amber-600" /> : <Building2 size={18} className="text-green-600" />;
  const bgFor = (type: AccountType) => type === 'BROKER' ? 'bg-indigo-100' : type === 'CRYPTO_EXCHANGE' ? 'bg-amber-100' : 'bg-green-100';

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div><h1 className="text-2xl font-bold text-slate-800">Brokers & Owners</h1><p className="text-slate-500 text-sm mt-1">Manage broker accounts, crypto exchanges, and portfolio owners</p></div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-slate-100 rounded-lg p-1 w-fit">
        <button onClick={() => setTab('accounts')} className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${tab === 'accounts' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600'}`}>
          <Building2 size={15} /> Accounts ({accounts.length})
        </button>
        <button onClick={() => setTab('owners')} className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${tab === 'owners' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600'}`}>
          <Users size={15} /> Owners ({owners.length})
        </button>
      </div>

      {/* ═══════ ACCOUNTS TAB ═══════ */}
      {tab === 'accounts' && (
        <>
          <div className="flex justify-end">
            <button onClick={() => { setShowAccountForm(!showAccountForm); setEditingAccount(null); setAccForm({ name: '', accountType: 'BROKER', currency: 'SGD', accountNumber: '', description: '', ownerId: 0 }); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
              <Plus size={16} /> New Account
            </button>
          </div>

          {showAccountForm && (
            <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
              <h3 className="text-lg font-semibold text-slate-800 mb-4">{editingAccount ? 'Edit Account' : 'Add Account'}</h3>
              <form onSubmit={handleAccountSubmit} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                <div><label className="block text-sm font-medium text-slate-700 mb-1">Name *</label><input type="text" value={accForm.name} onChange={e => setAccForm({...accForm, name: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" required /></div>
                <div><label className="block text-sm font-medium text-slate-700 mb-1">Type</label>
                  <div className="flex rounded-lg overflow-hidden border border-slate-300">
                    {([['BROKER', 'Broker'], ['CRYPTO_EXCHANGE', 'Crypto']] as const).map(([val, label]) => (
                      <button key={val} type="button" onClick={() => setAccForm({...accForm, accountType: val as AccountType})}
                        className={`flex-1 py-2 text-sm font-medium transition-colors ${accForm.accountType === val ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}>{label}</button>
                    ))}
                  </div></div>
                <div><label className="block text-sm font-medium text-slate-700 mb-1">Currency</label>
                  <SearchableSelect options={['SGD','USD','EUR','LKR','INR','GBP','AUD','JPY','CNY','MYR','HKD','CAD'].map(c => ({ value: c, label: c }))} value={accForm.currency} onChange={v => setAccForm({...accForm, currency: v as Currency})} placeholder="Select currency..." /></div>
                <div><label className="block text-sm font-medium text-slate-700 mb-1">Account Number</label><input type="text" value={accForm.accountNumber} onChange={e => setAccForm({...accForm, accountNumber: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Will be masked on display" /></div>
                <div><label className="block text-sm font-medium text-slate-700 mb-1">Owner</label>
                  <SearchableSelect options={[{ value: 0, label: 'Unlinked' }, ...owners.map(o => ({ value: o.id, label: `${o.name} (${o.relationship})`, icon: o.name[0] }))]} value={accForm.ownerId} onChange={v => setAccForm({...accForm, ownerId: Number(v)})} placeholder="Select owner..." /></div>
                <div><label className="block text-sm font-medium text-slate-700 mb-1">Description</label><input type="text" value={accForm.description} onChange={e => setAccForm({...accForm, description: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" /></div>
                <div className="flex items-end gap-2">
                  <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editingAccount ? 'Update' : 'Save'}</button>
                  <button type="button" onClick={() => { setShowAccountForm(false); setEditingAccount(null); }} className="px-4 py-2 bg-slate-200 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
                </div>
              </form>
            </div>
          )}

          {/* Account Table */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Account</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Type</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Currency</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Account No.</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Owner</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Description</th>
                    <th className="px-4 py-3 w-20"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {accounts.map(acc => (
                    <tr key={acc.id} className="hover:bg-slate-50 group">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2.5">
                          <div className={`p-1.5 rounded-lg ${bgFor(acc.accountType)}`}>{iconFor(acc.accountType)}</div>
                          <span className="font-medium text-slate-800">{acc.name}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3"><span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-medium">{acc.accountType}</span></td>
                      <td className="px-4 py-3"><span className="text-xs font-medium text-indigo-600">{acc.currency}</span></td>
                      <td className="px-4 py-3">
                        {acc.accountNumber ? (
                          <div className="flex items-center gap-1.5">
                            <span className="text-xs font-mono text-slate-600">{maskNumber(acc.accountNumber, acc.id)}</span>
                            <button onClick={() => toggleMask(acc.id)} className="text-slate-400 hover:text-slate-700">
                              {unmasked.has(acc.id) ? <EyeOff size={12} /> : <Eye size={12} />}
                            </button>
                          </div>
                        ) : <span className="text-xs text-slate-400">-</span>}
                      </td>
                      <td className="px-4 py-3">
                        {acc.owner ? (
                          <div className="flex items-center gap-1.5">
                            <div className="w-5 h-5 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-700 text-[9px] font-bold">{acc.owner.name[0]}</div>
                            <span className="text-xs text-slate-700">{acc.owner.name}</span>
                          </div>
                        ) : <span className="text-xs text-slate-400">Unlinked</span>}
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-500 max-w-40 truncate">{acc.description || '-'}</td>
                      <td className="px-4 py-3">
                        <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button onClick={() => startEditAccount(acc)} className="p-1 text-slate-400 hover:text-indigo-600"><Pencil size={13} /></button>
                          <button onClick={() => handleDeleteAccount(acc.id)} className="p-1 text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {accounts.length === 0 && <tr><td colSpan={7} className="px-4 py-12 text-center text-slate-400">No accounts</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {/* ═══════ OWNERS TAB ═══════ */}
      {tab === 'owners' && (
        <>
          <div className="flex justify-end">
            <button onClick={() => { setShowOwnerForm(!showOwnerForm); setEditingOwner(null); setOwnerForm({ name: '', relationship: 'SELF' }); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
              <UserPlus size={16} /> New Owner
            </button>
          </div>

          {showOwnerForm && (
            <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
              <h3 className="text-lg font-semibold text-slate-800 mb-4">{editingOwner ? 'Edit Owner' : 'Add Owner'}</h3>
              <form onSubmit={handleOwnerSubmit} className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div><label className="block text-sm font-medium text-slate-700 mb-1">Name *</label><input type="text" value={ownerForm.name} onChange={e => setOwnerForm({...ownerForm, name: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" required /></div>
                <div><label className="block text-sm font-medium text-slate-700 mb-1">Relationship</label>
                  <SearchableSelect options={['SELF','SPOUSE','SON','DAUGHTER','FATHER','MOTHER','BROTHER','SISTER'].map(r => ({ value: r, label: r.charAt(0) + r.slice(1).toLowerCase() }))} value={ownerForm.relationship} onChange={v => setOwnerForm({...ownerForm, relationship: v as OwnerRelationship})} placeholder="Select relationship..." /></div>
                <div className="flex items-end gap-2">
                  <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">{editingOwner ? 'Update' : 'Save'}</button>
                  <button type="button" onClick={() => { setShowOwnerForm(false); setEditingOwner(null); }} className="px-4 py-2 bg-slate-200 text-slate-700 rounded-lg text-sm font-medium">Cancel</button>
                </div>
              </form>
            </div>
          )}

          {/* Owner Table */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Owner</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Relationship</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Linked Accounts</th>
                    <th className="px-4 py-3 w-20"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {owners.map(o => {
                    const linkedAccounts = accounts.filter(a => a.owner?.id === o.id);
                    return (
                      <tr key={o.id} className="hover:bg-slate-50 group">
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-3">
                            <div className="w-9 h-9 rounded-full bg-gradient-to-br from-indigo-400 to-indigo-600 flex items-center justify-center text-white font-bold text-sm shadow-sm">{o.name[0]}</div>
                            <span className="font-medium text-slate-800">{o.name}</span>
                          </div>
                        </td>
                        <td className="px-4 py-3"><span className="text-xs px-2.5 py-1 rounded-full bg-slate-100 text-slate-700 font-medium">{o.relationship}</span></td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-1">
                            {linkedAccounts.map(a => (
                              <span key={a.id} className="text-[10px] px-2 py-0.5 bg-indigo-50 text-indigo-700 rounded font-medium">{a.name}</span>
                            ))}
                            {linkedAccounts.length === 0 && <span className="text-xs text-slate-400">No accounts</span>}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                            <button onClick={() => startEditOwner(o)} className="p-1 text-slate-400 hover:text-indigo-600"><Pencil size={13} /></button>
                            <button onClick={() => handleDeleteOwner(o.id)} className="p-1 text-slate-400 hover:text-red-500"><Trash2 size={13} /></button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                  {owners.length === 0 && <tr><td colSpan={4} className="px-4 py-12 text-center text-slate-400">No owners</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
