---
inclusion: always
---

# Security Rules

## Authentication

- Stateless **JWT** bearer auth. Token carries `username`, `userId`, and `role`. `JwtAuthFilter` validates it on every request and populates the `SecurityContext`.
- Missing/malformed/`Basic` headers are ignored (request continues unauthenticated → protected endpoints return 401). Invalid tokens do not authenticate. The filter never throws to the client.
- Passwords are hashed (BCrypt via Spring Security). Never log or return password hashes.
- **Self-registration is disabled.** Only admins create users. `/api/auth/register` must reject non-admin callers.

## Authorization (RBAC)

- Two roles: `USER` and `ADMIN`.
- **Admin-only** endpoints: user management (`/api/users/**`), audit trail (`/api/audit/**`). Non-admins get **403**. Guard tests exist (`shouldReturn403ForNonAdmin...`) — keep them.
- **Bank creation** is a normal-user action; admins should not create banks. Respect the "who can do what" split already encoded per controller.
- Admins do not own financial data — never wire the dashboard/asset APIs to an admin session.

## Tenant Isolation

- See `conventions.md` → Multi-Tenancy. This is a **security boundary**, not just a data-modelling choice. Any new query on user data MUST filter by `userId`.
- Treat cross-tenant data exposure as a security bug of the highest priority.

## Secrets & Config

- JWT secret and the internal `provisioning.token` come from config/env. The default `app.jwt.secret` is a placeholder — production must override it via env/secret store.
- Provisioning endpoints are **disabled (503)** when `PROVISIONING_TOKEN` is empty. Don't enable them by default.
- Never commit real secrets. Flag `.env`, credential files, or hard-coded tokens before staging.

## Production Data Safety

- Production starts with **admin only** — no seed currencies/users/owners. `DataInitializer` must not run under the `prod` profile (`app.init-data=false`).
- Never run destructive operations against `data-prod/`. Schema changes must be additive and preserve existing data.
- The demo/dev database (`data/`) is the only place sample data belongs.

## When Making Security-Sensitive Changes

State explicitly what you verified (isolation test green, 403 guards intact, no secret leakage) and what you could not verify. Prefer adding a guard test over assuming behaviour.
