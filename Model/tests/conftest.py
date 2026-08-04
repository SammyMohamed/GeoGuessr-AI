"""
Shared fixtures.

Nothing here touches real model weights or the network — the point is
that the whole suite runs fast and offline. Tests that need a Predictor
get a stubbed one via monkeypatching, not the real weight-loading class.
"""
import io

import pytest
from PIL import Image


@pytest.fixture
def sample_image_bytes() -> bytes:
    """A tiny synthetic RGB image, encoded as PNG bytes — stands in for an
    uploaded file without needing a real photo on disk."""
    img = Image.new("RGB", (64, 64), color=(120, 180, 220))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


@pytest.fixture
def sample_image() -> Image.Image:
    return Image.new("RGB", (64, 64), color=(120, 180, 220))


@pytest.fixture
def fake_class_names() -> list[str]:
    return ["Canada", "France", "Japan", "Brazil", "Kenya", "Norway"]


class FakePredictor:
    """Stands in for predictor.Predictor in API tests — returns canned
    results instead of running real inference."""

    def predict(self, image):
        return {
            "resnet50": [{"country": "France", "confidence": 0.51, "rank": 1}],
            "clip": [{"country": "France", "confidence": 0.48, "rank": 1}],
            "ensemble": [{"country": "France", "confidence": 0.495, "rank": 1}],
        }


@pytest.fixture
def fake_predictor_class():
    """Exposed as a fixture (rather than a plain import) so test_main.py
    doesn't need to import across test modules."""
    return FakePredictor
