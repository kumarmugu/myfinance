import { describe, it, expect, beforeEach } from 'vitest';
import * as authApi from '../api/authApi';
import * as portalApi from '../api/portalApi';
import * as publicApi from '../api/publicApi';
import { getToken, setToken, clearToken } from '../api/client';

/**
 * Export-surface and token-storage checks. These guard that the API modules expose the
 * expected functions and that token handling is consistent.
 */
describe('api modules', () => {
  it('authApi exposes the expected functions', () => {
    ['signup', 'login', 'verifyEmail', 'forgotPassword', 'resetPassword'].forEach((fn) => {
      expect(typeof (authApi as Record<string, unknown>)[fn]).toBe('function');
    });
  });

  it('portalApi exposes the expected functions', () => {
    ['getSubscription', 'getPayments', 'startCheckout', 'cancelSubscription'].forEach((fn) => {
      expect(typeof (portalApi as Record<string, unknown>)[fn]).toBe('function');
    });
  });

  it('publicApi exposes the expected functions', () => {
    expect(typeof publicApi.fetchPublicConfig).toBe('function');
    expect(typeof publicApi.fetchPlans).toBe('function');
  });
});

describe('token storage', () => {
  beforeEach(() => clearToken());

  it('stores, reads, and clears the portal token', () => {
    expect(getToken()).toBeNull();
    setToken('abc.def.ghi');
    expect(getToken()).toBe('abc.def.ghi');
    clearToken();
    expect(getToken()).toBeNull();
  });
});
