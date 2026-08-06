package com.geoguessr.repository

import com.geoguessr.db.GameSessions
import com.geoguessr.db.dbQuery
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update

data class GameSessionRecord(
    val id: Int,
    val imageId: Int,
    val userGuessCountry: String?,
    val completed: Boolean,
)

class GameSessionRepository {

    suspend fun create(imageId: Int): Int = dbQuery {
        GameSessions.insert {
            it[GameSessions.imageId] = imageId
            it[createdAt] = CurrentDateTime
        } get GameSessions.id
    }

    suspend fun findById(id: Int): GameSessionRecord? = dbQuery {
        GameSessions.select { GameSessions.id eq id }
            .map {
                GameSessionRecord(
                    id = it[GameSessions.id],
                    imageId = it[GameSessions.imageId],
                    userGuessCountry = it[GameSessions.userGuessCountry],
                    completed = it[GameSessions.completedAt] != null,
                )
            }
            .singleOrNull()
    }

    /** Returns true if a session was found and updated, false if the id didn't exist. */
    suspend fun submitGuess(sessionId: Int, guessedCountry: String): Boolean = dbQuery {
        val rowsUpdated = GameSessions.update({ GameSessions.id eq sessionId }) {
            it[userGuessCountry] = guessedCountry
            it[completedAt] = CurrentDateTime
        }
        rowsUpdated > 0
    }
}
