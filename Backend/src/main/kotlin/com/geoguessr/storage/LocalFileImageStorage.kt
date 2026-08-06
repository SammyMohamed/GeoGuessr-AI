package com.geoguessr.storage

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class LocalFileImageStorage(private val baseDir: Path) : ImageStorage {
    init {
        Files.createDirectories(baseDir)
    }

    override fun save(bytes: ByteArray, filename: String): String {
        val safeName = "${UUID.randomUUID()}_${filename.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
        val target = baseDir.resolve(safeName)
        Files.write(target, bytes)
        return target.toString()
    }

    override fun read(path: String): ByteArray = Files.readAllBytes(Path.of(path))
}
