package com.vukan.agentskillcomposer.ui

import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.vukan.agentskillcomposer.MyMessageBundle
import com.vukan.agentskillcomposer.model.ProjectFacts
import java.awt.BorderLayout
import javax.swing.JPanel

class AnalysisSummaryPanel : JPanel(BorderLayout()) {

    init {
        isVisible = false
    }

    fun update(facts: ProjectFacts) {
        removeAll()
        add(buildSummaryPanel(facts), BorderLayout.CENTER)
        isVisible = true
        revalidate()
        repaint()
    }

    fun clear() {
        removeAll()
        isVisible = false
    }

    private fun buildSummaryPanel(facts: ProjectFacts) = panel {
        collapsibleGroup(MyMessageBundle.message("group.analysisSummary")) {
            row(MyMessageBundle.message("label.project")) { label(facts.projectName) }
            row(MyMessageBundle.message("label.languages")) {
                label(facts.languages.joinToString(", ") { it.displayName })
            }
            facts.buildSystem?.let { bs ->
                row(MyMessageBundle.message("label.buildSystem")) { label(bs.displayName) }
            }

            if (facts.frameworks.isNotEmpty()) {
                group(MyMessageBundle.message("group.frameworks")) {
                    facts.frameworks.forEach { fw ->
                        row { label(fw.toDisplayString()) }
                    }
                }
            }

            if (facts.testFrameworks.isNotEmpty()) {
                row(MyMessageBundle.message("label.testFrameworks")) {
                    label(facts.testFrameworks.joinToString(", ") { it.displayName })
                }
            }

            if (facts.conventions.isNotEmpty()) {
                group(MyMessageBundle.message("group.conventions")) {
                    facts.conventions.forEach { conv ->
                        row {
                            label(conv.toDisplayString()).align(AlignX.FILL)
                        }
                        conv.evidence.take(3).forEach { e ->
                            row { comment(e) }
                        }
                    }
                }
            } else if (facts.frameworks.isEmpty() && facts.representativeFiles.isEmpty()) {
                row { comment(MyMessageBundle.message("summary.emptyAnalysis")) }
            }

            if (facts.representativeFiles.isNotEmpty()) {
                group(MyMessageBundle.message("group.representativeFiles")) {
                    facts.representativeFiles.forEach { file ->
                        row { label("${file.fileName} — ${file.role}") }
                    }
                }
            }
        }.apply { expanded = true }
    }
}
