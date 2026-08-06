package com.geoguessr.repository

import com.geoguessr.db.DatabaseFactory
import com.geoguessr.inference.PredictionResponse
import com.geoguessr.inference.RankedPrediction
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PredictionRepositoryTest {

    private lateinit var imageRepo: ImageRepository
    private lateinit var predictionRepo: PredictionRepository

    @BeforeEach
    fun setUp() {
        DatabaseFactory.init(jdbcUrl = "jdbc:h2:mem:test_${UUID.randomUUID()};DB_CLOSE_DELAY=-1")
        imageRepo = ImageRepository()
        predictionRepo = PredictionRepository()
    }

    private fun samplePredictions() = PredictionResponse(
        resnet50 = listOf(RankedPrediction("France", 0.51, 1), RankedPrediction("Belgium", 0.20, 2)),
        clip = listOf(RankedPrediction("France", 0.48, 1), RankedPrediction("Germany", 0.15, 2)),
        ensemble = listOf(RankedPrediction("France", 0.495, 1), RankedPrediction("Belgium", 0.15, 2)),
    )

    @Test
    fun `savePredictions and getLatestPredictions round trip`() = runTest {
        val image = imageRepo.insert("hash1", "/path.png", "user_upload")
        val predictions = samplePredictions()

        predictionRepo.savePredictions(image.id, predictions)
        val retrieved = predictionRepo.getLatestPredictions(image.id)

        assertNotNull(retrieved)
        assertEquals(predictions.resnet50.size, retrieved.resnet50.size)
        assertEquals("France", retrieved.ensemble.first().country)
        assertEquals(0.495, retrieved.ensemble.first().confidence)
        assertEquals(1, retrieved.ensemble.first().rank)
    }

    @Test
    fun `getLatestPredictions returns null when none exist`() = runTest {
        val image = imageRepo.insert("hash2", "/path2.png", "user_upload")

        assertNull(predictionRepo.getLatestPredictions(image.id))
    }

    @Test
    fun `getLatestPredictions returns the most recently saved batch`() = runTest {
        val image = imageRepo.insert("hash3", "/path3.png", "user_upload")

        predictionRepo.savePredictions(image.id, samplePredictions())
        val secondBatch = PredictionResponse(
            resnet50 = listOf(RankedPrediction("Japan", 0.9, 1)),
            clip = listOf(RankedPrediction("Japan", 0.85, 1)),
            ensemble = listOf(RankedPrediction("Japan", 0.875, 1)),
        )
        predictionRepo.savePredictions(image.id, secondBatch)

        val retrieved = predictionRepo.getLatestPredictions(image.id)

        assertNotNull(retrieved)
        assertEquals("Japan", retrieved.ensemble.first().country)
    }

    @Test
    fun `predictions for different images do not leak into each other`() = runTest {
        val imageA = imageRepo.insert("hashA", "/a.png", "user_upload")
        val imageB = imageRepo.insert("hashB", "/b.png", "user_upload")

        predictionRepo.savePredictions(imageA.id, samplePredictions())

        assertNotNull(predictionRepo.getLatestPredictions(imageA.id))
        assertNull(predictionRepo.getLatestPredictions(imageB.id))
    }
}
