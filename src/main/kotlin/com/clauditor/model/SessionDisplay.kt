package com.clauditor.model

import java.time.Instant

data class SessionDisplay(
    val sessionId: String,
    val name: String?,
    val firstPrompt: String,
    val messageCount: Int,
    val modified: Instant,
    val gitBranch: String?,
    val projectPath: String,
    val worktreeName: String? = null
) {
    val displayName: String
        get() = name
            ?: firstPrompt.take(60).let { if (firstPrompt.length > 60) "$it..." else it }

    val tabTitle: String
        get() = shorten(name ?: worktreeName ?: firstPrompt)
}

private const val TAB_MAX_WORDS = 4
private const val TAB_MAX_CHARS = 26

/** Tab-sized title: at most a few whole words, never cut mid-word unless one word is too long. */
private fun shorten(title: String): String {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val out = StringBuilder()
    for (w in words.take(TAB_MAX_WORDS)) {
        if (out.isNotEmpty() && out.length + 1 + w.length > TAB_MAX_CHARS) break
        if (out.isNotEmpty()) out.append(' ')
        out.append(w)
    }
    if (out.isEmpty()) return title.take(TAB_MAX_CHARS)
    if (out.length > TAB_MAX_CHARS) return out.take(TAB_MAX_CHARS - 1).toString() + "…"
    return out.toString().trimEnd(',', ':', ';', '-', '.')
}
