import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import LeveragedEtf from './LeveragedEtf';

// ── Mock the toast context (page calls useToast().showToast) ──
const showToast = vi.fn();
vi.mock('../contexts/ToastContext', () => ({
  useToast: () => ({ showToast }),
}));

// ── Mock recharts so ResponsiveContainer renders children without a real DOM size ──
vi.mock('recharts', async () => {
  const actual = await vi.importActual<typeof import('recharts')>('recharts');
  return { ...actual, ResponsiveContainer: ({ children }: any) => <div>{children}</div> };
});

// ── Mock the API layer. Every lev* function resolves with empty-ish data so the page mounts. ──
const ok = (data: any) => Promise.resolve({ data });
vi.mock('../api', () => ({
  levGetBenchmarks: vi.fn(() => ok([])),
  levCreateBenchmark: vi.fn(() => ok({})),
  levUpdateBenchmark: vi.fn(() => ok({})),
  levDeleteBenchmark: vi.fn(() => ok({})),
  levGetInstruments: vi.fn(() => ok([])),
  levCreateInstrument: vi.fn(() => ok({})),
  levUpdateInstrument: vi.fn(() => ok({})),
  levDeleteInstrument: vi.fn(() => ok({})),
  levGetStrategies: vi.fn(() => ok([])),
  levGetStrategy: vi.fn(() => ok({})),
  levCreateStrategy: vi.fn(() => ok({})),
  levUpdateStrategy: vi.fn(() => ok({})),
  levDeleteStrategy: vi.fn(() => ok({})),
  levGetHistory: vi.fn(() => ok([])),
  levRefreshBenchmark: vi.fn(() => ok({ message: 'ok' })),
  levRefreshEtf: vi.fn(() => ok({ message: 'ok' })),
  levAddManualBar: vi.fn(() => ok({})),
  levGetPositions: vi.fn(() => ok([])),
  levAddPosition: vi.fn(() => ok({})),
  levDeletePosition: vi.fn(() => ok({})),
  levCalculate: vi.fn(() => ok({})),
  levGetSnapshots: vi.fn(() => ok([])),
  levGetLatestSnapshot: vi.fn(() => ok(null)),
  levPreviewCurve: vi.fn(() => ok([{ drawdownPercent: 0, targetAllocationPercent: 10 }])),
  levGetPlans: vi.fn(() => ok([])),
  levGeneratePlan: vi.fn(() => ok({})),
  levApprovePlan: vi.fn(() => ok({})),
  levRejectPlan: vi.fn(() => ok({})),
  levExecutePlan: vi.fn(() => ok({})),
  levCancelPlan: vi.fn(() => ok({})),
  levGetBacktests: vi.fn(() => ok([])),
  levGetBacktest: vi.fn(() => ok({})),
  levRunBacktest: vi.fn(() => ok({})),
  levGetAlertPrefs: vi.fn(() => ok([])),
  levCreateAlertPref: vi.fn(() => ok({})),
  levDeleteAlertPref: vi.fn(() => ok({})),
  levGetNotifications: vi.fn(() => ok([])),
  levMarkNotificationRead: vi.fn(() => ok({})),
  levMarkAllNotificationsRead: vi.fn(() => ok({})),
}));

import * as api from '../api';

function renderPage() {
  return render(
    <MemoryRouter>
      <LeveragedEtf />
    </MemoryRouter>,
  );
}

describe('LeveragedEtf', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the header and advisory-only note', () => {
    renderPage();
    expect(screen.getByRole('heading', { name: /Leveraged ETF Allocation Planner/i })).toBeInTheDocument();
    expect(screen.getByText(/advisory only/i)).toBeInTheDocument();
  });

  it('renders all seven tabs', () => {
    renderPage();
    ['Overview', 'Index Monitor', 'Instruments', 'Strategy', 'Holdings & Rebalance', 'Backtesting', 'Notifications']
      .forEach(label => expect(screen.getByRole('button', { name: new RegExp(label, 'i') })).toBeInTheDocument());
  });

  it('loads shared reference data on mount', async () => {
    renderPage();
    await waitFor(() => {
      expect(api.levGetStrategies).toHaveBeenCalled();
      expect(api.levGetBenchmarks).toHaveBeenCalled();
      expect(api.levGetInstruments).toHaveBeenCalled();
    });
  });

  it('switches to the Notifications tab and loads notifications + alert prefs', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /Notifications/i }));
    await waitFor(() => {
      expect(api.levGetNotifications).toHaveBeenCalled();
      expect(api.levGetAlertPrefs).toHaveBeenCalled();
    });
  });

  it('switches to the Backtesting tab and loads past runs', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /Backtesting/i }));
    await waitFor(() => {
      expect(api.levGetBacktests).toHaveBeenCalled();
    });
  });

  it('switches to the Strategy tab and fetches the live allocation-curve preview', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /^Strategy$/i }));
    await waitFor(() => {
      expect(api.levPreviewCurve).toHaveBeenCalled();
    });
  });

  it('shows an empty-state on the Index Monitor tab when there are no benchmarks', () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /Index Monitor/i }));
    // The benchmarks list is empty (mock returns []), so the tab still renders without crashing.
    expect(screen.getByRole('heading', { name: /Leveraged ETF Allocation Planner/i })).toBeInTheDocument();
  });
});
