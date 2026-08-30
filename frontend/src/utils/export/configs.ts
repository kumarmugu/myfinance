import type { ExportConfig } from './types';
import type { Transaction, Account, Dividend, Holding, FixedDeposit } from '../../types';

// ─── Central registry of table export configurations ───
//
// Each config lists the COMPLETE set of user-relevant columns for the entity — not
// just the columns the UI table happens to render. Sensitive/internal fields
// (masked account numbers are shown masked in the UI; password hashes, tokens, etc.)
// are deliberately excluded.
//
// Adding export to a new table is just: add a config here + drop <ExportMenu> on the page.

export const transactionsExportConfig: ExportConfig<Transaction> = {
  entity: 'transactions',
  title: 'Transactions',
  columns: [
    { key: 'id', header: 'ID', accessor: t => t.id, type: 'number' },
    { key: 'transactionDate', header: 'Date', accessor: t => t.transactionDate, type: 'date' },
    { key: 'transactionType', header: 'Type', accessor: t => t.transactionType },
    { key: 'assetSymbol', header: 'Asset Symbol', accessor: t => t.asset?.symbol },
    { key: 'assetName', header: 'Asset Name', accessor: t => t.asset?.name },
    { key: 'account', header: 'Account', accessor: t => t.account?.name },
    { key: 'owner', header: 'Owner', accessor: t => t.owner?.name },
    { key: 'purpose', header: 'Purpose', accessor: t => (t.purpose ? t.purpose.replace(/_/g, ' ') : '') },
    { key: 'quantity', header: 'Quantity', accessor: t => t.quantity, type: 'number' },
    { key: 'pricePerUnit', header: 'Price Per Unit', accessor: t => t.pricePerUnit, type: 'currency', currencyAccessor: t => t.currency },
    { key: 'fees', header: 'Fees', accessor: t => t.fees, type: 'currency', currencyAccessor: t => t.currency },
    { key: 'totalAmount', header: 'Total Amount', accessor: t => t.totalAmount, type: 'currency', currencyAccessor: t => t.currency },
    { key: 'currency', header: 'Currency', accessor: t => t.currency },
    { key: 'notes', header: 'Notes', accessor: t => t.notes },
    { key: 'createdAt', header: 'Created Date', accessor: t => t.createdAt, type: 'datetime' },
  ],
};

export const accountsExportConfig: ExportConfig<Account> = {
  entity: 'accounts',
  title: 'Accounts',
  columns: [
    { key: 'id', header: 'ID', accessor: a => a.id, type: 'number' },
    { key: 'name', header: 'Account Name', accessor: a => a.name },
    { key: 'accountType', header: 'Type', accessor: a => a.accountType },
    { key: 'currency', header: 'Currency', accessor: a => a.currency },
    { key: 'owner', header: 'Owner', accessor: a => a.owner?.name },
    { key: 'ownerRelationship', header: 'Owner Relationship', accessor: a => a.owner?.relationship },
    { key: 'description', header: 'Description', accessor: a => a.description },
    { key: 'createdAt', header: 'Created Date', accessor: a => a.createdAt, type: 'datetime' },
    { key: 'updatedAt', header: 'Updated Date', accessor: a => a.updatedAt, type: 'datetime' },
    // NOTE: accountNumber deliberately omitted — it is sensitive and masked in the UI.
  ],
};

export const dividendsExportConfig: ExportConfig<Dividend> = {
  entity: 'dividends',
  title: 'Dividends',
  columns: [
    { key: 'id', header: 'ID', accessor: d => d.id, type: 'number' },
    { key: 'receivedDate', header: 'Received Date', accessor: d => d.receivedDate, type: 'date' },
    { key: 'year', header: 'Year', accessor: d => d.year, type: 'number' },
    { key: 'quarter', header: 'Quarter', accessor: d => d.quarter },
    { key: 'instrument', header: 'Instrument', accessor: d => d.instrument },
    { key: 'assetSymbol', header: 'Asset Symbol', accessor: d => d.asset?.symbol },
    { key: 'account', header: 'Account', accessor: d => d.account?.name },
    { key: 'owner', header: 'Owner', accessor: d => d.owner?.name },
    { key: 'amount', header: 'Amount', accessor: d => d.amount, type: 'currency', currencyAccessor: d => d.currency },
    { key: 'currency', header: 'Currency', accessor: d => d.currency },
    { key: 'notes', header: 'Notes', accessor: d => d.notes },
  ],
};

