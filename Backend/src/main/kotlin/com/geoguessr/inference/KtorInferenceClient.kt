package com.geoguessr.inference

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/** Builds the default HttpClient used in real (non-test) code. Tests build
 * their own HttpClient around a MockEngine instead of calling this. */
fun createDefaultHttpClient(config: InferenceClientConfig): HttpClient =
    HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutMs
        }
    }

class KtorInferenceClient(
    private val httpClient: HttpClient,
    private val config: InferenceClientConfig = InferenceClientConfig(),
) : InferenceClient {

    override suspend fun predict(imageBytes: ByteArray, filename: String): PredictionResponse {
        var lastException: Exception? = null

        repeat(config.maxRetries) { attempt ->
            try {
                val response = httpClient.submitFormWithBinaryData(
                    url = "${config.baseUrl}/predict",
                    formData = formData {
                        append(
                            "file",
                            imageBytes,
                            Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                            },
                        )
                    },
                )
                return handleResponse(response)
            } catch (e: InvalidImageException) {
                throw e // bad input, not a transient failure — don't waste retries on it
            } catch (e: Exception) {
                lastException = e
                if (attempt < config.maxRetries - 1) {
                    delay(config.initialBackoffMs * (1L shl attempt)) // exponential backoff
                }
            }
        }

        throw when (val e = lastException) {
            is InferenceException -> e
            else -> InferenceServiceException(
                "Prediction failed after ${config.maxRetries} attempts: ${e?.message}",
            )
        }
    }

    override suspend fun isHealthy(): Boolean =
        try {
            httpClient.get("${config.baseUrl}/health").status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }

    private suspend fun handleResponse(response: HttpResponse): PredictionResponse =
        when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.BadRequest -> throw InvalidImageException(
                "Image rejected by inference service: ${response.bodyAsText()}",
            )
            HttpStatusCode.ServiceUnavailable -> throw InferenceServiceUnavailableException(
                "Inference service is not ready",
            )
            else -> throw InferenceServiceException(
                "Unexpected response from inference service",
                statusCode = response.status.value,
            )
        }
}
