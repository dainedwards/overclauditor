package com.clauditor.toolwindow

import com.clauditor.editor.ClaudeSessionVirtualFile
import com.clauditor.model.SessionDisplay
import com.clauditor.model.SessionStatus
import com.clauditor.services.ClaudeSessionService
import com.clauditor.services.OpenSessionsPersistence
import com.clauditor.util.ClaudePathEncoder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class ClaudeSessionsToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val sessionService = ClaudeSessionService.getInstance(project)

        val getStatus = { sessionId: String ->
            when {
                // Plugin ownership wins: a session open in a Clauditor tab is never
                // "external", even if a stray/background claude process references it.
                isOpenInPlugin(project, sessionId) -> SessionStatus.OPEN_IN_PLUGIN
                sessionService.isExternallyOpen(sessionId) -> SessionStatus.OPEN_EXTERNALLY
                else -> SessionStatus.AVAILABLE
            }
        }

        val callbacks = SessionCallbacks(
            onSessionSelected = { session -> openSession(project, session) },
            onNewSession = { name -> openNewSession(project, name) },
            onForkSession = { session -> forkSession(project, session) }
        )

        // Regular sessions tab
        val sessionsPanel = SessionListPanel(
            project, sessionService, getStatus,
            callbacks.onSessionSelected, callbacks.onNewSession, callbacks.onForkSession
        )
        val sessionsContent = ContentFactory.getInstance().createContent(sessionsPanel.getComponent(), "Sessions", false)
        sessionsContent.icon = IconLoader.getIcon("/icons/sessions.svg", ClaudeSessionsToolWindowFactory::class.java)
        sessionsContent.isCloseable = false
        sessionsContent.setDisposer(sessionsPanel)
        toolWindow.contentManager.addContent(sessionsContent)

        // Worktree sessions tab
        val worktreePanel = SessionListPanel(
            project, sessionService, getStatus,
            callbacks.onSessionSelected,
            { name -> openNewWorktreeSession(project, name) },
            callbacks.onForkSession,
            worktreeMode = true
        )
        val worktreeContent = ContentFactory.getInstance().createContent(worktreePanel.getComponent(), "Worktrees", false)
        worktreeContent.icon = IconLoader.getIcon("/icons/worktree.svg", ClaudeSessionsToolWindowFactory::class.java)
        worktreeContent.isCloseable = false
        worktreeContent.setDisposer(worktreePanel)
        toolWindow.contentManager.addContent(worktreeContent)

        sessionService.startWatching()

        // Refresh session list status column when editor tabs open/close
        val editorListener = object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                if (file is ClaudeSessionVirtualFile) {
                    sessionsPanel.refreshStatuses()
                    worktreePanel.refreshStatuses()
                }
            }
            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                if (file is ClaudeSessionVirtualFile) {
                    sessionsPanel.refreshStatuses()
                    worktreePanel.refreshStatuses()
                }
            }
        }
        project.messageBus.connect(sessionsPanel).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER, editorListener
        )

        // Tabs open at shutdown are restored at project startup (TabBarNewSessionActivity),
        // not here: this only runs when the Sessions pane is first shown.
    }

    private data class SessionCallbacks(
        val onSessionSelected: (SessionDisplay) -> Unit,
        val onNewSession: (String) -> Unit,
        val onForkSession: (SessionDisplay) -> Unit
    )

    private fun isOpenInPlugin(project: Project, sessionId: String): Boolean {
        return FileEditorManager.getInstance(project).openFiles
            .filterIsInstance<ClaudeSessionVirtualFile>()
            .any { it.sessionId == sessionId }
    }

    private fun openSession(project: Project, session: SessionDisplay) {
        com.clauditor.editor.openClaudeSession(project, session, focus = true)
    }

    private fun openNewSession(project: Project, name: String) {
        com.clauditor.editor.openNewClaudeSession(project)
    }

    private fun openNewWorktreeSession(project: Project, worktreeName: String) {
        val file = ClaudeSessionVirtualFile(worktreeName, newWorktreeName = worktreeName).apply {
            // The worktree directory is deterministic from the name (the CLI normalizes
            // '/'→'+', mirrored by WorktreeInspector.normalize), so resolve it up front —
            // the git/worktree toolbars can show the real branch before the first message
            // instead of falling back to the main repo and showing "main". The CLI creates
            // the dir asynchronously after spawn; the toolbars tolerate it not existing yet.
            if (project.basePath != null) {
                workingDir = ClaudePathEncoder.worktreeAbsolutePath(
                    project.basePath!!, WorktreeInspector.normalize(worktreeName)
                )
            }
        }
        com.clauditor.editor.openSessionTabAtEnd(project, file, true)
    }

    private fun forkSession(project: Project, session: SessionDisplay) {
        val file = ClaudeSessionVirtualFile("Fork of ${session.tabTitle}", forkFrom = session.sessionId).apply {
            if (session.worktreeName != null && project.basePath != null) {
                workingDir = ClaudePathEncoder.worktreeAbsolutePath(project.basePath!!, session.worktreeName)
                isWorktreeSession = true
            }
        }
        com.clauditor.editor.openForkNextToSource(project, file, session.sessionId)
    }

    override fun shouldBeAvailable(project: Project): Boolean = project.basePath != null
}
