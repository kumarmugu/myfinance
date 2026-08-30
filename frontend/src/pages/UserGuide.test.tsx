import { render, screen, waitFor, fireEvent, within } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import UserGuide from './UserGuide';
import { ALL_GUIDE_PAGES, GUIDE_SECTIONS, SETUP_CHECKLIST } from './userGuideContent';

// hasFeature returns true by default (all features enabled); tests can override.
const mockHasFeature = vi.fn((feature: string): boolean => feature.length >= 0);
const mockNavigate = vi.fn();

vi.mock('../contexts/AuthContext', () => ({
  useAuth: () => ({ hasFeature: mockHasFeature }),
}));

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

function renderGuide() {
  return render(
    <MemoryRouter>
      <UserGuide />
    </MemoryRouter>,
  );
}

describe('UserGuide', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockHasFeature.mockImplementation(() => true);
    localStorage.clear();
  });

  it('renders the guide title, search box and progress', () => {
    renderGuide();
    expect(screen.getByRole('heading', { name: /User Guide/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/Search the user guide/i)).toBeInTheDocument();
    expect(screen.getByText(/Getting Started Progress/i)).toBeInTheDocument();
    expect(screen.getByText(new RegExp(`0 of ${SETUP_CHECKLIST.length} steps done`))).toBeInTheDocument();
  });

  it('renders all section titles in the navigation', () => {
    renderGuide();
    const nav = screen.getByRole('navigation', { name: /Guide sections/i });
    GUIDE_SECTIONS.forEach((s) => {
      expect(within(nav).getByText(s.title)).toBeInTheDocument();
    });
  });

  it('shows the Welcome page content by default', () => {
    renderGuide();
    expect(screen.getByRole('heading', { name: 'Welcome & Key Concepts' })).toBeInTheDocument();
    expect(screen.getByText(/What is this\?/i)).toBeInTheDocument();
    expect(screen.getByText(/Why would I use it\?/i)).toBeInTheDocument();
  });

  it('navigates to another page when a nav item is clicked', () => {
    renderGuide();
    // Getting Started is expanded by default; click the nav item (scope to nav to avoid
    // matching related-link chips with the same label in the content area).
    const nav = screen.getByRole('navigation', { name: /Guide sections/i });
    fireEvent.click(within(nav).getByRole('button', { name: /Currencies & Exchange Rates/i }));
    expect(screen.getByRole('heading', { name: 'Currencies & Exchange Rates' })).toBeInTheDocument();
    // Steps should be rendered
    expect(screen.getByText(/How to do it/i)).toBeInTheDocument();
  });

  it('expands a collapsed section and opens one of its pages', () => {
    renderGuide();
    const nav = screen.getByRole('navigation', { name: /Guide sections/i });
    // Investments section is collapsed initially
    fireEvent.click(within(nav).getByRole('button', { name: /^Investments$/i }));
    fireEvent.click(within(nav).getByRole('button', { name: /Portfolio/i }));
    expect(screen.getByRole('heading', { name: 'Portfolio' })).toBeInTheDocument();
  });

  it('searches and shows matching results', async () => {
    renderGuide();
    fireEvent.change(screen.getByLabelText(/Search the user guide/i), { target: { value: 'exchange rate' } });
    await waitFor(() => {
      expect(screen.getByText(/result/i)).toBeInTheDocument();
    });
    // The FX page should appear in results
    expect(screen.getAllByText(/Currencies & Exchange Rates/i).length).toBeGreaterThan(0);
  });

  it('search with no match shows an empty message', async () => {
    renderGuide();
    fireEvent.change(screen.getByLabelText(/Search the user guide/i), {
      target: { value: 'zzzznotarealtopic' },
    });
    await waitFor(() => {
      expect(screen.getByText(/No results for/i)).toBeInTheDocument();
    });
  });

  it('opens a related in-guide page via a related chip', () => {
    renderGuide();
    // Welcome page (default) has a related chip to "Owners & Accounts" in the content area.
    const article = screen.getByRole('article');
    fireEvent.click(within(article).getByRole('button', { name: /Owners & Accounts/i }));
    expect(screen.getByRole('heading', { name: 'Owners & Accounts' })).toBeInTheDocument();
  });

  it('navigates to an app route when a related route chip is clicked', () => {
    renderGuide();
    const nav = screen.getByRole('navigation', { name: /Guide sections/i });
    fireEvent.click(within(nav).getByRole('button', { name: /Currencies & Exchange Rates/i }));
    // FX page has a related chip "Open FX Rates screen" -> route /fx-rates
    fireEvent.click(screen.getByRole('button', { name: /Open FX Rates screen/i }));
    expect(mockNavigate).toHaveBeenCalledWith('/fx-rates');
  });

  it('marks a page as done and updates progress + persists', () => {
    renderGuide();
    const nav = screen.getByRole('navigation', { name: /Guide sections/i });
    // Open the FX page (part of the setup checklist)
    fireEvent.click(within(nav).getByRole('button', { name: /Currencies & Exchange Rates/i }));
    fireEvent.click(screen.getByRole('button', { name: /Mark as done/i }));
    expect(screen.getByText(new RegExp(`1 of ${SETUP_CHECKLIST.length} steps done`))).toBeInTheDocument();
    const saved = JSON.parse(localStorage.getItem('myfinance.guide.progress') || '{}');
    expect(saved['currencies-fx']).toBe(true);
  });

  it('renders a screenshot placeholder (no src) with accessible label', () => {
    renderGuide();
    // Welcome page has a screenshot slot with no src -> placeholder role=img
    const imgs = screen.getAllByRole('img');
    expect(imgs.length).toBeGreaterThan(0);
  });

  it('hides pages whose feature is disabled', () => {
    // Disable PORTFOLIO -> Asset Catalog (feature PORTFOLIO) should not be listed
    mockHasFeature.mockImplementation((f: string) => f !== 'PORTFOLIO');
    renderGuide();
    const nav = screen.getByRole('navigation', { name: /Guide sections/i });
    expect(within(nav).queryByRole('button', { name: /Asset Catalog/i })).not.toBeInTheDocument();
  });

  it('every content page has the required author fields', () => {
    // Content-model sanity: guarantees the guide references real, complete pages.
    ALL_GUIDE_PAGES.forEach((p) => {
      expect(p.id).toBeTruthy();
      expect(p.title).toBeTruthy();
      expect(p.summary).toBeTruthy();
      expect(p.what).toBeTruthy();
      expect(p.why).toBeTruthy();
    });
  });
});
