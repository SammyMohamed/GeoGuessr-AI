package com.geoguessr.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * H2 for now — zero setup, works identically in-memory (tests) or as a
 * local file (dev). Swapping to Postgres for real deployment means changing
 * jdbcUrl/driver here (and adding the Postgres JDBC driver dependency) —
 * nothing in the repositories needs to change, since they're written
 * against Exposed's DSL, not H2 specifically.
 */
object DatabaseFactory {
    fun init(
        jdbcUrl: String = "jdbc:h2:file:./data/geoguessr;DB_CLOSE_DELAY=-1",
        driver: String = "org.h2.Driver",
    ) {
        Database.connect(jdbcUrl, driver = driver)
        transaction {
            SchemaUtils.create(Images, ReferenceGroundTruth, PredictionBatches, PredictionResults, GameSessions)
        }
    }
}

/** Runs a blocking Exposed transaction on the IO dispatcher so it doesn't
 * block a request-handling thread. */
suspend fun <T> dbQuery(block: () -> T): T =
    withContext(Dispatchers.IO) { transaction { block() } }
