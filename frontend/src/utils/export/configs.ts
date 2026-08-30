import type { ExportConfig } from './types';
import type { Transaction, Account, Dividend, Holding, SoldPosition, FixedDeposit, InsurancePolicy } from '../../types';

const MONTH_NAMES = ['', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

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



// ─── Additional table configs ───
// These entities declare their row types inline in their page components. To keep this
// registry decoupled we describe just the fields we export via local interfaces.

interface SalaryRow {
  id: number; year: number; month: number; company: string; amount: number;
  basic: number; allowance: number; mobile: number; support: number; weekend: number;
  mealAllowance: number; deductions: number; cpfEmployee: number; cpfEmployer: number;
  epfEmployee: number; epfEmployer: number; etfEmployer: number; contributionScheme: string;
  contributionRemitted: boolean; netTakeHome: number; currency: string;
  isBonus: boolean; bonusMonths: number; country: string; notes: string;
}

export const salaryExportConfig: ExportConfig<SalaryRow> = {
  entity: 'salary',
  title: 'Salary Records',
  columns: [
    { key: 'id', header: 'ID', accessor: r => r.id, type: 'number' },
    { key: 'year', header: 'Year', accessor: r => r.year, type: 'number' },
    { key: 'month', header: 'Month', accessor: r => MONTH_NAMES[r.month] || r.month },
    { key: 'company', header: 'Company', accessor: r => r.company },
    { key: 'type', header: 'Type', accessor: r => (r.isBonus ? 'Bonus' : 'Salary') },
    { key: 'bonusMonths', header: 'Bonus Months', accessor: r => r.bonusMonths, type: 'number' },
    { key: 'currency', header: 'Currency', accessor: r => r.currency || 'SGD' },
    { key: 'amount', header: 'Amount', accessor: r => r.amount, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'basic', header: 'Basic', accessor: r => r.basic, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'allowance', header: 'Allowance', accessor: r => r.allowance, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'mobile', header: 'Mobile', accessor: r => r.mobile, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'support', header: 'Support', accessor: r => r.support, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'weekend', header: 'Weekend', accessor: r => r.weekend, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'mealAllowance', header: 'Meal Allowance', accessor: r => r.mealAllowance, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'deductions', header: 'Deductions', accessor: r => r.deductions, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'netTakeHome', header: 'Net Take-Home', accessor: r => r.netTakeHome, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'cpfEmployee', header: 'CPF Employee', accessor: r => r.cpfEmployee, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'cpfEmployer', header: 'CPF Employer', accessor: r => r.cpfEmployer, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'epfEmployee', header: 'EPF Employee', accessor: r => r.epfEmployee, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'epfEmployer', header: 'EPF Employer', accessor: r => r.epfEmployer, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'etfEmployer', header: 'ETF Employer', accessor: r => r.etfEmployer, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'contributionScheme', header: 'Contribution Scheme', accessor: r => r.contributionScheme },
    { key: 'contributionRemitted', header: 'Remitted', accessor: r => r.contributionRemitted, type: 'boolean' },
    { key: 'country', header: 'Country', accessor: r => r.country },
    { key: 'notes', header: 'Notes', accessor: r => r.notes },
  ],
};

interface WorkExpRow {
  id: number; company: string; position: string; level: string; country: string;
  startDate: string; endDate: string | null; isCurrent: boolean; industry: string; notes: string;
}

export const workExperienceExportConfig: ExportConfig<WorkExpRow> = {
  entity: 'work-experience',
  title: 'Work Experience',
  columns: [
    { key: 'id', header: 'ID', accessor: r => r.id, type: 'number' },
    { key: 'company', header: 'Company', accessor: r => r.company },
    { key: 'position', header: 'Position', accessor: r => r.position },
    { key: 'level', header: 'Level', accessor: r => r.level },
    { key: 'industry', header: 'Industry', accessor: r => r.industry },
    { key: 'country', header: 'Country', accessor: r => r.country },
    { key: 'startDate', header: 'Start Date', accessor: r => r.startDate, type: 'date' },
    { key: 'endDate', header: 'End Date', accessor: r => r.endDate, type: 'date' },
    { key: 'isCurrent', header: 'Current', accessor: r => r.isCurrent, type: 'boolean' },
    { key: 'notes', header: 'Notes', accessor: r => r.notes },
  ],
};

interface BankSavingsRow {
  id: number; accountName: string; bankName: string; balance: number; currency: string;
  country: string; includeInNetWorth: boolean; lastUpdated: string; notes: string;
  owner?: { name: string } | null;
}

export const bankSavingsExportConfig: ExportConfig<BankSavingsRow> = {
  entity: 'bank-savings',
  title: 'Bank Savings',
  columns: [
    { key: 'id', header: 'ID', accessor: r => r.id, type: 'number' },
    { key: 'accountName', header: 'Account Name', accessor: r => r.accountName },
    { key: 'bankName', header: 'Bank', accessor: r => r.bankName },
    { key: 'owner', header: 'Owner', accessor: r => r.owner?.name },
    { key: 'currency', header: 'Currency', accessor: r => r.currency },
    { key: 'balance', header: 'Balance', accessor: r => r.balance, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'country', header: 'Country', accessor: r => r.country },
    { key: 'includeInNetWorth', header: 'In Net Worth', accessor: r => r.includeInNetWorth, type: 'boolean' },
    { key: 'lastUpdated', header: 'Last Updated', accessor: r => r.lastUpdated, type: 'date' },
    { key: 'notes', header: 'Notes', accessor: r => r.notes },
    // NOTE: accountNumber deliberately omitted — sensitive and masked in the UI.
  ],
};

interface PropertyRow {
  id: number; propertyName: string; propertyType: string; address: string; country: string;
  purchasePrice: number; currentValue: number; outstandingLoan: number; currency: string;
  purchaseDate: string; tenure: string; areaSize: number; areaUnit: string; ownership: string;
  includeInNetWorth: boolean; status: string; monthlyRental: number; notes: string;
  owner?: { name: string } | null;
}

export const propertiesExportConfig: ExportConfig<PropertyRow> = {
  entity: 'properties',
  title: 'Real Estate',
  columns: [
    { key: 'id', header: 'ID', accessor: r => r.id, type: 'number' },
    { key: 'propertyName', header: 'Property Name', accessor: r => r.propertyName },
    { key: 'propertyType', header: 'Type', accessor: r => r.propertyType },
    { key: 'address', header: 'Address', accessor: r => r.address },
    { key: 'country', header: 'Country', accessor: r => r.country },
    { key: 'owner', header: 'Owner', accessor: r => r.owner?.name },
    { key: 'ownership', header: 'Ownership', accessor: r => r.ownership },
    { key: 'status', header: 'Status', accessor: r => r.status },
    { key: 'currency', header: 'Currency', accessor: r => r.currency },
    { key: 'purchasePrice', header: 'Purchase Price', accessor: r => r.purchasePrice, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'currentValue', header: 'Current Value', accessor: r => r.currentValue, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'outstandingLoan', header: 'Outstanding Loan', accessor: r => r.outstandingLoan, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'monthlyRental', header: 'Monthly Rental', accessor: r => r.monthlyRental, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'purchaseDate', header: 'Purchase Date', accessor: r => r.purchaseDate, type: 'date' },
    { key: 'tenure', header: 'Tenure', accessor: r => r.tenure },
    { key: 'areaSize', header: 'Area Size', accessor: r => r.areaSize, type: 'number' },
    { key: 'areaUnit', header: 'Area Unit', accessor: r => r.areaUnit },
    { key: 'includeInNetWorth', header: 'In Net Worth', accessor: r => r.includeInNetWorth, type: 'boolean' },
    { key: 'notes', header: 'Notes', accessor: r => r.notes },
  ],
};

interface PreciousMetalRow {
  id: number; metalType: string; form: string; description: string; weight: number;
  weightUnit: string; purity: string; purchasePrice: number; currentPrice: number; currency: string;
  purchaseDate: string; purchasedFrom: string; storageLocation: string; includeInNetWorth: boolean;
  status: string; soldPrice: number; soldDate: string; notes: string; owner?: { name: string } | null;
}

export const preciousMetalsExportConfig: ExportConfig<PreciousMetalRow> = {
  entity: 'precious-metals',
  title: 'Precious Metals',
  columns: [
    { key: 'id', header: 'ID', accessor: r => r.id, type: 'number' },
    { key: 'metalType', header: 'Metal', accessor: r => r.metalType },
    { key: 'form', header: 'Form', accessor: r => r.form },
    { key: 'description', header: 'Description', accessor: r => r.description },
    { key: 'owner', header: 'Owner', accessor: r => r.owner?.name },
    { key: 'weight', header: 'Weight', accessor: r => r.weight, type: 'number' },
    { key: 'weightUnit', header: 'Weight Unit', accessor: r => r.weightUnit },
    { key: 'purity', header: 'Purity', accessor: r => r.purity },
    { key: 'currency', header: 'Currency', accessor: r => r.currency },
    { key: 'purchasePrice', header: 'Purchase Price', accessor: r => r.purchasePrice, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'currentPrice', header: 'Current Value', accessor: r => r.currentPrice, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'purchaseDate', header: 'Purchase Date', accessor: r => r.purchaseDate, type: 'date' },
    { key: 'purchasedFrom', header: 'Purchased From', accessor: r => r.purchasedFrom },
    { key: 'storageLocation', header: 'Storage Location', accessor: r => r.storageLocation },
    { key: 'status', header: 'Status', accessor: r => r.status },
    { key: 'soldPrice', header: 'Sold Price', accessor: r => r.soldPrice, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'soldDate', header: 'Sold Date', accessor: r => r.soldDate, type: 'date' },
    { key: 'includeInNetWorth', header: 'In Net Worth', accessor: r => r.includeInNetWorth, type: 'boolean' },
    { key: 'notes', header: 'Notes', accessor: r => r.notes },
  ],
};

interface GenericFdRow {
  id: number; bankName: string; principalAmount: number; interestRate: number;
  startDate: string; maturityDate: string; tenure: string; expectedInterest: number;
  currency: string; status: string; includeInNetWorth: boolean; notes: string;
}

export const genericFdExportConfig: ExportConfig<GenericFdRow> = {
  entity: 'fixed-deposits',
  title: 'Fixed Deposits',
  columns: [
    { key: 'id', header: 'ID', accessor: r => r.id, type: 'number' },
    { key: 'bankName', header: 'Bank', accessor: r => r.bankName },
    { key: 'currency', header: 'Currency', accessor: r => r.currency },
    { key: 'principalAmount', header: 'Principal', accessor: r => r.principalAmount, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'interestRate', header: 'Interest Rate (%)', accessor: r => r.interestRate, type: 'number' },
    { key: 'expectedInterest', header: 'Expected Interest', accessor: r => r.expectedInterest, type: 'currency', currencyAccessor: r => r.currency },
    { key: 'startDate', header: 'Start Date', accessor: r => r.startDate, type: 'date' },
    { key: 'maturityDate', header: 'Maturity Date', accessor: r => r.maturityDate, type: 'date' },
    { key: 'tenure', header: 'Tenure', accessor: r => r.tenure },
    { key: 'status', header: 'Status', accessor: r => r.status },
    { key: 'includeInNetWorth', header: 'In Net Worth', accessor: r => r.includeInNetWorth, type: 'boolean' },
    { key: 'notes', header: 'Notes', accessor: r => r.notes },
    // NOTE: accountNumber deliberately omitted — sensitive.
  ],
};

interface HomeLoanRow {
  id: number; propertyName: string; propertyAddress: string; propertyValue: number;
  loanAmount: number; interestRate: number; loanType: string; tenureMonths: number;
  monthlyEmi: number; outstandingBalance: number; startDate: string; expectedEndDate: string;
  bank: string; includeInNetWorth: boolean; notes: string;
}

export const homeLoansExportConfig: ExportConfig<HomeLoanRow> = {
  entity: 'home-loans',
  title: 'Home Loans',
  columns: [
    { key: 'id', header: 'ID', accessor: r => r.id, type: 'number' },
    { key: 'propertyName', header: 'Property', accessor: r => r.propertyName },
    { key: 'propertyAddress', header: 'Address', accessor: r => r.propertyAddress },
    { key: 'bank', header: 'Bank', accessor: r => r.bank },
    { key: 'loanType', header: 'Loan Type', accessor: r => r.loanType },
    { key: 'propertyValue', header: 'Property Value', accessor: r => r.propertyValue, type: 'currency' },
    { key: 'loanAmount', header: 'Loan Amount', accessor: r => r.loanAmount, type: 'currency' },
    { key: 'outstandingBalance', header: 'Outstanding Balance', accessor: r => r.outstandingBalance, type: 'currency' },
    { key: 'monthlyEmi', header: 'Monthly EMI', accessor: r => r.monthlyEmi, type: 'currency' },
    { key: 'interestRate', header: 'Interest Rate (%)', accessor: r => r.interestRate, type: 'number' },
    { key: 'tenureMonths', header: 'Tenure (Months)', accessor: r => r.tenureMonths, type: 'number' },
    { key: 'startDate', header: 'Start Date', accessor: r => r.startDate, type: 'date' },
    { key: 'expectedEndDate', header: 'Expected End Date', accessor: r => r.expectedEndDate, type: 'date' },
    { key: 'includeInNetWorth', header: 'In Net Worth', accessor: r => r.includeInNetWorth, type: 'boolean' },
    { key: 'notes', header: 'Notes', accessor: r => r.notes },
  ],
};

interface TaxRow {
  id: number; assessmentYear: number; employment: number; donations: number; reliefs: number;
  srsDeduction: number; chargeableIncome: number; tax: number; taxRebate: number; taxPayable: number;
  country: string; notes: string; owner?: { name: string } | null;
}

export const taxExportConfig: ExportConfig<TaxRow> = {
  entity: 'tax-records',
  title: 'Tax Records',
  columns: [
    { key: 'id', header: 'ID', accessor: r => r.id, type: 'number' },
    { key: 'assessmentYear', header: 'Assessment Year', accessor: r => r.assessmentYear, type: 'number' },
    { key: 'owner', header: 'Owner', accessor: r => r.owner?.name },
    { key: 'country', header: 'Country', accessor: r => r.country },
    { key: 'employment', header: 'Employment Income', accessor: r => r.employment, type: 'currency' },
    { key: 'donations', header: 'Donations', accessor: r => r.donations, type: 'currency' },
    { key: 'reliefs', header: 'Reliefs', accessor: r => r.reliefs, type: 'currency' },
    { key: 'srsDeduction', header: 'SRS Deduction', accessor: r => r.srsDeduction, type: 'currency' },
    { key: 'chargeableIncome', header: 'Chargeable Income', accessor: r => r.chargeableIncome, type: 'currency' },
    { key: 'tax', header: 'Tax', accessor: r => r.tax, type: 'currency' },
    { key: 'taxRebate', header: 'Tax Rebate', accessor: r => r.taxRebate, type: 'currency' },
    { key: 'taxPayable', header: 'Tax Payable', accessor: r => r.taxPayable, type: 'currency' },
    { key: 'notes', header: 'Notes', accessor: r => r.notes },
  ],
};

export const soldPositionsExportConfig: ExportConfig<SoldPosition> = {
  entity: 'sold-positions',
  title: 'Sold Positions',
  columns: [
    { key: 'id', header: 'ID', accessor: s => s.id, type: 'number' },
    { key: 'assetSymbol', header: 'Asset Symbol', accessor: s => s.asset?.symbol },
    { key: 'assetName', header: 'Asset Name', accessor: s => s.asset?.name },
    { key: 'account', header: 'Account', accessor: s => s.account?.name },
    { key: 'owner', header: 'Owner', accessor: s => s.owner?.name },
    { key: 'quantity', header: 'Quantity', accessor: s => s.quantity, type: 'number' },
    { key: 'currency', header: 'Currency', accessor: s => s.currency },
    { key: 'buyPrice', header: 'Buy Price', accessor: s => s.buyPrice, type: 'currency', currencyAccessor: s => s.currency },
    { key: 'sellPrice', header: 'Sell Price', accessor: s => s.sellPrice, type: 'currency', currencyAccessor: s => s.currency },
    { key: 'investedAmount', header: 'Invested Amount', accessor: s => s.investedAmount, type: 'currency', currencyAccessor: s => s.currency },
    { key: 'soldAmount', header: 'Sold Amount', accessor: s => s.soldAmount, type: 'currency', currencyAccessor: s => s.currency },
    { key: 'profit', header: 'Profit', accessor: s => s.profit, type: 'currency', currencyAccessor: s => s.currency },
    { key: 'profitPercentage', header: 'Profit %', accessor: s => s.profitPercentage, type: 'number' },
    { key: 'investedDate', header: 'Invested Date', accessor: s => s.investedDate, type: 'date' },
    { key: 'soldDate', header: 'Sold Date', accessor: s => s.soldDate, type: 'date' },
    { key: 'holdingPeriod', header: 'Holding Period', accessor: s => s.holdingPeriod },
    { key: 'isShortTerm', header: 'Short Term', accessor: s => s.isShortTerm, type: 'boolean' },
    { key: 'notes', header: 'Notes', accessor: s => s.notes },
  ],
};

export const sriLankaFdExportConfig: ExportConfig<FixedDeposit> = {
  entity: 'sri-lanka-fixed-deposits',
  title: 'Sri Lanka Fixed Deposits',
  columns: [
    { key: 'id', header: 'ID', accessor: f => f.id, type: 'number' },
    { key: 'bank', header: 'Bank', accessor: f => f.bank?.name },
    { key: 'holder', header: 'Holder', accessor: f => f.holder?.name },
    { key: 'jointHolder', header: 'Joint Holder', accessor: f => f.jointHolder?.name },
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
    // NOTE: accountNumber deliberately omitted — sensitive.
  ],
};

export const insuranceExportConfig: ExportConfig<InsurancePolicy> = {
  entity: 'insurance-policies',
  title: 'Insurance Policies',
  columns: [
    { key: 'id', header: 'ID', accessor: p => p.id, type: 'number' },
    { key: 'policyName', header: 'Policy Name', accessor: p => p.policyName },
    { key: 'provider', header: 'Provider', accessor: p => p.provider },
    { key: 'policyType', header: 'Type', accessor: p => p.policyType },
    { key: 'owner', header: 'Owner', accessor: p => p.owner?.name },
    { key: 'currency', header: 'Currency', accessor: p => p.currency },
    { key: 'annualPremium', header: 'Annual Premium', accessor: p => p.annualPremium, type: 'currency', currencyAccessor: p => p.currency },
    { key: 'coverageAmount', header: 'Coverage Amount', accessor: p => p.coverageAmount, type: 'currency', currencyAccessor: p => p.currency },
    { key: 'cashValue', header: 'Cash Value', accessor: p => p.cashValue, type: 'currency', currencyAccessor: p => p.currency },
    { key: 'startDate', header: 'Start Date', accessor: p => p.startDate, type: 'date' },
    { key: 'maturityDate', header: 'Maturity Date', accessor: p => p.maturityDate, type: 'date' },
    { key: 'isActive', header: 'Active', accessor: p => p.isActive, type: 'boolean' },
    { key: 'includeInNetWorth', header: 'In Net Worth', accessor: p => p.includeInNetWorth, type: 'boolean' },
    { key: 'beneficiary', header: 'Beneficiary', accessor: p => p.beneficiary },
    { key: 'notes', header: 'Notes', accessor: p => p.notes },
    { key: 'createdAt', header: 'Created Date', accessor: p => p.createdAt, type: 'datetime' },
    // NOTE: policyNumber deliberately omitted — sensitive.
  ],
};
