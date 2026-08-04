"""
FastAPI service wrapping the GeoGuessr country classifier.

Run with:
    uvicorn app.main:app --host 0.0.0.0 --port 8000

Weights are loaded exactly once, at process startup (via lifespan), not
per-request. The Kotlin backend calls POST /predict with raw image bytes
and gets back each model's top-5 plus the ensembled top-5.
"""
import io
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError

from .predictor import Predictor

predictor: Predictor | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global predictor
    predictor = Predictor()  # loads both models' weights once
    yield
    predictor = None


app = FastAPI(title="GeoGuessr Inference Service", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok", "models_loaded": predictor is not None}


@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    if predictor is None:
        raise HTTPException(status_code=503, detail="Models not loaded yet")

    raw = await file.read()
    try:
        image = Image.open(io.BytesIO(raw))
        image.load()
    except UnidentifiedImageError:
        raise HTTPException(status_code=400, detail="File is not a valid image")

    return predictor.predict(image)
