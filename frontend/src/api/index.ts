import axios from 'axios';
import type {
  Owner, Account, Asset, Transaction, Holding, SoldPosition,
  Dividend, Bank, FDHolder, FixedDeposit, FDSummary, AllocationTarget,
  AccountDeposit, NetWorthSnapshot, DashboardSummary, TransactionRequest,
} from '../types';

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

// ─── Owners ───
export const getOwners = () => api.get<Owner[]>('/owners');
export const createOwner = (owner: Partial<Owner>) => api.post<Owner>('/owners', owner);

// ─── Accounts ───
export const getAccounts = () => api.get<Account[]>('/accounts');
export const getAccountsByOwner = (ownerId: number) => api.get<Account[]>(`/accounts/owner/${ownerId}`);
export const createAccount = (account: Partial<Account>) => api.post<Account>('/accounts', account);
export const updateAccount = (id: number, account: Partial<Account>) => api.put<Account>(`/accounts/${id}`, account);
export const deleteAccount = (id: number) => api.delete(`/accounts/${id}`);

// ─── Assets ───
export const getAssets = () => api.get<Asset[]>('/assets');
export const getAssetsByType = (type: string) => api.get<Asset[]>(`/assets/type/${type}`);
export const searchAssets = (query: string) => api.get<Asset[]>(`/assets/search?query=${query}`);
export const createAsset = (asset: Partial<Asset>) => api.post<Asset>('/assets', asset);
export const updateAsset = (id: number, asset: Partial<Asset>) => api.put<Asset>(`/assets/${id}`, asset);
export const updateAssetPrice = (id: number, price: number) => api.patch<Asset>(`/assets/${id}/price?price=${price}`);
export const deleteAsset = (id: number) => api.delete(`/assets/${id}`);

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
export const deleteTransaction = (id: number) => api.delete(`/transactions/${id}`);

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

// ─── Fixed Deposits ───
export const getFixedDeposits = (params?: { holderId?: number; bankId?: number; status?: string }) => api.get<FixedDeposit[]>('/fixed-deposits', { params });
export const getFixedDepositById = (id: number) => api.get<FixedDeposit>(`/fixed-deposits/${id}`);
export const getMaturingFDs = (days?: number) => api.get<FixedDeposit[]>(`/fixed-deposits/maturing`, { params: { days: days || 90 } });
export const getFDRequiringUpdate = () => api.get<FixedDeposit[]>('/fixed-deposits/requires-update');
export const getFDSummary = () => api.get<FDSummary>('/fixed-deposits/summary');
export const createFixedDeposit = (fd: Partial<FixedDeposit>) => api.post<FixedDeposit>('/fixed-deposits', fd);
export const updateFixedDeposit = (id: number, fd: Partial<FixedDeposit>) => api.put<FixedDeposit>(`/fixed-deposits/${id}`, fd);
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
export const deleteAccountDeposit = (id: number) => api.delete(`/planning/deposits/${id}`);

export default api;
