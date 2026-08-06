package com.geoguessr.repository

import com.geoguessr.db.PredictionBatches
import com.geoguessr.db.PredictionResults
import com.geoguessr.db.dbQuery
import com.geoguessr.inference.PredictionResponse
import com.geoguessr.inference.RankedPrediction
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.select

class PredictionRepository {

    /** Persists all three models' results for one prediction event. All
     * three are stored even though only `ensemble` is ever shown to the
     * user — captured for future model-performance analysis. */
    suspend fun savePredictions(imageId: Int, predictions: PredictionResponse): Int = dbQuery {
        val batchId = PredictionBatches.insert {
            it[PredictionBatches.imageId] = imageId
            it[createdAt] = CurrentDateTime
        } get PredictionBatches.id

        fun insertRows(modelName: String, ranked: List<RankedPrediction>) {
            ranked.forEach { prediction ->
                PredictionResults.insert {
                    it[PredictionResults.batchId] = batchId
                    it[PredictionResults.modelName] = modelName
                    it[predictedCountry] = prediction.country
                    it[confidence] = prediction.confidence
                    it[rank] = prediction.rank
                }
            }
        }

        insertRows("resnet50", predictions.resnet50)
        insertRows("clip", predictions.clip)
        insertRows("ensemble", predictions.ensemble)

        batchId
    }

    /** Returns the most recent prediction batch for an image, reconstructed
     * into the same shape the inference service returns. */
    suspend fun getLatestPredictions(imageId: Int): PredictionResponse? = dbQuery {
        val latestBatchId = PredictionBatches
            .select { PredictionBatches.imageId eq imageId }
            .orderBy(PredictionBatches.createdAt, SortOrder.DESC)
            .limit(1)
            .map { it[PredictionBatches.id] }
            .singleOrNull() ?: return@dbQuery null

        val rows = PredictionResults
            .select { PredictionResults.batchId eq latestBatchId }
            .orderBy(PredictionResults.rank, SortOrder.ASC)
            .toList()

        fun rankedFor(modelName: String) = rows
            .filter { it[PredictionResults.modelName] == modelName }
            .map {
                RankedPrediction(
                    country = it[PredictionResults.predictedCountry],
                    confidence = it[PredictionResults.confidence],
                    rank = it[PredictionResults.rank],
                )
            }

        PredictionResponse(
            resnet50 = rankedFor("resnet50"),
            clip = rankedFor("clip"),
            ensemble = rankedFor("ensemble"),
        )
    }
}
