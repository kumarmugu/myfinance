import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ToastProvider } from '../contexts/ToastContext';

// ─── Mocks ───
vi.mock('../api', () => ({
  getTransactions: vi.fn(),
  createTransaction: vi.fn(),
  deleteTransaction: vi.fn(),
  getAssets: vi.fn(),
  getAccounts: vi.fn(),
  getOwners: vi.fn(),
}));

vi.mock('../contexts/AuthContext', () => ({
  useAuth: () => ({ verifyPassword: vi.fn().mockResolvedValue(true) }),
}));

// Spy on the shared export entry point (keep the rest of the module real).
const exportTable = vi.fn().mockResolvedValue(undefined);
vi.mock('../utils/export', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../utils/export')>();
  return { ...actual, exportTable: (...args: any[]) => exportTable(...args) };
});

import Transactions from './Transactions';
import { getTransactions, getAssets, getAccounts, getOwners } from '../api';

// The UI table shows only: Date, Type, Asset, Account, Owner, Purpose, Qty, Price, Total.
// The full transaction object also carries id, fees, currency, notes, createdAt — which must be exported.
const fullTransaction = {
  id: 42,
  transactionType: 'BUY',
  quantity: 5,
  pricePerUnit: 200000,
  fees: 150,
  totalAmount: 1000000,
  currency: 'LKR',
  transactionDate: '2026-08-31',
  createdAt: '2026-08-31T09:30:00Z',
  notes: 'quarterly buy',
  purpose: 'LONG_TERM',
  asset: { id: 1, symbol: 'LKF', name: 'Lanka Fund' },
  account: { id: 1, name: 'CSE Broker' },
  owner: { id: 1, name: 'Self' },
};

function renderPage() {
  return render(<ToastProvider><Transactions /></ToastProvider>);
}

describe('Transactions page — export integration', () => {
  beforeEach(() => {
    exportTable.mockClear();
    (getTransactions as any).mockResolvedValue({ data: [fullTransaction] });
    (getAssets as any).mockResolvedValue({ data: [] });
    (getAccounts as any).mockResolvedValue({ data: [] });
    (getOwners as any).mockResolvedValue({ data: [{ id: 1, name: 'Self' }] });
  });

  it('shows the Export button on the transactions table', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('Transactions')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /export table/i })).toBeInTheDocument();
  });

  it('exports the complete loaded dataset with ALL columns, including UI-hidden ones', async () => {
    renderPage();
    await waitFor(() => expect(getTransactions).toHaveBeenCalled());

    fireEvent.click(screen.getByRole('button', { name: /export table/i }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'CSV' }));

    await waitFor(() => expect(exportTable).toHaveBeenCalledTimes(1));
    const [rows, config, format] = exportTable.mock.calls[0];

    // Full dataset passed through (the whole object, not a projection of visible cells).
    expect(format).toBe('csv');
    expect(rows).toHaveLength(1);
    expect(rows[0]).toBe(fullTransaction);

    // Config includes columns the UI table hides.
    const keys = config.columns.map((c: any) => c.key);
    expect(keys).toEqual(expect.arrayContaining(['id', 'fees', 'currency', 'notes', 'createdAt']));

    // And original currency/amount are preserved verbatim by the accessors.
    const byKey = Object.fromEntries(config.columns.map((c: any) => [c.key, c.accessor(fullTransaction)]));
    expect(byKey.totalAmount).toBe(1000000);
    expect(byKey.currency).toBe('LKR');
    expect(byKey.fees).toBe(150);
  });
});
