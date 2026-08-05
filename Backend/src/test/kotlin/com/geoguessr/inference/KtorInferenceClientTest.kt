package com.geoguessr.inference

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KtorInferenceClientTest {

    private val config = InferenceClientConfig(
        baseUrl = "http://fake-inference",
        maxRetries = 3,
        initialBackoffMs = 1, // keep retries fast in tests
    )

    private val samplePredictionJson = """
        {
          "resnet50": [{"country": "France", "confidence": 0.51, "rank": 1}],
          "clip": [{"country": "France", "confidence": 0.48, "rank": 1}],
          "ensemble": [{"country": "France", "confidence": 0.495, "rank": 1}]
        }
    """.trimIndent()

    private fun clientWith(engine: MockEngine): KtorInferenceClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return KtorInferenceClient(httpClient, config)
    }

    @Test
    fun `predict returns parsed response on 200`() = runTest {
        val engine = MockEngine {
            respond(
                content = samplePredictionJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = clientWith(engine).predict(byteArrayOf(1, 2, 3), "test.png")

        assertEquals("France", result.ensemble.first().country)
        assertEquals(0.495, result.ensemble.first().confidence)
    }

    @Test
    fun `predict throws InvalidImageException on 400 and does not retry`() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            respondError(HttpStatusCode.BadRequest, content = "File is not a valid image")
        }

        assertFailsWith<InvalidImageException> {
            clientWith(engine).predict(byteArrayOf(1, 2, 3), "bad.txt")
        }
        assertEquals(1, callCount, "a 400 shouldn't be retried — the image itself is the problem")
    }

    @Test
    fun `predict retries on transient failure then succeeds`() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            if (callCount < 3) {
                respondError(HttpStatusCode.InternalServerError)
            } else {
                respond(
                    content = samplePredictionJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }

        val result = clientWith(engine).predict(byteArrayOf(1, 2, 3), "test.png")

        assertEquals(3, callCount)
        assertEquals("France", result.ensemble.first().country)
    }

    @Test
    fun `predict throws after exhausting retries on repeated failure`() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            respondError(HttpStatusCode.InternalServerError)
        }

        assertFailsWith<InferenceServiceException> {
            clientWith(engine).predict(byteArrayOf(1, 2, 3), "test.png")
        }
        assertEquals(config.maxRetries, callCount)
    }

    @Test
    fun `predict surfaces service unavailable after retries exhausted`() = runTest {
        val engine = MockEngine {
            respondError(HttpStatusCode.ServiceUnavailable)
        }

        assertFailsWith<InferenceServiceUnavailableException> {
            clientWith(engine).predict(byteArrayOf(1, 2, 3), "test.png")
        }
    }

    @Test
    fun `isHealthy returns true on 200`() = runTest {
        val engine = MockEngine {
            respond(content = """{"status": "ok"}""", status = HttpStatusCode.OK)
        }

        assertTrue(clientWith(engine).isHealthy())
    }

    @Test
    fun `isHealthy returns false on error`() = runTest {
        val engine = MockEngine {
            respondError(HttpStatusCode.ServiceUnavailable)
        }

        assertFalse(clientWith(engine).isHealthy())
    }

    @Test
    fun `isHealthy returns false on network exception`() = runTest {
        val engine = MockEngine {
            throw java.io.IOException("connection refused")
        }

        assertFalse(clientWith(engine).isHealthy())
    }
}
