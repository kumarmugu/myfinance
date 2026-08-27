# MyFinance SaaS Platform — Flows

Status: Draft for review · Last updated: 2026-08-27

Detailed customer journeys, failure handling, and reconciliation. Complements
ARCHITECTURE.md (diagrams) and SECURITY.md (controls).

---

## 1. Signup → verification → trial → app access

1. Visitor submits signup (name, email, password, optional plan, terms).
2. saas-backend validates input, runs bot/rate-limit checks.
3. Create `Customer` (status `PENDING_VERIFICATION`).
4. Create `Subscription` (`TRIAL`, `trialEndsAt = now + 7d`, plan = chosen or default).
5. Send verification email (single-use, time-limited token).
6. Provision finance user (idempotent) → maps plan to `enabledFeatures`.
7. Send welcome email.
8. Visitor clicks verification link → `Customer.status = ACTIVE`.
9. Visitor clicks "Login" → redirected to the existing finance app `/login`.

Idempotency: if signup is retried with the same email while `PENDING_VERIFICATION`,
resend verification instead of creating a duplicate; provisioning uses an idempotency key.

---

## 2. Payment → subscription activation

1. Customer selects a paid plan in the portal.
2. saas-backend creates a Stripe Checkout Session / PaymentIntent (card or PayNow),
   returns client secret / redirect URL. Publishable key only on the client.
3. Customer completes payment on Stripe-hosted UI.
4. Stripe sends webhook(s): `checkout.session.completed`,
   `invoice.paid`, `customer.subscription.updated`, etc.
5. saas-backend verifies signature, checks idempotency (event id), updates
   `Subscription.state` (→ `ACTIVE`), stores Stripe references.
6. saas-backend calls finance app status API → access active.
7. Send payment-success email.

The browser return page shows a "processing" state and polls subscription status; it
never itself flips the subscription to active.

---

## 3. Trial lifecycle (scheduled)

- A scheduled job evaluates trials:
  - `trialEndsAt - 2d` → send "trial ending soon".
  - `trialEndsAt` passed and no active paid subscription → `TRIAL → EXPIRED`,
    call finance app to suspend access, send "trial expired".
- If the customer pays before expiry → `TRIAL → ACTIVE`.

---

## 4. Plan change (upgrade/downgrade)

1. Customer selects new plan in portal.
2. saas-backend updates the Stripe subscription (proration per config).
3. On confirming webhook, update local plan + `enabledFeatures` mapping.
4. Push new feature set to finance app via provisioning/status API.
5. Notify by email.

Downgrades that remove features take effect at period end (config-driven) to avoid
mid-cycle data-access surprises.

---

## 5. Cancellation & expiry

- Cancel at period end (default): `ACTIVE → CANCELLED`, access continues until
  `currentPeriodEnd`, then `CANCELLED → EXPIRED` + suspend finance access.
- Immediate cancel (if enabled): suspend at cancellation.

---

## 6. Payment failure & retry (PAST_DUE)

1. `invoice.payment_failed` webhook → `ACTIVE → PAST_DUE`, send "payment failed".
2. Stripe dunning retries; customer may update payment method and retry in the portal.
3. On recovery (`invoice.paid`) → `PAST_DUE → ACTIVE`.
4. If grace period ends without recovery → `PAST_DUE → EXPIRED` + suspend access.

---

## 7. Failure & recovery scenarios

| Scenario | Handling |
|----------|----------|
| Finance app API down during signup | Persist Customer+Subscription; enqueue provisioning for retry with backoff; verification email still sent; access granted once provisioning succeeds. |
| Payment succeeds but browser never returns | Webhook is source of truth; subscription activates server-side; portal reflects it on next poll. |
| Duplicate webhook | Event id persisted; duplicates are no-ops. |
| Out-of-order webhooks | Reconcile against Stripe object state (fetch current subscription) rather than trusting event order. |
| Webhook delayed | Portal shows "processing"; scheduled reconciliation job catches up. |
| Email provider down | Emails queued/retried; failure does not block signup or payment state changes. |
| Duplicate signup (same email) | Uniform response; resend verification if unverified; no duplicate customer. |
| Payment timeout | Idempotency key prevents double charge on retry. |

Reconciliation job: periodically compares local subscription state with Stripe and repairs
drift; re-attempts failed provisioning calls.

---

## 8. Access-control contract with finance app

| SaaS state | Finance app access |
|------------|--------------------|
| TRIAL (verified) | Allowed (features = trial plan set) |
| ACTIVE | Allowed (features = plan set) |
| PAST_DUE | Allowed during grace (config), then suspended |
| CANCELLED | Allowed until period end |
| EXPIRED | Suspended |

The finance app enforces access server-side based on the status pushed via the provisioning
API; the SaaS platform never relies on frontend redirects for enforcement.
