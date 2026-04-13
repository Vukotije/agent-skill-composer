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
import com.vukan.agentskillcomposer.output.ArtifactMetadata
import com.vukan.agentskillcomposer.output.ArtifactMetadataResolver
import com.vukan.agentskillcomposer.output.ArtifactRenderer
import com.vukan.agentskillcomposer.output.ArtifactWriter
import com.vukan.agentskillcomposer.output.SaveResult
import com.vukan.agentskillcomposer.output.SaveStatus
import com.vukan.agentskillcomposer.output.TargetPathResolver
import com.vukan.agentskillcomposer.output.impl.DefaultArtifactRenderer
import com.vukan.agentskillcomposer.output.impl.DefaultArtifactWriter
import com.vukan.agentskillcomposer.output.impl.DefaultTargetPathResolver
import com.vukan.agentskillcomposer.settings.PluginSettings
import com.vukan.agentskillcomposer.settings.PluginSettingsConfigurable
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

class AgentSkillToolWindowPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true) {

    private val pathResolver: TargetPathResolver = DefaultTargetPathResolver()
    private val metadataResolver = ArtifactMetadataResolver(pathResolver)
    private val renderer: ArtifactRenderer = DefaultArtifactRenderer()
    private val writer: ArtifactWriter = DefaultArtifactWriter()
    private val analysisSummaryPanel = AnalysisSummaryPanel()
    private val generationFormPanel = GenerationFormPanel()

    private var currentFacts: ProjectFacts? = null
    private var generatedArtifacts: List<GeneratedArtifact> = emptyList()
    private var previewFiles: List<VirtualFile> = emptyList()
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
        generationFormPanel.onSaveAll = { artifacts ->
            onSaveAll(artifacts)
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
                promptFactory = DefaultPromptFactory(pathResolver),
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

                EditorPreviewHelper.closePreviews(project, previewFiles)
                previewFiles = emptyList()

                generatedArtifacts = successes.map { it.artifact }
                generationFormPanel.showDone(generatedArtifacts)

                if (generatedArtifacts.isNotEmpty()) {
                    previewFiles = EditorPreviewHelper.openAllInEditor(project, generatedArtifacts)
                }
                failures.forEach { showErrorNotification(it.message) }
                generationFormPanel.setGenerating(false)
            }
        }
    }

    private data class SaveJob(
        val artifact: GeneratedArtifact,
        val metadata: ArtifactMetadata,
        val renderedContent: String,
        val absolutePath: Path,
    )

    private fun onSaveAll(artifacts: List<GeneratedArtifact>) {
        val projectRoot = currentFacts?.projectRoot ?: return
        if (artifacts.isEmpty()) return

        val jobs = artifacts.map { artifact ->
            val metadata = metadataResolver.resolve(artifact.target, artifact.artifactType)
            SaveJob(
                artifact = artifact,
                metadata = metadata,
                renderedContent = renderer.render(artifact, metadata),
                absolutePath = projectRoot.resolve(metadata.relativePath),
            )
        }

        val conflicts = jobs.filter { Files.exists(it.absolutePath) }
        if (conflicts.isNotEmpty() && !confirmOverwrite(conflicts)) return

        generationFormPanel.setSaving(true)
        val results = try {
            jobs.map { job -> writer.save(projectRoot, job.metadata, job.renderedContent) }
        } finally {
            generationFormPanel.setSaving(false)
        }

        if (results.any { it is SaveResult.Saved }) {
            EditorPreviewHelper.closePreviews(project, previewFiles)
            previewFiles = emptyList()
        }
        notifySaveOutcome(results)
    }

    private fun confirmOverwrite(conflicts: List<SaveJob>): Boolean {
        val list = conflicts.joinToString("\n") { "• ${it.metadata.relativePath}" }
        val answer = Messages.showYesNoDialog(
            project,
            MyMessageBundle.message("dialog.overwrite.message", list),
            MyMessageBundle.message("dialog.overwrite.title"),
            MyMessageBundle.message("dialog.overwrite.yes"),
            MyMessageBundle.message("dialog.overwrite.no"),
            Messages.getWarningIcon(),
        )
        return answer == Messages.YES
    }

    private fun notifySaveOutcome(results: List<SaveResult>) {
        val saved = results.filterIsInstance<SaveResult.Saved>()
        val failed = results.filterIsInstance<SaveResult.Failed>()

        if (saved.isNotEmpty()) {
            val created = saved.count { it.status == SaveStatus.CREATED }
            val overwritten = saved.count { it.status == SaveStatus.OVERWRITTEN }
            val body = buildString {
                if (created > 0) appendLine(MyMessageBundle.message("notification.savedCreated", created))
                if (overwritten > 0) appendLine(MyMessageBundle.message("notification.savedOverwritten", overwritten))
            }.trim()

            val notification = Notification(
                "Agent Skill Composer",
                MyMessageBundle.message("notification.savedTitle"),
                body,
                NotificationType.INFORMATION,
            )
            val firstFile = LocalFileSystem.getInstance().findFileByNioFile(saved.first().path)
            if (firstFile != null) {
                notification.addAction(
                    NotificationAction.createSimple(MyMessageBundle.message("action.revealInProject")) {
                        ProjectView.getInstance(project).select(null, firstFile, true)
                    },
                )
            }
            Notifications.Bus.notify(notification, project)
        }

        if (failed.isNotEmpty()) {
            val body = failed.joinToString("\n") { "• ${it.path}: ${it.message}" }
            Notifications.Bus.notify(
                Notification(
                    "Agent Skill Composer",
                    MyMessageBundle.message("notification.saveFailedTitle"),
                    body,
                    NotificationType.ERROR,
                ),
                project,
            )
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
