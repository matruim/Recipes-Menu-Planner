package com.family.mealplanner.service

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The app has no login, so anyone on the network can ask the server to fetch a
 * URL. These cases all fail validation before any request leaves the machine.
 */
class UrlFetcherTest {

    private val fetcher = UrlFetcher(allowPrivateHosts = false)

    private fun refuses(url: String): String =
        assertFailsWith<UnreachableSourceException>("expected $url to be refused") {
            fetcher.fetch(url)
        }.message.orEmpty()

    @Test
    fun `refuses loopback addresses`() {
        refuses("http://localhost:8080/admin")
        refuses("http://127.0.0.1/")
        refuses("http://[::1]/")
    }

    @Test
    fun `refuses addresses on the local network`() {
        refuses("http://192.168.1.1/")
        refuses("http://10.0.0.5/status")
        refuses("http://172.16.0.1/")
    }

    @Test
    fun `refuses the cloud metadata address`() {
        // 169.254.169.254 is the classic SSRF target on a hosted box.
        refuses("http://169.254.169.254/latest/meta-data/")
    }

    @Test
    fun `refuses schemes other than http and https`() {
        assertTrue(refuses("file:///etc/passwd").contains("http"))
        assertTrue(refuses("ftp://example.com/x").contains("http"))
        refuses("not a url at all")
    }

    @Test
    fun `allows private addresses when explicitly configured`() {
        // Opting in is a config choice, for someone self-hosting recipes on a LAN.
        val permissive = UrlFetcher(allowPrivateHosts = true)
        val message = assertFailsWith<UnreachableSourceException> {
            permissive.fetch("http://127.0.0.1:1/")
        }.message.orEmpty()
        // It got as far as trying to connect rather than refusing the address.
        assertTrue(message.contains("Could not reach"), "unexpected message: $message")
    }
}
