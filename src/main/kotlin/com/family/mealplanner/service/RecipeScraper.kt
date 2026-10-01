package com.family.mealplanner.service

import org.slf4j.LoggerFactory

/** Fetches a recipe page and reads whatever structured data it publishes. */
class RecipeScraper(
    private val fetcher: UrlFetcher,
    private val images: ImageStore,
) {
    private val log = LoggerFactory.getLogger(RecipeScraper::class.java)

    fun importFrom(url: String): ImportResult {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return ImportResult.Failed("Paste a link to the recipe first.")
        }
        val html = try {
            fetcher.fetch(trimmed)
        } catch (e: UnreachableSourceException) {
            return ImportResult.Failed(e.message ?: "Could not fetch that page.")
        }
        return JsonLdRecipeParser.parse(html, trimmed)
    }

    /**
     * Gets a recipe's picture, preferring a local copy.
     *
     * Where the CDN refuses this server - Cloudflare-fronted sites answer 403 to
     * anything that is not a browser - the address is kept and linked instead.
     * The browser showing the planner is not blocked, so the photo still appears;
     * it just depends on that site staying up.
     *
     * Never fails the import: a recipe without its photo is still worth having.
     */
    fun storeImage(imageUrl: String?): StoredImage {
        if (imageUrl.isNullOrBlank()) return StoredImage()
        return try {
            StoredImage(file = images.save(fetcher.fetchBytes(imageUrl)))
        } catch (e: UnreachableSourceException) {
            log.info("Linking recipe image {} instead of copying it: {}", imageUrl, e.message)
            StoredImage(remoteUrl = imageUrl)
        } catch (e: UnsupportedImageException) {
            log.info("Ignoring recipe image {}: {}", imageUrl, e.message)
            StoredImage()
        }
    }
}

/** Where a recipe's picture ended up: copied here, or left where it was. */
data class StoredImage(val file: String? = null, val remoteUrl: String? = null)
