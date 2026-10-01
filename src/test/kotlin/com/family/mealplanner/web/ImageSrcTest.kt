package com.family.mealplanner.web

import com.family.mealplanner.web.views.imageSrc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageSrcTest {

    @Test
    fun `prefers a local copy`() {
        assertEquals(
            "/images/abc.jpg",
            imageSrc("abc.jpg", "https://cdn.test/remote.jpg"),
        )
    }

    @Test
    fun `links the source site when there is no local copy`() {
        // Cloudflare-fronted CDNs refuse this server but serve the browser fine.
        assertEquals("https://cdn.test/remote.jpg", imageSrc(null, "https://cdn.test/remote.jpg"))
    }

    @Test
    fun `has no source when neither is present`() {
        assertNull(imageSrc(null, null))
    }

    @Test
    fun `refuses anything that is not an http address`() {
        assertNull(imageSrc(null, "javascript:alert(1)"))
        assertNull(imageSrc(null, "data:text/html,<script>alert(1)</script>"))
        assertNull(imageSrc(null, "/etc/passwd"))
    }
}
