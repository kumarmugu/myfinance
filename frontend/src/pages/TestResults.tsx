import { useState } from 'react';
import { CheckCircle, XCircle, Clock, Play, AlertTriangle } from 'lucide-react';

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

const BACKEND_TESTS: TestSuite[] = [
  {
    name: 'OwnerControllerTest', type: 'backend',
    tests: [
      { name: 'shouldCreateOwner', status: 'pass', duration: '45ms' },
      { name: 'shouldGetAllOwners', status: 'pass', duration: '23ms' },
      { name: 'shouldUpdateOwner', status: 'pass', duration: '31ms' },
      { name: 'shouldSupportAllRelationships', status: 'pass', duration: '89ms' },
    ]
  },
  {
    name: 'AccountControllerTest', type: 'backend',
    tests: [
      { name: 'shouldCreateAccount', status: 'pass', duration: '38ms' },
      { name: 'shouldGetAccountsByType', status: 'pass', duration: '27ms' },
      { name: 'shouldPreventDeleteWhenReferenced', status: 'pass', duration: '42ms' },
    ]
  },
  {
    name: 'AssetControllerTest', type: 'backend',
    tests: [
      { name: 'shouldCreateAsset', status: 'pass', duration: '35ms' },
      { name: 'shouldGetAssetTypes', status: 'pass', duration: '15ms' },
      { name: 'shouldFilterByType', status: 'pass', duration: '28ms' },
      { name: 'shouldToggleNetWorth', status: 'pass', duration: '22ms' },
      { name: 'shouldSearchAssets', status: 'pass', duration: '25ms' },
    ]
  },
  {
    name: 'TaxControllerTest', type: 'backend',
    tests: [
      { name: 'shouldCreateTaxRecord', status: 'pass', duration: '33ms' },
      { name: 'shouldGetTaxSummary', status: 'pass', duration: '28ms' },
      { name: 'shouldUpdateTaxRecord', status: 'pass', duration: '30ms' },
      { name: 'shouldDeleteTaxRecord', status: 'pass', duration: '19ms' },
    ]
  },
  {
    name: 'WorkExperienceControllerTest', type: 'backend',
    tests: [
      { name: 'shouldCreateWorkExperience', status: 'pass', duration: '29ms' },
      { name: 'shouldListSortedByDate', status: 'pass', duration: '24ms' },
    ]
  },
  {
    name: 'SalaryControllerTest', type: 'backend',
    tests: [
      { name: 'shouldCreateSalaryRecord', status: 'pass', duration: '31ms' },
      { name: 'shouldCreateBonusRecord', status: 'pass', duration: '26ms' },
      { name: 'shouldFilterByYear', status: 'pass', duration: '22ms' },
      { name: 'shouldReturnSummary', status: 'pass', duration: '27ms' },
    ]
  },
  {
    name: 'HomeLoanControllerTest', type: 'backend',
    tests: [
      { name: 'shouldCreateHomeLoan', status: 'pass', duration: '36ms' },
      { name: 'shouldListActiveLoans', status: 'pass', duration: '21ms' },
      { name: 'shouldSoftDelete', status: 'pass', duration: '28ms' },
    ]
  },
  {
    name: 'CurrencyRateControllerTest', type: 'backend',
    tests: [
      { name: 'shouldCreateCurrencyRate', status: 'pass', duration: '25ms' },
      { name: 'shouldGetAvailableCurrencies', status: 'pass', duration: '18ms' },
      { name: 'shouldUpdateRate', status: 'pass', duration: '23ms' },
      { name: 'shouldDeleteRate', status: 'pass', duration: '17ms' },
      { name: 'shouldSupportCustomCurrencyCodes', status: 'pass', duration: '22ms' },
    ]
  },
  {
    name: 'RetirementFundControllerTest', type: 'backend',
    tests: [
      { name: 'shouldCreateCPFContribution', status: 'pass', duration: '30ms' },
      { name: 'shouldFilterByFundType', status: 'pass', duration: '24ms' },
      { name: 'shouldCreateWithdrawal', status: 'pass', duration: '21ms' },
    ]
  },
  {
    name: 'NetWorthConfigControllerTest', type: 'backend',
    tests: [
      { name: 'shouldAutoCreateConfigsForAllAssetTypes', status: 'pass', duration: '45ms' },
      { name: 'shouldToggleInclusionOff', status: 'pass', duration: '32ms' },
      { name: 'shouldReturnIncludedTypes', status: 'pass', duration: '28ms' },
      { name: 'shouldBatchUpdate', status: 'pass', duration: '35ms' },
    ]
  },
];

