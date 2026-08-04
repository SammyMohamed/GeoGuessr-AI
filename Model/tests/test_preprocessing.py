"""
Tests for preprocessing.py.

The ResNet path is pure torchvision transforms, so it's tested directly.
The CLIP path normally downloads a processor config from Hugging Face on
first use — that's mocked out here so the suite stays offline and fast.
"""
from unittest.mock import MagicMock

import torch

from app import config, preprocessing


def test_resnet_transform_output_shape(sample_image):
    transform = preprocessing.get_resnet_transform()
    tensor = transform(sample_image)

    assert tensor.shape == (3, config.RESNET_IMAGE_SIZE, config.RESNET_IMAGE_SIZE)
    assert tensor.dtype == torch.float32


def test_resnet_transform_normalizes(sample_image):
    """A uniform mid-gray-ish image shouldn't produce raw [0,1] values after
    normalization — this catches someone accidentally dropping the
    Normalize step."""
    transform = preprocessing.get_resnet_transform()
    tensor = transform(sample_image)

    assert tensor.min() < 0 or tensor.max() > 1


def test_preprocess_for_resnet_adds_batch_dim(sample_image):
    transform = preprocessing.get_resnet_transform()
    batched = preprocessing.preprocess_for_resnet(sample_image, transform)

    assert batched.shape == (1, 3, config.RESNET_IMAGE_SIZE, config.RESNET_IMAGE_SIZE)


def test_preprocess_for_clip_uses_processor(sample_image):
    """Uses a fake processor so this never hits the network — we're testing
    that we call the processor correctly, not testing CLIP's own code."""
    fake_processor = MagicMock()
    fake_processor.return_value = {"pixel_values": torch.zeros(1, 3, 224, 224)}

    result = preprocessing.preprocess_for_clip(sample_image, fake_processor)

    fake_processor.assert_called_once()
    call_kwargs = fake_processor.call_args.kwargs
    assert call_kwargs["images"].mode == "RGB"
    assert result.shape == (1, 3, 224, 224)


def test_get_clip_processor_is_cached(monkeypatch):
    """get_clip_processor() should only construct the processor once and
    reuse it — this is what keeps repeated calls cheap."""
    calls = []

    def fake_from_pretrained(name):
        calls.append(name)
        return MagicMock()

    monkeypatch.setattr(preprocessing, "_clip_processor", None)
    monkeypatch.setattr(
        preprocessing.CLIPProcessor, "from_pretrained", staticmethod(fake_from_pretrained)
    )

    preprocessing.get_clip_processor()
    preprocessing.get_clip_processor()

    assert len(calls) == 1
