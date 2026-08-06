package com.geoguessr.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Plain Exposed DSL tables — not the DAO/entity API. Repositories write
 * explicit select/insert/update statements against these rather than going
 * through entity objects, keeping the data layer close to actual SQL.
 */

object Images : Table("images") {
    val id = integer("id").autoIncrement()
    val contentHash = varchar("content_hash", 64).uniqueIndex()
    val storagePath = varchar("storage_path", 512)
    val sourceType = varchar("source", 32) // "user_upload" | "seed_reference"
    val uploadedAt = datetime("uploaded_at")

    override val primaryKey = PrimaryKey(id)
}

/** Only populated for seeded images used in game mode — user uploads never
 * have ground truth, so this stays a separate table rather than nullable
 * columns on Images. */
object ReferenceGroundTruth : Table("reference_ground_truth") {
    val imageId = integer("image_id").references(Images.id)
    val actualCountry = varchar("actual_country", 100)
    val actualLat = double("actual_lat").nullable()
    val actualLng = double("actual_lng").nullable()
    val sourceDataset = varchar("source_dataset", 100).nullable()

    override val primaryKey = PrimaryKey(imageId)
}

/** One row per prediction request. PredictionResults holds the actual
 * per-model, per-rank guesses — kept separate since one image upload
 * produces many result rows (3 models x top-5 each). */
object PredictionBatches : Table("prediction_batches") {
    val id = integer("id").autoIncrement()
    val imageId = integer("image_id").references(Images.id)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

object PredictionResults : Table("prediction_results") {
    val id = integer("id").autoIncrement()
    val batchId = integer("batch_id").references(PredictionBatches.id)
    val modelName = varchar("model_name", 32) // "resnet50" | "clip" | "ensemble"
    val predictedCountry = varchar("predicted_country", 100)
    val confidence = double("confidence")
    val rank = integer("rank")

    override val primaryKey = PrimaryKey(id)
}

object GameSessions : Table("game_sessions") {
    val id = integer("id").autoIncrement()
    val imageId = integer("image_id").references(Images.id)
    val userGuessCountry = varchar("user_guess_country", 100).nullable()
    val createdAt = datetime("created_at")
    val completedAt = datetime("completed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}