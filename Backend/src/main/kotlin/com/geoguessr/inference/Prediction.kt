package com.geoguessr.inference

import kotlinx.serialization.Serializable

/**
 * Mirrors the RankedPrediction shape returned by the Python inference
 * service (app/predictor.py).
 */
@Serializable
data class RankedPrediction(
    val country: String,
    val confidence: Double,
    val rank: Int,
)

/**
 * Mirrors the JSON body returned by POST /predict — all three models'
 * results, even though only `ensemble` is shown to the user. Whether to
 * persist resnet50/clip is a decision for the caller, not this client.
 */
@Serializable
data class PredictionResponse(
    val resnet50: List<RankedPrediction>,
    val clip: List<RankedPrediction>,
    val ensemble: List<RankedPrediction>,
)
