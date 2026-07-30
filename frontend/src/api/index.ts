import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

export interface Account {
  id: number;
  name: string;
  accountType: 'BROKER' | 'BANK';
  description: string;
  createdAt: string;
  updatedAt: string;
}

export interface Asset {
  id: number;
  name: string;
  symbol: string;
  assetType: 'EQUITY' | 'INDEX_FUND' | 'MUTUAL_FUND' | 'CRYPTO' | 'BANK_DEPOSIT';
  currentPrice: number;
  exchange: string;
  description: string;
  createdAt: string;
  updatedAt: string;
}

export interface Transaction {
  id: number;
  asset: Asset;
  account: Account;
  transactionType: 'BUY' | 'SELL';
  quantity: number;
  pricePerUnit: number;
  totalAmount: number;
  fees: number;
  transactionDate: string;
  notes: string;
  createdAt: string;
}

export interface Holding {
  id: number;
  asset: Asset;
  account: Account;
  quantity: number;
  averageBuyPrice: number;
  investedAmount: number;
  updatedAt: string;
}

export interface NetWorthSnapshot {
  id: number;
  snapshotDate: string;
  totalEquity: number;
  totalIndexFund: number;
  totalMutualFund: number;
  totalCrypto: number;
  totalBankDeposit: number;
  totalNetWorth: number;
  createdAt: string;
}

export interface DashboardSummary {
  totalNetWorth: number;
  totalInvested: number;
  totalGainLoss: number;
  gainLossPercentage: number;
  allocationByType: Record<string, number>;
  totalHoldings: number;
  totalAccounts: number;
}

export interface TransactionRequest {
  assetId: number;
  accountId: number;
  transactionType: 'BUY' | 'SELL';
  quantity: number;
  pricePerUnit: number;
  fees?: number;
  transactionDate?: string;
  notes?: string;
}

// Dashboard API
export const getDashboardSummary = () => api.get<DashboardSummary>('/dashboard/summary');
export const getAllocation = () => api.get<Record<string, number>>('/dashboard/allocation');
export const takeSnapshot = () => api.post<NetWorthSnapshot>('/dashboard/snapshot');
export const getNetWorthHistory = () => api.get<NetWorthSnapshot[]>('/dashboard/net-worth/history');
export const getLatestSnapshot = () => api.get<NetWorthSnapshot>('/dashboard/net-worth/latest');

// Accounts API
export const getAccounts = () => api.get<Account[]>('/accounts');
export const getAccountById = (id: number) => api.get<Account>(`/accounts/${id}`);
export const createAccount = (account: Partial<Account>) => api.post<Account>('/accounts', account);
export const updateAccount = (id: number, account: Partial<Account>) => api.put<Account>(`/accounts/${id}`, account);
export const deleteAccount = (id: number) => api.delete(`/accounts/${id}`);

// Assets API
export const getAssets = () => api.get<Asset[]>('/assets');
export const getAssetById = (id: number) => api.get<Asset>(`/assets/${id}`);
export const getAssetsByType = (type: string) => api.get<Asset[]>(`/assets/type/${type}`);
export const createAsset = (asset: Partial<Asset>) => api.post<Asset>('/assets', asset);
export const updateAsset = (id: number, asset: Partial<Asset>) => api.put<Asset>(`/assets/${id}`, asset);
export const deleteAsset = (id: number) => api.delete(`/assets/${id}`);

// Transactions API
export const getTransactions = () => api.get<Transaction[]>('/transactions');
export const getTransactionsByDateRange = (start: string, end: string) =>
  api.get<Transaction[]>(`/transactions/date-range?start=${start}&end=${end}`);
export const getRecentTransactions = (days: number) =>
  api.get<Transaction[]>(`/transactions/recent?days=${days}`);
export const createTransaction = (req: TransactionRequest) => api.post<Transaction>('/transactions', req);
export const deleteTransaction = (id: number) => api.delete(`/transactions/${id}`);

// Holdings API
export const getActiveHoldings = () => api.get<Holding[]>('/holdings');
export const getHoldingsByAccount = (accountId: number) => api.get<Holding[]>(`/holdings/account/${accountId}`);
export const getHoldingsByType = (type: string) => api.get<Holding[]>(`/holdings/type/${type}`);

export default api;
