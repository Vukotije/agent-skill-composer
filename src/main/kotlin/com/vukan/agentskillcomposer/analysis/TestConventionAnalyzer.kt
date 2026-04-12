package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.Processor
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.ConventionType
import com.vukan.agentskillcomposer.model.DetectedConvention
import java.util.concurrent.Callable

class TestConventionAnalyzer {

    fun analyze(project: Project): List<DetectedConvention> =
        ReadAction.nonBlocking(Callable {
            val scope = GlobalSearchScope.projectScope(project)
            val facade = JavaPsiFacade.getInstance(project)

            listOfNotNull(detectTestMethodStyle(facade, scope))
        }).inSmartMode(project).executeSynchronously()

    private fun detectTestMethodStyle(facade: JavaPsiFacade, scope: GlobalSearchScope): DetectedConvention? {
        val testAnnotation = facade.findClass("org.junit.jupiter.api.Test", scope)
            ?: facade.findClass("org.junit.Test", scope)
            ?: return null

        val testMethods = mutableListOf<PsiMethod>()
        AnnotatedElementsSearch.searchPsiMembers(testAnnotation, scope).forEach(Processor { member ->
            if (member is PsiMethod) testMethods += member
            testMethods.size < MAX_TEST_METHODS
        })

        if (testMethods.isEmpty()) return null

        var backtickCount = 0
        var camelCaseCount = 0

        for (method in testMethods) {
            val name = method.name
            if (name.contains(" ")) {
                backtickCount++
            } else if (CAMEL_CASE_TEST_PREFIX.containsMatchIn(name)) {
                camelCaseCount++
            }
        }

        val total = backtickCount + camelCaseCount
        if (total == 0) return null

        val (summary, evidence) = when {
            backtickCount.toDouble() / total >= DOMINANCE_THRESHOLD ->
                "Backtick test method names describing behavior" to
                    listOf("$backtickCount backtick-style @Test methods found (via PSI)")

            camelCaseCount.toDouble() / total >= DOMINANCE_THRESHOLD ->
                "camelCase test method names with should/test/when prefix" to
                    listOf("$camelCaseCount camelCase-style @Test methods found (via PSI)")

            else ->
                "Mixed test method naming (backtick and camelCase)" to
                    listOf("$backtickCount backtick + $camelCaseCount camelCase @Test methods found")
        }

        return DetectedConvention(
            type = ConventionType.TEST_STYLE,
            summary = summary,
            confidence = if (total >= 5) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
            evidence = evidence,
        )
    }

    companion object {
        private const val MAX_TEST_METHODS = 50
        private const val DOMINANCE_THRESHOLD = 0.6

        private val CAMEL_CASE_TEST_PREFIX = Regex("^(should|test|when|given)[A-Z]")
    }
}
