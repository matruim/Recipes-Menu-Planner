package com.family.mealplanner.config

import io.ktor.server.config.ApplicationConfig

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int,
)

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
