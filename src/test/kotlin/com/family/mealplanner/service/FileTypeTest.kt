package com.family.mealplanner.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileTypeTest {

    private fun bytes(vararg header: Int, padding: Int = 24) =
        ByteArray(header.size + padding) { i -> if (i < header.size) header[i].toByte() else 0 }

    @Test
    fun `identifies uploads by their leading bytes`() {
        assertEquals(FileType.JPEG, FileType.of(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals(FileType.PNG, FileType.of(bytes(0x89, 0x50, 0x4E, 0x47)))
        assertEquals(FileType.GIF, FileType.of(bytes(0x47, 0x49, 0x46, 0x38)))
        assertEquals(FileType.PDF, FileType.of(bytes(0x25, 0x50, 0x44, 0x46)))
        assertEquals(
            FileType.WEBP,
            FileType.of("RIFF____WEBPVP8 ".toByteArray(Charsets.ISO_8859_1) + ByteArray(16)),
        )
    }

    @Test
    fun `does not trust a name or a declared type`() {
        // A script called recipe.jpg is still a script.
        assertNull(FileType.of("#!/bin/sh\nrm -rf /".toByteArray()))
        assertNull(FileType.of(ByteArray(0)))
        assertNull(FileType.of(bytes(0x00, 0x01, 0x02, 0x03)))
    }

    @Test
    fun `only images may be stored as recipe pictures`() {
        assertEquals(false, FileType.PDF.isImage)
        assertEquals(true, FileType.JPEG.isImage)
    }

    @Test
    fun `serves a stored file under the right media type`() {
        assertEquals("image/png", FileType.mediaTypeFor("abc.png"))
        assertEquals("image/webp", FileType.mediaTypeFor("abc.webp"))
        assertEquals("application/pdf", FileType.mediaTypeFor("abc.pdf"))
        assertEquals("image/jpeg", FileType.mediaTypeFor("abc.unknown"))
    }
}
