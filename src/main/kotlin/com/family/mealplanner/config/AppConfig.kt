package com.family.mealplanner.config

import io.ktor.server.config.ApplicationConfig

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int,
)

/**
 * The address this planner is reachable at, when it is not simply the one the
 * browser used — behind a reverse proxy, or on a real host name. Set it and the
 * bookmarklet points there regardless of how any one person reached the app.
 */
fun ApplicationConfig.publicBaseUrl(): String? =
    propertyOrNull("app.baseUrl")?.getString()?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }

data class ScraperConfig(
    /** Off by default: the app has no login, so it will not fetch LAN addresses. */
    val allowPrivateHosts: Boolean,
)

fun ApplicationConfig.scraperConfig(): ScraperConfig = ScraperConfig(
    allowPrivateHosts = propertyOrNull("scraper.allowPrivateHosts")?.getString()?.toBoolean() ?: false,
)

data class ImageConfig(val directory: String)

fun ApplicationConfig.imageConfig(): ImageConfig = ImageConfig(
    directory = propertyOrNull("images.directory")?.getString() ?: "data/images",
)

fun ApplicationConfig.databaseConfig(): DatabaseConfig = DatabaseConfig(
    url = property("database.url").getString(),
    user = property("database.user").getString(),
    password = property("database.password").getString(),
    maxPoolSize = propertyOrNull("database.maxPoolSize")?.getString()?.toIntOrNull() ?: 10,
)
