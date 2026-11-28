# The assistant service: a LangGraph agent on Claude, read-only tools, and retrieval over the bank's documents.
FROM python:3.12.12-slim-trixie AS base
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    FASTEMBED_CACHE_PATH=/app/models
WORKDIR /app

# Build stage: the tests, including the retrieval quality gate with the real embedding model. If one fails, no
# image is produced.
FROM base AS build
COPY pyproject.toml ./
RUN mkdir -p src/assistant && touch src/assistant/__init__.py && pip install ".[dev]"
COPY src ./src
COPY tests ./tests
RUN pip install --no-deps --force-reinstall . && python -m pytest -q -p no:cacheprovider

# Runtime image: the service and the embedding model (fetched once here, so start-up needs no download)
FROM base
COPY pyproject.toml ./
COPY src ./src
RUN pip install . && rm -rf src pyproject.toml
COPY --from=build /app/models ./models
RUN useradd --system --uid 10001 app
USER app
EXPOSE 8091
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8091/health/live', timeout=2)"]
CMD ["uvicorn", "assistant.main:app", "--host", "0.0.0.0", "--port", "8091"]
