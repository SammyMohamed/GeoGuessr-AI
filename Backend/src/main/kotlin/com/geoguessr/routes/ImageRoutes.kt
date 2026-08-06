package com.geoguessr.routes

import com.geoguessr.inference.InferenceException
import com.geoguessr.inference.InferenceClient
import com.geoguessr.inference.InvalidImageException
import com.geoguessr.repository.ImageRepository
import com.geoguessr.repository.PredictionRepository
import com.geoguessr.storage.ImageStorage
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.security.MessageDigest

fun Route.imageRoutes(
    imageRepository: ImageRepository,
    predictionRepository: PredictionRepository,
    imageStorage: ImageStorage,
    inferenceClient: InferenceClient,
) {
    route("/images") {

        post {
            val multipart = try {
                call.receiveMultipart()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Expected a multipart/form-data request with a file"))
                return@post
            }

            var imageBytes: ByteArray? = null
            var filename = "upload"

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    imageBytes = part.streamProvider().readBytes()
                    filename = part.originalFileName ?: filename
                }
                part.dispose()
            }

            val bytes = imageBytes
            if (bytes == null || bytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("No file uploaded"))
                return@post
            }

            val hash = sha256Hex(bytes)
            val image = imageRepository.findByHash(hash)
                ?: imageRepository.insert(hash, imageStorage.save(bytes, filename), source = "user_upload")

            val predictions = try {
                inferenceClient.predict(bytes, filename)
            } catch (e: InvalidImageException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid image"))
                return@post
            } catch (e: InferenceException) {
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Prediction service unavailable"))
                return@post
            }

            predictionRepository.savePredictions(image.id, predictions)
            call.respond(HttpStatusCode.Created, UploadImageResponse(image.id, predictions))
        }

        get("/{id}/predictions") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid image id"))
                return@get
            }

            val predictions = predictionRepository.getLatestPredictions(id)
            if (predictions == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("No predictions found for image $id"))
                return@get
            }

            call.respond(ImagePredictionsResponse(id, predictions))
        }
    }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }