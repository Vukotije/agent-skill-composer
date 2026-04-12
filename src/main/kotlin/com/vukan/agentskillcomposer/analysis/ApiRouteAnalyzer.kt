package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.Processor
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.ConventionType
import com.vukan.agentskillcomposer.model.DetectedConvention
import java.util.concurrent.Callable

/**
 * Extracts actual API endpoint URL paths from controller annotations.
 * Not just "uses @GetMapping" but "/owners/{ownerId}/pets/{petId}".
 * This gives the AI the actual API surface map.
 */
class ApiRouteAnalyzer {

    fun analyze(project: Project): DetectedConvention? =
        ReadAction.nonBlocking(Callable {
            val scope = GlobalSearchScope.projectScope(project)
            val facade = JavaPsiFacade.getInstance(project)
            extractRoutes(facade, scope)
        }).inSmartMode(project).executeSynchronously()

    private fun extractRoutes(facade: JavaPsiFacade, scope: GlobalSearchScope): DetectedConvention? {
        val controllers = mutableListOf<PsiClass>()
        for (fqn in CONTROLLER_ANNOTATIONS) {
            val annotation = facade.findClass(fqn, scope) ?: continue
            AnnotatedElementsSearch.searchPsiClasses(annotation, scope).forEach(Processor { psiClass ->
                controllers += psiClass
                controllers.size < MAX_CONTROLLERS
            })
        }

        if (controllers.isEmpty()) return null

        val routes = mutableListOf<String>()

        for (controller in controllers) {
            val classPath = extractPath(controller.getAnnotation("org.springframework.web.bind.annotation.RequestMapping"))
                ?: ""

            for (method in controller.methods) {
                for ((annotationFqn, httpMethod) in METHOD_ANNOTATIONS) {
                    val annotation = method.getAnnotation(annotationFqn) ?: continue
                    val methodPath = extractPath(annotation) ?: ""
                    val fullPath = combinePaths(classPath, methodPath)
                    if (fullPath.isNotEmpty()) {
                        routes += "$httpMethod $fullPath"
                    }
                }
            }
        }

        if (routes.isEmpty()) return null

        // Detect URL patterns
        val pathSegments = routes.map { it.substringAfter(" ") }
        val hasPathVariables = pathSegments.any { it.contains("{") }
        val commonPrefix = findCommonPrefix(pathSegments)

        val evidence = routes.sorted().take(MAX_ROUTES)

        val summary = buildString {
            append("${routes.size} endpoints")
            if (commonPrefix.isNotEmpty()) append(" under $commonPrefix")
            if (hasPathVariables) append(", REST-style path variables")
        }

        return DetectedConvention(
            type = ConventionType.API_ROUTES,
            summary = summary,
            confidence = if (routes.size >= 3) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
            evidence = evidence,
        )
    }

    private fun extractPath(annotation: PsiAnnotation?): String? {
        if (annotation == null) return null

        // Try "value" attribute first, then "path"
        val value = annotation.findAttributeValue("value")
            ?: annotation.findAttributeValue("path")
            ?: annotation.findAttributeValue(null) // default attribute
            ?: return null

        return when (value) {
            is PsiLiteralExpression -> value.value as? String
            else -> {
                // Handle array initializers like @RequestMapping({"/a", "/b"}) — take first
                val text = value.text
                    .removePrefix("{").removeSuffix("}")
                    .split(",")
                    .firstOrNull()
                    ?.trim()
                    ?.removeSurrounding("\"")
                text?.takeIf { it.isNotBlank() }
            }
        }
    }

    private fun combinePaths(classPath: String, methodPath: String): String {
        val base = classPath.trimEnd('/')
        val suffix = methodPath.trimStart('/')
        return when {
            base.isEmpty() && suffix.isEmpty() -> ""
            base.isEmpty() -> "/$suffix"
            suffix.isEmpty() -> base
            else -> "$base/$suffix"
        }
    }

    private fun findCommonPrefix(paths: List<String>): String {
        if (paths.size < 2) return ""
        val segments = paths.map { it.split("/").filter { s -> s.isNotEmpty() && !s.startsWith("{") } }
        if (segments.any { it.isEmpty() }) return ""

        val prefix = mutableListOf<String>()
        val maxDepth = segments.minOf { it.size }

        for (i in 0 until maxDepth) {
            val segment = segments[0][i]
            if (segments.all { it[i] == segment }) {
                prefix += segment
            } else {
                break
            }
        }

        return if (prefix.isNotEmpty()) "/${prefix.joinToString("/")}" else ""
    }

    companion object {
        private const val MAX_CONTROLLERS = 20
        private const val MAX_ROUTES = 15

        private val CONTROLLER_ANNOTATIONS = CommonAnnotations.CONTROLLER

        private val METHOD_ANNOTATIONS = listOf(
            "org.springframework.web.bind.annotation.GetMapping" to "GET",
            "org.springframework.web.bind.annotation.PostMapping" to "POST",
            "org.springframework.web.bind.annotation.PutMapping" to "PUT",
            "org.springframework.web.bind.annotation.DeleteMapping" to "DELETE",
            "org.springframework.web.bind.annotation.PatchMapping" to "PATCH",
            "org.springframework.web.bind.annotation.RequestMapping" to "REQUEST",
        )
    }
}
