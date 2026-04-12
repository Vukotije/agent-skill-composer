package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.Processor
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.ConventionType
import com.vukan.agentskillcomposer.model.DetectedConvention
import java.util.concurrent.Callable

class DiStyleAnalyzer {

    fun analyze(project: Project): DetectedConvention? =
        ReadAction.nonBlocking(Callable {
            val scope = GlobalSearchScope.projectScope(project)
            val facade = JavaPsiFacade.getInstance(project)
            detectDiStyle(facade, scope)
        }).inSmartMode(project).executeSynchronously()

    private fun detectDiStyle(facade: JavaPsiFacade, scope: GlobalSearchScope): DetectedConvention? {
        // Collect Spring-managed beans from ALL stereotype annotations, not just @Service
        val managedBeans = mutableListOf<PsiClass>()

        for (fqn in STEREOTYPE_ANNOTATIONS) {
            if (managedBeans.size >= MAX_CLASSES) break
            val annotation = facade.findClass(fqn, scope) ?: continue
            AnnotatedElementsSearch.searchPsiClasses(annotation, scope).forEach(Processor { psiClass ->
                managedBeans += psiClass
                managedBeans.size < MAX_CLASSES
            })
        }

        if (managedBeans.isEmpty()) return null

        var constructorInjectionCount = 0
        var fieldInjectionCount = 0

        for (psiClass in managedBeans.distinctBy { it.qualifiedName }) {
            val hasFieldInjection = psiClass.fields.any { field ->
                field.hasAnnotation("org.springframework.beans.factory.annotation.Autowired") ||
                    field.hasAnnotation("jakarta.inject.Inject") ||
                    field.hasAnnotation("javax.inject.Inject")
            }

            if (hasFieldInjection) {
                fieldInjectionCount++
            } else if (psiClass.constructors.any { it.parameterList.parametersCount > 0 }) {
                constructorInjectionCount++
            }
        }

        val total = constructorInjectionCount + fieldInjectionCount
        if (total == 0) return null

        val (summary, evidence) = when {
            constructorInjectionCount.toDouble() / total >= DOMINANCE_THRESHOLD ->
                "Constructor injection" to
                    listOf("$constructorInjectionCount/$total Spring-managed beans use constructor injection")

            fieldInjectionCount.toDouble() / total >= DOMINANCE_THRESHOLD ->
                "Field injection via @Autowired/@Inject" to
                    listOf("$fieldInjectionCount/$total Spring-managed beans use field injection")

            else ->
                "Mixed injection styles (constructor and field)" to
                    listOf("$constructorInjectionCount constructor + $fieldInjectionCount field injection")
        }

        return DetectedConvention(
            type = ConventionType.DI_STYLE,
            summary = summary,
            confidence = if (total >= 3) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
            evidence = evidence,
        )
    }

    companion object {
        private const val MAX_CLASSES = 30
        private const val DOMINANCE_THRESHOLD = 0.6

        private val STEREOTYPE_ANNOTATIONS = listOf(
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.stereotype.Repository",
            "org.springframework.context.annotation.Configuration",
        )
    }
}
