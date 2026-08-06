package com.geoguessr.routes

import com.geoguessr.inference.PredictionResponse
import kotlinx.serialization.Serializable

@Serializable
data class UploadImageResponse(val imageId: Int, val predictions: PredictionResponse)

@Serializable
data class ImagePredictionsResponse(val imageId: Int, val predictions: PredictionResponse)

@Serializable
data class RandomGameImageResponse(val sessionId: Int, val imageId: Int)

@Serializable
data class GuessRequest(val guessedCountry: String)

@Serializable
data class GuessResultResponse(
    val sessionId: Int,
    val userGuess: String,
    val modelGuess: String,
    val actualCountry: String,
    val correct: Boolean,
)

@Serializable
data class GameSessionResponse(
    val sessionId: Int,
    val imageId: Int,
    val userGuess: String?,
    val completed: Boolean,
)

@Serializable
data class ErrorResponse(val error: String)
