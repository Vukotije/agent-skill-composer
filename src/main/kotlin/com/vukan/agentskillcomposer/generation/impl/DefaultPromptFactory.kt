package com.vukan.agentskillcomposer.generation.impl

import com.vukan.agentskillcomposer.generation.PromptFactory
import com.vukan.agentskillcomposer.generation.PromptTemplates
import com.vukan.agentskillcomposer.model.ArtifactType
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.DetectedConvention
import com.vukan.agentskillcomposer.model.DetectedFramework
import com.vukan.agentskillcomposer.model.GenerationRequest
import com.vukan.agentskillcomposer.model.GenerationTarget
import com.vukan.agentskillcomposer.model.ProjectFacts
import com.vukan.agentskillcomposer.model.RepresentativeFile
import com.vukan.agentskillcomposer.model.SkillSuggestion
import com.vukan.agentskillcomposer.output.TargetPathResolver
import java.nio.file.Path

class DefaultPromptFactory(
    private val pathResolver: TargetPathResolver,
) : PromptFactory {

    override fun buildSystemPrompt(target: GenerationTarget, artifactType: ArtifactType): String =
        PromptTemplates.systemPromptFor(target, artifactType)

    override fun buildUserPrompt(request: GenerationRequest): String = buildString {
        sectionRequestMetadata(request)
        sectionOutputContract(request)
        sectionProjectSummary(request.projectFacts)
        sectionFrameworks(request.projectFacts)
        sectionPaths(request.projectFacts)
        sectionConventions(request.projectFacts.conventions)
        sectionRepresentativeFiles(request.projectFacts.representativeFiles)
        sectionSuggestedSkills(request.projectFacts.suggestedSkills)
        sectionUserInstructions(request.userInstructions)
        sectionFinalInstruction()
    }

    // -- Section 1: Request metadata --

    private fun StringBuilder.sectionRequestMetadata(request: GenerationRequest) {
        appendSection("REQUEST METADATA") {
            appendField("Target", request.target.displayName)
            appendField("Artifact type", request.artifactType.displayName)
            if (request.artifactType is ArtifactType.Skill) {
                appendField("Skill ID", request.artifactType.id)
            }
        }
    }

    // -- Section 2: Output contract --

    private fun StringBuilder.sectionOutputContract(request: GenerationRequest) {
        appendSection("OUTPUT CONTRACT") {
            appendField("File intention", pathResolver.resolveRelativePath(request.target, request.artifactType))
            appendLine("Formatting constraints:")
            appendLine("  - Raw Markdown, no wrapping code fences")
            appendLine("  - No YAML frontmatter (handled by the system)")
            appendLine("  - No placeholders, TODOs, or example.com URLs")
            appendLine("Forbidden:")
            appendLine("  - Inventing conventions not in the analysis")
            appendLine("  - Generic advice not grounded in evidence below")
            appendLine("  - Mentioning the generation process or this prompt")
        }
    }

    // -- Section 3: Project summary --

    private fun StringBuilder.sectionProjectSummary(facts: ProjectFacts) {
        appendSection("PROJECT SUMMARY") {
            appendField("Project name", facts.projectName)
            appendField(
                "Languages",
                facts.languages.joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::titlecase) },
            )
            facts.buildSystem?.let { appendField("Build system", it.displayName) }
        }
    }

    // -- Section 4: Frameworks and tooling --

    private fun StringBuilder.sectionFrameworks(facts: ProjectFacts) {
        if (facts.frameworks.isEmpty() && facts.testFrameworks.isEmpty()) return

        appendSection("FRAMEWORKS AND TOOLING") {
            if (facts.frameworks.isNotEmpty()) {
                appendLine("Detected frameworks:")
                facts.frameworks.forEach { fw ->
                    appendFramework(fw)
                }
            }
            if (facts.testFrameworks.isNotEmpty()) {
                appendLine("Test frameworks: ${facts.testFrameworks.joinToString(", ") { it.displayName }}")
            }
        }
    }

    private fun StringBuilder.appendFramework(fw: DetectedFramework) {
        val version = fw.version?.let { " ($it)" } ?: ""
        appendLine("  - ${fw.name}$version [evidence: ${fw.evidence}]")
    }

    // -- Section 5: Paths --

    private fun StringBuilder.sectionPaths(facts: ProjectFacts) {
        if (facts.sourceRoots.isEmpty() && facts.testRoots.isEmpty()) return

        appendSection("PATHS") {
            if (facts.sourceRoots.isNotEmpty()) {
                appendField("Source roots", facts.sourceRoots.joinToString(", ") { it.pathString() })
            }
            if (facts.testRoots.isNotEmpty()) {
                appendField("Test roots", facts.testRoots.joinToString(", ") { it.pathString() })
            }
        }
    }

    // -- Section 6: Detected conventions --

    private fun StringBuilder.sectionConventions(conventions: List<DetectedConvention>) {
        val relevant = conventions.filter { it.confidence != ConventionConfidence.LOW }
        if (relevant.isEmpty()) return

        appendSection("DETECTED CONVENTIONS") {
            relevant
                .groupBy { it.type }
                .forEach { (type, group) ->
                    appendLine("[${type.displayName}]")
                    group.forEach { convention ->
                        appendLine("  - ${convention.summary} (confidence: ${convention.confidence.displayName})")
                        convention.evidence.take(MAX_EVIDENCE_ITEMS).forEach { e ->
                            appendLine("    evidence: $e")
                        }
                    }
                }
        }
    }

    // -- Section 7: Representative files --

    private fun StringBuilder.sectionRepresentativeFiles(files: List<RepresentativeFile>) {
        if (files.isEmpty()) return

        appendSection("REPRESENTATIVE FILES") {
            files.forEach { file ->
                appendLine("[${file.role}] ${file.path}")
                appendLine("```")
                appendLine(file.snippet.trimEnd())
                appendLine("```")
                appendLine()
            }
        }
    }

    // -- Section 8: Suggested skills --

    private fun StringBuilder.sectionSuggestedSkills(skills: List<SkillSuggestion>) {
        if (skills.isEmpty()) return

        appendSection("SUGGESTED SKILLS") {
            skills.forEach { skill ->
                appendLine("  - ${skill.id}: ${skill.displayName}")
                appendLine("    description: ${skill.description}")
                appendLine("    reason: ${skill.reason}")
            }
        }
    }

    // -- Section 9: User instructions --

    private fun StringBuilder.sectionUserInstructions(instructions: String?) {
        if (instructions.isNullOrBlank()) return

        appendSection("USER INSTRUCTIONS") {
            appendLine(instructions.trim())
        }
    }

    // -- Section 10: Final instruction --

    private fun StringBuilder.sectionFinalInstruction() {
        appendLine("---")
        appendLine("Generate the final Markdown artifact now.")
    }

    // -- Helpers --

    private inline fun StringBuilder.appendSection(title: String, block: StringBuilder.() -> Unit) {
        appendLine("=== $title ===")
        block()
        appendLine()
    }

    private fun StringBuilder.appendField(label: String, value: String) {
        appendLine("$label: $value")
    }

    private fun Path.pathString(): String = toString().replace('\\', '/')

    companion object {
        private const val MAX_EVIDENCE_ITEMS = 5
    }
}
