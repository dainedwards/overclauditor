package com.clauditor.services

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverclauditorConfigTest {
    private fun parse(json: String) = OverclauditorConfig.parse(JsonParser.parseString(json).asJsonObject)

    @Test
    fun `slash commands get Enter and key chords become control bytes`() {
        val c = parse("""{"buttons":[{"label":"plan","send":"/plan"},{"label":"go","keys":"ctrl+x ctrl+r"},{"label":"raw","send":"hi"}]}""")
        assertEquals(listOf("/plan\r", "\u0018\u0012", "hi"), c.buttons.map { it.send })
    }

    @Test
    fun `icon buttons resolve AllIcons names`() {
        val c = parse("""{"buttons":[{"icon":"Actions.Copy","keys":"ctrl+x ctrl+y"},{"icon":"Actions.Execute","send":"/run"}]}""")
        assertEquals(2, c.buttons.count { it.icon != null })
    }

    @Test
    fun `a badge wraps the icon`() {
        val c = parse("""{"buttons":[{"icon":"Actions.Execute","badge":"*","keys":"ctrl+x ctrl+r"}]}""")
        assertTrue(c.buttons.single().icon is BadgeIcon)
    }

    @Test
    fun `new sessions get the configured model and effort, invalid values are ignored`() {
        assertEquals(listOf("--model", "opus", "--effort", "medium"), parse("""{"defaultModel":"opus","defaultEffort":"Medium"}""").newSessionArgs())
        assertEquals(listOf("--model", "opus[1m]"), parse("""{"defaultModel":"opus[1m]","defaultEffort":"turbo"}""").newSessionArgs())
        assertEquals(emptyList(), parse("""{"defaultModel":"opus; rm -rf /"}""").newSessionArgs())
        assertEquals(emptyList(), parse("{}").newSessionArgs())
    }

    @Test
    fun `special keys`() {
        assertEquals("\r\u001b\t", OverclauditorConfig.keysToBytes("enter esc tab"))
    }

    @Test
    fun `buttons without a label or a known icon, or without anything to send, are dropped`() {
        val c = parse("""{"buttons":[{"send":"/x"},{"icon":"Nope.Missing","send":"/x"},{"label":"empty"}]}""")
        assertEquals(0, c.buttons.size)
    }

    @Test
    fun `summary source and tilde expansion`() {
        val c = parse("""{"summary":{"source":"file","file":"~/s/{sessionId}.summary"}}""")
        assertEquals(OverclauditorConfig.SummarySource.FILE, c.summarySource)
        assertEquals(System.getProperty("user.home") + "/s/{sessionId}.summary", c.summaryFile)
    }

    @Test
    fun `defaults for an empty config`() {
        val c = parse("{}")
        assertEquals(listOf("/plan\r", "/overclauditor:merge\r", "/overclauditor:push\r", "/overclauditor:pull\r"), c.buttons.map { it.send })
        assertTrue(c.bundledCommands)
        assertEquals(OverclauditorConfig.SummarySource.BUILTIN, c.summarySource)
        assertNull(c.summaryFile)
        assertFalse(c.hideTerminalStatusLine)
    }

    @Test
    fun `unknown summary source falls back to builtin`() {
        assertEquals(OverclauditorConfig.SummarySource.BUILTIN, parse("""{"summary":{"source":"weird"}}""").summarySource)
        assertEquals(OverclauditorConfig.SummarySource.OFF, parse("""{"summary":{"source":"OFF"}}""").summarySource)
    }
}
