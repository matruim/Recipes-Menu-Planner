package com.family.mealplanner.service

import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class UnreachableSourceException(message: String) : Exception(message)

/**
 * Fetches a user-supplied URL for recipe import.
 *
 * The app has no login, so anyone who can reach it can ask the server to make a
 * request on their behalf. That makes this the one place worth being careful:
 * every hop is re-checked, and addresses inside the network the server itself
 * sits on are refused unless explicitly allowed.
 */
class UrlFetcher(
    private val allowPrivateHosts: Boolean = false,
    private val maxBytes: Int = 2 * 1024 * 1024,
    private val timeout: Duration = Duration.ofSeconds(10),
) {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(timeout)
        .build()

    fun fetch(rawUrl: String): String {
        var target = validated(rawUrl)

        repeat(MAX_REDIRECTS) {
            val response = send(target)
            val status = response.statusCode()

            if (status in 300..399) {
                val location = response.headers().firstValue("location").orElse(null)
                    ?: throw UnreachableSourceException("The page redirected without saying where.")
                // Re-validate: a public URL is free to redirect somewhere private.
                target = validated(target.resolve(location).toString())
                return@repeat
            }

            // Cloudflare and friends answer 403/429 to anything that is not a real
            // browser, whatever headers it sends. Point at the paste fallback.
            if (status == 403 || status == 429) {
                throw UnreachableSourceException(
                    "That site blocks automated requests (HTTP $status). " +
                        "Open the page in your browser and use \"paste the page source\" below.",
                )
            }
            if (status !in 200..299) {
                throw UnreachableSourceException("That page returned HTTP $status.")
            }
            return response.body().use { it.readNBytes(maxBytes).toString(Charsets.UTF_8) }
        }

        throw UnreachableSourceException("That page redirected too many times.")
    }

    /** Same address rules as [fetch]; used for pulling a recipe's picture across. */
    fun fetchBytes(rawUrl: String, limit: Int = maxBytes): ByteArray {
        var target = validated(rawUrl)
        repeat(MAX_REDIRECTS) {
            val response = send(target)
            val status = response.statusCode()
            if (status in 300..399) {
                val location = response.headers().firstValue("location").orElse(null)
                    ?: throw UnreachableSourceException("That image redirected without saying where.")
                target = validated(target.resolve(location).toString())
                return@repeat
            }
            if (status !in 200..299) throw UnreachableSourceException("That image returned HTTP $status.")
            return response.body().use { it.readNBytes(limit) }
        }
        throw UnreachableSourceException("That image redirected too many times.")
    }

    private fun send(target: URI): HttpResponse<java.io.InputStream> {
        val request = HttpRequest.newBuilder(target)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .timeout(timeout)
            .GET()
            .build()
        return try {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: IOException) {
            throw UnreachableSourceException("Could not reach that page: ${e.message}")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw UnreachableSourceException("Fetching that page was interrupted.")
        }
    }

    private fun validated(rawUrl: String): URI {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull()
            ?: throw UnreachableSourceException("That does not look like a web address.")

        if (uri.scheme?.lowercase() !in setOf("http", "https")) {
            throw UnreachableSourceException("Only http and https addresses can be imported.")
        }
        val host = uri.host ?: throw UnreachableSourceException("That address has no host name.")
        if (!allowPrivateHosts) {
            val addresses = try {
                InetAddress.getAllByName(host)
            } catch (e: UnknownHostException) {
                throw UnreachableSourceException("Could not find a server called \"$host\".")
            }
            if (addresses.any { it.isLoopbackAddress || it.isSiteLocalAddress ||
                    it.isLinkLocalAddress || it.isAnyLocalAddress || it.isMulticastAddress }
            ) {
                throw UnreachableSourceException("That address is inside this network, so it will not be fetched.")
            }
        }
        return uri
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val USER_AGENT =
            "Mozilla/5.0 (compatible; MealPlanner/0.1; personal recipe importer)"
    }
}
