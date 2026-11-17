# Document AI service: OCR with Tesseract (Greek and English), field extraction, expense classifier.

# Shared base: Python and Tesseract with its Greek and English language data
FROM python:3.12.12-slim-trixie AS base
RUN apt-get update \
    && apt-get install --yes --no-install-recommends tesseract-ocr tesseract-ocr-ell tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1
WORKDIR /app

# Build stage: run the tests (including real OCR and the model's quality gate), then train the model.
# If a test fails, no image is produced.
FROM base AS build
COPY pyproject.toml ./
RUN mkdir -p src/document_ai training \
    && touch src/document_ai/__init__.py training/__init__.py \
    && pip install ".[dev]"
COPY src ./src
COPY training ./training
COPY tests ./tests
RUN pip install --no-deps --force-reinstall . \
    && python -m pytest -q -p no:cacheprovider \
    && python -m training.train_classifier --out models

# Runtime image: only the service, its runtime libraries and the trained model; no tests or dev tools
FROM base
COPY pyproject.toml ./
COPY src ./src
RUN pip install . && rm -rf src pyproject.toml
COPY --from=build /app/models ./models
ENV DOCUMENT_AI_MODEL_PATH=/app/models/expense-classifier.joblib
RUN useradd --system --uid 10001 app
USER app
EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/health', timeout=2)"]
CMD ["uvicorn", "document_ai.main:app", "--host", "0.0.0.0", "--port", "8090", "--workers", "2"]
