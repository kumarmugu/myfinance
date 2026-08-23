import { useEffect, useState } from 'react';
import { CheckCircle, XCircle, Clock, Play, RefreshCw } from 'lucide-react';

interface TestCase {
  name: string;
  status: 'pass' | 'fail' | 'pending';
  duration?: string;
}

interface TestSuite {
  name: string;
  type: 'backend' | 'frontend';
  tests: TestCase[];
}

interface TestResultsData {
  timestamp: string;
  summary: { total: number; passed: number; failed: number; suites: number };
  suites: TestSuite[];
}

export default function TestResults() {
  const [data, setData] = useState<TestResultsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'all' | 'backend' | 'frontend'>('all');

  useEffect(() => { loadResults(); }, []);

  const loadResults = async () => {
    setLoading(true);
    try {
      const res = await fetch('/test-results.json');
      if (res.ok) {
        setData(await res.json());
      }
    } catch (err) { console.error('Could not load test results:', err); }
    finally { setLoading(false); }
  };

  if (loading) return <div className="flex items-center justify-center h-64"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div></div>;

  if (!data) {
    return (
      <div className="space-y-6 max-w-4xl">
        <div><h1 className="text-2xl font-bold text-slate-800">Test Results</h1></div>
        <div className="bg-amber-50 border border-amber-200 rounded-lg p-6 text-center">
          <p className="text-sm text-amber-800 font-medium mb-2">No test results found</p>
          <p className="text-xs text-amber-600 mb-4">Run the test suite to generate results. Tests run automatically when a Kiro session ends (Stop hook), or you can run manually:</p>
          <div className="space-y-1.5 text-left max-w-md mx-auto">
            <div className="flex items-center gap-2">
              <Play size={12} className="text-blue-600" />
              <code className="text-xs bg-white px-2 py-0.5 rounded border border-amber-200 text-slate-700">bash scripts/run-tests.sh</code>
            </div>
          </div>
        </div>
      </div>
    );
  }

  const { summary, suites, timestamp, coverage } = data as any;
  const filtered = filter === 'all' ? suites : suites.filter((s: any) => s.type === filter);
  const backendCount = suites.filter((s: any) => s.type === 'backend').reduce((s: number, st: any) => s + st.tests.length, 0);
  const frontendCount = suites.filter((s: any) => s.type === 'frontend').reduce((s: number, st: any) => s + st.tests.length, 0);

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Test Results</h1>
          <p className="text-slate-500 text-sm mt-0.5">Backend (Spring Boot) + Frontend (Vitest) test coverage</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 text-xs text-slate-500">
            <Clock size={13} /> {new Date(timestamp).toLocaleString()}
          </div>
          <button onClick={loadResults} className="p-1.5 text-slate-400 hover:text-indigo-600 rounded-lg hover:bg-slate-100"><RefreshCw size={15} /></button>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-4 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Total Tests</p>
          <p className="text-2xl font-bold text-slate-800 mt-1">{summary.total}</p>
        </div>
        <div className="bg-green-50 rounded-lg p-4 border border-green-200">
          <p className="text-[11px] text-green-600 uppercase">Passed</p>
          <p className="text-2xl font-bold text-green-700 mt-1">{summary.passed}</p>
        </div>
        <div className={`rounded-lg p-4 border ${summary.failed > 0 ? 'bg-red-50 border-red-200' : 'bg-slate-50 border-slate-200'}`}>
          <p className={`text-[11px] uppercase ${summary.failed > 0 ? 'text-red-600' : 'text-slate-500'}`}>Failed</p>
          <p className={`text-2xl font-bold mt-1 ${summary.failed > 0 ? 'text-red-700' : 'text-slate-400'}`}>{summary.failed}</p>
        </div>
        <div className="bg-white rounded-lg p-4 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Suites</p>
          <p className="text-2xl font-bold text-slate-800 mt-1">{summary.suites}</p>
        </div>
      </div>

      {/* Progress Bar */}
      <div className="bg-white rounded-lg p-4 border border-slate-200 shadow-sm">
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm font-medium text-slate-700">Pass Rate</span>
          <span className={`text-sm font-bold ${summary.failed === 0 ? 'text-green-600' : 'text-amber-600'}`}>{summary.total > 0 ? ((summary.passed / summary.total) * 100).toFixed(0) : 0}%</span>
        </div>
        <div className="h-3 bg-slate-100 rounded-full overflow-hidden">
          <div className={`h-full rounded-full transition-all ${summary.failed === 0 ? 'bg-green-500' : 'bg-amber-500'}`} style={{ width: `${summary.total > 0 ? (summary.passed / summary.total) * 100 : 0}%` }} />
        </div>
      </div>

      {/* Code Coverage */}
      {coverage && (coverage.backend !== null || coverage.frontend !== null) && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div className="bg-white rounded-lg p-4 border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-medium text-slate-700">Backend Coverage</span>
              <span className="text-sm font-bold text-blue-600">{coverage.backend != null ? `${coverage.backend}%` : 'N/A'}</span>
            </div>
            {coverage.backend != null && (
              <div className="h-2.5 bg-slate-100 rounded-full overflow-hidden">
                <div className="h-full bg-blue-500 rounded-full" style={{ width: `${coverage.backend}%` }} />
              </div>
            )}
          </div>
          <div className="bg-white rounded-lg p-4 border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-medium text-slate-700">Frontend Coverage</span>
              <span className="text-sm font-bold text-purple-600">{coverage.frontend != null ? `${coverage.frontend}%` : 'N/A'}</span>
            </div>
            {coverage.frontend != null && (
              <div className="h-2.5 bg-slate-100 rounded-full overflow-hidden">
                <div className="h-full bg-purple-500 rounded-full" style={{ width: `${coverage.frontend}%` }} />
              </div>
            )}
          </div>
        </div>
      )}

      {/* Filter */}
      <div className="flex gap-1 bg-slate-100 rounded-lg p-1 w-fit">
        {(['all', 'backend', 'frontend'] as const).map(f => (
          <button key={f} onClick={() => setFilter(f)} className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${filter === f ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600'}`}>
            {f === 'all' ? `All (${summary.total})` : f === 'backend' ? `Backend (${backendCount})` : `Frontend (${frontendCount})`}
          </button>
        ))}
      </div>

      {/* Test Suites */}
      <div className="space-y-3">
        {filtered.map((suite: any) => {
          const passed = suite.tests.filter((t: any) => t.status === 'pass').length;
          const total = suite.tests.length;
          const allPassed = passed === total;

          return (
            <div key={suite.name} className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
                <div className="flex items-center gap-3">
                  {allPassed ? <CheckCircle size={18} className="text-green-500" /> : <XCircle size={18} className="text-red-500" />}
                  <div>
                    <h4 className="font-medium text-slate-800 text-sm">{suite.name}</h4>
                    <span className={`text-[10px] px-1.5 py-0.5 rounded font-medium ${suite.type === 'backend' ? 'bg-blue-100 text-blue-700' : 'bg-purple-100 text-purple-700'}`}>{suite.type}</span>
                  </div>
                </div>
                <span className="text-xs text-slate-500">{passed}/{total} passed</span>
              </div>
              <div className="divide-y divide-slate-50">
                {suite.tests.map((test: any) => (
                  <div key={test.name} className="flex items-center justify-between px-5 py-2 hover:bg-slate-50">
                    <div className="flex items-center gap-2">
                      {test.status === 'pass' ? <CheckCircle size={14} className="text-green-500" /> : test.status === 'fail' ? <XCircle size={14} className="text-red-500" /> : <Clock size={14} className="text-amber-500" />}
                      <span className="text-sm text-slate-700">{test.name}</span>
                    </div>
                    <span className="text-[10px] text-slate-400 font-mono">{test.duration}</span>
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>

      {/* Run Instructions */}
      <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
        <p className="text-sm font-medium text-slate-700 mb-2">How tests are run</p>
        <ul className="text-xs text-slate-600 space-y-1.5 list-disc list-inside">
          <li><b>Automatic:</b> Tests run when a Kiro session ends (Stop hook)</li>
          <li><b>Manual:</b> Run <code className="bg-white px-1.5 py-0.5 rounded border border-slate-200">bash scripts/run-tests.sh</code> from the project root</li>
          <li>Results are saved to <code className="bg-white px-1.5 py-0.5 rounded border border-slate-200">frontend/public/test-results.json</code></li>
          <li>This page auto-refreshes from that file</li>
        </ul>
        <div className="mt-3 pt-3 border-t border-slate-200 space-y-1.5">
          <div className="flex items-center gap-2">
            <Play size={12} className="text-blue-600" />
            <code className="text-xs bg-white px-2 py-0.5 rounded border border-slate-200 text-slate-700">cd backend && ./mvnw test</code>
            <span className="text-xs text-slate-500">— Backend only</span>
          </div>
          <div className="flex items-center gap-2">
            <Play size={12} className="text-purple-600" />
            <code className="text-xs bg-white px-2 py-0.5 rounded border border-slate-200 text-slate-700">cd frontend && npm test</code>
            <span className="text-xs text-slate-500">— Frontend only</span>
          </div>
        </div>
      </div>
    </div>
  );
}
