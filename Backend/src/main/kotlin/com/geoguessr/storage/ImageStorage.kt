package com.geoguessr.storage

/** Abstraction over "where uploaded image bytes actually live". Local disk
 * for now (see LocalFileImageStorage); swapping to S3-style object storage
 * later means writing a new implementation of this interface, not touching
 * any route or repository code. */
interface ImageStorage {
    /** Persists the bytes and returns a path/key that can later be passed to read(). */
    fun save(bytes: ByteArray, filename: String): String

    fun read(path: String): ByteArray
}
