package com.clauditor.util

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonlTailReaderTest {
    private fun file(text: String) = Files.createTempFile("tail", ".jsonl").also { Files.writeString(it, text) }

    @Test
    fun `an unfinished last line waits for its newline`() {
        val f = file("{\"a\":1}\n{\"b\":")
        val r = JsonlTailReader()
        val seen = mutableListOf<String>()
        r.consume(f) { seen += it }
        assertEquals(listOf("{\"a\":1}"), seen)
        Files.writeString(f, "{\"a\":1}\n{\"b\":2}\n")
        r.consume(f) { seen += it }
        assertEquals(listOf("{\"a\":1}", "{\"b\":2}"), seen)
    }

    @Test
    fun `a restored offset resumes without rereading`() {
        val f = file("one\ntwo\n")
        val first = JsonlTailReader().also { it.consume(f) {} }
        Files.writeString(f, "one\ntwo\nthree\n")
        val resumed = JsonlTailReader().also { it.restore(first.offset) }
        val seen = mutableListOf<String>()
        resumed.consume(f) { seen += it }
        assertEquals(listOf("three"), seen)
    }

    @Test
    fun `a shrunk file restarts from the top and says so`() {
        val f = file("one\ntwo\nthree\n")
        val r = JsonlTailReader().also { it.consume(f) {} }
        Files.writeString(f, "new\n")
        var restarted = false
        val seen = mutableListOf<String>()
        r.consume(f, onRestart = { restarted = true }) { seen += it }
        assertTrue(restarted)
        assertEquals(listOf("new"), seen)
    }

    @Test
    fun `multibyte text split across chunks decodes intact`() {
        val text = "é".repeat(700_000)  // > 1 MiB of UTF-8, crosses the read chunk boundary
        val f = file("$text\n")
        val seen = mutableListOf<String>()
        JsonlTailReader().consume(f) { seen += it }
        assertEquals(listOf(text), seen)
    }
}
