package com.geoguessr.routes

import com.geoguessr.db.DatabaseFactory
import com.geoguessr.db.ReferenceGroundTruth
import com.geoguessr.db.dbQuery
import com.geoguessr.inference.InferenceClient
import com.geoguessr.inference.PredictionResponse
import com.geoguessr.inference.RankedPrediction
import com.geoguessr.repository.GameSessionRepository
import com.geoguessr.repository.ImageRecord
import com.geoguessr.repository.ImageRepository
import com.geoguessr.repository.PredictionRepository
import com.geoguessr.storage.LocalFileImageStorage
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class GameFakeInferenceClient(
    private val ensembleCountry: String = "Kenya",
) : InferenceClient {
    override suspend fun predict(imageBytes: ByteArray, filename: String): PredictionResponse =
        PredictionResponse(
            resnet50 = listOf(RankedPrediction(ensembleCountry, 0.6, 1)),
            clip = listOf(RankedPrediction(ensembleCountry, 0.55, 1)),
            ensemble = listOf(RankedPrediction(ensembleCountry, 0.575, 1)),
        )

    override suspend fun isHealthy(): Boolean = true
}

class GameRoutesTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var imageRepository: ImageRepository
    private lateinit var imageStorage: LocalFileImageStorage

    @BeforeEach
    fun setUp() {
        DatabaseFactory.init(jdbcUrl = "jdbc:h2:mem:test_${UUID.randomUUID()};DB_CLOSE_DELAY=-1")
        imageRepository = ImageRepository()
        imageStorage = LocalFileImageStorage(tempDir)
    }

    /** Inserts a seed image (with real bytes on disk) plus ground truth. Test
     * bodies passed to testApplication already run in a coroutine, so this
     * is a suspend fun rather than wrapping in runBlocking. */
    private suspend fun seedImageWithGroundTruth(actualCountry: String = "Kenya"): ImageRecord {
        val path = imageStorage.save(byteArrayOf(5, 5, 5), "seed.png")
        val image = imageRepository.insert("seed_${UUID.randomUUID()}", path, "seed_reference")
        dbQuery {
            ReferenceGroundTruth.insert {
                it[imageId] = image.id
                it[ReferenceGroundTruth.actualCountry] = actualCountry
            }
        }
        return image
    }

    private fun jsonClient(app: io.ktor.server.testing.ApplicationTestBuilder) =
        app.createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    @Test
    fun `GET random-image creates a session for a seeded image`() = testApplication {
        val seedImage = seedImageWithGroundTruth()
        application {
            install(ServerContentNegotiation) { json() }
            routing {
                gameRoutes(imageRepository, GameSessionRepository(), PredictionRepository(), imageStorage, GameFakeInferenceClient())
            }
        }
        val client = jsonClient(this)

        val response = client.get("/game/random-image")

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<RandomGameImageResponse>()
        assertEquals(seedImage.id, body.imageId)
    }

    @Test
    fun `GET random-image returns 404 when no seed images exist`() = testApplication {
        application {
            install(ServerContentNegotiation) { json() }
            routing {
                gameRoutes(imageRepository, GameSessionRepository(), PredictionRepository(), imageStorage, GameFakeInferenceClient())
            }
        }

        val response = client.get("/game/random-image")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST guess returns comparison and marks session complete`() = testApplication {
        val seedImage = seedImageWithGroundTruth(actualCountry = "Kenya")
        val sessionRepo = GameSessionRepository()
        val sessionId = sessionRepo.create(seedImage.id)

        application {
            install(ServerContentNegotiation) { json() }
            routing {
                gameRoutes(imageRepository, sessionRepo, PredictionRepository(), imageStorage, GameFakeInferenceClient(ensembleCountry = "Kenya"))
            }
        }
        val client = jsonClient(this)

        val response = client.post("/game/$sessionId/guess") {
            contentType(ContentType.Application.Json)
            setBody("""{"guessedCountry": "Kenya"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<GuessResultResponse>()
        assertEquals("Kenya", body.actualCountry)
        assertEquals("Kenya", body.modelGuess)
        assertTrue(body.correct)
    }

    @Test
    fun `POST guess on already-completed session returns 409`() = testApplication {
        val seedImage = seedImageWithGroundTruth()
        val sessionRepo = GameSessionRepository()
        val sessionId = sessionRepo.create(seedImage.id)
        sessionRepo.submitGuess(sessionId, "Already Guessed")

        application {
            install(ServerContentNegotiation) { json() }
            routing {
                gameRoutes(imageRepository, sessionRepo, PredictionRepository(), imageStorage, GameFakeInferenceClient())
            }
        }
        val client = jsonClient(this)

        val response = client.post("/game/$sessionId/guess") {
            contentType(ContentType.Application.Json)
            setBody("""{"guessedCountry": "Kenya"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `GET session returns 404 for unknown session`() = testApplication {
        application {
            install(ServerContentNegotiation) { json() }
            routing {
                gameRoutes(imageRepository, GameSessionRepository(), PredictionRepository(), imageStorage, GameFakeInferenceClient())
            }
        }

        val response = client.get("/game/999999")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}