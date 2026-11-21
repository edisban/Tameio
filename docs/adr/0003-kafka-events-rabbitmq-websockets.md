# 0003. Kafka for domain events, RabbitMQ for live updates

Status: superseded by [0008](0008-rabbitmq-work-queue-for-document-reading.md) · 2026-11-16

> Kafka was never introduced: the only work that leaves the process is document reading, and a RabbitMQ
> work queue fed by an outbox does it (ADR 0008). Live updates over WebSocket are a later idea.

## Context

Two different kinds of messaging are needed:

1. Domain events between modules and ML services (a document was uploaded, an assessment completed). They
   must be durable, ordered per entity, and replayable, for example to re-run a new model over old
   documents.
2. Live updates to users' browsers over WebSocket. With several instances of the core behind a load
   balancer, a message produced on one instance must reach a user connected to another. Spring's built-in
   in-memory broker only works with a single instance.

## Decision

- Kafka carries domain events, keyed by the entity's id so events for one document or application stay
  in order.
- The core's STOMP endpoint relays to RabbitMQ, which delivers each user's messages to whichever instance
  holds their connection.

## Consequences

- Events can be replayed, and consumers can be added without changing producers.
- Live updates work with any number of instances, which the load-balancing phase needs.
- Two brokers to run. Using one for both was considered: Kafka is a poor fit for per-user WebSocket
  fan-out, and RabbitMQ gives up the long-term, replayable log that model reprocessing relies on.
