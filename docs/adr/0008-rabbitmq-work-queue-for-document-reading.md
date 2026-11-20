# 0008. A RabbitMQ work queue for reading documents, fed by an outbox

Status: accepted · 2026-11-20 · refines [0003](0003-kafka-events-rabbitmq-websockets.md)

## Context

Every upload has to be read by document AI. OCR is slow (from under a second for a PDF with a text
layer to tens of seconds for a multi-page scan) and CPU-bound, and the service can be down or
restarting. The upload request should not wait for it, a job must not be lost if the core stops
half way, and a burst of uploads must not send document AI more work than it can do at once.

Kafka (ADR 0003) is planned for replayable domain events in phase 7. What is needed now is
different: each job done once, retried with back-off, and set aside if it keeps failing.

## Decision

- **Outbox.** `DocumentUploaded` is published inside the upload's transaction. Spring Modulith's event
  publication registry (JDBC, table `event_publication`) stores it with that transaction and sends it
  to RabbitMQ only after commit, waiting for the broker's publisher confirm. Events not sent when the
  app stopped are sent again on restart. So there is never a job for an upload that rolled back, and
  never an upload without a job.
- **Work queue.** Exchange `tameio.documents` (topic) routes `document.uploaded` and
  `document.read-requested` to the quorum queue `tameio.documents.read`. `DocumentReader` consumes it
  with 2 to 4 consumers per instance and a prefetch of 1, which bounds how many documents are read at
  once however many are uploaded.
- **Retries and dead letters.** If document AI does not answer, Spring AMQP retries after 5 s and 15 s.
  After the third attempt the document is marked `FAILED` (`GAVE_UP`) and the message goes through the
  dead-letter exchange to `tameio.documents.read.dead`, where it can be inspected and replayed. A
  definite answer ("this file has no text") ends the job at once, with no retries.
- **Idempotent consumer.** Delivery is at least once, so a job for a document that is already read or
  has failed for good is skipped.
- **No transaction around the HTTP call.** Status changes before and after it commit on their own, so
  a long OCR never holds a database connection.

## Consequences

- Uploads answer at once; the list shows each document's status and updates by polling until the
  WebSocket channel of ADR 0003 arrives.
- Several core instances share the queue as competing consumers without any coordination code.
- RabbitMQ is now part of the stack from phase 2 rather than phase 7, and it is the same broker that
  will relay WebSocket messages.
- When Kafka arrives, `DocumentUploaded` can be externalized to it as well, for replay, without
  changing the reading job.
