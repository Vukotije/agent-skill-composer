package com.vukan.agentskillcomposer.generation

import com.vukan.agentskillcomposer.generation.impl.DefaultPromptFactory
import com.vukan.agentskillcomposer.model.ArtifactType
import com.vukan.agentskillcomposer.model.BuildSystem
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.ConventionType
import com.vukan.agentskillcomposer.model.DetectedConvention
import com.vukan.agentskillcomposer.model.DetectedFramework
import com.vukan.agentskillcomposer.model.GenerationRequest
import com.vukan.agentskillcomposer.model.GenerationTarget
import com.vukan.agentskillcomposer.model.ProjectFacts
import com.vukan.agentskillcomposer.model.ProjectLanguage
import com.vukan.agentskillcomposer.model.RepresentativeFile
import com.vukan.agentskillcomposer.model.SkillSuggestion
import com.vukan.agentskillcomposer.model.TestFramework
import com.vukan.agentskillcomposer.output.impl.DefaultTargetPathResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class DefaultPromptFactoryTest {

    private val factory = DefaultPromptFactory(DefaultTargetPathResolver())

    private val skill = SkillSuggestion(
        id = "write-tests",
        displayName = "Write Tests",
        description = "Write tests following repo conventions",
        reason = "JUnit 5 detected",
    )

    private fun facts(
        conventions: List<DetectedConvention> = emptyList(),
        frameworks: List<DetectedFramework> = emptyList(),
        testFrameworks: List<TestFramework> = emptyList(),
        sourceRoots: List<Path> = emptyList(),
        testRoots: List<Path> = emptyList(),
        representativeFiles: List<RepresentativeFile> = emptyList(),
        suggestedSkills: List<SkillSuggestion> = emptyList(),
    ) = ProjectFacts(
        projectRoot = Path.of("/tmp/demo-repo"),
        languages = setOf(ProjectLanguage.KOTLIN),
        buildSystem = BuildSystem.GRADLE_KOTLIN,
        frameworks = frameworks,
        testFrameworks = testFrameworks,
        sourceRoots = sourceRoots,
        testRoots = testRoots,
        conventions = conventions,
        representativeFiles = representativeFiles,
        suggestedSkills = suggestedSkills,
    )

    @Test
    fun `user prompt contains project summary fields`() {
        val prompt = factory.buildUserPrompt(
            GenerationRequest(
                target = GenerationTarget.CLAUDE,
                artifactType = ArtifactType.ProjectGuidance,
                projectFacts = facts(),
            ),
        )

        assertTrue(prompt.contains("=== PROJECT SUMMARY ==="))
        assertTrue(prompt.contains("Project name: demo-repo"))
        assertTrue(prompt.contains("Languages: Kotlin"))
        assertTrue(prompt.contains("Build system: Gradle (Kotlin DSL)"))
    }

    @Test
    fun `resolved file intention matches TargetPathResolver`() {
        val junie = factory.buildUserPrompt(
            GenerationRequest(GenerationTarget.JUNIE, ArtifactType.ProjectGuidance, facts()),
        )
        val claude = factory.buildUserPrompt(
            GenerationRequest(GenerationTarget.CLAUDE, ArtifactType.ProjectGuidance, facts()),
        )

        assertTrue(junie.contains("File intention: .junie/AGENTS.md"))
        assertTrue(claude.contains("File intention: CLAUDE.md"))
    }

    @Test
    fun `frameworks section renders name version and evidence`() {
        val prompt = factory.buildUserPrompt(
            GenerationRequest(
                target = GenerationTarget.CLAUDE,
                artifactType = ArtifactType.ProjectGuidance,
                projectFacts = facts(
                    frameworks = listOf(DetectedFramework("Spring Boot", "3.2.0", "library: spring-boot-starter")),
                    testFrameworks = listOf(TestFramework.JUNIT5),
                ),
            ),
        )

        assertTrue(prompt.contains("=== FRAMEWORKS AND TOOLING ==="))
        assertTrue(prompt.contains("Spring Boot (3.2.0)"))
        assertTrue(prompt.contains("[evidence: library: spring-boot-starter]"))
        assertTrue(prompt.contains("Test frameworks: JUnit 5"))
    }

    @Test
    fun `frameworks section omitted when both lists empty`() {
        val prompt = factory.buildUserPrompt(
            GenerationRequest(GenerationTarget.CLAUDE, ArtifactType.ProjectGuidance, facts()),
        )
        assertFalse(prompt.contains("=== FRAMEWORKS AND TOOLING ==="))
    }

    @Test
    fun `low-confidence conventions are filtered out`() {
        val prompt = factory.buildUserPrompt(
            GenerationRequest(
                target = GenerationTarget.CLAUDE,
                artifactType = ArtifactType.ProjectGuidance,
                projectFacts = facts(
                    conventions = listOf(
                        DetectedConvention(ConventionType.NAMING, "Service suffix", ConventionConfidence.HIGH, listOf("UserService.kt")),
                        DetectedConvention(ConventionType.NAMING, "Speculative pattern", ConventionConfidence.LOW, listOf("x")),
                    ),
                ),
            ),
        )

        assertTrue(prompt.contains("Service suffix"))
        assertFalse(prompt.contains("Speculative pattern"))
    }

    @Test
    fun `evidence items capped at five per convention`() {
        val manyEvidence = (1..10).map { "file$it.kt" }
        val prompt = factory.buildUserPrompt(
            GenerationRequest(
                target = GenerationTarget.CLAUDE,
                artifactType = ArtifactType.ProjectGuidance,
                projectFacts = facts(
                    conventions = listOf(
                        DetectedConvention(ConventionType.NAMING, "pattern", ConventionConfidence.HIGH, manyEvidence),
                    ),
                ),
            ),
        )

        val evidenceLines = prompt.lines().count { it.trim().startsWith("evidence:") }
        assertEquals(5, evidenceLines)
    }

    @Test
    fun `suggested skills section renders id name description and reason`() {
        val prompt = factory.buildUserPrompt(
            GenerationRequest(
                target = GenerationTarget.CLAUDE,
                artifactType = ArtifactType.ProjectGuidance,
                projectFacts = facts(suggestedSkills = listOf(skill)),
            ),
        )

        assertTrue(prompt.contains("=== SUGGESTED SKILLS ==="))
        assertTrue(prompt.contains("write-tests: Write Tests"))
        assertTrue(prompt.contains("description: Write tests following repo conventions"))
        assertTrue(prompt.contains("reason: JUnit 5 detected"))
    }

    @Test
    fun `user instructions section omitted when blank`() {
        val blank = factory.buildUserPrompt(
            GenerationRequest(
                target = GenerationTarget.CLAUDE,
                artifactType = ArtifactType.ProjectGuidance,
                projectFacts = facts(),
                userInstructions = "   ",
            ),
        )
        assertFalse(blank.contains("=== USER INSTRUCTIONS ==="))

        val withInstructions = factory.buildUserPrompt(
            GenerationRequest(
                target = GenerationTarget.CLAUDE,
                artifactType = ArtifactType.ProjectGuidance,
                projectFacts = facts(),
                userInstructions = "  be concise  ",
            ),
        )
        assertTrue(withInstructions.contains("=== USER INSTRUCTIONS ==="))
        assertTrue(withInstructions.contains("be concise"))
    }

    @Test
    fun `skill request includes skill id in metadata section`() {
        val prompt = factory.buildUserPrompt(
            GenerationRequest(
                target = GenerationTarget.JUNIE,
                artifactType = ArtifactType.Skill(skill),
                projectFacts = facts(),
            ),
        )
        assertTrue(prompt.contains("Skill ID: write-tests"))
        assertTrue(prompt.contains("File intention: .junie/skills/write-tests/SKILL.md"))
    }

    @Test
    fun `paths section omitted when no roots`() {
        val prompt = factory.buildUserPrompt(
            GenerationRequest(GenerationTarget.CLAUDE, ArtifactType.ProjectGuidance, facts()),
        )
        assertFalse(prompt.contains("=== PATHS ==="))
    }

    @Test
    fun `paths section renders source and test roots when present`() {
        val prompt = factory.buildUserPrompt(
            GenerationRequest(
                target = GenerationTarget.CLAUDE,
                artifactType = ArtifactType.ProjectGuidance,
                projectFacts = facts(
                    sourceRoots = listOf(Path.of("src/main/kotlin")),
                    testRoots = listOf(Path.of("src/test/kotlin")),
                ),
            ),
        )
        assertTrue(prompt.contains("=== PATHS ==="))
        assertTrue(prompt.contains("Source roots: src/main/kotlin"))
        assertTrue(prompt.contains("Test roots: src/test/kotlin"))
    }

    @Test
    fun `final instruction line always emitted`() {
        val prompt = factory.buildUserPrompt(
            GenerationRequest(GenerationTarget.CLAUDE, ArtifactType.ProjectGuidance, facts()),
        )
        assertTrue(prompt.trimEnd().endsWith("Generate the final Markdown artifact now."))
    }
}
