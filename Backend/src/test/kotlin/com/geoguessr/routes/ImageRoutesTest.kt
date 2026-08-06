package com.geoguessr.routes

import com.geoguessr.db.DatabaseFactory
import com.geoguessr.inference.InferenceClient
import com.geoguessr.inference.InvalidImageException
import com.geoguessr.inference.PredictionResponse
import com.geoguessr.inference.RankedPrediction
import com.geoguessr.repository.ImageRepository
import com.geoguessr.repository.PredictionRepository
import com.geoguessr.storage.LocalFileImageStorage
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** Always returns a canned prediction — no real model, no real HTTP call. */
private class FakeInferenceClient(
    private val response: PredictionResponse = PredictionResponse(
        resnet50 = listOf(RankedPrediction("France", 0.51, 1)),
        clip = listOf(RankedPrediction("France", 0.48, 1)),
        ensemble = listOf(RankedPrediction("France", 0.495, 1)),
    ),
    private val shouldRejectImage: Boolean = false,
) : InferenceClient {
    override suspend fun predict(imageBytes: ByteArray, filename: String): PredictionResponse {
        if (shouldRejectImage) throw InvalidImageException("not a real image")
        return response
    }

    override suspend fun isHealthy(): Boolean = true
}

class ImageRoutesTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        DatabaseFactory.init(jdbcUrl = "jdbc:h2:mem:test_${UUID.randomUUID()};DB_CLOSE_DELAY=-1")
    }

    @Test
    fun `POST images stores image and returns predictions`() = testApplication {
        application {
            install(ServerContentNegotiation) { json() }
            routing {
                imageRoutes(ImageRepository(), PredictionRepository(), LocalFileImageStorage(tempDir), FakeInferenceClient())
            }
        }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.submitFormWithBinaryData(
            url = "/images",
            formData = formData {
                append("file", byteArrayOf(1, 2, 3), io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"test.png\"")
                })
            },
        )

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<UploadImageResponse>()
        assertEquals("France", body.predictions.ensemble.first().country)
    }

    @Test
    fun `POST images with no file returns 400`() = testApplication {
        application {
            install(ServerContentNegotiation) { json() }
            routing {
                imageRoutes(ImageRepository(), PredictionRepository(), LocalFileImageStorage(tempDir), FakeInferenceClient())
            }
        }

        val response = client.post("/images")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST images propagates invalid image rejection as 400`() = testApplication {
        application {
            install(ServerContentNegotiation) { json() }
            routing {
                imageRoutes(
                    ImageRepository(),
                    PredictionRepository(),
                    LocalFileImageStorage(tempDir),
                    FakeInferenceClient(shouldRejectImage = true),
                )
            }
        }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.submitFormWithBinaryData(
            url = "/images",
            formData = formData {
                append("file", byteArrayOf(9, 9, 9), io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"bad.txt\"")
                })
            },
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET predictions for unknown image returns 404`() = testApplication {
        application {
            install(ServerContentNegotiation) { json() }
            routing {
                imageRoutes(ImageRepository(), PredictionRepository(), LocalFileImageStorage(tempDir), FakeInferenceClient())
            }
        }

        val response = client.get("/images/999999/predictions")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
