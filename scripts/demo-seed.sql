-- ============================================================================
-- MyFinance DEMO DATA SEED  (DEV DATABASE ONLY)
-- ============================================================================
-- Target: jdbc:h2:file:./data/myfinance   (the DEV/default datasource)
-- Executed ONLY by scripts/seed-demo-data.sh, which hard-codes the dev DB path
-- and refuses to run against data-prod.
--
--   1. Deletes ALL rows from every data table (child-first for FK safety).
--   2. Recreates two users:
--        - admin / admin123   (role ADMIN, no financial data)
--        - mugu  / mugu        (role USER, owns ALL the demo financial data)
--   3. Inserts demo records covering every feature module.
--
-- Placeholders substituted by the runner script before execution:
--   @ADMIN_HASH@ / @MUGU_HASH@  -> fresh BCrypt hashes
--   @UID@                       -> the mugu user id (used as user_id everywhere)
-- ============================================================================

-- 0. ENSURE OPTIONAL TABLES EXIST
-- The Bonds feature exists in code, but this dev DB may predate it (the table is
-- only generated when the app runs after the entity was added). Create it here so
-- the demo can include bonds. Columns match the Bond @Entity, so Hibernate's
-- ddl-auto=update will accept the existing table on the next app start.
CREATE TABLE IF NOT EXISTS bonds (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id              BIGINT,
  name                 CHARACTER VARYING NOT NULL,
  issuer               CHARACTER VARYING,
  bond_type            CHARACTER VARYING,
  isin                 CHARACTER VARYING,
  currency             CHARACTER VARYING(10),
  face_value           NUMERIC(18,2),
  purchase_price       NUMERIC(18,2),
  current_value        NUMERIC(18,2),
  coupon_rate          NUMERIC(8,4),
  coupon_frequency     CHARACTER VARYING,
  purchase_date        DATE,
  maturity_date        DATE,
  status               CHARACTER VARYING,
  include_in_net_worth BOOLEAN,
  notes                CHARACTER VARYING,
  owner_id             BIGINT,
  created_at           TIMESTAMP,
  updated_at           TIMESTAMP
);

-- 1. WIPE (children before parents)
DELETE FROM loan_payments;
DELETE FROM insurance_bonus_entries;
DELETE FROM budget_allocations;
DELETE FROM budget_incomes;
DELETE FROM expenses;
DELETE FROM budget_plans;
DELETE FROM budget_categories;
DELETE FROM dividends;
DELETE FROM sold_positions;
DELETE FROM transactions;
DELETE FROM holdings;
DELETE FROM account_deposits;
DELETE FROM allocation_targets;
DELETE FROM net_worth_snapshots;
DELETE FROM net_worth_config;
DELETE FROM fixed_deposits;
DELETE FROM generic_fixed_deposits;
DELETE FROM bank_savings;
DELETE FROM properties;
DELETE FROM precious_metals;
DELETE FROM bonds;
DELETE FROM home_loans;
DELETE FROM insurance_policies;
DELETE FROM retirement_fund_entries;
DELETE FROM salary_records;
DELETE FROM work_experiences;
DELETE FROM tax_records;
DELETE FROM currency_rates;
DELETE FROM user_currencies;
DELETE FROM assets;
DELETE FROM accounts;
DELETE FROM owners;
DELETE FROM fd_holders;
DELETE FROM banks;
DELETE FROM audit_logs;
DELETE FROM app_users;

