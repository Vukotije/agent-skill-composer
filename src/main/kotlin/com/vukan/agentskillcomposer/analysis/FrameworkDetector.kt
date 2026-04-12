package com.vukan.agentskillcomposer.analysis

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.Processor
import com.vukan.agentskillcomposer.model.DetectedFramework
import java.util.concurrent.Callable

class FrameworkDetector {

    fun supplement(
        project: Project,
        existingFrameworks: List<DetectedFramework>,
    ): List<DetectedFramework> {
        val supplemental = ReadAction.nonBlocking(Callable {
            val results = mutableListOf<DetectedFramework>()

            results += detectPrimaryFramework(project)
            results += detectFromConfigFiles(project)
            results += detectFromFacets(project)

            results
        }).inSmartMode(project).executeSynchronously()

        val existingNames = existingFrameworks.map { it.name.lowercase() }.toSet()
        return supplemental
            .filter { fw -> existingNames.none { it.contains(fw.name.lowercase()) || fw.name.lowercase().contains(it) } }
            .distinctBy { it.name }
    }

    /**
     * Finds the primary application framework by checking for entry point annotations.
     * This tells us which framework is actually driving the app, not just on the classpath.
     */
    private fun detectPrimaryFramework(project: Project): List<DetectedFramework> {
        val scope = GlobalSearchScope.projectScope(project)
        val facade = JavaPsiFacade.getInstance(project)
        val results = mutableListOf<DetectedFramework>()

        for ((annotationFqn, frameworkName) in ENTRY_POINT_ANNOTATIONS) {
            val annotation = facade.findClass(annotationFqn, scope) ?: continue
            var found = false
            AnnotatedElementsSearch.searchPsiClasses(annotation, scope).forEach(Processor {
                found = true
                false // stop after first
            })
            if (found) {
                results += DetectedFramework(frameworkName, null, "entry point: @${annotationFqn.substringAfterLast('.')}")
            }
        }

        return results
    }

    /**
     * Checks for framework-specific configuration files in the project.
     */
    private fun detectFromConfigFiles(project: Project): List<DetectedFramework> {
        val projectDir = project.guessProjectDir() ?: return emptyList()
        val results = mutableListOf<DetectedFramework>()

        for ((paths, frameworkName) in CONFIG_FILE_MAPPING) {
            val found = paths.any { path -> resolveFile(projectDir, path) != null }
            if (found) {
                results += DetectedFramework(frameworkName, null, "config file: ${paths.first()}")
            }
        }

        return results
    }

    private fun resolveFile(root: com.intellij.openapi.vfs.VirtualFile, relativePath: String): com.intellij.openapi.vfs.VirtualFile? {
        var current = root
        for (segment in relativePath.split("/")) {
            current = current.findChild(segment) ?: return null
        }
        return current
    }

    /**
     * Checks IDE-detected facets as a tertiary signal.
     */
    private fun detectFromFacets(project: Project): List<DetectedFramework> {
        val results = mutableListOf<DetectedFramework>()

        for (module in ModuleManager.getInstance(project).modules) {
            for (facet in FacetManager.getInstance(module).allFacets) {
                val name = FACET_MAPPING[facet.type.stringId] ?: continue
                results += DetectedFramework(name, null, "IDE facet: ${facet.name}")
            }
        }

        return results.distinctBy { it.name }
    }

    companion object {
        private val ENTRY_POINT_ANNOTATIONS = listOf(
            "org.springframework.boot.autoconfigure.SpringBootApplication" to "Spring Boot",
            "io.micronaut.runtime.Micronaut" to "Micronaut",
            "io.quarkus.runtime.annotations.QuarkusMain" to "Quarkus",
        )

        private val CONFIG_FILE_MAPPING = listOf(
            listOf("src/main/resources/application.yml", "src/main/resources/application.yaml", "src/main/resources/application.properties") to "Spring",
            listOf("src/main/resources/application.conf", "src/main/resources/application.hocon") to "Ktor",
            listOf("src/main/resources/application-micronaut.yml", "micronaut-cli.yml") to "Micronaut",
        )

        private val FACET_MAPPING = mapOf(
            "Spring" to "Spring",
            "jpa" to "JPA",
            "web" to "Java EE Web",
        )
    }
}
