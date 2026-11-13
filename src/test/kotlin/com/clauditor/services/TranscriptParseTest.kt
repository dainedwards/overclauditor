package com.clauditor.services

import com.clauditor.services.ClaudeSessionService.Companion.applyLine
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranscriptParseTest {
    private fun parse(vararg lines: String) = ClaudeSessionService.SessionAccumulator().also { acc -> lines.forEach { applyLine(acc, it) } }

    private fun user(text: String, ts: String = "2026-01-01T00:00:00Z", meta: Boolean = false) =
        """{"parentUuid":null,"message":{"role":"user","content":${com.google.gson.Gson().toJson(text)}},"isMeta":$meta,"type":"user","uuid":"u","timestamp":"$ts","gitBranch":"main"}"""

    @Test
    fun `first real prompt skips command wrappers and meta lines`() {
        val acc = parse(
            user("<local-command-caveat>Caveat: the messages below</local-command-caveat>"),
            user("injected context", meta = true),
            user("  build the loading screen  "),
            user("second prompt")
        )
        assertEquals("build the loading screen", acc.firstPrompt)
        assertEquals("main", acc.gitBranch)
        assertEquals(4, acc.messageCount)
    }

    @Test
    fun `custom title before the first prompt is the launch name, after it a rename`() {
        val acc = parse(
            """{"type":"custom-title","customTitle":"launch-name","sessionId":"s"}""",
            user("hello"),
            """{"type":"custom-title","customTitle":"my rename","sessionId":"s"}"""
        )
        assertEquals("launch-name", acc.customTitle)
        assertEquals("my rename", acc.renamedTitle)
    }

    @Test
    fun `the newest ai title wins and blank ones are ignored`() {
        val acc = parse(
            """{"type":"ai-title","aiTitle":"first","sessionId":"s"}""",
            """{"type":"ai-title","aiTitle":"second","sessionId":"s"}""",
            """{"type":"ai-title","aiTitle":"  ","sessionId":"s"}"""
        )
        assertEquals("second", acc.aiTitle)
    }

    @Test
    fun `type and timestamp after a large message are still read`() {
        val big = "x".repeat(2_000_000)
        val line = """{"parentUuid":"p","message":{"role":"assistant","content":[{"type":"text","text":"$big"}]},"type":"assistant","uuid":"a","timestamp":"2026-03-04T05:06:07Z"}"""
        val acc = parse(line)
        assertEquals(1, acc.messageCount)
        assertEquals(Instant.parse("2026-03-04T05:06:07Z"), acc.lastTimestamp)
    }

    @Test
    fun `the latest timestamp wins, numeric or string`() {
        val acc = parse(
            user("a", ts = "2026-05-01T00:00:00Z"),
            """{"type":"system","timestamp":${Instant.parse("2026-06-01T00:00:00Z").toEpochMilli()}}""",
            user("b", ts = "2026-04-01T00:00:00Z")
        )
        assertEquals(Instant.parse("2026-06-01T00:00:00Z"), acc.lastTimestamp)
    }

    @Test
    fun `malformed and non-object lines are skipped`() {
        val acc = parse("not json", "[1,2,3]", "{\"type\":", "")
        assertEquals(0, acc.messageCount)
        assertNull(acc.firstPrompt)
    }

    @Test
    fun `array content takes the first text block`() {
        val line = """{"message":{"role":"user","content":[{"type":"image","source":{}},{"type":"text","text":"look at this"}]},"type":"user","timestamp":"2026-01-01T00:00:00Z"}"""
        assertEquals("look at this", parse(line).firstPrompt)
    }
}
