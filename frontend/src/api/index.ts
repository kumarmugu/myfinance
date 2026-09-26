import axios from 'axios';
import type {
  Owner, Account, Asset, Transaction, Holding, SoldPosition,
  Dividend, Bank, FDHolder, FixedDeposit, FDSummary, AllocationTarget,
  AccountDeposit, NetWorthSnapshot, DashboardSummary, TransactionRequest, CurrencyRate,
} from '../types';

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

// Attach token from localStorage on init
const storedToken = localStorage.getItem('token');
if (storedToken) {
  api.defaults.headers.common['Authorization'] = `Bearer ${storedToken}`;
}

// ─── Request/Response Logging ───
api.interceptors.request.use(
  config => {
    console.log(`[API] ${config.method?.toUpperCase()} ${config.url}`, config.data || '');
    return config;
  }
);

api.interceptors.response.use(
  response => {
    console.log(`[API] ${response.status} ${response.config.method?.toUpperCase()} ${response.config.url}`);
    return response;
  },
  error => {
    const status = error.response?.status || 'NETWORK_ERROR';
    const data = error.response?.data;
    console.error(`[API] ${status} ${error.config?.method?.toUpperCase()} ${error.config?.url}`, data || error.message);

    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      delete api.defaults.headers.common['Authorization'];
      window.location.href = '/';
    }
    return Promise.reject(error);
  }
);

// ─── Owners ───
export const getOwners = () => api.get<Owner[]>('/owners');
export const getOwnerById = (id: number) => api.get<Owner>(`/owners/${id}`);
export const createOwner = (owner: Partial<Owner>) => api.post<Owner>('/owners', owner);
export const updateOwner = (id: number, owner: Partial<Owner>) => api.put<Owner>(`/owners/${id}`, owner);
export const deleteOwner = (id: number) => api.delete(`/owners/${id}`);

// ─── Accounts ───
export const getAccounts = () => api.get<Account[]>('/accounts');
export const getAccountsByOwner = (ownerId: number) => api.get<Account[]>(`/accounts/owner/${ownerId}`);
export const createAccount = (account: Partial<Account>) => api.post<Account>('/accounts', account);
export const updateAccount = (id: number, account: Partial<Account>) => api.put<Account>(`/accounts/${id}`, account);
export const deleteAccount = (id: number) => api.delete(`/accounts/${id}`);

// ─── Assets ───
export const getAssets = () => api.get<Asset[]>('/assets');
export const getAssetTypes = () => api.get<string[]>('/assets/types');
export const getAssetsByType = (type: string) => api.get<Asset[]>(`/assets/type/${type}`);
export const searchAssets = (query: string) => api.get<Asset[]>(`/assets/search?query=${query}`);
export const createAsset = (asset: Partial<Asset>) => api.post<Asset>('/assets', asset);
export const updateAsset = (id: number, asset: Partial<Asset>) => api.put<Asset>(`/assets/${id}`, asset);
export const updateAssetPrice = (id: number, price: number) => api.patch<Asset>(`/assets/${id}/price?price=${price}`);
export const refreshAssetPrice = (id: number) => api.post<{ updated: boolean; asset?: Asset; message?: string }>(`/assets/${id}/refresh-price`);
export const refreshAllAssetPrices = () => api.post<{ updated: number; skipped: string[]; total: number }>(`/assets/refresh-prices`);
export const toggleAssetNetWorth = (id: number, include: boolean) => api.patch<Asset>(`/assets/${id}/net-worth?include=${include}`);
export const deleteAsset = (id: number) => api.delete(`/assets/${id}`);
// Merge import-created "NAME (TICKER)" duplicate assets back into the canonical ticker.
export const mergeDuplicateAssets = () => api.post<{ assetsMerged: number; dividendsRepointed: number; transactionsRepointed: number; holdingsRepointed: number; duplicateDividendsRemoved: number }>(`/assets/merge-duplicates`);
// Fold one asset into another (e.g. an imported FB into the renamed META). Moves txns/holdings/dividends,
// records the source ticker as a previous symbol of the target, deletes the source.
export const mergeAssets = (sourceId: number, targetId: number) =>
  api.post<{ survivingSymbol: string; mergedSymbol: string; transactionsRepointed: number; holdingsMerged: number; dividendsRepointed: number }>(`/assets/merge`, { sourceId, targetId });

