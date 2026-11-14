package com.clauditor.services

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon

/**
 * Per-user setup that differs between machines, in ~/.overclauditor/config.json so a person
 * or an agent can write it without the settings UI. Reloaded when the file changes. Every
 * key is optional; a missing file means the defaults below. Reference: docs/SETUP.md.
 */
object OverclauditorConfig {
    /** A toolbar button. Text buttons sit left of fork; icon buttons sit after Messages. */
    /**
     * [send] is typed into the session; [builtin] instead runs a plugin action:
     * "copyLastCodeBlock" copies the last code block of Claude's latest reply (else the reply).
     */
    data class Button(val label: String?, val icon: Icon?, val tooltip: String, val send: String, val builtin: String? = null)

    const val COPY_LAST_CODE_BLOCK = "copyLastCodeBlock"

    enum class SummarySource { BUILTIN, FILE, OFF }

    data class Config(
        val buttons: List<Button>,
        val summarySource: SummarySource,
        /** For FILE: path with {sessionId}, line 1 the gist, line 2 what to test. */
        val summaryFile: String?,
        /** Blank the user's own status line in plugin sessions (the toolbar shows the same information). */
        val hideTerminalStatusLine: Boolean,
        /** Load the bundled /overclauditor:merge, :push, :pull into plugin sessions (--plugin-dir). */
        val bundledCommands: Boolean,
        /**
         * Model and effort every NEW session starts with (claude --model / --effort, session-only).
         * The dropdowns change only their own session; these decide where new ones start.
         * Null: leave it to Claude Code's own default. Resumes and forks keep their model and get the
         * effort they last ran on (SessionSettings).
         */
        val defaultModel: String? = null,
        val defaultEffort: String? = null
    ) {
        /** Flags for a brand-new session. */
        fun newSessionArgs(): List<String> =
            (defaultModel?.let { listOf("--model", it) } ?: emptyList()) +
                (defaultEffort?.let { listOf("--effort", it) } ?: emptyList())
    }

    val file: Path = Path.of(System.getProperty("user.home"), ".overclauditor", "config.json")

    private val DEFAULT = Config(
        buttons = listOf(
            Button("plan", null, "Plan mode (/plan)", "/plan\r"),
            Button("merge", null, "Merge this worktree into main (/overclauditor:merge)", "/overclauditor:merge\r"),
            Button("push", null, "Merge into main, stay in the worktree (/overclauditor:push)", "/overclauditor:push\r"),
            Button("pull", null, "Bring main into this worktree (/overclauditor:pull)", "/overclauditor:pull\r"),
            Button(null, com.intellij.icons.AllIcons.Actions.Copy, "Copy the last code block of Claude's reply", "", COPY_LAST_CODE_BLOCK)
        ),
        summarySource = SummarySource.BUILTIN,
        summaryFile = null,
        hideTerminalStatusLine = false,
        bundledCommands = true
    )

    @Volatile private var cached: Config = DEFAULT
    @Volatile private var cachedMtime = -1L

    fun get(): Config {
        val mtime = try { Files.getLastModifiedTime(file).toMillis() } catch (_: Exception) { 0L }
        if (mtime != cachedMtime) {
            cachedMtime = mtime
            cached = if (mtime == 0L) DEFAULT else try { parse(JsonParser.parseString(Files.readString(file)).asJsonObject) } catch (_: Exception) { DEFAULT }
        }
        return cached
    }

    internal fun parse(o: JsonObject): Config {
        val buttons = o.getAsJsonArray("buttons")?.mapNotNull { e ->
            val b = e.asJsonObject
            val label = b.get("label")?.asString
            val icon = b.get("icon")?.asString?.let(::resolveIcon)
                ?.let { base -> b.get("badge")?.asString?.takeIf { it.isNotBlank() }?.let { BadgeIcon(base, it.take(2)) } ?: base }
            val builtin = b.get("action")?.asString?.takeIf { it == COPY_LAST_CODE_BLOCK }
            val send = b.get("send")?.asString?.let { if (it.startsWith("/")) "$it\r" else it }
                ?: b.get("keys")?.asString?.let(::keysToBytes)
                ?: if (builtin != null) "" else return@mapNotNull null
            if (label == null && icon == null) return@mapNotNull null
            Button(label, icon, b.get("tooltip")?.asString ?: (label ?: builtin ?: send.trim()), send, builtin)
        } ?: DEFAULT.buttons
        val summary = o.getAsJsonObject("summary")
        val source = when (summary?.get("source")?.asString?.lowercase()) {
            "file" -> SummarySource.FILE
            "off" -> SummarySource.OFF
            else -> SummarySource.BUILTIN
        }
        return Config(
            buttons = buttons,
            summarySource = source,
            summaryFile = summary?.get("file")?.asString?.replaceFirst("~", System.getProperty("user.home")),
            hideTerminalStatusLine = o.get("hideTerminalStatusLine")?.asBoolean ?: DEFAULT.hideTerminalStatusLine,
            bundledCommands = o.get("bundledCommands")?.asBoolean ?: DEFAULT.bundledCommands,
            defaultModel = o.get("defaultModel")?.asString?.trim()?.takeIf { it.matches(Regex("[A-Za-z0-9._\\[\\]-]+")) },
            defaultEffort = o.get("defaultEffort")?.asString?.trim()?.lowercase()?.takeIf { it in EFFORTS }
        )
    }

    private val EFFORTS = setOf("low", "medium", "high", "xhigh", "max")

    /** "Actions.Copy" → AllIcons.Actions.Copy. */
    private fun resolveIcon(name: String): Icon? = try {
        val group = name.substringBefore('.')
        val field = name.substringAfter('.')
        Class.forName("com.intellij.icons.AllIcons\$$group").getField(field).get(null) as? Icon
    } catch (_: Exception) { null }

    /** "ctrl+x ctrl+r" → the bytes a terminal sends for that chord sequence. */
    internal fun keysToBytes(keys: String): String = buildString {
        for (stroke in keys.trim().split(Regex("\\s+"))) {
            val parts = stroke.lowercase().split('+')
            val key = parts.last()
            if ("ctrl" in parts && key.length == 1 && key[0] in 'a'..'z') append((key[0].code - 'a'.code + 1).toChar())
            else if (key == "enter") append('\r')
            else if (key == "esc") append('\u001b')
            else if (key == "tab") append('\t')
            else append(key)
        }
    }
}

/** An icon with a short mark (e.g. "*") in its top-right corner: "run" vs "run this worktree". */
class BadgeIcon(private val base: Icon, private val mark: String) : Icon {
    override fun getIconWidth() = base.iconWidth
    override fun getIconHeight() = base.iconHeight
    override fun paintIcon(c: java.awt.Component?, g: java.awt.Graphics, x: Int, y: Int) {
        base.paintIcon(c, g, x, y)
        val g2 = g.create() as java.awt.Graphics2D
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.font = (c?.font ?: java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 11))
                .deriveFont(java.awt.Font.BOLD, com.intellij.util.ui.JBUI.scale(11).toFloat())
            val fm = g2.fontMetrics
            g2.color = com.intellij.ui.JBColor(java.awt.Color(0xE0, 0x7B, 0x2E), java.awt.Color(0xFF, 0xC5, 0x3D))
            g2.drawString(mark, x + iconWidth - fm.stringWidth(mark) + com.intellij.util.ui.JBUI.scale(3), y + fm.ascent - com.intellij.util.ui.JBUI.scale(3))
        } finally {
            g2.dispose()
        }
    }
}
