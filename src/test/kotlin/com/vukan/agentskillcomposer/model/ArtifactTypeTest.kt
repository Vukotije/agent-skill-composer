package com.vukan.agentskillcomposer.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtifactTypeTest {

    private val skillA = SkillSuggestion("write-tests", "Write Tests", "desc-a", "reason")
    private val skillB = SkillSuggestion("implement-service", "Implement Service", "desc-b", "reason")

    @Test
    fun `ProjectGuidance applies to both targets`() {
        assertTrue(ArtifactType.ProjectGuidance.applicableTo(GenerationTarget.JUNIE))
        assertTrue(ArtifactType.ProjectGuidance.applicableTo(GenerationTarget.CLAUDE))
    }

    @Test
    fun `Skill applies to both targets`() {
        val skill = ArtifactType.Skill(skillA)
        assertTrue(skill.applicableTo(GenerationTarget.JUNIE))
        assertTrue(skill.applicableTo(GenerationTarget.CLAUDE))
    }

    @Test
    fun `ReviewChangesCommand is Claude-only`() {
        assertFalse(ArtifactType.ReviewChangesCommand.applicableTo(GenerationTarget.JUNIE))
        assertTrue(ArtifactType.ReviewChangesCommand.applicableTo(GenerationTarget.CLAUDE))
    }

    @Test
    fun `forTarget Junie emits guidance plus skills and excludes ReviewChanges`() {
        val types = ArtifactType.forTarget(GenerationTarget.JUNIE, listOf(skillA, skillB))

        assertEquals(3, types.size)
        assertEquals(ArtifactType.ProjectGuidance, types[0])
        assertTrue(types[1] is ArtifactType.Skill)
        assertTrue(types[2] is ArtifactType.Skill)
        assertFalse(types.any { it is ArtifactType.ReviewChangesCommand })
    }

    @Test
    fun `forTarget Claude appends ReviewChangesCommand after skills`() {
        val types = ArtifactType.forTarget(GenerationTarget.CLAUDE, listOf(skillA))

        assertEquals(3, types.size)
        assertEquals(ArtifactType.ProjectGuidance, types[0])
        assertTrue(types[1] is ArtifactType.Skill)
        assertEquals(ArtifactType.ReviewChangesCommand, types[2])
    }

    @Test
    fun `forTarget with no skills still includes guidance and command as appropriate`() {
        val junie = ArtifactType.forTarget(GenerationTarget.JUNIE, emptyList())
        val claude = ArtifactType.forTarget(GenerationTarget.CLAUDE, emptyList())

        assertEquals(listOf(ArtifactType.ProjectGuidance), junie)
        assertEquals(listOf(ArtifactType.ProjectGuidance, ArtifactType.ReviewChangesCommand), claude)
    }

    @Test
    fun `Skill id displayName description delegate to suggestion`() {
        val skill = ArtifactType.Skill(skillA)
        assertEquals(skillA.id, skill.id)
        assertEquals(skillA.displayName, skill.displayName)
        assertEquals(skillA.description, skill.description)
    }
}
