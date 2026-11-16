# 0002. Java for the core, Python for machine learning

Status: accepted · 2026-11-16

## Context

The business side needs what banks and their integrators standardise on: transactions, security,
integration with external systems, long-term maintainability. The ML side needs the libraries where models
are trained and served: PyTorch, Hugging Face, scikit-learn, Ultralytics YOLO, MLflow.

## Decision

- The core is Java 21 with Spring Boot.
- Models are trained in Python and served by small Python services (FastAPI), one per capability.
- The core calls them over HTTP for short, user-facing requests, with timeouts and fallbacks, and through
  Kafka events for longer work such as reading a document.
- The interface between them is versioned and covered by contract tests.

## Consequences

- Each side uses the best tools for its job, as most companies that ship ML do.
- Two languages and two build pipelines to maintain.
- The core keeps working when a model service is slow or down: requests fall back (for example to "no
  suggestion") and events wait in Kafka until the service is back.
