package com.clauditor.editor

import com.clauditor.services.TerminalSession
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.nio.file.Path

/**
 * A session's running Claude process and terminal, kept on its [ClaudeSessionVirtualFile] rather
 * than in the editor, the way the IDE's own terminal tabs do (TerminalSessionEditor). Dragging a
 * tab closes its editor and opens a new one (FileEditorManagerKeys.CLOSING_TO_REOPEN is set
 * meanwhile); the new editor adopts this instead of starting `claude --resume` again, so the
 * session keeps running and its scrollback survives.
 *
 * The callbacks go to whichever editor owns it now: each editor sets them when it adopts.
 */
class LiveTerminal(
    val disposable: Disposable,
    val monitoringId: String,
    val statusFile: Path,
    val notifyFile: Path,
    val needsLinking: Boolean
) {
    lateinit var session: TerminalSession
    @Volatile var onActive: (Boolean) -> Unit = {}
    @Volatile var onUserInput: () -> Unit = {}
    @Volatile var onUnresponsive: (Boolean) -> Unit = {}
    @Volatile var onExit: (Int) -> Unit = {}
    @Volatile var owned = false
    @Volatile private var killed = false

    init { Disposer.register(disposable, Disposable { killed = true }) }

    val isAlive: Boolean get() = !killed && ::session.isInitialized && session.process.isAlive

    fun kill() { if (!killed) Disposer.dispose(disposable) }
}
