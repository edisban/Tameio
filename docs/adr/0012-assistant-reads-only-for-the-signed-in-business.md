# 0012. The assistant reads only, and only for the signed-in business

Status: accepted · 2026-11-28

## Context

A business asks the assistant about its money in plain language, and a language model decides which lookups to make.
Whatever the model is told by a user, a document or a mistake of its own, it must not see another business's data or
change anything.

## Decision

- The assistant is its own Python service (FastAPI), called only by the backend. The backend checks that the
  signed-in user belongs to the business, then sends the question with that business.
- The service binds the business into the tools itself; no tool takes a business as an argument, so the model
  cannot ask for another one.
- The tools only read, through a database role (`tameio_assistant`) that can only `SELECT` the six tables they need.
  The assistant has no way to move money or submit an application.
- The agent's flow is a LangGraph graph with a cap on turns; Claude is called through the official Anthropic SDK.
  Retrieval over the bank's documents runs in the service, with an ONNX embedding model, in memory.

## Consequences

- A prompt injection can at worst make the assistant read this business's own figures or say something wrong;
  it cannot reach other data or act.
- Every answer depends on a hosted model and costs a call; the service says when it has no key, and the app works
  without it.
- Actions (an application, a transfer) would need a confirmation step by the user, outside the model, before being
  added; none are now.
