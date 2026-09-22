import { useEffect, useState } from 'react';
import { Plus, Trash2, Building2, TrendingUp, Bitcoin, Pencil, Eye, EyeOff, Users, UserPlus, KeyRound, ShieldCheck, ShieldAlert } from 'lucide-react';
import { getAccounts, createAccount, updateAccount, deleteAccount, getOwners, createOwner, updateOwner, deleteOwner,
  getBrokerCredentials, saveBrokerCredential, deleteBrokerCredential } from '../api';
import type { BrokerKind, BrokerCredentialStatus } from '../api';
import SearchableSelect from '../components/SearchableSelect';
import ExportMenu from '../components/ExportMenu';
import { accountsExportConfig } from '../utils/export/configs';
import type { Account, AccountType, Currency, Owner, OwnerRelationship } from '../types';
import { formatCurrency, formatDate } from '../utils/formatters';
import { useToast } from '../contexts/ToastContext';
import { useAuth } from '../contexts/AuthContext';

export default function Accounts() {
  const { hasFeature } = useAuth();
  const canBrokerSync = hasFeature('BROKER_SYNC') || hasFeature('IBKR_SYNC'); // IBKR_SYNC kept for back-compat
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [owners, setOwners] = useState<Owner[]>([]);
  const [tab, setTab] = useState<'accounts' | 'owners' | 'brokers'>('accounts');
  const [showAccountForm, setShowAccountForm] = useState(false);
  const [showOwnerForm, setShowOwnerForm] = useState(false);
  const [editingAccount, setEditingAccount] = useState<Account | null>(null);
  const [editingOwner, setEditingOwner] = useState<Owner | null>(null);
  const [unmasked, setUnmasked] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const { showToast } = useToast();

  const [accForm, setAccForm] = useState({ name: '', accountType: 'BROKER' as AccountType, currency: 'SGD' as Currency, accountNumber: '', description: '', ownerId: 0, cashBalance: '', includeCashInNetWorth: true });
  const [ownerForm, setOwnerForm] = useState({ name: '', relationship: 'SELF' as OwnerRelationship });

  // ─── Broker integrations (stored, encrypted credentials) ───
  const [creds, setCreds] = useState<BrokerCredentialStatus[]>([]);
  const [encryptionEnabled, setEncryptionEnabled] = useState(true);
  const [credBroker, setCredBroker] = useState<BrokerKind>('IBKR');
  const [credOwnerId, setCredOwnerId] = useState(0);
  const [credAccountId, setCredAccountId] = useState(0);
  const [credMeta1, setCredMeta1] = useState(''); // IBKR Query ID / Tiger ID
  const [credMeta2, setCredMeta2] = useState(''); // Tiger account
  const [credSecret1, setCredSecret1] = useState(''); // Flex token / RSA private key (write-only)
  const [credSaving, setCredSaving] = useState(false);
  const [editingCredId, setEditingCredId] = useState<number | null>(null);

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      const calls: [Promise<any>, Promise<any>, Promise<any>?] = [getAccounts(), getOwners()];
      if (canBrokerSync) calls[2] = getBrokerCredentials();
      const [accRes, ownRes, credRes] = await Promise.all(calls as any);
      setAccounts(accRes.data.filter((a: Account) => a.accountType !== 'BANK')); setOwners(ownRes.data);
      if (credRes) { setCreds(credRes.data.credentials); setEncryptionEnabled(credRes.data.encryptionEnabled); }
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  // Broker accounts belonging to the chosen owner (credentials are per account).
  const brokerAccountsForOwner = (ownerId: number) =>
    accounts.filter(a => a.accountType === 'BROKER' && a.owner?.id === ownerId);

  const resetCredForm = () => {
    setCredBroker('IBKR'); setCredOwnerId(0); setCredAccountId(0);
    setCredMeta1(''); setCredMeta2(''); setCredSecret1(''); setEditingCredId(null);
  };

  const startEditCred = (c: BrokerCredentialStatus) => {
    setEditingCredId(c.id);
    setCredBroker(c.broker);
    setCredOwnerId(c.ownerId ?? 0);
    setCredAccountId(c.accountId ?? 0);
    setCredMeta1(c.meta1 ?? '');
    setCredMeta2('');   // meta2 (Tiger account) is not returned in status; re-enter if changing
    setCredSecret1(''); // secrets are never returned; blank keeps the existing one
  };

  const handleCredSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!credOwnerId || !credAccountId) { showToast('Select the owner and broker account', 'error'); return; }
    const isNew = editingCredId === null;
    // On create, require the primary secret; on edit a blank secret keeps the stored one.
    if (isNew && !credSecret1.trim()) {
      showToast(credBroker === 'IBKR' ? 'Enter the Flex token' : 'Enter the private key', 'error'); return;
    }
    setCredSaving(true);
    try {
      await saveBrokerCredential({
        broker: credBroker, ownerId: credOwnerId, accountId: credAccountId,
        meta1: credMeta1.trim() || undefined,
        meta2: credMeta2.trim() || undefined,
        secret1: credSecret1.trim() || undefined,
      });
      showToast('Broker credentials saved', 'success');
      resetCredForm();
      loadData();
    } catch (err: any) {
      console.error(err);
      showToast(err.response?.data?.message || 'Failed to save credentials', 'error');
    } finally { setCredSaving(false); }
  };

  const handleCredDelete = async (c: BrokerCredentialStatus) => {
    if (!c.accountId) return;
    if (!confirm(`Remove the ${c.broker} credentials for this account?`)) return;
    try {
      await deleteBrokerCredential(c.broker, c.accountId);
      showToast('Credentials removed', 'success');
      if (editingCredId === c.id) resetCredForm();
      loadData();
    } catch (err: any) {
      console.error(err);
      showToast(err.response?.data?.message || 'Failed to remove credentials', 'error');
    }
  };

  const ownerName = (id: number | null) => owners.find(o => o.id === id)?.name ?? '—';
  const accountName = (id: number | null) => accounts.find(a => a.id === id)?.name ?? `#${id ?? '—'}`;

  // ─── Account handlers ───
  const handleAccountSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const payload: any = {
      ...accForm,
      owner: accForm.ownerId ? { id: accForm.ownerId } : null,
      cashBalance: accForm.cashBalance.trim() === '' ? null : parseFloat(accForm.cashBalance),
      includeCashInNetWorth: accForm.includeCashInNetWorth,
    };
    delete payload.ownerId;
    try {
      if (editingAccount) { await updateAccount(editingAccount.id, payload); }
      else { await createAccount(payload); }
      setShowAccountForm(false); setEditingAccount(null);
      setAccForm({ name: '', accountType: 'BROKER', currency: 'SGD', accountNumber: '', description: '', ownerId: 0, cashBalance: '', includeCashInNetWorth: true });
      loadData();
    } catch (err: any) { console.error(err); showToast(err.response?.data?.message || err.message || 'Failed to save account'); }
  };

  const startEditAccount = (acc: Account) => {
    setEditingAccount(acc);
    setAccForm({ name: acc.name, accountType: acc.accountType, currency: acc.currency, accountNumber: acc.accountNumber || '', description: acc.description || '', ownerId: acc.owner?.id || 0, cashBalance: acc.cashBalance != null ? String(acc.cashBalance) : '', includeCashInNetWorth: acc.includeCashInNetWorth !== false });
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
        {canBrokerSync && (
          <button onClick={() => setTab('brokers')} className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${tab === 'brokers' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600'}`}>
            <KeyRound size={15} /> Broker integrations ({creds.length})
          </button>
        )}
      </div>

      {/* ═══════ ACCOUNTS TAB ═══════ */}
      {tab === 'accounts' && (
        <>
          <div className="flex justify-end gap-3">
            <ExportMenu rows={accounts} config={accountsExportConfig} />
            <button onClick={() => { setShowAccountForm(!showAccountForm); setEditingAccount(null); setAccForm({ name: '', accountType: 'BROKER', currency: 'SGD', accountNumber: '', description: '', ownerId: 0, cashBalance: '', includeCashInNetWorth: true }); }} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
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
                <div><label className="block text-sm font-medium text-slate-700 mb-1">Cash Balance ({accForm.currency})</label>
                  <input type="number" step="any" value={accForm.cashBalance} onChange={e => setAccForm({...accForm, cashBalance: e.target.value})} className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm" placeholder="Uninvested cash in this account" />
                  <label className="mt-2 flex items-center gap-2 text-xs text-slate-600 cursor-pointer">
                    <input type="checkbox" checked={accForm.includeCashInNetWorth} onChange={e => setAccForm({...accForm, includeCashInNetWorth: e.target.checked})} className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500" />
                    Include this cash in Net Worth
                  </label>
                </div>
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
                    <th className="text-right px-4 py-3 font-medium text-slate-600">Cash</th>
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
                      <td className="px-4 py-3 text-right">
                        {acc.cashBalance != null && acc.cashBalance !== 0 ? (
                          <span className={`text-sm font-medium ${acc.includeCashInNetWorth !== false ? 'text-slate-800' : 'text-slate-400 line-through'}`} title={acc.includeCashInNetWorth !== false ? 'Counted in net worth' : 'Excluded from net worth'}>
                            {formatCurrency(acc.cashBalance, acc.currency)}
                          </span>
                        ) : <span className="text-xs text-slate-400">-</span>}
                      </td>
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
                  {accounts.length === 0 && <tr><td colSpan={8} className="px-4 py-12 text-center text-slate-400">No accounts</td></tr>}
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

      {/* ═══════ BROKER INTEGRATIONS TAB ═══════ */}
      {tab === 'brokers' && canBrokerSync && (
        <>
          {/* Encryption status banner */}
          <div className={`flex items-start gap-2 rounded-lg border px-4 py-3 text-sm ${encryptionEnabled ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-amber-200 bg-amber-50 text-amber-800'}`}>
            {encryptionEnabled ? <ShieldCheck size={16} className="mt-0.5 shrink-0" /> : <ShieldAlert size={16} className="mt-0.5 shrink-0" />}
            <div>
              {encryptionEnabled
                ? <span>Credentials are encrypted at rest (AES-256-GCM). Secret values are write-only — they are never shown or returned after saving.</span>
                : <span>Encryption key is not configured. Set <code className="font-mono">CREDENTIAL_MASTER_KEY</code> before saving credentials so secrets are never stored as plain text.</span>}
            </div>
          </div>

          {/* Add / edit credential form */}
          <div className="bg-white rounded-xl p-6 border border-slate-200 shadow-sm">
            <h3 className="text-base font-semibold text-slate-800 mb-1">{editingCredId ? 'Update broker credentials' : 'Add broker credentials'}</h3>
            <p className="text-xs text-slate-500 mb-4">Configure a broker once per account. These are used by the live sync on the Transactions and Dividends pages.</p>
            <form onSubmit={handleCredSubmit} className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div><label className="block text-xs font-medium text-slate-600 mb-1">Broker *</label>
                  <div className="flex rounded-lg overflow-hidden border border-slate-300">
                    {(['IBKR', 'TIGER'] as BrokerKind[]).map(b => (
                      <button key={b} type="button" onClick={() => setCredBroker(b)} disabled={editingCredId !== null}
                        className={`flex-1 py-2 text-sm font-medium transition-colors disabled:opacity-50 ${credBroker === b ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}>
                        {b === 'IBKR' ? 'Interactive Brokers' : 'Tiger'}</button>
                    ))}
                  </div></div>
                <div><label className="block text-xs font-medium text-slate-600 mb-1">Owner *</label>
                  <SearchableSelect options={[{ value: 0, label: 'Select owner...' }, ...owners.filter(o => brokerAccountsForOwner(o.id).length > 0).map(o => ({ value: o.id, label: o.name }))]}
                    value={credOwnerId} onChange={v => { setCredOwnerId(Number(v)); setCredAccountId(0); }} placeholder="Select owner..." /></div>
                <div><label className="block text-xs font-medium text-slate-600 mb-1">Broker account *</label>
                  <SearchableSelect options={[{ value: 0, label: credOwnerId ? 'Select account...' : 'Select an owner first' }, ...brokerAccountsForOwner(credOwnerId).map(a => ({ value: a.id, label: `${a.name} (${a.currency})` }))]}
                    value={credAccountId} onChange={v => setCredAccountId(Number(v))} placeholder="Select account..." /></div>
              </div>

              {credBroker === 'IBKR' ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div><label className="block text-xs font-medium text-slate-600 mb-1">Flex Query ID</label>
                    <input type="text" inputMode="numeric" autoComplete="off" value={credMeta1} onChange={e => setCredMeta1(e.target.value)} placeholder="e.g. 123456" disabled={credSaving}
                      className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 disabled:opacity-50" /></div>
                  <div><label className="block text-xs font-medium text-slate-600 mb-1">Flex Web Service token {editingCredId ? '' : '*'}</label>
                    <input type="password" autoComplete="new-password" value={credSecret1} onChange={e => setCredSecret1(e.target.value)} placeholder={editingCredId ? 'Leave blank to keep the saved token' : 'Flex Web Service token'} disabled={credSaving}
                      className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 disabled:opacity-50" /></div>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div><label className="block text-xs font-medium text-slate-600 mb-1">Tiger ID</label>
                    <input type="text" autoComplete="off" value={credMeta1} onChange={e => setCredMeta1(e.target.value)} placeholder="Your Tiger developer ID" disabled={credSaving}
                      className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 disabled:opacity-50" /></div>
                  <div><label className="block text-xs font-medium text-slate-600 mb-1">Tiger account</label>
                    <input type="text" autoComplete="off" value={credMeta2} onChange={e => setCredMeta2(e.target.value)} placeholder="Trading account no." disabled={credSaving}
                      className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 disabled:opacity-50" /></div>
                  <div><label className="block text-xs font-medium text-slate-600 mb-1">RSA private key {editingCredId ? '' : '*'}</label>
                    <textarea rows={1} autoComplete="off" value={credSecret1} onChange={e => setCredSecret1(e.target.value)} placeholder={editingCredId ? 'Leave blank to keep the saved key' : '-----BEGIN PRIVATE KEY-----'} disabled={credSaving}
                      className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm font-mono focus:ring-2 focus:ring-indigo-500 disabled:opacity-50" /></div>
                </div>
              )}

              <div className="flex items-center gap-2">
                <button type="submit" disabled={credSaving} className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:opacity-50">
                  {credSaving ? 'Saving…' : editingCredId ? 'Update credentials' : 'Save credentials'}</button>
                {editingCredId !== null && (
                  <button type="button" onClick={resetCredForm} disabled={credSaving} className="px-4 py-2 bg-slate-200 text-slate-700 rounded-lg text-sm font-medium disabled:opacity-50">Cancel</button>
                )}
              </div>
            </form>
          </div>

          {/* Configured credentials table */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Broker</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Owner</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Account</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Reference</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Secret</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Updated</th>
                    <th className="px-4 py-3 w-20"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {creds.map(c => (
                    <tr key={c.id} className="hover:bg-slate-50 group">
                      <td className="px-4 py-3"><span className="text-[10px] px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 font-medium">{c.broker}</span></td>
                      <td className="px-4 py-3 text-xs text-slate-700">{ownerName(c.ownerId)}</td>
                      <td className="px-4 py-3 text-xs text-slate-700">{accountName(c.accountId)}</td>
                      <td className="px-4 py-3 text-xs text-slate-500">{c.meta1 ? (c.broker === 'IBKR' ? `Query ID ${c.meta1}` : `Tiger ID ${c.meta1}`) : '—'}</td>
                      <td className="px-4 py-3">
                        {c.secret1Set
                          ? <span className="inline-flex items-center gap-1 text-xs text-emerald-700"><ShieldCheck size={12} /> Configured ••••</span>
                          : <span className="text-xs text-amber-600">Not set</span>}
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-500">{c.updatedAt ? formatDate(c.updatedAt) : '—'}</td>
                      <td className="px-4 py-3">
                        <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button onClick={() => startEditCred(c)} className="p-1 text-slate-400 hover:text-indigo-600" title="Edit"><Pencil size={13} /></button>
                          <button onClick={() => handleCredDelete(c)} className="p-1 text-slate-400 hover:text-red-500" title="Remove"><Trash2 size={13} /></button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {creds.length === 0 && <tr><td colSpan={7} className="px-4 py-12 text-center text-slate-400">No broker credentials configured</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
