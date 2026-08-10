package com.geoguessr.repository

import com.geoguessr.db.Images
import com.geoguessr.db.ReferenceGroundTruth
import com.geoguessr.db.dbQuery
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.select

data class ImageRecord(
    val id: Int,
    val contentHash: String,
    val storagePath: String,
    val source: String,
)

data class GroundTruthRecord(
    val imageId: Int,
    val actualCountry: String,
)

class ImageRepository {

    suspend fun findByHash(contentHash: String): ImageRecord? = dbQuery {
        Images.select { Images.contentHash eq contentHash }
            .map { it.toImageRecord() }
            .singleOrNull()
    }

    suspend fun findById(id: Int): ImageRecord? = dbQuery {
        Images.select { Images.id eq id }
            .map { it.toImageRecord() }
            .singleOrNull()
    }

    suspend fun insert(contentHash: String, storagePath: String, source: String): ImageRecord = dbQuery {
        val newId = Images.insert {
            it[Images.contentHash] = contentHash
            it[Images.storagePath] = storagePath
            it[Images.sourceType] = source
            it[Images.uploadedAt] = CurrentDateTime
        } get Images.id

        ImageRecord(newId, contentHash, storagePath, source)
    }

    /** Picks a random seeded image that has ground truth attached, for game mode. */
    suspend fun findRandomSeed(): ImageRecord? = dbQuery {
        (Images innerJoin ReferenceGroundTruth)
            .select { Images.sourceType eq "seed_reference" }
            .map { it.toImageRecord() }
            .shuffled()
            .firstOrNull()
    }

    suspend fun findGroundTruth(imageId: Int): GroundTruthRecord? = dbQuery {
        ReferenceGroundTruth.select { ReferenceGroundTruth.imageId eq imageId }
            .map { GroundTruthRecord(it[ReferenceGroundTruth.imageId], it[ReferenceGroundTruth.actualCountry]) }
            .singleOrNull()
    }

    private fun ResultRow.toImageRecord() = ImageRecord(
        id = this[Images.id],
        contentHash = this[Images.contentHash],
        storagePath = this[Images.storagePath],
        source = this[Images.sourceType],
    )

    suspend fun insertGroundTruth(imageId: Int, actualCountry: String) = dbQuery {
        ReferenceGroundTruth.insert {
            it[ReferenceGroundTruth.imageId] = imageId
            it[ReferenceGroundTruth.actualCountry] = actualCountry
        }
    }
}