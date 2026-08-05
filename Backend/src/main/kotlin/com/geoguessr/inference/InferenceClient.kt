package com.geoguessr.inference

/**
 * All tunables for talking to the Python inference service, kept in one
 * place — same reasoning as centralizing paths in the Python side's
 * config.py.
 */
data class InferenceClientConfig(
    val baseUrl: String = "http://localhost:8000",
    val requestTimeoutMs: Long = 10_000,
    val maxRetries: Int = 3,
    val initialBackoffMs: Long = 200,
)

/**
 * Talks to the Python /predict service. An interface (rather than exposing
 * KtorInferenceClient directly) so callers — and tests of callers — can
 * swap in a fake implementation without touching Ktor at all.
 */
interface InferenceClient {
    suspend fun predict(imageBytes: ByteArray, filename: String): PredictionResponse
    suspend fun isHealthy(): Boolean
}
