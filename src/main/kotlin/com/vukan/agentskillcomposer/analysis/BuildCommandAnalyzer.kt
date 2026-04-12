package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.vfs.VirtualFile
import com.vukan.agentskillcomposer.model.BuildSystem
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.ConventionType
import com.vukan.agentskillcomposer.model.DetectedConvention
import com.vukan.agentskillcomposer.model.DetectedFramework

/**
 * Infers build, test, and run commands from the project structure.
 * Every useful CLAUDE.md / AGENTS.md starts with "how to build and test."
 */
class BuildCommandAnalyzer {

    fun analyze(
        projectBaseDir: VirtualFile,
        buildSystem: BuildSystem?,
        frameworks: List<DetectedFramework>,
    ): DetectedConvention? {
        if (buildSystem == null) return null

        val wrapper = detectWrapper(projectBaseDir, buildSystem)
        val commands = inferCommands(wrapper, buildSystem, frameworks)

        if (commands.isEmpty()) return null

        return DetectedConvention(
            type = ConventionType.BUILD_TOOLING,
            summary = "Build: ${commands.first()}",
            confidence = ConventionConfidence.HIGH,
            evidence = commands,
        )
    }

    private fun detectWrapper(projectBaseDir: VirtualFile, buildSystem: BuildSystem): String = when (buildSystem) {
        BuildSystem.GRADLE_KOTLIN, BuildSystem.GRADLE_GROOVY -> {
            val hasWrapper = projectBaseDir.findChild("gradlew") != null
            if (hasWrapper) "./gradlew" else "gradle"
        }
        BuildSystem.MAVEN -> {
            val hasWrapper = projectBaseDir.findChild("mvnw") != null
            if (hasWrapper) "./mvnw" else "mvn"
        }
    }

    private fun inferCommands(
        wrapper: String,
        buildSystem: BuildSystem,
        frameworks: List<DetectedFramework>,
    ): List<String> {
        val commands = mutableListOf<String>()
        val isGradle = buildSystem == BuildSystem.GRADLE_KOTLIN || buildSystem == BuildSystem.GRADLE_GROOVY
        val hasSpringBoot = frameworks.any { it.name == "Spring Boot" }
        val hasKtor = frameworks.any { it.name == "Ktor" }

        if (isGradle) {
            commands += "Build: $wrapper build"
            commands += "Test: $wrapper test"
            when {
                hasSpringBoot -> commands += "Run: $wrapper bootRun"
                hasKtor -> commands += "Run: $wrapper run"
            }
            commands += "Clean: $wrapper clean"
        } else {
            commands += "Build: $wrapper package"
            commands += "Test: $wrapper test"
            if (hasSpringBoot) {
                commands += "Run: $wrapper spring-boot:run"
            }
            commands += "Clean: $wrapper clean"
        }

        return commands
    }
}