-- 2. USERS
INSERT INTO app_users (id, username, email, password, display_name, role, is_active, sl_fd_enabled, enabled_features, base_currency, display_currencies, created_at, updated_at)
VALUES (1, 'admin', 'admin@myfinance.local', '@ADMIN_HASH@', 'Admin', 'ADMIN', TRUE, FALSE, NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO app_users (id, username, email, password, display_name, role, is_active, sl_fd_enabled, enabled_features, base_currency, display_currencies, created_at, updated_at)
VALUES (@UID@, 'mugu', 'mugu@myfinance.local', '@MUGU_HASH@', 'Mugu', 'USER', TRUE, TRUE,
        'PORTFOLIO,CRYPTO,DIVIDENDS,CASH_FLOWS,BANK_SAVINGS,FIXED_DEPOSITS,SL_FD,REAL_ESTATE,PRECIOUS_METALS,BONDS,INSURANCE,HOME_LOANS,SALARY,TAX,WORK_EXPERIENCE,SRS_CPF,REPORTS,BUDGET',
        'SGD', 'SGD,USD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 3. OWNERS
INSERT INTO owners (id, user_id, name, relationship, is_active, created_at) VALUES
 (1, @UID@, 'Mugu (Self)', 'SELF',   TRUE, CURRENT_TIMESTAMP),
 (2, @UID@, 'Spouse',      'SPOUSE', TRUE, CURRENT_TIMESTAMP);

-- 4. ACCOUNTS
INSERT INTO accounts (id, user_id, owner_id, name, account_type, currency, account_number, description, created_at, updated_at) VALUES
 (1, @UID@, 1, 'Tiger',       'BROKER',          'USD', 'TG-001',  'Tiger Brokers - US stocks', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 'Saxo',        'BROKER',          'SGD', 'SX-001',  'Saxo Capital Markets',      CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, @UID@, 1, 'IBKR',        'BROKER',          'SGD', 'IB-001',  'Interactive Brokers',       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (4, @UID@, 1, 'Poems',       'BROKER',          'SGD', 'PM-001',  'Phillip Securities - SRS',  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (5, @UID@, 1, 'DBS',         'BANK',            'SGD', 'DBS-001', 'DBS Savings',               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (6, @UID@, 1, 'Coinhako',    'CRYPTO_EXCHANGE', 'SGD', 'CH-001',  'Coinhako crypto exchange',  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (7, @UID@, 2, 'Tiger-Spouse','BROKER',          'USD', 'TG-002',  'Tiger Brokers - Spouse',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 5. ASSETS
INSERT INTO assets (id, user_id, name, symbol, asset_type, current_price, currency, exchange, include_in_net_worth, created_at, updated_at) VALUES
 (1,  @UID@, 'Vanguard S&P 500 ETF',   'VOO',       'INDEX_FUND',      530,   'USD', 'NYSE',    TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2,  @UID@, 'Invesco QQQ Trust',      'QQQ',       'INDEX_FUND',      510,   'USD', 'NASDAQ',  TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3,  @UID@, 'Tesla Inc',              'TSLA',      'GROWTH_EQUITY',   350,   'USD', 'NASDAQ',  TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (4,  @UID@, 'Apple Inc',              'AAPL',      'GROWTH_EQUITY',   230,   'USD', 'NASDAQ',  TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (5,  @UID@, 'NVIDIA Corp',            'NVDA',      'GROWTH_EQUITY',   140,   'USD', 'NASDAQ',  TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (6,  @UID@, 'DBS Group Holdings',     'D05',       'DIVIDEND_EQUITY', 42,    'SGD', 'SGX',     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (7,  @UID@, 'Amundi Prime USA',       'AMUNDI-USA','MUTUAL_FUND',     220,   'SGD', 'Poems',   TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (8,  @UID@, 'ProShares UltraPro QQQ', 'TQQQ',      'LEVERAGED_ETF',   62,    'USD', 'NASDAQ',  TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (9,  @UID@, 'Bitcoin',                'BTC',       'CRYPTO',          95000, 'USD', 'Coinhako',TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (10, @UID@, 'Ethereum',               'ETH',       'CRYPTO',          3200,  'USD', 'Coinhako',TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 6. TRANSACTIONS (buys)
INSERT INTO transactions (id, user_id, owner_id, account_id, asset_id, transaction_type, quantity, price_per_unit, total_amount, fees, currency, transaction_date, notes, created_at) VALUES
 (1, @UID@, 1, 1, 1,  'BUY', 15,   410,   6150,  0, 'USD', DATE '2022-03-15', 'Initial VOO', CURRENT_TIMESTAMP),
 (2, @UID@, 1, 1, 3,  'BUY', 30,   202,   6060,  0, 'USD', DATE '2022-05-12', 'TSLA',        CURRENT_TIMESTAMP),
 (3, @UID@, 1, 1, 4,  'BUY', 34,   178,   6052,  0, 'USD', DATE '2024-03-22', 'AAPL',        CURRENT_TIMESTAMP),
 (4, @UID@, 1, 1, 5,  'BUY', 30,   85,    2550,  0, 'USD', DATE '2024-04-09', 'NVDA',        CURRENT_TIMESTAMP),
 (5, @UID@, 1, 1, 8,  'BUY', 50,   28,    1400,  0, 'USD', DATE '2022-05-09', 'TQQQ',        CURRENT_TIMESTAMP),
 (6, @UID@, 1, 2, 2,  'BUY', 49,   510,   24990, 0, 'SGD', DATE '2021-08-03', 'QQQ Saxo',    CURRENT_TIMESTAMP),
 (7, @UID@, 1, 3, 6,  'BUY', 100,  36,    3600,  0, 'SGD', DATE '2024-07-22', 'DBS',         CURRENT_TIMESTAMP),
 (8, @UID@, 1, 4, 7,  'BUY', 80,   200,   16000, 0, 'SGD', DATE '2025-07-17', 'SRS fund',    CURRENT_TIMESTAMP),
 (9, @UID@, 1, 6, 9,  'BUY', 0.05, 45000, 2250,  0, 'USD', DATE '2021-11-15', 'BTC',         CURRENT_TIMESTAMP),
 (10,@UID@, 1, 6, 10, 'BUY', 1.6,  1800,  2880,  0, 'USD', DATE '2021-12-03', 'ETH',         CURRENT_TIMESTAMP),
 (11,@UID@, 2, 7, 1,  'BUY', 3,    386,   1158,  0, 'USD', DATE '2022-03-08', 'Spouse VOO',  CURRENT_TIMESTAMP);

-- 7. HOLDINGS (current positions; TransactionService bypassed in raw SQL)
INSERT INTO holdings (id, user_id, owner_id, account_id, asset_id, quantity, average_buy_price, invested_amount, currency, purpose, updated_at) VALUES
 (1, @UID@, 1, 1, 1,  15,   410,   6150,  'USD', 'LONG_TERM', CURRENT_TIMESTAMP),
 (2, @UID@, 1, 1, 3,  30,   202,   6060,  'USD', 'LONG_TERM', CURRENT_TIMESTAMP),
 (3, @UID@, 1, 1, 4,  34,   178,   6052,  'USD', 'LONG_TERM', CURRENT_TIMESTAMP),
 (4, @UID@, 1, 1, 5,  30,   85,    2550,  'USD', 'LONG_TERM', CURRENT_TIMESTAMP),
 (5, @UID@, 1, 1, 8,  50,   28,    1400,  'USD', 'TRADING',   CURRENT_TIMESTAMP),
 (6, @UID@, 1, 2, 2,  49,   510,   24990, 'SGD', 'LONG_TERM', CURRENT_TIMESTAMP),
 (7, @UID@, 1, 3, 6,  100,  36,    3600,  'SGD', 'DIVIDEND_REINVESTMENT', CURRENT_TIMESTAMP),
 (8, @UID@, 1, 4, 7,  80,   200,   16000, 'SGD', 'SRS',       CURRENT_TIMESTAMP),
 (9, @UID@, 1, 6, 9,  0.05, 45000, 2250,  'USD', 'LONG_TERM', CURRENT_TIMESTAMP),
 (10,@UID@, 1, 6, 10, 1.6,  1800,  2880,  'USD', 'LONG_TERM', CURRENT_TIMESTAMP),
 (11,@UID@, 2, 7, 1,  3,    386,   1158,  'USD', 'LONG_TERM', CURRENT_TIMESTAMP);

-- 8. SOLD POSITIONS
INSERT INTO sold_positions (id, user_id, owner_id, account_id, asset_id, quantity, buy_price, sell_price, invested_amount, sold_amount, profit, profit_percentage, currency, invested_date, sold_date, holding_period, is_short_term, notes, created_at) VALUES
 (1, @UID@, 1, 1, 5, 30, 95,  135, 2850, 4050, 1200, 42.1, 'USD', DATE '2024-03-08', DATE '2024-06-19', '3 Months', FALSE, 'Partial profit', CURRENT_TIMESTAMP),
 (2, @UID@, 1, 1, 3, 16, 411, 450, 6576, 7200, 624,  9.5,  'USD', DATE '2024-12-31', DATE '2025-09-29', '9 Months', FALSE, NULL, CURRENT_TIMESTAMP),
 (3, @UID@, 1, 1, 8, 30, 100, 117, 3000, 3510, 510,  17,   'USD', DATE '2025-10-11', DATE '2025-10-28', '17 days',  TRUE,  'Short-term trade', CURRENT_TIMESTAMP);

-- 9. DIVIDENDS
INSERT INTO dividends (id, user_id, owner_id, account_id, asset_id, amount, currency, received_date, dividend_year, quarter, instrument, notes, created_at) VALUES
 (1, @UID@, 1, 2, 2, 32, 'SGD', DATE '2025-03-28', 2025, 'Q1', 'QQQ',  NULL, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 2, 1, 63, 'SGD', DATE '2025-06-28', 2025, 'Q2', 'VOO',  NULL, CURRENT_TIMESTAMP),
 (3, @UID@, 1, 3, 6, 54, 'SGD', DATE '2025-08-15', 2025, 'Q3', 'D05',  NULL, CURRENT_TIMESTAMP),
 (4, @UID@, 1, 1, 1, 85, 'USD', DATE '2025-06-15', 2025, 'Q2', 'VOO',  NULL, CURRENT_TIMESTAMP),
 (5, @UID@, 1, 1, 4, 45, 'USD', DATE '2025-09-15', 2025, 'Q3', 'AAPL', NULL, CURRENT_TIMESTAMP);

-- 10. ACCOUNT DEPOSITS / WITHDRAWALS (Cash Flows)
INSERT INTO account_deposits (id, user_id, account_id, amount, deposit_type, currency, deposit_date, notes, created_at) VALUES
 (1, @UID@, 1, 5000,  'DEPOSIT',    'SGD', DATE '2022-02-23', NULL, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 40000, 'DEPOSIT',    'SGD', DATE '2024-03-23', NULL, CURRENT_TIMESTAMP),
 (3, @UID@, 2, 25000, 'DEPOSIT',    'SGD', DATE '2021-08-18', NULL, CURRENT_TIMESTAMP),
 (4, @UID@, 3, 20000, 'DEPOSIT',    'SGD', DATE '2025-03-13', NULL, CURRENT_TIMESTAMP),
 (5, @UID@, 1, 10000, 'WITHDRAWAL', 'SGD', DATE '2025-03-12', 'Cashed out gains', CURRENT_TIMESTAMP);

-- 11. BANK SAVINGS
INSERT INTO bank_savings (id, user_id, owner_id, account_name, bank_name, account_number, balance, currency, country, include_in_net_worth, last_updated, notes, created_at, updated_at) VALUES
 (1, @UID@, 1, 'DBS Multiplier',   'DBS',  'DBS-SAV-1',  45000, 'SGD', 'Singapore', TRUE,  DATE '2026-08-01', 'Emergency fund', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 'OCBC 360',         'OCBC', 'OCBC-SAV-1', 22000, 'SGD', 'Singapore', TRUE,  DATE '2026-08-01', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, @UID@, 1, 'Spending Account', 'CIMB', 'CIMB-SAV-1', 3000,  'SGD', 'Singapore', FALSE, DATE '2026-08-01', 'Excluded from net worth', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 12. FIXED DEPOSITS: BANKS + HOLDERS (global reference, no user_id)
INSERT INTO banks (id, name, short_name, country) VALUES
 (1, 'Bank of Ceylon',        'BOC',        'Sri Lanka'),
 (2, 'National Savings Bank', 'NSB',        'Sri Lanka'),
 (3, 'Commercial Bank',       'Commercial', 'Sri Lanka');

INSERT INTO fd_holders (id, name, relationship, is_senior_citizen, notes) VALUES
 (1, 'Parent A',      'Mother',      TRUE,  NULL),
 (2, 'Parent B',      'Father',      TRUE,  NULL),
 (3, 'Self/Parent A', 'Self/Mother', FALSE, NULL);

-- 12a. FIXED DEPOSITS (Sri Lanka module)
INSERT INTO fixed_deposits (id, user_id, holder_id, joint_holder_id, bank_id, account_number, principal_amount, interest_rate, start_date, maturity_date, period, branch, category, status, expected_interest, beneficiary, currency, requires_update, include_in_net_worth, notes, created_at, updated_at) VALUES
 (1, @UID@, 1, NULL, 2, 'FD-NSB-001', 500000,  8.0,   DATE '2025-01-11', DATE '2026-01-11', '12 Months', 'Main Branch', 'SENIOR_CITIZEN', 'ACTIVE',          40000, 'PARENT_A', 'LKR', FALSE, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, @UID@, 2, NULL, 1, 'FD-BOC-001', 1000000, 8.0,   DATE '2025-05-22', DATE '2026-05-22', '12 Months', 'Main Branch', 'SENIOR_CITIZEN', 'ACTIVE',          80000, 'PARENT_B', 'LKR', FALSE, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, @UID@, 3, NULL, 3, 'FD-COM-001', 750000,  11.46, DATE '2025-09-08', DATEADD('DAY', 20, CURRENT_DATE), '12 Months', 'Main Branch', NULL, 'ACTIVE',      85950, 'PARENT_A', 'LKR', FALSE, TRUE, 'Maturing soon', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (4, @UID@, 1, NULL, 2, 'FD-NSB-002', 460000,  6.5,   DATE '2023-02-03', DATE '2024-02-03', '12 Months', 'Main Branch', 'SENIOR_CITIZEN', 'REQUIRES_UPDATE', 29900, 'PARENT_A', 'LKR', TRUE, TRUE, 'Old certificate - verify', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 12b. GENERIC FIXED DEPOSITS (any bank)
INSERT INTO generic_fixed_deposits (id, user_id, bank_name, account_number, principal_amount, interest_rate, start_date, maturity_date, tenure, expected_interest, currency, status, include_in_net_worth, notes, created_at, updated_at) VALUES
 (1, @UID@, 'Standard Chartered', 'SC-FD-01', 50000, 3.5, DATE '2025-06-01', DATE '2026-06-01', '12 Months', 1750, 'SGD', 'ACTIVE', TRUE, 'SGD term deposit', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, @UID@, 'Maybank',            'MB-FD-01', 30000, 3.2, DATE '2025-03-15', DATEADD('DAY', 10, CURRENT_DATE), '12 Months', 960, 'SGD', 'ACTIVE', TRUE, 'Maturing soon', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 13. REAL ESTATE (Property)
INSERT INTO properties (id, user_id, owner_id, property_name, property_type, address, country, purchase_price, current_value, outstanding_loan, currency, purchase_date, tenure, area_size, area_unit, ownership, monthly_rental, include_in_net_worth, status, notes, created_at, updated_at) VALUES
 (1, @UID@, 1, 'HDB Flat - Woodlands', 'HDB',         '123 Woodlands Ave', 'Singapore', 500000, 620000, 380000, 'SGD', DATE '2020-06-01', '99-year',  90.0,  'sqm', 'Joint',  0,    TRUE, 'OWNED',  'Primary residence', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 'Condo - Colombo',      'Condominium', 'Marine Drive',      'Sri Lanka', 200000, 240000, 0,      'LKR', DATE '2019-03-15', 'Freehold', 120.0, 'sqm', 'Single', 1200, TRUE, 'RENTED', 'Rental income property', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 14. PRECIOUS METALS
INSERT INTO precious_metals (id, user_id, owner_id, metal_type, form, description, weight, weight_unit, purity, purchase_price, current_price, currency, purchase_date, purchased_from, storage_location, include_in_net_worth, status, notes, created_at, updated_at) VALUES
 (1, @UID@, 1, 'GOLD',   'COIN', 'Gold Sovereign coins', 50.0,   'g', '22K', 4500, 6200, 'SGD', DATE '2021-05-10', 'BullionStar', 'Home safe', TRUE, 'HELD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 'SILVER', 'BAR',  'Silver bullion bars',  1000.0, 'g', '999', 900,  1050, 'SGD', DATE '2022-08-01', 'BullionStar', 'Home safe', TRUE, 'HELD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 15. BONDS
INSERT INTO bonds (id, user_id, owner_id, name, issuer, bond_type, isin, currency, face_value, purchase_price, current_value, coupon_rate, coupon_frequency, purchase_date, maturity_date, status, include_in_net_worth, notes, created_at, updated_at) VALUES
 (1, @UID@, 1, 'Singapore Savings Bond', 'MAS',    'GOVERNMENT', 'SGXZ12345', 'SGD', 10000, 10000, 10250, 3.0,   'SEMI_ANNUAL', DATE '2024-01-01', DATE '2034-01-01', 'HELD', TRUE, 'SSB', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 'Astrea 7 Bond',          'Azalea', 'CORPORATE',  'SGXAST7',   'SGD', 5000,  5000,  5100,  4.125, 'SEMI_ANNUAL', DATE '2023-05-01', DATE '2028-05-01', 'HELD', TRUE, NULL,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 16. HOME LOANS + PAYMENTS
INSERT INTO home_loans (id, user_id, owner_id, property_name, property_address, property_value, loan_amount, interest_rate, loan_type, tenure_months, monthly_emi, outstanding_balance, total_paid, total_interest_paid, start_date, expected_end_date, bank, currency, is_active, include_in_net_worth, notes, created_at, updated_at) VALUES
 (1, @UID@, 1, 'HDB Flat - Woodlands', '123 Woodlands Ave', 550000, 400000, 2.6, 'HDB', 300, 1800, 380000, 43200, 20800, DATE '2020-06-01', DATE '2045-06-01', 'HDB', 'SGD', TRUE, TRUE, 'Primary mortgage', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO loan_payments (id, user_id, loan_id, payment_date, amount, principal_portion, interest_portion, balance_after, payment_type, notes, created_at) VALUES
 (1, @UID@, 1, DATE '2026-06-01', 1800,  940,   860, 381000, 'EMI',        NULL, CURRENT_TIMESTAMP),
 (2, @UID@, 1, DATE '2026-07-01', 1800,  942,   858, 380058, 'EMI',        NULL, CURRENT_TIMESTAMP),
 (3, @UID@, 1, DATE '2026-08-01', 10000, 10000, 0,   370058, 'PREPAYMENT', 'Lump-sum prepayment', CURRENT_TIMESTAMP);

-- 17. INSURANCE + BONUS ENTRIES
INSERT INTO insurance_policies (id, user_id, owner_id, policy_name, provider, policy_number, policy_type, annual_premium, currency, coverage_amount, cash_value, start_date, maturity_date, is_active, include_in_net_worth, beneficiary, notes, created_at, updated_at) VALUES
 (1, @UID@, 1, 'Whole Life Plan', 'Prudential', 'PRU-001', 'WHOLE_LIFE', 3600, 'SGD', 200000, 18000, DATE '2015-10-01', DATE '2055-10-01', TRUE, TRUE,  'Spouse', 'Participating policy', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 'Term Life',       'AIA',        'AIA-001', 'TERM',       800,  'SGD', 500000, 0,     DATE '2020-01-01', DATE '2045-01-01', TRUE, FALSE, 'Spouse', 'Pure protection', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO insurance_bonus_entries (id, user_id, policy_id, year_number, year_date, age, premium_amount, expected_bonus, expected_bonus_total, expected_total, actual_bonus, actual_bonus_total, notes, created_at) VALUES
 (1, @UID@, 1, 1, '10/2015', 30, 3600, 500, 500,  4100,  480, 480,  NULL, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 2, '10/2016', 31, 3600, 520, 1020, 8220,  510, 990,  NULL, CURRENT_TIMESTAMP),
 (3, @UID@, 1, 3, '10/2017', 32, 3600, 540, 1560, 12360, 530, 1520, NULL, CURRENT_TIMESTAMP);

-- 18. RETIREMENT (CPF / SRS)
INSERT INTO retirement_fund_entries (id, user_id, owner_id, fund_type, entry_type, amount, entry_date, "year", "month", account, balance, employer, currency, notes, created_at, updated_at) VALUES
 (1, @UID@, 1, 'CPF', 'CONTRIBUTION',          2500,  DATE '2026-01-15', 2026, 1, 'OA', 85000, 'BCS', 'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 'CPF', 'EMPLOYER_CONTRIBUTION', 2448,  DATE '2026-01-15', 2026, 1, 'OA', 87448, 'BCS', 'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, @UID@, 1, 'CPF', 'CONTRIBUTION',          1300,  DATE '2026-01-15', 2026, 1, 'SA', 42000, 'BCS', 'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (4, @UID@, 1, 'SRS', 'CONTRIBUTION',          15300, DATE '2026-01-05', 2026, 1, NULL, 15300, NULL,  'SGD', 'Annual SRS top-up', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 19. SALARY
INSERT INTO salary_records (id, user_id, owner_id, "year", "month", company, amount, basic, mobile, deductions, is_bonus, bonus_months, country, currency, notes, created_at, updated_at) VALUES
 (1, @UID@, 1, 2026, 1, 'BCS', 14442, 14400, 60, 18, FALSE, NULL, 'Singapore', 'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 2026, 2, 'BCS', 14442, 14400, 60, 18, FALSE, NULL, 'Singapore', 'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, @UID@, 1, 2026, 3, 'BCS', 14442, 14400, 60, 18, FALSE, NULL, 'Singapore', 'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (4, @UID@, 1, 2026, 3, 'BCS', 56000, NULL, NULL, NULL, TRUE, 4.04, 'Singapore', 'SGD', 'Annual bonus', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 20. WORK EXPERIENCE
INSERT INTO work_experiences (id, user_id, owner_id, company, position, level, country, start_date, end_date, is_current, industry, notes, created_at, updated_at) VALUES
 (1, @UID@, 1, 'Shell Infortech',  'Software Developer',       NULL,  'Singapore', DATE '2015-01-15', DATE '2016-10-15', FALSE, 'Tech', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 'Welcome Realtime', 'Software Engineer',        NULL,  'Singapore', DATE '2016-10-16', DATE '2019-02-28', FALSE, 'Tech', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, @UID@, 1, 'BCS',              'Senior Software Engineer', 'PM5', 'Singapore', DATE '2019-03-01', NULL,              TRUE,  'Tech', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 21. TAX RECORDS
INSERT INTO tax_records (id, user_id, owner_id, assessment_year, employment, donations, reliefs, srs_deduction, chargeable_income, tax, tax_rebate, tax_payable, country, currency, notes, created_at, updated_at) VALUES
 (1, @UID@, 1, 2024, 220032, 570, 1000, 0,     218220, 24611.80, 200, 24411.80, 'Singapore', 'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 2025, 275300, 570, 1000, 35400, 238330, 27000.00, 0,   27000.00, 'Singapore', 'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 22. BUDGET & EXPENSES
INSERT INTO budget_categories (id, user_id, name, parent_category, sort_order, is_active, created_at) VALUES
 (1, @UID@, 'Groceries',     'Essential', 1, TRUE, CURRENT_TIMESTAMP),
 (2, @UID@, 'Dining',        'Lifestyle', 2, TRUE, CURRENT_TIMESTAMP),
 (3, @UID@, 'Transport',     'Essential', 3, TRUE, CURRENT_TIMESTAMP),
 (4, @UID@, 'Utilities',     'Essential', 4, TRUE, CURRENT_TIMESTAMP),
 (5, @UID@, 'Entertainment', 'Lifestyle', 5, TRUE, CURRENT_TIMESTAMP);

INSERT INTO budget_plans (id, user_id, "year", "month", savings_target_pct, notes, created_at, updated_at) VALUES
 (1, @UID@, 2026, 8, 40.00, 'August plan', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO budget_incomes (id, user_id, budget_plan_id, source, amount, notes, created_at) VALUES
 (1, @UID@, 1, 'Salary',    14442, NULL, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 'Dividends', 200,   NULL, CURRENT_TIMESTAMP);

INSERT INTO budget_allocations (id, user_id, budget_plan_id, category_id, planned_amount, created_at) VALUES
 (1, @UID@, 1, 1, 800, CURRENT_TIMESTAMP),
 (2, @UID@, 1, 2, 500, CURRENT_TIMESTAMP),
 (3, @UID@, 1, 3, 300, CURRENT_TIMESTAMP),
 (4, @UID@, 1, 4, 400, CURRENT_TIMESTAMP),
 (5, @UID@, 1, 5, 250, CURRENT_TIMESTAMP);

INSERT INTO expenses (id, user_id, category_id, expense_date, description, amount, currency, notes, created_at, updated_at) VALUES
 (1, @UID@, 1, DATE '2026-08-03', 'FairPrice groceries',  132.40, 'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, @UID@, 2, DATE '2026-08-05', 'Dinner at restaurant', 85.00,  'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, @UID@, 3, DATE '2026-08-06', 'Grab rides',           42.50,  'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (4, @UID@, 4, DATE '2026-08-08', 'Electricity bill',     110.00, 'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (5, @UID@, 1, DATE '2026-08-12', 'Weekly groceries',     98.75,  'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (6, @UID@, 5, DATE '2026-08-15', 'Cinema tickets',       36.00,  'SGD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 23. CURRENCY RATES + USER CURRENCIES
INSERT INTO user_currencies (id, user_id, code, name, created_at) VALUES
 (1, @UID@, 'SGD', 'Singapore Dollar', CURRENT_TIMESTAMP),
 (2, @UID@, 'USD', 'US Dollar',        CURRENT_TIMESTAMP),
 (3, @UID@, 'EUR', 'Euro',             CURRENT_TIMESTAMP),
 (4, @UID@, 'LKR', 'Sri Lankan Rupee', CURRENT_TIMESTAMP);

INSERT INTO currency_rates (id, user_id, from_currency, to_currency, rate, effective_date, updated_at) VALUES
 (1, @UID@, 'USD', 'SGD', 1.35000000, CURRENT_DATE, CURRENT_TIMESTAMP),
 (2, @UID@, 'EUR', 'SGD', 1.45000000, CURRENT_DATE, CURRENT_TIMESTAMP),
 (3, @UID@, 'LKR', 'SGD', 0.00390000, CURRENT_DATE, CURRENT_TIMESTAMP);

-- 24. PLANNING: ALLOCATION TARGETS
INSERT INTO allocation_targets (id, user_id, owner_id, asset_type, target_percentage, target_amount) VALUES
 (1, @UID@, 1, 'INDEX_FUND',      37, 370000),
 (2, @UID@, 1, 'GROWTH_EQUITY',   23, 230000),
 (3, @UID@, 1, 'DIVIDEND_EQUITY', 10, 100000),
 (4, @UID@, 1, 'CRYPTO',          5,  50000),
 (5, @UID@, 1, 'FIXED_DEPOSIT',   5,  50000),
 (6, @UID@, 1, 'SAVINGS',         5,  50000);

-- 25. NET WORTH SNAPSHOTS (history)
INSERT INTO net_worth_snapshots (id, user_id, owner_id, snapshot_date, snapshot_year, total_index_fund, total_mutual_fund, total_growth_equity, total_dividend_equity, total_leveraged_etf, total_money_market, total_fixed_deposit, total_savings, total_crypto, total_net_worth, currency, created_at) VALUES
 (1, @UID@, NULL, DATE '2023-12-31', 2023, 85000,  9000,  60000,  0,     12000, 0, 24000, 180000, 10500, 380500, 'SGD', CURRENT_TIMESTAMP),
 (2, @UID@, NULL, DATE '2024-12-31', 2024, 180000, 11000, 130000, 12000, 22000, 0, 33000, 195000, 17500, 600500, 'SGD', CURRENT_TIMESTAMP),
 (3, @UID@, NULL, DATE '2025-12-31', 2025, 250000, 16000, 180000, 20000, 15000, 0, 50000, 200000, 12000, 743000, 'SGD', CURRENT_TIMESTAMP);

-- 26. NET WORTH CONFIG
INSERT INTO net_worth_config (id, user_id, asset_type, include_in_net_worth, label) VALUES
 (1, @UID@, 'INDEX_FUND',      TRUE, 'Index Funds'),
 (2, @UID@, 'GROWTH_EQUITY',   TRUE, 'Growth Equities'),
 (3, @UID@, 'DIVIDEND_EQUITY', TRUE, 'Dividend Equities'),
 (4, @UID@, 'CRYPTO',          TRUE, 'Crypto'),
 (5, @UID@, 'FIXED_DEPOSIT',   TRUE, 'Fixed Deposits'),
 (6, @UID@, 'SAVINGS',         TRUE, 'Bank Savings');

-- 27. AUDIT LOG (sample so the admin Audit page isn't empty)
INSERT INTO audit_logs (id, user_id, username, action, entity, entity_id, details, timestamp) VALUES
 (1, @UID@, 'mugu', 'CREATE', 'Expense',      1, 'Created expense: FairPrice groceries', CURRENT_TIMESTAMP),
 (2, @UID@, 'mugu', 'CREATE', 'FixedDeposit', 1, 'Created FD: FD-NSB-001',               CURRENT_TIMESTAMP),
 (3, @UID@, 'mugu', 'UPDATE', 'BankSavings',  1, 'Updated balance for DBS Multiplier',   CURRENT_TIMESTAMP);

-- 28. RESET IDENTITY SEQUENCES so the app can insert new rows afterward
ALTER TABLE app_users              ALTER COLUMN id RESTART WITH 100;
ALTER TABLE owners                 ALTER COLUMN id RESTART WITH 100;
ALTER TABLE accounts               ALTER COLUMN id RESTART WITH 100;
ALTER TABLE assets                 ALTER COLUMN id RESTART WITH 100;
ALTER TABLE transactions           ALTER COLUMN id RESTART WITH 100;
ALTER TABLE holdings               ALTER COLUMN id RESTART WITH 100;
ALTER TABLE sold_positions         ALTER COLUMN id RESTART WITH 100;
ALTER TABLE dividends              ALTER COLUMN id RESTART WITH 100;
ALTER TABLE account_deposits       ALTER COLUMN id RESTART WITH 100;
ALTER TABLE bank_savings           ALTER COLUMN id RESTART WITH 100;
ALTER TABLE banks                  ALTER COLUMN id RESTART WITH 100;
ALTER TABLE fd_holders             ALTER COLUMN id RESTART WITH 100;
ALTER TABLE fixed_deposits         ALTER COLUMN id RESTART WITH 100;
ALTER TABLE generic_fixed_deposits ALTER COLUMN id RESTART WITH 100;
ALTER TABLE properties             ALTER COLUMN id RESTART WITH 100;
ALTER TABLE precious_metals        ALTER COLUMN id RESTART WITH 100;
ALTER TABLE bonds                  ALTER COLUMN id RESTART WITH 100;
ALTER TABLE home_loans             ALTER COLUMN id RESTART WITH 100;
ALTER TABLE loan_payments          ALTER COLUMN id RESTART WITH 100;
ALTER TABLE insurance_policies     ALTER COLUMN id RESTART WITH 100;
ALTER TABLE insurance_bonus_entries ALTER COLUMN id RESTART WITH 100;
ALTER TABLE retirement_fund_entries ALTER COLUMN id RESTART WITH 100;
ALTER TABLE salary_records         ALTER COLUMN id RESTART WITH 100;
ALTER TABLE work_experiences       ALTER COLUMN id RESTART WITH 100;
ALTER TABLE tax_records            ALTER COLUMN id RESTART WITH 100;
ALTER TABLE budget_categories      ALTER COLUMN id RESTART WITH 100;
ALTER TABLE budget_plans           ALTER COLUMN id RESTART WITH 100;
ALTER TABLE budget_incomes         ALTER COLUMN id RESTART WITH 100;
ALTER TABLE budget_allocations     ALTER COLUMN id RESTART WITH 100;
ALTER TABLE expenses               ALTER COLUMN id RESTART WITH 100;
ALTER TABLE user_currencies        ALTER COLUMN id RESTART WITH 100;
ALTER TABLE currency_rates         ALTER COLUMN id RESTART WITH 100;
ALTER TABLE allocation_targets     ALTER COLUMN id RESTART WITH 100;
ALTER TABLE net_worth_snapshots    ALTER COLUMN id RESTART WITH 100;
ALTER TABLE net_worth_config       ALTER COLUMN id RESTART WITH 100;
ALTER TABLE audit_logs             ALTER COLUMN id RESTART WITH 100;
