package com.family.mealplanner.service

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class UnsupportedImageException(message: String) : Exception(message)

/**
 * Holds recipe pictures on disk.
 *
 * Names are generated rather than taken from the upload, and reads only accept
 * that generated shape, so nothing a browser sends can steer a path.
 */
class ImageStore(private val directory: Path, private val maxBytes: Int = 6 * 1024 * 1024) {

    init {
        Files.createDirectories(directory)
    }

    /** Sniffs the real format from the leading bytes rather than trusting the label. */
    fun save(bytes: ByteArray): String {
        if (bytes.isEmpty()) throw UnsupportedImageException("That file was empty.")
        if (bytes.size > maxBytes) {
            throw UnsupportedImageException("Images must be under ${maxBytes / (1024 * 1024)} MB.")
        }
        val extension = detectExtension(bytes)
            ?: throw UnsupportedImageException("That file is not a JPEG, PNG, GIF or WebP image.")

        val name = "${UUID.randomUUID()}.$extension"
        Files.write(directory.resolve(name), bytes)
        return name
    }

    /** Null for anything that is not a name this store generated. */
    fun resolve(name: String): Path? {
        if (!STORED_NAME.matches(name)) return null
        val path = directory.resolve(name).normalize()
        if (!path.startsWith(directory.normalize())) return null
        return path.takeIf { Files.isRegularFile(it) }
    }

    fun contentTypeOf(name: String): String = when (name.substringAfterLast('.', "")) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    private fun detectExtension(bytes: ByteArray): String? = when {
        bytes.startsWith(0xFF, 0xD8, 0xFF) -> "jpg"
        bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> "png"
        bytes.startsWith(0x47, 0x49, 0x46, 0x38) -> "gif"
        // RIFF....WEBP
        bytes.size > 12 && bytes.startsWith(0x52, 0x49, 0x46, 0x46) &&
            bytes.copyOfRange(8, 12).toString(Charsets.ISO_8859_1) == "WEBP" -> "webp"
        else -> null
    }

    private fun ByteArray.startsWith(vararg signature: Int): Boolean {
        if (size < signature.size) return false
        return signature.withIndex().all { (index, value) -> this[index] == value.toByte() }
    }

    private companion object {
        val STORED_NAME = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|gif|webp)""")
    }
}
