import { describe, it, expect } from 'vitest';
import {
  transactionsExportConfig,
  accountsExportConfig,
  dividendsExportConfig,
  holdingsExportConfig,
  salaryExportConfig,
  workExperienceExportConfig,
  bankSavingsExportConfig,
  propertiesExportConfig,
  preciousMetalsExportConfig,
  genericFdExportConfig,
  homeLoansExportConfig,
  taxExportConfig,
  soldPositionsExportConfig,
  sriLankaFdExportConfig,
  insuranceExportConfig,
} from './configs';
import { buildCsv } from './csv';
import type { Transaction, Account } from '../../types';

describe('export/configs — all user-relevant columns, sensitive fields excluded', () => {
  it('transactions config exports columns the UI hides (ID, fees, currency, createdAt)', () => {
    const keys = transactionsExportConfig.columns.map(c => c.key);
    // UI table only shows: type, symbol, account, owner, purpose, qty, price, total.
    // Export must ALSO include these otherwise-hidden columns:
    expect(keys).toEqual(expect.arrayContaining(['id', 'fees', 'currency', 'notes', 'createdAt']));
  });

  it('transactions export preserves original currency + amount (no conversion)', () => {
    const tx = {
      id: 7, transactionType: 'BUY', quantity: 10, pricePerUnit: 100000, fees: 0,
      totalAmount: 1000000, currency: 'LKR', transactionDate: '2026-08-31', createdAt: '2026-08-31T00:00:00Z',
      notes: '', purpose: 'LONG_TERM',
      asset: { symbol: 'ABC', name: 'Alpha' }, account: { name: 'Broker' }, owner: { name: 'Self' },
    } as unknown as Transaction;
    const csv = buildCsv([tx], transactionsExportConfig.columns);
    expect(csv).toContain('1000000'); // original amount kept
    expect(csv).toContain('LKR');     // original currency kept
  });

  it('accounts config EXCLUDES the sensitive account number', () => {
    const keys = accountsExportConfig.columns.map(c => c.key);
    expect(keys).not.toContain('accountNumber');
    // But still exports otherwise-hidden columns:
    expect(keys).toEqual(expect.arrayContaining(['id', 'createdAt', 'updatedAt', 'ownerRelationship']));
  });

  it('accounts export does not leak an account number even if present on the object', () => {
    const acc = {
      id: 1, name: 'My Broker', accountType: 'BROKER', currency: 'USD',
      accountNumber: '1234567890', description: '', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z',
      owner: { name: 'Self', relationship: 'SELF' },
    } as unknown as Account;
    const csv = buildCsv([acc], accountsExportConfig.columns);
    expect(csv).not.toContain('1234567890');
  });

  it('dividends and holdings configs include hidden columns (id, currency)', () => {
    expect(dividendsExportConfig.columns.map(c => c.key)).toEqual(
      expect.arrayContaining(['id', 'currency', 'notes']),
    );
    expect(holdingsExportConfig.columns.map(c => c.key)).toEqual(
      expect.arrayContaining(['id', 'currency', 'updatedAt', 'assetType']),
    );
  });

  it('every column has a header and accessor', () => {
    for (const cfg of [transactionsExportConfig, accountsExportConfig, dividendsExportConfig, holdingsExportConfig]) {
      for (const col of cfg.columns) {
        expect(col.header).toBeTruthy();
        expect(typeof col.accessor).toBe('function');
      }
    }
  });
});

describe('export/configs — additional table configs exclude sensitive fields', () => {
  it('bank savings, generic FD, SL FD exclude account numbers; insurance excludes policy number', () => {
    expect(bankSavingsExportConfig.columns.map(c => c.key)).not.toContain('accountNumber');
    expect(genericFdExportConfig.columns.map(c => c.key)).not.toContain('accountNumber');
    expect(sriLankaFdExportConfig.columns.map(c => c.key)).not.toContain('accountNumber');
    expect(insuranceExportConfig.columns.map(c => c.key)).not.toContain('policyNumber');
  });

  it('salary/work-experience/property/metals/tax configs include hidden columns (id, notes)', () => {
    for (const cfg of [salaryExportConfig, workExperienceExportConfig, propertiesExportConfig, preciousMetalsExportConfig, taxExportConfig, homeLoansExportConfig, soldPositionsExportConfig]) {
      const keys = cfg.columns.map(c => c.key);
      expect(keys).toContain('id');
      expect(keys).toContain('notes');
    }
  });

  it('every additional config column has a header and accessor', () => {
    for (const cfg of [salaryExportConfig, workExperienceExportConfig, bankSavingsExportConfig, propertiesExportConfig, preciousMetalsExportConfig, genericFdExportConfig, homeLoansExportConfig, taxExportConfig, soldPositionsExportConfig, sriLankaFdExportConfig, insuranceExportConfig]) {
      expect(cfg.entity).toBeTruthy();
      expect(cfg.title).toBeTruthy();
      for (const col of cfg.columns) {
        expect(col.header).toBeTruthy();
        expect(typeof col.accessor).toBe('function');
      }
    }
  });
});
