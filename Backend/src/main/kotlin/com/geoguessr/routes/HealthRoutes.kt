package com.geoguessr.routes

import com.geoguessr.inference.InferenceClient
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val inferenceServiceHealthy: Boolean)

fun Route.healthRoutes(inferenceClient: InferenceClient) {
    get("/health") {
        call.respond(HealthResponse(status = "ok", inferenceServiceHealthy = inferenceClient.isHealthy()))
    }
}
