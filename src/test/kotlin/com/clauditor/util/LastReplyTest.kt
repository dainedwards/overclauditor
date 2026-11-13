package com.clauditor.util

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LastReplyTest {
    @Test
    fun `last fenced block wins`() {
        val r = "Run this:\n```bash\nnpm test\n```\nthen this:\n```\nnpm run build\n```\nDone."
        assertEquals("npm run build", LastReply.pick(r))
    }

    @Test
    fun `quote-mark rule lines are stripped from a block's edges`() {
        val r = "Prompt:\n```\n''''''''''''''''''''''\nWrite a haiku.\n''''''''''''''''''''''\n```"
        assertEquals("Write a haiku.", LastReply.pick(r))
    }

    @Test
    fun `no block copies the whole reply`() {
        assertEquals("Just text.", LastReply.pick("  Just text.  "))
    }

    @Test
    fun `reply is everything after the last prompt, tool results and local commands don't split it`() {
        fun user(c: String) = """{"type":"user","message":{"role":"user","content":$c}}"""
        fun asst(t: String) = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":${com.google.gson.Gson().toJson(t)}}]}}"""
        val lines = listOf(
            user("\"first question\""), asst("old answer"),
            user("\"second question\""), asst("part one"),
            user("""[{"type":"tool_result","tool_use_id":"x","content":"ok"}]"""),
            asst("```\ncopy me\n```"),
            user("\"<local-command-stdout>Set model</local-command-stdout>\"")
        )
        val f = Files.createTempFile("t", ".jsonl").also { Files.writeString(it, lines.joinToString("\n") + "\n") }
        assertEquals("part one\n\n```\ncopy me\n```", LastReply.latestReplyText(f))
        assertEquals("copy me", LastReply.copyText(f))
    }

    @Test
    fun `empty transcript gives nothing`() {
        assertNull(LastReply.copyText(Files.createTempFile("e", ".jsonl")))
    }
}
