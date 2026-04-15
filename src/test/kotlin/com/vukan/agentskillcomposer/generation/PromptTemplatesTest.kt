package com.vukan.agentskillcomposer.generation

import com.vukan.agentskillcomposer.model.ArtifactType
import com.vukan.agentskillcomposer.model.GenerationTarget
import com.vukan.agentskillcomposer.model.SkillSuggestion
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PromptTemplatesTest {

    private val skill = ArtifactType.Skill(
        SkillSuggestion("write-tests", "Write Tests", "desc", "reason"),
    )

    @Test
    fun `system prompt includes backbone grounding rules for every combo`() {
        val combos = listOf(
            GenerationTarget.JUNIE to ArtifactType.ProjectGuidance,
            GenerationTarget.JUNIE to skill,
            GenerationTarget.CLAUDE to ArtifactType.ProjectGuidance,
            GenerationTarget.CLAUDE to skill,
            GenerationTarget.CLAUDE to ArtifactType.ReviewChangesCommand,
        )

        combos.forEach { (target, type) ->
            val prompt = PromptTemplates.systemPromptFor(target, type)
            assertTrue(
                prompt.contains("You are generating a final, drop-in agent guidance artifact"),
                "Backbone missing for $target / ${type.displayName}",
            )
            assertTrue(
                prompt.contains("Grounding rules:"),
                "Grounding rules missing for $target / ${type.displayName}",
            )
        }
    }

    @Test
    fun `junie target overlay applied for Junie targets`() {
        val prompt = PromptTemplates.systemPromptFor(GenerationTarget.JUNIE, ArtifactType.ProjectGuidance)
        assertTrue(prompt.contains("Target: Junie (JetBrains AI agent)"))
    }

    @Test
    fun `claude target overlay applied for Claude targets`() {
        val prompt = PromptTemplates.systemPromptFor(GenerationTarget.CLAUDE, ArtifactType.ProjectGuidance)
        assertTrue(prompt.contains("Target: Claude Code (Anthropic AI agent)"))
    }

    @Test
    fun `skill overlay only included for Skill artifacts`() {
        val skillPrompt = PromptTemplates.systemPromptFor(GenerationTarget.JUNIE, skill)
        val guidancePrompt = PromptTemplates.systemPromptFor(GenerationTarget.JUNIE, ArtifactType.ProjectGuidance)

        assertTrue(skillPrompt.contains("Artifact: Reusable Skill"))
        assertTrue(guidancePrompt.contains("Artifact: Project Guidance"))
    }

    @Test
    fun `review command overlay only included for ReviewChangesCommand`() {
        val prompt = PromptTemplates.systemPromptFor(GenerationTarget.CLAUDE, ArtifactType.ReviewChangesCommand)
        assertTrue(prompt.contains("Artifact: Review Changes Command"))
    }

    @Test
    fun `review command against Junie throws`() {
        assertThrows<IllegalArgumentException> {
            PromptTemplates.systemPromptFor(GenerationTarget.JUNIE, ArtifactType.ReviewChangesCommand)
        }
    }
}
