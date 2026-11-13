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
    data class Button(val label: String?, val icon: Icon?, val tooltip: String, val send: String)

    enum class SummarySource { BUILTIN, FILE, OFF }

    data class Config(
        val buttons: List<Button>,
        val summarySource: SummarySource,
        /** For FILE: path with {sessionId}, line 1 the gist, line 2 what to test. */
        val summaryFile: String?,
        /** Blank the user's own status line in plugin sessions (the toolbar shows the same information). */
        val hideTerminalStatusLine: Boolean,
        /** Load the bundled /overclauditor:merge, :push, :pull into plugin sessions (--plugin-dir). */
        val bundledCommands: Boolean
    )

    val file: Path = Path.of(System.getProperty("user.home"), ".overclauditor", "config.json")

    private val DEFAULT = Config(
        buttons = listOf(
            Button("plan", null, "Plan mode (/plan)", "/plan\r"),
            Button("merge", null, "Merge this worktree into main (/overclauditor:merge)", "/overclauditor:merge\r"),
            Button("push", null, "Merge into main, stay in the worktree (/overclauditor:push)", "/overclauditor:push\r"),
            Button("pull", null, "Bring main into this worktree (/overclauditor:pull)", "/overclauditor:pull\r")
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
            val send = b.get("send")?.asString?.let { if (it.startsWith("/")) "$it\r" else it }
                ?: b.get("keys")?.asString?.let(::keysToBytes)
                ?: return@mapNotNull null
            if (label == null && icon == null) return@mapNotNull null
            Button(label, icon, b.get("tooltip")?.asString ?: (label ?: send.trim()), send)
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
            bundledCommands = o.get("bundledCommands")?.asBoolean ?: DEFAULT.bundledCommands
        )
    }

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
