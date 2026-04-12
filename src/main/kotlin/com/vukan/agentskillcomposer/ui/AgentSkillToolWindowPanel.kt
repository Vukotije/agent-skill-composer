package com.vukan.agentskillcomposer.ui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.vukan.agentskillcomposer.MyMessageBundle
import com.vukan.agentskillcomposer.analysis.ProjectAnalyzer
import com.vukan.agentskillcomposer.model.AnalysisResult
import com.vukan.agentskillcomposer.model.GeneratedArtifact
import com.vukan.agentskillcomposer.model.ProjectFacts
import com.vukan.agentskillcomposer.model.SampleData
import javax.swing.BoxLayout
import javax.swing.JPanel

class AgentSkillToolWindowPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true) {

    private val analysisSummaryPanel = AnalysisSummaryPanel()
    private val generationFormPanel = GenerationFormPanel()
    private val statusPanel = GenerationStatusPanel()

    private var currentFacts: ProjectFacts? = null
    private var generatedArtifacts: List<GeneratedArtifact> = emptyList()

    init {
        val topBar = panel {
            row {
                button(MyMessageBundle.message("action.analyze")) { onAnalyze() }
                    .align(AlignX.CENTER)
            }
        }

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(topBar)
            add(analysisSummaryPanel)
            add(generationFormPanel)
            add(statusPanel)
        }

        setContent(JBScrollPane(content))
        wireCallbacks()
    }

    private fun wireCallbacks() {
        generationFormPanel.onGenerate = { target, artifactTypes, _ ->
            val artifacts = artifactTypes.map { type ->
                SampleData.createSampleArtifact(target, type)
            }
            generatedArtifacts = artifacts
            statusPanel.update(artifacts)
            EditorPreviewHelper.openAllInEditor(project, artifacts)
        }
    }

    private fun onAnalyze() {
        val analyzer = project.getService(ProjectAnalyzer::class.java)

        object : Task.Backgroundable(project, MyMessageBundle.message("progress.analyzing"), true) {
            private var result: AnalysisResult? = null

            override fun run(indicator: ProgressIndicator) {
                result = analyzer.analyze(indicator)
            }

            override fun onSuccess() {
                when (val r = result) {
                    is AnalysisResult.Success -> {
                        currentFacts = r.facts
                        generatedArtifacts = emptyList()
                        analysisSummaryPanel.update(r.facts)
                        generationFormPanel.reveal(r.facts)
                        statusPanel.clear()
                    }
                    is AnalysisResult.Failure -> {
                        Notifications.Bus.notify(
                            Notification(
                                "Agent Skill Composer",
                                MyMessageBundle.message("error.analysisFailedTitle"),
                                MyMessageBundle.message("error.analysisFailedMessage", r.message),
                                NotificationType.ERROR,
                            ),
                            project,
                        )
                    }
                    null -> { /* cancelled */ }
                }
            }
        }.queue()
    }
}
