package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.Processor
import com.vukan.agentskillcomposer.model.RepresentativeFile
import java.nio.file.Path
import java.util.concurrent.Callable

class RepresentativeFileSelector {

    fun select(
        project: Project,
        sourceFiles: List<VirtualFile>,
        testFiles: List<VirtualFile>,
        projectRoot: Path,
    ): List<RepresentativeFile> = ReadAction.nonBlocking(Callable {
        val scope = GlobalSearchScope.projectScope(project)
        val facade = JavaPsiFacade.getInstance(project)
        val allFiles = sourceFiles + testFiles

        ROLES.mapNotNull { role ->
            val file = findByAnnotation(role, facade, scope)
                ?: findTestByAnnotation(role, facade, scope)
                ?: findByName(role, allFiles)
            file?.let { readAsRepresentative(it, role.label, projectRoot) }
        }.take(MAX_FILES)
    }).inSmartMode(project).executeSynchronously()

    /**
     * Primary strategy: find a class annotated with one of the role's annotations.
     * Tries annotations in priority order.
     */
    private fun findByAnnotation(role: FileRole, facade: JavaPsiFacade, scope: GlobalSearchScope): VirtualFile? {
        for (fqn in role.annotationFqns) {
            val annotationClass = facade.findClass(fqn, scope) ?: continue
            var result: VirtualFile? = null
            AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope).forEach(Processor { psiClass ->
                result = psiClass.containingFile?.virtualFile
                result == null
            })
            if (result != null) return result
        }
        return null
    }

    /**
     * For test roles: find a class containing @Test-annotated methods.
     */
    private fun findTestByAnnotation(role: FileRole, facade: JavaPsiFacade, scope: GlobalSearchScope): VirtualFile? {
        if (!role.isTestRole) return null

        for (fqn in TEST_ANNOTATIONS) {
            val testAnnotation = facade.findClass(fqn, scope) ?: continue
            var result: VirtualFile? = null
            AnnotatedElementsSearch.searchPsiMembers(testAnnotation, scope).forEach(Processor { member ->
                if (member is PsiMethod) {
                    result = member.containingFile?.virtualFile
                }
                result == null
            })
            if (result != null) return result
        }
        return null
    }

    /**
     * Fallback strategy: match by filename suffix pattern.
     */
    private fun findByName(role: FileRole, allFiles: List<VirtualFile>): VirtualFile? =
        allFiles.firstOrNull { role.namePattern.matches(it.nameWithoutExtension) }

    private fun readAsRepresentative(file: VirtualFile, role: String, projectRoot: Path): RepresentativeFile? {
        val snippet = VfsUtilCore.loadText(file)
            .lineSequence()
            .take(MAX_SNIPPET_LINES)
            .joinToString("\n")

        if (snippet.isBlank()) return null

        val absolutePath = file.toNioPath()
        val relativePath = if (absolutePath.startsWith(projectRoot)) {
            projectRoot.relativize(absolutePath)
        } else {
            absolutePath
        }

        return RepresentativeFile(path = relativePath, role = role, snippet = snippet)
    }

    private data class FileRole(
        val label: String,
        val annotationFqns: List<String>,
        val namePattern: Regex,
        val isTestRole: Boolean = false,
    )

    companion object {
        private const val MAX_FILES = 6
        private const val MAX_SNIPPET_LINES = 60

        private val TEST_ANNOTATIONS = listOf(
            "org.junit.jupiter.api.Test",
            "org.junit.Test",
        )

        private val ROLES = listOf(
            FileRole(
                "Service class",
                listOf(
                    "org.springframework.stereotype.Service",
                    "io.micronaut.context.annotation.Bean",
                    "jakarta.inject.Singleton",
                    "javax.inject.Singleton",
                ),
                Regex(".+Service"),
            ),
            FileRole(
                "Controller",
                listOf(
                    "org.springframework.web.bind.annotation.RestController",
                    "org.springframework.stereotype.Controller",
                    "io.micronaut.http.annotation.Controller",
                    "jakarta.ws.rs.Path",
                    "javax.ws.rs.Path",
                ),
                Regex(".+Controller"),
            ),
            FileRole(
                "Repository",
                listOf(
                    "org.springframework.stereotype.Repository",
                    "io.micronaut.data.annotation.Repository",
                ),
                Regex(".+Repository"),
            ),
            FileRole(
                "Entity",
                listOf(
                    "jakarta.persistence.Entity",
                    "javax.persistence.Entity",
                ),
                Regex(".+Entity"),
            ),
            FileRole(
                "Test class",
                annotationFqns = emptyList(),
                namePattern = Regex(".+Test"),
                isTestRole = true,
            ),
            FileRole(
                "Configuration",
                listOf(
                    "org.springframework.context.annotation.Configuration",
                    "io.micronaut.context.annotation.Factory",
                ),
                Regex(".+(Config|Configuration)"),
            ),
        )
    }
}
