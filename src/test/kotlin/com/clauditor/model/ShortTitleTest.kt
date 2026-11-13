package com.clauditor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShortTitleTest {
    @Test
    fun `keeps a few whole words`() {
        assertEquals("Offloading work to Windows", shortTitle("Offloading work to Windows desktop remotely"))
    }

    @Test
    fun `never ends on a connective`() {
        assertEquals("Recovering sessions", shortTitle("Recovering sessions after restart"))
    }

    @Test
    fun `short titles pass through`() {
        assertEquals("Cache bar", shortTitle("Cache bar"))
    }

    @Test
    fun `one very long word is cut with an ellipsis`() {
        val t = shortTitle("a".repeat(60))
        assertTrue(t.length <= 26 && t.endsWith("…"), t)
    }

    @Test
    fun `blank input stays blank`() {
        assertEquals("", shortTitle("   "))
    }
}
