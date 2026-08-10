package com.geoguessr

import com.geoguessr.db.DatabaseFactory
import com.geoguessr.repository.ImageRepository
import com.geoguessr.storage.S3ImageStorage
import com.geoguessr.storage.createS3Client
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png")

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * One-time seeding tool — NOT run by the deployed app, and not invoked by
 * Application.kt's main(). Run this yourself, pointed at the deployed
 * Postgres and S3 bucket via environment variables, to upload a local
 * <Country>/<image files> folder as game-mode seed images.
 *
 * Required env vars: DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD,
 * S3_BUCKET_NAME, AWS_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY.
 * Pass the local folder path as the first program argument (defaults to
 * "seed-images" if omitted).
 */
fun main(args: Array<String>) {
    val baseDir = File(args.getOrNull(0) ?: "seed-images")
    if (!baseDir.exists()) {
        println("No directory found at ${baseDir.path}")
        return
    }

    val bucket = System.getenv("S3_BUCKET_NAME") ?: error("S3_BUCKET_NAME env var is required")
    val region = System.getenv("AWS_REGION") ?: "us-east-1"

    DatabaseFactory.init() // reads DATABASE_URL/USER/PASSWORD from env
    val imageStorage = S3ImageStorage(bucketName = bucket, client = createS3Client(region))
    val imageRepository = ImageRepository()

    var added = 0
    var skipped = 0
    val countryDirs = baseDir.listFiles { f -> f.isDirectory }.orEmpty()

    for (countryDir in countryDirs) {
        val country = countryDir.name
        val imageFiles = countryDir.listFiles { f ->
            f.isFile && f.extension.lowercase() in IMAGE_EXTENSIONS
        }.orEmpty()

        for (imageFile in imageFiles) {
            val bytes = imageFile.readBytes()
            val hash = sha256Hex(bytes)

            runBlocking {
                if (imageRepository.findByHash(hash) == null) {
                    val key = imageStorage.save(bytes, imageFile.name)
                    val image = imageRepository.insert(hash, key, source = "seed_reference")
                    imageRepository.insertGroundTruth(image.id, country)
                    added++
                } else {
                    skipped++
                }
            }
        }
    }

    println("Seeding complete: $added new image(s) uploaded, $skipped already present.")
}