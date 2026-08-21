import { describe, it, expect } from 'vitest';
import * as api from '../api';

describe('API module exports', () => {
  it('exports owner CRUD functions', () => {
    expect(api.getOwners).toBeDefined();
    expect(api.createOwner).toBeDefined();
    expect(api.updateOwner).toBeDefined();
    expect(api.deleteOwner).toBeDefined();
  });

  it('exports account CRUD functions', () => {
    expect(api.getAccounts).toBeDefined();
    expect(api.createAccount).toBeDefined();
    expect(api.updateAccount).toBeDefined();
    expect(api.deleteAccount).toBeDefined();
  });

  it('exports asset functions', () => {
    expect(api.getAssets).toBeDefined();
    expect(api.getAssetTypes).toBeDefined();
    expect(api.createAsset).toBeDefined();
    expect(api.deleteAsset).toBeDefined();
    expect(api.toggleAssetNetWorth).toBeDefined();
  });

  it('exports transaction functions', () => {
    expect(api.getTransactions).toBeDefined();
    expect(api.createTransaction).toBeDefined();
    expect(api.deleteTransaction).toBeDefined();
  });

  it('exports currency rate functions', () => {
    expect(api.getCurrencyRates).toBeDefined();
    expect(api.getAvailableCurrencies).toBeDefined();
    expect(api.createCurrencyRate).toBeDefined();
    expect(api.updateCurrencyRate).toBeDefined();
    expect(api.deleteCurrencyRate).toBeDefined();
  });

  it('exports tax functions', () => {
    expect(api.getTaxRecords).toBeDefined();
    expect(api.getTaxSummary).toBeDefined();
    expect(api.createTaxRecord).toBeDefined();
    expect(api.updateTaxRecord).toBeDefined();
    expect(api.deleteTaxRecord).toBeDefined();
  });

  it('exports work experience functions', () => {
    expect(api.getWorkExperiences).toBeDefined();
    expect(api.createWorkExperience).toBeDefined();
    expect(api.updateWorkExperience).toBeDefined();
    expect(api.deleteWorkExperience).toBeDefined();
  });

  it('exports salary functions', () => {
    expect(api.getSalaryRecords).toBeDefined();
    expect(api.getSalarySummary).toBeDefined();
    expect(api.createSalaryRecord).toBeDefined();
    expect(api.updateSalaryRecord).toBeDefined();
    expect(api.deleteSalaryRecord).toBeDefined();
  });

  it('exports retirement fund functions', () => {
    expect(api.getRetirementFundEntries).toBeDefined();
    expect(api.getRetirementFundSummary).toBeDefined();
    expect(api.createRetirementFundEntry).toBeDefined();
    expect(api.deleteRetirementFundEntry).toBeDefined();
  });

  it('exports home loan functions', () => {
    expect(api.getHomeLoans).toBeDefined();
    expect(api.createHomeLoan).toBeDefined();
    expect(api.updateHomeLoan).toBeDefined();
    expect(api.deleteHomeLoan).toBeDefined();
    expect(api.getLoanPayments).toBeDefined();
    expect(api.createLoanPayment).toBeDefined();
    expect(api.deleteLoanPayment).toBeDefined();
  });

  it('exports insurance bonus functions', () => {
    expect(api.getInsuranceBonusEntries).toBeDefined();
    expect(api.createInsuranceBonusEntry).toBeDefined();
    expect(api.updateInsuranceBonusEntry).toBeDefined();
    expect(api.deleteInsuranceBonusEntry).toBeDefined();
  });
});
