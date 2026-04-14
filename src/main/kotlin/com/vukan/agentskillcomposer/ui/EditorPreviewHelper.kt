package com.vukan.agentskillcomposer.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.vukan.agentskillcomposer.MyMessageBundle
import com.vukan.agentskillcomposer.model.GeneratedArtifact

object EditorPreviewHelper {

    fun openAllInEditor(project: Project, artifacts: List<GeneratedArtifact>): List<VirtualFile> {
        val manager = FileEditorManager.getInstance(project)
        return artifacts.mapIndexed { index, artifact ->
            val file = LightVirtualFile(artifact.previewFileName, artifact.content)
            manager.openFile(file, index == 0)
            file
        }
    }

    fun closePreviews(project: Project, files: List<VirtualFile>) {
        if (files.isEmpty()) return
        val manager = FileEditorManager.getInstance(project)
        files.forEach { manager.closeFile(it) }
    }
}

private val GeneratedArtifact.previewFileName: String
    get() = MyMessageBundle.message("editor.previewPrefix", defaultPath)
