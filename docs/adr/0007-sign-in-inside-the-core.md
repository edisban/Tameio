# 0007. Sign-in inside the core

Status: accepted · 2026-11-17 · supersedes [0004](0004-keycloak-for-identity.md)

## Context

Keycloak ran as a separate service and used about 700 MB of memory, more than the core itself. For
one application with a few roles and no single sign-on across products, that was more machinery than
the project needs at this stage, and it made running the stack on a laptop heavier.

## Decision

The core signs users in itself:

- Accounts live in PostgreSQL (`user_account`); passwords are stored as Argon2id hashes with OWASP's
  parameters (19 MiB, 2 iterations). Each hash names its algorithm, so older BCrypt hashes still verify
  and are rehashed with Argon2id when their owner next signs in.
- A successful sign-in returns a JWT signed with HS256, valid for 8 hours, in an `HttpOnly`,
  `SameSite=Lax` cookie (`Secure` in production). Scripts cannot read it.
- Every request is authenticated by Spring Security's JWT support, as before; no session is kept on
  the server, so instances stay interchangeable behind a load balancer.
- Because the browser sends the cookie by itself, state-changing requests also need a CSRF token the
  page reads from its own cookie and sends in a header.
- Wrong email and wrong password get the same answer and take the same time; five wrong passwords in a
  row lock the account for 15 minutes.

## Consequences

- One service fewer and about 700 MB less memory; the sign-in pages match the rest of the app.
- We now own password storage, lock-out and sign-up, and their tests.
- Multi-factor authentication, password reset by email and single sign-on are not built in. If the
  bank side needs them, an identity provider can come back: the API already authenticates with JWTs,
  so only token issuing would move out.
