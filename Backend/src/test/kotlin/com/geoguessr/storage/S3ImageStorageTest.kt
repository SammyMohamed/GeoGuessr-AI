package com.geoguessr.storage

import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

/** Builds the real S3 client used outside tests. Credentials are never read
 * directly here — the AWS SDK's default credential chain picks up
 * AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY from environment variables. */
fun createS3Client(region: String): S3Client =
    S3Client.builder().region(Region.of(region)).build()

/**
 * S3-backed image storage. Takes an S3Client rather than building one
 * internally — same reasoning as KtorInferenceClient taking an HttpClient —
 * so tests can inject a mock instead of hitting real AWS.
 *
 * "storagePath" for S3-backed images is just the S3 object key, not a
 * filesystem path — the Images table column doesn't need to change since
 * it was always just "the identifier read() needs," regardless of backend.
 */
class S3ImageStorage(
    private val bucketName: String,
    private val client: S3Client,
) : ImageStorage {

    override fun save(bytes: ByteArray, filename: String): String {
        val key = "${UUID.randomUUID()}_${filename.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
        client.putObject(
            PutObjectRequest.builder().bucket(bucketName).key(key).build(),
            RequestBody.fromBytes(bytes),
        )
        return key
    }

    override fun read(path: String): ByteArray {
        val request = GetObjectRequest.builder().bucket(bucketName).key(path).build()
        return client.getObjectAsBytes(request).asByteArray()
    }
}