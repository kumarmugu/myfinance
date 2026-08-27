# MyFinance SaaS Platform — Security Architecture

Status: Draft for review · Last updated: 2026-08-27

The SaaS website is publicly accessible and handles account signup and payments. It is
treated as internet-facing production software. Payments and provisioning are
security-sensitive.

---

## 1. Trust boundaries

```
Untrusted:  Public internet, browsers, webhook payloads (until signature-verified)
Semi-trust: saas-web (holds only publishable keys; enforces nothing security-critical)
Trusted:    saas-backend (holds secrets, enforces authz/tenant isolation server-side)
Protected:  Existing MyFinance backend (internal provisioning endpoint, token-gated)
```

Rule: **never trust the frontend** for authorization or payment status.

---

## 2. Authentication

- Customer portal uses its own JWT auth, **fully isolated** from the finance app's auth.
- A portal token grants access to billing/subscription APIs only — never finance-app pages
  or `/api/internal/**` or finance domain APIs.
- Passwords hashed with BCrypt (matching existing app convention). Password policy enforced
  server-side (min length, complexity). Email verification required before finance-app access.
- Account recovery via time-limited, single-use reset tokens. Secure logout (client token discard;
  short token lifetime; optional deny-list for high-value actions).

## 3. Authorization & tenant isolation

- Every portal API resolves the customer from the verified JWT and scopes all queries to
  that customer id. Object-level checks (BOLA/IDOR protection) on every subscription,
  invoice, and payment resource.
- No endpoint accepts a customer id from the client to read another customer's data.
- Defense in depth: auth → authz → server-side ownership check → DB filter.

## 4. API security

- Input/schema validation on all endpoints (Bean Validation).
- Rate limiting on signup, login, password reset, and payment initiation.
- Account-enumeration protection: signup/login/forgot-password return uniform responses.
- Idempotency keys for payment initiation and provisioning.
- Secure error handling: no stack traces or secrets in responses.
- Audit logging for account, subscription, and payment state changes.

## 5. Webhook security (Stripe)

- Verify the Stripe signature (`Stripe-Signature`) using the webhook signing secret before
  processing. Reject unverified events with 400.
- Validate event structure/type; ignore unexpected types safely.
- Idempotency: persist processed Stripe event ids; duplicates are no-ops.
- Handle out-of-order/delayed events by reconciling against Stripe object state.
- Never activate a paid subscription from browser-reported success — only verified webhooks.

## 6. Payment data / PCI

- No raw PAN, CVV, or PIN is ever received or stored. Card entry happens on Stripe-hosted
  UI / Stripe Elements. saas-backend stores only Stripe references (customer id,
  subscription id, payment intent id) and non-sensitive metadata.
- This keeps PCI scope minimal (SAQ-A style).

## 7. Web / browser security (saas-web + saas-backend responses)

- HTTPS only; HSTS. Secure, HttpOnly, SameSite cookies if cookies are used.
- Security headers: CSP, X-Content-Type-Options, X-Frame-Options/frame-ancestors
  (clickjacking), Referrer-Policy, Permissions-Policy.
- CORS restricted to the known saas-web origin(s).
- Output encoding / framework auto-escaping to prevent XSS. No `dangerouslySetInnerHTML`
  with untrusted content.
- CSRF: bearer-token APIs are not cookie-based; if cookies are introduced, add CSRF tokens.
- SSRF protection on any server-side outbound calls (allow-list the finance app + Stripe).

## 8. Secrets management

Never in source control, git, frontend JS/HTML, or committed config:
- JWT signing secret, Stripe secret key, Stripe webhook secret, provisioning service token,
  email/SMTP credentials, DB credentials.

All provided via environment variables / secret store. A `.template` file documents required
variables; the real config is gitignored.

## 9. Provisioning endpoint (existing app) hardening

- Token-gated via `X-Provisioning-Token` (constant-time comparison).
- Additive only; does not weaken existing controls. Not publicly routable in production.
- Idempotent create prevents duplicate users on retries.

## 10. Infrastructure (deployment guidance)

TLS everywhere, WAF, DDoS protection, secure DNS/CDN, network segmentation between saas-web,
saas-backend, and the finance app, least-privilege IAM, centralized logging/monitoring/alerting,
dependency and image scanning.

## 11. Logging hygiene

Never log: passwords, card data, CVV, auth tokens, API secrets, full PII. Log identifiers and
event types. Payment and account/subscription changes get audit records with non-sensitive fields.

## 12. OWASP Top 10 mapping (summary)

| Risk | Mitigation |
|------|------------|
| Broken Access Control | Server-side authz + tenant scoping + BOLA checks |
| Cryptographic Failures | TLS, BCrypt, secrets in secret store |
| Injection | JPA/parameterized queries, Bean Validation |
| Insecure Design | State machine, idempotency, webhook verification |
| Security Misconfiguration | Security headers, CORS allow-list, no debug in prod |
| Vulnerable Components | Dependency scanning, pinned versions |
| Auth Failures | Rate limiting, enumeration protection, email verification |
| Integrity Failures | Signed webhooks, idempotency, no client-trusted state |
| Logging/Monitoring | Structured logs, audit trail, alerting |
| SSRF | Outbound allow-list (finance app + Stripe only) |
