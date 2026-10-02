package com.family.mealplanner.service

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class ScanUnavailableException(message: String) : Exception(message)

/**
 * Reads the text off an uploaded photo or PDF.
 *
 * Uses the operating system's own text recognition - the engine behind Live Text
 * - rather than a bundled OCR library. On a photographed page that difference is
 * stark: the system engine shrugs off rotation, soft focus and uneven lighting,
 * where tesseract loses whole words and merges columns.
 *
 * The cost is that it is macOS only. The feature reports itself unavailable
 * elsewhere rather than silently producing worse results, and every other way of
 * adding a recipe keeps working.
 *
 * The helper is shipped as source and compiled on first use, so there is no
 * platform-specific binary in the repository and none in the jar.
 */
class PageScanner(private val cacheDirectory: Path) {

    private val log = LoggerFactory.getLogger(PageScanner::class.java)
    private val helper: Path get() = cacheDirectory.resolve("RecipeOcr")

    val isAvailable: Boolean
        get() = System.getProperty("os.name").orEmpty().contains("Mac", ignoreCase = true) &&
            which("swiftc") != null

    /** The text on the page, top to bottom, a line at a time. */
    fun readText(bytes: ByteArray): String {
        if (!isAvailable) {
            throw ScanUnavailableException(
                "Scanning a photo needs macOS. Everything else still works: paste the " +
                    "recipe's text in, or type it.",
            )
        }
        val type = FileType.of(bytes)
            ?: throw ScanUnavailableException("That file is not a photo or a PDF.")

        Files.createDirectories(cacheDirectory)
        val page = Files.createTempFile(cacheDirectory, "scan-", ".${type.extension}")
        return try {
            Files.write(page, bytes)
            run(listOf(ensureHelper().toString(), page.toString()), SCAN_TIMEOUT)
        } finally {
            Files.deleteIfExists(page)
        }
    }

    /** Compiled once and kept; recompiled if the cached copy goes missing. */
    private fun ensureHelper(): Path {
        if (Files.isExecutable(helper)) return helper

        val source = cacheDirectory.resolve("RecipeOcr.swift")
        javaClass.getResourceAsStream("/ocr/RecipeOcr.swift")
            ?.use { Files.copy(it, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            ?: throw ScanUnavailableException("The text recognition helper is missing from this build.")

        log.info("Compiling the text recognition helper into {}", helper)
        run(listOf("swiftc", "-O", "-o", helper.toString(), source.toString()), COMPILE_TIMEOUT)
        return helper
    }

    private fun run(command: List<String>, timeout: Long): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val errors = process.errorStream.bufferedReader().use { it.readText() }

        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw ScanUnavailableException("Reading that page took too long and was stopped.")
        }
        if (process.exitValue() != 0) {
            log.warn("{} failed: {}", command.first(), errors.trim())
            throw ScanUnavailableException(
                "That page could not be read: ${errors.trim().lines().firstOrNull().orEmpty()}",
            )
        }
        return output
    }

    private fun which(command: String): Path? =
        System.getenv("PATH").orEmpty().split(":")
            .map { Path.of(it, command) }
            .firstOrNull { Files.isExecutable(it) }

    private companion object {
        const val COMPILE_TIMEOUT = 120L
        const val SCAN_TIMEOUT = 90L
    }
}
