package com.geoguessr.repository

import com.geoguessr.db.DatabaseFactory
import com.geoguessr.db.ReferenceGroundTruth
import com.geoguessr.db.dbQuery
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.insert
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ImageRepositoryTest {

    private lateinit var repo: ImageRepository

    @BeforeEach
    fun setUp() {
        // Fresh isolated in-memory DB per test — no shared state, no ordering dependence.
        DatabaseFactory.init(jdbcUrl = "jdbc:h2:mem:test_${UUID.randomUUID()};DB_CLOSE_DELAY=-1")
        repo = ImageRepository()
    }

    @Test
    fun `insert and findByHash round trip`() = runTest {
        val inserted = repo.insert("hash123", "/path/to/file.png", "user_upload")

        val found = repo.findByHash("hash123")

        assertNotNull(found)
        assertEquals(inserted.id, found.id)
        assertEquals("/path/to/file.png", found.storagePath)
        assertEquals("user_upload", found.source)
    }

    @Test
    fun `findByHash returns null when not found`() = runTest {
        assertNull(repo.findByHash("nonexistent"))
    }

    @Test
    fun `findById returns inserted record`() = runTest {
        val inserted = repo.insert("hash456", "/path/b.png", "user_upload")

        val found = repo.findById(inserted.id)

        assertNotNull(found)
        assertEquals("hash456", found.contentHash)
    }

    @Test
    fun `findRandomSeed only returns seed images with ground truth`() = runTest {
        repo.insert("upload_hash", "/path/upload.png", "user_upload")
        val seedImage = repo.insert("seed_hash", "/path/seed.png", "seed_reference")

        dbQuery {
            ReferenceGroundTruth.insert {
                it[imageId] = seedImage.id
                it[actualCountry] = "Japan"
            }
        }

        val result = repo.findRandomSeed()

        assertNotNull(result)
        assertEquals(seedImage.id, result.id)
    }

    @Test
    fun `findRandomSeed returns null when no seed images have ground truth`() = runTest {
        repo.insert("seed_no_gt", "/path/seed2.png", "seed_reference")

        assertNull(repo.findRandomSeed())
    }

    @Test
    fun `findGroundTruth returns the correct country`() = runTest {
        val seedImage = repo.insert("seed_hash2", "/path/seed3.png", "seed_reference")
        dbQuery {
            ReferenceGroundTruth.insert {
                it[imageId] = seedImage.id
                it[actualCountry] = "Brazil"
            }
        }

        val groundTruth = repo.findGroundTruth(seedImage.id)

        assertNotNull(groundTruth)
        assertEquals("Brazil", groundTruth.actualCountry)
    }
}
