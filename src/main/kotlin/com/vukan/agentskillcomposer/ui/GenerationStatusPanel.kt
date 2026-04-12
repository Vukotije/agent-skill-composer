package com.vukan.agentskillcomposer.ui

import com.intellij.ui.dsl.builder.panel
import com.vukan.agentskillcomposer.MyMessageBundle
import com.vukan.agentskillcomposer.model.GeneratedArtifact
import java.awt.BorderLayout
import javax.swing.JPanel

class GenerationStatusPanel : JPanel(BorderLayout()) {

    init {
        isVisible = false
    }

    fun update(artifacts: List<GeneratedArtifact>) {
        removeAll()

        val statusContent = panel {
            group(MyMessageBundle.message("group.generatedArtifacts")) {
                for (artifact in artifacts) {
                    row(artifact.title) {
                        comment(artifact.defaultPath)
                    }
                }
            }
            row {
                comment(MyMessageBundle.message("status.artifactsOpenedInEditor"))
            }
        }

        add(statusContent, BorderLayout.CENTER)
        isVisible = true
        revalidate()
        repaint()
    }

    fun clear() {
        removeAll()
        isVisible = false
    }
}
