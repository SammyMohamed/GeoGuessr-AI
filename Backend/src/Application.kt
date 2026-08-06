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
import com.geoguessr.storage.LocalFileImageStorage
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.http.HttpStatusCode
import java.nio.file.Path

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()

    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Unexpected error"))
        }
    }

    val imageStorage = LocalFileImageStorage(Path.of("uploaded_images"))
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
