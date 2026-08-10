package com.geoguessr

import com.geoguessr.db.DatabaseFactory
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
import java.nio.file.Path

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()

    install(ContentNegotiation) { json() }
    install(CORS) {
        val frontendHost = System.getenv("FRONTEND_HOST") ?: "localhost:5173"
        allowHost(frontendHost, schemes = listOf("http", "https"))
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Unexpected error"))
        }
    }

    // Uses S3 when S3_BUCKET_NAME is set (production); falls back to local
    // disk otherwise (local dev) — same pattern as the DB connection below.
    val imageStorage: ImageStorage = System.getenv("S3_BUCKET_NAME")?.let { bucket ->
        S3ImageStorage(bucketName = bucket, client = createS3Client(System.getenv("AWS_REGION") ?: "us-east-1"))
    } ?: LocalFileImageStorage(Path.of("uploaded_images"))

    val inferenceServiceUrl = System.getenv("INFERENCE_SERVICE_URL") ?: "http://localhost:8000"
    val inferenceConfig = InferenceClientConfig(baseUrl = inferenceServiceUrl)
    val httpClient = createDefaultHttpClient(inferenceConfig)
    val inferenceClient = KtorInferenceClient(httpClient, inferenceConfig)

    val imageRepository = ImageRepository()
    val predictionRepository = PredictionRepository()
    val gameSessionRepository = GameSessionRepository()

    routing {
        healthRoutes(inferenceClient)
        imageRoutes(imageRepository, predictionRepository, imageStorage, inferenceClient)
        gameRoutes(imageRepository, gameSessionRepository, predictionRepository, imageStorage, inferenceClient)
    }
}