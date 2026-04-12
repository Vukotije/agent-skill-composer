package com.vukan.agentskillcomposer.model

data class SkillSuggestion(
    val id: String,
    val displayName: String,
    val description: String,
    val reason: String,
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9-]*"))) {
            "Skill id must be a lowercase slug: $id"
        }
    }
}
