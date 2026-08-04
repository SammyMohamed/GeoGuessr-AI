"""
Core prediction logic.

Weights are loaded once (via Predictor.load(), called at service startup)
and reused across requests — never reloaded per-request. predict() runs
both models on a single image and combines them via the same equal-weight
soft-voting scheme evaluated as "Ensemble: RN + CLIP" in the notebook
(section 11): average the two models' softmax probability vectors.
"""
from dataclasses import dataclass

import torch
import torch.nn.functional as F
from PIL import Image

from . import config, models, preprocessing


@dataclass
class RankedPrediction:
    country: str
    confidence: float
    rank: int


class Predictor:
    """Holds loaded models + preprocessing so they're constructed exactly once."""

    def __init__(self):
        self.device = models.get_device()
        self.class_names = models.load_class_names()
        num_classes = len(self.class_names)

        self.resnet_model = models.load_resnet50(num_classes, self.device)
        self.clip_model = models.load_clip_classifier(num_classes, self.device)

        self.resnet_transform = preprocessing.get_resnet_transform()
        self.clip_processor = preprocessing.get_clip_processor()

    @torch.no_grad()
    def _resnet_probs(self, image: Image.Image) -> torch.Tensor:
        pixel_values = preprocessing.preprocess_for_resnet(image, self.resnet_transform)
        pixel_values = pixel_values.to(self.device)
        logits = self.resnet_model(pixel_values)
        return F.softmax(logits, dim=-1).squeeze(0)  # shape: (num_classes,)

    @torch.no_grad()
    def _clip_probs(self, image: Image.Image) -> torch.Tensor:
        pixel_values = preprocessing.preprocess_for_clip(image, self.clip_processor)
        pixel_values = pixel_values.to(self.device)
        logits = self.clip_model(pixel_values)
        return F.softmax(logits, dim=-1).squeeze(0)

    def _top_k(self, probs: torch.Tensor, k: int = config.TOP_K) -> list[RankedPrediction]:
        top_probs, top_idx = torch.topk(probs, k)
        return [
            RankedPrediction(
                country=self.class_names[idx.item()],
                confidence=round(prob.item(), 6),
                rank=rank + 1,
            )
            for rank, (prob, idx) in enumerate(zip(top_probs, top_idx))
        ]

    def predict(self, image: Image.Image) -> dict:
        """Returns top-K predictions from each model plus the ensembled result.

        All three are returned — the API layer decides what to persist vs.
        what to actually show the user.
        """
        resnet_probs = self._resnet_probs(image)
        clip_probs = self._clip_probs(image)

        ensemble_probs = (
            config.RESNET_ENSEMBLE_WEIGHT * resnet_probs
            + config.CLIP_ENSEMBLE_WEIGHT * clip_probs
        )

        return {
            "resnet50": [p.__dict__ for p in self._top_k(resnet_probs)],
            "clip": [p.__dict__ for p in self._top_k(clip_probs)],
            "ensemble": [p.__dict__ for p in self._top_k(ensemble_probs)],
        }
