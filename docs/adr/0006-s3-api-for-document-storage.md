# 0006. The S3 API for document storage

Status: accepted · 2026-11-17

## Context

Businesses upload receipts, invoices and statements. These files do not belong in PostgreSQL: they are
large, never queried by content, and would make backups and replicas heavy. The design first named MinIO
as the store, but MinIO no longer publishes free container images, so it cannot be pinned and run in
local development and CI as before.

## Decision

The core stores files through the S3 API, using the AWS SDK for Java, behind a small `FileStore`
interface in the `platform` module. Locally and in tests the S3 server is SeaweedFS, a pinned open source
image. The database keeps each document's details and its storage key, never the bytes.

## Consequences

- The same code runs against AWS S3, MinIO, Ceph or any other S3-compatible service; only the endpoint and
  credentials change.
- Modules depend on `FileStore`, not on S3, so they stay simple to test and the storage can change without
  touching them.
- Storage and the database are separate systems with no shared transaction. An upload stores the file
  first and removes it again if the database rejects the record; a later clean-up job can catch files left
  behind by a crash between the two steps.
