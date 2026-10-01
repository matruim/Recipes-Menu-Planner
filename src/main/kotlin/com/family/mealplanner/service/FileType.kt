package com.family.mealplanner.service

/**
 * What an upload actually is, judged by its leading bytes.
 *
 * The browser's declared type and the file name are both supplied by whoever is
 * uploading, so neither decides anything here.
 */
enum class FileType(val extension: String, val mediaType: String, val isImage: Boolean) {
    JPEG("jpg", "image/jpeg", true),
    PNG("png", "image/png", true),
    GIF("gif", "image/gif", true),
    WEBP("webp", "image/webp", true),
    PDF("pdf", "application/pdf", false),
    ;

    companion object {
        fun of(bytes: ByteArray): FileType? = when {
            bytes.startsWith(0xFF, 0xD8, 0xFF) -> JPEG
            bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> PNG
            bytes.startsWith(0x47, 0x49, 0x46, 0x38) -> GIF
            bytes.startsWith(0x25, 0x50, 0x44, 0x46) -> PDF
            // RIFF....WEBP
            bytes.size > 12 && bytes.startsWith(0x52, 0x49, 0x46, 0x46) &&
                bytes.copyOfRange(8, 12).toString(Charsets.ISO_8859_1) == "WEBP" -> WEBP
            else -> null
        }

        fun mediaTypeFor(fileName: String): String =
            entries.firstOrNull { fileName.endsWith(".${it.extension}", ignoreCase = true) }
                ?.mediaType
                ?: JPEG.mediaType

        private fun ByteArray.startsWith(vararg signature: Int): Boolean {
            if (size < signature.size) return false
            return signature.withIndex().all { (index, value) -> this[index] == value.toByte() }
        }
    }
}
