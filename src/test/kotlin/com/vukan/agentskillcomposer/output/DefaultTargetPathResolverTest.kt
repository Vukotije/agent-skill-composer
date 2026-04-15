package com.vukan.agentskillcomposer.output

import com.vukan.agentskillcomposer.model.ArtifactType
import com.vukan.agentskillcomposer.model.GenerationTarget
import com.vukan.agentskillcomposer.model.SkillSuggestion
import com.vukan.agentskillcomposer.output.impl.DefaultTargetPathResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path

class DefaultTargetPathResolverTest {

    private val resolver = DefaultTargetPathResolver()

    private val sampleSkill = ArtifactType.Skill(
        SkillSuggestion(
            id = "write-tests",
            displayName = "Write Tests",
            description = "Write tests following repo conventions",
            reason = "JUnit 5 detected",
        ),
    )

    @Test
    fun `junie project guidance maps to dot-junie agents md`() {
        assertEquals(
            ".junie/AGENTS.md",
            resolver.resolveRelativePath(GenerationTarget.JUNIE, ArtifactType.ProjectGuidance),
        )
    }

    @Test
    fun `junie skill path includes skill id`() {
        assertEquals(
            ".junie/skills/write-tests/SKILL.md",
            resolver.resolveRelativePath(GenerationTarget.JUNIE, sampleSkill),
        )
    }

    @Test
    fun `claude project guidance maps to CLAUDE md at root`() {
        assertEquals(
            "CLAUDE.md",
            resolver.resolveRelativePath(GenerationTarget.CLAUDE, ArtifactType.ProjectGuidance),
        )
    }

    @Test
    fun `claude skill path includes skill id`() {
        assertEquals(
            ".claude/skills/write-tests/SKILL.md",
            resolver.resolveRelativePath(GenerationTarget.CLAUDE, sampleSkill),
        )
    }

    @Test
    fun `claude review-changes command maps to dot-claude commands`() {
        assertEquals(
            ".claude/commands/review-changes.md",
            resolver.resolveRelativePath(GenerationTarget.CLAUDE, ArtifactType.ReviewChangesCommand),
        )
    }

    @Test
    fun `junie rejects review-changes command via require guard`() {
        assertThrows<IllegalArgumentException> {
            resolver.resolveRelativePath(GenerationTarget.JUNIE, ArtifactType.ReviewChangesCommand)
        }
    }

    @Test
    fun `resolveOutputPath resolves relative path against project root`() {
        val root = Path.of("/tmp/proj")
        val absolute = resolver.resolveOutputPath(root, GenerationTarget.CLAUDE, ArtifactType.ProjectGuidance)
        assertEquals(root.resolve("CLAUDE.md"), absolute)
    }

    @Test
    fun `skill ids with hyphens survive path segments`() {
        val hyphenated = ArtifactType.Skill(
            SkillSuggestion("implement-service", "Implement Service", "desc", "reason"),
        )
        assertEquals(
            ".junie/skills/implement-service/SKILL.md",
            resolver.resolveRelativePath(GenerationTarget.JUNIE, hyphenated),
        )
    }
}
