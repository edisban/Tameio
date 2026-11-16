# 0001. Start as a modular monolith

Status: accepted · 2026-11-16

## Context

Tameio has several clear areas (documents, accounting, myDATA, lending, assistants) and will eventually
need to scale parts of it independently. Splitting into microservices from day one would add network
calls, distributed transactions, separate deployments and operational load before we know where the real
boundaries and bottlenecks are.

## Decision

The business logic lives in one Spring Boot application divided into modules with strict boundaries:
each module exposes a small public API, owns its own tables, and talks to others through that API or
through domain events. Module boundaries are checked in tests (Spring Modulith).

The ML services are separate from the start (see [0002](0002-java-core-python-ml.md)), because they use a
different language and runtime, not because of scale.

## Consequences

- One deployment and one database to run while the domain settles; refactoring across modules is cheap.
- Any module can later be extracted into its own service along an existing boundary, using the events it
  already publishes.
- Discipline is needed to keep modules from reaching into each other; the boundary tests enforce it.
