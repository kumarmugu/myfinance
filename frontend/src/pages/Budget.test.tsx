import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import Budget from './Budget';
import { ToastProvider } from '../contexts/ToastContext';

// Mock the api module
vi.mock('../api', () => {
  const mockApi = {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
    defaults: { headers: { common: {} } },
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
    create: vi.fn(),
  };
  return { default: mockApi };
});

import api from '../api';

const mockCategories = [
  { id: 1, name: 'Groceries', parentCategory: 'Essential' },
  { id: 2, name: 'Rent', parentCategory: 'Essential' },
  { id: 3, name: 'Entertainment', parentCategory: 'Lifestyle' },
];

function renderBudget() {
  return render(<ToastProvider><Budget /></ToastProvider>);
}

describe('Budget', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.get as any).mockImplementation((url: string) => {
      if (url === '/budget/categories') return Promise.resolve({ data: mockCategories });
      if (url.includes('/budget/plans/')) return Promise.resolve({ status: 204, data: null });
      if (url.includes('/expenses')) return Promise.resolve({ data: [] });
      if (url.includes('/budget/report/')) return Promise.resolve({ data: null });
      return Promise.resolve({ data: [] });
    });
  });

  it('renders page title and description', async () => {
    renderBudget();
    await waitFor(() => expect(screen.getByText('Budget & Expenses')).toBeInTheDocument());
  });

  it('renders all four tabs', async () => {
    renderBudget();
    await waitFor(() => expect(api.get).toHaveBeenCalled());
    expect(screen.getByText('Plan')).toBeInTheDocument();
    expect(screen.getByText('Expenses')).toBeInTheDocument();
    expect(screen.getByText('Report')).toBeInTheDocument();
    expect(screen.getByText('Categories')).toBeInTheDocument();
  });

  it('shows plan tab summary cards by default', async () => {
    renderBudget();
    await waitFor(() => expect(screen.getByText('Budget & Expenses')).toBeInTheDocument());
    await waitFor(() => {
      expect(screen.getByText('Total Income')).toBeInTheDocument();
      expect(screen.getByText('Available for Expenses')).toBeInTheDocument();
    });
  });

  it('switches to Categories tab and shows category management', async () => {
    renderBudget();
    await waitFor(() => expect(api.get).toHaveBeenCalledWith('/budget/categories'));

    fireEvent.click(screen.getByText('Categories'));

    await waitFor(() => {
      expect(screen.getByText('Add Expense Category')).toBeInTheDocument();
      expect(screen.getByPlaceholderText('Category name')).toBeInTheDocument();
    });
    // Existing categories shown
    expect(screen.getByText('Groceries')).toBeInTheDocument();
    expect(screen.getByText('Rent')).toBeInTheDocument();
  });

  it('adds a new category', async () => {
    (api.post as any).mockResolvedValue({ data: { id: 4, name: 'Gym', parentCategory: 'Lifestyle' } });
    renderBudget();
    await waitFor(() => expect(api.get).toHaveBeenCalledWith('/budget/categories'));

    fireEvent.click(screen.getByText('Categories'));
    await waitFor(() => expect(screen.getByPlaceholderText('Category name')).toBeInTheDocument());

    fireEvent.change(screen.getByPlaceholderText('Category name'), { target: { value: 'Gym' } });
    fireEvent.click(screen.getByText('Add Category'));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/budget/categories', expect.objectContaining({ name: 'Gym' }));
    });
  });

  it('switches to Expenses tab', async () => {
    renderBudget();
    await waitFor(() => expect(api.get).toHaveBeenCalled());

    fireEvent.click(screen.getByText('Expenses'));
    await waitFor(() => {
      expect(screen.getByText('Add Expense')).toBeInTheDocument();
    });
  });

  it('switches to Report tab and shows empty state', async () => {
    renderBudget();
    await waitFor(() => expect(api.get).toHaveBeenCalled());

    fireEvent.click(screen.getByText('Report'));
    await waitFor(() => {
      expect(screen.getByText(/No report data available/i)).toBeInTheDocument();
    });
  });

  it('has month and year selectors', async () => {
    renderBudget();
    await waitFor(() => expect(api.get).toHaveBeenCalled());
    // Month dropdown has Jan-Dec, year has 2024-2027
    expect(screen.getByText('Jan')).toBeInTheDocument();
    expect(screen.getByText('2026')).toBeInTheDocument();
  });
});
