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
    // Don't end on a dangling connective ("Recovering sessions after")
    val kept = out.toString().split(' ').toMutableList()
    while (kept.size > 1 && kept.last().trimEnd(',', ':', ';', '-', '.').lowercase() in DANGLING) kept.removeAt(kept.lastIndex)
    return kept.joinToString(" ").trimEnd(',', ':', ';', '-', '.')
}

private val DANGLING = setOf("a", "an", "the", "to", "of", "for", "and", "or", "with", "in", "on", "at", "by", "from", "after", "before", "into", "via", "vs")
