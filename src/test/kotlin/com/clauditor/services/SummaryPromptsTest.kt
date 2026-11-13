package com.clauditor.services

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SummaryPromptsTest {
    @Test
    fun `recent prompts skip meta, wrappers and tool results, oldest first, last six`() {
        val lines = buildList {
            add("""{"message":{"role":"user","content":"<command-name>/clear</command-name>"},"type":"user"}""")
            add("""{"message":{"role":"user","content":"injected"},"isMeta":true,"type":"user"}""")
            add("""{"message":{"role":"user","content":[{"type":"tool_result","content":"x"}]},"type":"user"}""")
            for (i in 1..8) add("""{"message":{"role":"user","content":"prompt   $i"},"type":"user"}""")
            add("""{"message":{"role":"assistant","content":[{"type":"text","text":"reply"}]},"type":"assistant"}""")
        }
        val f = Files.createTempFile("t", ".jsonl").also { Files.writeString(it, lines.joinToString("\n") + "\n") }
        assertEquals((3..8).map { "prompt $it" }, SummaryService.recentPrompts(f))
    }
}
