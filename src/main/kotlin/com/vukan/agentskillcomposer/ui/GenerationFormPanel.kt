package com.vukan.agentskillcomposer.ui

import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.vukan.agentskillcomposer.MyMessageBundle
import com.vukan.agentskillcomposer.model.ArtifactType
import com.vukan.agentskillcomposer.model.GenerationTarget
import com.vukan.agentskillcomposer.model.ProjectFacts
import com.vukan.agentskillcomposer.model.SkillSuggestion
import com.vukan.agentskillcomposer.output.PathResolverFn
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JPanel

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

    var pathResolver: PathResolverFn? = null
    var onGenerate: ((GenerationTarget, List<ArtifactType>, String?) -> Unit)? = null

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
                        .align(AlignX.LEFT)
                }
            }
        }

        add(formPanel, BorderLayout.CENTER)
    }

    fun reveal(facts: ProjectFacts) {
        currentSkills = facts.suggestedSkills
        refreshArtifactCheckBoxes()
        isVisible = true
        revalidate()
        repaint()
    }

    fun clear() {
        isVisible = false
        instructionsArea.text = ""
        currentSkills = emptyList()
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
            }
            artifactCheckBoxes[type] = checkBox
            checkBoxContainer.add(checkBox)
        }

        checkBoxContainer.revalidate()
        checkBoxContainer.repaint()
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
