package com.vukan.agentskillcomposer.generation

import com.vukan.agentskillcomposer.model.ArtifactType
import com.vukan.agentskillcomposer.model.GenerationTarget

object PromptTemplates {

    fun systemPromptFor(target: GenerationTarget, artifactType: ArtifactType): String =
        buildString {
            append(BACKBONE)
            appendLine()
            appendLine()
            append(targetOverlay(target))
            appendLine()
            appendLine()
            append(artifactOverlay(target, artifactType))
        }

    // -- Shared backbone --

    private val BACKBONE = """
        You are generating a final, drop-in agent guidance artifact for a Kotlin/Java repository.
        You will receive structured repository analysis data in the user message.

        Grounding rules:
        - Use ONLY the supplied analysis data and optional user instructions.
        - Do not invent conventions, workflows, commands, architecture, or project goals unsupported by the input.
        - If evidence is weak or missing for a claim, omit it or qualify it with "Analysis suggests...".
        - Prefer repository-specific instructions over generic best practices.
        - Reuse representative code examples when they make guidance concrete.
        - Never output filler like "follow best practices", "write clean code", or "ensure scalability" unless tied to actual evidence.

        Output rules:
        - Output raw Markdown only.
        - Do not wrap the output in code fences.
        - Do not mention the prompt, the analyzer, or the generation process.
        - Do not output placeholders like TODO, "replace me", "example.com", or "add your command here".
        - Produce exactly one artifact.
    """.trimIndent()

    // -- Target overlays --

    private fun targetOverlay(target: GenerationTarget): String = when (target) {
        GenerationTarget.JUNIE -> JUNIE_OVERLAY
        GenerationTarget.CLAUDE -> CLAUDE_OVERLAY
    }

    private val JUNIE_OVERLAY = """
        Target: Junie (JetBrains AI agent).
        Tone: direct, operational, instruction-oriented.
        Do not include YAML frontmatter — the system prepends it automatically for skills.
    """.trimIndent()

    private val CLAUDE_OVERLAY = """
        Target: Claude Code (Anthropic AI agent).
        Tone: concise project memory and instructions. Claude Code reads this file on every interaction, so brevity matters.
        No YAML frontmatter.
    """.trimIndent()

    // -- Artifact overlays --

    private fun artifactOverlay(target: GenerationTarget, artifactType: ArtifactType): String =
        when (artifactType) {
            is ArtifactType.ProjectGuidance -> PROJECT_GUIDANCE_OVERLAY
            is ArtifactType.Skill -> SKILL_OVERLAY
            is ArtifactType.ReviewChangesCommand -> {
                require(target == GenerationTarget.CLAUDE) {
                    "ReviewChangesCommand is not applicable to ${target.displayName}"
                }
                REVIEW_COMMAND_OVERLAY
            }
        }

    private val PROJECT_GUIDANCE_OVERLAY = """
        Artifact: Project Guidance.
        Section expectations:
        - Describe what the project is, only if supported by evidence.
        - Summarize stack, frameworks, test setup, important source/test locations.
        - List key conventions with concrete examples.
        - Include build/test commands when inferable from analysis.
        - Include practical agent rules ("when writing code" sections).
        - Do not include long explanations or onboarding prose.
        - Focus on information that helps an agent act correctly in this repository.
    """.trimIndent()

    private val SKILL_OVERLAY = """
        Artifact: Reusable Skill.
        Section expectations:
        - Include a "When to use" section scoping when this skill applies.
        - Include step-by-step instructions referencing actual project conventions.
        - Include at least one concrete code example drawn from representative files.
        - Include a validation checklist the agent can verify against.
        - Keep the skill tightly scoped to one task.
    """.trimIndent()

    private val REVIEW_COMMAND_OVERLAY = """
        Artifact: Review Changes Command.
        This file instructs Claude Code to review a git diff against project conventions.
        Section expectations:
        - Emphasize correctness and consistency with detected repository patterns.
        - Each check must reference a specific detected convention or representative example.
        - Tell the agent to cite concrete violations, not just flag general concerns.
        - Include checks for test coverage, naming, and regression risks grounded in the analysis.
        - Do not produce a generic code review rubric unless no repository evidence exists.
    """.trimIndent()
}
