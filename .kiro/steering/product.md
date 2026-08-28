---
inclusion: always
---

# Product Overview

MyFinance is a self-hosted **personal finance and net-worth management** application. A single deployment supports multiple users, each of whom sees only their own data (multi-tenant by `userId`).

## Who Uses It

- **Regular users** — track their own assets, income, and net worth.
- **Admins** — manage users, toggle per-user feature access, and view the audit trail. Admins do NOT own financial data; the dashboard and asset pages are user-facing only.

## What Users Track

Different users care about different things, so almost every module can be enabled/disabled per user (see `conventions.md` → feature flags):

- **Investments** — stock/crypto accounts, holdings, transactions (buy/sell with average-cost tracking), dividends, sold positions.
- **Bank Savings** — savings accounts across banks, balances included/excluded from net worth.
- **Fixed Deposits** — a generic FD module (any bank) plus a specialised Sri Lanka FD module.
- **Real Estate** — properties with valuation, outstanding loan, equity, and rental income.
- **Precious Metals** — gold/silver/platinum holdings by weight, purity, and value.
- **Retirement** — CPF / SRS / employer contributions (Singapore-oriented).
- **Insurance** — policies with annual bonus tracking.
- **Home Loans** — mortgage tracking with payment schedules.
- **Salary & Work Experience** — income history feeding planning.
- **Tax** — tax records and summaries.
- **Budget & Expenses** — planned income/allocations vs actual expenses, by month, with category management and reports.
- **Currency Rates** — user-maintained FX rates (no external feed); user creates the currencies they need.
- **Planning / Net Worth** — allocation targets, deposits/withdrawals, net-worth snapshots and history, and a configurable net-worth composition.

## Product Principles

- **No seed data in production.** A fresh production install has only the admin user. Users create their own currencies, owners, and records. The demo/dev database may contain sample data.
- **User owns their data.** Nothing is shared between users. Deletes are blocked when a record is still referenced (with a clear message).
- **Money is exact.** All monetary values use `BigDecimal`, never `double`/`float`.
- **The UI reflects only enabled features** so users aren't shown modules they don't use.
