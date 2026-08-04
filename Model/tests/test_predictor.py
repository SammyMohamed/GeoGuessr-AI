"""
Tests for predictor.py.

Predictor.__init__ loads real weights, which we deliberately never do in
this suite. Instead we build a "bare" instance via __new__ and set only
the attributes each test needs, then exercise the pure logic (top-k
ranking, ensemble weighting) directly. This is the same reasoning as
mocking the CLIP processor in test_preprocessing.py: test our logic, not
someone else's I/O.
"""
import torch

from app import config
from app.predictor import Predictor


def make_bare_predictor(class_names):
    """A Predictor instance with no models loaded — enough to test _top_k
    and predict()'s combination logic in isolation."""
    p = Predictor.__new__(Predictor)
    p.class_names = class_names
    p.device = torch.device("cpu")
    return p


def test_top_k_returns_correct_count_and_order(fake_class_names):
    p = make_bare_predictor(fake_class_names)
    probs = torch.tensor([0.05, 0.60, 0.10, 0.05, 0.15, 0.05])

    results = p._top_k(probs, k=3)

    assert len(results) == 3
    assert [r.country for r in results] == ["France", "Kenya", "Japan"]
    assert [r.rank for r in results] == [1, 2, 3]
    # confidences should be strictly descending
    confidences = [r.confidence for r in results]
    assert confidences == sorted(confidences, reverse=True)


def test_top_k_confidences_match_input_probs(fake_class_names):
    p = make_bare_predictor(fake_class_names)
    probs = torch.tensor([0.7, 0.1, 0.1, 0.03, 0.03, 0.04])

    results = p._top_k(probs, k=1)

    assert results[0].country == "Canada"
    assert results[0].confidence == 0.7


def test_predict_combines_models_with_configured_weights(monkeypatch, fake_class_names, sample_image):
    p = make_bare_predictor(fake_class_names)

    resnet_probs = torch.tensor([0.9, 0.02, 0.02, 0.02, 0.02, 0.02])
    clip_probs = torch.tensor([0.1, 0.02, 0.02, 0.02, 0.02, 0.82])

    monkeypatch.setattr(p, "_resnet_probs", lambda image: resnet_probs)
    monkeypatch.setattr(p, "_clip_probs", lambda image: clip_probs)
    monkeypatch.setattr(config, "RESNET_ENSEMBLE_WEIGHT", 0.5)
    monkeypatch.setattr(config, "CLIP_ENSEMBLE_WEIGHT", 0.5)

    result = p.predict(sample_image)

    # equal-weight average -> Canada: 0.5, Kenya (Norway idx 5): 0.42, both
    # plausible top picks depending on exact averaging; check the math directly.
    expected_ensemble = (resnet_probs + clip_probs) / 2.0
    top_country = fake_class_names[int(expected_ensemble.argmax())]

    assert result["ensemble"][0]["country"] == top_country
    assert result["resnet50"][0]["country"] == "Canada"
    assert result["clip"][0]["country"] == "Norway"


def test_predict_returns_all_three_keys_with_top_k_length(monkeypatch, fake_class_names, sample_image):
    p = make_bare_predictor(fake_class_names)
    even_probs = torch.full((len(fake_class_names),), 1.0 / len(fake_class_names))

    monkeypatch.setattr(p, "_resnet_probs", lambda image: even_probs)
    monkeypatch.setattr(p, "_clip_probs", lambda image: even_probs)

    result = p.predict(sample_image)

    assert set(result.keys()) == {"resnet50", "clip", "ensemble"}
    for key in result:
        assert len(result[key]) == min(config.TOP_K, len(fake_class_names))


def test_ensemble_weights_sum_to_one():
    """Sanity check on the config itself — if these ever drift apart, the
    ensemble silently stops being a proper weighted average."""
    assert config.RESNET_ENSEMBLE_WEIGHT + config.CLIP_ENSEMBLE_WEIGHT == 1.0