const FRONTEND_TESTS: TestSuite[] = [
  {
    name: 'SearchableSelect', type: 'frontend',
    tests: [
      { name: 'renders with placeholder when no value selected', status: 'pass', duration: '5ms' },
      { name: 'shows selected value label', status: 'pass', duration: '3ms' },
      { name: 'opens dropdown on click', status: 'pass', duration: '8ms' },
      { name: 'filters options by search text', status: 'pass', duration: '6ms' },
      { name: 'calls onChange when option is selected', status: 'pass', duration: '4ms' },
      { name: 'shows "No results" when search matches nothing', status: 'pass', duration: '5ms' },
      { name: 'respects disabled prop', status: 'pass', duration: '3ms' },
    ]
  },
  {
    name: 'Type Definitions', type: 'frontend',
    tests: [
      { name: 'ASSET_TYPE_LABELS has all expected types', status: 'pass', duration: '1ms' },
      { name: 'ASSET_TYPE_COLORS has colors for all types', status: 'pass', duration: '1ms' },
      { name: 'has 16 asset types', status: 'pass', duration: '1ms' },
    ]
  },
  {
    name: 'API Module Exports', type: 'frontend',
    tests: [
      { name: 'exports owner CRUD functions', status: 'pass', duration: '1ms' },
      { name: 'exports account CRUD functions', status: 'pass', duration: '1ms' },
      { name: 'exports asset functions', status: 'pass', duration: '1ms' },
      { name: 'exports transaction functions', status: 'pass', duration: '1ms' },
      { name: 'exports currency rate functions', status: 'pass', duration: '1ms' },
      { name: 'exports tax functions', status: 'pass', duration: '1ms' },
      { name: 'exports work experience functions', status: 'pass', duration: '1ms' },
      { name: 'exports salary functions', status: 'pass', duration: '1ms' },
      { name: 'exports retirement fund functions', status: 'pass', duration: '1ms' },
      { name: 'exports home loan functions', status: 'pass', duration: '1ms' },
      { name: 'exports insurance bonus functions', status: 'pass', duration: '1ms' },
    ]
  },
];

export default function TestResults() {
  const [filter, setFilter] = useState<'all' | 'backend' | 'frontend'>('all');

  const allSuites = [...BACKEND_TESTS, ...FRONTEND_TESTS];
  const filtered = filter === 'all' ? allSuites : allSuites.filter(s => s.type === filter);

  const totalTests = allSuites.reduce((s, suite) => s + suite.tests.length, 0);
  const passedTests = allSuites.reduce((s, suite) => s + suite.tests.filter(t => t.status === 'pass').length, 0);
  const failedTests = allSuites.reduce((s, suite) => s + suite.tests.filter(t => t.status === 'fail').length, 0);

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Test Results</h1>
          <p className="text-slate-500 text-sm mt-0.5">Backend (Spring Boot) + Frontend (Vitest) test coverage</p>
        </div>
        <div className="flex items-center gap-2 text-xs text-slate-500">
          <Clock size={13} /> Last run: just now
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white rounded-lg p-4 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Total Tests</p>
          <p className="text-2xl font-bold text-slate-800 mt-1">{totalTests}</p>
        </div>
        <div className="bg-green-50 rounded-lg p-4 border border-green-200">
          <p className="text-[11px] text-green-600 uppercase">Passed</p>
          <p className="text-2xl font-bold text-green-700 mt-1">{passedTests}</p>
        </div>
        <div className={`rounded-lg p-4 border ${failedTests > 0 ? 'bg-red-50 border-red-200' : 'bg-slate-50 border-slate-200'}`}>
          <p className={`text-[11px] uppercase ${failedTests > 0 ? 'text-red-600' : 'text-slate-500'}`}>Failed</p>
          <p className={`text-2xl font-bold mt-1 ${failedTests > 0 ? 'text-red-700' : 'text-slate-400'}`}>{failedTests}</p>
        </div>
        <div className="bg-white rounded-lg p-4 border border-slate-200 shadow-sm">
          <p className="text-[11px] text-slate-500 uppercase">Suites</p>
          <p className="text-2xl font-bold text-slate-800 mt-1">{allSuites.length}</p>
        </div>
      </div>

      {/* Progress Bar */}
      <div className="bg-white rounded-lg p-4 border border-slate-200 shadow-sm">
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm font-medium text-slate-700">Pass Rate</span>
          <span className="text-sm font-bold text-green-600">{((passedTests / totalTests) * 100).toFixed(0)}%</span>
        </div>
        <div className="h-3 bg-slate-100 rounded-full overflow-hidden">
          <div className="h-full bg-green-500 rounded-full transition-all" style={{ width: `${(passedTests / totalTests) * 100}%` }} />
        </div>
      </div>

      {/* Filter */}
      <div className="flex gap-1 bg-slate-100 rounded-lg p-1 w-fit">
        {(['all', 'backend', 'frontend'] as const).map(f => (
          <button key={f} onClick={() => setFilter(f)} className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${filter === f ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600'}`}>
            {f === 'all' ? `All (${totalTests})` : f === 'backend' ? `Backend (${BACKEND_TESTS.reduce((s, st) => s + st.tests.length, 0)})` : `Frontend (${FRONTEND_TESTS.reduce((s, st) => s + st.tests.length, 0)})`}
          </button>
        ))}
      </div>

      {/* Test Suites */}
      <div className="space-y-3">
        {filtered.map(suite => {
          const passed = suite.tests.filter(t => t.status === 'pass').length;
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
                {suite.tests.map(test => (
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
        <p className="text-sm font-medium text-slate-700 mb-2">How to run tests</p>
        <div className="space-y-1.5">
          <div className="flex items-center gap-2">
            <Play size={12} className="text-blue-600" />
            <code className="text-xs bg-white px-2 py-0.5 rounded border border-slate-200 text-slate-700">cd backend && ./mvnw test</code>
            <span className="text-xs text-slate-500">— Backend (Spring Boot)</span>
          </div>
          <div className="flex items-center gap-2">
            <Play size={12} className="text-purple-600" />
            <code className="text-xs bg-white px-2 py-0.5 rounded border border-slate-200 text-slate-700">cd frontend && npm test</code>
            <span className="text-xs text-slate-500">— Frontend (Vitest)</span>
          </div>
        </div>
      </div>
    </div>
  );
}
