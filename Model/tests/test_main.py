"""
Tests for main.py — the FastAPI app.

The Predictor class is monkeypatched at the app.main module level before
the TestClient starts, so entering the `with TestClient(app)` context
(which runs the lifespan startup) never tries to load real weights or hit
the network. This is the "startup smoke test": if the app can't start
cleanly with a stub predictor, something is wrong with the wiring, not
the model.
"""
import io

import pytest
from fastapi.testclient import TestClient

from app import main


@pytest.fixture
def client(monkeypatch, fake_predictor_class):
    monkeypatch.setattr(main, "Predictor", fake_predictor_class)
    with TestClient(main.app) as c:  # __enter__ triggers lifespan startup
        yield c


def test_app_starts_and_loads_predictor(client):
    """Startup smoke test: if lifespan raised, this fixture would already
    have failed before reaching here."""
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "models_loaded": True}


def test_predict_returns_expected_shape(client, sample_image_bytes):
    response = client.post(
        "/predict",
        files={"file": ("test.png", sample_image_bytes, "image/png")},
    )

    assert response.status_code == 200
    body = response.json()
    assert set(body.keys()) == {"resnet50", "clip", "ensemble"}
    assert body["ensemble"][0]["country"] == "France"


def test_predict_rejects_non_image_file(client):
    garbage = io.BytesIO(b"this is not an image, just text")

    response = client.post(
        "/predict",
        files={"file": ("test.txt", garbage, "text/plain")},
    )

    assert response.status_code == 400


def test_predict_requires_file_field(client):
    response = client.post("/predict")

    assert response.status_code == 422  # FastAPI's own validation, no file provided


def test_predict_503_when_models_not_loaded(monkeypatch, sample_image_bytes, fake_predictor_class):
    """If a request somehow lands before/after the model is loaded, the
    service should fail clearly rather than throw an AttributeError."""
    monkeypatch.setattr(main, "Predictor", fake_predictor_class)
    with TestClient(main.app) as c:
        # Simulate models having been torn down (e.g. app shutting down)
        main.predictor = None
        response = c.post(
            "/predict",
            files={"file": ("test.png", sample_image_bytes, "image/png")},
        )

    assert response.status_code == 503
