package com.vukan.agentskillcomposer.ui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.vukan.agentskillcomposer.MyMessageBundle
import com.vukan.agentskillcomposer.analysis.ProjectAnalyzer
import com.vukan.agentskillcomposer.generation.AiProviderFactory
import com.vukan.agentskillcomposer.generation.impl.DefaultArtifactGenerator
import com.vukan.agentskillcomposer.generation.impl.DefaultPromptFactory
import com.vukan.agentskillcomposer.model.AnalysisResult
import com.vukan.agentskillcomposer.model.ArtifactType
import com.vukan.agentskillcomposer.model.GeneratedArtifact
import com.vukan.agentskillcomposer.model.GenerationRequest
import com.vukan.agentskillcomposer.model.GenerationResult
import com.vukan.agentskillcomposer.model.GenerationTarget
import com.vukan.agentskillcomposer.model.ProjectFacts
import com.vukan.agentskillcomposer.output.TargetPathResolver
import com.vukan.agentskillcomposer.output.impl.DefaultTargetPathResolver
import com.vukan.agentskillcomposer.settings.PluginSettings
import com.vukan.agentskillcomposer.settings.PluginSettingsConfigurable
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.ui.AnimatedIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

class AgentSkillToolWindowPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true) {

    private val pathResolver: TargetPathResolver = DefaultTargetPathResolver()
    private val analysisSummaryPanel = AnalysisSummaryPanel()
    private val generationFormPanel = GenerationFormPanel()

    private var currentFacts: ProjectFacts? = null
    private var generatedArtifacts: List<GeneratedArtifact> = emptyList()
    private var analyzeButton: JButton? = null
    private val spinnerIcon = AnimatedIcon.Default()

    init {
        toolbar = createToolbar().component

        val topBar = panel {
            row {
                button(MyMessageBundle.message("action.analyze")) { onAnalyze() }
                    .align(AlignX.CENTER)
                    .applyToComponent { analyzeButton = this }
            }
        }

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(topBar)
            add(analysisSummaryPanel)
            add(generationFormPanel)
        }

        setContent(JBScrollPane(content))
        wireCallbacks()
    }

    private fun createToolbar() = ActionManager.getInstance().createActionToolbar(
        "AgentSkillComposer",
        DefaultActionGroup(
            object : AnAction(
                MyMessageBundle.message("action.openSettings"),
                MyMessageBundle.message("action.openSettings.description"),
                AllIcons.General.GearPlain,
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, PluginSettingsConfigurable::class.java)
                }
            },
        ),
        true,
    )

    private fun wireCallbacks() {
        generationFormPanel.pathResolver = { target, type ->
            pathResolver.resolveRelativePath(target, type)
        }
        generationFormPanel.onGenerate = { target, artifactTypes, userInstructions ->
            onGenerate(target, artifactTypes, userInstructions)
        }
    }

    private fun onGenerate(
        target: GenerationTarget,
        artifactTypes: List<ArtifactType>,
        userInstructions: String?,
    ) {
        val facts = currentFacts ?: return
        val settings = PluginSettings.getInstance()

        if (settings.apiKey.isNullOrBlank()) {
            showSettingsPrompt("error.missingApiKeyTitle", "error.missingApiKeyMessage")
            return
        }

        if (settings.state.modelName.isBlank()) {
            showSettingsPrompt("error.missingModelTitle", "error.missingModelMessage")
            return
        }

        generationFormPanel.setGenerating(true)
        generationFormPanel.showProgress(artifactTypes.size)

        (project as ComponentManagerEx).getCoroutineScope().launch {
            val provider = try {
                AiProviderFactory.create(settings)
            } catch (e: Exception) {
                withContext(Dispatchers.EDT) {
                    showErrorNotification(e.message ?: "Failed to create AI provider")
                    generationFormPanel.setGenerating(false)
                }
                return@launch
            }

            val generator = DefaultArtifactGenerator(
                aiProvider = provider,
                promptFactory = DefaultPromptFactory(),
                pathResolver = pathResolver,
            )

            val results = mutableListOf<GenerationResult>()

            for ((index, artifactType) in artifactTypes.withIndex()) {
                val request = GenerationRequest(
                    target = target,
                    artifactType = artifactType,
                    projectFacts = facts,
                    userInstructions = userInstructions,
                )
                val result = generator.generate(request)
                results.add(result)

                withContext(Dispatchers.EDT) {
                    generationFormPanel.updateProgress(index + 1, artifactTypes.size, result)
                }
            }

            withContext(Dispatchers.EDT) {
                val successes = results.filterIsInstance<GenerationResult.Success>()
                val failures = results.filterIsInstance<GenerationResult.Failure>()

                generatedArtifacts = successes.map { it.artifact }
                generationFormPanel.showDone(generatedArtifacts)

                if (generatedArtifacts.isNotEmpty()) {
                    EditorPreviewHelper.openAllInEditor(project, generatedArtifacts)
                }
                failures.forEach { showErrorNotification(it.message) }
                generationFormPanel.setGenerating(false)
            }
        }
    }

    private fun showSettingsPrompt(titleKey: String, messageKey: String) {
        val notification = Notification(
            "Agent Skill Composer",
            MyMessageBundle.message(titleKey),
            MyMessageBundle.message(messageKey),
            NotificationType.WARNING,
        )
        notification.addAction(
            NotificationAction.createSimple(MyMessageBundle.message("action.openSettings")) {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, PluginSettingsConfigurable::class.java)
            },
        )
        Notifications.Bus.notify(notification, project)
    }

    private fun showErrorNotification(message: String) {
        Notifications.Bus.notify(
            Notification(
                "Agent Skill Composer",
                MyMessageBundle.message("error.generationFailedTitle"),
                MyMessageBundle.message("error.generationFailedMessage", message),
                NotificationType.ERROR,
            ),
            project,
        )
    }

    private fun setAnalyzing(analyzing: Boolean) {
        analyzeButton?.apply {
            isEnabled = !analyzing
            icon = if (analyzing) spinnerIcon else null
            text = if (analyzing) {
                MyMessageBundle.message("progress.analyzing")
            } else {
                MyMessageBundle.message("action.analyze")
            }
        }
    }

    private fun onAnalyze() {
        val analyzer = project.getService(ProjectAnalyzer::class.java)
        setAnalyzing(true)

        object : Task.Backgroundable(project, MyMessageBundle.message("progress.analyzing"), true) {
            private var result: AnalysisResult? = null

            override fun run(indicator: ProgressIndicator) {
                result = analyzer.analyze(indicator)
            }

            override fun onSuccess() {
                setAnalyzing(false)
                when (val r = result) {
                    is AnalysisResult.Success -> {
                        currentFacts = r.facts
                        generatedArtifacts = emptyList()
                        analysisSummaryPanel.update(r.facts)
                        generationFormPanel.reveal(r.facts)
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

            override fun onCancel() {
                setAnalyzing(false)
            }
        }.queue()
    }
}
