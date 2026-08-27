# MyFinance SaaS Platform — Architecture

Status: Draft for review · Last updated: 2026-08-27

The SaaS platform is a **separate ecosystem** around the existing MyFinance app. It
consists of a public marketing site, a SaaS backend (signup, billing, subscriptions,
payments, notifications), and a thin secure integration into the existing app.

---

## 1. Components & responsibilities

| Component | Tech | Responsibility |
|-----------|------|----------------|
| `saas-web` | React 18 + Vite + Tailwind | Marketing site, signup UI, billing portal UI, Stripe UI. No secrets. |
| `saas-backend` | Spring Boot 3.2.5 (Java 17) | Signup, customer auth, plans, subscriptions, Stripe payments, webhooks, emails, provisioning client. Own DB, own secrets. |
| Existing `backend` | Spring Boot (unchanged) | The finance app. Gains ONE additive guarded provisioning endpoint. |
| Existing `frontend` | React (unchanged) | The finance app UI. Login target for the SaaS "Login" button. |
| Stripe | External | Card + PayNow processing, hosted payment UI, webhooks, invoices. |

Ports (dev): existing backend `8080`, existing frontend `5173`, `saas-backend` `8090`,
`saas-web` `5174`.

---

## 2. System diagram

```
                         Public Internet (HTTPS/TLS only)
                                       │
                 ┌─────────────────────┴──────────────────────┐
                 │                saas-web (SPA)               │
                 │  Home · Features · Pricing · FAQ · Signup   │
                 │  Billing Portal · "Login" button            │
                 └───────┬───────────────────────────┬─────────┘
        Public REST /api │                           │ "Login" → redirect
        (signup, portal, │                           ▼  to existing frontend /login
         Stripe intents) │                 ┌───────────────────────┐
                         ▼                 │  Existing MyFinance    │
                 ┌───────────────────┐     │  frontend + backend    │
                 │   saas-backend    │     │  (unchanged behavior)  │
                 │  ┌─────────────┐  │     └───────────┬────────────┘
                 │  │ Auth (JWT)  │  │                 ▲
                 │  │ Signup      │  │   Server-to-server, service token
                 │  │ Plans/Subs  │  │   POST /api/internal/provisioning/**
                 │  │ Payments    │──┼─────────────────┘  (network-restricted)
                 │  │ Webhooks    │  │
                 │  │ Emails      │  │        Webhooks (signed)
                 │  └─────────────┘  │◄───────────────────────────── Stripe
                 │   own DB, secrets │
                 └───────────────────┘
```

---

## 3. Data flows

### 3.1 Signup / trial / provisioning
```
Visitor → saas-web signup form
  → POST /api/public/signup (saas-backend)
      1. validate + bot/rate-limit check
      2. create Customer (status=PENDING_VERIFICATION)
      3. create Subscription (state=TRIAL, trialEndsAt=now+7d)
      4. send verification email
      5. call existing app: POST /api/internal/provisioning/users (idempotent)
         → creates MyFinance AppUser with plan→enabledFeatures
      6. send welcome email
  → Customer verifies email → status=ACTIVE
  → Customer clicks "Login" → existing finance app /login
```

### 3.2 Payment / subscription activation
```
Customer → Select plan → saas-web
  → saas-backend creates Stripe Checkout Session / PaymentIntent (card or PayNow)
  → Customer pays on Stripe-hosted UI
  → Stripe → webhook → saas-backend (verify signature, idempotent)
      → update Subscription (TRIAL/PAST_DUE → ACTIVE), persist event id
      → call existing app: update access status (active)
      → send payment-success email
```

Browser-reported success is treated as **informational only**; the subscription is
activated solely by the verified webhook.

---

## 4. Integration boundary (existing app)

Additive endpoint group on the existing backend (Option 2, approved):

```
POST /api/internal/provisioning/users     # idempotent create; body: email, plan, features
POST /api/internal/provisioning/status    # set access active/suspended for a user
GET  /api/internal/provisioning/users/{externalId}   # fetch status
```

- Secured by header `X-Provisioning-Token` matching a config secret
  (`app.provisioning.token`, sourced from env/secret store — never committed).
- Not exposed publicly; in production restricted to the SaaS backend's network/origin.
- Existing `/api/auth/**`, `/api/admin/**`, and all domain endpoints are unchanged.

---

## 5. Subscription state model

```
                 signup
                   │
                   ▼
                TRIAL ──────────── trial expires, no payment ──────────► EXPIRED
                   │                                                        ▲
       payment ok  │                                                        │
                   ▼                                                        │
                ACTIVE ──── payment fails ────► PAST_DUE ── grace ends ─────┤
                   │  ▲                             │                       │
        cancel     │  └──── payment recovers ───────┘                       │
                   ▼                                                        │
              CANCELLED ───────── period ends ────────────────────────────►┘
```

Transitions are event-driven (webhooks / scheduled trial checks). No scattered boolean
flags; a single `state` enum plus timestamps (`trialEndsAt`, `currentPeriodEnd`,
`cancelledAt`) is the source of truth.

---

## 6. Configuration & secrets

- All secrets (JWT signing key, Stripe secret key, Stripe webhook secret, provisioning
  token, SMTP/email creds) come from environment variables / secret store.
- No secrets in source control, frontend JS, or committed config. Frontend receives only
  the Stripe **publishable** key at runtime.
- Plans/pricing/content are DB-backed config seeded from files, editable without code changes.

---

## 7. Repository layout

```
myfinance/
├── backend/        (existing, unchanged except additive provisioning endpoint)
├── frontend/       (existing, unchanged)
├── saas-backend/   (new Spring Boot service)
├── saas-web/       (new React/Vite app)
└── docs/saas/      (these specs)
```
