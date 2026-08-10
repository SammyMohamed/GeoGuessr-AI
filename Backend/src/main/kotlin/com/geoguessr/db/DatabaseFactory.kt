package com.geoguessr.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Reads a Postgres connection from environment variables when present
 * (production/Neon), falling back to the local H2 file for local dev.
 * jdbcUrl/driver can still be overridden explicitly — tests use this for
 * per-test isolated in-memory databases.
 *
 * Nothing in the repositories needs to change for this swap: they're
 * written against Exposed's DSL, not H2-specific SQL, so Exposed generates
 * dialect-correct DDL for whichever database it connects to.
 */
object DatabaseFactory {
    fun init(jdbcUrl: String? = null, driver: String? = null) {
        val resolvedUrl = jdbcUrl
            ?: System.getenv("DATABASE_URL")
            ?: "jdbc:h2:file:./data/geoguessr;DB_CLOSE_DELAY=-1"

        val resolvedDriver = driver
            ?: if (resolvedUrl.startsWith("jdbc:postgresql")) "org.postgresql.Driver" else "org.h2.Driver"

        val user = System.getenv("DATABASE_USER") ?: ""
        val password = System.getenv("DATABASE_PASSWORD") ?: ""

        if (user.isNotEmpty()) {
            Database.connect(resolvedUrl, driver = resolvedDriver, user = user, password = password)
        } else {
            Database.connect(resolvedUrl, driver = resolvedDriver)
        }

        transaction {
            SchemaUtils.create(Images, ReferenceGroundTruth, PredictionBatches, PredictionResults, GameSessions)
        }
    }
}

/** Runs a blocking Exposed transaction on the IO dispatcher so it doesn't
 * block a request-handling thread. */
suspend fun <T> dbQuery(block: () -> T): T =
    withContext(Dispatchers.IO) { transaction { block() } }