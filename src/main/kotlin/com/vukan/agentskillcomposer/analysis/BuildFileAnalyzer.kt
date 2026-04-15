package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.vukan.agentskillcomposer.model.BuildSystem
import com.vukan.agentskillcomposer.model.DetectedFramework
import com.vukan.agentskillcomposer.model.TestFramework
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.Callable

class BuildFileAnalyzer {

    fun detectBuildSystem(projectBaseDir: VirtualFile): BuildSystem? = when {
        projectBaseDir.findChild("build.gradle.kts") != null -> BuildSystem.GRADLE_KOTLIN
        projectBaseDir.findChild("build.gradle") != null -> BuildSystem.GRADLE_GROOVY
        projectBaseDir.findChild("pom.xml") != null -> BuildSystem.MAVEN
        else -> null
    }

    fun extractBuildClues(project: Project, buildSystem: BuildSystem): BuildClues {
        val libraryCoordinates = ReadAction.nonBlocking(Callable {
            collectLibraryCoordinates(project)
        }).inSmartMode(project).executeSynchronously()

        val frameworks = detectFrameworks(libraryCoordinates)
        val testFrameworks = detectTestFrameworks(libraryCoordinates)

        return BuildClues(
            buildSystem = buildSystem,
            rawDependencies = libraryCoordinates.map { it.raw },
            detectedFrameworks = frameworks,
            detectedTestFrameworks = testFrameworks,
        )
    }

    private fun collectLibraryCoordinates(project: Project): List<LibCoord> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<LibCoord>()

        for (module in ModuleManager.getInstance(project).modules) {
            for (entry in ModuleRootManager.getInstance(module).orderEntries) {
                if (entry !is LibraryOrderEntry) continue
                val name = entry.libraryName ?: continue
                if (!seen.add(name)) continue
                parseCoordinate(name)?.let { result += it }
            }
        }

        return result
    }

    @VisibleForTesting
    internal fun parseCoordinate(libraryName: String): LibCoord? {
        // Library names may have prefixes: "Gradle: group:artifact:version"
        // or "Maven: group:artifact:version" or just "group:artifact:version"
        // May also have a classifier: "group:artifact:version:classifier"
        val coordinate = libraryName
            .substringAfter(": ", libraryName) // strip "Gradle: " / "Maven: " prefix, keep original if no prefix
            .trim()
        val parts = coordinate.split(":")
        if (parts.size < 2) return null
        // Skip entries that don't look like Maven coordinates (e.g., module library names)
        if (!parts[0].contains(".") && parts.size == 2) return null
        return LibCoord(
            group = parts[0],
            artifact = parts[1],
            version = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
            raw = libraryName,
        )
    }

    @VisibleForTesting
    internal fun detectFrameworks(coords: List<LibCoord>): List<DetectedFramework> {
        val frameworks = mutableListOf<DetectedFramework>()

        val springBootCoord = coords.firstOrNull { it.group == "org.springframework.boot" && it.artifact.startsWith("spring-boot-starter") }
        if (springBootCoord != null) {
            frameworks += DetectedFramework("Spring Boot", springBootCoord.version, "library: ${springBootCoord.raw}")
        }

        for ((matcher, name) in FRAMEWORK_MAPPING) {
            val match = coords.firstOrNull { matcher(it) } ?: continue
            frameworks += DetectedFramework(name, match.version, "library: ${match.raw}")
        }

        return frameworks.distinctBy { it.name }
    }

    @VisibleForTesting
    internal fun detectTestFrameworks(coords: List<LibCoord>): List<TestFramework> = buildList {
        if (coords.any { it.group == "org.junit.jupiter" || it.artifact.contains("junit-jupiter") }) {
            add(TestFramework.JUNIT5)
        }
        if (coords.any { it.group == "junit" && it.artifact == "junit" }) {
            add(TestFramework.JUNIT4)
        }
        if (coords.any { it.group == "org.testng" }) {
            add(TestFramework.TESTNG)
        }
        if (coords.any { it.group == "io.kotest" }) {
            add(TestFramework.KOTEST)
        }
    }

    @VisibleForTesting
    internal data class LibCoord(
        val group: String,
        val artifact: String,
        val version: String?,
        val raw: String,
    )

    companion object {
        private val FRAMEWORK_MAPPING: List<Pair<(LibCoord) -> Boolean, String>> = listOf(
            { c: LibCoord -> c.artifact == "spring-boot-starter-web" } to "Spring Web",
            { c: LibCoord -> c.artifact == "spring-boot-starter-webflux" } to "Spring WebFlux",
            { c: LibCoord -> c.artifact == "spring-boot-starter-data-jpa" } to "Spring Data JPA",
            { c: LibCoord -> c.artifact == "spring-boot-starter-data-mongodb" } to "Spring Data MongoDB",
            { c: LibCoord -> c.artifact == "spring-boot-starter-data-r2dbc" } to "Spring Data R2DBC",
            { c: LibCoord -> c.artifact == "spring-boot-starter-security" } to "Spring Security",
            { c: LibCoord -> c.artifact == "spring-boot-starter-actuator" } to "Spring Actuator",
            { c: LibCoord -> c.group == "io.ktor" } to "Ktor",
            { c: LibCoord -> c.group == "io.micronaut" } to "Micronaut",
            { c: LibCoord -> c.group == "jakarta.persistence" || c.group == "javax.persistence" } to "JPA",
            { c: LibCoord -> c.group == "org.hibernate" || c.group == "org.hibernate.orm" } to "Hibernate",
            { c: LibCoord -> c.group == "org.jetbrains.exposed" } to "Exposed",
            { c: LibCoord -> c.group == "org.jetbrains.kotlinx" && c.artifact.startsWith("kotlinx-coroutines") } to "Kotlin Coroutines",
            { c: LibCoord -> c.group == "org.jetbrains.kotlinx" && c.artifact.startsWith("kotlinx-serialization") } to "Kotlin Serialization",
            { c: LibCoord -> c.group == "com.fasterxml.jackson.core" } to "Jackson",
        )
    }
}