export const holdingsExportConfig: ExportConfig<Holding> = {
  entity: 'holdings',
  title: 'Holdings',
  columns: [
    { key: 'id', header: 'ID', accessor: h => h.id, type: 'number' },
    { key: 'assetSymbol', header: 'Asset Symbol', accessor: h => h.asset?.symbol },
    { key: 'assetName', header: 'Asset Name', accessor: h => h.asset?.name },
    { key: 'assetType', header: 'Asset Type', accessor: h => h.asset?.assetType },
    { key: 'account', header: 'Account', accessor: h => h.account?.name },
    { key: 'owner', header: 'Owner', accessor: h => h.owner?.name },
    { key: 'purpose', header: 'Purpose', accessor: h => (h.purpose ? h.purpose.replace(/_/g, ' ') : '') },
    { key: 'quantity', header: 'Quantity', accessor: h => h.quantity, type: 'number' },
    { key: 'averageBuyPrice', header: 'Average Buy Price', accessor: h => h.averageBuyPrice, type: 'currency', currencyAccessor: h => h.currency },
    { key: 'investedAmount', header: 'Invested Amount', accessor: h => h.investedAmount, type: 'currency', currencyAccessor: h => h.currency },
    { key: 'currentPrice', header: 'Current Price', accessor: h => h.asset?.currentPrice, type: 'currency', currencyAccessor: h => h.currency },
    { key: 'currency', header: 'Currency', accessor: h => h.currency },
    { key: 'updatedAt', header: 'Updated Date', accessor: h => h.updatedAt, type: 'datetime' },
  ],
};

export const fixedDepositsExportConfig: ExportConfig<FixedDeposit> = {
  entity: 'fixed-deposits',
  title: 'Fixed Deposits',
  columns: [
    { key: 'id', header: 'ID', accessor: f => f.id, type: 'number' },
    { key: 'bank', header: 'Bank', accessor: f => f.bank?.name },
    { key: 'holder', header: 'Holder', accessor: f => f.holder?.name },
    { key: 'jointHolder', header: 'Joint Holder', accessor: f => f.jointHolder?.name },
    { key: 'accountNumber', header: 'Account Number', accessor: f => f.accountNumber },
    { key: 'principalAmount', header: 'Principal', accessor: f => f.principalAmount, type: 'currency' },
    { key: 'interestRate', header: 'Interest Rate (%)', accessor: f => f.interestRate, type: 'number' },
    { key: 'expectedInterest', header: 'Expected Interest', accessor: f => f.expectedInterest, type: 'currency' },
    { key: 'startDate', header: 'Start Date', accessor: f => f.startDate, type: 'date' },
    { key: 'maturityDate', header: 'Maturity Date', accessor: f => f.maturityDate, type: 'date' },
    { key: 'period', header: 'Period', accessor: f => f.period },
    { key: 'status', header: 'Status', accessor: f => f.status },
    { key: 'branch', header: 'Branch', accessor: f => f.branch },
    { key: 'category', header: 'Category', accessor: f => f.category },
    { key: 'beneficiary', header: 'Beneficiary', accessor: f => f.beneficiary },
    { key: 'purpose', header: 'Purpose', accessor: f => f.purpose },
    { key: 'includeInNetWorth', header: 'In Net Worth', accessor: f => f.includeInNetWorth, type: 'boolean' },
    { key: 'netWorthAmount', header: 'Net Worth Amount', accessor: f => f.netWorthAmount, type: 'currency' },
    { key: 'notes', header: 'Notes', accessor: f => f.notes },
    { key: 'createdAt', header: 'Created Date', accessor: f => f.createdAt, type: 'datetime' },
    { key: 'updatedAt', header: 'Updated Date', accessor: f => f.updatedAt, type: 'datetime' },
  ],
};
