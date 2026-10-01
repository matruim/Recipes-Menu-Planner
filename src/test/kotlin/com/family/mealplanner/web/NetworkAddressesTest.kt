package com.family.mealplanner.web

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkAddressesTest {

    @Test
    fun `recognises the addresses that only work on this machine`() {
        listOf(
            "http://localhost:8080",
            "http://LOCALHOST:8080",
            "http://127.0.0.1:8080",
            "http://[::1]:8080",
        ).forEach { assertTrue(NetworkAddresses.isLoopback(it), "$it should be loopback") }
    }

    @Test
    fun `treats network addresses as reachable`() {
        listOf(
            "http://192.168.1.50:8080",
            "http://nas.local:8080",
            "https://meals.example.com",
        ).forEach { assertFalse(NetworkAddresses.isLoopback(it), "$it should not be loopback") }
    }

    @Test
    fun `survives something that is not an address`() {
        assertFalse(NetworkAddresses.isLoopback("not a url"))
        assertFalse(NetworkAddresses.isLoopback(""))
    }

    @Test
    fun `suggested origins are absolute and carry the port`() {
        // Whatever this machine has, every suggestion must be usable as-is.
        NetworkAddresses.reachableOrigins(8080).forEach {
            assertTrue(it.startsWith("http://"), it)
            assertTrue(it.endsWith(":8080"), it)
            assertFalse(NetworkAddresses.isLoopback(it), "should not suggest $it")
        }
    }
}
