import { useState } from 'react';
import { Landmark, Globe } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import GenericFD from './GenericFD';
import SriLankaFD from './SriLankaFD';

type Tab = 'generic' | 'sri-lanka';

export default function FixedDeposits() {
  const { slFdEnabled } = useAuth();
  const [tab, setTab] = useState<Tab>('generic');

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Fixed Deposits</h1>
        <p className="text-slate-500 text-sm mt-1">Manage your fixed deposit investments</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-slate-100 rounded-lg p-1 w-fit">
        <button
          onClick={() => setTab('generic')}
          className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${tab === 'generic' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'}`}
        >
          <Landmark size={15} /> Fixed Deposits
        </button>
        {slFdEnabled && (
          <button
            onClick={() => setTab('sri-lanka')}
            className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${tab === 'sri-lanka' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'}`}
          >
            <Globe size={15} /> Sri Lanka FDs
          </button>
        )}
      </div>

      {/* Tab Content */}
      {tab === 'generic' && <GenericFD />}
      {tab === 'sri-lanka' && slFdEnabled && <SriLankaFD />}
    </div>
  );
}
