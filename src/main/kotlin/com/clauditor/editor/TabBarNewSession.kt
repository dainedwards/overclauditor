package com.clauditor.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tabs.JBTabs
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities

/**
 * Open a session tab at the END of the tab row. The platform puts new tabs next to the current
 * one unless "Open new tabs at the end" is on; the public way to ask for the end is to switch
 * that setting on for this one open and restore it, leaving the user's other files alone.
 */
fun openSessionTabAtEnd(project: Project, file: VirtualFile, focus: Boolean) {
    val ui = com.intellij.ide.ui.UISettings.getInstance()
    val was = ui.openTabsAtTheEnd
    ui.openTabsAtTheEnd = true
    try {
        FileEditorManager.getInstance(project).openFile(file, focus)
    } finally {
        ui.openTabsAtTheEnd = was
    }
}

/** Open (or focus) a tab for an existing session. */
fun openClaudeSession(project: Project, session: com.clauditor.model.SessionDisplay, focus: Boolean) {
    val manager = FileEditorManager.getInstance(project)
    val existing = manager.openFiles.filterIsInstance<ClaudeSessionVirtualFile>().find { it.sessionId == session.sessionId }
    if (existing != null) {
        if (focus) manager.openFile(existing, true)
        return
    }
    val file = ClaudeSessionVirtualFile(session.tabTitle, session.sessionId).apply {
        if (session.worktreeName != null && project.basePath != null) {
            workingDir = com.clauditor.util.ClaudePathEncoder.worktreeAbsolutePath(project.basePath!!, session.worktreeName)
            isWorktreeSession = true
        }
    }
    openSessionTabAtEnd(project, file, focus)
}

/**
 * Reopen the session tabs that were open at shutdown. Runs at project startup whether or not
 * the Sessions pane is showing, and waits for the session list's first background scan:
 * the list is empty until it lands, and restoring against that found nothing.
 */
fun restoreOpenSessions(project: Project) {
    val saved = com.clauditor.services.OpenSessionsPersistence.getInstance(project).getAll()
    if (saved.isEmpty()) return
    val service = com.clauditor.services.ClaudeSessionService.getInstance(project)
    fun restore() {
        val byId = service.getSessions().associateBy { it.sessionId }
        for (id in saved) byId[id]?.let { openClaudeSession(project, it, focus = false) }
    }
    if (service.isLoaded) { restore(); return }
    val listener = object : () -> Unit {
        override fun invoke() {
            if (!service.isLoaded || project.isDisposed) return
            service.removeChangeListener(this)
            restore()
        }
    }
    service.addChangeListener(listener)
    service.refresh()
}

/** Open a fresh Claude session tab; the CLI titles it after the first message. */
fun openNewClaudeSession(project: Project) {
    openSessionTabAtEnd(project, ClaudeSessionVirtualFile("New session"), true)
}

/** "New Claude Session" in the right-click menu of an editor tab. */
class NewClaudeSessionAction : AnAction("New Claude Session", "Start a new Claude session", AllIcons.General.Add), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        openNewClaudeSession(e.project ?: return)
    }
    override fun update(e: AnActionEvent) { e.presentation.isEnabledAndVisible = e.project != null }
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/**
 * The empty part of the editor tab bar (past the last tab) has no menu of its own. Give it
 * one: right-click shows "New Claude Session", double-click opens one directly. Installed
 * once per editor window, as windows appear (splits, new windows).
 */
class TabBarNewSessionActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        com.clauditor.util.ClauditorExecutor.submit { com.clauditor.util.OcPaths.sweepStaleTempFiles() }
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            claimDoubleClick()
            restoreOpenSessions(project)
            installAll(project)
            project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {
                    override fun fileOpened(source: FileEditorManager, file: VirtualFile) = installAll(project)
                })
        }
    }

    private fun installAll(project: Project) {
        // Editor-window tab internals aren't stable API: if a platform version changes them,
        // lose this convenience rather than break startup or every file open.
        try { install(project) } catch (_: Throwable) {}
    }

    private fun install(project: Project) {
        for (window in FileEditorManagerEx.getInstanceEx(project).windows) {
            val tabs = window.tabbedPane.tabs as? JBTabs ?: continue
            val c: JComponent = tabs.component
            if (c.getClientProperty(INSTALLED) == true) continue
            c.putClientProperty(INSTALLED, true)
            c.addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) = maybePopup(e)
                override fun mouseReleased(e: MouseEvent) = maybePopup(e)
                override fun mouseClicked(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2 && tabs.findInfo(e) == null) {
                        openNewClaudeSession(project)
                    }
                }
                private fun maybePopup(e: MouseEvent) {
                    if (!e.isPopupTrigger || tabs.findInfo(e) != null) return
                    JPopupMenu().apply {
                        add(JMenuItem("New Claude Session", AllIcons.General.Add).apply {
                            addActionListener { openNewClaudeSession(project) }
                        })
                    }.show(e.component, e.x, e.y)
                }
            })
        }
    }

    private companion object {
        const val INSTALLED = "overclauditor.tabBarNewSession"
    }
}

/** The platform's own double-click on the empty tab bar: hide/restore all tool windows. */
private const val MAXIMIZE_KEY = "editor.maximize.on.double.click"
private const val RESTORE_PROP = "overclauditor.restoreMaximizeOnDoubleClick"

/**
 * Double-clicking the empty tab bar is ours (new session). The platform runs its own handler
 * first and ignores consumed events, so both fired and every new session also toggled all
 * tool windows. Switch the platform's off, remembering the user's value for uninstall.
 */
private fun claimDoubleClick() {
    try {
    val key = com.intellij.openapi.util.registry.Registry.get(MAXIMIZE_KEY)
    if (key.asBoolean()) {
        com.intellij.ide.util.PropertiesComponent.getInstance().setValue(RESTORE_PROP, true)
        key.setValue(false)
    }
    } catch (_: Throwable) {}
}

/** Uninstall: give the platform its double-click back if we took it. */
fun releaseDoubleClick() {
    try {
    val props = com.intellij.ide.util.PropertiesComponent.getInstance()
    if (props.getBoolean(RESTORE_PROP)) {
        com.intellij.openapi.util.registry.Registry.get(MAXIMIZE_KEY).setValue(true)
        props.unsetValue(RESTORE_PROP)
    }
    } catch (_: Throwable) {}
}

