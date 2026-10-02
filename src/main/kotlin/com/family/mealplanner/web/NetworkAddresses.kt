package com.family.mealplanner.web

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Addresses other devices on the network could use to reach this server.
 *
 * The bookmarklet bakes in whichever address the planner was open at when it was
 * dragged to the bookmarks bar. Made while sitting at the server on "localhost",
 * it sends a phone to the phone's own localhost, so the form offers these
 * instead.
 */
object NetworkAddresses {

    private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "[::1]")

    fun isLoopback(origin: String): Boolean {
        val host = runCatching { java.net.URI(origin).host }.getOrNull() ?: return false
        return host.lowercase() in LOOPBACK_HOSTS
    }

    /**
     * Addresses to offer, most durable first.
     *
     * The host name comes first deliberately: a DHCP lease will eventually hand
     * this machine a different IP, and a bookmarklet built on the old one breaks
     * silently, whereas an mDNS name keeps working.
     */
    fun reachableOrigins(port: Int): List<String> =
        (listOfNotNull(hostNameOrigin(port)) + siteLocalOrigins(port)).distinct()

    /** Only a dotted name, since a bare one will not resolve from another device. */
    private fun hostNameOrigin(port: Int): String? =
        runCatching {
            InetAddress.getLocalHost().hostName
                ?.takeIf { it.contains('.') && !it.first().isDigit() }
                ?.let { "http://$it:$port" }
        }.getOrNull()

    /** Site-local IPv4 addresses on interfaces that are actually up. */
    private fun siteLocalOrigins(port: Int): List<String> =
        runCatching {
            NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .mapNotNull { it.hostAddress }
                .distinct()
                .sorted()
                .map { "http://$it:$port" }
                .toList()
        }.getOrDefault(emptyList())
}
