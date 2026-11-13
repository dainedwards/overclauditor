package com.clauditor.util

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.PathManager
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Where the plugin keeps its own files. Caches live in the IDE's system folder (the
 * platform-standard place), not the user's home, and are removed with the plugin. The only
 * file in the home folder is the user-written ~/.overclauditor/config.json.
 */
object OcPaths {
    private const val PLUGIN_ID = "com.overclauditor"

    /** Session-list index, pinned titles, built-in summaries. */
    val dataDir: Path by lazy {
        val dir = Path.of(PathManager.getSystemPath(), "overclauditor")
        migrateFromHome(dir)
        dir
    }

    /** Builds before this one kept caches in ~/.overclauditor; move them once. */
    private fun migrateFromHome(dir: Path) {
        val old = Path.of(System.getProperty("user.home"), ".overclauditor")
        for (name in listOf("session-index", "titles.json", "summary")) {
            val from = old.resolve(name)
            val to = dir.resolve(name)
            if (!Files.exists(from) || Files.exists(to)) continue
            try {
                Files.createDirectories(dir)
                Files.move(from, to)
            } catch (_: Exception) {}
        }
    }

    private val BUNDLED = listOf(
        ".claude-plugin/plugin.json", "commands/merge.md", "commands/push.md", "commands/pull.md",
        "scripts/git-merge-lock.sh"
    )

    /**
     * The Claude Code plugin bundled in this jar (commands /overclauditor:merge, :push, :pull),
     * unpacked into [dataDir] and passed to each session with --plugin-dir. Nothing goes into
     * ~/.claude; it is rewritten only when the bundled copy changes. Null if unpacking failed.
     */
    fun bundledClaudePlugin(): Path? = try {
        val root = dataDir.resolve("claude-plugin")
        for (rel in BUNDLED) {
            val bytes = OcPaths::class.java.getResourceAsStream("/claude-plugin/$rel")?.use { it.readBytes() } ?: return null
            val target = root.resolve(rel)
            if (!Files.exists(target) || !Files.readAllBytes(target).contentEquals(bytes)) {
                Files.createDirectories(target.parent)
                Files.write(target, bytes)
                if (rel.endsWith(".sh")) target.toFile().setExecutable(true)
            }
        }
        root
    } catch (_: Exception) { null }

    /** Temp files the status line, notify hooks and launch settings leave if the IDE dies mid-session. */
    fun sweepStaleTempFiles(maxAgeMs: Long = 24L * 3600 * 1000) {
        val now = System.currentTimeMillis()
        File(System.getProperty("java.io.tmpdir")).listFiles { f -> f.name.startsWith("clauditor-") }
            ?.filter { now - it.lastModified() > maxAgeMs }
            ?.forEach { runCatching { it.delete() } }
    }

    /** Uninstall (or disable): remove the caches and temp files so nothing is left behind. */
    class CleanupOnUnload : DynamicPluginListener {
        override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
            if (isUpdate || pluginDescriptor.pluginId.idString != PLUGIN_ID) return
            runCatching { File(PathManager.getSystemPath(), "overclauditor").deleteRecursively() }
            sweepStaleTempFiles(maxAgeMs = 0)
        }
    }
}
