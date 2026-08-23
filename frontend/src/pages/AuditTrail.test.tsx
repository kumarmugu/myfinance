import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import AuditTrail from './AuditTrail';

// Mock the api module
vi.mock('../api', () => {
  const mockApi = {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
    defaults: { headers: { common: {} } },
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
    create: vi.fn(),
  };
  return { default: mockApi };
});

import api from '../api';

const mockAuditData = {
  content: [
    { id: 1, userId: 1, username: 'admin', action: 'CREATE', entity: 'Account', entityId: 5, details: 'Account.create', timestamp: '2024-06-15T10:30:00' },
    { id: 2, userId: 2, username: 'mugu', action: 'UPDATE', entity: 'Asset', entityId: 3, details: 'Asset.update', timestamp: '2024-06-15T11:00:00' },
    { id: 3, userId: 2, username: 'mugu', action: 'DELETE', entity: 'Transaction', entityId: 12, details: 'Transaction.delete', timestamp: '2024-06-15T14:30:00' },
  ],
  totalPages: 1,
  totalElements: 3,
  number: 0,
  size: 30,
};

describe('AuditTrail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.get as any).mockResolvedValue({ data: mockAuditData });
  });

  it('renders page title and description', async () => {
    render(<AuditTrail />);
    expect(screen.getByText('Audit Trail')).toBeInTheDocument();
    expect(screen.getByText('Track all user actions across the system')).toBeInTheDocument();
  });

  it('loads and displays audit entries', async () => {
    render(<AuditTrail />);
    await waitFor(() => {
      expect(screen.getByText('admin')).toBeInTheDocument();
    });
    expect(screen.getByText('Account')).toBeInTheDocument();
    expect(screen.getByText('Asset')).toBeInTheDocument();
    expect(screen.getByText('Transaction')).toBeInTheDocument();
  });

  it('displays action badges with correct labels', async () => {
    render(<AuditTrail />);
    await waitFor(() => {
      expect(screen.getByText('CREATE')).toBeInTheDocument();
      expect(screen.getByText('UPDATE')).toBeInTheDocument();
      expect(screen.getByText('DELETE')).toBeInTheDocument();
    });
  });

  it('shows total entries count', async () => {
    render(<AuditTrail />);
    await waitFor(() => {
      expect(screen.getByText('3 entries found')).toBeInTheDocument();
    });
  });

  it('renders filter controls', async () => {
    render(<AuditTrail />);
    await waitFor(() => expect(api.get).toHaveBeenCalled());
    // Find the action select by its option text
    expect(screen.getByText('All Actions')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('e.g. Account, Asset')).toBeInTheDocument();
    expect(screen.getByText('Clear')).toBeInTheDocument();
  });

  it('calls API with action filter', async () => {
    render(<AuditTrail />);
    await waitFor(() => expect(api.get).toHaveBeenCalled());

    // Find the select element (it's the only select in the component)
    const actionSelect = document.querySelector('select') as HTMLSelectElement;
    fireEvent.change(actionSelect, { target: { value: 'CREATE' } });

    await waitFor(() => {
      const lastCall = (api.get as any).mock.calls[(api.get as any).mock.calls.length - 1][0];
      expect(lastCall).toContain('action=CREATE');
    });
  });

  it('calls API with entity filter', async () => {
    render(<AuditTrail />);
    await waitFor(() => expect(api.get).toHaveBeenCalled());

    const entityInput = screen.getByPlaceholderText('e.g. Account, Asset');
    fireEvent.change(entityInput, { target: { value: 'Account' } });

    await waitFor(() => {
      const lastCall = (api.get as any).mock.calls[(api.get as any).mock.calls.length - 1][0];
      expect(lastCall).toContain('entity=Account');
    });
  });

  it('clears all filters', async () => {
    render(<AuditTrail />);
    await waitFor(() => expect(api.get).toHaveBeenCalled());

    // Set a filter
    const entityInput = screen.getByPlaceholderText('e.g. Account, Asset');
    fireEvent.change(entityInput, { target: { value: 'Account' } });

    // Click clear
    const clearBtn = screen.getByText('Clear');
    fireEvent.click(clearBtn);

    await waitFor(() => {
      expect((entityInput as HTMLInputElement).value).toBe('');
    });
  });

  it('shows empty state when no entries', async () => {
    (api.get as any).mockResolvedValue({ data: { content: [], totalPages: 0, totalElements: 0, number: 0, size: 30 } });
    render(<AuditTrail />);
    await waitFor(() => {
      expect(screen.getByText('No audit entries found')).toBeInTheDocument();
    });
  });

  it('shows loading spinner initially', () => {
    (api.get as any).mockReturnValue(new Promise(() => {})); // never resolves
    render(<AuditTrail />);
    expect(screen.getByText('Audit Trail')).toBeInTheDocument();
    // Loading spinner should be visible (the animate-spin div)
    const spinner = document.querySelector('.animate-spin');
    expect(spinner).toBeInTheDocument();
  });
});
