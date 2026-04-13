package com.vukan.agentskillcomposer.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.vukan.agentskillcomposer.MyMessageBundle
import com.vukan.agentskillcomposer.model.GeneratedArtifact

object EditorPreviewHelper {

    fun openAllInEditor(project: Project, artifacts: List<GeneratedArtifact>) {
        artifacts.forEachIndexed { index, artifact ->
            val focusOnOpen = index == 0
            val file = LightVirtualFile(artifact.previewFileName, artifact.content)
            FileEditorManager.getInstance(project).openFile(file, focusOnOpen)
        }
    }
}

private val GeneratedArtifact.previewFileName: String
    get() = MyMessageBundle.message("editor.previewPrefix", defaultPath)
