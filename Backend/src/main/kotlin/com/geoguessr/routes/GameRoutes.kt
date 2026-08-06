package com.geoguessr.routes

import com.geoguessr.inference.InferenceClient
import com.geoguessr.repository.GameSessionRepository
import com.geoguessr.repository.ImageRepository
import com.geoguessr.repository.PredictionRepository
import com.geoguessr.storage.ImageStorage
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.gameRoutes(
    imageRepository: ImageRepository,
    gameSessionRepository: GameSessionRepository,
    predictionRepository: PredictionRepository,
    imageStorage: ImageStorage,
    inferenceClient: InferenceClient,
) {
    route("/game") {

        get("/random-image") {
            val image = imageRepository.findRandomSeed()
            if (image == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("No seed images available"))
                return@get
            }

            val sessionId = gameSessionRepository.create(image.id)
            call.respond(HttpStatusCode.Created, RandomGameImageResponse(sessionId, image.id))
        }

        post("/{sessionId}/guess") {
            val sessionId = call.parameters["sessionId"]?.toIntOrNull()
            if (sessionId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid session id"))
                return@post
            }

            val session = gameSessionRepository.findById(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Session not found"))
                return@post
            }
            if (session.completed) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Session already completed"))
                return@post
            }

            val groundTruth = imageRepository.findGroundTruth(session.imageId)
            if (groundTruth == null) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No ground truth for this image"))
                return@post
            }

            val request = call.receive<GuessRequest>()

            // Reuse a previously computed prediction if we have one for this
            // seed image; otherwise run inference now and cache it.
            var predictions = predictionRepository.getLatestPredictions(session.imageId)
            if (predictions == null) {
                val imageRecord = imageRepository.findById(session.imageId)!!
                val bytes = imageStorage.read(imageRecord.storagePath)
                predictions = inferenceClient.predict(bytes, "seed_image.jpg")
                predictionRepository.savePredictions(session.imageId, predictions)
            }

            gameSessionRepository.submitGuess(sessionId, request.guessedCountry)

            val modelGuess = predictions.ensemble.first().country
            call.respond(
                GuessResultResponse(
                    sessionId = sessionId,
                    userGuess = request.guessedCountry,
                    modelGuess = modelGuess,
                    actualCountry = groundTruth.actualCountry,
                    correct = request.guessedCountry.equals(groundTruth.actualCountry, ignoreCase = true),
                ),
            )
        }

        get("/{sessionId}") {
            val sessionId = call.parameters["sessionId"]?.toIntOrNull()
            if (sessionId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid session id"))
                return@get
            }

            val session = gameSessionRepository.findById(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Session not found"))
                return@get
            }

            call.respond(
                GameSessionResponse(
                    sessionId = session.id,
                    imageId = session.imageId,
                    userGuess = session.userGuessCountry,
                    completed = session.completed,
                ),
            )
        }
    }
}
