package com.geoguessr.repository

import com.geoguessr.db.DatabaseFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSessionRepositoryTest {

    private lateinit var imageRepo: ImageRepository
    private lateinit var sessionRepo: GameSessionRepository

    @BeforeEach
    fun setUp() {
        DatabaseFactory.init(jdbcUrl = "jdbc:h2:mem:test_${UUID.randomUUID()};DB_CLOSE_DELAY=-1")
        imageRepo = ImageRepository()
        sessionRepo = GameSessionRepository()
    }

    @Test
    fun `create returns a new incomplete session`() = runTest {
        val image = imageRepo.insert("hash1", "/p.png", "seed_reference")

        val sessionId = sessionRepo.create(image.id)
        val session = sessionRepo.findById(sessionId)

        assertNotNull(session)
        assertEquals(image.id, session.imageId)
        assertNull(session.userGuessCountry)
        assertFalse(session.completed)
    }

    @Test
    fun `submitGuess records the guess and marks session completed`() = runTest {
        val image = imageRepo.insert("hash2", "/p2.png", "seed_reference")
        val sessionId = sessionRepo.create(image.id)

        val updated = sessionRepo.submitGuess(sessionId, "Canada")

        assertTrue(updated)
        val session = sessionRepo.findById(sessionId)
        assertNotNull(session)
        assertEquals("Canada", session.userGuessCountry)
        assertTrue(session.completed)
    }

    @Test
    fun `submitGuess returns false for a nonexistent session`() = runTest {
        assertFalse(sessionRepo.submitGuess(999999, "Nowhere"))
    }

    @Test
    fun `findById returns null for a nonexistent session`() = runTest {
        assertNull(sessionRepo.findById(999999))
    }
}