// ─── Dashboard ───
export const getDashboardSummary = (ownerId?: number) => api.get<DashboardSummary>('/dashboard/summary', { params: { ownerId } });
export const getAllocation = () => api.get<Record<string, number>>('/dashboard/allocation');
export const takeSnapshot = (ownerId?: number) => api.post<NetWorthSnapshot>('/dashboard/snapshot', null, { params: { ownerId } });
export const getNetWorthHistory = (ownerId?: number) => api.get<NetWorthSnapshot[]>('/dashboard/net-worth/history', { params: { ownerId } });
export const getLatestSnapshot = () => api.get<NetWorthSnapshot>('/dashboard/net-worth/latest');

// ─── Transactions ───
export const getTransactions = (ownerId?: number) => api.get<Transaction[]>('/transactions', { params: { ownerId } });
export const getTransactionsByAccount = (accountId: number) => api.get<Transaction[]>(`/transactions/account/${accountId}`);
export const getTransactionsByAsset = (assetId: number) => api.get<Transaction[]>(`/transactions/asset/${assetId}`);
export const getRecentTransactions = (days: number) => api.get<Transaction[]>(`/transactions/recent?days=${days}`);
export const createTransaction = (req: TransactionRequest) => api.post<Transaction>('/transactions', req);
export const updateTransaction = (id: number, req: TransactionRequest) => api.put<Transaction>(`/transactions/${id}`, req);
export const deleteTransaction = (id: number) => api.delete(`/transactions/${id}`);
// One-time maintenance: recompute FX-aware realized P/L for the user's existing sells.
export const recomputeRealizedPnl = () => api.post<{ sellsRecomputed: number; soldPositionsSynced: number; holdingsCurrencyFixed: number; holdingsBuyFxBackfilled: number }>('/transactions/recompute-pnl');

// Apply a stock split (or reverse split) to the caller's position in a symbol. Adjusts every
// transaction/holding dated on or before the effective date: quantity ×(numerator/denominator),
// price ÷(numerator/denominator), so the cost basis is preserved.
export interface StockSplitResult {
  symbol: string; effectiveDate: string; numerator: number; denominator: number;
  transactionsAdjusted: number; holdingsAdjusted: number;
}
export const applyStockSplit = (body: { symbol: string; effectiveDate: string; numerator: number; denominator: number }) =>
  api.post<StockSplitResult>('/transactions/split', body);

// ─── IBKR trade sync (Flex Web Service). Token/queryId sent only for the request; never stored. ───
export interface IbkrTradePlan {
  tradeId: string | null; symbol: string; type: string; quantity: number; price: number;
  currency: string; tradeDate: string; classification: 'NEW' | 'DUPLICATE' | 'MISMATCH';
  existingTransactionId: number | null; mismatchDetail: string | null;
}
export interface IbkrSplitInfo { symbol: string; date: string | null; description: string | null; alreadyApplied: boolean; }
export interface IbkrSyncPreview {
  newTrades: IbkrTradePlan[]; duplicates: IbkrTradePlan[]; mismatches: IbkrTradePlan[];
  skippedNonStock: string[]; corporateActions: string[]; needsReview: string[];
  splits: IbkrSplitInfo[];
}
export interface IbkrSyncBody {
  accountId: number; ownerId: number;
  broker?: 'IBKR' | 'TIGER'; // omitted → backend defaults to IBKR
  mode: 'ALL' | 'RANGE'; from?: string | null; to?: string | null;
  approvedMismatchTradeIds?: string[];
}
export const previewIbkrSync = (body: IbkrSyncBody) =>
  api.post<IbkrSyncPreview>('/transactions/ibkr-sync/preview', body);
export const applyIbkrSync = (body: IbkrSyncBody) =>
  api.post<{ inserted: number; updated: number; skipped: number; assetsCreated: number }>('/transactions/ibkr-sync/apply', body);

