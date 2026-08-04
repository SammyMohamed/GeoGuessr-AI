"""
Central configuration for the GeoGuessr inference service.

Every filesystem path and model-loading constant lives here so that when
the real deployment location for model weights is known, this is the only
file that needs to change.
"""
from pathlib import Path

# ---------------------------------------------------------------------------
# Base directories
# ---------------------------------------------------------------------------
# In the original notebook, checkpoints were written under
# `<DRIVE_ROOT>/<experiment_name>/best.pt`. We keep that same relative
# structure here, rooted at OUTPUT_DIR. Point OUTPUT_DIR at wherever the
# weights actually live (local disk, a mounted volume, cloud storage
# synced locally, etc.) when deploying.
OUTPUT_DIR = Path("runs")

# ---------------------------------------------------------------------------
# ResNet-50
# ---------------------------------------------------------------------------
RESNET_EXPERIMENT_NAME = "resnet50_baseline"
RESNET_FINETUNE_EXPERIMENT = f"{RESNET_EXPERIMENT_NAME}_finetune"
RESNET_CKPT_PATH = OUTPUT_DIR / RESNET_FINETUNE_EXPERIMENT / "best.pt"

RESNET_IMAGE_SIZE = 384
IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]

# ---------------------------------------------------------------------------
# CLIP
# ---------------------------------------------------------------------------
CLIP_MODEL_NAME = "openai/clip-vit-base-patch32"
CLIP_EXPERIMENT_NAME = "clip_vitb32_baseline"
CLIP_FINETUNE_EXPERIMENT = f"{CLIP_EXPERIMENT_NAME}_finetune"
CLIP_CKPT_PATH = OUTPUT_DIR / CLIP_FINETUNE_EXPERIMENT / "best.pt"

# ---------------------------------------------------------------------------
# Class labels
# ---------------------------------------------------------------------------
# The notebook builds class_to_idx/idx_to_class dynamically from train_df
# (sorted country names -> integer index). That mapping has to be
# reproducible at inference time without re-deriving it from the dataset,
# so it should be dumped once during/after training, e.g.:
#
#   import json
#   class_names = sorted(train_df["country"].unique())
#   json.dump(class_names, open("class_names.json", "w"))
#
# idx_to_class[i] == class_names[i], matching build_label_maps() in the
# notebook. Point this at that file once it exists.
CLASS_NAMES_PATH = OUTPUT_DIR / "class_names.json"

# ---------------------------------------------------------------------------
# Ensemble
# ---------------------------------------------------------------------------
# Equal-weight soft voting on softmax probabilities, matching the
# "Ensemble: RN + CLIP" variant evaluated in the notebook.
RESNET_ENSEMBLE_WEIGHT = 0.5
CLIP_ENSEMBLE_WEIGHT = 0.5

TOP_K = 5

# ---------------------------------------------------------------------------
# Device
# ---------------------------------------------------------------------------
DEVICE = "cuda"  # falls back to "cpu" automatically in models.py if unavailable
