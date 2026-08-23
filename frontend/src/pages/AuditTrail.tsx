import { useEffect, useState } from 'react';
import { ClipboardList, Filter, ChevronLeft, ChevronRight } from 'lucide-react';
import api from '../api';

interface AuditEntry {
  id: number;
  userId: number;
  username: string;
  action: string;
  entity: string;
  entityId: number | null;
  details: string;
  timestamp: string;
}

interface AuditPage {
  content: AuditEntry[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
}

const ACTION_COLORS: Record<string, string> = {
  CREATE: 'bg-green-100 text-green-700',
  UPDATE: 'bg-blue-100 text-blue-700',
  DELETE: 'bg-red-100 text-red-700',
};

export default function AuditTrail() {
  const [data, setData] = useState<AuditPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);

  // Filters
  const [filterAction, setFilterAction] = useState('');
  const [filterEntity, setFilterEntity] = useState('');
  const [filterFrom, setFilterFrom] = useState('');
  const [filterTo, setFilterTo] = useState('');

  useEffect(() => { loadData(); }, [page, filterAction, filterEntity, filterFrom, filterTo]);

  const loadData = async () => {
    try {
      setLoading(true);
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('size', '30');
      if (filterAction) params.append('action', filterAction);
      if (filterEntity) params.append('entity', filterEntity);
      if (filterFrom) params.append('from', filterFrom);
      if (filterTo) params.append('to', filterTo);

      const res = await api.get(`/audit?${params.toString()}`);
      setData(res.data);
    } catch (err) {
      console.error('Failed to load audit logs', err);
    } finally {
      setLoading(false);
    }
  };

  const clearFilters = () => {
    setFilterAction('');
    setFilterEntity('');
    setFilterFrom('');
    setFilterTo('');
    setPage(0);
  };

  const formatTimestamp = (ts: string) => {
    const d = new Date(ts);
    return d.toLocaleString('en-SG', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Audit Trail</h1>
        <p className="text-slate-500 text-sm mt-1">Track all user actions across the system</p>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <div className="flex items-center gap-2 mb-3">
          <Filter size={16} className="text-slate-500" />
          <h3 className="text-sm font-semibold text-slate-700">Filters</h3>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-3">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Action</label>
            <select
              value={filterAction}
              onChange={e => { setFilterAction(e.target.value); setPage(0); }}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm"
            >
              <option value="">All Actions</option>
              <option value="CREATE">CREATE</option>
              <option value="UPDATE">UPDATE</option>
              <option value="DELETE">DELETE</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Entity</label>
            <input
              type="text"
              value={filterEntity}
              onChange={e => { setFilterEntity(e.target.value); setPage(0); }}
              placeholder="e.g. Account, Asset"
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">From Date</label>
            <input
              type="date"
              value={filterFrom}
              onChange={e => { setFilterFrom(e.target.value); setPage(0); }}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">To Date</label>
            <input
              type="date"
              value={filterTo}
              onChange={e => { setFilterTo(e.target.value); setPage(0); }}
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm"
            />
          </div>
          <div className="flex items-end">
            <button
              onClick={clearFilters}
              className="px-4 py-2 text-sm font-medium text-slate-600 bg-slate-100 rounded-lg hover:bg-slate-200"
            >
              Clear
            </button>
          </div>
        </div>
      </div>

      {/* Results */}
      {loading ? (
        <div className="flex items-center justify-center h-40">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
        </div>
      ) : (
        <>
          {/* Summary */}
          <div className="flex items-center justify-between">
            <p className="text-sm text-slate-500">
              {data ? `${data.totalElements} entries found` : 'No data'}
            </p>
          </div>

          {/* Table */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Timestamp</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">User</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Action</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Entity</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">ID</th>
                    <th className="text-left px-4 py-3 font-medium text-slate-600">Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data?.content.map(entry => (
                    <tr key={entry.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3 text-slate-600 whitespace-nowrap text-xs">
                        {formatTimestamp(entry.timestamp)}
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-slate-800 font-medium">{entry.username}</span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${ACTION_COLORS[entry.action] || 'bg-slate-100 text-slate-600'}`}>
                          {entry.action}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-slate-700">{entry.entity}</td>
                      <td className="px-4 py-3 text-slate-500 font-mono text-xs">
                        {entry.entityId || '-'}
                      </td>
                      <td className="px-4 py-3 text-slate-500 text-xs max-w-xs truncate">
                        {entry.details}
                      </td>
                    </tr>
                  ))}
                  {(!data || data.content.length === 0) && (
                    <tr>
                      <td colSpan={6} className="px-4 py-12 text-center text-slate-400">
                        <ClipboardList size={32} className="mx-auto mb-2 opacity-50" />
                        <p>No audit entries found</p>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Pagination */}
          {data && data.totalPages > 1 && (
            <div className="flex items-center justify-between">
              <p className="text-xs text-slate-500">
                Page {data.number + 1} of {data.totalPages}
              </p>
              <div className="flex gap-1">
                <button
                  onClick={() => setPage(Math.max(0, page - 1))}
                  disabled={page === 0}
                  className="p-2 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <ChevronLeft size={16} />
                </button>
                <button
                  onClick={() => setPage(Math.min(data.totalPages - 1, page + 1))}
                  disabled={page >= data.totalPages - 1}
                  className="p-2 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