// Trade file import (IBKR Flex XML or "Transaction History" CSV). Two-phase like the live sync:
// preview classifies (new/duplicate/mismatch) without writing; apply inserts new + approved overwrites.
export const previewTradeImport = (file: File, accountId: number, ownerId: number) => {
  const fd = new FormData();
  fd.append('file', file);
  fd.append('accountId', String(accountId));
  fd.append('ownerId', String(ownerId));
  return api.post<IbkrSyncPreview>('/transactions/import/preview', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
};
export const applyTradeImport = (file: File, accountId: number, ownerId: number, approvedMismatchTradeIds: string[]) => {
  const fd = new FormData();
  fd.append('file', file);
  fd.append('accountId', String(accountId));
  fd.append('ownerId', String(ownerId));
  approvedMismatchTradeIds.forEach(id => fd.append('approvedMismatchTradeIds', id));
  return api.post<{ inserted: number; updated: number; skipped: number; assetsCreated: number }>('/transactions/import/apply', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
};

// Bulk cleanup of transactions scoped to one owner+account (both required). Preview shows how many
// transactions/holdings/sold positions would go; delete removes them all and recomputes P/L.
export interface TxnBulkResult { transactions: number; holdings: number; soldPositions: number; }
export const previewTxnBulkDelete = (ownerId: number, accountId: number) =>
  api.get<TxnBulkResult>('/transactions/bulk-count', { params: { ownerId, accountId } });
export const txnBulkDelete = (ownerId: number, accountId: number, password: string) =>
  api.post<TxnBulkResult>('/transactions/bulk-delete', { ownerId, accountId, password });

// ─── Holdings ───
export const getActiveHoldings = (ownerId?: number) => api.get<Holding[]>('/holdings', { params: { ownerId } });
export const getHoldingsByAccount = (accountId: number) => api.get<Holding[]>(`/holdings/account/${accountId}`);
export const getHoldingsByType = (type: string) => api.get<Holding[]>(`/holdings/type/${type}`);

// ─── Sold Positions ───
export const getSoldPositions = (ownerId?: number, accountId?: number) => api.get<SoldPosition[]>('/sold-positions', { params: { ownerId, accountId } });
export const getShortTermTrades = () => api.get<SoldPosition[]>('/sold-positions/short-term');
export const createSoldPosition = (sp: Partial<SoldPosition>) => api.post<SoldPosition>('/sold-positions', sp);
export const deleteSoldPosition = (id: number) => api.delete(`/sold-positions/${id}`);

// ─── Dividends ───
export const getDividends = (params?: { ownerId?: number; accountId?: number; year?: number }) => api.get<Dividend[]>('/dividends', { params });
export const getDividendSummary = () => api.get<Array<[number, number]>>('/dividends/summary');
export const createDividend = (div: Partial<Dividend>) => api.post<Dividend>('/dividends', div);
export const deleteDividend = (id: number) => api.delete(`/dividends/${id}`);
// Import a broker dividend statement (IBKR CSV / Saxo XLSX). Gated by the DIVIDEND_IMPORT feature.
export const importDividends = (file: File, accountId: number, ownerId: number) => {
  const fd = new FormData();
  fd.append('file', file);
  fd.append('accountId', String(accountId));
  fd.append('ownerId', String(ownerId));
  return api.post<{ imported: number; skipped: number; assetsCreated: number }>('/dividends/import', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
// Fetch dividends from IBKR via the Flex Web Service using the account's stored credentials
// (configured on the Account page). Gated by the broker-sync feature.
export const fetchIbkrDividends = (accountId: number, ownerId: number) =>
  api.post<{ imported: number; skipped: number; assetsCreated: number }>('/dividends/fetch-ibkr',
    { accountId, ownerId });
// Bulk cleanup of dividends scoped to one owner+account (both required). Preview count, then delete
// with password confirmation.
export const previewDividendBulkDelete = (ownerId: number, accountId: number) =>
  api.get<{ dividends: number }>('/dividends/bulk-count', { params: { ownerId, accountId } });

// ─── Broker API credentials (stored, encrypted server-side; secrets never returned) ───
export type BrokerKind = 'IBKR' | 'TIGER' | 'SAXO';
export interface BrokerCredentialStatus {
  id: number; broker: BrokerKind; ownerId: number | null; accountId: number | null;
  meta1: string | null; secret1Set: boolean; secret2Set: boolean; updatedAt: string | null;
}
export const getBrokerCredentials = () =>
  api.get<{ encryptionEnabled: boolean; credentials: BrokerCredentialStatus[] }>('/broker-credentials');
export const saveBrokerCredential = (body: {
  broker: BrokerKind; ownerId: number; accountId: number;
  meta1?: string; meta2?: string; secret1?: string; secret2?: string;
}) => api.post<BrokerCredentialStatus>('/broker-credentials', body);
export const deleteBrokerCredential = (broker: BrokerKind, accountId: number) =>
  api.delete('/broker-credentials', { params: { broker, accountId } });
export const dividendBulkDelete = (ownerId: number, accountId: number, password: string) =>
  api.post<{ deleted: number }>('/dividends/bulk-delete', { ownerId, accountId, password });

// ─── Fixed Deposits ───
export const getFixedDeposits = (params?: { holderId?: number; bankId?: number; status?: string }) => api.get<FixedDeposit[]>('/fixed-deposits', { params });
export const getFixedDepositById = (id: number) => api.get<FixedDeposit>(`/fixed-deposits/${id}`);
export const getMaturingFDs = (days?: number) => api.get<FixedDeposit[]>(`/fixed-deposits/maturing`, { params: { days: days || 90 } });
export const getFDRequiringUpdate = () => api.get<FixedDeposit[]>('/fixed-deposits/requires-update');
export const getFDSummary = () => api.get<FDSummary>('/fixed-deposits/summary');
export const createFixedDeposit = (fd: Partial<FixedDeposit>) => api.post<FixedDeposit>('/fixed-deposits', fd);
export const updateFixedDeposit = (id: number, fd: Partial<FixedDeposit>) => api.put<FixedDeposit>(`/fixed-deposits/${id}`, fd);
export const toggleFDNetWorth = (id: number, includeInNetWorth: boolean, netWorthAmount?: number) => api.patch<FixedDeposit>(`/fixed-deposits/${id}/net-worth`, { includeInNetWorth, netWorthAmount });
export const deleteFixedDeposit = (id: number) => api.delete(`/fixed-deposits/${id}`);
export const getBanks = () => api.get<Bank[]>('/fixed-deposits/banks');
export const getFDHolders = () => api.get<FDHolder[]>('/fixed-deposits/holders');
export const createBank = (bank: Partial<Bank>) => api.post<Bank>('/fixed-deposits/banks', bank);
export const createFDHolder = (holder: Partial<FDHolder>) => api.post<FDHolder>('/fixed-deposits/holders', holder);

// ─── Planning ───
export const getAllocationPlan = (ownerId?: number) => api.get<{ targets: AllocationTarget[]; current: Record<string, number> }>('/planning/allocation', { params: { ownerId } });
export const updateAllocationTargets = (targets: AllocationTarget[]) => api.put<AllocationTarget[]>('/planning/allocation', targets);
export const getAccountDeposits = (accountId?: number) => api.get<AccountDeposit[]>('/planning/deposits', { params: { accountId } });
export const createAccountDeposit = (deposit: Partial<AccountDeposit>) => api.post<AccountDeposit>('/planning/deposits', deposit);
export const updateAccountDeposit = (id: number, deposit: Partial<AccountDeposit>) => api.put<AccountDeposit>(`/planning/deposits/${id}`, deposit);
export const deleteAccountDeposit = (id: number) => api.delete(`/planning/deposits/${id}`);

// ─── Bonds ───
export const getBonds = (ownerId?: number) => api.get<any[]>('/bonds', { params: { ownerId } });
export const getBondSummary = () => api.get<any>('/bonds/summary');
export const createBond = (bond: any) => api.post<any>('/bonds', bond);
export const updateBond = (id: number, bond: any) => api.put<any>(`/bonds/${id}`, bond);
export const deleteBond = (id: number) => api.delete(`/bonds/${id}`);

// ─── Currency Rates ───
export const getCurrencyRates = () => api.get<CurrencyRate[]>('/currency-rates');
export const getAvailableCurrencies = () => api.get<string[]>('/currency-rates/currencies');
export const createCurrencyRate = (rate: Partial<CurrencyRate>) => api.post<CurrencyRate>('/currency-rates', rate);
export const updateCurrencyRate = (id: number, rate: Partial<CurrencyRate>) => api.put<CurrencyRate>(`/currency-rates/${id}`, rate);
export const deleteCurrencyRate = (id: number) => api.delete(`/currency-rates/${id}`);
export const refreshCurrencyRate = (id: number) => api.post<{ updated: boolean; rate?: CurrencyRate; message?: string }>(`/currency-rates/${id}/refresh`);
export const refreshAllCurrencyRates = () => api.post<{ updated: number; skipped: string[]; total: number }>(`/currency-rates/refresh-all`);

// ─── Insurance ───
export const getInsurancePolicies = (ownerId?: number) => api.get<any[]>('/insurance', { params: { ownerId } });
export const createInsurancePolicy = (policy: any) => api.post<any>('/insurance', policy);
export const updateInsurancePolicy = (id: number, policy: any) => api.put<any>(`/insurance/${id}`, policy);
export const deleteInsurancePolicy = (id: number) => api.delete(`/insurance/${id}`);
export const getInsuranceBonusEntries = (policyId: number) => api.get<any[]>(`/insurance/${policyId}/bonus`);
export const createInsuranceBonusEntry = (policyId: number, entry: any) => api.post<any>(`/insurance/${policyId}/bonus`, entry);
export const updateInsuranceBonusEntry = (entryId: number, entry: any) => api.put<any>(`/insurance/bonus/${entryId}`, entry);
export const deleteInsuranceBonusEntry = (entryId: number) => api.delete(`/insurance/bonus/${entryId}`);
export const batchCreateBonusEntries = (policyId: number, entries: any[]) => api.post<any[]>(`/insurance/${policyId}/bonus/batch`, entries);

// ─── Tax ───
export const getTaxRecords = (ownerId?: number, country?: string) => api.get<any[]>('/tax', { params: { ownerId, country } });
export const getTaxSummary = () => api.get<any>('/tax/summary');
export const createTaxRecord = (record: any) => api.post<any>('/tax', record);
export const updateTaxRecord = (id: number, record: any) => api.put<any>(`/tax/${id}`, record);
export const deleteTaxRecord = (id: number) => api.delete(`/tax/${id}`);

// ─── Work Experience ───
export const getWorkExperiences = (ownerId?: number) => api.get<any[]>('/work-experience', { params: { ownerId } });
export const createWorkExperience = (exp: any) => api.post<any>('/work-experience', exp);
export const updateWorkExperience = (id: number, exp: any) => api.put<any>(`/work-experience/${id}`, exp);
export const deleteWorkExperience = (id: number) => api.delete(`/work-experience/${id}`);

// ─── Salary ───
export const getSalaryRecords = (year?: number, country?: string) => api.get<any[]>('/salary', { params: { year, country } });
export const getSalarySummary = () => api.get<any>('/salary/summary');
export const getSalaryYears = () => api.get<number[]>('/salary/years');
export const createSalaryRecord = (record: any) => api.post<any>('/salary', record);
export const updateSalaryRecord = (id: number, record: any) => api.put<any>(`/salary/${id}`, record);
export const deleteSalaryRecord = (id: number) => api.delete(`/salary/${id}`);

// ─── Retirement Fund (CPF/EPF/SRS) ───
export const getRetirementFundEntries = (fundType?: string, ownerId?: number) => api.get<any[]>('/retirement-fund', { params: { fundType, ownerId } });
export const getRetirementFundSummary = () => api.get<any>('/retirement-fund/summary');
export const createRetirementFundEntry = (entry: any) => api.post<any>('/retirement-fund', entry);
export const updateRetirementFundEntry = (id: number, entry: any) => api.put<any>(`/retirement-fund/${id}`, entry);
export const deleteRetirementFundEntry = (id: number) => api.delete(`/retirement-fund/${id}`);

// ─── Home Loans ───
export const getHomeLoans = (ownerId?: number) => api.get<any[]>('/home-loans', { params: { ownerId } });
export const createHomeLoan = (loan: any) => api.post<any>('/home-loans', loan);
export const updateHomeLoan = (id: number, loan: any) => api.put<any>(`/home-loans/${id}`, loan);
export const deleteHomeLoan = (id: number) => api.delete(`/home-loans/${id}`);
export const getLoanPayments = (loanId: number) => api.get<any[]>(`/home-loans/${loanId}/payments`);
export const createLoanPayment = (loanId: number, payment: any) => api.post<any>(`/home-loans/${loanId}/payments`, payment);
export const deleteLoanPayment = (paymentId: number) => api.delete(`/home-loans/payments/${paymentId}`);

export default api;

// ─── Expenses / Receipt Scan ───
export interface ReceiptScanResult {
  expenseDate: string | null;
  description: string | null;
  amount: number | null;
  currency: string | null;
  suggestedCategoryId: number | null;
  suggestedCategoryName: string | null;
  rawText: string | null;
  lowConfidence: boolean;
}

/**
 * Uploads a receipt image for local OCR and returns a DRAFT expense to review.
 * Overrides the default JSON content-type so the browser sets the multipart boundary.
 */
export const scanReceipt = (file: File) => {
  const form = new FormData();
  form.append('file', file);
  return api.post<ReceiptScanResult>('/expenses/scan-receipt', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
