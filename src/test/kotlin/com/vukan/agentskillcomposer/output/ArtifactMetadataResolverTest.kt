package com.vukan.agentskillcomposer.output

import com.vukan.agentskillcomposer.model.ArtifactType
import com.vukan.agentskillcomposer.model.GenerationTarget
import com.vukan.agentskillcomposer.model.SkillSuggestion
import com.vukan.agentskillcomposer.output.impl.DefaultTargetPathResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtifactMetadataResolverTest {

    private val resolver = ArtifactMetadataResolver(DefaultTargetPathResolver())

    private fun skill(
        id: String = "write-tests",
        name: String = "Write Tests",
        description: String = "Write tests following repo conventions",
    ) = ArtifactType.Skill(SkillSuggestion(id, name, description, reason = "ignored"))

    @Test
    fun `junie skill requires YAML frontmatter with name and description`() {
        val metadata = resolver.resolve(GenerationTarget.JUNIE, skill())

        assertEquals("SKILL.md", metadata.fileName)
        assertEquals(".junie/skills/write-tests/SKILL.md", metadata.relativePath)
        assertTrue(metadata.requiresFrontmatter)
        assertNotNull(metadata.frontmatterTemplate)
        val template = metadata.frontmatterTemplate!!
        assertTrue(template.startsWith("---"))
        assertTrue(template.endsWith("---"))
        assertTrue(template.contains("name: Write Tests"))
        assertTrue(template.contains("description: Write tests following repo conventions"))
    }

    @Test
    fun `claude skill does not require frontmatter`() {
        val metadata = resolver.resolve(GenerationTarget.CLAUDE, skill())

        assertFalse(metadata.requiresFrontmatter)
        assertNull(metadata.frontmatterTemplate)
    }

    @Test
    fun `project guidance never requires frontmatter`() {
        val junie = resolver.resolve(GenerationTarget.JUNIE, ArtifactType.ProjectGuidance)
        val claude = resolver.resolve(GenerationTarget.CLAUDE, ArtifactType.ProjectGuidance)

        assertFalse(junie.requiresFrontmatter)
        assertFalse(claude.requiresFrontmatter)
    }

    @Test
    fun `claude review-changes command does not require frontmatter`() {
        val metadata = resolver.resolve(GenerationTarget.CLAUDE, ArtifactType.ReviewChangesCommand)
        assertFalse(metadata.requiresFrontmatter)
        assertNull(metadata.frontmatterTemplate)
    }

    @Test
    fun `yaml-sensitive chars in skill name get quoted and escaped`() {
        val tricky = skill(name = "Write: \"safe\" tests", description = "normal")
        val metadata = resolver.resolve(GenerationTarget.JUNIE, tricky)

        assertNotNull(metadata.frontmatterTemplate)
        val template = metadata.frontmatterTemplate!!
        assertTrue(
            template.contains("name: \"Write: \\\"safe\\\" tests\""),
            "Expected quoted+escaped name, got:\n$template",
        )
    }

    @Test
    fun `plain description with no yaml-sensitive chars stays unquoted`() {
        val metadata = resolver.resolve(GenerationTarget.JUNIE, skill(description = "plain text"))
        assertNotNull(metadata.frontmatterTemplate)
        val template = metadata.frontmatterTemplate!!
        assertTrue(template.contains("description: plain text"))
    }

    @Test
    fun `fileName is derived from relative path`() {
        val skillMeta = resolver.resolve(GenerationTarget.CLAUDE, skill())
        val guidanceMeta = resolver.resolve(GenerationTarget.CLAUDE, ArtifactType.ProjectGuidance)
        val commandMeta = resolver.resolve(GenerationTarget.CLAUDE, ArtifactType.ReviewChangesCommand)

        assertEquals("SKILL.md", skillMeta.fileName)
        assertEquals("CLAUDE.md", guidanceMeta.fileName)
        assertEquals("review-changes.md", commandMeta.fileName)
    }
}
