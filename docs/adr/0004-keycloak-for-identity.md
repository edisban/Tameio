# 0004. Keycloak for identity

Status: superseded by [0007](0007-sign-in-inside-the-core.md) · 2026-11-17

## Context

Tameio has two very different groups of users (businesses and bank staff), roles within each, several
tenants, and later more than one front end and service that must trust the same identity. Writing our own
sign-in would mean owning password storage, multi-factor authentication, sessions and token handling.

## Decision

Users sign in through Keycloak with OpenID Connect. The core is an OAuth2 resource server that validates
Keycloak's tokens; roles and the tenant come from token claims. Business users and bank staff are kept in
separate realms.

## Consequences

- Multi-factor authentication, password policies, account lockout and single sign-on come from a proven,
  widely used product, as in most enterprise and banking projects.
- Services only validate tokens; none of them stores passwords.
- One more component to run and configure, and local development needs a Keycloak container.
