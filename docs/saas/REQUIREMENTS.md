# MyFinance SaaS Platform — Requirements

Status: Draft for review · Owner: Platform · Last updated: 2026-08-27

This document defines the requirements for a **separate** public-facing SaaS platform
that markets, sells, and provisions access to the existing MyFinance personal finance
application. It does **not** modify existing MyFinance behavior except for one additive,
guarded provisioning endpoint (see Integration).

---

## 1. Scope & Non-Goals

### In scope
- Public marketing website (Home, Features, How It Works, Pricing, FAQ, Contact, legal).
- Self-service signup with a 7-day free trial (no card required by default).
- Isolated customer billing/subscription portal (separate auth from the finance app).
- Stripe payments (card + PayNow) behind a provider abstraction.
- Subscription lifecycle management and payment notifications (email).
- Secure server-to-server provisioning into the existing MyFinance app.

### Non-goals (v1)
- Rewriting or refactoring the existing MyFinance app.
- A full admin console (configuration via DB-backed config + files is sufficient v1).
- Replacing the finance app's own login (the SaaS "Login" button redirects to it).
- Storing raw card data (handled entirely by Stripe; PCI scope minimized).

---

## 2. Actors

| Actor | Description |
|-------|-------------|
| Visitor | Anonymous public user browsing the marketing site. |
| Customer | A signed-up account owner managing trial/subscription/billing. |
| MyFinance User | The identity used to log into the existing finance app (provisioned on signup). |
| SaaS System | The SaaS backend performing provisioning, billing, notifications. |
| Payment Provider | Stripe (initially), via the PaymentProvider abstraction. |

Note: In v1 a **Customer maps 1:1 to a single MyFinance user (one tenant)**, matching the
existing app's tenant model (tenant = one AppUser).

---

## 3. Functional Requirements

### 3.1 Marketing site
- FR-1 Present product value proposition, real features, pricing, FAQ, trust/security.
- FR-2 "Start Free Trial" primary CTA and "Login" secondary CTA (redirects to finance app).
- FR-3 Feature/screenshot/video content is **configuration-driven**, not hardcoded.
- FR-4 SEO foundation: titles, meta, Open Graph, sitemap, robots, semantic HTML.
- FR-5 Accessible (WCAG-oriented), responsive, fast.

### 3.2 Signup & trial
- FR-6 Collect only name, email, password, plan (optional), terms acceptance.
- FR-7 Validate input server-side; enforce password policy; verify email format.
- FR-8 Bot/abuse protection (CAPTCHA hook + rate limiting) and account-enumeration protection.
- FR-9 Create a SaaS Customer and start a 7-day TRIAL (no payment required by default).
- FR-10 Send email verification and a welcome email.
- FR-11 Provision a MyFinance user via the secure internal provisioning API (idempotent).
- FR-12 After verification, the customer can log into the finance app.

### 3.3 Portal (authenticated customer area)
- FR-13 View current plan, subscription status, trial status and end date.
- FR-14 Upgrade / downgrade (where supported) / cancel (where supported).
- FR-15 View payment history and invoices/receipts (from Stripe) where available.
- FR-16 Update payment method; retry failed payments where supported.
- FR-17 The portal must NOT grant access to finance app pages or internal APIs.

### 3.4 Payments
- FR-18 Support card and PayNow via Stripe.
- FR-19 Provider is configurable/replaceable via a PaymentProvider interface.
- FR-20 Subscription state changes only from **verified server-side** events (webhooks),
  never from browser-reported success.
- FR-21 Idempotent payment operations and webhook processing.

### 3.5 Notifications
- FR-22 Configurable email templates for: welcome, verification, trial started,
  trial ending soon, trial expired, subscription created, payment successful,
  payment failed, subscription cancelled, subscription expired, password reset.
- FR-23 Emails must not contain sensitive data unnecessarily.

### 3.6 Access control communicated to finance app
- FR-24 The SaaS system communicates access state (trial active, subscription active,
  expired, cancelled) to the finance app through the provisioning/status API.
- FR-25 The finance app enforces access server-side; SaaS never relies on frontend-only checks.

---

## 4. Non-Functional Requirements

- NFR-1 Security: treat as internet-facing production (see SECURITY.md).
- NFR-2 Availability: graceful degradation if payment/email/finance API is down.
- NFR-3 Observability: structured logs, audit records for account/subscription/payment
  changes, health checks. Never log secrets/card/PII unnecessarily.
- NFR-4 Data isolation: no cross-customer/cross-tenant data access, enforced server-side.
- NFR-5 Configurability: plans, pricing, trial length, features, templates, content.
- NFR-6 Consistency: no inconsistent subscription states under failure/retry/out-of-order events.
- NFR-7 Reuse existing tech stack (Spring Boot + React/Vite); no new languages introduced.

---

## 5. Plans & Pricing (configurable — placeholder values)

Prices below are **placeholders (TODO: set real values)**. Plans map to the existing
finance app's `enabledFeatures` string so a plan directly controls feature access.

| Plan | Billing | Price (placeholder) | Trial | enabledFeatures (mapped) |
|------|---------|---------------------|-------|--------------------------|
| Free Trial | — | 0 for 7 days | 7 days | STARTER set |
| Starter | monthly/annual | TODO | — | PORTFOLIO,DIVIDENDS,CASH_FLOWS,REPORTS |
| Pro | monthly/annual | TODO | — | Starter + CRYPTO,BANK_SAVINGS,FIXED_DEPOSITS,BUDGET,SRS_CPF,TAX |
| Premium | monthly/annual | TODO | — | All features (empty string = all) |

Configurable per plan: name, description, price, currency, billing period (monthly/annual),
trial duration, feature set, limits, availability, promo pricing, recommended flag,
display order, active/inactive.

---

## 6. Integration with existing app (additive, approved: Option 2)

- A new endpoint on the existing MyFinance backend under `/api/internal/provisioning/**`,
  secured by a **service token** (shared secret from environment/secret store), intended
  for server-to-server use only and network-restricted in production.
- Operations: create user (idempotent, maps plan→enabledFeatures), update access status
  (active/suspended), fetch status. No existing endpoint or behavior is changed.

---

## 7. Acceptance criteria (high level)

- A visitor can sign up, verify email, and log into the finance app with a working trial.
- Trial expiry, upgrade, payment success/failure, and cancellation update subscription
  state correctly and drive finance-app access via the status API.
- Webhooks with invalid signatures are rejected; duplicate/out-of-order events are safe.
- No customer can view another customer's subscription, invoices, or payment data.
- The existing MyFinance test suite passes unchanged.
