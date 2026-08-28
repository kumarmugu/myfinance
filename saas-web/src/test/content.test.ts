import { describe, it, expect } from 'vitest';
import { site } from '../content/site';

describe('site content config', () => {
  it('exposes a brand and value proposition', () => {
    expect(site.brand).toBe('MyFinance');
    expect(site.valueProp.length).toBeGreaterThan(0);
  });

  it('only lists real MyFinance feature keys', () => {
    const realFeatureKeys = new Set([
      'PORTFOLIO', 'CRYPTO', 'DIVIDENDS', 'CASH_FLOWS', 'BANK_SAVINGS', 'FIXED_DEPOSITS',
      'SL_FD', 'REAL_ESTATE', 'PRECIOUS_METALS', 'INSURANCE', 'HOME_LOANS', 'SALARY',
      'TAX', 'WORK_EXPERIENCE', 'SRS_CPF', 'REPORTS', 'BUDGET',
    ]);
    for (const f of site.features) {
      expect(realFeatureKeys.has(f.key)).toBe(true);
    }
  });

  it('has at least one FAQ and how-it-works step', () => {
    expect(site.faqs.length).toBeGreaterThan(0);
    expect(site.howItWorks.length).toBeGreaterThan(0);
  });
});
