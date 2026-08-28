import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Signup from '../pages/Signup';
import * as authApi from '../api/authApi';
import { ConfigProvider } from '../context/ConfigContext';

// Prevent the ConfigProvider's network call from failing the test environment.
vi.mock('../api/publicApi', () => ({
  fetchPublicConfig: vi.fn().mockResolvedValue({
    trialDays: 7, paymentProvider: 'stripe', stripePublishableKey: 'pk_test', loginUrl: 'http://finance.test',
  }),
  fetchPlans: vi.fn().mockResolvedValue([]),
}));

function renderSignup() {
  return render(
    <ConfigProvider>
      <MemoryRouter initialEntries={['/signup']}>
        <Signup />
      </MemoryRouter>
    </ConfigProvider>,
  );
}

describe('Signup', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('blocks submission with a weak password', async () => {
    const signupSpy = vi.spyOn(authApi, 'signup');
    renderSignup();

    fireEvent.change(screen.getByLabelText(/full name/i), { target: { value: 'Jane Doe' } });
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'jane@example.com' } });
    fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: 'short' } });
    fireEvent.click(screen.getByLabelText(/i agree to the/i));
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/at least 8 characters/i);
    expect(signupSpy).not.toHaveBeenCalled();
  });

  it('blocks submission when terms are not accepted', async () => {
    const signupSpy = vi.spyOn(authApi, 'signup');
    renderSignup();

    fireEvent.change(screen.getByLabelText(/full name/i), { target: { value: 'Jane Doe' } });
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'jane@example.com' } });
    fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: 'password1' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/accept the terms/i);
    expect(signupSpy).not.toHaveBeenCalled();
  });

  it('submits valid input and shows the check-email screen', async () => {
    const signupSpy = vi.spyOn(authApi, 'signup').mockResolvedValue({ message: 'ok', provisioned: true });
    renderSignup();

    fireEvent.change(screen.getByLabelText(/full name/i), { target: { value: 'Jane Doe' } });
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'jane@example.com' } });
    fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: 'password1' } });
    fireEvent.click(screen.getByLabelText(/i agree to the/i));
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => expect(signupSpy).toHaveBeenCalled());
    expect(await screen.findByText(/check your email/i)).toBeInTheDocument();

    // Honeypot is passed (empty for a human) as the second arg.
    const call = signupSpy.mock.calls[0];
    expect(call[0]).toMatchObject({ email: 'jane@example.com', acceptTerms: true });
  });
});
