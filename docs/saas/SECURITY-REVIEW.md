# MyFinance SaaS Platform — Security Review & Final Validation

Status: Completed · Last updated: 2026-08-27

This closes Phase 9 (security review for public internet exposure) and Phase 10 (validate that
the existing application is not broken).

---

## 1. Existing application: no regression (Phase 10)

- **Backend**: full suite **245 tests, 0 failures** (`./mvnw test`). Includes
  `MultiTenantIsolationTest` (confirmed executed) and the admin `shouldReturn403ForNonAdmin...`
  guards — all green.
- **Frontend**: full suite **55 tests, 0 failures** (`npm test`).
- The only change to the existing app was **additive**: a new `com.myfinance.provisioning`
  package plus two backward-compatible edits (a SecurityConfig matcher + filter, and a new
  `app.provisioning.token` property defaulting to empty). No existing endpoint, entity, or
  behavior was modified. Self-registration remains disabled.
- Provisioning endpoints are **disabled (503)** unless `PROVISIONING_TOKEN` is set, so a default
  install is unaffected.

## 2. Secrets

- Verified no live secret keys (`sk_live`, `whsec_live`, `pk_live`) are committed anywhere.
- All sensitive SaaS config is environment-driven with empty/placeholder defaults:
  `SAAS_JWT_SECRET`, `PROVISIONING_TOKEN`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`,
  SMTP credentials. Only the Stripe **publishable** key is ever exposed to the browser
  (via `/api/public/config`), and a test asserts the secret key is never returned.
- Real prod config files are gitignored (`application-prod.yml`, `.env`).

## 3. OWASP-oriented review (Phase 9)

| Area | Control in place |
|------|------------------|
| Broken Access Control | Portal APIs resolve `CustomerPrincipal.customerId` from the JWT, never from the request body; `PortalControllerTest` asserts one customer cannot see another's payments. |
| Auth failures | Uniform login errors; enumeration-safe signup/forgot-password; BCrypt hashing; email verification; rate limiting on login/signup/reset (bucket4j). |
| Cryptographic failures | TLS assumed at the edge; BCrypt for passwords; one-time tokens stored only as SHA-256 hashes. |
| Injection | JPA/parameterized queries; Bean Validation on all inputs; email templates HTML-escape dynamic values (anti-injection test). |
| Insecure design | Subscription state machine + idempotency; provisioning idempotent by email; trials/access driven by verified server-side events. |
| Security misconfiguration | CSP, frame-ancestors none, referrer-policy, CORS restricted to the saas-web origin, stack traces/error messages suppressed in responses. |
| Vulnerable components | Pinned dependency versions; Stripe SDK pinned. |
| Integrity failures | Stripe webhook signature verified (fail-closed on bad/missing signature); duplicate events ignored; subscription NEVER activated from browser-reported success. |
| Logging/monitoring | Structured logs, dedicated AUDIT logger for account/subscription/payment events, actuator health + finance-app indicator. Emails and logs mask addresses; no secrets/card data logged. |
| SSRF | Server-to-server calls limited to the configured finance-app base URL and Stripe. |

## 4. Payment security (PCI scope minimized)

- Card/PayNow entry happens on Stripe-hosted Checkout (redirect flow). The SaaS backend and
  frontend never receive or store PAN/CVV. Only Stripe references (customer/subscription/
  payment ids) and non-sensitive metadata are stored.

## 5. Tenant isolation

- Each portal request is scoped to the authenticated customer id. Cross-customer reads are
  prevented server-side and covered by tests. The billing portal exposes no finance-app
  internal pages or APIs (frontend test asserts no links into `/portfolio|/dashboard|/admin|
  /transactions`).

## 6. Follow-ups before go-live (not blockers to the build)

- **Pricing**: plan prices and `stripePriceId` values are PLACEHOLDERS (`TODO(pricing)` in
  `PlanSeeder`). Set real values and create the matching Stripe Prices.
- **Media**: replace `MediaPlaceholder` with real screenshots/videos under `saas-web/public/media`.
- **Legal**: replace placeholder Terms/Privacy copy with reviewed legal text.
- **Secrets**: set `SAAS_JWT_SECRET`, `PROVISIONING_TOKEN` (same value on both services),
  `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET`, and SMTP creds in the
  deployment environment. Switch `EMAIL_PROVIDER=smtp`.
- **Infrastructure**: TLS/HSTS, WAF, DDoS protection, and centralized monitoring are deployment
  concerns (documented in SECURITY.md), to be provisioned by the hosting environment.
- **Rate limiting** is in-memory (single node). Move to a shared store (e.g. Redis) for
  multi-node deployments.

## 7. Test totals

- saas-backend: **74 tests**, all passing.
- saas-web: **13 tests**, all passing.
- Existing backend: **245 tests**, all passing (no regression).
- Existing frontend: **55 tests**, all passing (no regression).
