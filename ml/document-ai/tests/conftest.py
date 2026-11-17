import pytest
from fastapi.testclient import TestClient

from document_ai.classifier import ExpenseClassifier, build_pipeline
from document_ai.main import app, get_classifier
from training.receipts import generate


@pytest.fixture(scope="session")
def classifier() -> ExpenseClassifier:
    """A small model trained in a second, so tests never depend on a file left by a build."""
    receipts = generate(60, seed=1)
    pipeline = build_pipeline().fit([r.text for r in receipts], [r.category for r in receipts])
    return ExpenseClassifier(pipeline, {"version": "test"})


@pytest.fixture
def client(classifier: ExpenseClassifier) -> TestClient:
    # Not entered as a context manager, so start-up does not load the model file from disk
    app.dependency_overrides[get_classifier] = lambda: classifier
    yield TestClient(app)
    app.dependency_overrides.clear()
