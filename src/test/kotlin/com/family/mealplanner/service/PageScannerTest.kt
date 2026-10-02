package com.family.mealplanner.service

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PageScannerTest {

    private val scanner = PageScanner(createTempDirectory("meal-planner-scan"))

    @Test
    fun `refuses a file that is neither a photo nor a PDF`() {
        if (!scanner.isAvailable) return
        val message = assertFailsWith<ScanUnavailableException> {
            scanner.readText("Dear Jane, how are you?".toByteArray())
        }.message.orEmpty()
        assertTrue(message.contains("not a photo or a PDF"), message)
    }

    @Test
    fun `says so plainly where the machine cannot do it`() {
        // Scanning leans on the operating system, so it is simply absent elsewhere
        // rather than producing worse results quietly.
        if (scanner.isAvailable) return
        val message = assertFailsWith<ScanUnavailableException> {
            scanner.readText(ByteArray(32) { 0xFF.toByte() })
        }.message.orEmpty()
        assertTrue(message.contains("macOS"), message)
        assertTrue(message.contains("paste") || message.contains("type"), message)
    }
}
