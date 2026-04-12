package com.vukan.agentskillcomposer.analysis

import com.vukan.agentskillcomposer.model.ConventionType
import com.vukan.agentskillcomposer.model.DetectedConvention
import com.vukan.agentskillcomposer.model.DetectedFramework
import com.vukan.agentskillcomposer.model.SkillSuggestion
import com.vukan.agentskillcomposer.model.TestFramework

class SkillSuggestionEngine {

    fun suggest(
        frameworks: List<DetectedFramework>,
        testFrameworks: List<TestFramework>,
        conventions: List<DetectedConvention>,
    ): List<SkillSuggestion> = RULES.mapNotNull { rule ->
        if (rule.condition(frameworks, testFrameworks, conventions)) {
            SkillSuggestion(
                id = rule.id,
                displayName = rule.displayName,
                description = rule.description,
                reason = rule.reasonBuilder(frameworks, testFrameworks, conventions),
            )
        } else {
            null
        }
    }

    private class SkillRule(
        val id: String,
        val displayName: String,
        val description: String,
        val condition: (List<DetectedFramework>, List<TestFramework>, List<DetectedConvention>) -> Boolean,
        val reasonBuilder: (List<DetectedFramework>, List<TestFramework>, List<DetectedConvention>) -> String,
    )

    companion object {
        private val RULES = listOf(
            SkillRule(
                id = "write-tests",
                displayName = "Write Tests",
                description = "Write tests following project conventions",
                condition = { _, testFrameworks, _ -> testFrameworks.isNotEmpty() },
                reasonBuilder = { _, testFrameworks, conventions ->
                    val fw = testFrameworks.joinToString(", ") { it.displayName }
                    val style = conventions
                        .firstOrNull { it.type == ConventionType.TEST_STYLE }
                        ?.summary
                    if (style != null) "Detected $fw with $style" else "Detected $fw"
                },
            ),
            SkillRule(
                id = "implement-service",
                displayName = "Implement Service",
                description = "Implement service classes following project patterns",
                condition = { frameworks, _, conventions ->
                    conventions.hasNamingContaining("Service") ||
                        frameworks.any { it.name.contains("Spring") }
                },
                reasonBuilder = { _, _, conventions ->
                    val diStyle = conventions
                        .firstOrNull { it.type == ConventionType.DI_STYLE }
                        ?.summary
                    "Detected *Service naming pattern" +
                        (diStyle?.let { " with $it" } ?: "")
                },
            ),
            SkillRule(
                id = "implement-repository",
                displayName = "Implement Repository",
                description = "Implement repository interfaces following project patterns",
                condition = { frameworks, _, conventions ->
                    conventions.hasNamingContaining("Repository") ||
                        frameworks.any { it.name.contains("Data", ignoreCase = true) }
                },
                reasonBuilder = { frameworks, _, _ ->
                    val dataFramework = frameworks
                        .firstOrNull { it.name.contains("Data") || it.name.contains("JPA") }
                        ?.name
                    "Detected *Repository pattern" +
                        (dataFramework?.let { " with $it" } ?: "")
                },
            ),
            SkillRule(
                id = "implement-controller",
                displayName = "Implement Controller",
                description = "Implement API controllers following project patterns",
                condition = { _, _, conventions ->
                    conventions.hasNamingContaining("Controller")
                },
                reasonBuilder = { frameworks, _, _ ->
                    val webFramework = frameworks
                        .firstOrNull { it.name.contains("Web") || it.name.contains("Ktor") }
                        ?.name
                    "Detected *Controller naming pattern" +
                        (webFramework?.let { " with $it" } ?: "")
                },
            ),
            SkillRule(
                id = "implement-entity",
                displayName = "Implement Entity",
                description = "Implement JPA entities following project patterns",
                condition = { frameworks, _, conventions ->
                    frameworks.any { it.name.contains("JPA") || it.name.contains("Hibernate") } &&
                        conventions.hasNamingContaining("Entity")
                },
                reasonBuilder = { frameworks, _, _ ->
                    val fw = frameworks
                        .firstOrNull { it.name.contains("JPA") || it.name.contains("Hibernate") }
                        ?.name ?: "JPA"
                    "Detected $fw with *Entity naming pattern"
                },
            ),
        )

        private fun List<DetectedConvention>.hasNamingContaining(keyword: String): Boolean =
            any { it.type == ConventionType.NAMING && it.summary.contains(keyword, ignoreCase = true) }
    }
}
