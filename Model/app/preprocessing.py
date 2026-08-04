"""
Inference-time preprocessing.

ResNet-50 uses the notebook's eval_tf (no augmentation — just resize,
tensor, normalize). CLIP uses its own processor, which handles resizing
and normalization internally with CLIP's expected stats.
"""
from PIL import Image
from torchvision import transforms
from transformers import CLIPProcessor

from . import config

_clip_processor: CLIPProcessor | None = None


def get_resnet_transform() -> transforms.Compose:
    """Matches build_transforms()'s eval_tf in the notebook (section 4.3)."""
    return transforms.Compose([
        transforms.Resize((config.RESNET_IMAGE_SIZE, config.RESNET_IMAGE_SIZE)),
        transforms.ToTensor(),
        transforms.Normalize(config.IMAGENET_MEAN, config.IMAGENET_STD),
    ])


def get_clip_processor() -> CLIPProcessor:
    """CLIPProcessor is stateless config, not weights, so it's cheap to load
    once and cache at module level."""
    global _clip_processor
    if _clip_processor is None:
        _clip_processor = CLIPProcessor.from_pretrained(config.CLIP_MODEL_NAME)
    return _clip_processor


def preprocess_for_resnet(image: Image.Image, transform: transforms.Compose):
    return transform(image.convert("RGB")).unsqueeze(0)  # add batch dim


def preprocess_for_clip(image: Image.Image, processor: CLIPProcessor):
    return processor(images=image.convert("RGB"), return_tensors="pt")["pixel_values"]
