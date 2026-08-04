"""
Model architectures and checkpoint loading.

Ported directly from the training notebook's `build_model()` factory
(section 4.5) and `CLIPClassifier` (section 9), stripped of anything
training-specific (loss, optimizer, freezing helpers). Only the pieces
needed to reconstruct the architectures and load trained weights remain.
"""
import json

import torch
import torch.nn as nn
from torchvision import models
from transformers import CLIPModel

from . import config


def get_device() -> torch.device:
    if config.DEVICE == "cuda" and torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")


def load_class_names() -> list[str]:
    """Load the ordered list of country names. idx_to_class[i] == names[i]."""
    with open(config.CLASS_NAMES_PATH) as f:
        return json.load(f)


def build_resnet50(num_classes: int) -> nn.Module:
    """Same architecture as build_model('resnet50', ...) in the notebook."""
    m = models.resnet50(weights=None)  # weights loaded from our checkpoint, not ImageNet
    m.fc = nn.Linear(m.fc.in_features, num_classes)
    return m


class CLIPClassifier(nn.Module):
    """CLIP image encoder + linear classification head.

    Identical to the notebook's CLIPClassifier (section 9.3).
    """

    def __init__(self, clip_model, num_classes, head_dim=None):
        super().__init__()
        self.vision = clip_model.vision_model
        self.visual_projection = clip_model.visual_projection
        in_features = head_dim or clip_model.config.projection_dim
        self.classifier = nn.Linear(in_features, num_classes)

    def forward(self, pixel_values):
        vision_out = self.vision(pixel_values=pixel_values)
        pooled = vision_out.pooler_output
        image_features = self.visual_projection(pooled)
        return self.classifier(image_features)


def load_resnet50(num_classes: int, device: torch.device) -> nn.Module:
    model = build_resnet50(num_classes).to(device)
    ckpt = torch.load(config.RESNET_CKPT_PATH, map_location=device, weights_only=False)
    model.load_state_dict(ckpt["model_state"])
    model.eval()
    return model


def load_clip_classifier(num_classes: int, device: torch.device) -> nn.Module:
    base_clip = CLIPModel.from_pretrained(config.CLIP_MODEL_NAME).to(device)
    model = CLIPClassifier(base_clip, num_classes=num_classes).to(device)
    ckpt = torch.load(config.CLIP_CKPT_PATH, map_location=device, weights_only=False)
    model.load_state_dict(ckpt["model_state"])
    model.eval()
    return model
