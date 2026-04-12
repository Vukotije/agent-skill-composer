package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.Processor
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.ConventionType
import com.vukan.agentskillcomposer.model.DetectedConvention
import java.util.concurrent.Callable

/**
 * Detects where and how validation happens in the project.
 * @Valid on controller parameters, constraint annotations on fields.
 */
class ValidationAnalyzer {

    fun analyze(project: Project): DetectedConvention? =
        ReadAction.nonBlocking(Callable {
            val scope = GlobalSearchScope.projectScope(project)
            val facade = JavaPsiFacade.getInstance(project)
            detectValidation(facade, scope)
        }).inSmartMode(project).executeSynchronously()

    private fun detectValidation(facade: JavaPsiFacade, scope: GlobalSearchScope): DetectedConvention? {
        val evidence = mutableListOf<String>()

        // Count @Valid usage on method parameters (controller-level validation)
        val validParamCount = countValidAnnotatedParams(facade, scope)
        if (validParamCount > 0) {
            evidence += "@Valid on $validParamCount method parameters (controller-level validation)"
        }

        // Count constraint annotations on fields
        val constraintCounts = mutableMapOf<String, Int>()
        for ((fqn, label) in CONSTRAINT_ANNOTATIONS) {
            val annotation = facade.findClass(fqn, scope) ?: continue
            var count = 0
            AnnotatedElementsSearch.searchPsiMembers(annotation, scope).forEach(Processor {
                count++
                count < 100
            })
            if (count > 0) constraintCounts.merge(label, count) { old, new -> old + new }
        }

        if (constraintCounts.isNotEmpty()) {
            evidence += "Constraints: ${constraintCounts.entries.joinToString(", ") { "${it.key} (${it.value})" }}"
        }

        if (evidence.isEmpty()) return null

        val summary = when {
            validParamCount > 0 && constraintCounts.isNotEmpty() ->
                "Jakarta/Bean Validation with @Valid on endpoints"
            constraintCounts.isNotEmpty() ->
                "Bean Validation constraints on fields"
            validParamCount > 0 ->
                "@Valid parameter validation"
            else -> return null
        }

        val total = validParamCount + constraintCounts.values.sum()
        return DetectedConvention(
            type = ConventionType.VALIDATION,
            summary = summary,
            confidence = if (total >= 3) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
            evidence = evidence,
        )
    }

    private fun countValidAnnotatedParams(facade: JavaPsiFacade, scope: GlobalSearchScope): Int {
        var count = 0
        var controllersScanned = 0

        for (fqn in CONTROLLER_ANNOTATIONS) {
            val annotation = facade.findClass(fqn, scope) ?: continue
            AnnotatedElementsSearch.searchPsiClasses(annotation, scope).forEach(Processor { controller ->
                controllersScanned++
                for (method in controller.methods) {
                    for (param in method.parameterList.parameters) {
                        if (VALID_ANNOTATIONS.any { param.hasAnnotation(it) }) {
                            count++
                        }
                    }
                }
                controllersScanned < MAX_CONTROLLERS
            })
        }

        return count
    }

    companion object {
        private const val MAX_CONTROLLERS = 30

        private val CONTROLLER_ANNOTATIONS = CommonAnnotations.CONTROLLER

        private val VALID_ANNOTATIONS = listOf(
            "jakarta.validation.Valid",
            "javax.validation.Valid",
            "org.springframework.validation.annotation.Validated",
        )

        private val CONSTRAINT_ANNOTATIONS = listOf(
            "jakarta.validation.constraints.NotNull" to "@NotNull",
            "javax.validation.constraints.NotNull" to "@NotNull",
            "jakarta.validation.constraints.NotBlank" to "@NotBlank",
            "javax.validation.constraints.NotBlank" to "@NotBlank",
            "jakarta.validation.constraints.NotEmpty" to "@NotEmpty",
            "javax.validation.constraints.NotEmpty" to "@NotEmpty",
            "jakarta.validation.constraints.Size" to "@Size",
            "javax.validation.constraints.Size" to "@Size",
            "jakarta.validation.constraints.Min" to "@Min",
            "javax.validation.constraints.Min" to "@Min",
            "jakarta.validation.constraints.Max" to "@Max",
            "javax.validation.constraints.Max" to "@Max",
            "jakarta.validation.constraints.Email" to "@Email",
            "javax.validation.constraints.Email" to "@Email",
            "jakarta.validation.constraints.Pattern" to "@Pattern",
            "javax.validation.constraints.Pattern" to "@Pattern",
        )

    }
}
