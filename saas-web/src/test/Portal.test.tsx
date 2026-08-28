import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Portal from '../pages/Portal';
import { ConfigProvider } from '../context/ConfigContext';
import { setToken, clearToken } from '../api/client';

vi.mock('../api/publicApi', () => ({
  fetchPublicConfig: vi.fn().mockResolvedValue({
    trialDays: 7, paymentProvider: 'stripe', stripePublishableKey: 'pk_test', loginUrl: 'http://finance.test',
  }),
  fetchPlans: vi.fn().mockResolvedValue([]),
}));

vi.mock('../api/portalApi', () => ({
  getSubscription: vi.fn().mockResolvedValue({
    state: 'TRIAL', planCode: 'free_trial', planName: 'Free Trial', inTrial: true,
    trialEndsAt: new Date(Date.now() + 5 * 86400000).toISOString(), trialDaysRemaining: 5,
    currentPeriodEnd: null, cancelledAt: null, grantsAccess: true,
  }),
  getPayments: vi.fn().mockResolvedValue([]),
  startCheckout: vi.fn(),
  cancelSubscription: vi.fn(),
}));

function renderPortal() {
  return render(
    <ConfigProvider>
      <MemoryRouter initialEntries={['/portal/billing']}>
        <Portal />
      </MemoryRouter>
    </ConfigProvider>,
  );
}

describe('Portal', () => {
  beforeEach(() => clearToken());

  it('shows the login gate when there is no token', async () => {
    renderPortal();
    expect(await screen.findByRole('heading', { name: /billing portal login/i })).toBeInTheDocument();
  });

  it('shows the subscription dashboard when authenticated', async () => {
    setToken('fake.jwt.token');
    renderPortal();

    expect(await screen.findByRole('heading', { name: /your subscription/i })).toBeInTheDocument();
    expect(await screen.findByText(/Free Trial/i)).toBeInTheDocument();
    expect(screen.getByText(/5 day\(s\) remaining/i)).toBeInTheDocument();
    clearToken();
  });

  it('does not link to finance-app internal pages from the portal', async () => {
    setToken('fake.jwt.token');
    renderPortal();
    await waitFor(() => screen.getByRole('heading', { name: /your subscription/i }));
    // The only finance-app link is the explicit "Open the app" login link.
    const links = screen.getAllByRole('link');
    for (const link of links) {
      const href = link.getAttribute('href') ?? '';
      // No links into internal finance routes like /portfolio, /dashboard, /admin, etc.
      expect(href).not.toMatch(/\/(portfolio|dashboard|admin|transactions)/);
    }
    clearToken();
  });
});
