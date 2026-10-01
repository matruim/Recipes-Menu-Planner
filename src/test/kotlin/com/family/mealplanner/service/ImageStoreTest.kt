package com.family.mealplanner.service

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageStoreTest {

    private val directory: Path = createTempDirectory("meal-planner-images")
    private val store = ImageStore(directory)

    private fun bytes(vararg header: Int, padding: Int = 32): ByteArray =
        ByteArray(header.size + padding) { index ->
            if (index < header.size) header[index].toByte() else 0
        }

    private val jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0)
    private val png = bytes(0x89, 0x50, 0x4E, 0x47)

    @Test
    fun `stores the formats a browser can show`() {
        assertTrue(store.save(jpeg).endsWith(".jpg"))
        assertTrue(store.save(png).endsWith(".png"))
        assertTrue(store.save(bytes(0x47, 0x49, 0x46, 0x38)).endsWith(".gif"))

        val webp = "RIFF____WEBPVP8 ".toByteArray(Charsets.ISO_8859_1) + ByteArray(16)
        assertTrue(store.save(webp).endsWith(".webp"))
    }

    @Test
    fun `refuses anything that is not an image`() {
        // The declared type is never trusted; the leading bytes decide.
        val message = assertFailsWith<UnsupportedImageException> {
            store.save("#!/bin/sh\nrm -rf /".toByteArray())
        }.message.orEmpty()
        assertTrue(message.contains("not a JPEG"), message)

        assertFailsWith<UnsupportedImageException> { store.save(ByteArray(0)) }
    }

    @Test
    fun `refuses images past the size limit`() {
        val small = ImageStore(directory, maxBytes = 64)
        assertFailsWith<UnsupportedImageException> { small.save(bytes(0xFF, 0xD8, 0xFF, padding = 200)) }
    }

    @Test
    fun `names are generated, never taken from the caller`() {
        val name = store.save(jpeg)
        assertTrue(Regex("""[0-9a-f-]{36}\.jpg""").matches(name), name)
        assertTrue(Files.exists(directory.resolve(name)))
    }

    @Test
    fun `resolves only names it generated`() {
        assertNotNull(store.resolve(store.save(png)))
        assertNull(store.resolve("does-not-exist.png"))
        assertNull(store.resolve("notauuid.png"))
    }

    @Test
    fun `refuses to be steered out of its directory`() {
        Files.writeString(directory.resolve("secret.txt"), "private")
        listOf(
            "../secret.txt",
            "../../etc/passwd",
            "..%2Fsecret.txt",
            "/etc/passwd",
            "subdir/../../escape.png",
        ).forEach { assertNull(store.resolve(it), "should have refused $it") }
    }

    @Test
    fun `reports the media type from the stored name`() {
        assertEquals("image/png", store.contentTypeOf("a.png"))
        assertEquals("image/webp", store.contentTypeOf("a.webp"))
        assertEquals("image/jpeg", store.contentTypeOf("a.jpg"))
    }
}
