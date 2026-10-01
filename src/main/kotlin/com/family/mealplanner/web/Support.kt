package com.family.mealplanner.web

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import java.util.UUID

fun ApplicationCall.uuidParam(name: String): UUID? =
    parameters[name]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

fun Parameters.intOrNull(name: String): Int? = this[name]?.trim()?.toIntOrNull()

suspend fun ApplicationCall.respondFragment(html: String) =
    respondText(html, ContentType.Text.Html.withCharset(Charsets.UTF_8))
