# Tameio

A platform for Greek small businesses. It keeps their books, sends their invoices to myDATA (AADE), and lets them apply for a business loan from a bank (here the fictional **Kyma Bank · demo**).

> Work in progress. This first slice covers sign-in and business registration. See [docs/design.md](docs/design.md) for the full plan and [docs/adr/](docs/adr/) for the decisions behind it.

## What works today

- Sign in or sign up through Keycloak (OpenID Connect, PKCE)
- Register a business with its ΑΦΜ, checked on both sides with the official check digit
- List only the businesses you belong to; anyone else gets 404, not 403, so the API does not reveal that a business exists
- Errors follow RFC 9457 (`application/problem+json`)

## Stack

| Layer | Choice |
| --- | --- |
| Backend | Java 21, Spring Boot 4, Spring Modulith, Spring Security (OAuth2 resource server), Spring Data JPA |
| Database | PostgreSQL 17, schema owned by Flyway (`ddl-auto: validate`) |
| Identity | Keycloak 26 (realm imported from `infra/keycloak`) |
| Frontend | React 19, TypeScript, Vite, MUI, TanStack Query, React Hook Form + Zod |
| Tests | JUnit 5, Testcontainers (real Postgres), MockMvc; Vitest |
| CI | GitHub Actions: backend `verify`, frontend lint + test + build |

## Architecture

```mermaid
flowchart LR
    browser[React app<br/>Vite · MUI · TanStack Query]
    keycloak[Keycloak<br/>OpenID Connect]
    subgraph api[Spring Boot · modular monolith]
        identity[identity]
        platform[platform<br/>security · errors · persistence]
    end
    db[(PostgreSQL<br/>Flyway)]

    browser -- sign in, PKCE --> keycloak
    browser -- REST + JWT --> api
    api -- checks token signature --> keycloak
    api --> db
```

- **Modular monolith.** One deployable, split into business modules (`identity` today, then `documents`, `accounting`, `mydata`, `lending`, …). Spring Modulith tests that a module only uses another module's public API, so a module can later be moved into its own service if it ever needs to be ([ADR 0001](docs/adr/0001-modular-monolith-first.md)).
- **Inside a module:** controller → service → repository. Classes are package-private unless another module needs them. The API never returns JPA entities, only records such as `BusinessView`.
- **`platform`** holds what every module shares: JWT validation and role mapping, RFC 9457 errors, the audited base entity, ΑΦΜ validation.
- **Lombok** is limited to constructors, getters and loggers. `@Data`, setters and generated `equals`/`hashCode` are turned off in `lombok.config`, because they cause bugs in JPA entities.

## Run it locally

Requirements: Docker, JDK 21 or newer, Node 22.

```bash
# 1. Postgres (port 5433) and Keycloak (port 8180)
docker compose up -d

# 2. Backend on http://localhost:8080
cd backend
./mvnw spring-boot:run

# 3. Frontend on http://localhost:5180
cd frontend
npm install
npm run dev
```

Open http://localhost:5180 and sign in with a demo user:

| User | Password | Role |
| --- | --- | --- |
| `maria` | `maria` | Business owner |
| `giorgos` | `giorgos` | Bank analyst (cannot reach the business API) |

These users exist only in the local dev realm.

Useful links while it runs:

- API docs: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Keycloak admin: http://localhost:8180 (admin / admin)

## Tests

```bash
cd backend && ./mvnw verify   # needs Docker for Testcontainers
cd frontend && npm test
```

## Configuration

The backend reads its settings from environment variables prefixed `TAMEIO_`. The defaults suit local development; the `prod` profile has no defaults for secrets and logs in ECS JSON.

| Variable | Default |
| --- | --- |
| `TAMEIO_DB_URL` | `jdbc:postgresql://localhost:5433/tameio` |
| `TAMEIO_DB_USERNAME` | `tameio` |
| `TAMEIO_DB_PASSWORD` | `tameio` |
| `TAMEIO_KEYCLOAK_ISSUER` | `http://localhost:8180/realms/tameio` |

## Project layout

```
backend/    Spring Boot app, one package per module (platform, identity, ...)
frontend/   React app, one folder per feature
infra/      Keycloak realm and, later, the rest of the local infrastructure
docs/       Design document and architecture decision records
```
