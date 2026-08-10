package com.geoguessr

import com.geoguessr.db.DatabaseFactory
import com.geoguessr.db.Images
import com.geoguessr.db.ReferenceGroundTruth
import com.geoguessr.db.dbQuery
import com.geoguessr.inference.InferenceClientConfig
import com.geoguessr.inference.KtorInferenceClient
import com.geoguessr.inference.createDefaultHttpClient
import com.geoguessr.repository.GameSessionRepository
import com.geoguessr.repository.ImageRepository
import com.geoguessr.repository.PredictionRepository
import com.geoguessr.routes.ErrorResponse
import com.geoguessr.routes.gameRoutes
import com.geoguessr.routes.healthRoutes
import com.geoguessr.routes.imageRoutes
import com.geoguessr.storage.ImageStorage
import com.geoguessr.storage.LocalFileImageStorage
import com.geoguessr.storage.S3ImageStorage
import com.geoguessr.storage.createS3Client
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.select
import java.nio.file.Path

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

/** One-off seed for game mode's single sample image — only inserts if no
 * seed row exists yet, so it's safe to leave in while testing. */
suspend fun seedGameImageIfEmpty() = dbQuery {
    val alreadySeeded = Images.select { Images.sourceType eq "seed_reference" }.any()
    if (alreadySeeded) return@dbQuery

    val imageId = Images.insert {
        it[contentHash] = "seed-001"
        it[storagePath] = "C:/Users/Sammy/Documents/GeoGuessr AI/Frontend/public/game-image.jpg"
        it[sourceType] = "seed_reference"
        it[uploadedAt] = CurrentDateTime
    } get Images.id

    ReferenceGroundTruth.insert {
        it[ReferenceGroundTruth.imageId] = imageId
        it[actualCountry] = "Kenya" // TODO: replace with your sample image's real country
    }
}

fun Application.module() {
    DatabaseFactory.init()
    runBlocking { seedGameImageIfEmpty() }

    install(ContentNegotiation) { json() }
    install(CORS) {
        allowHost("localhost:5173")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Unexpected error"))
        }
    }

    val imageStorage: ImageStorage = System.getenv("S3_BUCKET_NAME")?.let { bucket ->
        S3ImageStorage(bucketName = bucket, client = createS3Client(System.getenv("AWS_REGION") ?: "us-east-1"))
    } ?: LocalFileImageStorage(Path.of("uploaded_images"))

    val httpClient = createDefaultHttpClient(InferenceClientConfig())
    val inferenceClient = KtorInferenceClient(httpClient)

    val imageRepository = ImageRepository()
    val predictionRepository = PredictionRepository()
    val gameSessionRepository = GameSessionRepository()

    routing {
        healthRoutes(inferenceClient)
        imageRoutes(imageRepository, predictionRepository, imageStorage, inferenceClient)
        gameRoutes(imageRepository, gameSessionRepository, predictionRepository, imageStorage, inferenceClient)
    }
}