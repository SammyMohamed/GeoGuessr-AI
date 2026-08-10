"""
Ensures model weights and class names exist locally before the service
tries to load them — downloading from S3 if they're missing.

This makes config.py's paths work identically whether running locally
(weights already sitting on disk, as before) or in production (weights
live in S3 and need pulling down once per fresh container).
"""
import os

from . import config


def ensure_weights_available() -> None:
    """Downloads any missing checkpoint/class-names files from S3.

    No-op if S3_BUCKET_NAME isn't set (local dev, weights already on disk)
    or if a file already exists locally (e.g. a container that didn't
    restart, or you staged weights manually) — never re-downloads
    something that's already there.
    """
    bucket = os.environ.get("S3_BUCKET_NAME")
    if not bucket:
        return

    import boto3  # imported lazily so boto3 isn't a hard dependency for local dev

    s3 = boto3.client("s3", region_name=os.environ.get("AWS_REGION", "us-east-1"))

    for local_path in (config.RESNET_CKPT_PATH, config.CLIP_CKPT_PATH, config.CLASS_NAMES_PATH):
        if local_path.exists():
            continue

        local_path.parent.mkdir(parents=True, exist_ok=True)
        # Mirrors the same relative structure locally and in S3, e.g.
        # "runs/resnet50_baseline_finetune/best.pt" as both the local path
        # (relative to config.py's OUTPUT_DIR) and the S3 key.
        key = local_path.as_posix()
        print(f"Downloading s3://{bucket}/{key} -> {local_path}")
        s3.download_file(bucket, key, str(local_path))