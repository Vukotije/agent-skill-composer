package com.vukan.agentskillcomposer.ui

import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.vukan.agentskillcomposer.MyMessageBundle
import com.vukan.agentskillcomposer.model.ArtifactType
import com.vukan.agentskillcomposer.model.GeneratedArtifact
import com.vukan.agentskillcomposer.model.GenerationResult
import com.vukan.agentskillcomposer.model.GenerationTarget
import com.vukan.agentskillcomposer.model.ProjectFacts
import com.vukan.agentskillcomposer.model.SkillSuggestion
import com.vukan.agentskillcomposer.output.PathResolverFn
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JProgressBar

typealias OnGenerateFn = (GenerationTarget, List<ArtifactType>, String?) -> Unit
typealias OnSaveAllFn = (List<GeneratedArtifact>) -> Unit

class GenerationFormPanel : JPanel(BorderLayout()) {

    private val targetCombo = JComboBox(GenerationTarget.entries.toTypedArray())
    private val artifactCheckBoxes = mutableMapOf<ArtifactType, JCheckBox>()
    private val checkBoxContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private val instructionsArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
    }

    private var currentSkills: List<SkillSuggestion> = emptyList()
    private var generateButton: JButton? = null
    private var saveAllButton: JButton? = null
    private val spinnerIcon = AnimatedIcon.Default()

    private val progressBar = JProgressBar().apply { isVisible = false }
    private val progressLabel = JBLabel(" ").apply { isVisible = false }
    private val resultContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isVisible = false
    }

    private var lastGenerated: List<GeneratedArtifact> = emptyList()

    var pathResolver: PathResolverFn? = null
    var onGenerate: OnGenerateFn? = null
    var onSaveAll: OnSaveAllFn? = null

    init {
        isVisible = false
        targetCombo.addActionListener { refreshArtifactCheckBoxes() }

        val formPanel = panel {
            group(MyMessageBundle.message("group.generateArtifacts")) {
                row(MyMessageBundle.message("label.target")) {
                    cell(targetCombo).align(AlignX.FILL)
                }
                row {
                    label(MyMessageBundle.message("label.artifactTypes"))
                }
                row {
                    cell(checkBoxContainer).align(AlignX.FILL)
                }
                row {
                    textArea()
                        .label(MyMessageBundle.message("label.userInstructions"), LabelPosition.TOP)
                        .align(AlignX.FILL)
                        .rows(3)
                        .applyToComponent {
                            lineWrap = true
                            wrapStyleWord = true
                            instructionsArea.document = document
                        }
                }
                row {
                    button(MyMessageBundle.message("action.generate")) { fireGenerate() }
                        .applyToComponent { generateButton = this }
                    button(MyMessageBundle.message("action.saveAll")) { fireSaveAll() }
                        .align(AlignX.RIGHT)
                        .applyToComponent {
                            saveAllButton = this
                            isVisible = false
                        }
                }
                row {
                    cell(progressBar).align(AlignX.FILL)
                }
                row {
                    cell(progressLabel)
                }
                row {
                    cell(resultContainer).align(AlignX.FILL)
                }
            }
        }

        add(formPanel, BorderLayout.CENTER)
    }

    fun reveal(facts: ProjectFacts) {
        currentSkills = facts.suggestedSkills
        refreshArtifactCheckBoxes()
        clearProgress()
        isVisible = true
        revalidate()
        repaint()
    }

    fun setGenerating(generating: Boolean) {
        val enabled = !generating
        targetCombo.isEnabled = enabled
        instructionsArea.isEnabled = enabled
        artifactCheckBoxes.values.forEach { it.isEnabled = enabled }
        generateButton?.apply {
            isEnabled = enabled && hasSelection()
            icon = if (generating) spinnerIcon else null
            text = if (generating) {
                MyMessageBundle.message("action.generating")
            } else {
                MyMessageBundle.message("action.generate")
            }
        }
        if (generating) {
            resultContainer.removeAll()
            resultContainer.isVisible = false
            lastGenerated = emptyList()
            saveAllButton?.isVisible = false
        }
    }

    fun setSaving(saving: Boolean) {
        saveAllButton?.apply {
            isEnabled = !saving
            icon = if (saving) spinnerIcon else null
            text = if (saving) {
                MyMessageBundle.message("action.saving")
            } else {
                MyMessageBundle.message("action.saveAll")
            }
        }
    }

    private fun hasSelection(): Boolean =
        artifactCheckBoxes.values.any { it.isSelected }

    private fun refreshGenerateButtonEnabled() {
        generateButton?.isEnabled = hasSelection()
    }

    fun showProgress(total: Int) {
        progressBar.apply {
            minimum = 0
            maximum = total
            value = 0
            isIndeterminate = false
            isVisible = true
        }
        progressLabel.apply {
            text = MyMessageBundle.message("progress.generating", 0, total)
            isVisible = true
        }
        revalidate()
        repaint()
    }

    fun updateProgress(completed: Int, total: Int, result: GenerationResult) {
        progressBar.value = completed
        progressLabel.text = MyMessageBundle.message("progress.generating", completed, total)

        val line = JBLabel(
            when (result) {
                is GenerationResult.Success ->
                    "${MyMessageBundle.message("status.success")} ${result.artifact.artifactType.displayName} \u2192 ${result.artifact.defaultPath}"
                is GenerationResult.Failure ->
                    "${MyMessageBundle.message("status.failed")} ${result.message}"
            },
        )
        resultContainer.add(line)
        resultContainer.isVisible = true
        revalidate()
        repaint()
    }

    fun showDone(artifacts: List<GeneratedArtifact>) {
        progressBar.isVisible = false
        lastGenerated = artifacts
        if (artifacts.isNotEmpty()) {
            progressLabel.text = MyMessageBundle.message("status.artifactsOpenedInEditor")
            saveAllButton?.isVisible = true
        } else {
            progressLabel.text = MyMessageBundle.message("status.allFailed")
            saveAllButton?.isVisible = false
        }
        revalidate()
        repaint()
    }

    private fun clearProgress() {
        progressBar.isVisible = false
        progressLabel.isVisible = false
        resultContainer.removeAll()
        resultContainer.isVisible = false
        lastGenerated = emptyList()
        saveAllButton?.isVisible = false
    }

    private fun fireSaveAll() {
        if (lastGenerated.isEmpty()) return
        onSaveAll?.invoke(lastGenerated)
    }

    private fun refreshArtifactCheckBoxes() {
        val target = targetCombo.selectedItem as? GenerationTarget ?: return
        val types = ArtifactType.forTarget(target, currentSkills)

        checkBoxContainer.removeAll()
        artifactCheckBoxes.clear()

        types.forEach { type ->
            val tooltip = when (type) {
                is ArtifactType.Skill -> "${type.description} — ${type.suggestion.reason}"
                else -> type.description
            }
            val resolvedPath = pathResolver?.invoke(target, type)
            val label = if (resolvedPath != null) {
                MyMessageBundle.message("label.artifactPath", type.displayName, resolvedPath)
            } else {
                type.displayName
            }
            val checkBox = JCheckBox(label).apply {
                toolTipText = tooltip
                isSelected = type is ArtifactType.ProjectGuidance
                addItemListener { refreshGenerateButtonEnabled() }
            }
            artifactCheckBoxes[type] = checkBox
            checkBoxContainer.add(checkBox)
        }

        checkBoxContainer.revalidate()
        checkBoxContainer.repaint()
        refreshGenerateButtonEnabled()
    }

    private fun fireGenerate() {
        val target = targetCombo.selectedItem as? GenerationTarget ?: return
        val selectedTypes = artifactCheckBoxes
            .filter { (_, cb) -> cb.isSelected }
            .map { (type, _) -> type }

        if (selectedTypes.isEmpty()) return

        val instructions = instructionsArea.text.takeIf { it.isNotBlank() }
        onGenerate?.invoke(target, selectedTypes, instructions)
    }
}
